[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$private = Join-Path $PSScriptRoot 'private'
$configPath = Join-Path $private 'backend.json'
if (-not (Test-Path $configPath)) { throw 'Run .\Initialize-Backend.ps1 first.' }
$config = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
if ($config.adminToken -notmatch '^[A-Za-z0-9_-]{32,128}$' -or $config.databasePassword -notmatch '^[A-Za-z0-9_-]{32,128}$') { throw 'Invalid private configuration.' }
if ([int]$config.port -lt 1024 -or [int]$config.port -gt 65535) { throw 'Invalid backend port.' }
$java = 'java.exe'
if ($env:JAVA_HOME) { $java = Join-Path $env:JAVA_HOME 'bin\java.exe' }
if (-not (Get-Command $java -ErrorAction SilentlyContinue)) { throw 'Install Java 17 or newer, and set JAVA_HOME or PATH.' }
$database = (Join-Path $private 'db\scanplus').Replace('\', '/')
if ($database -match '[;\r\n]') { throw 'The installation path cannot contain semicolons or line breaks.' }
$env:SCANPLUS_ADMIN_TOKEN = [string]$config.adminToken
$env:SCANPLUS_DB_PASSWORD = [string]$config.databasePassword
$env:SCANPLUS_DATABASE = 'jdbc:h2:file:' + $database + ';AUTO_SERVER=FALSE'
$env:SCANPLUS_APKS_DIRECTORY = Join-Path $private 'apks'
$env:SCANPLUS_BIND = '127.0.0.1'
$env:SCANPLUS_PORT = [string]$config.port
# Proxy trust and the loopback binding are explicit in cloudflare-backend.properties.
$env:JAVA_OPTS = '-Xms64m -Xmx512m -Duser.timezone=UTC -Dfile.encoding=UTF-8'
$log = Join-Path $private ('logs\backend-' + [DateTime]::UtcNow.ToString('yyyy-MM-dd-HHmmss') + '.log')
$launcher = Join-Path $PSScriptRoot 'backend\bin\scanplus-security-backend.bat'
Write-Host ('Starting backend on loopback port ' + $config.port + '. Log: ' + $log)
Push-Location (Join-Path $PSScriptRoot 'backend')
try {
    # Native stderr is logged rather than interpreted as a PowerShell exception.
    $ErrorActionPreference = 'Continue'
    & $launcher '--spring.config.additional-location=file:../cloudflare-backend.properties' >> $log 2>&1
    $code = $LASTEXITCODE
} finally {
    Pop-Location
    Remove-Item Env:SCANPLUS_ADMIN_TOKEN -ErrorAction SilentlyContinue
    Remove-Item Env:SCANPLUS_DB_PASSWORD -ErrorAction SilentlyContinue
}
exit $code
