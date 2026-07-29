param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleTasks = @("assembleRelease")
)

$ErrorActionPreference = "Stop"

$signingRoot = "E:\Android\signing"
$keystorePath = Join-Path $signingRoot "quotacheck-release.jks"
$secretPath = Join-Path $signingRoot "quotacheck-release-password.xml"
$keyAlias = "quotacheck"
$keytoolPath = "C:\Users\KK\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.15.6-hotspot\bin\keytool.exe"
$gradleScript = Join-Path $PSScriptRoot "android-gradle.ps1"

New-Item -ItemType Directory -Path $signingRoot -Force | Out-Null

if ((Test-Path $keystorePath) -xor (Test-Path $secretPath)) {
    throw "Release signing files are incomplete in $signingRoot. Restore the matching pair before building."
}

if (-not (Test-Path $keystorePath)) {
    $randomBytes = New-Object byte[] 48
    $randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomGenerator.GetBytes($randomBytes)
    } finally {
        $randomGenerator.Dispose()
    }
    $generatedPassword = [Convert]::ToBase64String($randomBytes)
    ConvertTo-SecureString $generatedPassword -AsPlainText -Force | Export-Clixml -Path $secretPath

    & $keytoolPath `
        -genkeypair `
        -keystore $keystorePath `
        -storepass $generatedPassword `
        -keypass $generatedPassword `
        -alias $keyAlias `
        -keyalg RSA `
        -keysize 4096 `
        -validity 10000 `
        -dname "CN=QuotaCheck, OU=Personal, O=QuotaCheck, L=Bangkok, C=TH"
    if ($LASTEXITCODE -ne 0) {
        Remove-Item -LiteralPath $keystorePath, $secretPath -Force -ErrorAction SilentlyContinue
        throw "Failed to create the QuotaCheck release key."
    }
    $generatedPassword = $null
}

$securePassword = Import-Clixml -Path $secretPath
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:QUOTACHECK_STORE_FILE = $keystorePath
    $env:QUOTACHECK_STORE_PASSWORD = $plainPassword
    $env:QUOTACHECK_KEY_ALIAS = $keyAlias
    $env:QUOTACHECK_KEY_PASSWORD = $plainPassword
    & $gradleScript @GradleTasks
    if ($LASTEXITCODE -ne 0) {
        throw "Android release build failed."
    }
} finally {
    $env:QUOTACHECK_STORE_PASSWORD = $null
    $env:QUOTACHECK_KEY_PASSWORD = $null
    $plainPassword = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
