[CmdletBinding()]
param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$GradleArguments
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'android-env.ps1')
& (Join-Path $PSScriptRoot 'verify-android-storage.ps1')

$projectDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..\android-app')).Path
$wrapper = Join-Path $projectDirectory 'gradlew.bat'
Push-Location $projectDirectory
try {
  & $wrapper @GradleArguments
  exit $LASTEXITCODE
} finally {
  Pop-Location
}
