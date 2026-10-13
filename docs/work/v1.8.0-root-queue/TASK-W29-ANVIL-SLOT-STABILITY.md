# TASK W29: per-slot stability in the escrow Anvil reader (the native E2 `BARRIER_UNSTABLE`)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w29-anvil-slot` |
| Branch | `codex/v1.8.0-w29-anvil-slot` (local only; Root merges) |
| Kind | Escrow follow-up (the open item in the ADR-065 W11b1 note) |
| Record | `docs/work/v1.8.0-root-queue/records/W29.md` |

## Problem

`resourceescrow/AnvilRecordReader.read` makes three checks between its first and second read:
- the endpoint's header slot is equal;
- the payload bytes are equal;
- the **whole region file's** size and modification time are equal (`same(before, attributes(path))`).

A vanilla save of any other chunk in the same `.mca` file during the read changes the file's
attributes, and the readback then fails with "Anvil record changed during readback". W11b1F's
first complete native selection crashed E2 restart 1 with `BARRIER_UNSTABLE` this way: startup's
eager saves wrote neighbouring chunks. In production this is a retryable stall, so it is not a
safety bug, but it makes proofs fail spuriously and native runs flaky.

The design (`records/W11A-DESIGN.md` round 2, and the round-2 review's C1) asks for stability of
the endpoint's **slot**: the header before and after, and the record bytes. It does not ask for
stability of the whole file.

## Root's decision

1. **Narrow the check to the slot.**
   - Replace the whole-file attribute comparison with these checks:
     - the endpoint's 4-byte location and 4-byte timestamp header entries are equal before and
       after the read;
     - the payload bytes are equal;
     - the file has not shrunk below the slot's end.
   - Keep every other check: external records, bounds, the bounded NBT envelope, trailing
     bytes.
   - Apply the same rule to the external-record probe.
2. **Prove that no false confirmation becomes possible.** Using the vanilla `RegionFile` write
   order (new sectors, then the header, then freeing the old sectors; cite it from the mapped
   jar with `javap`), argue each interleaving below. Add a unit test for each, using a real
   region file written through vanilla `RegionFile`:
   - a concurrent write to **another** chunk in the same file during the read: now stable;
   - a concurrent rewrite of **this** chunk to new sectors: refused, or the decoded root is the
     consistent old record, which the barrier's validator then rejects because the marker or
     frontier is missing;
   - this chunk's old sectors freed and reused by another chunk between the two reads:
     refused;
   - the file truncated: refused.
3. Keep the barrier's store → force → readback order and the validator unchanged.
4. **Native evidence.** On the final source, re-run the native escrow selection twice in a row
   through the existing harness (the K, C, E and G cases), pinned to the source and jar.
   Report any `BARRIER_UNSTABLE`.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. Report the totals.
- The two native selections.

## Not in this task

- Other escrow behaviour.
- The W11b2 scope; W11b2 merges this later.
- Status files and the ledger.
