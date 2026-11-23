# Campaign checklists F: stations and warp

These are unrun P4 instructions for [the campaign plan](FINAL-CAMPAIGN-PLAN.md), inventory F01–F07.
Sources are [v1.5 sections 10–13](../../../versions/V1.5.0-ORBITAL-STATION-WARP.md),
[G7/G8](../../../releases/v1.5.0/GATE-STATUS.md) and the implementation rules in
[ADR-044](../../../decisions/ADR-044-STATION-WARP-LOGICAL-RELOCATION.md).
No result or Gate is approved here.

Accepted common procedures for **every F row**: `CAMPAIGN-CHECKLISTS-A.md`, section "Accepted procedures and delivered tooling",
pins C6 (C1), the four W400 `cell.json` paths/hashes, W399's commands and session
fields, C2/R535's effective display cells, C5/C10/R535's matched sampling/review
rules, R546's receipt-plus-state refusal proof, and W403's observers, evidence root
and checksum commands. Apply them before the row's actions; old generic display
wording or demands for nonexistent refusal logs do not override these decisions.

## Shared setup and evidence

- H means a harness/operator action; P means a human action or judgement. Every numbered action below has its
  expected observation after the arrow. Run scripted actions before asking for judgement.
- Use the P2 representative v1.3–v1.7 world fixture, specifically its station/warp copy. P2 does not supply archive
  names, station IDs, coordinates, owner UUIDs or a charging recipe. These **K08 supplied input/limits: station sets, two-account recipes and ordinary-energy copies; entry F-K08 (R621/R627)** in its fixture manifest
  before execution. `<station-A>`, `<station-B>`, `<target-A>` and `<target-B>` below mean those recorded values,
  not literal command arguments. Both targets must be known, orbitable, different from the starting orbit, and
  include the Earth/Tau Ceti e inter-system route. Never substitute the shared Space body as an orbit target.
- Command warp requests require the connected player's own command source, their loaded committed region,
  and a server ray pick looking at its warp core within 5 blocks. Record the player/core coordinates and view
  before a request; console-origin commands are not a substitute for player authority.
- Pin the P1 C6 commit, hosted artifact, JAR SHA-256 and Java 17. Default lane is Forge 47.4.10 without JEI;
  repeat the compatibility smoke on 47.4.23. JEI 15.56.0.205 is not needed for these commands. Record actual JEI
  presence. V1 uses a real GPU; G8 also requires the second GPU category. V2 uses two independently authenticated
  premium accounts A/B, not fake players.
- Phase 3 DS uses the packaged server on the development PC and a disposable world copy. Keep difficulty,
  view-distance and simulation-distance fixed and record their values; the sources give no mandatory numbers.
  V2 in phase 4 uses `online-mode=true`, `white-list=true`, `enforce-whitelist=true`, `enable-rcon=false` and
  only the two authenticated accounts. Phase 5 REF uses 4 vCPU/8 GiB/Debian 12, a non-root user and Java 17,
  `Xmx4G` maximum, server bound to loopback with SSH-tunnel client access, and an exclusive node.
  No campaign bay gate or JVM test property is required; production switches stay unchanged.
- Evidence root E is `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/`. Put DS evidence in
  `E/phase3-ds/<row>/`, V1 in `E/phase2-v1/<machine>/<round>/`, V2 in `E/phase4-v2/<sitting>/`, and REF in
  `E/phase5-ref/<run>/`. Keep server/client native logs, per-step UTC times, state projections, configuration,
  hardware and candidate identity. Screenshots are `<row>-<step>-<client>-<language>-<scale>.png`; videos are
  `<row>-<client>.mkv`. Index every artifact and SHA-256 in `E/results/RESULTS.md`, `results.json` and
  `FINDINGS.md`; seal with `E/SHA256SUMS.txt`. Keep failed attempts. Never modify source fixtures.
- For every row, also record Forge/JEI, machine, start/end UTC, JAR SHA-256 and FAIL finding/NOT_COVERED reason.
  Missing fixture values or instrumentation mean NOT_COVERED, not an invented command or PASS.

## Package preparation index (W456, R649)

This index resolves the package notes below; it is preparation, not execution or a Gate verdict.
Each entry gives the sealed input, its checksum-list SHA-256, the recipe/step mapping and the
remaining partial or NOT_COVERED work. Paths are external read-only inputs, never repository links.
Only K02 (W447) and K12 (W454) remain package deliveries in progress. A delivered recipe is not
an observed client result; preserve every original action, oracle, result field and budget.

### F-K06: 23 hashed data/resource packs, not blanket live qualification

Source: `W406.md; QUEUE.md row W406`. Root acceptance/disposition: **R550** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K06/SHA256SUMS.txt` | `3be8336be6e1aed8fc2859323d0ecbae9b7209f2fc8048a09944407a45992ca5` |

**Recipe and original-step mapping.** manifest.json packs[].path/rows/prerequisites/load_check and campaign_values give the per-row reload sequence: B03 route baseline/valid/invalid/unknown; B06 test_mars/locked; E01 itinerary; E05 100/128/129 and ID bounds; F05 station overlays; D04 tag pair; D08/D09 payload pair; E06/E11 sky resource packs. Run load_check at the existing row step and retain actual generations/quotes.

**Remaining coverage and reasons.** candidate_loads_run=false in this manifest. K21 separately qualified tag/payload loads, K08 loaded station baselines and K07 binding refusal; those do not prove B03's live quotes/two restarts, every catalog, sky load, removed selection or cooldown. Unperformed branches remain NOT_COVERED. Invalid packs are refusal-only, never setup. The 48-character value is a station name. Preserve the manifest's recorded generator commit and C6 JAR identity.

**Mapping in checklist F.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| F05 | packs[station_baseline.zip/station_target_removal.zip/station_cost_change.zip].load_check -> original before-due overlay/refusal/recovery actions; real countdown is K08 client work. |

### F-K08: station sets, two-account recipes and ordinary-energy copies

Source: `W440.md; W442.md; QUEUE.md rows W440/W442`. Root acceptance/disposition: **R621/R627** in `../QUEUE.md`.

| External checksum list (sealed input directory) | SHA-256 of the list |
|---|---|
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08/two/SHA256SUMS.txt` | `021440c6b39bd9864ba5106358b454e2f105103b85e2ecf3b2cbe4acb646ee52` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08/ten/SHA256SUMS.txt` | `0919948566148e793440c0bade53006f0e2486f41b485ce7210ad9149d7cc1e2` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08/hundred/SHA256SUMS.txt` | `1089dab5a1f238eb955d3d751fb2d0206b9bccebe696418ad87bb0b21ba7c773` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08/client-steps-addendum/SHA256SUMS.txt` | `405e16eea3273dec74e4f2c2904be59cd878bd4e5729b6c204aa1a0f305f8847` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08B/two/SHA256SUMS.txt` | `9e11421235fa268c33b421cb22e6eb665e3852a9f0b610cd65e5e54de4d9d693` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08B/target-removal/SHA256SUMS.txt` | `79f68839d090a25598c13197607cba0a5bb858fa67e703e79ce6110f95fbdb4c` |
| `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08B/cost-change/SHA256SUMS.txt` | `1c2bb1169b55c9f682a685b1b66b8c88344999ce6c84a4b1b19e86d345eeba80` |

**Recipe and original-step mapping.** K08 manifests stations/regions/commands and client-steps-addendum/client-steps.json variants[].client_steps.actions/approach/ordinary_creation/overlay/pending/docked_rocket map B02, E05/E09/E10 and F01-F07. Use K08 ten/hundred with k08_station_load.py for F01; use a fresh K08B two/target-removal/cost-change source-world per paid F02/F03/F05/F07 operation, retaining the same bound station IDs.

**Remaining coverage and reasons.** K08B closes only energy preparation: two A/B 3,288,096/2,902,914 FE; removal A 2,756,374; changed-cost A 8,381,014. Operator-created stations and placeholder owners are not ordinary acquisition/O5 evidence. Bind by fresh command-built outputs, never editing sealed owners. Live local consent/warp, memory-only countdown, F05 before-due overlay and two post-abort restarts, docked rocket, membership/refusals and V1/V2 remain scripted/NOT_COVERED (no saved pending or docking command). Full REF windows and direct caches remain unqualified.

**Mapping in checklist F.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| F01 | ten/hundred manifests plus k08_station_load.py -> original unloaded/loaded/repeated-baseline steps; short Windows qualification is not full REF. |
| F02 | K08B/two source-world + addendum variants[two].client_steps.actions -> original independent local quote/confirm/warp steps on fresh copies. |
| F03 | K08B/two + addendum pending/actions -> original owner/passenger reconnect countdown controls. Pending is memory-only and requires live players. |
| F04 | addendum client_steps.docked_rocket -> original dock-before-warp actions; no docking hook/source exists, so not a prepared docked fixture. |
| F05 | K08B target-removal/cost-change + corresponding addendum overlay/pending actions -> original confirmation-before-overlay/due-abort/two-restart steps. Baseline packs active, overlays disabled at start. |
| F06 | two/addendum client_steps.actions -> original invite/accept/remove/nonmember/admin-delete controls. Invite uses online name; remove uses uuid; no forced-open owner menu. |
| F07 | Fresh K08B two/cost-change copies + addendum approach/actions -> original in-system/interstellar controller/sky/cancel steps, with three-second cancellation and scales2/3. |

### F-K15: bounded observers, with explicit remaining visibility gaps

Source: `W403.md; QUEUE.md row W403; W452.md/R648`. Root acceptance/disposition: **R548; R648 for A08/A09** in `../QUEUE.md`.

**Recipe and original-step mapping.** Tracked scripts/campaign_observe/{ticks,process,jdk,host,evidence}.py and scripts/run_campaign_observer.py at implementation 385c079dc10718f781a425527cd3e9a0765a0518 supply matched-copy window/process/heap/JFR/evidence operations. Use sample_ticks/force-load queries and owned-process-only diagnostics at the row's existing sampling step; K17 schedule and K08/K09/K10 load drivers supply concrete harness integrations.

**Remaining coverage and reasons.** W403 created no sealed live fixture or SHA256SUMS.txt: none is invented. Window aggregates cannot prove whole-run percentiles/spike lengths. C6/K17's release-test raw sampler is used only in the separately labelled R648 REF run for A09. All-Forge-ticket, queue-internal, client/GPU allocation and direct cache-release proof remain NOT_COVERED where unavailable; histogram/heap/RSS proxies are labelled, not direct evidence. Actual REF acceptance/full windows and overhead checks remain unexecuted.

**Mapping in checklist F.**

| Original row/actions | Recipe selector and retained limitation |
|---|---|
| F01 | At the row's original measurement/readback actions, call tracked sample_ticks and process/owned-JFR helpers; retain source limitation above. No direct cache/all-Forge-ticket or unavailable raw-stream proof is supplied by window/histogram proxies. |
| F07 | At the row's original measurement/readback actions, call tracked sample_ticks and process/owned-JFR helpers; retain source limitation above. No direct cache/all-Forge-ticket or unavailable raw-stream proof is supplied by window/histogram proxies. |

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

**Accepted preparation/action steps (W408):**

(C8/C10, R535) Each real account owns one ordinary-created station; load-only 10/100 records use permission-2 `/arce station admin create` with distinct owner UUIDs and an operator-created label. Run the standard two-minute discarded warm-up/ten-minute intervals and two matched baselines, spread prompting Root review only. **K08 supplied input/limits: station sets, two-account recipes and ordinary-energy copies; entry F-K08 (R621/R627)**: worlds, energy/region/owner manifest and 10/100 driver; **K15 supplied input/limits: bounded observers, with explicit remaining visibility gaps; entry F-K15 (R548; R648 for A08/A09)**: live query/raw-tick observation.

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

**Accepted preparation/action steps (W408):**

(C8/C10) Use ordinary A/B-owned stations, never bypass their per-owner limit; any comparison inherits A's matched windows/review rule. **K08 supplied input/limits: station sets, two-account recipes and ordinary-energy copies; entry F-K08 (R621/R627)**: exact commands, regions, targets, energy and due-commit controls.

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

**Accepted preparation/action steps (W408):**

(C8/C10) Retain ordinary ownership and matched sampling where measured. **K08 supplied input/limits: station sets, two-account recipes and ordinary-energy copies; entry F-K08 (R621/R627)**: separate owner/passenger reconnect copies, balances and countdown commands.

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

**Accepted preparation/action steps (W408):**

(C8/C10) Keep eligible ordinary ownership and A's matched sampling where measured. **K08 supplied input/limits: station sets, two-account recipes and ordinary-energy copies; entry F-K08 (R621/R627)**: docked-rocket/station manifest, balances and commands.

**Evidence to keep:** Phase 3 logs, normal-stop writer-drain lines, countdown and committed projections, restart
comparisons, rocket screenshot and station quote.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## F05 — Catalog changes invalidate a pending warp safely

**Phase/level/machine/accounts:** 3 DS, development PC, A; optional V1 feedback capture.
**Gate:** Ordinary disposable fixture; no campaign gate.
**Preconditions:** K08 station scene plus W406's `station_baseline.zip` and exactly one
`station_target_removal.zip` / `station_cost_change.zip` overlay, hashes and outcomes below. Record original catalog generation, orbit, balance and the quote's cost/class.

1. [P/H] Request and confirm a warp to the fixture target. → A countdown exists, but no debit/relocation yet.
2. [H] Activate the target-removal pack and run `/reload` before commit. → The new catalog lacks that target.
3. [H] Observe the due countdown. → Commit rechecks and aborts with readable reason; original orbit/balance remain.
4. [H] Restore the pack and run `/reload`. → Valid target returns; no old confirmation becomes active again.
5. [H] Repeat with a catalog/config change that changes the quoted cost class. → Old consent is invalid; no warp
   or debit without a new quote and confirmation. Common-config changes require restart, not merely `/reload`.
6. [H] Restart the world twice. → Aborted work remains aborted; station and rocket resources are unchanged.

**Accepted preparation/action steps (W408):**

(C8/C10) Keep ordinary owner limits and matched comparisons. **K08 supplied input/limits: station sets, two-account recipes and ordinary-energy copies; entry F-K08 (R621/R627)**: live overlay activation boundary before the due commit, folded energy and effective cost readback. Pack generation below is not a timing qualification.

(W406) Keep baseline enabled below exactly one overlay; confirm Earth→`k06:warp_target`, then activate/reload overlay **before** due commit. Removal expects WARP_TARGET_UNAVAILABLE. Reparenting to Tau Ceti changes IN_SYSTEM→INTERSTELLAR with no Level; WARP_QUOTE_CHANGED is conditional on differing effective costs and folded balance sufficient for the larger cost (another guard can mask it). Read effective costs, do not assume defaults; `/reload` is not config reload. Verify no debit/relocation/resurrected consent, remove overlay/reload and perform two clean restarts. Activation/energy control still **K08 supplied input/limits: station sets, two-account recipes and ordinary-energy copies; entry F-K08 (R621/R627)**; actual load **K06 supplied input/limits: 23 hashed data/resource packs, not blanket live qualification; entry F-K06 (R550)**.

**Delivered K06 inputs (W406; generated, not load-qualified).** All ZIP names
below are under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K06/`.
A means source-derived acceptance conditional on setup, R refusal-only, O an
observed tag-merge outcome still required; none is a campaign result.

| ZIP | SHA-256 | Expected load/outcome |
|---|---|---|
| `station_baseline.zip` | `5f3f827703682697bcdf340b3aea74ca0b898e55a7f28cf4ca9c268844c6d271` | A; add orbitable, discovery-free `k06:warp_target`, parent Earth, no Level. |
| `station_target_removal.zip` | `088e4873cfbf46d968838b10595caabd03459de3112bca85f07894b75e97bb02` | A above station baseline; filter only that body; due commit should return `WARP_TARGET_UNAVAILABLE`. |
| `station_cost_change.zip` | `42b97ed2e5a88bfeff30723cf12f746ef60d315183a9c1f1798b280b0e599a29` | A above station baseline; parent changes to Tau Ceti, Level stays absent; class changes IN_SYSTEM → INTERSTELLAR. |


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
   membership and ownership remain unchanged. Exact command grammar still **K08 supplied input/limits: station sets, two-account recipes and ordinary-energy copies; entry F-K08 (R621/R627)**.
4. [P] A requests/confirms a valid warp. → Owner can operate the same fixture; a broken fixture is not a refusal pass.
5. [H] Compare registry, energy and chunk counts around B's attempts. → No mutation or arbitrary chunk load.

**Accepted preparation/action steps (W408):**

(C8/C10) Keep ordinary ownership/member rules; comparisons use A's matched plan. **K08 supplied input/limits: station sets, two-account recipes and ordinary-energy copies; entry F-K08 (R621/R627)**: exact deletion/permission grammar, owner/member/non-OP actions and station IDs. Refusals use R546 receipt and unchanged state, logs only where emitted.

**Evidence to keep:** Both clients' refusal/success views and logs, member-list and chunk-count comparisons in phase 4.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## F07 — Station context, warp feedback and visual cache

**Phase/level/machine/accounts:** 2 V1 on main and second GPU categories; 4 V2 with A/B.
**Gate:** Ordinary copy; no campaign gate.
**Preconditions:** P2 two-station fixture with different orbit bodies, charged core and a controller; one successful
warp, one deliberate valid refusal fixture. Record CPU/GPU/driver/resolution and initial player coordinates.

1. [H] View each station before warping. → Orbit body, star, sun and gravity hints match that station's server context.
2. [P/H] Request and confirm a warp from the controller and capture the command quote too. → Target, cost,
   countdown, state and routeless-target warning agree with the server; current C6 includes the newer controller UI.
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

**Accepted preparation/action steps (W408):**

(C8/C10; J21 finding 8-3) In separate warp runs at effective GUI scales 2/3, quote, confirm, then cancel after three seconds. Expect cancellation text, not a lingering "in 10 s", inside the panel. Non-ticking seconds remain accepted Low KI-V180-UI-01 (R542 N11), not a new fix requirement. **K08 supplied input/limits: station sets, two-account recipes and ordinary-energy copies; entry F-K08 (R621/R627)**: countdown/cancel scenes; direct context cache evidence **K15 supplied input/limits: bounded observers, with explicit remaining visibility gaps; entry F-K15 (R548; R648 for A08/A09)**.

**Evidence to keep:** Phase 2 screenshots/cache and log observations, warp gameplay video, scale/language index,
phase 4 synchronized views and phase 2 second-GPU report. No numeric cache-release time is specified by the sources.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## Open questions for Root

- **Answered:** C8 ordinary A/B ownership and distinct-owner operator load records; C10/R535 sampling/review; W406 baseline/overlay filenames, hashes and outcomes; finding 8-3 cancellation recheck and R542 Low non-ticking notice.
- **F01-F07:** station worlds/regions/owners/energy, exact local warp/deletion/member commands and 10/100 driver **K08 supplied input/limits: station sets, two-account recipes and ordinary-energy copies; entry F-K08 (R621/R627)**.
- **F01/F07:** live query/tick/direct-cache observation **K15 supplied input/limits: bounded observers, with explicit remaining visibility gaps; entry F-K15 (R548; R648 for A08/A09)**.
- **F05:** actual loads **K06 supplied input/limits: 23 hashed data/resource packs, not blanket live qualification; entry F-K06 (R550)**; before-commit overlay timing, differing effective costs and sufficient folded balance **K08 supplied input/limits: station sets, two-account recipes and ordinary-energy copies; entry F-K08 (R621/R627)**. No filename is missing, but generation does not prove due-commit behavior.
- O9 supplies second GPU/time arrangements; ordinary refusal steps use R546 receipt and unchanged-state proof.
