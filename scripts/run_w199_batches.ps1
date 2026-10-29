param([Parameter(Mandatory=$true)][string]$Commit, [string]$Label='batches')
$ErrorActionPreference='Stop'
$workspace=(Resolve-Path "$PSScriptRoot/..").Path
if($Label -notmatch '^batches(?:-[a-z0-9-]+)?$') { throw 'Invalid evidence label' }
$root="D:/GitHub/ARCE-Task-Evidence/root-queue/W199/$Label"
$temp='D:/GitHub/ARCE-Task-Evidence/Temp'
$export="$temp/W199-$Label-export"; $archive="$temp/W199-$Label.zip"
$lock='D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm'
$token="W199-batches pid=$PID $([guid]::NewGuid())"
$owned=$false; $created=$false; $archived=$false
$result=@{source_commit=$Commit;started=(Get-Date -Format o);runs=@();status='RUNNING'}
function Remove-Owned([string]$Path) {
    $resolved=[IO.Path]::GetFullPath($Path)
    $prefix=[IO.Path]::GetFullPath($export)+[IO.Path]::DirectorySeparatorChar
    if($resolved -ne [IO.Path]::GetFullPath($export) -and !$resolved.StartsWith($prefix)) { throw 'Cleanup escaped export' }
    if((Get-Content -LiteralPath "$export/.w199-owned") -ne $token) { throw 'Export ownership changed' }
    if(Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
if((Test-Path $root) -or (Test-Path $export) -or (Test-Path $archive)) { throw 'Output already exists' }
New-Item -ItemType Directory -Path $root | Out-Null
try {
    Set-Location $workspace
    if((git rev-parse HEAD) -ne $Commit -or (git status --porcelain)) { throw 'Source pin is not clean' }
    & git archive --format=zip "--output=$archive" $Commit
    if($LASTEXITCODE -ne 0) { throw 'Export failed' }; $archived=$true
    Expand-Archive -LiteralPath $archive -DestinationPath $export
    $created=$true; Set-Content -LiteralPath "$export/.w199-owned" $token
    $classes=Get-ChildItem "$export/src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server" -Filter '*GameTests.java' |
        Where-Object { $_.Name -match '^(RocketI6|Sealed)' }
    $batches=@($classes | ForEach-Object { [regex]::Matches((Get-Content -Raw $_.FullName),'batch\s*=\s*"([^"]+)"') } |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    if(!$batches.Count) { throw 'No batches selected' }
    $result.batches=$batches
    $env:JAVA_HOME='C:/Program Files/Java/jdk-17.0.7'
    $env:JAVA_TOOL_OPTIONS="-Dadvancedrocketrycommunity.gameTestBatches=$($batches -join ',')"
    Set-Location $export
    for($iteration=1; $iteration -le 3; $iteration++) {
        $out="$root/run-$iteration"; New-Item -ItemType Directory -Path $out | Out-Null
        $deadline=(Get-Date).AddHours(3)
        try {
            while($true) {
                & cmd.exe /d /c "mkdir `"$lock`" 2>NUL"
                if($LASTEXITCODE -eq 0) { $owned=$true; break }
                if((Get-Date) -gt $deadline -or ((Get-Date)-(Get-Item -LiteralPath $lock).CreationTime).TotalHours -gt 3) {
                    throw 'Lock older than three hours or wait expired; not removed'
                }
                Start-Sleep 60
            }
            Set-Content -LiteralPath "$lock/owner.txt" "$token $(Get-Date -Format o)"
            foreach($drive in 'C','D') { if((Get-PSDrive $drive).Free -lt 10GB) { throw "Disk $drive below 10 GiB" } }
            & .\gradlew.bat --offline --no-daemon --no-parallel --max-workers=1 --console=plain runGameTestServer *> "$out/gradle.log"
            $code=$LASTEXITCODE
            Copy-Item -LiteralPath 'build/gametest/logs/latest.log' -Destination "$out/latest.log"
            $result.runs+=@{iteration=$iteration;exit_code=$code;finished=(Get-Date -Format o)}
            if($code -ne 0) { throw "GameTest run $iteration failed" }
        } finally {
            try { if($created) { Remove-Owned "$export/build/gametest/world" } }
            catch { $result.cleanup_error=$_.Exception.ToString() }
            if($owned) {
                $owner=Get-Content -LiteralPath "$lock/owner.txt" -ErrorAction SilentlyContinue | Select-Object -First 1
                if($owner -and $owner.StartsWith($token)) {
                    if([IO.Path]::GetFullPath($lock) -ne 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm') { throw 'Lock target changed' }
                    Remove-Item -LiteralPath $lock -Recurse -Force
                }
                $owned=$false
            }
        }
        if($iteration -lt 3) { Start-Sleep 90 }
    }
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
    } catch { $result.cleanup_error=$_.Exception.ToString() }
    $result.finished=Get-Date -Format o
    $result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$root/result.json"
}
