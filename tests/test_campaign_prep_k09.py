"""Linux-safe unit checks; no installed Minecraft/Java/worlds required."""
import copy
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

from scripts.campaign_prep import k09_missions as m
from scripts.campaign_prep import k09_mission_load as load


def root(status="active"):
    return dict(clock=dict(logical_game_time=100, last_observed_game_time=100),
        missions=[dict(mission_id=[1, 2, 3, 4], kind="data", status=status, completes_at=200, reward=120)],
        research_accounts=[dict(balance=20)], instances=[])


class K09Tests(unittest.TestCase):
    def test_pins(self):
        self.assertEqual(m.common.CANDIDATE, m.chain.C6_COMMIT)
        self.assertEqual(m.common.JAR_HASH, m.chain.C6_HASH)

    def test_batches_are_bounded_and_keep_owners(self):
        for size in m.SIZES.values():
            commands = m.batches(size)
            self.assertEqual(sum(int(c.split()[-2]) for c in commands), size)
            self.assertTrue(all(1 <= int(c.split()[-2]) <= 100 and c.endswith(" 32") for c in commands))

    def test_invalid_sizes(self):
        for size in (0, -1, 1001, 1.5, True):
            with self.assertRaises(ValueError):
                m.batches(size)

    def test_fixed_java_owners(self):
        import hashlib
        import uuid
        owners = m.batch_owners()
        self.assertEqual(len(set(owners)), 32)
        self.assertEqual(owners[0], str(uuid.UUID(bytes=hashlib.md5(b"arce:v160:stress-owner:0").digest(), version=3)))
        self.assertTrue(all(uuid.UUID(o).version == 3 for o in owners))

    def test_effective_definitions_read_jar(self):
        with TemporaryDirectory() as tmp:
            jar = Path(tmp) / "test.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr(f"data/{m.common.NS}/satellite_definitions/data.json", json.dumps(dict(mission_duration_ticks=200)))
            values = m.effective_values(jar)
            self.assertEqual(values["definitions"]["data"]["mission_duration_ticks"], 200)
            self.assertEqual(values["fixed_limits"]["completions"], 32)
            self.assertEqual(values["fixed_limits"]["inspections"], 64)
            self.assertEqual(values["fixed_limits"]["interval_ticks"], 20)

    def test_same_tick_not_inferred_from_timestamp_or_epoch(self):
        observation = m.same_tick_observation(["[12:00:00] ARCE_MISSION_DELIVERY event=CLAIM save_epoch=4",
                                               "[12:00:00] ARCE_MISSION_DELIVERY event=CLAIM save_epoch=4"])
        self.assertEqual(observation["status"], "NOT_COVERED")
        self.assertIsNone(observation["tick"])
        self.assertIn("A0", observation["a1_same_tick_two_owner"])

    def test_scheduler_budget_is_observed_not_raised(self):
        line = "ARCE_SATELLITE_SCHEDULER completed=32 pruned=0 inspected=64 remaining=100"
        self.assertEqual(m.scheduler_observation([line])["max_completed"], 32)
        for bad in (line.replace("completed=32", "completed=33"), line.replace("inspected=64", "inspected=65")):
            with self.assertRaises(ValueError):
                m.scheduler_observation([bad])
        self.assertIsNone(m.scheduler_observation([])["max_completed"])

    def test_nondefault_limits_rejected_without_edit(self):
        expected = dict(unfinishedMissionsGlobal=1024, unfinishedMissionsPerOwner=64, finishedMissionsPerOwner=128,
            missionsTotal=3072, satellitesGlobal=4096, satellitesPerOwner=256, asteroidInstancesGlobal=2048,
            asteroidInstancesPerOwner=16, intentIntervalTicks=10, selectionIntervalTicks=2,
            asteroidInstanceTtlTicks=168000, asteroidMissionTimePercent=100, gasMissionTimePercent=100)
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "common.toml"
            text = "[satellites]\n" + "".join(f"{k} = {v}\n" for k,v in expected.items())
            path.write_text(text)
            self.assertEqual(m.config_readback(path), expected)
            changed = text.replace("unfinishedMissionsGlobal = 1024", "unfinishedMissionsGlobal = 2048")
            path.write_text(changed)
            with self.assertRaises(ValueError):
                m.config_readback(path)
            self.assertEqual(path.read_text(), changed)

    def test_mapping_never_equates_snapshot_with_durability(self):
        mapping = m.cut_mapping()
        self.assertIn("not durable", mapping["async"]["boundary"])
        self.assertIn("await", mapping["B"]["boundary"])
        for cut in ("C", "D", "neither"):
            self.assertIn("NOT_COVERED", mapping[cut]["coverage"])

    def test_client_plan_has_no_public_observer_permission(self):
        plan = m.client_steps()
        self.assertEqual(plan["controls"]["claim"], 4)
        self.assertEqual(plan["controls"]["withdraw"], 10)
        self.assertIn("owner-private", " ".join(plan["workflow"]))
        self.assertIn("SCRIPTED_ONLY", plan["status"])

    def test_only_natural_completion_allowed(self):
        before = root()
        after = copy.deepcopy(before)
        after["missions"][0].update(status="ready", ready_at=220)
        self.assertEqual(len(m.compare_missions(before, after)["completed"]), 1)
        self.assertEqual(before, root(), "Comparison mutated input")

    def test_mutations_rejected(self):
        for area, key, value in (("missions", "reward", 240), ("missions", "status", "claimed"),
                                 ("missions", "completes_at", 800), ("research_accounts", "balance", 30)):
            before = root()
            after = copy.deepcopy(before)
            after[area][0][key] = value
            with self.assertRaises(ValueError):
                m.compare_missions(before, after)

    def test_lost_or_added_mission_rejected(self):
        before = root()
        after = copy.deepcopy(before)
        after["missions"] = []
        with self.assertRaises(ValueError):
            m.compare_missions(before, after)

    def test_exact_instance_migration_only(self):
        before = root()
        before["instances"] = [dict(schema_version=1, source_mission=[1, 2, 3, 4], seed=5)]
        after = copy.deepcopy(before)
        after["instances"] = [dict(schema_version=2, origin_kind="survey_mission", origin_id=[1, 2, 3, 4], seed=5)]
        m.compare_missions(before, after)
        after["instances"][0]["seed"] = 9
        with self.assertRaises(ValueError):
            m.compare_missions(before, after)

    def test_root3_adds_only_empty_ground_surveys(self):
        before, after = root(), root()
        before["schema_version"] = 3
        after.update(schema_version=4, ground_surveys=[])
        m.compare_missions(before, after)
        after["ground_surveys"] = [dict(job="unexpected")]
        with self.assertRaises(ValueError):
            m.compare_missions(before, after)
        before["schema_version"] = 4
        after["ground_surveys"] = []
        with self.assertRaises(ValueError):
            m.compare_missions(before, after)

    def test_downtime_cannot_increase_clock(self):
        before, after = root(), root()
        after["clock"] = dict(logical_game_time=120, last_observed_game_time=120)
        self.assertEqual(m.clock_check(before, after)["logical_delta"], 20)
        after["clock"]["logical_game_time"] += 1800 * 20
        with self.assertRaises(ValueError):
            m.clock_check(before, after)

    def test_historical_clock_rollback_bound(self):
        before, after = root(), root()
        before["clock"]["last_observed_game_time"] = 72000
        after["clock"] = dict(logical_game_time=110, last_observed_game_time=510)
        m.clock_check(before, after, 500)
        after["clock"]["logical_game_time"] = 111
        with self.assertRaises(ValueError):
            m.clock_check(before, after, 500)

    def test_source_restart_requires_stopped_interval(self):
        with TemporaryDirectory() as tmp:
            folder = Path(tmp) / "missions-101/load-start"
            folder.mkdir(parents=True)
            receipt = folder / "receipt.json"
            args = SimpleNamespace(output=Path(tmp), variant="missions-101", mode="load-restart", stop_seconds=1800)
            receipt.write_text(json.dumps(dict(stopped_utc=datetime.now(timezone.utc).isoformat())))
            with self.assertRaises(ValueError):
                load.source_for(args)
            receipt.write_text(json.dumps(dict(stopped_utc=(datetime.now(timezone.utc)-timedelta(seconds=1801)).isoformat())))
            self.assertEqual(load.source_for(args), folder / "source-world")

    def test_recover_copy_chain(self):
        for mode, parent in (("recover-A-1", "cut-A"), ("recover-A-2", "recover-A-1"),
                             ("recover-B-1", "cut-B"), ("recover-B-2", "recover-B-1")):
            args = SimpleNamespace(output=Path("evidence"), variant="missions-101", mode=mode)
            self.assertEqual(load.source_for(args), Path("evidence/missions-101") / parent / "source-world")

    def test_raw_sampler_keeps_ticks_and_no_ref_verdict(self):
        replies = iter(("ARCE_RELEASE_TEST ticks_raw tick=1 entries=0 data=",
                       "ARCE_RELEASE_TEST ticks_raw tick=3 entries=2 data=2:1000000,3:2000000",
                       "ARCE_RELEASE_TEST ticks_raw tick=4 entries=2 data=3:2000000,4:3000000"))
        console = SimpleNamespace(query=lambda *_: next(replies))
        args = SimpleNamespace(warmup_seconds=2, sample_seconds=3, stop_seconds=1, qualification=True)
        with TemporaryDirectory() as tmp, patch.object(load.time, "sleep"), patch.object(load, "schedule", return_value=[0]):
            receipt = {}
            load.sample(console, Path(tmp), args, receipt)
            self.assertNotIn("reference_budgets", receipt["sampling"]["summary"])
            self.assertEqual(receipt["sampling"]["summary"]["tick_count"], 3)
            self.assertIn("2,1000000", (Path(tmp) / "ticks.csv").read_text())
            self.assertEqual(receipt["sampling"]["first_tick_exclusive"], 1)

    def test_sampler_discards_all_warmup_ring_entries(self):
        replies = iter(("ARCE_RELEASE_TEST ticks_raw tick=10 entries=1 data=9:999999999",
                       "ARCE_RELEASE_TEST ticks_raw tick=11 entries=2 data=10:999999999,11:1000000",
                       "ARCE_RELEASE_TEST ticks_raw tick=12 entries=2 data=11:1000000,12:2000000"))
        console = SimpleNamespace(query=lambda *_: next(replies))
        args = SimpleNamespace(warmup_seconds=2, sample_seconds=3, stop_seconds=1, qualification=True)
        with TemporaryDirectory() as tmp, patch.object(load.time, "sleep"), patch.object(load, "schedule", return_value=[0]):
            receipt = {}
            load.sample(console, Path(tmp), args, receipt)
            summary = receipt["sampling"]["summary"]
            self.assertEqual(summary["first_tick"], 11)
            self.assertEqual(summary["last_tick"], 12)
            self.assertEqual(summary["mean_ms"], 1.5)

    def test_cut_oracles(self):
        for cut, phase, buffer, status, acknowledged in (("A", "cut", 184, "ready", 0),
                ("B", "cut", 0, "claimed", 0), ("A", "recover", 184, "claimed", 1)):
            state = root()
            mission = dict(state["missions"][0], kind="asteroid", reward=[dict(count=184)], acknowledged=acknowledged,
                           instance_id=[4, 3, 2, 1], status=status)
            terminal = dict(reward_buffer=[dict(count=buffer)] if buffer else [], receipts=[[1, 2, 3, 4]] if cut == "A" and phase == "cut" else [])
            args = SimpleNamespace(mode=f"cut-{cut}" if phase == "cut" else f"recover-{cut}-1")
            with patch.object(m, "resource_mission", return_value=mission), patch.object(m, "terminal_disk", return_value=(terminal, {})), patch.object(load, "source_for", return_value=Path("copy")):
                receipt = {}
                load.check_cut(args, state, state, Path("world"), receipt)
                self.assertTrue(receipt["tickets_unchanged"])
                terminal["reward_buffer"] = [dict(count=369)]
                with self.assertRaises(ValueError):
                    load.check_cut(args, state, state, Path("world"), {})

    def test_terminal_readback_targets_owning_block_chunk(self):
        from scripts import v120_precision_world_fixture as region
        terminal = dict(x=2048, y=100, z=2048, SatelliteTerminal=dict(reward_buffer=[]))
        with TemporaryDirectory() as tmp:
            world = Path(tmp).resolve()
            with patch.object(region, "_read_regular", return_value=b"raw") as read, \
                 patch.object(m, "crash_region_chunk", return_value=b"chunk") as select, \
                 patch.object(region, "_decode_chunk", return_value=dict(block_entities=[terminal])):
                saved, tickets = m.terminal_disk(world)
                self.assertEqual(saved, terminal["SatelliteTerminal"])
                self.assertEqual(tickets, {})
                self.assertEqual(read.call_args.args[0], world / "region/r.4.4.mca")
                select.assert_called_once_with(b"raw", 128, 128)

    def test_terminal_readback_still_rejects_invalid_allocation(self):
        from scripts import v120_precision_world_fixture as region
        with TemporaryDirectory() as tmp, patch.object(region, "_read_regular", return_value=bytes(8192)):
            with self.assertRaises(ValueError):
                m.terminal_disk(Path(tmp).resolve())

    def test_crash_reader_accepts_only_complete_unpadded_records(self):
        import struct
        raw = bytearray(8192)
        raw[:4] = bytes((0, 0, 2, 1))
        raw += struct.pack(">I", 9) + b"\x02abcdefgh"
        self.assertEqual(m.crash_region_chunk(raw, 128, 128), b"abcdefgh")
        for invalid in (raw[:-1], raw[:8192], raw[:4]):
            with self.assertRaises(ValueError):
                m.crash_region_chunk(invalid, 128, 128)
        raw[8196] = 3
        with self.assertRaises(ValueError):
            m.crash_region_chunk(raw, 128, 128)

    def test_ticket_comparison_is_an_exact_set_not_list_order(self):
        first = {"data/chunks.dat": dict(DataVersion=3465, data=dict(Forced=[1, 2, 3]))}
        second = {"data/chunks.dat": dict(DataVersion=3465, data=dict(Forced=[3, 1, 2]))}
        self.assertEqual(load.ticket_projection(first), load.ticket_projection(second))
        second["data/chunks.dat"]["data"]["Forced"] = [1, 2, 4]
        self.assertNotEqual(load.ticket_projection(first), load.ticket_projection(second))
        second["data/chunks.dat"]["data"]["Forced"] = [1, 1, 2]
        with self.assertRaises(ValueError):
            load.ticket_projection(second)

    def test_seal_catches_changed_and_unlisted_world_bytes(self):
        with TemporaryDirectory() as tmp:
            folder = Path(tmp).resolve()
            world = folder / "source-world"
            world.mkdir()
            file = world / "sample.dat"
            file.write_bytes(b"original")
            m.chain.seal(folder)
            file.write_bytes(b"changed")
            with self.assertRaises(ValueError):
                m.chain.verify_seal(folder)
            file.write_bytes(b"original")
            (world / "new.dat").write_bytes(b"unlisted")
            with self.assertRaises(ValueError):
                m.chain.verify_seal(folder)


if __name__ == "__main__":
    unittest.main()
