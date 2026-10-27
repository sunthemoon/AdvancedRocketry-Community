# TASK W168: the ground survey's whole-root preflight (W129 Q6)

Root: Claude. Date: 2026-10-27. Read [WORKER-RULES](WORKER-RULES.md), section 2.4 of
[records/W129-DESIGN.md](records/W129-DESIGN.md), item 13 of
[ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md) and docs/17 section 4 first. You are a
new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w168` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w168` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W168.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W168/` |

W129 moved the satellite registry's writes off the server thread (merged at `a03fdd048`), but one encoding stays on
it. `GroundSurveys` (around lines 367-397) encodes and round-trips the whole registry root on the server thread in three
cases:
- every ground-survey START;
- every research-account creation through a ground mutation;
- every change while the section reservations cannot bound the root (a restored root above its section budgets).

At the 16 MiB bound, W128 measured a whole-root encode at hundreds of milliseconds, far over docs/17's P95 of 50 ms
and P99 of 100 ms for one tick of server-thread work.

## What to do

1. **Measure first.** Add a standalone benchmark under `src/test`, following the pattern of W128's
   `SatelliteRegistryBenchmark`. Run it with Xmx4G under the heavy-JVM lock, at least 50 samples each, reporting P50,
   P95, P99 and the maximum:
   - a START on a root at the byte bound;
   - an account creation on that root;
   - one ordinary change on a restored root whose reservations exceed the bound.
2. **A fast path that changes no answer.** For START and account creation, first try the reservation proof the
   other changes already use, with the new record's own reservation and, if a new account is created,
   `RecordSizer.ACCOUNT_BYTES` added. Encode only when that proof fails.
   - Because the reservations bound the encoding, this changes no result: a change refused today is still refused,
     and one accepted today is still accepted.
   - Prove that with tests at the boundary: reservations exactly at the bound, one byte over, and a restored
     over-budget root.
3. **Measure again**, and report how often the slow path can still run.
   - **If it still misses docs/17 in some case,** stop and report options with numbers. Do not choose one. Examples:
     refuse with STORAGE_BUDGET whenever the reservations cannot prove the fit (a behaviour change, which needs Root's
     decision and a risk note), or a per-tick allowance for preflights.
   - Do not reduce any bound.

## Rules and verification

Nothing may change:
- the registry's saved format, schema or bounds;
- any section budget, quota, timeout or protocol;
- which requests are refused, except as step 3 reports for Root's decision.

Verification:
- the new tests alone, failing first where they pin a fix;
- the `satellite` packages, then the whole unit suite;
- one complete GameTest with the unchanged log checker, under the lock.

Check C: and D: above 10 GiB and 6 GiB of free memory before each heavy run. A fresh read-only review follows
before merge.

## Continuation under Root decision R129 (2026-10-28)

W168 delivered `00409f25c`. Its review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W168-review/REVIEW.md` (SHA-256
`7df7009b…894e`), returned BLOCK. Read the whole review and its probe first.

- **W168-R1 (High).** `CodecRecordSizer.missionBytes` reserves less than a valid restored mission can encode. A DATA
  mission quarantined from CLAIM_PENDING_DISCOVERY, with `resolved_at` and the longest legal reason, encodes 37 bytes
  over its reservation. Its probe builds a loadable root on which W168's fast path admits a START that the baseline
  refuses, leaving the root 961 bytes over the cap.
- The undercount predates W168. The older fast path for ordinary changes trusts the same reservations.

A fresh session continues on `codex/v1.8.0-w168` and appends a "Continuation (R129)" section to the record:

1. **A one-page note first.** For each record kind the reservations cover (satellites, missions, instances, ground
   surveys, accounts), name the largest encoding any supported state can reach. Include restored records and every
   transition, such as quarantine from each earlier status with its timestamps and the longest reason. Then compare
   that with what the sizer reserves today.
2. **Fix the sizer** so each reservation covers its record's largest reachable encoding. Add real-codec regressions:
   - the review's probe, which must now refuse exactly as the baseline does, leaving the root, accounts,
     reservations, clock and mutation sequence unchanged;
   - a worst-state case for each kind.
3. **Report the capacity effect.** Larger reservations can admit fewer records within a section budget. Report how
   many missions, instances and jobs fit before and after, and what a restored root at today's budgets does after the
   change. Nothing may refuse to load. If any already-admitted record could be refused, stop and report.
4. **Keep the whole-root fallback** wherever the reservation proof cannot be shown sound.
5. **Rerun the benchmark** on the fixed code.

Verification: the new tests alone, failing first; the `satellite` packages and the whole unit suite. If the heavy-JVM
lock is free within an hour, run one complete GameTest with the unchanged checker; otherwise report it, and the hosted
regression after merge stands in. A fresh read-only review follows.

## Continuation under Root decision R132 (2026-10-28)

The R129 continuation (`ac6af2e5b`) closed W168-R1 for missions. Its re-review,
`D:/GitHub/ARCE-Task-Evidence/root-queue/W168-rereview/REVIEW.md` (SHA-256 `b112ec0a…5886`), returned BLOCK. Read it
and its `probes/DecoderReviewTest.java` first.

- **W168-R3 (High).** The decoder accepts an unqualified name of up to 128 characters, which normalization lengthens
  (`minecraft:` is added). A raw GAS mission that decodes at 4,090 bytes needs 4,280 bytes once normalized. Its
  encoder correctly throws at the unchanged 4,096-byte record limit. `CodecRecordSizer.bounded` turns that exception
  into a 4,096-byte reservation, and the fast path trusts it. On a loaded root, W168 admits a START, an account
  creation and a final analysis that the whole-root check refuses. The ordinary-change fast path, which is older,
  does the same.
- **W168-R4 (Low).** The capacity wording ignores the 4,096-byte cap. The increase is 0 to 40 bytes, and capacity is
  `min(3072, floor(6291456 / reservation))` for a population of one mission shape.

A fresh session continues on `codex/v1.8.0-w168` and appends a "Continuation (R132)" section to the record:

1. **A failed sizing never proves a fit.**
   - Distinguish a reservation proven from the record's canonical encoding from one capped after an encoder failure.
   - While any record in the root has an unproven reservation, every ground change takes the whole-root check: START,
     account creation, final analysis, and the older ordinary-change path. With no such record, the fast path stays.
   - The whole-root check and every refusal must stay exactly as they are.
2. **Regressions on raw saved data.**
   - The review's decoder probes, which must now answer exactly as the baseline.
   - A record at the normalization boundary, and records whose valid reservation is capped at exactly 4,096.
   - Refusals must leave nothing changed.
3. **W168-R4:** correct the capacity text in the record, and add saturation cases to `ReservationCoverageTest`.
4. **Do not change the decoder or refuse any root at load.** That loaded roots can be unencodable is older than W168,
   and it is W173's subject.

Verification: the new tests alone, failing first; the `satellite` packages; the whole unit suite. No GameTest is needed
unless a production path outside `satellite` changes. A fresh read-only review follows.

## Not in this task

The registry writer (W129), the bay slices, ADRs, the risk register, status files and the ledger (Root).
