[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$wc = New-Object System.Net.WebClient
$zipPath = "C:\Users\Lenovo\.gemini\antigravity-ide\scratch\diple-engine\maven.zip"
$destDir = "C:\Users\Lenovo\.gemini\antigravity-ide\scratch\diple-engine\tools"
$wc.DownloadFile("https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip", $zipPath)
Expand-Archive -Path $zipPath -DestinationPath $destDir -Force
Remove-Item $zipPath -Force
& "C:\Users\Lenovo\.gemini\antigravity-ide\scratch\diple-engine\tools\apache-maven-3.9.6\bin\mvn.cmd" -version
