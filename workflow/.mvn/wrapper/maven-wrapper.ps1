# Minimal SHA-256-pinned Maven bootstrapper for Windows.
$ErrorActionPreference = 'Stop'
$propertiesPath = Join-Path $PSScriptRoot 'maven-wrapper.properties'
$props = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
    if ($line -match '^([^#=]+)=(.*)$') { $props[$matches[1].Trim()] = $matches[2].Trim() }
}
$url = $props['distributionUrl']
$expected = $props['distributionSha256Sum']
if (-not $url.StartsWith('https://repo.maven.apache.org/')) { throw 'Unexpected Maven download URL' }
if ($expected -notmatch '^[0-9a-f]{64}$') { throw 'Missing/invalid Maven SHA-256' }
$archiveName = [IO.Path]::GetFileName(([uri]$url).AbsolutePath)
$distName = $archiveName -replace '-bin\.zip$', ''
$cache = if ($env:MVNW_CACHE_HOME) { $env:MVNW_CACHE_HOME } else { Join-Path $HOME '.m2\wrapper\dists' }
$distHome = Join-Path $cache $distName
$mvnCmd = Join-Path $distHome 'bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $mvnCmd)) {
    New-Item -ItemType Directory -Force -Path $cache | Out-Null
    $tmp = Join-Path ([IO.Path]::GetTempPath()) ('vik-mvn-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $tmp | Out-Null
    try {
        $archive = Join-Path $tmp $archiveName
        Invoke-WebRequest -Uri $url -OutFile $archive -UseBasicParsing
        $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $expected.ToLowerInvariant()) { throw 'Maven download checksum mismatch' }
        Expand-Archive -LiteralPath $archive -DestinationPath $tmp
        $extracted = Join-Path $tmp $distName
        if (-not (Test-Path (Join-Path $extracted 'bin\mvn.cmd'))) { throw 'Invalid Maven archive structure' }
        if (-not (Test-Path -LiteralPath $distHome)) { Move-Item -LiteralPath $extracted -Destination $distHome }
    }
    finally { Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue }
}
Push-Location (Split-Path $PSScriptRoot -Parent | Split-Path -Parent)
try {
    & $mvnCmd @args
    exit $LASTEXITCODE
}
finally { Pop-Location }
