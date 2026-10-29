param(
    [Parameter(Mandatory=$true)][string]$Project,
    [string]$Editor = 'C:/asv/tools/unity/2022.3.62f2/Editor/Unity.exe'
)
$ErrorActionPreference='Stop'
$Project=(Resolve-Path -LiteralPath $Project).Path
if (!(Test-Path -LiteralPath $Editor)) { throw "Editor no instalado: $Editor" }
$config=Join-Path $Project 'data/calibration-01/config.json'
if (!(Test-Path -LiteralPath $config)) { throw 'Falta config de calibración' }
$data=Get-Content -Raw -LiteralPath $config | ConvertFrom-Json
if(Test-Path -LiteralPath $data.output) { throw 'Output ya existe; conservar evidencias y crear otra config antes de repetir' }
$log=Join-Path $Project ('logs/unity-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.log')
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$env:CARD_PILOT_CONFIG=$config
$process=Start-Process -FilePath $Editor -ArgumentList "-batchmode -quit -force-d3d11 -projectPath `"$Project`" -executeMethod CardPilot.Generate -logFile `"$log`"" -WindowStyle Hidden -PassThru -Wait
if ($process.ExitCode -ne 0) { throw "Unity falló ($($process.ExitCode)); revisar $log. No entrenar." }
if (!(Test-Path -LiteralPath (Join-Path $data.output 'COMPLETE'))) { throw "Unity no confirmó renders completos. Revisar $log" }
Write-Output "Renders completos en $($data.output). Ejecutar validate.py y REVISAR overlays antes de entrenar."
