$ErrorActionPreference = 'Stop'
$jar = Join-Path $PSScriptRoot 'micropolis-2.0.0.jar'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    $jar = Join-Path $PSScriptRoot 'target/micropolis-2.0.0.jar'
}
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw "ビルド済みJARが見つかりません。先に mvn clean package を実行してください: $jar"
}
if ($env:JAVA_HOME) {
    $java = Join-Path $env:JAVA_HOME 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
        throw "JAVA_HOMEのJavaが見つかりません: $java"
    }
} else {
    $java = (Get-Command java -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
}
& $java '--enable-native-access=ALL-UNNAMED' '-Duser.language=ja' '-Duser.country=JP' -jar $jar
exit $LASTEXITCODE
