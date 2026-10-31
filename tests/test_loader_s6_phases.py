"""Synthetic complete F4/F5 controls, never counted as native evidence."""
import copy
import unittest
from test_loader_s6_forward import fixture
import loader_s6_phases as phases
from loader_s6_phases import forward, flight, SmokeError


def prefix(cut, stored_phase="COMMITTED"):
    image, witness, recovered, end = fixture()
    disk = image["native"]; row = disk["journal"]["transfers"][0]
    row.update(phase=stored_phase if cut.endswith("STORED") else phases.PHASES[cut],
               destination_entity_id=row["native_transfer"]["armed"]["d"])
    if cut != "LF_S6_F4_ACK":
        source = disk["source"]; source["Pos"][1] = 148.
        source["RocketEntityData"]["flight_data"] = copy.deepcopy(row["source_flight"])
        source["RocketEntityData"]["flight_data"]["state_started_game_time"] = 161
        witness["publication_source"] = dict(phase_time=161, digest=forward.survivor_digest(source))
    witness["phase"] = dict(phase=row["phase"], digest=forward.transfer_digest(row))
    if cut.endswith("STORED"):
        destination = copy.deepcopy(recovered["native"]["destination"])
        destination["Pos"][1] = 148.
        destination["RocketEntityData"]["flight_data"] = copy.deepcopy(row["destination_flight"])
        disk["destination"] = destination
        witness["stored"] = dict(owner="FIXTURE", phase="DESCENT", phase_time=180,
                                  digest=forward.survivor_digest(destination))
    return image, witness, recovered, end


class PhaseOracleTests(unittest.TestCase):
    def test_phase_log_framing_order_and_uniqueness(self):
        cut = "LF_S6_F4_STORED"
        transfer = "ARCE_LOADER_S6_FLIGHT_TRANSFER cut=" + cut + " digest=" + "a"*64
        source = "ARCE_LOADER_S6_FLIGHT_PUBLICATION_SOURCE cut=" + cut + " phase_time=161 digest=" + "b"*64
        stored = "ARCE_LOADER_S6_FLIGHT_STORED cut=" + cut + " owner=FIXTURE phase=DESCENT phase_time=180 digest=" + "c"*64
        phase = "ARCE_LOADER_S6_FLIGHT_PHASE cut=" + cut + " phase=COMMITTED digest=" + "d"*64
        pause = "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " hook=FIXTURE_PHASE_READBACK"
        lines = [transfer, source, stored, phase, pause]
        self.assertEqual("COMMITTED", phases.witness(lines, cut)["phase"]["phase"])
        for invalid in (lines[:3] + lines[4:], lines[:3] + [phase, phase, pause],
                        [source, transfer, stored, phase, pause], [transfer, phase, source, stored, pause],
                        [transfer, source, stored, phase[:-1], pause], [transfer, source, phase, pause]):
            with self.assertRaises(SmokeError): phases.witness(invalid, cut)

    def test_exact_prefixes_and_two_complete_restarts(self):
        for cut in phases.CASES:
            image, witness, recovered, end = prefix(cut); before = copy.deepcopy(image)
            first = phases.check(image, 0, None, cut, witness)
            self.assertEqual(5500 if cut.endswith("STORED") else 5000, first["raw_total"])
            self.assertIsNone(first["available_total"])
            self.assertEqual(before, image)
            identity = phases.check(recovered, 1, first["identity"], cut, end)["identity"]
            self.assertEqual(identity, phases.check(recovered, 2, identity, cut, end)["identity"])

    def test_fixture_save_allows_concurrent_checked_phase_progress_not_other_authority(self):
        cut = "LF_S6_F4_STORED"
        for phase in ("DESTINATION_SPAWNED", "PASSENGERS_TRANSFERRED", "SOURCE_REMOVED", "COMMITTED"):
            image, witness, _, _ = prefix(cut, phase)
            self.assertEqual("FIXTURE", phases.check(image, 0, None, cut, witness)["store_owner"])
        for phase in ("PREPARED", "CANCELLED", "UNKNOWN"):
            image, witness, _, _ = prefix(cut, phase)
            with self.assertRaises(SmokeError): phases.check(image, 0, None, cut, witness)

    def test_phase_and_full_checked_transfer_cannot_change_together(self):
        for cut in phases.CASES:
            image, witness, _, _ = prefix(cut)
            for mutate in (lambda t: t.update(owner_id="00000000-0000-0000-0000-000000000099"),
                           lambda t: t.update(destination_entity_id=t["source_entity_id"]),
                           lambda t: t.update(required_fuel=499), lambda t: t.update(extra="unexpected"),
                           lambda t: t.update(phase="PREPARED")):
                changed = copy.deepcopy(image); row = changed["native"]["journal"]["transfers"][0]; mutate(row)
                live = copy.deepcopy(witness); live["phase"] = dict(phase=row["phase"], digest=forward.transfer_digest(row))
                with self.subTest(cut=cut), self.assertRaises(SmokeError): phases.check(changed, 0, None, cut, live)

    def test_native_resources_identity_and_frame_remain_exact_even_with_rehashed_live_witness(self):
        changes = [lambda d: d.update(read_status="UNKNOWN"),
            lambda d: d["native"]["source"]["RocketEntityData"]["flight_data"]["fuel"].update(amount=999),
            lambda d: d["native"]["source"]["RocketEntityData"]["resource_escrow"].update(resource_epoch=0),
            lambda d: d["native"]["source"].update(Motion=[0., 1., 0.]),
            lambda d: d["native"]["source"].update(UUID="00000000-0000-0000-0000-000000000099"),
            lambda d: d["native"]["ledger"].update(writer_generation=5),
            lambda d: d["native"]["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=3999)]
        for cut in phases.CASES:
            image, witness, _, _ = prefix(cut)
            for mutate in changes:
                changed = copy.deepcopy(image); mutate(changed); live = copy.deepcopy(witness)
                if "publication_source" in live:
                    live["publication_source"]["digest"] = forward.survivor_digest(changed["native"]["source"])
                with self.subTest(cut=cut), self.assertRaises(SmokeError): phases.check(changed, 0, None, cut, live)

    def test_stored_destination_is_exact_and_no_other_cut_has_one(self):
        image, witness, _, _ = prefix("LF_S6_F4_STORED")
        for mutate in (lambda d: d["RocketEntityData"]["flight_data"]["fuel"].update(amount=501),
                       lambda d: d["RocketEntityData"]["resource_escrow"].update(resource_epoch=1),
                       lambda d: d.update(Pos=[80.5, 147., 64.5]), lambda d: d.update(Passengers=[{}])):
            changed = copy.deepcopy(image); mutate(changed["native"]["destination"])
            live = copy.deepcopy(witness); live["stored"]["digest"] = forward.survivor_digest(changed["native"]["destination"])
            with self.assertRaises(SmokeError): phases.check(changed, 0, None, "LF_S6_F4_STORED", live)
        for cut in set(phases.CASES) - {"LF_S6_F4_STORED"}:
            other, live, _, _ = prefix(cut); other["native"]["destination"] = image["native"]["destination"]
            with self.assertRaises(SmokeError): phases.check(other, 0, None, cut, live)


if __name__ == "__main__": unittest.main()
