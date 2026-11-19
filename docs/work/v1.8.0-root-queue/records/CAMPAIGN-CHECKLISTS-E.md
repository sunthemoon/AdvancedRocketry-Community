# Final campaign checklists E: planetary expansion

Status: planned, not executed. Covers E01-E11 of
[FINAL-CAMPAIGN-PLAN](FINAL-CAMPAIGN-PLAN.md), section 4.5. Writing these
procedures does not run the campaign, approve a Gate or enable a switch.

## Shared setup and source interpretation

Use the run conventions in `CAMPAIGN-CHECKLISTS-A.md`: P1's C4 identity
(TASK-W396 names `659bbcb6a`), `[H]` harness versus `[P]` human action,
authenticated V2 settings, two-restart DS protocol, and the campaign section-6
evidence root/filenames/result metadata. Use Java 17, Minecraft 1.20.1,
Forge **47.4.10**, **JEI absent** unless a separately identified 47.4.23 /
JEI 15.56.0.205 compatibility cell is run. All worlds are ordinary disposable
copies, **not O2's marked world**; keep admission switches off and no JVM
test properties. Do not enter a disabled bay/cargo/fuel test path for these rows.

P2 supplies **representative v1.3 to v1.7 worlds**, **a v1.0 world**, **the
reference workload world of docs/17 section 4**, and associated immutable hashes.
The planetary scene is a P2-derived disposable copy with a fueled legal rocket,
powered Satellite Terminal, Data Satellite components, supplied sealed rooms,
complete oxygen-filled equipment and authorized station access. P2 must name
actual paths, coordinates, owners, station IDs and each pack variant; the source
manuals do not provide a universal fixture placement script.

Read sources: [v1.4 version](../../../versions/V1.4.0-PLANETARY-EXPANSION.md),
[MT140-01 to MT140-09](../../../releases/v1.4.0/MANUAL-TEST.md),
[visual report](../../../releases/v1.4.0/VISUAL-VALIDATION-REPORT.md),
[G7/G8](../../../releases/v1.4.0/GATE-STATUS.md),
[navigation cases](../../v1.4.0-nav/MANUAL-CASES.md),
[sky cases](../../v1.4.0-sky/MANUAL-CASES.md),
[discovery cases](../../v1.4.0-discovery/MANUAL-CASES.md),
[celestial data guide](../../../CELESTIAL-DATA-GUIDE.md),
[discovery guide](../../../PLANETARY-DISCOVERY-GUIDE.md),
[sky guide](../../../PLANETARY-SKY-GUIDE.md) and
[migration limits](../../../releases/v1.4.0/MIGRATION-REPORT.md).
The navigation cases' old flight-protocol-7 precondition conflicts with later
discovery's protocol 8; both identify historical builds. Use matching C4
client/server and P1's actual protocol inventory, not mixed old artifacts.

The namespace below is `advancedrocketrycommunity`. Earth is the Overworld;
Moon, Mars, Venus and Space use their actual registered Levels. The gas giant
is an **unmapped body**, not a Level. `/arce celestial goto` is not a general
planet teleporter; use normal rocket travel or P2's explicitly recorded setup.
Do not invent a gas-giant dimension or use a raw Level change as discovery proof.

## E01 - Research, consecutive planetary travel and return

**Header:** Phase 2 V1 and phase 3 DS; development PC, real GPU client/account
A; ordinary copy, switches off, no properties. **Sources:** v1.4 section 10.1,
MT140-01 and DISC-V1-02/03.

**Preconditions:** P2 planetary scene with undiscovered Mars/Venus, rocket
equipment and powered terminal; baseline/JEI absent. Record inventory/fuel,
research/discovery, terrain/location and room/equipment supply before each leg.
Built-in Data Satellite needs chassis, solar module, data storage and unbound
control chip. Its built-in mission lasts **200 running-server ticks**, awards
**120 research**, and captures **100 first-discovery fee** (net **20**) when
started undiscovered; later-started missions to discovered bodies net **120**.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Manufacture the Data Satellite through the powered terminal. | Correct package/component debit and bound chip. |
| 2 | [H] Launch its Mars research mission. | Owner/target/duration/fee captured from the effective definition. |
| 3 | [H] Claim after at least 200 running-server ticks. | Built-in first-discovery net 20; Mars opens once, no fuel supplied. |
| 4 | [H] Repeat steps 1 to 3 for Venus with its own valid package/chip. | Venus research/discovery recorded without resetting Mars or private balances. |
| 5 | [H] Open the rocket console's Star map and select Mars with Use surface. | Readable ready quote; selection alone does not launch. |
| 6 | [H] Confirm Launch in the console. | Server-authorized Mars arrival with one fuel debit and intact inventory/seats. |
| 7 | [H] Capture Mars terrain and environment. | Correct body, terrain, server environment and client HUD; C4 terrain follows current generation, not obsolete v1.4 art. |
| 8 | [H] Launch the next permitted Venus leg. | Correct target/fuel quote, safe landing or explicit refusal; no silent alternate destination. |
| 9 | [H] Capture Venus terrain and environment. | Correct distinct body/terrain and intact assets. |
| 10 | [H] Save/stop the landed world. | Clean exit; stopped-state resource baseline. |
| 11 | [H] Restart the same copy and reconnect A. | Same identity, location, rocket, cargo and research. |
| 12 | [H] Launch the permitted return to Earth. | Correct arrival, one debit, no duplicated/lost asset. |
| 13 | [H] Save/stop and restart the same copy again. | Second restart retains returned state. |
| 14 | [P] Review terrain, environment, fuel and inventory comparisons. | Visible state agrees with server; legitimate consumption reconciles. |

If the effective route graph needs intermediate bodies, P2 must enumerate
those legs and quotes; the sources do not guarantee a direct Mars-to-Venus leg.
Record each actual leg rather than changing the graph to force this itinerary.

**Evidence to keep:** Phase-2 per-body/terminal/console screenshots;
`phase3-ds/E01/` per-leg quote/debit/arrival records, terrain/body observations,
inventory/research ledger and both restart projections/logs.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E02 - Shared discovery and private captured mission prices

**Header:** Phase 4, sitting 2; V2; two authenticated real accounts A/B and
clients; ordinary world, switches off, no properties. **Sources:** v1.4
section 10.2, MT140-02 and DISC-V1-01/03, DISC-V2-01/02.

**Preconditions:** Fresh undiscovered-body variant of P2 planetary scene,
baseline/JEI absent; each account owns a terminal, satellite/chip and separate
rocket. Capture both research balances (use zero-start accounts for the numeric
oracle below), discovery, target quotes and inventories. Use built-in mission
values **200 ticks / 120 reward / 100 captured undiscovered fee**.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [P] Open both rocket maps before discovery. | Mars/Venus muted/locked with full research-required explanation; Launch disabled. |
| 2 | [P] Start A's mission to the selected undiscovered body. | Captured fee 100 and owner A. |
| 3 | [P] Start B's mission to the same body before either claim. | B independently captures fee 100, not a future post-discovery price. |
| 4 | [P] Keep B's rocket console open through A's completed claim. | A gets net 20; shared discovery opens B's eligible target; B's private balance stays unchanged. |
| 5 | [H] Observe the next console navigation refresh. | Built-in guide specifies refresh within **20 ticks**; ready quote still requires explicit launch. |
| 6 | [P] Claim B's completed mission. | B also gets net 20, not 120; its start-time fee remains captured. |
| 7 | [P] Repeat each claim. | Neither private balance changes again. |
| 8 | [P] Start and claim a later mission after discovery. | Later mission fee zero; its owner earns 120, other account unchanged. |
| 9 | [P] Attempt the other account's chip/terminal operation. | Refusal without package/research mutation. |
| 10 | [P] Reconnect B. | Shared discovery and separate balances persist. |
| 11 | [H] Restart the copied server and reconnect both clients. | Same discoveries/captured fees/claim receipts; no repeat reward. |
| 12 | [H] Reload the declared discovery-rule variant while a previously ready selection is held. | Destination rules/quotes refresh; an already prepared flight is not silently rewritten or cancelled by discovery alone. |

**Evidence to keep:** Sitting-2 synchronized videos/screenshots in both
languages, both player balances before/after each claim, captured fee/mission
snapshots, refresh tick receipts, reconnect/restart/reload logs and explicit
launch observations. Zero-start net figures are conditional on that fixture,
not a reason to reset an existing account to obtain the expected total.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E03 - Private station access and gas-giant refusal

**Header:** Phase 4, sitting 2, with phase-3 DS comparisons; V2, DS; real
accounts A/B, B non-OP; ordinary world, switches off, no properties.
**Sources:** v1.4 section 10.4, MT140-03, navigation V2 cases and DISC-V2-01.

**Preconditions:** P2 private station around a discovery-locked body, A owner,
B initially nonmember; gas giant with an accessible orbit-station fixture but
**no surface Level**. Baseline/JEI absent. Record station UUID/region/member
set, discovery, both menus and one active countdown fixture. Membership changes
use supported station controls, not direct edits to observed SavedData.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [P] Request the private station before orbit-body discovery. | Discovery lock and independent access check remain in effect. |
| 2 | [P] Complete the authorized shared discovery. | Knowledge opens eligible routes but grants no station membership. |
| 3 | [P] Attempt B's nonmember station request after discovery. | Still denied; resources and station authority unchanged. |
| 4 | [P] Add B through the ordinary invitation/accept flow. | Membership only for this station. |
| 5 | [P] Request that station as B. | Member's valid quote/arrival allowed, subject to normal route/fuel checks. |
| 6 | [P] Remove B while both navigation menus remain open. | Permission refresh removes B's access without substituting a different station. |
| 7 | [P] Inspect the gas giant node in Star map. | Public metadata visible, Use surface unavailable; no invented Level. |
| 8 | [H] Submit the approved bounded gas-giant surface intent. | Server refuses even after discovery; no fuel debit, chunk load or asset change. |
| 9 | [P] Select a permitted gas-giant orbit station. | Separate valid station destination, not a generic gas-giant surface. |
| 10 | [P] Begin the recorded authorized station countdown. | Active target and fuel ledger recorded. |
| 11 | [H] Revoke/remove the selected station through the supported fixture action. | No unauthorized arrival; preserve active-plan and refresh observations. |
| 12 | [P] Change B's editable target choice. | A's active target is not replaced by B's selection. |
| 13 | [P] Cancel from the controlling client's console. | Actual cancellation target follows the active plan; no extra fuel debit. |
| 14 | [H] Save/stop and restart the station copy. | Station identity/discovery/rights persist. |
| 15 | [H] Repeat step 14 for the second restart. | Same saved rights and no unintended gas-giant Level. |

**Evidence to keep:** Sitting-2 both menu/quote videos, active/editable target
projections, membership/gas-intent/cancellation receipts, loaded-chunk and fuel
comparisons; `phase3-ds/E03/` both restart state/log sets. If the bounded
surface intent is unavailable, disabled UI alone does not close server refusal.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E04 - Exposure, passive equipment and supplied climate rooms

**Header:** Phase 2; V1; development PC real GPU/client A; ordinary copy,
switches off, no properties. **Sources:** MT140-04 and celestial data guide's
Environmental exposure section.

**Preconditions:** P2 planetary exposure variants with
`environment_effects=true` on unambiguous surfaces, survival-mode A, supplied
sealed rooms and four correctly slotted protection items. Baseline/JEI absent.
Manifest gives actual body/position/config values; sources do not provide one
ready-made world for all hazards. Keep oxygen separate from passive protection.

| Hazard | Exact source condition |
|---|---|
| Cold | Temperature **below 240 K**. |
| Heat | Temperature **above 330 K**. |
| High pressure | Pressure **above 2**. |
| Sunlight | Solar intensity **above 1.5**, daytime and direct sky visibility. |

| Step | Action, repeated for each isolated hazard and the combined-hazard fixture | Expected observation |
|---|---|---|
| 1 | [H] Capture server environment, equipment, oxygen, health and protection state. | Relevant threshold/opt-in true; no creative/spectator exemption. |
| 2 | [H] Expose the unprotected survival player for the recorded interval. | At most one **2-health-point damage attempt every 20 ticks**, not one per hazard; pressure > heat > cold > sunlight priority; localized warning. |
| 3 | [H] Equip the full passive protection set for the tested hazard. | Four count-one correctly slotted tagged ArmorItems protect that exposure, not oxygen by implication. |
| 4 | [H] Repeat exposure with suit oxygen deliberately exhausted in the supplied fixture. | Passive insulation can remain but suffocation is not prevented by the passive tag. No duplicate simultaneous suffocation attempt. |
| 5 | [H] Enter the sealed room with powered, oxygen-supplied vent. | Finite supply provides climate protection, including breathable but hot/high-pressure air. |
| 6 | [H] Open the fixture seal. | No room protection while broken/pending/unsupplied. |
| 7 | [H] Restore the seal and supply. | Correct protection returns after valid room state. |
| 8 | [H] Enter ordinary outdoor cover in the sunlight fixture. | Direct sunlight blocked; cover alone does not cool air or reduce pressure. |
| 9 | [H] Return to safety. | Exposure resets; no persistent/offline exposure debt. |
| 10 | [P] Review action-bar and oxygen HUD against the server record. | Messages distinguish oxygen from remaining climate hazards and are readable. |

Actual observed health loss may be reduced/cancelled by armor, resistance,
spawn immunity or another damage hook; keep attempts and those modifiers
visible in the record. Radiation is metadata, **not** an implemented damage
hazard. Station orbit never imports its planet's surface heat/pressure.

**Evidence to keep:** Per-hazard step screenshots/video, effective pack/tag
values, damage-attempt/health/oxygen/resource timeline, vent supply and logs.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E05 - Star-map controls, bounded large catalogs and reload

**Header:** Phase 2; V1; development PC real GPU/client A; ordinary copy,
switches off, no properties. **Sources:** v1.4 section 11.2, MT140-05 and
all six navigation V1 cases.

**Preconditions:** Fueled P2 planetary rocket, accessible station list,
long-ID body and **48-character station-name** fixture, valid **100-body**
and **128-body** catalogs, valid/invalid reload variants and missing/rejected
client-cache method; baseline/JEI absent. Run English/Chinese, GUI scales
**2/3/4**, smallest supported and large windows, windowed/fullscreen.
Exact supported pixel dimensions and full-catalog cooldown are not in the manual.

| Step | Action, for each display/catalog cell | Expected observation |
|---|---|---|
| 1 | [H] Open the rocket flight console and select Star map. | Correct catalog/quote generation, readable title/details/status. |
| 2 | [H] Click a body node. | Correct selected body and metadata; selection does not launch. |
| 3 | [H] Drag the viewport. | Bounded panning; record node/selection position. |
| 4 | [H] Scroll the viewport. | Correct zoom and hit testing. |
| 5 | [H] Click outside the clipped viewport. | No selection of invisible out-of-viewport nodes. |
| 6 | [H] Use < and > to focus every body in the current catalog. | All 100 or 128 valid nodes reachable; retain frame timing and overlap/clipping findings. |
| 7 | [H] Cycle Station n/m and hover its tooltip. | Only permitted stations; correct 48-character name and route status. |
| 8 | [H] Choose Use station or Use surface for the selected valid target. | Chosen target returns to console; no implicit launch. |
| 9 | [H] Change the fixture's fuel/component/control state. | Console quote/reason matches server legality, capacity/thrust/fuel and permission. |
| 10 | [H] Exercise BOARD and LEAVE through the console while the map workflow is active. | Current seat/menu state stays consistent; map is not an alternate authority. |
| 11 | [H] Start a valid countdown. | Active plan distinct from editable selection. |
| 12 | [H] Return to the console and cancel it. | Cancellation remains reachable and targets the active plan. |
| 13 | [H] Reload the valid data variant while the map is open. | New catalog/quote generation synchronizes; no mismatched-generation launch. |
| 14 | [H] Reload the invalid variant. | Last valid body/route pair retained with bounded readable diagnostic. |
| 15 | [H] Exercise the declared missing/rejected client-catalog case. | Safe disabled launch/fallback, not a stale ready quote. |
| 16 | [H] Request refresh within the actual full-catalog cooldown. | Rate limit respected; automatic retry/recovery time recorded. |
| 17 | [H] Reopen the menu. | Correct current catalog and quote; no stale identity. |
| 18 | [P] Review every display cell's screenshots and timing. | Full controls/tooltips/long text readable without critical clipping. |

**Evidence to keep:** Phase-2 per-cell screenshots, node/input/selection
table, raw frame times, catalog/pack hashes, generation and cooldown/recovery
receipts, active/editable target comparisons and client/server logs.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E06 - Planetary sky matrix on two real GPU categories

**Header:** Phase 2; V1 on **two real GPU categories** under O9; real client
A on each, development PC plus owner's second GPU machine; ordinary worlds,
switches off, no properties. **Sources:** v1.4 sections 11.1 and 11.5,
MT140-06, visual report, SKY-V1-01/03/04/06/07/09 and sky guide.

**Preconditions:** P2 scene for **Earth, Moon, Mars, Venus and shared Space**
with station context, valid custom profile, invalid-schema and oversized-profile
packs, and missing-profile fixture; baseline/JEI absent. Record each machine's
OS/CPU/GPU/driver/resolution/JVM, render distance, packs and no unstated rendering
mods. Repeat day/night where the type permits, English/Chinese scales **2/3/4**,
windowed/fullscreen. Gas giant has no surface sky to visit. O8 supplies frame-time
tool; Vanilla/Forge baselines come from A12 on the same machines/settings.

| Step | Action, separately on each GPU and each listed body | Expected observation |
|---|---|---|
| 1 | [H] Enter the recorded viewpoint by the permitted route. | Client Level/body/station catalog agrees with server context. |
| 2 | [H] Set day through the recorded server time control. | Where time applies, Earth normal sky, airless star field or distinct planetary sky/sun/fog as configured; no missing textures/clouds. |
| 3 | [H] Set night through the same control. | Correct time-dependent profile rather than prior-body geometry. |
| 4 | [P] Inspect horizon, sun and stars while turning the view. | No holes, severe seam, flicker, sun artifacts or persistent OpenGL errors. |
| 5 | [H] Enter the water observation fixture. | Vanilla visibility restriction preserved; sky does not draw over it. |
| 6 | [H] Enter the lava observation fixture. | Same vanilla restriction preserved. |
| 7 | [H] Enter the powder-snow observation fixture. | Same visibility restriction preserved. |
| 8 | [H] Apply the fixture's blindness effect. | Restricted visibility preserved. |
| 9 | [H] Replace it with the darkness fixture. | Darkness behavior preserved. |
| 10 | [H] Clear the effect/immersion. | Ordinary fog returns correctly. |
| 11 | [H] Change render distance. | Planetary fog never reveals beyond ordinary visible terrain. |
| 12 | [H] Enter the covered/cave viewpoint. | Additional planetary ambience stops; no sky drawn through cover. |
| 13 | [H] Enable and reload the valid custom profile with F3+T. | Intended profile replacement; authority/environment unchanged. |
| 14 | [H] Reload the invalid-schema profile variant. | Prior valid map retained with bounded diagnostic. |
| 15 | [H] Reload the oversized-file variant. | Prior map retained; profile file limit **16,384 UTF-8 bytes** enforced. |
| 16 | [H] Remove the test profile pack and reload. | Base resources restored; no persistent GPU-buffer/cache growth. |
| 17 | [H] Exercise the allowed unknown/unavailable-profile fixture. | Conservative dimension fallback, not another body's presentation; server rules unchanged. |
| 18 | [H] Compare frame times and memory with same-machine baselines. | Keep average FPS, 1% low, stalls and cache observations; no invented absolute FPS limit. |
| 19 | [P] Compare both hardware-category results. | Both have complete real-GPU observations, not V0 screenshots. |

Profile constraints also include **128** winning files, **16** JSON nesting
levels and **128-character** IDs. Invalid duplicate Level mappings cannot be
forced into the server catalog to obtain a client test; if no allowed rejected-
catalog fixture exists, keep that SKY-V1-07 branch NOT_COVERED for Root.
SKY-V1-08's pre-sky-world identity/time/light/inventory comparison is in E10.

**Evidence to keep:** `phase2-v1/<machine>/<round>/` complete body/time/
display/immersion/effect matrix, native screenshots, videos, frame-time/memory
data, profile bytes/hashes and client/server logs for **each** GPU. Hash copies
from the second machine. Do not count a fifteen-minute tour as full E06 there.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E07 - Transition lifecycle, late menus and bounded ambience

**Header:** Phase 2 V1; additionally phase 4 sitting 2 V2 for SKY-V2-01 and
navigation V2 lifecycle; real GPU A, plus B for two-client steps; ordinary
copies, switches off, no properties. **Sources:** v1.4 section 11.3,
MT140-07, SKY-V1-02/05, SKY-V2-01 and navigation V2 case 3.

**Preconditions:** P2 planetary scene and separate second world, normal audio,
baseline/JEI absent, valid profile reload pack; synchronized real-client capture.
Additional ambience is at most **one non-looping sound**, at least **400
unpaused client ticks** apart; Moon/Space add none. This is audible/client-time
behavior, not proof from server time alone.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Rapidly traverse the permitted body sequence. | No previous-body geometry, color, fog or growing stale GPU buffer state. |
| 2 | [H] Disconnect during the recorded pending menu/refresh state. | Old world/menu resources are retired. |
| 3 | [H] Join the separate world. | No prior-world sky/fog/sound; unavailable catalog uses conservative fallback. |
| 4 | [H] Return to the planetary fixture and reload profiles. | Correct fresh map/cache; no old geometry retained. |
| 5 | [H] Reconnect/change dimension/replace the test rocket in separately recorded pending-menu variants. | Late responses do not update another rocket/menu generation; IDs and actual receipt order retained. |
| 6 | [P] Listen while exposed on Mars/Venus. | At most one quiet non-looping added sound; actual spacing >=400 unpaused client ticks. |
| 7 | [P] Change the Ambient/Environment volume slider. | Added ambience follows the slider, ordinary game audio not globally muted. |
| 8 | [H] Enter cover. | Added sound stops. |
| 9 | [H] Exercise a real paused-client fixture if available. | Pause does not create a burst or reset cooldown; a multiplayer menu is not claimed to pause the world/client. |
| 10 | [H] Travel/reload/disconnect in the sound lifecycle variants. | Sound stops on each transition; cooldown not reset; no lingering audio. |
| 11 | [P] Listen on Moon and Space. | No added planetary ambience there. |
| 12 | [P] Put A and B on different planets in the V2 sitting. | Each receives its own synchronized body presentation. |
| 13 | [P] Reconnect/reload/travel A independently of B. | A's changes do not alter B's sky/sound or server environmental rules. |

**Evidence to keep:** Phase-2 audio/video, transition/body/menu/rocket IDs,
raw cache/memory observations and unpaused-tick timing; sitting-2 both-client
media/logs and separate sky/environment receipts. Preserve any unavailable
late-packet/pause branch as NOT_COVERED; do not silently drop it because it was
absent from the inventory's short summary.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E08 - Paired body/route reload, removal and restoration

**Header:** Phase 3; DS; development PC, real client A and console operator;
ordinary disposable copy, switches off, no properties. **Sources:** v1.4
section 10.3, MT140-08, DISC-MIG-01 and migration report.

**Preconditions:** P2 planetary world with visited/researched body and station,
complete backed-up packs, valid and invalid paired body/route variants, coherent
removed-body/routes/satellite-target variant and exact same-ID restoration.
Baseline/JEI absent. Capture catalog generation, destination availability,
station/progress identities and `advancedrocketrycommunity_planetary_bindings.json`.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Install the valid paired body/route pack on the copy. | Hashed candidate inputs recorded. |
| 2 | [H] Issue `reload`. | One validated body/route generation published. |
| 3 | [H] Install the declared invalid pair. | Deliberate validation fault recorded, no source world change. |
| 4 | [H] Issue `reload` again. | Previous valid pair retained; bounded diagnostic, not an empty catalog. |
| 5 | [H] Install the coherent removed-body/dependent-route/target variant. | No dangling route/mission references accidentally invalidate the intended removal test. |
| 6 | [H] Issue `reload`. | Removed body unavailable for new entry; saved Level/binding/station/discovery records not deleted. |
| 7 | [H] Attempt a new arrival to the removed body. | Safe refusal, no fallback or fuel/asset loss. |
| 8 | [H] Restore the same body identity plus valid dependent routes/targets. | No remapping/reuse of old identity or binding deletion. |
| 9 | [H] Issue `reload`. | Prior discovery/routes become usable again; paid pending discovery can finish without a second reward. |
| 10 | [H] Save/stop and restart the copy. | Station/rocket/progress/binding identities retained. |
| 11 | [H] Repeat step 10 for the second restart. | No extra migration/claim replay. |
| 12 | [H] Compare original fixture hashes and all preserved projections. | Originals intact; no unexplained resource/identity change. |

An allowed **blocked historical-progress** variant also follows DISC-MIG-01.
P2 must supply its independently backed-up state; never erase historical IDs
to make it proceed. Run this additional step list on that variant:

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Record the blocked claim, paid receipt, account totals and discovery IDs. | Complete historical baseline; unpaid READY is not treated as an already paid claim. |
| 2 | [H] Repeat the claim through the ordinary terminal. | No second reward/fee; an unavailable target or **128 historical-discovery-ID** capacity does not evict an old ID. |
| 3 | [H] Restore the same unavailable target with its valid related definitions. | Original identity retained; no replacement body with a different ID. |
| 4 | [H] Observe bounded automatic retry. | The paid discovery completes when its documented preconditions permit, without another award; capacity blockage may remain blocked. |
| 5 | [H] If still blocked, follow the documented operator recovery on a new backed-up copy. | Record the actual action and outcome; no erased history, selective store merge or manually promoted scratch file. |
| 6 | [H] Compare the complete ledger and pending state again. | Exactly one paid receipt; retained IDs/assets and any unresolved condition documented. |

The binding file separately retains **128 lifetime bindings / 32,768 UTF-8
bytes**. A `.json.pending` binding file blocks startup pending recovery; it is
not the research/discovery `.arce-pending` scratch file and must not be promoted
or deleted as a normal test shortcut. This row is S1/reload, not an S2 cut claim.

**Evidence to keep:** `phase3-ds/E08/` all pack variants, generation/reload/
refusal/restore logs, binding and station/progress projections, pending-claim
receipt/balance timeline, restart state and unchanged-original checks.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E09 - A station around a newly added body

**Header:** Phase 3; DS; development PC, real client/account A; ordinary
copy, switches off, no properties. **Source:** v1.4 section 10.5.

**Preconditions:** P2 planetary scene, discovered/accessible Mars or Venus
surface, station-eligible account with ownership capacity, legal fueled rocket;
baseline/JEI absent. Record the actual body/Level and station ownership limit.
Use ordinary station creation while standing on that mapped orbitable surface.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Arrive on the selected new surface by normal flight. | Correct body context and authorized arrival. |
| 2 | [H] Create the station through supported station controls. | New stable station UUID with that surface's orbit-body ID. |
| 3 | [H] Request the created station through the rocket console. | Correct named station/quote and permission checks. |
| 4 | [H] Travel to the station. | Shared Space Level, committed station region and correct orbit context. |
| 5 | [H] Save/stop and restart the same copy. | Station orbit, ownership, region, rocket and inventory preserved. |
| 6 | [H] Reconnect A and inspect the orbit information. | Still the chosen new body, not implicit Earth/Space. |
| 7 | [H] Repeat steps 5 to 6 for the second restart. | Stable context/rights after reopen two. |

**Evidence to keep:** `phase3-ds/E09/` creation/arrival receipts, station
UUID/orbit/member/region and environment projections, both restart logs and
client orbit screenshots. E03 is the separate nonmember/private-access matrix.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E10 - Representative upgrade worlds and pre-sky preservation

**Header:** Phase 3 DS, phase-2 V1 observation for SKY-V1-08; development PC,
real GPU client A; ordinary copies, switches off, no properties. **Source:**
MT140-09, migration report and SKY-V1-08.

**Preconditions:** P2 **representative v1.3 to v1.7 worlds**, individually
inventoried before C4, plus a pre-sky world variant for the specific sky
upgrade check. Baseline/JEI absent; matching provider/pack hashes. Never create
new state on C4 and label it an untouched historical upgrade fixture.

| Step | Action, for each source-world variant | Expected observation |
|---|---|---|
| 1 | [H] Hash the complete stopped original and its backup. | Source identity/file inventory recorded before copying. |
| 2 | [H] Copy it into a new run directory. | Original remains read-only and recoverable. |
| 3 | [H] Open the copy with C4 and matching dependencies. | Supported migration or explicitly retained refusal; no silent unknown-data loss. |
| 4 | [H] Capture stable body/Level/binding IDs, station orbits/rights, rockets/cargo/fuel, research/discovery and provider payloads. | Field-level conservation with legitimate elapsed progress reconciled. |
| 5 | [H] Capture Moon/Space positions, terrain/time/light settings and inventories in the pre-sky variant. | Same identities/settings/contents as the historical fixture; renderer changes do not migrate gameplay state. |
| 6 | [H] Save/stop and reopen the same copy. | No repeated migration/debit/reward. |
| 7 | [H] Compare the full saved projections. | Stable identities and exact accounting, not just successful startup. |
| 8 | [H] Repeat steps 6 to 7 for the second reopen. | Idempotent second migration/readback. |
| 9 | [H] Continue one representative station/rocket/research operation. | Upgraded world remains playable. |
| 10 | [P] Inspect pre-sky Moon/Space presentation. | New effects only where dimension-type effects opt in; higher-priority type overrides remain authoritative. |
| 11 | [H] Rehash every original/backup. | No original changed; failures stay with their own copied run. |

**Evidence to keep:** `phase3-ds/E10/<source>/` old runtime/world/pack
hashes, raw inventory and schemas, migration differences, both restart logs,
continued-use receipts and original-hash comparison; phase-2 pre-sky screenshots.
This is not direct 1.12.2 loading, arbitrary downgrade or a complete R-044 waiver.
Do not merge state files from different backups to claim recovery.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## E11 - Sky resource lifecycle and reference measurements

**Header:** Phase 2 V1 sky profiling and phase 5 REF; real GPU client A plus
accepted normal-user 4-vCPU/8-GiB Debian node/workload accounts; ordinary worlds,
switches off, no properties. **Sources:** v1.4 section 13.2 and GATE-1.4 G7.

**Preconditions:** P2 planetary scene and **reference workload world of
docs/17 section 4**; baseline/JEI absent, server heap <=4 GiB. Same-machine
Vanilla/Forge frame-time baselines from A12. Root supplies actual resource-
allocation/cache profiling tool and version; screenshots alone cannot prove
that sky resources are not rebuilt every frame.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Record a steady planetary-sky frame/allocation trace. | Cached resources reused, not rebuilt on each rendered frame. |
| 2 | [H] Trigger the valid resource reload. | Bounded deliberate buffer rebuild; obsolete buffers released. |
| 3 | [H] Change world/body and repeat the trace. | Correct cache invalidation, no stale geometry or monotonic retained growth. |
| 4 | [H] Sample CPU/RSS/GC/tick/network/cache under the reference workload. | Raw values tied to hardware/workload/interval and catalog size, not empty-server inference. |
| 5 | [H] Compare the client trace against its baseline. | Frame times, stalls and cache allocation reported; no invented absolute FPS/resource count threshold. |
| 6 | [H] Compare server measures against the quality budgets. | Mean/P95/P99 MSPT <=25/50/100 ms, sustained RSS <=5.5 GiB, no OOM/watchdog/unexpected forced chunks; report every CPU/network/cache finding. |

**Evidence to keep:** Phase-2 raw resource/frame/allocation traces and E06
media references; `phase5-ref/<run>/E11/` host/JVM/workload/config identity,
all raw CPU/memory/tick/network/cache samples and explicit budget comparisons.
Sources supply no extra numerical CPU/network/cache limit; report that absence,
not an invented pass criterion. Dedicated server must not load sky client classes.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## Open questions for Root

- **E01-E11:** Publish the planetary P2 manifests, exact coordinates, packs,
  recipe/mission effective values and C4 P1 identity. The original navigation
  manual's protocol 7 conflicts with later protocol 8; use matching C4 only.
- **E01:** Enumerate permitted consecutive route legs and fuel quotes; the
  sources do not guarantee direct Mars-to-Venus travel.
- **E02/E03:** Supply undiscovered-body/private-station variants without
  resetting historical player balances; confirm O5's second-client operator.
- **E03/E05/E07:** Supply bounded forbidden-surface/cache/late-packet controls
  and pending-menu timing. Disabled UI alone cannot prove a server received
  and refused an invalid intent. Do not inject a server-invalid catalog as setup.
- **E04:** Supply isolated cold/heat/pressure/sunlight and combined-hazard
  fixtures with damage modifiers controlled/recorded; the guide gives exact
  thresholds but not every ready-made fixture/coordinate.
- **E05:** Supply long-ID/48-character-name packs, 100/128-body catalogs,
  actual full-catalog cooldown and supported minimum/large pixel dimensions.
- **E06/E11:** O8/O9 still need owner equipment/tool confirmation. Supply
  the second real GPU category and profile/cache allocation observer. No
  screenshot alone can establish cache release or no per-frame rebuild.
- **E07:** Supply an actual paused-client fixture or mark the pause branch
  NOT_COVERED; opening a menu on a dedicated server does not establish pause.
  This row includes SKY-V2-01/navigation V2 lifecycle in sitting 2 because the
  source manuals require them, although the inventory summarizes E07 as V1.
- **E08:** Supply paid-pending/blocked historical-progress variants and the
  supported operator recovery route. Preserve original binding history and
  both SavedData stores; no selective backup merge or erased-ID shortcut.
- **E09/E10:** Supply a station-eligible owner, historical world identities and
  pre-sky variant. Do not invent a creation command or infer historical fixture
  identity from a newly generated candidate world.

## W396 preparation and verification record

This section replaces a separate W396 record because TASK-W396 permits changes
only to `CAMPAIGN-CHECKLISTS-A.md` through `CAMPAIGN-CHECKLISTS-E.md`. W396's
write scope is exactly those five files on `codex/v1.8.0-w396`, based on shared
commit `d8fddeaab`. The delivered documentation commit is reported in the final
worker output; its source base is not misrepresented as a tested product build.

Completed scope: one numbered action/expected-observation checklist for each
of the 56 inventory rows A01-A18, B01-B07, C01-C08, D01-D12 and E01-E11.
The files preserve blank result fields and identify fixtures, account/machine
requirements, Forge/JEI cells, switch-off ordinary-world execution, media/data
to retain and unresolved inputs. Additional source-manual checks are included
under the relevant existing row IDs rather than changing the campaign plan.

Design choices: reuse P2's fixture names, require concrete run manifests before
execution, use matching C4 artifacts instead of historical protocol pins, keep
C05's O6 disposition distinct from a pass, and keep missing tooling/values
explicit. No ADR decision, performance limit, production switch or Gate changes.

Not done: campaign execution; fixture/harness implementation; Gradle, GameTest,
native server, clients, actual V1/V2/REF/SOAK, push, merge, tag or release approval.
No automated tests are added or modified for this documentation-only task.
OpenPet was unreachable; its failed progress event did not block the work.

Commands actually used for preparation were `Get-Content`, bounded Python text
selections, `rg`, `git status --short`, `git branch --show-current`,
`git log -1 --oneline`, `git worktree list` and
`Get-CimInstance Win32_Process`. They established the task/rules, clean starting
worktree, assigned branch and independent W397 process. Source reads had no
product execution. Some broad reads exceeded the tool output limit and were
repeated as bounded sections; a nonexistent Electrolyzer-recipe lookup produced
no input and was not used as evidence. `apply_patch` created only the five
assigned outputs.

Verification performed on the five staged outputs:

| Command or check | Actual result |
|---|---|
| `python -B scripts/validate_repository.py` | Exit 0 on three runs, including the final staged check: 45 passed, 0 pending, 0 warnings, 0 failed each time. |
| Inline Python coverage/form/link audit against `git ls-tree -r --name-only d8fddeaab` and the plan's inventory | Exit 0: exactly 56 original IDs in order, 504 numbered actions, 76 links to files at the shared base; every row has preconditions, header, evidence and five blank result fields; each file under 1,500 lines. |
| `git diff --cached --check` | Initial draft failed on 90 trailing spaces in A's blank fields; all removed with a scoped patch. Recheck passed with exit 0. |
| `git diff --check`, `git diff --cached --stat`, `git diff --cached --numstat` and scoped diff review | Whitespace checks passed; only the five assigned Markdown outputs are staged. |
| Inline Python staged-scope check using `git diff --cached --name-only` | Exit 0: write scope is exactly A through E. |

Several context-only patch attempts were rejected without changes. No failed
command or unsuccessful source lookup is counted as passing product evidence.
The local documentation commit uses `docs(test): add campaign checklists for groups A-E`;
its resulting SHA is in the final worker response and discoverable with
`git log -1 -- docs/work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-E.md`.
No sealed evidence packet or additional worker record is created.

Known risks: P1/P2/P3 still need concrete run manifests and some packaged
fixture/intent/cache controls; O5/O7/O8/O9 require the owner's account, time or
equipment arrangements. Historical protocol/artifact references cannot substitute
for matching C4 identities. These are execution prerequisites/open questions,
not passed observations. No current version's complete Required Gate set is
satisfied by these written procedures.
Remaining work is Root's review of P4/open questions, P1-P3/P5-P9 preparation and
the scheduled v1.8 final campaign, not implementation of a later version.
