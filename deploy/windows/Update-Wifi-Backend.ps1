[CmdletBinding()]
param([string]$TargetRoot = 'C:\SCANPLUS-Windows-Host')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$TargetRoot = [System.IO.Path]::GetFullPath($TargetRoot)
$source = Join-Path $PSScriptRoot 'backend'
$destination = Join-Path $TargetRoot 'backend'
$private = Join-Path $TargetRoot 'private'
$configPath = Join-Path $private 'backend.json'
if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) { throw 'Existing private/backend.json was not found. This is an update, not an initializer.' }
if (-not (Test-Path -LiteralPath (Join-Path $source 'bin\scanplus-security-backend.bat') -PathType Leaf)) { throw 'Extract the complete Wi-Fi update package before running this script.' }
if ([System.IO.Path]::GetFullPath($source).TrimEnd('\') -eq $destination.TrimEnd('\')) { throw 'Extract this update into a separate directory, not over the running installation.' }
$config = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
$port = [int]$config.port
if ($port -lt 1024 -or $port -gt 65535) { throw 'Invalid configured backend port.' }
$running = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
if ($running) { throw 'Stop the existing SCANPLUS backend first (Ctrl+C in its window, or stop its scheduled task). Leave the Cloudflare tunnel running, then run this script again.' }
$task = Get-ScheduledTask -TaskName 'SCANPLUS-Backend' -ErrorAction SilentlyContinue
if ($task -and $task.State -eq 'Running') { throw 'Stop the SCANPLUS-Backend scheduled task before updating.' }
$database = Join-Path $private 'db'
if (Test-Path -LiteralPath $database) {
    foreach ($file in Get-ChildItem -LiteralPath $database -Filter '*.mv.db' -File) {
        $handle = $null
        try { $handle = [System.IO.File]::Open($file.FullName, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::None) }
        catch { throw 'The backend database is still open. Wait for its process to stop before updating.' }
        finally { if ($handle) { $handle.Dispose() } }
    }
}
$stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-ffff')
$backup = Join-Path $private ('backups\wifi-' + $stamp)
$stage = Join-Path $TargetRoot ('backend-wifi-stage-' + $stamp)
New-Item -ItemType Directory -Path $backup -ErrorAction Stop | Out-Null
# Backups stay under the existing restricted private directory.
if (Test-Path -LiteralPath $database) { Copy-Item -LiteralPath $database -Destination (Join-Path $backup 'db') -Recurse }
Copy-Item -LiteralPath $source -Destination $stage -Recurse
$old = Join-Path $backup 'backend'
if (Test-Path -LiteralPath $destination) { Move-Item -LiteralPath $destination -Destination $old }
try { Move-Item -LiteralPath $stage -Destination $destination }
catch {
    if ((Test-Path -LiteralPath $old) -and -not (Test-Path -LiteralPath $destination)) { Move-Item -LiteralPath $old -Destination $destination }
    throw
}
Write-Host ('Backend files updated. Previous backend and database backup: ' + $backup)
Write-Host 'Private configuration, APK storage, agent enrollment and tunnel settings were retained.'
Write-Host 'Start the backend with the existing Start-Backend.ps1, or restart its existing scheduled task.'
Write-Host 'Then deploy the cloudflare folder from this update with your existing Wrangler account.'
Write-Host 'No Wi-Fi blocking policy has been enabled by this update.'
