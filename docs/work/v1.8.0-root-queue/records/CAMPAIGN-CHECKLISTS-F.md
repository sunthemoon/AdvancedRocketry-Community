# Campaign checklists F: stations and warp

These are unrun P4 instructions for [the campaign plan](FINAL-CAMPAIGN-PLAN.md), inventory F01–F07.
Sources are [v1.5 sections 10–13](../../../versions/V1.5.0-ORBITAL-STATION-WARP.md),
[G7/G8](../../../releases/v1.5.0/GATE-STATUS.md) and the implementation rules in
[ADR-044](../../../decisions/ADR-044-STATION-WARP-LOGICAL-RELOCATION.md).
No result or Gate is approved here.

## Shared setup and evidence

- H means a harness/operator action; P means a human action or judgement. Every numbered action below has its
  expected observation after the arrow. Run scripted actions before asking for judgement.
- Use the P2 representative v1.3–v1.7 world fixture, specifically its station/warp copy. P2 does not supply archive
  names, station IDs, coordinates, owner UUIDs or a charging recipe. Root must freeze these in its fixture manifest
  before execution. `<station-A>`, `<station-B>`, `<target-A>` and `<target-B>` below mean those recorded values,
  not literal command arguments. Both targets must be known, orbitable, different from the starting orbit, and
  include the Earth/Tau Ceti e inter-system route. Never substitute the shared Space body as an orbit target.
- Command warp requests require the connected player's own command source, their loaded committed region,
  and a server ray pick looking at its warp core within 5 blocks. Record the player/core coordinates and view
  before a request; console-origin commands are not a substitute for player authority.
- Pin the P1 C4 commit, hosted artifact, JAR SHA-256 and Java 17. Default lane is Forge 47.4.10 without JEI;
  repeat the compatibility smoke on 47.4.23. JEI 15.56.0.205 is not needed for these commands. Record actual JEI
  presence. V1 uses a real GPU; G8 also requires the second GPU category. V2 uses two independently authenticated
  premium accounts A/B, not fake players.
- Phase 3 DS uses the packaged server on the development PC and a disposable world copy. Keep difficulty,
  view-distance and simulation-distance fixed and record their values; the sources give no mandatory numbers.
  V2 in phase 4 uses `online-mode=true`, `white-list=true`, `enforce-whitelist=true`, `enable-rcon=false` and
  only the two authenticated accounts. Phase 5 REF uses 4 vCPU/8 GiB/Debian 12, a non-root user and Java 17,
  `Xmx4G` maximum, server bound to loopback with SSH-tunnel client access, and an exclusive node.
  No campaign bay gate or JVM test property is required; production switches stay unchanged.
- Evidence root E is `D:/GitHub/ARCE-Task-Evidence/final-campaign/<C4 short SHA>/`. Put DS evidence in
  `E/phase3-ds/<row>/`, V1 in `E/phase2-v1/<machine>/<round>/`, V2 in `E/phase4-v2/<sitting>/`, and REF in
  `E/phase5-ref/<run>/`. Keep server/client native logs, per-step UTC times, state projections, configuration,
  hardware and candidate identity. Screenshots are `<row>-<step>-<client>-<language>-<scale>.png`; videos are
  `<row>-<client>.mkv`. Index every artifact and SHA-256 in `E/results/RESULTS.md`, `results.json` and
  `FINDINGS.md`; seal with `E/SHA256SUMS.txt`. Keep failed attempts. Never modify source fixtures.
- For every row, also record Forge/JEI, machine, start/end UTC, JAR SHA-256 and FAIL finding/NOT_COVERED reason.
  Missing fixture values or instrumentation mean NOT_COVERED, not an invented command or PASS.

## F01 — Concurrent stations, unload/reload and station benchmark

**Phase/level/machine/accounts:** 3 DS on the development PC, one client A; 5 REF on the reference node.
**Gate:** Ordinary copy; no bay gate; switches unchanged.
**Preconditions:** P2 station world with at least ten distinct records and a manifest of their region, orbit,
owner, permissions, energy and docked rockets. Prepare separate 10- and 100-record benchmark copies.

1. [H] Capture all station records before loading their regions. → IDs, bounds, balances and orbits are enumerable.
2. [H] Operate at least ten records concurrently through their normal station operations. → No station acquires
   another station's owner, permissions, orbit or energy.
3. [H] Move players out of those chunks and remove only fixture-owned tickets. → Regions unload; station records
   remain, without an unexpected forced chunk.
4. [H] Re-enter each recorded region. → Its own context and saved state return, not another station's.
5. [H] Stop normally and restart the same copy twice. → Compare all recorded fields after each restart;
   changes must be explained by the recorded operations, not loss or duplication.
6. [H] On REF, measure environment queries and warp preparation/commit with the 10-record layout. → Report CPU,
   RSS, tick mean/P95/P99, network and write frequency; queries must not scan the entire registry per query.
7. [H] Repeat the identical measurement with 100 records. → Preparation is bounded and charging does not dirty the
   entire root every tick. ADR-044 folds pending energy every 200 ticks; read-only warp status is not a fold trigger.
8. [H] Compare the REF windows to docs/17: mean ≤25 ms, P95 ≤50 ms, P99 ≤100 ms, RSS not persistently >5.5 GiB,
   no watchdog/OOM or unexpected forced chunks. → Record measured values, not the older development-host figures.

**Evidence to keep:** F01 record projections before/unloaded/reloaded/each restart, chunk/ticket counts, command
transcript, 10/100 workload manifest, raw reference samples and logs in the phase 3 and phase 5 folders.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## F02 — Two stations warp to different targets

**Phase/level/machine/accounts:** 4 V2, two real clients A/B on real GPU hardware and packaged DS.
**Gate:** Ordinary copy; no campaign gate.
**Preconditions:** A owns station-A, B owns station-B, each locally present in its loaded region, with a charged
warp core. Record starting balances. Default costs are 2,000,000 FE in-system and 8,000,000 FE interstellar;
record the loaded config rather than assuming defaults.

1. [P/H] A runs `/arce station warp <target-A>`. → Quote identifies A's station, orbit, target, cost and balance.
2. [P/H] B runs `/arce station warp <target-B>`. → B's quote remains independent of A's.
3. [P/H] A runs `/arce station warp confirm <station-A>` within 200 ticks. → A's 200-tick countdown begins.
4. [P/H] B confirms its station within 200 ticks, aligning the two due commits using the harness if available.
   → Each station has its own countdown; online members receive 10/5/3/2/1-second messages.
5. [H] Capture both committed records and client views. → At most one commit per tick; a second due commit waits
   and is revalidated. Each orbit changes only to its selected target and each balance loses its server-quoted cost once.
6. [P] Compare sky/context and positions on both clients. → Station identity does not cross; logical relocation
   does not teleport either player or station blocks.

**Evidence to keep:** Both videos/logs, quotes, confirmations, due/commit tick transcript, per-station pre/post state,
position and energy comparisons in the phase 4 sitting folder.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## F03 — Owner reconnect and online/offline passenger during warp

**Phase/level/machine/accounts:** 4 V2, A owner and B member/passenger, real clients, packaged DS.
**Gate:** Ordinary copy; no campaign gate.
**Preconditions:** P2 station with enough energy for separate runs; B is authorized to be aboard. Snapshot player
positions, station orbit and energy before each run; do not reset player data to fabricate recovery.

1. [P] A requests and confirms a warp as in F02. → A 200-tick countdown is visible to online members.
2. [P] A disconnects during the countdown while B stays online. → Logout does not cancel a confirmed countdown.
3. [P] A reconnects after commit. → One orbit/cost change; both clients see the station's new context at unchanged
   station-relative positions, without a second charge.
4. [P] Start a separate confirmed warp with B aboard, then disconnect B before commit. → Offline B does not block
   a valid warp, and no fabricated teleport is written to offline player data.
5. [P] Reconnect B. → B receives the current station context and ordinary saved position; no duplicate passenger.
6. [H] Compare station and player projections for both runs. → Distinguish connection events from the warp commit;
   an unconfirmed quote is cleared on logout, unlike a confirmed countdown.

**Evidence to keep:** Both videos, connection/countdown/commit logs and pre/post player/station fields in phase 4.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## F04 — Docked rocket persists across a warp

**Phase/level/machine/accounts:** 3 DS, development PC, one client A.
**Gate:** Ordinary copy; no campaign gate.
**Preconditions:** P2 station with a stationary docked rocket and no overlapping in-flight transaction. Record
logical/entity identities, cargo, fuel, seats, station ID and coordinates.

1. [H] Save the docked rocket and station normally. → Capture the complete baseline projection.
2. [P/H] A requests and confirms the station warp. → A quote warns that docked rockets move with the station.
3. [H] Save during countdown, then stop the server. → Countdown is memory-only; restart must not resume or charge it.
4. [H] Restart and compare the rocket/station. → Original orbit, unchanged charge and exact docked rocket contents.
5. [P/H] Confirm another warp and allow commit. → Same physical region and docked rocket, new orbital context,
   one station-energy debit.
6. [H] Stop and restart the same world twice. → Rocket identity, seats, contents, fuel and current station context
   survive both; no stale captured `current_body` prevents later station-source admission.

**Evidence to keep:** Phase 3 logs, normal-stop writer-drain lines, countdown and committed projections, restart
comparisons, rocket screenshot and station quote.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## F05 — Catalog changes invalidate a pending warp safely

**Phase/level/machine/accounts:** 3 DS, development PC, A; optional V1 feedback capture.
**Gate:** Ordinary disposable fixture; no campaign gate.
**Preconditions:** P2 station fixture plus a valid target-removal/reload data pack. Sources give no pack filename;
Root must pin it. Record original catalog generation, orbit, balance and the quote's cost/class.

1. [P/H] Request and confirm a warp to the fixture target. → A countdown exists, but no debit/relocation yet.
2. [H] Activate the target-removal pack and run `/reload` before commit. → The new catalog lacks that target.
3. [H] Observe the due countdown. → Commit rechecks and aborts with readable reason; original orbit/balance remain.
4. [H] Restore the pack and run `/reload`. → Valid target returns; no old confirmation becomes active again.
5. [H] Repeat with a catalog/config change that changes the quoted cost class. → Old consent is invalid; no warp
   or debit without a new quote and confirmation. Common-config changes require restart, not merely `/reload`.
6. [H] Restart the world twice. → Aborted work remains aborted; station and rocket resources are unchanged.

**Evidence to keep:** Pack files/hashes, reload log, quote/abort screenshots, before/after orbit and energy and
two restart comparisons in phase 3. Do not claim all catalog reloads must abort if the confirmed facts stay valid.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## F06 — Non-member warp and permission requests are refused

**Phase/level/machine/accounts:** 4 V2, A owner, B non-member/non-operator, packaged DS and two real clients.
**Gate:** Ordinary copy; no campaign gate.
**Preconditions:** Record member list, orbit, energy and loaded chunks. B can reach the station fixture but is not
granted ownership by an operator during the test.

1. [P] B requests `/arce station warp <target-A>` locally. → Refused; no quote that grants commit authority.
2. [P] B tries `/arce station warp confirm <station-A>`. → Refused; no countdown, charge or relocation.
3. [P] B attempts station deletion and permission changes through the ordinary station interface. → Refused;
   membership and ownership remain unchanged. Exact command grammar is not supplied by the inventory sources.
4. [P] A requests/confirms a valid warp. → Owner can operate the same fixture; a broken fixture is not a refusal pass.
5. [H] Compare registry, energy and chunk counts around B's attempts. → No mutation or arbitrary chunk load.

**Evidence to keep:** Both clients' refusal/success views and logs, member-list and chunk-count comparisons in phase 4.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## F07 — Station context, warp feedback and visual cache

**Phase/level/machine/accounts:** 2 V1 on main and second GPU categories; 4 V2 with A/B.
**Gate:** Ordinary copy; no campaign gate.
**Preconditions:** P2 two-station fixture with different orbit bodies, charged core and a controller; one successful
warp, one deliberate valid refusal fixture. Record CPU/GPU/driver/resolution and initial player coordinates.

1. [H] View each station before warping. → Orbit body, star, sun and gravity hints match that station's server context.
2. [P/H] Request and confirm a warp from the controller and capture the command quote too. → Target, cost,
   countdown, state and routeless-target warning agree with the server; current C4 includes the newer controller UI.
3. [H] Capture 10/5/3/2/1-second countdown and commit. → Readable feedback; position stays fixed, new sky/context
   appears only for the correct station.
4. [H] Exercise the F05 refusal. → Readable failure, not a falsely successful state or unexplained frozen countdown.
5. [H] Change GUI scales to 2, 3 and 4, in windowed/fullscreen and English/Chinese. → No essential control or text
   is clipped. These scales come from the campaign's visual matrix, not a new numeric warp budget.
6. [H] Warp between contexts, change dimension, reconnect and press F3+T. → Old station sky resources/cache are
   released; no wrong-body geometry, stale solar/gravity hints or persistent OpenGL errors.
7. [P] B witnesses the same successful and refused operations. → Both clients agree on station, passengers and
   outcome; a separate nearby station keeps its own context.
8. [P] Review the sequence on the second real GPU category. → Preserve a separate hardware record and judgement.

**Evidence to keep:** Phase 2 screenshots/cache and log observations, warp gameplay video, scale/language index,
phase 4 synchronized views and phase 2 second-GPU report. No numeric cache-release time is specified by the sources.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## Open questions for Root

- F01–F07: P2 supplies fixture descriptions, not archive paths, coordinates, station/account IDs, energy supply
  steps or fixed sampling windows. Publish the hashed fixture manifest and driving commands before execution.
- F01: The inventory sources do not name a current C4 10/100 benchmark entry point. Bind the accepted runner
  and sampling method; do not substitute an old JAR's timing or an empty-server run.
- F05: Supply the target-removal/cost-change data pack and its exact activation procedure.
- F06: Supply the normal deletion/permission command syntax from the pinned candidate, not a guessed command.
