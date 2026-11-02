# W254: two-real-client V2 checklist for the bay safe exit

Date: 2026-11-02. Author: W254 (W230-S3), for Root and the owner's campaign. This is a checklist, not a result: no
real client was run for it. It turns [W230-DESIGN](W230-DESIGN.md) section 7 into steps, using the behaviour that S2
([W242](W242.md)) implemented and that [W254](W254.md)'s native cuts observed on the packaged JAR. Bots, embedded test
players, GameTests and Xvfb runs are not V2 evidence.

## 0. Before the campaign

### 0.1 A decision Root must take first

The design says to test "through the existing fully gated fixture while admission is disabled". That fixture
(`arce bay release-test fixture` and W254's `exit fixture`) passes only the complete release-test gate,
`EscrowTestEndpoints.enabled`, which requires that **every connected player was created by the release-test facility**.
A real client closes that gate, so the fixture cannot launch a held rocket with real clients aboard, and owner
decision Q1 (R243) keeps bay admission off until the V2 evidence exists. The campaign therefore needs one of these,
decided by Root (and the owner where it touches admission):

1. **Recommended:** a campaign-only gate variant, on a disposable world only, that also admits the two named real
   client UUIDs, with its own property, its own log line at startup and a test like `BayReleaseTestGateTest`. It needs
   a short ADR note, because it widens a release-test gate.
2. Enable bay admission on a disposable campaign world only. The design warns that changing an admission property is
   not evidence of production approval; the record must say so.

Until one is decided, sections 1 to 6 cannot start. Record the decision (ID, date, channel) at the top of the
campaign record.

A second prerequisite is the source under test. Use a commit that contains W257's committed samples (R283) and W262's
real-client landing rules (R288); record its SHA. On older sources the fall offer ends on a real client before it lands:
two ticks after the exit before W257 ([W254](W254.md), "The S2 finding"), and one tick before the landing packet with
W257 alone (W257-R1), so section 3's steps 3.2 and 3.5 would only record that defect. (Updated by W262, 2026-11-02.)

### 0.2 What to pin and record once

| Item | What to record |
|---|---|
| Source | The tested commit SHA and the reobfuscated JAR's SHA-256, size and file name. The server and both clients use the same JAR. |
| Forge and Java | The Forge version on the server and on each client, and each Java version. |
| Configuration | `server.properties` (keep `difficulty`, `view-distance` and `simulation-distance` the same for every run), the mod's server configuration, and any campaign property from 0.1. |
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
| 3.5 | Repeat 3.1 in a new world, then, before B lands, let an operator teleport B a little (for example `/tp <B> ~0.1 ~ ~`) or let B throw an ender pearl. | The landing hurts: the offer ended. | Which of the two was used, what ended the offer (the server log), health. |
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
