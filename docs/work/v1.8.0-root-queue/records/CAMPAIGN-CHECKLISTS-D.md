# Final campaign checklists D: public API and compatibility

Status: planned, not executed. Covers D01-D12 of
[FINAL-CAMPAIGN-PLAN](FINAL-CAMPAIGN-PLAN.md), section 4.4. No API or release
Gate is approved. See `CAMPAIGN-CHECKLISTS-A.md` for candidate
identity, `[H]` harness/`[P]` human markers, the two-restart DS protocol,
authentication and section-6 evidence names/metadata.

Accepted common procedures for **every D row**: `CAMPAIGN-CHECKLISTS-A.md`, section "Accepted procedures and delivered tooling",
pins C4 (C1), the four W400 `cell.json` paths/hashes, W399's commands and session
fields, C2/R535's effective display cells, C5/C10/R535's matched sampling/review
rules, R546's receipt-plus-state refusal proof, and W403's observers, evidence root
and checksum commands. Apply them before the row's actions; old generic display
wording or demands for nonexistent refusal logs do not override these decisions.

## Common setup and sources

Use P1's C4 identity (TASK-W396 names `659bbcb6a`) and P2 **the API fixture
mod against C4**, independently built from the API classifier, plus P2 world
copies. The fixture is disposable test content, not a gameplay dependency.
Record its JAR SHA-256, API publication hash, compiler/boundary report and
effective provider/pack variants. Never install the API-classifier JAR as a mod.

Default cell: Minecraft 1.20.1, Java 17, Forge **47.4.10**, **JEI absent**,
matching mandatory host/fixture mods on the real client and server. D11 names
the additional four-cell matrix. Worlds here are ordinary disposable worlds,
**not O2's marked bay/cargo/fuel world**. Keep production switches off. No JVM
test property is needed except the explicit startup-only variants named below;
record those in each run. Do not enable the separate gated escrow-fuel path.
Server-only fixture diagnostic commands/actors cannot replace real-player UI.

Read sources: [v1.3 version](../../../versions/V1.3.0-PUBLIC-API-COMPATIBILITY.md),
[M130-01 to M130-11](../../../releases/v1.3.0/MANUAL-TEST.md),
[G7 remaining measurements](../../../releases/v1.3.0/GATE-STATUS.md),
[fixture README](../../../../compat-test-mod/README.md), and
[migration/removal limits](../../../releases/v1.3.0/MIGRATION-REPORT.md).
Historical protocol 6/7/8 and API-minor numbers in these sources identify their
own builds, not the C4 wire pin. Match current client/server artifacts.

The scene **needs package K21** to publish paths, owners, positions, slot/metadata ledgers, known
unsupported container and rejected-provider controls. The manual tables do not
give all those inputs; unresolved controls are listed at the end.

## D01 - Fresh API integration lifecycle and real reconnect

**Header:** Phase 3 DS startup; phase 4 sitting 1 V2; development PC/server,
two authenticated real clients/accounts A/B; ordinary world, switches off,
no variant properties. **Sources:** v1.3 section 10.1, M130-01; duplicate
registration rejection in v1.3 section 10.4 is included here, not dropped.

**Preconditions:** Fresh disposable world; host and P2 matching fixture JARs;
baseline/JEI absent. Fixture normal registration performs two deliberate invalid
registration attempts: duplicate adapter ID with another valid type, and new
ID claiming an already registered type. These are one fixture mod's collisions,
not proof of two unrelated mod authors' interoperability.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Start the host plus fixture on the packaged server. | Correct API/loader diagnostics, normal adapter registered once, both deliberate collisions refused; no client-only linkage. |
| 2 | [H] Record the installed host/fixture/API identities. | Exactly the P1/P2 artifacts; API classifier not loaded as gameplay mod. |
| 3 | [H] Save/stop the new world. | Clean exit. |
| 4 | [H] Restart that same world. | Same registration/freeze behavior and stable world data. |
| 5 | [H] Repeat steps 3 to 4 for the second restart. | Repeated startup does not duplicate runtime registrations or persisted state. |
| 6 | [P] Connect A on the authenticated phase-4 server. | Matching mandatory integration accepted. |
| 7 | [P] Connect B. | Independent real account joins and sees the same integration-backed world. |
| 8 | [P] Reconnect B using the same signed-in client. | Shared state remains correct without duplicated inventories/entities. |
| 9 | [H] Compare server and both clients' diagnostics/state. | Correct identities, API compatibility and no client-only server class load. |

**Accepted preparation/action steps (W408):**

(C1/C15) **Needs package K01**: API/source JARs and independently built `arce-adapter-compat-test-1.0.0.jar`, hashes and boundary reports. **Needs package K21**: actual installed/omitted-client connections. Quote metadata's required-both-sides/no-display-test-exemption policy as expectation, but report measured connections separately; an omitted mandatory mod that connects is a finding. No client policy is inferred from metadata alone.

**Evidence to keep:** `phase3-ds/D01/` startup/restart logs, registration
receipts and JAR manifest; `phase4-v2/sitting-1/` both client logs/screenshots,
reconnect times and shared-state projections. Missing collision observability
is recorded as a coverage gap, not assumed from successful startup.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## D02 - External cargo round trip and explicit fuel disposal

**Header:** Phase 3; DS; development PC, client/account A; ordinary copy,
switches off, no properties. **Sources:** v1.3 section 10.2, M130-02 and
[adapter flight runner](../../../../scripts/run_v130_adapter_flight_smoke.py).

**Preconditions:** P2 API fixture container installed on a legal rocket, exact
named-item/metadata inventory captured; baseline/JEI absent. The runner's
five-block origin is Earth **(264,101,264)** with assembler **(264,100,264)**;
tank **(263,101,264)** and external container **(265,101,264)**. Use the full
runner/P2 layout, not those two blocks alone. Its unchanged default-route
fixture has mass 210, capacity 1,000 and per-leg quote **372**; verify those
effective values before applying numeric expectations on C4.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Capture every cargo slot, item identity and metadata. | Complete equality baseline, not item-count-only evidence. |
| 2 | [H] Assemble through the normal assembler. | One captured external adapter payload and one rocket authority. |
| 3 | [H] Fuel to the recorded 1,000-unit initial level. | Loader/rocket resources reconcile; helper fueling, if used, is explicitly labelled fixture-assisted. |
| 4 | [H] Select and launch the Moon route. | Default fixture quote 372; fuel 1,000 ->628, cargo intact. |
| 5 | [H] Save/stop and restart after Moon landing. | Same logical rocket/cargo and committed debit. |
| 6 | [H] Launch the return without refueling. | Default fixture fuel 628 ->256, one debit for this leg. |
| 7 | [H] Save/stop and restart after Earth landing. | Second restart preserves exact cargo and fuel. |
| 8 | [H] Request disassembly without remaining-fuel consent. | Refusal leaves cargo, fuel and authority unchanged. |
| 9 | [H] Confirm the exact remaining-fuel disposal through the supported control. | Only explicit matching consent permits disassembly; record 256 units discarded for this default fixture. |
| 10 | [H] Compare the restored native container against step 1. | Every slot/metadata equal; one block inventory authority, no competing rocket or dropped duplicates. |

The source runner exercises `discard-fuel <amount>` consent; never substitute
the historical amount if C4's effective quote differs. Fuel deliberately disposed
with consent is not claimed as recovered fuel or unexplained loss.

**Evidence to keep:** `phase3-ds/D02/` complete cargo SNBT, snapshots,
flight/debit receipts, both restart logs, refused/confirmed disposal receipts
and raw restored container. Keep real-client boarding/console media distinct
from the no-player native runner's supporting evidence.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## D03 - Unsupported container and rejected provider feedback

**Header:** Phase 2; V1; development PC real GPU/client A; ordinary copy,
switches off; no properties unless P2 defines an approved fixture control.
**Sources:** v1.3 section 11.2 and M130-03.

**Preconditions:** Baseline/JEI absent; P2 API fixture variants for a known
unsupported container and an intentionally rejected provider, plus a healthy
supported variant. Each has recorded source blocks and complete inventory.
Fault callbacks exposed only inside GameTests are not ordinary player controls;
C16 records that callback branch NOT_COVERED at V1, because no packaged control exists.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Inspect the unsupported-container rocket in the assembler. | Readable bounded refusal naming the unsupported condition. |
| 2 | [H] Compare the source structure/inventory with its baseline. | No extraction, loss, drops or second authority. |
| 3 | [H] Inspect the intentionally rejected-provider variant. | Readable bounded provider failure; no partial commit. |
| 4 | [H] Compare that source with its baseline. | Source resources and metadata untouched. |
| 5 | [H] Retry the healthy supported variant. | Normal assembly succeeds without residual failure state. |
| 6 | [P] Review the diagnostics in English/Chinese at scales 2/3/4. | Reason visible, useful and not clipped or an unbounded payload dump. |

**Accepted preparation/action steps (W408):**

(C16) Rejected-provider callbacks are NOT_COVERED at V1 (and slow callbacks at REF/D12): fault controls exist only in GameTests. Cite their A1 result as A1, not client/REF evidence. A finite extra control still **needs package K18** and Root scope approval. The healthy/unsupported packaged scene **needs package K21**.

**Evidence to keep:** Per-variant phase-2 assembler screenshots, fixture
control/identity, exact before/after source projections, refusal and healthy-retry
logs. If the packaged rejection control is missing, mark that branch NOT_COVERED.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## D04 - Integration-provided atmosphere boundary

**Header:** Phase 2 V1 and phase 3 DS; development PC, real GPU client A;
ordinary copy, switches off, no properties normally. **Source:** M130-04;
fixture README's boundary contract.

**Preconditions:** P2 API fixture Moon room with supplied vent and
`arce_adapter_test:state_boundary` in its wall; baseline/JEI absent. Its open
state retains a full collision cube, but the API rule makes it permeable.
Boundary/vent positions **needs package K21**; W406 supplies the exact
runner tag variants and hashes below (C17).

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Observe the closed supplied room. | Server room authority and client oxygen feedback agree. |
| 2 | [H] Change the fixture boundary to its open state. | Protection revoked by the API state rule despite full-cube collision. |
| 3 | [H] Restore the closed state. | Correct bounded rescan and supplied-room protection. |
| 4 | [H] Install the recorded supported tag override. | Only the declared boundary behavior changes. |
| 5 | [H] Issue `reload`. | Old room/scan results invalidate; feedback follows the newly effective tags/rules. |
| 6 | [H] Restore the original tag pack and reload. | Original authority returns without stale supply. |
| 7 | [H] Save/stop and restart the same copy. | Boundary state, supply/resources and rules remain coherent. |
| 8 | [H] Repeat step 7 for the second restart. | No reset or stale protection. |
| 9 | [P] Review the visible oxygen transitions. | Each screenshot corresponds to the server's actual supplied state. |

For a separately recorded missing-rule variant,
`-Darce_adapter_test.skipAtmosphereBoundary=true` omits the rule but keeps the
block. `-Darce_adapter_test.failAtmosphereBoundary=true` is a deliberate
startup-compilation failure, not the normal visible-room case.

**Accepted preparation/action steps (W408):**

(C17; W406) Use only the atmosphere runner's own exact tag override, not an invented replacement tag. Enabled is additive with `arce_adapter_test:state_boundary`; disabled has empty additive values and removes only that pack's contribution. Capture actual tag membership/diagnostics and vent/boundary authority; do not promise a merge outcome without the installed fixture. **Needs package K21**: Moon room coordinates/supplies; **needs package K01**: binary; **needs package K06**: live load qualification.

**Delivered K06 inputs (W406; generated, not load-qualified).** All ZIP names
below are under `D:/GitHub/ARCE-Task-Evidence/final-campaign/659bbcb6a/fixtures/K06/`.
A means source-derived acceptance conditional on setup, R refusal-only, O an
observed tag-merge outcome still required; none is a campaign result.

| ZIP | SHA-256 | Expected load/outcome |
|---|---|---|
| `atmosphere_tag_enabled.zip` | `c6ecc70d5f57927bfddfa136efa1f27318bcbc48dfc422ebd264a3f88d10eb1a` | O; exact runner metadata and additive sealing tag containing `arce_adapter_test:state_boundary`. |
| `atmosphere_tag_disabled.zip` | `a95ef0dd725b39dd92e626126255a4005bd17d754bc952e41e9925932fd28081` | O; exact runner metadata and empty additive values; removes only this pack's contribution. |


**Evidence to keep:** Phase-2 HUD/boundary images; `phase3-ds/D04/` state/tag
packs, vent oxygen/energy ledger, invalidation/restart logs and raw room data.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## D05 - External oxygen armor with a real player

**Header:** Phase 2; V1; development PC real GPU/client A; ordinary world,
switches off, no properties. **Sources:** v1.3 section 11.3, M130-05 and
fixture README's equipment limits.

**Preconditions:** P2 API fixture registers **vanilla leather armor** as the
external suit with its owned oxygen envelope; baseline/JEI absent. Prepare
vacuum, the required correctly slotted pieces, known oxygen and exact refill
canister count. Manifest must give initial oxygen/capacity and debit interval;
M130-05 does not specify them. No suitSmoke FakePlayer substitutes for A.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Equip the registered complete external suit on real A. | Visible leather equipment and server-owned oxygen data agree. |
| 2 | [H] Enter vacuum for the recorded life-support interval. | Server protection/oxygen debit and HUD match the provider; no invented oxygen from the client. |
| 3 | [H] Use the recorded refill canister through normal interaction. | Exact oxygen increment and canister debit/remainder, once. |
| 4 | [H] Disconnect A. | Normal player data saved, not only a chest fixture. |
| 5 | [H] Reconnect A with the same account. | Real equipment/oxygen/HUD retained, no refill replay. |
| 6 | [P] Compare visible equipment/HUD with server state. | Same protection feedback quality as native equipment; no missing model or critical clipping. |

**Accepted preparation/action steps (W408):**

(C17) Measure the real player's consumption/refill over a recorded interval with raw suit and canister ledgers. Actor figures 999, 998 and 1,997 are not expectations. **Needs package K21**: actual equipment/oxygen/refill amounts, interval, HUD and reconnect controls.

**Evidence to keep:** Phase-2 equipment/HUD/refill/rejoin screenshots, real
player ItemStack before/after data, canister ledger and all logs. The fixture's
twenty-manually-dispatched-tick actor test is supporting A1/S1, not this V1 case.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## D06 - Frozen registered fuel batch and one remainder

**Header:** Phase 2 V1 and phase 3 DS; development PC real GPU/client A;
ordinary copy, switches off; normal variant has no property. **Sources:**
v1.3 section 11.1, M130-06, fixture README and
[fixture fuel definitions](../../../../src/adapterTest/java/io/github/sunthemoon/arceadaptertest/FixtureRocketFuels.java).

**Preconditions:** Baseline/JEI absent; P2 API fixture's legal **1,500-unit**
tank, partly filled to **F=750** (C17 procedure parameter), ordinary
item Fuel Loader and one **blaze powder** registered as
`arce_adapter_test:large_fuel`: **1,573 units**, remainder **one stick**.
Record F before insertion. This tests the ordinary item-fuel path, not the
production-disabled escrow fuel-loader admission. No direct rocket-fuel edit.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Insert the one blaze powder through the normal loader input/hopper. | One accepted whole-item batch captured with owner/target and remainder. |
| 2 | [H] Allow transfer until the tank is full. | Transfer is 1,500-F; frozen batch retains 73+F units; remainder not issued early. |
| 3 | [H] Save/stop and restart the copied server. | Partial units, captured fuel identity and remainder persist. |
| 4 | [H] Compare loader/tank state after restart. | No second item consumption, partial-batch loss or repeated transfer. |
| 5 | [H] Complete the captured batch through the declared eligible target fixture. | All remaining units accounted for; one stick returned exactly once. |
| 6 | [H] Save/stop and restart the same copy again. | No repeated stick or fuel grant on the second restart. |
| 7 | [H] Inspect fuel/component names and icons in the actual client. | Readable correct identity and diagnostics. |
| 8 | [P] Review the fuel/remainder ledger and visible feedback. | Counts reconcile exactly; no missing icon or clipped essential value. |

A separate definition-change variant may restart with
`-Darce_adapter_test.fuelVariant=updated` (**2,001 units and bowl** for a new
blaze-powder batch); an existing captured batch must remain 1,573/stick. Do not
replace its captured values with the new definition or assume the old 73-unit
empty-start native fixture proves every partial starting amount.

**Accepted preparation/action steps (W408):**

(C17) Begin at F=750 of 1,500 capacity (half capacity, a procedure parameter), through the ordinary item loader: the first fill moves 750 and retains 823 units of the captured 1,573 batch; one stick is issued only at completion. Keep this component/item-loader case separate from FI29 escrow fuel. **Needs package K21**: eligible completion target and ordinary-loader scene.

**Evidence to keep:** Phase-2 fuel/name/icon images; `phase3-ds/D06/` F,
input/buffer/rocket/remainder ledger, schema/batch envelope, startup variants,
two restart logs and raw data. Unknown/malformed roots remain protected, not
cleared to continue the test.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## D07 - Captured engine/tank values across definition changes

**Header:** Phase 3; DS; development PC, client A; ordinary copy, switches
off; startup properties only as listed. **Source:** M130-07, fixture README,
[component runner](../../../../scripts/run_v130_component_smoke.py).

**Preconditions:** P2 API fixture, baseline/JEI absent, zero fuel.
At Earth origin **(264,101,264)** place diamond block; emerald block
**(263,101,264)**; oak planks **(264,102,264)**; gold block **(264,103,264)**;
quartz block **(265,101,264)**; assembler **(264,100,264)**. These supply engine,
tank, seat, guidance and structure respectively. Normal five-block snapshot:
mass **254**, thrust **2,400**, capacity **1,500**, one engine/seat/guidance.
Updated definitions give mass **294**, thrust **2,800**, capacity **2,000**.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Assemble with normal definitions. | Captured five-block values are 254/2,400/1,500; fuel zero. |
| 2 | [H] Save/stop the copied server. | Complete captured numeric snapshot preserved. |
| 3 | [H] Restart with `-Darce_adapter_test.skipRocketComponents=true`. | Definitions omitted but blocks remain installed; existing rocket still uses captured values. |
| 4 | [H] Capture its snapshot and visible statistics. | Same old values and identity; no registration-time recalculation. |
| 5 | [H] Save/stop again. | Same snapshot saved. |
| 6 | [H] Restart without skip, with `-Darce_adapter_test.componentVariant=updated`. | Updated registry active; old snapshot remains 254/2,400/1,500. |
| 7 | [H] Disassemble the zero-fuel rocket. | Exactly one restored structure. |
| 8 | [H] Reassemble under the updated definitions. | New capture is 294/2,800/2,000, not the former values. |
| 9 | [H] Save/stop and restart with the updated variant. | New snapshot persists; no second capture or resource duplication. |

**Evidence to keep:** `phase3-ds/D07/` all four process logs, explicit
property lists, old/new snapshot hashes/numbers, UI captures and native saved
fuel/seat/component projections. A missing-definition case is not missing blocks.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## D08 - Registered payload, stale menu and replayed claim

**Header:** Phase 2 V1 and phase 3 DS; development PC real GPU/client A;
ordinary copy, switches off, no actor-smoke properties. **Sources:** M130-08,
fixture README and [payload registration](../../../../src/adapterTest/java/io/github/sunthemoon/arceadaptertest/SatellitePayloadFixture.java).

**Preconditions:** P2 API fixture's powered terminal owned by real A, chassis,
solar module, unbound chip and **untagged amethyst shard** for
`arce_adapter_test:research_payload`. Baseline/JEI absent. Registered mission:
**400 ticks**, reward **137**, first-discovery fee **11**, targets Earth/Moon.
Capture actual target discovery and start-time fee; do not assume every target
charges 11. Use W406's hashed `satellite_payload.zip` then
`satellite_payload_changed.zip` below for the open-menu reload.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Manufacture the payload satellite through A's production terminal. | Correct components consumed and chip/package bound to registered identity. |
| 2 | [H] Launch a permitted target mission. | 400-tick duration, 137 reward and applicable captured fee recorded. |
| 3 | [H] Keep the terminal menu open during the declared definition reload. | Old menu generation becomes stale. |
| 4 | [H] Attempt its old bounded selection action. | Stale action refused; no duplicate mission or resource mutation. |
| 5 | [H] Reopen the terminal. | Fresh generation and readable current target list. |
| 6 | [H] Wait until the started mission is actually ready. | Started task keeps captured duration/reward/fee despite definition change. |
| 7 | [H] Claim through the real player's terminal. | One captured reward minus captured fee; owner-only claim and correct discovery. |
| 8 | [H] Repeat the claim. | No second reward or fee. |
| 9 | [H] Save/stop and restart the copy. | Claim receipt and balance persist. |
| 10 | [H] Repeat step 9 for the second restart. | No replayed award on either reopen. |
| 11 | [P] Review target, stale-menu and claim feedback. | Real-client UI agrees with server state. |

**Accepted preparation/action steps (W408):**

(C14; W406) Start/capture the real mission at 400/137/11 (fee conditional on discovery) **before** applying changed pack 20/211/11 while its menu is open; the task keeps captured values and claims once. Use tool receipt/unchanged state, not an assumed server log, for stale/replay cases. **Needs package K18**: bounded stale/permission/range probes and A1 fallback names; **needs package K21**: real terminal/chip/equipment scene; **needs package K06**: live generation/load checks.

**Delivered K06 inputs (W406; generated, not load-qualified).** All ZIP names
below are under `D:/GitHub/ARCE-Task-Evidence/final-campaign/659bbcb6a/fixtures/K06/`.
A means source-derived acceptance conditional on setup, R refusal-only, O an
observed tag-merge outcome still required; none is a campaign result.

| ZIP | SHA-256 | Expected load/outcome |
|---|---|---|
| `satellite_payload.zip` | `feac2b6a20d1fc241eaea4100525851829a8ffea2606ca5204b5627cfa30b37b` | A; registered `arce_adapter_test:research_payload` values: 400 ticks, reward 137, fee 11, targets Earth/Moon. |
| `satellite_payload_changed.zip` | `b6cafb4e45e3b97c53513c217137e3de8b8af9507fbff73f4dab10fb8f55570a` | A; exact runner override: 20 ticks, reward 211, fee 11, same targets. |


**Evidence to keep:** Phase-2 real-player terminal images; `phase3-ds/D08/`
definition bytes/hashes, chip/mission snapshots, generation/claim receipts,
research ledger and restart comparisons. No actor-only `arce_sat_fixture`
result counts as the actual real-player menu observation.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## D09 - Non-owner and bounded hostile integration intents

**Header:** Phase 4, sitting 1; V2; authenticated real clients/accounts A/B,
B non-owner/non-OP; ordinary world, switches off, no properties. **Sources:**
v1.3 section 10.5 and M130-09.

**Preconditions:** P2 API fixture cargo/rocket/terminal owned by A;
baseline/JEI absent; exact resources, positions and loaded-chunk baseline.
Root provides an approved bounded stale/out-of-range intent method; standard
window input alone may not send an invalid intent after a menu closes.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [P] Attempt B's protected inventory operation. | Denial; contents unchanged. |
| 2 | [P] Attempt B's rocket control/disassembly operation. | Denial; fuel, snapshot, seats and authority unchanged. |
| 3 | [P] Attempt B's terminal launch/claim operation. | Denial; A's package/chip/reward untouched. |
| 4 | [H] Submit the bounded stale-menu intent with its recorded identity. | Refusal; no reuse of an old valid session. |
| 5 | [H] Submit the bounded out-of-range intent. | Refusal without forced loading of arbitrary coordinates. |
| 6 | [H] Compare resources and loaded chunks against the baseline. | No unauthorized delta or log flood. |
| 7 | [P] Perform a valid A action as the positive control. | Normal owner operation still works once. |

**Accepted preparation/action steps (W408):**

(C14 corrected by R546; W406) The two payload packs supply registration/reload context only, not hostile packet probes. Use their real open-menu generations for stale cases, record the tool's receipt and authoritative resources/chunks read back; use a log only if that handler writes it. **Needs package K18**: ordinary-client bounded stale/range/permission probes; unreachable cases are NOT_COVERED with reason and named A1 test. **Needs package K21**: A-owned payload/cargo/terminal scene and **needs package K06**: live pack loads.

**Delivered K06 inputs (W406; generated, not load-qualified).** All ZIP names
below are under `D:/GitHub/ARCE-Task-Evidence/final-campaign/659bbcb6a/fixtures/K06/`.
A means source-derived acceptance conditional on setup, R refusal-only, O an
observed tag-merge outcome still required; none is a campaign result.

| ZIP | SHA-256 | Expected load/outcome |
|---|---|---|
| `satellite_payload.zip` | `feac2b6a20d1fc241eaea4100525851829a8ffea2606ca5204b5627cfa30b37b` | A; registered `arce_adapter_test:research_payload` values: 400 ticks, reward 137, fee 11, targets Earth/Moon. |
| `satellite_payload_changed.zip` | `b6cafb4e45e3b97c53513c217137e3de8b8af9507fbff73f4dab10fb8f55570a` | A; exact runner override: 20 ticks, reward 211, fee 11, same targets. |


**Evidence to keep:** Sitting-1 both-client media/logs, sender/menu/target
identities, bounded intent specification, exact resource/loaded-chunk comparisons
and refusal receipts. Record unexercised intent branches as NOT_COVERED.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## D10 - Missing provider versus missing fixture mod

**Header:** Phase 3; DS; development PC packaged server, real client only
where the mandatory mod set can connect; ordinary disposable copies, switches
off. **Sources:** v1.3 section 10.3, M130-10, migration report and fixture README.

**Preconditions:** Two independently backed-up copies of the P2 API fixture
world with a captured external inventory and exact raw snapshot/journal data;
baseline/JEI absent. First copy keeps fixture blocks/items installed; second
uses a controlled removal of only the copied fixture JAR. Original artifacts,
ordinary player worlds and historical evidence are never removed or repaired.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Start the provider-only copy with `-Darce_adapter_test.skipRocketAdapter=true`. | Registration explicitly skipped while native block/type/tag remain installed. |
| 2 | [H] Attempt destructive restoration/disassembly of the captured inventory. | Documented refusal; opaque payload and authority retained, no lossy fallback. |
| 3 | [H] Save/stop that copy. | Raw retained data available for comparison. |
| 4 | [H] Restart without the skip property. | Exact provider restored; recovery becomes possible. |
| 5 | [H] Repeat the authorized restoration. | Exact original cargo/metadata restored once. |
| 6 | [H] Stop the independently prepared second copy before removing its copied fixture JAR. | No live file removal; untouched backup remains. |
| 7 | [H] Start that second copy without the fixture mod. | Missing-block/item/provider diagnostics distinguished; captured opaque data preserved where the documented contract applies. |
| 8 | [H] Archive the native saved state and refusal diagnostics. | No manual repair or omitted failed data. |
| 9 | [H] Stop the second copy. | Process exits before dependency replacement. |
| 10 | [H] Reinstall the exact hashed fixture JAR on the second copy. | Original registration/payload version restored. |
| 11 | [H] Restart and recover through the supported operation. | One cargo authority with complete metadata; no duplicate journal application. |
| 12 | [H] Save/stop and restart recovered copies again. | At least two restarts per tested copy; exact recovered contents persist. |
| 13 | [H] Rehash original worlds/artifacts. | Originals remain unchanged. |

**Accepted preparation/action steps (W408):**

(C18) Use two fresh independent removal copies under `D:/GitHub/ARCE-Task-Evidence/final-campaign/659bbcb6a/fixtures/`, never the source. Take complete file inventories/hashes before removing the copied mod. **Needs package K21**: concrete copy paths and captured-payload inventories.

**Evidence to keep:** `phase3-ds/D10/<variant>/` copied-mod manifests,
property lists, all logs/exits, raw retained/restored payloads and field
comparisons. Known missing-registry diagnostics stay identified as such, not
suppressed generically. This does not guarantee arbitrary missing ordinary world
blocks/items, creative destruction, downgrade or physical power-loss recovery.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## D11 - Declared optional-client combinations

**Header:** Phase 3; DS; development PC and matching real client A; ordinary
copies, switches off, no properties. **Sources:** v1.3 section 11.4, M130-11;
campaign P3 and D9/O6 support scope.

**Preconditions:** P2 API fixture; matching mandatory C4/fixture installed
on both sides. P3 cells: Forge **47.4.10** and **47.4.23**, client JEI
**absent** and **15.56.0.205**. Dedicated server needs no optional client
integration. Do not remove the mandatory fixture JAR and call it optional.
No unselected rendering/pipe/other mod is part of the declared support matrix.

| Step | Action, separately for each of the four cells | Expected observation |
|---|---|---|
| 1 | [H] Start the packaged server without optional client integration. | Dedicated readiness without client-only linkage. |
| 2 | [H] Connect the matching real client with the selected JEI state. | Supported combination connects; identities and optional state recorded. |
| 3 | [H] Open the integration-backed rocket, fuel and terminal interfaces. | Permitted optional absence remains usable; state/resources intact. |
| 4 | [H] Save/stop and restart the same copy. | Correct state and no side-only dependency failure. |
| 5 | [H] Reconnect A and compare state. | Client optional state does not corrupt mandatory data. |
| 6 | [H] Repeat steps 4 to 5 for the second restart. | Same result after reopen two. |

**Accepted preparation/action steps (W408):**

(W400) The four named cells in A are the entire declared optional matrix; this input is answered, not a request to appoint another mod. **Needs package K01**: matching mandatory fixture binary; **needs package K21**: per-cell installed/omitted-client observations and restarts.

**Evidence to keep:** `phase3-ds/D11/<forge>-<jei>/` mod manifests,
server/client logs, startup/restart exits and real-client screenshots. If an
additional declared optional integration exists, Root supplies its exact support
cells before claiming this row complete; unsupported combinations must fail
clearly, not silently corrupt state.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## D12 - Reference integration CPU, network and cache cost

**Header:** Phase 5; REF; accepted 4-vCPU/8-GiB Debian 12 node, normal user,
two workload accounts/equivalent under O7; ordinary copies, switches off,
no properties. **Source:** v1.3 Gate G7; [quality budgets](../../../17-V1PLUS-QUALITY-BUDGETS.md).

**Preconditions:** P2 reference workload plus API fixture, baseline/JEI
absent, heap <=4 GiB. Record comparable no-extension workload and integration
workload; samplers must cover CPU, RSS/GC, per-tick times, packet bytes and
cache/query work. Sources provide no integration-specific CPU/network/cache
percentage threshold; retain measured values and the actual frozen limits.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Sample the declared no-extension reference interval. | Comparable baseline with exact configuration/workload. |
| 2 | [H] Sample the declared integration workload interval. | Raw CPU/memory/tick/network/cache data under identical host conditions. |
| 3 | [H] Exercise assembly/disassembly boundaries with the supported container. | Adapter serialization at transaction boundaries, not every tick. |
| 4 | [H] Exercise repeated environment queries and a declared invalidation. | Bounded cache and correct invalidation, no forced chunk loads. |
| 5 | [H] Exercise the finite slow-provider fixture if P2 exposes it. | Warning/limit behavior recorded; returned-time budget is not preemptive timeout. |
| 6 | [H] Compare all raw measures with their budgets and baseline. | MSPT <=25/50/100 ms mean/P95/P99, sustained RSS <=5.5 GiB, zero OOM/watchdog/unexpected tickets; unexplained regressions retained. |

Keep the existing tighter visual snapshot limits when measuring traffic:
**524,288 bytes total**, **32,768-byte chunks**, and **1,048,576 bytes total
BlockEntity NBT**. The suggested regular-packet <64 KiB guideline is not a
replacement for stricter channel limits. Record actual packet/cache bounds;
do not infer them from a successful connection.

**Accepted preparation/action steps (W408):**

(C10/C16, R535) Use A's matched baseline/feature intervals and review-only spread trigger; no integration-specific percentage allowance exists. Slow/rejected callbacks remain NOT_COVERED at REF because controls are A1-only, unless Root adds finite K18 controls. **Needs package K15**: CPU/network/cache and raw-tick integration; **needs package K21**: comparable integration workload.

**Evidence to keep:** `phase5-ref/<run>/D12/` node/workload/JVM manifests,
raw CPU/tick/RSS/GC/network/cache traces, callback timing/diagnostics and
baseline/budget comparisons. A GameTest's finite 20 ms callback delay is not
automatically an available packaged-server performance fixture.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## Open questions for Root

- **Answered:** C1 identity/classifier provenance; C15 measured installed/omitted-client policy; C16 callback NOT_COVERED above A1; C17 exact runner tag scope, measured player oxygen and F=750; C18 safe removal copies; W400 four-cell support; W406 tag/payload hashes and conditional outcomes.
- **D01-D12:** independently built matching API/fixture binary/reports **needs package K01**; concrete API scenes/coordinates and installed/omitted-client connections **needs package K21**.
- **D03/D12:** rejected/slow callbacks stay NOT_COVERED at V1/REF, because controls are GameTest-only. Added finite controls **needs package K18** and Root scope approval.
- **D04/D08/D09:** actual tag/definition acceptance and restart checks **needs package K06**; room/payload/equipment/loader scene **needs package K21**. Delivered ZIP bytes are not live loads.
- **D05/D06:** actual oxygen/refill interval and eligible completion target/loader layout **needs package K21**; actor numbers are not real-player expectations, and F is already answered.
- **D08/D09:** bounded stale/range/permission probes, receipt/readback and A1 references **needs package K18**.
- **D10:** concrete two-copy paths and full pre-removal file/payload inventories **needs package K21**; never remove from a source world.
- **D12:** complete network/cache/raw-tick and matched profiling integration **needs package K15**. No separate numerical percentage allowance exists.
