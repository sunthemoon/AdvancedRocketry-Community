param(
    [Parameter(Mandatory=$true)][string]$Commit,
    [Parameter(Mandatory=$true)][string]$Label,
    [ValidateSet('Native','Batches')][string]$Mode='Native'
)
$ErrorActionPreference='Stop'
$workspace=(Resolve-Path "$PSScriptRoot/..").Path
$temp='D:/GitHub/ARCE-Task-Evidence/Temp'
$out="D:/GitHub/ARCE-Task-Evidence/root-queue/W210/$Label"
if($Label -notmatch '^[a-z][a-z0-9-]+$') { throw 'Invalid evidence label' }
$export="$temp/W210-$Label-export"; $archive="$temp/W210-$Label.zip"
$lock='D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm'
$token="W210-$Label pid=$PID $([guid]::NewGuid())"
$owned=$false; $created=$false; $archived=$false; $live=$false
$result=@{source_commit=$Commit;mode=$Mode;status='RUNNING';started=(Get-Date -Format o);runs=@()}
if((Test-Path $out) -or (Test-Path $export) -or (Test-Path $archive)) { throw 'Evidence/export exists' }
New-Item -ItemType Directory -Path $out | Out-Null
function Persist { $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$out/driver.json" }
function DiskCheck {
    foreach($drive in 'C','D') { if((Get-PSDrive $drive).Free -lt 10GB) { throw "Disk $drive below 10 GiB" } }
}
function TakeLock {
    $deadline=(Get-Date).AddHours(3)
    while($true) {
        & cmd.exe /d /c "mkdir `"$lock`" 2>NUL"
        if($LASTEXITCODE -eq 0) { $script:owned=$true; break }
        if((Get-Date) -gt $deadline -or ((Get-Date)-(Get-Item -LiteralPath $lock).CreationTime).TotalHours -gt 3) {
            throw 'Lock older than three hours or wait expired; not removed'
        }
        $result.status='WAITING_LOCK'; Persist; Start-Sleep 60
    }
    $script:ownerRecord="$token $(Get-Date -Format o)"
    Set-Content -LiteralPath "$lock/owner.txt" $ownerRecord
    DiskCheck
}
function ReleaseLock {
    if(!$owned -or $live) { return }
    $owner=Get-Content -LiteralPath "$lock/owner.txt" -ErrorAction SilentlyContinue | Select-Object -First 1
    if($owner -and $owner.StartsWith($token)) {
        if([IO.Path]::GetFullPath($lock) -ne 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm') { throw 'Lock target changed' }
        Remove-Item -LiteralPath $lock -Recurse -Force
    }
    $script:owned=$false
}
function Cleanup([string]$Path) {
    $resolved=[IO.Path]::GetFullPath($Path)
    $prefix=[IO.Path]::GetFullPath($export)+[IO.Path]::DirectorySeparatorChar
    if($resolved -ne [IO.Path]::GetFullPath($export) -and !$resolved.StartsWith($prefix)) { throw 'Cleanup escaped export' }
    if((Get-Content -LiteralPath "$export/.w210-owned") -ne $token) { throw 'Export ownership changed' }
    if(Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
try {
    Set-Location $workspace
    if((git rev-parse HEAD) -ne $Commit -or (git status --porcelain)) { throw 'Exact clean source pin required' }
    & git archive --format=zip "--output=$archive" $Commit
    if($LASTEXITCODE -ne 0) { throw 'Export failed' }; $archived=$true
    Expand-Archive -LiteralPath $archive -DestinationPath $export; $created=$true
    Set-Content -LiteralPath "$export/.w210-owned" $token
    $env:JAVA_HOME='C:/Program Files/Java/jdk-17.0.7'; $env:JAVA_TOOL_OPTIONS=''
    $gradle=@('--offline','--no-daemon','--no-parallel','--max-workers=1','--console=plain')
    Set-Location $export
    if($Mode -eq 'Native') {
        TakeLock; $result.status='BUILDING'; Persist
        @{
            time=(Get-Date -Format o)
            cpu=(Get-CimInstance Win32_Processor | Select-Object Name,NumberOfCores,NumberOfLogicalProcessors,LoadPercentage)
            os=(Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,TotalVisibleMemorySize,FreePhysicalMemory)
            disks=(Get-PSDrive C,D | Select-Object Name,Free)
            processes=(Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^(java|javaw|codex|claude)(\.exe)?$'} |
                Select-Object ProcessId,ParentProcessId,Name,WorkingSetSize)
        } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$out/host-before.json"
        & .\gradlew.bat @gradle jar *> "$out/production-build.log"
        if($LASTEXITCODE -ne 0) { throw 'Production jar failed' }
        $production="$export/production.jar"
        Copy-Item -LiteralPath 'build/libs/advancedrocketry-community-1.20.1-1.8.0-dev.jar' -Destination $production
        foreach($relative in @('rocket/persistence/W208JournalFixture.java','rocket/persistence/W208WriterControl.java',
                'rocket/server/W208NativeAvailability.java','rocket/server/W208WriterTiming.java')) {
            $package='io/github/sunthemoon/advancedrocketrycommunity'
            $from=[IO.Path]::GetFullPath("$export/src/test/java/$package/$relative")
            $to=[IO.Path]::GetFullPath("$export/src/main/java/$package/$relative")
            $prefix=[IO.Path]::GetFullPath($export)+[IO.Path]::DirectorySeparatorChar
            if(!$from.StartsWith($prefix) -or !$to.StartsWith($prefix)) { throw 'Diagnostic move escaped export' }
            Move-Item -LiteralPath $from -Destination $to
        }
        & python -B scripts/w210_admission_profile.py instrument $export $out
        if($LASTEXITCODE -ne 0) { throw 'Diagnostic instrumentation failed' }
        & .\gradlew.bat @gradle jar *> "$out/diagnostic-build.log"
        if($LASTEXITCODE -ne 0) { throw 'Diagnostic jar failed' }
        $diagnostic="$export/build/libs/advancedrocketry-community-1.20.1-1.8.0-dev.jar"
        $result.status='MEASURING'; Persist
        foreach($case in @('small-quiet','near-quiet')) {
            DiskCheck; $live=$true
            & python -B scripts/w210_admission_profile.py --case $case --jar $diagnostic --production-jar $production `
                --libraries 'D:/GitHub/ARCE-Task-Evidence/v1.8.0/guard-observation-04/native-04/server/libraries' `
                --evidence $out --source-commit $Commit --lock-owner $ownerRecord *> "$out/$case-console.log"
            $code=$LASTEXITCODE; $result.runs+=@{case=$case;exit_code=$code}; Persist
            $caseResult=Get-Content -Raw "$out/$case/result.json" | ConvertFrom-Json
            if($caseResult.live_process) { throw 'Live native process: world and lock retained' }
            $live=$false
            if($code -ne 0) { throw "Native observation failed: $case" }
        }
        & python -B scripts/w208_summary.py $out *> "$out/summary.log"
        if($LASTEXITCODE -ne 0) { throw 'Native summary failed' }
    } else {
        $classes=Get-ChildItem 'src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket' -Recurse -Filter '*GameTests.java' |
            Where-Object {$_.Name -match '^(Sealed|RocketTransactionProof)'}
        $names=@($classes | ForEach-Object {[regex]::Matches((Get-Content -Raw $_.FullName),'batch\s*=\s*"([^"]+)"')} |
            ForEach-Object {$_.Groups[1].Value} | Sort-Object -Unique)
        if(!$names.Count) { throw 'No sealed/journal batches found' }
        $result.batches=$names
        $env:JAVA_TOOL_OPTIONS="-Dadvancedrocketrycommunity.gameTestBatches=$($names -join ',')"
        for($iteration=1; $iteration -le 3; $iteration++) {
            try {
                TakeLock; $result.status="BATCH-$iteration"; Persist
                & .\gradlew.bat @gradle runGameTestServer *> "$out/batch-$iteration.log"
                $code=$LASTEXITCODE
                Copy-Item -LiteralPath 'build/gametest/logs/latest.log' -Destination "$out/batch-$iteration-latest.log"
                $result.runs+=@{iteration=$iteration;exit_code=$code}; Persist
                if($code -ne 0) { throw "Sealed/journal batches failed: $iteration" }
            } finally { Cleanup "$export/build/gametest/world"; ReleaseLock }
            if($iteration -lt 3) { Start-Sleep 90 }
        }
    }
    $result.status='PASS'
} catch { $result.status='FAILED'; $result.error=$_.Exception.ToString() }
finally {
    Set-Location $workspace
    try {
        if($created -and !$live) { Cleanup $export }
        if($archived -and !$live) {
            if([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($archive)) -ne [IO.Path]::GetFullPath($temp)) { throw 'Archive target changed' }
            Remove-Item -LiteralPath $archive
        }
    } catch { $result.cleanup_error=$_.Exception.ToString() }
    ReleaseLock
    $result.live_process=$live; $result.finished=Get-Date -Format o; Persist
}
