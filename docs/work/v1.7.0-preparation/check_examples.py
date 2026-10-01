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
    """R1-M8: marker at local 2..13, so cells (local 1..14) and their direct neighbours stay in the chunk.

    Java `x & 15` equals Python `x & 15` for negative ints (two's complement).
    """
    return 2 <= (mx & 15) <= 13 and 2 <= (mz & 15) <= 13


def neighbours_inside_chunk(mx, mz):
    """Every direct neighbour of every footprint cell is in the marker's chunk."""
    chunk = (mx >> 4, mz >> 4)
    for x, z in shaft_cells(mx, mz):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if ((x + dx) >> 4, (z + dz) >> 4) != chunk:
                return False
    return True


def shaft_cells(mx, mz):
    return [[mx - 1 + i % 3, mz - 1 + i // 3] for i in range(9)]


def shaft_floor(my, max_depth, min_build_height):
    return max(min_build_height + 1, my - max_depth)


# --- ADR-055 physical-mode payment counters ---------------------------------------------------

BUFFER = 200_000  # ADR-055 controller buffer


def counter_model(events, cost, energy):
    """Runs controller/marker events with independent saves; returns the settled durable result.

    events: OP, CHARGE (cost FE arrives, capped at the buffer), SAVE_C, SAVE_M, CRASH. Debt is settled one
    layer at a time from the buffer (R1-M7); after the sequence the controller keeps receiving CHARGE until
    it is settled, the way a powered controller eventually would. `spent` is the controller's durable debit.
    """
    live_c = {"energy": energy, "paid": 0, "spent": 0}
    live_m = {"done": 0}
    dur_c, dur_m = dict(live_c), dict(live_m)

    def contact(c, m):
        while m["done"] > c["paid"] and c["energy"] >= cost:  # debt, layer by layer
            c["energy"] -= cost
            c["spent"] += cost
            c["paid"] += 1
        if c["paid"] > m["done"]:
            m["done"] = c["paid"]  # credit layers performed without a new payment
        return m["done"] == c["paid"]

    def charge(c):
        c["energy"] = min(BUFFER, c["energy"] + cost)

    for event in events:
        if event == "OP":
            if contact(live_c, live_m) and live_c["energy"] >= cost:
                live_c["energy"] -= cost
                live_c["spent"] += cost
                live_c["paid"] += 1
                live_m["done"] += 1
        elif event == "CHARGE":
            charge(live_c)
        elif event == "SAVE_C":
            dur_c = dict(live_c)
        elif event == "SAVE_M":
            dur_m = dict(live_m)
        elif event == "CRASH":
            live_c, live_m = dict(dur_c), dict(dur_m)
        else:
            raise ValueError(event)
    for _ in range(64):
        if contact(live_c, live_m):
            break
        charge(live_c)
    else:
        raise AssertionError("debt never settled")
    return {"paid": live_c["paid"], "done": live_m["done"], "spent": live_c["spent"]}


# --- ADR-054 section 11 transit ledger --------------------------------------------------------

class Transit:
    """One payload, one source S, one destination D, with live and durable copies of each store.

    Records are tuples (seq, state, dispatch_epoch, acked, ack_epoch); state is
    'T' (IN_TRANSIT), 'A' (ARRIVED) or 'C' (CLAIMED at D). Epochs are rank-compressed so the
    state space stays finite while every comparison the protocol uses is preserved.
    """

    MAX_SEQ = 4
    # R1-H1: tombstone removal needs an aged absence; tests switch it off to separate the residual.
    TOMBSTONE_REMOVAL = True

    @staticmethod
    def initial(payloads=1):
        # input, outbox{(seq, persisted, aged)}, next_seq, present, absence (False, "seen" or "aged")
        s_live = (payloads, frozenset(), 1, True, False)
        s_dur = (payloads, frozenset(), 1, True)
        l_live = (frozenset(), 0, 1)  # records, hw, E
        l_dur = (frozenset(), 0, 1)
        d_live = (0, frozenset())  # buffer, receipts{(seq,persisted)}
        d_dur = (0, frozenset())
        return (s_live, s_dur, l_live, l_dur, d_live, d_dur, False, (0, 0))

    @staticmethod
    def normalize(state):
        s_live, s_dur, (records, hw, e), (drecords, dhw, de), d_live, d_dur, rollback, destroyed = state
        epochs = {e, de}
        for record in records | drecords:
            epochs.add(record[2])
            if record[4] is not None:
                epochs.add(record[4])
        rank = {value: index for index, value in enumerate(sorted(epochs))}

        def remap(rs):
            return frozenset((r[0], r[1], rank[r[2]], r[3], None if r[4] is None else rank[r[4]]) for r in rs)

        return (s_live, s_dur, (remap(records), hw, rank[e]), (remap(drecords), dhw, rank[de]), d_live, d_dur, rollback,
                destroyed)

    @staticmethod
    def record(records, seq):
        for r in records:
            if r[0] == seq:
                return r
        return None

    @classmethod
    def successors(cls, state, crashes_left, faults_left):
        """Yields (label, new_state, crashes_left, faults_left) for every enabled event."""
        s_live, s_dur, l_live, l_dur, d_live, d_dur, rollback, destroyed = state
        s_input, outbox, next_seq, present, absence_seen = s_live
        records, hw, e = l_live
        hw_value = 0 if hw is None else hw  # None: the tombstone was removed
        buffer, receipts = d_live
        out_seqs = {o[0] for o in outbox}
        rec = lambda seq: cls.record(records, seq)
        receipt_seqs = {r[0] for r in receipts}

        def make(**changes):
            parts = dict(s_live=s_live, s_dur=s_dur, l_live=l_live, l_dur=l_dur,
                         d_live=d_live, d_dur=d_dur, rollback=rollback, destroyed=destroyed)
            parts.update(changes)
            return cls.normalize((parts["s_live"], parts["s_dur"], parts["l_live"], parts["l_dur"],
                                  parts["d_live"], parts["d_dur"], parts["rollback"], parts["destroyed"]))

        # Source rollback detection (ADR-054 section 11, last source row); escrow waits for it.
        rollback_pending = present and next_seq <= hw_value
        if rollback_pending:
            yield ("FIX_ROLLBACK", make(s_live=(s_input, outbox, hw_value + 1, present, absence_seen),
                                         rollback=True),
                   crashes_left, faults_left)
        if present and not rollback_pending and s_input > 0 and len(outbox) < 4 and next_seq <= cls.MAX_SEQ:
            yield ("ESCROW", make(s_live=(s_input - 1, outbox | {(next_seq, False, False)}, next_seq + 1,
                                          present, absence_seen)), crashes_left, faults_left)
        if present and s_input == 0:
            # R1-H3: removal by any cause. Escrowed entries never drop as items: registered ones are delivered
            # by the ledger, unregistered ones are destroyed (audited loss). R2-H2: if a registered entry's
            # record is not yet durable, the removal writes the ledger with a barrier flush in the same tick.
            # The input buffer is a plain container, so the model removes S only when it is empty.
            unregistered = sum(1 for seq in out_seqs if seq > hw_value)
            pending = any(rec(seq) is not None and not e > rec(seq)[2] for seq in out_seqs if seq <= hw_value)
            barrier = dict(l_live=(records, hw, e + 1), l_dur=(records, hw, e + 1)) if pending else {}
            yield ("REMOVE_S", make(s_live=(0, frozenset(), next_seq, False, False),
                                    destroyed=(destroyed[0] + unregistered, destroyed[1]), **barrier),
                   crashes_left, faults_left)
        new_outbox = frozenset((o[0], True, o[2]) for o in outbox)
        saved = (s_input, frozenset(out_seqs), next_seq, present)
        observed = absence_seen if absence_seen or present else "seen"
        if saved != s_dur or new_outbox != outbox or observed != absence_seen or destroyed[0] != destroyed[1]:
            yield ("SAVE_S", make(s_live=(s_input, new_outbox, next_seq, present, observed), s_dur=saved,
                                  destroyed=(destroyed[0], destroyed[0])), crashes_left, faults_left)
            if faults_left:
                # The save is observed (ChunkDataEvent.Save) but its asynchronous file write is lost.
                yield ("SAVE_S_LOST", make(s_live=(s_input, new_outbox, next_seq, present, observed)),
                       crashes_left, faults_left - 1)
        if any(o[1] and not o[2] for o in outbox) or absence_seen == "seen":
            yield ("AGE", make(s_live=(s_input, frozenset((o[0], o[1], o[1] or o[2]) for o in outbox), next_seq,
                                       present, "aged" if absence_seen else False)), crashes_left, faults_left)
        if cls.TOMBSTONE_REMOVAL and hw is not None and not present and absence_seen == "aged":
            # R1-H1: the tombstone goes only once the source's absence is persisted.
            yield ("DROP_HW", make(l_live=(records, None, e)), crashes_left, faults_left)
        lowest = min(out_seqs) if out_seqs else None
        for seq, persisted, aged in sorted(outbox):
            r = rec(seq)
            if r is None and seq <= hw_value:
                yield ("STALE_DROP", make(s_live=(s_input, outbox - {(seq, persisted, aged)}, next_seq,
                                                  present, absence_seen)), crashes_left, faults_left)
            elif r is None and persisted and aged and seq == lowest and seq > hw_value:
                yield ("REGISTER", make(l_live=(records | {(seq, "T", e, False, None)}, seq, e)),
                       crashes_left, faults_left)
            elif r is not None and e > r[2]:
                yield ("RELEASE", make(s_live=(s_input, outbox - {(seq, persisted, aged)}, next_seq,
                                               present, absence_seen)), crashes_left, faults_left)
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
        _, s_dur, _, l_dur, _, d_dur, rollback, destroyed = state
        s_input, out_seqs, next_seq, present = s_dur
        # After restart the chunk load is a persistence observation; ageing restarts.
        s_live = (s_input, frozenset((seq, True, False) for seq in out_seqs), next_seq, present,
                  False if present else "seen")
        d_buffer, d_receipts = d_dur
        return cls.normalize((s_live, s_dur, l_dur, l_dur, (d_buffer, frozenset((r, True) for r in d_receipts)),
                              d_dur, rollback, (destroyed[1], destroyed[1])))

    @classmethod
    def drain(cls, state):
        """Fair continuation without crashes or faults; returns the quiescent state."""
        order = ["FIX_ROLLBACK", "STALE_DROP", "D_RECOVER", "D_ACK", "D_DROP", "D_REMAT", "PRUNE",
                 "RELEASE", "REGISTER", "CLAIM", "ARRIVE", "ESCROW", "AGE", "DROP_HW",
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
        (s_input, outbox, _, _, _), _, (records, _, _), _, (buffer, receipts), _, rollback, destroyed = state
        assert not outbox and not records and not receipts and s_input == 0, state
        return buffer, destroyed[1], rollback

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
            key = cls.delivered(cls.drain(state)) + (faults_left < faults,)
            outcomes[key] = outcomes.get(key, 0) + 1
            for _, new_state, c, f in cls.successors(state, crashes_left, faults_left):
                stack.append((new_state, c, f))
        return len(seen), outcomes


class Delivery:
    """Destination half of ADR-054 section 11 with a third store: a player who withdraws the payload.

    One payload whose record is already durable and ARRIVED. `gated=True` is revision 2: a claim lands in a
    non-extractable `incoming` area and becomes withdrawable only after its receipt is persisted. `gated=False`
    is the ADR-051 shape the v1.3-v1.6 deep-test report (F02) found duplicating: the claim is extractable at once.
    The destination can be removed by any cause (R1-H3): an unacknowledged claim returns to ARRIVED, and after
    the removal is persisted an owner or operator redirects the record to a new endpoint.

    The `vanilla` flag is set at a crash that tears the ordinary container/player pair, the class every vanilla
    chest has: the player's file already holds the payload while the destination's saved, extractable buffer
    still does, or the player loses unsaved items. Protocol-created duplicates are those without the flag.
    """

    @staticmethod
    def initial():
        record = ("A", False, None)  # state, acked, ack_epoch
        # d: incoming (0, "U", "P"), buffer, receipt (None, "U", "P"), present, absence (False or "seen")
        return (record, record, 1, 1, (0, 0, None, True, False), (0, 0, False, True), 0, 0, False)

    @staticmethod
    def normalize(state):
        rec, rec_dur, e, e_dur, d, d_dur, p, p_dur, vanilla = state
        epochs = {e, e_dur} | {r[2] for r in (rec, rec_dur) if r is not None and r[2] is not None}
        rank = {value: index for index, value in enumerate(sorted(epochs))}
        fix = lambda r: None if r is None else (r[0], r[1], None if r[2] is None else rank[r[2]])
        return (fix(rec), fix(rec_dur), rank[e], rank[e_dur], d, d_dur, p, p_dur, vanilla)

    @classmethod
    def successors(cls, state, gated, crashes_left, faults_left=0):
        rec, rec_dur, e, e_dur, (incoming, buffer, receipt, present, absence), d_dur, p, p_dur, vanilla = state
        c, f = crashes_left, faults_left

        def make(**c):
            parts = dict(rec=rec, rec_dur=rec_dur, e=e, e_dur=e_dur,
                         d=(incoming, buffer, receipt, present, absence), d_dur=d_dur, p=p, p_dur=p_dur,
                         vanilla=vanilla)
            parts.update(c)
            return cls.normalize(tuple(parts[k] for k in ("rec", "rec_dur", "e", "e_dur", "d", "d_dur", "p",
                                                          "p_dur", "vanilla")))

        def d(**c):
            parts = dict(incoming=incoming, buffer=buffer, receipt=receipt, present=present, absence=absence)
            parts.update(c)
            return tuple(parts[k] for k in ("incoming", "buffer", "receipt", "present", "absence"))

        land = (lambda: d(incoming="U", receipt="U")) if gated else (lambda: d(buffer=buffer + 1, receipt="U"))
        claimed = rec is not None and rec[0] == "C" and not rec[1]
        if present and rec is not None and rec[0] == "A" and receipt is None:
            yield "CLAIM", make(rec=("C", False, None), d=land()), c, f
        if present and gated and incoming == "A" and (claimed or rec is None or rec[1]):
            # The move and the acknowledgement are one step (the ledger is updated in the same tick). An already
            # acknowledged or pruned record means D's chunk is behind its own move: move again.
            yield "MOVE", make(d=d(incoming=0, buffer=buffer + 1), rec=("C", True, e) if claimed else rec), c, f
        if present and buffer and p < 3:
            yield "WITHDRAW", make(d=d(buffer=buffer - 1), p=p + 1), c, f
        saved = (1 if incoming else 0, buffer, receipt is not None, present)
        seen = absence or (False if present else "seen")
        observe = lambda flag: flag if flag in (0, None, "A") else "P"
        if saved != d_dur or receipt == "U" or incoming == "U" or seen != absence:
            yield "SAVE_D", make(d=d(incoming=observe(incoming), receipt=observe(receipt), absence=seen),
                                 d_dur=saved), c, f
            if f:
                # R1-M1: the save is observed (ChunkDataEvent.Save) but its asynchronous file write is lost.
                yield "SAVE_D_LOST", make(d=d(incoming=observe(incoming), receipt=observe(receipt), absence=seen)), \
                    c, f - 1
        if incoming == "P" or receipt == "P":
            age = lambda flag: "A" if flag == "P" else flag
            yield "AGE_D", make(d=d(incoming=age(incoming), receipt=age(receipt))), c, f
        if p != p_dur:
            yield "SAVE_P", make(p_dur=p), c, f
        if rec != rec_dur:
            yield "FLUSH_L", make(rec_dur=rec, e=e + 1, e_dur=e + 1), c, f
        if present and rec is not None and rec[0] == "A" and receipt is not None:
            yield "D_RECOVER", make(rec=("C", False, None)), c, f
        # Recovery after a crash that kept D's moved state but lost the ledger's acknowledgement.
        if present and claimed and receipt == "A" and not incoming:
            yield "D_ACK", make(rec=("C", True, e)), c, f
        if present and claimed and receipt is None:
            yield "D_REMAT", make(d=land()), c, f
        if receipt is not None and (rec is None or (rec[1] and e > rec[2])):
            yield "D_DROP", make(d=d(receipt=None)), c, f
        if rec is not None and rec[1] and e > rec[2]:
            yield "PRUNE", make(rec=None), c, f
        due_move = incoming in ("P", "A") and (rec is None or rec[1])
        if present and buffer == 0 and not due_move:
            # R1-H3: removal by any cause, settled from D's live state and written with a barrier flush. A payload
            # still incoming (or never materialized here) returns its record to ARRIVED; one that moved is
            # acknowledged. An incoming payload whose record is already acknowledged is D's own content (its move
            # is due) and drops with the receive buffer; the model covers that case as MOVE followed by a plain
            # container drop, so it removes D only with an empty receive buffer and no due move.
            if rec is not None and not rec[1]:
                settled = ("C", True, e) if receipt is not None and not incoming else ("A", False, None)
            else:
                settled = rec
            yield "REMOVE_D", make(d=(0, 0, None, False, False), rec=settled, rec_dur=settled, e=e + 1,
                                   e_dur=e + 1), c, f
        if not present and absence == "seen" and rec is not None and rec[0] == "A":
            yield "REDIRECT", make(d=(0, 0, None, True, False), d_dur=(0, 0, False, True)), c, f
        if crashes_left:
            torn = (p_dur >= 1 and d_dur[0] + d_dur[1] >= 1) or p > p_dur
            yield "CRASH", cls.normalize((rec_dur, rec_dur, e_dur, e_dur,
                                          ("P" if d_dur[0] else 0, d_dur[1], "P" if d_dur[2] else None, d_dur[3],
                                           False if d_dur[3] else "seen"), d_dur,
                                          p_dur, p_dur, vanilla or torn)), c - 1, f

    @classmethod
    def outcome(cls, state, gated):
        order = ["D_RECOVER", "D_ACK", "D_DROP", "D_REMAT", "PRUNE", "CLAIM", "MOVE", "AGE_D", "FLUSH_L",
                 "SAVE_D", "SAVE_P", "REDIRECT"]
        for _ in range(300):
            options = {label: s for label, s, _, _ in cls.successors(state, gated, 0)}
            chosen = next((label for label in order if label in options), None)
            if chosen is None:
                rec, _, _, _, (incoming, buffer, receipt, present, _), _, p, _, vanilla = state
                assert rec is None and receipt is None and not incoming, state
                return buffer + p, vanilla
            state = options[chosen]
        raise AssertionError("delivery drain did not terminate")

    @classmethod
    def explore(cls, gated, crashes=2, faults=0):
        seen, stack, outcomes = set(), [(cls.normalize(cls.initial()), crashes, faults)], {}
        while stack:
            node = stack.pop()
            if node in seen:
                continue
            seen.add(node)
            state, left, faults_left = node
            key = cls.outcome(state, gated) + (faults_left < faults,)
            outcomes[key] = outcomes.get(key, 0) + 1
            for _, new_state, c, f in cls.successors(state, gated, left, faults_left):
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
    if not 10 <= percent <= 400:  # R1-L11: the largest cost must fit the 1,000,000 FE buffer
        raise ValueError("railgun percent outside 10..400")
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


def jump_height(multiplier, v0=0.42, drag=0.98, gravity=0.08):
    """Peak height of a vanilla player jump with the gravity attribute scaled (1.00 g gives 1.25 blocks)."""
    y, v, best = 0.0, v0, 0.0
    for _ in range(400):
        y += v
        best = max(best, y)
        v = (v - gravity * multiplier) * drag
        if v < 0 and y < best:
            break
    return best


def field_affects(case):
    """R1-M9: inside a station region everyone; elsewhere the owner and the allow list."""
    return case["in_station"] or case["player"] == case["owner"] or case["player"] in case["allow"]


# --- ADR-054 authority and protection order ---------------------------------------------------

def authority(case):
    actor, action = case["actor"], case["action"]
    if actor == "operator":
        return True
    if action in ("RIDE", "SHIP"):  # ADR-054 section 3 per-action overrides (R1-M3)
        return actor in ("station_owner", "station_member") and case["anchor_owner_in_station"]
    if action == "BIND":
        return actor == "station_owner"
    if action == "UNBIND":
        return actor in ("station_owner", "anchor_owner")
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
            self.assertEqual(neighbours_inside_chunk(mx, mz), case["inside_chunk"], case)
            self.assertEqual(shaft_cells(mx, mz), case["cells"], case)
            self.assertEqual(shaft_floor(my, case["max_depth"], case["min_build_height"]), case["floor"], case)

    def test_counter_settlement_every_crash_cut(self):
        cost = 10_000
        alphabet = ["OP", "CHARGE", "SAVE_C", "SAVE_M", "CRASH"]
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

    def test_counter_debt_larger_than_the_buffer_settles(self):
        # R1-M7: at 1,000 % a layer costs 100,000 FE; three unsaved controller layers owe more than the buffer.
        cost = laser_cost(1000)
        events = ["OP", "CHARGE", "OP", "CHARGE", "OP", "SAVE_M", "CRASH"]
        self.assertEqual(counter_model(events, cost, BUFFER), {"paid": 3, "done": 3, "spent": 300_000})

    def test_counter_named_cuts(self):
        for case in EXAMPLES["counter_cuts"]:
            self.assertEqual(counter_model(case["events"], 10_000, 100_000), case["expected"], case["name"])

    def test_transit_exactly_once_without_faults(self):
        # Two payloads exercise in-order registration, two outbox entries and stale drops.
        states, outcomes = Transit.explore(crashes=2, faults=0, payloads=2)
        for buffer, destroyed, rollback, faulted in outcomes:
            self.assertFalse(rollback or faulted)
            self.assertLessEqual(buffer, 2)  # never a duplicate
            # Every payload is delivered or audited as destroyed by a forced removal (R1-H3, R2-H2).
            self.assertEqual(buffer + destroyed, 2)
        self.assertEqual(states, EXAMPLES["transit"]["states_two_payloads_two_crashes"])

    def test_transit_lost_write_residual_is_detected(self):
        # With tombstones kept, every duplicate needs a lost escrow write and is audited.
        Transit.TOMBSTONE_REMOVAL = False
        try:
            states, outcomes = Transit.explore(crashes=2, faults=1)
        finally:
            Transit.TOMBSTONE_REMOVAL = True
        for (delivered, destroyed, rollback, faulted), _ in outcomes.items():
            self.assertGreaterEqual(delivered + destroyed, 1)
            if delivered > 1:
                self.assertTrue(faulted and rollback, outcomes)  # only after a lost write, always audited
            if not faulted:
                self.assertLessEqual(delivered, 1)
                self.assertFalse(rollback)
        self.assertIn((2, 0, True, True), outcomes)  # the residual is reachable, as documented
        self.assertEqual(states, EXAMPLES["transit"]["states_one_payload_two_crashes_one_lost_write"])

    def test_transit_tombstone_residual_needs_a_lost_absence_write(self):
        states, outcomes = Transit.explore(crashes=2, faults=1)
        unaudited = {key for key in outcomes if key[0] > 1 and not key[2]}
        self.assertTrue(unaudited)
        self.assertTrue(all(key[3] for key in unaudited))  # only after a lost write, as ADR-054 section 11 states
        self.assertEqual(states, EXAMPLES["transit"]["states_tombstone_removal_one_lost_write"])

    def test_transit_named_cuts(self):
        for case in EXAMPLES["transit"]["named_cuts"]:
            state = Transit.normalize(Transit.initial())
            for label in case["events"]:
                options = {l: s for l, s, _, _ in Transit.successors(state, 1, 1)}
                self.assertIn(label, options, (case["name"], label, sorted(options)))
                state = options[label]
            self.assertEqual(list(Transit.delivered(Transit.drain(state))), case["expected"], case["name"])

    def test_delivery_gate_leaves_only_the_container_torn_save_class(self):
        states, outcomes = Delivery.explore(gated=True, crashes=2)
        for (total, torn, _), _ in outcomes.items():
            if total != 1:
                self.assertTrue(torn, outcomes)  # no duplicate or loss without a container/player torn save
        self.assertEqual(states, EXAMPLES["delivery"]["states_gated_two_crashes"])

    def test_delivery_lost_write_residual(self):
        # R1-M1: with one lost destination write, a non-torn loss or duplicate needs that fault.
        states, outcomes = Delivery.explore(gated=True, crashes=2, faults=1)
        for (total, torn, faulted), _ in outcomes.items():
            if total != 1 and not torn:
                self.assertTrue(faulted, outcomes)
        self.assertIn((0, False, True), outcomes)  # the documented destination-side loss
        self.assertEqual(states, EXAMPLES["delivery"]["states_gated_two_crashes_one_lost_write"])

    def test_delivery_removal_settles_once(self):
        for case in EXAMPLES["delivery"]["removal_cuts"]:
            state = Delivery.normalize(Delivery.initial())
            for label in case["events"]:
                state = {l: s for l, s, *_ in Delivery.successors(state, True, 1)}[label]
            self.assertEqual(list(Delivery.outcome(state, True)), case["expected"], case["name"])

    def test_delivery_without_the_gate_reproduces_f02(self):
        _, outcomes = Delivery.explore(gated=False, crashes=2)
        self.assertIn((2, False, False), outcomes)
        state = Delivery.normalize(Delivery.initial())
        for label in EXAMPLES["delivery"]["f02_path"]:
            state = {l: s for l, s, *_ in Delivery.successors(state, False, 1)}[label]
        self.assertEqual(Delivery.outcome(state, False), (2, False))
        gated = Delivery.normalize(Delivery.initial())
        for label in EXAMPLES["delivery"]["f02_path_gated"]:
            gated = {l: s for l, s, *_ in Delivery.successors(gated, True, 1)}[label]
        # With the gate the same prefix offers no withdrawal until a chunk save captured the claim.
        self.assertNotIn("WITHDRAW", {l for l, *_ in Delivery.successors(gated, True, 1)})

    def test_railgun_quotes(self):
        for case in EXAMPLES["railgun"]:
            self.assertEqual(railgun_quote(case["local"], case["dx"], case["dz"], case["percent"]),
                             case["expected"], case)
            self.assertLessEqual(case["expected"][1], 1_000_000)  # fits the railgun buffer

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
        for multiplier, can_step_up in data["jump"]:
            self.assertEqual(jump_height(multiplier) >= 1.0, can_step_up, multiplier)
        for case in data["consent"]:
            self.assertEqual(field_affects(case), case["affected"], case)

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
