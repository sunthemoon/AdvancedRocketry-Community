# TASK W153-review: independent read-only review of W153 (C17a-04, the full linker and the rocket monitoring station)

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w153` at `caef08fd` (base: shared `acd20903`, merged as `a2b1baec`). Review worktree `D:/GitHub/arce-v180-w153-review`, a detached checkout of `caef08fd`; read `git diff acd20903..caef08fd` (57 files) |
| Task it answers | [TASK-W153](TASK-W153-C17A-04-LINKER-MONITOR.md), with Root decision R92 (QUEUE, and the R92 note after ADR-065 section 11.1) |
| Contract | ADR-065 sections 4.2, 4.3, 11.1 and 11.2 |
| Record | `docs/work/v1.8.0-root-queue/records/W153.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W153/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W153-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **R92 and the wire.** `ROCKET_MONITOR` is byte 4 on the existing intent and view; `classic_controls` is protocol 2
   with exact-version refusal; kinds 0 to 3 encode byte for byte as before; the 512-byte and 8 KiB bounds and the
   canonical-encoding and trailing-byte checks hold for the new kind; no test lost an assertion (commit `1abb673f`);
   the six new expected ERROR lines in `scripts/gametest_expected_log.json` are the rejection cases they claim.
2. **C2S validation.** The server resolves the monitor from the open menu only (no client position, no chunk load);
   it checks player, distance, permission, state and the displayed quote token; stale or replayed intents refuse.
3. **The linker (ADR-065 4.2).** The schema-1 selection is unchanged; every new binding (monitor, managed pad) is
   decided server-side against loaded objects, owners and the 8-block rule; no scan or search runs from a packet or for
   an unbound device; loader admission and the existing roots are unchanged.
4. **The monitor (ADR-065 4.3).** Status, relative height and vertical velocity, banks and costs carry no other owner's
   coordinates; comparator output is 0 to 15; one rising redstone edge submits one fresh request for the persisted armed
   destination; arming and destination are invalidated when authority, orbit, catalog or binding change; the launch
   uses the shared flight path, never `requestAdminFlight` and never `operator=true` for an unattended launch,
   including with the owner offline; refusals keep cargo and fuel; governed (escrow) rockets keep their rules.
5. **Production paths touched.** `RocketFlightService`, `RocketTransferService`, `RocketManager` and `RocketRuntime`
   change: confirm that existing checks are not weakened and no existing behaviour changes for players who do not use
   the monitor.
6. **Persistence.** The monitor's block-entity root: schema 1, bounded, exact fields, unknown-data preservation per
   ADR-061/064, and what happens to an armed monitor across unload and restart (the record's "unloaded arming").
   Assess the record's two known risks: the pad alias depends on a retained journal identity, and the monitor does not
   rebind across dimensions.
7. **Resources and tests.** Original art and provenance, EN and zh text, recipes and data generation; tests fail on the
   old code where they claim to; new classes under 500 lines.
8. **Merge** with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`) and with W152
   (`codex/v1.8.0-w152`, in progress, which touches the rocket transaction and Forge adapters): list the shared files
   and any conflict.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: W153's new and changed unit classes alone,
then the `classiccontrol`, `rocket.monitor`, `rocket.fuel.loader` and `rocket.server` packages; the log checker's Python
tests. No GameTest, runData or server; no heavy-JVM lock. Check 6 GiB free memory first. Delete the worktree's `build`,
`.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W153-R1, ...), severity, file and line, problem
and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
