$ErrorActionPreference = "Stop"
$sdk = "C:\Users\adity\Android\Sdk"
$zip = "C:\Users\adity\Downloads\cmdline-tools.zip"
$url = "https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip"

New-Item -ItemType Directory -Force -Path $sdk | Out-Null

if (-not (Test-Path "$sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
    if (-not (Test-Path $zip)) {
        Write-Output "Downloading $url ..."
        curl.exe -L -o $zip $url
    }
    Write-Output "Extracting..."
    $tmp = "C:\Users\adity\Downloads\cmdline-tools-extract"
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-Archive -Path $zip -DestinationPath $tmp -Force
    New-Item -ItemType Directory -Force -Path "$sdk\cmdline-tools" | Out-Null
    if (Test-Path "$sdk\cmdline-tools\latest") { Remove-Item -Recurse -Force "$sdk\cmdline-tools\latest" }
    Move-Item "$tmp\cmdline-tools" "$sdk\cmdline-tools\latest"
}
Write-Output "sdkmanager present: $(Test-Path "$sdk\cmdline-tools\latest\bin\sdkmanager.bat")"
Get-ChildItem "$sdk\cmdline-tools\latest" -Name
