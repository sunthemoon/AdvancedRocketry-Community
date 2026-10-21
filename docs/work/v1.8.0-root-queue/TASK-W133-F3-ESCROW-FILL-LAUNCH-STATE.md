# TASK W133: F3, an assembled rocket filled through escrow stays ASSEMBLED and cannot launch

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), the W125F section of
[records/W125.md](records/W125.md) ("Additional finding") and this TASK first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w133` (new; from the shared branch at the commit named in your prompt, which has W125 merged) |
| Branch | `codex/v1.8.0-w133` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W133.md` (new; a short plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W133/` |

## The defect (F3)

W125F's native cases found that every launch request after a retired escrow fill returned `INVALID_STATE`. Root traced
it in the source:

- An ordinary fuel change goes through `RocketFlightData.withFuel`, which applies `FUEL_AVAILABLE` and moves an
  `ASSEMBLED` rocket to `FUELED` once it has fuel (and moves an empty `FUELED` rocket back to `ASSEMBLED`).
- An escrow fill builds its after flight with `EscrowNativeEnvelope.fuelOnly` (`EscrowReconciliationChange`), which
  restores the flight with the **same state** and only new fuel, and `RocketEntity.applyEscrowFill` publishes it.
- `RocketFlightService.launch` promotes only a `LANDED` rocket with launchable fuel; anything else that is not `FUELED`
  returns `INVALID_STATE`. `EscrowGovernedLaunch.planned` likewise accepts only `FUELED` or `LANDED`.

So a newly assembled, empty rocket that is refuelled through escrow keeps `ASSEMBLED` with fuel and can never launch.
Production escrow admission is disabled, so no player meets this yet, but it blocks enabling admission.

## Root decision R55 (2026-10-22)

1. **Fix at the launch boundary, not in the escrow envelope.** An `ASSEMBLED` rocket whose fuel is available for launch
   is treated exactly as a `LANDED` one is today: the launch (ordinary and governed) first applies `withFuel(fuel, ...)`
   to promote it, then continues unchanged. `fuelOnly`, the fill's after envelope, its frozen digests, classification and
   replay stay byte-for-byte unchanged, so no captured or in-flight operation changes meaning.
2. **One predicate.** Add one method on `RocketFlightData` (for example `launchReady()`: `FUELED`, or `LANDED` or
   `ASSEMBLED` with fuel available for launch) and use it everywhere the code now asks "FUELED or LANDED" to decide that a
   stationary rocket may be offered or sent on a flight: `RocketFlightService.launch`, `EscrowGovernedLaunch.planned`,
   `RocketFlightQuotes`, `RocketFlightMenuData` (and so the client's `RocketFlightScreen`, which must stay display-only),
   and `RocketNavigationService`. Inventory every other `FUELED`/`LANDED` check first and list in the record which you
   changed and why each other one stays.
3. **Not changed:** an empty `ASSEMBLED` rocket still returns `INVALID_STATE`; a rocket with a transfer record still meets
   the governed no-transfer guard (`TRANSFER_JOURNAL_BLOCKED`, F1b/D2, W130's subject); no saved format, schema, hash,
   envelope, network field, bound, budget or timeout changes; no existing assertion is relaxed. If the fix seems to need
   any of these, or if launch-time promotion breaks the governed launch's checkpoint comparison
   (`EscrowGovernedLaunch`: "Launch changed the checkpointed source flight"), stop and report.

## What to do, each in its own commit with a test that fails before the change

1. The predicate and the ordinary launch path, with unit tests (ASSEMBLED with fuel promotes and continues; ASSEMBLED
   empty, and a rocket that requires no fuel, behave as before; FUELED and LANDED unchanged).
2. The governed launch path (`EscrowGovernedLaunch.planned` and its checkpoint), with a test.
3. The quotes, menu and navigation checks.

## Tests

- A0: the predicate and each changed check.
- A1 (GameTests): an empty assembled rocket refuelled through the escrow fill path (as `FluidFuelLoaderEscrowGameTests`
  does, through the release-test hooks) then launched: it enters COUNTDOWN and the cancellation restores it; the same
  rocket through the governed launch; the ordinary loader path unchanged; an empty ASSEMBLED rocket still refused.
- Native: rerun W125F's seven follow-up cases (`EscrowLoaderFollowUpProbe`, two restarts each) on your committed source
  and report each launch code; Root expects the named cases to reach the governed no-transfer guard instead of
  `INVALID_STATE`. Report the actual codes; do not change the oracle's expectation to match.

## Verification

Each new or changed test class alone, then the whole unit suite; one full GameTest with the unchanged log checker on
committed source; then the native cases, all under the heavy-JVM lock with the atomic pattern in WORKER-RULES, releasing
it and deleting every server copy and `build/gametest/world` in the `finally` of the same detached script that took it.
Check C: and D: above 10 GiB and that 6 GiB of memory is free before each Gradle run or native start.

An independent read-only review follows before merge, because this changes launch admission.

## Not in this task

D2 (W130, which runs in parallel and touches the escrow loader admission), cargo's F1 extension, enabling admission,
ADRs, the risk register, status files and the ledger (Root).
