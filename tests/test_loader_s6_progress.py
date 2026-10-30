"""Persisted phase fixtures and complete native mutation controls, not native evidence."""
import copy
import unittest
from test_loader_s6_forward import fixture
import loader_s6_progress as progress
import loader_s6_forward as forward
from loader_s6_forward import flight, SmokeError


def stored(cut):
    image, witness, recovered, end = fixture()
    row = image["native"]["journal"]["transfers"][0]
    source = image["native"]["source"]
    phase = cut.removeprefix("LF_S6_F3_")
    state = copy.deepcopy(row["source_flight"])
    state.update(state=phase, state_started_game_time=80 if phase == "ASCENT" else 160)
    source["RocketEntityData"]["flight_data"] = state
    source["Pos"][1] = 112. if phase == "ASCENT" else 148.
    witness = dict(witness, stored=dict(owner="FIXTURE", phase=phase,
        phase_time=state["state_started_game_time"], digest=forward.survivor_digest(source)))
    return image, witness, recovered, end


class ProgressOracleTests(unittest.TestCase):
    def test_fixture_readback_witness_requires_exact_framing_and_order(self):
        for cut in progress.CASES:
            transfer = "ARCE_LOADER_S6_FLIGHT_TRANSFER cut=" + cut + " digest=" + "a"*64
            stored_line = "ARCE_LOADER_S6_FLIGHT_STORED cut=" + cut + " owner=FIXTURE phase=ASCENT phase_time=80 digest=" + "b"*64
            pause = "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " hook=FIXTURE_PHASE_READBACK"
            self.assertEqual("FIXTURE", progress.stored_witness([transfer, stored_line, pause], cut)["owner"])
            for lines in ([transfer, pause], [stored_line, transfer, pause], [transfer, pause, stored_line],
                          [transfer, stored_line, stored_line, pause], [transfer, stored_line[:-1], pause],
                          [transfer, stored_line.replace("FIXTURE", "PRODUCT"), pause]):
                with self.assertRaises(SmokeError): progress.stored_witness(lines, cut)

    def test_exact_stored_source_and_two_restarts(self):
        for cut in progress.CASES:
            image, witness, recovered, end = stored(cut)
            before = copy.deepcopy(image)
            first = progress.check(image, 0, None, cut, witness)
            self.assertEqual(5000, first["raw_total"])
            self.assertIsNone(first["available_total"])
            self.assertEqual(before, image)
            identity = progress.check(recovered, 1, first["identity"], cut, end)["identity"]
            self.assertEqual(identity, progress.check(recovered, 2, identity, cut, end)["identity"])

    def test_actual_phase_cannot_be_replaced_by_stationary_or_another_phase(self):
        for cut in progress.CASES:
            image, witness, _, _ = fixture()
            with self.subTest(cut=cut), self.assertRaises(SmokeError):
                progress.check(image, 0, None, cut, witness)

    def test_complete_typed_native_and_authority_mutations_are_rejected(self):
        changes = [lambda d: d.update(read_status="UNKNOWN"),
            lambda d: d["native"]["source"]["RocketEntityData"]["flight_data"]["fuel"].update(amount=1001),
            lambda d: d["native"]["source"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"].update(revision=2),
            lambda d: d["native"]["source"]["RocketEntityData"]["snapshot"].update(content_hash="0"*64),
            lambda d: d["native"]["source"]["RocketEntityData"]["resource_escrow"].update(resource_epoch=2),
            lambda d: d["native"]["source"]["RocketEntityData"]["resource_escrow"].update(markers=[{}]),
            lambda d: d["native"]["source"].update(UUID="00000000-0000-0000-0000-000000000099"),
            lambda d: d["native"]["source"].update(Pos=[64.5, 149., 64.5]),
            lambda d: d["native"]["source"].update(Motion=[0., 1., 0.]),
            lambda d: d["native"]["ledger"].update(writer_generation=5),
            lambda d: d["native"]["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=3999),
            lambda d: d["native"]["journal"]["transfers"][0].update(checksum="0"*64)]
        for cut in progress.CASES:
            image, witness, _, _ = stored(cut)
            for index, change in enumerate(changes):
                changed = copy.deepcopy(image); change(changed)
                live = copy.deepcopy(witness)
                live["stored"]["digest"] = forward.survivor_digest(changed["native"]["source"])
                with self.subTest(cut=cut, mutation=index), self.assertRaises(SmokeError):
                    progress.check(changed, 0, None, cut, live)

    def test_stored_witness_is_mandatory_exact_and_fixture_owned(self):
        image, witness, _, _ = stored(progress.CASES[0])
        for key, value in [("owner", "PRODUCT"), ("phase", "TRANSIT"), ("phase_time", 81), ("digest", "0"*64)]:
            changed = copy.deepcopy(witness); changed["stored"][key] = value
            with self.assertRaises(SmokeError): progress.check(image, 0, None, progress.CASES[0], changed)
        witness.pop("stored")
        with self.assertRaises(SmokeError): progress.check(image, 0, None, progress.CASES[0], witness)


if __name__ == "__main__": unittest.main()
