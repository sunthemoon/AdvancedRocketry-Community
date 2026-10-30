# TASK W216: the ground-survey START whole-root preflight near the 16 MiB bound

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w216` (new; from the shared head named in your prompt, which contains W213's record) |
| Branch | `codex/v1.8.0-w216` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W216.md` (new; the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W216/` |
| Finding | `docs/work/v1.8.0-root-queue/records/W213.md` ("Decision" and "Measurements") and its evidence and timing agent in `D:/GitHub/ARCE-Task-Evidence/root-queue/W213/` |
| Code | under `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/satellite/`: `mission/GroundSurveys.java` (`preflight`, lines 346 to 384; `reservationsBoundRoot`, from line 399), `mission/RecordSizer.java`, `persistence/CodecRecordSizer.java` (`groundMutationFits`), `StorageBudget` |
| Test | `src/test/java/.../satellite/persistence/GroundSurveyScaleTest.java`, `startOnARootNearTheBoundStaysWithinTheSpikeBudget` (unchanged 500 ms limit) |
| Background | `records/W168.md` (the ground survey's reservation proof and when it skips the whole-root encode), `records/W210.md` (exact cached sizes for immutable residents), [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md); R-030 in `docs/11-RISK-REGISTER.md` |

**Root decision R191.** W213 measured START on a root near the bound: the reservation proof cannot bound the root
there (worst-case reservations exceed the limit), so `preflight` falls back to `groundMutationFits`, which encodes the
whole registry root (101.6 ms idle), writes and compresses it for validation (99.4 ms) and inflates and reads it back
(64.3 ms): complete START about 301 ms idle, 436 ms under load and 571 ms on a slow hosted runner, against docs/17's
500 ms spike budget, allocating about 164 MiB per call. This is a production cost in a persistence path.

## What to do

1. **Design note first, committed before code** (one page): exactly what `groundMutationFits` proves (the encoded byte
   size, the compressed size, the decoded equality, or more) and which of those a START actually needs; a remedy that
   gives the same answer with bounded server-thread work near the bound, for example exact sizes kept per immutable
   record (as W210 did) so that only the changed records are encoded, or a proof that the validation round trip adds
   nothing START needs; why every refusal (`STORAGE_BUDGET`, `CAPACITY_REACHED`, `INVARIANT_FAILED`) and every
   accepted root stays exactly the same, including after a load, a migration, a failed sizing and at, just under and
   just over the bound; memory cost. No format, schema, budget or bound change.
2. **Fix it** with unit tests that fail first: the remedy's answer equals the whole-root preflight's for fresh and
   reloaded roots, after each mutation path (start, advance, completion, removal, account creation), at the byte edges
   and the section budgets, and for a failed sizing; and an allocation or work test that the near-bound START no
   longer encodes the whole root.
3. **Measure before and after** with W213's method (its timing agent and parallel load), idle and under load: START's
   per-run median of seven, its stages, CPU, allocation and GC; then `GroundSurveyScaleTest` repeated (state the count
   and pass rate).

## Verification

The new tests alone, failing first; the satellite persistence and mission packages; the whole unit suite; the
satellite GameTest batches three times under the heavy-JVM lock. Root's hosted run on the pushed branch stands in for a
complete local GameTest. An independent read-only review follows before merge.

## Not in this task

The 500 ms budget and the test's limit, warm-ups or sample count, the 16 MiB root bound, section budgets, the saved
format, other satellite costs, ADRs, status files and the ledger.
