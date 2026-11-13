"""Explicit saved accounting readers for native oracles; never migration or write authority."""
from __future__ import annotations

import hashlib
import copy
import re
import struct
from uuid import UUID

import loader_cut_nbt as nbt
from run_dedicated_server_smoke import SmokeError

EPOCHS = {2: "v1.8.0-resource-escrow", 3: "v1.8.0-resource-escrow-fluid-accounting"}
FLUID_KINDS = {"FLUID_PROPULSION_FILL", "FLUID_CARGO_LOAD", "FLUID_CARGO_UNLOAD"}


def require(ok, message):
    if not ok:
        raise ValueError(message)


def identity(value):
    if isinstance(value, str):
        return str(UUID(value))
    require(isinstance(value, list) and len(value) == 4
            and all(type(v) is int and -(2**31) <= v < 2**31 for v in value), "Invalid account UUID")
    return str(UUID(bytes=struct.pack(">4i", *value)))


def digest_bytes(value):
    require(isinstance(value, list) and len(value) == 32
            and all(type(v) is int and 0 <= v <= 255 for v in value), "Invalid account digest bytes")
    return bytes(value)


def digest_hex(value):
    if isinstance(value, list):
        return digest_bytes(value).hex()
    require(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value), "Invalid account digest")
    return value


def canonical(tag):
    """NC1: sorted compound names, with native scalar/array types preserved."""
    if tag.type == nbt.COMPOUND:
        return nbt.Tag(nbt.COMPOUND, {k: canonical(v) for k, v in sorted(tag.value.items())})
    if tag.type == nbt.LIST:
        return nbt.Tag(nbt.LIST, (tag.value[0], [canonical(v) for v in tag.value[1]]))
    return tag


def digest(tag):
    return hashlib.sha256(nbt.write(b"", canonical(tag))).hexdigest()


def bounds(tag):
    """R443 counts array tags once, exactly as BoundedNbt (root depth is one)."""
    pending = [(tag, 1)]; nodes = 0
    while pending:
        child, depth = pending.pop(); nodes += 1
        require(depth <= 5 and nodes <= 65, "Account row exceeds depth/node bound")
        if child.type == nbt.COMPOUND:
            pending.extend((v, depth + 1) for v in child.value.values())
        elif child.type == nbt.LIST:
            require(child.value[0] == (nbt.COMPOUND if child.value[1] else 0), "Noncanonical account list")
            pending.extend((v, depth + 1) for v in child.value[1])
    require(len(nbt.write(b"", tag)) <= 2048, "Account row exceeds byte bound")


def row_tag(row):
    """Reconstruct only the closed codec's types, including for normalized JSON observations."""
    def fields(value, required, optional=()):
        require(isinstance(value, dict) and set(required) <= value.keys()
                and value.keys() <= set(required) | set(optional), "Invalid account fields")

    def scalar(value, kind):
        if kind == nbt.STRING:
            require(isinstance(value, str) and value.isascii(), "Invalid account string")
            return nbt.Tag(kind, value.encode("ascii"))
        if kind == nbt.BYTES:
            return nbt.Tag(kind, digest_bytes(value))
        if kind == nbt.INTS:
            return nbt.uuid_tag(UUID(identity(value)))
        require(type(value) is int and -(2**63) <= value < 2**63, "Invalid account integer")
        require(kind != nbt.BYTE or -128 <= value <= 127, "Invalid account byte")
        require(kind != nbt.INT or -(2**31) <= value < 2**31, "Invalid account int")
        return nbt.Tag(kind, value)

    def entries(values, frozen=False):
        require(isinstance(values, list) and 1 <= len(values) <= 5, "Invalid account key count")
        result = []
        for entry in values:
            required = ("i", "d", "m", "o") if frozen else ("i", "d", "s", "t", "x", "y")
            fields(entry, required, ("s", "t", "a") if frozen else ())
            result.append(nbt.Tag(nbt.COMPOUND, {k: scalar(v, nbt.STRING if k == "i" else nbt.BYTES if k == "d"
                else nbt.BYTE if k == "m" else nbt.INTS if k == "o" else nbt.LONG) for k, v in entry.items()}))
        return nbt.Tag(nbt.LIST, (nbt.COMPOUND, result))

    fields(row, ("v", "op", "b"), ("f",)); fields(row["b"], ("s", "e"))
    result = {"v": scalar(row["v"], nbt.INT), "op": scalar(row["op"], nbt.INTS),
              "b": nbt.Tag(nbt.COMPOUND, {"s": scalar(row["b"]["s"], nbt.BYTE), "e": entries(row["b"]["e"])})}
    if "f" in row:
        f = row["f"]; fields(f, ("k", "g", "e"), ("m",))
        frozen = {"k": scalar(f["k"], nbt.BYTE), "g": scalar(f["g"], nbt.INTS), "e": entries(f["e"], True)}
        if "m" in f:
            require(isinstance(f["m"], list) and 4 <= len(f["m"]) <= 56 and len(f["m"]) % 4 == 0,
                    "Account row exceeds member bound")
            for index in range(0, len(f["m"]), 4): identity(f["m"][index:index + 4])
            frozen["m"] = nbt.Tag(nbt.INTS, f["m"])
        result["f"] = nbt.Tag(nbt.COMPOUND, frozen)
    return nbt.Tag(nbt.COMPOUND, result)


def key(entry):
    require(isinstance(entry["i"], str) and len(entry["i"]) <= 128
            and re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", entry["i"]), "Invalid account fluid ID")
    return entry["i"], digest_bytes(entry["d"]).hex()


def checked(value):
    require(-(2**63) <= value < 2**63, "Account arithmetic overflow")
    return value


def read_row(row, typed=None):
    tag = row_tag(row); bounds(typed if typed is not None else tag)
    require(typed is None or typed == tag, "Wrong account NBT type")
    require(row["v"] == 1, "Unsupported account schema")
    scope = row["b"]["s"]; require(scope in (0, 1), "Invalid account scope")
    base = row["b"]["e"]; require(len(base) <= (2 if scope == 0 else 1), "Account baseline cardinality")
    def ordered(entries):
        keys = [key(e) for e in entries]
        require(keys == sorted(set(keys)), "Duplicate/unsorted account keys")
        return keys
    base_keys = ordered(base); moved = []
    for e in base:
        require(e["s"] >= 0 and e["t"] >= 0 and (e["s"] or e["t"]), "Invalid baseline amount")
        require(checked(e["x"] + e["y"]) == 0, "Invalid account plan")
        if e["x"] or e["y"]:
            require(-1000 <= e["x"] <= -1 and e["s"] >= -e["x"], "Invalid moved amount")
            moved.append(e)
        require(checked(e["s"] + e["x"]) >= 0 and checked(e["t"] + e["y"]) >= 0, "Invalid plan result")
    require(len(moved) == 1, "Account must move exactly one key")
    if "f" not in row:
        return row
    f = row["f"]; op = identity(row["op"]); owner = identity(f["g"])
    members = [identity(f["m"][i:i + 4]) for i in range(0, len(f.get("m", [])), 4)]
    require((bool(members) and members[0] == op and len(set(members)) == len(members)) if owner == op
            else not members, "Invalid account members")
    require(f["k"] in (1, 2, 3), "Invalid frozen knowledge")
    frozen_keys = ordered(f["e"])
    require(len(frozen_keys) <= (5 if scope == 0 else 3) and set(base_keys) <= set(frozen_keys), "Invalid frozen key union")
    masks = set()
    for e in f["e"]:
        mask = e["m"]; require(0 <= mask <= 7, "Invalid account mask"); masks.add(mask & 3)
        for bit, name in enumerate(("s", "t", "a")):
            require((name in e) == bool(mask & (1 << bit)), "Account mask/field mismatch")
        require(all(e[name] >= 0 for name in ("s", "t") if name in e), "Negative observed amount")
        entry_owner = identity(e["o"])
        require(owner != op or entry_owner in members, "Invalid adjustment owner")
        require(("a" in e) == (f["k"] == 1 and entry_owner == op), "Invalid adjustment knowledge/owner")
        require(key(e) in base_keys or e.get("s", 0) or e.get("t", 0), "Unoccupied extra key")
    require(len(masks) == 1 and (f["k"] != 1 or masks == {3}), "Incomplete KNOWN observations")
    single = members == [op]
    require(not (single and f["k"] == 3 and masks == {3}), "Single UNKNOWN_ENDPOINT has no unknown endpoint")
    if single and f["k"] == 1:
        for e in f["e"]:
            before = next((b for b in base if key(b) == key(e)), {"s": 0, "t": 0})
            residual = checked(checked(e["s"] - before["s"]) + checked(e["t"] - before["t"]))
            require(checked(residual + e["a"]) == 0, "Incorrect signed account balance")
    return row


def rows_by_id(rows, typed=None):
    require(isinstance(rows, list) and len(rows) <= 128, "Invalid account row count")
    if typed is not None:
        require(typed.type == nbt.LIST and typed.value[0] == (nbt.COMPOUND if rows else 0), "Invalid account list type")
    result = {}
    for index, row in enumerate(rows):
        read_row(row, typed.value[1][index] if typed else None)
        op = identity(row["op"]); require(op not in result, "Duplicate account row"); result[op] = row
    return result


def receipt_account(receipt, accounts):
    version = receipt.get("schema_version")
    if version == 3:
        require(receipt.get("accounting_status") == "UNKNOWN" and "accounting_digest" not in receipt, "Invalid legacy receipt accounting")
        return None
    require(version == 4 and accounts is not None, "Unsupported receipt schema or missing accounts")
    row = accounts.get(identity(receipt["op_id"])); require(row is not None and "f" in row, "Missing receipt account")
    read_row(row)
    require(digest_hex(receipt["accounting_digest"]) == digest(row_tag(row)), "Receipt account digest mismatch")
    status = "KNOWN" if row["f"]["k"] == 1 else "UNKNOWN"
    require(receipt.get("accounting_status") == status, "Receipt account status mismatch")
    require(not receipt.get("missing") or status == "UNKNOWN", "Missing endpoint cannot be KNOWN")
    return row


def validate_root(root, typed_rows=None, fresh_fluid=False):
    """Validate accounts without treating this diagnostic reader as the production root codec."""
    version = root.get("schema_version", 2)  # historical oracle-only JSON projections omit framing
    require(version in (2, 3), "Unsupported escrow root")
    if version == 2:
        require("fluid_accounts" not in root, "Accounting rows in root 2")
        for op in root.get("records", []):
            require(op.get("schema_version", 2) == 2 and "accounting_baseline" not in op
                    and "account_commitment" not in op.get("settlement", {}), "Accounting operation in root 2")
        for receipt in root.get("tombstones", []):
            receipt_account(dict(receipt, schema_version=receipt.get("schema_version", 3)), None)
        return {}
    require(root.get("format_epoch") == EPOCHS[3] and "fluid_accounts" in root, "Wrong root-3 framing")
    accounts = rows_by_id(root["fluid_accounts"], typed_rows); expected = set()
    operations = root.get("records", []); receipts = root.get("tombstones", [])
    for op in operations:
        uid = identity(op["op_id"]); row = accounts.get(uid); schema = op.get("schema_version")
        require(schema in (2, 3) and (schema == 3) == ("accounting_baseline" in op) == (row is not None), "Operation account reference mismatch")
        if fresh_fluid and op["kind"] in FLUID_KINDS: require(row is not None, "Fresh fluid operation took legacy branch")
        if row is not None:
            expected.add(uid)
            require(op["kind"] in FLUID_KINDS and row["b"]["s"] == int(op["kind"] != "FLUID_PROPULSION_FILL"), "Account scope mismatch")
            require(digest_hex(op["accounting_baseline"]) == digest(row_tag(row).value["b"]), "Baseline digest mismatch")
            fluid = op["payload"] if row["b"]["s"] == 0 else op["payload"]["fluid"]
            moved = next(e for e in row["b"]["e"] if e["x"])
            require(moved["i"] == fluid["FluidName"] and moved["y"] == fluid["Amount"], "Account payload mismatch")
            # The amount-free key's native Tag digest needs the original NBT types; checked by the typed reader below.
            require(bool(op.get("settlement")) == ("f" in row), "Operation account freeze mismatch")
    for receipt in receipts:
        row = receipt_account(receipt, accounts)
        uid = identity(receipt["op_id"])
        live = next((op for op in operations if identity(op["op_id"]) == uid), None)
        require(live is None or (live["schema_version"] == 3) == (row is not None), "O3 receipt version mismatch")
        if row is not None: expected.add(uid)
    require(expected == accounts.keys(), "Orphan account row")
    groups = {}
    for uid, row in accounts.items():
        if "f" in row: groups.setdefault(identity(row["f"]["g"]), {})[uid] = row
    for owner, group in groups.items():
        require(owner in group, "Missing account group owner")
        f = group[owner]["f"]; ids = [identity(f["m"][i:i + 4]) for i in range(0, len(f["m"]), 4)]
        require(set(ids) == group.keys(), "Incomplete account group")
        owners = {}; decisions = set()
        for uid in ids:
            row = group[uid]; require(row["f"]["k"] == f["k"], "Split account knowledge")
            for e in row["f"]["e"]: owners.setdefault(key(e), uid)
            for op in operations:
                if identity(op["op_id"]) == uid: decisions.add(op["settlement"]["digest"])
            for receipt in receipts:
                if identity(receipt["op_id"]) == uid: decisions.add(receipt["decision_digest"])
        require(len(decisions) == 1, "Split account decision")
        for row in group.values():
            for e in row["f"]["e"]: require(identity(e["o"]) == owners[key(e)], "Split/later adjustment owner")
    for op in operations:
        intent = op.get("settlement", {})
        if not intent: continue
        members = [identity(m["op_id"]) for m in intent["members"]]
        rows = [accounts[uid] for uid in members if uid in accounts]
        require(intent["schema_version"] == (3 if rows else 2), "Wrong intent account version")
        if rows:
            pairs = [nbt.Tag(nbt.COMPOUND, {"op": nbt.uuid_tag(UUID(identity(r["op"]))),
                "digest": nbt.Tag(nbt.BYTES, bytes.fromhex(digest(row_tag(r))))}) for r in rows]
            commitment = digest(nbt.Tag(nbt.COMPOUND, {"rows": nbt.Tag(nbt.LIST, (nbt.COMPOUND, pairs))}))
            require(digest_hex(intent["account_commitment"]) == commitment, "Intent account commitment mismatch")
            require([identity(r["op"]) for r in rows] == [identity(rows[0]["f"]["m"][i:i + 4])
                for i in range(0, len(rows[0]["f"]["m"]), 4)], "Intent account member mismatch")
        else: require("account_commitment" not in intent, "Legacy intent has account commitment")
    return accounts


def check(root, fresh_fluid=True):
    """Oracle boundary: malformed numeric evidence is a harness failure, not zero loss."""
    try:
        return validate_root(root, fresh_fluid=fresh_fluid) if root is not None else {}
    except (ValueError, KeyError, TypeError, IndexError, StopIteration) as failure:
        raise SmokeError(f"Invalid escrow accounting: {failure}") from failure


def receipt_fields(root, op):
    """Expected versioned receipt fields, derived from the retained O1 account, not the observed receipt."""
    if root.get("schema_version", 2) == 2: return {}
    rows = check(root); row = rows.get(identity(op["op_id"]))
    if row is None: return {}
    return dict(schema_version=4, accounting_digest=digest(row_tag(row)),
                accounting_status="KNOWN" if row["f"]["k"] == 1 else "UNKNOWN")


def resource_projection(root):
    """After checking real authority, reuse legacy physical checks on a private, non-authoritative projection."""
    check(root)
    result = copy.deepcopy(root)
    if result.get("schema_version") == 3:
        result.update(schema_version=2, format_epoch=EPOCHS[2]); result.pop("fluid_accounts")
        for op in result["records"]:
            op["schema_version"] = 2; op.pop("accounting_baseline", None)
            if op.get("settlement"):
                op["settlement"]["schema_version"] = 2; op["settlement"].pop("account_commitment", None)
    return result


def expect_transfer(root, op, source_before=5000, destination_before=0, source=None, destination=None):
    """Fixture-specific amounts are independent expectations, not quantities inferred from account hashes."""
    if root.get("schema_version") != 3: return
    rows = check(root); row = rows[identity(op["op_id"])]
    moved = next(e for e in row["b"]["e"] if e["x"])
    if moved["s"] != source_before or moved["t"] != destination_before:
        raise SmokeError("Transfer account baseline differs from fixture")
    if "f" in row and source is not None and destination is not None:
        entry = next(e for e in row["f"]["e"] if key(e) == key(moved))
        if entry.get("s") != source or entry.get("t") != destination:
            raise SmokeError("Frozen transfer amounts differ from chosen native fixture")


def expect_frozen(row, source, destination):
    """One-key fixture's chosen cargo amounts; None is an unknown contribution, never zero."""
    if "f" not in row: return
    moved = next(e for e in row["b"]["e"] if e["x"])
    entry = next(e for e in row["f"]["e"] if key(e) == key(moved))
    if entry.get("s") != source or entry.get("t") != destination:
        raise SmokeError("Frozen transfer differs from chosen cargo endpoints")
    if any(e.get("s", 0) or e.get("t", 0) for e in row["f"]["e"] if key(e) != key(moved)):
        raise SmokeError("Chosen one-fluid cargo fixture acquired another fluid")


def check_progress(before, after):
    """A restart may retire a planned row, but cannot replace a baseline or an O1-frozen account."""
    if before.get("schema_version", 2) != 3:
        return
    old = check(before); new = check(after)
    if after.get("schema_version") != 3: raise SmokeError("Restart downgraded the accounting root")
    for uid, row in old.items():
        if "f" in row and new.get(uid) != row:
            raise SmokeError("Restart changed or lost the frozen account")
        if uid in new and row["b"] != new[uid]["b"]:
            raise SmokeError("Restart replaced the transfer baseline")
