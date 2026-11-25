# Stops Suma Tracking in Docker. All data is kept; start again with scripts\docker-start.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\docker-stop.ps1
. "$PSScriptRoot\docker-common.ps1"
Set-Location (Split-Path $PSScriptRoot -Parent)
Assert-Docker
docker compose stop
if ($LASTEXITCODE -eq 0) { Good "Stopped. Your data is safe." } else { Fail "Could not stop. Send a screenshot." }
