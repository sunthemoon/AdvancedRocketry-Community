# Final campaign preparation breakdown: choices, work packages and owner items

**DRAFT, 2026-11-20.** W401, for [TASK-W401](../TASK-W401-CAMPAIGN-PREP-BREAKDOWN.md), Root decision R534.
Read-only work on `codex/v1.8.0-w401`, starting at `27027c5b8`. The write scope is this record alone. Nothing here
is decided: every choice is a proposal for Root, every size is an estimate, and no row of the campaign is run,
passed or waived by this record.

## 1. What this record sorts

Section 5 of [CAMPAIGN-INPUTS-MANIFEST](CAMPAIGN-INPUTS-MANIFEST.md) has 68 rows. Two of them (C05 and D11) are
answered and carry no remainder. The other 66 each end in a literal remainder: 64 say "needs Root" and 2 say
"needs Root/owner". The task and the QUEUE row say 69; I could not reproduce that number from section 5 and I use
66 below. Root should treat 66 as the count of this record and check it against its own.

Names used in this record:

- **Row ID.** `A-1` is the first row of the manifest's table for checklist A, `J-13` the last row for checklist J.
  The second column of section 2 gives the campaign rows (A01, J23 and so on) that the manifest row names.
- **Cn** is a proposed choice in section 3. **Knn** is a proposed work package in section 4. **O4, O5, O7, O8, O9**
  are the items of [OWNER-FINAL-PACKET-DRAFT](OWNER-FINAL-PACKET-DRAFT.md) section 6. **N1 to N3** are owner
  arrangements that the packet does not number yet (section 5).
- **FInn** is a fixture family of the manifest's section 3. **P1 to P9** are the phase 1 steps of
  [FINAL-CAMPAIGN-PLAN](FINAL-CAMPAIGN-PLAN.md) section 5.
- **C4** is the candidate at commit `659bbcb6a745885565c07d98f5d695d43beef3f2`, release JAR SHA-256
  `c048743e61da4f939ce0492ca3ec73b7d69ef1634577bebc3279c91cf506cea1` (manifest section 2).
- **The lock** is the machine lock `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` of
  [WORKER-RULES](../WORKER-RULES.md). **E** is `D:/GitHub/ARCE-Task-Evidence/`. **REF** is the reference node of
  [docs/17](../../../17-V1PLUS-QUALITY-BUDGETS.md) section 4.
- **Needs C4 itself** means the package must start the C4 JAR or read C4's finished native campaign outputs.
  **Identity only** means the commit and the hashes are enough, so the package can start while C4's native
  campaign still holds the lock.

Rules I followed for choices. A choice states a procedure or a recording rule. Where a number appears (a trial
count, a window length), it is a procedure parameter that the documents leave to Root, and it is marked as such;
it is never a pass or fail limit. Where no document gives a limit, the choice says so. No choice relaxes a check,
gives a player more than ordinary rules allow, or changes production code.

## 2. Classification of every remainder

Each row lists its parts. A dash means the row has no part of that kind.

### A

| ID | Campaign rows | Choice | Preparation | Owner |
|---|---|---|---|---|
| A-1 | A01-A18 | C1 (C4's identity in the manifest replaces the plan's "not built" text). | K01 for the sources and API JARs. The per-row copies and configurations come from K02 to K21, each with its own manifest. | - |
| A-2 | A02, A06, A13, A17 | C2 (viewports and scales; no supported minimum is declared). | K16 builds the C4 core scene, measures the GUI mapping and writes the control manifest. | - |
| A-3 | A04 | C3 (A04's baselines are qualified apart from J22). | K02 retargets the two v1.0 forced-stop runners to C4. | - |
| A-4 | A08, A11 | C4 (effective values are C4's defaults; room size; save and restart schedule). | K15 for the sampler and queue observer, K17 for the 16 rooms and the combined world. | O7 |
| A-5 | A12 | C5 (frame method; no FPS floor exists). | K04 for the three rockets, K15 for the cache observation procedure. W399 supplies the frame-time reader. | O8, O9 |
| A-6 | A18 | C6 (trial count, reset and review rule; no tolerated rate exists). | W400's cells and W399's launch mode are enough. | - |

### B

| ID | Campaign rows | Choice | Preparation | Owner |
|---|---|---|---|---|
| B-1 | B01, B04 | C7 (H02 serves only the legacy-passenger branch; a rebuilt world is labelled as reconstructed). | K02 builds the normal v1.0 world and the in-flight variant on the pinned historical JAR. | - |
| B-2 | B02 | C8 (owners without a limit bypass). | K08 writes the region, owner and energy manifest. | - |
| B-3 | B03, B06 | - | K06 builds the route packs and the data-only `test_mars` pack and records the quotes. | - |
| B-4 | B05 | C9 (overlap is reported from server receipt ticks; no tolerance exists). | W399's session records give the receipt times. | O5 |
| B-5 | B07 | C10 (matched baseline and review rule; no percentage exists). | K15 for the cache invalidation observer. | - |

### C

| ID | Campaign rows | Choice | Preparation | Owner |
|---|---|---|---|---|
| C-1 | C01 | C11 (machine count, mix and interval). | K05 packages the scene. | - |
| C-2 | C02, C04 | C12 (the expectation is the conserved ledger; refusal or teardown is recorded per damage mode). | K05 writes the coordinates and the damage and unload variants. | - |
| C-3 | C03 | - | K05 adds the two-owner permission scene. | O5 |
| C-4 | C05 | Answered in the manifest: NOT_COVERED under plan decision O6. No remainder. | - | - |
| C-5 | C06 | C2 for the window sizes, C13 for long names and extreme numbers. | K05 reads the declared limits from C4 and writes the values. | - |
| C-6 | C08 | C14 (scope of the intent tool, and NOT_COVERED where it does not reach). | K18 builds the tool. K05 measures the actual allowed distance. | - |

### D

| ID | Campaign rows | Choice | Preparation | Owner |
|---|---|---|---|---|
| D-1 | D01-D12 | C1 for the classifier, C15 (the client policy is reported as measured, not from metadata). | K01 builds the API JAR and the independent fixture binary. K21 runs the installed and omitted client connections. | - |
| D-2 | D03, D12 | C16 (rejected and slow callbacks are NOT_COVERED above A1). | - | - |
| D-3 | D04 | C17 (only the runner's own tag override is used). | K06 builds the pack with its hash. K21 builds the Moon room world. | - |
| D-4 | D05 | C17 (the real player's consumption is measured, not predicted). | K21 writes the equipment, refill and HUD and reconnect steps. | - |
| D-5 | D06 | C17 (initial partial fuel; the item loader stays apart from FI29). | K21 builds the component scene and the eligible target. | - |
| D-6 | D08, D09 | C14 for the stale and range probes. | K06 builds the payload pack and its changed-definition variant. K21 builds the payload scene. K18 supplies the probes. | - |
| D-7 | D10 | C18 (two removal copies, never the source). | K21 makes the copies and their inventories. | - |
| D-8 | D11 | Answered in the manifest: the four cells are the declared scope. No remainder. | - | - |
| D-9 | D12 | C10 (intervals, baseline and review rule; no percentage exists). | K15 for the profile sampler. | - |

### E

| ID | Campaign rows | Choice | Preparation | Owner |
|---|---|---|---|---|
| E-1 | E01-E11 | C1 (the flight protocol is C4's pin, 10). | K06 and K07 write the planetary run manifest and the effective values read from C4. | - |
| E-2 | E01 | C19 (legs are enumerated from C4's catalog; no direct Mars to Venus leg is assumed). | K06 records the server quotes for each leg. | - |
| E-3 | E02, E03 | C7 (earned balances are never edited). | K07 builds the undiscovered and private-station copies. | O5 |
| E-4 | E03, E05, E07 | C14 for the forbidden-surface, cache and late-packet controls. | K18 for the tool, K15 for the cache readback. | - |
| E-5 | E04 | C20 (hazard modifiers stay at C4's defaults and are recorded). | K07 writes the coordinates, the equipment and the isolated and combined cases. | - |
| E-6 | E05 | C2 for the pixel bounds, C21 (cooldown is read from C4). | K06 builds the long-ID, 48-character and 100 and 128-body catalogs with hashes. | - |
| E-7 | E06, E11 | C5 for the method. | K15 for the allocation and cache observer. | O8, O9 |
| E-8 | E07 | C22 (the pause branch runs on an integrated client or is NOT_COVERED; the V2 lifecycle stays in sitting 2). | K16 adds the single-player round. | - |
| E-9 | E08 | C23 (only supported recovery routes; no selective restore). | K07 builds the paid-pending and blocked variants from earned progress. | - |
| E-10 | E09, E10 | C7 for the historical identities, C8 for the eligible owner. | K03 and K07 restore or rebuild the pre-sky world. | - |

### F

| ID | Campaign rows | Choice | Preparation | Owner |
|---|---|---|---|---|
| F-1 | F01-F07 | C8 for the owners, C10 for the sample window. | K08 restores the station world and writes the drive commands. | - |
| F-2 | F01 | C10 (sampling). | K08 writes the 10 and 100-station driver. | - |
| F-3 | F05 | - | K06 builds the target-removal and cost-change pack. K08 qualifies the activation boundary. | - |
| F-4 | F06 | - | K08 binds the station IDs and the owner, member and non-operator actions. | - |

### G

| ID | Campaign rows | Choice | Preparation | Owner |
|---|---|---|---|---|
| G-1 | G01-G07 | - | K09 builds the runnable mission copy and the command and GUI manifest. | - |
| G-2 | G01, G02, G05 | C10 for the sample window, C24 (durations and deadlines; limits are C4's defaults). | K09 reads the effective limits and sets the deadlines. | - |
| G-3 | G03 | C9 (same tick is claimed only from the server's tick record). | K09 adds the tick observation. K18 if a bounded intent is needed. | O5 |
| G-4 | G04 | C25 (an old cut is used only where a C4 save boundary is identified). | K09 writes the mapping. | - |
| G-5 | G05, G06 | C26 (the inherited physical wording is tested as ADR-051 defines it). | - | - |

### H

| ID | Campaign rows | Choice | Preparation | Owner |
|---|---|---|---|---|
| H-1 | H01-H07 | - | K10 chooses the coordinates, binds the owners and screens and writes the native invocation. K15 for the REF sampler. | - |
| H-2 | H03 | - | K10 pins the batch-cut seam and the large-range variant. | - |
| H-3 | H04 | C27 (NOT_COVERED under D9; the adapter result is not a real-mod pass). | - | - |
| H-4 | H07 | C28 (actors are classified; achieved throughput is reported against the unchanged load). | K10 qualifies the actors and the measurement. | O7 for the two real connections. |

### I

| ID | Campaign rows | Choice | Preparation | Owner |
|---|---|---|---|---|
| I-1 | I01 | C29 (the witness route and the endgame device). | K16 builds the continuous route and its counts. | O4 |
| I-2 | I02, I03, I06, I08 | C30 (classification, sample and scale). | K13 for the inventory, K02 for the old-ID world, K05 for the machine scene. | - |
| I-3 | I04 | C31 (counts are read from C4; one page and one usage query per category). | K13 reads the counts. The JEI cells come from W400. | - |
| I-4 | I07 | C32 (no captured 1.12 session exists; the comparison uses W241's audited inventory). | K13 builds the missing-resource packs. K14 prepares the paired break, repair and tracking scene. | - |
| I-5 | I09 | C5. | K04 for the three rockets, K15 for the heap method. W399 for the frame-time reader. | O8 |
| I-6 | I11 | C14. | K18 for the tool. K13 writes the holder and policy matrix from C4's code. | - |

### J

| ID | Campaign rows | Choice | Preparation | Owner |
|---|---|---|---|---|
| J-1 | All J rows | C1. | K11 to K14 write the run manifests. K15 for the REF drivers. | N1, N2, N3 |
| J-2 | J01 | C33 (a foreign owner's cap coverage is not claimed). | K11 writes the menu-status sequence and the cap recipe. | - |
| J-3 | J02, J03 | C34 (step 3.4 is "not run; covered natively by X5"; J03 is NOT_COVERED while no LAN gate exists). | K11 pins the coordinates and seats. | N1 |
| J-4 | J04 | C35 (each variant is real-client, or synthetic-only with its native reference). | K11 writes the control recipes. K20 writes the per-variant table into checklist J. | - |
| J-5 | J05, J08, J09, J10 | C10, C36 (no latency maximum exists; wait 23 is isolated). | K12 builds the REF queue fixtures and the raw sampler. | N2 |
| J-6 | J11 | C37 (measure on REF; over budget opens a separate design task). | K12 for the measurement fixture. | N2 |
| J-7 | J12-J16 | C38 (the audio producers are enumerated from C4). | K13 for the producer map, the audio-enabled profile and the long-font pack. | O9, N3 |
| J-8 | J17 | C39 (first-event H cuts are not authorized with a connected player; X stays external-stop). | K12 writes the fourteen recipes with award probes. | - |
| J-9 | J18 | C40 (the respawn extension is not implemented and is not a test row). | K14 qualifies the freeze rows and builds the scene. | - |
| J-10 | J19 | C41 (values are read from C4; variants come from ordinary actions). | K14 builds the variants. | - |
| J-11 | J21 | C42 (one of four dispositions per finding). | K19 writes the map. K16 regenerates the recheck batches. | - |
| J-12 | J22 | C43 (X01, X02 and the downgrade verdicts stay separate). | Root reviews C4's finished outputs and keeps the hashed manifest. This is Root's own work and not a package. | - |
| J-13 | J23-J29 | C44 (B shows the ordinary refusal and the permitted feedback). | K11 writes the menu, hold, seat-clear and probe steps and the refusal variants. | N1 |

## 3. Proposed choices

Root can accept or change these in one decision. "Source" names the document the choice rests on. "No limit"
means that I found no pass or fail limit for the matter in docs/17, the version documents or the sources the
manifest cites; I did not search every ADR.

| ID | Rows | Proposed choice | Source |
|---|---|---|---|
| C1 | A-1, D-1, E-1, J-1 | The campaign pins C4 as the manifest's section 2 gives it. The plan's "not built" text in section 7.1 is superseded. The hosted artifacts hold no sources or API JAR, so K01 builds both from the same commit and records them as locally built, with hashes. The hosted release JAR stays the tested artifact; if the local build's release JAR has a different hash, the record says so. | Manifest section 2; plan P1. |
| C2 | A-2, C-5, E-6 | No document declares a supported minimum window. The campaign tests windowed 1280 by 720 (W400's cell default) at GUI scales 2, 3 and 4 in both languages, and fullscreen at the machine's native resolution. The report says that no minimum is declared and that smaller windows were not tested. A key button outside the window at a tested scale is a finding under docs/17 section 8. Old pointer coordinates are never replayed. | docs/17 section 8; manifest section 4.2; [TASK-W400](../TASK-W400-CAMPAIGN-RUNTIME-CELLS.md). |
| C3 | A-3 | A04 is qualified by its own runs of the two v1.0 forced-stop runners on C4. J22's result does not stand in for it. A changed oracle is a reviewed script change that states what it now checks. Adjacent-chunk arrival, current-seat selection and native expiry are recorded as separate observations; one that has no deterministic control is NOT_COVERED with that reason. | Manifest section 3.3; checklist A. |
| C4 | A-4 | The scan budgets are C4's shipped defaults of `atmosphere.maxVolumeCells` and `atmosphere.maxInspectionsPerLevelTick`, read back from the generated configuration and not changed. All 16 rooms use one recorded footprint of at most 4,096 blocks. Schedule (procedure parameters): vanilla autosave, an explicit flushed save every 30 minutes, and one orderly restart at the two-hour point; the four hours are sampled time, and memory is compared across the restart. | docs/17 section 4; [test plan](../../../05-MASTER-TEST-PLAN.md) section 10; version document section 13. |
| C5 | A-5, E-7, I-5 | Each figure is paired with the Vanilla and Forge empty-scene baseline on the same cell, settings and viewport. The report gives the average, the 1% low and the first stall by W399's definitions, with the tool and version. No FPS floor or stall limit exists. The reader's required stall argument is set to 50 ms as a reporting parameter, and the longest frame is always reported. The correctness list of docs/17 section 8 is checked as written. A Medium finding ("明显性能回退") is raised when Root's review of a pair finds a drop larger than the spread between two runs of the baseline. | docs/17 sections 3 and 8; [TASK-W399](../TASK-W399-CAMPAIGN-CLIENT-HARNESS.md). |
| C6 | A-6 | Ten first-join trials on each Forge lane with JEI absent (a procedure parameter), each on a fresh server copy and a fresh client process and game directory, plus every first join of accounts A and B in phase 4. The report gives the timeouts and the trials per lane. No tolerated rate exists: each timeout is a finding that stays open until Root dispositions it, and a retry never hides it. | Checklist A, A18. |
| C7 | B-1, E-3, E-10 | A world rebuilt on a historical JAR is labelled "reconstructed on JAR (hash), not historically played". It may show that the old format migrates. It does not show that a played world migrates; where no retained played source exists (the v1.0 in-flight world, the pre-sky world), that claim is NOT_COVERED with this reason. H02 covers the legacy-passenger branch only. Earned balances, IDs and binding history are never edited. | Manifest section 3.1. |
| C8 | B-2, E-10, F-1 | The two real accounts each own one station by the ordinary route. The 10 and 100 station records for load use the permission-2 command `/arce station admin create` with a distinct owner UUID per station, so no owner passes the per-owner limit; the record says they are operator-created. | Manifest FI23 and FI25. |
| C9 | B-4, G-3 | No overlap tolerance exists. The record keeps the server tick at which each intent arrived. "Same tick" is claimed only when the server's record shows one tick. Otherwise the gap is reported, the outcome is still checked (one winner, nothing lost or gained), and the same-tick branch is NOT_COVERED at V2 with its A1 test named. | Checklists B and G. |
| C10 | B-5, D-9, F-1, F-2, G-2, J-5 | A comparison uses the same world copy, cell, machine and sampling plan with and without the thing measured, run back to back, and the baseline is run twice to show its spread. The standard short sample is a two-minute warm-up that is discarded and ten minutes of samples (procedure parameters). The absolute budgets of docs/17 section 4 apply on REF. No percentage allowance exists; a Medium finding is raised when Root's review finds a difference larger than the baseline's spread. | docs/17 sections 3 and 4. |
| C11 | C-1 | Two scenes: the version's 100 idle machines, and the scale test's layout of 144 controllers (16 of each of nine kinds), first idle and then half active, with the recipes that test uses. Both are sampled as C10 says. The document gives only "100 idle" and no active count, so the second scene is a choice. | Manifest FI10; version document section 13. |
| C12 | C-2 | The expectation for every damage mode is the conservation rule: the per-machine ledger before equals the ledger after plus the drops. Whether the device refused the damage or was torn down is recorded per mode as an observation, and neither is a failure by itself. | Test plan section 8; manifest FI10. |
| C13 | C-5 | Long names and extreme numbers are taken from the limits C4's code declares for each field: the value at the limit, and one past it, which must be refused. No value is invented; a field with no declared limit is listed as such. | docs/17 section 7. |
| C14 | C-6, D-6, E-4, I-6 | K18 builds a separate fixture mod that is never shipped. For the eight pinned channels it sends only what an ordinary client could send, but stale, out of range, without permission or late, and each case is bounded. The server's log proves receipt and refusal. A branch the tool does not reach is NOT_COVERED with the reason and its A1 test; a disabled button is never counted as a refusal. If K18 is not ready when a phase starts, those branches are NOT_COVERED for that phase. | Checklists C, E and I; docs/17 section 6. |
| C15 | D-1 | The report states what the installed and the omitted client did on connection. The metadata (required on both sides, no display-test exemption) is quoted as the expectation; a client that connects without the mod is a finding. | Manifest FI11. |
| C16 | D-2 | Rejected and slow callbacks are NOT_COVERED at V1 and REF: the fixture's fault controls exist only in GameTests, and the A1 result stays an A1 result. Root can instead add finite controls to K18's scope. | Fixture README, as the manifest quotes it. |
| C17 | D-3, D-4, D-5 | D04 uses only the tag override that the atmosphere runner's `tag_pack` builds. D05 measures a real player's oxygen use and refill over a recorded interval; the actor counts 999, 998 and 1,997 are not expectations. D06 starts from half the component capacity (750 of 1,500, a procedure parameter) and uses the ordinary item loader; escrow fuel stays in FI29. | Manifest FI19 and FI20. |
| C18 | D-7 | Each removal runs on its own fresh copy under the evidence root's `fixtures/`, with a full file inventory and hashes taken before the mod is removed. The source world is never used. | Manifest FI21; plan section 6. |
| C19 | E-2 | The legs are those that C4's loaded catalog and the server's quotes give. Each permitted consecutive pair is tested. A leg the catalog does not give, such as a direct Mars to Venus leg, is not assumed and is not a failure. | Manifest FI22. |
| C20 | E-5 | Damage modifiers, difficulty and game rules stay at C4's and vanilla's defaults and are recorded. Each hazard is tested alone and then combined. | Checklist E. |
| C21 | E-6 | The full-catalog cooldown is read from C4, not assumed. Catalogs of 100 and 128 bodies and names of 48 characters come from K06 with hashes. An oversized or invalid pack is a refusal test, never the setup of another row. | Checklist E. |
| C22 | E-8 | The pause branch runs in phase 2 on an integrated single-player client. If the harness has no single-player round by then, that branch alone is NOT_COVERED, because a menu on a dedicated server does not pause it. SKY-V2-01 and the navigation V2 lifecycle stay in sitting 2. | Checklist E; manifest section 4.2. |
| C23 | E-9 | Recovery uses only supported routes: restoring a complete world backup, and the documented station commands. A variant with no supported recovery is reported as that. No selective merge of backups and no erased ID. | Manifest FI23. |
| C24 | G-2 | Scheduler limits are C4's defaults, read back. The online part runs inside the four-hour soak. The offline part is the soak's restart plus one stop of 30 minutes (a procedure parameter). Deadlines are set so that at least one mission of each kind expires inside the window. Wall-clock downtime is never counted as progress. | Checklist G; docs/17 section 4. |
| C25 | G-4 | A historical cut is selected only where K09 names the matching C4 save boundary. A cut with no match is NOT_COVERED with that reason and is not presumed equivalent. | Checklist G. |
| C26 | G-5 | [ADR-051](../../../decisions/ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md) defines mission instances as logical records with no world region. The inherited words "instance chunks" and "instance visuals" are tested as terminal unloading, logical presentation and record reclamation, and the result row says so and cites the ADR. No dimension is invented. | ADR-051; checklist G. |
| C27 | H-3 | H04 is NOT_COVERED in v1.8 with D9 as the reason and moves to v1.9, as plan decision O6 says. The adapter result is recorded beside it as supplementary and is not a pass of the inherited real-mod item. | Plan section 7.3, O6. |
| C28 | H-4 | The report classifies each actor: the two real connections, and the builder's fixture players, which are not real players. It reports the railgun throughput achieved against the load [ADR-054](../../../decisions/ADR-054-ENDGAME-AUTHORITY-PROTECTION-AND-AUDIT.md) requires. A shortfall is reported as not met; the load is never lowered. Historical Windows figures are not REF evidence. | Manifest FI27; checklist H. |
| C29 | I-1 | The witness route is one scripted continuous route on C4 from the first machine to one endgame device. I recommend the railgun pair as that device, because H07 measures it as well; this is my suggestion and no document names a device. The hand-played subset is the owner's item O4. | Version document sections 10 and 11; packet O4. |
| C30 | I-2 | High-risk assets are those the version document names for the real GPU (OBJ models, multiblocks, armour) and those W244, W261 and W265 corrected. They are all checked. Every screen is opened once per language; other assets are sampled one per model family (a procedure parameter). The many-machine scale is FI10's 144 controllers, with no further factor. | Version document section 11; manifest FI30. |
| C31 | I-3 | Category and recipe counts are read from C4 in each JEI cell. W188's totals are history. One recipe page and one usage query are shown per category. | Version document section 10. |
| C32 | I-4 | No captured 1.12 session exists and none is created from unaudited art. The comparison uses W241's audited renderer inventory at upstream commit `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`; "compared with 1.12 screenshots" is NOT_COVERED with that reason. The corrections of W244, W261 and W265 are kept. The paired break, repair and tracking scene runs in phase 4. | Manifest FI30. |
| C33 | J-2 | The record claims cap coverage for the owner the fixture uses, and nothing for another owner. A distinct owner's admission is shown only with account B as the owner of B's own bay under ordinary rules. | Checklist J. |
| C34 | J-3 | W254 step 3.4 is recorded "not run; covered natively by X5". J03 is NOT_COVERED while the switches are off: integrated and LAN servers ignore the campaign property, and a LAN gate would be a production change and a new candidate. If the owner enables the bay switch (packet P4), J03 runs on that candidate under production admission. | [W254-V2-CHECKLIST](W254-V2-CHECKLIST.md) sections 0.1 and 3; checklist J, J03. |
| C35 | J-4 | Each of the ten online variants and EXIT.0 to EXIT.10 gets one disposition in checklist J: real-client, with its recipe on the marked world, or synthetic-only, with its W325 native case named. A facility-only "online" case is never counted as V2. | Checklist J; manifest FI28. |
| C36 | J-5 | No latency maximum exists for unbounded waits; the raw waits are recorded. Wait 23 is measured in a world with no other ticket owner, and a sweep with other owners present is not compared with the 400-tick expiry. Budget findings come from REF results only. | Checklist J. |
| C37 | J-6 | The first-event tick is measured fresh on REF. If it is over budget, Root opens the separately designed and reviewed persistence task that R245 requires, before release. A disclosed Windows limitation does not replace the measurement. | Checklist J, J11. |
| C38 | J-7 | The audio producers and previews are enumerated from C4's own sound definitions and code, not from W16's older list. Audio rows run on an audio-enabled profile. | Checklist J. |
| C39 | J-8 | First-event H cuts are not run with a connected player; the connected-player exception covers bay selection only. X cuts use external stops only. | Checklist J, J17. |
| C40 | J-9 | Saving a planet point when `allowPlanetRespawn` is on is not implemented in v1.8 ([W37B](W37B.md)). It is recorded as not implemented and is neither a test row nor a pass. Ordinary sleep is tested as the freeze matrix defines it. | W37B. |
| C41 | J-10 | UI strings and the section-7 values are read from C4. The owner, competitor, rebind, full-account and pause variants are produced by ordinary player actions on copies. No balance is edited and no chunk is force-loaded. | Checklist J, J19. |
| C42 | J-11 | Each historical finding gets one disposition: fixed (task and commit), known issue, matches the design (W294), or recheck on C4. Informational and round-8 residuals are listed for Root to accept one by one; none is accepted by default. | Plan section 7.1. |
| C43 | J-12 | J22's result is the reviewed C4 output with its hashes. The X01 and X02 results, the failed downgrade verdicts and the open risk decisions stay as separate rows; no family is labelled passed by assumption. | Manifest section 3.3. |
| C44 | J-13 | A opens the loader menu. B shows the ordinary refusal and the permitted synchronized feedback, and never opens A's owner-only menu. The extra fuel property selects the test path only; no production switch is enabled. | Checklist J; manifest FI29. |

## 4. Work packages

### 4.1 The two existing packages

| Package | Step | Rows it serves |
|---|---|---|
| W399, the tracked client harness (launch mode, attach mode, online-server settings, frame-time reader) | P3 | A-2, A-5, A-6, B-4, E-7, E-8, I-5, J-1, J-3, J-13, and every scripted real-client row through K16. |
| W400, the four Forge and JEI cells | P3 | A-6, D-8 (D11's four cells), I-3, and every row that runs on a cell. |

Neither package regenerates the C4 batches, builds a single-player round or picks the frame-time tool. K16 takes
the first two; the tool is owner item O8.

### 4.2 Proposed packages

Sizes are sums of W398's rough estimates in the manifest, which overlap and are not measured. "Lock" says whether
the package must hold the lock for part of its work. Each package writes only its own new files; I suggest
`scripts/campaign_prep/<package>_*.py`, `tests/test_campaign_prep_<package>_*.py`, its record, and a folder under
`E/final-campaign/659bbcb6a/fixtures/<package>/`. These paths are suggestions and do not exist yet.

| ID | Step | What is built | From | Lock | C4 | Hours | Depends on | Rows |
|---|---|---|---|---|---|---|---|---|
| K01 | P1, P2 | The sources and API JARs of the C4 commit, a local Maven publication, the independent fixture binary `arce-adapter-compat-test-1.0.0.jar`, boundary reports and hashes. | `compat-test-mod/` and its README; FI11. | Yes, for the builds. | Identity only. | 3 to 5 | - | A-1, D-1 |
| K02 | P2 | Copies and manifests of the Beta world (FI01), a normal v1.0 world and its in-flight variant (FI02, FI03), the v1.3 world (FI04) and the all-ID v1.7 world (FI09); the two v1.0 forced-stop runners retargeted to C4. | H01 to H03, H08; `scripts/v100_beta_world_fixture.py`, `scripts/run_v100_flight_forced_stop.py`, `scripts/run_v100_transaction_forced_stop.py`, `scripts/v170_world_fixture.py`. | Yes. | Identity to build; C4 itself to qualify. | 8 to 16 | Historical JARs must be located first. | A-3, B-1, I-2 |
| K03 | P2 | The chain of v1.4, v1.5, v1.6 and v1.7 worlds (FI05 to FI08), each rebuilt from the one before on its pinned historical JAR, with manifests. | FI04; `scripts/run_v140_migration_smoke.py`; the archived native scripts in H05 to H07. | Yes. | Identity to build; C4 itself to qualify. | 12 to 16 | K02 | E-10, and the sources of K07 to K10 |
| K04 | P2 | Legal fuelled rockets of exactly 64, 512 and 2,048 blocks (FI12 to FI14), with seated and observer positions, a multi-rocket copy and the separate 128-payload boundary scene. | The assembler fixtures; `scripts/run_v180_cargo_native_benchmark.py`. | Yes. | C4 itself. | 4 to 8 | - | A-5, I-5, K17 |
| K05 | P2 | The packaged many-machine scene (FI10): 100 idle, and 144 controllers idle then half active; unload, damage, permission and long-name variants with coordinates and ledgers. | `ClassicScaleGameTests`; W70; `E/v18-sp-20261104/scripts/gen*.py`. | Yes. | C4 itself. | 8 to 16 | - | C-1, C-2, C-3, C-5, C-6, I-2 |
| K06 | P2 | Data packs with hashes: valid, invalid, unknown and locked routes; data-only `test_mars`; sky packs; long-ID, 48-character and 100 and 128-body catalogs; the station target-removal and cost-change pack; the API tag and payload packs with a changed-definition variant; the quotes read from C4. | The tracked test JSON; `scripts/v140_migration_fixture.py`; the v1.3 runners' pack builders. | No for generation. Yes for one load check on C4. | Identity for generation; C4 itself for the load check. | 6 to 10 | - | B-3, D-3, D-6, E-1, E-2, E-6, F-3 |
| K07 | P2 | Planetary world variants (FI23, FI24): undiscovered, discovered, private-station, paid-pending and blocked copies; hazard sites; the pre-sky copy. | K03's worlds; `scripts/run_v140_discovery_cut_smoke.py`, `scripts/run_v140_planetary_worlds_smoke.py`. | Yes. | C4 itself. | 8 to 12 | K03, K06 | E-1, E-3, E-5, E-9, E-10 |
| K08 | P2 | Station and warp fixtures (FI25): two player stations, the 10 and 100-record sets, cores and energy, the driver and the bound command list. | K03's v1.5 world; `StationCommands`; `StationWarpGameTests` as a seed. | Yes. | C4 itself. | 4 to 8 | K03, K06 | B-2, F-1 to F-4 |
| K09 | P2 | Mission fixtures (FI26): sets of over 100, 500 and 1,000, deadlines per kind, the tick observation and the cut mapping. | K03's v1.6 world; H06's native driver. | Yes. | C4 itself. | 4 to 8 | K03 | G-1 to G-4 |
| K10 | P2 | Endgame fixtures (FI27): the reference-load build at chosen coordinates, owners and screens, the batch-cut and large-range variants, actor classification and the throughput measurement. | `ReferenceLoadReleaseTestCommands`, `ReferenceLoadFixture`; K03's v1.7 world. | Yes. | C4 itself. | 4 to 8 | K03, K15 | H-1, H-2, H-4 |
| K11 | P5 | The disposable campaign world kit (FI17, FI29): the marker file, the two properties and the fuel property as W399 session files, the two-seat bay rocket, pads, cap and status recipes, the fuel menu, hold, seat-clear and refusal variants, and the no-hook control. UUIDs are parameters until the owner gives them. | W254-V2-CHECKLIST section 0.1; `scripts/run_v180_bay_exit_cuts.py`; W11B3-DESIGN. | Yes. | C4 itself. | 8 to 16 | W399, W400; N1 to finish. | J-1 to J-4, J-13 |
| K12 | P5 | Cargo and queue fixtures (FI28): larger REF queues, the isolated wait-23 world, the raw sampler, and the fourteen save and deferral recipes with award probes. | W325-MATRIX; `scripts/run_v180_integrated_campaign.py`; C4-SWITCH-REPORT. | Yes. | C4 itself, and its finished native outputs. | 8 to 16 | K04, K15 | J-1, J-5, J-6, J-8 |
| K13 | P2 | The C4 asset, screen and sound-producer inventory with the C30 classification; the holder and policy matrix; JEI counts; missing-resource and long-font resource packs. | The C4 JAR's contents; W16, W87, W241, W244, W261, W265. | No. | The C4 JAR file, which exists now. | 8 | - | I-2, I-3, I-4, I-6, J-1, J-7 |
| K14 | P2 | Sleep, research, ground-survey and landing scenes (FI31), and the paired break, repair and tracking scene. | W37B, W21A-DESIGN, W189, W311. | Yes. | C4 itself. | 4 to 8 | K16's core world | I-4, J-1, J-9, J-10 |
| K15 | P3, P6, P8 | Observers and their unit tests: a server tick, RSS, GC and forced-chunk sampler; a JFR procedure; the client heap and cache observation steps; the REF acceptance script of docs/15 section 18; the evidence-root creator with the free-space check. | `jcmd` and `jfr` of the local JDK; the native diagnostic commands. | No. | Identity only. | 6 to 10 | - | A-4, A-5, B-5, D-9, E-4, E-7, H-1, I-5, J-1 |
| K16 | P3, P9 | The C4 core route world (FI16), the control manifest, the regenerated batches at the C2 viewports, a single-player round, the J21 recheck batches, and the dry run itself. | `E/v18-client-20261010/` and `E/v18-sp-20261104/` scripts and batches. | Yes, with the GPU PC. | C4 itself. | 8 to 16 | W399, W400 | A-2, E-8, I-1, J-11 |
| K17 | P2 | The combined reference workload world (FI15): 16 rooms, 10 stations, 100 missions, two active rockets, the 2,048 assembly, a transfer and the C4 schedule. | docs/17 section 4; the outputs of K04, K08, K09. | Yes. | C4 itself. | 8 to 16 | K04, K08, K09, K15 | A-4 |
| K18 | P3 | The intent fixture mod of C14 (FI32), bounded per channel, with its unit tests. | The network pin; the checklists' refusal branches. | Yes, for its build only. | Identity only. | 8 to 16 | Root accepts C14. | C-6, D-6, E-4, G-3, I-6 |
| K19 | P4 | The finding to task to batch to disposition map for J21. | H12, H13; the QUEUE; plan section 7.1. | No. | Identity only. | 4 to 6 | - | J-11 |
| K20 | P4 | The accepted choices and the packages' manifests written into checklists A to J, including J04's per-variant table. One worker owns all ten files. | This record after Root's decision. | No. | Identity only. | 6 to 8 | Root's decision; the manifests as they arrive. | All rows, J-4 |
| K21 | P2 | The API scenes on C4 (FI19 to FI21): the component and loader scene, the Moon room and suit steps, payload scenes, the two removal copies, and the installed and omitted client connection check. | The v1.3 runners; K01's binary; K06's packs. | Yes. | C4 itself. | 7 to 13 | K01, K06 | D-1, D-3 to D-7 |

Order. K06, K13, K15 and K19 need no lock and only C4's identity or its JAR file, so they can start while C4's
native campaign runs. K01 needs the lock only for short builds. K02 comes before K03, and K03 before K07 to K10.
K16 follows W399 and W400. K17 comes last among the fixtures. K20 follows Root's decision on section 3.

One thing to settle before K02 and K03: the manifest names the historical JARs for v0.9 and the three C4 campaign
hosts, but I did not find in it a located JAR for the v1.0 and v1.3 to v1.7 hosts. If they are not retained, each
must be rebuilt from its tag, and the estimates above do not include that.

## 5. Owner items

| Item | Rows | What is needed |
|---|---|---|
| O4 | I-1 | The hand-played subset of the technology route, and the recorded play. |
| O5 | B-4, C-3, E-3, G-3 | Who operates the second client, and how coordinated inputs are given. |
| O7 | A-4, H-4 | The two real connections for the four-hour soak. |
| O8 | A-5, E-7, I-5 | The frame-time tool and its version on the GPU machine. |
| O9 | A-5, E-7, J-7 | The second GPU machine and the subset it runs. |
| N1 (new) | J-1, J-3, J-13 | The two accounts' UUIDs, not the credentials, for the whitelist and the campaign property. The plan lists this as an owner step without a packet number. |
| N2 (new) | J-1, J-5, J-6 | A normal-user login on the reference node, or the owner running Root's setup script there. Also an owner step without a packet number. |
| N3 (new) | J-1, J-7 | When the second machine and a second player are free for the three sittings. Also an owner step without a packet number. |

Root may prefer to fold N1 to N3 into the packet's section 6 as O10 to O12, or into O5 and O9.

## 6. Totals

| Count | Value |
|---|---|
| Manifest rows in section 5 | 68: 66 with a remainder, 2 answered (C-4, D-8) |
| Rows with a choice part | 59 |
| Rows with a preparation part | 63 (60 served by the proposed packages; A-6 and B-4 need only W399 and W400; J-12 is Root's own review) |
| Rows with an owner part | 16 |
| Proposed choices | 44 (C1 to C44) |
| Owner items | 8: O4, O5, O7, O8, O9, and N1 to N3, which need new packet numbers |
| Existing packages | 2 (W399, W400) |
| Proposed packages | 21 (K01 to K21) |

Preparation hours by package, as ranges: K01 3 to 5; K02 8 to 16; K03 12 to 16; K04 4 to 8; K05 8 to 16; K06 6 to
10; K07 8 to 12; K08 4 to 8; K09 4 to 8; K10 4 to 8; K11 8 to 16; K12 8 to 16; K13 8; K14 4 to 8; K15 6 to 10; K16 8
to 16; K17 8 to 16; K18 8 to 16; K19 4 to 6; K20 6 to 8; K21 7 to 13. The sum is 136 to 240 hours, or 17 to 30
worker days of eight hours, not counting W399, W400, lock waits or product fixes. The plan's section 8 allows 5 to 8
Root and worker days for all of phase 1. The gap is real: the plan's figure predates the manifest's fixture-by-fixture
estimates. The 24 to 34 hours of the four lock-free packages (K06, K13, K15, K19) can run at once; the other seventeen need the lock in
turn, so their calendar time depends on how long each holds it.

What blocks the dry run (P9), which is one scripted round of phase 2 on C4 on Forge 47.4.10:

1. C4's native campaign must release the lock.
2. W399 must be merged, and W400's cell `47.4.10-nojei` must have been started once under the lock.
3. K16 must deliver at least the core scene and one regenerated batch.
4. K15's evidence-root creator (P8) must exist, or Root creates the root by hand.
5. Root must accept C2, because the batch is generated at those viewports.

The dry run uses the launch mode with disposable offline clients, so it does not wait for any owner item, for the
frame-time tool, or for K01 to K14 and K17 to K21. P9 is the last step of phase 1, however, and the phase's exit
also needs P2, P5, P6 and P7; those wait for the other packages and for N1, N2, O8 and O9.

## 7. Record

**Done.** This one DRAFT record: 68 rows classified, 44 choices proposed, 21 packages proposed, 8 owner items
listed. It is committed on `codex/v1.8.0-w401` in one commit; the SHA is in the final answer and is found with
`git log -1 -- docs/work/v1.8.0-root-queue/records/CAMPAIGN-PREP-BREAKDOWN.md`. Nothing is pushed.

**Files changed.** Only this record. No code, script, test, ADR, register, status file, QUEUE, checklist or other
record.

**Commands and results.**

| Command | Result |
|---|---|
| `python -B scripts/validate_repository.py` | Exit 0 on the staged record: 45 passed, 0 pending, 0 warnings, 0 failed. |
| An inline Python check that every relative link in this record resolves to a file tracked at `27027c5b8`, and that the counts in section 6 match the tables in section 2 | 13 links, all tracked; 68 rows; 59, 63 and 16 rows with a choice, preparation and owner part; 44 choices; 21 packages summing to 136 to 240 hours. The first run found section 6's counts wrong (54, 62, 17) and four package row lists incomplete; they were corrected. |
| `git diff --cached --stat` | Only this record was staged. |

No JVM, Gradle, GameTest, native run, client or other agent was started. The unit suite was not run, because the
task is documentation only and forbids JVM work.

**Choices I made where the sources were open.** I used 66 remainders, not 69 (section 1). I read the manifest in
full and the checklists' open-question sections, the plan's sections 5 to 8, the packet, docs/17, the version
document's sections 10 to 13 and the test plan's sections 10 and 11; I did not read the checklists' step lists or
the cited worker records in full, so a proposed choice may repeat or miss a detail they hold. Procedure parameters
(ten trials, a ten-minute sample, a 30-minute save interval, a 50 ms reporting parameter, half capacity for D06,
the railgun pair for I01) are mine and are marked where they appear.

**Not done.** No choice is accepted. No package is dispatched. The checklists and the plan are unchanged. ADR-051
and ADR-054, on which C26 and C28 rest, both carry `status: ACCEPTED`.

**Risks.** The package sizes rest on W398's estimates and could be wrong by a large factor in either direction.
K02 and K03 depend on historical JARs that may have to be rebuilt. C14 and C16 leave some network refusal branches
NOT_COVERED above A1 if K18 is late or narrow. C34 leaves J03 NOT_COVERED unless the bay switch is enabled. C7
leaves the "historically played" claim NOT_COVERED for two worlds. Root should check each of these against the
Gate texts before accepting them, because a NOT_COVERED row is not a pass and may still block a Gate.

## 8. Root decision R535

Root (Claude) reviewed this record on 2026-11-20 and accepted it, with these changes. Everything not listed here
is accepted as written. Owner items stay the owner's; no choice signs a risk, enables a switch or marks a Gate.

| Item | Root's change |
|---|---|
| C2 | Minecraft's GUI scale needs at least 320 by 240 scaled pixels, so a 1280 by 720 window offers scales 1 to 3 and
clamps 4 to 3. The campaign tests windowed 1280 by 720 at scales 2 and 3, and fullscreen at the machine's native
resolution at scale 4 when that resolution offers it (1920 by 1080 does). Each screenshot records the effective
scale read from the game, not the requested one. The rest of C2 stands. |
| C4 | Accepted. The 4,096-block room footprint, the 30-minute flushed save and the two-hour restart are procedure
parameters, not budgets; the record of the run names them as such. |
| C5, C10 | Accepted. "Larger than the baseline's spread" is a trigger for Root's review, not a pass or fail limit;
the finding's severity follows docs/17 section 3. |
| N1 to N3 | Added to the owner packet's section 6 as O10 (the two accounts' UUIDs), O11 (the reference node login
or script run) and O12 (when the second machine and a second player are free). |
| O8 | The packet now names Root's recommendation: Intel PresentMon, or another tool the owner trusts that writes
a PresentMon-format CSV, which W399's reader takes. |
| Order | Lock-free packages first, at most two Codex workers and one Claude worker at a time: K13, K15, K19 and K06
(its generation part). K16 starts as soon as W399 and W400 are merged and the lock is free, because it leads to
the P9 dry run. K01 takes the lock briefly after C4. K02 to K12, K14, K17, K18, K20 and K21 follow in the record's
dependency order. Before K02 and K03, Root locates or rebuilds the historical v1.0 and v1.3 to v1.7 JARs and adds
that time. |
| Estimate | The plan's phase 1 figure of 5 to 8 Root and worker days is superseded by this record's 17 to 30 worker
days (estimates, not budgets). |

docs/work/v1.8.0-root-queue/records/CAMPAIGN-PREP-BREAKDOWN.md
