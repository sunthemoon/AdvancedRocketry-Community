param([Parameter(Mandatory=$true)][string]$Commit, [string]$Label='native-01',
      [string]$Cases='new-contained,new-border,new-square,new-strip,generated-contained,generated-border,generated-square,generated-strip,capacity,replace,restart',
      [switch]$Batches, [ValidatePattern('^W[0-9]+$')][string]$TaskId='W206', [switch]$Jfr,
      [switch]$Attribution, [ValidateSet('full','timing-only')][string]$TransitionMode='full',
      [ValidateRange(1,4)][int]$Repetitions=1)
$ErrorActionPreference='Stop'
$workspace=(Resolve-Path "$PSScriptRoot/..").Path
if($Label -notmatch '^native-[a-z0-9-]+$') { throw 'Invalid label' }
$root="D:/GitHub/ARCE-Task-Evidence/root-queue/$TaskId/$Label"
$temp='D:/GitHub/ARCE-Task-Evidence/Temp'
$export="$temp/$TaskId-$Label-export"; $archive="$temp/$TaskId-$Label.zip"
$lock='D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm'
$token="$TaskId pid=$PID $([guid]::NewGuid())"
$owned=$false; $created=$false; $archived=$false
$result=@{source_commit=$Commit;started=(Get-Date -Format o);status='RUNNING'}
if((Test-Path $root) -or (Test-Path $export) -or (Test-Path $archive)) { throw 'Existing output is immutable' }
New-Item -ItemType Directory -Path $root | Out-Null
function Remove-Owned([string]$Path) {
    $resolved=[IO.Path]::GetFullPath($Path)
    $prefix=[IO.Path]::GetFullPath($export)+[IO.Path]::DirectorySeparatorChar
    if($resolved -ne [IO.Path]::GetFullPath($export) -and !$resolved.StartsWith($prefix)) { throw 'Cleanup escaped export' }
    if((Get-Content -LiteralPath "$export/.w206-owned") -ne $token) { throw 'Export ownership changed' }
    if(Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
try {
    Set-Location $workspace
    if((git rev-parse HEAD) -ne $Commit -or (git status --porcelain)) { throw 'Source pin is not clean' }
    & git archive --format=zip "--output=$archive" $Commit
    if($LASTEXITCODE -ne 0) { throw 'Export failed' }; $archived=$true
    Expand-Archive -LiteralPath $archive -DestinationPath $export
    $created=$true; Set-Content -LiteralPath "$export/.w206-owned" $token
    $deadline=(Get-Date).AddHours(3)
    while($true) {
        & cmd.exe /d /c "mkdir `"$lock`" 2>NUL"
        if($LASTEXITCODE -eq 0) { $owned=$true; break }
        if((Get-Date) -gt $deadline -or ((Get-Date)-(Get-Item -LiteralPath $lock).CreationTime).TotalHours -gt 3) { throw 'Heavy lock wait expired; not removed' }
        Start-Sleep 60
    }
    Set-Content -LiteralPath "$lock/owner.txt" $token
    foreach($drive in 'C','D') { if((Get-PSDrive $drive).Free -lt 10GB) { throw "Disk $drive below 10 GiB" } }
    @{
        cpu=(Get-CimInstance Win32_Processor | Select-Object Name,NumberOfCores,NumberOfLogicalProcessors)
        os=(Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,TotalVisibleMemorySize)
        disk=(Get-PSDrive C,D | Select-Object Name,Free)
        processes=(Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^(java|codex|claude)(\.exe)?$'} | Select-Object ProcessId,Name,CommandLine)
    } | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath "$root/host.json"
    $env:JAVA_HOME='C:/Program Files/Java/jdk-17.0.7'; $env:JAVA_TOOL_OPTIONS=''
    $gradle=@('--offline','--no-daemon','--no-parallel','--max-workers=1','--console=plain')
    Set-Location $export
    & .\gradlew.bat @gradle jar *> "$root/production-build.log"
    if($LASTEXITCODE -ne 0) { throw 'Production JAR build failed' }
    $jar='advancedrocketry-community-1.20.1-1.8.0-dev.jar'
    Copy-Item -LiteralPath "build/libs/$jar" -Destination "$root/production.jar"
    if($Batches) {
        $env:JAVA_TOOL_OPTIONS='-Dadvancedrocketrycommunity.gameTestBatches=rocket_warmup_kept_pad,rocket_warmup_capacity,rocket_warmup_stale_copy,rocket_warmup_discard,rocket_warmup_named_pad'
        & .\gradlew.bat @gradle runGameTestServer *> "$root/batches-gradle.log"
        $result.batches_exit=$LASTEXITCODE
        Copy-Item -LiteralPath 'build/gametest/logs/latest.log' -Destination "$root/batches-latest.log"
        Remove-Owned "$export/build/gametest/world"
        $env:JAVA_TOOL_OPTIONS=''
        if($result.batches_exit -ne 0) { throw 'Warm-up batches failed' }
    }
    $package='io/github/sunthemoon/advancedrocketrycommunity'
    foreach($name in 'rocket/server/W206Observation','rocket/server/W206NativeWarmup','resourceescrow/W206NativeFixtures') {
        $from=[IO.Path]::GetFullPath("$export/src/test/java/$package/$name.java")
        $to=[IO.Path]::GetFullPath("$export/src/main/java/$package/$name.java")
        $prefix=[IO.Path]::GetFullPath($export)+[IO.Path]::DirectorySeparatorChar
        if(!$from.StartsWith($prefix) -or !$to.StartsWith($prefix)) { throw 'Diagnostic move escaped export' }
        Move-Item -LiteralPath $from -Destination $to
    }
    if($Attribution) {
        Copy-Item -LiteralPath "$export/src/test/java/$package/resourceescrow/W212Timing.java" -Destination "$export/src/main/java/$package/resourceescrow/W212Timing.java"
        Remove-Item -LiteralPath "$export/src/test/java/$package/resourceescrow/W212Timing.java"
        & python -B "$workspace/scripts/w212_instrument.py" $export $root *> "$root/instrumentation.log"
        if($LASTEXITCODE -ne 0) { throw 'Diagnostic instrumentation failed' }
    }
    & .\gradlew.bat @gradle test --tests '*W206ObservationTest' jar *> "$root/diagnostic-build.log"
    if($LASTEXITCODE -ne 0) { throw 'Diagnostic build failed' }
    Copy-Item -LiteralPath "build/libs/$jar" -Destination "$root/diagnostic.jar"
    Set-Location $workspace
    $profileArgs=@(); if($Jfr) { $profileArgs += '--jfr' }
    if($Attribution) { $profileArgs += '--attribution' }
    $profileArgs += @('--transition-mode',$TransitionMode,'--repetitions',"$Repetitions")
    & python -B scripts/run_w206_warmup.py --jar "$root/diagnostic.jar" --production-jar "$root/production.jar" `
        --libraries 'D:/GitHub/ARCE-Task-Evidence/v1.8.0/guard-observation-04/native-04/server/libraries' `
        --evidence $root --source-commit $Commit --lock-owner $token --cases $Cases @profileArgs *> "$root/native-console.log"
    $result.native_exit=$LASTEXITCODE
    if($LASTEXITCODE -ne 0) { throw 'Native case failed; retained evidence; production unchanged' }
    $result.status='PASS'
} catch { $result.status='FAIL'; $result.error=$_.Exception.ToString() }
finally {
    Set-Location $workspace
    try {
        if($created) { Remove-Owned $export }
        if($archived) {
            if([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($archive)) -ne [IO.Path]::GetFullPath($temp)) { throw 'Archive target changed' }
            Remove-Item -LiteralPath $archive
        }
        # JARs are reproducible from the pinned source; retain their inventory hashes, not duplicate artifacts.
        foreach($name in 'production.jar','diagnostic.jar') { if(Test-Path -LiteralPath "$root/$name") { Remove-Item -LiteralPath "$root/$name" } }
    } catch { $result.cleanup_error=$_.Exception.ToString() }
    if($owned) {
        $owner=Get-Content -LiteralPath "$lock/owner.txt" -ErrorAction SilentlyContinue | Select-Object -First 1
        if($owner -and $owner.StartsWith($token)) {
            if([IO.Path]::GetFullPath($lock) -ne 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm') { throw 'Lock target changed' }
            Remove-Item -LiteralPath $lock -Recurse -Force
        }
    }
    $result.finished=Get-Date -Format o
    $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$root/runner-result.json"
}
