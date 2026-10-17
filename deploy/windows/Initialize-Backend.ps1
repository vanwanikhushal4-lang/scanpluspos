[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$private = Join-Path $PSScriptRoot 'private'
$configPath = Join-Path $private 'backend.json'
if (Test-Path $configPath) { throw 'Configuration already exists. It was not overwritten.' }
if ((Test-Path $private) -and @(Get-ChildItem -Force -LiteralPath $private).Count -gt 0) { throw 'The private directory contains existing files. It was not changed.' }
if (-not (Test-Path (Join-Path $PSScriptRoot 'backend\bin\scanplus-security-backend.bat'))) { throw 'Build and copy the backend distribution first; see the deployment README.' }

# Restrict this new private directory to its owner, SYSTEM and Administrators.
New-Item -ItemType Directory -Force -Path $private | Out-Null
$acl = New-Object System.Security.AccessControl.DirectorySecurity
$acl.SetAccessRuleProtection($true, $false)
$owner = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
$acl.SetOwner($owner)
$sids = @($owner, (New-Object System.Security.Principal.SecurityIdentifier('S-1-5-18')), (New-Object System.Security.Principal.SecurityIdentifier('S-1-5-32-544')))
foreach ($sid in $sids) {
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $private -AclObject $acl
foreach ($name in @('db', 'apks', 'logs')) { New-Item -ItemType Directory -Force -Path (Join-Path $private $name) | Out-Null }
function New-Secret {
    $bytes = New-Object byte[] 40
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
$config = [ordered]@{ adminToken = (New-Secret); databasePassword = (New-Secret); port = 9443; initializedUtc = [DateTime]::UtcNow.ToString('o'); migrationComplete = $false }
[IO.File]::WriteAllText($configPath, ($config | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
Write-Host 'Private Windows configuration created. Credentials were not displayed.'
Write-Host 'Next: run .\Start-Backend.ps1, then .\Check-Backend.ps1 in another window.'
Write-Host 'This is a new empty database. The existing A920 has not been migrated.'
