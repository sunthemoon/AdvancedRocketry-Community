#!/usr/bin/env python3
"""Reference projections of ADR-054..059 (revision 1) rules; not production code or runtime behaviour.

The functions restate the proposed integer rules and protocols so that the Java slices (C11/C12)
can be checked against the same vectors in examples.json. The transit-ledger and payment-counter
models enumerate crash interleavings; they check the stated protocol, not the implementation.
"""

import json
from pathlib import Path
import unittest


EXAMPLES = json.loads(Path(__file__).with_name("examples.json").read_text(encoding="utf-8"))
MASK = (1 << 64) - 1
GAMMA = 0x9E3779B97F4A7C15
LASERDRL = int.from_bytes(b"LASERDRL", "big")


class SplitMix64:
    """ADR-052 section 3; values are unsigned 64-bit, identical to Java long bit patterns."""

    def __init__(self, seed):
        self.state = seed & MASK

    def next(self):
        self.state = (self.state + GAMMA) & MASK
        return mix64(self.state)


def mix64(z):
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & MASK
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & MASK
    return z ^ (z >> 31)


def pick(weights, r):
    cumulative = 0
    for index, weight in enumerate(weights):
        cumulative += weight
        if cumulative > r:
            return index
    raise AssertionError("r outside total weight")


# --- ADR-055 laser-v1 -------------------------------------------------------------------------

def laser_output(seed, n):
    """The n-th output of SplitMix64(seed ^ LASERDRL), without iterating."""
    return mix64(((seed ^ LASERDRL) + (n + 1) * GAMMA) & MASK)


def laser_draw(table, seed, n):
    weights = [entry["weight"] for entry in table["entries"]]
    r = (laser_output(seed, n) >> 1) % sum(weights)
    entry = table["entries"][pick(weights, r)]
    return [entry["item"], entry["count"]]


def laser_cost(percent):
    if not 10 <= percent <= 1000:
        raise ValueError("percent outside 10..1000")
    return 10_000 * percent // 100


def footprint_inside_chunk(mx, mz):
    """Java `x & 15` equals Python `x & 15` for negative ints (two's complement)."""
    return 1 <= (mx & 15) <= 14 and 1 <= (mz & 15) <= 14


def shaft_cells(mx, mz):
    return [[mx - 1 + i % 3, mz - 1 + i // 3] for i in range(9)]


def shaft_floor(my, max_depth, min_build_height):
    return max(min_build_height + 1, my - max_depth)


# --- ADR-055 physical-mode payment counters ---------------------------------------------------

def counter_model(events, cost, energy):
    """Runs controller/marker events with independent saves; returns the settled durable result.

    events: OP, SAVE_C, SAVE_M, CRASH. After the sequence a final contact settles debt and credit
    with unlimited energy added, the way a powered controller eventually would.
    """
    live_c = {"energy": energy, "paid": 0}
    live_m = {"done": 0}
    dur_c, dur_m = dict(live_c), dict(live_m)

    def contact(c, m):
        if m["done"] > c["paid"]:
            debt = (m["done"] - c["paid"]) * cost
            if c["energy"] < debt:
                return False
            c["energy"] -= debt
            c["paid"] = m["done"]
        if c["paid"] > m["done"]:
            m["done"] = c["paid"]  # credit layers performed without a new payment
        return True

    for event in events:
        if event == "OP":
            if contact(live_c, live_m) and live_c["energy"] >= cost:
                live_c["energy"] -= cost
                live_c["paid"] += 1
                live_m["done"] += 1
        elif event == "SAVE_C":
            dur_c = dict(live_c)
        elif event == "SAVE_M":
            dur_m = dict(live_m)
        elif event == "CRASH":
            live_c, live_m = dict(dur_c), dict(dur_m)
        else:
            raise ValueError(event)
    live_c["energy"] += 10 ** 9
    added = 10 ** 9
    assert contact(live_c, live_m)
    spent = energy + added - live_c["energy"]
    return {"paid": live_c["paid"], "done": live_m["done"], "spent": spent}


# --- ADR-054 section 11 transit ledger --------------------------------------------------------

class Transit:
    """One payload, one source S, one destination D, with live and durable copies of each store.

    Records are tuples (seq, state, dispatch_epoch, acked, ack_epoch); state is
    'T' (IN_TRANSIT), 'A' (ARRIVED) or 'C' (CLAIMED at D). Epochs are rank-compressed so the
    state space stays finite while every comparison the protocol uses is preserved.
    """

    MAX_SEQ = 4

    @staticmethod
    def initial(payloads=1):
        # input, outbox{(seq, persisted, aged)}, next_seq
        s_live = (payloads, frozenset(), 1)
        s_dur = (payloads, frozenset(), 1)
        l_live = (frozenset(), 0, 1)  # records, hw, E
        l_dur = (frozenset(), 0, 1)
        d_live = (0, frozenset())  # buffer, receipts{(seq,persisted)}
        d_dur = (0, frozenset())
        return (s_live, s_dur, l_live, l_dur, d_live, d_dur, False)

    @staticmethod
    def normalize(state):
        s_live, s_dur, (records, hw, e), (drecords, dhw, de), d_live, d_dur, rollback = state
        epochs = {e, de}
        for record in records | drecords:
            epochs.add(record[2])
            if record[4] is not None:
                epochs.add(record[4])
        rank = {value: index for index, value in enumerate(sorted(epochs))}

        def remap(rs):
            return frozenset((r[0], r[1], rank[r[2]], r[3], None if r[4] is None else rank[r[4]]) for r in rs)

        return (s_live, s_dur, (remap(records), hw, rank[e]), (remap(drecords), dhw, rank[de]), d_live, d_dur, rollback)

    @staticmethod
    def record(records, seq):
        for r in records:
            if r[0] == seq:
                return r
        return None

    @classmethod
    def successors(cls, state, crashes_left, faults_left):
        """Yields (label, new_state, crashes_left, faults_left) for every enabled event."""
        s_live, s_dur, l_live, l_dur, d_live, d_dur, rollback = state
        s_input, outbox, next_seq = s_live
        records, hw, e = l_live
        buffer, receipts = d_live
        out_seqs = {o[0] for o in outbox}
        rec = lambda seq: cls.record(records, seq)
        receipt_seqs = {r[0] for r in receipts}

        def make(**changes):
            parts = dict(s_live=s_live, s_dur=s_dur, l_live=l_live, l_dur=l_dur,
                         d_live=d_live, d_dur=d_dur, rollback=rollback)
            parts.update(changes)
            return cls.normalize((parts["s_live"], parts["s_dur"], parts["l_live"], parts["l_dur"],
                                  parts["d_live"], parts["d_dur"], parts["rollback"]))

        # Source rollback detection (ADR-054 section 11, last source row); escrow waits for it.
        rollback_pending = next_seq <= hw
        if rollback_pending:
            yield ("FIX_ROLLBACK", make(s_live=(s_input, outbox, hw + 1), rollback=True),
                   crashes_left, faults_left)
        if not rollback_pending and s_input > 0 and len(outbox) < 4 and next_seq <= cls.MAX_SEQ:
            yield ("ESCROW", make(s_live=(s_input - 1, outbox | {(next_seq, False, False)}, next_seq + 1)),
                   crashes_left, faults_left)
        new_outbox = frozenset((o[0], True, o[2]) for o in outbox)
        saved = (s_input, frozenset(out_seqs), next_seq)
        if saved != s_dur or new_outbox != outbox:
            yield ("SAVE_S", make(s_live=(s_input, new_outbox, next_seq), s_dur=saved), crashes_left, faults_left)
            if faults_left:
                # The save is observed (ChunkDataEvent.Save) but its asynchronous file write is lost.
                yield ("SAVE_S_LOST", make(s_live=(s_input, new_outbox, next_seq)), crashes_left, faults_left - 1)
        if any(o[1] and not o[2] for o in outbox):
            yield ("AGE", make(s_live=(s_input, frozenset((o[0], o[1], o[1] or o[2]) for o in outbox), next_seq)),
                   crashes_left, faults_left)
        for seq, persisted, aged in sorted(outbox):
            r = rec(seq)
            if r is None and seq <= hw:
                yield ("STALE_DROP", make(s_live=(s_input, outbox - {(seq, persisted, aged)}, next_seq)),
                       crashes_left, faults_left)
            elif r is None and persisted and aged and seq == hw + 1:
                yield ("REGISTER", make(l_live=(records | {(seq, "T", e, False, None)}, seq, e)),
                       crashes_left, faults_left)
            elif r is not None and e > r[2]:
                yield ("RELEASE", make(s_live=(s_input, outbox - {(seq, persisted, aged)}, next_seq)),
                       crashes_left, faults_left)
        for r in sorted(records, key=lambda x: x[0]):
            seq, st, de, acked, ae = r
            if st == "T":
                yield ("ARRIVE", make(l_live=(records - {r} | {(seq, "A", de, acked, ae)}, hw, e)),
                       crashes_left, faults_left)
            if st == "A" and e > de and seq not in receipt_seqs:
                yield ("CLAIM", make(l_live=(records - {r} | {(seq, "C", de, False, None)}, hw, e),
                                     d_live=(buffer + 1, receipts | {(seq, False)})), crashes_left, faults_left)
            if st in ("T", "A") and seq in receipt_seqs:
                yield ("D_RECOVER", make(l_live=(records - {r} | {(seq, "C", de, False, None)}, hw, e)),
                       crashes_left, faults_left)
            if st == "C" and not acked and (seq, True) in receipts:
                yield ("D_ACK", make(l_live=(records - {r} | {(seq, "C", de, True, e)}, hw, e)),
                       crashes_left, faults_left)
            if st == "C" and not acked and seq not in receipt_seqs:
                yield ("D_REMAT", make(d_live=(buffer + 1, receipts | {(seq, False)})), crashes_left, faults_left)
            if st == "C" and acked and e > ae:
                yield ("PRUNE", make(l_live=(records - {r}, hw, e)), crashes_left, faults_left)
        for receipt in sorted(receipts):
            r = rec(receipt[0])
            if r is None or (r[1] == "C" and r[3] and e > r[4]):
                yield ("D_DROP", make(d_live=(buffer, receipts - {receipt})), crashes_left, faults_left)
        if (records, hw) != (l_dur[0], l_dur[1]):
            yield ("FLUSH_L", make(l_live=(records, hw, e + 1), l_dur=(records, hw, e + 1)), crashes_left, faults_left)
        d_saved = (buffer, frozenset(r[0] for r in receipts))
        new_receipts = frozenset((r[0], True) for r in receipts)
        if d_saved != d_dur or new_receipts != receipts:
            yield ("SAVE_D", make(d_live=(buffer, new_receipts), d_dur=d_saved), crashes_left, faults_left)
        if crashes_left:
            yield ("CRASH", cls.crash(state), crashes_left - 1, faults_left)

    @classmethod
    def crash(cls, state):
        _, s_dur, _, l_dur, _, d_dur, rollback = state
        s_input, out_seqs, next_seq = s_dur
        # After restart the chunk load is a persistence observation; ageing restarts.
        s_live = (s_input, frozenset((seq, True, False) for seq in out_seqs), next_seq)
        d_buffer, d_receipts = d_dur
        return cls.normalize((s_live, s_dur, l_dur, l_dur, (d_buffer, frozenset((r, True) for r in d_receipts)),
                              d_dur, rollback))

    @classmethod
    def drain(cls, state):
        """Fair continuation without crashes or faults; returns the quiescent state."""
        order = ["FIX_ROLLBACK", "STALE_DROP", "D_RECOVER", "D_ACK", "D_DROP", "D_REMAT", "PRUNE",
                 "RELEASE", "REGISTER", "CLAIM", "ARRIVE", "ESCROW", "AGE",
                 "FLUSH_L", "SAVE_D", "SAVE_S"]
        for _ in range(400):
            options = {}
            for label, new_state, _, _ in cls.successors(state, 0, 0):
                options.setdefault(label, new_state)
            chosen = next((label for label in order if label in options), None)
            if chosen is None:
                return state
            state = options[chosen]
        raise AssertionError("drain did not terminate")

    @classmethod
    def delivered(cls, state):
        (s_input, outbox, _), _, (records, _, _), _, (buffer, receipts), _, rollback = state
        assert not outbox and not records and not receipts and s_input == 0, state
        return buffer, rollback

    @classmethod
    def explore(cls, crashes, faults, payloads=1):
        start = cls.normalize(cls.initial(payloads))
        seen = set()
        stack = [(start, crashes, faults)]
        outcomes = {}
        while stack:
            node = stack.pop()
            if node in seen:
                continue
            seen.add(node)
            state, crashes_left, faults_left = node
            buffer, rollback = cls.delivered(cls.drain(state))
            outcomes.setdefault((buffer, rollback, faults_left < faults), 0)
            outcomes[(buffer, rollback, faults_left < faults)] += 1
            for _, new_state, c, f in cls.successors(state, crashes_left, faults_left):
                stack.append((new_state, c, f))
        return len(seen), outcomes


# --- ADR-056 railgun classes ------------------------------------------------------------------

def isqrt(n):
    x = int(n ** 0.5)
    while x * x > n:
        x -= 1
    while (x + 1) * (x + 1) <= n:
        x += 1
    return x


def railgun_quote(same_level_same_body, dx, dz, percent):
    if same_level_same_body:
        d = isqrt(dx * dx + dz * dz)
        cost = min(25_000 + 10 * d, 250_000)
        travel = min(max(20 + d // 64, 20), 200)
        klass = "LOCAL"
    else:
        cost, travel, klass = 250_000, 600, "ORBITAL"
    return [klass, cost * percent // 100, travel]


# --- ADR-057 black-hole burn ------------------------------------------------------------------

def black_hole_run(case):
    fuel = list(case["fuel"])
    burn = case["burn_ticks"]
    default = case["default_burn_ticks"]
    capacity = case["capacity"]
    rate = case["output"] * case["percent"] // 100
    energy, remaining, consumed = case.get("energy", 0), 0, 0
    extract = case.get("extract_per_tick", 0)
    for _ in range(case["ticks"]):
        if remaining == 0 and energy < capacity and fuel:
            item = fuel.pop(0)
            remaining = burn.get(item, default)
            consumed += 1
        if remaining > 0 and capacity - energy >= rate:
            energy += rate
            remaining -= 1
        energy -= min(energy, extract)
    return {"energy": energy, "remaining": remaining, "consumed": consumed}


# --- ADR-058 gravity fields -------------------------------------------------------------------

def field_box(center, radius, clip=None):
    x, y, z = center
    box = [x - radius, y - radius, z - radius, x + radius, y + radius, z + radius]
    if clip is not None:
        min_x, min_z, max_x, max_z = clip
        box = [max(box[0], min_x), box[1], max(box[2], min_z), min(box[3], max_x), box[4], min(box[5], max_z)]
        if box[0] > box[3] or box[2] > box[5]:
            return None
    return box


def volume(box):
    return (box[3] - box[0] + 1) * (box[4] - box[1] + 1) * (box[5] - box[2] + 1)


def chunk_span(low, high):
    return (high >> 4) - (low >> 4) + 1


def field_winner(fields, position):
    x, y, z = position
    inside = [f for f in fields if f["box"][0] <= x <= f["box"][3] and f["box"][1] <= y <= f["box"][4]
              and f["box"][2] <= z <= f["box"][5]]
    if not inside:
        return None
    inside.sort(key=lambda f: (volume(f["box"]), f["id"]))  # canonical lowercase UUID strings
    return inside[0]["id"]


def upkeep(radius):
    return 5 + 2 * radius


# --- ADR-054 authority and protection order ---------------------------------------------------

def authority(case):
    actor, action = case["actor"], case["action"]
    if actor == "operator":
        return True
    station = case.get("station")
    if station is None:
        if actor == "device_owner":
            return True
        return action == "VIEW"
    if actor == "device_owner":
        return station["owner_build_access"] or action == "VIEW"
    if actor == "station_owner":
        return True
    if actor == "station_member":
        return action == "VIEW"
    return False


PROTECTION_ORDER = ["loaded", "bounds", "zone", "station", "spawn", "api_event", "break_event"]
PROTECTION_CODES = {"loaded": "TARGET_UNLOADED", "bounds": "TARGET_OUT_OF_BOUNDS"}


def protection(failing):
    for step in PROTECTION_ORDER:
        if step in failing:
            return PROTECTION_CODES.get(step, "TARGET_PROTECTED")
    return "ALLOWED"


# --- ADR-059 bind order -----------------------------------------------------------------------

BIND_ORDER = [  # ADR-045 rules 1-5 keep their existing ElevatorEndpointCode names, then ADR-059 steps 2-6
    ("registry_operational", "REGISTRY_UNAVAILABLE"), ("station_exists", "STATION_MISSING"),
    ("actor_owner_or_operator", "UNAUTHORIZED"), ("body_is_orbit", "NOT_CURRENT_ORBIT"),
    ("surface_unique_level", "NO_SURFACE"), ("column_in_border", "OUTSIDE_WORLD_BORDER"),
    ("terminal_active_in_region", "TERMINAL_UNAVAILABLE"), ("anchor_active", "ANCHOR_UNAVAILABLE"),
    ("anchor_owned", "ANCHOR_FOREIGN"), ("station_unbound", "STATION_BOUND"),
    ("anchor_unbound", "ANCHOR_BOUND"), ("terminal_unbound", "TERMINAL_BOUND"),
    ("column_unbound", "COLUMN_BOUND"), ("no_warp_pending", "WARP_PENDING"), ("root_admission", "ROOT_FULL"),
]


def bind(case):
    for key, code in BIND_ORDER:
        if not case.get(key, True):
            return code
    return "BOUND"


class Vectors(unittest.TestCase):
    def test_splitmix64_published_seed0(self):
        generator = SplitMix64(0)
        self.assertEqual([format(generator.next(), "016x") for _ in range(3)], EXAMPLES["splitmix64_seed0"])

    def test_laser_constant_and_closed_form(self):
        self.assertEqual(format(LASERDRL, "016X"), EXAMPLES["laser_v1"]["laserdrl_hex"])
        for seed_hex in EXAMPLES["laser_v1"]["closed_form_seeds"]:
            seed = int(seed_hex, 16)
            stream = SplitMix64(seed ^ LASERDRL)
            for n in range(64):
                self.assertEqual(laser_output(seed, n), stream.next())

    def test_laser_draws(self):
        data = EXAMPLES["laser_v1"]
        for case in data["draws"]:
            seed = int(case["seed"], 16)
            got = [laser_draw(data["table"], seed, n) for n in range(case["from"], case["from"] + len(case["expected"]))]
            self.assertEqual(got, case["expected"], case["seed"])

    def test_laser_default_table_proportion(self):
        table = EXAMPLES["laser_v1"]["default_table"]
        ore = sum(e["weight"] for e in table["entries"] if e["item"] != "minecraft:cobblestone")
        total = sum(e["weight"] for e in table["entries"])
        self.assertEqual(ore * 10, total)  # legacy: one hit in ten is ore
        self.assertTrue(all(e["count"] == 5 for e in table["entries"] if e["item"] != "minecraft:diamond"))

    def test_laser_cost(self):
        for percent, expected in EXAMPLES["laser_cost"]:
            self.assertEqual(laser_cost(percent), expected)
        for bad in (9, 1001):
            with self.assertRaises(ValueError):
                laser_cost(bad)

    def test_shaft_geometry(self):
        for case in EXAMPLES["shaft"]:
            mx, my, mz = case["marker"]
            self.assertEqual(footprint_inside_chunk(mx, mz), case["inside_chunk"], case)
            self.assertEqual(shaft_cells(mx, mz), case["cells"], case)
            self.assertEqual(shaft_floor(my, case["max_depth"], case["min_build_height"]), case["floor"], case)

    def test_counter_settlement_every_crash_cut(self):
        cost = 10_000
        alphabet = ["OP", "SAVE_C", "SAVE_M", "CRASH"]
        checked = 0

        def walk(prefix, depth):
            nonlocal checked
            result = counter_model(prefix, cost, 3 * cost)
            # Declarative rule: after settlement, energy spent equals cost x layers done.
            self.assertEqual(result["paid"], result["done"], prefix)
            self.assertEqual(result["spent"], cost * result["done"], prefix)
            checked += 1
            if depth:
                for event in alphabet:
                    walk(prefix + [event], depth - 1)

        walk([], 7)
        self.assertEqual(checked, EXAMPLES["counter_sequences_checked"])

    def test_counter_named_cuts(self):
        for case in EXAMPLES["counter_cuts"]:
            self.assertEqual(counter_model(case["events"], 10_000, 100_000), case["expected"], case["name"])

    def test_transit_exactly_once_without_faults(self):
        # Two payloads exercise in-order registration, two outbox entries and stale drops.
        states, outcomes = Transit.explore(crashes=2, faults=0, payloads=2)
        self.assertEqual(set(outcomes), {(2, False, False)}, outcomes)
        self.assertEqual(states, EXAMPLES["transit"]["states_two_payloads_two_crashes"])

    def test_transit_lost_write_residual_is_detected(self):
        states, outcomes = Transit.explore(crashes=2, faults=1)
        for (delivered, rollback, faulted), _ in outcomes.items():
            self.assertIn(delivered, (1, 2))
            if delivered == 2:
                self.assertTrue(faulted and rollback, outcomes)  # only after a lost write, always audited
            if not faulted:
                self.assertEqual((delivered, rollback), (1, False))
        self.assertIn((2, True, True), outcomes)  # the residual is reachable, as documented
        self.assertEqual(states, EXAMPLES["transit"]["states_one_payload_two_crashes_one_lost_write"])

    def test_transit_named_cuts(self):
        for case in EXAMPLES["transit"]["named_cuts"]:
            state = Transit.normalize(Transit.initial())
            for label in case["events"]:
                options = {l: s for l, s, _, _ in Transit.successors(state, 1, 1)}
                self.assertIn(label, options, (case["name"], label, sorted(options)))
                state = options[label]
            self.assertEqual(list(Transit.delivered(Transit.drain(state))), case["expected"], case["name"])

    def test_railgun_quotes(self):
        for case in EXAMPLES["railgun"]:
            self.assertEqual(railgun_quote(case["local"], case["dx"], case["dz"], case["percent"]),
                             case["expected"], case)

    def test_black_hole_burn(self):
        for case in EXAMPLES["black_hole"]:
            self.assertEqual(black_hole_run(case), case["expected"], case["name"])
        self.assertLessEqual(2048 * 400 // 100, 8192)

    def test_gravity_fields(self):
        data = EXAMPLES["gravity"]
        for case in data["boxes"]:
            self.assertEqual(field_box(case["center"], case["radius"], case.get("clip")), case["box"], case)
        for case in data["winners"]:
            fields = [{"id": f["id"], "box": field_box(f["center"], f["radius"])} for f in case["fields"]]
            self.assertEqual(field_winner(fields, case["position"]), case["winner"], case["name"])
        for radius, cost in data["upkeep"]:
            self.assertEqual(upkeep(radius), cost)
        worst = max(chunk_span(x - 16, x + 16) for x in range(-40, 40))
        self.assertEqual(worst, 3)

    def test_authority_matrix(self):
        for case in EXAMPLES["authority"]:
            self.assertEqual(authority(case), case["allowed"], case)

    def test_protection_order(self):
        for case in EXAMPLES["protection"]:
            self.assertEqual(protection(case["failing"]), case["expected"], case)

    def test_bind_order(self):
        for case in EXAMPLES["bind"]:
            self.assertEqual(bind(case["facts"]), case["expected"], case["name"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
