param([Parameter(Mandatory=$true)][string]$Label, [Parameter(Mandatory=$true)][string]$Order)
$ErrorActionPreference = 'Stop'
$workspace = 'D:\GitHub\arce-v180-w225'
$evidence = "$workspace\build\w225-fix3"
if ($Label -notmatch '^[a-z0-9-]+$') { throw 'Invalid label' }
$result = "$evidence\$Label"
New-Item -ItemType Directory -Path $result -Force | Out-Null
Set-Location -LiteralPath $workspace
if ($Label -eq 'full') {
    # The first green ran the initial cleanup implementation. Repeat once more on the final join-capture code.
    $repeat = 'endgame_destruction_blast,w164_adapter_fault,station_warp_killswitch,jackhammer_native,w01g_partial_resources_root,beacon_native_reload,propulsion_nuclear,w05_planks,machine_menu_channel,bay_copies_riding,named_pad_occupancy,research_debit,classic_milestone_tree,fluid_fuel_loader_escrow'
    & pwsh -NoProfile -File "$evidence\run-selected.ps1" -Label green-4 -Order $repeat
    if ($LASTEXITCODE -ne 0 -or (Get-Content "$evidence\green-4\native-exit.txt") -ne '0' -or
            (Get-Content "$evidence\green-4\checker-exit.txt") -ne '0') { throw 'Final ordered repetition failed' }
    Start-Sleep -Seconds 90
    $alone = 'endgame_destruction_blast,beacon_native_reload,propulsion_bipropellant,propulsion_advanced_bipropellant,propulsion_basic,propulsion_mono,propulsion_waiver,propulsion_nuclear,fluid_fuel_loader_escrow'
    & pwsh -NoProfile -File "$evidence\run-selected.ps1" -Label alone -Order $alone
    if ($LASTEXITCODE -ne 0 -or (Get-Content "$evidence\alone\native-exit.txt") -ne '0' -or
            (Get-Content "$evidence\alone\checker-exit.txt") -ne '0') { throw 'Isolated changed-batch run failed' }
    Start-Sleep -Seconds 90
}
$lock = 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm'
$token = "W225-FIX3-$Label pid=$PID $([guid]::NewGuid())"
$deadline = (Get-Date).AddHours(3)
while ($true) {
    if ((Get-Date) -gt $deadline) { throw 'Timed out waiting for heavy-JVM lock' }
    if ((Test-Path -LiteralPath $lock) -and ((Get-Date) - (Get-Item -LiteralPath $lock).CreationTime).TotalHours -gt 3) {
        throw 'Heavy-JVM lock is older than three hours; not removing it'
    }
    cmd /c mkdir "$lock" 2>$null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Seconds 60
}
try {
    Set-Content -LiteralPath "$lock\owner.txt" -Value "$token $(Get-Date -Format o)"
    Get-PSDrive C,D | Select-Object Name,Free | ConvertTo-Json | Set-Content "$result\disk-before.json"
    foreach ($drive in Get-PSDrive C,D) { if ($drive.Free -lt 10GB) { throw 'Insufficient free space' } }
    git rev-parse HEAD > "$result\source-sha.txt"
    git status --porcelain=v1 --untracked-files=all > "$result\source-status.txt"
    git diff > "$result\source.patch"
    $arguments = if ($Order -eq 'ALL') { @('--offline','--no-daemon','--console=plain','runGameTestServer') }
        else { @('--offline','--no-daemon','--console=plain','-I','scripts/ci_gametest/init.gradle',
        '-I',"$evidence\trace.gradle",'-PciShard=1',"-PtraceOrder=$Order",'runGameTestServer') }
    '.\gradlew.bat ' + ($arguments -join ' ') | Set-Content "$result\command.txt"
    & .\gradlew.bat @arguments *> "$result\gradle.log"
    $native = $LASTEXITCODE
    Set-Content "$result\native-exit.txt" $native
    New-Item -ItemType Directory "$result\logs" -Force | Out-Null
    Copy-Item -LiteralPath 'build/gametest/logs/latest.log' -Destination "$result\logs\latest.log"
    Copy-Item -LiteralPath 'build/ci-gametest-result/result.json' -Destination "$result\result.json" -ErrorAction SilentlyContinue
    if ($Order -eq 'ALL') { python -B scripts/check_gametest_log.py "$result\logs\latest.log" *> "$result\log-check.log" }
    else { python -B "$evidence\check_selected.py" $result $Order *> "$result\log-check.log" }
    Set-Content "$result\checker-exit.txt" $LASTEXITCODE
} finally {
    $world = [IO.Path]::GetFullPath("$workspace\build\gametest\world")
    if ($world -ne 'D:\GitHub\arce-v180-w225\build\gametest\world') { throw 'Unexpected cleanup target' }
    if (Test-Path -LiteralPath $world) { Remove-Item -LiteralPath $world -Recurse -Force }
    $owner = Get-Content "$lock\owner.txt" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($owner -and $owner.StartsWith($token)) { Remove-Item -LiteralPath $lock -Recurse -Force }
}
