"""Decode only unreleased schema-3 receipt wire names for native verification evidence."""
from __future__ import annotations

FIELDS = (
    "schema_version", "op_id", "retired", "guards", "before_choice", "payload_amount", "payload_hash",
    "accounting_status", "decision_digest", "actor", "actor_name", "choice", "confirmed_tick", "released",
    "release_generation", "anchor_op", "rocket_id", "transfer_id", "journal_generation", "journal_digest",
    "endpoint", "missing", "bound", "quantity", "signature_kind", "invariant_digest", "state", "kind", "level",
    "identity", "logical_rocket", "type", "x", "y", "z", "unit", "before_epoch", "after_epoch", "before_signature",
    "after_signature", "ledger_id", "epoch", "signature", "markers", "seq", "role", "epoch_after",
)
DIGESTS = {"payload_hash", "decision_digest", "digest", "signature", "before_signature", "after_signature",
           "journal_digest", "invariant_digest"}


def decode_receipt(wire: dict) -> dict:
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
    if receipt.get("schema_version") != 3 or receipt.get("accounting_status") != "UNKNOWN":
        raise ValueError("Unsupported receipt schema/accounting")
    observations = receipt.get("before_choice")
    if not isinstance(observations, list) or not 1 <= len(observations) <= 2:
        raise ValueError("Invalid receipt audit observation count")
    receipt["missing"] = [item["endpoint"] for item in observations if item["missing"]]
    receipt["survivors"] = [dict(endpoint=item["endpoint"], epoch=item["state"]["epoch"], signature=item["state"]["signature"],
                                signature_kind=item["signature_kind"], invariant_digest=item["invariant_digest"], quantity=item["quantity"],
                                bound=bool(item["bound"] or item["signature_kind"] == "FUEL_VECTOR_V1"))
                            for item in observations if not item["missing"]]
    return receipt
