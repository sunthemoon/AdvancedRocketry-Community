$ErrorActionPreference = 'Stop'
$workspace = 'D:\GitHub\arce-v180-w225'
$evidence = "$workspace\build\w225-fix3"
Set-Location -LiteralPath $workspace
$order = 'endgame_destruction_blast,w164_adapter_fault,station_warp_killswitch,jackhammer_native,w01g_partial_resources_root,beacon_native_reload,propulsion_nuclear,w05_planks,machine_menu_channel,bay_copies_riding,named_pad_occupancy,research_debit,classic_milestone_tree,fluid_fuel_loader_escrow'
$alone = 'endgame_destruction_blast,beacon_native_reload,propulsion_bipropellant,propulsion_advanced_bipropellant,propulsion_basic,propulsion_mono,propulsion_waiver,propulsion_nuclear,fluid_fuel_loader_escrow'
$runs = @(@{Label='alone-complete'; Order=$alone}, @{Label='green-8'; Order=$order},
    @{Label='green-9'; Order=$order}, @{Label='green-10'; Order=$order}, @{Label='full-final'; Order='ALL'})
foreach ($run in $runs) {
    Start-Sleep -Seconds 90
    & pwsh -NoProfile -File "$evidence\run-selected.ps1" -Label $run.Label -Order $run.Order
    if ($LASTEXITCODE -ne 0) { throw "Runner failed: $($run.Label)" }
    if ((Get-Content "$evidence\$($run.Label)\native-exit.txt") -ne '0' -or
            (Get-Content "$evidence\$($run.Label)\checker-exit.txt") -ne '0') { throw "Native or checker failed: $($run.Label)" }
}
Set-Content "$evidence\final-driver-exit.txt" '0'
