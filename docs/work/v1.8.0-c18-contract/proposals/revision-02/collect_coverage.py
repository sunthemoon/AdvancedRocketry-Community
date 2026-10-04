import csv
import fnmatch
import hashlib
import json
from collections import Counter
from pathlib import Path

ROOT = Path('D:/GitHub/AdvancedRocketry-Community')
OUT = Path(__file__).parent
UPSTREAM = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c14-189c61a748584cab97c5881f8bdc1251/upstream/ar')

def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as source:
        return list(csv.DictReader(source))

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def write_csv(name, entries):
    with (OUT / name).open('w', encoding='utf-8', newline='') as target:
        writer = csv.DictWriter(target, fieldnames=list(entries[0]))
        writer.writeheader()
        writer.writerows(entries)

advancements = dict(zip(
    'root blockpresser rollin electrifying feeltheheat spindoctor crystalline dilithium holographic suitedup warp beer moonlanding onesmallstep wenttothemoon givingitallshesgot flightofpheonix'.split(),
    'root block_press rolling electrolysis arc_furnace lathe crystallizer dilithium formed_multiblock suited_up warp_core seat_on_tnt moon_landing one_small_step moon_surface_visit station_warp first_station_warp'.split()))
objects = {
    'block:airlock_door': ('airlock_door', '3.2'),
    'block:observatory': ('observatory', '6.1'),
    'block:oxygenCharger': ('gas_charge_pad', '4'),
    'block:oxygenDetection': ('atmosphere_detector', '3.1'),
    'block:oxygenScrubber': ('co2_scrubber', '3.1'),
    'block:pipeSealer': ('pipe_seal', '3.2'),
    'block:planetAnalyser': ('astrobody_data_processor', '6.1'),
    'block:suitWorkStation': ('suit_workstation', '5.1'),
    'block:thermiteTorch': ('thermite_torch / thermite_wall_torch', '3.2'),
    'block:unlitTorch': ('unlit_torch / unlit_wall_torch', '3.2'),
    'command:root/addtorch': ('tag:combustion_torches', '3.2'),
    'enchantment:spacebreathing': ('enchantment:space_breathing', '5.1'),
    'entity:ARSpaceElevatorCapsule': ('client_effect:elevator_capsule', '7.1'),
    'asm_rule:GravityHandler.applyGravity(living)': ('Forge ENTITY_GRAVITY transient ARCE modifier', '3.3'),
    'item:atmAnalyser': ('atmosphere_analyzer', '3.1'),
    'item:basicLaserGun': ('basic_laser_gun', '5.3'),
    'item:carbonScrubberCartridge': ('carbon_scrubber_cartridge', '3.1'),
    'item:jackHammer': ('jackhammer', '5.3'),
    'item:jetPack': ('jetpack', '5.2'),
    'item:sealDetector': ('seal_detector', '3.1'),
    'item:smallAirlockDoor': ('airlock_door', '3.2'),
    'item:thermite': ('thermite / tag:dusts/thermite', '3.2'),
    'material:Thermite': ('thermite / tag:dusts/thermite', '3.2'),
    'keybinding:toggleJetpack': ('key:jetpack_toggle -> bounded toggle/cycle intent', '5.2'),
    'packet:PacketLaserGun': ('C2S bounded laser-use intent; no target coordinates', '5.3'),
}
events = {
    'ClientProxy.modelBakeEvent(ModelBakeEvent)': ('physical-client approved OBJ/MTL bake', '7.1'),
    'PlanetEventHandler.blockPlacedEvent(PlaceEvent)': ('combustion_torches placement/invalidation rules', '3.2'),
    'PlanetEventHandler.blockRightClicked(RightClickBlock)': ('combustion_fire_starters server adjacent-cell validation', '3.2'),
    'PlanetEventHandler.CheckSpawn(CheckSpawn)': ('bounded actor atmosphere spawn admission', '3.3'),
    'PlanetEventHandler.fallEvent(LivingFallEvent)': ('ARCE resolved gravity factor once / padded boots', '3.3 / 5.2'),
    'PlanetEventHandler.fogColor(RenderFogEvent)': ('client profile-density fog / anti_fog_visor', '5.2'),
    'PlanetEventHandler.playerTick(LivingUpdateEvent)': ('finite underwater oxygen / safe authorized adrift return', '3.3'),
    'PlanetEventHandler.sleepEvent(PlayerSleepInBedEvent)': ('server breathability sleep admission', '3.3'),
    'RenderComponents.renderPostSpecial(Post)': ('bounded client equipment attachment render', '7.1'),
    'RocketEventHandler.onScreenRender(Post)': ('authoritative snapshot rocket HUD', '7.1 / 7.2'),
}
settings = {
    'CATEGORY_GENERAL.jetPackForce': ('COMMON equipment.jetPackForce 0.05..4', '5.2'),
    'CATEGORY_GENERAL.lowGravityBoots': ('COMMON equipment.lowGravityBoots', '5.2'),
    'CLIENT.advancedVFX': ('CLIENT effects.advancedVisuals', '7.1 / 7.2'),
    'CLIENT.EnableAtmosphericNausea': ('CLIENT effects.atmosphericNausea', '5.2'),
    'CLIENT.overworldSkyOverride': ('CLIENT sky.overworldOverride', '7.2'),
    'CLIENT.PlanetSkyOverride': ('CLIENT sky.planetOverride', '7.2'),
    'CLIENT.StationSkyOverride': ('CLIENT sky.stationOverride', '7.2'),
    'OXYGEN.dropExtinguishedTorches': ('COMMON lifeSupport.dropExtinguishedTorches', '3.2'),
    'OXYGEN.entityAtmBypass': ('entity_type tag:atmosphere_bypass', '3.3'),
    'OXYGEN.scrubberRequiresCartrige': ('COMMON lifeSupport.scrubberRequiresCartridge', '3.1 / 9 D5'),
    'OXYGEN.spaceSuitO2Buffer': ('COMMON equipment.spaceSuitO2Buffer bounded built-in units; D1', '5.1 / 9 D1'),
    'OXYGEN.suitTankCapacity': ('COMMON equipment.suitTankCapacity bounded tank multiplier; D1', '5.1 / 9 D1'),
    'OXYGEN.torchBlocks': ('block tag:combustion_torches', '3.2'),
    'OXYGEN.vacuumDamage': ('COMMON lifeSupport.vacuumDamage 0..20', '3.3'),
    'PLANET.allowPlanetRespawn': ('COMMON lifeSupport.allowPlanetRespawn', '3.3'),
    'PLANET.forcePlanetRespawn': ('COMMON lifeSupport.forcePlanetRespawn', '3.3'),
}
sound_names = dict(zip(
    'airHissLoop basicLaserGun combustionRocket ElectricShockSmall gravityOhhh laserDrill lathe MachineLarge railgunBang rollingMachine'.split(),
    'air_hiss_loop basic_laser_gun combustion_rocket electric_shock_small gravity_controller laser_drill lathe machine_large railgun_bang rolling_machine'.split()))
upgrade_names = 'hover_upgrade flight_speed_upgrade bionic_leg_upgrade padded_landing_boots anti_fog_visor earthbright_visor'.split()
tank_names = 'low_pressure_tank pressure_tank high_pressure_tank super_high_pressure_tank'.split()

coverage = []
for row in rows(ROOT / 'docs/work/v1.8.0-content-ledger.csv'):
    if not row['plan'].startswith('C18'):
        continue
    unit = row['unit_id']
    if unit.startswith('advancement:'):
        target, section = 'advancedrocketrycommunity:classic/' + advancements[unit.split(':', 1)[1]], '6.2'
    elif unit.startswith('item_variant:itemUpgrade/'):
        target, section = upgrade_names[int(unit.rsplit('/', 1)[1])], '5.1 / 5.2'
    elif unit.startswith('item_variant:pressureTank/'):
        target, section = tank_names[int(unit.rsplit('/', 1)[1])], '5.1 / 9 D1'
    elif unit.startswith('event:'):
        target, section = events[unit.split(':', 1)[1]]
    elif unit.startswith('sound_event:'):
        target, section = 'sound_event:' + sound_names[unit.split(':', 1)[1]], '7.1 / 7.3'
    elif unit.startswith('config:'):
        key = unit.split(':', 1)[1]
        if key in settings:
            target, section = settings[key]
        else:
            panels = {'atmBar': 'environment', 'hydrogenBar': 'hydrogen', 'oxygenBar': 'oxygen', 'suitPanel': 'suit'}
            short = key.removeprefix('CLIENT.')
            for old, panel in panels.items():
                if short.startswith(old):
                    tail = short.removeprefix(old)
                    target = 'CLIENT hud.' + panel + '.' + {'X': 'x', 'Y': 'y', 'ModeX': 'anchorX', 'ModeY': 'anchorY'}[tail]
                    section = '7.2'
                    break
            else:
                raise AssertionError(('unmapped config', unit))
    else:
        target, section = objects[unit]
    coverage.append({
        'unit_id': unit, 'batch': row['plan'], 'proposed_target': target,
        'contract_sections': section,
        'migration': 'ADR-066 sections 2, ' + section,
        'verification': 'ADR-066 section 8; actual slice evidence required',
        'provenance': 'ADR-061 / ADR-066 sections 1 and 7.3; NEW or separately approved import only',
        'player_limitations': 'ADR-066 owning section; accepted ADR-062 deferrals unchanged',
        'status': 'PROPOSED; ledger remains PLANNED',
    })
assert len(coverage) == 104
assert len({row['unit_id'] for row in coverage}) == 104
write_csv('unit-coverage.csv', coverage)

asset_rules = rows(ROOT / 'docs/work/v1.8.0-asset-plan.csv')
asset_coverage = []
for asset in rows(ROOT / 'legacy-manifest/assets.csv'):
    short = asset['source_path'].removeprefix('src/main/resources/assets/advancedrocketry/')
    rule = next(rule for rule in asset_rules if fnmatch.fnmatchcase(short, rule['pattern']))
    if rule['plan'] != 'C18d':
        continue
    asset_coverage.append({
        'source_path': asset['source_path'], 'source_sha256': asset['sha256'],
        'handling': rule['handling'], 'owning_units': rule['units'],
        'rule_order': rule['order'], 'rule_pattern': rule['pattern'],
        'terminal_evidence': 'ADR-066 section 7.3; per-file import or origin/replacement decision still required',
    })
write_csv('asset-coverage.csv', asset_coverage)

java_manifest = {row['path']: row['sha256'] for row in rows(ROOT / 'legacy-manifest/java-files.csv')}
asset_manifest = {row['source_path']: row['sha256'] for row in rows(ROOT / 'legacy-manifest/assets.csv')}
fact_sources = [
    'item/components/ItemJetpack.java', 'item/components/ItemPressureTank.java',
    'item/components/ItemUpgrade.java', 'tile/TileSuitWorkStation.java',
    'tile/atmosphere/TileCO2Scrubber.java', 'tile/atmosphere/TileOxygenVent.java',
    'tile/atmosphere/TileAtmosphereDetector.java', 'tile/atmosphere/TileGasChargePad.java',
    'tile/multiblock/TileObservatory.java', 'tile/multiblock/TileAstrobodyDataProcessor.java',
    'event/PlanetEventHandler.java', 'api/ARConfiguration.java',
    'enchant/EnchantmentSpaceBreathing.java', 'item/ItemJackHammer.java',
    'entity/EntityRocket.java', 'tile/station/TileWarpController.java',
    'advancements/ARAdvancements.java',
]
source_entries = []
for relative in fact_sources:
    path = 'src/main/java/zmaster587/advancedRocketry/' + relative
    actual = digest(UPSTREAM / path)
    assert actual == java_manifest[path], path
    source_entries.append({'path': path, 'sha256': actual, 'manifest_match': True})
for name in advancements:
    path = 'src/main/resources/assets/advancedrocketry/advancements/normal/' + name + '.json'
    actual = digest(UPSTREAM / path)
    assert actual == asset_manifest[path], path
    source_entries.append({'path': path, 'sha256': actual, 'manifest_match': True})
(OUT / 'source-files.json').write_text(json.dumps({
    'upstream_commit': 'c5cd5af62fc07cd4e0d24f06a16033f181c47c04',
    'source_files': source_entries,
    'authorities': [{'path': path, 'sha256': digest(ROOT / path)} for path in [
        'docs/decisions/ADR-025-SUIT-OXYGEN-PROVIDERS.md',
        'docs/decisions/ADR-034-PLANETARY-EXPOSURE-AND-PROTECTION.md',
        'docs/decisions/ADR-037-PLANETARY-DISCOVERY-AND-RESEARCH.md',
        'docs/decisions/ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md',
        'docs/decisions/ADR-057-BLACK-HOLE-GENERATOR.md',
        'docs/decisions/ADR-059-SPACE-ELEVATOR-LOGISTICS.md',
        'docs/decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md',
        'docs/decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md',
        'docs/decisions/ADR-064-CLASSIC-MACHINES-FLUIDS-AND-COMPONENTS.md',
        'docs/work/v1.8.0-content-ledger.csv', 'docs/work/v1.8.0-asset-plan.csv',
    ]],
}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
summary = {
    'ledger_units': len(coverage), 'ledger_batches': dict(Counter(row['batch'] for row in coverage)),
    'c18d_assets': len(asset_coverage), 'c18d_asset_handling': dict(Counter(row['handling'] for row in asset_coverage)),
    'matched_upstream_source_files': len(source_entries),
    'runtime_edits': 0, 'gradle_or_native_commands': 0,
}
(OUT / 'coverage-summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
print(json.dumps(summary))
