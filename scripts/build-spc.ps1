param([string]$SourceDirectory)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$buildRoot = Join-Path $root 'target/spc-build'
$commit = '9e23d10f9fd2a6a2f33b10912dd8dc7153258995'
New-Item -ItemType Directory -Force -Path $buildRoot | Out-Null
$archive = Join-Path $buildRoot "game-music-emu-$commit.zip"
if (-not $SourceDirectory) {
    if (-not (Test-Path -LiteralPath $archive)) {
        Invoke-WebRequest -Uri "https://github.com/libgme/game-music-emu/archive/$commit.zip" -OutFile $archive
    }
    $SourceDirectory = Join-Path $buildRoot "game-music-emu-$commit"
    if (-not (Test-Path -LiteralPath $SourceDirectory)) {
        Expand-Archive -LiteralPath $archive -DestinationPath $buildRoot
    }
}
$build = Join-Path $buildRoot 'build'
if (Test-Path -LiteralPath (Join-Path $SourceDirectory '.git')) {
    $actualCommit = (git -C $SourceDirectory rev-parse HEAD).Trim()
    if ($actualCommit -ne $commit) { throw "Unexpected libgme revision: $actualCommit" }
    if (git -C $SourceDirectory status --porcelain --untracked-files=no) { throw 'libgme source checkout has modifications' }
}
cmake -S $SourceDirectory -B $build -G 'Visual Studio 17 2022' -A x64 -DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded -DBUILD_SHARED_LIBS=ON -DGME_BUILD_STATIC=OFF -DGME_ZLIB=OFF -DUSE_GME_SPC=ON -DUSE_GME_NSFE=OFF -DUSE_GME_NSF=OFF -DUSE_GME_GBS=OFF -DUSE_GME_GYM=OFF -DUSE_GME_VGM=OFF -DUSE_GME_AY=OFF -DUSE_GME_HES=OFF -DUSE_GME_KSS=OFF -DUSE_GME_SAP=OFF
if ($LASTEXITCODE -ne 0) { throw 'libgme configure failed' }
cmake --build $build --config Release
if ($LASTEXITCODE -ne 0) { throw 'libgme build failed' }
$native = Join-Path $root 'target/native'
New-Item -ItemType Directory -Force -Path $native | Out-Null
Copy-Item -LiteralPath (Join-Path $build 'gme/Release/gme.dll') -Destination (Join-Path $native 'gme.dll')
$sources = Join-Path $root 'target/dependency-sources'
New-Item -ItemType Directory -Force -Path $sources | Out-Null
if (Test-Path -LiteralPath $archive) {
    Copy-Item -LiteralPath $archive -Destination $sources
} else {
    # A supplied source checkout is supported for offline development.
    $sourceArchive = Join-Path $sources "game-music-emu-$commit.zip"
    git -C $SourceDirectory archive --format=zip --output=$sourceArchive HEAD
    if ($LASTEXITCODE -ne 0) { throw 'libgme source archive failed' }
}
# The upstream source contains a game music sample (test.nsf) which this SPC-only
# build never uses. Supply the corresponding emulator source without music assets.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$sourceArchive = Join-Path $sources "game-music-emu-$commit.zip"
$zip = [IO.Compression.ZipFile]::Open($sourceArchive, [IO.Compression.ZipArchiveMode]::Update)
try {
    $assets = @($zip.Entries | Where-Object { $_.FullName -match '(?i)\.(nsf|nsfe|spc|sfc|smc|gbs|gym|vgm|vgz|hes|kss|sap|ay)$' })
    foreach ($entry in $assets) { $entry.Delete() }
} finally {
    $zip.Dispose()
}
Write-Output "SPC decoder ready: $native"
