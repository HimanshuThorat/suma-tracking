# Starts the Suma Tracking website. Start the backend first, in another terminal.
#   powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\start-frontend.ps1

# Is the backend answering on port 8080? (tries IPv4 and IPv6)
function Test-Backend {
    foreach ($h in @('127.0.0.1', '::1')) {
        $c = New-Object System.Net.Sockets.TcpClient
        try {
            $r = $c.BeginConnect($h, 8080, $null, $null)
            if ($r.AsyncWaitHandle.WaitOne(800) -and $c.Connected) { $c.Close(); return $true }
        } catch { }
        $c.Close()
    }
    return $false
}

if (-not (Test-Backend)) {
    Write-Host "Waiting for the backend on port 8080 (up to 2 minutes)..." -ForegroundColor Yellow
    $ok = $false
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        if (Test-Backend) { $ok = $true; break }
        Write-Host "." -NoNewline
    }
    Write-Host ""
    if (-not $ok) {
        Write-Host "The backend is still not answering, so sign-in would fail." -ForegroundColor Red
        Write-Host "In the backend terminal, check that it shows 'Started SumaTrackingBackendApplication'. If not, start it:" -ForegroundColor Yellow
        Write-Host "  powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\start-backend.ps1" -ForegroundColor Yellow
        exit 1
    }
}
Write-Host "Backend is running." -ForegroundColor Green
Set-Location C:\Dev\SumaTracking\frontend
if (-not (Test-Path node_modules)) { npm install }
Write-Host "Open the address shown below (http://localhost:5173) in Chrome." -ForegroundColor Green
npm run dev