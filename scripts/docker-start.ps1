# =====================================================================
# Starts Suma Tracking with Docker: database + backend + website.
# Works from any folder the project was unzipped to:
#   powershell -ExecutionPolicy Bypass -File scripts\docker-start.ps1
# The database password is typed hidden and never saved in any file.
# =====================================================================
. "$PSScriptRoot\docker-common.ps1"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root

Step "1/5  Checking Docker..."
Assert-Docker

$volume = docker volume ls -q --filter "name=suma-tracking_suma-db" 2>$null | Where-Object { $_ -eq 'suma-tracking_suma-db' }
if (-not $volume) {
    Step "2/5  First start on this computer: make a database password."
    Write-Host "     Remember it. You will type it every time you start Suma Tracking." -ForegroundColor Yellow
    $env:SUMA_DB_PASSWORD = Read-NewPassword "database password" $null
} else {
    Step "2/5  Type the database password you made the first time."
    $env:SUMA_DB_PASSWORD = Read-Hidden "Database password"
}

Step "3/5  Building and starting. The FIRST time takes 5 to 15 minutes (it downloads Java, Node and PostgreSQL)..."
$since = Get-Since
docker compose up -d --build --force-recreate
if ($LASTEXITCODE -ne 0) { Fail "Docker could not start Suma Tracking. Read the red lines above, or send a screenshot." }

Step "4/5  Waiting for the backend to start"
$logs = { docker compose logs backend --no-color --since $since 2>$null }.GetNewClosure()
$result = Wait-ForBackend $logs 900
if ($result -eq 'BADPASS') {
    docker compose stop backend frontend *> $null
    Fail ("Wrong database password. Run this again and type the password you made the FIRST time.`n" +
          "Forgot it? Run:  powershell -ExecutionPolicy Bypass -File scripts\docker-reset-db-password.ps1")
}
if ($result -ne 'OK') {
    docker compose logs backend --no-color --tail 40
    Fail "The backend did not start ($result). The last lines of its log are above. Send a screenshot."
}
Good "Backend is running."

Step "5/5  Checking the first Super Admin..."
$count = (docker compose exec -T db psql -U suma_app -d suma_tracking -tAc "select count(*) from users" 2>$null | Out-String).Trim()
if ($count -eq '0') {
    $admin = Read-FirstAdmin
    $FirstAdminSql | docker compose exec -T db psql -U suma_app -d suma_tracking -v ON_ERROR_STOP=1 -q `
        -v "code=$($admin.Code)" -v "name=$($admin.Name)" -v "email=$($admin.Email)"
    if ($LASTEXITCODE -ne 0) { Fail "Could not create the Super Admin. Send a screenshot." }

    # The backend gives this Super Admin the temporary password once (BootstrapAdminRunner)...
    $env:SUMA_BOOTSTRAP_ADMIN_EMAIL = $admin.Email
    $env:SUMA_BOOTSTRAP_ADMIN_PASSWORD = $admin.Password
    $since = Get-Since
    docker compose up -d --no-deps --force-recreate backend
    $logs = { docker compose logs backend --no-color --since $since 2>$null }.GetNewClosure()
    $result = Wait-ForBackend $logs 300
    $bootLog = (docker compose logs backend --no-color --since $since 2>$null | Out-String)
    # ...then the backend is started again WITHOUT the password, so it is not kept anywhere
    Remove-Item Env:SUMA_BOOTSTRAP_ADMIN_EMAIL, Env:SUMA_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    $since = Get-Since
    docker compose up -d --no-deps --force-recreate backend
    $logs = { docker compose logs backend --no-color --since $since 2>$null }.GetNewClosure()
    $null = Wait-ForBackend $logs 300
    if ($bootLog -match 'temporary password set') {
        Good "Super Admin $($admin.Email) is ready. Sign in with the temporary password, then make your own."
    } else {
        Write-Host ($bootLog -split "`n" | Select-String 'Bootstrap admin' | Out-String) -ForegroundColor Yellow
        Fail "The temporary password was not set. Send a screenshot of the lines above."
    }
} else {
    Good "Users already exist. Sign in with your account."
}

Remove-Item Env:SUMA_DB_PASSWORD -ErrorAction SilentlyContinue
Write-Host ""
Write-Host "Suma Tracking is running:  http://localhost:3000" -ForegroundColor Green
Write-Host "To stop it:  powershell -ExecutionPolicy Bypass -File scripts\docker-stop.ps1" -ForegroundColor Yellow
Start-Process "http://localhost:3000"
