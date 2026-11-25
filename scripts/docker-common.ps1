# Shared helpers for the Docker and Kubernetes scripts of Suma Tracking.
# Passwords are always typed hidden and are never written to any file.

$ErrorActionPreference = 'Continue'
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)

function Step([string]$Text) { Write-Host $Text -ForegroundColor Cyan }
function Good([string]$Text) { Write-Host "     $Text" -ForegroundColor Green }
function Fail([string]$Text) {
    Write-Host ""
    Write-Host $Text -ForegroundColor Red
    Remove-Item Env:SUMA_DB_PASSWORD, Env:SUMA_BOOTSTRAP_ADMIN_EMAIL, Env:SUMA_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    exit 1
}

function Assert-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Fail ("Docker is not installed. Install it with this command, then restart the computer and open Docker Desktop:`n" +
              "  winget install -e --id Docker.DockerDesktop")
    }
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        Fail "Docker Desktop is not running. Open Docker Desktop, wait until it says 'Engine running', then run this again."
    }
    Good "Docker is running."
}

function Read-Hidden([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    return [System.Net.NetworkCredential]::new("", $secure).Password
}

function Test-PasswordRules([string]$Password, [string]$Email) {
    if ($Password.Length -lt 10) { return "Use at least 10 characters." }
    if ($Password.Length -gt 72) { return "Use at most 72 characters." }
    if ($Password -notmatch '[A-Za-z]' -or $Password -notmatch '[0-9]') { return "Use at least one letter and one number." }
    if ($Email -and $Password.Trim().ToLower() -eq $Email.Trim().ToLower()) { return "The password must not be the email." }
    return ""
}

function Read-NewPassword([string]$What, [string]$Email) {
    while ($true) {
        $one = Read-Hidden "Make a $What (10+ characters, letters and numbers)"
        $problem = Test-PasswordRules $one $Email
        if ($problem) { Write-Host "     $problem" -ForegroundColor Red; continue }
        $two = Read-Hidden "Type the same $What again"
        if ($one -ne $two) { Write-Host "     The two passwords are not the same. Try again." -ForegroundColor Red; continue }
        return $one
    }
}

function Read-FirstAdmin {
    Write-Host ""
    Write-Host "This database has no users yet. Let us make the first Super Admin." -ForegroundColor Yellow
    while ($true) {
        $name = (Read-Host "Full name (example: Mayur Shelar)").Trim()
        if ($name -match "^[A-Za-z][A-Za-z .'-]{1,149}$") { break }
        Write-Host "     Use only letters, spaces, dot, apostrophe or hyphen (2 to 150 characters)." -ForegroundColor Red
    }
    while ($true) {
        $email = (Read-Host "Company email (example: name@sumasoft.com)").Trim().ToLower()
        if ($email -match '^[a-z0-9._%+-]+@[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}$' -and $email.Length -le 254) { break }
        Write-Host "     That is not a valid email address." -ForegroundColor Red
    }
    while ($true) {
        $code = (Read-Host "Employee ID (press Enter for SA001)").Trim()
        if (-not $code) { $code = 'SA001' }
        if ($code -match '^[A-Za-z0-9-]{1,30}$') { break }
        Write-Host "     Use only letters, numbers and hyphen (up to 30)." -ForegroundColor Red
    }
    Write-Host "Now a TEMPORARY sign-in password. You must change it at the first sign-in." -ForegroundColor Yellow
    $pw = Read-NewPassword "temporary sign-in password" $email
    return @{ Name = $name; Email = $email; Code = $code; Password = $pw }
}

$FirstAdminSql = "INSERT INTO users (employee_code, full_name, email, role_id, status) " +
                 "SELECT :'code', :'name', :'email', id, 'ACTIVE' FROM roles WHERE code = 'SUPER_ADMIN';"

function Get-Since { return (Get-Date).ToUniversalTime().AddSeconds(-5).ToString("yyyy-MM-ddTHH:mm:ssZ") }

# Waits until the backend log says it started. Returns OK, BADPASS, FAILED or TIMEOUT.
function Wait-ForBackend([scriptblock]$GetLogs, [int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    $dots = 0
    while ((Get-Date) -lt $deadline) {
        $log = (& $GetLogs | Out-String)
        if ($log -match 'Started SumaTrackingBackendApplication') { Write-Host ""; return 'OK' }
        if ($log -match 'password authentication failed') { Write-Host ""; return 'BADPASS' }
        if ($log -match 'APPLICATION FAILED TO START' -or $log -match 'FlywayValidateException|Migration .+ failed') { Write-Host ""; return 'FAILED' }
        Start-Sleep -Seconds 4
        $dots++
        Write-Host -NoNewline "."
        if ($dots % 15 -eq 0) { Write-Host " still starting ($([int](($Seconds - ($deadline - (Get-Date)).TotalSeconds))) s)" }
    }
    Write-Host ""
    return 'TIMEOUT'
}
