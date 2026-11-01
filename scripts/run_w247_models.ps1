# W247: matched focused GameTest runs of the K4/K3 model batches (stages before/after) and one full run (stage full).
# Runs from a git archive export of the pinned commit, under the heavy-JVM lock, as a detached process.
param(
    [Parameter(Mandatory=$true)][string]$Commit,
    [Parameter(Mandatory=$true)][ValidateSet('before','after','full')][string]$Stage,
    [string]$Label=''
)
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath("$PSScriptRoot/..")
if($Label -notmatch '^[a-z0-9-]{0,20}$') { throw 'Invalid label' }
$name=if($Label) { "$Stage-$Label" } else { $Stage }
$root="D:/GitHub/ARCE-Task-Evidence/root-queue/W247/$name"
$temp='D:/GitHub/ARCE-Task-Evidence/Temp'
$export=[IO.Path]::GetFullPath("$temp/W247-$name-export"); $archive=[IO.Path]::GetFullPath("$temp/W247-$name.zip")
$lock='D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm'
$token="W247-$name pid=$PID $([guid]::NewGuid())"
$owned=$false; $created=$false; $archived=$false
$result=[ordered]@{source_commit=$Commit;stage=$Stage;label=$Label;started=(Get-Date -Format o);runs=@();status='RUNNING'}
function Remove-Owned([string]$Path) {
    $resolved=[IO.Path]::GetFullPath($Path)
    if($resolved -ne $export -and !$resolved.StartsWith($export+[IO.Path]::DirectorySeparatorChar)) { throw 'Cleanup escaped the export' }
    if((Get-Content -LiteralPath "$export/.w247-owned") -ne $token) { throw 'Export ownership changed' }
    if(Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
if((Test-Path $root) -or (Test-Path $export) -or (Test-Path $archive)) { throw 'Output already exists' }
New-Item -ItemType Directory -Path $root | Out-Null
try {
    Set-Location $workspace
    if((git branch --show-current) -ne 'codex/v1.8.0-w247' -or (git rev-parse HEAD) -ne $Commit) { throw 'Source pin is not the assigned branch head' }
    & git archive --format=zip "--output=$archive" $Commit
    if($LASTEXITCODE -ne 0) { throw 'Export failed' }; $archived=$true
    Expand-Archive -LiteralPath $archive -DestinationPath $export
    $created=$true; Set-Content -LiteralPath "$export/.w247-owned" $token
    $env:JAVA_HOME='C:/Program Files/Java/jdk-17.0.7'
    $env:JAVA_TOOL_OPTIONS=$null
    $k4=@([regex]::Matches((Get-Content -Raw "$export/src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/K4ModelGameTests.java"),'batch\s*=\s*"([^"]+)"') |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    if($k4.Count -ne 12) { throw 'Unexpected K4 batch inventory' }
    $k3=@('w236_analyser_model','w236_observatory_model','w236_solar_model','w236_beacon_model')
    $neighbours=@('station_warp_deop','w133_escrow_fill_launch')
    $groups=@([ordered]@{name='k4';batches=$k4}, [ordered]@{name='k3';batches=$k3},
              [ordered]@{name='neighbours';batches=$neighbours}, [ordered]@{name='mixed';batches=@($k4)+$k3+$neighbours})
    if($Stage -eq 'full') { $groups=@([ordered]@{name='all';batches=@()}) }
    $result.groups=$groups
    $deadline=(Get-Date).AddHours(3)
    while($true) {
        & cmd.exe /d /c "mkdir `"$lock`" 2>NUL"
        if($LASTEXITCODE -eq 0) { $owned=$true; break }
        if((Get-Date) -gt $deadline -or ((Get-Date)-(Get-Item -LiteralPath $lock).CreationTime).TotalHours -gt 3) {
            throw 'Heavy lock older than three hours or wait expired; left untouched'
        }
        Start-Sleep 60
    }
    Set-Content -LiteralPath "$lock/owner.txt" "$token $(Get-Date -Format o)"
    $result.lock_taken=Get-Date -Format o
    Set-Location $export
    foreach($group in $groups) {
        $out="$root/$($group.name)"; New-Item -ItemType Directory -Path $out | Out-Null
        foreach($drive in 'C','D') { if((Get-PSDrive $drive).Free -lt 10GB) { throw "Disk $drive below 10 GiB" } }
        $gradleArgs=@('--offline','--no-daemon','--no-parallel','--max-workers=1','--console=plain')
        if($Stage -ne 'full') {
            $gradleArgs+=@('-I','scripts/w247_probe/init.gradle',"-Pw247Output=$out/ticks.jsonl",
                           "-Pw247Batches=$($group.batches -join ',')","-Pw247Jfr=$out/server.jfr")
        }
        $gradleArgs+='runGameTestServer'
        $gradleArgs | ConvertTo-Json | Set-Content -LiteralPath "$out/command.json"
        $begin=Get-Date
        & .\gradlew.bat @gradleArgs *> "$out/gradle.log"
        $code=$LASTEXITCODE
        foreach($log in 'latest.log','debug.log') {
            if(Test-Path -LiteralPath "build/gametest/logs/$log") { Copy-Item -LiteralPath "build/gametest/logs/$log" -Destination "$out/$log" }
        }
        $checker=$null
        if(Test-Path -LiteralPath "$out/latest.log") {
            & python -X utf8 -B scripts/check_gametest_log.py "$out/latest.log" *> "$out/checker.log"
            $checker=$LASTEXITCODE
        }
        $result.runs+=[ordered]@{name=$group.name;exit_code=$code;checker_exit=$checker;started=$begin.ToString('o');finished=(Get-Date -Format o)}
        $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$root/result.json"
        Remove-Owned "$export/build/gametest/world"
    }
    $result.status=if(@($result.runs | Where-Object { $_.exit_code -ne 0 }).Count) { 'FAIL' } else { 'COMPLETED' }
} catch { $result.status='FAIL'; $result.error=$_.Exception.ToString() }
finally {
    Set-Location $workspace
    try {
        if($created) { Remove-Owned $export }
        if($archived) {
            if([IO.Path]::GetDirectoryName($archive) -ne [IO.Path]::GetFullPath($temp)) { throw 'Archive target changed' }
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
    $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$root/result.json"
}
if($result.status -eq 'FAIL') { exit 1 }
