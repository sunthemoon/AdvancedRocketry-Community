# TASK W182-review: independent read-only review of the menu and flight-intent authority fixes

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w182` at `fb68ae5d8`, cut from the shared head `0aec00998`; the slice is `0aec00998..fb68ae5d8` (7 commits). Review worktree `D:/GitHub/arce-v180-w182-review`, a detached checkout of `fb68ae5d8` |
| Task it answers | [TASK-W182](TASK-W182-MENU-AUTHORITY-GAPS.md) |
| Findings it closes | W179-F1 to F3, in the "Findings for Root" section of [records/W179.md](records/W179.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W182.md` on the branch, with the design note at its top |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W182/` |
| Hosted run | the run on `fb68ae5d8` (Root pushed the branch at 00:06); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W182-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

This slice changes which C2S requests the server serves. AGENTS section 3.3 asks every C2S path to check the player,
distance, permission, state and range.

## What to check

1. **F3, the flight intents.** The note says the client sends LAUNCH, CANCEL, BOARD and LEAVE only from the flight
   menu, so all four now need that menu open and valid, a connected, living, non-spectator player that is not a fake
   player, and the server thread. Check the client code for any other sender of the `rocket_flight` packet (a key, an
   interaction, a screen other than the flight menu), and any server-side caller of `RocketFlightService.request` that
   is not a client packet: commands, governed flight, rejoin, escrow, bays. Would any of them now be refused where it
   was served before?
2. **F1, spectators.** Each of the twelve menus refuses a spectator at opening (the holder's `createMenu` returns no
   menu) and in `stillValid` or `EndgameDeviceMenu.openRefusal`, reusing the existing check. Does a `createMenu` that
   returns null for a spectator break anything vanilla or Forge expects, such as a crash or a client stuck in a screen?
3. **F2, removed holders.** The electrolyzer and rolling machine menus hold their block entity and end when it is
   removed, as the receiver and satellite menus do. Check that a menu holding a block entity keeps no stale reference
   after an unload.
4. **The changed tests (`004f2d74c`).** About 40 calls now send intents as a connected player with the flight menu
   open, and fake players became connected stand-ins that never join the player list. Confirm that no assertion was
   removed or loosened, that each test still proves what it did, and that the one added assertion in
   `RocketFlightSecurityGameTests` is right.
5. **The native probes that were not run:** `EscrowCargoFlightCuts`, `EscrowReturnProbe`, `EscrowTerminalProbe`,
   `EscrowFlightBenchmarkProbe`, `RocketRejoinNativeRows.board` and `W151NativeReturns.request`. Will each still reach
   the path its native cases test? Name the native cases Root should rerun before merge.
6. **The other 16 menus.** The record says some, such as the combustion generator, still open briefly for a spectator
   and send vanilla's open packet before the server closes them. Is that exposure worth a follow-up?
7. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the unit classes the slice touches and the
`rocket` and `menu` packages, and `python -B scripts/check_client_imports.py`. Targeted unit runs need no heavy-JVM
lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build or server. Delete the worktree's
`build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W182-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
