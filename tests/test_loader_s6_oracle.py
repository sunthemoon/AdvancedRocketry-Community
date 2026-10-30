"""S6 raw-state rejection tests; no native server is needed."""
from pathlib import Path
import copy
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
        return dict(read_status="KNOWN", native=fixture(cut, restart))

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
        return [
            "ARCE_LOADER_S6_ACCOUNT tick=1 known=false reason=UNLOADED raw=UNKNOWN available=UNKNOWN held=UNKNOWN",
            "ARCE_LOADER_S6_ACCOUNT tick=2 known=true records=1 q=1000 source=5000 destination=1000 source_available=4000 source_held=1000 destination_available=0 destination_held=1000 in_transit=0 spendable=UNKNOWN",
            "ARCE_LOADER_S6_ACCOUNT tick=3 known=true records=0 q=null source=4000 destination=1000 source_available=4000 source_held=0 destination_available=1000 destination_held=0 in_transit=0 spendable=5000",
            "ARCE_LOADER_S6_RESULT cut=" + oracle.E3 + " observations=2 total=5000 records=0 markers=0 source_epoch=1 destination_epoch=1",
        ]

    def test_recovery_log_requires_record_backed_every_tick_partition(self):
        oracle.check_observations(self.recovery_lines(), 1)

    def test_recovery_log_rejects_overcredit_or_fabricated_held_or_unknown_zero(self):
        for old, new in (("destination=1000", "destination=1001"), ("q=1000", "q=999"),
                         ("source_held=0", "source_held=1"), ("source=4000", "source=3999"),
                         ("raw=UNKNOWN", "raw=0"), ("tick=3", "tick=4"),
                         ("observations=2", "observations=3"), ("spendable=UNKNOWN", "spendable=6000")):
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
        far = fixture("LF_M_K6" if not restart else "LF_M_K7", restart)
        healthy = fixture("LF_M_K7", 1)
        far["loaders"][0].update(x=2050, z=2048)
        far["rocket"]["Pos"] = [2048.5, 100.0, 2048.5]
        h = healthy["loaders"][0]["arce_fluid_fuel_loader"]
        h.update(device="healthy-device", binding=dict(physical_rocket="healthy-physical", logical_rocket="healthy-logical"))
        healthy["rocket"]["UUID"] = "healthy-physical"
        healthy["rocket"]["RocketEntityData"]["assembly_transaction_id"] = "healthy-logical"
        healthy["rocket"]["RocketEntityData"]["flight_data"]["logical_rocket_id"] = "healthy-logical"
        ledger = far["ledger"]
        ledger["issued_through"] = 2
        if ledger["records"]:
            ledger["records"][0]["seq"] = 1
        return dict(read_status="KNOWN", native=dict(ledger=ledger, pairs=[far, healthy]))

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
            labels = ("ledger", "op", "owner", "device0", "physical", "logical",
                      "healthy-device", "healthy-physical", "healthy-logical")
            if isinstance(value, dict): return {k: convert(v, as_array) for k, v in value.items()}
            if isinstance(value, list): return [convert(v, as_array) for v in value]
            if isinstance(value, str) and value in labels:
                ident = uuid.uuid5(uuid.NAMESPACE_OID, value)
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
                old = convert("owner", True)
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
                      source_entity_id="physical", logical_rocket_id="logical", owner_id="owner",
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

    def test_r2_logs_require_fence_copy_count_and_unmounted_reconnect(self):
        cut = "LF_S6_R2_DUPLICATE_RECONNECT"
        line = "ARCE_LOADER_S6_R2_RESULT cut=" + cut + " tank=4000 bank=1000 total=5000 epochs=1,1 records=0 markers=0 journal=1 copies=2 reason=FLOOR_CONFLICT passenger=unmounted"
        oracle.check_observations([line], 1, cut)
        for changed in ([], [line, line], [line.replace("copies=2", "copies=1")],
                        [line.replace("unmounted", "mounted")], [line, "[Server/ERROR] unrelated"]):
            with self.subTest(lines=changed), self.assertRaises(SmokeError): oracle.check_observations(changed, 1, cut)


if __name__ == "__main__":
    unittest.main()
