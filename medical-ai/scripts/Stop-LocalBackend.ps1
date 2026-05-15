param(
    [switch]$DryRun,
    [switch]$KeepInfra
)

$ErrorActionPreference = 'Stop'

$medicalAiRoot = Split-Path -Parent $PSScriptRoot
$dockerDir = Join-Path $medicalAiRoot 'docker'
$stateDir = Join-Path $medicalAiRoot '.local-dev'
$stateFile = Join-Path $stateDir 'backend-state.json'

$infraServices = @(
    'mysql',
    'redis',
    'nacos',
    'rabbitmq',
    'milvus-etcd',
    'milvus-minio',
    'milvus',
    'seata-server'
)

if (Test-Path -LiteralPath $stateFile) {
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json

    foreach ($service in @($state.services)) {
        $runningProcess = Get-Process -Id $service.pid -ErrorAction SilentlyContinue
        if (-not $runningProcess) {
            Write-Host ("Skipped {0}: PID {1} is not running" -f $service.name, $service.pid)
            continue
        }

        if ($DryRun) {
            Write-Host ("[dry-run] taskkill /PID {0} /T /F" -f $service.pid)
            continue
        }

        & taskkill.exe /PID $service.pid /T /F | Out-Null
        Write-Host ("Stopped {0}: PID {1}" -f $service.name, $service.pid)
    }

    if (-not $DryRun) {
        Remove-Item -LiteralPath $stateFile -Force
    }
}
else {
    Write-Host ("State file not found: {0}" -f $stateFile)
}

if ($KeepInfra) {
    return
}

Get-Command docker -ErrorAction Stop | Out-Null

if ($DryRun) {
    Write-Host ('[dry-run] docker compose stop ' + ($infraServices -join ' '))
    return
}

Push-Location $dockerDir
try {
    & docker compose stop @infraServices
}
finally {
    Pop-Location
}
