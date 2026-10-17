[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$config = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'private\backend.json') | ConvertFrom-Json
$base = 'http://127.0.0.1:' + [int]$config.port
$headers = @{ 'X-Forwarded-Proto' = 'https'; Authorization = 'Bearer ' + $config.adminToken }
$health = Invoke-RestMethod -Uri ($base + '/api/health') -Headers $headers -TimeoutSec 15
$devices = @(Invoke-RestMethod -Uri ($base + '/api/admin/devices') -Headers $headers -TimeoutSec 15)
if ($health.status -ne 'up') { throw 'Backend health check failed.' }
Write-Host ('Backend OK. Device records: ' + $devices.Count)
Write-Host 'This verifies the local Windows backend only. Public HTTPS and A920 check-in are separate checks.'
