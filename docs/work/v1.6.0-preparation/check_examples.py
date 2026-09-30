#!/usr/bin/env python3
"""Reference projections of ADR-049..052 arithmetic, not production code or runtime behavior.

The functions below restate the frozen integer rules so that the Java slices (C7/C8)
can be checked against the same vectors in examples.json.
"""

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


def survey(types, system, mission_seed, count):
    """ADR-052 section 4, survey-v1: returns [type id, instance seed, yield] per instance."""
    candidates = sorted(
        (t for t in types if not t["systems"] or system in t["systems"]), key=lambda t: t["id"]
    )
    if not candidates:
        raise ValueError("NO_ASTEROID_TYPES")
    stream = SplitMix64(mission_seed ^ SURVEY01)
    instances = []
    for _ in range(count):
        instance_seed = stream.next()
        chooser = SplitMix64(instance_seed ^ ASTTYPE1)
        chosen = candidates[pick([t["weight"] for t in candidates],
                                 chooser.bounded(sum(t["weight"] for t in candidates)))]
        instances.append([chosen["id"], f"{instance_seed:016x}", asteroid_yield(chosen, instance_seed)])
    return instances


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
FIELD = {"power": "power_generation", "battery": "battery_capacity", "data": "data_capacity",
         "cargo": "cargo_stacks"}


def blueprint(components, kind, scan_energy=0):
    """ADR-049 section 4; components are catalog entries in slot order."""
    roles = [c["role"] for c in components]
    if roles.count("chassis") != 1 or roles.count("primary") > 1:
        return "INVALID_LAYOUT"
    modules = [c for c in components if c["role"] not in ("chassis", "primary")]
    if len(modules) > 6:
        return "INVALID_LAYOUT"
    primary = [c for c in components if c["role"] == "primary"]
    if (kind != "data" and not primary) or (primary and primary[0]["kind"] != kind):
        return "INVALID_LAYOUT"
    stats = {"power": 0, "battery": 720, "data": 0, "cargo": 0,
             "rating": primary[0]["primary_rating"] if primary else 0}
    for component in modules:
        role_stat = {"power": "power", "battery": "battery", "data_storage": "data", "cargo": "cargo"}
        stat = role_stat[component["role"]]
        stats[stat] += component[FIELD[stat]]
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


def reconcile(registry, receipt):
    """ADR-051 section 6: registry in {ACTIVE, READY, CLAIMED, CLAIMED_ACK, ABSENT};
    receipt in {NONE, UNSERIALIZED, SERIALIZED}."""
    if registry in ("ACTIVE", "READY"):
        return "SET_CLAIMED_NO_ITEMS" if receipt != "NONE" else "NONE"
    if registry == "CLAIMED":
        return {"SERIALIZED": "ACKNOWLEDGE", "UNSERIALIZED": "WAIT", "NONE": "REMATERIALIZE"}[receipt]
    if registry == "CLAIMED_ACK":
        return "NONE"
    if registry == "ABSENT":
        return "DROP_RECEIPT" if receipt != "NONE" else "NONE"
    raise ValueError(registry)


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
        types = EXAMPLES["asteroid_types"]
        for case in EXAMPLES["survey"]:
            result = survey(types, case["system"], int(case["mission_seed"], 16), case["count"])
            self.assertEqual(result, case["instances"])
            for _, _, entries in result:
                self.assertLessEqual(sum(count for _, count in entries), 4096)
                self.assertLessEqual(len(entries), 17)
        scoped = [t for t in types if t["systems"]]
        self.assertTrue(scoped)
        with self.assertRaises(ValueError):
            survey(scoped, "example:elsewhere", 1, 1)

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

    def test_gas(self):
        for case in EXAMPLES["gas"]:
            self.assertEqual(gas(case["amount_per_1000_ticks"], case["rating"], case["cargo"],
                                 case["config_pct"]), case["expected"])

    def test_blueprints(self):
        catalog = EXAMPLES["components"]
        for case in EXAMPLES["blueprints"]:
            components = [catalog[name] for name in case["components"]]
            self.assertEqual(blueprint(components, case["kind"], case.get("scan_energy", 0)),
                             case["expected"], case["name"])

    def test_scheduler_backlog(self):
        for case in EXAMPLES["scheduler_backlog"]:
            self.assertEqual(drain_passes(case["due"]), case["passes"])
            self.assertEqual(drain_passes(case["due"]) * 20, case["ticks"])

    def test_reconciliation_table(self):
        for case in EXAMPLES["reconciliation"]:
            self.assertEqual(reconcile(case["registry"], case["receipt"]), case["action"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
