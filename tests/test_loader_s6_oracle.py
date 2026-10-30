"""S6 raw-state rejection tests; no native server is needed."""
from pathlib import Path
import copy
import gzip
import json
import struct
import tempfile
from unittest.mock import patch
import sys
import unittest
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import loader_s6_oracle as oracle
from test_v180_loader_cuts import fixture
from run_dedicated_server_smoke import SmokeError


class LoaderS6OracleTest(unittest.TestCase):
    # Exact native op_id from R186 E5 attempt 1, start 0, healthy pair.
    # Retained result.json SHA-256 is recorded in W211's R188 continuation.
    NATIVE_OP = [1142397210, -1093579555, -2135548581, -1618621573]

    def image(self, cut="LF_M_K6", restart=0):
        # Full native-form seed, with only the selected prefix varied in memory.
        native = json.loads((Path(__file__).parent / "fixtures/loader_s6/integrity-native.json").read_text())
        image = oracle.normalize_ids(copy.deepcopy(native["e3"]["images"][0]))
        disk = image["native"]; exp = oracle.legacy.expected(cut, restart)
        op = disk["ledger"]["records"][0]
        root = disk["ledger"]
        root.update(issued_through=exp["issued"], records=[op] if exp["records"] else [],
                    writer_generation=1 if not exp["issued"] else 2 if exp["records"] else 3)
        source = disk["loaders"][0]["arce_fluid_fuel_loader"]
        data = disk["rocket"]["RocketEntityData"]; fuel = data["flight_data"]["fuel"]
        source["tank"]["Amount"] = 5000 - 1000 * exp["source"]
        fuel["amount"] = 1000 * exp["destination"]
        fuel["propulsion"].update(primary=oracle.integrity.mono_bank(fuel["amount"]), revision=exp["destination"])
        for section, after, role in ((source["resource_escrow"], exp["source"], "SOURCE"),
                                     (data["resource_escrow"], exp["destination"], "DESTINATION")):
            section.clear(); section.update(schema_version=1, resource_epoch=after, markers=[])
            if after:
                section["ledger_id"] = root["ledger_id"]
                if exp["markers"]:
                    section["markers"] = [dict(op_id=op["op_id"], seq=1, epoch_after=1, role=role)]
        return image

    def test_all_four_locked_durable_prefixes_are_distinguished(self):
        for name, available in (("K2", 5000), ("K4", 4000), ("K5", 6000), ("K6", 5000)):
            with self.subTest(name=name):
                result = oracle.check_e3(self.image("LF_M_" + name), 0)
                self.assertEqual(name, result["prefix"])
                self.assertEqual(available, result["raw_total"])
                self.assertIsNone(result["available_total"])
                self.assertIsNone(result["loss"])

    def test_restarts_require_exact_completed_resources(self):
        before = oracle.check_e3(self.image(), 0)
        for restart in (1, 2):
            result = oracle.check_e3(self.image("LF_M_K7", restart), restart, before["identity"])
            self.assertEqual(5000, result["available_total"])
            self.assertEqual(0, result["loss"])

    def test_changed_quantity_fails(self):
        image = self.image()
        image["native"]["loaders"][0]["arce_fluid_fuel_loader"]["tank"]["Amount"] += 1
        with self.assertRaises(SmokeError):
            oracle.check_e3(image, 0)

    def test_changed_marker_fields_fail(self):
        for key, value in (("op_id", "other"), ("seq", 2), ("role", "DESTINATION"), ("epoch_after", 2)):
            image = self.image()
            image["native"]["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"]["markers"][0][key] = value
            with self.subTest(key=key), self.assertRaises(SmokeError):
                oracle.check_e3(image, 0)

    def test_changed_epoch_fails(self):
        image = self.image()
        image["native"]["rocket"]["RocketEntityData"]["resource_escrow"]["resource_epoch"] += 1
        with self.assertRaises(SmokeError):
            oracle.check_e3(image, 0)

    def test_consistently_changed_identity_between_restarts_fails(self):
        before = oracle.check_e3(self.image(), 0)["identity"]
        for field in ("device", "owner", "ledger", "physical", "logical"):
            image = self.image("LF_M_K7", 1)
            raw = image["native"]
            loader = raw["loaders"][0]["arce_fluid_fuel_loader"]
            rocket = raw["rocket"]["RocketEntityData"]
            if field == "device":
                loader["device"] = "other"
            elif field == "owner":
                loader["owner"] = rocket["owner_id"] = "other"
            elif field == "ledger":
                raw["ledger"]["ledger_id"] = loader["resource_escrow"]["ledger_id"] = rocket["resource_escrow"]["ledger_id"] = "other"
            elif field == "physical":
                raw["rocket"]["UUID"] = loader["binding"]["physical_rocket"] = "other"
            else:
                rocket["assembly_transaction_id"] = rocket["flight_data"]["logical_rocket_id"] = loader["binding"]["logical_rocket"] = "other"
            with self.subTest(field=field), self.assertRaises(SmokeError):
                oracle.check_e3(image, 1, before)

    def test_unknown_read_is_not_empty_or_zero_loss(self):
        for image in ({"read_status": "UNKNOWN", "native": None},
                      dict(self.image(), read_status="UNKNOWN"), {}, {"read_status": "KNOWN", "native": None}):
            with self.subTest(image=image), self.assertRaises(SmokeError):
                oracle.check_e3(image, 0)

    def test_missing_ledger_or_operation_cannot_unlock_cut(self):
        for root in (None, dict(self.image()["native"]["ledger"], records=[])):
            image = self.image()
            image["native"]["ledger"] = root
            with self.assertRaises(SmokeError):
                oracle.check_e3(image, 0)

    def test_mutated_raw_total_cannot_cancel_between_endpoints(self):
        image = self.image()
        image["native"]["loaders"][0]["arce_fluid_fuel_loader"]["tank"]["Amount"] -= 1
        image["native"]["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"]["amount"] += 1
        with self.assertRaises(SmokeError):
            oracle.check_e3(image, 0)

    def test_inspection_failure_stays_unknown(self):
        from unittest.mock import patch
        with patch.object(oracle.legacy, "inspect", side_effect=SmokeError("unreadable")):
            image = oracle.inspect(Path("missing"))
        self.assertEqual("UNKNOWN", image["read_status"])
        self.assertIsNone(image["native"])
        self.assertIn("unreadable", image["error"])

    def test_missing_or_duplicate_failure_observation_is_rejected(self):
        lines = ["ARCE_LOADER_S6_E3 records=1 pending=1 issued=1 io_failures=1 reason=BARRIER_IO proof=false",
                 "ARCE_LOADER_S6_CACHE regions=256 own_region_cached=false",
                 "[IO-Worker/ERROR] Failed to store chunk [4, 4]", "java.nio.channels.ClosedChannelException"]
        oracle.check_observations(lines, 0)
        for changed in (lines[1:], lines + [lines[0]], lines + ["[Server/ERROR] unrelated failure"]):
            with self.assertRaises(SmokeError):
                oracle.check_observations(changed, 0)

    def test_runner_counts_failures_and_stops_after_two_attempts(self):
        from contextlib import nullcontext
        import tempfile
        from types import SimpleNamespace
        from unittest.mock import patch
        import run_v180_loader_s6 as runner
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sessions = root / "sessions"
            sessions.mkdir()
            args = SimpleNamespace(source_commit="pinned", jar=root / "a.jar", session_parent=sessions,
                                   evidence_dir=root / "evidence", case=[oracle.E3, "NOT_REACHED"])
            row = dict(status="FAILED", starts=[dict(restart=i) for i in range(3)])
            with patch.object(runner.shared, "assert_pin"), patch.object(runner.shared, "digest", return_value="hash"), \
                    patch.object(runner.shared, "heavy_lock", side_effect=lambda _: nullcontext()), \
                    patch.object(runner, "run_cut", return_value=row) as run_cut, patch.object(runner.time, "sleep") as sleep:
                result = runner.run(args)
            self.assertEqual("STOPPED_AFTER_TWO_FAILURES", result["status"])
            self.assertEqual(6, result["starts"])
            self.assertEqual(4, result["restarts"])
            self.assertEqual(2, run_cut.call_count)
            self.assertEqual([90, 90], [call.args[0] for call in sleep.call_args_list])

    def recovery_lines(self):
        return ["ARCE_LOADER_S6_BEGIN tick=1",
            "ARCE_LOADER_S6_ACCOUNT tick=1 known=false reason=UNLOADED raw=UNKNOWN available=UNKNOWN held=UNKNOWN",
        ] + [f"ARCE_LOADER_S6_ACCOUNT tick={tick} known=true records=1 q=1000 source=5000 destination=1000 source_available=4000 source_held=1000 destination_available=0 destination_held=1000 in_transit=0 spendable=UNKNOWN" for tick in range(2, 101)] + [
            "ARCE_LOADER_S6_ACCOUNT tick=101 known=true records=0 q=null source=4000 destination=1000 source_available=4000 source_held=0 destination_available=1000 destination_held=0 in_transit=0 spendable=5000",
            "ARCE_LOADER_S6_RESULT cut=" + oracle.E3 + " observations=100 total=5000 records=0 markers=0 source_epoch=1 destination_epoch=1 start_tick=1 end_tick=101 total_observations=101",
        ]

    def test_recovery_log_requires_record_backed_every_tick_partition(self):
        oracle.check_observations(self.recovery_lines(), 1)

    def test_recovery_log_rejects_overcredit_or_fabricated_held_or_unknown_zero(self):
        for old, new in (("destination=1000", "destination=1001"), ("q=1000", "q=999"),
                         ("source_held=0", "source_held=1"), ("source=4000", "source=3999"),
                         ("raw=UNKNOWN", "raw=0"), ("tick=3", "tick=4"),
                         ("observations=100", "observations=99"), ("spendable=UNKNOWN", "spendable=6000")):
            lines = [line.replace(old, new) for line in self.recovery_lines()]
            with self.subTest(change=new), self.assertRaises(SmokeError):
                oracle.check_observations(lines, 1)

    def test_runner_stops_on_unexplained_record_backed_image(self):
        from contextlib import nullcontext
        import tempfile
        from types import SimpleNamespace
        from unittest.mock import patch
        import run_v180_loader_s6 as runner
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sessions = root / "sessions"
            sessions.mkdir()
            args = SimpleNamespace(source_commit="pinned", jar=root / "a.jar", session_parent=sessions,
                                   evidence_dir=root / "evidence", case=[oracle.E3])
            row = dict(status="FAILED", starts=[dict(restart=i) for i in range(3)], unexplained_image=True)
            with patch.object(runner.shared, "assert_pin"), patch.object(runner.shared, "digest", return_value="hash"), \
                    patch.object(runner.shared, "heavy_lock", side_effect=lambda _: nullcontext()), \
                    patch.object(runner, "run_cut", return_value=row) as run_cut, patch.object(runner.time, "sleep"):
                result = runner.run(args)
            self.assertEqual("STOPPED_UNEXPLAINED_IMAGE", result["status"])
            self.assertEqual(3, result["starts"])
            self.assertEqual(2, result["restarts"])
            self.assertEqual(1, run_cut.call_count)

    def test_unload_diagnostic_is_one_start_without_fairness_pass_or_retry(self):
        from contextlib import nullcontext
        import tempfile
        from types import SimpleNamespace
        from unittest.mock import patch
        import run_v180_loader_s6 as runner
        for outcome in ("DIAGNOSTIC_OBSERVED", "FAILED"):
            with self.subTest(outcome=outcome), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                sessions = root / "sessions"
                sessions.mkdir()
                args = SimpleNamespace(source_commit="pinned", jar=root / "a.jar", session_parent=sessions,
                                       evidence_dir=root / "evidence", case=[oracle.E5], diagnose_unload=True)
                row = dict(status=outcome, starts=[dict(restart=0)])
                with patch.object(runner.shared, "assert_pin"), patch.object(runner.shared, "digest", return_value="hash"), \
                        patch.object(runner.shared, "heavy_lock", side_effect=lambda _: nullcontext()), \
                        patch.object(runner, "run_cut", return_value=row) as run_cut, patch.object(runner.time, "sleep"):
                    result = runner.run(args)
                self.assertEqual(outcome, result["status"])
                self.assertEqual(1, result["starts"])
                self.assertEqual(0, result["restarts"])
                self.assertEqual(1, run_cut.call_count)
                self.assertIn("not fairness", result["coverage"])

    def fairness_image(self, restart=0):
        native = json.loads((Path(__file__).parent / "fixtures/loader_s6/integrity-native.json").read_text())
        image = oracle.normalize_ids(copy.deepcopy(native["e5"]["images"][restart]))
        # Live healthy completion may be saved before or after retired metadata pruning.
        healthy = image["native"]["pairs"][1]
        healthy["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"]["markers"] = []
        healthy["rocket"]["RocketEntityData"]["resource_escrow"]["markers"] = []
        return image

    def test_fairness_counts_each_pair_and_preserves_distinct_identities(self):
        initial = oracle.check_e5(self.fairness_image(), 0)
        self.assertIsNone(initial["available_total"])
        for restart in (1, 2):
            result = oracle.check_e5(self.fairness_image(restart), restart, initial["identity"])
            self.assertEqual([5000, 5000], result["pair_totals"])
            self.assertEqual(10000, result["available_total"])

    def test_fairness_cannot_offset_loss_between_loaders_or_accept_unknown(self):
        image = self.fairness_image(1)
        image["native"]["pairs"][0]["loaders"][0]["arce_fluid_fuel_loader"]["tank"]["Amount"] += 1
        image["native"]["pairs"][1]["loaders"][0]["arce_fluid_fuel_loader"]["tank"]["Amount"] -= 1
        with self.assertRaises(SmokeError):
            oracle.check_e5(image, 1)
        image = self.fairness_image()
        image["read_status"] = "UNKNOWN"
        with self.assertRaises(SmokeError):
            oracle.check_e5(image, 0)

    def test_fairness_rejects_changed_identity_marker_epoch_and_payload(self):
        before = oracle.check_e5(self.fairness_image(), 0)["identity"]
        for field in ("device", "marker", "epoch", "payload"):
            image = self.fairness_image()
            source = image["native"]["pairs"][0]["loaders"][0]["arce_fluid_fuel_loader"]
            if field == "device": source["device"] = "changed"
            elif field == "marker": source["resource_escrow"]["markers"][0]["seq"] = 2
            elif field == "epoch": source["resource_escrow"]["resource_epoch"] = 2
            else: image["native"]["ledger"]["records"][0]["payload"]["Amount"] = 1001
            with self.subTest(field=field), self.assertRaises(SmokeError):
                oracle.check_e5(image, 0, before)

    def test_fairness_distinguishes_retired_native_markers_from_live_completion(self):
        image = self.fairness_image()
        pair = image["native"]["pairs"][1]
        source = pair["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"]
        destination = pair["rocket"]["RocketEntityData"]["resource_escrow"]
        for section, role in ((source, "SOURCE"), (destination, "DESTINATION")):
            section["markers"] = [dict(op_id="healthy-op", seq=2, role=role, epoch_after=1)]
        oracle.check_e5(image, 0)
        destination["markers"][0]["op_id"] = "other"
        with self.assertRaises(SmokeError): oracle.check_e5(image, 0)
        image = self.fairness_image(1)
        image["native"]["pairs"][1]["rocket"]["RocketEntityData"]["resource_escrow"]["markers"] = source["markers"]
        with self.assertRaises(SmokeError): oracle.check_e5(image, 1)

    def native_markers_image(self):
        image = self.fairness_image()
        pair = image["native"]["pairs"][1]
        for section, role in ((pair["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"], "SOURCE"),
                              (pair["rocket"]["RocketEntityData"]["resource_escrow"], "DESTINATION")):
            section["markers"] = [dict(op_id=list(self.NATIVE_OP), seq=2, role=role, epoch_after=1)]
        return image

    def test_retained_native_integer_array_markers_are_counted_without_mutation(self):
        image = self.native_markers_image()
        original = copy.deepcopy(image)
        result = oracle.check_e5(image, 0)
        self.assertEqual([5000, 5000], result["pair_totals"])
        self.assertIsNone(result["available_total"])
        self.assertEqual(original, image)

    def test_uuid_canonical_value_preserves_signed_bits_and_string_form(self):
        # Independently computed from the four signed big-endian words, not by the oracle.
        expected = str(uuid.UUID(bytes=b"".join(x.to_bytes(4, "big", signed=True) for x in self.NATIVE_OP)))
        self.assertEqual(expected, oracle.canonical_uuid(self.NATIVE_OP))
        self.assertEqual(expected, oracle.canonical_uuid(expected.upper()))
        self.assertEqual(expected, oracle.canonical_uuid(tuple(self.NATIVE_OP)))
        self.assertNotEqual(expected, oracle.canonical_uuid([*self.NATIVE_OP[:3], self.NATIVE_OP[3] + 1]))

    def test_malformed_integer_arrays_cannot_alias_a_uuid(self):
        for value in ([], self.NATIVE_OP[:3], self.NATIVE_OP + [0], [True, 0, 0, 0],
                      [1.0, 0, 0, 0], [2**32, 0, 0, 0], [-2**31-1, 0, 0, 0], None):
            with self.subTest(value=value), self.assertRaises(SmokeError):
                oracle.canonical_uuid(value)

    def test_mixed_native_and_text_markers_still_reject_conflicts_and_live_ids(self):
        image = self.native_markers_image()
        far_op = image["native"]["ledger"]["records"][0]
        destination = image["native"]["pairs"][1]["rocket"]["RocketEntityData"]["resource_escrow"]["markers"][0]
        text = str(uuid.UUID(bytes=b"".join(x.to_bytes(4, "big", signed=True) for x in self.NATIVE_OP)))
        destination["op_id"] = text
        oracle.check_e5(image, 0)
        destination["op_id"] = str(uuid.UUID(int=uuid.UUID(text).int + 1))
        with self.assertRaises(SmokeError): oracle.check_e5(image, 0)
        destination["op_id"] = text
        far_op["op_id"] = text
        far = image["native"]["pairs"][0]
        far["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"]["markers"][0]["op_id"] = list(self.NATIVE_OP)
        far["rocket"]["RocketEntityData"]["resource_escrow"]["markers"][0]["op_id"] = text
        with self.assertRaises(SmokeError): oracle.check_e5(image, 0)

    def test_all_identity_fields_normalize_without_changing_nonidentity_arrays(self):
        # Includes native_binding and the string-form snapshot ID found in native images,
        # plus the returned identity envelope used across JSON replay/restarts.
        keys = ("UUID", "ledger_id", "op_id", "identity", "owner", "owner_id", "device",
                "physical_rocket", "logical_rocket", "logical_rocket_id", "assembly_transaction_id",
                "rocket_id", "physical", "logical", "snapshot", "snapshot_id", "ledger")
        text = str(uuid.UUID(bytes=b"".join(x.to_bytes(4, "big", signed=True) for x in self.NATIVE_OP)))
        raw = dict(nested=[{k: list(self.NATIVE_OP) for k in keys}], amount=list(self.NATIVE_OP),
                   snapshot=dict(snapshot_id=text.upper()), hash=[1, 2, 3, 4])
        result = oracle.normalize_ids(raw)
        self.assertEqual({k: text for k in keys}, result["nested"][0])
        self.assertEqual(dict(snapshot_id=text), result["snapshot"])
        self.assertEqual(raw["amount"], result["amount"])
        self.assertEqual(raw["hash"], result["hash"])
        self.assertEqual(self.NATIVE_OP, raw["nested"][0]["op_id"])

    def test_e3_and_e5_normalize_every_identity_before_legacy_checks_and_restart_comparison(self):
        def convert(value, as_array):
            # The synthetic fixture's labels stand for UUIDs. Keep non-ID strings unchanged.
            if isinstance(value, dict): return {k: convert(v, as_array) for k, v in value.items()}
            if isinstance(value, list): return [convert(v, as_array) for v in value]
            if isinstance(value, str) and len(value) == 36 and value.count("-") == 4:
                ident = uuid.UUID(value)
                return [int.from_bytes(ident.bytes[i:i+4], "big", signed=True) for i in range(0, 16, 4)] if as_array else str(ident)
            return value
        for check, make in ((oracle.check_e3, lambda r: self.image("LF_M_K7" if r else "LF_M_K6", r)),
                            (oracle.check_e5, self.fairness_image)):
            with self.subTest(oracle=check.__name__):
                initial = check(convert(make(0), True), 0)
                expected = check(convert(make(0), False), 0)["identity"]
                self.assertEqual(expected, initial["identity"])
                for restart in (1, 2):
                    result = check(convert(make(restart), False), restart, initial["identity"])
                    self.assertEqual(expected, result["identity"])
                # A consistently changed, valid native identity must still fail.
                changed = convert(make(1), True)
                raw = make(1)["native"]
                old = convert((raw.get("pairs", [raw]))[0]["rocket"]["RocketEntityData"]["owner_id"], True)
                def replace(v):
                    if v == old: return list(self.NATIVE_OP)
                    if isinstance(v, dict): return {k: replace(x) for k, x in v.items()}
                    if isinstance(v, list): return [replace(x) for x in v]
                    return v
                with self.assertRaises(SmokeError): check(replace(changed), 1, initial["identity"])

    def r2_image(self, cut):
        image = self.image("LF_M_K7", 1)
        disk = image["native"]
        data = disk["rocket"]["RocketEntityData"]
        data["snapshot"] = dict(snapshot_id="source-snapshot", source_origin=[64, 100, 64])
        arrival = copy.deepcopy(data["flight_data"])
        arrival["fuel"]["propulsion"]["primary"]["amount"] = 0
        record = dict(transfer_id="transfer", phase="COMMITTED" if "DESTRUCTIVE" in cut else "PREPARED",
                      source_entity_id=disk["rocket"]["UUID"], logical_rocket_id=data["assembly_transaction_id"], owner_id=data["owner_id"],
                      source_snapshot=copy.deepcopy(data["snapshot"]),
                      destination_snapshot=dict(snapshot_id="destination-snapshot", source_origin=[72, 100, 64]),
                      source_flight=copy.deepcopy(data["flight_data"]), destination_flight=arrival)
        disk["journal"] = dict(transfers=[record])
        disk["copies"] = []
        if cut != "LF_S6_R2_KEEP_SOURCE":
            duplicate = copy.deepcopy(disk["rocket"])
            duplicate.update(UUID="copy", Pos=[72.5, 100., 64.5])
            duplicate["RocketEntityData"].update(snapshot=copy.deepcopy(record["destination_snapshot"]),
                flight_data=copy.deepcopy(arrival), resource_escrow=dict(schema_version=1, resource_epoch=0, markers=[]))
            disk["copies"].append(duplicate)
            if "DESTRUCTIVE" in cut: record["destination_entity_id"] = "copy"
        return image

    def test_r2_keeps_exact_retired_fill_and_stale_history_on_both_restarts(self):
        for cut in ("LF_S6_R2_KEEP_SOURCE", "LF_S6_R2_DESTRUCTIVE_SOURCE", "LF_S6_R2_DUPLICATE_RECONNECT"):
            with self.subTest(cut=cut):
                first = oracle.check_r2(self.r2_image(cut), 0, cut=cut)
                self.assertEqual(5000, first["raw_total"])
                self.assertIsNone(first["available_total"])
                for restart in (1, 2):
                    self.assertEqual(first, oracle.check_r2(self.r2_image(cut), restart, first["identity"], cut))

    def test_r2_rejects_mutated_resources_epochs_markers_identities_or_stale_record(self):
        cut = "LF_S6_R2_DESTRUCTIVE_SOURCE"
        before = oracle.check_r2(self.r2_image(cut), 0, cut=cut)["identity"]
        for mutation in ("quantity", "epoch", "marker", "owner", "record", "copy", "unknown"):
            image = self.r2_image(cut); disk = image["native"]
            source = disk["loaders"][0]["arce_fluid_fuel_loader"]
            if mutation == "quantity": source["tank"]["Amount"] += 1
            elif mutation == "epoch": source["resource_escrow"]["resource_epoch"] += 1
            elif mutation == "marker": source["resource_escrow"]["markers"].append(dict(op_id=self.NATIVE_OP, seq=1, role="SOURCE", epoch_after=1))
            elif mutation == "owner": source["owner"] = "other"
            elif mutation == "record": disk["journal"]["transfers"][0]["transfer_id"] = "changed"
            elif mutation == "copy": disk["copies"][0]["UUID"] = disk["journal"]["transfers"][0]["destination_entity_id"] = "changed"
            else: image["read_status"] = "UNKNOWN"
            with self.subTest(mutation=mutation), self.assertRaises(SmokeError): oracle.check_r2(image, 1, before, cut)

    def test_g3_inflight_stop_requires_the_drained_l1_then_two_exact_completions(self):
        before = oracle.check_g3_l1(self.image("LF_M_K2"), 0)
        self.assertEqual(5000, before["raw_total"])
        self.assertIsNone(before["available_total"])
        for restart in (1, 2):
            result = oracle.check_g3_l1(self.image("LF_M_K7", restart), restart, before["identity"])
            self.assertEqual(5000, result["available_total"])
        for wrong in ("LF_M_K1", "LF_M_K4", "LF_M_K5", "LF_M_K6", "LF_M_K9"):
            with self.subTest(wrong=wrong), self.assertRaises(SmokeError):
                oracle.check_g3_l1(self.image(wrong), 0)

    def test_g3_rejects_changed_quantities_markers_epochs_identity_and_unknown(self):
        baseline = oracle.check_g3_l1(self.image("LF_M_K2"), 0)["identity"]
        for mutation in ("quantity", "marker", "epoch", "identity", "unknown"):
            image = self.image("LF_M_K7", 1)
            loader = image["native"]["loaders"][0]["arce_fluid_fuel_loader"]
            if mutation == "quantity": loader["tank"]["Amount"] += 1
            elif mutation == "marker": loader["resource_escrow"]["markers"].append(dict(op_id="foreign", seq=1, role="SOURCE", epoch_after=1))
            elif mutation == "epoch": loader["resource_escrow"]["resource_epoch"] += 1
            elif mutation == "identity": loader["device"] = "foreign"
            else: image["read_status"] = "UNKNOWN"
            with self.subTest(mutation=mutation), self.assertRaises(SmokeError):
                oracle.check_g3_l1(image, 1, baseline)

    def test_g3_requires_stop_during_the_selected_writer_state_and_normal_exit_log(self):
        cut = "LF_S6_G3_L1_INFLIGHT"
        lines = ["ARCE_LOADER_S6_DRAIN_READY cut=" + cut + " boundary=K1 running=true",
                 "ARCE_LOADER_S6_DRAIN_RELEASE cut=" + cut + " boundary=K1 running=false",
                 "[Server thread/INFO] [minecraft/MinecraftServer]: Stopping server"]
        oracle.check_observations(lines, 0, cut)
        for wrong in (lines[1:], lines[:-1], lines + [lines[0]],
                      [line.replace("running=false", "running=true") for line in lines],
                      lines + ["[Server/ERROR] unexpected"]):
            with self.subTest(wrong=wrong), self.assertRaises(SmokeError):
                oracle.check_observations(wrong, 0, cut)

    def test_g3_runner_requests_normal_stop_and_rejects_abnormal_exit_without_killing(self):
        from unittest.mock import Mock
        import run_v180_loader_s6 as runner
        process = Mock()
        process.finish.return_value = 0
        result = runner.finish_initial(process, "LF_S6_G3_L1_INFLIGHT", False)
        process.command.assert_called_once_with("stop")
        process.finish.assert_called_once_with()
        process.process.kill.assert_not_called()
        process.abort.assert_not_called()
        self.assertEqual(dict(stop="NORMAL_DRAIN_FROM_SELECTED_WRITER_STATE", exit_code=0), result)
        process.finish.return_value = 1
        with self.assertRaises(SmokeError): runner.finish_initial(process, "LF_S6_G3_L1_INFLIGHT", False)
        process.process.kill.assert_not_called()

    def test_g3_l2_retired_markers_require_the_observed_operation_and_exact_frontiers(self):
        image = self.image("LF_M_K9")
        operation = image["native"]["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"]["markers"][0]["op_id"]
        before = oracle.check_g3_l2(image, 0, operation=operation)
        self.assertEqual(5000, before["raw_total"])
        for restart in (1, 2):
            result = oracle.check_g3_l2(self.image("LF_M_K7", restart), restart, before["identity"])
            self.assertEqual(5000, result["available_total"])
        for mutation in ("id", "sequence", "role", "epoch", "quantity", "record", "unknown"):
            changed = copy.deepcopy(image)
            root = changed["native"]["loaders"][0]["arce_fluid_fuel_loader"]
            marker = root["resource_escrow"]["markers"][0]
            if mutation == "id": marker["op_id"] = "foreign"
            elif mutation == "sequence": marker["seq"] = 2
            elif mutation == "role": marker["role"] = "DESTINATION"
            elif mutation == "epoch": root["resource_escrow"]["resource_epoch"] = 2
            elif mutation == "quantity": root["tank"]["Amount"] += 1
            elif mutation == "record": changed["native"]["ledger"]["records"] = self.image("LF_M_K6")["native"]["ledger"]["records"]
            else: changed["read_status"] = "UNKNOWN"
            with self.subTest(mutation=mutation), self.assertRaises(SmokeError):
                oracle.check_g3_l2(changed, 0, operation=operation)

    def test_g3_l2_checks_each_retired_marker_independently_without_changing_input(self):
        for source_count, destination_count in ((0, 0), (0, 1), (1, 0), (1, 1)):
            image = self.image("LF_M_K9")
            source = image["native"]["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"]
            destination = image["native"]["rocket"]["RocketEntityData"]["resource_escrow"]
            operation = source["markers"][0]["op_id"]
            source["markers"] = source["markers"][:source_count]
            destination["markers"] = destination["markers"][:destination_count]
            preserved = copy.deepcopy(image)
            with self.subTest(source=source_count, destination=destination_count):
                oracle.check_g3_l2(image, 0, operation=operation)
                self.assertEqual(preserved, image)
                with self.assertRaises(SmokeError): oracle.check_g3_l2(image, 0)

    def test_g3_l2_logs_pin_the_same_observed_operation_before_and_after_normal_stop(self):
        cut = "LF_S6_G3_L2_INFLIGHT"
        operation = oracle.canonical_uuid(self.NATIVE_OP)
        lines = [f"ARCE_LOADER_S6_DRAIN_READY cut={cut} boundary=K8 running=true operation={operation}",
                 f"ARCE_LOADER_S6_DRAIN_RELEASE cut={cut} boundary=K8 running=false operation={operation}",
                 "[Server thread/INFO] Stopping server"]
        oracle.check_observations(lines, 0, cut)
        self.assertEqual(operation, oracle.shutdown_operation(lines, cut))
        for wrong in (lines[1:], lines + [lines[0]], lines[::-1],
                      [line.replace("K8", "K1") for line in lines],
                      [lines[0], lines[1].replace(operation, "foreign"), lines[2]]):
            with self.subTest(wrong=wrong), self.assertRaises(SmokeError):
                oracle.check_observations(wrong, 0, cut)

    def test_g3_l2_uses_normal_stop_not_the_forced_cut_transport(self):
        from unittest.mock import Mock
        import run_v180_loader_s6 as runner
        process = Mock()
        process.finish.return_value = 0
        self.assertEqual("NORMAL_DRAIN_FROM_SELECTED_WRITER_STATE",
                         runner.finish_initial(process, "LF_S6_G3_L2_INFLIGHT", False)["stop"])
        process.command.assert_called_once_with("stop")
        process.process.kill.assert_not_called()
        process.abort.assert_not_called()

    def test_retired_metadata_is_not_held_fuel_or_spendable_before_pruning(self):
        operation = oracle.canonical_uuid(self.NATIVE_OP)
        base = "ARCE_LOADER_S6_ACCOUNT tick={tick} known=true records=0 q=null source=4000 destination=1000 source_available=4000 source_held=0 destination_available=1000 destination_held=0 in_transit=0 spendable={spendable} retired={retired} retired_operation=" + operation
        rows = ["ARCE_LOADER_S6_BEGIN tick=1", base.format(tick=1, spendable="UNKNOWN", retired=2),
                base.format(tick=2, spendable="UNKNOWN", retired=1)] + [base.format(tick=tick, spendable="5000", retired=0)
                for tick in range(3, 102)] + ["ARCE_LOADER_S6_RESULT cut=" + oracle.E3 + " observations=101 total=5000 records=0 markers=0 source_epoch=1 destination_epoch=1 start_tick=1 end_tick=101 total_observations=101"]
        oracle.check_recovery_observations(rows, retired_operation=operation)
        for before, after in (("retired=2", "retired=3"), ("spendable=UNKNOWN", "spendable=5000"),
                              ("retired_operation=" + operation, "retired_operation=foreign"),
                              ("source_held=0", "source_held=1000"), ("retired=0", "retired=1")):
            with self.subTest(change=before), self.assertRaises(SmokeError):
                oracle.check_recovery_observations([row.replace(before, after) for row in rows], retired_operation=operation)
        with self.assertRaises(SmokeError): oracle.check_recovery_observations(rows)

    def test_unknown_l1_has_no_durable_fill_while_unknown_l2_replays_only_its_l1(self):
        for cut, prefix in (("LF_S6_G3_L1_UNKNOWN", "LF_M_E4L1_UNKNOWN"),
                            ("LF_S6_G3_L2_UNKNOWN", "LF_M_E4L2_UNKNOWN")):
            first = oracle.check_g3_unknown(self.image(prefix), 0, cut=cut)
            self.assertIsNone(first["loss"])
            self.assertIsNone(first["available_total"])
            for restart in (1, 2):
                result = oracle.check_g3_unknown(self.image(prefix, restart), restart, first["identity"], cut)
                self.assertEqual(5000, result["available_total"])
            for mutation in ("quantity", "marker", "epoch", "identity", "unknown"):
                changed = self.image(prefix, 1)
                source = changed["native"]["loaders"][0]["arce_fluid_fuel_loader"]
                if mutation == "quantity": source["tank"]["Amount"] += 1
                elif mutation == "marker": source["resource_escrow"]["markers"].append(dict(op_id="foreign", seq=1, role="SOURCE", epoch_after=1))
                elif mutation == "epoch": source["resource_escrow"]["resource_epoch"] += 1
                elif mutation == "identity": source["device"] = "foreign"
                else: changed["read_status"] = "UNKNOWN"
                with self.subTest(cut=cut, mutation=mutation), self.assertRaises(SmokeError):
                    oracle.check_g3_unknown(changed, 1, first["identity"], cut)

    def test_unknown_shutdown_logs_require_actual_unknown_state_and_bounded_resolution(self):
        cut = "LF_S6_G3_L1_UNKNOWN"
        state = "active:true,successor:false,completions:0,running:false,unknown:true,generation:2,durable:1"
        lines = [f"ARCE_LOADER_S6_DRAIN_READY cut={cut} boundary=WRITE_UNKNOWN running=true state={state}",
                 f"ARCE_LOADER_S6_DRAIN_RELEASE cut={cut} boundary=WRITE_UNKNOWN running=false state={state}",
                 "Escrow ledger shutdown stopped retrying generation 2 after 3 unknown outcomes; restart uses the durable ledger file",
                 "Stopping server"]
        oracle.check_observations(lines, 0, cut)
        for wrong in (lines[:2] + lines[3:], [x.replace("unknown:true", "unknown:false") for x in lines],
                      [x.replace("after 3", "after 2") for x in lines], lines + [lines[0]], lines + ["[Server/ERROR] foreign"]):
            with self.subTest(wrong=wrong), self.assertRaises(SmokeError): oracle.check_observations(wrong, 0, cut)

    def test_server_thread_shutdown_cases_wait_for_normal_exit_without_sending_another_command(self):
        from unittest.mock import Mock
        import run_v180_loader_s6 as runner
        for cut in ("LF_S6_G3_L1_UNKNOWN", "LF_S6_G3_L2_UNKNOWN", "LF_S6_G3_FAILED_CLOSE"):
            process = Mock(); process.finish.return_value = 0
            result = runner.finish_initial(process, cut, False)
            self.assertEqual("NORMAL_DRAIN_FROM_SELECTED_WRITER_STATE", result["stop"])
            process.command.assert_not_called()
            process.process.kill.assert_not_called()
            process.abort.assert_not_called()

    def test_queued_shutdown_logs_require_a_real_successor_not_just_an_active_writer(self):
        cut = "LF_S6_G3_L1_QUEUED"
        state = "active:true,successor:true,completions:0,running:true,unknown:false,generation:2,durable:1"
        lines = [f"ARCE_LOADER_S6_DRAIN_READY cut={cut} boundary=K1 running=true state={state}",
                 f"ARCE_LOADER_S6_DRAIN_RELEASE cut={cut} boundary=K1 running=false state={state}", "Stopping server"]
        oracle.check_observations(lines, 0, cut)
        for before, after in (("successor:true", "successor:false"), ("unknown:false", "unknown:true"),
                              ("generation:2", "generation:3"), ("completions:0", "completions:1")):
            with self.subTest(before=before), self.assertRaises(SmokeError):
                oracle.check_observations([line.replace(before, after) for line in lines], 0, cut)

    def test_failed_close_shutdown_allows_only_its_exact_propagated_native_failure(self):
        cut = "LF_S6_G3_FAILED_CLOSE"
        error = "[IO/ERROR] [minecraft/IOWorker]: Failed to store chunk [4, 4]"
        lines = ["ARCE_LOADER_S6_CACHE regions=256 own_region_cached=false", error,
                 "java.nio.channels.ClosedChannelException",
                 "ARCE_LOADER_S6_CLOSE_FAILURE records=1 pending=1 issued=1 io_failures=1 reason=BARRIER_IO proof=false",
                 f"ARCE_LOADER_S6_DRAIN_READY cut={cut} boundary=BARRIER_IO running=true",
                 f"ARCE_LOADER_S6_DRAIN_RELEASE cut={cut} boundary=BARRIER_IO running=false", "Stopping server"]
        oracle.check_observations(lines, 0, cut)
        oracle.check_observations(lines + [error], 0, cut)
        for wrong in (lines + [error, error], lines + ["[Server/FATAL] foreign"],
                      [line.replace("[4, 4]", "[5, 4]") for line in lines],
                      [line.replace("proof=false", "proof=true") for line in lines], lines[1:]):
            with self.subTest(wrong=wrong), self.assertRaises(SmokeError): oracle.check_observations(wrong, 0, cut)

    def test_l2_queued_requires_native_proof_and_busy_other_write_before_normal_stop(self):
        cut = "LF_S6_G3_L2_QUEUED"
        state = "active:true,successor:false,completions:0,running:true,unknown:false,generation:3,durable:2"
        lines = ["ARCE_LOADER_S6_L2_PENDING records=1 pending=1 issued=1 proved=true locked=true tank=4000 bank=1000 epochs=1,1 markers=1,1",
                 f"ARCE_LOADER_S6_DRAIN_READY cut={cut} boundary=C6_PENDING running=true state={state}",
                 f"ARCE_LOADER_S6_DRAIN_RELEASE cut={cut} boundary=C6_PENDING running=false state={state}", "Stopping server"]
        oracle.check_observations(lines, 0, cut)
        for before, after in (("proved=true", "proved=false"), ("locked=true", "locked=false"),
                              ("pending=1", "pending=0"), ("running:true", "running:false"),
                              ("generation:3", "generation:2"), ("durable:2", "durable:3"),
                              ("tank=4000", "tank=5000"), ("markers=1,1", "markers=0,0")):
            with self.subTest(before=before), self.assertRaises(SmokeError):
                oracle.check_observations([line.replace(before, after) for line in lines], 0, cut)
        for wrong in (lines[1:], lines + [lines[0]], lines + ["[Server/ERROR] unrelated"]):
            with self.assertRaises(SmokeError): oracle.check_observations(wrong, 0, cut)

    def test_l2_queued_stopped_authority_keeps_exact_fill_until_both_restarts(self):
        image = self.image("LF_M_K6")
        image["native"]["ledger"]["writer_generation"] = 3
        initial = oracle.check_g3_l2_queued(image, 0)
        self.assertIsNone(initial["loss"])
        for restart in (1, 2):
            final = self.image("LF_M_K7", restart)
            final["native"]["ledger"]["writer_generation"] = 4
            self.assertEqual(5000, oracle.check_g3_l2_queued(final, restart, initial["identity"])["available_total"])
        for prefix in ("K2", "K4", "K5", "K9"):
            changed = self.image("LF_M_" + prefix)
            changed["native"]["ledger"]["writer_generation"] = 3
            with self.subTest(prefix=prefix), self.assertRaises(SmokeError): oracle.check_g3_l2_queued(changed, 0)
        for key, value in (("read_status", "UNKNOWN"),):
            changed = copy.deepcopy(image); changed[key] = value
            with self.assertRaises(SmokeError): oracle.check_g3_l2_queued(changed, 0)

    def test_l2_queued_runner_uses_normal_stop(self):
        from unittest.mock import Mock
        import run_v180_loader_s6 as runner
        process = Mock(); process.finish.return_value = 0
        result = runner.finish_initial(process, "LF_S6_G3_L2_QUEUED", False)
        self.assertEqual("NORMAL_DRAIN_FROM_SELECTED_WRITER_STATE", result["stop"])
        process.command.assert_called_once_with("stop")
        process.process.kill.assert_not_called()

    def test_unknown_l2_can_resolve_guard_refusal_without_claiming_l2_committed(self):
        cut = "LF_S6_G3_L2_UNKNOWN"
        state = "active:true,successor:false,completions:0,running:false,unknown:true,generation:4,durable:3"
        lines = [f"ARCE_LOADER_S6_DRAIN_READY cut={cut} boundary=WRITE_UNKNOWN running=true state={state}",
                 f"ARCE_LOADER_S6_DRAIN_RELEASE cut={cut} boundary=WRITE_UNKNOWN running=false state={state}",
                 "Escrow ledger generation 4 resolved as FAILED_BEFORE_REPLACE after an unknown outcome", "Stopping server"]
        oracle.check_observations(lines, 0, cut)
        for wrong in ([x.replace("FAILED_BEFORE_REPLACE", "COMMITTED") for x in lines],
                      [x.replace("generation 4", "generation 3") for x in lines]):
            with self.assertRaises(SmokeError): oracle.check_observations(wrong, 0, cut)

    def test_r2_logs_require_fence_copy_count_and_unmounted_reconnect(self):
        cut = "LF_S6_R2_DUPLICATE_RECONNECT"
        line = "ARCE_LOADER_S6_R2_RESULT cut=" + cut + " tank=4000 bank=1000 total=5000 epochs=1,1 records=0 markers=0 journal=1 copies=2 reason=FLOOR_CONFLICT passenger=unmounted"
        oracle.check_observations([line], 1, cut)
        for changed in ([], [line, line], [line.replace("copies=2", "copies=1")],
                        [line.replace("unmounted", "mounted")], [line, "[Server/ERROR] unrelated"]):
            with self.subTest(lines=changed), self.assertRaises(SmokeError): oracle.check_observations(changed, 1, cut)


class GuardOracleTest(unittest.TestCase):
    def image(self, cut="LF_S6_G1_VETO", restart=0):
        disk = fixture("LF_M_N0", 0)
        original = disk["loaders"][0]
        original["arce_fluid_fuel_loader"]["schema_version"] = 1
        original["arce_fluid_fuel_loader"]["binding"].update(
            schema_version=1, level="minecraft:overworld", x=64, y=100, z=64)
        disk["loaders"] = []
        for index in range(257 if cut.endswith("SATURATION") else 1):
            loader = copy.deepcopy(original)
            loader.update(x=(4 + index % 16) * 16 + 2, z=(4 + index // 16) * 16)
            loader["arce_fluid_fuel_loader"]["device"] = str(uuid.UUID(int=index + 1))
            disk["loaders"].append(loader)
        disk.update(day_time=(restart + 1) * 10000, protected_block="minecraft:stone",
                    control_block="minecraft:stone" if cut.endswith("SATURATION") else
                    "minecraft:diamond_block" if restart == 1 else "minecraft:gold_block")
        disk["rocket"]["CustomName"] = '{"text":"W211_G1_' + str(restart + 1) + '"}'
        return dict(read_status="KNOWN", native=disk)

    def test_native_guard_preserves_baseline_and_independent_stores_on_two_restarts(self):
        for cut in ("LF_S6_G1_VETO", "LF_S6_G1_SATURATION"):
            identity = None
            for restart in range(3):
                result = oracle.check_g1(self.image(cut, restart), restart, identity, cut)
                identity = result["identity"]
                self.assertEqual(5000 * (257 if cut.endswith("SATURATION") else 1), result["raw_total"])
                self.assertIsNone(result["available_total"])

    def test_native_guard_rejects_changed_resource_identity_and_save_scope(self):
        cut = "LF_S6_G1_SATURATION"
        baseline = self.image(cut)
        identity = oracle.check_g1(baseline, 0, None, cut)["identity"]
        changes = (
            lambda d: d.update(day_time=0), lambda d: d.update(protected_block="minecraft:gold_block"),
            lambda d: d.update(control_block="minecraft:diamond_block"), lambda d: d["loaders"].pop(),
            lambda d: d["rocket"].update(CustomName='{"text":"other"}'),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=4999),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"].update(resource_epoch=1),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"].update(markers=[{}]),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"].update(owner="another"),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["binding"].update(physical_rocket="another"),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"].update(device=str(uuid.UUID(int=2))),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"].update(extra="changed"),
        )
        for change in changes:
            damaged = copy.deepcopy(baseline); change(damaged["native"])
            with self.subTest(change=change), self.assertRaises(SmokeError):
                oracle.check_g1(damaged, 0, identity, cut)
        with self.assertRaises(SmokeError): oracle.check_g1(dict(read_status="UNKNOWN", native=None), 0, None, cut)

    def test_guard_logs_require_real_capacity_and_exact_save_refusal(self):
        cut = "LF_S6_G1_SATURATION"
        lines = ["ARCE_LOADER_S6_G1_CAPACITY entries=256 duplicate_stable=true unrelated_allowed=true",
                 "ARCE_LOADER_S6_G1_RESULT cut=" + cut + " round=0 loaders=257 denials=256 scope=LEVEL retained_bits=7fc00001 save_attempted=true",
                 "[Server thread/ERROR]: Exception caught during firing event: Refusing chunk save with a fluid fuel loader root that native serialization cannot keep; back up and repair offline",
                 "[Server thread/ERROR]: Failed to save chunk 4,4",
                 "java.lang.IllegalStateException: Refusing chunk save with a fluid fuel loader root that native serialization cannot keep; back up and repair offline",
                 "[Server thread/ERROR]: Failed to save chunk 21,4",
                 "java.lang.IllegalStateException: Refusing chunk save after denial capacity exhausted; back up and repair first"]
        oracle.check_observations(lines, 0, cut)
        for ending in ("\n", "\r\n"):
            captured = ["[18:21:49] [Server thread/INFO] [io.gi.su.ad.AdvancedRocketryCommunity/]: " + line + ending
                        if line.startswith("ARCE_") else line + ending for line in lines]
            oracle.check_observations(captured, 0, cut)
            with self.assertRaises(SmokeError):
                oracle.check_observations([line.rstrip("\r\n") + " extra" + ending for line in captured], 0, cut)
        for wrong in (lines[1:], lines + [lines[1]], lines + ["[Server/ERROR] unrelated"],
                      [line.replace("7fc00001", "7fc00000") for line in lines],
                      [line.replace("capacity exhausted", "something else") for line in lines]):
            with self.assertRaises(SmokeError): oracle.check_observations(wrong, 0, cut)

    def test_native_binding_checks_every_field_without_accepting_extensions(self):
        image = self.image()
        oracle.check_g1(image, 0)
        binding = image["native"]["loaders"][0]["arce_fluid_fuel_loader"]["binding"]
        for field in binding:
            for remove in (False, True):
                damaged = copy.deepcopy(image)
                target = damaged["native"]["loaders"][0]["arce_fluid_fuel_loader"]["binding"]
                if remove:
                    del target[field]
                else:
                    target[field] = target[field] + 1 if type(target[field]) is int else "changed"
                with self.subTest(field=field, remove=remove), self.assertRaises(SmokeError):
                    oracle.check_g1(damaged, 0)
        binding["unexpected"] = 1
        with self.assertRaises(SmokeError): oracle.check_g1(image, 0)

    def test_native_level_dat_exceeds_celestial_tags_but_not_s6_bounds(self):
        from inspect_celestial_saved_data import NbtReader, NbtError
        path = Path(__file__).parent / "fixtures/loader_s6/native-level.dat.gz"
        payload = gzip.decompress(path.read_bytes())
        with self.assertRaises(NbtError): NbtReader(payload).read_root()
        self.assertEqual(4495, oracle.read_g1_day_time(path))
        # The independently configured evidence reader must not change the default.
        with self.assertRaises(NbtError): NbtReader(payload).read_root()

    def test_global_read_and_bound_failures_remain_unknown(self):
        from inspect_celestial_saved_data import NbtError
        path = Path(__file__).parent / "fixtures/loader_s6/native-level.dat.gz"
        with tempfile.TemporaryDirectory() as directory:
            bad = Path(directory) / "level.dat"
            for payload in (b"not gzip", path.read_bytes()[:20], gzip.compress(b"x" * 4_194_305)):
                bad.write_bytes(payload)
                with self.subTest(size=len(payload)), self.assertRaises((OSError, ValueError, SmokeError)):
                    oracle.read_g1_day_time(bad)
        with patch.object(oracle, "inspect_g1", side_effect=NbtError("tag bound")):
            image = oracle.inspect(Path("unused"), oracle.G1_VETO)
            self.assertEqual("UNKNOWN", image["read_status"])
            self.assertIsNone(image["native"])
            with self.assertRaises(SmokeError): oracle.check_g1(image, 0)

    def test_global_tag_limit_is_enforced_and_unknown_has_no_numeric_total(self):
        # One root, one Data compound and 65,535 scalar children exceed 65,536 tags.
        fields = []
        for index in range(65_535):
            name = str(index).encode("ascii")
            fields.append(b"\x03" + struct.pack(">H", len(name)) + name + struct.pack(">i", index))
        payload = b"\x0a\0\0\x0a\0\x04Data" + b"".join(fields) + b"\0\0"
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "level.dat"
            path.write_bytes(gzip.compress(payload))
            with self.assertRaisesRegex(ValueError, "65536 tags"):
                oracle.read_g1_day_time(path)
            with patch.object(oracle, "inspect_g1", side_effect=lambda *args: oracle.read_g1_day_time(path)):
                image = oracle.inspect(Path(directory), oracle.G1_VETO)
            self.assertEqual("UNKNOWN", image["read_status"])
            self.assertIsNone(image["native"])
            with self.assertRaises(SmokeError): oracle.check_g1(image, 0)


    def test_guard_palette_decodes_cross_word_padded_indices(self):
        palette = [{"Name": "minecraft:stone"}, {"Name": "minecraft:gold_block"}]
        data = [0] * 256
        index = 4 * 256 + 3
        data[index // 16] = 1 << (index % 16 * 4)
        chunk = dict(sections=[dict(Y=6, block_states=dict(palette=palette, data=data))])
        self.assertEqual("minecraft:gold_block", oracle.block_at(chunk, (67, 100, 64)))
        self.assertEqual("minecraft:stone", oracle.block_at(chunk, (66, 100, 64)))
        data[index // 16] = 2 << (index % 16 * 4)
        with self.assertRaises(SmokeError): oracle.block_at(chunk, (67, 100, 64))


class SettlementOracleTest(unittest.TestCase):
    def image(self, cut="LF_S6_O1_REPLACED", restart=0):
        import json
        from run_v180_rocket_settlement_intent import ACTOR
        image = json.loads((Path(__file__).parent / "fixtures/loader_s6/settlement-seed.json").read_text())
        root = image["native"]["ledger"]
        op = root["records"][0]
        op.update(phase="REPAIR" if cut == "LF_S6_O1_BEFORE" else "SETTLING_ACCEPT", reason="OTHER_STATE")
        observations = []
        for key, quantity, kind, role in (("source", 4000, "FLUID_TANK_V1", "SOURCE"), ("destination", 1000, "FUEL_VECTOR_V1", "DESTINATION")):
            observations.append(dict(endpoint=copy.deepcopy(op[key]), missing=0, bound=1, quantity=quantity,
                signature_kind=kind, invariant_digest="1" * 64,
                state=dict(ledger_id=root["ledger_id"], epoch=1, signature=op[key]["after_signature"],
                           markers=[dict(op_id=op["op_id"], seq=1, role=role, epoch_after=1)])))
        if cut != "LF_S6_O1_BEFORE":
            op["settlement"] = dict(schema_version=2, choice="ACCEPT_CURRENT", ledger_id=root["ledger_id"], actor=ACTOR,
                actor_name="Server", preview_tick=100, confirmed_tick=100, digest="a" * 64,
                members=[dict(op_id=op["op_id"], phase="REPAIR", reason="OTHER_STATE", digest="b" * 64)], observations=observations)
        if restart and cut != "LF_S6_O1_BEFORE":
            root["records"] = []
            root["writer_generation"] = 6
            root["tombstones"] = [dict(schema_version=3, op_id=op["op_id"], retired=[dict(op_id=op["op_id"])],
                guards=[op["transfer_guard"]], before_choice=observations, payload_amount=1000, payload_hash=op["payload_hash"],
                accounting_status="UNKNOWN", decision_digest="a" * 64, actor=ACTOR, actor_name="Server", choice="ACCEPT_CURRENT",
                confirmed_tick=100, released=1, release_generation=6, anchor_op=op["op_id"], missing=[],
                survivors=[dict(endpoint=o["endpoint"], epoch=1, signature=o["state"]["signature"], signature_kind=o["signature_kind"],
                                invariant_digest=o["invariant_digest"], quantity=o["quantity"], bound=True) for o in observations])]
            data = image["native"]["rocket"]["RocketEntityData"]
            root["rocket_authorities"] = [dict(v=1, l=data["assembly_transaction_id"], o=data["owner_id"], a=op["op_id"],
                p=image["native"]["rocket"]["UUID"], e=1, g=6, k="FUEL_VECTOR_V1", s=list(bytes.fromhex(op["destination"]["after_signature"])),
                i=list(bytes.fromhex("1" * 64)), issued=0, completed=0, outcome="READY")]
            data["resource_escrow"]["markers"] = []
            image["native"]["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"]["markers"] = []
        return image

    def test_operator_boundaries_and_two_restarts_preserve_exact_authority(self):
        for cut in ("LF_S6_O1_BEFORE", "LF_S6_O1_REPLACED", "LF_S6_O1_ACK"):
            identity = None
            for restart in range(3):
                result = oracle.check_o1(self.image(cut, restart), restart, identity, cut)
                identity = result["identity"]
                self.assertEqual(5000, result["raw_total"])
                self.assertIsNone(result["available_total"])
                self.assertIsNone(result["loss"])

    def test_operator_rejects_changed_quantity_epoch_marker_and_frozen_fields(self):
        cut = "LF_S6_O1_REPLACED"
        baseline = self.image(cut)
        changes = [
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=5000),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"].update(resource_epoch=2),
            lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"]["markers"][0].update(op_id="other"),
            lambda d: d["ledger"]["records"][0]["settlement"].update(actor="other"),
            lambda d: d["ledger"]["records"][0]["settlement"]["members"][0].update(op_id="other"),
            lambda d: d["ledger"]["records"][0]["settlement"]["observations"][0].update(quantity=999),
            lambda d: d["ledger"]["records"][0]["settlement"]["observations"][1]["state"].update(epoch=2),
        ]
        for change in changes:
            damaged = copy.deepcopy(baseline); change(damaged["native"])
            with self.subTest(change=change), self.assertRaises(SmokeError): oracle.check_o1(damaged, 0, None, cut)
        with self.assertRaises(SmokeError): oracle.check_o1(dict(read_status="UNKNOWN", native=None), 0, None, cut)

    def test_receipt_and_ready_head_must_match_the_complete_initial_choice(self):
        cut = "LF_S6_O1_ACK"
        identity = oracle.check_o1(self.image(cut), 0, None, cut)["identity"]
        final = self.image(cut, 1)
        for field, value in (("actor", "other"), ("decision_digest", "c" * 64), ("accounting_status", "EXACT"),
                             ("payload_amount", 999), ("anchor_op", "other"), ("payload_hash", "c" * 64)):
            damaged = copy.deepcopy(final); damaged["native"]["ledger"]["tombstones"][0][field] = value
            with self.subTest(field=field), self.assertRaises(SmokeError): oracle.check_o1(damaged, 1, identity, cut)
        for field in ("l", "o", "a", "p", "e", "g", "s", "i", "issued", "completed", "outcome"):
            damaged = copy.deepcopy(final); damaged["native"]["ledger"]["rocket_authorities"][0][field] = "changed"
            with self.subTest(field=field), self.assertRaises(SmokeError): oracle.check_o1(damaged, 1, identity, cut)

    def test_operator_native_binding_and_snapshot_cannot_change_identity(self):
        cut = "LF_S6_O1_REPLACED"
        for field in ("physical", "logical", "owner", "snapshot"):
            image = self.image(cut)
            image["native"]["ledger"]["records"][0]["native_binding"][field] = "changed"
            with self.subTest(field=field), self.assertRaises(SmokeError): oracle.check_o1(image, 0, None, cut)
        identity = oracle.check_o1(self.image(cut), 0, None, cut)["identity"]
        image = self.image(cut, 1)
        image["native"]["rocket"]["RocketEntityData"]["snapshot"]["created_at_game_time"] += 1
        with self.assertRaises(SmokeError): oracle.check_o1(image, 1, identity, cut)

    def test_operator_logs_pin_each_actual_cut_and_reject_errors(self):
        for cut, hook in (("LF_S6_O1_BEFORE", "K1"), ("LF_S6_O1_REPLACED", "K2"), ("LF_S6_O1_ACK", "O1_ACK")):
            line = f"ARCE_LOADER_S6_O_PAUSED cut={cut} hook={hook}\n"
            oracle.check_observations([line], 0, cut)
            for lines in ([], [line, line], [line.replace(hook, "OTHER")], [line, "[Server/ERROR] unrelated"]):
                with self.assertRaises(SmokeError): oracle.check_observations(lines, 0, cut)


class SettlementStagedOracleTest(unittest.TestCase):
    CUTS = ("LF_S6_O2_BEFORE_0", "LF_S6_O2_AFTER_0", "LF_S6_O2_BEFORE_1", "LF_S6_O2_AFTER_1",
            "LF_S6_O3_BEFORE", "LF_S6_O3_REPLACED", "LF_S6_O3_ACK", "LF_S6_O4_BEFORE")

    def image(self, cut, restart=0):
        source = SettlementOracleTest()
        image = source.image(restart=restart)
        if not restart and cut in self.CUTS[-3:]:
            receipt = source.image(restart=1)["native"]["ledger"]["tombstones"][0]
            receipt.update(released=0, release_generation=0)
            image["native"]["ledger"]["tombstones"] = [receipt]
        return image

    def test_each_survivor_and_install_boundary_then_two_restarts(self):
        for cut in self.CUTS:
            identity = None
            for restart in range(3):
                with self.subTest(cut=cut, restart=restart):
                    checked = oracle.check_o1(self.image(cut, restart), restart, identity, cut)
                    identity = checked["identity"]
                    self.assertEqual(5000, checked["raw_total"])
                    self.assertIsNone(checked["available_total"])
                    self.assertIsNone(checked["loss"])

    def test_installed_receipt_is_exact_and_not_a_release(self):
        cut = "LF_S6_O3_REPLACED"
        baseline = self.image(cut)
        for key in baseline["native"]["ledger"]["tombstones"][0]:
            damaged = copy.deepcopy(baseline)
            del damaged["native"]["ledger"]["tombstones"][0][key]
            with self.subTest(key=key), self.assertRaises(SmokeError): oracle.check_o1(damaged, 0, cut=cut)
        for key, value in (("released", 1), ("release_generation", 1), ("survivors", []), ("unknown_extra", 1)):
            damaged = copy.deepcopy(baseline)
            damaged["native"]["ledger"]["tombstones"][0][key] = value
            with self.subTest(key=key), self.assertRaises(SmokeError): oracle.check_o1(damaged, 0, cut=cut)
        for cut in self.CUTS:
            with self.subTest(cut=cut), self.assertRaises(SmokeError):
                oracle.check_o1(dict(read_status="UNKNOWN", native=None), 0, cut=cut)

    def test_staged_logs_require_the_actual_boundary(self):
        hooks = ("O2_BEFORE_0", "O2_AFTER_0", "O2_BEFORE_1", "O2_AFTER_1", "K1", "K2", "O3_AFTER", "K8")
        for cut, hook in zip(self.CUTS, hooks):
            line = f"ARCE_LOADER_S6_O_PAUSED cut={cut} hook={hook}\r\n"
            oracle.check_observations([line], 0, cut)
            for bad in ([], [line, line], [line.replace(hook, "unrelated")], [line, "[Server/ERROR] unrelated"]):
                with self.subTest(cut=cut), self.assertRaises(SmokeError): oracle.check_observations(bad, 0, cut)

    def test_native_o1_receipt_shape_and_staged_mutations(self):
        import json
        initial, completed = json.loads((Path(__file__).parent / "fixtures/loader_s6/settlement-o1-native.json").read_text())
        original = copy.deepcopy([initial, completed])
        identity = oracle.check_o1(initial, 0, cut="LF_S6_O1_REPLACED")["identity"]
        oracle.check_o1(completed, 1, identity, "LF_S6_O1_REPLACED")
        staged = copy.deepcopy(initial)
        staged["native"]["ledger"]["tombstones"] = copy.deepcopy(completed["native"]["ledger"]["tombstones"])
        staged["native"]["ledger"]["tombstones"][0].update(released=0, release_generation=0)
        oracle.check_o1(staged, 0, cut="LF_S6_O3_REPLACED")
        for cut in self.CUTS:
            image = staged if cut in self.CUTS[-3:] else initial
            for change in (
                lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=3999),
                lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"].update(resource_epoch=2),
                lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"]["markers"][0].update(op_id="other"),
                lambda d: d["ledger"]["records"][0]["native_binding"].update(owner="other")):
                damaged = copy.deepcopy(image); change(damaged["native"])
                with self.subTest(cut=cut), self.assertRaises(SmokeError): oracle.check_o1(damaged, 0, cut=cut)
        self.assertEqual(original, [initial, completed])

    def test_release_preserves_all_receipt_fields_not_only_before_choice(self):
        initial = SettlementOracleTest().image()
        identity = oracle.check_o1(initial, 0, cut="LF_S6_O1_REPLACED")["identity"]
        for key, value in (("survivors", []), ("unrecognized", 1)):
            changed = SettlementOracleTest().image(restart=1)
            changed["native"]["ledger"]["tombstones"][0][key] = value
            with self.subTest(key=key), self.assertRaises(SmokeError):
                oracle.check_o1(changed, 1, identity, "LF_S6_O1_REPLACED")


class SettlementReleaseOracleTest(unittest.TestCase):
    def image(self, restart=0):
        import json
        image = json.loads((Path(__file__).parent / "fixtures/loader_s6/settlement-o1-native.json").read_text())[1]
        if not restart:
            receipt = image["native"]["ledger"]["tombstones"][0]
            source = image["native"]["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"]
            destination = image["native"]["rocket"]["RocketEntityData"]["resource_escrow"]
            for observation in receipt["before_choice"]:
                section = source if observation["endpoint"]["kind"] == "BLOCK_ENTITY" else destination
                section["markers"] = copy.deepcopy(observation["state"]["markers"])
        return image

    def test_replaced_and_acknowledged_o4_allow_only_retired_metadata_until_restart(self):
        for cut in ("LF_S6_O4_REPLACED", "LF_S6_O4_ACK"):
            identity = None
            for restart in range(3):
                with self.subTest(cut=cut, restart=restart):
                    checked = oracle.check_o1(self.image(restart), restart, identity, cut)
                    identity = checked["identity"]
                    self.assertEqual(5000, checked["raw_total"])
                    self.assertIsNone(checked["loss"])
                    self.assertIsNone(checked["available_total"])

    def test_o4_checks_raw_state_and_the_whole_receipt(self):
        cut = "LF_S6_O4_REPLACED"
        image = self.image()
        identity = oracle.check_o1(image, 0, cut=cut)["identity"]
        mutations = [lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=3999),
                     lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"].update(resource_epoch=2),
                     lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"]["markers"][0].update(op_id="other"),
                     lambda d: d["ledger"]["tombstones"][0]["before_choice"][0].update(extra=1)]
        for key, value in (("actor", "other"), ("payload_hash", "c" * 64), ("survivors", []), ("released", 0),
                           ("accounting_status", "EXACT"), ("extra", 1)):
            mutations.append(lambda d, k=key, v=value: d["ledger"]["tombstones"][0].update({k: v}))
        for mutation in mutations:
            damaged = copy.deepcopy(image); mutation(damaged["native"])
            with self.assertRaises(SmokeError): oracle.check_o1(damaged, 0, cut=cut)
        for field in ("device", "owner", "binding"):
            damaged = self.image(1)
            damaged["native"]["loaders"][0]["arce_fluid_fuel_loader"][field] = "other"
            with self.assertRaises(SmokeError): oracle.check_o1(damaged, 1, identity, cut)
        with self.assertRaises(SmokeError): oracle.check_o1(dict(read_status="UNKNOWN", native=None), 0, cut=cut)
        with self.assertRaises(SmokeError): oracle.check_o1(image, 1, identity, cut)

    def test_o4_logs_pin_empty_record_writer_and_owner_ack(self):
        for cut, hook in (("LF_S6_O4_REPLACED", "K9"), ("LF_S6_O4_ACK", "O4_AFTER")):
            line = f"ARCE_LOADER_S6_O_PAUSED cut={cut} hook={hook}"
            oracle.check_observations([line], 0, cut)
            with self.assertRaises(SmokeError): oracle.check_observations([line.replace(hook, "K2")], 0, cut)


class SettlementJournalOracleTest(unittest.TestCase):
    CUTS = ("LF_S6_O3J_BEFORE", "LF_S6_O3J_REPLACED", "LF_S6_O3J_ACK")

    def image(self, cut, restart=0):
        import json
        image = SettlementStagedOracleTest().image("LF_S6_O3_REPLACED", restart)
        transfer = json.loads((Path(__file__).parent / "fixtures/loader_s6/named-transfer.json").read_text())
        disk = image["native"]; root = disk["ledger"]; data = disk["rocket"]["RocketEntityData"]
        transfer.update(owner_id=data["owner_id"], logical_rocket_id=data["assembly_transaction_id"],
                        destination_entity_id=disk["rocket"]["UUID"], destination_snapshot=copy.deepcopy(data["snapshot"]))
        for op in root["records"]: op["transfer_guard"]["transfer_id"] = transfer["transfer_id"]
        root["tombstones"][0]["guards"][0]["transfer_id"] = transfer["transfer_id"]
        disk["transfers"] = [transfer] if cut == self.CUTS[0] and not restart else []
        disk["healthy"] = None
        return image

    def test_named_journal_replacement_sides_and_two_restarts(self):
        for cut in self.CUTS:
            identity = None
            for restart in range(3):
                with self.subTest(cut=cut, restart=restart):
                    checked = oracle.check_o1(self.image(cut, restart), restart, identity, cut)
                    identity = checked["identity"]
                    self.assertEqual(5000, checked["raw_total"])
                    self.assertIsNone(checked["loss"])

    def test_named_guard_and_durable_removal_cannot_be_substituted(self):
        cut = self.CUTS[0]
        image = self.image(cut)
        for key, value in (("phase", "PREPARED"), ("destination_entity_id", "other"), ("logical_rocket_id", "other"),
                           ("owner_id", "other"), ("transfer_id", "other"), ("destination_snapshot", {})):
            damaged = copy.deepcopy(image); damaged["native"]["transfers"][0][key] = value
            with self.subTest(key=key), self.assertRaises(SmokeError): oracle.check_o1(damaged, 0, cut=cut)
        for wrong_cut in self.CUTS[1:]:
            with self.assertRaises(SmokeError): oracle.check_o1(image, 0, cut=wrong_cut)
        with self.assertRaises(SmokeError): oracle.check_o1(self.image(self.CUTS[1]), 0, cut=cut)
        with self.assertRaises(SmokeError): oracle.check_o1(dict(read_status="UNKNOWN", native=None), 0, cut=cut)

    def test_reused_i6_flags_are_initial_only_and_select_no_other_boundary(self):
        import run_v180_loader_s6 as runner
        for cut, mode in zip(self.CUTS, ("PLAIN_MOVE", "PLAIN_MOVED", None)):
            self.assertEqual([] if mode is None else ["-Dadvancedrocketrycommunity.i6NativeCut=" + mode], runner.journal_cut_properties(cut, 0))
            self.assertEqual([], runner.journal_cut_properties(cut, 1))
        self.assertEqual([], runner.journal_cut_properties("LF_S6_O4_ACK", 0))

    def test_named_baseline_log_matches_the_durable_guard(self):
        import run_v180_loader_s6 as runner
        for cut, hook in zip(self.CUTS, ("PLAIN_MOVE", "PLAIN_MOVED", "O3J_AFTER")):
            image = self.image(cut)
            guard = oracle.normalize_ids(image["native"]["ledger"]["records"][0]["transfer_guard"])
            baseline = f"ARCE_LOADER_S6_O3J_BASELINE cut={cut} transfer={guard['transfer_id']} generation={guard['journal_generation']} digest={guard['journal_digest']} phase=COMMITTED"
            marker = (f"ARCE_W164_CUT boundary={hook}" if cut != self.CUTS[2]
                      else f"ARCE_LOADER_S6_O_PAUSED cut={cut} hook={hook}")
            runner.check_log([baseline, marker], 0, cut, image=image)
            for lines in ([marker], [baseline, baseline, marker], [baseline.replace("COMMITTED", "PREPARED"), marker],
                          [baseline.replace(guard["transfer_id"], "other"), marker]):
                with self.assertRaises(SmokeError): runner.check_log(lines, 0, cut, image=image)


if __name__ == "__main__":
    unittest.main()
