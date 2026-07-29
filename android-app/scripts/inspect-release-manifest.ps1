param(
    [string]$ManifestPath = "app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
)

$ErrorActionPreference = "Stop"
$resolvedManifest = Resolve-Path -LiteralPath $ManifestPath -ErrorAction Stop
[xml]$manifest = Get-Content -Raw -LiteralPath $resolvedManifest
$application = $manifest.manifest.application

$androidNamespace = "http://schemas.android.com/apk/res/android"
$forbiddenAttributes = @("debuggable", "usesCleartextTraffic", "testOnly")
foreach ($attribute in $forbiddenAttributes) {
    $value = $application.GetAttribute($attribute, $androidNamespace)
    if ($value -eq "true") {
        throw "Forbidden release manifest setting android:$attribute=true in $resolvedManifest"
    }
}

foreach ($profileable in @($application.ChildNodes | Where-Object { $_.LocalName -eq "profileable" })) {
    if ($profileable.GetAttribute("shell", $androidNamespace) -eq "true") {
        throw "Forbidden release manifest element <profileable android:shell=`"true`"> in $resolvedManifest"
    }
}

if ($manifest.manifest.ChildNodes | Where-Object {
    $_.LocalName -eq "uses-permission" -and
    $_.GetAttribute("name", $androidNamespace) -eq "android.permission.QUERY_ALL_PACKAGES"
}) {
    throw "Forbidden release permission android.permission.QUERY_ALL_PACKAGES in $resolvedManifest"
}

Write-Host "Release manifest passes forbidden-setting checks: $resolvedManifest"
