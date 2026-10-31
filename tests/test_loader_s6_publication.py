"""Publication controls are synthetic; only the separately run native rows are evidence."""
import copy
import base64
import unittest
from test_loader_s6_forward import fixture
import loader_s6_publication as publication
from loader_s6_publication import forward, flight, SmokeError


def controls(cut):
    image, witness, recovered, end = fixture()
    disk = image["native"]; row = disk["journal"]["transfers"][0]
    witness["capture"] = copy.deepcopy(disk["journal"])
    phase = "DESTINATION_SPAWNED" if cut == publication.RETURN else "PASSENGERS_TRANSFERRED"
    row.update(phase=phase, destination_entity_id=row["native_transfer"]["armed"]["d"])
    source = disk["source"]; state = source["RocketEntityData"]["flight_data"]
    veto_source = copy.deepcopy(source)
    veto_source["Pos"][1] = 148.
    veto_source["RocketEntityData"]["flight_data"] = copy.deepcopy(row["source_flight"])
    if cut == publication.RETURN:
        state.update(state="FUELED", state_started_game_time=200)
        witness["returned"] = dict(time=200, digest=forward.survivor_digest(source))
        recovered = copy.deepcopy(image)
        recovered["native"]["ledger"].update(writer_generation=6, armed_flights=[])
        recovered["native"]["journal"]["transfers"] = []
        end = dict(time=200, digest=forward.survivor_digest(source))
    else:
        source["Pos"][1] = 148.
        source["RocketEntityData"]["flight_data"] = copy.deepcopy(row["source_flight"])
        witness["publication_source"] = dict(phase_time=160, digest=forward.survivor_digest(source))
    witness["phase"] = dict(phase=phase, digest=forward.transfer_digest(row))
    veto = dict(phase=phase, transfer=row["transfer_id"], destination=row["destination_entity_id"],
                source_time=160, source_digest=forward.survivor_digest(veto_source), transfer_digest=forward.transfer_digest(row), count=1)
    witness["veto"] = veto
    return image, witness, recovered, end


class PublicationOracleTests(unittest.TestCase):
    def test_complete_log_witnesses_are_ordered_unique_and_bound_to_both_rounds(self):
        for cut in publication.CASES:
            image, w, recovered, end = controls(cut)
            encoded = base64.b64encode(flight.nbt.write(b"", forward.native_tag(w["capture"]))).decode()
            lines = [f"ARCE_LOADER_S6_FLIGHT_FILLED cut={cut} operation={w['operation']} source={w['source']} logical={w['logical']} owner={w['owner']} ledger={w['ledger']} source_digest={w['source_digest']} tank=4000 bank=1000 epoch=1",
                f"ARCE_LOADER_S6_FLIGHT_ARMED cut={cut} digest={w['armed_digest']}",
                f"ARCE_LOADER_S6_FLIGHT_TRANSFER cut={cut} digest={w['transfer_digest']}",
                f"ARCE_LOADER_S6_FLIGHT_CAPTURE cut={cut} nbt={encoded}"]
            veto = w["veto"]
            rejected = f"ARCE_LOADER_S6_PUBLICATION_VETO cut={cut} phase={veto['phase']} transfer={veto['transfer']} destination={veto['destination']} source_time={veto['source_time']} source_digest={veto['source_digest']} transfer_digest={veto['transfer_digest']} count=1"
            if cut == publication.RETURN:
                returned = f"ARCE_LOADER_S6_PUBLICATION_RETURNED cut={cut} time={w['returned']['time']} digest={w['returned']['digest']}"
                lines += [rejected, returned]
            else:
                value = w["publication_source"]
                lines += [f"ARCE_LOADER_S6_FLIGHT_PUBLICATION_SOURCE cut={cut} phase_time={value['phase_time']} digest={value['digest']}"]
            lines += [f"ARCE_LOADER_S6_FLIGHT_PHASE cut={cut} phase={w['phase']['phase']} digest={w['phase']['digest']}",
                      f"ARCE_LOADER_S6_FLIGHT_PAUSED cut={cut} hook={publication.hook(cut, 0)}"]
            parsed = publication.check_logs(lines, 0, cut)
            result = publication.check(image, 0, cut=cut, witness=parsed)
            for index in range(len(lines)):
                with self.subTest(cut=cut, omitted=index), self.assertRaises(SmokeError):
                    publication.check_logs(lines[:index] + lines[index + 1:], 0, cut)
            for invalid in (lines + [lines[-1]], list(reversed(lines)), [line.replace("count=1", "count=0") for line in lines] if cut == publication.RETURN else lines[1:]):
                with self.assertRaises(SmokeError): publication.check_logs(invalid, 0, cut)
            if cut == publication.HELD:
                retry = [rejected, f"ARCE_LOADER_S6_PUBLICATION_HELD cut={cut} source_locked=true destination_absent=true return=false",
                         f"ARCE_LOADER_S6_FLIGHT_PAUSED cut={cut} hook=PUBLICATION_HELD"]
                publication.check(image, 1, result["identity"], cut, publication.check_logs(retry, 1, cut))
                for invalid in (retry[1:], retry[:-1], retry + [rejected], list(reversed(retry)), [line.replace("return=false", "return=true") for line in retry]):
                    with self.assertRaises(SmokeError): publication.check_logs(invalid, 1, cut)

    def test_returned_source_and_held_retry_have_different_completed_outcomes(self):
        for cut in publication.CASES:
            image, witness, recovered, end = controls(cut)
            result = publication.check(image, 0, cut=cut, witness=witness)
            self.assertEqual((5000, 4000, 1000), (result["raw_total"], result["available_total"], result["held_total"]))
            self.assertIsNone(result["loss"])
            if cut == publication.HELD:
                result = publication.check(image, 1, result["identity"], cut, dict(veto=witness["veto"]))
                self.assertEqual(1000, result["held_total"])
                result = publication.check(recovered, 2, result["identity"], cut, end)
                self.assertEqual((4500, 500, 0), (result["raw_total"], result["authorized_debit"], result["held_total"]))
            else:
                result = publication.check(recovered, 1, result["identity"], cut, end)
                self.assertEqual((5000, 0, 0), (result["raw_total"], result["authorized_debit"], result["held_total"]))
                self.assertEqual(result, publication.check(recovered, 2, result["identity"], cut, end))

    def test_unknown_and_missing_record_cannot_supply_held_resources(self):
        for cut in publication.CASES:
            image, witness, _, _ = controls(cut)
            for mutate in (lambda x: x.update(read_status="UNKNOWN"),
                           lambda x: x["native"]["ledger"].update(armed_flights=[]),
                           lambda x: x["native"]["journal"].update(transfers=[])):
                bad = copy.deepcopy(image); mutate(bad)
                with self.assertRaises(SmokeError): publication.check(bad, 0, cut=cut, witness=witness)

    def test_held_restart_rejects_each_changed_native_leaf_without_rebalancing(self):
        image, witness, _, _ = controls(publication.HELD)
        identity = publication.check(image, 0, cut=publication.HELD, witness=witness)["identity"]
        def leaves(node, path=()):
            if isinstance(node, dict):
                for key, value in node.items():
                    if key != "files": yield from leaves(value, path + (key,))
            elif isinstance(node, list) and node:
                for index, value in enumerate(node): yield from leaves(value, path + (index,))
            else: yield path
        for path in leaves(image["native"]):
            with self.subTest(path=path):
                bad = copy.deepcopy(image); node = bad["native"]
                for key in path[:-1]: node = node[key]
                old = node[path[-1]]
                node[path[-1]] = old + 1 if isinstance(old, (int, float)) else "changed"
                with self.assertRaises(SmokeError):
                    publication.check(bad, 1, identity, publication.HELD, dict(veto=witness["veto"]))

    def test_return_restart_rejects_refund_duplicate_destination_or_changed_typed_fuel(self):
        image, witness, recovered, end = controls(publication.RETURN)
        identity = publication.check(image, 0, cut=publication.RETURN, witness=witness)["identity"]
        for kind in ("refund", "destination", "fluid", "snapshot", "epoch", "debit"):
            with self.subTest(kind=kind):
                bad = copy.deepcopy(recovered); disk = bad["native"]; data = disk["source"]["RocketEntityData"]
                if kind == "refund": data["flight_data"]["fuel"]["amount"] += 500
                elif kind == "destination": disk["destination"] = copy.deepcopy(disk["source"])
                elif kind == "fluid": data["flight_data"]["fuel"]["propulsion"]["primary"]["fluid"] = "minecraft:water"
                elif kind == "snapshot": data["snapshot"]["blocks"] = []
                elif kind == "epoch": data["resource_escrow"]["resource_epoch"] += 1
                else: data["flight_data"]["fuel"]["committed_debits"] = [dict(transaction_id=identity["transfer"]["transfer_id"])]
                with self.assertRaises(SmokeError): publication.check(bad, 1, identity, publication.RETURN, end)


if __name__ == "__main__": unittest.main()
