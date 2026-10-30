"""Full authority and immutable survivor mutations for S6's F2 forward cuts."""
import copy
import base64
import json
from pathlib import Path
import unittest

from test_loader_s6_flight import seed
import loader_s6_flight as flight
import loader_s6_forward as forward
from run_dedicated_server_smoke import SmokeError


def fixture():
    image = seed(); disk = image["native"]; root = disk["ledger"]
    source = disk["source"]; data = source["RocketEntityData"]; initial = data["flight_data"]
    transfer = "00000000-0000-0000-0000-000000000011"
    destination = "00000000-0000-0000-0000-000000000012"
    arm = dict(v=1, ledger=root["ledger_id"], l=data["assembly_transaction_id"], o=data["owner_id"],
        src=source["UUID"], epoch=1, t=transfer, serial=1, g=4, binding=[1]*32, launch=[2]*32,
        source=list(bytes.fromhex(flight.source_digest(source))), dest=[3]*32, d=destination,
        j=dict(rocket_id=data["assembly_transaction_id"], journal_generation=1, journal_digest=[4]*32), release=0, outcome="ARMED")
    root.update(writer_generation=4, issued_launch_serial=1, armed_flights=[arm])
    plan = dict(schema_version=4, request_id=transfer, source_body=initial["current_body"],
        destination_body="advancedrocketrycommunity:moon", source_dimension="minecraft:overworld",
        destination_dimension="minecraft:overworld", required_fuel=500, created_at_game_time=20,
        destination_target=dict(schema_version=1, type="advancedrocketrycommunity:body_surface", body_id="advancedrocketrycommunity:moon"),
        propulsion_debit=dict(schema=1, primary=500, oxidizer=0, revision=1,
            resources=flight.integrity.fuel_signature(initial["fuel"]["propulsion"]["descriptor"], 1000, 1),
            catalog=flight.integrity.sha("W211 S6 real-loader flight")))
    transit = copy.deepcopy(initial); transit.update(state="TRANSIT", state_started_game_time=160, plan=plan, active_transfer_id=transfer)
    descent = copy.deepcopy(transit); descent.update(state="DESCENT", state_started_game_time=180,
        current_body="advancedrocketrycommunity:moon", current_origin=[80, 100, 64], current_target=plan["destination_target"])
    descent["fuel"].update(amount=500, committed_debits=[dict(transaction_id=transfer)])
    descent["fuel"]["propulsion"].update(primary=flight.integrity.mono_bank(500), revision=2)
    snapshot = copy.deepcopy(data["snapshot"])
    snapshot.update(snapshot_id="00000000-0000-0000-0000-000000000013", source_origin=[80, 100, 64],
                    created_at_game_time=20, content_hash="5"*64)
    pose = dict(pos=[64.5, 100., 64.5], motion=[0., 0., 0.], no_gravity=1, no_physics=1,
                on_ground=0, invulnerable=0, yaw=0., pitch=0., passengers=[],
                flight_changes=dict(state=initial["state"], state_started_game_time=initial["state_started_game_time"]),
                flight_absent=["active_transfer_id", "plan"])
    target_pose = copy.deepcopy(pose); target_pose.update(pos=[80.5, 148., 64.5], flight_changes={}, flight_absent=[])
    row = dict(schema_version=3, transfer_id=transfer, phase="PREPARED", logical_rocket_id=arm["l"], owner_id=arm["o"],
        source_entity_id=arm["src"], source_snapshot=data["snapshot"], destination_snapshot=snapshot,
        source_flight=transit, destination_flight=descent, required_fuel=500, created_at_game_time=20, checksum="6"*64,
        native_transfer=dict(schema_version=1, armed=arm, source=pose, destination=target_pose, signature_kind="FUEL_VECTOR_V1"))
    disk["journal"]["transfers"] = [row]
    witness = dict(operation="00000000-0000-0000-0000-000000000001", source=source["UUID"], logical=arm["l"],
        owner=arm["o"], ledger=root["ledger_id"], source_digest=flight.source_digest(source),
        armed_digest=flight.armed_digest(arm), transfer_digest=forward.transfer_digest(row))
    recovered = copy.deepcopy(image); result = recovered["native"]
    survivor = copy.deepcopy(source); survivor.update(UUID=destination, Pos=[80.5, 100., 64.5])
    landed = copy.deepcopy(descent); landed.update(state="LANDED", state_started_game_time=260)
    landed.pop("plan"); landed.pop("active_transfer_id")
    survivor["RocketEntityData"].update(snapshot=snapshot, flight_data=landed,
        resource_escrow=dict(schema_version=1, ledger_id=root["ledger_id"], resource_epoch=0, markers=[]))
    result.update(source=None, destination=survivor)
    result["ledger"].update(writer_generation=6, armed_flights=[])
    result["journal"]["transfers"] = []
    recovery_witness = dict(landed_time=260, survivor_digest=forward.survivor_digest(survivor))
    return image, witness, recovered, recovery_witness


class ForwardOracleTests(unittest.TestCase):
    def test_native_debit_uuid_arrays_are_canonicalized_on_initial_and_both_restarts(self):
        retained = json.loads((Path(__file__).parent / "fixtures/loader_s6/forward-replaced-native.json").read_text())
        row = retained["images"][0]["native"]["journal"]["transfers"][0]
        self.assertIsInstance(row["destination_flight"]["fuel"]["committed_debits"][0]["transaction_id"], list)
        identity = None
        for restart, image in enumerate(retained["images"]):
            identity = flight.check(image, restart, identity, forward.CASES[0], retained["witnesses"][restart])["identity"]
        changed = copy.deepcopy(retained["images"][0])
        changed["native"]["journal"]["transfers"][0]["destination_flight"]["fuel"]["committed_debits"][0]["transaction_id"][0] ^= 1
        with self.assertRaises(SmokeError): flight.check(changed, 0, cut=forward.CASES[0], witness=retained["witnesses"][0])

    def test_complete_record_digest_matches_the_independent_java_codec_bytes(self):
        sample = json.loads((Path(__file__).parent / "fixtures/loader_s6/forward-codec.json").read_text())
        raw = base64.b64decode(sample["nbt_base64"])
        self.assertEqual(sample["record"], flight.nbt.plain(flight.nbt.read(raw)[1]))
        self.assertEqual(sample["expected_digest"], forward.transfer_digest(sample["record"]))

    def test_missing_duplicate_malformed_and_misordered_full_witnesses_are_refused(self):
        for cut in forward.CASES:
            arm = "ARCE_LOADER_S6_FLIGHT_ARMED cut=" + cut + " digest=" + "a"*64
            row = "ARCE_LOADER_S6_FLIGHT_TRANSFER cut=" + cut + " digest=" + "b"*64
            pause = "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " hook=unused"
            self.assertEqual("b"*64, forward.transfer_witness([arm, row, pause], cut))
            for lines in ([arm, pause], [arm, row, row, pause], [arm, pause, row], [row, arm, pause], [arm, row[:-1], pause]):
                with self.assertRaises(SmokeError): forward.transfer_witness(lines, cut)
            survivor = "ARCE_LOADER_S6_FLIGHT_SURVIVOR cut=" + cut + " landed_time=260 digest=" + "c"*64
            self.assertEqual(dict(landed_time=260, survivor_digest="c"*64), forward.recovery_witness([survivor], cut))
            for lines in ([], [survivor, survivor], [survivor[:-1]]):
                with self.assertRaises(SmokeError): forward.recovery_witness(lines, cut)

    def test_prepared_commit_and_both_restarts_preserve_complete_authority(self):
        for cut in forward.CASES:
            image, witness, recovered, end = fixture()
            identity = flight.check(image, 0, cut=cut, witness=witness)["identity"]
            identity = flight.check(recovered, 1, identity, cut, end)["identity"]
            self.assertEqual(identity, flight.check(recovered, 2, identity, cut, end)["identity"])

    def test_every_transfer_leaf_is_bound_to_the_checked_witness(self):
        image, witness, _, _ = fixture(); cut = forward.CASES[0]
        def paths(value, prefix=()):
            if isinstance(value, dict):
                for key, child in value.items(): yield from paths(child, prefix + (key,))
            elif isinstance(value, list) and value:
                for i, child in enumerate(value): yield from paths(child, prefix + (i,))
            else: yield prefix
        for path in paths(image["native"]["journal"]["transfers"][0]):
            changed = copy.deepcopy(image); target = changed["native"]["journal"]["transfers"][0]
            for key in path[:-1]: target = target[key]
            value = target[path[-1]]
            target[path[-1]] = value + 1 if isinstance(value, (int, float)) else ["extra"] if isinstance(value, list) else value + "x"
            with self.subTest(path=path), self.assertRaises(SmokeError): flight.check(changed, 0, cut=cut, witness=witness)

    def test_native_state_and_ledger_mutations_are_not_hidden_by_the_transfer_digest(self):
        image, witness, _, _ = fixture(); cut = forward.CASES[0]
        mutations = [lambda d: d.update(read_status="UNKNOWN"),
            lambda d: d["native"]["ledger"].update(writer_generation=5),
            lambda d: d["native"]["journal"].update(extra=1),
            lambda d: d["native"]["source"]["RocketEntityData"]["snapshot"].update(created_at_game_time=999),
            lambda d: d["native"]["source"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"].update(revision=2),
            lambda d: d["native"]["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=3999)]
        for mutate in mutations:
            changed = copy.deepcopy(image); mutate(changed)
            with self.assertRaises(SmokeError): flight.check(changed, 0, cut=cut, witness=witness)

    def test_recovery_rejects_changed_survivor_and_loader_even_with_a_matching_live_hash(self):
        image, witness, recovered, end = fixture(); cut = forward.CASES[0]
        identity = flight.check(image, 0, cut=cut, witness=witness)["identity"]
        mutations = [lambda d: d["ledger"].update(writer_generation=7),
            lambda d: d.update(source=copy.deepcopy(image["native"]["source"])),
            lambda d: d["destination"]["RocketEntityData"]["snapshot"].update(content_hash="7"*64),
            lambda d: d["destination"]["RocketEntityData"]["flight_data"]["fuel"].update(amount=501),
            lambda d: d["destination"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"].update(revision=3),
            lambda d: d["destination"]["RocketEntityData"]["flight_data"]["fuel"].update(committed_debits=[]),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["binding"].update(physical_rocket=d["destination"]["UUID"])]
        for mutate in mutations:
            changed = copy.deepcopy(recovered); mutate(changed["native"])
            live = dict(end, survivor_digest=forward.survivor_digest(changed["native"]["destination"]))
            with self.assertRaises(SmokeError): flight.check(changed, 1, identity, cut, live)

    def test_second_restart_cannot_change_the_landing_clock(self):
        image, witness, recovered, end = fixture(); cut = forward.CASES[0]
        identity = flight.check(image, 0, cut=cut, witness=witness)["identity"]
        identity = flight.check(recovered, 1, identity, cut, end)["identity"]
        changed = copy.deepcopy(recovered)
        changed["native"]["destination"]["RocketEntityData"]["flight_data"]["state_started_game_time"] += 1
        live = dict(landed_time=261, survivor_digest=forward.survivor_digest(changed["native"]["destination"]))
        with self.assertRaises(SmokeError): flight.check(changed, 2, identity, cut, live)


if __name__ == "__main__": unittest.main()
