# TASK W125F: W125's review follow-ups (W125-R1, R2) under Root decision R54

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W125](TASK-W125-F1-NAMED-FILL-FIX.md),
W125's record (`docs/work/v1.8.0-root-queue/records/W125.md` on your branch), R52 at the top of
[records/W126-DESIGN.md](records/W126-DESIGN.md) (D2-3's three outcomes) and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W125-review/REVIEW.md` (SHA-256 `50395a5b…c1d23f`) with its probes, first. You
are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w125`, branch `codex/v1.8.0-w125` at `23385aee`; continue on that branch |
| Record | append a "W125F" section to `docs/work/v1.8.0-root-queue/records/W125.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W125F/` (keep W125's sealed bundle unchanged) |

## Root decision R54 (2026-10-22)

1. **First, merge the shared head into your branch** (`git merge` the shared commit named in your prompt, no
   fast-forward), resolving any conflict so that both C-E2's cargo reconciliation and W125's fuel changes are kept, and
   commit it. W125-R2 exists only in the combined code.
2. **W125-R2:** gate W125's relocation lookup with `EscrowLoaderEndpoint.is(endpoint)` instead of the shared `loader`
   flag (which C-E2 widened to cargo devices), so a valid cargo device never consults the fuel-loader index and is never
   marked `RELOCATED_ENDPOINT` by it; keep C-E2's cargo observation and reconstruction paths; a missing fuel endpoint
   keeps W125's lookup. Add a regression on the merged code for both. Do not extend the fuel self-guard to cargo (R48's
   D4 stays separate).
3. **W125-R1:** for each named DISTINCT and COALESCED variant, the E5 case and the cross-Level K2 and K9 cases, record on
   each restart three separate outcomes: (1) retirement and conservation, (2) the actual result of admitting a second
   fill, and (3) the observed result code of a launch request. Rename the oracle's `USABLE` and "next-launch authority"
   wording to what is observed. The launch refusal (the governed no-transfer guard) is a known limitation that W130 (D2)
   addresses, reported apart from conservation and from F1b, never as a pass. Run the cases natively, two restarts each,
   as a supplementary evidence folder; correct the record; do not overwrite W125's sealed bundle.

No saved format, schema, hash, bound, budget or timeout changes; no existing assertion is relaxed. Stop and report if any
seems needed.

## Verification

Each changed test class alone, then the whole unit suite on the merged branch; one full GameTest with the unchanged log
checker; then the native cases above, all under the heavy-JVM lock, releasing it and deleting every server copy and
`build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). If the checker refuses
only the host-overload warning count, record the count and the number of agent sessions running. Check C: and D: above
10 GiB and that 6 GiB of memory is free before each Gradle run or native start.

## Not in this task

D2 (W130), cargo's F1 extension, production admission, ADRs, the risk register, status files and the ledger (Root).
