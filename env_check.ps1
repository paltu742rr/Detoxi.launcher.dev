$ErrorActionPreference = "Continue"
Write-Output "=== JAVA ==="
java -version 2>&1 | Out-String
Write-Output ("JAVA_HOME=" + $env:JAVA_HOME)
Write-Output ("ANDROID_HOME=" + $env:ANDROID_HOME)
Write-Output ("ANDROID_SDK_ROOT=" + $env:ANDROID_SDK_ROOT)
Write-Output "=== SDK candidate paths ==="
$paths = @(
  "$env:LOCALAPPDATA\Android\Sdk",
  "$env:USERPROFILE\AppData\Local\Android\Sdk",
  "C:\Android\Sdk",
  "$env:PROGRAMFILES\Android\android-sdk"
)
foreach ($p in $paths) {
  if (Test-Path $p) {
    Write-Output "FOUND: $p"
    Get-ChildItem $p -Name | Out-String
  } else {
    Write-Output "missing: $p"
  }
}
Write-Output "=== local.properties ==="
if (Test-Path "local.properties") { Get-Content "local.properties" } else { Write-Output "no local.properties" }
Write-Output "=== wrapper ==="
Get-Content "gradle\wrapper\gradle-wrapper.properties"
Write-Output "=== git submodule status ==="
git submodule status 2>&1 | Out-String
Write-Output "=== prebuilts/libs ==="
if (Test-Path "prebuilts\libs") { Get-ChildItem "prebuilts\libs" -Name | Out-String } else { Write-Output "missing prebuilts/libs" }
