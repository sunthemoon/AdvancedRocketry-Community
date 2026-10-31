"""Synthetic F7/F8 exact-state mutation controls, not native release evidence."""
import base64
import copy
import unittest
from test_loader_s6_terminal import proof
import loader_s6_cleanup as cleanup
from loader_s6_cleanup import terminal, forward, flight, SmokeError


def prefix(cut):
    image, witness, recovered, end = proof(terminal.CASES[-1])
    root = image["native"]["ledger"]
    root["armed_flights"] = copy.deepcopy(root["armed_flights"])
    if cut != "LF_S6_F7_BEFORE":
        root["writer_generation"] = 5; root["armed_flights"][0]["outcome"] = "HANDED_OFF"
    if cut.startswith("LF_S6_F8_") and cut != "LF_S6_F8_JOURNAL_BEFORE": image["native"]["journal"]["transfers"] = []
    if cut in ("LF_S6_F8_PRUNE_REPLACED", "LF_S6_F8_PRUNE_ACK"):
        root.update(writer_generation=6, armed_flights=[])
    if "_JOURNAL_" not in cut:
        staged = copy.deepcopy(root)
        if cut.startswith("LF_S6_F7_"):
            staged.update(writer_generation=5); staged["armed_flights"][0]["outcome"] = "HANDED_OFF"
        else: staged.update(writer_generation=6, armed_flights=[])
        witness["staged_ledger"] = staged
    return image, witness, recovered, end


class CleanupOracleTests(unittest.TestCase):
    def test_native_uuid_array_forms_in_each_checked_capture_agree(self):
        for cut in cleanup.CASES:
            image, witness, _, _ = prefix(cut)
            image["native"]["journal"] = flight.nbt.plain(forward.native_tag(image["native"]["journal"]))
            image["native"]["ledger"]["armed_flights"] = [flight.nbt.plain(flight.armed_tag(a)) for a in image["native"]["ledger"]["armed_flights"]]
            witness["capture"] = terminal.decode_capture(base64.b64encode(flight.nbt.write(b"", forward.native_tag(witness["capture"]))).decode())
            if "staged_ledger" in witness:
                witness["staged_ledger"]["armed_flights"] = [flight.nbt.plain(flight.armed_tag(a)) for a in witness["staged_ledger"]["armed_flights"]]
            cleanup.check(image, 0, None, cut, witness)

    def test_complete_ledger_log_is_unique_ordered_and_cannot_overwrite_ledger_identity(self):
        cut = "LF_S6_F8_PRUNE_BEFORE"; _, witness, _, _ = prefix(cut)
        nbt = flight.nbt; fields = {}
        for key, value in witness["staged_ledger"].items():
            if key == "ledger_id": fields[key] = flight.integrity.uid(value)
            elif isinstance(value, list): fields[key] = nbt.Tag(nbt.LIST, (0, []))
            else: fields[key] = flight.integrity.scalar(value, nbt.STRING if isinstance(value, str) else nbt.INT if key == "schema_version" else nbt.LONG)
        encoded = base64.b64encode(nbt.write(b"", flight.integrity.compound(fields))).decode()
        captured = base64.b64encode(nbt.write(b"", forward.native_tag(witness["capture"]))).decode()
        lines = ["ARCE_LOADER_S6_FLIGHT_TRANSFER cut=" + cut + " digest=" + "a"*64,
            "ARCE_LOADER_S6_FLIGHT_CAPTURE cut=" + cut + " nbt=" + captured,
            "ARCE_LOADER_S6_FLIGHT_PUBLICATION_SOURCE cut=" + cut + " phase_time=161 digest=" + "b"*64,
            "ARCE_LOADER_S6_FLIGHT_LANDED cut=" + cut + " landed_time=260 digest=" + "c"*64,
            "ARCE_LOADER_S6_FLIGHT_LEDGER cut=" + cut + " nbt=" + encoded,
            "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " hook=K8"]
        parsed = cleanup.witness(lines, cut)
        self.assertEqual(witness["staged_ledger"], parsed["staged_ledger"])
        self.assertNotIn("ledger", parsed)
        for invalid in (lines[:4] + lines[5:], lines[:5] + lines[4:], lines[:3] + [lines[4], lines[3], lines[5]],
                        lines[:4] + [lines[4][:-5], lines[5]]):
            with self.assertRaises(SmokeError): cleanup.witness(invalid, cut)

    def test_every_cleanup_prefix_and_two_restarts_preserve_the_same_landed_object(self):
        for cut in cleanup.CASES:
            image, witness, recovered, end = prefix(cut); before = copy.deepcopy(image)
            result = cleanup.check(image, 0, None, cut, witness)
            self.assertEqual(4500, result["raw_total"]); self.assertIsNone(result["available_total"])
            self.assertEqual(before, image)
            first = cleanup.check(recovered, 1, result["identity"], cut, end)["identity"]
            self.assertEqual(first, cleanup.check(recovered, 2, first, cut, end)["identity"])
            changed = copy.deepcopy(recovered); live = copy.deepcopy(end)
            changed["native"]["destination"]["RocketEntityData"]["flight_data"]["state_started_game_time"] += 1
            live["landed_time"] += 1; live["survivor_digest"] = forward.survivor_digest(changed["native"]["destination"])
            with self.assertRaises(SmokeError): cleanup.check(changed, 1, result["identity"], cut, live)

    def test_native_authority_generation_outcome_and_no_t_are_exact(self):
        for cut in cleanup.CASES:
            image, witness, _, _ = prefix(cut)
            for mutate in (lambda d: d.update(read_status="UNKNOWN"),
                           lambda d: d["native"]["ledger"].update(writer_generation=7),
                           lambda d: d["native"]["ledger"].update(issued_launch_serial=2),
                           lambda d: d["native"]["ledger"].update(extra="unexpected"),
                           lambda d: d["native"]["ledger"].update(armed_flights=[{}]),
                           lambda d: d["native"]["journal"].update(transfers=[{}]),
                           lambda d: d["native"].update(source={})):
                changed = copy.deepcopy(image); mutate(changed)
                with self.subTest(cut=cut), self.assertRaises(SmokeError): cleanup.check(changed, 0, None, cut, witness)

    def test_staged_complete_ledger_is_required_and_cannot_change_any_field(self):
        for cut in (c for c in cleanup.CASES if "_JOURNAL_" not in c):
            image, witness, _, _ = prefix(cut)
            changed = copy.deepcopy(witness); changed.pop("staged_ledger")
            with self.assertRaises(SmokeError): cleanup.check(image, 0, None, cut, changed)
            for key in witness["staged_ledger"]:
                changed = copy.deepcopy(witness); changed["staged_ledger"].pop(key)
                with self.subTest(cut=cut, field=key), self.assertRaises(SmokeError): cleanup.check(image, 0, None, cut, changed)

    def test_full_survivor_loader_and_captured_transfer_are_not_only_quantity_checks(self):
        for cut in cleanup.CASES:
            image, witness, _, _ = prefix(cut)
            for mutate in (lambda e: e["RocketEntityData"]["flight_data"]["fuel"].update(amount=501),
                           lambda e: e["RocketEntityData"]["resource_escrow"].update(resource_epoch=1),
                           lambda e: e.update(UUID="00000000-0000-0000-0000-000000000099"),
                           lambda e: e.update(Passengers=[{}])):
                changed = copy.deepcopy(image); mutate(changed["native"]["destination"])
                live = copy.deepcopy(witness); live["landed"]["survivor_digest"] = forward.survivor_digest(changed["native"]["destination"])
                with self.assertRaises(SmokeError): cleanup.check(changed, 0, None, cut, live)
            changed = copy.deepcopy(image); changed["native"]["loaders"][0]["arce_fluid_fuel_loader"]["tank"]["Amount"] = 3999
            with self.assertRaises(SmokeError): cleanup.check(changed, 0, None, cut, witness)
            changed = copy.deepcopy(witness); changed["capture"]["transfers"][0]["checksum"] = "0"*64
            with self.assertRaises(SmokeError): cleanup.check(image, 0, None, cut, changed)


if __name__ == "__main__": unittest.main()
