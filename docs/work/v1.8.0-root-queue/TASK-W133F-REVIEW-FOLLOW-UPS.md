# TASK W133F: W133's review follow-ups and the native launch after D2, under Root decision R58

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W133](TASK-W133-F3-ESCROW-FILL-LAUNCH-STATE.md),
[records/W133.md](records/W133.md), W130's record (`records/W130.md` once W130 merges) and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W133-review/REVIEW.md` (SHA-256 `7080e9c3…c5de496`) with its probes, and
W130-R2 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W130-review/REVIEW.md` first. You are a new session. W130 (D2,
retire the transfer record before the first fill) merged as `3bce8373`.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w133f` (new; from the shared branch at the commit named in your prompt, which has W133 and W130 merged) |
| Branch | `codex/v1.8.0-w133f` (local commits only; Root merges) |
| Record | append a "W133F" section to `docs/work/v1.8.0-root-queue/records/W133.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W133F/` (keep W133's evidence unchanged) |

## Root decision R58 (2026-10-22)

W133-review approves with changes; W133 merged as `695f76b0`. Root accepts W133's three behaviour changes: an
`ASSEMBLED` rocket whose propulsion needs no fuel is launch-ready (no production path creates one, and it matches
`withFuel`'s own promotion); an empty `LANDED` rocket's console quote shows `INVALID_STATE`; the governed launch refuses
an empty `LANDED` source with another exception type (unreachable). The review found that W133's native runs never
reached the governed promotion: the six refusals carrying fuel stop in `begin`'s preconditions, before
`EscrowGovernedLaunch.prepare`, and `begin` returns the same `TRANSFER_JOURNAL_BLOCKED` when `prepare` throws. So:

1. **W133-R1, the native launch after D2** (corrected by W130-review's W130-R2, Root decision R59). W125F's seven
   follow-up cases are W130's named-fill seam cases: they keep their transfer record on purpose, so with W130 and W133
   merged they still refuse with `TRANSFER_JOURNAL_BLOCKED` and never reach `prepare`. So:
   - rerun the seven (`EscrowLoaderFollowUpProbe`, two restarts each) as **controls** that expect that refusal;
   - add native cases that do reach `prepare`: an `ASSEMBLED` rocket with no transfer record filled through escrow with
     a NONE guard, and a landed rocket whose record W130's retirement removes before its first fill, each then launched
     through the governed path to COUNTDOWN, two restarts each;
   - rerun W130's twelve native retirement cases on the combined tree and expect W130's results.
   For every launch request, record which check answered: a precondition in `begin`, an exception from `prepare` (with
   its message), or success (COUNTDOWN). Do this in the probe or through a release-test hook that production never
   enables; production refusal codes and behaviour stay unchanged, and a new WARN or ERROR that a GameTest drives needs
   its own exact manifest entry. Report the actual codes; do not change the oracle's expectations to match. If a case
   refuses unexpectedly, say which check refused and why.
2. **W133-R2:** split `rocketThatRequiresNoFuelBehavesAsBefore` into a test of what is unchanged and a test named for
   R58's accepted change (the waived `ASSEMBLED` rocket is launch-ready), keeping every assertion.
3. **W133-R5:** a test that saves and reloads during a governed countdown that started from an `ASSEMBLED` rocket (the
   review's probe P2 is the model; a GameTest restart is better if the probe fits one).
4. **W133-R3 and R4:** correct the record in your W133F section: the `acceptsFuel()` callers and frozen-envelope readers
   the inventory omitted, and the F1b attribution of the eight fuel-0 refusals stated as an inference unless your rerun
   observes it.

No saved format, schema, hash, envelope, network field, bound, budget or timeout changes; no existing assertion is
relaxed. Stop and report if any of these seems needed.

## Verification

Each new or changed test class alone, then the whole unit suite; one full GameTest with the unchanged log checker (plus
any entry of yours); then the seven native cases, all on committed source under the heavy-JVM lock with the atomic
pattern and the hand-off rule in WORKER-RULES, releasing it and deleting every server copy and `build/gametest/world` in
the `finally` of the same detached script that took it. Check C: and D: above 10 GiB and that 6 GiB of memory is free
before each Gradle run or native start.

## Not in this task

Cargo's F1 extension, enabling admission, ADRs, the risk register, status files and the ledger (Root).
