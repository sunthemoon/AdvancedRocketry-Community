param(
    [Parameter(Mandatory=$true)][string]$Commit,
    [Parameter(Mandatory=$true)][string]$Label,
    [switch]$GameTest
)
$ErrorActionPreference='Stop'
$workspace=(Resolve-Path "$PSScriptRoot/..").Path
$root='D:/GitHub/ARCE-Task-Evidence/root-queue/W197'
$temp='D:/GitHub/ARCE-Task-Evidence/Temp'
$lock='D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm'
if($Label -notmatch '^native-[a-z0-9-]+$') { throw 'Invalid evidence label' }
$out="$root/$Label"; $export="$temp/W197-$Label-export"; $archive="$temp/W197-$Label.zip"
$token="W197 pid=$PID $([guid]::NewGuid())"
$owned=$false; $exportCreated=$false; $archiveCreated=$false; $gameTestStarted=$false
if((Test-Path $out) -or (Test-Path $export) -or (Test-Path $archive)) { throw 'Output already exists' }
New-Item -ItemType Directory -Path $out | Out-Null
$result=@{source_commit=$Commit;started=(Get-Date -Format o);status='RUNNING'}
function HostSnapshot([string]$Name) {
    @{
        time=(Get-Date -Format o)
        cpu=(Get-CimInstance Win32_Processor | Select-Object Name,NumberOfCores,NumberOfLogicalProcessors,LoadPercentage)
        os=(Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,TotalVisibleMemorySize,FreePhysicalMemory)
        disks=(Get-PSDrive C,D | Select-Object Name,Free)
        processes=(Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^(java|javaw|codex|claude|python|node)(\.exe)?$'} |
            Select-Object ProcessId,ParentProcessId,Name,CommandLine,KernelModeTime,UserModeTime,WorkingSetSize)
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$out/$Name.json"
}
try {
    Set-Location $workspace
    if((git rev-parse HEAD) -ne $Commit -or (git status --porcelain)) { throw 'Source pin is not clean' }
    $deadline=(Get-Date).AddHours(3)
    while($true) {
        & cmd.exe /d /c "mkdir `"$lock`" 2>NUL"
        if($LASTEXITCODE -eq 0) { $owned=$true; break }
        if((Get-Date) -gt $deadline -or ((Get-Date)-(Get-Item -LiteralPath $lock).CreationTime).TotalHours -gt 3) {
            throw 'Heavy lock older than three hours or wait expired; not removed'
        }
        Start-Sleep 60
    }
    Set-Content -LiteralPath "$lock/owner.txt" $token
    foreach($drive in 'C','D') { if((Get-PSDrive $drive).Free -lt 10GB) { throw "Disk $drive below 10 GiB" } }
    HostSnapshot 'before-build'
    $env:JAVA_HOME='C:/Program Files/Java/jdk-17.0.7'; $env:JAVA_TOOL_OPTIONS=''
    $gradle=@('--offline','--no-daemon','--no-parallel','--max-workers=1','--console=plain')
    & .\gradlew.bat @gradle jar *> "$out/production-build.log"
    if($LASTEXITCODE -ne 0) { throw 'Production jar build failed' }
    $production="$workspace/build/libs/advancedrocketry-community-1.20.1-1.8.0-dev.jar"
    if($GameTest) {
        if(Test-Path -LiteralPath 'build/gametest/world') { throw 'Existing GameTest world is not owned by this invocation' }
        $gameTestStarted=$true
        & .\gradlew.bat @gradle runGameTestServer *> "$out/gametest.log"
        $result.gametest_exit=$LASTEXITCODE
        & python -B scripts/check_gametest_log.py build/gametest/logs/latest.log *> "$out/gametest-check.log"
        $result.gametest_checker_exit=$LASTEXITCODE
        Copy-Item -LiteralPath 'build/gametest/logs/latest.log' -Destination "$out/gametest-latest.log"
    }
    & git archive --format=zip "--output=$archive" $Commit
    if($LASTEXITCODE -ne 0) { throw 'Source export failed' }
    $archiveCreated=$true
    Expand-Archive -LiteralPath $archive -DestinationPath $export
    $exportCreated=$true
    Set-Content -LiteralPath "$export/.w197-owned" $token
    $package='io/github/sunthemoon/advancedrocketrycommunity/rocket/server'
    foreach($name in 'W197MaximumFixture','W197CostObserver','W197I6NativeCost') {
        $from=[IO.Path]::GetFullPath("$export/src/test/java/$package/$name.java")
        $to=[IO.Path]::GetFullPath("$export/src/main/java/$package/$name.java")
        $prefix=[IO.Path]::GetFullPath($export)+[IO.Path]::DirectorySeparatorChar
        if(!$from.StartsWith($prefix) -or !$to.StartsWith($prefix)) { throw 'Diagnostic source move escaped export' }
        Move-Item -LiteralPath $from -Destination $to
    }
    Set-Location $export
    & .\gradlew.bat @gradle test --tests '*W197MaximumFixtureTest' jar *> "$out/diagnostic-build.log"
    if($LASTEXITCODE -ne 0) { throw 'Diagnostic qualification/build failed' }
    Copy-Item -LiteralPath 'build/test-results/test/TEST-io.github.sunthemoon.advancedrocketrycommunity.rocket.server.W197MaximumFixtureTest.xml' -Destination "$out/fixture.xml"
    $diagnostic="$export/build/libs/advancedrocketry-community-1.20.1-1.8.0-dev.jar"
    Set-Location $workspace
    # No concurrent Gradle invocation is launched by this worker during the measurement.
    HostSnapshot 'before-native'
    & python -B scripts/run_w197_i6_cost.py --jar $diagnostic --production-jar $production `
        --libraries 'D:/GitHub/ARCE-Task-Evidence/v1.8.0/guard-observation-04/native-04/server/libraries' `
        --evidence $out --source-commit $Commit --lock-owner $token *> "$out/native-console.log"
    $result.native_exit=$LASTEXITCODE
    HostSnapshot 'after-native'
    if($result.native_exit -ne 0) { throw 'Native benchmark did not qualify; retained the failed samples' }
    $result.status='PASS'
} catch { $result.status='FAIL'; $result.error=$_.Exception.ToString() }
finally {
    Set-Location $workspace
    try {
        if($gameTestStarted) {
            $world=[IO.Path]::GetFullPath("$workspace/build/gametest/world")
            $expected=[IO.Path]::GetFullPath($workspace)+[IO.Path]::DirectorySeparatorChar+'build\gametest\world'
            if($world -ne $expected) { throw 'GameTest cleanup escaped workspace' }
            if(Test-Path -LiteralPath $world) { Remove-Item -LiteralPath $world -Recurse -Force }
        }
        if($exportCreated -and (Test-Path -LiteralPath $export)) {
            if([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($export)) -ne [IO.Path]::GetFullPath($temp) -or
                (Get-Content -LiteralPath "$export/.w197-owned") -ne $token) { throw 'Export cleanup ownership changed' }
            Remove-Item -LiteralPath $export -Recurse -Force
        }
        if($archiveCreated -and (Test-Path -LiteralPath $archive)) {
            if([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($archive)) -ne [IO.Path]::GetFullPath($temp)) { throw 'Archive cleanup escaped Temp' }
            Remove-Item -LiteralPath $archive
        }
    } catch { $result.cleanup_error=$_.Exception.ToString() }
    if($owned) {
        $owner=Get-Content -LiteralPath "$lock/owner.txt" -ErrorAction SilentlyContinue | Select-Object -First 1
        if($owner -and $owner.StartsWith($token)) {
            if([IO.Path]::GetFullPath($lock) -ne 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm') { throw 'Lock cleanup target changed' }
            Remove-Item -LiteralPath $lock -Recurse -Force
        }
    }
    $result.finished=Get-Date -Format o
    $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$out/runner-result.json"
}
