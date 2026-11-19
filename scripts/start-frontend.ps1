# Starts the Suma Tracking website. Start the backend first, in another terminal.
#   powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\start-frontend.ps1
$backend = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if (-not $backend) {
    Write-Host "The backend is not running yet, so login would fail (ECONNREFUSED)." -ForegroundColor Red
    Write-Host "Start it first in another terminal and wait for 'Started SumaTrackingBackendApplication':" -ForegroundColor Yellow
    Write-Host "  powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\start-backend.ps1" -ForegroundColor Yellow
    exit 1
}
Set-Location C:\Dev\SumaTracking\frontend
if (-not (Test-Path node_modules)) { npm install }
Write-Host "Open the address shown below (http://localhost:5173) in Chrome." -ForegroundColor Green
npm run dev