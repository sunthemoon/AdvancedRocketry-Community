# Campaign checklists I: v1.8 version acceptance

These are unrun P4 lists for I01–I11 of [the campaign](FINAL-CAMPAIGN-PLAN.md), using
[v1.8 sections 10–13](../../../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md), [W62](W62.md),
[W188](W188.md) and the current, not superseded, model decisions named under I07.
No Gate or version is approved.

Accepted common procedures for **every I row**: `CAMPAIGN-CHECKLISTS-A.md`, section "Accepted procedures and delivered tooling",
pins C4 (C1), the four W400 `cell.json` paths/hashes, W399's commands and session
fields, C2/R535's effective display cells, C5/C10/R535's matched sampling/review
rules, R546's receipt-plus-state refusal proof, and W403's observers, evidence root
and checksum commands. Apply them before the row's actions; old generic display
wording or demands for nonexistent refusal logs do not override these decisions.

## Shared preconditions, execution labels and evidence

H marks a harness/operator action; P marks a person's action/judgement. Each arrow gives the expected observation.
Use P1's C4 commit and hosted JAR SHA-256 on Java 17. Default is Forge 47.4.10 without JEI; compatibility is
47.4.23. JEI-present means exactly 15.56.0.205. I04 explicitly covers all four cells; I09 covers both JEI states.
V1 means a real GPU, with CPU/GPU/driver/resolution/window/GUI-scale recorded, not Xvfb/LLVMpipe.
P2 supplies immutable source fixtures; use disposable copies only. Its names below are fixture descriptions,
not already delivered archive filenames. These **needs package K02** (historical), **needs package K05** (machine) or
**needs package K16** (live control) archive hashes, coordinates, IDs and setup commands, as the rows specify.
Pin server difficulty, view/simulation distance, config and loaded switches before each run.

Phase 3 DS uses the packaged server on the development PC with one real client A. Phase 4 V2 uses two real
authenticated premium accounts A/B on the restricted packaged server with `online-mode=true`, `white-list=true`,
`enforce-whitelist=true`, `enable-rcon=false`. No ordinary I-row needs the marked bay/fuel campaign gate or extra
JVM property. Admission stays off; do not use gated cargo/fuel/bay transfers as ordinary production technology steps.
Gated asset/status views, if separately needed, belong to J and retain their own switch-off comparison.
Phase 5 REF/SOAK uses a non-root user on 4 vCPU/8 GiB/Debian 12, Java 17, server `Xmx4G` maximum,
bound to loopback with SSH-tunnel client access on an exclusive node.

Evidence root E is `D:/GitHub/ARCE-Task-Evidence/final-campaign/659bbcb6a/`. Use
`E/phase2-v1/<machine>/<round>/`, `E/phase3-ds/<row>/`, `E/phase4-v2/<sitting>/` and `E/phase5-ref/<run>/`.
Retain `server.log`, native client logs, `results.jsonl`, `summary.json`, configuration, fixture and candidate
identities, raw samples and per-step UTC times. Screenshots are `<row>-<step>-<client>-<language>-<scale>.png`;
videos are `<row>-<client>.mkv`. Index each artifact/reference/SHA-256 in `E/results/RESULTS.md`, `results.json`
and `FINDINGS.md`; seal `E/SHA256SUMS.txt`. Each result also records Forge/JEI, machine, tester/script revision,
start/end UTC and JAR SHA-256. Missing prerequisites mean NOT_COVERED with a reason, not a speculative PASS.

## I01 — One technology route from vanilla resources to endgame

**Phase/level/machine/accounts:** 2 DS/V1, main real-GPU client A and packaged server.
**Gate:** Ordinary P2 technology-route world; admission switches off.
**Preconditions:** P2 new-world route fixture and the candidate's acquisition/reachability witness reports.
O4 is the owner's pending confirmation of scripted rehearsal plus a recorded hand-played subset, not a requirement
for a full survival-played run and not authorization to omit the complete route.

1. [H] Freeze the witness route, recipes, counts and source resource ledger. → Starts with vanilla resources;
   console grants or scene setup are disclosed rather than represented as survival acquisition.
2. [H/P] Follow the witness to the combustion generator. → The generator is crafted/formed and provides the expected
   server-authoritative energy from its documented inputs.
3. [H/P] Produce the route's classic-machine components through their actual processes. → Inputs, outputs,
   intermediate machines and advancement steps match the candidate's witness, without unavailable required nodes.
4. [H/P] Build, fuel and fly the route's legal rocket to the Moon and return. → Valid travel, inventory/fuel and
   life support remain coherent; no creative/gated fill is passed off as technology-route completion.
5. [H/P] Establish the station and complete an inter-system warp. → Costs/consent and station context agree with the server.
6. [H/P] Build and operate the selected endgame device. → Its normal recipe, permission and operation complete the route.
7. [P] Perform the owner-agreed subset by hand on C4 and review the full rehearsal video. → Exact covered steps,
   setup shortcuts, controls and judgement are recorded; build hash appears in the video index.

**Accepted preparation/action steps (W408):**

(C29) Use one continuous scripted C4 witness from first machine to the **railgun pair** endgame device (accepted choice), including recipes/counts/resources. **Needs package K16**: route scene and witness manifest. O4 still chooses/records the hand-played subset; rehearsal is not a full survival-played claim.

**Evidence to keep:** Continuous/referenced route video, witness route and resource ledger, per-stage screenshots,
server/client logs and the owner-confirmed hand-played subset under phase 2.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I02 — Many machine types coexist, unload and scale

**Phase/level/machine/accounts:** 3 DS on development PC with A; 5 REF on the reference node.
**Gate:** Ordinary P2 many-machine layout.
**Preconditions:** The P2 many-machine fixture (W62's S6 layout), enumerating every delivered v1.8 machine kind,
idle/active states, coordinates, recipes, buffers, energy, fluids and progress. C30 selects 144 controllers without another scaling factor.

1. [H] Capture the complete layout's initial state. → Every machine kind is present, not just the old v1.2 idle fixture.
2. [H] Run the selected machines together. → Processes progress with exact resource conservation and bounded queues.
3. [H] Remove fixture-owned loads and move A away. → Machine chunks unload without permanent tickets.
4. [H] Return and then restart the copy twice. → Compare resources/progress after each reload; no duplicate output
   or lost state. This also covers W62's requirement to restart every v1.8 machine mid-job twice.
5. [H] On REF, scale the same declared layout and record idle measurements. → Work/cost is measured rather than
   inferred from a “no recipe search” counter.
6. [H] Activate the scaled layout and sample CPU/MSPT/RSS/GC/network. → Mean ≤25 ms, P95 ≤50 ms, P99 ≤100 ms,
   RSS not persistently >5.5 GiB, no watchdog/OOM/unexpected forced chunks; state the exact active population.

**Accepted preparation/action steps (W408):**

(C30) Use FI10's 144 controllers, 16 of nine kinds, with no further scaling factor. **Needs package K05**: concrete many-machine layout/recipes/ledgers and per-machine restart sequence. Classification/inventory comes from W402/K13; no extra scaling is invented.

**Evidence to keep:** Phase 3 machine-by-machine inventory/progress/unload/restart comparisons and phase 5 layout,
scale factor, raw idle/active samples, queue/ticket counts and host record.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I03 — v1.7 placeholder and old-ID upgrade

**Phase/level/machine/accounts:** 3 DS, development PC and A. **Gate:** Ordinary upgrade copy.
**Preconditions:** P2 v1.7 world with every placeholder and old registry ID, its pre-upgrade ID/state manifest and
immutable archive/hash. The complete archive/ID manifest **needs package K02**.

1. [H] Hash the source archive and copy it without modifying the original. → Source remains recoverable and unchanged.
2. [H] Start that copy on C4 and enumerate old blocks/items/roots. → All stable IDs remain; replacement art/behavior
   does not silently delete placeholders or invent an ID migration.
3. [P/H] Use representative upgraded machines and inventories. → State and player resources are usable, not merely loadable.
4. [H] Stop and restart twice. → Compare the same IDs, contents and managed state after each start.
5. [H] Re-hash the original archive and backup. → Both unchanged; preserve failed copies separately if upgrade fails.

**Accepted preparation/action steps (W408):**

(C30) **Needs package K02**: immutable all-old-ID v1.7 source and manifest. Use K13's delivered inventory to enumerate new art; reconstruction remains labelled as such and is not a played-source claim.

**Evidence to keep:** Phase 3 archive identity, pre/post ID/state inventory, two restart comparisons and full logs.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I04 — Four Forge × JEI cells, real recipe display and usage

**Phase/level/machine/accounts:** 2/3 DS/V1, development PC, real-GPU client A.
**Gate:** Ordinary P2 many-machine/recipe world. **Preconditions:** Four clean profiles: Forge 47.4.10 and
47.4.23, each JEI absent or JEI 15.56.0.205 present, all with the same C4 JAR. Pin candidate-shipped category
counts from its resources/runner; W188's historical counts are not automatically C4 counts.

1. [H] Start the packaged server and client in the first matrix cell. → Correct Forge/JAR/JEI identity, no sidedness error.
2. [H] Join fresh. → For JEI-present cells, retain every category receipt in order: the last `status=registered`
   receipt after tags arrives has the shipped count; early zero/held counts are allowed, wrong final counts are not.
   Without JEI, require a normal join with no optional-class loading error, not a nonexistent JEI receipt.
3. [P/H] In JEI-present cells, open every ARCE category and inspect a tag-based input's alternatives. → Actual
   ingredients render, not barrier/“Empty Tag” placeholders. In absent cells, mark this JEI-only action not applicable
   and inspect the candidate's ordinary recipe/machine screen instead; do not attempt to open an uninstalled UI.
4. [P/H] In JEI-present cells, use recipe/usage lookup on representative inputs/outputs in every category. → Correct
   inputs, fluids, outputs and machine category in both indexes. In absent cells, exercise the pinned ordinary recipe.
5. [H] Run `/reload`, with JEI open only where installed. → Tags/recipes stay usable in the ordinary machine;
   present cells also keep the correct final JEI categories without duplicates.
6. [H] Stop and restart the same world twice and reconnect. → Tag gate resets per connection; correct final counts
   and screens return. Without JEI, ordinary machines and server work without optional client classes.
7. [H] Repeat steps 1–6 in each of the other three cells. → Preserve four separate results, not one combined receipt.

**Accepted preparation/action steps (W408):**

(C31; W400) Read category/recipe counts from C4 in each actual JEI-present named cell; W188 totals remain history. Show at least one recipe page and one usage query per category, capturing final synchronized receipts. The four manifest paths/hashes are in A. Runtime counts still **needs package K13**; recipe/machine scene **needs package K05**.

**Evidence to keep:** Phase 2 real-client pages/usages/screenshots and phase 3 per-cell command/profile manifests,
ordered category receipts, counts, initial/restart client/server logs and state comparisons. D9 limits v1.8 support
to these four cells; it does not prove or promise a third-party modpack matrix.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I05 — Long automated production conserves resources

**Phase/level/machine/accounts:** 5 SOAK on REF, two connected real players or O7 verified equivalent.
**Gate:** Ordinary P2 many-machine/reference workload, admission switches off.
**Preconditions:** A declared chain fed by vanilla hoppers or approved pipes, with recipe stoichiometry and initial
item/fluid/energy ledgers; no unknown third-party mod. Integrate this with I10's ≥4-hour workload.

1. [H] Inventory every input, output, internal buffer, tank and loose item before starting. → Exact initial totals exist.
2. [H] Run automated production for the complete multi-hour interval. → More than one batch completes, with no
   duplicated outputs, hidden spill or unexplained fluid/resource disappearance.
3. [H] Save and reload the chain during production. → Pending work resumes consistently; record restart boundaries.
4. [H] Continue production and capture final inventories/fluids/loose items. → Inputs minus recipe consumption and
   outputs plus in-progress state reconcile exactly; report conversions rather than equating unlike items.
5. [H] Compare periodic samples and final state. → No progressive resource gain/loss; an empty/idle soak is not coverage.

**Evidence to keep:** Phase 5 chain/recipe manifest, timestamped totals, all restart logs, conservation calculation,
spills and unresolved differences, alongside I10's continuous samples.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I06 — Sample all new presentation and inspect every high-risk asset

**Phase/level/machine/accounts:** 2 V1, A on real GPU. **Gate:** Ordinary P2 presentation/many-machine world.
**Preconditions:** Candidate asset inventory, sample selection and complete high-risk list, including all new
screens/models/textures/sounds. Missing sample definition is a preparation gap, not permission to omit a class.

1. [H] Map every new asset category and high-risk entry to a scene/screen. → Coverage is enumerated against C4.
2. [P/H] Display each selected screen/model/texture in its intended context. → No purple-black texture, absent
   mesh, clipped essential control or unreasonable model/collision placement.
3. [P] Listen to every selected sound at its real producer. → Sound and subtitle match the server-visible state;
   unbound producers are recorded, not silently “passed” with `/playsound`.
4. [H] Search the complete client log for missing model/texture/bake warnings and recurring OpenGL errors.
   → Zero missing model/texture acceptance; retain all matched lines and findings, not just a count.
5. [P] Review the contact sheets and free-look scenes. → Owner judgement is separate from harness capture.

**Accepted preparation/action steps (W408):**

(C30; W402/R546) Use K13's `classification.csv`/`model-families.csv`: inspect all high-risk OBJ/multiblock/armour and W244/W261/W265-corrected assets, plus one pick per other model family. Open **every screen once per language**, including the eleven no other row names (W402 section 4). The 48 unreferenced textures are v1.9 Low cleanup, outside this campaign (R546), not silently a passed sample. **Needs package K05**: scene placement; screen navigation/recheck controls **needs package K16**.

**Evidence to keep:** Phase 2 coverage inventory, high-risk complete checklist, contact sheets, screenshots/video,
sound index, logs and matching-line list. I07/J15 provide deeper checks but do not replace the broad inventory.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I07 — OBJ, baked models, multiblocks and armour on a real GPU

**Phase/level/machine/accounts:** 2 V1 on real GPU A; phase 4 V2 supplementary formation/tracking observations
from W241, with B. **Gate:** Ordinary P2 presentation/machine/rocket scenes; no admission changes.
**Preconditions:** Sources [W241](W241.md), [W244](W244.md), [W249](W249.md), [W255](W255.md),
[W256](W256.md), [W258](W258.md), [W260](W260.md), [W261](W261.md), [W265](W265.md).
Use their final corrections: actual Capsule mesh, all-face `rocketpad_`, and flat laser-gun icon, not their earlier drafts.

1. [H] Form black-hole generator, orbital laser drill, normal precision assembler, railgun, rolling machine and
   surface elevator anchor in every supported facing. → Full-scale hull placement, UVs, lighting and culling are
   inspectable; smaller modern patterns may leave intentional world blocks visible, not automatic overlap approval.
2. [P] Walk around and through the elevator platform and inspect all six hulls from every side. → Record clipping,
   cross-cell overlap, z-fighting and clearance; anchor is canonical NORTH, not a legacy orbital hull or 508-block tether.
3. [H] Use the mirrored precision formation. → JSON/world-block fallback, not a falsely mirrored mesh.
4. [H] Rotate a retained directional controller, then restore its validated facing. → Hull clears on mismatch and
   returns after valid restoration; server formation/bindings are not rewritten by visual flags.
5. [H] Break/repair and unload/reload each body, with B observing in V2. → Formed hull and laser/accretion/tether
   effects agree on both clients; no stale hull after invalidation.
6. [H] Form the precision laser etcher in all four facings; rotate/restore its controller and break/repair its motor.
   → Hull follows validated facing, clears on mismatch, restores correctly; no coplanar overlap or wrong light/culling.
7. [H] Start, cancel and commit elevator rides; remove an endpoint and change Level. → One stationary Capsule per
   pending endpoint, doorway NORTH, no Door mesh; cage clears on terminal paths. Inspect inside/outside, countdown
   tint, rider visibility and the documented floor 0.25 blocks above rider feet/platform-corner protrusion.
8. [H] View both mono and both bipropellant motors placed, adjacent to tanks/pad surfaces and assembled on rockets.
   → Open nozzle does not hide neighboring faces, rim/seams remain inspectable; inspect UV orientation against W241's
   audited renderer inventory (C32, not unavailable 1.12 screenshots), flat/smooth lighting, particles and all eight item display contexts/both hands.
9. [H] Repeat motor inspection for the nuclear nozzle in all six facings. → Visual rotation follows each state;
   only the downward nozzle is flight-legal. No test assumes sideways legality.
10. [H] Inspect the static saw block and formed cutting machine in four machine facings. → Saw axle remains east–west;
    record its motor mismatch in two rotations and both placed/K2-hull blades crossing, parallelism or z-fighting.
    R285 accepts the axle limitation, not an unobserved visual result.
11. [H] Inspect the three standalone fuel tanks, open ends, adjacent faces, seams, particles, held/inventory views
    and assembled snapshots. → Rounded standalone mesh; no connected/top/bottom presentation is implied; nuclear tank unchanged.
12. [H] View the landing-pad grid from top, bottom and sides and in item/particle contexts. → `rocketpad_` on every
    face per R289, no connected border and no live NEW `landing_pad.png` side/particle assumption.
13. [P/H] Hold and use `basic_laser_gun` in both hands, then inspect GUI/ground/fixed/head views. → R293's flat icon
    and upstream hand transforms; judge grip, muzzle/beam alignment, animation, lighting, particles and sound, not the removed OBJ.
14. [P] Inspect armour and worn component fit from all sides, sneaking and with elytra. → No missing resources or
    severe clipping; pair with J14's full mount/HUD matrix.
15. [H] Press F3+T, reconnect and exercise approved missing-primary-resource packs. → Formed hulls use documented
    project fallbacks; missing/corrupt Capsule resource gives the existing line cage; ordinary baked motor/tank/saw
    models have no promised runtime fallback. Capture failures rather than pretending every model has one.

**Accepted preparation/action steps (W408):**

(C32) No captured 1.12 session exists; "compared with 1.12 screenshots" is NOT_COVERED for that reason. Compare instead to W241's audited renderer inventory at upstream `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`, preserving W244/W261/W265 corrections and without importing unaudited art. W402 delivers `k13-missing-resource-pack.zip`, SHA-256 `81c43eea624037097b995d69d4317ee38a2b269f9fe99685c635acb35e88e1cf`, under `D:/GitHub/ARCE-Task-Evidence/final-campaign/659bbcb6a/fixtures/K13/`; its seven source-derived overrides/fallback expectations are W402 section 7, not observed loads. **Needs package K14**: phase-4 paired break/repair/tracking scene and actual pack checks.

**Evidence to keep:** Phase 2 per-model/facing/context screenshots, owner judgement, resource-pack names/hashes,
full client logs, reload observations and phase 4 paired formation views. Sources give no substitute automated
visual verdict; the 1.12-screenshot comparison remains NOT_COVERED under C32; use W241's audited inventory instead.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I08 — Language, GUI scale and resource reload

**Phase/level/machine/accounts:** 2 V1, A real GPU. **Gate:** Ordinary P2 many-machine/presentation world.
**Preconditions:** Complete candidate screen inventory, English/Chinese profiles and reproducible long labels/refusal states.

1. [H] Open every new v1.8 screen in English at scales 2, 3 and 4. → Labels, values, tooltips and essential buttons remain usable.
2. [H] Repeat in Chinese at each scale. → No raw translation key, clipped reason code or language-dependent unreachable control.
3. [H] Resize the window and repeat fullscreen. → Layout remains within the viewport; include long recipe/status names.
4. [H] Press F3+T with each representative screen open. → Textures/fonts restore; current server state is not reset or misreported.
5. [P] Review both-language contact sheets. → Actual readability is judged, not just pixel bounds. J14 separately covers HUD scales 1–6.

**Accepted preparation/action steps (W408):**

(C30) Open every delivered screen once in each language; K13's 32-row `screens.csv` includes overlays/non-menu screens and the eleven not named by another row. Use C2 effective viewports, never clamped scale 4 as a separate result. **Needs package K16**: navigation/long-state controls; **needs package K05**: machine placement. W402 supplies screen/asset classifications.

**Evidence to keep:** Phase 2 screen × language × scale × mode index, screenshots, tooltips/reload timings and logs.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I09 — Rendering, reload heap/time and JEI responsiveness

**Phase/level/machine/accounts:** 2 V1, A on real GPU with P3 frame-time capture O8.
**Gate:** Ordinary P2 complex-model world and 64/512/2,048-block rockets.
**Preconditions:** Same hardware/resolution/settings for vanilla and empty Forge baseline; profiles with/without
JEI 15.56.0.205. Freeze sampling interval, frame collector and heap measurement method before running.

1. [H] Capture vanilla and Forge empty-scene frame times. → Average FPS, 1% low and first-stall baseline are retained.
2. [H] Capture the same metrics with the centrifuge OBJ and every formed v1.8 multiblock in view. → Compare to the
   baseline, not a guessed absolute FPS floor; include single/multiple rockets of 64/512/2,048 blocks.
3. [H] Record heap and timestamp immediately before F3+T. → A reproducible pre-reload sample exists.
4. [H] Press F3+T and record completion time and post-reload heap/cache. → Record elapsed time, first stall and
   resource/cache release; no missing model/texture or persistent OpenGL error storm.
5. [H] Repeat steps 3–4 for three reloads without JEI, then three with JEI. → Retain every value, not only the best run.
6. [P/H] With JEI present, open each classic category. → Record time to first drawn frame and actual page usability.
7. [P/H] Search and perform usage lookup. → Record response time and correct results with the full recipe set.

**Accepted preparation/action steps (W408):**

(C5, R535; W399/W403) Follow A's two same-cell Vanilla/Forge baseline runs, average/slowest-1%/first-stall/longest-frame definitions, reporting-only 50 ms and review-only spread trigger; no FPS/stall cutoff exists. Owned-client heap/histogram proxies are measured outside timed frames, never asserted as GPU/cache release. **Needs package K04**: three rockets; **needs package K15**: direct heap/cache qualification and comparison plan; O8 approves capture/version.

**Evidence to keep:** Phase 2 `frametime/` raw data, baselines/settings/hardware, scene manifests, first-stall video,
three reloads per JEI state with heap/cache readings and per-category/search timing table. Sources set no numeric
FPS-ratio, reload-time or JEI-response cutoff; do not create one after seeing results.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I10 — Four-hour joint reference workload within the declared matrix

**Phase/level/machine/accounts:** 5 SOAK, REF node and two real clients or O7 verified equivalent.
**Gate:** Ordinary reference copy; admission switches remain off.
**Preconditions:** P2 docs/17 reference world, node exclusive, Java 17/`Xmx4G` max. Declare Forge/JEI cell and map
I04's complete four-cell rerun separately; D9 does not require four independent four-hour soaks.

1. [H] Inventory the load: two connected players; 16 oxygen devices/sealed bodies; ten stations; 100 missions;
   two active rockets. → Counts and actual activity are proven, not empty-server labels.
2. [H] Start continuous host/tick/RSS/GC/ticket sampling and the I05 production/H05 device workloads. → One joint
   run includes v1.8 content without double-counting elapsed time as separate runs.
3. [H] Assemble and disassemble a 2,048-block rocket during the run. → Capture bounded work, contents and timing.
4. [H] Perform a dimension transfer. → Record server authority, resources and cross-dimension recovery context.
5. [H] Perform periodic normal saves/restarts with field comparisons. → Writers drain normally and state remains conserved.
6. [H] Continue for at least four hours and compare all budgets. → Mean ≤25 ms; P95 ≤50 ms; P99 ≤100 ms;
   non-start/save spikes not sustained >500 ms; RSS not sustained >5.5 GiB; watchdog/OOM/unexpected forced chunks 0;
   no monotonic memory growth. Interrupted runs remain failed/incomplete and are not added together.
7. [H] Write the declared-cell and four-cell-coverage report. → Neither W188's short historical matrix nor a future
   v1.9 modpack run substitutes for this C4 soak.

**Accepted preparation/action steps (W408):**

**Needs package K17**: complete combined load, four sampled hours, 30-minute flushed saves and two-hour restart schedule (C4/R535 procedure parameters). **Needs package K15**: complete raw-tick/all-ticket observation; O7/O11 supply real connections and REF acceptance. Keep each Forge/JEI result distinct.

**Evidence to keep:** Phase 5 raw full-duration samples, workload/actions/connection manifest, configuration,
restart/resource comparisons and exact actual budget table; link I04's four-cell evidence by row ID.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## I11 — New-machine packet policy and endgame permissions with two clients

**Phase/level/machine/accounts:** 4 V2, A owner, B non-owner and station-member variants, packaged DS.
**Gate:** Ordinary P2 machine/endgame fixture.
**Preconditions:** Every new menu/intent mapped to its holder and policy; include beacon, landing pad,
force-field projector and pump as well as endgame devices. Record permissions and initial resources/chunks.

1. [P/H] A operates each valid local machine control. → Ordinary intended operation works with the correct holder/window.
2. [P] B tries the same controls on A's devices as a non-owner. → Refusal and no unauthorized state/resource mutation.
3. [P] Repeat as station member but not device owner. → Membership alone grants no prohibited endgame action.
4. [H/P] Exercise stale-window, out-of-reach, unloaded-holder, spectator and repeated-intent variants using the
   candidate's pinned shared-policy fixture. → Sender, distance, permission, state/range/load and rate checks hold;
   client-provided positions never cause arbitrary chunk loading. Do not inject an unspecified packet format.
5. [H] Compare both clients' views and the server's resource/chunk/audit projections. → Authoritative refusal/status
   synchronization and unchanged protected resources. W62's menu-specific tests are not a V2 substitute.

**Accepted preparation/action steps (W408):**

(C14 corrected by R546; W402 section 6) Use delivered K13 holder/policy matrix, with bounded tool receipt and unchanged server-state readback for every negative case; logs only where code emits one. `suit_motion` affects only the sender's own worn jetpack and needs **no distance step**. Beacon, pad, projector and pump are block-use devices, not invented menus/packets. The projector's silent non-operator use is accepted Low KI-V180-UI-01, still check unchanged state. **Needs package K18**: safe per-channel probes and named A1 references for unreachable/late cases; the live fixture **needs package K16**.

**Evidence to keep:** Phase 4 menu/holder/variant table, paired screenshots/video, both client logs, server policy
receipts, unchanged-resource/chunk comparisons and successful owner controls.
**Result fields:** Actual: ____; Screenshot or video: ____; Tester: ____; Date: ____; Build hash: ____.

## Open questions for Root

- **Answered:** C29 railgun-pair continuous witness; C30 high-risk/every-screen/family sample/144-controller scale; C31 page/usage query method; C32 audited W241 inventory with unavailable 1.12 screenshot comparison NOT_COVERED. W402 delivers classifications/screens/policies/resource packs; R546 covers unnamed screens, silent projector Low and no jetpack distance step.
- **I01:** complete witness/recipe/count/control manifest **needs package K16**; O4 chooses the hand-played subset.
- **I02/I03/I06/I08:** live machine scene **needs package K05**, all-old-ID historical source **needs package K02**, navigation/recheck batches **needs package K16**. Inventories/classifications are delivered, not new open choices.
- **I04:** runtime per-cell JEI counts **needs package K13**, recipe scene **needs package K05**. W400 names/manifest paths are answered.
- **I07:** actual missing-pack load and paired break/repair/tracking scene **needs package K14**. No captured 1.12 session is invented.
- **I09:** three rocket scenes **needs package K04**, direct heap/cache observation **needs package K15**; O8 approves capture/version. No FPS/reload/response cutoff exists.
- **I05/I10:** combined workload **needs package K17**, production recipe layout **needs package K05**, complete observers **needs package K15**. O7/O11 supply connections/node access.
- **I11:** live fixture **needs package K16**, bounded receipt/readback/probes and A1 references **needs package K18**. Follow the actual holder matrix, not blanket owner-only policy.
