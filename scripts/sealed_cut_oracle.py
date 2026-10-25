"""Bounded resource/election oracle shared by the sealed process-stop harness.

Input consists of independently decoded native images. U is a retained snapshot,
not an extra inventory. This module does not infer durability from fixture logs.
"""
from __future__ import annotations

from collections import Counter


PHASES = frozenset(("SNAPSHOT_VALIDATED", "LOCKED", "EXTRACTING", "EXTRACTED", "SPAWNED", "RESTORING",
                    "RESTORED", "COMMITTED", "ROLLING_BACK", "ROLLED_BACK", "FAILED"))


def inventory(root: object) -> dict[str, int]:
    """Count labelled stacks once, never descend into an item's arbitrary metadata."""
    result = Counter()
    pending = [(root, 1)]
    visited = 0
    while pending:
        value, depth = pending.pop()
        visited += 1
        if visited > 262_144 or depth > 64:
            raise ValueError("Inventory image exceeds the native slot walk bounds")
        if isinstance(value, dict):
            item = isinstance(value.get("id"), str) and "Count" in value
            fluid = isinstance(value.get("FluidName"), str) and "Amount" in value
            if item or fluid:
                tag = value.get("tag" if item else "Tag", {})
                if isinstance(tag, dict) and "W152" in tag:
                    label, amount = tag["W152"], value["Count" if item else "Amount"]
                    if (not isinstance(label, str) or not 1 <= len(label) <= 64 or type(amount) is not int
                            or not 1 <= amount <= (64 if item else 2**31 - 1)):
                        raise ValueError("Malformed labelled resource stack")
                    result[label] += amount
                continue
            if visited + len(pending) + len(value) > 262_144:
                raise ValueError("Inventory image exceeds the native slot walk bounds")
            pending.extend((child, depth + 1) for child in value.values())
        elif isinstance(value, list):
            if visited + len(pending) + len(value) > 262_144:
                raise ValueError("Inventory image exceeds the native slot walk bounds")
            pending.extend((child, depth + 1) for child in value)
    return dict(sorted(result.items()))


def _sum(*inventories: dict[str, int]) -> dict[str, int]:
    result = Counter()
    for contents in inventories:
        result.update(contents)
    return dict(sorted(result.items()))


def elect(phase: str | None, entities: list, sealed: list, native: list, recipients: list,
          *, retained: object = None) -> dict:
    """Apply existing D/C/U election without treating a physical shadow as a second authority."""
    if phase is not None and phase not in PHASES:
        raise ValueError("Unrecognized durable U phase")
    d, c, n, consumers = (inventory(value) for value in (entities, sealed, native, recipients))
    if phase is not None and native:
        raise ValueError("Native terrain is exposed while durable U remains")
    if phase is None and entities and (sealed or native):
        raise ValueError("Competing D and terrain without a retained election")
    if phase is not None and phase not in ("RESTORED", "COMMITTED") and not entities:
        if retained is None:
            raise ValueError("Missing earlier-U snapshot authority")
        authority, effective = "RETAINED_SNAPSHOT", _sum(inventory(retained), consumers)
    elif entities and (phase is None or phase not in ("RESTORED", "COMMITTED")):
        authority, effective = "ENTITY", _sum(d, consumers)
    else:
        authority, effective = "BLOCKS", _sum(c, n, consumers)
    return dict(authority=authority, effective=effective, entity_images=d, sealed_images=c, native_images=n,
                recipients=consumers, sealed_shadow=c if authority == "ENTITY" else {},
                entity_shadow=d if phase in ("RESTORED", "COMMITTED") else {})


def require_inventory(observation: dict, expected: dict[str, int]) -> None:
    if observation["effective"] != dict(sorted(expected.items())):
        raise ValueError(f"Native conservation differs: {observation['effective']} != {expected}")
