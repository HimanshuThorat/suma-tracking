# Starts the Suma Tracking backend safely. Run from any folder:
#   powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\start-backend.ps1
# The password is typed hidden, checked first, and never saved anywhere.
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"

Write-Host "1/4  Checking the database service..." -ForegroundColor Cyan
$svc = Get-Service postgresql* -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $svc) { Write-Host "PostgreSQL is not installed on this computer." -ForegroundColor Red; exit 1 }
if ($svc.Status -ne 'Running') {
    try { Start-Service $svc.Name -ErrorAction Stop }
    catch { Write-Host "PostgreSQL is stopped. Open PowerShell as Administrator and run:  Start-Service $($svc.Name)" -ForegroundColor Red; exit 1 }
}
Write-Host "     PostgreSQL is running." -ForegroundColor Green

Write-Host "2/4  Checking the suma_app password..." -ForegroundColor Cyan
$ok = $false
for ($i = 1; $i -le 3; $i++) {
    $pw = [System.Net.NetworkCredential]::new("", (Read-Host "suma_app DB password" -AsSecureString)).Password
    $env:PGPASSWORD = $pw
    & $psql -U suma_app -h localhost -d suma_tracking -tAc "select 1" *> $null
    $ok = ($LASTEXITCODE -eq 0)
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    if ($ok) { break }
    Write-Host "     Wrong password (try $i of 3). Type it again carefully." -ForegroundColor Red
}
if (-not $ok) {
    Write-Host "The password did not work 3 times. Reset it with:" -ForegroundColor Red
    Write-Host "  powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\reset-db-password.ps1" -ForegroundColor Yellow
    exit 1
}
$env:SUMA_DB_PASSWORD = $pw
Write-Host "     Password is correct." -ForegroundColor Green

# Step 43: the email password (for reset links and report emails). Optional: press Enter to skip.
$cfg = "C:\Dev\SumaTracking\config\mail-and-reports.properties"
$from = "the sending email"
if (Test-Path $cfg) {
    $line = Select-String -Path $cfg -Pattern '^\s*mail\.username\s*=\s*(.+)$' | Select-Object -First 1
    if ($line) { $from = $line.Matches[0].Groups[1].Value.Trim() }
}
$mailPw = [System.Net.NetworkCredential]::new("", (Read-Host "Email password for $from (press Enter to skip email)" -AsSecureString)).Password
if ($mailPw) { $env:SUMA_MAIL_PASSWORD = $mailPw; Write-Host "     Email is on." -ForegroundColor Green }
else { Remove-Item Env:SUMA_MAIL_PASSWORD -ErrorAction SilentlyContinue; Write-Host "     Email is off for this run." -ForegroundColor Yellow }

Write-Host "3/4  Freeing port 8080 (stopping an old backend if one is still running)..." -ForegroundColor Cyan
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }

Write-Host "4/4  Starting the backend. Wait for: Started SumaTrackingBackendApplication" -ForegroundColor Cyan
Set-Location C:\Dev\SumaTracking\backend
.\mvnw.cmd clean spring-boot:run
