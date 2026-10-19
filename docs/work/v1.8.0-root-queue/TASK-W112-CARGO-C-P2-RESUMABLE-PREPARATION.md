# TASK W112: cargo leaf C-P, part 2: the resumable preparation, its lock and the permit-gated publication

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
(frozen at `49f7e08b`; sections 2.3 and 2.4 and the C-P row of section 6), ADR-065 section 4.1.1 ("Whole-snapshot work
admission") and [records/W106.md](records/W106.md) (C-P's first part, especially "Blocking preparation finding") first.
You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w112` (new; from the shared branch at the commit named in your prompt, which has W106 merged as `3075a240`) |
| Branch | `codex/v1.8.0-w112` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W112.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W112/` |

This changes how rocket snapshots are constructed and hashed, so it is a persistence slice (AGENTS.md section 13):
commit a one-page plan first, and Root arranges an independent read-only review before merge.

## Root decision (2026-10-20): the snapshot hooks are authorized

W106 found that `RocketStructureSnapshot`'s private constructor hashes synchronously in `RocketSnapshotHasher`, and a
codec-valid 2,048-block fixture feeds about 18 MB into its two hashes, above the 16 MiB per-tick budget. ADR-065
section 4.1.1 already decides this case: when one reconstruction cannot fit a tick, the preparer uses a bounded
resumable preparation cursor that charges actual bytes and visits on each tick, and skipping a hash or payload check to
fit is forbidden. Root therefore authorizes the smallest hooks in `RocketStructureSnapshot`, `RocketSnapshotHasher` and
the snapshot codec that this needs, for example an incremental hasher that can stop and resume between block records,
and a construction path that accepts digests the resumable preparer computed over the same canonical input. Every
content and structure hash must stay **byte-identical** to today's for every input (C-I pinned prior values; keep those
tests and add pins for the resumed path). No saved format, schema, hash definition or bound changes.

## What to do

1. **The plan** (`records/W112-PLAN.md`, one page, committed before Java): the hooks and why they are the smallest; how
   the cursor holds its state in fixed memory; where bytes and visits are charged; how a resumed hash is proved equal to
   a one-pass hash; the lock and publication order.
2. **The resumable preparation** of section 2.3: the transient cursor (rocket UUID, snapshot ID, before content hash,
   block index, bytes and visits charged so far), in RAM only and discarded on restart, unload, cancellation or server
   stop; at most one whole-snapshot reconstruction per server tick, 16 MiB of charged bytes and 16,384 block visits,
   counting every payload copy, native encode or decode and hashing pass (ADR-065 section 4.1.1); fair deferral, so a
   maximum-size rocket is not starved by small requests.
3. **The transient preparation lock**: while a cursor exists, `RocketRuntime.resourceMutationAllowed`, launch and
   disassembly refuse; the prior snapshot and hash stay authoritative.
4. **Permit-gated publication** (section 3.2 item 5's one-use server-thread permit, for the rocket side): publication
   recomputes or revalidates the after snapshot against the frozen digest and publishes the new snapshot, resource state,
   revision and hash together, through a new permit-gated method, never a public setter.
5. **Wire W106's `EscrowCargoEligibility`** before sequence allocation, queueing, reservation or mutation, and again at
   final capture if an input changed, as section 2.3 states.
6. Nothing in production admits cargo yet: the fair queue and the per-kind gate are C-E's. Tests drive the preparation
   and publication through fixtures.

## Tests

A0: resumed and one-pass hashes equal for many split points, including the 2,048-block fixture and W106's two
fixtures; the per-tick charges never exceed 16 MiB or 16,384 visits; the cursor's memory bound; discard on each event;
publication refuses a changed before state, a stale permit and a second use. A1 (GameTests): the lock refuses launch,
disassembly and resource mutation while a cursor exists; a fixture publication through the permit; a maximum-size rocket
among small requests completes. Each new class alone, the whole unit suite, and one full GameTest with the unchanged log
checker under the heavy-JVM lock, on committed source.

## Write scope and not in this task

The snapshot model, hasher and codec hooks above; new cargo preparation, lock and publication classes; the smallest
runtime and escrow core hooks they need (`RocketRuntime`, the rocket entity's permit-gated method, the escrow
coordinator's capture path); tests; the plan and the record. No change to a hash value, a saved format, a bound, a
proof, a lease or the replay exclusion, and no relaxed assertion, limit or timeout; stop and report if one seems
needed. Take the heavy-JVM lock for GameTests and release it at once; check C: and D: above 10 GiB first; run long
heavy jobs as detached processes. Not in this task: devices (C-A), the fair queue and gate (C-E), data and art (C-D),
S2 cuts, ADRs, the risk register, status files and the ledger (Root).
