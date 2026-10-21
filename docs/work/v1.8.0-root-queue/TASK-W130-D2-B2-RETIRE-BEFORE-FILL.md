# TASK W130: R52's D2 (b2), retire a landed rocket's transfer record before its first escrow fill

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md), [records/W126-DESIGN.md](records/W126-DESIGN.md)
(with Root decision R52 at its top), the review `D:/GitHub/ARCE-Task-Evidence/root-queue/W126-review/REVIEW.md`
(SHA-256 `af34236c…0d06`) with its probes, `records/W125.md` (the F1 fix this builds on, merged before you start),
[records/W98.md](records/W98.md), [records/W116.md](records/W116.md) and [records/W96-DESIGN.md](records/W96-DESIGN.md)
(the ordinary retirement proof), first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w130` (new; from the shared branch at the commit named in your prompt, which has W125 merged) |
| Branch | `codex/v1.8.0-w130` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W130.md` (new; the plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W130/` |

**Root note (2026-10-22).** W125 merged with W125F as `88bcebfc`. W125F found F3: an assembled rocket filled through
escrow keeps `ASSEMBLED` and every launch returns `INVALID_STATE` before the governed guard (Root decision R55 in
[TASK-W133](TASK-W133-F3-ESCROW-FILL-LAUNCH-STATE.md), which fixes it in parallel at the launch boundary). Do not fix F3
here. Before your final full GameTest and native runs, merge the shared branch head if it contains W133's merge; if it
does not yet, report the launch codes you observe as they are, keep D2-3's launch outcome separate from F3, and Root
reruns the launch outcomes after both merge.

## Root decision R52 (2026-10-21), on W126's note and its review

- **D2-1, (b2) adopted** as this persistence slice: before the first escrow fill on a landed rocket that still has its
  COMMITTED transfer record T, retire T through W98's on-disk proof applied at the destination (the landed rocket's own
  slot proven, the old launch site's origin slot shown empty), so the fill carries no transfer guard and leaves no floor;
  later starts never need the old site and the governed launch is allowed. With these corrections from the review:
  - **W126-R1:** the retirement places the existing ordinary flight ticket on the old launch site's origin column (and
    refreshes the destination's), only on demand, when a loader attempts a fill on a landed rocket that still has T; at
    most one attempt per rocket per 200 ticks and a small fixed number of rockets per tick (state the number); released
    on success or after the existing 400-tick ticket lifetime; never at startup on its own. A loader's retry must not
    reset W98's backoff.
  - **W126-R2:** each attempt scans both ends for a stale copy and discards it under the existing rules before the
    proof, so a copy that loads after the retirement started cannot fail the proof forever.
  - **W126-R3:** W125's named-fill fixtures fill through production admission, which (b2) makes refuse a rocket with T;
    add a gated, test-only seam (inert unless the release-test hook and its case are set) that still creates a named fill,
    so W125's F1 coverage stays meaningful, and say which W125 cases use it.
  - **W126-R6:** after retirement the landed rocket's pad is no longer reserved through the journal; state how a later
    flight's pad choice treats it, and test that a flight does not land on it.
  - **W126-R9:** logs and oracles must not assume `FLOOR_PROOF_REQUIRED` is the only fence reason.
- **D2-2:** if fuel admission is ever enabled before this merges, (d) applies behind the production switch (admit only
  fills on rockets with no transfer record), with its own clear refusal reason.
- **D2-3:** for each named cut and restart, report three outcomes separately: resources conserved, refuelling restored,
  and launch allowed or refused.
- **D2-4:** unsupported entity storage holds the retirement instead of removing T without proof.
- **D2-5:** cargo follows under D4's own review, not here.

This is a persistence slice: commit a one-page plan at the top of the record before code; an independent read-only review
follows before merge.

## Tests and cuts

Unit tests of the on-demand ticket step and its rate limits, the backoff kept, the discard scan, and the gated seam inert
with the gate off. GameTests: a same-Level and a cross-Level landed rocket with T, refuelled through production
admission after (b2) retires T, then launched; a stale copy at the old site that loads mid-retirement; the pad after
retirement. Native, two restarts each: the review's cut list (section on cuts) including the four missing cases, and
W125's named cuts through the gated seam, each reporting D2-3's three outcomes. The existing fence, recovery,
retirement, scheduling and loader tests stay unchanged and pass.

No saved format, schema, hash, bound, dictionary, proof, lease, budget or timeout changes; no existing assertion is
relaxed; `RocketTransferService` (796 lines) must not grow past 800. A new WARN or ERROR that a GameTest drives needs its
own exact manifest entry. Stop and report if any of these seems needed.

## Verification

Each new or changed test class alone, then the whole unit suite; one full GameTest with the unchanged log checker on
committed source; then the native cuts, all under the heavy-JVM lock, releasing it and deleting every server copy and
`build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). Check C: and D: above
10 GiB and that 6 GiB of memory is free before each Gradle run or native start.

## Not in this task

Cargo (D4), production admission, ADRs, the risk register, status files and the ledger (Root).
