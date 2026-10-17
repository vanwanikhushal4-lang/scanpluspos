[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object System.Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Run this script from PowerShell opened as administrator.' }
if (-not (Test-Path (Join-Path $PSScriptRoot 'private\backend.json'))) { throw 'Initialize and check the backend first.' }
$taskName = 'SCANPLUS-Backend'
if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) { throw 'The SCANPLUS-Backend task already exists. It was not replaced.' }
$engine = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$arguments = '-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + (Join-Path $PSScriptRoot 'Start-Backend.ps1') + '"'
$action = New-ScheduledTaskAction -Execute $engine -Argument $arguments -WorkingDirectory $PSScriptRoot
$trigger = New-ScheduledTaskTrigger -AtStartup
# S4U runs as this account with limited privileges, without saving a password.
# Keep the bundle on an ordinary local disk, outside EFS and network drives.
$runAs = New-ScheduledTaskPrincipal -UserId $identity.Name -LogonType S4U -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $runAs -Settings $settings -Description 'SCANPLUS backend, bound to loopback and reached through its Cloudflare tunnel.' | Out-Null
Write-Host 'Startup task registered. Stop the manually running backend before starting this task.'
Write-Host "Start-ScheduledTask -TaskName 'SCANPLUS-Backend'"
Write-Host 'Java must be available to this account at startup, preferably through system JAVA_HOME.'
