import copy
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_cargo_cuts as runner


class CargoCutsRunnerTest(unittest.TestCase):
    def disk(self, load=True, restarted=False):
        marker = lambda role: [] if restarted else [dict(op_id="op", seq=1, epoch_after=1, role=role)]
        section = lambda role: dict(resource_epoch=1, ledger_id="ledger", markers=marker(role))
        fluid = dict(FluidName="minecraft:water", Amount=1000 if load else 4000)
        return dict(ledger=dict(ledger_id="ledger", issued_through=1, records=[] if restarted else [dict(op_id="op", seq=1)],
                                rocket_authorities=[], armed_flights=[], issued_launch_serial=0),
                    rocket=dict(resource_escrow=section("DESTINATION" if load else "SOURCE"),
                                snapshot=dict(relative_blocks=[dict(block_entity=dict(data=dict(tank=dict(fluid=fluid))))])),
                    device=dict(role="LOAD" if load else "UNLOAD", tank=dict(FluidName="minecraft:water", Amount=4000 if load else 1000),
                                resource_escrow=section("SOURCE" if load else "DESTINATION")))

    def test_matrix_has_all_fourteen_boundaries_in_both_directions(self):
        self.assertEqual(28, sum(row["family"] == "C-S1" for row in runner.CASES.values()))
        for direction in ("LOAD", "UNLOAD"):
            rows = [row for row in runner.CASES.values() if row["direction"] == direction and row["family"] == "C-S1"]
            self.assertEqual(14, len(rows))
            self.assertEqual({f"K{i}" for i in range(11)} | {"K2_ACK", "K3_DEBIT", "K10_ACK"}, {row["cut"] for row in rows})
        self.assertEqual("PREPARING", runner.CASES["FC_LOAD_PREPARING"]["checkpoint"])
        self.assertEqual("K9", runner.CASES["FC_LOAD_L2_REPLACED"]["checkpoint"])

    def test_every_new_boundary_uses_the_appropriate_existing_raw_oracle(self):
        with patch.object(runner.native, "inspect", return_value={}) as inspect, patch.object(runner, "validate_markers"):
            for case, expected in runner.CASES.items():
                if expected["oracle"] == "storage":
                    continue
                for restarted in (False, True):
                    runner.inspect(Path("world"), case, restarted)
                    inspect.assert_called_with(Path("world"), expected["oracle"], restarted)

    def test_storage_refusal_is_exact_and_external_stays_held_on_both_restarts(self):
        case = "FC_LOAD_E2_ROCKET_EXTERNAL"
        with patch.object(runner.storage, "inspect", return_value={}) as inspect, patch.object(runner, "validate_markers") as markers:
            for restarted in (False, True):
                runner.inspect(Path("world"), case, restarted)
                inspect.assert_called_with(Path("world"), case, restarted)
                markers.assert_called_with({}, case, False)
        for restart in (0, 1, 2):
            lines = ([f"ARCE_RELEASE_CARGO_RESULT cut={case} reason=BARRIER_EXTERNAL records=1 locked=true"] if restart else
                     [f"ARCE_RELEASE_CARGO_PAUSED cut={case} checkpoint=E2_ROCKET_EXTERNAL selected=DESTINATION",
                      f"ARCE_CARGO_STORAGE_PROOF cut={case} reason=BARRIER_EXTERNAL io=0 refusals=1 superseding=0 records=1 pending=1"])
            runner.observations(lines, case, restart)
            with self.assertRaises(runner.native.smoke.SmokeError):
                runner.observations([line.replace("BARRIER_EXTERNAL", "BARRIER_IO") for line in lines], case, restart)

    def test_exact_markers_fluid_identity_and_bindings_in_both_roles(self):
        for load in (True, False):
            case = f"FC_{'LOAD' if load else 'UNLOAD'}_C6_JOINT_READBACK"
            disk = self.disk(load)
            runner.validate_markers(disk, case, False)
            for mutate in (
                    lambda d: d["device"]["tank"].update(FluidName="minecraft:lava"),
                    lambda d: d["device"]["tank"].update(Tag={"x": 1}),
                    lambda d: d["device"].update(role="OTHER"),
                    lambda d: d["rocket"]["resource_escrow"].update(ledger_id="foreign"),
                    lambda d: d["rocket"]["resource_escrow"].update(markers=[]),
                    lambda d: d["device"]["resource_escrow"]["markers"][0].update(op_id="foreign"),
                    lambda d: d["device"]["resource_escrow"]["markers"][0].update(epoch_after=2),
                    lambda d: d["ledger"].update(issued_launch_serial=1),
                    lambda d: d["ledger"].update(issued_through=2)):
                changed = copy.deepcopy(disk); mutate(changed)
                with self.assertRaises(runner.native.smoke.SmokeError):
                    runner.validate_markers(changed, case, False)

    def test_each_restart_requires_pruned_markers(self):
        case = "FC_LOAD_C6_JOINT_READBACK"
        runner.validate_markers(self.disk(restarted=True), case, True)
        with self.assertRaises(runner.native.smoke.SmokeError):
            runner.validate_markers(self.disk(), case, True)

    def test_wrong_missing_repeated_boundary_and_native_errors_are_findings(self):
        case = "FC_LOAD_SOURCE_ONLY_STORE"
        line = "ARCE_RELEASE_CARGO_PAUSED cut=" + case + " checkpoint=BARRIER_FORCE_AFTER selected=SOURCE"
        runner.observations([line], case, 0)
        for lines in ([], [line, line], [line.replace("selected=SOURCE", "selected=DESTINATION")],
                      [line.replace("FORCE_AFTER", "FORCE_BEFORE")], [line, "[worker/ERROR] failure"]):
            with self.assertRaises(runner.native.smoke.SmokeError):
                runner.observations(lines, case, 0)

    def test_first_failed_cut_retains_three_starts_and_stops_before_next_cut(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); (root / "template/libraries").mkdir(parents=True)
            jar = root / "test.jar"; jar.write_bytes(b"fixture")
            args = SimpleNamespace(source_commit="sha", lock_owner="owner", source_server=root / "template", jar=jar,
                                   session_dir=root / "session", evidence_dir=root / "evidence", java="java", startup_timeout=240,
                                   case=["FC_LOAD_PREPARING", "FC_LOAD_L1_BEFORE"])
            process = Mock(); process.finish.return_value = 0; process.lines = []
            with patch.object(runner.native, "assert_pin"), patch.object(runner.native, "require_lock"), \
                    patch.object(runner.native, "preflight", return_value={}), patch.object(runner.native, "digest", return_value="hash"), \
                    patch.object(runner.native.smoke, "CapturedProcess", return_value=process) as starts, \
                    patch.object(runner.native.smoke, "allocate_port", return_value=25565), \
                    patch.object(runner.native.smoke, "write_server_configuration"), \
                    patch.object(runner, "raw_snapshot", return_value={}), \
                    patch.object(runner, "observations"), \
                    patch.object(runner, "inspect", side_effect=[runner.native.smoke.SmokeError("mixed-state finding"),
                                                                dict(identity="same"), dict(identity="same")]):
                with self.assertRaisesRegex(runner.native.smoke.SmokeError, "First contradictory cut"):
                    runner.run(args)
                self.assertEqual(3, starts.call_count)
            import json
            report = json.loads((args.evidence_dir / "results.json").read_text())
            self.assertEqual("FAILED", report["status"])
            self.assertEqual(1, len(report["cases"]))
            self.assertEqual(3, len(report["cases"][0]["starts"]))
            self.assertEqual(3, len(list(args.evidence_dir.glob("*-raw.json"))))
            self.assertTrue(report["cleanup"])
            self.assertFalse(args.session_dir.exists())


if __name__ == "__main__":
    unittest.main()
