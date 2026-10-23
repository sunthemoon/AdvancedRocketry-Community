param(
    [Parameter(Mandatory = $true)][string]$EvidenceDirectory,
    [Parameter(Mandatory = $true)][string]$SourceCommit,
    [ValidateSet('micro', 'pressure', 'reference')][string]$Stage = 'micro',
    [string]$SourceServer
)
# Start this finite driver as a detached process. It alone acquires and releases its lock.
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$evidence = [IO.Path]::GetFullPath($EvidenceDirectory)
$allowed = [IO.Path]::GetFullPath('D:/GitHub/ARCE-Task-Evidence/root-queue/W144/')
if (-not $evidence.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $evidence)) {
    throw 'Evidence directory must be new and inside the W144 evidence root'
}
New-Item -ItemType Directory -Path $evidence | Out-Null
Start-Transcript -Path (Join-Path $evidence 'driver.log') | Out-Null
$lock = 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm'
$token = "W144 pid=$PID $([guid]::NewGuid())"
$acquired = $false
$exitCode = 1
try {
    Set-Location -LiteralPath $repo
    if ((git rev-parse HEAD) -ne $SourceCommit -or (git status --porcelain)) { throw 'Source must be clean and exact' }
    $handoff = Join-Path $allowed 'last-heavy-release.txt'
    if (Test-Path -LiteralPath $handoff) {
        $released = [DateTimeOffset]::Parse((Get-Content -LiteralPath $handoff | Select-Object -First 1))
        $remaining = 90 - ([DateTimeOffset]::UtcNow - $released).TotalSeconds
        if ($remaining -gt 0) { Start-Sleep -Seconds ([int][Math]::Ceiling($remaining)) }
    }
    $deadline = (Get-Date).AddHours(3)
    while ($true) {
        # Native mkdir is atomic; PowerShell New-Item is not the machine-lock protocol.
        $previous = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
        cmd /c mkdir "$lock" 2>$null
        $mkdirExit = $LASTEXITCODE; $ErrorActionPreference = $previous
        if ($mkdirExit -eq 0) { $acquired = $true; break }
        if (((Get-Date) - (Get-Item -LiteralPath $lock).CreationTime).TotalHours -gt 3 -or (Get-Date) -ge $deadline) {
            throw 'Heavy-JVM lock is older than three hours or wait expired; not removing it'
        }
        Start-Sleep -Seconds 60
    }
    Set-Content -LiteralPath "$lock/owner.txt" -Value "$token $(Get-Date -Format o)"
    $env:W144_LOCK_TOKEN = $token
    function Assert-Resources {
        $os = Get-CimInstance Win32_OperatingSystem
        $free = [long]$os.FreePhysicalMemory * 1024
        $drives = Get-PSDrive C,D
        $drives | Select-Object Name,Free | Format-Table
        Write-Output "Free physical bytes=$free"
        if ($free -lt 6GB -or @($drives | Where-Object { $_.Free -lt 10GB }).Count) {
            throw 'Requires 6 GiB RAM and 10 GiB free on C: and D:'
        }
    }
    Assert-Resources
    $classpath = Join-Path $evidence 'classpath.txt'
    $task = if ($Stage -eq 'micro') { 'w144BenchmarkClasspath' } else { 'reobfW144NativeJar' }
    & .\gradlew.bat --offline --no-daemon --no-parallel --max-workers=1 -I scripts/cargo_benchmark.init.gradle `
        "-Pw144Classpath=$classpath" $task --console=plain *> (Join-Path $evidence 'classpath-build.log')
    if ($LASTEXITCODE -ne 0) { throw "Classpath build failed: $LASTEXITCODE" }
    # --no-daemon's single-use JVM may exit just after the Gradle client. Do not kill any foreign JVM.
    $quietDeadline = (Get-Date).AddMinutes(5)
    do {
        $jvms = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') })
        if (-not $jvms.Count) { break }
        if ((Get-Date) -ge $quietDeadline) { throw 'Other JVMs remain; microbenchmark not started' }
        Start-Sleep -Seconds 5
    } while ($true)
    Assert-Resources
    $previous = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    if ($Stage -eq 'micro') {
        & python -B scripts/run_v180_cargo_benchmark.py --java "$env:JAVA_HOME/bin/java.exe" --classpath-file $classpath `
            --source-commit $SourceCommit --evidence-dir (Join-Path $evidence 'micro') *> (Join-Path $evidence 'runner.log')
    } else {
        $jars = @(Get-ChildItem -LiteralPath "$repo/build/libs" -Filter '*-w144-benchmark.jar')
        if ($jars.Count -ne 1 -or -not $SourceServer) { throw 'Requires one native benchmark artifact and a source server' }
        & python -B scripts/run_v180_cargo_native_benchmark.py --java "$env:JAVA_HOME/bin/java.exe" --case $Stage `
            --jar $jars[0].FullName --source-server $SourceServer --source-commit $SourceCommit `
            --evidence-dir (Join-Path $evidence 'native') *> (Join-Path $evidence 'runner.log')
    }
    $exitCode = $LASTEXITCODE; $ErrorActionPreference = $previous
} catch {
    Write-Output $_.Exception.ToString()
} finally {
    # The native child removes its unique, marker-owned server in its finally before returning.
    if ($acquired) {
        $owner = Get-Content -LiteralPath "$lock/owner.txt" -ErrorAction SilentlyContinue | Select-Object -First 1
        $resolved = [IO.Path]::GetFullPath($lock)
        if ($owner -and $owner.StartsWith($token) -and $resolved -eq 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm') {
            Remove-Item -LiteralPath "$lock/owner.txt"
            Remove-Item -LiteralPath $lock
            Set-Content -LiteralPath $handoff -Value ([DateTimeOffset]::UtcNow.ToString('o'))
        } else { Write-Output 'Lock ownership changed; left untouched' }
    }
    Set-Content -LiteralPath (Join-Path $evidence 'exit-code.txt') -Value $exitCode
    Stop-Transcript | Out-Null
}
exit $exitCode
