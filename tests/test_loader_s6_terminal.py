"""Complete F6 prefix controls; all generated cases are synthetic."""
import base64
import copy
import unittest
from test_loader_s6_phases import prefix
from test_loader_s6_forward import fixture
import loader_s6_terminal as terminal
from loader_s6_terminal import forward, flight, SmokeError


def proof(cut):
    image, witness, recovered, end = prefix("LF_S6_F5_COMMITTED_ACK")
    witness["capture"] = copy.deepcopy(fixture()[0]["native"]["journal"])
    witness["landed"] = end
    if not cut.endswith("BEFORE_0"): image["native"]["source"] = None
    if cut.endswith("AFTER_1"): image["native"]["destination"] = copy.deepcopy(recovered["native"]["destination"])
    return image, witness, recovered, end


class TerminalOracleTests(unittest.TestCase):
    def test_already_landed_prefix_cannot_change_its_clock_on_first_restart(self):
        cut = terminal.CASES[-1]; image, witness, recovered, end = proof(cut)
        identity = terminal.check(image, 0, None, cut, witness)["identity"]
        recovered["native"]["destination"]["RocketEntityData"]["flight_data"]["state_started_game_time"] += 1
        end["landed_time"] += 1
        end["survivor_digest"] = forward.survivor_digest(recovered["native"]["destination"])
        with self.assertRaises(SmokeError): terminal.check(recovered, 1, identity, cut, end)

    def test_bounded_capture_and_log_order_require_complete_checked_bytes(self):
        cut = terminal.CASES[0]; image, witness, _, _ = proof(cut)
        raw = flight.nbt.write(b"", forward.native_tag(witness["capture"]))
        encoded = base64.b64encode(raw).decode()
        expected = forward.normalize(flight.nbt.plain(flight.nbt.read(raw)[1]))
        self.assertEqual(expected, terminal.decode_capture(encoded))
        for invalid in (None, "a"*87385, encoded + "=x", encoded[:-5], base64.b64encode(b"not nbt").decode()):
            with self.assertRaises(SmokeError): terminal.decode_capture(invalid)
        lines = ["ARCE_LOADER_S6_FLIGHT_TRANSFER cut=" + cut + " digest=" + "a"*64,
            "ARCE_LOADER_S6_FLIGHT_CAPTURE cut=" + cut + " nbt=" + encoded,
            "ARCE_LOADER_S6_FLIGHT_PUBLICATION_SOURCE cut=" + cut + " phase_time=161 digest=" + "b"*64,
            "ARCE_LOADER_S6_FLIGHT_LANDED cut=" + cut + " landed_time=260 digest=" + "c"*64,
            "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " hook=BARRIER_STORE_BEFORE_0"]
        self.assertEqual(expected, terminal.witness(lines, cut)["capture"])
        for invalid in (lines[:1] + lines[2:], lines[:2] + lines[1:], [lines[1], lines[0]] + lines[2:],
                        lines[:2] + [lines[3], lines[2], lines[4]], lines[:3] + [lines[3][:-1], lines[4]]):
            with self.assertRaises(SmokeError): terminal.witness(invalid, cut)

    def test_native_uuid_arrays_in_capture_and_checked_authority_agree(self):
        for cut in terminal.CASES:
            image, witness, _, _ = proof(cut)
            witness["capture"] = terminal.decode_capture(base64.b64encode(
                flight.nbt.write(b"", forward.native_tag(witness["capture"]))).decode())
            image["native"]["journal"] = flight.nbt.plain(forward.native_tag(image["native"]["journal"]))
            image["native"]["ledger"]["armed_flights"][0] = flight.nbt.plain(flight.armed_tag(
                image["native"]["ledger"]["armed_flights"][0]))
            terminal.check(image, 0, None, cut, witness)

    def test_each_exact_store_prefix_and_both_restarts(self):
        for cut in terminal.CASES:
            image, witness, recovered, end = proof(cut); before = copy.deepcopy(image)
            first = terminal.check(image, 0, None, cut, witness)
            self.assertEqual(5000 if cut.endswith("BEFORE_0") else 4500 if cut.endswith("AFTER_1") else 4000, first["raw_total"])
            self.assertIsNone(first["available_total"])
            self.assertIsNone(first["loss"])
            self.assertEqual(before, image)
            identity = terminal.check(recovered, 1, first["identity"], cut, end)["identity"]
            self.assertEqual(identity, terminal.check(recovered, 2, identity, cut, end)["identity"])

    def test_missing_or_extra_source_destination_and_authority_are_refused(self):
        source = proof(terminal.CASES[0])[0]["native"]["source"]
        destination = proof(terminal.CASES[-1])[0]["native"]["destination"]
        for cut in terminal.CASES:
            image, witness, _, _ = proof(cut)
            for endpoint, opposite in (("source", source), ("destination", destination)):
                changed = copy.deepcopy(image)
                changed["native"][endpoint] = None if changed["native"][endpoint] is not None else opposite
                with self.assertRaises(SmokeError): terminal.check(changed, 0, None, cut, witness)
            for mutate in (lambda d: d.update(read_status="UNKNOWN"),
                           lambda d: d["native"]["journal"]["transfers"][0].update(phase="SOURCE_REMOVED"),
                           lambda d: d["native"]["ledger"].update(writer_generation=5),
                           lambda d: d["native"]["ledger"].update(armed_flights=[]),
                           lambda d: d["native"]["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=4001)):
                changed = copy.deepcopy(image); mutate(changed)
                with self.assertRaises(SmokeError): terminal.check(changed, 0, None, cut, witness)

    def test_retained_checked_capture_cannot_authorize_changed_payload(self):
        for cut in terminal.CASES:
            image, witness, _, _ = proof(cut)
            changed = copy.deepcopy(witness)
            changed["capture"]["transfers"][0]["destination_flight"]["fuel"]["propulsion"]["primary"]["amount"] = 501
            with self.assertRaises(SmokeError): terminal.check(image, 0, None, cut, changed)
            changed = copy.deepcopy(witness); changed["capture"]["transfers"] = []
            with self.assertRaises(SmokeError): terminal.check(image, 0, None, cut, changed)

    def test_landed_complete_resources_and_identity_not_only_the_digest_are_checked(self):
        cut = terminal.CASES[-1]; image, witness, _, _ = proof(cut)
        for mutate in (lambda e: e["RocketEntityData"]["flight_data"]["fuel"].update(amount=501),
                       lambda e: e["RocketEntityData"]["resource_escrow"].update(resource_epoch=1),
                       lambda e: e.update(UUID="00000000-0000-0000-0000-000000000099"),
                       lambda e: e.update(Pos=[80.5, 101., 64.5]), lambda e: e.update(Passengers=[{}])):
            changed = copy.deepcopy(image); mutate(changed["native"]["destination"])
            live = copy.deepcopy(witness); live["landed"]["survivor_digest"] = forward.survivor_digest(changed["native"]["destination"])
            with self.assertRaises(SmokeError): terminal.check(changed, 0, None, cut, live)


if __name__ == "__main__": unittest.main()
