# TASK W123: W27c3, the rocket transaction journal's checked policy and acknowledgement

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w123` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w123` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W123.md` (new; the one-page design at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W123/` |
| Contract | [records/W27A-DESIGN.md](records/W27A-DESIGN.md), frozen: section 8's "Transaction journal" bullet, the W27c3 row of section 9 and cut B14 (section 9's S2 table) |
| Builds on | W11b2's checked journal policy and exact acknowledgement (`RocketJournalWriter`, the checked saved-data policy, `RocketJournalDigest`, the shared streaming digest) and the W27c2 registry proof ([records/W119.md](records/W119.md), merged `078c7be0`) as a sibling example |

## What W27c3 is

`ROCKET_TRANSACTIONS`, the rocket assembly transaction journal, adopts W11b2's checked policy and acknowledgement
(sequence, root digest, record digests), which W11b2 did not adopt for it. W27d's deployment step D0 will use it: if the
acknowledgement is not current, D0 requests one checked write and keeps the rocket waiting, and a failure rejects all
bays of that rocket with `ASSEMBLY_JOURNAL_UNPROVED`; if a record for the rocket remains, D0 refuses with
`ASSEMBLY_JOURNAL_PENDING` until legitimate transaction recovery or removal is durably proved (B14). W27c3 provides the
checked write, the readback and the acknowledgement query and refusal; the D0 caller is W27d's.

This is a persistence slice (AGENTS.md section 13): commit a one-page design at the top of the record before any Java
change, naming the classes, the write path (the existing writer generalized or reused), what is acknowledged, the
behaviour on a definite failure and on an UNKNOWN outcome, the effect on existing assembly and disassembly paths, and the
saved format (no change is the strong preference); Root has an independent read-only review done before merge.

## Tests

A1: a checked write, its readback and its refusal; B14 at unit level (a current acknowledgement, a stale one that asks
for one checked write, a failed write, an UNKNOWN outcome, and a remaining record for the rocket refused as pending even
after a successful write of it). The existing transaction journal, assembly and disassembly tests stay unchanged and
pass; if one must change, stop and report. No saved format, schema, bound, budget or timeout changes; new classes stay
under 500 lines; `RocketTransferService` (796 lines) must not grow. A new WARN or ERROR that a GameTest drives needs its
own exact manifest entry.

## Verification

Each new or changed test class alone, then the whole unit suite. Then one full GameTest with the unchanged log checker
on committed source, under the heavy-JVM lock, releasing it and deleting `build/gametest/world` in the `finally` of the
same detached script that took it (WORKER-RULES). If the checker refuses only the host-overload warning count, record
the count and the number of agent sessions running. Check C: and D: above 10 GiB and that 6 GiB of memory is free
before each Gradle run.

## Not in this task

W27d (the deployment protocol and the D0 caller), W27e, ADRs, the risk register, status files and the ledger (Root).
