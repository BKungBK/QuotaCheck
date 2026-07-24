# Install Android SDK CLI to D:\Android\Sdk (Optimized Fast Version)
$ErrorActionPreference = "Stop"

$sdkRoot = "D:\Android\Sdk"
$cmdlineToolsDir = Join-Path $sdkRoot "cmdline-tools"
$latestDir = Join-Path $cmdlineToolsDir "latest"
$tmpZip = Join-Path $env:TEMP "cmdline-tools.zip"

Write-Host "Setting up directories at $sdkRoot..."
New-Item -ItemType Directory -Force -Path $cmdlineToolsDir | Out-Null

if (-not (Test-Path (Join-Path $latestDir "bin\sdkmanager.bat"))) {
    if (-not (Test-Path $tmpZip)) {
        Write-Host "Downloading command-line tools via curl..."
        curl.exe -L -o $tmpZip "https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip"
    }

    Write-Host "Extracting command-line tools..."
    $extractDir = Join-Path $env:TEMP "cmdline-tools-extract"
    if (Test-Path $extractDir) { Remove-Item $extractDir -Recurse -Force }
    Expand-Archive -Path $tmpZip -DestinationPath $extractDir -Force

    if (Test-Path $latestDir) { Remove-Item $latestDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $latestDir | Out-Null

    Copy-Item -Path "$extractDir\cmdline-tools\*" -Destination $latestDir -Recurse -Force
    Remove-Item $tmpZip -Force
    Remove-Item $extractDir -Recurse -Force
}

Write-Host "Setting Environment Variables..."
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdkRoot, "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $sdkRoot, "User")
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot

$sdkmanager = Join-Path $latestDir "bin\sdkmanager.bat"

Write-Host "Accepting licenses..."
echo "y`ny`ny`ny`ny`ny`n" | & $sdkmanager --licenses --sdk_root=$sdkRoot

Write-Host "Installing platform-tools and platform-34..."
& $sdkmanager --sdk_root=$sdkRoot "platform-tools" "platforms;android-34" "build-tools;34.0.0"

Write-Host "SUCCESS: SDK setup complete at $sdkRoot"
