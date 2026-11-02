# Shared by the Japanese and English launchers. Compatible with Windows PowerShell 5.1.
function Find-MicropolisJava {
    $candidates = @()
    if ($env:MICROPOLIS_JAVA_HOME) {
        $candidates += Join-Path $env:MICROPOLIS_JAVA_HOME 'bin/java.exe'
    } else {
        if ($env:JAVA_HOME) {
            $candidates += Join-Path $env:JAVA_HOME 'bin/java.exe'
        }
        $candidates += @(Get-Command java -CommandType Application -All -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty Source)
        $jdkDirectory = Join-Path $HOME '.jdks'
        if (Test-Path -LiteralPath $jdkDirectory -PathType Container) {
            $candidates += @(Get-ChildItem -LiteralPath $jdkDirectory -Directory |
                Sort-Object Name -Descending |
                ForEach-Object { Join-Path $_.FullName 'bin/java.exe' })
        }
    }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        # java -version writes to stderr. Use redirected streams so PowerShell 5.1
        # does not turn normal version output into a terminating native error.
        $info = New-Object System.Diagnostics.ProcessStartInfo
        $info.FileName = $candidate
        $info.Arguments = '-version'
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardError = $true
        $info.RedirectStandardOutput = $true
        $process = [System.Diagnostics.Process]::Start($info)
        try {
            $version = $process.StandardError.ReadToEnd() + $process.StandardOutput.ReadToEnd()
            $process.WaitForExit()
            if ($process.ExitCode -eq 0 -and $version -match 'version\s+"(?<major>\d+)') {
                if ([int]$Matches['major'] -ge 25) { return $candidate }
            }
        } finally {
            $process.Dispose()
        }
    }
    throw 'Micropolis requires Java 25 or later. Set MICROPOLIS_JAVA_HOME to its installation directory, or install it under your user .jdks directory.'
}
