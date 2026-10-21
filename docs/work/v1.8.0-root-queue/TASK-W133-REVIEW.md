# TASK W133-review: independent read-only review of W133 (F3, launch of an assembled rocket filled through escrow) before merge

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents or
subagents. A Claude session wrote W133. Both Codex slots are in use (W130 and W131F), and W133 should merge before W134
starts, so Root chose a fresh Claude session with no context from the author over waiting for a Codex slot.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w133` at `92a5f9b0` (base `6dc96301`; tested source `14edd63a`). Your review worktree `D:/GitHub/arce-v180-w133-review` is a detached checkout of `92a5f9b0`; read the diff with `git diff 6dc96301..92a5f9b0` there |
| Task it answers | [TASK-W133](TASK-W133-F3-ESCROW-FILL-LAUNCH-STATE.md), with Root decision R55 |
| Background | The W125F section of [records/W125.md](records/W125.md) ("Additional finding") |
| Record | `docs/work/v1.8.0-root-queue/records/W133.md` on the branch (the plan and the check inventory) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W133/all-14edd63a-20261022-044714/` (`verification.json`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W133-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The predicate.** `RocketFlightData.launchReady()` is `FUELED`, or `ASSEMBLED` or `LANDED` with fuel available for
   launch. Check `RocketFuelState.availableForLaunch()` for every propulsion kind and fuel state the code can hold.
2. **The inventory.** Search every `FUELED`, `LANDED` and `ASSEMBLED` check in `src/main` yourself and compare with the
   record's inventory. Is any check that decides whether a stationary rocket may be offered or sent on a flight left
   unchanged? Does any later step (countdown, cancellation, departure, transfer, station docking, escrow cancellation or
   settlement, the HUD or a C2S handler) assume that a launching rocket was `FUELED` before the launch, and break or
   change meaning when it was `ASSEMBLED`?
3. **The ordinary launch.** `RocketFlightService.launchSource` promotes with `withFuel(fuel, gameTime)` exactly as the
   `LANDED` path did. Is the promoted flight what the countdown, its cancellation and a save during the countdown expect?
   Does any check before the promotion (ownership, distance, chunk loading, transfer guard, propulsion) now pass or fail
   differently?
4. **The governed launch.** `EscrowGovernedLaunch.planned` uses the same predicate and promotion. Does the checkpoint
   comparison ("Launch changed the checkpointed source flight") still compare like with like? Is the frozen source
   envelope byte-for-byte unchanged, so no captured or in-flight operation changes meaning? Does the escrow fill's
   `EscrowNativeEnvelope.fuelOnly`, its digests, classification and replay stay unchanged?
5. **The three behaviour changes the worker lists** for your verdict:
   - An `ASSEMBLED` rocket whose propulsion needs no fuel is now launch-ready. TASK-W133 item 1 said such a rocket should
     behave as before; the worker says assembly cannot produce that state today. Check whether any path (assembly, a
     saved or migrated rocket, a command, creative mode, a test hook) can produce it, and recommend keeping or excluding
     it.
   - An empty `LANDED` rocket's console quote now shows `INVALID_STATE`.
   - The governed launch refuses an empty `LANDED` source with a different exception type.
6. **Client and authority.** `RocketFlightScreen` stays display-only, the menu flag comes from server data, and no
   client value decides launch readiness; no network field or packet changes.
7. **Nothing weaker.** List every edited pre-existing test or fixture file and say whether any assertion, bound, budget
   or timeout became weaker; no saved format, schema, hash, envelope or network field changes; the log manifest is
   unchanged; new classes stay under 500 lines.
8. **The native evidence.** All 14 launch requests in W125F's seven cases now answer `TRANSFER_JOURNAL_BLOCKED`, 6 from
   the governed F1 start and 8 from the F1b resource interlock. Do the logs and the probe support that reading, and is
   that what F1b (W130's subject) predicts? Is anything still masked?
9. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`), which since W133's
   base merged W132 (item cargo admission and reconciliation) and Root's records. Preview with `git merge-tree` and say
   what the merge must keep. W130 (D2, in progress on `codex/v1.8.0-w130`) and W131 (C-E3, on `codex/v1.8.0-w131`) are
   not merged; note any file W133 shares with them as a merge risk only.
10. **The record** against the code and the evidence.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: W133's
new and changed unit classes, each alone (`RocketLaunchReadinessTest`, `EscrowGovernedLaunchTest`,
`EscrowGovernedSourceActionTest`, `RocketFlightQuotesTest`), then the `rocket.server`, `rocket.menu`, `rocket.flight` and
`resourceescrow` packages. You may add a probe test to check a finding; keep each probe in the output folder. Before
each Gradle run, check that at least 6 GiB of memory is free and wait if not; run one Gradle process at a time, and no
GameTest, `runData` or server; take no heavy-JVM lock. Delete the worktree's `build` and `.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W133-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
