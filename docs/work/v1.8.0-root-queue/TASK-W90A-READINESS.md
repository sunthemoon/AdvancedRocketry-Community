# TASK W90a: governed flight readiness (the checked column set, margin tickets and admission bounds)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w90a` (from the shared branch, which has W11b2, W92 and W95 merged) |
| Branch | `codex/v1.8.0-w90a` (local commits only; Root merges) |
| Design (frozen) | [records/W90-DESIGN.md](records/W90-DESIGN.md) revision 2, with Root decisions R21 (`D:/GitHub/ARCE-Task-Evidence/root-queue/W90/ROOT-DECISION-R21.md`, SHA-256 `1322ab425a6aa87397f42abf49aac11f444beba6c89c38f5e61ddd49db1b91ee`) and R22 (`D:/GitHub/ARCE-Task-Evidence/root-queue/W90/ROOT-DECISION-R22.md`, SHA-256 `a10c8cd36ead8d03e516bc70700c3417b05f5ec906356cf1962e99d4e52006f2`); R22's amendments override revision 2 where they differ |
| Leaf | R22's "Leaves": W90a = R21 items 1, 2, 3, 5 and 6 and R22 amendment 5 |
| Record | `docs/work/v1.8.0-root-queue/records/W90A.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W90A/` |

W91E (P1-F3) is changing `RocketTransferService` and the transfer journal in parallel; keep your changes to those files
small and self-contained so that Root can merge both.

## What to do

1. **Recheck the design against the shared branch** (revision 2 read W11b2 at `379525ea`; since then W11b2's
   continuations 53 and 54, W92 and W95 merged). Note moved sites; stop and report if a difference changes the design.
2. **The checked column set** (R21 item 2): one server-free, overflow-checked helper used by readiness, every governed
   pad-admission path, F1's source check, the ticket refresh and the release; the origin column must be inside it; at
   most `MAX_LANDING_CHUNKS` columns.
3. **Margin tickets for governed records** (R21 item 1): one `arce_rocket_flight` ticket per checked column per end,
   added only by the checked ACTIVE governed dispatch and never while held; F8 releases the same set in each end's
   Level, including the origin ticket; legacy flights keep their origin tickets.
4. **The `DISCOVERY_MARGIN` executor hold** for a recorded end whose set is invalid: one WARN per (flight, reason), no
   refresh, offline repair (R21 item 2, R17 item 6's hold style).
5. **Admission bounds for governed launches only** (R21 item 3): surface, station and named-pad selection and F1's
   source use the helper before terrain access or F1's source store; a destination that fails gets `chunk_budget`; a
   source that fails gets `LAUNCH_CHUNK_BUDGET`; an unready source gets `LAUNCH_AREA_INACTIVE` (R22's answer 4), with
   English and Chinese text, carried through `RocketFlightRequestResult`. Legacy launches keep today's selection.
6. **The wait logs and inspect field** (R21 item 6): one INFO after 400 ticks continuously not ready and one when ready
   again, distinguishing column loading from census conflicts and executor or storage holds; a read-only
   `/arce rocket inspect` field over the bounded recorded set only.
7. **Tests** as revision 2 section 10 lists for these items (the column set at one chunk, across one border and at the
   bound; release symmetry over two Levels; a GameTest with border-crossing margins and no player near the
   destination that lands, with a test-only warm-up held until F2 since the real warm-up is W90b; the hold; the admission
   refusals; the pending-entity-load and demoted-chunk readiness cases of R22 amendment 5). Run each new class alone,
   the whole unit suite, `runData` with a clean diff, and one full GameTest with the unchanged log checker (add an
   expectation only for a line your new code drives). Say if you add a GameTest batch (about 379 batch names; about 384
   reshuffles the run order).

## Write scope and boundaries

`rocket/server/RocketTransferEntities`, `RocketGovernedSourceDispatcher`, `RocketGovernedCleanupDispatcher`, the pad
selectors and staging, `RocketTransferService` (admission inputs only), `RocketFlightRequestCode` and the request result,
the inspect command, the language providers, new tests, and the record. No saved-format change, no change to an
acknowledgement order, a proof, a lease or the replay exclusion, no destination warm-up (W90b), and no relaxed
assertion, limit or timeout. Stop and report if one seems needed.

## Commands

As WORKER-RULES says. Other workers (W91E, W94B, W92F) also run tests; take the heavy-JVM lock for every GameTest,
`runData` and native run and release it between runs when another worker waits. When your heavy runs end, remove your
own lock and your `build/gametest/world` with a plain `rm -rf` after checking that the lock's `owner.txt` names you; if
the tool refuses, write the paths in your record and final answer at once. Check that C: and D: have more than 10 GiB
free before each heavy start.

## Not in this task

W90b (the destination warm-up), W91, W92F, W94B, production admission, ADRs, the risk register, status files and the
ledger (Root).
