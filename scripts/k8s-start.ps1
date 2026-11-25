# =====================================================================
# Starts Suma Tracking on Kubernetes (the one inside Docker Desktop).
#   powershell -ExecutionPolicy Bypass -File scripts\k8s-start.ps1
# Website:  http://localhost:30300
# This is a SEPARATE copy from the Docker one (its own database).
# =====================================================================
. "$PSScriptRoot\docker-common.ps1"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root
$Ns = 'suma-tracking'

function Kube-Secret([string]$Name, [hashtable]$Data) {
    # Sent to Kubernetes through the input, never as a command-line word or a file
    $obj = @{ apiVersion = 'v1'; kind = 'Secret'; type = 'Opaque'
              metadata = @{ name = $Name; namespace = $Ns }; stringData = $Data }
    $obj | ConvertTo-Json -Depth 5 | kubectl apply -f - | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail "Could not save the secret $Name in Kubernetes." }
}

Step "1/6  Checking Docker and Kubernetes..."
Assert-Docker
if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    Fail "kubectl was not found. In Docker Desktop: Settings > Kubernetes > Enable Kubernetes > Apply. Then open a NEW PowerShell window."
}
$ctx = (kubectl config current-context 2>$null | Out-String).Trim()
if ($ctx -ne 'docker-desktop') {
    kubectl config use-context docker-desktop *> $null
    if ($LASTEXITCODE -ne 0) { Fail "Kubernetes of Docker Desktop is not on. Docker Desktop: Settings > Kubernetes > Enable Kubernetes > Apply." }
}
kubectl get nodes *> $null
if ($LASTEXITCODE -ne 0) { Fail "Kubernetes is still starting. Wait until Docker Desktop shows 'Kubernetes running', then run this again." }
Good "Kubernetes is running (docker-desktop)."

Step "2/6  Building the images (the first time takes 5 to 15 minutes)..."
docker compose build
if ($LASTEXITCODE -ne 0) { Fail "Building the images failed. Read the red lines above, or send a screenshot." }

Step "3/6  Database password..."
kubectl apply -f k8s\00-namespace.yaml | Out-Null
kubectl -n $Ns get secret suma-db *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "     First start on Kubernetes: make a database password. Kubernetes keeps it as a Secret." -ForegroundColor Yellow
    $pw = Read-NewPassword "database password" $null
    Kube-Secret 'suma-db' @{ password = $pw }
    $pw = $null
    Good "Saved as a Kubernetes Secret."
} else {
    Good "Already set (Kubernetes Secret suma-db)."
}

Step "4/6  Starting database, backend and website..."
kubectl apply -f k8s\10-database.yaml -f k8s\20-backend.yaml -f k8s\30-frontend.yaml | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "Kubernetes did not accept the files in the k8s folder. Send a screenshot." }
# after k8s-stop everything is at 0 copies; bring them back
kubectl -n $Ns scale statefulset db --replicas=1 | Out-Null
kubectl -n $Ns scale deployment backend --replicas=1 | Out-Null
kubectl -n $Ns scale deployment frontend --replicas=2 | Out-Null
# use the images just built
kubectl -n $Ns rollout restart deployment backend frontend | Out-Null

Step "5/6  Waiting until everything is ready (up to 10 minutes)..."
kubectl -n $Ns rollout status statefulset/db --timeout=300s
if ($LASTEXITCODE -ne 0) { Fail "The database did not start. Run:  kubectl -n $Ns logs db-0" }
kubectl -n $Ns rollout status deployment/backend --timeout=600s
if ($LASTEXITCODE -ne 0) {
    kubectl -n $Ns logs deployment/backend --tail=40
    Fail "The backend did not start. The last lines of its log are above. Send a screenshot."
}
kubectl -n $Ns rollout status deployment/frontend --timeout=300s
if ($LASTEXITCODE -ne 0) { Fail "The website did not start. Run:  kubectl -n $Ns get pods" }
Good "All running."

Step "6/6  Checking the first Super Admin..."
$count = (kubectl -n $Ns exec db-0 -- psql -U suma_app -d suma_tracking -tAc "select count(*) from users" 2>$null | Out-String).Trim()
if ($count -eq '0') {
    $admin = Read-FirstAdmin
    $FirstAdminSql | kubectl -n $Ns exec -i db-0 -- psql -U suma_app -d suma_tracking -v ON_ERROR_STOP=1 -q `
        -v "code=$($admin.Code)" -v "name=$($admin.Name)" -v "email=$($admin.Email)"
    if ($LASTEXITCODE -ne 0) { Fail "Could not create the Super Admin. Send a screenshot." }

    # One start of the backend with the temporary password, then the secret is removed and it starts again without it
    Kube-Secret 'suma-bootstrap' @{ email = $admin.Email; password = $admin.Password }
    $since = Get-Since
    kubectl -n $Ns rollout restart deployment backend | Out-Null
    kubectl -n $Ns rollout status deployment/backend --timeout=600s | Out-Null
    $logs = { kubectl -n $Ns logs deployment/backend --since-time=$since 2>$null }.GetNewClosure()
    $null = Wait-ForBackend $logs 300
    $bootLog = (kubectl -n $Ns logs deployment/backend --since-time=$since 2>$null | Out-String)
    kubectl -n $Ns delete secret suma-bootstrap | Out-Null
    kubectl -n $Ns rollout restart deployment backend | Out-Null
    kubectl -n $Ns rollout status deployment/backend --timeout=600s | Out-Null
    if ($bootLog -match 'temporary password set') {
        Good "Super Admin $($admin.Email) is ready. Sign in with the temporary password, then make your own."
    } else {
        Write-Host ($bootLog -split "`n" | Select-String 'Bootstrap admin' | Out-String) -ForegroundColor Yellow
        Fail "The temporary password was not set. Send a screenshot of the lines above."
    }
} else {
    Good "Users already exist. Sign in with your account."
}

Write-Host ""
kubectl -n $Ns get pods
Write-Host ""
Write-Host "Suma Tracking on Kubernetes:  http://localhost:30300" -ForegroundColor Green
Write-Host "To stop it:  powershell -ExecutionPolicy Bypass -File scripts\k8s-stop.ps1" -ForegroundColor Yellow
Start-Process "http://localhost:30300"
