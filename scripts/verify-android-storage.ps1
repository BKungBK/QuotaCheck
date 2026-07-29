$ErrorActionPreference = 'Stop'

$required = @(
  'ANDROID_HOME',
  'ANDROID_SDK_ROOT',
  'ANDROID_USER_HOME',
  'GRADLE_USER_HOME',
  'TEMP',
  'TMP'
)

foreach ($name in $required) {
  $value = [Environment]::GetEnvironmentVariable($name, 'Process')
  if ([string]::IsNullOrWhiteSpace($value) -or $value -notmatch '^[DdEe]:\\') {
    throw "$name must point to drive D or E, got '$value'"
  }
}

$expectedJavaHome = 'C:\Users\KK\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.15.6-hotspot'
if ($env:JAVA_HOME -ne $expectedJavaHome) {
  throw "JAVA_HOME must reuse the verified JDK 17"
}

Write-Host 'Android storage configuration is restricted to D/E drives.'
