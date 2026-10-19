$ErrorActionPreference = 'Stop'
# Maintainer-only lock refresh. Review all changes before running setup.
$headers = @{ 'User-Agent' = 'Chronicle-bootstrap/0.1 (local development)' }
$minecraftVersion = '1.21.10'
function Get-Json($url) { Invoke-RestMethod $url -Headers $headers -TimeoutSec 60 }
$jdk = (Get-Json 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse')[0]
$installers = Get-Json 'https://meta.fabricmc.net/v2/versions/installer'
$installer = $installers | Where-Object stable | Select-Object -First 1
$hashResponse = (Invoke-WebRequest ($installer.url + '.sha256') -UseBasicParsing -TimeoutSec 60).Content
if ($hashResponse -is [byte[]]) { $hashResponse = [Text.Encoding]::UTF8.GetString($hashResponse) }
$installerHash = $hashResponse.Trim()
$mods = foreach ($slug in @('fabric-api', 'simple-voice-chat')) {
    $gameFilter = [uri]::EscapeDataString('["' + $minecraftVersion + '"]')
    $v = Get-Json "https://api.modrinth.com/v2/project/$slug/version?game_versions=$gameFilter&loaders=%5B%22fabric%22%5D"
    $v = $v | Where-Object version_type -eq release | Select-Object -First 1
    $file = $v.files | Where-Object primary | Select-Object -First 1
    if (-not $file) { throw "No primary artifact for $slug" }
    if ($v.dependencies | Where-Object dependency_type -eq required) { throw "Review new required dependencies for $slug" }
    @{ slug=$slug; project_id=$v.project_id; version_id=$v.id; version=$v.version_number; url=$file.url; filename=$file.filename; sha512=$file.hashes.sha512; sha1=$file.hashes.sha1; size=$file.size }
}
$lock = [ordered]@{
    schema=1; resolved=(Get-Date -Format 'yyyy-MM-dd'); minecraft=$minecraftVersion; minecraft_protocol=773; loader='0.19.5'; yarn='1.21.10+build.3'; loom='1.11.8'
    gradle=@{ version='8.14.3'; url='https://services.gradle.org/distributions/gradle-8.14.3-bin.zip'; sha256='bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531' }
    java=@{ version=$jdk.version.semver; url=$jdk.binary.package.link; sha256=$jdk.binary.package.checksum; filename=$jdk.binary.package.name }
    installer=@{ version=$installer.version; url=$installer.url; sha256=$installerHash }
    mods=@($mods)
}
$path = Join-Path $PSScriptRoot '..\bootstrap.lock.json'
[IO.File]::WriteAllText($path, ($lock | ConvertTo-Json -Depth 12) + "`n", [Text.UTF8Encoding]::new($false))
Write-Output "Wrote $path. Review pins, align gradle.properties, then run setup."
