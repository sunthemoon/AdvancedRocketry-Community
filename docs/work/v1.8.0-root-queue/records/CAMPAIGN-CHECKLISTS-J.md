# Campaign checklists J: retained acceptance and gated interactions

These are unrun P4 lists for J01–J29 of [the campaign](FINAL-CAMPAIGN-PLAN.md).
They are procedures, not C6 results, switch approval or Required Gate approval. R581 identifies C6 as
`c8fa16e16`; the full commit, hosted release hash and artifact path are pinned by C1 in checklist A.
Sources/API artifacts still **needs package K01**.
Historical results in the sources are not copied into the blank result fields.

Accepted common procedures for **every J row**: `CAMPAIGN-CHECKLISTS-A.md`, section "Accepted procedures and delivered tooling",
pins C6 (C1), the four W400 `cell.json` paths/hashes, W399's commands and session
fields, C2/R535's effective display cells, C5/C10/R535's matched sampling/review
rules, R546's receipt-plus-state refusal proof, and W403's observers, evidence root
and checksum commands. Apply them before the row's actions; old generic display
wording or demands for nonexistent refusal logs do not override these decisions.

## Shared preconditions, execution labels and evidence

H marks a harness/operator action; P marks a person's action or judgement. Each arrow gives the expected result.
Numbered steps are performed in order, repeating an explicitly named matrix for each separate fixture copy.
P1's identical hosted C6 JAR is required on server and clients. Use Java 17, Forge 47.4.10 without JEI for the
full pass; repeat affected compatibility smokes on 47.4.23 and with client JEI 15.56.0.205. Record the exact cell;
JEI is not added to a server merely to label a JEI-present client run. I04 owns the complete four-cell campaign.
Pin difficulty, distances, mods, configs and server properties before each run. No source specifies a universal
set of coordinates or command to construct every J fixture: the P2 manifest must supply the missing values.

Use P2's immutable source fixtures: representative v1.3–v1.7 worlds, the many-machine layout (W62 item 10.2),
the API fixture mod against C6, the 64/512/2,048-block rockets, and the docs/17 reference workload world.
J-specific bay/cargo/fuel, progression and sleep arrangements are additions to copies of these fixtures, not
newly claimed P2 archive names. Record source archive/hash, copied-world path, seed, dimensions, positions,
IDs, account roles, initial resources, expected boundaries and driver revision before step 1. Never edit a source
world or an old sealed evidence packet. Missing fixtures or resources mean NOT_COVERED, not PASS.

Phase 2 V1 uses main-PC real GPU client A, with GPU/driver/CPU/OS/resolution/JVM and GUI scale recorded; V0 is
not a substitute. Phase 3 DS/S2 uses a packaged dedicated server and the same complete copied world for both
restart observations. Phase 4 V2 uses two independent premium accounts A/B signed in by the owner on two real
GPU clients; `online-mode=true`, `white-list=true`, `enforce-whitelist=true`, `enable-rcon=false`, restricted
network access and exactly those two whitelist entries. Synchronize UTC on all machines; archive both client logs.
Phase 5 REF uses an exclusive Debian 12, 4 vCPU/8 GiB node under a normal user, Java 17 and `Xmx4G` at most.
Bind that server to loopback; the phase-4 clients reach it through SSH tunnels, not a new public test endpoint.
Keep its host/steal/swap/disk record and at least 10 GB free before heavy execution. The docs/17 load and limits
apply: mean/P95/P99 MSPT at most 25/50/100 ms, RSS not sustained above 5.5 GiB, no sustained non-start/save
spike above 500 ms, watchdog/OOM/unexpected forced loads zero. A short microbenchmark is not the reference load.

### Disposable campaign gate, production switches off

For J01–J10 where a gated fixture is needed, J02/J04 and gated J23–J29, use the disposable dedicated copy and
[W254-V2-CHECKLIST section 0.1](W254-V2-CHECKLIST.md), as corrected by R298 and implemented by W269.
All production admission constants remain off. This is O2 test admission, not permission to enable a product.
Place both properties **before** Forge's argument file:

```text
-Dadvancedrocketrycommunity.releaseTestHooks=true
-Dadvancedrocketrycommunity.bayV2CampaignPlayers=<A-full-hyphenated-uuid>,<B-full-hyphenated-uuid>
```

Use exactly two distinct UUIDs, no spaces. In the **copied** world only, write the regular, non-symlink marker:

```powershell
$worldRoot = (Resolve-Path -LiteralPath '<disposable copy>').Path
$path = Join-Path $worldRoot '.arce-bay-v2-campaign'
[IO.File]::WriteAllBytes($path, [Text.Encoding]::ASCII.GetBytes("ARCE_BAY_V2_DISPOSABLE_WORLD_V1`n"))
Get-FileHash -LiteralPath $path -Algorithm SHA256
```

Expected: 32 ASCII bytes with LF/no BOM, SHA-256
`e67124e2a697a32ccdafc384e672362f81c732e5e01872012a2e22c6e4590f98`.
Do not use `Set-Content`/`Out-File`. At startup and each selected login, require these INFO shapes, with lowercase,
lexically sorted UUIDs and the absolute copied-world root:

```text
ARCE_BAY_V2_CAMPAIGN startup property=advancedrocketrycommunity.bayV2CampaignPlayers clients=<uuid1>,<uuid2> world=<absolute-world-root> marker=true
ARCE_BAY_V2_CAMPAIGN connect property=advancedrocketrycommunity.bayV2CampaignPlayers clients=<uuid1>,<uuid2> world=<absolute-world-root> marker=true player=<uuid>
```

Selection lines alone do not prove the whole gate: hooks, server thread, RCON and every connection are checked.
No third real client may connect; that closes the gate and invalidates the sitting. Facility-owned test players
may coexist but do not replace either real client. Startup refusal can exit 0: require `Done` and absence of the
format error/crash report. Stop if `ARCE_BAY_EXIT observer unavailable` appears. Launch no bay rocket outside
the prescribed held-rocket procedure; record every unexpected `ARCE_BAY_CLAIM` with time and identities.
Marker/selection changes require restart. LAN ignores this selection property; J03 needs its own approved route.

**Fuel-only addition:** gated J23–J29 also require
`-Dadvancedrocketrycommunity.escrowLoaderAdmission=true`. Marker/hooks/selected UUIDs alone do not enable that
seam. Keep `EscrowCoordinator.PRODUCTION_ADMISSION=false`, the four cargo switches and bay switch off.
The separate ordinary-disabled comparison uses no release-test hooks or fuel seam. Fuel's P9 decision remains
pending; an enabling change creates a new candidate and requires affected rows and no-hook smoke again.

### Evidence common to every row

E is `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/`. Keep as-run lists under `E/checklists/`,
candidate identities under `E/candidate/` and immutable source-fixture hashes under `E/fixtures/`.
Use `phase2-v1/<machine>/<round>/`, `phase3-ds/<row>/`, `phase4-v2/<sitting>/` and `phase5-ref/<run>/`.
Keep native `server.log`, client logs, `results.jsonl`, `summary.json`, commands, UTC step/boundary times,
configurations and before/after state. Screenshots: `<row>-<step>-<client>-<language>-<scale>.png`;
videos: `<row>-<client>.mkv`. A stitched sheet supplements, never replaces, original frames.
Every result in `E/results/RESULTS.md` and `results.json` records ID/level, candidate commit/JAR SHA-256,
Forge/JEI, machine, tester/script revision, UTC start/end, PASS/FAIL/NOT_COVERED, artifact reference/hash and
finding or coverage reason. Seal `E/SHA256SUMS.txt` after the phase; keep failures and add corrections separately.
Delete only the run's owned disposable server/export/archive trees after retaining logs/state/manifests; preserve
source worlds and sealed evidence. Root, not these checklists, fills release reports and makes acceptance proposals.

## J01 — Satellite bay menu statuses and OWNER_PENDING_FULL

**Phase/level/machine/accounts:** 2 V1, main GPU client A, packaged dedicated server; scripted then owner review.
**Gate:** Ordinary disabled comparison plus marked campaign copy; all production admission off.
**Preconditions:** P2 legal bay rocket and accepted/locked solar packages; Root's bounded status/cap fixture.
Sources: [C6 switch report section 3.4 and 9.1](C6-SWITCH-REPORT.md), [owner cap decision R244](OWNER-DECISIONS-20261101.md).

1. [H] Open the bay's ordinary no-hook menu. → Disabled admission is represented honestly; inspecting it does not deploy.
2. [H] On the gated copy, open the bay in each status prepared by the fixture. → Visible reason matches the server;
   package, slot and progress text do not overlap. The report does not enumerate a complete GUI-status sequence;
   That sequence **needs package K11** before claiming complete coverage.
3. [H] Establish 16 unique pending union entries for owner A using the pinned cap fixture. → The union includes fuel,
   cargo and bay, with no OP exemption; do not count one captured/UNKNOWN successor more than once.
4. [H] Request another A bay deployment. → Menu shows `OWNER_PENDING_FULL`; no package consumption or new claim.
5. [P] Inspect the full reason and slots in en_us and zh_cn at scales 2, 3 and 4. → Text is readable and agrees with
   receipt/server readback (log only where emitted, R546), including the refusal suffix; record the actual localized wording rather than inventing it.
6. [H] Repeat the cap view after a save/restart on the same copy. → Durable held charges remain; restart is not a release.

**Accepted preparation/action steps (W408):**

(C1/C33) Pin the common C6 identity, not a historical candidate. Claim cap coverage only for the owner actually used by the fixture; a different owner's admission is tested only as B owning B's ordinary bay. **Needs package K11**: current menu/status/cap recipes and marked world; owner O10 supplies the two real UUIDs.

**Evidence to keep:** Phase-2 disabled/status/cap screens, pending-union identities and totals, server/client logs,
config/marker/properties, plus phase-3 restart comparison if step 6 uses that sitting.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J02 — Two-client safe exit on the packaged dedicated server

**Phase/level/machine/accounts:** 4 V2, two real GPU clients A/B and packaged dedicated server; owner plays.
**Gate:** Marked W269 disposable copy only; production admission off.
**Preconditions:** All shared gate checks; [W254 sections 0–4 and 6–7](W254-V2-CHECKLIST.md).
Use its Earth→Moon hand-fuelled two-seat bay rocket with accepted data and research-locked solar packages,
held at INTENT **48 blocks above its recorded origin**. Record origin, seat offsets, dimensions and pad coordinates.
W257/R283 and W262/R288 must be included in P1's candidate. Restore the held fixture for each independent subcase.
Place a 3×3 stone safe pad one block below the origin for safe cases. Witness and competing-exit cases are
separate two-player runs; never add a third real client to keep a witness aboard during two simultaneous exits.

1. [H] Record both health values, vehicle/assigned-seat states, rocket position, packages, fuel and loaded/ticketed chunks.
   → Initial state is reproducible and two real players, not injected UUIDs, occupy the seats.
2. [P-B] Sneak once. → B exits onto a checked safe position with unchanged health; A stays seated.
   Feedback begins “Exited to a checked standing position”; log has `outcome=safe code=SAFE candidate=<n>`.
3. [P-A] Press LEAVE SEAT. → A takes the next free checked position, not a launch; no “Flight request accepted”.
4. [H] Restore the held fixture with exit inputs exchanged (A sneaks, B uses the menu). → Ownership is unchanged;
   this reverses exit inputs, not a transfer of rocket ownership.
5. [P] Repeat the two exit inputs. → Both inputs/roles can leave safely; neither can move the rocket.
6. [P-A] In a restored two-player run, exit while B stays aboard. → B is never forced out; its seat/view is unchanged.
7. [P-A] Replay LEAVE SEAT while no longer riding. → No movement/new exit-attempt log; B's witness seat remains unchanged.
8. [P] In another restored two-player run, both sneak in the same second. → Search visits drain in capture order;
   first takes the first safe box, second the next box or fallback. Record actual attempt/capture sequences,
   candidates and positions, not merely two successful non-overlapping exits.
9. [H] Restore the held fixture without a safe pad near the origin. → Ordinary air fallback cannot be mistaken for a safe pad.
10. [H] Prepare a separate 3×3 landing floor about 10 blocks below the seat. → The floor is more than one block
    from origin height, avoiding the safe-search anchor; record exact coordinates and fall distance.
11. [P-B] Sneak once while A witnesses. → No dismount cancellation or teleport search beyond loaded bounds;
    fallback stays in place, `outcome=fall_offer code=NO_SAFE_EXIT candidate=0`.
12. [P-B] Let the first ordinary fall land without another action. → Single safeguard prevents ordinary fall damage;
    at the 48-block fixture the usual fall is about 2.4 seconds, not an asserted exact timing bound.
13. [P-B] Make a second ordinary fall. → Ordinary damage applies; the first offer is not reusable.
14. [H] Record W254 step 3.4 as not run on real clients, with its reason. → At the specified fixture the 200-tick
    deadline cannot be reached: ordinary landing happens first, and fluid/levitation/flight/climbing end the offer.
    Native X5 covers its no-movement-client expiry; do not invent a V2 expiry pass or a new flight-bypass fixture.
15. [H] Start another fallback run. → A fresh offer, not the spent one, is present.
16. [H] Execute `/execute at <B> run tp <B> ~0.1 ~ ~` during that fall. → Teleport ends the offer; ordinary landing
    damage applies. Repeat with an ender pearl in a separate run, without combining the two cancellation causes.
17. [P-B] Land a fresh fallback onto the pinned dripstone case. → Non-ordinary-fall damage is not prevented.
18. [P-B] Disconnect while seated in a restored held rocket. → The rocket stays held and the assigned seat remains.
19. [P-B] Reconnect while the hold is still active. → No remount; after about 10 seconds log
    `ARCE_PASSENGER_RECONNECT_EXPIRED`, with assigned seat still recorded.
20. [P-B] Disconnect during a separate airborne fallback. → No safeguard survives logout; assigned seat remains as specified.
21. [P-B] Reconnect in that fallback case. → No stale fall offer or automatic remount is restored.
22. [H] Kill the exited player in a separate exit run. → Death clears the offer.
23. [P-B] Respawn. → No stale protection is carried to the new life.
24. [P-B] Enter the prepared portal after another exit. → Dimension change ends the offer; it cannot teleport B back.
25. [P-B] As a nearby non-passenger, press LEAVE SEAT or sneak in separate attempts. → No exit log/movement/authority.
26. [H] Compare packages, fuel and seat manifest to each run's initial state. → Exit itself changes no bay payload or fuel;
    retained assigned seats are not silently cleared by vanilla dismount.
27. [H] Compare loaded chunks and `/forceload query` with the initial state. → Exit introduced no forced or arbitrary loads.
28. [H] Release the prescribed flight hold. → Flight retries and lands without waiting for the exited passenger;
    repeat with the passenger online and offline as separate runs. Later flight passenger phases may move/remount
    an online assigned B; that is distinct from the forbidden remount while the hold is active.

**Accepted preparation/action steps (W408):**

(C1/C34) Record W254 step 3.4 exactly as "not run; covered natively by X5": the real fall lands before expiry and the other controls end the offer. No V2 expiry pass is inferred. **Needs package K11**: marked held-bay scene, pads/seat coordinates and controls; owner O10 supplies A/B UUIDs.

**Evidence to keep:** Both continuous client videos and logs, all exact exit/offer/capture/expiry markers, health
and coordinates at each boundary, pre/post payload/seat/chunk comparisons, both roles and all cancellation runs
under phase 4. W254's near-floor residual (last sample within 0.01 of floor, or two movement packets in one tick
combining over four blocks of fall) is recorded with packet/tick evidence, not hidden or redefined as a generic pass.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J03 — Safe exit on LAN with each client hosting

**Phase/level/machine/accounts:** 4 V2 LAN, two real clients/GPU machines A/B; owner plays each host role.
**Gate:** Production switches off; W269 dedicated selection is not a LAN gate.
**Preconditions:** [W254 section 5](W254-V2-CHECKLIST.md). C34 records this row NOT_COVERED
while switches are off and no LAN gate exists. The following steps remain conditional,
not instructions to create a gate; any owner-enabled future scene **needs package K11**.

1. [P-A] Host the first LAN fixture using Root's approved route. → Both real clients see the held rocket and gate state is recorded.
2. [P] Repeat J02 safe-exit steps 1–5 on this host. → Both owner/crew roles exit safely with a seated witness as applicable.
3. [P] Repeat J02 fallback steps 9–12. → In-place fallback and the first single fall safeguard work for the LAN guest.
4. [P] Repeat J02 seated disconnect/rejoin steps 18–19. → No remount during the hold and the assigned seat is retained.
5. [P-A] Save and Quit while the applicable exited/seat state exists. → Orderly host save ends the session; after reopening,
   ordinary player data is used, no stale offer/remount, and assigned seat remains.
6. [H] In a separate disposable run, stop only the owned host process without a save. → An older saved position is
   possible; record the actual state and retained seat rather than requiring crash durability.
7. [P-B] Host the second LAN copy and repeat steps 2–6 with A as guest. → Both host assignments have independent evidence.

**Accepted preparation/action steps (W408):**

(C34) This row is NOT_COVERED while production bay switches remain off: integrated/LAN ignores the dedicated campaign property and no approved LAN gate exists. Do not execute the conditional steps through an invented bypass. Only an owner-enabled bay candidate under packet P4 permits production-admission execution; that is a new candidate, not authority granted here. Any then-permitted held scene **needs package K11**.

**Evidence to keep:** Approved LAN route/config, both host-role logs/videos, safe/fallback/expiry markers and orderly
versus unsaved-stop player/seat comparisons under phase 4. If the LAN route is missing, retain NOT_COVERED.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J04 — Bay online passengers and S3 with real clients

**Phase/level/machine/accounts:** 4 V2 with DS/S3 cuts, two real clients A/B; owner plus harness.
**Gate:** Marked dedicated campaign copy; production admission off.
**Preconditions:** [C6 sections 3.2–3.4](C6-SWITCH-REPORT.md), [W325 matrix](W325-MATRIX.md).
Real-client cut controls/checkpoints **needs package K11**; facility-player native passes are not V2.
The B4 native case names below come from `W331.md` section 3, not W325's older missing-fixture rows.
Use P2 bay copies with a real seated passenger and record source present/absent, X live/absent and save boundaries.

1. [H] Build the real-client case map for B3.8–B3.11, B4.6–B4.9, B8.5–B8.6 and EXIT.0–EXIT.10 below. → Ten
   online-passenger variants and the eleven S3 exit boundaries remain separate; use C6's W331 B4 additions rather
   than W325's historical missing-fixture description. Synthetic-only boundaries are not relabelled as V2.
2. [P-B] Board the mapped passenger seat. → Server manifest, actual rider and both client views agree before the cut.
3. [H] Stop the owned server externally at the selected S3 boundary. → Preserve whole-world state and boundary evidence;
   do not call a headless-only halt seam with real clients or substitute an empty manifest.
4. [H] Restart the same complete copy for observation 1. → Registered/consumed packages, unique satellite and flight
   authority follow the mapped cut oracle; both clients' actual seat/reconnect state is recorded.
5. [H] Save the recovered copy. → Persist the observed recovery, not a fresh imported fixture.
6. [H] Restart it for observation 2. → No duplicate satellite/package consumption/recovery debit or unauthorized seat remount.
7. [H] Repeat steps 2–6 for every mapped reachable variant. → Each has an independent boundary marker and
   real-client witness; unavailable/synthetic-only cases retain NOT_COVERED and a native-evidence reference.

   | Variant | Native case / boundary | C35 disposition for this pass |
   |---|---|---|
   | B3.8 | `W325_B3_STAGED_ONLINE`: REGISTERED staged-only, source present | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | B3.9 | `W325_B3_STAGED_ONLINE_ABSENT`: REGISTERED staged-only, source absent | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | B3.10 | `W325_B3_MOVED_ONLINE`: REGISTERED replaced, source present | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | B3.11 | `W325_B3_MOVED_ONLINE_ABSENT`: REGISTERED replaced, source absent | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | B4.6 | `W331_B4_GONE_X_S_PRESENT_ONLINE`: X absent, source present | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | B4.7 | `W331_B4_GONE_X_S_ABSENT_ONLINE`: X absent, source absent | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | B4.8 | `W331_B4_LIVE_X_S_PRESENT_ONLINE`: X live, source present | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | B4.9 | `W331_B4_LIVE_X_S_ABSENT_ONLINE`: X live, source absent | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | B8.5 | `W325_B8_STAGED_ONLINE`: Retirement staged-only | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | B8.6 | `W325_B8_MOVED_ONLINE`: Retirement replaced before callback | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | EXIT.0 | `X0_CONTROL`: Held passenger, no exit: recovery control. | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | EXIT.1 | `X1_OFFER`: Sneak exit, live fall offer. | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | EXIT.2 | `X2_DESCENT`: Detached descent, playerdata flushed. | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | EXIT.3 | `X3_CORRECTION`: Correction window before tracker acknowledgement. | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | EXIT.4 | `X4_LANDING`: Real-client-timed landing, one protected FALL, exit flushed. | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | EXIT.5 | `X5_EXPIRY`: 200-tick no-movement expiry: W254 says unreachable by real clients; native only, not V2 PASS. | Synthetic-only; real-client expiry unreachable as W254/X5 explains. No V2 pass. |
   | EXIT.6 | `X6_TRUSTED`: Trusted movement revokes the slot without remount. | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | EXIT.7 | `X7_REVOKED`: Same-Level teleport revokes protection; later landing unprotected. | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | EXIT.8 | `X8_SAFE_MENU`: Held-menu SAFE exit onto loaded pad, exit flushed. | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | EXIT.9 | `X9_FIRST_TICK`: Same-tick protected landing: mapped-observer native smoke; real timing must be proven or left uncovered. | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |
   | EXIT.10 | `X10_FIRST_TICK_REVOKED`: Same-tick teleport-revoked landing: same limitation, no fabricated client packet timing. | Synthetic-only; no real-client recipe yet; **needs package K11** to qualify it. No V2 pass. |

**Accepted preparation/action steps (W408):**

(C35) Each planned real-client variant below still **needs package K11**: a marked-world external-stop recipe and live qualification. Until supplied, that variant is NOT_COVERED at V2; its named native case is supplementary, never a facility-only V2 pass. EXIT.5 is synthetic-only for the documented real-fall expiry reason. No headless halt seam is invoked with a real connection.

**Evidence to keep:** Per-variant real-client case map, cut/receipt/journal/registry and source-copy hashes, two restart
comparisons, both videos/logs and seat states under phase 4 with phase-3 state references. Unavailable controls are NOT_COVERED.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J05 — Reference-node bay step timing, all 23 waits

**Phase/level/machine/accounts:** 5 REF, normal-user reference server; harness, verified player equivalents if needed.
**Gate:** Gated disposable reference fixture, production admission off; do not grant production admission for load.
**Preconditions:** P2 reference load plus bay census fixture. [C6 section 9.2](C6-SWITCH-REPORT.md) and R-043
define the waits. Pin the measurement driver and boundary instrumentation before running; include **more than
three census slots and more than two queued claims**, not just W336's development-host profiles.

1. [H] Calibrate native whole-tick and boundary timestamps on the reference host. → Keep observation cost included;
   raw game-time brackets and wall time are distinguishable from exclusive service execution time.
2. [H] Run the ten C6 timing profiles from section 3.2 on the candidate. → B12_D4, B12_D7, B13_FAILED,
   B13_UNKNOWN, B16_MOTION, B10_BLOCKED, RECONNECT, TURNS-2, TURNS-3 and D6_RETRY have both restart observations.
3. [H] Run the larger census/queue workload. → Actual D6 service, per-slot and whole-queue waits are measured;
   the “about one minute/36 minutes” figures remain estimates, not maximum latency promises.
4. [H] Measure each wait below at its actual boundary. → Unavailable/unbounded/unfinished cases stay explicit,
   not zero or inferred from a neighboring writer benchmark.

   | # | Boundary to report | Rule, not an invented latency limit |
   |---|---|---|
   | 1 | D0/B14 journal absence write | No overall bound. |
   | 2 | D1/outcome exact acknowledgement | Writer retries 200 ticks; outcome writes each tick; no overall bound. |
   | 3 | D3 REGISTRY_BLOCKED | No timeout; a chosen hold is not a bound. |
   | 4 | D3 first published registry completion | Include D3 and registry-writer cost separately. |
   | 5 | D3 REGISTRY_UNPROVED retry | 200 ticks per attempt, no overall bound. |
   | 6 | D4 shared cargo work-budget wait | Measure competing cargo, cross-reference J09. |
   | 7 | D4 receipt-store availability | No overall bound. |
   | 8 | D5 T/source/consumption-port readiness | No overall bound; three journal writes is a code argument. |
   | 9 | D5 copies | One changed copy per tick server-wide, no completion deadline. |
   | 10 | D6 before capture | No timeout. |
   | 11 | D6 BAY_PROOF_TURN queue | A departing claim rejoins at the back. |
   | 12 | D6 per slot/barrier | Minimum 20-tick spacing per Level forced batch, at most 32 completions/tick. |
   | 13 | D6 retry after refusal | 200 ticks per attempt, none overall. |
   | 14 | D6 shared capture quota | Include F1, W98 and sealed disassembly contention; J09. |
   | 15 | D7 quiet-store guard/fresh D6 | No overall bound. |
   | 16 | D7 UNKNOWN removal/exact retry | No overall bound. |
   | 17 | D7 refusal returning to D6 | Measure the repeat turn, not a completed claim. |
   | 18 | Restart INTENT processing | 32 steps/tick shared round-robin; held rows last the session. |
   | 19 | RESUME | No timeout. |
   | 20 | Released flight | No timeout. |
   | 21 | End when no receipt names L | Released/REPAIR may require offline repair; no timeout release. |
   | 22 | Reconnect queue | 200 ticks. |
   | 23 | Ticket expiry | 400 ticks after last renewal; isolate all other ticket owners. |

5. [H] Compare whole-tick cohorts and memory against docs/17. → Budget failures stay failures; no idle dilution or
   subtraction of probes, GC, checkpoint or cold-use work. Keep unbounded waits distinct from budgeted execution cost.

**Accepted preparation/action steps (W408):**

(C10/C36, R535) Use matched worlds/cells/host, two-minute discarded warm-up and ten-minute standard sample, with two baselines; spread triggers Root review only. No maximum latency exists for unbounded waits: retain raw waits, unfinished cases and reasons. Wait 23 runs on a world with no other ticket owner; a contaminated sweep is not compared to 400 ticks. **Needs package K12**: larger queues/isolated ticket world and raw boundary driver; **needs package K15**: REF observer integration. Owner O11 supplies node access.

**Evidence to keep:** All 23 raw boundary intervals, queue/slot/claim identities, D6/barrier and D3/writer samples,
reference-host configuration, tick/RSS/GC/JFR data and restart states under phase 5, with per-wait coverage verdicts.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J06 — Four cargo devices on a real GPU

**Phase/level/machine/accounts:** 2 V1, main GPU A and packaged dedicated server; scripted then owner review.
**Gate:** O2 marked campaign copy, all production switches off.
**Preconditions:** P2 cargo rocket copies; [W11CD leaf C-V](W11CD-DESIGN.md) and
[C6 section 4.4](C6-SWITCH-REPORT.md). Devices are `advancedrocketrycommunity:rocket_fluid_loader`,
`rocket_fluid_unloader`, `rocket_item_loader`, `rocket_item_unloader` in that namespace, not the fuel loader.
Pin a 64,000 mB cargo tank and a supported vanilla chest/barrel payload, four-slot item buffers and exact stacks.

1. [H] Open each ordinary disabled device screen on the no-hook comparison. → Inspection does not admit cargo work.
2. [H] Bind each gated device to its matching idle, unoccupied rocket using the linker. → Owner, direction,
   payload/cursor and binding identify cargo, not propulsion or the bay.
3. [H] Run a fluid load with 5,000 mB in the device and empty cargo tank. → One 1,000 mB control move leaves 4,000
   and 1,000 mB; reverse the source/destination setup for an independent unload.
4. [H] Run a pinned partial item move through each item device. → Exactly n items move; stack identity/NBT and
   remainder agree with the server. The design does not provide universal n for every stack; record the chosen case.
5. [P] Inspect labels, Fluid/item, amounts, status and selected cursor before/during/after each operation. → No
   tentative success; typed endpoints and final resource totals are accurate.
6. [P] Repeat the screen views in en_us/zh_cn, scales 2/3/4, windowed/fullscreen. → All controls and full reasons remain readable.
7. [P] Press F3+T with each screen open. → Resources reload without missing models, broken labels or lost authoritative binding.
8. [P] Observe the rocket immediately after each cargo operation. → Cargo-sensitive content hash and visual publication
   refresh match the new payload, with propulsion/geometry/logical identity unchanged; no stale visual after reopen.

**Accepted preparation/action steps (W408):**

**Needs package K12**: four-device/cursor/partial-stack scenes and gated hold/readback recipes. W399 session fields and gate settings are in the shared procedure; K11 supplies the marked/no-hook kit. Apply R546 to every refused cargo action.

**Evidence to keep:** Each device/direction's labeled screens and content-hash/typed-cursor/resource comparisons,
reload timing, client/server logs and video of visual refresh under phase 2; gate/no-hook configuration separately.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J07 — Cargo authority and synchronized holds with two clients

**Phase/level/machine/accounts:** 4 V2, two real GPU clients A owner/B foreign owner or passenger; both roles.
**Gate:** O2 marked copy only; all production switches off.
**Preconditions:** J06 four-device fixtures, [W11CD C-V](W11CD-DESIGN.md), pinned L1/L2 holds and endpoint/seat probes.

1. [P-B] Attempt to operate A's bound device as a non-owner. → Server refuses; neither resource total nor binding changes.
2. [P-A] Start the pinned operation with L1 held. → PREPARING is visible; no premature endpoint move or spendable credit.
3. [P-B] Observe the held operation through an authorized observation route. → B's permitted view agrees with A/server;
   do not bypass an owner-only menu just to manufacture a screenshot.
4. [H] Submit the competing-device attempt. → One live cargo operation per rocket; no mixed-kind/opposite-direction
   chain, duplicate debit or incompatible cursor admission.
5. [H] Release the exact L1 acknowledgement. → Server applies only that operation; TRANSFERRING/pending reflects
   applied-but-locked resources, not a claim that nothing moved.
6. [H] Hold then release the mapped L2 boundary in separate observations. → Both permitted client views follow
   the server and final unlock occurs only after exact acknowledgement.
7. [P-B] Board the bound rocket in a separate idle run. → Cargo request refuses with no resource movement while actual
   or assigned passengers remain; dismount alone is not proof the assignment cleared.
8. [P-A] Disconnect after a separately captured L1. → Recovery continues from durable authority, not client prediction.
9. [P-A] Reconnect. → Binding/status and exactly-once endpoints agree; compare the same world after restart if needed.
10. [H] Repeat the sequence for each of the four devices and reversed ownership. → No direction or typed adapter is
    silently covered by another device's result.

**Accepted preparation/action steps (W408):**

**Needs package K12**: four-kind L1/L2, competitor and seat probes; **needs package K11**: marked gate/owner roles. The common R546 receipt and authoritative readback rule applies to all refusals; a forced-open private menu is never a synchronized observer view.

**Evidence to keep:** Both videos/client logs, server boundary/owner/seat/binding records and exact item/NBT or Fluid
totals, competitor refusals and per-kind case map under phase 4; phase-3 recovery references if used.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J08 — Cargo reference benchmark and per-device cycle time

**Phase/level/machine/accounts:** 5 REF, exclusive normal-user node; harness and verified reference-load players.
**Gate:** Gated disposable cargo load only; all production switches off.
**Preconditions:** P2 reference workload and 2,048-block rocket with 128 payloads/1 MiB aggregate-NBT boundary;
[W11CD C-B and benchmark paragraph](W11CD-DESIGN.md). Pin actual admitted maxima separately from oversize fixtures.

1. [H] Run the largest **admitted** typed cargo fixture. → Report effective native encoded size, framing/depth/node
   limits and maximum admitted Fluid/item payload, not a presumed 8 KiB Fluid or 4 KiB item entitlement.
2. [H] Run each oversized boundary fixture. → Refusal before mutation; preserve endpoint bytes/totals and reason.
3. [H] Run 64 synthetic competing records with one maximum-size rocket among small mixed fuel/cargo requests. → At most
   one whole-snapshot reconstruction/tick, 16 MiB and 16,384 visits; at most 64 commits and 8 reconciliations/tick;
   detached memory at most 16 MiB. Synthetic results are a separate cohort.
4. [H] Measure capture, apply recomputation, off-thread readback hash and visual republication. → Keep each cost and
   the complete tick; do not drop visual-refresh cost or subtract the observer.
5. [H] Run each of the four real devices under the docs/17 native reference load. → Measure request→acknowledged
   release cycle time and sustained cadence per device; 1,000 mB or one stack per 20 ticks is a cap, not guaranteed throughput.
6. [H] Compare raw reference tick/memory/GC/forced-load results with the shared budgets. → No microbenchmark-only pass;
   failure requires a new designed cadence/preparation change, not a larger budget.

**Accepted preparation/action steps (W408):**

(C10/C36) Standard comparisons use matched ten-minute samples after two warm-up minutes and two baselines; keep longer/special cost cohorts intact. No unbounded-wait maximum is invented; budget findings come only from REF. **Needs package K12**: largest-admitted/oversize/mixed/native device scenes and raw cycle driver; **needs package K15**: REF observation. O11 supplies node access.

**Evidence to keep:** Boundary encodings/admission/refusal totals, synthetic versus native sample files, per-device
cycle table, capture/apply/hash/publication samples and host/tick/RSS/GC records under phase 5; shared soak I10 separate.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J09 — Shared bay/cargo work and capture contention

**Phase/level/machine/accounts:** 5 REF, normal-user reference server; harness with pinned load actors.
**Gate:** Gated disposable mixed fixture, all production admission off.
**Preconditions:** J05/J08 workload, R-043/R438 and [C6 section 9.2 rows 6 and 14](C6-SWITCH-REPORT.md).
Pin real concurrent bay D4/D5, four-kind cargo preparation, F1, W98 and sealed-disassembly capture workloads.

1. [H] Run the cargo-only baseline. → Keep aggregate work/visits/capture grants and per-request wait intervals.
2. [H] Run the bay-only baseline. → D4 snapshots and D5 changed-copy turns have separate measured costs.
3. [H] Run bay recovery and all cargo kinds concurrently. → One server-wide 16 MiB/16,384-visit work budget applies;
   each D5 copy takes that tick's whole budget, with no fairness guarantee invented.
4. [H] Add competing F1/W98/sealed-disassembly captures. → The existing shared capture quota is observed, not
   enlarged or duplicated per consumer; D6's total queue delay and grants are recorded separately.
5. [H] Compare before/during/after fairness and waits. → Cargo can delay D4/D5 and bay can delay cargo by whole ticks;
   report starvation/unbounded holds honestly and keep complete tick budgets unchanged.

**Accepted preparation/action steps (W408):**

(C10/C36) Run each baseline twice back to back under the same world/cell/host and sample plan (two discarded warm-up minutes, ten measured minutes). Baseline-spread differences trigger Root review only. Raw waits have no invented latency ceiling; REF alone supplies budget findings. **Needs package K12**: competing workload/queue driver; **needs package K15**: grant/raw-tick observation; owner O11 supplies node access.

**Evidence to keep:** Shared per-tick grant/work/visit trace, consumer IDs, D4/D5/D6/cargo wait timelines, baseline
comparisons and whole-tick/reference host samples under phase 5, cross-referenced by J05 waits 6/14.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J10 — Cold destination, readiness and first-use checkpoint cost

**Phase/level/machine/accounts:** 5 REF, reference node, harness; equivalent players qualified before use.
**Gate:** Gated governed-flight copies, production admission off.
**Preconditions:** P2 reference load/rockets; [W206](W206.md), [W207](W207.md), R-035/R438.
Pin a reference-compatible driver; the Windows diagnostic wrapper is not assumed runnable on Debian.
Use contained one-column, border, 4×4 maximum-square and 1×16 maximum-strip checked margins. New-terrain and
generated-but-cold controls are distinct; generated controls are saved by a separate preparatory process.

1. [H] Qualify each measured destination as cold without calling a selector/heightmap to prewarm it. → Arithmetic-only
   candidate columns, no support/START/forced/player tickets, and no terrain records for the new-terrain case.
2. [H] Issue the installed product flight request. → Record synchronous result/API and whole-tick cost; loading is
   not acceptance, F1, reservation or automatic launch. Pre-F1 source/fuel and journal/ARMED absence remain valid.
3. [H] Wait for observed READY. → Record ticks and wall time, actual selected columns, source identity and original
   deadline; preparation and warm-up together cannot renew beyond the captured 600-tick-or-lower deadline.
4. [H] Issue a fresh request. → All authority/terrain/occupancy/reservation checks repeat before BOUND/F1.
5. [H] Follow the ordinary flight through F2, handover, landing witness and F8. → Same resource debit and snapshot;
   owned tickets released independently of whether chunks have evicted.
6. [H] Repeat steps 1–5 for all four geometries in both terrain cohorts. → No maximum-margin case omitted or warmed by an earlier run.
7. [H] Run capacity/replacement/lifecycle cases. → Two allocations/player, eight global; ninth refused without owned
   tickets; replacement/disconnect/removal/expiry release only owned tickets, no automatic launch.
8. [H] Stop with an unbound warm-up and reopen the same world. → Observe 100 restart ticks with no saved preparation,
   warm-up or destination support ticket; retain the original source and persisted ledger identity.
9. [H] Send a fresh request for that same logical rocket after restart. → Loading→READY→fresh launch→F2/F8 completes;
   no repaired/replaced source or artificial destination ticket.
10. [H] Compare all-tick, active-work and checkpoint-bearing cohorts separately. → Nearest-rank percentiles keep
    cold generation, first-use/relaunch and callbacks; no observer subtraction. W207's 118.937–164.162 ms checkpoint
    P99 and 131.482 ms restart relaunch are historical failures, not C6/reference results or exemptions.

**Accepted preparation/action steps (W408):**

(C10/C36) Keep matched-copy/cell/host comparisons and twice-run baselines; the short-window plan is two discarded warm-up minutes/ten measured minutes without dropping cold/first-use cohorts. No maximum for unbounded waits exists. **Needs package K12**: Debian cold/queue/lifecycle driver; **needs package K15**: complete raw-tick/REF observations. Owner O11 supplies node access; Windows figures remain history.

**Evidence to keep:** Cold qualification, geometry/column/ticket manifests, full native events/ticks, request/READY/F2/F8
times, checkpoint cohorts/JFR/RSS/GC and two restart observations under phase 5; reference timing is separate from old Windows runs.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J11 — First-event progression flush on the reference node

**Phase/level/machine/accounts:** 5 REF, normal-user reference server; harness, eligible player/equivalent fixture.
**Gate:** Production switches off; no first-event headless cut property with connected real clients.
**Preconditions:** P2 reference workload with a fresh actual qualifying Moon landing/station first-warp event;
[R245](OWNER-DECISIONS-20261101.md) and [KI-V180-SAVE-01](../../../releases/v1.8.0/KNOWN-ISSUES.md).
The real `advancedrocketrycommunity_classic_progression.dat` checked receipt barrier must execute.

1. [H] Record the empty relevant first-event state and baseline reference load. → No previous winner/receipt can bypass the first write.
2. [H/P] Complete the qualifying event by its product route. → Same elected event/participants and durability order;
   no `/advancement grant` or injected pre-delivered receipt as a substitute for the measured event.
3. [H] Measure the complete first-event tick and checked flush/force interval. → Keep storage/OS/GC/raw tick cost;
   the development sample 139.804 ms is only a comparison, never the expected reference result.
4. [H] Compare the tick against the unchanged reference budget. → If it exceeds the budget, report FAIL and require
   a designed, independently reviewed asynchronous progression-saving task before release under R245; otherwise
   record the measured result and the v1.9 follow-up disposition. Do not remove the receipt barrier.
5. [H] Reopen the saved world twice. → Same event/receipt/award is stable and no second first-event debit or grant is manufactured.

**Accepted preparation/action steps (W408):**

(C37) Measure a fresh first-event tick on REF; over-budget evidence requires Root's separately designed and independently reviewed persistence task under R245 before release. Disclosing a Windows limitation does not replace this run or waive its budget. **Needs package K12**: actual-event workload/driver; **needs package K15**: force/flush/raw-tick observation; O11 supplies access.

**Evidence to keep:** Reference host/storage identity, raw first-event and flush/force timestamps, native tick/GC samples,
pre/post progression/event files and restart comparisons under phase 5, linked to the R245 disposition.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J12 — Owner confirmation of the code-drawn sky

**Phase/level/machine/accounts:** 2 V1, main real GPU A, reduced second-GPU pass; owner judges.
**Gate:** Ordinary P2 sky/station copies; all admission off.
**Preconditions:** [D4](OWNER-DECISIONS-20261031.md), [W43](W43.md), known Earth orbit and Cygnus X-1
SINGULARITY station contexts. Pin matching client celestial snapshots and station positions/orbit distances.

1. [P] Observe the planetary body and Earth disc from the pinned orbit. → Code geometry is the published direction,
   not claimed identical to the excluded legacy textures; save wide and close views.
2. [P] Observe the Cygnus X-1 sky. → Opaque black disc, bright photon rim and tilted accretion ring are correctly ordered,
   follow station attitude and distance, and differ from a generic blue-grey body disc.
3. [P] Compare `sky.stationOverride` on/off. → Off uses fallback sky and clears the override context effects.
4. [P] Compare All/Decreased/Minimal particles with `effects.advancedVisuals` on/off. → W43's motes are 2/1/0 per
   tick by setting, at most 8 attempts between ticks/128 tracked; off produces no accent motes and moves no actor.
5. [P] Change context/Level, disconnect and reload resources in separate views. → Old motes/mesh context clears;
   unknown/missing context uses fallback, not a stale black hole.
6. [P] Record the owner's judgement on colours, sizes and mote density. → Keep/change is specific to C6/GPU/view;
   tuning requests are findings and a changed JAR requires a new candidate, not silent screenshot replacement.

**Accepted preparation/action steps (W408):**

(C38) Enumerate sound-producing sky contexts from C6's delivered K13 producer map, not W16's older list; use an audio-enabled profile. **Needs package K13**: that live profile, **needs package K16**: orbit/control scene. Owners O9/O12 arrange second GPU and sitting time; audio is not proved by screenshots.

**Evidence to keep:** Labeled sky/black-hole/Earth-disc videos and screens, context/switch/reload records, both GPU
identities and owner judgement under phase 2; E06 supplies its full independent two-GPU sky matrix.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J13 — Fixed-midnight Moon on the published candidate

**Phase/level/machine/accounts:** 2 V1, real GPU A; owner confirms.
**Gate:** Ordinary P2 Moon travel fixture; production admission off.
**Preconditions:** [D12](OWNER-DECISIONS-20261031.md), [KI-V180-MOON-01](../../../releases/v1.8.0/KNOWN-ISSUES.md).
Moon dimension-type `fixed_time=18000`; record game time, local day time and sky configuration separately.

1. [P] Enter the Moon normally. → Midnight presentation is visible with the candidate's code sky/Earth disc.
2. [H] Observe the world while total game ticks advance. → Sky remains fixed midnight; world simulation, machines
   and timers are not frozen by `fixed_time`.
3. [H] Compare observations across chosen ordinary day-time values. → No new daylight cycle is required; record
   exact times instead of reporting fixed midnight as the older “Moon daytime” defect.
4. [P] Leave and re-enter the Moon. → Same published appearance and no stale prior-Level sky.
5. [P] Confirm the appearance against D12. → Owner judgement is recorded without reopening sleep/respawn or heightmap landing rules.

**Accepted preparation/action steps (W408):**

(C38) Keep C6's producer map and audio-enabled profile, not old W16 paths; fixed-midnight D12 remains unchanged. **Needs package K13**: live audio profile; **needs package K16**: actual Moon/time scene. O9/O12 supply hardware/time.

**Evidence to keep:** Time-stamped before/after/re-entry Moon screens/video, runtime type/time observations, candidate/GPU
identity and owner's C6 confirmation under phase 2.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J14 — HUD layout and worn component rendering

**Phase/level/machine/accounts:** 2 V1 A; 4 V2 A/B on real GPUs for observer mounts/independent layouts; both.
**Gate:** Ordinary P2 equipment/rocket/station fixture, production switches off.
**Preconditions:** [W16 final visual-evidence section](W16.md). Pin authorized suit states for all environment,
oxygen, hydrogen, suit and ridden-rocket panels; worn jetpack/tank/visor modules and armor/elytra combinations.

1. [H] Present every status and gauge extreme from server-authoritative equipment/rocket state. → All five panels
   show the actual synchronized values; no snapshot/authorization means no invented suit data.
2. [P] Capture each panel in en_us/zh_cn at GUI scales **1–6**, resizing the window. → Text/gauges remain readable;
   impossible layouts are documented, not misreported as all panels fitting.
3. [H] Exercise each X/Y anchor with offsets through ±4,096. → Clamping, displacement and compact placement are
   stable and do not overlap critical controls where a valid layout exists.
4. [H] Change client HUD configuration in place. → Live layout change is visible; record the config-change log,
   separately recording atomic-replacement behavior rather than assuming a file watcher event.
5. [P] Press F3+T. → Reload retains valid layout, textures and current authoritative values.
6. [P] Toggle F1, then F3 in separate observations. → HUD hiding/debug behavior follows the candidate; no lingering panel.
7. [P] Ride the rocket through its states. → Rocket fuel/altitude/state readout follows the ridden server entity.
8. [P] View worn jetpack/tank/visor from all sides with armor, sneaking and elytra. → Mounts follow body/head without
   missing textures or unacceptable intersection; actual fit remains an owner judgement.
9. [P-B] Observe A wearing the same modules. → B sees tracked-player components, not A's private client HUD layout.
10. [P] Set different HUD layouts on A and B. → Each local layout stays independent across equipment changes/reload.

**Accepted preparation/action steps (W408):**

(C38; J21 A-F10a) On the Moon wearing a full suit, open F3: life-support HUD hides while debug is open and returns afterward. Retain the explicit 1-6 investigation with effective-scale readback, not unavailable scale claims. **Needs package K13**: current producer/profile inventory qualification; **needs package K16**: HUD extremes and worn-module scene; O9/O12 supply hardware/time.

**Evidence to keep:** Complete status/extreme/scale/anchor index, live-config/reload logs, all-side component screens,
ridden readout video and both-client independent-layout views under phases 2/4.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J15 — C6 audio seams, ranges and caps

**Phase/level/machine/accounts:** 2 V1, real GPU/audio output A; owner listens.
**Gate:** Ordinary P2 many-source fixture; production admission off.
**Preconditions:** [W16](W16.md), [D5 of 2026-11-04](OWNER-DECISIONS-20261104.md).
The ten approved NEW sounds are air_hiss_loop, basic_laser_gun, combustion_rocket, electric_shock_small,
gravity_controller, laser_drill, lathe, machine_large, railgun_bang and rolling_machine. Prior audio approval
is not C6 playback evidence. Pin source coordinates, audio settings and actual producer/preview paths.

1. [P] Listen to each of the ten candidate sounds through its actual trigger where available. → Keep the D5 sound
   design; disclose preview-only/unbound sources (notably historical machine_large), rather than claiming a producer.
   C6's preview command is `/playsound advancedrocketrycommunity:<id> master <A>` (W402 section 5, R546).
2. [P] Listen through several complete cycles of each loop. → No unintended seam/click/stutter on this audio device.
3. [P] Approach an inactive loop source from beyond 32 to within 30 blocks. → Loop begins within the 30-block start range.
4. [P] Walk away past 32 blocks. → The loop stops beyond 32; the 30–32 band is hysteresis, not repeated start/stop.
5. [H] Activate more than 32 loop sources. → At most 32 simultaneous loops, nearest first with deterministic ties;
   discovery every 10 ticks scans at most 2,048 block entities and 64 rockets, not an unbounded world search.
6. [P] Trigger the one-shots in the pinned stress fixture. → Railgun range 32, shock/laser 16; simultaneous caps
   8/1/4 and 40-tick cut-off remain; no extra arrival bang is inferred from a launch sound.
7. [P] Reload resources while loops play. → No leaked/duplicated loop or broken playback after F3+T.
8. [P] Pause and resume the appropriate client session. → Audio lifecycle is recorded for integrated versus
   dedicated behavior; do not claim a dedicated server paused with its client.
9. [P] Log out and reconnect. → Old sources stop and no orphan loop survives; new valid sources can play.

**Accepted preparation/action steps (W408):**

(C38; W402 section 5, R546) Use the C6 sound-events/producer tables: eleven events, including `ui_select` from machine casing. `lathe` has lathe/precision-laser-etcher producers, `rolling_machine` has rolling-machine/chemical-reactor producers, and the laser gun has its item; `machine_large` alone remains preview-only. Preview in namespace `advancedrocketrycommunity`, never `_v180`. **Needs package K13**: actual audio-enabled profile/producer placements; **needs package K16**: integrated pause/control round. O9/O12 supply hardware/time.

**Evidence to keep:** Audio-bearing recording, per-sound trigger/preview table, range coordinates and distances,
cap/lifecycle logs, C6 JAR and audio hardware/settings under phase 2. Owner records any C6 regression separately from D5 approval.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J16 — Drill translation, action banner and disabled jackhammer

**Phase/level/machine/accounts:** 2 V1 A; 4 V2 A owner/B non-owner/observer; both.
**Gate:** Ordinary P2 endgame/machine/hand-tool fixture; production admission off.
**Preconditions:** [W87 remaining V1/V2](W87.md), drill/railgun and hard mining target such as obsidian.
Server disables classic equipment; client keeps its default, so client configuration cannot explain the refusal.

1. [P] Open the drill on each pinned body in both languages. → Body names use celestial translations; unknown body
   falls back to its ID, without falsely treating every fallback as a missing translation.
2. [H] Send a real refusal/progress action-bar message while a mod screen is open. → The latest unchanged message
   appears in the bottom banner for six seconds, readable without covering a critical control.
3. [P] Close/change the screen. → Banner clears; logout/expiry also clears it, without resending/canceling the server message.
4. [P] Start mining obsidian with the jackhammer. → Immediate server-disabled feedback, no unauthorized progress/drop/durability use;
   repeated notices are limited to one per player per 20 player ticks, not only after impossible completion.
5. [P-B] Attempt an owner-only interaction while its screen is legitimately open. → B sees the refusal banner;
   A's authoritative device status/resources do not change to B's local refusal.
6. [P-A] Launch the railgun while B watches. → Accepted launch/arrival effects reach observers; unknown effect IDs
   do not create arbitrary effects. Compare advanced visuals on/off without hiding essential feedback.
7. [P] Repeat screen/reason views at scales 2/3 and with W402's hashed K13 long-text resource pack (A's delivered-input table). → Long reason suffix and
   full hover text are retained; pack identity is recorded rather than silently substituting a default font.

**Accepted preparation/action steps (W408):**

(C38; J21 A-F5/A-F7/B-2) At scale 3 in both languages, open railgun, black-hole generator, drill and target with longest reason lines: fit with full hover. Have a non-owner legitimately click railgun Launch with screen open: visible `UNAUTHORIZED` inside; on elevator Ride, keep the screen open and read "Ride starting". With server `equipment.classicEnabled=false`/client default, mine obsidian for 2.5 seconds: disabled notice at START, at most once/20 ticks, no progress/drop/durability debit. C6 producer inventory/audio-enabled profile **needs package K13** live qualification; regenerated scene/actions **needs package K16**. R546 refusal proof applies; N4's client-tooltip mismatch is accepted design, not a demanded sync change.

**Evidence to keep:** Drill translations, banner appearance/expiry/clear video, disabled START timing and resource/tool
comparison, both-client refusal/railgun video and logs under phases 2/4.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J17 — Fourteen real-client world-first advancement variants

**Phase/level/machine/accounts:** 3 DS restart setup; 4 V2, eligible participant A and nonparticipant B; both.
**Gate:** Production admission off. **No `arce.releaseTest.firstEventCut` property or headless halt seam** in real-client runs.
**Preconditions:** [W44I “Not done”](W44I.md), [W44 design sections 3 and 6](W44-DESIGN.md).
Debugger breakpoints, owned PIDs and exact C6 source lines **needs package K12**. Use controlled external stop X,
not headless H: gate relaxation is forbidden. Actual criterion oracle is vanilla `AdvancementProgress` plus
saved player advancement JSON for `classic/one_small_step` or `classic/first_station_warp`; a receipt alone is insufficient.
Moon C-M means A aboard the unique completing rocket/B watching without a seat. Station C-W means A eligible
and present at winning station/B outside or without VISIT. T0 is a retained real Moon event E/receipt R(E),
winner legitimately disassembled and `transfers=[]`; seed only the specified retained state, never a new landing claim.
W44's deferred queue examines at most 16 entries per END tick, rechecks one no more than once per 20 ticks,
and after readiness allows a fair pass within `20 + ceil(2P / 16)` ticks for P connected players (21 ticks for
these two clients). Observe eligibility/readiness first; no claim of that latency during a permanent hold.

1. [H] Create one complete disposable world per variant below. → Pin immutable event, participants, receipt/fingerprint,
   source state and before-file hashes; A initially lacks the criterion where specified and B is not a participant.
2. [H/P] Execute that variant's actual event/login/deferral action. → No `/advancement grant`, injected participant
   list or zero-recipient dispatch substitutes for player eligibility/award evidence.
3. [H] Stop only the owned process at its named X boundary. → Keep thread, event UUID, sequence/progress, raw roots
   and whether move/force/confirmation occurred. Do not queue `save-all` on a paused server thread.
4. [H] Restart the same whole world for observation 1 with the specified held/released boundary. → Follow the table;
   inspect actual criterion and B's negative result, not merely trigger invocation.
5. [H] Save the recovered player advancement state through the normal routine. → Save completion and JSON hash are recorded;
   unrelated criteria are retained.
6. [H] Restart the same recovered world for observation 2. → Same E/R(E), saved criterion and economic state;
   no new winner, event, duplicate toast or new qualifying flight/warp is manufactured.
7. [H] Repeat steps 1–6 for all fourteen variants. → Each has its own two-restart comparison, actual A award and B negative.

| Variant | X boundary / distinct setup | Required observation |
|---|---|---|
| FM10a | W baseline before acknowledgement; T0 ELECTED, A JSON absent. | Restart 1 holds baseline through A login, then release awards A in that session without reconnect/new flight; save/ack with zero records. |
| FM10b | Same, T0 ELECTED, A JSON stale. | Same repair; unrelated criteria preserved. |
| FM10c | Same, T0 DELIVERED, A JSON absent. | Confirm retained source despite zero records; deferred login awards A with same receipt. |
| FM10d | Same, T0 DELIVERED, A JSON stale. | Same actual criterion repair, not just unchanged outbox. |
| FX-Ma | S after actual Moon grant, before DELIVERED; debugger invokes existing player-save in paused S frame and returns. | Saved A complete on both starts, no duplicate toast/winner, B negative. |
| FX-Mb | Same Moon grant boundary, external stop **before** player save. | Restart 1 immediate/deferred login repairs A; saved completion survives restart 2. |
| FX-Wa | S after actual station grant, before root-only DELIVERED; player-save invocation completed. | Saved A complete, same E/FE/orbit on both starts; B negative. |
| FX-Wb | Same station grant boundary, stop before player save. | First restart grants lagging A, second retains it; no second warp/winner. |
| FX-Rm | S dead-login deferral; retained DELIVERED Moon E/R(E). | No award while dead; respawn in the same session grants within the existing queue bound. |
| FX-Rw | Same for station event. | Same dead/respawn rule and stable second start. |
| FX-Sm | S spectator-login deferral; retained DELIVERED Moon event. | Spectator negative; survival transition grants in the same session within the queue bound. |
| FX-Sw | Same for station event. | Same eligibility transition and stable second start. |
| FX-Dm | W baseline held through stale-criterion A login then disconnect; stop before release. | Queue entry removed/no offline award/file mutation; restart 1 reconnect+baseline confirms award, restart 2 stable. |
| FX-Dw | S after spectator A disconnect and removal of its deferred entry, before eligibility change. | No offline grant; restart 1 survival reconnect grants, restart 2 stable. |

**Accepted preparation/action steps (W408):**

(C39) First-event H cuts remain forbidden with connected real players; the bay connected-player exception does not apply. X uses external process stops only. **Needs package K12**: fourteen pinned debugger/save/deferral recipes, owned process IDs and actual award probes. No receipt alone or headless result proves a player criterion.

**Evidence to keep:** Debugger plan/transcripts, exact pause/stop/PID and save-return boundaries, two client logs/videos,
actual progress/JSON before and after both starts, E/R(E) and participant/nonparticipant checks under phases 3/4.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J18 — Off-world sleeping, native saved points and natural-type effects

**Phase/level/machine/accounts:** 3 DS restarts; 2 V1 compass/clock; 4 V2 sleep quorum/observers A/B; both.
**Gate:** Ordinary P2 fresh and representative prior-world copies; admission off.
**Preconditions:** [W37B final D1 table](W37B.md) and
[D1 outcome freeze](../../v1.8.0-c18a-sleep/D1-OUTCOME-FREEZE-33.md), including its B1/R1 conditions.
Six hosts are Moon, Space, Mars, Venus, Tau Ceti f/g. Pin valid supplied beds (ambient breathable f is allowed),
station VISIT, prior native-home tuple and absence, forced/off-world points, pending R-036 backups and invalid-home cases.

1. [H] Read back packaged/runtime holders on fresh and prior-world copies (V02). → Exactly the six correct Level/type
   keys, `natural=true`, all other fields unchanged; Moon/Space retain fixed time 18000.
2. [H] Apply each pinned override/missing/reused-type datapack and reload (V03). → Compare to the freeze's exact
   holder/pack policy; archive diagnostics, never normalize the pack silently.
3. [P] Sleep in each breathable, permitted bed with prior home, then with native absence (V04–V06). → Ordinary sleep
   preserves dimension, nullable position, **raw angle bits** and forced flag exactly.
4. [P] Attempt beds in daytime, near monsters, vacuum/PENDING/incomplete air and without station VISIT as separate cases.
   → Appropriate refusal; even late native refusals retain the original tuple and pending backup.
5. [H] Exercise copied prior-world forced/off-world/invalid-home and pending-backup cases (V07). → Existing R-036
   validation/fallback applies; no ordinary bed replaces the prior saved point or invents a planet point.
6. [H] Save and reopen each V02, V04–V07 and V13 fixture twice. → Disk tuples/backup and actual post-restart behavior
   match the freeze; importing a fresh world between starts is not accepted.
7. [P] With both clients, exercise V08 on one host at `playersSleepingPercentage` 100, 50 and 101, including
   spectator/disconnect/manual-wake controls. → Native active/sleep/deep counts and 100-tick deep-sleep threshold;
   insufficient quorum does not complete and no stale durable counter remains.
8. [P] Inspect ordinary compass and clock on all six hosts (V11). → Compass targets local shared spawn with moved-spawn/
   border control; lodestone/recovery controls remain distinct. Moon/Space clocks use fixed 18000, other four local cycles.
9. [H] Run V12 on an already-loaded controlled portal with `doMobSpawning` enabled/disabled, peaceful/nonpeaceful
   and the existing Forge veto. → Conditional STRUCTURE piglin attempt characterized, native floor/random checks
   retained, no changed routing/actor policy or invented guaranteed spawn.
10. [H/P] Run V06's finite unrelated/nested/null/admin/restore and indeterminate controls under the qualified B1/R1
    recipe. → Ordinary bed preservation never becomes a blanket spawn-setter veto; keep native cancellations,
    newer tuples, exact nullable fields and failure receipts. Missing callback qualification remains NOT_COVERED.
11. [P] Run V13 immediate save, leave-bed, replacement, logout and orderly-stop cases with two real clients. → Each
    ordinary admitted bed keeps the native tuple; admin/unrelated setters still work and no stale operation restores over them.
12. [P] Run V09 with two Space stations/two distinct surface hosts, off-world-only then simultaneous Overworld completion.
    → One Space quorum, separate surface quorums; record game/day/fixed time, data owner and weather before/after;
    no ARCE forwarding of off-world sleep into an Earth time/weather write.
13. [P] Run V10 on one fixed and one cyclic host with `doDaylightCycle` false/true and rain/weather-cycle controls.
    → Native local wake is independent of the daylight write; fixed Moon/Space sky and primary time/weather ownership remain.
14. [H] Compare disk/player/world state after both restarts. → No tuple/backup loss. Keep the accepted exceptional
    same-window pause/crash/third-party-save residuals from W37B visible; do not claim unconditional crash protection.

**Accepted preparation/action steps (W408):**

(C40) Saving a planet point with `allowPlanetRespawn` is **not implemented in v1.8** (W37B): it is neither a test row nor a pass. Ordinary sleep follows the freeze matrix exactly. **Needs package K14**: qualified V02-V13/B1/R1 scene, packs/quorum/portal recipes and tuple readback.

**Evidence to keep:** Per-host V02–V13 subcase map, freeze/B1/R1 qualification, full tuple/backup dumps and hashes,
two restart state sheets, both-client quorum/time/weather/portal logs and compass/clock screens under phases 2/3/4.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J19 — Observatory, data processor and ground surveys

**Phase/level/machine/accounts:** 2 V1 A; 4 V2 A owner/B competitor; both on real GPUs.
**Gate:** Ordinary P2 research/many-machine/station fixture; production admission off.
**Preconditions:** [W21A section 7 and runtime](W21A-DESIGN.md), formed observatory and astrobody data processor
in four rotations using its tracked patterns. Supply at least 20,000 FE/survey and 4,000 FE/analysis; expected
analysis credit 120 points. Record starting balances, job/instance and device UUIDs, lens and power-plug positions.

1. [P] Open each formed controller in both languages/scales 2/3/4. → Status/phase/progress/FE/owner/selection and
   missing-cell diagnostic are readable and match server `ContainerData`, not client-supplied yield.
2. [P-A] Press START on the observatory. → ACTIVE job/PENDING instance; WAITING_SAVE before first paid advance,
   then progress under valid sky/night/weather/FE. Normal coalesced barrier is about 100 ticks, not a hard IO deadline.
3. [H] Obstruct the lens, create day/rain, remove power and unload a required cell in separate runs. → NO_SKY,
   DAYTIME, WEATHER, NO_POWER or UNLOADED pause without loading a chunk. Dry-biome precipitation rules remain distinct.
4. [P-A] Complete the valid survey. → READY job and AVAILABLE instance; exact 20,000 FE control and one result.
5. [P-A] Select that READY/WAITING job at the processor with PREVIOUS/NEXT and press CLAIM. → RUNNING claim,
   WAITING_SAVE before payment; list is server-held, at most 16 owner jobs, not a client-injected index.
6. [P-B] Attempt foreign START/CANCEL/CLAIM/REBIND in separate attempts. → Owner checks refuse and balances/progress remain unchanged.
7. [P-A] Try to claim the same job from a second processor. → ALREADY_CLAIMED/refusal, no second credit.
8. [P-A] Complete the analysis. → Exact 4,000 FE and 120 research points once; replay cannot credit again.
9. [P-A] Break/rebind/cancel in separate pinned mid-job cases. → SOURCE_MISSING pauses, rebind preserves progress;
   cancel removes PENDING instance, no refund/award; repeated actions follow server state.
10. [P] Inspect the station case, then its changed-system pause. → VISIT/orbit-derived system is authoritative;
    station bypasses sky/day/weather checks and SYSTEM_CHANGED pauses after inter-system relocation.
11. [H] Save/reopen mid-survey and mid-analysis using the phase-3 fixtures. → Progress/balance are coherent and final
    completion credits once; V1/V2 evidence supplements, not replaces, native persistence coverage.

**Accepted preparation/action steps (W408):**

(C41) Read UI strings and section-7 effective values from C6 before using the numeric controls. Produce owner, competitor, rebind, full-account and pause variants only by ordinary player actions on complete copies; never edit balances or force-load a chunk. **Needs package K14**: real values, scenes and variant recipes.

**Evidence to keep:** Both menus/rotations/languages and live-job video, FE/points/job/instance before/after, owner
refusals, pause/rebind/restart records and both clients' logs under phases 2/4 with phase-3 references.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J20 — Moving rocket, Moon slow fall and high-seat console

**Phase/level/machine/accounts:** 2 V1 real GPU A, packaged server; scripted then hand-played movement.
**Gate:** Ordinary P2 rocket/Moon/named-pad fixture; production admission off.
**Preconditions:** [W189](W189.md), [W311](W311.md). Pin a tall rocket with top seat nine blocks above origin,
including origin 12–15 blocks into an entity section; survival/creative variants, safe Moon slow-fall and unloaded named pad.

1. [P] Watch a real rocket during ASCENT and DESCENT while moving the camera/player. → Position tracking and exhaust
   trail stay aligned; record actual visible stutter. W189's every-two-tick tracker correction is not visual sign-off.
2. [P] Compare parked/COUNTDOWN/TRANSIT states. → No invented velocity or unnecessary moving-state cadence in inactive states.
3. [P] Begin a Moon slow fall from rest using the pinned effect. → Positive low-gravity descent does not stick at
   vanilla's dead zone; rising/ordinary/liquid/disabled-gravity controls retain their behavior.
4. [P] Board the top seat and close the console. → Rider remains in the actual high seat, not a fixed bottom offset.
5. [P] Right-click from the seat while facing several directions. → Console reopens in survival and creative,
   including the section-margin fallback; do not use F3's Targeted Entity line as rocket proof.
6. [P] Request flight toward the unloaded named pad. → “Landing pad refused: …” gives the actual reason and stays
   until the next button action or plan change, rather than expiring after about three seconds.
7. [H] Compare logs and player-facing diagnostics. → Player sees translated reason; operator-only diagnostics retain
   bounded codes/positions, not an untranslated parameter map on the console.

**Accepted preparation/action steps (W408):**

(J21 A-F3/A-F10c/A-F10d, R542 N16) Watch a fuelled pad rocket from a ground viewpoint 20 blocks away through ASCENT: rocket/trail rise together without held-position jumps. Run `data get entity` on the assembled rocket: its name must not be `menu.advancedrocketrycommunity.rocket_flight`. Assemble the invalid/blocked-target variant: player feedback is translated with no `coordinate_space=absolute`. Explicitly build the high-seat rocket with origin 12-15 blocks into an entity section, reopen from several directions in survival and creative; do not just cite the precondition. **Needs package K14**: tall/slow-fall/named-pad scenes; **needs package K16**: regenerated tracking/recheck batches.

**Evidence to keep:** Real-player camera/rocket/trail video, slow-fall from-rest position/health record, top-seat
coordinates and survival/creative reopen views, named-pad refusal duration/actions and client/server logs under phase 2.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J21 — Earlier real-client findings repeated on C6

**Phase/level/machine/accounts:** 2 DS/V1 main GPU A; packaged restarts in phase 3 where required; scripted.
**Gate:** Ordinary copies except explicitly marked J fuel/cargo/bay follow-ups; all production switches off.
**Preconditions:** [current V1/V2 status](../../../status/CURRENT_VERSION.md), `J21-FINDING-MAP.md`, external historical
`D:/GitHub/ARCE-Task-Evidence/pre-v18-client-20261006/REPORT.md`
(SHA-256 `4bcee6e655da2a5cf50ab61bae324cb8c72398224dc6dd40bfbf205377b30aa3`),
`D:/GitHub/ARCE-Task-Evidence/v18-client-20261010/REPORT.md` section 4 and
`D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md` (rounds 1–8), with their plans/screenshots.
Use P2 representative prior-world/many-machine/rocket copies and the delivered finding→task→current expectation→batch
map for W280–W282, W284–W296 and W301. Regenerated batches **needs package K16**.
An old report's offline Forge 47.4.22 profiles are not campaign profiles.

1. [H] Inventory every historical finding before running. → No informational/known-limitation finding disappears
   because its task was merged; disposition and source screenshot accompany each current expected result.
2. [H] Repeat October 10 findings 1–5 separately. → Solar English per-mille/fit, immediate server-disabled
   jackhammer feedback, oxygen vent's actual 20 FE/t consumption, vacuum torch behavior and in-place versus atomic
   config reload each have measured/visible evidence; balance or watcher limitations are not silently retuned.
3. [H] Repeat the round-1 machine/menu/charging cases. → Numeric FE versus wait text, electrolyser slots/layout,
   all affected player-inventory areas, tank unrelated-item feedback and O1 canister exchange match current decisions.
4. [H] Repeat round-2 startup/low-gravity/air/seat/torch cases. → Preserve any intermittent start failure, no illicit
   flight kick, effective sealed-room analyzer reading, explicit retained versus cleared seat assignment, torch reload,
   assembler placement and current player-facing text. No retry is erased from the failure count.
5. [H] Repeat round-3 station/landing/pad/presentation cases. → Zero-g movement and return are valid, no unintended
   sea landing, named-pad reason/selection stable, seat at its actual block, casing permits intended placement, full
   GUI text/IDs; informational readings and damage observations retain their own dispositions.
6. [H] Repeat round-4 endgame configuration/layout/tooltip/audit cases. → COMMON switches use documented restart
   behavior; received-slot/energy-bar, stale label, raw-ID and owner-display cases match the candidate, without treating
   an accepted display limitation as economic correctness.
7. [H] Repeat rounds 5–6 regressions. → Electrolyser layout, experimental warning/art distinctions, nitrogen icon,
   drill settled-status explanation, outside-region zero-g return/kick/rocket avoidance, slow fall and grounded
   observations have independent evidence and current dispositions.
8. [H] Repeat rounds 7–8 regressions. → High-seat disconnect/assignment cleanup, console reopening, compressed text,
   tooltip IDs, named-pad refusal persistence, authoritative versus last-refusal text, rocket-box interaction trade-off
   and removed-station `/arcepad list` wording are all recorded, including remaining untested side-seat/multiplayer cases.
9. [H] Compare all findings with C6 code and current owner decisions. → Fixed items have fresh reproducer evidence;
   accepted limitations cite their scoped decision (for example fixed-midnight Moon); unresolved/missing cases are
   FAIL/NOT_COVERED, never a blanket pass on merged-task status.

**Accepted preparation/action steps (W408):**

(C42; R542) Use `J21-FINDING-MAP.md` for all 74 historical findings. Each has one base disposition: fixed (task/commit), known issue, matches design, or recheck on C6; a fixed item not yet seen in a client also owes its recheck. All fixed items still get fresh C6 evidence under original step 9. The 21 owed rechecks and N15-N19 below are actions, not closed results. K19's map is delivered; **needs package K16**: current-viewpoint/control batches, plus K14's physical scenes. Missing native receipt/control means NOT_COVERED with reason, not a blanket merged-task pass.

**Candidate recheck actions (C42/R542; all must retain individual results).**
`J21-FINDING-MAP.md` section 4 supplies these scene,
action and observation recipes. Original batch names are historical inputs,
not commands to replay: each **needs package K16** regeneration/qualification;
physical scene/readback dependencies **needs package K14** (K10 for H06's
generator). Run here or give an exact current-row evidence reference; do not
skip the other Fixed findings required by step 9.

| Finding | Action and expected observation | Original batch reference |
|---|---|---|
| A-F3 | A fuelled rocket on a pad, a second viewpoint 20 blocks away on the ground. Launch and record ASCENT. The rocket and its trail rise together, without the rocket holding still and jumping. | `r03-rocket-a.json`, `r03-rocket-b.json` |
| A-F4 | A black-hole generator generating at Cygnus X-1. View the ring from two blocks below and from five blocks above. It is visible from both. | A: `r02-screens-*`. H13: `m62_bh_views_b11` |
| A-F5 | Railgun, black-hole generator, laser drill and laser target screens with their longest reason lines, at GUI scale 3 in English and Chinese. Every line stays inside its box; hover shows the full text. | A: `r02-screens-a` to `-e`, `r03-pdrill*`. H13: `eg_bh`, `eg_drill_8`, `eg_rail_a`, `eg_target` (batches7) |
| A-F6 | Satellite terminal and builder at scale 3 in both languages, idle and after a build. No text overlaps a button or a slot. | H13: `b1_terminal`, `b1_builder` (batches7) |
| A-F7 | (1) A non-owner clicks Launch on the owner's railgun with its screen open. `UNAUTHORIZED` shows inside the screen. (2) Press Ride on an elevator. "Ride starting" is readable with the screen open. | `r03-perm-a.json` to `-c.json`, `r02-elevator-ride.json` |
| A-F10a | On the Moon in a full suit, press F3. The life-support HUD hides while the debug screen is open and returns after. | `r04-f3.json` |
| A-F10c | Run `data get entity` on an assembled rocket. Its name is not `menu.advancedrocketrycommunity.rocket_flight`. | None (a console command in r02) |
| A-F10d | Assemble a rocket whose structure is invalid (for example a blocked target). The player's message is translated and has no `coordinate_space=absolute`. | None |
| B-2 | Server with `equipment.classicEnabled=false`, client default. Hold the jackhammer on obsidian for 2.5 seconds. The "disabled" notice appears at the start, at most once per 20 ticks; no progress, drop or durability loss. | `r02-d03-jackhammer-disabled.json` |
| B-3 | A sealed room with one vent holding 40,000 FE and no supply. Time the room until it loses air. Record the rate (the report measured 20 FE per tick, about 100 seconds). | `b11-moon-powered.json` |
| B-5 | Edit the client configuration (a sky switch) once in place and once by rename. Record which edit the client applies without a restart. | None (edited by hand) |
| 2-5 | A lit torch in a sealed, supplied Moon room. Stop the server, start it, join. The torch is still lit; an outdoor torch is still put out. | `c70` to `c73`, `c80`, `c81` (batches2) |
| 2-7 | (1) Open the monitor bound to a rocket bound for the Moon: the destination is a name, not an ID. (2) Select the monitor with the linker: the text names the monitor. (3) Open the fluid loader: the status fits, with the full text on hover. | `c20`, `c22`, `c60`, `c61` (batches2); `c1_fluid_loader` (batches7) |
| 2-8 | Bind a monitor to a rocket, fly to the Moon and back, open the monitor. It still shows the rocket, not `Stale binding`. | `c50_return_flight`, `c53` (batches2) |
| 3-2 | Occupy the first Overworld landing point near a sea and return a second rocket from the station. It does not land on water. | `f04_fly_u20-to-earth`, `f05_landed_u21-earth`, `f11_visit_u22-earth-rocket-on-water` (batches3) |
| 3-5 | Hold a multiblock controller and right-click the top of a machine casing without sneaking. The controller is placed. | `e20b_place_controllers_sneaking` (batches3), without the sneak |
| 3-6 | Launch a data satellite's discovery mission from the terminal and claim it. The status line leaves `PENDING_DISCOVERY` at once. | `e02_terminal_press_s06-launch-mars`, `e02_terminal_press_s07-claim-mars` (batches3) |
| 4-4 | Bind an elevator pair and ride it. In the server log, the `space_elevator` `bind`, `ride_request` and `ride_commit` lines carry the device owner, not `owner=-`. | `m42_bind`, `m45_ride_l20-ride-down` (batches4) |
| 7-6c | An elevator terminal whose anchor is on another body than the station's orbit. Press Bind. Record the refusal text; today it is "An elevator endpoint rule fails [ELEVATOR_RULE]". | `eg_elevator` (batches7 and batches8) |
| 8-2 | Create a station, register a pad, delete the station, then run `/arcepad list <that station>`. The reply says the station is unknown or cannot be visited. | `b3_register_b`, `chat_b3-07-deleted-pad-list` (batches8) |
| 8-3 | Quote and confirm a warp, then cancel after three seconds, at GUI scales 2 and 3. The notice shows the cancellation, not "in 10 s", and stays inside the panel. | `warp8_countdown_b2-en2-cd`, `warp8_countdown_b2-en3-cd` (batches8) |

**Additional R542 N15-N19 actions (not accepted as untested).**

| Residual | Action and observation to record | Remaining input |
|---|---|---|
| N15 | Repeat logout/seat-release/rejoin from the side seat and with both real riders, not only the former top-seat one-rider case. Compare actual/assigned seats, safe position and health on rejoin; retain each role's result. | **Needs package K14** scene and **needs package K16** real-client batches; O5/O10 supply the two-account arrangement. |
| N16 | Execute J20's high-seat console reopening in survival/creative with rocket origin 12-15 blocks into its entity section, recording actual origin/seat and all approach directions. | **Needs package K14** tall scene and **needs package K16** input mapping. |
| N17 | Expand rocket-box interaction rechecks beyond one chest/three survival positions: exercise the K14-named inside, box-edge and outside controls and device kinds in survival/creative. Record which block/rocket interaction wins, compare unchanged protected state, and keep W321's accepted rectangular-box trade-off visible, not a demanded product change. | **Needs package K14** enumerated devices/positions; do not invent coordinates. |
| N18 | Open every C30 screen in Chinese and at effective GUI scale 3, not only warp/flight; repeat longest reasons/hover and record clipping or unavailable controls. | K13's screen inventory is delivered; **needs package K16** per-screen batches and **needs package K05** machine scene. |
| N19 | Separately recheck W303 elevator arrival/rejoin safety; W309 station creation from Space refusal; W300F second-loaded-copy seat cleanup; and Tau Ceti e route warning in navigation/warp. Record actual arrival/seat/health/authority or refusal receipt/state and warning text; no single result covers all four. | **Needs package K14** ordinary lifecycle scenes, **needs package K08** station/warp controls, **needs package K16** batches. |

**R542 residual dispositions to retain beside observations.** N1 is
KI-V180-CONNECT-01 (A18 counts every attempt); N2 is accepted current vent
balance, not a v1.8 retune; N3 is KI-V180-CONFIG-01; N4-N8 and N12 are
accepted design with the map's sources. N9 is W233's formed OBJ overlay design,
shown to the owner in phase-2 screenshots. N10/N11/N13 are Low KI-V180-UI-01:
generic ELEVATOR_RULE, non-ticking warp seconds and truncated builder status
with hover are not demands for new v1.8 screens. N14/N24 are KI-V180-USER-01's
documented slow-fall/rocket-box/config/experimental/rejoin limitations. N20 is
accepted any-entry-as-visit design, including operator teleport. N21/N22 accept
no-effect vanilla movement warnings, intended per-entry first-event WARN and
dedicated-server language keys; this does not suppress unrelated errors.
N23 is accepted action-bar-only loader feedback: actually read it in both
languages (J23/J25), not merely quote the design. N15-N19 remain rechecks
above, never automatically accepted or passed.

**Evidence to keep:** Finding-to-task/batch/disposition matrix, original-reference hashes, every C6 repro result and
failure attempt, screens/video, restart/state comparisons and native logs under phases 2/3. Preserve sealed old reports unchanged.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J22 — Full integrated candidate restart campaign

**Phase/level/machine/accounts:** 3 packaged DS/S2, harness on development host; no real clients for headless-only cuts.
**Gate:** Existing per-family release-test gates only; all production admission off, no widened hook permissions.
**Preconditions:** P1 C6 JAR/hash and source, P2 immutable fixtures, free disk and exclusive heavy-JVM lock.
Sources: [current S1 status](../../../status/CURRENT_VERSION.md), [owner brief item 5](OWNER-BRIEF-20261103.md),
R386/R438, [current integrated runner](../../../../scripts/run_v180_integrated_campaign.py) and C6 report.
Pin explicit historical G2, downgrade, X03 root-2 and C3 reader JARs/hashes/commits; never guess a historical artifact.

1. [H] Freeze the current runner's complete family plan on P1's candidate. → Expected inventory is the C6 report's
   78 families at this source; explain a changed selection before execution, not by editing a result after the fact.
2. [H] Dry-run the exact selected command below with `--dry-run` appended. → No unmet requirement; no native start.

   ```text
   python -B scripts/run_v180_integrated_campaign.py --candidate-commit <full-C6-SHA> --jar <P1-jar> --jar-sha256 <P1-hash> --template <P3-template> --evidence-root <new-J22-attempt> --java <Java17-executable> --old-jar <G2-jar> --old-source-commit <G2-SHA> --downgrade-old-jar <W176-jar> --downgrade-old-jar-sha256 <W176-hash> --x03-root-2-jar <X03-jar> --x03-root-2-jar-sha256 <X03-hash> --x03-root-2-commit <X03-SHA> --root2-jar <C3-jar> --root2-jar-sha256 <C3-hash>
   ```

3. [H] Run the same command without `--dry-run` and without `--only`. → Every family executes with the same candidate;
   filtered runs are partial evidence. Lock acquisition and cleanup follow the runner/worker rules, not a second JVM.
4. [H] Audit every family's two same-world restart observations and raw oracles. → Exact resources/roots/identity,
   save/cut boundaries, replay and refusal expectations hold; source worlds and historical evidence remain unchanged.
5. [H] Reconcile totals and failures with the full plan. → Keep expected downgrade failures, ordinary-route exclusions,
   unobservable E4 residuals, fixture failures and NOT_COVERED separately; no full-campaign PASS from a process exit alone.
6. [H] Refill candidate-specific results and the switch/fuel reports through Root's report task. → Actual C6 family
   evidence replaces PENDING only where supported, never copied from C1/C2/C3 or this unrun checklist.
7. [H] Run the separately selected C6 X01 S3 and X02 S5 campaigns under their own approved plans. → They remain
   separately accounted: this runner's 78 families do not imply all external storage/fault evidence is complete.

**Accepted preparation/action steps (W408):**

(C43) Root reviews the actual C6 outputs with a retained hashed manifest. Keep X01, X02, failed downgrade verdicts and open risk decisions as distinct result rows, never mark an entire family passed by assumption. Historical JAR/source identities still **needs package K01** / **needs package K02** and the per-family manifests **needs package K12**. Root's finished-output review is a Root action, not an invented package or verdict.

**Evidence to keep:** Exact dry/full commands and runner revision, full family/case inventory, candidate and historical
artifact identities, raw per-start logs/results/state/hash comparisons and aggregate report under phase 3. C6's existing
`D:/GitHub/ARCE-Task-Evidence/root-queue/C6/` may be referenced by a hashed manifest, not modified or relabelled blindly.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## Fuel-specific preconditions for J23–J29

Sources: [FUEL-ADMISSION-REPORT F18](FUEL-ADMISSION-REPORT.md), [W11B3 design sections 3–5/B3-V](W11B3-DESIGN.md)
and [campaign fuel clarification](FINAL-CAMPAIGN-PLAN.md). These seven rows are **fuel**, not J06/J07 cargo coverage.
Use `advancedrocketrycommunity:fluid_fuel_loader` and `advancedrocketrycommunity:linker`, a claimed 16,000 mB
single-Fluid local tank, exact server-owned binding, same-Level FULL device/rocket chunks and loaded entity sections.
Actor interaction is within 8 blocks; endpoints are within 6. The rocket is stationary, ASSEMBLED/FUELED/LANDED,
with one unambiguous compatible propulsion bank and no unproved transfer/assembly/bay/flight hold.
An assigned seat, actual passenger or anything riding makes it busy; walking away is not proof the assignment cleared.
Owner-only access has no OP bypass. Pin C6's actual menu controls, boundary-hold driver and probe commands first;
the design does not give those invocation spellings. Do not introduce code or hidden client state to fill that gap.

For a single-operation control: device 5,000 mB, bank 0, one 1,000 mB move gives 4,000/1,000. A partial control
with bank 1,251/2,000 and device 1,000 moves 749 mB, leaving 251. Two admitted 1,000 mB controls from two 5,000 mB
devices give 4,000/4,000 and bank 2,000. One device attempts at most once per 20 ticks; this is not a guaranteed
transfer cycle time. Freeze initial values and exact bank identity for every subcase, never assume two controls
can bypass current shared-marker/byte capacity. L1 acknowledgement authorizes apply; L2 acknowledgement authorizes
unlock. PREPARING before apply, including UNKNOWN L1, must not show moved or spendable credit. Applied pending
work may truthfully have moved resources but stays locked; do not label it “nothing moved”.

## J23 — Fuel loader/linker bilingual labels with admission off

**Phase/level/machine/accounts:** 2 V1, one owner-signed-in account A, real GPU; scripted with owner review.
**Gate:** Separate no-hook disabled world and fuel-only gated copy; all production switches off.
**Preconditions:** Fuel preconditions above, P2 legal fuel rocket/linker fixture and both languages.

1. [H] Start the ordinary copy with all test properties absent. → Production fuel admission remains false.
2. [P] Open the fuel loader and inspect the linker tooltip/selection feedback. → Labels in en_us are readable;
   disabled status is `ADMISSION_DISABLED`, not a successful transfer.
3. [P] Switch to zh_cn and repeat the same views. → Correct localized labels/no raw translation key, unchanged tank/bank.
4. [H] Start the separately marked copy with the additional fuel-only property. → Logs prove full gate and correct
   fuel seam, never production admission.
5. [P] Inspect the same labels on the gated loader/linker in both languages. → Same product vocabulary and correct
   current state; a gated screen is not labelled an ordinary-enabled production screen.

**Accepted preparation/action steps (W408):**

(C44; R542 N23) A opens the owner-only loader menu. B demonstrates ordinary refusal and only permitted synchronized feedback, never A's private menu. Keep the action-bar line visible/readable in en_us and zh_cn; action-bar-only feedback is accepted design. The extra fuel property selects a test seam only, not production admission. **Needs package K11**: exact menu/link/hold/seat-clear/probe and refusal recipes; owner O10 supplies UUIDs. Every negative action uses R546 receipt plus authoritative unchanged-state proof, server logs only where emitted.

**Evidence to keep:** Labeled loader/linker screenshots in both modes/languages, property/config/marker identities,
client/server logs and unchanged no-hook endpoint/ledger-presence check under phase 2; mere lookup must not create a ledger.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J24 — Fuel amount, Fluid and status through durable boundaries

**Phase/level/machine/accounts:** 2 V1, signed-in A on real GPU, packaged server; scripted with owner review.
**Gate:** No-hook ADMISSION_DISABLED comparison, transfers only on the fuel-only marked copy.
**Preconditions:** Fuel control totals and pinned L1/L2 holds; fresh per-case copies with exact bank capacity.

1. [P] Request a fill on the ordinary no-hook copy. → ADMISSION_DISABLED and unchanged device/bank totals.
2. [P] Inspect READY on the gated idle fixture. → Displayed Fluid/amount/binding matches raw server endpoints.
3. [H] Request one fill with exact L1 acknowledgement held. → PREPARING, 5,000/0 unchanged, no completed/spendable credit.
4. [H] Observe UNKNOWN L1 in its separate pinned hold. → Still no apply or available tentative credit; record the reason.
5. [H] Release exact L1. → One 1,000 mB apply: 4,000/1,000 raw values, TRANSFERRING with endpoints still locked.
6. [H] Hold L2/endpoint proof in the mapped pending case. → Pending/refusal reflects applied-but-locked work;
   `ESCROW_PENDING` does not claim a pre-apply zero movement.
7. [H] Release the exact proof/acknowledgement. → One final spendable result, no second credit on update/reopen.
8. [H] Run the 749 mB capacity control separately. → Bank becomes 2,000, device 251; UI matches partial server amount.
9. [H] Present the applicable refusal fixtures one at a time. → Correct reason and no unauthorized new move for EMPTY,
   BANK_FULL, INCOMPATIBLE_FLUID, AMBIGUOUS_BANK, UNBOUND, STALE_BINDING, WAITING_UNLOADED, OUT_OF_RANGE,
   NOT_OWNER, ROCKET_BUSY, ESCROW_FULL, LEDGER_UNAVAILABLE, TRANSFER_JOURNAL_UNPROVED, UNSUPPORTED_DATA
   and INVALID_DATA. Safe controls for each **needs package K11**; unavailable cases remain NOT_COVERED.

**Accepted preparation/action steps (W408):**

(C44; R542 N23) A opens the owner-only loader menu. B demonstrates ordinary refusal and only permitted synchronized feedback, never A's private menu. Keep the action-bar line visible/readable in en_us and zh_cn; action-bar-only feedback is accepted design. The extra fuel property selects a test seam only, not production admission. **Needs package K11**: exact menu/link/hold/seat-clear/probe and refusal recipes; owner O10 supplies UUIDs. Every negative action uses R546 receipt plus authoritative unchanged-state proof, server logs only where emitted.

**Evidence to keep:** Before/PREPARING/UNKNOWN/apply/pending/release screens, exact endpoints and availability flags,
L1/L2 markers, partial control, refusal case map and client/server logs under phase 2; no credit inferred from UI alone.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J25 — Fuel linker identity, stale/range/unloaded refusals

**Phase/level/machine/accounts:** 2 V1 real GPU A; owner and harness.
**Gate:** Ordinary binding checks with admission disabled; fill-dependent follow-ups only on fuel-only marked copy.
**Preconditions:** Fuel fixture with server-recorded logical/physical UUIDs, snapshot/bank/binding identity,
loaded-chunk baseline and precise within/outside-6/8-block positions.

1. [P] Use the linker on the loaded claimed loader. → Selection feedback identifies that device, not an arbitrary client position.
2. [P] Use it on the eligible rocket. → Exact same-Level/owner binding succeeds within the allowed range.
3. [P] Sneak-use the linker while idle. → Selection clears as specified; no fill or unrelated inventory change.
4. [H] Establish the pinned stale-binding case. → Replaced/changed identity refuses with STALE_BINDING, not a silent rebind.
5. [P] Attempt the link outside the allowed range. → OUT_OF_RANGE/refusal, no binding or endpoint mutation.
6. [H] Unload the target chunk without adding a support ticket. → Baseline has no FULL/entity-section availability.
7. [P] Replay the selection/use action against that target. → WAITING_UNLOADED/refusal, no client-named chunk load.
8. [H] Compare loaded/ticketed chunks, endpoint totals and binding before/after all refusals. → No arbitrary chunk
   acquisition, forced load, provisional debit or identity rewrite; run any fill-dependent confirmation only on the gated copy.

**Accepted preparation/action steps (W408):**

(C44; R542 N23) A opens the owner-only loader menu. B demonstrates ordinary refusal and only permitted synchronized feedback, never A's private menu. Keep the action-bar line visible/readable in en_us and zh_cn; action-bar-only feedback is accepted design. The extra fuel property selects a test seam only, not production admission. **Needs package K11**: exact menu/link/hold/seat-clear/probe and refusal recipes; owner O10 supplies UUIDs. Every negative action uses R546 receipt plus authoritative unchanged-state proof, server logs only where emitted.

**Evidence to keep:** Selection/bind/clear/stale/range/unloaded feedback screens, positions/UUIDs/binding and chunk
ticket baselines, exact unchanged totals and native logs under phase 2.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J26 — Fuel GUI scale, language, window and reload matrix

**Phase/level/machine/accounts:** 2 V1 signed-in A on real GPU; scripted with owner review.
**Gate:** Disabled ordinary copy compared with progressing/held fuel-only gated copy; production admission off.
**Preconditions:** J23/J24/J25 screens, stable pinned held states so reload does not erase the observation opportunity.
Matrix is en_us/zh_cn × GUI scale 2/3/4 × windowed/fullscreen × disabled/READY/PREPARING/pending/refusal.

1. [H] Select one matrix cell and its real server state. → Record actual window resolution/scale/language, not an enlarged crop.
2. [P] Open the loader and linker/binding views. → Labels, Fluid, amount, status, binding and full refusal remain readable;
   no critical slot/button is clipped or covered.
3. [P] Press F3+T. → Record reload start/end; same authoritative binding/status/totals return with no missing texture or key.
4. [H] Compare raw endpoints before/after reload. → Reload does not apply/replay/complete a held transaction.
5. [H] Repeat steps 1–4 for every cell. → Index every language/scale/mode/state and mark missing cells explicitly;
   no source gives a numeric reload-time budget, so report timing without inventing one.

**Accepted preparation/action steps (W408):**

(C44; R542 N23) A opens the owner-only loader menu. B demonstrates ordinary refusal and only permitted synchronized feedback, never A's private menu. Keep the action-bar line visible/readable in en_us and zh_cn; action-bar-only feedback is accepted design. The extra fuel property selects a test seam only, not production admission. **Needs package K11**: exact menu/link/hold/seat-clear/probe and refusal recipes; owner O10 supplies UUIDs. Every negative action uses R546 receipt plus authoritative unchanged-state proof, server logs only where emitted.

**Evidence to keep:** Indexed pre/post-reload native screenshots for all cells, reload timing, logs/configs and
endpoint/binding comparisons under phase 2, with gated and no-hook modes clearly separated.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J27 — Fuel ownership, competition and synchronized pending state

**Phase/level/machine/accounts:** 4 V2, two owner-signed-in premium accounts A/B, two real GPU clients; both.
**Gate:** Fill/competition only on the fuel-only marked world; production admission off.
**Preconditions:** Fuel fixtures with A-owned loader/rocket, B-owned foreign control and two legal competing devices;
(C44) B records ordinary refusal and permitted synchronized feedback, never A's private screen;
the exact observation recipe **needs package K11**.

1. [P-B] Attempt ordinary interaction with A's device/rocket as foreign owner. → NOT_OWNER/refusal, no OP bypass,
   resource mutation or binding change; capture B's actual permitted view.
2. [P-A] Start a fill with L1 held. → A sees PREPARING, no endpoint move/credit.
3. [P-B] Observe the operation using the approved permitted route. → Any synchronized state agrees with server;
   a refused owner-only screen is recorded as such, not forcibly opened for a matching screenshot.
4. [H] Submit the two-device/duplicate-interaction contention case. → Shared fair queue/identity/marker and capacity
   rules apply; no duplicate apply or unauthorized competitor admission. Log which requests were actually admitted.
5. [H] Release L1 into an applied pending hold. → Clients' permitted state matches the locked server totals; pending
   is not an available duplicate credit.
6. [H] Release the exact L2 proof/acknowledgement. → Final counts reconcile once. In the two admitted 1,000 mB
   control, device A/B are 4,000/4,000 and bank 2,000; do not require those totals if only one operation was admitted.
7. [H] Reverse ownership and repeat steps 1–6. → Neither account receives special authority or divergent final state.

**Accepted preparation/action steps (W408):**

(C44; R542 N23) A opens the owner-only loader menu. B demonstrates ordinary refusal and only permitted synchronized feedback, never A's private menu. Keep the action-bar line visible/readable in en_us and zh_cn; action-bar-only feedback is accepted design. The extra fuel property selects a test seam only, not production admission. **Needs package K11**: exact menu/link/hold/seat-clear/probe and refusal recipes; owner O10 supplies UUIDs. Every negative action uses R546 receipt plus authoritative unchanged-state proof, server logs only where emitted.

**Evidence to keep:** Both continuous videos/logs, ordinary foreign-owner feedback, gate/UUID checks, admitted-request
IDs, L1/L2/holds and exact raw/spendable endpoint totals under phase 4.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J28 — Fuel refusal while a passenger or assigned seat remains

**Phase/level/machine/accounts:** 4 V2, A fuel owner/B passenger, two signed-in premium accounts/real GPU clients; both.
**Gate:** Both fill attempts on the fuel-only marked copy, production admission off.
**Preconditions:** Bound idle legal fuel rocket, exact initial tank/bank and server actual/assigned-seat probes.

1. [P-B] Board the rocket. → Both clients and server agree on B's actual rider and assigned seat.
2. [P-A] Request a fill. → ROCKET_BUSY on A and B's permitted feedback/view, no L1 admission or fuel movement.
3. [P-B] Leave the vehicle normally. → Actual riding ends; record whether the assigned seat remains.
4. [P-A] Request a fill while that assignment remains. → ROCKET_BUSY, unchanged totals; vanilla dismount alone is insufficient.
5. [H/P] Clear the assigned seat through the pinned legitimate seat-management action. → No actual/assigned passenger
   or other rider remains; do not edit NBT to pretend the product cleared it.
6. [P-A] Request a fill again. → Eligible operation proceeds through acknowledged L1/apply/L2 once and both client
   views reflect the server totals; baseline control is 5,000/0 → 4,000/1,000.

**Accepted preparation/action steps (W408):**

(C44; R542 N23) A opens the owner-only loader menu. B demonstrates ordinary refusal and only permitted synchronized feedback, never A's private menu. Keep the action-bar line visible/readable in en_us and zh_cn; action-bar-only feedback is accepted design. The extra fuel property selects a test seam only, not production admission. **Needs package K11**: exact menu/link/hold/seat-clear/probe and refusal recipes; owner O10 supplies UUIDs. Every negative action uses R546 receipt plus authoritative unchanged-state proof, server logs only where emitted.

**Evidence to keep:** Both client videos/logs including ROCKET_BUSY, actual/assigned seat state at each attempt,
binding/operation IDs and pre/post exact fuel totals under phase 4.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## J29 — Fuel owner disconnect after L1 and retained passenger assignment

**Phase/level/machine/accounts:** 4 V2 A owner/B witness/passenger, two signed-in premium accounts/real GPUs;
phase-3 DS restart comparison if needed; both.
**Gate:** Operations only on fuel-only marked copy; production admission off.
**Preconditions:** Fuel control fixture, pinned exact durable L1 boundary and endpoint/seat/binding/status probes.
Owner-disconnect and passenger-disconnect are independent runs, each with fresh baseline and UTC timeline.

1. [P-A] Request one legal fill. → Capture operation and exact L1 acknowledgement before permitting the disconnect;
   PREPARING without L1 is not this boundary.
2. [P-A] Disconnect after that acknowledged L1. → Server keeps durable operation/recovery; B remains as the real witness.
3. [P-B] Observe the permitted status and world while A is offline. → Recovery is server-owned; no duplicate apply,
   refund or false completed/spendable tentative credit.
4. [P-A] Reconnect. → Same binding and coherent status; exact debit/credit occurs once, including any work completed offline.
5. [H] If recovery needs a process restart, save/reopen the same complete world twice. → Same operation/endpoint
   identity and once-only outcome; archive both observations rather than replacing the fixture.
6. [H] Restore a separate idle passenger fixture. → Resource/seat baseline is independent of the completed owner case.
7. [P-B] Board and then disconnect. → Assigned seat remains after actual connection/riding ends.
8. [P-A] Request a fill. → ROCKET_BUSY/refusal, no fuel movement while the assigned seat is retained.
9. [P-B] Reconnect and follow the legitimate seat-clear procedure. → No stale automatic credit or authority change;
   capture actual and assigned seat state separately.
10. [P-A] Request the now-eligible fill. → Exactly one acknowledged transfer, both views/totals agree and no second
    credit occurs on another reconnect.

**Accepted preparation/action steps (W408):**

(C44; R542 N23) A opens the owner-only loader menu. B demonstrates ordinary refusal and only permitted synchronized feedback, never A's private menu. Keep the action-bar line visible/readable in en_us and zh_cn; action-bar-only feedback is accepted design. The extra fuel property selects a test seam only, not production admission. **Needs package K11**: exact menu/link/hold/seat-clear/probe and refusal recipes; owner O10 supplies UUIDs. Every negative action uses R546 receipt plus authoritative unchanged-state proof, server logs only where emitted.

**Evidence to keep:** Both client disconnect/reconnect videos/logs, exact L1 evidence and UTC, server recovery/L2,
binding/seat state and raw/available totals at each boundary under phase 4, plus phase-3 restart evidence when used.
**Actual:**
**Screenshot or video:**
**Tester:**
**Date:**
**Build hash:**

## Open questions for Root

- **Answered:** C1 candidate, W399 sessions/commands, W400 cells, W403 bounded observers/seals; C33-C44 cap scope, LAN/expiry non-coverage, per-variant dispositions, comparison/wait rules, REF first-event requirement, C6 audio, X-only cuts, unimplemented respawn extension, ordinary research variants, C42 findings and owner-only fuel views. R542 decides each residual; R546 fixes preview and refusal proof.
- **All J rows:** source/API/historical identities **needs package K01** / **needs package K02**; current manifests **needs package K11**, **needs package K12**, **needs package K14** or **needs package K16** as each row specifies. Direct raw-tick/cache/all-ticket integration **needs package K15**. O10/O11/O12 supply UUIDs/node access/sitting arrangements.
- **J01/J02:** cap/menu/held-rocket/pad/seat recipes **needs package K11**. J03 remains NOT_COVERED while switches off/no LAN gate; an owner-enabled different candidate is not authorized here.
- **J04:** all 21 variants have individual synthetic-only/native references for this pass. Potential real-client external-stop recipes each **needs package K11** before changing coverage; EXIT.5 real-client expiry is unreachable.
- **J05-J10:** larger queues/cargo/admitted/cold/isolated-wait-23 drivers **needs package K12**; marked/no-hook kit **needs package K11**; complete live observers **needs package K15**. No unbounded wait gains a maximum.
- **J11:** actual-event REF run **needs package K12** / **needs package K15**. An over-budget tick opens R245's separate designed/reviewed task, not a waiver.
- **J12-J16:** producer tables/long-text pack are delivered; audio-enabled live profile **needs package K13**, scene/pause/recheck batches **needs package K16**. O9/O12 supply machine/time. Preview uses the corrected namespace.
- **J17:** fourteen external-stop/save/deferral recipes and real award probes **needs package K12**; connected-player H cuts remain forbidden.
- **J18/J19:** freeze/B1/R1 qualification and ordinary owner/competitor/rebind/full-account/pause scenes/C6 values **needs package K14**. The planet-point extension is not a test.
- **J20/J21:** K19 map is delivered; all 21 owed rechecks, all Fixed finding evidence and N15-N19 actions **needs package K16** batches plus **needs package K14** scenes; disc **needs package K10**, station/warp **needs package K08**, machines **needs package K05**. Missing receipt probes **needs package K18**.
- **J22:** historical/per-family manifests **needs package K12**, JAR/source identities **needs package K01** / **needs package K02**. Root reviews finished output hashes; X01/X02, downgrade verdicts and risk decisions remain separate.
- **J23-J29:** menus/permitted B views/holds/probes/legitimate seat-clear and refusal recipes **needs package K11**; no invented packet or forced owner-menu access.

## W397 preparation and verification record

Scope is only the five `CAMPAIGN-CHECKLISTS-F.md` through `-J.md` files, for 61 inventory rows.
Starting branch is `codex/v1.8.0-w397`, base `d8fddeaab`. No code, assets, upstream files, central status,
other worker's files or sealed evidence are changed. No subagent, Gradle, GameTest, native server or client
session is part of this documentation task. Runtime results above remain blank for the campaign executors.
Verification on 2026-11-20:

| Executed command/check | Observed result |
|---|---|
| `git status --short`, `git branch --show-current`, `git worktree list`, read-only process inspection | Assigned worktree/branch confirmed; initial tree clean; other workers left alone. |
| `python -B -` with inline inventory/field/step/link assertions | 61 rows in order (7 F, 7 G, 7 H, 11 I, 29 J); required fields and contiguous numbered steps present; links resolve to existing tracked files; each file below 1,500 lines. |
| `python -B scripts/validate_repository.py` | Exit 0: `Summary: 45 passed, 0 pending, 0 warnings, 0 failed`. |
| `git diff --check`; `git diff --cached --check` | Both exit 0; no whitespace errors. |
| `git diff --cached --stat`; `git diff --cached --name-only` | Exactly the five assigned checklist files staged; no unrelated changes. |

Only checklist documents are added; no automated test implementation is added or changed. Runtime campaigns,
full Gradle build/DataGen/GameTest and client runs are deliberately not executed under this TASK's exclusion.
There is no new runtime acceptance evidence. The preparation evidence is these five files and this command
record; the delivery commit is identified in branch history and the worker's final response, not a fabricated
self-referential commit ID. These checks cannot satisfy all v1.8 Required Gates. Root reviews the lists,
supplies the open fixtures and continues current-v1.8 campaign preparation. This worker does not push or approve a Gate.
