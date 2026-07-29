[CmdletBinding()]
param(
  [string]$SourcePath = (Join-Path $PSScriptRoot '..\src-tauri\src\quota_client.rs'),
  [string]$OutputPath = (Join-Path $PSScriptRoot '..\android-app\private-api.properties')
)

$ErrorActionPreference = 'Stop'
$allowedRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\android-app'))
$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
if (-not $resolvedOutput.StartsWith($allowedRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Output path must remain inside android-app.'
}
if ([IO.Path]::GetFileName($resolvedOutput) -ne 'private-api.properties') {
  throw 'Output filename must be private-api.properties.'
}

$source = Get-Content -Raw -LiteralPath $SourcePath
$clientId = [regex]::Match($source, '\("client_id",\s*"([^"]+)"\)').Groups[1].Value
$clientSecret = [regex]::Match($source, '\("client_secret",\s*"([^"]+)"\)').Groups[1].Value
if ([string]::IsNullOrWhiteSpace($clientId) -or [string]::IsNullOrWhiteSpace($clientSecret)) {
  throw 'Authorized OAuth compatibility constants were not found in the legacy quota client.'
}
[IO.File]::WriteAllLines($resolvedOutput, @("oauthClientId=$clientId", "oauthClientSecret=$clientSecret"), [System.Text.UTF8Encoding]::new($false))
