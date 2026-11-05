# TASK W291R: independent review of W291's pad selection and flight-plan packet change

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
you did not write this change. Review; do not edit, commit or push anything in any checkout.

| Item | Value |
|---|---|
| Change under review | Branch `codex/v1.8.0-w291` at `ccba9d62a` (base `b0d36a4b5` on the shared branch), checked out read-only and detached at `D:/GitHub/arce-v180-w291r` |
| Implementer's record and design | `docs/work/v1.8.0-root-queue/records/W291.md` on that branch (section "Scope and implementation design"); logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W291/` |
| Task it implements | [TASK-W291](TASK-W291-PAD-SELECTION.md) with Root decision R324 |
| Your output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W291R/REVIEW.md`, plus your logs in that folder |
| Why a review | It changes a network packet and the `rocket_flight` channel revision (AGENTS.md section 13) |

## What to check

1. **The packet.** `RocketFlightPlanPacket` gains a display-only pad name written only when the plan carries a pad
   reference; `MAX_ENCODED_BYTES` grows and the 32 KiB ceiling is unchanged. Check the encode/decode symmetry, the
   length bound on read (a malicious or old server cannot make the client allocate or read more than the bound), the
   trailing-bytes check, that an empty name with a pad reference and a name without a reference are handled as
   designed, and that the channel revision bump (9 to 10) makes a mismatched client and server refuse to connect
   rather than misread frames. Check `src/test/resources/network-protocols.txt` and the protocol pin tests.
2. **Server authority.** The client only displays the name; no C2S packet, coordinate, launch validation or saved
   flight plan changes. Confirm nothing the client sends now decides a pad.
3. **The unloaded-pad choice.** A command may record a station-owned indexed pad reference without loading its chunk,
   as menu intent only; flight admission (`NamedLandingPadStaging.select` and `recheck`) still requires the loaded
   pad with its existing geometry, occupancy and claim checks (ADR-065 section 6.1). Look for a path where a recorded
   but unloaded choice lands a rocket without those checks, loads a chunk, or outlives the station or pad it names.
4. **The console.** Station cycling keeps the pad only while the station UUID is unchanged; check the menu's state on
   the server and that the screen shows the server's value.
5. **The command messages.** Each refusal has its own translated message in both language files.
6. **Tests.** Do the new tests fail without the code they test?

Run the `pad_selection` GameTests and the landing pad, flight menu and network unit tests on the branch under the
heavy-JVM lock (`D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm`, protocol in WORKER-RULES); build with
`--no-build-cache` or `compileJava --rerun`. A full GameTest run is not required. Delete any scratch export when done.

## Report

Write REVIEW.md in English: a verdict (APPROVE, APPROVE WITH NOTES, or CHANGES REQUIRED), each finding with its
severity, the file and line, what is wrong and what would fix it, your conclusion on each point above, and the
commands you ran with their results. The last line of your answer is the REVIEW.md path.
