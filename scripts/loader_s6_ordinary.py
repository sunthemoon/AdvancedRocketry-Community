"""R203 historical ordinary authority, following a completely settled real-loader flight."""
import copy
import re
import struct
import loader_s6_cleanup as cleanup
from loader_s6_cleanup import forward, flight, legacy, SmokeError

F9 = "LF_S6_F9_RESPAWN"
CASES = (F9,)

def check(image, restart, identity=None, witness=None):
    legacy.require(restart in (0, 1, 2) and image.get("read_status") == "KNOWN" and witness is not None,
                   "UNKNOWN ordinary observation is not zero loss")
    try:
        disk=forward.normalize(image["native"])
        legacy.require(disk["original"] is None, "Original governed source survived F8")
        if not restart:
            legacy.require(disk["source"] is not None and disk["destination"] is None
                           and len(disk["journal"]["transfers"]) == 1, "Ordinary initial authority changed")
            row=disk["journal"]["transfers"][0]
            legacy.require(forward.transfer_digest(row) == witness["ordinary_digest"], "Changed full checked ordinary T")
            # Validate the actual settled source against its first-flight capture, not as new inventory.
            first=copy.deepcopy(image); first["native"].update(source=None, destination=disk["source"])
            first["native"]["journal"]["transfers"]=[]
            cleanup.check(first, 0, None, "LF_S6_F8_PRUNE_ACK", witness["first"])
            historical(row, disk["source"])
            identity=dict(root=disk["ledger"], journal=disk["journal"], loaders=disk["loaders"],
                          source=flight.projection(disk["source"]))
            return dict(identity=identity, raw_total=4500, available_total=None, authorized_debit=500, loss=None)
        legacy.require(identity is not None, "Ordinary restart lacks its initial identity")
        identity=forward.normalize(identity); row=identity["journal"]["transfers"][0]
        legacy.require(disk["ledger"] == identity["root"] and disk["journal"] == identity["journal"]
                       and disk["loaders"] == identity["loaders"] and disk["source"] is None
                       and disk["destination"] is not None, "Ordinary restart changed source, loader or authority")
        end=witness["landed_time"]
        legacy.require(type(end) is int and end >= row["destination_flight"]["state_started_game_time"] + 80,
                       "Ordinary arrival has no complete descent clock")
        expected=copy.deepcopy(identity["source"])
        expected.update(UUID=row["destination_entity_id"], Pos=[96.5, 100., 64.5])
        state=copy.deepcopy(row["destination_flight"]); state.update(state="LANDED", state_started_game_time=end)
        state.pop("plan"); state.pop("active_transfer_id")
        expected["RocketEntityData"].update(snapshot=row["destination_snapshot"], flight_data=state,
            resource_escrow=dict(schema_version=1, resource_epoch=0, markers=[]))
        actual=flight.projection(disk["destination"])
        legacy.require(actual == expected and not disk["destination"].get("Passengers")
                       and forward.survivor_digest(disk["destination"]) == witness["survivor_digest"], "Changed complete ordinary survivor")
        result=dict(identity, survivor=actual)
        if restart == 2: legacy.require(result == identity, "Second ordinary restart changed its settled survivor")
        return dict(identity=result, raw_total=4250, available_total=4250, authorized_debit=750, loss=0)
    except (KeyError, TypeError, IndexError, ValueError, OverflowError) as failure:
        raise SmokeError("Malformed ordinary-stage observation: " + str(failure)) from failure

def historical(row, source):
    data=source["RocketEntityData"]; base=data["flight_data"]; start=row["created_at_game_time"]
    legacy.require(set(row) == {"schema_version", "transfer_id", "phase", "logical_rocket_id", "owner_id", "source_entity_id",
        "destination_entity_id", "source_snapshot", "destination_snapshot", "source_flight", "destination_flight", "required_fuel", "created_at_game_time", "checksum"}
        and row["schema_version"] == 2 and row["phase"] == "COMMITTED" and row["source_entity_id"] == source["UUID"]
        and row["logical_rocket_id"] == data["assembly_transaction_id"] and row["owner_id"] == data["owner_id"]
        and row["source_snapshot"] == data["snapshot"] and row["required_fuel"] == 250
        and start >= base["state_started_game_time"] and row["destination_entity_id"] != source["UUID"], "Changed historical ordinary authority")
    transfer=row["transfer_id"]; actual=row["source_flight"]; plan=actual["plan"]
    expected_plan=dict(schema_version=4, request_id=transfer, source_body=base["current_body"], destination_body="advancedrocketrycommunity:earth",
        source_dimension=base["current_dimension"], destination_dimension=base["current_dimension"], required_fuel=250, created_at_game_time=start,
        destination_target=dict(schema_version=1, type="advancedrocketrycommunity:body_surface", body_id="advancedrocketrycommunity:earth"),
        propulsion_debit=dict(schema=1, primary=250, oxidizer=0, revision=2,
            resources=flight.integrity.fuel_signature(base["fuel"]["propulsion"]["descriptor"], 500, 2),
            catalog=flight.integrity.sha("W211 R203 ordinary historical flight")))
    transit=copy.deepcopy(base); transit.update(state="TRANSIT", state_started_game_time=start+140, plan=expected_plan, active_transfer_id=transfer)
    descent=copy.deepcopy(transit); descent.update(state="DESCENT", state_started_game_time=start+160,
        current_body=expected_plan["destination_body"], current_origin=[96, 100, 64], current_target=expected_plan["destination_target"])
    descent["fuel"].update(amount=250, committed_debits=base["fuel"]["committed_debits"]+[dict(transaction_id=transfer)])
    descent["fuel"]["propulsion"].update(primary=flight.integrity.mono_bank(250), revision=3)
    legacy.require(actual == transit and row["destination_flight"] == descent, "Changed complete historical typed debit, clock or manifest")
    snapshot=copy.deepcopy(data["snapshot"]); target=row["destination_snapshot"]
    snapshot.update(snapshot_id=target["snapshot_id"], content_hash=target["content_hash"], source_origin=[96, 100, 64], created_at_game_time=start)
    legacy.require(snapshot == target and target["snapshot_id"] != data["snapshot"]["snapshot_id"], "Changed historical relocated structure")

def inspect(world):
    first=flight.inspect(world)
    if first["read_status"] != "KNOWN": return first
    try:
        path=world / "entities/r.0.0.mca"
        with path.open("rb") as stream: stream.seek(134*4); header=stream.read(4)
        legacy.require(len(header) == 4, "Truncated ordinary entity header")
        entities=[] if struct.unpack(">I",header)[0] == 0 else flight.record(path,134)["Entities"]
        rockets=[e for e in entities if "RocketEntityData" in e or e.get("id") == legacy.ROCKET]
        legacy.require(len(rockets) <= 1, "Duplicate ordinary endpoint")
        disk=first["native"]; disk.update(original=disk["source"], source=disk["destination"], destination=rockets[0] if rockets else None)
        return first
    except (OSError, ValueError, KeyError, TypeError, SmokeError) as failure:
        return dict(read_status="UNKNOWN", native=None, error=str(failure))

def witness(lines, restart):
    def exact(family,label,expression):
        prefix="ARCE_LOADER_S6_"+family+"_"+label+" cut="+F9+" "
        found=[(i,re.search(re.escape(prefix)+expression+"$",line.rstrip("\r\n"))) for i,line in enumerate(lines) if prefix in line]
        legacy.require(len(found)==1 and found[0][1], "Missing, duplicate or malformed ordinary witness: "+label)
        return found[0]
    if restart:
        _, end=exact("ORDINARY","RESULT",r"landed_time=(\d+) digest=([0-9a-f]{64}) bank=250 debits=2 journal=1")
        return dict(landed_time=int(end.group(1)),survivor_digest=end.group(2))
    a,filled=exact("FLIGHT","FILLED",r"operation=(\S+) source=(\S+) logical=(\S+) owner=(\S+) ledger=(\S+) source_digest=([0-9a-f]{64}) tank=4000 bank=1000 epoch=1")
    b,arm=exact("FLIGHT","ARMED",r"digest=([0-9a-f]{64})")
    c,transfer=exact("FLIGHT","TRANSFER",r"digest=([0-9a-f]{64})")
    d,capture=exact("FLIGHT","CAPTURE",r"nbt=([A-Za-z0-9+/=]+)")
    e,publication=exact("FLIGHT","PUBLICATION_SOURCE",r"phase_time=(\d+) digest=([0-9a-f]{64})")
    f,landed=exact("FLIGHT","LANDED",r"landed_time=(\d+) digest=([0-9a-f]{64})")
    g,ledger=exact("ORDINARY","SETTLED",r"nbt=([A-Za-z0-9+/=]+)")
    h,ordinary=exact("ORDINARY","HISTORICAL",r"digest=([0-9a-f]{64}) schema=2 source_bank=500 destination_bank=250")
    i,paused=exact("ORDINARY","PAUSED",r"rejected=3 sequence=(\d+) source_unchanged=true")
    legacy.require(a<b<c<d<e<f<g<h<i,"Misordered multi-stage ordinary setup")
    first=dict(zip(("operation","source","logical","owner","ledger","source_digest"),filled.groups()))
    first.update(armed_digest=arm.group(1),transfer_digest=transfer.group(1),capture=cleanup.terminal.decode_capture(capture.group(1)),
        publication_source=dict(phase_time=int(publication.group(1)),digest=publication.group(2)),
        landed=dict(landed_time=int(landed.group(1)),survivor_digest=landed.group(2)),staged_ledger=cleanup.terminal.decode_capture(ledger.group(1)))
    return dict(first=first,ordinary_digest=ordinary.group(1))

def check_logs(lines,restart):
    legacy.require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines),"Unexpected ordinary ERROR/FATAL")
    return witness(lines,restart)
