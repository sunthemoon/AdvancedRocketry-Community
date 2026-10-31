"""Ordinary-stage mutations over the complete loader-funded F8 fixture."""
import copy
import base64
import unittest
import test_loader_s6_cleanup as first
import loader_s6_ordinary as ordinary
from loader_s6_ordinary import forward, flight, SmokeError

def fixture():
    image, witness, _, _ = first.prefix("LF_S6_F8_PRUNE_ACK")
    disk = image["native"]; source = disk["destination"]; data = source["RocketEntityData"]
    base = data["flight_data"]; old = witness["capture"]["transfers"][0]
    transfer = "00000000-0000-0000-0000-000000000021"
    destination = "00000000-0000-0000-0000-000000000022"
    start = base["state_started_game_time"] + 10
    plan = copy.deepcopy(old["source_flight"]["plan"])
    plan.update(request_id=transfer, source_body=base["current_body"], destination_body="advancedrocketrycommunity:earth",
                required_fuel=250, created_at_game_time=start,
                destination_target=dict(schema_version=1, type="advancedrocketrycommunity:body_surface", body_id="advancedrocketrycommunity:earth"))
    plan["propulsion_debit"].update(primary=250, revision=2,
        resources=flight.integrity.fuel_signature(base["fuel"]["propulsion"]["descriptor"], 500, 2),
        catalog=flight.integrity.sha("W211 R203 ordinary historical flight"))
    transit = copy.deepcopy(base); transit.update(state="TRANSIT", state_started_game_time=start+140, plan=plan, active_transfer_id=transfer)
    descent = copy.deepcopy(transit); descent.update(state="DESCENT", state_started_game_time=start+160,
        current_body=plan["destination_body"], current_origin=[96, 100, 64], current_target=plan["destination_target"])
    descent["fuel"].update(amount=250, committed_debits=base["fuel"]["committed_debits"]+[dict(transaction_id=transfer)])
    descent["fuel"]["propulsion"].update(primary=flight.integrity.mono_bank(250), revision=3)
    snapshot = copy.deepcopy(data["snapshot"])
    snapshot.update(snapshot_id="00000000-0000-0000-0000-000000000023", source_origin=[96, 100, 64], created_at_game_time=start, content_hash="8"*64)
    row = dict(schema_version=2, transfer_id=transfer, phase="COMMITTED", logical_rocket_id=data["assembly_transaction_id"], owner_id=data["owner_id"],
        source_entity_id=source["UUID"], destination_entity_id=destination, source_snapshot=data["snapshot"], destination_snapshot=snapshot,
        source_flight=transit, destination_flight=descent, required_fuel=250, created_at_game_time=start, checksum="9"*64)
    disk.update(original=None, source=source, destination=None); disk["journal"]["transfers"]=[row]
    witness = dict(first=witness, ordinary_digest=forward.transfer_digest(row))
    recovered = copy.deepcopy(image); final = copy.deepcopy(source)
    final.update(UUID=destination, Pos=[96.5, 100., 64.5]); state = copy.deepcopy(descent)
    state.update(state="LANDED", state_started_game_time=start+240); state.pop("plan"); state.pop("active_transfer_id")
    final["RocketEntityData"].update(snapshot=snapshot, flight_data=state, resource_escrow=dict(schema_version=1, resource_epoch=0, markers=[]))
    recovered["native"].update(source=None, destination=final)
    end = dict(landed_time=start+240, survivor_digest=forward.survivor_digest(final))
    return image, witness, recovered, end

class OrdinaryOracleTests(unittest.TestCase):
    def test_log_parser_requires_the_ordered_checked_setup_and_three_actual_rejections(self):
        image,witness,_,_=fixture(); w=witness["first"]; cut=ordinary.F9
        def encoded(value): return base64.b64encode(flight.nbt.write(b"",forward.native_tag(value))).decode()
        nbt=flight.nbt
        ledger_tag=flight.integrity.compound({key:flight.integrity.uid(value) if key=="ledger_id" else
            nbt.Tag(nbt.LIST,(0,[])) if isinstance(value,list) else flight.integrity.scalar(value,
                nbt.STRING if isinstance(value,str) else nbt.INT if key=="schema_version" else nbt.LONG)
            for key,value in w["staged_ledger"].items()})
        ledger_encoded=base64.b64encode(nbt.write(b"",ledger_tag)).decode()
        lines=[f"ARCE_LOADER_S6_FLIGHT_FILLED cut={cut} operation={w['operation']} source={w['source']} logical={w['logical']} owner={w['owner']} ledger={w['ledger']} source_digest={w['source_digest']} tank=4000 bank=1000 epoch=1",
            f"ARCE_LOADER_S6_FLIGHT_ARMED cut={cut} digest={w['armed_digest']}",
            f"ARCE_LOADER_S6_FLIGHT_TRANSFER cut={cut} digest={w['transfer_digest']}",
            f"ARCE_LOADER_S6_FLIGHT_CAPTURE cut={cut} nbt={encoded(w['capture'])}",
            f"ARCE_LOADER_S6_FLIGHT_PUBLICATION_SOURCE cut={cut} phase_time={w['publication_source']['phase_time']} digest={w['publication_source']['digest']}",
            f"ARCE_LOADER_S6_FLIGHT_LANDED cut={cut} landed_time={w['landed']['landed_time']} digest={w['landed']['survivor_digest']}",
            f"ARCE_LOADER_S6_ORDINARY_SETTLED cut={cut} nbt={ledger_encoded}",
            f"ARCE_LOADER_S6_ORDINARY_HISTORICAL cut={cut} digest={witness['ordinary_digest']} schema=2 source_bank=500 destination_bank=250",
            f"ARCE_LOADER_S6_ORDINARY_PAUSED cut={cut} rejected=3 sequence=9 source_unchanged=true"]
        expected=copy.deepcopy(witness); expected["first"].pop("phase",None)
        expected["first"]["capture"]=ordinary.cleanup.terminal.decode_capture(encoded(w["capture"]))
        self.assertEqual(expected,ordinary.witness(lines,0))
        for bad in (lines[1:],lines+[lines[-1]],lines[:-1],list(reversed(lines)),lines[:-1]+[lines[-1].replace("rejected=3","rejected=0")]):
            with self.assertRaises(SmokeError): ordinary.check_logs(bad,0)

    def test_first_settled_source_then_exact_second_debit_survives_two_restarts(self):
        image, witness, recovered, end = fixture(); before=copy.deepcopy(image)
        initial=ordinary.check(image, 0, witness=witness)
        self.assertEqual(4500, initial["raw_total"]); self.assertIsNone(initial["loss"])
        one=ordinary.check(recovered, 1, initial["identity"], end)
        self.assertEqual(4250, one["raw_total"]); self.assertEqual(750, one["authorized_debit"]); self.assertEqual(0, one["loss"])
        self.assertEqual(one, ordinary.check(recovered, 2, one["identity"], end)); self.assertEqual(before, image)

    def test_changed_full_source_record_or_first_authority_is_rejected(self):
        image, witness, _, _=fixture()
        for mutate in (lambda d:d.update(read_status="UNKNOWN"),
                lambda d:d["native"].update(original={}),
                lambda d:d["native"]["ledger"].update(issued_launch_serial=2),
                lambda d:d["native"]["source"]["RocketEntityData"]["resource_escrow"].pop("ledger_id"),
                lambda d:d["native"]["source"]["RocketEntityData"]["flight_data"]["fuel"].update(amount=501),
                lambda d:d["native"]["journal"]["transfers"][0].update(schema_version=3),
                lambda d:d["native"]["journal"]["transfers"][0].update(checksum="0"*64),
                lambda d:d["native"]["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=4001)):
            changed=copy.deepcopy(image); mutate(changed)
            with self.assertRaises(SmokeError): ordinary.check(changed, 0, witness=witness)

    def test_restart_does_not_duplicate_source_change_identity_or_rededuct(self):
        image,witness,recovered,end=fixture(); identity=ordinary.check(image,0,witness=witness)["identity"]
        for mutate in (lambda d:d.update(source=image["native"]["source"]),
                lambda d:d["destination"].update(UUID="00000000-0000-0000-0000-000000000099"),
                lambda d:d["destination"]["RocketEntityData"]["resource_escrow"].update(ledger_id=image["native"]["ledger"]["ledger_id"]),
                lambda d:d["destination"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"].update(revision=4),
                lambda d:d["journal"]["transfers"][0].update(phase="PREPARED")):
            changed=copy.deepcopy(recovered); mutate(changed["native"])
            with self.assertRaises(SmokeError): ordinary.check(changed,1,identity,end)
