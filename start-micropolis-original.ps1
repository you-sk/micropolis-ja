$ErrorActionPreference = 'Stop'
$jar = Join-Path $PSScriptRoot 'micropolis-2.0.0.jar'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    $jar = Join-Path $PSScriptRoot 'target/micropolis-2.0.0.jar'
}
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw "ビルド済みJARが見つかりません。先に mvn clean package を実行してください: $jar"
}
. (Join-Path $PSScriptRoot 'start-micropolis-common.ps1')
$java = Find-MicropolisJava
& $java '--enable-native-access=ALL-UNNAMED' '-Duser.language=en' '-Duser.country=US' -jar $jar
exit $LASTEXITCODE
