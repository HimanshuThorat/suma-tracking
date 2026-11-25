# Forgot the Docker database password? This sets a new one. Data is kept.
#   powershell -ExecutionPolicy Bypass -File scripts\docker-reset-db-password.ps1
. "$PSScriptRoot\docker-common.ps1"
Set-Location (Split-Path $PSScriptRoot -Parent)
Assert-Docker
$volume = docker volume ls -q --filter "name=suma-tracking_suma-db" 2>$null | Where-Object { $_ -eq 'suma-tracking_suma-db' }
if (-not $volume) { Fail "There is no Suma Tracking database in Docker yet. Run scripts\docker-start.ps1 first." }
docker compose stop backend frontend *> $null
docker compose up -d db
Start-Sleep -Seconds 8
$pw = Read-NewPassword "NEW database password" $null
# Sent to the database through the input, never as a command-line word
$sql = "ALTER ROLE suma_app WITH PASSWORD '" + $pw.Replace("'", "''") + "';"
$sql | docker compose exec -T db psql -U postgres -d postgres -v ON_ERROR_STOP=1 -q
if ($LASTEXITCODE -ne 0) { Fail "Could not change the password. Send a screenshot." }
Good "Password changed. Now run scripts\docker-start.ps1 and type the NEW password."
