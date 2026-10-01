#!/usr/bin/env python3
"""Mutation check for the reference models in check_examples.py (standard library only).

Each mutation changes one rule of ADR-054 section 9.1 or 11 in a copy of check_examples.py and searches that
copy's own model for a violation: a duplicate, an unaccounted loss, or a state that never becomes quiescent.
The script exits 0 only when the unmodified control shows no violation and every mutation is caught.

Usage: python -B check_mutations.py <output directory outside the repository>
"""
import importlib.util
import shutil
import sys
from pathlib import Path

SRC = Path(__file__).resolve().parent

TRANSIT = {  # name: (old text, new text, lost writes needed to expose it)
    "register-unpersisted": ("elif r is None and persisted and aged and seq == lowest and seq > hw_value:",
                             "elif r is None and seq == lowest and seq > hw_value:", 0),
    "release-before-durable": ("elif r is not None and e > r[2]:", "elif r is not None:", 0),
    "claim-before-durable": ('if st == "A" and e > de and seq not in receipt_seqs:',
                             'if st == "A" and seq not in receipt_seqs:', 0),
    "drop-receipt-before-durable-ack": ('if r is None or (r[1] == "C" and r[3] and e > r[4]):',
                                        'if r is None or (r[1] == "C" and r[3]):', 0),
    "ack-unpersisted-receipt": ('if st == "C" and not acked and (seq, True) in receipts:',
                                'if st == "C" and not acked and seq in receipt_seqs:', 0),
    "no-stale-drop": ("if r is None and seq <= hw_value:", "if False:", 0),
    "prune-before-durable-ack": ('if st == "C" and acked and e > ae:', 'if st == "C" and acked:', 0),
    "source-removal-without-barrier": (
        "barrier = dict(l_live=(records, hw, e + 1), l_dur=(records, hw, e + 1)) if pending else {}",
        "barrier = {}", 0),
    "drop-tombstone-before-persisted-absence": ('hw is not None and not present and absence_seen == "aged":',
                                                "hw is not None and not present:", 0),
    "no-rollback-fix": ("        if rollback_pending:\n", "        if False:\n", 1),
}

DELIVERY = {
    "removal-always-unclaims": (
        'settled = ("C", True, e) if receipt is not None and not incoming else ("A", False, None)',
        'settled = ("A", False, None)'),
    "removal-without-barrier": (
        'yield "REMOVE_D", make(d=(0, 0, None, False, False), rec=settled, rec_dur=settled, e=e + 1,',
        'yield "REMOVE_D", make(d=(0, 0, None, False, False), rec=settled, rec_dur=rec_dur, e=e + 1,'),
    "move-before-persisted": (
        'if present and gated and incoming == "A" and (claimed or rec is None or rec[1]):',
        'if present and gated and incoming and (claimed or rec is None or rec[1]):'),
}


def load(out, name, old=None, new=None):
    target = out / name
    target.mkdir(parents=True, exist_ok=True)
    shutil.copy(SRC / "examples.json", target / "examples.json")
    text = (SRC / "check_examples.py").read_text(encoding="utf-8")
    if old is not None:
        if text.count(old) != 1:
            raise SystemExit("mutation %s no longer matches the model text" % name)
        text = text.replace(old, new)
    (target / "check_examples.py").write_text(text, encoding="utf-8", newline="\n")
    spec = importlib.util.spec_from_file_location("mutation_" + name.replace("-", "_"), target / "check_examples.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def transit_violation(module, faults, limit=300_000):
    """Depth-first search for a state whose fault-free continuation is not exactly-once or not quiescent."""
    model = module.Transit
    seen, stack = set(), [(model.normalize(model.initial(1)), 2, faults, ())]
    while stack:
        state, crashes, faults_left, path = stack.pop()
        if (state, crashes, faults_left) in seen:
            continue
        seen.add((state, crashes, faults_left))
        if len(seen) > limit:
            return None
        try:
            delivered, destroyed, rollback = model.delivered(model.drain(state))
        except AssertionError:
            return "stuck after " + " ".join(path)
        # Without a fault nothing may duplicate, roll back or vanish unaccounted. With one lost write the
        # documented residual is a duplicate that is audited as SOURCE_ROLLBACK, so only others count.
        unaccounted = delivered + destroyed < 1
        duplicate = delivered > 1 and (faults == 0 or not rollback)
        if unaccounted or duplicate or (faults == 0 and rollback):
            return "delivered %d, destroyed %d after %s" % (delivered, destroyed, " ".join(path))
        for _, new_state, c, f in model.successors(state, crashes, faults_left):
            stack.append((new_state, c, f, path + (_,)))
    return None


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    out = Path(sys.argv[1]).resolve()
    if SRC.parents[2] in out.parents or out == SRC.parents[2]:
        raise SystemExit("write the mutation copies outside the repository")
    failures = []
    control = load(out, "control")
    for faults in (0, 1):
        found = transit_violation(control, faults)
        print("control transit, %d lost write(s): %s" % (faults, found or "no violation"), flush=True)
        if found:
            failures.append("control")
    for name, (old, new, faults) in TRANSIT.items():
        found = transit_violation(load(out, "transit-" + name, old, new), faults)
        print("transit %-40s %s" % (name, ("CAUGHT: " + found[:90]) if found else "NOT CAUGHT"), flush=True)
        if not found:
            failures.append(name)
    for name, (old, new) in DELIVERY.items():
        module = load(out, "delivery-" + name, old, new)
        try:
            _, outcomes = module.Delivery.explore(True, 2)
            bad = sorted(k for k in outcomes if k[0] != 1 and not k[1])
        except AssertionError:
            bad = ["stuck"]
        print("delivery %-39s %s" % (name, ("CAUGHT: %s" % bad[:3]) if bad else "NOT CAUGHT"), flush=True)
        if not bad:
            failures.append(name)
    _, outcomes = control.Redirect.explore(False, 0)
    bad = sorted(k for k in outcomes if k[0] != 1)
    print("redirect %-39s %s" % ("without-retirement", ("CAUGHT: %s" % bad) if bad else "NOT CAUGHT"), flush=True)
    if not bad:
        failures.append("without-retirement")
    if failures:
        raise SystemExit("not caught: " + ", ".join(failures))
    print("all mutations caught", flush=True)


if __name__ == "__main__":
    main()
