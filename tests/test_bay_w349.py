"""W349 inventory regressions, based on W348's complete-case and raw-oracle probes.

Only first_start and server I/O are scripted. The complete case uses its real stopped-world
readers, restart checks and snapshot copies, with real gzip player NBT and region records.
"""
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import test_bay_w346 as t

nbt, runner = t.nbt, t.w346
MOON_TERRAIN = t.bay.MOON_ENTITIES.parent / "region"


def package(satellite=t.DATA):
    identity = nbt.Tag(nbt.COMPOUND, dict(schema_version=nbt.Tag(nbt.INT, 1), satellite_id=t.ident(satellite),
                                        owner_id=t.ident(t.OWNER), definition_id=t.string("advancedrocketrycommunity:data_satellite")))
    return nbt.Tag(nbt.COMPOUND, dict(id=t.string("advancedrocketrycommunity:data_satellite_package"),
                                     Count=nbt.Tag(nbt.BYTE, 1), Slot=nbt.Tag(nbt.BYTE, 0),
                                     tag=nbt.Tag(nbt.COMPOUND, dict(SatelliteIdentity=identity))))


def container(satellite=t.DATA):
    return nbt.Tag(nbt.COMPOUND, dict(Items=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [package(satellite)]))))


def add_snapshot_inventory(snapshot, satellite=t.DATA):
    snapshot.value["relative_blocks"].value[1].append(nbt.Tag(nbt.COMPOUND, dict(
        position=nbt.Tag(nbt.INTS, [0, 10, 0]), palette=nbt.Tag(nbt.INT, 3),
        block_entity=nbt.Tag(nbt.COMPOUND, dict(adapter=t.string("advancedrocketrycommunity:vanilla_container_v1"),
                                              data=container(satellite))))))


def terrain(world, directory=Path("region"), satellite=t.DATA):
    # A different region and a negative coordinate: scanning only the fixture's chunk misses this inventory.
    folder = world / directory
    t.region(folder, {(1, 31): []})
    (folder / "r.0.0.mca").rename(folder / "r.1.-1.mca")
    chest = container(satellite)
    chest.value.update(id=t.string("minecraft:chest"), x=nbt.Tag(nbt.INT, 536),
                       y=nbt.Tag(nbt.INT, 2), z=nbt.Tag(nbt.INT, -8))
    nbt.write_chunk(folder, 33, -1, nbt.Tag(nbt.COMPOUND, dict(
        block_entities=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [chest])))))


def inject(world, location, satellite=t.DATA):
    if location in ("Inventory", "EnderItems", "nested_player"):
        folder = world / "playerdata"
        folder.mkdir(exist_ok=True)
        payload = container(satellite) if location == "nested_player" else nbt.Tag(nbt.LIST, (nbt.COMPOUND, [package(satellite)]))
        key = "ForgeCaps" if location == "nested_player" else location
        nbt.write_gzip(folder / f"{t.OWNER}.dat", b"", nbt.Tag(nbt.COMPOUND, {key: payload}))
    elif location in ("terrain", "moon_terrain", "other_dimension"):
        folder = {"terrain": Path("region"), "moon_terrain": MOON_TERRAIN,
                  "other_dimension": Path("DIM-1/region")}[location]
        terrain(world, folder, satellite)
    elif location in ("source_snapshot", "destination_snapshot"):
        path = world / "data" / runner.TRANSFERS_FILE
        name, root = nbt.read_gzip(path)
        add_snapshot_inventory(root.value["data"].value["transfers"].value[1][0].value[location], satellite)
        nbt.write_gzip(path, name, root)
    elif location == "entity_snapshot":
        entity = t.rocket(t.OP, logical=t.SOLAR)
        add_snapshot_inventory(entity.value["RocketEntityData"].value["snapshot"], satellite)
        t.region(world / runner.OVERWORLD_ENTITIES, {(0, 0): [entity]})
    elif location == "entity_inventory":
        entity = container(satellite)
        entity.value["id"] = t.string("minecraft:chest_minecart")
        t.region(world / "DIM-1/entities", {(0, 0): [entity]})
    else:
        raise AssertionError(location)


class CompleteSession:
    def __init__(self, root, location=None, stage=0, satellite=t.DATA):
        self.args = SimpleNamespace(settle_timeout=0, exit_timeout=1)
        self.port = 25565
        self.session, self.evidence = root / "session", root / "evidence"
        self.session.mkdir(parents=True)
        self.evidence.mkdir()
        self.world = self.session / ("world-" + t.CASE)
        self.location, self.stage, self.satellite = location, stage, satellite
        self.seed(0)

    def seed(self, stage):
        t.world(self.world, moon={(0, 0): [t.rocket(t.DEST)]} if stage else None)
        if self.location and stage == self.stage:
            inject(self.world, self.location, self.satellite)

    def start(self, case, restart, properties):
        self.seed(restart)
        phase, sources, destinations, action, result = runner.W346_CASES[case]["recovery"][restart - 1]
        self.lines = [f"ARCE_TRANSFER_RECOVERY transfer={t.T} phase={phase} source_count={sources} "
                      f"destination_count={destinations} action={action} status={result}"]
        return SimpleNamespace(lines=self.lines)

    def status(self, logical): return t.status()
    def wait(self, *args): return self.lines[0]
    def pending(self): return dict(pending=0, retained=0, work=False)
    def end(self, *args): return 0


class CompleteInventoryTests(unittest.TestCase):
    def complete(self, directory, location=None, stage=0, satellite=t.DATA):
        run = CompleteSession(directory, location, stage, satellite)
        with patch.object(runner, "first_start", return_value=(t.fixture(), t.first())):
            return runner.run_w346_case(run, t.CASE)

    def test_complete_control_passes_both_restarts(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.complete(Path(directory))
        self.assertEqual(("PASS", 2), (result["status"], len(result["restarts"])))

    def test_complete_case_rejects_registered_packages_at_the_kill_and_each_restart(self):
        locations = ("Inventory", "EnderItems", "nested_player", "terrain", "other_dimension",
                     "source_snapshot", "destination_snapshot", "entity_snapshot", "entity_inventory")
        with tempfile.TemporaryDirectory() as directory:
            for stage in (0, 1, 2):
                for location in locations:
                    with self.subTest(stage=stage, location=location), self.assertRaisesRegex(t.bay.smoke.SmokeError, "registered package"):
                        self.complete(Path(directory) / f"{stage}-{location}", location, stage)

    def test_complete_case_rejects_a_package_in_saved_moon_terrain_after_either_restart(self):
        with tempfile.TemporaryDirectory() as directory:
            for stage in (1, 2):
                with self.subTest(stage=stage), self.assertRaisesRegex(t.bay.smoke.SmokeError, "registered package"):
                    self.complete(Path(directory) / str(stage), "moon_terrain", stage)

    def test_complete_case_does_not_count_the_unregistered_solar_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            for location in ("Inventory", "EnderItems", "terrain", "source_snapshot", "entity_snapshot", "entity_inventory"):
                with self.subTest(location=location):
                    result = self.complete(Path(directory) / location, location, satellite=t.SOLAR)
                    self.assertEqual(("PASS", 2), (result["status"], len(result["restarts"])))

    def test_kill_never_reads_held_moon_terrain_but_restarts_do(self):
        with tempfile.TemporaryDirectory() as directory:
            world = t.world(Path(directory))
            terrain(world, MOON_TERRAIN)
            cut = runner.cut_disk(world, t.fixture(), t.first())
            self.assertIn((MOON_TERRAIN / "r.1.-1.mca").as_posix(), cut["inventories"]["skipped_held_terrain"])
            t.world(world, moon={(0, 0): [t.rocket(t.DEST)]})
            with self.assertRaisesRegex(t.bay.smoke.SmokeError, "registered package"):
                runner.restart_disk(world, t.fixture(), cut, cut["entity_files"], 1)

    def test_identity_not_the_item_name_selects_the_registered_package(self):
        with tempfile.TemporaryDirectory() as directory:
            world = t.world(Path(directory))
            inject(world, "Inventory")
            path = world / "playerdata" / f"{t.OWNER}.dat"
            name, root = nbt.read_gzip(path)
            root.value["Inventory"].value[1][0].value["id"] = t.string("advancedrocketrycommunity:other_package")
            nbt.write_gzip(path, name, root)
            with self.assertRaisesRegex(t.bay.smoke.SmokeError, "registered package.*Inventory"):
                runner.cut_disk(world, t.fixture(), t.first())

    def test_inventory_evidence_lists_every_scanned_file_with_hashes_and_slot_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            world = t.world(Path(directory))
            inject(world, "EnderItems", t.SOLAR)
            terrain(world, satellite=t.SOLAR)
            observed = runner.cut_disk(world, t.fixture(), t.first())["inventories"]
            rows = {row["path"]: row for row in observed["files"]}
            self.assertEqual(5, len(rows))
            self.assertEqual(1, rows["region/r.1.-1.mca"]["stored_chunks"])
            self.assertIn(f"playerdata/{t.OWNER}.dat", rows)
            self.assertEqual([], observed["registered_packages"])
            for path, row in rows.items():
                self.assertEqual(t.evidence.file_sha256(world / path), row["sha256"])

    def test_inventory_scan_refuses_unreadable_files_and_excess_files(self):
        with tempfile.TemporaryDirectory() as directory:
            world = t.world(Path(directory))
            folder = world / "region"
            folder.mkdir()
            region = folder / "r.0.0.mca"
            region.write_bytes(b"truncated")
            with self.assertRaisesRegex(t.bay.smoke.SmokeError, "Truncated or oversized"):
                runner.cut_disk(world, t.fixture(), t.first())
            region.unlink()
            inject(world, "Inventory", t.SOLAR)
            (world / "playerdata" / f"{t.OWNER}.dat").write_bytes(b"not gzip")
            with self.assertRaisesRegex(t.bay.smoke.SmokeError, "Unreadable inventory evidence"):
                runner.cut_disk(world, t.fixture(), t.first())
            with patch.object(runner, "MAX_INVENTORY_FILES", 1), self.assertRaisesRegex(t.bay.smoke.SmokeError, "file budget"):
                runner.cut_disk(world, t.fixture(), t.first())

    def test_opened_but_never_written_region_has_no_persisted_inventory_slots(self):
        with tempfile.TemporaryDirectory() as directory:
            world = t.world(Path(directory))
            # The native D6 census opens neighboring Moon and overworld entity regions without storing any record.
            path = world / runner.OVERWORLD_ENTITIES / "r.-1.-1.mca"
            path.touch()
            observed = runner.cut_disk(world, t.fixture(), t.first())["inventories"]
            row = next(row for row in observed["files"] if row["path"] == "entities/r.-1.-1.mca")
            self.assertEqual(0, row["stored_chunks"])
            self.assertEqual(t.evidence.file_sha256(path), row["sha256"])


if __name__ == "__main__":
    unittest.main()
