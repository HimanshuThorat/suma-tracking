# Sets a new password for the suma_app database user.
# You need the "postgres" password you chose when you installed PostgreSQL.
#   powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\reset-db-password.ps1
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
Write-Host "First type the POSTGRES password (from the PostgreSQL install)." -ForegroundColor Cyan
Write-Host "Then type the NEW suma_app password twice. Nothing shows while you type; that is normal." -ForegroundColor Cyan
& $psql -U postgres -h localhost -d postgres -c "\password suma_app"
if ($LASTEXITCODE -eq 0) {
    Write-Host "Done. Use the NEW password when start-backend.ps1 asks for it." -ForegroundColor Green
} else {
    Write-Host "Not changed. The postgres password was probably wrong. Try again." -ForegroundColor Red
}