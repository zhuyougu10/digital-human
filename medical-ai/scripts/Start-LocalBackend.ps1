param(
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$medicalAiRoot = Split-Path -Parent $PSScriptRoot
$dockerDir = Join-Path $medicalAiRoot 'docker'
$stateDir = Join-Path $medicalAiRoot '.local-dev'
$stateFile = Join-Path $stateDir 'backend-state.json'
$defaultInternalSecret = 'local-dev-internal-secret'
$startupDelaySeconds = 5

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

$services = @(
    @{ Name = 'user-service'; WorkingDirectory = $medicalAiRoot; FilePath = 'cmd.exe'; Display = 'mvn.cmd -f medical-service/medical-user-service/pom.xml spring-boot:run'; Command = 'call mvn.cmd -f medical-service/medical-user-service/pom.xml spring-boot:run' },
    @{ Name = 'doctor-service'; WorkingDirectory = $medicalAiRoot; FilePath = 'cmd.exe'; Display = 'mvn.cmd -f medical-service/medical-doctor-service/pom.xml spring-boot:run -Dspring-boot.run.profiles=local'; Command = 'call mvn.cmd -f medical-service/medical-doctor-service/pom.xml spring-boot:run -Dspring-boot.run.profiles=local' },
    @{ Name = 'knowledge-service'; WorkingDirectory = $medicalAiRoot; FilePath = 'cmd.exe'; Display = 'mvn.cmd -f medical-service/medical-knowledge-service/pom.xml spring-boot:run'; Command = 'call mvn.cmd -f medical-service/medical-knowledge-service/pom.xml spring-boot:run' },
    @{ Name = 'appointment-service'; WorkingDirectory = $medicalAiRoot; FilePath = 'cmd.exe'; Display = 'mvn.cmd -f medical-service/medical-appointment-service/pom.xml -Dmaven.test.skip=true spring-boot:run -Dspring-boot.run.profiles=local'; Command = 'call mvn.cmd -f medical-service/medical-appointment-service/pom.xml -Dmaven.test.skip=true spring-boot:run -Dspring-boot.run.profiles=local' },
    @{ Name = 'ai-service'; WorkingDirectory = $medicalAiRoot; FilePath = 'cmd.exe'; Display = 'mvn.cmd -f medical-service/medical-ai-service/pom.xml spring-boot:run'; Command = 'call mvn.cmd -f medical-service/medical-ai-service/pom.xml spring-boot:run' },
    @{ Name = 'gateway'; WorkingDirectory = $medicalAiRoot; FilePath = 'cmd.exe'; Display = 'mvn.cmd -f medical-gateway/pom.xml spring-boot:run'; Command = 'call mvn.cmd -f medical-gateway/pom.xml spring-boot:run' },
    @{ Name = 'admin-dev'; WorkingDirectory = (Join-Path $medicalAiRoot '..\medical-admin'); FilePath = 'cmd.exe'; Display = 'npm.cmd run dev'; Command = 'call npm.cmd run dev' },
    @{ Name = 'live2d-dev'; WorkingDirectory = (Join-Path $medicalAiRoot '..\medical-mp\live2d-h5'); FilePath = 'cmd.exe'; Display = 'npm.cmd run dev -- --host 0.0.0.0 --port 5174'; Command = 'call npm.cmd run dev -- --host 0.0.0.0 --port 5174' }
)

function Test-TrackedProcessRunning {
    param(
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return $false
    }

    $state = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    foreach ($service in @($state.services)) {
        if (Get-Process -Id $service.pid -ErrorAction SilentlyContinue) {
            return $true
        }
    }

    return $false
}

function New-CmdInvocation {
    param(
        [string]$Command,
        [string]$Display
    )

    return @{
        Display = $Display
        CommandLine = $Command
    }
}

if (-not (Test-Path -LiteralPath $dockerDir)) {
    throw "Docker directory not found: $dockerDir"
}

Get-Command docker -ErrorAction Stop | Out-Null
Get-Command mvn.cmd -ErrorAction Stop | Out-Null
Get-Command npm.cmd -ErrorAction Stop | Out-Null

if (-not $env:SECURITY_INTERNAL_API_SECRET) {
    $env:SECURITY_INTERNAL_API_SECRET = $defaultInternalSecret
}

if ($DryRun) {
    Write-Host ('[dry-run] docker compose up -d ' + ($infraServices -join ' '))
    foreach ($service in $services) {
        $command = New-CmdInvocation -Command $service.Command -Display $service.Display
        Write-Host ('[dry-run] ' + $command.Display)
    }
    return
}

if (Test-TrackedProcessRunning -Path $stateFile) {
    throw "Detected running backend processes from a previous launch. Stop them first with medical-ai/scripts/Stop-LocalBackend.ps1"
}

Push-Location $dockerDir
try {
    & docker compose up -d @infraServices
}
finally {
    Pop-Location
}

$state = [ordered]@{
    startedAt = (Get-Date).ToString('s')
    root = $medicalAiRoot
    infraServices = $infraServices
    services = @()
}

foreach ($service in $services) {
    $command = New-CmdInvocation -Command $service.Command -Display $service.Display
    $windowTitle = 'local-dev:' + $service.Name
    $windowCommand = 'title {0} && {1}' -f $windowTitle, $command.CommandLine
    $process = Start-Process -FilePath $service.FilePath -ArgumentList @('/d', '/k', $windowCommand) -WorkingDirectory $service.WorkingDirectory -PassThru

    $state.services += [ordered]@{
        name = $service.Name
        pid = $process.Id
        windowTitle = $windowTitle
        command = $command.Display
        startedAt = (Get-Date).ToString('s')
    }

    Write-Host ("Started {0} (PID {1})" -f $service.Name, $process.Id)
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $stateFile -Encoding UTF8
    Start-Sleep -Seconds $startupDelaySeconds
}

Write-Host ''
Write-Host ("Infra services are running via Docker Compose in {0}" -f $dockerDir)
Write-Host ("Backend process metadata: {0}" -f $stateFile)
Write-Host 'Service logs are shown in their individual cmd windows.'
