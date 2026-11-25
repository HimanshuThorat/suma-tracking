# Stops Suma Tracking on Kubernetes (all copies to 0). The data and the password Secret are kept.
#   powershell -ExecutionPolicy Bypass -File scripts\k8s-stop.ps1
. "$PSScriptRoot\docker-common.ps1"
$Ns = 'suma-tracking'
kubectl -n $Ns scale deployment backend frontend --replicas=0 | Out-Null
kubectl -n $Ns scale statefulset db --replicas=0 | Out-Null
if ($LASTEXITCODE -eq 0) { Good "Stopped. Your data is safe. Start again with scripts\k8s-start.ps1" } else { Fail "Could not stop. Is Kubernetes running in Docker Desktop?" }
