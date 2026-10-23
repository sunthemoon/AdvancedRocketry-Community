# TASK W148: P1-N3 leaf N3-B (I6 and I7), the Design M amendment (design only)

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md), [records/W96-DESIGN.md](records/W96-DESIGN.md) (the P1-N3
design, its I1 to I7 inventory and Design M, frozen with Root decision R34's amendments), [records/W98.md](records/W98.md)
(N3-A1, which closed I1 to I3 with the pre-retirement proof) and [records/W116.md](records/W116.md) (N3-A2), and the
R-035 entry in [docs/11-RISK-REGISTER.md](../../11-RISK-REGISTER.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w148` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w148` (local commits only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W148-DESIGN.md` (new; the amendment) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W148/` (probes or source excerpts, if any) |

P1-N3 is a production defect class in ordinary flights in every world: a hard kill at the wrong moment can leave a rocket
in a state nothing recovers. N3-A1 and N3-A2 closed I1 to I3 with the pre-retirement proof. N3-B, I6 and I7, remains:
W96's design says I6 has the same pre-retirement gap plus a second, independent terrain transaction, and I7 loses an old
end's exclusion authority, and recommends the same pre-retirement proof for I6a, I6b and I7 subject to an amendment of
Design M.

## What to write (no code)

1. **Restate I6 and I7 on today's code** (the shared head has W98, W116, W130 and the escrow and cargo slices): the exact
   windows (which write, which crash point, which restart) and what is lost, with source references; say whether any
   later slice already closed part of either.
2. **The amendment to Design M**: how the pre-retirement proof extends to I6a, I6b and I7; the order of writes and proofs;
   how I6's second terrain transaction is made safe; how I7 keeps the old end's exclusion authority until it is proven
   gone; what each refusal or hold looks like to a player and an operator, its scope, duration, log lines and recovery
   (AGENTS section 3.3, for Root's risk-register entry).
3. **Constraints**: no saved format, schema, bound, lease, budget or timeout change unless unavoidable, in which case
   state it and why; per-tick work bounded; the W123 acknowledgement rules and W98's census and backoff kept.
4. **Tests that would prove it**: unit, GameTest and native cut cases (two restarts each) for every window, including the
   ones the original W91K cuts missed.
5. **Proposed implementation slices** and what each must not do.

Keep it to the length the content needs; reference sections instead of repeating them. An independent read-only review
by a fresh Codex session follows; Root then decides.

## Verification

Read-only source inspection and, if useful, probe tests you keep in the evidence folder; no production change. The
repository validator on your branch.

## Not in this task

Code, W98-R1 (governed admission), ADR and risk-register edits, status files and the ledger (Root).
