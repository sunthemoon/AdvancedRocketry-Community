"""W451 passive observation: synthetic raws and a fake server process; never launches Java."""
import copy
import json
from pathlib import Path
import shutil
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_v180_crash_replay as campaign
import v180_crash_replay as replay
import v180_passive_observation as passive
from test_v180_crash_replay import Trace

LEDGER_ID, OP = [1, 2, 3, 4], [9, 9, 9, 9]


def ledger(issued=1, generation=3, records=(), accounts=(), tombstones=()):
    return dict(ledger_id=LEDGER_ID, schema_version=3, format_epoch="e", writer_generation=generation, issued_through=issued,
                issued_launch_serial=0, records=list(records), fluid_accounts=list(accounts), tombstones=list(tombstones),
                rocket_authorities=[], floors=[], armed_flights=[])


def section(epoch=1, seq=1, markers=True):
    return dict(schema_version=1, resource_epoch=epoch, ledger_id=LEDGER_ID,
                markers=[dict(op_id=OP, seq=seq, role="SOURCE", epoch_after=epoch)] if markers else [])


def rocket(cargo, fuel=0, escrow=None):
    return dict(UUID=[5, 5, 5, 5], id="arce:rocket", Pos=[68.5, 100.0, 64.5], RocketEntityData=dict(
        assembly_transaction_id=[6, 6, 6, 6], owner_id=[7, 7, 7, 7], resource_escrow=escrow or section(),
        flight_data=dict(state="ASSEMBLED", fuel=dict(amount=fuel), passengers=dict(assignments=[])),
        snapshot=dict(snapshot_id="s", relative_blocks=[dict(block_entity=dict(adapter="a", data=cargo))])))


def water(amount):
    return dict(FluidName="minecraft:water", Amount=amount) if amount else {}


def fluid_raw(rocket_mb, device_mb, root=None, **extra):
    """The shape of run_v180_cargo_cuts.raw_snapshot."""
    device = dict(x=64, y=100, z=64, id="arce:loader", arce_rocket_fluid_cargo=dict(
        device=[8, 8, 8, 8], owner=[7, 7, 7, 7], role="LOAD", binding=dict(logical_rocket=[6, 6, 6, 6]),
        resource_escrow=section(), tank=water(device_mb)))
    return dict(ledger=ledger() if root is None else root, terrain=dict(block_entities=[device]),
                entities=dict(Entities=[rocket(dict(tank=dict(fluid=water(rocket_mb))), **extra), dict(id="minecraft:pig")]),
                journal=dict(transfers=[]), records={})


def stone(count, slot=None):
    return dict(id="minecraft:stone", Count=count, tag=dict(b=7), **({} if slot is None else dict(Slot=slot)))


def item_raw(rocket_count, device_count):
    """The shape of item_cuts_oracle.read."""
    device = dict(x=64, y=100, z=64, arce_rocket_item_cargo=dict(
        device=[8, 8, 8, 8], owner=[7, 7, 7, 7], role="UNLOAD", binding={}, resource_escrow=section(),
        buffer=dict(slots=[stone(device_count), {}, {}, {}])))
    return dict(ledger=ledger(), terrain=dict(block_entities=[device]),
                entities=[rocket(dict(Items=[stone(rocket_count, 0)]))], transfers=[])


def snapshots(*raws):
    return [dict(view=passive.view(raw)) for raw in raws]


STARTS = [dict(start=1, saved=True, exit_code=0), dict(start=2, saved=True, exit_code=0)]


class ProjectionTests(unittest.TestCase):
    def test_probe_free_command_keeps_only_the_base_and_refuses_a_selected_probe(self):
        base = ["java", "-Xms512M", "-Xmx4G", "-Dadvancedrocketrycommunity.releaseTestHooks=true", "@args", "nogui"]
        self.assertEqual(base, passive.passive_command(base, True))
        self.assertEqual(["java", "-Xms512M", "-Xmx4G", "@args", "nogui"], passive.passive_command(base, False))
        for selected in ("escrowScenario=recover", "escrowCase=FC_LOAD_K8", "escrowCheckpoint=K8", "storageTrace=x"):
            with self.assertRaises(replay.ReplayError):
                passive.passive_command(base[:1] + ["-Dadvancedrocketrycommunity." + selected] + base[1:], True)

    def test_fluid_view_keeps_amounts_per_endpoint_identities_markers_and_fuel(self):
        value = passive.view(fluid_raw(1000, 4000, fuel=700))
        self.assertEqual({"fluid:minecraft:water": 5000, "rocket-fuel": 700}, value["totals"])
        self.assertEqual({"fluid:minecraft:water": 1000}, value["rockets"][0]["cargo"])
        self.assertEqual({"fluid:minecraft:water": 4000}, value["devices"][0]["contents"])
        self.assertEqual(([5, 5, 5, 5], [6, 6, 6, 6], 1), (value["rockets"][0]["uuid"], value["rockets"][0]["logical"],
                                                           len(value["rockets"][0]["escrow"]["markers"])))
        self.assertEqual(1, len(value["rockets"]))  # The pig is not an endpoint.
        self.assertEqual((1, 3, 0), (value["ledger"]["issued_through"], value["ledger"]["writer_generation"], value["journal"]))

    def test_item_view_totals_by_item_type_and_tag_and_empty_tanks_count_nothing(self):
        value = passive.view(item_raw(6, 64))
        key = next(iter(value["totals"]))
        self.assertTrue(key.startswith("item:minecraft:stone#")); self.assertEqual({key: 70}, value["totals"])
        other = item_raw(6, 64); other["entities"][0]["RocketEntityData"]["snapshot"]["relative_blocks"][0]["block_entity"]["data"]["Items"][0]["tag"] = dict(b=8)
        self.assertEqual(2, len(passive.view(other)["totals"]))
        self.assertEqual({"fluid:minecraft:water": 5000}, passive.view(fluid_raw(0, 5000))["totals"])

    def test_settlement_shape_and_a_restored_block_are_read_too(self):
        raw = fluid_raw(5000, 0)
        flat = dict(ledger=raw["ledger"], journal=dict(transfers=[{}]), entities=raw["entities"]["Entities"],
                    block_entities=raw["terrain"]["block_entities"] + [dict(x=68, y=100, z=64, id="tank", tank=dict(fluid=water(250)))])
        value = passive.view(flat)
        self.assertEqual({"fluid:minecraft:water": 5250}, value["totals"])
        self.assertEqual(([68, 100, 64], 1), (value["other_blocks"][0]["pos"], value["journal"]))

    def test_rebuilt_state_must_match_the_sealed_derivations_and_file_presence(self):
        derivation = dict(kind="LATE-LOSS", rule="R6", targets=["data/a.dat"], open_seqs=[14])
        state = replay.State("07-LATE-LOSS", {}, [derivation], {"data/a.dat": "new", "data/a.dat.arce-pending": None})
        sealed = dict(states=[dict(id="07-LATE-LOSS", derivations=[dict(derivation)],
                                   files={"data/a.dat": "sealed", "data/a.dat.arce-pending": None})])
        self.assertEqual("sealed", passive.same_state(sealed, state)["sealed_files"]["data/a.dat"])
        for change in (lambda s: s["states"][0].update(id="06-S-NEW"),
                       lambda s: s["states"][0]["derivations"][0].update(open_seqs=[21]),
                       lambda s: s["states"][0]["files"].update({"data/a.dat": None})):
            other = copy.deepcopy(sealed); change(other)
            with self.assertRaises(replay.ReplayError): passive.same_state(other, state)


class ClassificationTests(unittest.TestCase):
    def setUp(self):
        self.recorded = passive.view(fluid_raw(1000, 4000))
        self.rewound = fluid_raw(1000, 4000, root=ledger(issued=0, generation=1))

    def test_conserved_quantities_with_lost_sequence_and_orphan_markers(self):
        result = passive.classify(self.recorded, snapshots(self.rewound, self.rewound, self.rewound), STARTS)
        self.assertEqual(passive.CONSERVED_LOST, result["verdict"])
        self.assertEqual([{}, {}], result["deltas_after_each_start"])
        self.assertIn("issued_through 1 -> 0", result["authority_lost"][-1])
        self.assertIn("writer_generation 3 -> 1", result["authority_lost"][-1])
        self.assertTrue(any("beyond issued_through 0" in line for line in result["orphan_markers"][-1]))

    def test_lost_records_accounts_receipts_and_the_whole_ledger_are_named(self):
        before = passive.authority(ledger(records=[dict(seq=1, op_id=OP, phase="SETTLING_ACCEPT")], accounts=[{}], tombstones=[{}]))
        lost = passive.authority_lost(before, passive.authority(ledger(issued=0, generation=1)), 1, 0)
        for name in ("records 1 -> 0", "fluid_accounts 1 -> 0", "tombstones 1 -> 0", "journal transfers 1 -> 0"):
            self.assertIn(name, lost)
        self.assertEqual(["the whole ledger"], passive.authority_lost(before, None))

    def test_intact_authority_is_reported_as_such_not_as_a_pass(self):
        same = fluid_raw(1000, 4000)
        result = passive.classify(self.recorded, snapshots(same, same, same), STARTS)
        self.assertEqual(passive.CONSERVED, result["verdict"]); self.assertNotIn("PASS", json.dumps(result))

    def test_lost_and_created_amounts_are_reported_per_start_even_when_later_restored(self):
        result = passive.classify(self.recorded, snapshots(self.rewound, fluid_raw(1000, 3000), fluid_raw(2000, 4000)), STARTS)
        self.assertEqual(passive.CHANGED, result["verdict"])
        self.assertEqual([{"fluid:minecraft:water": 1000}, {}], result["lost"])
        self.assertEqual([{}, {"fluid:minecraft:water": 1000}], result["created"])
        moved = passive.classify(self.recorded, snapshots(self.rewound, fluid_raw(0, 5000), fluid_raw(0, 5000)), STARTS)
        self.assertNotEqual(passive.CHANGED, moved["verdict"])  # A rollback between endpoints conserves the total.
        once = passive.classify(self.recorded, snapshots(self.rewound, fluid_raw(1000, 3000), self.rewound), STARTS)
        self.assertEqual(passive.CHANGED, once["verdict"])

    def test_undecidable_names_what_is_missing(self):
        broken = snapshots(self.rewound, self.rewound) + [dict(error="SmokeError: Missing or oversized native ledger")]
        result = passive.classify(self.recorded, broken, STARTS)
        self.assertEqual(passive.UNDECIDABLE, result["verdict"]); self.assertIn("snapshot 2", result["missing"][0])
        crashed = [STARTS[0], dict(start=2, saved=False, exit_code=1, error="Server exited with code 1")]
        result = passive.classify(self.recorded, snapshots(self.rewound, self.rewound, self.rewound), crashed)
        self.assertEqual(passive.UNDECIDABLE, result["verdict"]); self.assertIn("start 2", result["missing"][0])
        result = passive.classify(self.recorded, snapshots(fluid_raw(1000, 3000), self.rewound, self.rewound), STARTS)
        self.assertEqual(passive.UNDECIDABLE, result["verdict"])
        self.assertEqual(passive.UNDECIDABLE, passive.classify(self.recorded, snapshots(self.rewound, self.rewound), STARTS[:1])["verdict"])

    def test_changed_identity_is_never_called_intact(self):
        other = fluid_raw(1000, 4000); other["entities"]["Entities"][0]["UUID"] = [0, 0, 0, 1]
        same = fluid_raw(1000, 4000)
        result = passive.classify(self.recorded, snapshots(same, same, other), STARTS)
        self.assertEqual(passive.CONSERVED_LOST, result["verdict"]); self.assertEqual([True, False], result["identities_unchanged"])


class FakeProcess:
    instances: list = []
    fail_start = None

    def __init__(self, command, cwd, log_path):
        self.launch, self.cwd, self.sent, self.lines = command, cwd, [], ["Done (1.0s)! For help\n", "ARCE_X marker\n"]
        log_path.write_text("".join(self.lines), encoding="utf-8")
        self.world = sorted(p for p in cwd.iterdir() if p.name.startswith("world"))[0]
        self.started_with = (self.world / "data/a.dat").read_bytes()
        self.process = SimpleNamespace(poll=lambda: 1)
        FakeProcess.instances.append(self)
        self.number = len(FakeProcess.instances)

    def wait_for(self, marker, timeout, start_at=0):
        if self.number == FakeProcess.fail_start:
            raise passive.smoke.SmokeError("Server exited with code 1")
        if not any(marker.search(line) for line in self.lines[start_at:]):
            raise passive.smoke.SmokeError("Timed out")

    def command(self, value):
        self.sent.append(value)
        if value == "save-all flush":
            self.lines.append("Saved the game\n")
            (self.world / "data/a.dat").write_bytes(self.started_with + b"+")

    def finish(self):
        return 0

    def abort(self):
        self.sent.append("ABORT")


class DriverTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.kill = self.root / "kill"; (self.kill / "data").mkdir(parents=True); (self.kill / "data/a.dat").write_bytes(b"new")
        (self.kill / "level.dat").write_bytes(b"level")
        (self.root / "server/libraries").mkdir(parents=True)
        (self.root / "candidate.jar").write_bytes(b"jar")
        self.output = self.root / "out"; self.output.mkdir()
        self.state = replay.State("07-LATE-LOSS", {"data/a.dat": b"old"}, [dict(kind="LATE-LOSS")], {"data/a.dat": replay.sha(b"old")})
        self.args = SimpleNamespace(source_server=self.root / "server", jar=self.root / "candidate.jar", jar_sha256=replay.sha(b"jar"),
                                    java="java", startup_timeout=1)
        FakeProcess.instances, FakeProcess.fail_start = [], None
        base = ["java", "-Xms512M", "-Xmx1024M", "-Dadvancedrocketrycommunity.releaseTestHooks=true", "@args", "nogui"]
        for target, name, value in ((passive.smoke, "CapturedProcess", FakeProcess), (passive.smoke, "allocate_port", lambda: 25565),
                                    (passive.smoke, "write_server_configuration", lambda *a: "x"),
                                    (passive.smoke, "expected_server_properties", lambda *a: {"server-port": "25565"}),
                                    (passive, "_server_command", lambda java: list(base)), (passive.time, "sleep", Mock())):
            patcher = patch.object(target, name, value); patcher.start(); self.addCleanup(patcher.stop)

    def read(self, world):
        # The fake server appends one byte per save; decode it as extra millibuckets to see each save.
        return fluid_raw(1000, 4000 + (world / "data/a.dat").read_bytes().count(b"+"))

    def variant(self, label="hooks", hooks=True, pin=lambda: None):
        return passive.observe_variant(self.args, self.state, self.kill, "world-X", self.read, self.output,
                                       self.root / "temp" / label, label, hooks, pin, dwell=7)

    def test_two_probe_free_starts_save_then_stop_and_keep_three_snapshots(self):
        pins = Mock()
        result = self.variant(pin=pins)
        self.assertEqual(2, len(FakeProcess.instances)); self.assertEqual(2, pins.call_count)
        for process in FakeProcess.instances:
            self.assertEqual(["save-all flush", "stop"], process.sent)
            self.assertFalse(any("escrowScenario" in part or "escrowCase" in part for part in process.launch))
            self.assertEqual("-Xmx4G", process.launch[2])
        # Start 1 sees the rewound state, start 2 continues from start 1's save: one copy, not two.
        self.assertEqual([b"old", b"old+"], [p.started_with for p in FakeProcess.instances])
        self.assertEqual([4000, 4001, 4002], [s["view"]["devices"][0]["contents"]["fluid:minecraft:water"] for s in result["snapshots"]])
        passive.time.sleep.assert_called_with(7)
        self.assertEqual([True, True], [s["saved"] for s in result["starts"]])
        self.assertEqual([0, 0], [s["exit_code"] for s in result["starts"]])
        self.assertEqual(["ARCE_X marker"], result["starts"][0]["markers"])
        self.assertEqual(b"new", (self.kill / "data/a.dat").read_bytes())
        self.assertTrue(result["cleanup"]); self.assertFalse((self.root / "temp/hooks").exists())

    def test_archives_and_logs_are_kept_and_hashed(self):
        result = self.variant()
        names = {p.name for p in self.output.iterdir()}
        expected = {f"07-LATE-LOSS-hooks-{n}-{kind}" for n in (0, 1, 2) for kind in ("raw.json", "native.zip")}
        expected |= {f"07-LATE-LOSS-hooks-{n}.log.gz" for n in (1, 2)}
        self.assertEqual(expected, names)
        first = result["snapshots"][0]
        self.assertEqual(passive.evidence_tools.file_sha256(self.output / first["raw"]["file"]), first["raw"]["sha256"])
        self.assertEqual(replay.sha(b"old"), first["native"]["members"]["world-X/data/a.dat"]["sha256"])
        self.assertIn("world-X/level.dat", first["native"]["members"])

    def test_plain_variant_also_drops_the_release_test_gate(self):
        self.variant("plain", False)
        self.assertTrue(all(not any("advancedrocketrycommunity" in part for part in p.launch) for p in FakeProcess.instances))

    def test_a_failed_start_is_recorded_still_followed_by_the_second_start_and_undecidable(self):
        FakeProcess.fail_start = 1
        result = self.variant()
        self.assertEqual(2, len(FakeProcess.instances)); self.assertEqual(["ABORT"], FakeProcess.instances[0].sent)
        self.assertEqual((False, 1), (result["starts"][0]["saved"], result["starts"][0]["exit_code"]))
        self.assertIn("exited with code 1", result["starts"][0]["error"])
        self.assertEqual(3, len(result["snapshots"])); self.assertTrue(result["cleanup"])
        verdict = passive.classify(passive.view(fluid_raw(1000, 4000)), result["snapshots"], result["starts"])
        self.assertEqual(passive.UNDECIDABLE, verdict["verdict"])

    def test_case_observes_only_the_named_state_in_both_variants_from_fresh_copies(self):
        sealed = self.root / "sealed/cases/X01-05"; sealed.mkdir(parents=True)
        (sealed / "states.json").write_text(json.dumps(dict(states=[dict(
            id="07-LATE-LOSS", derivations=self.state.derivations, files={"data/a.dat": "sealed-hash"})])))
        self.args.triage_of = self.root / "sealed/MANIFEST.json"
        other = replay.State("06-S-NEW", {}, [dict(kind="S-NEW")], {})
        evidence = self.root / "evidence"; evidence.mkdir()
        report = passive.observe_case(self.args, "X01-05", [other, self.state], self.kill, dict(raw0=fluid_raw(1000, 4000)),
                                      "world-X", self.read, evidence, self.root / "temp")
        self.assertEqual(["hooks", "plain"], [v["variant"] for v in report["variants"]])
        self.assertEqual([b"old", b"old+", b"old", b"old+"], [p.started_with for p in FakeProcess.instances])
        self.assertEqual(["06-S-NEW"], report["not_started"])
        self.assertEqual([passive.CHANGED] * 2, [v["classification"]["verdict"] for v in report["variants"]])
        self.assertEqual([{"fluid:minecraft:water": 1}, {"fluid:minecraft:water": 2}], report["variants"][0]["classification"]["created"])
        saved = json.loads((evidence / "observations/07-LATE-LOSS.json").read_text())
        self.assertEqual("sealed-hash", saved["rebuilt"]["sealed_files"]["data/a.dat"])
        self.assertFalse((evidence / "results.jsonl").exists())
        with self.assertRaisesRegex(replay.ReplayError, "did not plan"):
            passive.observe_case(self.args, "X01-05", [other], self.kill, dict(raw0={}), "world-X", self.read, evidence, self.root / "temp")


class CampaignOptInTests(unittest.TestCase):
    def test_only_the_five_residual_states_are_named(self):
        self.assertEqual({"X01-05": "07-LATE-LOSS", "X01-06": "07-LATE-LOSS", "X01-07": "01-LATE-LOSS",
                          "X01-09": "07-LATE-LOSS", "X01-11": "12-LATE-LOSS"}, passive.OBSERVED)
        self.assertTrue(all(campaign.CASES[case][0] in campaign.RAW for case in passive.OBSERVED))

    def test_opt_in_case_records_once_then_observes_without_any_restart_oracle_or_result_row(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); t = Trace(root / "source")
            t.write(data=b"g1" * 5000, complete=True); t.write(old=b"g1" * 5000, data=b"g2" * 5000)
            evidence = root / "evidence"; evidence.mkdir(); scratch = root / "scratch"; scratch.mkdir()
            args = SimpleNamespace(evidence_dir=evidence, candidate_commit="candidate", jar_sha256="jar", observe_passive=True,
                                   replay_harness_commit="harness")
            calls = []

            def existing(module, supplied, output):
                calls.append(supplied); output.mkdir()
                shutil.copytree(t.trace, supplied.storage_trace)
                supplied.replay_capture(t.kill, dict(starts=[dict(disk={})], raw0=dict(marker=1)))
                supplied.replay_commands.append(dict(restart=0, command=["java", "record"]))
                return dict(status="PASS", cases=[{}]), None

            def observed(supplied, case, states, kill, context, world, read, target, temp, pin):
                self.assertTrue((target / "states.json").is_file()); self.assertTrue(kill.is_dir())
                self.assertEqual(("X01-05", "world-FC_LOAD_L2_REPLACED", campaign.fluid.raw_snapshot, dict(marker=1)),
                                 (case, world, read, context["raw0"]))
                self.assertEqual(7, len(states))
                return dict(state="07-LATE-LOSS", variants=[
                    dict(variant=name, starts=[dict(start=n, command=["java", name]) for n in (1, 2)],
                         classification=dict(verdict=passive.CONSERVED_LOST)) for name in ("hooks", "plain")])

            with patch.object(campaign, "TEMP", scratch), patch.object(campaign, "run_existing", side_effect=existing), \
                    patch.object(campaign, "preflight"), patch.object(passive, "observe_case", side_effect=observed):
                result = campaign.run_case(args, "X01-05", evidence / "case", scratch / "case", "owner")
            self.assertEqual(1, len(calls))
            self.assertEqual(("OBSERVED", "07-LATE-LOSS", 5), (result["status"], result["observed"], result["starts"]))
            self.assertEqual({"hooks": passive.CONSERVED_LOST, "plain": passive.CONSERVED_LOST}, result["verdicts"])
            self.assertFalse((evidence / "case/results.jsonl").exists()); self.assertFalse((scratch / "case/kill").exists())
            commands = json.loads((evidence / "case/commands.json").read_text())
            self.assertEqual([None, "hooks", "hooks", "plain", "plain"], [c.get("variant") for c in commands])

    def test_campaign_refuses_observation_without_a_followup_or_for_another_case(self):
        for extra in (dict(case=["X01-05"], triage_run=None), dict(case=["X01-05", "X01-02"], triage_run="followup-x"),
                      dict(case=None, triage_run="followup-x")):
            args = SimpleNamespace(observe_passive=True, **extra)
            with patch.object(campaign, "preflight"), patch.object(campaign, "evidence_target") as target,                     patch.object(campaign, "run_case") as run_case:
                with self.assertRaisesRegex(replay.ReplayError, "Passive observation"):
                    campaign.run(args)
                target.assert_not_called(); run_case.assert_not_called()

    def test_default_run_case_never_reaches_the_observation(self):
        with patch.object(passive, "observe_case") as observe, tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); t = Trace(root / "source"); t.write(store="BAY_RECEIPTS")
            evidence = root / "evidence"; evidence.mkdir(); scratch = root / "scratch"; scratch.mkdir()
            args = SimpleNamespace(evidence_dir=evidence, candidate_commit="candidate", jar_sha256="jar")

            def existing(module, supplied, output):
                output.mkdir()
                if hasattr(supplied, "replay_capture"):
                    shutil.copytree(t.trace, supplied.storage_trace); supplied.replay_capture(t.kill, dict(fixture={}, first_start={}))
                    return dict(status="PASS", cases=[dict(restarts=[])]), None
                return dict(status="PASS", cases=[dict(restarts=[dict(restart=n, status="PASS") for n in (1, 2)])]), None

            with patch.object(campaign, "TEMP", scratch), patch.object(campaign, "run_existing", side_effect=existing), \
                    patch.object(campaign, "preflight"):
                result = campaign.run_case(args, "X01-01", evidence / "case", scratch / "case", "owner")
            observe.assert_not_called(); self.assertEqual("PASS", result["status"])
            self.assertTrue((evidence / "case/results.jsonl").is_file())


if __name__ == "__main__":
    unittest.main()
