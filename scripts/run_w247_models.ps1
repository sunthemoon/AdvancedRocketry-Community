param(
    [Parameter(Mandatory=$true)][string]$Commit,
    [Parameter(Mandatory=$true)][ValidateSet('before','after','full')][string]$Stage
)
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath("$PSScriptRoot/..")
$root="D:/GitHub/ARCE-Task-Evidence/root-queue/W247/$Stage"
$lock='D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm'
$token="W247-$Stage pid=$PID $([guid]::NewGuid())"
$owned=$false
$result=@{source_commit=$Commit;stage=$Stage;started=(Get-Date -Format o);runs=@();status='RUNNING'}
if(Test-Path -LiteralPath $root) { throw 'Evidence directory already exists' }
New-Item -ItemType Directory -Path $root | Out-Null
function Remove-World {
    $world=[IO.Path]::GetFullPath("$workspace/build/gametest/world")
    if($world -ne 'D:\GitHub\arce-v180-w247\build\gametest\world') { throw 'World cleanup escaped W247' }
    if(Test-Path -LiteralPath $world) { Remove-Item -LiteralPath $world -Recurse -Force }
}
try {
    Set-Location $workspace
    if((git branch --show-current) -ne 'codex/v1.8.0-w247' -or (git rev-parse HEAD) -ne $Commit -or (git status --porcelain)) {
        throw 'Source pin must be clean on the assigned branch'
    }
    $env:JAVA_HOME='C:/Program Files/Java/jdk-17.0.7'
    $env:JAVA_TOOL_OPTIONS=$null
    $k4=@([regex]::Matches((Get-Content -Raw 'src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/K4ModelGameTests.java'),'batch\s*=\s*"([^"]+)"') |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    if($k4.Count -ne 12) { throw 'Unexpected K4 batch inventory' }
    $groups=@(@{name='k4';batches=$k4},
              @{name='mixed';batches=@($k4)+@('station_warp_deop','w133_escrow_fill_launch')},
              @{name='k3';batches=@('w236_analyser_model','w236_observatory_model','w236_solar_model','w236_beacon_model')})
    if($Stage -eq 'full') { $groups=@(@{name='all';batches=@()}) }
    foreach($group in $groups) {
        $out="$root/$($group.name)"
        New-Item -ItemType Directory -Path $out | Out-Null
        $deadline=(Get-Date).AddHours(3)
        try {
            while($true) {
                & cmd.exe /d /c "mkdir `"$lock`" 2>NUL"
                if($LASTEXITCODE -eq 0) { $owned=$true; break }
                if((Get-Date) -gt $deadline -or ((Get-Date)-(Get-Item -LiteralPath $lock).CreationTime).TotalHours -gt 3) {
                    throw 'Heavy lock older than three hours or wait expired; left untouched'
                }
                Start-Sleep 60
            }
            Set-Content -LiteralPath "$lock/owner.txt" "$token $(Get-Date -Format o)"
            foreach($drive in 'C','D') { if((Get-PSDrive $drive).Free -lt 10GB) { throw "Disk $drive below 10 GiB" } }
            if((git rev-parse HEAD) -ne $Commit -or (git status --porcelain)) { throw 'Source changed while waiting for heavy lock' }
            $args=@('--offline','--no-daemon','--no-parallel','--max-workers=1','--console=plain','runGameTestServer')
            if($Stage -ne 'full') {
                $args+=@('-I','scripts/w247_profile.init.gradle',"-Pw247Batches=$($group.batches -join ',')","-Pw247Profile=$out/server.jfr")
            }
            $args | ConvertTo-Json | Set-Content -LiteralPath "$out/command.json"
            # Windows PowerShell 5 treats native stderr as an ErrorRecord. Preserve the native exit code as well.
            $ErrorActionPreference='Continue'
            & .\gradlew.bat @args *> "$out/gradle.log"
            $code=$LASTEXITCODE
            $ErrorActionPreference='Stop'
            foreach($name in 'latest.log','debug.log') {
                if(Test-Path -LiteralPath "build/gametest/logs/$name") {
                    Copy-Item -LiteralPath "build/gametest/logs/$name" -Destination "$out/$name"
                }
            }
            & python -X utf8 -B scripts/check_gametest_log.py "$out/latest.log" *> "$out/checker.log"
            $checker=$LASTEXITCODE
            $result.runs+=@{name=$group.name;batches=$group.batches;exit_code=$code;checker_exit=$checker;finished=(Get-Date -Format o)}
            $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$root/result.json"
        } finally {
            try { if($owned) { Remove-World } }
            catch { $result.cleanup_error=$_.Exception.ToString() }
            if($owned) {
                $owner=Get-Content -LiteralPath "$lock/owner.txt" -ErrorAction SilentlyContinue | Select-Object -First 1
                if($owner -and $owner.StartsWith($token)) {
                    if([IO.Path]::GetFullPath($lock) -ne 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm') { throw 'Lock cleanup target changed' }
                    Remove-Item -LiteralPath $lock -Recurse -Force
                }
                $owned=$false
            }
        }
        if($group -ne $groups[-1]) { Start-Sleep 90 }
    }
    $result.status=if(@($result.runs | Where-Object { $_.exit_code -ne 0 }).Count) { 'FAIL' } else { 'COMPLETED' }
} catch { $result.status='FAIL'; $result.error=$_.Exception.ToString() }
finally {
    $result.finished=Get-Date -Format o
    $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$root/result.json"
}
if($result.status -eq 'FAIL') { exit 1 }
