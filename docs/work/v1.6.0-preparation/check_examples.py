#!/usr/bin/env python3
"""Reference projections of ADR-049..052 (revision 3) arithmetic, not production code or runtime behavior.

The functions below restate the frozen integer rules so that the Java slices (C7/C8)
can be checked against the same vectors in examples.json.
"""

import hashlib
import itertools
import json
from pathlib import Path
import unittest


EXAMPLES = json.loads(Path(__file__).with_name("examples.json").read_text(encoding="utf-8"))
MASK = (1 << 64) - 1
SURVEY01 = 0x5355525645593031
ASTTYPE1 = 0x4153545459504531
ASTYIELD = 0x4153545949454C44


class SplitMix64:
    """ADR-052 section 3; values are unsigned 64-bit, identical to Java long bit patterns."""

    def __init__(self, seed):
        self.state = seed & MASK

    def next(self):
        self.state = (self.state + 0x9E3779B97F4A7C15) & MASK
        z = self.state
        z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & MASK
        z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & MASK
        return z ^ (z >> 31)

    def bounded(self, n):
        if type(n) is not int or not 1 <= n <= 1 << 31:
            raise ValueError("bound outside 1..2^31")
        return (self.next() >> 1) % n


def ceil_div(a, b):
    return -(-a // b)


def clamp(value, low, high):
    return max(low, min(high, value))


def pick(weights, r):
    cumulative = 0
    for index, weight in enumerate(weights):
        cumulative += weight
        if cumulative > r:
            return index
    raise AssertionError("r outside total weight")


def asteroid_yield(asteroid_type, seed):
    """ADR-052 section 5, asteroid-v1."""
    y = SplitMix64(seed ^ ASTYIELD)
    mv = asteroid_type["mass_variability_pct"]
    dm = y.bounded(mv + 1) - mv // 2
    mass = clamp(asteroid_type["mass"] * (100 + dm) // 100, 1, 4096)
    rv = asteroid_type["richness_variability_pct"]
    dr = y.bounded(rv + 1) - rv // 2
    rich = clamp(asteroid_type["richness_pct"] + dr, 0, 100)
    ore_total = mass * rich // 100
    weights = [ore["weight"] for ore in asteroid_type["ores"]]
    counts = [0] * len(weights)
    for _ in range(ore_total):
        counts[pick(weights, y.bounded(sum(weights)))] += 1
    result = [[ore["item"], count] for ore, count in zip(asteroid_type["ores"], counts) if count > 0]
    if mass - ore_total > 0:
        result.append([asteroid_type["base_item"], mass - ore_total])
    return result


def candidates(types, system, key=lambda t: t["id"]):
    """Full-string order (String.compareTo on toString()); `key` lets a test show the path-first trap."""
    return sorted((t for t in types if not t["systems"] or system in t["systems"]), key=key)


def fingerprint(sorted_candidates):
    lines = "".join(f"{t['id']}\t{t['weight']}\t{t['table_version']}\n" for t in sorted_candidates)
    return hashlib.sha256(lines.encode("utf-8")).hexdigest()[:16]


def survey(types, system, mission_seed, count, key=lambda t: t["id"]):
    """ADR-052 section 4, survey-v1: returns (fingerprint, [type id, instance seed, yield] per instance)."""
    chosen_from = candidates(types, system, key)
    if not chosen_from:
        raise ValueError("NO_ASTEROID_TYPES")
    stream = SplitMix64(mission_seed ^ SURVEY01)
    instances = []
    for _ in range(count):
        instance_seed = stream.next()
        chooser = SplitMix64(instance_seed ^ ASTTYPE1)
        chosen = chosen_from[pick([t["weight"] for t in chosen_from],
                                  chooser.bounded(sum(t["weight"] for t in chosen_from)))]
        instances.append([chosen["id"], f"{instance_seed:016x}", asteroid_yield(chosen, instance_seed)])
    return fingerprint(chosen_from), instances


def truncate(entries, cargo):
    remaining = cargo * 64
    delivered = []
    for item, count in entries:
        take = min(count, remaining)
        if take > 0:
            delivered.append([item, take])
        remaining -= take
    return delivered


def asteroid_duration(time_multiplier_pct, config_pct, rating):
    return clamp(ceil_div(12_000 * time_multiplier_pct * config_pct * 10, 100 * 100 * rating), 200, 72_000)


def gas(amount_per_1000_ticks, rating, cargo, config_pct):
    capacity = cargo * 64
    rate = max(1, amount_per_1000_ticks * rating // 10)
    base = clamp(ceil_div(capacity * 1000, rate), 200, 72_000)
    amount = min(capacity, rate * base // 1000)
    duration = clamp(ceil_div(base * config_pct, 100), 200, 72_000)
    return {"rate": rate, "base": base, "amount": amount, "duration": duration}


CAPS = {"power": 1_000, "battery": 1_000_000, "data": 100_000, "cargo": 27, "rating": 100}
MODULE_STAT = {"power": ("power", "power_generation"), "battery": ("battery", "battery_capacity"),
               "data_storage": ("data", "data_capacity"), "cargo": ("cargo", "cargo_stacks")}


def blueprint(slots, kind, catalog, scan_energy=0):
    """ADR-049 section 4: slot 0 chassis, slot 1 primary (None only for the legacy data label),
    slots 2..7 modules. Returns stats or the first refusal code in the section-4 order."""
    chassis, primary, modules = slots["chassis"], slots["primary"], slots["modules"]
    chassis = catalog.get(chassis) if chassis else None
    primary = catalog.get(primary) if primary else None
    modules = [catalog[name] for name in modules]
    if (chassis is None or chassis["role"] != "chassis" or len(modules) > 6
            or any(m["role"] not in MODULE_STAT for m in modules)):
        return "INVALID_LAYOUT"
    if kind == "data":
        if primary is not None:
            return "INVALID_LAYOUT"
    elif primary is None or primary["role"] != "primary" or primary["kind"] != kind:
        return "INVALID_LAYOUT"
    stats = {"power": 0, "battery": 720, "data": 0, "cargo": 0,
             "rating": primary["primary_rating"] if primary else 0}
    for module in modules:
        stat, field = MODULE_STAT[module["role"]]
        stats[stat] += module[field]
    if any(stats[name] > cap for name, cap in CAPS.items()):
        return "STAT_LIMIT"
    if stats["power"] < 1:
        return "REQUIREMENT_UNMET"
    if kind in ("data", "survey") and stats["data"] < 1:
        return "REQUIREMENT_UNMET"
    if kind == "survey" and stats["battery"] < scan_energy:
        return "REQUIREMENT_UNMET"
    if kind in ("asteroid_miner", "gas_harvester") and stats["cargo"] < 1:
        return "REQUIREMENT_UNMET"
    return stats


def drain_passes(due, per_pass=32):
    return ceil_div(due, per_pass)


REGISTRY_STATES = ("UNAVAILABLE", "ABSENT", "ACTIVE_HERE", "READY_HERE", "ACTIVE_ELSEWHERE",
                   "READY_ELSEWHERE", "CLAIMED_PAID_HERE", "CLAIMED_ACK_PENDING", "CLAIMED_ACK_DURABLE",
                   "CLAIMED_PAID_ELSEWHERE", "CANCELLED", "QUARANTINED")
RECEIPT_STATES = ("NONE", "UNPERSISTED", "PERSISTED")


def reconcile(registry, receipt):
    """ADR-051 revision 3, section 7: total over REGISTRY_STATES x RECEIPT_STATES.
    *_HERE / PAID_HERE are relative to the reconciling terminal; ACK states imply paid here."""
    if registry not in REGISTRY_STATES or receipt not in RECEIPT_STATES:
        raise ValueError((registry, receipt))
    if registry == "UNAVAILABLE":
        return "NONE_REGISTRY_BLOCKED"
    if registry == "CLAIMED_PAID_HERE":
        return {"PERSISTED": "ACKNOWLEDGE", "UNPERSISTED": "WAIT", "NONE": "REMATERIALIZE"}[receipt]
    if receipt == "NONE":
        return "NONE"
    return {
        "ABSENT": "DROP_RECEIPT",
        "ACTIVE_HERE": "SET_CLAIMED_PAID_HERE",
        "READY_HERE": "SET_CLAIMED_PAID_HERE",
        "ACTIVE_ELSEWHERE": "SET_CLAIMED_PAID_HERE_BIND_BACK",
        "READY_ELSEWHERE": "SET_CLAIMED_PAID_HERE_BIND_BACK",
        "CLAIMED_ACK_PENDING": "WAIT",
        "CLAIMED_ACK_DURABLE": "DROP_RECEIPT",
        "CLAIMED_PAID_ELSEWHERE": "KEEP_AUDIT_DOUBLE_PAY",
        "CANCELLED": "KEEP_AUDIT_PAID_THEN_CANCELLED",
        "QUARANTINED": "KEEP_MARK_RECEIPT_SEEN",
    }[registry]


REBIND_EVENTS = ("t1_return", "t2_reconcile", "t2_claim", "owner_cancel_t2")


def simulate_rebind(events):
    """ADR-051 section 9 composition, using only reconcile() above. Premise (crash cut 2): T1 paid and
    holds a persisted receipt, the registry reverted to READY bound to T1, T1 was carried away, and an
    operator rebinds to T2. Returns (payments, instance state)."""
    mission = {"status": "READY", "bound": "T2", "paid": None, "rebound": True}
    receipts = {"T1": "PERSISTED", "T2": "NONE"}
    payments = 1
    instance = "ALLOCATED"

    def view(terminal):
        if mission["status"] in ("ACTIVE", "READY"):
            return mission["status"] + ("_HERE" if mission["bound"] == terminal else "_ELSEWHERE")
        if mission["status"] == "CLAIMED":
            return "CLAIMED_PAID_HERE" if mission["paid"] == terminal else "CLAIMED_PAID_ELSEWHERE"
        return mission["status"]

    def run_reconcile(terminal):
        nonlocal payments, instance
        action = reconcile(view(terminal), receipts[terminal])
        if action.startswith("SET_CLAIMED_PAID_HERE"):
            mission.update(status="CLAIMED", paid=terminal)
            instance = "DEPLETED"
            if action.endswith("BIND_BACK"):
                mission["bound"] = terminal
        elif action == "REMATERIALIZE":
            payments += 1
            receipts[terminal] = "UNPERSISTED"

    for event in events:
        if event == "t1_return":
            run_reconcile("T1")
        elif event == "t2_reconcile":
            run_reconcile("T2")
        elif event == "t2_claim":
            run_reconcile("T2")
            if mission["status"] == "READY" and mission["bound"] == "T2":
                mission.update(status="CLAIMED", paid="T2")
                receipts["T2"] = "UNPERSISTED"
                payments += 1
                instance = "DEPLETED"
        elif event == "owner_cancel_t2":
            run_reconcile("T2")
            if mission["status"] in ("ACTIVE", "READY") and mission["bound"] == "T2":
                mission["status"] = "CANCELLED"
                instance = "QUARANTINED" if mission["rebound"] else "AVAILABLE"
        else:
            raise ValueError(event)
    return payments, instance


class ExampleTests(unittest.TestCase):
    def test_splitmix64_published_seed_zero(self):
        stream = SplitMix64(0)
        self.assertEqual([f"{stream.next():016x}" for _ in range(3)], EXAMPLES["splitmix64_seed0"])

    def test_splitmix64_vectors(self):
        for case in EXAMPLES["splitmix64"]:
            stream = SplitMix64(int(case["seed"], 16))
            self.assertEqual([f"{stream.next():016x}" for _ in case["outputs"]], case["outputs"])

    def test_bounded_vectors(self):
        for case in EXAMPLES["bounded"]:
            stream = SplitMix64(int(case["seed"], 16))
            self.assertEqual([stream.bounded(case["n"]) for _ in case["values"]], case["values"])
        with self.assertRaises(ValueError):
            SplitMix64(0).bounded(0)

    def test_survey_and_yield_vectors(self):
        for case in EXAMPLES["survey"]:
            types = EXAMPLES[case["types"]]
            found, result = survey(types, case["system"], int(case["mission_seed"], 16), case["count"])
            self.assertEqual(found, case["candidate_fingerprint"])
            self.assertEqual(result, case["instances"])
            for _, _, entries in result:
                self.assertLessEqual(sum(count for _, count in entries), 4096)
                self.assertLessEqual(len(entries), 17)
        scoped = [t for t in EXAMPLES["asteroid_types"] if t["systems"]]
        self.assertTrue(scoped)
        with self.assertRaises(ValueError):
            survey(scoped, "example:elsewhere", 1, 1)

    def test_mixed_namespace_order_differs_from_path_first_order(self):
        case = next(c for c in EXAMPLES["survey"] if c["types"] == "mixed_namespace_types")
        types = EXAMPLES["mixed_namespace_types"]
        path_first = survey(types, case["system"], int(case["mission_seed"], 16), case["count"],
                            key=lambda t: (t["id"].split(":", 1)[1], t["id"].split(":", 1)[0]))
        self.assertNotEqual(path_first[1], case["instances"])
        self.assertNotEqual(path_first[0], case["candidate_fingerprint"])

    def test_yield_is_reproducible_and_seed_sensitive(self):
        small = EXAMPLES["asteroid_types"][0]
        self.assertEqual(asteroid_yield(small, 42), asteroid_yield(small, 42))
        self.assertNotEqual(asteroid_yield(small, 42), asteroid_yield(small, 43))

    def test_truncation(self):
        for case in EXAMPLES["truncation"]:
            self.assertEqual(truncate(case["entries"], case["cargo"]), case["delivered"])

    def test_asteroid_duration(self):
        for case in EXAMPLES["asteroid_duration"]:
            self.assertEqual(asteroid_duration(case["time_multiplier_pct"], case["config_pct"],
                                               case["rating"]), case["duration"])
        self.assertGreater(12_000 * 1000 * 1000 * 10, 2**31 - 1)  # 32-bit int would overflow (ADR-052 L3)

    def test_gas(self):
        for case in EXAMPLES["gas"]:
            self.assertEqual(gas(case["amount_per_1000_ticks"], case["rating"], case["cargo"],
                                 case["config_pct"]), case["expected"])

    def test_blueprints(self):
        catalog = EXAMPLES["components"]
        for case in EXAMPLES["blueprints"]:
            self.assertEqual(blueprint(case["slots"], case["kind"], catalog, case.get("scan_energy", 0)),
                             case["expected"], case["name"])

    def test_scheduler_backlog(self):
        for case in EXAMPLES["scheduler_backlog"]:
            self.assertEqual(drain_passes(case["due"]), case["passes"])
            self.assertEqual(drain_passes(case["due"]) * 20, case["ticks"])

    def test_rebind_orderings(self):
        cases = EXAMPLES["rebind_orderings"]
        expected_orders = {tuple(order) for n in range(1, len(REBIND_EVENTS) + 1)
                           for order in itertools.permutations(REBIND_EVENTS, n)}
        self.assertEqual({tuple(case["events"]) for case in cases}, expected_orders)
        for case in cases:
            payments, instance = simulate_rebind(case["events"])
            self.assertEqual([payments, instance], [case["payments"], case["instance"]], case["events"])
            self.assertNotEqual(instance, "AVAILABLE")

    def test_reconciliation_table_is_total(self):
        rows = {(case["registry"], case["receipt"]): case["action"] for case in EXAMPLES["reconciliation"]}
        self.assertEqual(set(rows), set(itertools.product(REGISTRY_STATES, RECEIPT_STATES)))
        for (registry, receipt), action in rows.items():
            self.assertEqual(reconcile(registry, receipt), action, (registry, receipt))


if __name__ == "__main__":
    unittest.main(verbosity=2)
