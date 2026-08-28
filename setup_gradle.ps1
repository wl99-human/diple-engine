$toolsDir = "D:\Projects\diple-engine\tools"
if (!(Test-Path $toolsDir)) { New-Item -ItemType Directory -Path $toolsDir | Out-Null }
$zipPath = "$toolsDir\gradle-8.10.2-bin.zip"
$gradleDest = "$toolsDir\gradle-8.10.2"

if (!(Test-Path "$gradleDest\bin\gradle.bat")) {
    Write-Host "Downloading Gradle 8.10.2..."
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip" -OutFile $zipPath
    Write-Host "Extracting Gradle 8.10.2..."
    Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
    Remove-Item $zipPath -Force
}
Write-Host "Gradle 8.10.2 ready at $gradleDest"
