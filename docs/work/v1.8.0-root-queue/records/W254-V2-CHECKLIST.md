# W254: two-real-client V2 checklist for the bay safe exit

Date: 2026-11-02. Author: W254 (W230-S3), for Root and the owner's campaign. This is a checklist, not a result: no
real client was run for it. It turns [W230-DESIGN](W230-DESIGN.md) section 7 into steps, using the behaviour that S2
([W242](W242.md)) implemented and that [W254](W254.md)'s native cuts observed on the packaged JAR. Bots, embedded test
players, GameTests and Xvfb runs are not V2 evidence.

## 0. Before the campaign

### 0.1 Dedicated campaign selection: Root decision R298

Root chose option 1 on 2026-11-03 in [TASK-W269](../TASK-W269-V2-CAMPAIGN-GATE.md), under the owner's standing
delegation for design points. Use the existing fully gated fixture on a disposable copy, with the two real client
UUIDs selected as follows. Root corrected R298's original admission wording on 2026-11-03 in `TASK-W269-FIX.md`
on the shared checkout: option 1 leaves production bay admission off everywhere outside GameTest and the full
release-test gate (ADR-065 section 10), but opens that full gate and D0's bay deployment admission on the marked
campaign world. A named client's ordinary bay-rocket launch can therefore start D0 there. This is not option 2
(a production admission switch). Campaign execution and scheduling remain the owner's decision. Record R298's date,
task source and this correction in the campaign record. [W269-DESIGN](W269-DESIGN.md) defines the boundary.

- Set both JVM properties before the Forge argument file:
  `-Dadvancedrocketrycommunity.releaseTestHooks=true` and
  `-Dadvancedrocketrycommunity.bayV2CampaignPlayers=<client-A-uuid>,<client-B-uuid>`.
  Use exactly two distinct, full hyphenated UUIDs, with no spaces. Empty, duplicate, abbreviated, extra or malformed
  values refuse dedicated-server startup, including with hooks off; the bounded error names the property and format.
  A refused startup still exits with status 0: judge refusal from the format error, the crash report and the absence
  of the server's `Done` readiness line, not from the process status.
- After copying the world, create `<world-root>/.arce-bay-v2-campaign` with exactly the ASCII bytes
  `ARCE_BAY_V2_DISPOSABLE_WORLD_V1\n` (LF, no BOM). Never create it in the source world. It must be a regular file,
  not a symlink. Startup samples it once; changing the marker or selected clients requires a restart. Absence,
  unreadability or different bytes leave the ordinary connection policy unchanged. The marker is an explicit operator
  acknowledgment, not proof of copying; archive its bytes and hash with the campaign configuration.
  The marker is 32 bytes, SHA-256 `e67124e2a697a32ccdafc384e672362f81c732e5e01872012a2e22c6e4590f98`.
  Write it byte-exactly in PowerShell (set `$worldRoot` to the disposable copy):

  ```powershell
  $path = Join-Path $worldRoot '.arce-bay-v2-campaign'
  [IO.File]::WriteAllBytes($path, [Text.Encoding]::ASCII.GetBytes("ARCE_BAY_V2_DISPOSABLE_WORLD_V1`n"))
  Get-FileHash -LiteralPath $path -Algorithm SHA256
  ```

  Do not use `Set-Content` or `Out-File`, which can add CRLF or a BOM.
- Require a dedicated server with `enable-rcon=false`, `online-mode=true`, `white-list=true` and
  `enforce-whitelist=true`. The whitelist contains exactly the two selected accounts, using their authenticated
  Mojang UUIDs. Restrict network access so nobody else can reach the server, and connect no other real clients.
  Offline mode does not authenticate UUIDs: an impersonator can select the same name and obtain its derived UUID.
  These are campaign prerequisites, not additional runtime checks. The gate still checks the server thread,
  hooks, RCON and every connection. Exact facility-owned test players may coexist with either or both named clients;
  a third real client's connection closes it. Selection does not adopt clients into the test-player facility.
- Check these exact INFO message shapes (the UUID list is lowercase and lexically sorted):
  `ARCE_BAY_V2_CAMPAIGN startup property=advancedrocketrycommunity.bayV2CampaignPlayers clients=<uuid1>,<uuid2> world=<absolute-world-root> marker=true`
  and, once for each named UUID login,
  `ARCE_BAY_V2_CAMPAIGN connect property=advancedrocketrycommunity.bayV2CampaignPlayers clients=<uuid1>,<uuid2> world=<absolute-world-root> marker=true player=<uuid>`.
  An unmarked selection logs `marker=false`; neither line appears when the property is unset. These lines describe
  selection, not proof that all the other gate conditions hold. Unset preserves the existing facility-only policy.

W269 changes the connection policy of the single full release-test gate behind all gated op-only command families,
not only the bay exit fixture. On this world D0 admits bay rockets launched by named clients as well as facility
rockets. Other gated command families and seams also accept these connections; seams needing a separate property
stay off without it. Production admission switches, fixture command implementations, packets, saved data and
GameTest are unchanged.

- [ ] Launch no bay rocket outside the campaign's prescribed held-rocket steps. Any `ARCE_BAY_CLAIM` other than
  those steps is a deviation to record with the rocket/transfer IDs and log times.

Integrated/LAN servers ignore this property; it does not provide the LAN fixture route needed by section 5.
The LAN procedure remains a separate campaign prerequisite for Root. Root must apply the drafted ADR-065 section 10
note and finish review/integration before using the new selection; this checklist is not a V2 result or Gate approval.

A second prerequisite is the source under test. Use a commit that contains W257's committed samples (R283) and W262's
real-client landing rules (R288); record its SHA. On older sources the fall offer ends on a real client before it lands:
two ticks after the exit before W257 ([W254](W254.md), "The S2 finding"), and one tick before the landing packet with
W257 alone (W257-R1), so section 3's steps 3.2 and 3.5 would only record that defect. (Updated by W262, 2026-11-02.)

### 0.2 What to pin and record once

| Item | What to record |
|---|---|
| Source | The tested commit SHA and the reobfuscated JAR's SHA-256, size and file name. The server and both clients use the same JAR. |
| Forge and Java | The Forge version on the server and on each client, and each Java version. |
| Configuration | `server.properties`, explicitly `online-mode=true`, `white-list=true`, `enforce-whitelist=true` and `enable-rcon=false`; `whitelist.json` listing exactly the two accounts; the network access restriction; the mod's server configuration and campaign properties from 0.1. Keep `difficulty`, `view-distance` and `simulation-distance` the same for every run. |
| Clients | For client A and client B: account name and UUID, role (owner or crew), OS, CPU, GPU, driver version, render distance, language (run one pass in English and one in Chinese). |
| Clocks | UTC on all three machines, synchronized; every step below records its start and end time. |
| World | A disposable copy, never the owner's source world. Record its folder name and the SHA-256 of `level.dat` before the campaign. |
| Logs | The server log at INFO for the whole session and its SHA-256; both clients' `latest.log`. |
| Video | Both clients recorded with the F3 screen (position, facing), the health and hunger bars, and chat visible. Record file names and SHA-256. |

Before section 1, search the server log for `ARCE_BAY_EXIT observer unavailable`. If it appears, stop: the packaged
observers did not resolve and no fall safeguard can be granted.

### 0.3 The held rocket

Use the forced-stop fixture shape that W254 used: a hand-fuelled Earth-to-Moon rocket with one seat per passenger (the
native fixture has one seat; the campaign rocket needs two), one data bay and one research-locked solar bay, held in
its INTENT hold at 48 blocks above its launch origin. Keep the launch origin column empty unless a step says to build a
pad. Record the logical rocket ID, the transfer ID and the seat index of each client from the server's
`ARCE_BAY_CLAIM` and `ARCE_BAY_EXIT` lines.

## 1. Safe exit to the ground, both roles

Build a 3x3 stone pad one block below the launch origin before the launch.

| Step | Do | See on both clients | Record |
|---|---|---|---|
| 1.1 | A (owner) and B (crew) ride the held rocket in different seats. B sneaks. | B detaches and stands on the pad at the origin's height; A stays seated; B's health is unchanged; B sees "Exited to a checked standing position…" (English) or its Chinese text. | B's position before and after, health, the message, the server's `ARCE_BAY_EXIT ... outcome=safe code=SAFE candidate=<n>` line. |
| 1.2 | A opens the rocket menu and presses LEAVE. | A stands on the next free pad position, not inside B; the menu shows no "Flight request accepted" line. | Both positions, the candidate ordinal of each exit (B's first, A's next), the attempt sequence order. |
| 1.3 | Repeat 1.1 and 1.2 with the roles swapped (A sneaks, B uses the menu). | The same outcomes with the roles exchanged. | The same fields. |
| 1.4 | Both clients look at the rocket. | The rocket stays held: same position, no launch, no landing. | Screenshot from each client. |

## 2. A witness stays aboard; competing exits; a menu replay

| Step | Do | See | Record |
|---|---|---|---|
| 2.1 | With both aboard, A exits; B stays. | B is never forced out; B's seat and view are unchanged. | B's mount state before and after (F3 shows the vehicle). |
| 2.2 | Both press sneak in the same second. | Two exits in a server order: the first takes the first safe box, the second the next box, or the fallback if none is left. | Both outcome lines with their attempt and capture sequences, candidates and positions. Non-overlap alone is not enough. |
| 2.3 | After exiting, A (owner, or an operator) stands next to the rocket and presses LEAVE in a menu replay. | Nothing moves; B's seat is unchanged; no new `ARCE_BAY_EXIT` line for A. | Positions of both, the server log excerpt. |

## 3. No safe place: the fallback and the one-descent safeguard

Remove the pad (or start a fresh world without one). Do not do this on the owner's source world.

Before 3.1, an operator places a 3x3 stone floor in the launch-origin column about ten blocks under B's seat, as the
native X4 does. A fall of 48 blocks takes about 2.4 seconds, so the floor cannot be placed after the exit. Keep it more
than one block above or below the launch origin's height: a floor in that column within one block of the origin's height
is an anchor candidate (`BayExitCandidates.ordered`) and turns the exit into a safe placement. Record its coordinates.

| Step | Do | See | Record |
|---|---|---|---|
| 3.1 | B sneaks at 48 blocks. | B detaches in place and starts to fall; B sees "No safe position found; detached in place. One limited ordinary-fall safeguard…". | B's exit position, the outcome line (`outcome=fall_offer code=NO_SAFE_EXIT candidate=0`), the time. |
| 3.2 | B falls onto the floor placed before 3.1, without moving sideways. | B takes no fall damage on that first landing. Vanilla's server-side step reaches the floor one tick before B's landing packet arrives; since W262 (R288) that no longer ends the offer (W257-R1). | B's health before and after, the time from exit to landing, the server's `ARCE_BAY_EXIT` line. |
| 3.3 | B climbs and falls again. | The second fall hurts: there is no second safeguard. | Health before and after. |
| 3.4 | Not reachable by a real client: from 48 blocks a client lands in about 40 ticks, and fluid, levitation, flight and climbing all end the offer before the 200-tick deadline. | Mark it "not run; covered natively by X5" (W254's client that sends no moves). | Nothing. |
| 3.5 | Repeat 3.1 in a new world, then, before B lands, let an operator teleport B a little (for example `/execute at <B> run tp <B> ~0.1 ~ ~`, relative to B, above the same floor) or let B throw an ender pearl. | The landing hurts: the offer ended. | Which of the two was used, B's actual coordinates before and after, what ended the offer (the server log), health. |
| 3.6 | Repeat 3.1 onto pointed dripstone. | Dripstone damage is not prevented. | Health and the death or damage message. |

W254 and W262 show the packaged mechanism natively: a landing in the exit's own tick (X9) and a ten-block landing with a
real client's timing (X4: the landing packet one full server tick after the last airborne one) each cancel exactly one
FALL attempt, and a teleport after the correction window (X7) or two teleport steps (X10) leave the landing unprotected.
V2 shows what a real client sees. Record every early end with the time from the exit and the client's movement. Two
residuals can end the offer early on a real client and are not checklist failures when the server log shows them: a
last airborne position within 0.01 block of the floor (the next committed sample sees ordinary contact), and two move
packets bunched into one server tick that together fall more than four blocks.

## 4. Disconnect, reconnect, death and portal

| Step | Do | See | Record |
|---|---|---|---|
| 4.1 | B disconnects while seated, then reconnects while the rocket is still held. | B is not remounted while the hold lasts; after about 10 seconds the server logs `ARCE_PASSENGER_RECONNECT_EXPIRED`; B's seat stays assigned. | B's login position, mount state, the log lines. |
| 4.2 | B exits (fallback case), then disconnects in the air before landing, then reconnects. | No safeguard after the reconnect; the seat stays assigned. | Positions, health, log. |
| 4.3 | B exits, dies (for example `/kill` by an operator), and respawns. | No stale placement after the respawn; no new safeguard. | Respawn position, log. |
| 4.4 | B exits and enters a nether portal before landing. | No placement back in the overworld; no safeguard on the other side. | Positions, log. |
| 4.5 | After the hold is released (by the operator procedure for this fixture), the flight continues with B online and offline in turn. | An online assigned B may be moved or remounted by the later passenger phase; an offline B is not required for the flight to continue. | Both clients' views, the passenger phase lines. |

## 5. LAN host and guest

Repeat sections 1, 3.1 to 3.2 and 4.1 on a LAN world, A hosting and B joining, then with B hosting and A joining.

| Step | Do | See | Record |
|---|---|---|---|
| 5.1 | The host exits; the guest witnesses. Then reverse. | The host has no extra exit rights; the outcomes match the dedicated server's. | Both clients' views and the host's log. |
| 5.2 | After an exit, the host saves and quits, then reopens the world. | No exit state returns; the seat is still assigned; the host's own saved position is ordinary player data, not an exit record. | Positions before and after, mount state. |
| 5.3 | After an exit, end the host's process without saving (task manager). Reopen. | The host's position may be the older one from before the exit; the seat is still assigned. | The returned position; say explicitly whether it is the old one. |

## 6. Authority and load checks

| Step | Do | See | Record |
|---|---|---|---|
| 6.1 | A non-passenger client next to the rocket tries the menu LEAVE and sneaks near it. | Nothing moves; no exit line for it. | Log excerpt. |
| 6.2 | Before and after a set of exits, an operator records the rocket's bay packages, its fuel and its passenger manifest (the operator diagnostics for the rocket and the bay status). | All unchanged. | The before and after values. |
| 6.3 | Before and after an exit, an operator records the loaded chunk count and forced chunks (`/forceload query`, the server's chunk statistics). | No new loaded or forced chunk from the exit. | The values. |
| 6.4 | After the exits, release the hold and let the flight finish. | The bay steps retry normally and the flight lands without waiting for an exited passenger to save or connect. | The claim lines to the landing. |

## 7. What the campaign record must contain

- Section 0's pinned items, the 0.1 decision and every step's start and end time.
- For each step: pass, fail or not run, with the reason, the coordinates, health values, messages (both languages
  once), mount states and the matching server lines.
- Every deviation from this checklist, every client error, and every step that could not run, listed as missing.
- The video and log file names with their SHA-256.

A V2 result needs at least two real clients on both the packaged dedicated server and a LAN world (W230 section 7).
Neither W254 nor this checklist is that evidence, and neither approves a Gate or bay admission.
