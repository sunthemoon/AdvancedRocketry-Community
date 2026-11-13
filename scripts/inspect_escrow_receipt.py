"""Explicit schema-3 and schema-4 receipt readers for native verification evidence."""
from __future__ import annotations

from escrow_accounting import receipt_account
import loader_cut_nbt as nbt

FIELDS = (
    "schema_version", "op_id", "retired", "guards", "before_choice", "payload_amount", "payload_hash",
    "accounting_status", "decision_digest", "actor", "actor_name", "choice", "confirmed_tick", "released",
    "release_generation", "anchor_op", "rocket_id", "transfer_id", "journal_generation", "journal_digest",
    "endpoint", "missing", "bound", "quantity", "signature_kind", "invariant_digest", "state", "kind", "level",
    "identity", "logical_rocket", "type", "x", "y", "z", "unit", "before_epoch", "after_epoch", "before_signature",
    "after_signature", "ledger_id", "epoch", "signature", "markers", "seq", "role", "epoch_after", "accounting_digest",
)
DIGESTS = {"payload_hash", "decision_digest", "digest", "signature", "before_signature", "after_signature",
           "journal_digest", "invariant_digest", "accounting_digest"}


def decode_receipt(wire: dict, accounts=None) -> dict:
    if isinstance(wire, nbt.Tag):
        def check_types(tag):
            if tag.type == nbt.COMPOUND:
                for key, value in tag.value.items():
                    if key in {"f" + _base36(FIELDS.index(name)) for name in DIGESTS if name in FIELDS}:
                        if value.type != nbt.BYTES or len(value.value) != 32:
                            raise ValueError("Invalid receipt native digest type")
                    check_types(value)
            elif tag.type == nbt.LIST:
                for child in tag.value[1]: check_types(child)
        if wire.type != nbt.COMPOUND or nbt.compound(wire, "f0").type != nbt.INT:
            raise ValueError("Invalid receipt native schema type")
        check_types(wire); wire = nbt.plain(wire)
    def decode(value):
        if isinstance(value, list):
            return [decode(item) for item in value]
        if not isinstance(value, dict):
            return value
        result = {}
        for key, raw in value.items():
            if not isinstance(key, str) or not key.startswith("f"):
                raise ValueError("Unknown schema-3 receipt wire field")
            index = int(key[1:], 36)
            if not 0 <= index < len(FIELDS):
                raise ValueError("Unknown schema-3 receipt wire index")
            digits = "0123456789abcdefghijklmnopqrstuvwxyz"
            canonical = "f" + (digits[index] if index < 36 else "1" + digits[index - 36])
            if key != canonical:
                raise ValueError("Noncanonical schema-3 receipt wire index")
            name = FIELDS[index]
            if name in DIGESTS:
                if not isinstance(raw, list) or len(raw) != 32 or any(type(byte) is not int or not 0 <= byte <= 255 for byte in raw):
                    raise ValueError("Invalid schema-3 receipt digest bytes")
                result[name] = bytes(raw).hex()
            else:
                result[name] = decode(raw)
        return result
    receipt = decode(wire)
    observations = receipt.get("before_choice")
    if not isinstance(observations, list) or not 1 <= len(observations) <= 2:
        raise ValueError("Invalid receipt audit observation count")
    receipt["missing"] = [item["endpoint"] for item in observations if item["missing"]]
    receipt["survivors"] = [dict(endpoint=item["endpoint"], epoch=item["state"]["epoch"], signature=item["state"]["signature"],
                                signature_kind=item["signature_kind"], invariant_digest=item["invariant_digest"], quantity=item["quantity"],
                                bound=bool(item["bound"] or item["signature_kind"] == "FUEL_VECTOR_V1"))
                            for item in observations if not item["missing"]]
    if receipt.get("schema_version") == 3:
        receipt_account(receipt, None)
    elif receipt.get("schema_version") == 4:
        receipt_account(receipt, accounts)
    else:
        raise ValueError("Unsupported receipt schema")
    return receipt


def _base36(index):
    digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    return digits[index] if index < 36 else "1" + digits[index - 36]
