$ErrorActionPreference = 'Stop'

$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_SDK_ROOT = 'D:\Android\Sdk'
$env:ANDROID_USER_HOME = 'E:\Android\.android'
$env:GRADLE_USER_HOME = 'E:\Android\.gradle'
$env:JAVA_HOME = 'C:\Users\KK\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.15.6-hotspot'
$env:TEMP = 'E:\tmp\android'
$env:TMP = 'E:\tmp\android'

@($env:ANDROID_USER_HOME, $env:GRADLE_USER_HOME, $env:TEMP, 'E:\Android\bootstrap') |
  ForEach-Object { New-Item -ItemType Directory -Force -Path $_ | Out-Null }

$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"
