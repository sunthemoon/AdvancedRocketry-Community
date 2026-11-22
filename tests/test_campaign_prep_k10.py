"""Linux-safe K10 planning, isolation, readback and throughput checks."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts.campaign_prep import k10_endgame as k
from scripts.campaign_prep import k10_endgame_load as load


class K10Tests(unittest.TestCase):
    def test_java_name_uuid(self):
        # Independently checked with Java 17 UUID.nameUUIDFromBytes via jshell.
        self.assertEqual("894c3119-32d4-3eb8-ba7a-7cea0e537912", k.named_uuid("arce-refload-owner-0"))

    def test_actor_roles_are_explicit(self):
        actors = k.actors()
        self.assertEqual(104, len(actors["actors"]))
        self.assertEqual(104, len({r["uuid"] for r in actors["actors"]}))
        self.assertEqual(44, sum(r["classification"].startswith("embedded-channel") for r in actors["actors"]))
        self.assertEqual(0, actors["real_connections"]["count"])
        self.assertEqual(2, actors["real_connections"]["required"])
        self.assertIn("FakePlayer", actors["physical_break_actor"])
        players = [r for r in actors["actors"] if r["classification"].startswith("embedded-channel")]
        self.assertEqual(44, len({r["profile_name"] for r in players}))
        self.assertTrue(all(len(r["profile_name"]) <= 16 for r in players))

    def test_ground_has_no_inherited_regions(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            proof = k.ground_check(root)
            self.assertEqual(256, proof["shaft_bounds"]["max_depth"])
            path = root / "region" / proof["absent_inherited_regions"][0]
            path.parent.mkdir()
            path.write_bytes(b"inherited")
            with self.assertRaisesRegex(ValueError, "intersects"):
                k.ground_check(root)
            self.assertEqual(b"inherited", path.read_bytes())

    def test_shafts_are_inside_chunks(self):
        targets = [r for r in k.overworld_devices() if r["kind"] == "laser_target"]
        self.assertEqual(4, len(targets))
        self.assertTrue(all(1 <= r["pos"][0] % 16 <= 14 and 1 <= r["pos"][2] % 16 <= 14 for r in targets))

    def test_layout_owners_and_positions(self):
        rows = k.overworld_devices()
        self.assertEqual(108, len(rows))
        self.assertEqual(len(rows), len({tuple(r["pos"]) for r in rows}))
        self.assertEqual(64, sum(r["kind"] == "gravity_field_controller" for r in rows))
        self.assertEqual(32, sum(r["kind"] == "railgun" for r in rows))

    def test_client_plan_is_not_a_run(self):
        plan = k.client_steps()
        for row in ("H01", "H02", "H03_large_range", "H04", "H06"):
            self.assertIn(row, plan)
        self.assertIn("NOT_COVERED", plan["H04"])
        self.assertIn("SCRIPTED_ONLY", plan["status"])
        self.assertEqual(5, len(plan["menus"]))
        self.assertIn("2..16", plan["menus"]["gravity_field_controller"])

    def test_historical_mapping_does_not_invent_equivalence(self):
        mapping = k.historical_cuts(k.common.ROOT / "docs/work/v1.7.0-c13-closure/root-checks.zip")
        self.assertEqual(17, len(mapping))
        for name, row in mapping.items():
            if name != "physical-layer":
                self.assertEqual("MAPPED_NOT_RERUN", row["status"])
                self.assertIsNotNone(row["boundary"])
                self.assertIn("NOT_COVERED", row["coverage"])
                self.assertIn("not a physical", row["reason"])

    def test_window_defaults_and_short_classification(self):
        self.assertEqual("PROCEDURE_NOT_REF_ACCEPTANCE", load.windows(120, 600, False)["classification"])
        self.assertEqual("QUALIFICATION_NOT_REF", load.windows(1, 3, True)["classification"])
        for values in ((1,600,False), (120,2,False), (float("nan"),600,True), (1,float("inf"),True), (0,1,True)):
            with self.assertRaises(ValueError):
                load.windows(*values)

    def test_shortfall_is_not_met_and_load_unchanged(self):
        before = dict(launches_requested=0, launches_escrowed=0)
        after = dict(launches_requested=160, launches_escrowed=23)
        result = load.throughput(before, after, 200, 10)
        self.assertEqual("NOT_MET", result["load_obligation"])
        self.assertEqual(16, result["required_launches_per_20_ticks"])
        self.assertEqual(160, result["required_launches_in_interval"])
        self.assertEqual(2.3, result["escrowed_per_game_second"])
        self.assertEqual(137, result["shortfall"])
        self.assertIn("NOT_COVERED", result["delivery_throughput"])

    def test_throughput_rejects_reset_and_invalid_intervals(self):
        for after,ticks,seconds in ((dict(launches_requested=0,launches_escrowed=-1),20,1),
            (dict(launches_requested=1,launches_escrowed=2),20,1),
            (dict(launches_requested=1,launches_escrowed=1),0,1),
            (dict(launches_requested=1,launches_escrowed=1),20,float("nan"))):
            with self.assertRaises(ValueError):
                load.throughput(dict(launches_requested=0, launches_escrowed=0), after,ticks,seconds)

    def test_status_requires_built_runtime(self):
        with self.assertRaisesRegex(ValueError, "unbuilt"):
            load.status("ARCE_RELEASE_TEST refload status built=false")
        line = "ARCE_RELEASE_TEST refload status built=true driving=true launches_requested=32 launches_escrowed=2 rides_requested=0 players=44 drills_running=20 fields_active=64 generators_burning=16 elevators_bound=8"
        self.assertEqual(20, load.status(line)["drills_running"])

    def test_restart_requires_full_unloaded_device_roots(self):
        state = dict(devices=[dict(region="region/r.0.0.mca", value=dict(x=1,y=2,z=3,root=dict(energy=5)))],
                     stations=[], forced_chunks={})
        other = json.loads(json.dumps(state))
        self.assertTrue(k.compare(state,other)["full_device_roots_equal"])
        other["devices"][0]["value"]["root"]["energy"] = 4
        with self.assertRaisesRegex(ValueError, "roots changed"):
            k.compare(state,other)
        self.assertFalse(k.compare(state,other,exact=False)["full_device_roots_equal"])

    def test_unobservable_caches_and_tickets_are_not_inferred(self):
        plan = load.observation_plan()
        self.assertIn("NOT_COVERED", plan["cache_scope"])
        self.assertIn("not every Forge ticket", plan["ticket_scope"])

    def test_jfr_stop_does_not_overwrite_start_output(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "load.jfr").write_bytes(b"start output")
            with patch("scripts.campaign_observe.jdk.stop_jfr", return_value="stopped") as stop, \
                 patch("scripts.campaign_observe.jdk.summarize_recording", return_value={}) as summary:
                load.finish_recording(object(), root, "jcmd", "jfr")
                self.assertEqual("load-stopped.jfr", stop.call_args.args[2])
                self.assertEqual(root / "load-stopped.jfr", summary.call_args.args[0])
            self.assertEqual(b"start output", (root / "load.jfr").read_bytes())


if __name__ == "__main__":
    unittest.main()
