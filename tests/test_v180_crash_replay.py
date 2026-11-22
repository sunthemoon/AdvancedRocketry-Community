"""Synthetic W369 traces and opt-in X01 runner API; never launches Java."""
import copy
from contextlib import ExitStack
import json
import shutil
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import v180_crash_replay as replay
import run_v180_crash_replay as campaign


class Trace:
    """The on-disk format written by W369 (including null directory store)."""
    def __init__(self, root):
        self.trace, self.kill = root / "trace", root / "kill"
        for folder in (self.trace / "blobs", self.trace / "pre", self.kill / "data"):
            folder.mkdir(parents=True)
        self.ops = []

    def emit(self, thread, store, op, path, **extra):
        self.ops.append(dict(seq=len(self.ops), thread=thread, store=store, op=op, path=path, result="ok", **extra))
        self.save()

    def save(self):
        (self.trace / "ops.jsonl").write_text("".join(json.dumps(op) + "\n" for op in self.ops), encoding="utf-8")

    def write(self, target="data/a.dat", old=b"old", data=b"n" * 10000, complete=False,
              store="RESOURCE_ESCROW", thread="writer", chunks=None):
        seq = len(self.ops)
        if old is None:
            (self.trace / "pre" / f"{seq}.absent").write_bytes(b"")
            pre = dict(pre="absent")
        else:
            (self.trace / "pre" / str(seq)).write_bytes(old)
            pre = dict(pre=replay.sha(old), preLength=len(old))
        self.emit(thread, store, "open", target + replay.PENDING, target=target, **pre)
        offset = 0
        for blob in chunks or [data]:
            digest = replay.sha(blob)
            (self.trace / "blobs" / digest).write_bytes(blob)
            self.emit(thread, store, "write", target + replay.PENDING, offset=offset, length=len(blob), sha256=digest)
            offset += len(blob)
        for op in ("force", "close", "readStaged", "move"):
            self.emit(thread, store, op, target + replay.PENDING, **(dict(target=target) if op == "move" else {}))
        path = self.kill / target; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        if complete:
            self.emit(thread, None, "forceDirectory", str(Path(target).parent).replace("\\", "/"))
        return seq


class BuilderTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name); self.t = Trace(self.root)

    def states(self, **kwargs):
        return replay.plan(self.t.trace, self.t.kill, **kwargs)

    def test_all_six_kinds_exact_bytes_and_world_is_untouched(self):
        data = bytes(range(1, 255)) * 40
        self.t.write(data=data, chunks=[data[:5000], data[5000:]])
        (self.t.kill / "outside.dat").write_bytes(b"R7")
        states = self.states()
        self.assertEqual(list(replay.CRASH_POINTS), [s.derivations[0]["kind"] for s in states])
        targets = [b"old"] * 5 + [data]
        pending = [None, b"", data[:len(data) // 2], data[:-4096] + bytes(4096), data, None]
        for index, state in enumerate(states):
            world = self.root / f"copy-{index}"
            replay.materialize(self.t.kill, world, state)
            self.assertEqual(targets[index], (world / "data/a.dat").read_bytes())
            self.assertEqual(pending[index], replay.read_optional(world, "data/a.dat.arce-pending"))
            self.assertEqual(b"R7", (world / "outside.dat").read_bytes())
            self.assertEqual({"data/a.dat", "data/a.dat.arce-pending"}, set(state.files))
        self.assertEqual(data, (self.t.kill / "data/a.dat").read_bytes())
        self.assertFalse((self.t.kill / "data/a.dat.arce-pending").exists())

    def test_first_creation_old_is_absent_not_an_empty_target(self):
        self.t.write(old=None)
        states = self.states()
        for state in states[:5]:
            self.assertIsNone(state.files["data/a.dat"])
        replay.materialize(self.t.kill, self.root / "copy", states[0])
        self.assertFalse((self.root / "copy/data/a.dat").exists())

    def test_same_thread_next_open_completes_a_moved_attempt_without_directory_force(self):
        self.t.write(data=b"baseline")
        self.t.write(old=b"baseline", data=b"candidate")
        writes = replay.read_trace(self.t.trace, self.t.kill)
        self.assertTrue(writes[0].complete)
        self.assertFalse(writes[1].complete)
        self.assertEqual("move", writes[0].phase)  # Completion is not directory durability.
        states = self.states(late_stores=set())
        self.assertEqual(6, len(states))
        self.assertTrue(all(s.derivations[0]["open_seq"] == 6 for s in states))

    def test_next_open_cannot_complete_an_unmoved_attempt_or_a_different_threads_write(self):
        for thread, truncate in (("writer", True), ("other", False)):
            with self.subTest(thread=thread):
                self.t.write(data=b"baseline")
                if truncate:
                    self.t.ops.pop(); self.t.save()
                self.t.write(old=b"baseline", data=b"next", thread=thread)
                with self.assertRaisesRegex(replay.ReplayError, "Overlapping"):
                    self.states()
                self.t.ops = []

    def test_completed_nonforced_write_remains_eligible_for_late_loss(self):
        self.t.write(data=b"baseline")
        self.t.write(old=b"baseline", data=b"candidate")
        states = self.states(late_stores={"RESOURCE_ESCROW"})
        late = [s for s in states if any(d["kind"] == "LATE-LOSS" for d in s.derivations)]
        self.assertEqual(1, len(late))
        self.assertEqual(b"old", late[0].changes["data/a.dat"])

    def test_untouched_seam_roots_are_hashed_but_region_and_player_files_are_not(self):
        self.t.write()
        name = "data/advancedrocketrycommunity_celestial.dat"
        (self.t.kill / name).write_bytes(b"unmodified")
        (self.t.kill / "level.dat").write_bytes(b"outside-seam")
        for state in self.states():
            self.assertEqual(replay.sha(b"unmodified"), state.files[name])
            self.assertNotIn(name, state.changes)
            self.assertNotIn("level.dat", state.files)

    def test_short_zero_data_deduplicates_and_keeps_all_crash_point_derivations(self):
        self.t.write(data=b"\0")
        states = self.states()
        self.assertLess(len(states), 6)
        self.assertEqual(set(replay.CRASH_POINTS), {d["kind"] for s in states for d in s.derivations})

    def test_mapping_covers_all_seven_prefixes_and_loss_variants(self):
        points = {p for values in replay.CRASH_POINTS.values() for p in values}
        for prefix in ("before-open", "open", "write", "force", "close", "move", "directory-flush"):
            self.assertTrue(any(p.split(":")[0] == prefix for p in points), prefix)
        self.assertEqual(["write:prefix"], replay.CRASH_POINTS["S-PREFIX"])
        self.assertIn("move:undone-staged", replay.CRASH_POINTS["S-STAGED"])
        self.assertIn("move:undone-entry-lost", replay.CRASH_POINTS["S-OLD"])

    def test_late_loss_all_seven_combinations_and_earliest_generation(self):
        for n in range(3):
            self.t.write(f"data/{n}.dat", old=bytes([n]), complete=True, store=str(n))
        states = self.states()
        self.assertEqual(7, len(states))
        self.assertEqual([1, 1, 1, 2, 2, 2, 3], [len(s.derivations[0]["targets"]) for s in states])
        for s in states:
            self.assertEqual(6, len(s.files))
        self.assertEqual(bytes([0]), states[-1].changes["data/0.dat"])

    def test_window_excludes_fixture_creation_and_names_only_case_stores(self):
        self.t.write(old=None, data=b"baseline", complete=True)
        start = self.t.write(old=b"baseline", data=b"intent", complete=True)
        self.t.write("data/other.dat", complete=True, store="OTHER")
        states = self.states(late_stores={"RESOURCE_ESCROW"}, window_start=start)
        self.assertEqual(1, len(states)); self.assertEqual(b"baseline", states[0].changes["data/a.dat"])
        self.assertNotIn("data/other.dat", states[0].changes)

    def test_two_unfinished_writes_have_no_cross_product(self):
        self.t.write()
        self.t.write("data/b.dat", store="BAY_RECEIPTS", thread="other")
        states = self.states()
        self.assertEqual(11, len(states))  # The common, unedited S-NEW deduplicates.
        for state in states:
            old_targets = [p for p, value in state.changes.items() if not p.endswith(replay.PENDING) and value == b"old"]
            self.assertLessEqual(len(old_targets), 1)

    def test_more_than_three_late_stores_is_over_bound_not_sampled(self):
        for n in range(4):
            self.t.write(f"data/{n}.dat", complete=True, store=str(n))
        with self.assertRaisesRegex(replay.OverBound, "OVER_BOUND"):
            self.states()

    def test_total_bound_16_is_enforced(self):
        for n in range(3):
            self.t.write(f"data/c{n}.dat", complete=True, store=str(n))
        for n in range(2):
            self.t.write(f"data/u{n}.dat", store=f"u{n}", thread=str(n))
        with self.assertRaisesRegex(replay.OverBound, "18 distinct states"):
            self.states()

    def test_exactly_sixteen_distinct_states_is_allowed(self):
        for n in range(3):
            self.t.write(f"data/u{n}.dat", store=f"u{n}", thread=str(n))
        self.assertEqual(16, len(self.states()))

    def test_operation_and_byte_budgets(self):
        self.t.write()
        with patch.object(replay, "MAX_OPS", 2), self.assertRaises(replay.OverBound): self.states()
        with patch.object(replay, "MAX_BYTES", 12000), self.assertRaises(replay.OverBound):
            self.t.write("data/b.dat", old=b"o" * 4000, store="B", thread="b"); self.states()

    def test_corrupted_blob_and_preimage_are_rejected(self):
        self.t.write()
        blob = next((self.t.trace / "blobs").iterdir()); original = blob.read_bytes()
        blob.write_bytes(b"wrong")
        with self.assertRaisesRegex(replay.ReplayError, "damaged blob"): self.states()
        blob.write_bytes(original); (self.t.trace / "pre/0").write_bytes(b"wrong")
        with self.assertRaisesRegex(replay.ReplayError, "Damaged pre-image"): self.states()

    def test_missing_absence_ambiguous_pre_and_partial_json_refused(self):
        self.t.write(old=None)
        marker = self.t.trace / "pre/0.absent"; marker.unlink()
        with self.assertRaises(replay.ReplayError): self.states()
        marker.write_bytes(b""); (self.t.trace / "pre/0").write_bytes(b"old")
        with self.assertRaises(replay.ReplayError): self.states()
        (self.t.trace / "pre/0").unlink()
        ops = self.t.trace / "ops.jsonl"; ops.write_bytes(ops.read_bytes()[:-1])
        with self.assertRaisesRegex(replay.ReplayError, "partial"): self.states()

    def test_sequence_offset_length_order_and_results_refused(self):
        self.t.write(); saved = copy.deepcopy(self.t.ops)
        for index, field, value in ((0, "seq", 3), (1, "offset", 1), (1, "length", 1),
                                    (2, "op", "move"), (3, "result", "java.io.IOException"),
                                    (0, "target", "data/wrong.dat"), (5, "target", "data/wrong.dat")):
            with self.subTest(field=field):
                self.t.ops = copy.deepcopy(saved); self.t.ops[index][field] = value; self.t.save()
                with self.assertRaises(replay.ReplayError): self.states()

    def test_kill_image_must_match_and_target_paths_cannot_escape(self):
        self.t.write()
        (self.t.kill / "data/a.dat").write_bytes(b"wrong")
        with self.assertRaises(replay.ReplayError): self.states()
        for name in ("../escape", "D:/file", "/absolute", "data\\file", ""):
            with self.assertRaises(replay.ReplayError): replay.safe_path(self.t.kill, name)

    def test_staging_image_cannot_contradict_the_recorded_move(self):
        self.t.write()
        (self.t.kill / "data/a.dat.arce-pending").write_bytes(b"unexpected")
        with self.assertRaisesRegex(replay.ReplayError, "staging file differs"): self.states()

    def test_state_table_written_with_all_hashes_and_derivations_before_materialization(self):
        self.t.write(); states = self.states()
        table = self.root / "states.json"
        replay.write_table(table, states, "X01-04", campaign.outcomes("X01-04"))
        value = json.loads(table.read_text())
        self.assertEqual(6, len(value["states"]))
        self.assertEqual(replay.MODEL, value["model"])
        self.assertEqual(states[0].files, value["states"][0]["files"])
        self.assertEqual([], value["states"][0]["allowed_outcomes"]["allowed_markers"])

    def test_materializer_refuses_occupied_and_overlapping_worlds(self):
        self.t.write(); state = self.states()[0]
        for destination in (self.t.kill, self.t.kill / "nested", self.t.trace):
            with self.assertRaises(replay.ReplayError): replay.materialize(self.t.kill, destination, state)


class CampaignTests(unittest.TestCase):
    def test_triage_directory_is_new_pinned_sibling_and_original_is_untouched(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(campaign, "EVIDENCE_ROOT", Path(tmp)):
            root = Path(tmp); sha = "a" * 40
            original = root / sha; original.mkdir()
            manifest = original / "MANIFEST.json"
            pins = dict(source_commit=sha, candidate_commit=sha, jar_sha256="b" * 64)
            raw = json.dumps(dict(model=replay.MODEL, status="FAIL", **pins)).encode()
            manifest.write_bytes(raw)
            args = SimpleNamespace(**pins, evidence_dir=root / (sha + "-triage"), triage_of=manifest)
            result = campaign.evidence_target(args)
            self.assertEqual(replay.sha(raw), result["sha256"])
            self.assertEqual(raw, manifest.read_bytes())
            args.triage_of = None
            with self.assertRaises(replay.ReplayError): campaign.evidence_target(args)
            args.evidence_dir = original
            with self.assertRaises(replay.ReplayError): campaign.evidence_target(args)
            args.triage_of = manifest; args.evidence_dir = root / (sha + "-triage")
            args.jar_sha256 = "c" * 64
            with self.assertRaisesRegex(replay.ReplayError, "pins/model"): campaign.evidence_target(args)
            args.jar_sha256 = pins["jar_sha256"]
            args.evidence_dir.mkdir()
            with self.assertRaises(replay.ReplayError): campaign.evidence_target(args)

    def test_triage_refuses_missing_foreign_or_unfinished_manifest(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(campaign, "EVIDENCE_ROOT", Path(tmp)):
            root = Path(tmp); sha = "a" * 40
            original = root / sha; original.mkdir()
            manifest = original / "MANIFEST.json"
            pins = dict(source_commit=sha, candidate_commit=sha, jar_sha256="b" * 64)
            args = SimpleNamespace(**pins, evidence_dir=root / (sha + "-triage"), triage_of=manifest)
            with self.assertRaises(replay.ReplayError): campaign.evidence_target(args)
            manifest.write_text(json.dumps(dict(model=replay.MODEL, status="IN_PROGRESS", **pins)))
            with self.assertRaisesRegex(replay.ReplayError, "not finished"): campaign.evidence_target(args)
            args.triage_of = root / "other.json"; args.triage_of.write_bytes(manifest.read_bytes())
            with self.assertRaises(replay.ReplayError): campaign.evidence_target(args)

    def test_triage_runner_pins_harness_and_product_separately(self):
        args = SimpleNamespace(source_commit="product", candidate_commit="product", jar_sha256="jar",
                               triage_of=Path("MANIFEST.json"), replay_harness_commit="harness")
        actual = campaign.runner_args(args, Path("scratch"), Path("output"), "FF_N1", "owner")
        self.assertEqual("harness", actual.source_commit)
        self.assertEqual("product", actual.candidate_pin["source_commit"])

    def test_case_oracle_uses_only_the_rewound_store_not_an_independent_write(self):
        state = replay.State("old-journal", {}, [dict(kind="S-OLD", store="ROCKET_TRANSFERS")])
        self.assertEqual("B3_MOVED", campaign.side_for(state, "B3_MOVED", "B3_STAGED", "BAY_RECEIPTS"))
        state.derivations = [dict(kind="LATE-LOSS", targets=["data/receipts.dat"])]
        self.assertEqual("B3_STAGED", campaign.side_for(state, "B3_MOVED", "B3_STAGED", "BAY_RECEIPTS", "data/receipts.dat"))

    def test_twelve_case_mapping_and_existing_selections_are_unchanged(self):
        self.assertEqual([f"X01-{i:02d}" for i in range(1, 13)], list(campaign.CASES))
        self.assertEqual(("F", "FC_LOAD_C6_JOINT_READBACK", None, None, ("RESOURCE_ESCROW",)), campaign.CASES["X01-07"])
        for alias, recorded, old, _, _ in campaign.CASES.values():
            self.assertIn(recorded, campaign.RUNNERS[alias].CASES)
            if old not in (None, "ITEM_EARLIER"):
                self.assertIn(old, campaign.RUNNERS[alias].CASES)
        self.assertEqual(("registry", "STAGED", "PAUSE", "kill", 0, True), campaign.registry.CASES["R2"])

    def test_case_plan_requires_the_recorded_moved_store(self):
        with tempfile.TemporaryDirectory() as tmp:
            t = Trace(Path(tmp)); t.write(store="BAY_RECEIPTS")
            self.assertEqual(6, len(campaign.case_plan(t.trace, t.kill, "X01-01")))
            with self.assertRaises(replay.ReplayError): campaign.case_plan(t.trace, t.kill, "X01-04")
            with self.assertRaises(replay.ReplayError): campaign.case_plan(t.trace, t.kill, "X01-07")

    def test_late_loss_window_starts_after_bootstrap(self):
        with tempfile.TemporaryDirectory() as tmp:
            t = Trace(Path(tmp)); t.write(old=None, data=b"baseline", complete=True)
            start = t.write(old=b"baseline", data=b"intent", complete=True)
            states = campaign.case_plan(t.trace, t.kill, "X01-07")
            self.assertEqual(b"baseline", states[0].changes["data/a.dat"])
            self.assertEqual(start, states[0].derivations[0]["window_start"])

    def test_lock_owns_its_directory_and_does_not_break_a_foreign_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "lock"
            with campaign.machine_lock(path) as token:
                self.assertEqual(token, (path / "owner.txt").read_text())
            self.assertFalse(path.exists())
            path.mkdir(); (path / "owner.txt").write_text("foreign")
            with self.assertRaises(replay.ReplayError):
                with campaign.machine_lock(path, timeout=0): pass
            self.assertEqual("foreign", (path / "owner.txt").read_text())

    def test_lock_never_releases_changed_ownership(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "lock"
            with campaign.machine_lock(path): (path / "owner.txt").write_text("replacement")
            self.assertTrue(path.exists())

    def test_trace_property_only_applies_to_recording_start(self):
        args = SimpleNamespace(storage_trace=Path("trace"))
        for restart in (0, 1, 2):
            command = ["java", "-Xmx4G"]
            replay.trace_command(args, command, restart)
            self.assertEqual(restart == 0, any("storageTrace=" in x for x in command))

    def test_replay_api_materializes_once_and_selects_exactly_two_restarts(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); t = Trace(root); t.write()
            context = dict(starts=[dict(restart=0, disk=dict(identity="same"))], raw0=dict(ledger="root"))
            args = SimpleNamespace(replay_world=t.kill, replay_state=replay.plan(t.trace, t.kill)[0], replay_context=context)
            row = dict(starts=[]); evidence = root / "evidence"; evidence.mkdir()
            self.assertEqual((1, 2), replay.replay_starts(args, row, root / "world", evidence, "case"))
            self.assertEqual(context["starts"], row["starts"])
            self.assertEqual(context["raw0"], json.loads((evidence / "case-0-raw.json").read_text()))
            self.assertEqual([0, 1, 2], list(replay.replay_starts(SimpleNamespace(), {}, root / "unused", evidence, "case")))

    def test_capture_api_preserves_context_and_no_capture_is_noop(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); (root / "case-0-raw.json").write_text('{"ledger": 1}')
            callback = Mock(); row = dict(starts=[dict(restart=0)])
            self.assertTrue(replay.capture_replay(SimpleNamespace(replay_capture=callback), root, row, root, "case"))
            self.assertEqual(dict(ledger=1), callback.call_args.args[1]["raw0"])
            self.assertFalse(replay.capture_replay(SimpleNamespace(), root, row, root, "case"))

    def test_item_earlier_oracles_use_existing_typed_conservation_checks(self):
        module = campaign.item
        disk = dict(ledger=dict(rocket_authorities=[], armed_flights=[], issued_launch_serial=0), raw_total=70,
                    rocket=dict(snapshot=dict(relative_blocks=[dict(block_entity=dict(data=dict(Items=[])))])),
                    device=dict(buffer=dict(slots=[])))
        with patch.object(module.native, "inspect", return_value=disk) as inspect, patch.object(module.oracle, "exact_stack") as stack:
            for case, probe in (("IC_LOAD_K2", "IC_LOAD_L1_BEFORE"), ("IC_UNLOAD_K9", "IC_UNLOAD_C6_JOINT_READBACK")):
                self.assertEqual(disk, module.replay_old_check(Path("world"), case))
                inspect.assert_called_with(Path("world"), probe, True)
            self.assertEqual(4, stack.call_count)
            disk["raw_total"] = 69
            with self.assertRaises(module.native.smoke.SmokeError): module.replay_old_check(Path("world"), "IC_LOAD_K2")

    def test_item_earlier_probe_observes_its_actual_case_without_rewriting_logs(self):
        module = campaign.item
        line = "ARCE_RELEASE_CARGO_RESULT cut=IC_LOAD_L1_BEFORE cargo=60 device=10 epoch=0 records=0"
        module.observations([line], "IC_LOAD_K2", 1, module.replay_old_probe("IC_LOAD_K2"))
        with self.assertRaises(module.native.smoke.SmokeError): module.observations([line], "IC_LOAD_K2", 1)
        with self.assertRaises(module.native.smoke.SmokeError):
            module.observations([line, line], "IC_LOAD_K2", 1, "IC_LOAD_L1_BEFORE")

    def test_python_cargo_runners_restart_without_recording_or_touching_oracles(self):
        for alias, case, old_item in (("F", "FC_LOAD_L1_BEFORE", False), ("I", "IC_LOAD_K2", False),
                                      ("I", "IC_LOAD_K2", True), ("S", "FS_O3J_BEFORE", False)):
            with self.subTest(alias=alias), tempfile.TemporaryDirectory() as tmp, ExitStack() as patches:
                root = Path(tmp); t = Trace(root); t.write()
                (root / "template/libraries").mkdir(parents=True)
                jar = root / "test.jar"; jar.write_bytes(b"jar")
                args = SimpleNamespace(source_commit="sha", lock_owner="owner", source_server=root / "template", jar=jar,
                    session_dir=root / "session", evidence_dir=root / "evidence", java="java", startup_timeout=240,
                    case=[case], dev=False, replay_old_item=old_item, replay_world=t.kill, replay_state=replay.plan(t.trace, t.kill)[0],
                    replay_context=dict(starts=[dict(restart=0, disk=dict(identity="same"))], raw0={}))
                module = campaign.RUNNERS[alias]
                native = module if alias == "S" else module.native
                smoke = native.smoke
                process = Mock(); process.finish.return_value = 0; process.lines = []
                for name, value in (("assert_pin", None), ("require_lock", None), ("preflight", {})):
                    patches.enter_context(patch.object(native, name, return_value=value))
                patches.enter_context(patch.object(native, "digest", return_value=module.evidence_tools.file_sha256(jar)))
                starts = patches.enter_context(patch.object(smoke, "CapturedProcess", return_value=process))
                patches.enter_context(patch.object(smoke, "allocate_port", return_value=25565))
                patches.enter_context(patch.object(smoke, "write_server_configuration"))
                observations = patches.enter_context(patch.object(module, "observations"))
                if alias == "I":
                    patches.enter_context(patch.object(module, "retained_files", return_value={}))
                    patches.enter_context(patch.object(module.oracle, "read", return_value={}))
                    check = patches.enter_context(patch.object(module, "replay_old_check" if old_item else "check", return_value=dict(identity="same")))
                elif alias == "F":
                    patches.enter_context(patch.object(module, "raw_snapshot", return_value={}))
                    check = patches.enter_context(patch.object(module, "inspect", return_value=dict(identity="same")))
                else:
                    # Use the existing test's complete, independently constructed settlement fixture.
                    from test_cargo_s4_s5_cuts_runner import released_disk
                    patches.enter_context(patch.object(module, "raw_snapshot", return_value={}))
                    patches.enter_context(patch.object(module, "inspect", return_value=released_disk()))
                    patches.enter_context(patch.object(module, "same_identity", return_value=True))
                    check = patches.enter_context(patch.object(module, "check"))
                module.run(args)
                self.assertEqual(2, starts.call_count)
                self.assertEqual(2, check.call_count); self.assertEqual(2, observations.call_count)
                process.process.kill.assert_not_called()
                for call in starts.call_args_list:
                    self.assertIn("-Dadvancedrocketrycommunity.escrowScenario=recover", call.args[0])
                    if old_item:
                        self.assertIn("-Dadvancedrocketrycommunity.escrowCase=IC_LOAD_L1_BEFORE", call.args[0])
                self.assertFalse(args.session_dir.exists())

    def test_bay_restart_only_reuses_both_existing_restart_checks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); t = Trace(root); t.write(store="BAY_RECEIPTS")
            session = root / "session"; session.mkdir(); evidence = root / "evidence"; evidence.mkdir()
            fixture = dict(logical="l", transfer="t", data="d")
            args = SimpleNamespace(replay_world=t.kill, replay_state=replay.plan(t.trace, t.kill)[0],
                replay_context=dict(fixture=fixture, first_start={}), exit_timeout=180)
            process = Mock(); process.lines = []
            run = Mock(session=session, evidence=evidence, port=25565, args=args)
            run.start.return_value = process; run.end.return_value = 0
            run.pending.return_value = dict(pending=0, retained=0, work=False)
            with patch.object(campaign.bay, "first_start", side_effect=AssertionError("first start forbidden")), \
                    patch.object(campaign.bay, "first_tick_satellite", return_value=None), \
                    patch.object(campaign.bay.evidence_tools, "snapshot_world", return_value={}), \
                    patch.object(campaign.bay, "settled_disk", return_value={}) as check:
                result = campaign.bay.run_case(run, "B1_STAGED")
            self.assertEqual("PASS", result["status"])
            self.assertEqual(2, check.call_count); self.assertEqual(2, run.await_settled.call_count)
            self.assertEqual([1, 2], [call.args[1] for call in run.start.call_args_list])

    def test_registry_old_restart_keeps_zero_satellites_and_the_bound_terminal_package(self):
        for package in ("bound", "empty"):
            with self.subTest(package=package), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp); t = Trace(root); t.write(store="SATELLITE_MISSIONS")
                session = root / "session"; session.mkdir()
                process = Mock(); process.lines = []
                run = Mock(session=session, port=25565, args=SimpleNamespace(replay_world=t.kill,
                    replay_state=replay.plan(t.trace, t.kill)[0], replay_context=dict(first_start={}),
                    replay_old_registry=True, exit_timeout=180))
                run.start.return_value = process; run.end.return_value = 0
                run.status.return_value = dict(acknowledged="0", satellites=0, terminal="present", package=package)
                run.proved.return_value = dict(satellites=0)
                disk = dict(satellites=0, ids=[], staging=False, mars_discovered=False)
                with patch.object(campaign.registry, "first_start", side_effect=AssertionError("first start forbidden")), \
                        patch.object(campaign.registry, "read_data", return_value={}), \
                        patch.object(campaign.registry, "registry_view", return_value=disk.copy()), \
                        patch.object(campaign.registry, "inspect", return_value=disk):
                    if package == "empty":
                        with self.assertRaises(campaign.bay.smoke.SmokeError): campaign.registry.run_case(run, "R2")
                    else:
                        result = campaign.registry.run_case(run, "R2")
                        self.assertEqual("PASS", result["status"])
                        self.assertEqual([1, 2], [call.args[1] for call in run.start.call_args_list])

    def test_campaign_writes_table_before_starting_and_deletes_each_copy(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); t = Trace(root / "source"); t.write(store="BAY_RECEIPTS")
            evidence = root / "evidence"; evidence.mkdir()
            scratch = root / "scratch"; scratch.mkdir()
            args = SimpleNamespace(evidence_dir=evidence, candidate_commit="candidate", jar_sha256="jar")
            seen = []
            context = dict(fixture={}, first_start={})

            def existing(module, supplied, output):
                output.mkdir()
                if hasattr(supplied, "replay_capture"):
                    shutil.copytree(t.trace, supplied.storage_trace)
                    supplied.replay_capture(t.kill, context)
                    entries = []
                else:
                    table = json.loads((evidence / "case/states.json").read_text())
                    self.assertEqual(6, len(table["states"]))
                    self.assertTrue(all(not path.exists() for path in seen))
                    world = supplied.session_dir / "world"; seen.append(world)
                    replay.materialize(supplied.replay_world, world, supplied.replay_state)
                    shutil.rmtree(supplied.session_dir)
                    entries = [dict(restart=n, status="PASS") for n in (1, 2)]
                    supplied.replay_commands.extend(dict(restart=n, command=["java"]) for n in (1, 2))
                return dict(status="PASS", cases=[dict(restarts=entries)]), None

            with patch.object(campaign, "TEMP", scratch), patch.object(campaign, "run_existing", side_effect=existing), \
                    patch.object(campaign, "preflight"):
                result = campaign.run_case(args, "X01-01", evidence / "case", scratch / "case", "owner")
            self.assertEqual("PASS", result["status"])
            self.assertEqual(6, len(seen)); self.assertEqual(12, result["starts"])
            self.assertFalse((scratch / "case/kill").exists())
            rows = [json.loads(line) for line in (evidence / "case/results.jsonl").read_text().splitlines()]
            self.assertEqual(12, len(rows)); self.assertTrue(all(r["status"] == "PASS" for r in rows))

    def test_failed_first_bay_restart_still_starts_the_modified_image_a_second_time(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); t = Trace(root / "source"); t.write(store="BAY_RECEIPTS")
            evidence = root / "evidence"; evidence.mkdir()
            scratch = root / "scratch"; scratch.mkdir()
            args = SimpleNamespace(evidence_dir=evidence, candidate_commit="candidate", jar_sha256="jar")
            continued = []

            def existing(module, supplied, output):
                output.mkdir()
                if hasattr(supplied, "replay_capture"):
                    shutil.copytree(t.trace, supplied.storage_trace)
                    supplied.replay_capture(t.kill, dict(fixture={}, first_start={}))
                    return dict(status="PASS", cases=[]), None
                if hasattr(supplied, "replay_restart_numbers"):
                    continued.append(supplied.replay_restart_numbers)
                    self.assertEqual(b"written-at-start-one", (supplied.replay_world / "continuity").read_bytes())
                    self.assertEqual({}, supplied.replay_state.changes)
                    supplied.replay_commands.append(dict(restart=2, command=["java"]))
                    return dict(status="PASS", cases=[dict(restarts=[dict(restart=2)])]), None
                if supplied.replay_state.id.endswith("S-OLD"):
                    world = supplied.session_dir / "world"
                    replay.materialize(supplied.replay_world, world, supplied.replay_state)
                    (world / "continuity").write_bytes(b"written-at-start-one")
                    supplied.replay_commands.append(dict(restart=1, command=["java"]))
                    supplied.replay_preserve(world)
                    shutil.rmtree(supplied.session_dir)
                    return dict(status="FAILED", cases=[]), "first restart oracle rejected"
                supplied.replay_commands.extend(dict(restart=n, command=["java"]) for n in (1, 2))
                return dict(status="PASS", cases=[dict(restarts=[dict(restart=n) for n in (1, 2)])]), None

            with patch.object(campaign, "TEMP", scratch), patch.object(campaign, "run_existing", side_effect=existing), \
                    patch.object(campaign, "preflight"):
                result = campaign.run_case(args, "X01-01", evidence / "case", scratch / "case", "owner")
            self.assertEqual("FAIL", result["status"]); self.assertEqual([(2,)], continued)
            rows = [json.loads(line) for line in (evidence / "case/results.jsonl").read_text().splitlines()]
            self.assertEqual(["FAIL", "PASS"], [row["status"] for row in rows[:2]])

    def test_log_reduction_is_explicit_and_never_reduces_failed_state_logs(self):
        import gzip
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); evidence = root / "cases/X01-01"; logs = evidence / "logs"; logs.mkdir(parents=True)
            rows = [dict(state="pass", status="PASS"), dict(state="fail", status="FAIL")]
            (evidence / "results.jsonl").write_text("".join(json.dumps(r) + "\n" for r in rows))
            raw = b"ordinary detail\nARCE_MARKER proof\n"
            for state in ("pass", "fail"):
                (logs / f"{state}-1.log.gz").write_bytes(gzip.compress(raw))
            with patch.object(campaign, "LIMIT", 1), self.assertRaises(replay.OverBound): campaign.check_size(root)
            self.assertEqual(b"ARCE_MARKER proof\n", gzip.decompress((logs / "pass-1.log.gz").read_bytes()))
            self.assertEqual(raw, gzip.decompress((logs / "fail-1.log.gz").read_bytes()))
            self.assertTrue((root / "LOG-REDUCTION.json").is_file())


if __name__ == "__main__":
    unittest.main()
