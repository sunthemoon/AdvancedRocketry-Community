# W30: v1.8 status reconciliation (a proposal for Root)

Recorded on 2026-10-13 by a headless Claude implementer session under
[TASK-W30](../TASK-W30-STATUS-RECONCILIATION.md). Worktree `D:/GitHub/arce-v180-w30-status`,
local branch `codex/v1.8.0-w30-status`, base `25d89beb`. When I read the shared branch
`codex/v1.8.0-classic-content`, it was at `165bb1f5`, one commit ahead of my base. That commit
changes only QUEUE.md, so every code fact below holds for both.

This file is a proposal only. I did not change any status file, the content ledger, an ADR or
the risk register. Root reviews this file and applies what it accepts. Nothing here marks a Gate,
a candidate or the version as passed.

Sections:

0. Method and limits
1. Per-chunk table for C16 to C19
2. Content ledger proposal
3. Pre-handover items and their disposition
4. Proposed replacement for `CURRENT_VERSION.md`
5. Remaining v1.8 work that the queue does not cover
6. Other findings for Root
7. Record of this task

Appendix A holds the 146 proposed ledger rows in CSV form. Appendix B maps each unmerged
worktree to its branch, HEAD and section 3 group.

## 0. Method and limits

- **Ancestry.** COMPLETION-PLAN lines 64 to 1012 (C16 to C19 and the sections after them) cite
  95 commit SHAs. All 95 resolve to commits.
  - 68 are ancestors of the shared branch at `165bb1f5`.
  - The other 27 are covered in section 3, with what happened to their content.
- **Worktrees.** `git worktree list` shows 195 worktrees, 183 of them `arce-v180-*`.
  - I compared each `arce-v180-*` HEAD, plus the shared checkout, with the shared branch.
  - 117 HEADs are merged. 67 are not merged; they share 55 distinct HEADs.
  - For each unmerged HEAD I checked two things: whether `git cherry` finds equivalent
    patches, and whether each file the branch changed since its merge base is byte-identical
    (same blob) in the shared branch.
  - Every unmerged pre-handover commit exists on an `origin/*` branch, as last fetched. So
    removing a local worktree does not make any cited SHA unreachable.
- **Reviews.** I recomputed the SHA-256 of all 29 `REVIEW.md` files under
  `D:/GitHub/ARCE-Task-Evidence/root-queue/*-review/`.
  - 28 values match the prefix or full value given in a merge commit message or in QUEUE.md,
    so those review files have not changed since their merges.
  - The W03 merge message and QUEUE.md give no value for W03-review, so there is nothing to
    compare it with. Section 1.1 records the value now.
  - `W27a-review/` holds only `REVIEW-TASK.md`; the review has not run yet.
- **Hosted runs.** The run IDs and their results come from the merge commit messages and
  QUEUE.md at `165bb1f5`. I could not query GitHub again, because the `gh` CLI is not installed
  in this session's Bash or PowerShell. Treat them as Root's recorded observations.
  - The workflow `.github/workflows/v180-development.yml` runs, in order: the ledger validator
    with `--require-accepted`; `clean build` with the unit tests; the artifact audit; the
    client-boundary check; `runData` twice with a clean-tree check; `runGameTestServer`; and
    `check_gametest_log.py`.
  - It does not run the full Python suite, the strict repository validator, a dedicated
    server, a restart or a real client.
- **Code facts.** I checked against the code and generated data, not against records alone:
  - registered IDs, read through the ledger validator's own `registered_ids()`;
  - config definitions;
  - JEI `RecipeType` declarations;
  - generated recipes, tags, advancements and machine patterns;
  - GameTest names.
- **Ledger shape check.** I applied Appendix A to a `git archive` export of `25d89beb`
  (`legacy-manifest`, `docs`, `tools/audit`, `scripts`, `src/main/resources`,
  `src/generated`).
  - I added stub evidence files that list each unit and its modern IDs.
  - `python -I -B scripts/validate_v180_content_ledger.py --require-accepted` then exited 0
    with result PASS.
  - The export (about 590 MB) was deleted afterwards.
- **Not done.**
  - I ran no Gradle, GameTest, dedicated server or client.
  - I did not read the 1,000-line status files or the implementation log in full. I read the
    C16 to C19 parts of COMPLETION-PLAN, the YAML keys of CURRENT_VERSION and the records named
    below.

## 1. Per-chunk table for C16 to C19

### 1.1 Evidence index for the Root-queue merges

All merges are first-parent commits on `codex/v1.8.0-classic-content`. The tested HEAD and the
hosted run come from each merge message.

| Slices | Chunk | Merge | Tested HEAD, hosted run | Review (verdict) | Open follow-ups from the merge message or QUEUE.md |
|---|---|---|---|---|---|
| W03 | C18b | `1e36af58` | `6132f9ca`, 38155101250 | W03-review (MERGE-WITH-FOLLOWUP) | M1, L1 were fixed by W06 |
| W01, W01F, W05, W01G | C16a, C16b | `ab5d9808` | `2c60eadd`, 38199986751 | W01-review (BLOCK), W01C-review (BLOCK), W01G-review (MERGE-WITH-FOLLOWUP) | L1 to L3 |
| W02, W02F | C17a | `ebf4c07b` | `57090329`, 38199403721 attempt 2 | W02-review (BLOCK), W02F-review (MERGE-WITH-FOLLOWUP) | native restart for F1/F2; a passenger who never returns locks the rocket; F3 goes to W11b3 |
| W04, W04F | C17b | `17fb7e15` | `874bba5c`, 38207662335 | W04-review (BLOCK), W04F-review (MERGE-WITH-FOLLOWUP) | N1 to N3 went to W04G |
| W06, W06F | C18b | `d8352a27` | `fda2cc98`, 38217298302 | W06-review (BLOCK), W06F-review (MERGE) | with `allow-flight=true`, vanilla trusts client flight |
| W09 | tests | `487da4e6` | `fc3fedc9`, 38217303450 | Root review | none |
| W04G | C17b | `f557e5c0` | `d3e04104`, 38220027274 | W04G-review (MERGE-WITH-FOLLOWUP) | optional N4 to N6 |
| W08 | persistence | `88b16cef` | `1722dccb`, 38221050014 | W08-review (MERGE-WITH-FOLLOWUP) | R02: start the packaged SRG jar on a dedicated server |
| W10 | C16c | `7324a7dd` | `b0c8d992`, 38224855115 | W10-review (MERGE-WITH-FOLLOWUP) | W10-R1 (Low) |
| W07, W07F, W07G | C17b | `0fc2aed4` | `5cdef2ae`, 38226376205 (QUEUE also cites 38229462409) | W07-review (BLOCK), W07F-review (BLOCK on N1, fixed by W07G) | V1/V2, dedicated server, native restart |
| W12 | C18c | `667fa40c` | `4093fa04`, 38229336877 | Root review | the two world-first advancements stay `impossible` until W11 |
| W15 | C16d | `f5038f2f` | `963d7db5`, 38239083487 | Root review | the 18 graph gaps went to W19 |
| W16 | C18d | `6a20a674` | `a6001eb9`, 38240401114 | Root review | fold the `advancedrocketrycommunity_v180` sound namespace |
| W13, W13F | C17b | `19da6240` | `20bc128f`, 38248887802 | W13-review (BLOCK), W13F-review (MERGE-WITH-FOLLOWUP) | N1 to N3 |
| W14, W14F | C18a | `a859d2f4` | `02d86c65`, 38249148243 | W14-review (BLOCK), W14F-review (MERGE-WITH-FOLLOWUP) | L1/L2 fixed by W25; L3; gas charger |
| W11a, W11a2 | D3 design | `c9514352` | design only | W11a-review (REDESIGN), W11a2-review (APPROVE-WITH-CHANGES) | C1 to C6 went to W11b1; P1 to P5 went to the W11b2 design |
| W19 | C16d | `3d499253` | `fe37b526`, 38261140501 | Root review | none |
| W11b2 design | D3 design | `811a5796` | design only | W11b2-design-review (APPROVE-WITH-CHANGES) | R1 to R6 go into W11b2 |
| W17, W17F | C17c | `b9ace0cd` | `81b288b6`, 38283733951 | W17-review (BLOCK; Root reviewed the F1 diff) | none |
| W18, W18F | C17c | `804be2ab` | `9f9e3e85`, 38283811217 | W18-review (MERGE-WITH-FOLLOWUP) | F8; F4, F6 and F7 went to W26 |
| W20 | C18b | `284709bf` | `3611d553`, 38289378640 | Root review | none |
| W21a | C18c design | `fa890675` | design only | W21a-review (APPROVE-WITH-CHANGES) | none open |
| W23 | security | `e8f4d02a` | `53a4ea68`, 38328763897 | Root review | none |
| W24 | C17b | `58d763d4` | `b91efd55`, 38336046793 | Root review | none |
| W22, W22F | C17b | `286bdfe9` | `4901af7b`, 38337117540 | W22-review (MERGE-WITH-FOLLOWUP) | F8 to F10 |
| W21b, W21bF | C18c | `a7b0af22` | `bf723b8a`, 38344780650 | W21b-review (MERGE-WITH-FOLLOWUP) | F9 |
| W25 | C18a | `06f1a898` | `977e0258`, 38354701151 | Root review | none |
| W11b1, W11b1F | D3 core | `f1eb5d83` | `548f0c3f`, 38360139403 | W11b1-review (MERGE-WITH-FOLLOWUP) | E2 `BARRIER_UNSTABLE` goes to W29 |
| W21c, W21cF | C18c | `5e75f835` | `cabba779`, 38368323736 | W21c-review (MERGE-WITH-FOLLOWUP) | V1 of the menu texts; the optional M1 |
| W26 | C17c | `7fb7e50c` | `a9a2b691`, 38369606917 | Root review | V1 of the finder strip position |

Root also merged two fixes: `0596d124` (the missing cutting-machine texture) and `798a2465` (the
Mixin refmap, hosted run 38258700306). QUEUE.md at `165bb1f5` records that hosted run 38371580132
passed on the shared branch at `888a81b2`, which holds every merge above.

The table lists 30 merge commits. The SHA-256 values below were recomputed on 2026-10-13 from
`D:/GitHub/ARCE-Task-Evidence/root-queue/<name>/REVIEW.md`. The W03 merge message gives no
hash; I add it here.

| Review | SHA-256 |
|---|---|
| W01-review | `5954064319de8587de9f7b50e89a08d5c6ec2387f9e2a994bce932384ebd388a` |
| W01C-review | `bb1ee58e098d84e85d1cac7ec8cbe865d1f717241609ba244298b704120edbdb` |
| W01G-review | `d27a69843e865a47e33038be548a77724089df77d698944bf789fa81fc830101` |
| W02-review | `4b9127e665e4dcc7074aaf37b2896faafbbfee62c49a003f8d5fc19105401d7a` |
| W02F-review | `c3e02c1f93c6947742df9c4f3dd0ea21a426801348939403b7bb54fec99332f7` |
| W03-review | `4b74bf776c585444e8332a89a410baa612030ceb41e6861f6ba06c102b64f73f` |
| W04-review | `45695dd5b04eb9761f75e5b900515b2935fbb56375948e004b777043a958e6b4` |
| W04F-review | `49319b9caa76a894835008101aac6bd7ab60ff08e8e89567826a2499605d3735` |
| W04G-review | `ec894726a1ffe653d6bee9d276a7eddeff3b2f09e04c11bfb948a8e759c2eb3e` |
| W06-review | `9bdd12238f18c32df093a42df5bb9c610aeafe78852b1a53b34928409d0c71e0` |
| W06F-review | `753c20281362745da4d53077f6bc981b3a69aea282bd1be95294111c21d27f15` |
| W07-review | `266db5a543b00f3b4eb45a112209b62f9d14cfb12b4f1d2383ed6afbdeb38edb` |
| W07F-review | `19859b139a62abaf42ad7f27c9f5b017a683c9ceda59aea5b21614b65636c39b` |
| W08-review | `935f718c25b8bedb0babd98e568becd0b8ba9f4272d1b458c7acb82778f0faaf` |
| W10-review | `5317fbec165f10f36b6132ee9c87a10daa84e639eca6809a86ecb2ab0f433c4b` |
| W11a-review | `1cfcbb8f6d4406f4302ed73cd346f99f3429ce9cf4bffcae17d1fc107316b0fe` |
| W11a2-review | `e54fd579dd5dee437c16c5d385a5fe5254280e080c78f1c661d2d3231cb68dc2` |
| W11b1-review | `e3d1fd91d6f113dfc4ead6a148251ea6fa33aac3741a2355c9099130f4927e3a` |
| W11b2-design-review | `8ec318a3863eef3b20bdc078309cc0dee9de8ad0d3e7cbb579a983a66a8b6f06` |
| W13-review | `d5b1db4adc436c5059af516a5637904038d1ab125fd9d80117471e667c167a63` |
| W13F-review | `ba6c974da4eece579c262c14d7d717aa5da18d70acb8806dc9d686a5ad85465d` |
| W14-review | `121651590cfb1678801b4da6e805ec52f001a36762400df322887f6219f0e0b5` |
| W14F-review | `d5f9bead354ef3b505cf41fbf0b9b7a2f2078872e7452894d97ecd2e80223fe3` |
| W17-review | `4bd848ae286597d29e05bbb38ca45cddcd1ca9845ae0d07805bf40cc147873b2` |
| W18-review | `7a65b5f1d81f738ea9b1ff101f6d3eea64d0e7f6ea31f3e3034dee904c6e63ff` |
| W21a-review | `0c76c2d4f9d125ca1af75ee96e35ddbd46ba914aaabbfddf5f9d3f32d01dfb50` |
| W21b-review | `d2a7401cb474a01c63fe011533ecbfa9d9abdafb441cd41e6b9af64ce0eb9d4a` |
| W21c-review | `8917c9fa7a34d38b7cb52765477dc7971125cb5dc5ad59a1059574fc1e3f5126` |
| W22-review | `1cbb11093ac46480458f0230dd741e31cac9df1331371f0d135bc243705b103d` |

**Open in every merged slice.** The following open items apply to every merged slice and are not
repeated in each row below:

- dedicated-server and native-restart evidence (S1);
- crash-cut evidence where a slice persists across stores (S2);
- real-GPU V1 and two-client V2 evidence;
- a manual review of the NEW art, and the provenance review of each slice's new resources.

The records list these as not done. For W16, see `records/W16.md` lines 168 to 179.

### 1.2 C16 (COMPLETION-PLAN line 64)

Proposed chunk mark: `[~]`. C16b and C16c are delivered. C16a-07b, the C16d tracking circuit and
two C16d checks remain open.

| Line | Item | Proposed state | Evidence | What remains |
|---|---|---|---|---|
| 66 | C16a-01 | delivered (keep `[x]`) | the plan line cites no commit; I did not re-verify it | line 67: full S1, malicious callbacks, removal and native save eligibility. These are now part of S1 for the kernel machines. W01G's record says no native server, `/data remove`, region writer or restart was exercised (`records/W01G.md` lines 136 to 143) |
| 68, 69 | C16a-02, C16a-03a | delivered (keep) | none needed | none |
| 70 | C16a-03b physical adapter and shared save | superseded by W01 (`ab5d9808`) | `TASK-W01-HATCH-KERNEL.md` lines 14 to 18; QUEUE.md lines 15 to 17; save protection reuses `GuardedChunkSaves` (`records/W01.md` lines 37 and 38) | the owner's words for this choice are not recorded (section 6) |
| 71, 77 to 82, 86, 110, 113, 118, 143 | 01 data, K1, K2, K3, diagnostic script, spawn migration, SETUP05 diagnosis and verification, lathe rows, guarded resources, chunk metadata, power-carrier format | delivered history (keep `[x]`) | all cited SHAs are ancestors; the `7a10f520` power-carrier files are byte-identical in the shared branch | none |
| 72 | raw-fidelity correction (`a34de0ad`) | close: superseded by W01 | as for line 70 | none |
| 84 | C16a-S1-GUARD-OBS04 | close: superseded | observation for the pre-kernel guard path; QUEUE.md lines 15 to 17 say it no longer blocks any task | none |
| 89 | hash/frame/native codec, GuardTicket, owner lifecycle (`8f7e1d10`) | close: superseded by W01 | TASK-W01 lines 35 to 39 made these classes optional; `machine/classic/adapter` is still not registered (`records/W23.md` lines 47, 48 and 134) | decide whether the dormant adapter code stays (section 6) |
| 105 | public save interface (`826f5f20`) | close: superseded by W01 | as for line 89 | none |
| 123 | 02 real empty hatch lifecycle (`7d7b474e`) | superseded: the runtime hatch lifecycle is W01, W01F and W01G | `ab5d9808` | S1 |
| 148 | server-side creative input interception, real client, FE conservation | item-NBT half done by W23 (`e8f4d02a`): the runtime `ClassicHatchBlockEntity` and `ClassicControllerBlockEntity` return `onlyOpCanSetNbt() = true` (`records/W23.md` lines 49 and 50) | `e8f4d02a`, hosted run 38328763897 | a real-client FE conservation check (V1) |
| 149 | 03/04 physical hatch, capability, controller, first lathe | delivered by W01 | `ab5d9808` | S1, V1 |
| 150 to 153 | C16a-04, tank/pump, C16a-07a, R021-DOC01 | delivered (keep) | none needed | none |
| 154 | C16a-LOAD-BINDING-20 (`932600d9`, documents only, not merged) | close: superseded by W01 | as for line 70; ADR-068 stays PROPOSED and is not needed (QUEUE.md "Parked") | none |
| 159, 164 | NATIVE-LOAD-21 (`79cb3e91`), PLACEMENT-PROVENANCE-22 (`71594573`) | delivered test history (keep `[x]`) | ancestors | none |
| 170 | C16a-COUPLED-CONTRACT-23 | close: superseded by W01 | as for line 70 | none |
| 179 | C16a-07b, machine formation with every motor tier | open | The classic patterns use the block tag `advancedrocketrycommunity:motors`. The generated tag lists all four tiers (`src/generated/v1.8/resources/data/advancedrocketrycommunity/tags/blocks/motors.json`). I found no GameTest that forms a classic machine with each tier. The search of `gametest` and `src/test` found `MotorDefinitionTest`, `PackagedRecipeGraphTest`, and the tag check at `MotorGameTests.java` line 45 | one GameTest per tier (section 5, item 4) |
| 180 | C16b arc furnace, lathe, cutting machine | delivered | W01 (lathe), W05 (arc furnace, blast brick, cutting machine, saw blade); `ab5d9808`; hosted run 38199986751; three reviews in section 1.1. Also present: the sawmill switch (`CommonConfig.java` line 28), JEI types `arc_furnace`, `lathe`, `cutting`, and 15 `cutting_*` recipes including log-to-plank recipes | W01 L1 to L3; no save-admission test uses the `arc_furnace` or `cutting_machine` IDs (`records/W01G.md` lines 140 and 141); S1, V1 |
| 181 | C16c crystallizer, chemical reactor, etcher, centrifuge | delivered | W10 (`7324a7dd`), hosted run 38224855115, W10-review. Formation with `endgame_casing` is tested by `C16cMachineGameTests.centrifugeFormsInFourRotationsAndRefusesWrongCasing` | W10-R1. The chemical reactor has only `chemical_reacting_bone_meal` and `chemical_reacting_rocket_fuel`; the two special C18 recipes are missing (section 5, item 5). S1, V1 |
| 182 | C16d components, recipe graph, rebalance | partly delivered | components from the pre-handover acquisition slices (all ancestors); W15 `f5038f2f`; W19 `3d499253`. Steel's first source is `arc_furnace_steel.json` (W05). The etcher outputs both circuit plates (`laser_etching_basic_circuit_plate.json`, `laser_etching_advanced_circuit_plate.json`) | (a) The tracking circuit (`item_variant:ic/1`, ADR-064 section 10.1) is not registered. (b) ADR-064 section 10.2 asks for a whole-graph run with the Tau Ceti Levels removed. I found only partial removal tests (`CodeAcquisitionGraphTest`, removed gas giant, lightwood and volcano), so I cannot verify it. (c) Section 10.3, the rebalance with CHANGELOG entries: not verified |
| 183 | C16d-ACQUISITION-01 (`8fe5a0ec`) | delivered | merged; later hosted runs pass | V1 of the NEW art |
| 192, 201, 210, 217 | IO-BOARD-03, LIQUID-IO-BOARD-04, CONTROL-BOARD-05, CIRCUIT-PLATES-06 | delivered | `50d4a3ea`, `0dc14e4f`, `c2934b83`, `b3595a73` are ancestors. "Second etcher production" is now W10 | V1 of the NEW art |
| 227 | C16d-FORWARD-15 | delivered (keep `[x]`) | `3815db7d`. The Root staging merge `e91ddc16` was not merged itself; its evaluator landed in `3815db7d` | none |

### 1.3 C17 (line 243)

Proposed chunk mark: `[~]`. Propulsion, station controls, pads, light, force field, notices,
solar and beacon are delivered. The loaders, monitor, linker and satellite bay are open.

| Line | Item | Proposed state | Evidence | What remains |
|---|---|---|---|---|
| 245 to 260 | single solar generator, solar surface fixture | delivered | `d8568c44` (generator), `e0c601e4` (fixture); both ancestors | S1, V1 |
| 261 | C17-TAU-OBSERVATION-02 (`c6d60282`), FAILED at the time | close: superseded | The pacing fix `0f91c6ee` (2026-10-10; implementation log lines 50 to 60) and W09 `487da4e6` (which "fixes the flakes of runs 38084795112 and 38206390869 attempt 1"). Every hosted run in section 1.1 passed the whole GameTest set, and no test in `src/main/java` sets `required = false`. This does not prove a unique cause | none |
| 267 | station light | delivered | `a9393281` | S1, V1 |
| 273 to 316 | C17 contract prerequisites (orbital eligibility Medium, NUM-01, NC1, typed hash, O3, candidate03 O1 to O7) | close: superseded | W02 implemented ADR-065 section 3 (typed propulsion, D1-A) and W07 implemented section 5.2 (D2 orbital clock, record schema 3, registry schema 5) directly from the accepted ADR, as AGENTS.md section 13 and WORKER-RULES require. The NUM-01 file (`9b50488a`, patch-equivalent to `dd456592`) and the NC1 correction (`451bddb6`, patch-equivalent to `e170eacc`) are byte-identical in the shared branch. `RocketPropulsionNumbers` is used by two other files, `RocketNativeFrame` by one | none |
| new | C17a propulsion: eight engines, tanks and core; typed fuel; ten ROCKET settings | delivered | W02, W02F, `ebf4c07b`. Keys in `RocketPropulsionConfig.java` lines 14 to 23 | W02F follow-ups (section 1.1) |
| new | C17a loaders, monitoring station, linker (ADR-065 section 4) | open | not registered | section 5, item 1 |
| new | C17b controls, pads, force field, notices | delivered | W07, W07F, W07G (`0fc2aed4`); W13, W13F (`19da6240`); W24 (`58d763d4`); W04, W04F, W04G (`17fb7e15`, `f557e5c0`); W22, W22F (`286bdfe9`) | follow-ups in section 1.1 |
| new | C17c solar array, beacon, beacon finder | delivered | W17, W17F (`b9ace0cd`); W18, W18F (`804be2ab`); W26 (`7fb7e50c`). The finder installs into the built-in helmet (`records/W18.md` line 73) | W18 F8 |
| new | C17c satellite bay (ADR-065 section 8) | open | the W27a design is delivered (`b55a41f2`, `9fffc35b`, not merged); its review has not run | section 5, item 2 |
| new | D3-A escrow (ADR-065 section 10) | core merged with admission disabled | W11b1, W11b1F (`f1eb5d83`) | W11b2 is running; W29 is running; W11b3 is not dispatched |

### 1.4 C18 (line 318)

Proposed chunk mark: `[~]` (it is `[ ]` now). Most devices, all equipment, research and
presentation are delivered. The C18a environment rules, the gas pad, two advancements and part of
C18d are open.

| Line | Item | Proposed state | Evidence | What remains |
|---|---|---|---|---|
| 323, 332 | C18b-JACKHAMMER-01, START-FEEDBACK | delivered | `80bbf16d`, `d95aa9b5`. The titanium source in survival is now `arc_furnace_titanium.json` (W05) | S1, V1 |
| 342 to 440 | C18a-SLEEP-01 and its sub-items | split: the behaviour is open; the tooling sub-items close | section 3, sleep row | section 5, item 7 |
| 442 | C18a-THERMITE-01 | delivered | `a6675a4e`, `ead0ece2` | S1, V1/V2, NEW art and provenance approval |
| 462 | C18a-AIRLOCK-EXPOSURE-01 | delivered (keep `[x]`) | `ee77c51a` | none |
| 467 | C18a-AIRLOCK-01 | delivered | `c263f7d9`, integrated by `61ae5001`. The failures that kept it open predate `0f91c6ee`. Every later hosted run in QUEUE.md passed the whole required set, which includes `AirlockDoorGameTests` | S1, V1/V2 |
| 480, 489, 496 | AIRLOCK-FIXTURE-02, SUPPLY-DIAG-01, airlock seed inputs | close: superseded by `0f91c6ee` | the diagnostics stay merged (`22f7d1ca`, `2a59cfac`, `b8136f0b`) | none |
| 502 | Tau cold-target investigation | close: superseded | as for line 261 | none |
| 506 | loader drop-count diagnostic (`10eb561a`) | close: superseded by FUEL-DROP-02 | `ba680996` | none |
| 514 | V180-FUEL-DROP-02 | delivered test history | `ba680996` | none |
| 523 | C18d-SKY-01 | delivered | `cbbb78e1`, `d2b4324f`; W16 added `sky.overworldOverride` (`ClientConfig.java` line 33) | V1/V2 |
| 535 | C18d-HUD-ENV-O2-01 | delivered | `f9f2d9d2` (environment and oxygen); W16 added the hydrogen and suit panels, making all 16 keys (`records/W16.md` line 18) | V1/V2 |
| 548 | C18a living gravity and fall damage | leaf A delivered; leaf B open | A: `1b6071d1`, `8b3fdca9`. B, gravity-scaled fall damage: only padded boots handle `LivingFallEvent` (`PaddedBoots.java`, `SuitMotionEvents.java` line 87). The vehicle and passenger Medium M1 is open (`c18a-living-gravity/TASK-01.md` lines 102 to 111) | section 5, item 6 |
| 559, 566 | C18 D1 auxiliary oxygen reserve, C18b-RESERVE-15 | delivered | W03 `e0e378c1` uses the `SuitReserveTransition` calculator from `707c389b` (`records/W03.md` lines 12 to 19). Keys: `OXYGEN.suitTankCapacity`, `OXYGEN.spaceSuitO2Buffer` | W03 chose the tank tiers 1,000, 2,000, 4,000 and 8,000. I found no owner approval of those numbers; ADR-066 section 9 item 1 calls for a scoped review of them |
| 575, 589, 594 | C18a-SEAL-01, INSTALLED-RUNTIME-02, ADMISSION-03 | delivered | `9cd5890b`, `33a3156e`, `c554e810`, `b0013161` | S1, V1 |
| 605, 620 | SEAL-SPATIAL-04, LASER-OWNER-FIXTURE-05 | close: test sources integrated | by stable patch-id, `770b3134` is `f77975fa` and `0b02c513` is `2f137f99`, both in the shared branch | The strict-validator timeout goes to C19. The COMMON watcher exception has no entry in `scripts/gametest_expected_log.json`, so the hosted log check would reject it. It did not appear in the passing runs |
| 638 | first events | open | the generated `classic/one_small_step.json` and `classic/first_station_warp.json` are still `minecraft:impossible` | section 5, item 9 |
| 642, 687 | Task04 report fields, terminal report fields | close as history | all ancestors | none |
| 655 | underwater life support | open | no implementation found | section 5, item 7 |
| 659 | C18c-01 inventory tutorial advancements | delivered by W12 (`667fa40c`) | the six IDs in `c18c-tutorial-inventory/TASK.md` are among the 17 generated `classic/*.json` files | S1. Its native Python driver sub-items: section 3 |
| 695 | C18a-01 atmosphere analyzer | delivered | `9fce551e` | S1, V1 |
| new | C18a scrubber, cartridge, pipe seal, detector | delivered | W14, W14F (`a859d2f4`); W25 (`06f1a898`) | W14F L3; the lossless cartridge refresh (section 5, item 5) |
| new | C18a gas charge pad | open, blocked on D4 | `records/W14.md` lines 26 to 29 and 128 to 133 | section 5, item 8 |
| new | C18b workstation, tanks, upgrades, jetpack, laser gun | delivered | W03 `1e36af58`; W06, W06F `d8352a27`; W20 `284709bf` | `space_breathing` cannot be obtained in survival (section 5, item 5) |
| new | C18c observatory, processor, registry schema 4 | delivered | W21a `fa890675`; W21b, W21bF `a7b0af22`; W21c, W21cF `5e75f835` | W21b F9; W21c V1 and M1 |
| new | C18c advancements | 15 of 17 delivered | W12 `667fa40c`; W15 `f5038f2f` | two world-first advancements (line 638) |
| new | C18d HUD, sounds, client settings, component layer | delivered | W16 `6a20a674` | section 5, item 10 |

### 1.5 C19 (line 704)

Proposed chunk mark: `[ ]` (unchanged). C19 work on the validators was paused at the handover,
and the queue covers none of it.

| Line | Item | Proposed state | Evidence | What remains |
|---|---|---|---|---|
| 706 | INVENTORY-TEST-11 | delivered (keep `[x]`) | `ffadaca3`; `5c0dee24` is patch-equivalent | none |
| 711, 713 | R11-OPS01, R14-OPS01 | Root to decide: close as operational history, or keep | These concern old temporary outputs whose cleanup the tool policy refused. They have no product effect | none, if closed |
| 716 | CHECKSUM-INPUT-16 | delivered | `a38f1dc9`. The three files of `0cefe86e` are byte-identical in the shared branch. `5049c27a` and `804c4a18` are its ancestors and are superseded by it | full Python and strict qualification (line 946) |
| 729 | G4-PROFILE-33 | delivered (keep `[x]`) | `63149455` | none |
| 735 to 935 | GIT-OBJECT-SESSION-34, BOOTSTRAP-FIXTURE-49, SOURCE-BLOB-73, MANUAL-FIXTURE-86, MANUAL-DECOMPOSITION-92, FINAL-INPUT-FIXTURE-97, FINAL-REVIEW-FIXTURE-101, BOOTSTRAP-ORGANIZATION-108, checkpoint 114, PACKET-ORGANIZATION-119, GIT-METADATA-124, WINDOWS-OBSERVER-132, VALIDATION-ADMISSION-139, INVOCATION-ADMISSION-150, PYTHON-PAYLOAD-161, PAYLOAD-STANDARD-166 | pending: one linear chain, not merged | The 14 commits from `607c626d` to `82f82b1f` form one line of descent. I checked each step with `git merge-base --is-ancestor`. It branches from `63149455`, which is in the shared branch. The chain changes 66 files, and the shared branch has changed none of them since `63149455` | merge, then qualify (section 5, item 12) |
| 778 | G4-ADR-64 (`d605be33`) | pending, separate from the chain | not an ancestor of `82f82b1f`. Checkpoint 114 says integration must handle it explicitly (line 849) | merge with the chain |
| 850 | the 1,606-line packet test class (Medium) | handled by the chain | `f87ff4c3` brings the largest class to 407 lines | merge |
| 936 | R32-03 (Low) | open | none | section 5, item 12 |
| 938 | portable committed-evidence references | open | The strict Markdown check stops after its 256-error prefix (line 938) | section 5, item 12 |
| 940 | STRICT-DIAGNOSTICS-08 | delivered (keep `[x]`) | `af363704`; `945e07c6` is patch-equivalent | none |
| 946 | full Python and strict qualification | open | the hosted workflow does not run either | section 5, item 12 |
| 959 | "186 PLANNED / 154 REVIEW" | replace | after Appendix A: 40 PLANNED; 154 REVIEW assets unchanged | none |

### 1.6 Other sections of COMPLETION-PLAN

- **"Follow-up execution and returned Claude inputs" (lines 962 to 1012).** These are the graph,
  audio and equipment draft contracts and the thermite drafts. Their content was delivered later
  by W15 and W19 (graph), W16 (audio), W03 and W06 (equipment), and the thermite integration.
  Section 3 lists the branches. I propose keeping one sentence that points to the
  implementation log.
- **"Current automated regression and risks" (lines 1014 to 1069)** describes the `b3595a73`
  run of 2026-10-11. I propose replacing it with the single current figure: hosted run
  38371580132 at `888a81b2`.
- **The IOWorker sections (lines 1071 to 1175).** These are history.
  - ADR-073's Mixin (W08, `88b16cef`) addresses the hosted chunk-save CME.
  - Its open item is the W08 review's R02: start the packaged SRG jar on a dedicated server.
  - I propose moving these sections to the implementation log, as AGENTS.md section 10
    requires.

## 2. Content ledger proposal

The ledger is `docs/work/v1.8.0-content-ledger.csv`. Its last change is `9e95770b` (2026-10-04).
The validator at `25d89beb` reports these counts:

- 653 units: 186 PLANNED, 104 IMPLEMENTED, 187 REDESIGNED, 93 MERGED, 25 DEFERRED and 58 REJECTED;
- assets: 154 REVIEW, 176 IMPORT, 11 IMPORTED, 444 REGENERATE and 113 EXCLUDE.

### 2.1 Constraints that shape the proposal

- **Evidence path.** A v1.8.0 IMPLEMENTED or REDESIGNED row must name an evidence file.
  - The path must match `docs/work/v1.8.0-(c1[5-8][a-d])-<slug>/<file>.md`
    (`scripts/validate_v180_content_ledger.py` line 78).
  - The file must exist and list the unit ID and every `advancedrocketrycommunity:<id>` in the
    target (lines 300 to 310).
  - The Root-queue records do not match this pattern, so Root needs per-batch delivery files.
    C15 follows the same pattern, for example `docs/work/v1.8.0-c15c-worlds/VERIFICATION.md`
    with its "Delivered ledger units" table.
- **Proposed evidence files.** All ten paths are free today:
  - `v1.8.0-c16b-machines/VERIFICATION.md`
  - `v1.8.0-c16c-machines/VERIFICATION.md`
  - `v1.8.0-c16d-components-graph/VERIFICATION.md`
  - `v1.8.0-c17a-propulsion/VERIFICATION.md`
  - `v1.8.0-c17b-station/VERIFICATION.md`
  - `v1.8.0-c17c-solar-beacon/VERIFICATION.md`
  - `v1.8.0-c18a-life-support/VERIFICATION.md`
  - `v1.8.0-c18b-equipment/VERIFICATION.md`
  - `v1.8.0-c18c-research/VERIFICATION.md`
  - `v1.8.0-c18d-presentation/VERIFICATION.md`

  Each file needs the following contents:
  - the merge commits and tested HEADs;
  - the hosted runs;
  - each review path with its SHA-256 (section 1.1);
  - a "Delivered ledger units" table that names each unit ID and modern ID in Appendix A;
  - the open S1, S2 and V1/V2 items;
  - the statement that no Gate is approved.
- **Registered IDs.** The validator reads registered IDs from the generated language keys and
  `assets/advancedrocketrycommunity/sounds.json` (lines 154 to 177).
  - **Sounds.** W16's ten sound definitions are in `assets/advancedrocketrycommunity_v180/sounds.json`.
    That file is not read, so a `sound_event` row cannot be IMPLEMENTED until Root folds the
    namespace (the W16 follow-up) or changes the validator.
  - **Advancement targets.** An advancement target cannot contain
    `advancedrocketrycommunity:classic/...`: the ID pattern would match `classic` and report it
    as unregistered. The proposal therefore writes `advancement classic/<id>`.
  - **The airlock door item.** `item:smallAirlockDoor` cannot be IMPLEMENTED. The airlock door
    item's language key is a `block.` key, so `airlock_door` is not in the item family. I
    propose MERGED into `block:airlock_door`, as the C15 rows do for internal parts. ADR-062
    section 1 defines MERGED as "an internal part that follows another row".
- **Change control.** ADR-062 section 7 allows these moves without an ADR revision. None of
  them moves a row to DEFERRED or REJECTED.
- **Decision tokens.** They follow the C15 and C16a rows: `ADR-062 ADR-064` for C16,
  `ADR-062 ADR-065` for C17 and `ADR-062 ADR-066` for C18. The existing tokens are kept
  (ADR-025, ADR-027, ADR-046, ADR-051, ADR-058), and ADR-067 is added for the seal detector.
  Configuration rows are REDESIGNED with `COMMON` or `CLIENT` key targets, and recipe XML files
  are REDESIGNED as recipe types, as in C15 and C16a.

### 2.2 Proposed changes

Appendix A lists all 146 rows exactly as the shape check applied them. By batch:

| Batch | Rows now PLANNED | Proposed delivered | Stay PLANNED |
|---|---:|---:|---:|
| C16a | 2 | 1 (`libvulpes:java/blockAdvStructureBlock` -> `endgame_casing`) | 1 |
| C16b | 13 | 13 | 0 |
| C16c | 16 | 16 | 0 |
| C16d | 10 | 8 | 2 |
| C17a | 24 | 18 (eight blocks, ten ROCKET settings) | 6 |
| C17b | 9 | 9 | 0 |
| C17c | 8 | 7 | 1 |
| C18a | 28 | 13 (twelve IMPLEMENTED or REDESIGNED, one MERGED) | 15 |
| C18b | 23 | 22 | 1 |
| C18c | 19 | 17 (15 advancements, observatory, processor) | 2 |
| C18d | 34 | 22 (20 client settings, two render rows) | 12 |
| **Total** | **186** | **146** | **40** |

After Appendix A, the counts become 193 IMPLEMENTED, 243 REDESIGNED, 94 MERGED and 40 PLANNED,
with DEFERRED and REJECTED unchanged. The validator with `--require-accepted` returns PASS on
that tree, given evidence files that list the units.

Among the 17 advancements, 13 are IMPLEMENTED. `holographic` and `wenttothemoon` are REDESIGNED,
because their ledger notes already say the trigger is new. Root may prefer IMPLEMENTED for all 15
delivered advancements.

### 2.3 Rows that stay PLANNED, and why

| Row(s) | Reason | Source |
|---|---|---|
| `libvulpes:java/blockMotor` | no per-tier formation GameTest | ADR-064 section 4.1; section 1.2, line 179 |
| `item_variant:ic/1` (tracking circuit) | not registered | ADR-064 section 10.1 |
| `config:CATEGORY_GENERAL.makeMaterialsForOtherMods` | probably deliverable as REDESIGNED ("tags replace the switch", ADR-064 section 2), but I could not name the delivering commit; Root to confirm | ADR-064 section 2 |
| `block:monitoringStation`, `block_variant:loader/2` to `/5`, `libvulpes:java/itemLinker` | not implemented | ADR-065 sections 4.2 and 4.3 |
| `block_variant:loader/1` (satellite bay) | design only | ADR-065 section 8 |
| `block:oxygenCharger` | D4 not resolved | ADR-066 sections 4 and 9 (item 4) |
| `block:unlitTorch`, `command:root/addtorch`, `config:OXYGEN.torchBlocks`, `config:OXYGEN.dropExtinguishedTorches`, `event:...blockPlacedEvent`, `event:...blockRightClicked` | no unlit torch, no `combustion_torches` or `combustion_fire_starters` tag, no `torch_pairs` catalog | ADR-066 section 3.2 |
| `config:OXYGEN.entityAtmBypass`, `event:...CheckSpawn` | no `atmosphere_bypass` tag, no spawn admission | ADR-066 section 3.3 |
| `event:...fallEvent` | gravity-scaled fall damage missing; M1 open | ADR-066 section 3.3 |
| `config:OXYGEN.vacuumDamage`, `config:PLANET.allowPlanetRespawn`, `config:PLANET.forcePlanetRespawn`, `event:...sleepEvent`, `event:...playerTick` | none implemented | ADR-066 section 3.3 |
| `enchantment:spacebreathing` | registered by W03, but the chemical recipe that applies it is missing, so survival players cannot get it (`records/W03.md` lines 159 to 161) | ADR-066 section 5.1 |
| `advancement:onesmallstep`, `advancement:flightofpheonix` | still `minecraft:impossible` | ADR-066 section 6.2; D3 |
| `entity:ARSpaceElevatorCapsule` | there is no S2C countdown carrier (`records/W16.md` lines 117 to 119) | ADR-066 section 7.1 |
| `event:ClientProxy.modelBakeEvent(ModelBakeEvent)` | no OBJ file imported (`records/W16.md` lines 113 to 116) | ADR-066 sections 7.1 and 7.3 |
| ten `sound_event:*` rows | Seven have a producer, so they can become IMPLEMENTED once the namespace is folded and the manual listen is done. The registered event IDs are already `advancedrocketrycommunity:<id>`: `air_hiss_loop`, `basic_laser_gun` (now used by W20's `LaserGunService`), `combustion_rocket`, `electric_shock_small`, `gravity_controller`, `laser_drill`, `railgun_bang`. Three have no producer: `lathe`, `rolling_machine`, `machine_large` | ADR-066 section 7.1 |

## 3. Pre-handover items and their disposition

The 51 pre-handover worktree HEADs that are not ancestors of the shared branch fall into six
groups. The active W tasks are excluded: W11b2 `a31f89a0`, W27a `9fffc35b`, W28 `9ee037dc` and
W29 `d2820e6f`. The table names each branch by its worktree suffix; Appendix B gives the full
worktree directory and branch name for all 67 unmerged worktrees.

| Group | Branches (worktree suffix, HEAD) | What exists | Proposed disposition |
|---|---|---|---|
| Content already in the shared branch by another commit | `power-carrier-format` `7a10f520`; `propulsion-numbers` `9b50488a`; `nc1-computation` `451bddb6`; `sleep-json-core` `a1ea5b35` (with `fa83657f`); `checksum-relative-depth` `0cefe86e` (with `5049c27a`, `804c4a18`); `inventory-test-applicability` `5c0dee24`; `strict-phase-diagnostics` `945e07c6`; `laser-owner-registration-fixture` `0b02c513`; `seal-spatial-boundary` `770b3134` (with `fd585a98`); `tau-holder-observation` `4a9e608d`; `transfer-wait-history` `f43ecefb`; `checkpoint-compare` `cde841d0` | Either `git cherry` marks every patch as present, or every changed file is byte-identical in the shared branch. The shared commits that carry them, by stable patch-id or by the commit that last wrote each file: `7a10f520`=`257e7b3a`; `9b50488a`=`dd456592`; `451bddb6`=`e170eacc` (its parent `4264a312`=`6213d613`); `a1ea5b35` -> `f7f02cda`; `0cefe86e` -> `a38f1dc9`; `5c0dee24`=`ffadaca3`; `945e07c6`=`af363704`; `0b02c513`=`2f137f99`; `770b3134`=`f77975fa` (`fd585a98`=`8182b38f`); `4a9e608d`=`7ab1b087`; `f43ecefb`=`046fc477`; `cde841d0` -> `8f7e1d10` | close as integrated, citing these commits |
| Pre-kernel hatch work-in-progress | `hatch-type-injection` `32243eed` (40 of 49 files identical), `classic-save-integration` `cb0a0019` (39 of 48), `guarded-owners` `4ce6b228` (37 of 45), `classic-central` `5e878778` (5 of 7), `claude-hatch-binding20` `932600d9` (three documents) | checkpoints of the guarded-save line integrated at `8f7e1d10`; the files that differ were changed afterwards | close as superseded by `8f7e1d10` and W01 (`ab5d9808`) |
| Native and IOWorker probes | `native-storage-ownership` `23729a00`; `native-proto-promotion` `0fcd67b4` (parked in QUEUE.md) | an experimental probe; the previous Root's diagnostic and handoff snapshot | Close `23729a00` as superseded by W08 (`88b16cef`, ADR-073). Keep the `0fcd67b4` branch: AGENTS.md section 13 cites that commit as the handover snapshot |
| Claude draft contracts (documents only) | `claude-graph-access` `c9b2c001`, `claude-graph-access04` `0179645c`; `claude-audio-lifetime` `3a607583`, `-start04` `e7c1f7b3`, `-oracle05` `6923dae6`, `-clarification06` `b000cae5`; `claude-equipment-bounds` `9903b3f6`, `-commit04` `0c6bc521`, `-witness05` `10eb068f`, `-clarification06` `2f828a28`; `claude-thermite-contract` `d9d97eed`, `claude-thermite-correction02` `a8133ec1` | unreviewed or partly reviewed drafts, never merged (COMPLETION-PLAN lines 979 to 1007) | close as superseded by the code: W15 and W19 (graph); W16 (audio); W03 and W06 (equipment); `a6675a4e` and `ead0ece2` (thermite) |
| Airlock analysis documents | `claude-airlock-triage18` `c7b99a2d`, `claude-airlock-next19` `5bbad2b5` (which also holds `e91ddc16`) | triage of the pre-pacing airlock failures | close as superseded by `0f91c6ee`; the evaluator in `e91ddc16` is in `3815db7d` |
| C19 validator chain | `object-session` `607c626d` … `python-suite-cost` and `tree-transport` `82f82b1f` (14 commits); `g4-malformed-adr` `d605be33` | performance and fixture work for the Python suite and the strict validator; each commit is pushed | **Merge** as one Root integration, then qualify (section 5, item 12). The `tree-transport` worktree has an uncommitted change to `scripts/prepare_v002_g0_review_packet.py`. The implementation log describes it as the "untested tree-transport prototype", so it is not mine and I have not touched it |

Line items without a branch of their own:

- **C18a-SLEEP-01 (COMPLETION-PLAN lines 342 to 440).** Every cited sleep commit is merged:
  `ce64a8eb`, plus `f7f02cda` (byte-identical to `a1ea5b35`). No sleep behaviour exists: no
  handler for `PlayerSleepInBedEvent` or `CanPlayerSleep` was found in `src/main/java`.
  Proposed disposition:
  - **Re-dispatch** the behaviour as one W task. It would implement ADR-066 section 3.3's sleep
    admission and the respawn options, using these inputs: owner decisions 01, 03 and 05 in
    `docs/work/v1.8.0-c18a-sleep/`, and the frozen D1 outcome
    (`D1-OUTCOME-FREEZE-33.md`).
  - **Close** the observation, JSON-core, driver and log-disposition sub-items, without a
    successor. Their purpose was to qualify native observation before the simplified flow;
    under AGENTS.md section 13 a W task proves the behaviour with GameTests and S1 instead.
  - This is a change of approach, so Root (or the owner, if Root prefers) should confirm it.
    The decision records stay.
- **C18c-01 native Python driver (phases 01, 02a, JSON, report stages and terminals).** All are
  merged. Close them as history: W12 delivered the advancements, and their S1 restart check
  becomes part of the general S1 item.
- **Underwater life support and first events (lines 655 and 638).** Re-dispatch them as W tasks
  (section 5, items 7 and 9).

## 4. Proposed replacement for `CURRENT_VERSION.md`

This replaces the whole file. The v1.0 acceptance block is copied unchanged. The roughly 200
historical `*_qualification`, `*_hosted_ci` and similar keys move to the implementation log
(AGENTS.md section 10).

If Root applies the ledger change in a separate commit, use the validator's numbers at that
commit for the ledger line.

````markdown
# CURRENT_VERSION

```yaml
current_version: v1.0.0
status: IN_PROGRESS
next_action: Review remaining adjacent-chunk loading and expiry behavior plus candidate acceptance gaps in docs/releases/v1.0.0/RELEASE-EVIDENCE.md; controlled native queue recovery and pending-player logout pass, genuine storage ordering and final v1.0 Gates remain open, no long-load work
last_updated: 2026-09-06
prerequisite_version: v0.9.0
prerequisite_status: PASSED
prerequisite_merge_commit: a7196ff9b22220c344071a1af69a663036f76aef
work_branch: codex/v1.0.0-stable-core
base_commit: 34b2e99b48a33f4ba8905b6a69a38efee1649d3f
build: 1.20.1-1.0.0-dev
tested_implementation_commit: ""
artifact_sha256: ""
```

The accepted Beta identity, approvals and published artifact remain immutable
in [v0.9.0 GATE-STATUS](../releases/v0.9.0/GATE-STATUS.md). v1.0 stabilizes that
core without implementing v1.1+ features. Development-tree checks are not a
frozen release-candidate commit or stable approval. See the
[implementation log](../work/v1.0.0-implementation-log.md).

## Active development checkout

The acceptance cursor above stays at the earliest unfinished release Gate. Under
[ADR-060](../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md), v1.8 development
proceeds separately from inherited release acceptance.

```yaml
active_development_version: v1.8.0
active_development_branch: codex/v1.8.0-classic-content
status: IN_PROGRESS
phase: IMPLEMENTING
root: Claude (AGENTS.md section 13, since 2026-10-11)
task_queue: docs/work/v1.8.0-root-queue/QUEUE.md
completion_plan: docs/status/COMPLETION-PLAN.md
development_log: docs/work/v1.8.0-implementation-log.md
accepted_development_baseline: 55da6a58842762c382edce0a5a842d06bb76ff6e
runtime_build: 1.20.1-1.8.0-dev
latest_code_merge: 7fb7e50cb23314808add2cc1afc6bee0af087545
latest_hosted_regression: run 38371580132 at 888a81b2fae2c109591e983e3c7ccd766d841410, passed
ledger_planned_units: 40
asset_review_items: 154
required_gates_approved: none
last_updated: 2026-10-13
```

## Current state

Since the Root handover on 2026-10-11, 30 Root-queue merges have landed on the shared branch.
The queue records each one with its hosted run and review.

- C16b and C16c are complete on the classic kernel.
- C17a propulsion, the C17b station blocks, pads, force field and notices, and the C17c solar
  array and beacon are complete.
- Most C18 devices are complete, as are all C18b equipment, the C18c observatory, processor and
  15 of 17 advancements, and the C18d HUD, sounds and client settings.
- The D3-A escrow core is merged with admission disabled.

The latest hosted regression runs the following on the exact pushed commit: a clean build with
the unit tests, the artifact audit, `runData` twice with a clean tree, every GameTest, and the
GameTest log check. It does not run the full Python suite, the strict repository validator, a
dedicated server, a restart or a real client.

Open, in short:

- 40 ledger units are still PLANNED. They are listed with their ADR sections in
  docs/work/v1.8.0-root-queue/records/W30-STATUS-PROPOSAL.md, section 2.3.
- 154 legacy assets are still under REVIEW (ADR-066 section 7.3).
- No slice has dedicated-server or native-restart (S1) evidence.
- D3-A crash cuts (S2) wait for W11b2 and W11b3.
- No slice has real-GPU (V1) or two-client (V2) evidence.
- The full Python suite and the strict validator are not qualified (C19).
- R-021 and the risks added by the W slices stay open.

No v1.8 Required Gate is approved. Only the owner marks a Gate or the version as passed.
````

## 5. Remaining v1.8 work that the queue does not cover

QUEUE.md's "Queued" table is empty. Its "Next candidates" list is mostly merged now (section 6).
The following work has no task yet.

1. **C17a loaders, monitoring station and linker**, ADR-065 sections 4.2 and 4.3.
   - The design already splits this into W11b3, W11c, W11d and W11e (`records/W11A-DESIGN.md`
     section 8, lines 322 to 341):
     - W11b3: `fluid_fuel_loader` and the rocket-binding half of the linker, with packaged S2
       evidence. It also fixes W02 F3's precise refill.
     - W11c: cargo Fluid loaders and unloaders.
     - W11d: item cargo loaders and unloaders.
     - W11e: the full linker and monitor.
   - The six ledger rows in section 2.3 depend on these slices.
2. **C17c satellite bay**, ADR-065 section 8. W27a's design is done; its review and an
   implementation slice follow W11b2 and W11b3.
3. **C16d tracking circuit**, ADR-064 section 10.1. ADR-062 section 6 names tracking circuits
   as a C16d input of C17a.

   Two parts of the same section are unverified:
   - section 10.2: the graph run with the Tau Ceti Levels removed;
   - section 10.3: the rebalance with its CHANGELOG entries.
4. **C16a-07b**, ADR-064 section 4.1: one formation GameTest per motor tier.
5. **The chemical reactor's two special recipes.**
   - The `space_breathing` enchantment (ADR-066 section 5.1; ADR-062 section 6 C18b depends on
     C16c for it).
   - The lossless carbon-cartridge refresh (ADR-066 section 3.1, lines 186 to 188).

   W03 and W10 left both out (`records/W03.md` lines 159 to 161; `records/W10.md` lines 29
   to 31).
6. **Gravity-scaled fall damage (leaf B)**, ADR-066 section 3.3. The vehicle and passenger
   Medium M1 must be resolved first (`c18a-living-gravity/TASK-01.md` lines 102 to 111).
7. **The other C18a environment rules**, ADR-066 sections 3.2 and 3.3:
   - torches and fire starters (`unlit_torch`, the `combustion_torches` and
     `combustion_fire_starters` tags, the `torch_pairs` catalog, `dropExtinguishedTorches`);
   - non-player atmosphere effects, the `atmosphere_bypass` tag and spawn admission;
   - `vacuumDamage`;
   - sleep and the two respawn options (see the sleep row in section 3);
   - underwater breathing;
   - the space safety return with its reverse station index.

   These could be split into three or four W tasks.
8. **The gas charge pad**, ADR-066 sections 4 and 9 (D4). Before implementation, ADR-066
   requires demonstrated player-file save ordering, or an alternative interaction that the
   owner approves. This needs an owner or Root decision before dispatch. QUEUE.md says only
   that it "waits for W11b3"; I found no record that maps D4 onto D3-A.
9. **The world-first advancements and the first-event election**: `one_small_step` and
   `first_station_warp`. ADR-066 section 6.2 and D3 apply. This follows W11b2's rocket journal
   schema 3.
10. **The open C18d items**, ADR-066 section 7.1:
    - client rendering of the black-hole sky (W07 already sends the server's `SINGULARITY` sky
      kind, but no client code reads it);
    - the elevator capsule effect (it needs a Root-owned S2C countdown carrier);
    - OBJ model loading, once OBJ assets are imported;
    - producers for the `lathe`, `rolling_machine` and `machine_large` sounds;
    - the sound-namespace fold;
    - the manual listen.

    ADR-062 section 5 also lists the black-hole sky (ADR-057) as C18d.
11. **Per-asset closure** for the 154 REVIEW assets, ADR-066 section 7.3 and ADR-062 section 6
    (C19).
12. **C19**, ADR-062 section 6 and version document sections 9, 14 and 15:
    - merge the validator chain `82f82b1f` and `d605be33` (section 3);
    - run the full Python suite and the strict validator once;
    - fix the portable evidence references;
    - update `docs/PORTING_MATRIX.md`;
    - decide ADR-070 and ADR-071 (PROPOSED). The organization candidates in the chain bring the
      large test classes under 500 lines, which may make them unnecessary;
    - decide ADR-068 (PROPOSED, parked);
    - close R32-03;
    - run `validate_v180_content_ledger.py --closure`.
13. **S1 and V1/V2 for every merged slice** (version document sections 10 and 11), plus W08 R02:
    start the packaged SRG jar on a dedicated server. ADR-066 section 8 keeps the full campaign
    scheduled under ADR-018 (see ADR-062 section 8).

The open follow-ups from merged slices are already listed in QUEUE.md and section 1.1. They have
no task yet:

- W01 L1 to L3;
- W02F (native restart; the locked rocket);
- W04 N4 to N6;
- W10-R1;
- W13 N1 to N3;
- W18 F8;
- W21b F9;
- W21c M1;
- W22 F8 to F10.

## 6. Other findings for Root

- **QUEUE.md "Next candidates" (lines 79 to 115) is stale.**
  - The four C16c machines, C17b controls and pads, the C17c beacon and solar array, C18a, C18b,
    C18c and C18d are merged.
  - The CME line still says "being fixed by W08", but W08 is merged.
  - I propose replacing that list with section 5.
- **No verbatim owner record for the kernel choice.** QUEUE.md lines 15 to 17, TASK-W01 lines
  14 to 18 and COMPLETION-PLAN line 10 record the 2026-10-11 choice ("build the kernel as the
  accepted ADR-064 describes, with Forge events") only in Root's words. AGENTS.md section 10
  asks for the owner's words, or a verifiable source (date and channel).
- **Dormant classic adapter code.** `machine/classic/adapter` and the pre-kernel guard classes
  remain unregistered (`records/W23.md` line 134). They are not part of v1.8's delivered
  behaviour. Root may want a later cleanup decision. I propose no change now.
- **Worktrees.** Several pre-handover worktrees can be removed: the closed groups in section 3,
  and the detached review checkouts whose HEAD is merged.
  - Removing them only deletes local checkouts. Every cited commit also exists on an `origin/*`
    branch.
  - Two worktrees have uncommitted content that is not mine: `tree-transport` (one modified
    script) and `checksum-input-review-20261010-18` (an untracked `.checksum-review18-temp/`).
  - AGENTS.md section 11 forbids me from touching them.
- **W03's tank numbers.** W03 chose the tank tiers 1,000, 2,000, 4,000 and 8,000
  (`records/W03.md` line 15). ADR-066 section 9 item 1 says numeric tiers still need a scoped
  review. I found no record of that review.

## 7. Record of this task

- **Done.** I wrote this proposal and committed it on `codex/v1.8.0-w30-status`. No other file in
  the repository changed. My scratch scripts and the CSV are in
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W30/scratch/`. The 590 MB export was deleted.
- **Commands and results.** All were read-only, except the scratch export.

  | Command | Result |
  |---|---|
  | `git log`, `git show`, `git merge-base --is-ancestor`, `git cherry`, `git rev-parse <commit>:<path>` over the shared branch and 184 worktree HEADs | results in sections 0 to 3 |
  | `git log -p … \| git patch-id --stable` for the twelve integrated pre-handover HEADs | the mapping in section 3 |
  | `sha256sum` over 29 `REVIEW.md` files | 28 match the merge messages or QUEUE.md; W03-review has no recorded value |
  | `python -I -B scripts/validate_v180_content_ledger.py` at `25d89beb` | exit 0, PASS, 186 PLANNED |
  | the same with `--require-accepted`, on the export with Appendix A and stub evidence files | exit 0, PASS, 40 PLANNED |
  | `gh run view` (Bash and PowerShell) | not available: `gh` is not installed. Hosted results are unverified here |
- **ADR choices.** None. The ledger dispositions follow the C15 and C16a conventions; section 2.1
  explains each departure.
- **Not done.** Gradle, GameTest, a dedicated server and any client were out of scope. I did not
  query the hosted runs again. I did not read the implementation log in full.
- **Known risks.**
  - A hosted-run result I cite could be wrong if a merge message was wrong; I could not check
    GitHub.
  - The shared branch may move after `165bb1f5`. Root should repeat the ancestry check for any
    later merge.
  - The sleep and C18c-01 tooling dispositions in section 3 are a change of approach and need
    Root's (or the owner's) confirmation.

## Appendix A: proposed ledger rows (CSV, same columns as the ledger)

Each row replaces the PLANNED row with the same `unit_id`. The rows were applied exactly as shown
for the shape check in section 0.

```csv
unit_id,disposition,plan,target,decision,notes,evidence
libvulpes:java/blockAdvStructureBlock,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:endgame_casing,ADR-064,advanced machine casing; the centrifuge and laser etcher patterns form with it,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
block:arcfurnace,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:arc_furnace,ADR-062 ADR-064,legacy 5 x 5 x 5 layout on the classic kernel; alloys and silicon,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
block:blastbrick,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:blast_brick,ADR-062 ADR-064,arc furnace structure,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
block:cuttingMachine,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:cutting_machine,ADR-062 ADR-064,wafers and sawmill recipes,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
block:lathe,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:lathe,ADR-062 ADR-064,rods,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
block:sawBlade,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:saw_blade,ADR-062 ADR-064,cutting machine structure,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
config:CATEGORY_GENERAL.sawMillCutVanillaWood,REDESIGNED,v1.8.0,COMMON machines.sawmillVanillaWood,ADR-062 ADR-064,off pauses sawmill-marked cutting recipes without consuming resources,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
config_file:CuttingMachine.xml,REDESIGNED,v1.8.0,cutting recipe type (data pack recipes),ADR-062 ADR-064,,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
config_file:ElectricArcFurnace.xml,REDESIGNED,v1.8.0,arc_furnace recipe type (data pack recipes),ADR-062 ADR-064,,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
config_file:Lathe.xml,REDESIGNED,v1.8.0,lathe recipe type (data pack recipes),ADR-062 ADR-064,,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
integration:jei/arcFurnace,IMPLEMENTED,v1.8.0,JEI arc furnace category,ADR-062 ADR-064,,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
integration:jei/lathe,IMPLEMENTED,v1.8.0,JEI lathe category,ADR-062 ADR-064,,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
integration:jei/sawmill,IMPLEMENTED,v1.8.0,JEI cutting category,ADR-062 ADR-064,cutting machine wood recipes,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
item_variant:sawBladeIron/0,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:iron_saw_blade,ADR-062 ADR-064,cutting machine part,docs/work/v1.8.0-c16b-machines/VERIFICATION.md
block:centrifuge,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:centrifuge,ADR-062 ADR-064,enriched lava separation with seeded retained chance outcomes,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
block:chemicalReactor,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:chemical_reactor,ADR-062 ADR-064,rocket fuel and chemistry; the C18 special recipes are separate rows,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
block:crystallizer,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:crystallizer,ADR-062 ADR-064,silicon boules and dilithium; low-gravity rule,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
block:precisionlaseretcher,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:precision_laser_etcher,ADR-062 ADR-064,circuit plates; the lens is retained,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
block:quartzcrucible,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:quartz_crucible,ADR-062 ADR-064,crystallizer structure,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
config:CATEGORY_GENERAL.crystalliserMaximumGravity,REDESIGNED,v1.8.0,COMMON machines.crystallizerMaximumGravity,ADR-062 ADR-064,,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
config:CATEGORY_GENERAL.lavaCentrifugeOutputs,REDESIGNED,v1.8.0,centrifuging recipe type (data pack recipes),ADR-062 ADR-064,data-driven outputs,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
config_file:Centrifuge.xml,REDESIGNED,v1.8.0,centrifuging recipe type (data pack recipes),ADR-062 ADR-064,,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
config_file:ChemicalReactor.xml,REDESIGNED,v1.8.0,chemical_reacting recipe type (data pack recipes),ADR-062 ADR-064,,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
config_file:Crystallizer.xml,REDESIGNED,v1.8.0,crystallizing recipe type (data pack recipes),ADR-062 ADR-064,,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
config_file:PrecisionLaserEtcher.xml,REDESIGNED,v1.8.0,laser_etching recipe type (data pack recipes),ADR-062 ADR-064,,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
integration:jei/centrifuge,IMPLEMENTED,v1.8.0,JEI centrifuge category,ADR-062 ADR-064,,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
integration:jei/chemicalReactor,IMPLEMENTED,v1.8.0,JEI chemical reactor category,ADR-062 ADR-064,,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
integration:jei/crystallizer,IMPLEMENTED,v1.8.0,JEI crystallizer category,ADR-062 ADR-064,,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
integration:jei/precisionLaserEtcher,IMPLEMENTED,v1.8.0,JEI laser etcher category,ADR-062 ADR-064,,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
item:iquartzcrucible,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:quartz_crucible_shell,ADR-062 ADR-064,crafting part of the crucible,docs/work/v1.8.0-c16c-machines/VERIFICATION.md
item_variant:ic/3,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:control_board,ADR-062 ADR-064,component,docs/work/v1.8.0-c16d-components-graph/VERIFICATION.md
item_variant:ic/4,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:item_io_board,ADR-062 ADR-064,component,docs/work/v1.8.0-c16d-components-graph/VERIFICATION.md
item_variant:ic/5,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:liquid_io_board,ADR-062 ADR-064,component,docs/work/v1.8.0-c16d-components-graph/VERIFICATION.md
item_variant:itemCircuitPlate/0,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:basic_circuit_plate,ADR-062 ADR-064,laser etcher output,docs/work/v1.8.0-c16d-components-graph/VERIFICATION.md
item_variant:itemCircuitPlate/1,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:advanced_circuit_plate,ADR-062 ADR-064,laser etcher output,docs/work/v1.8.0-c16d-components-graph/VERIFICATION.md
item_variant:misc/0,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:user_interface,ADR-062 ADR-064,component,docs/work/v1.8.0-c16d-components-graph/VERIFICATION.md
item_variant:misc/1,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:carbon_brick,ADR-062 ADR-064,carbon source,docs/work/v1.8.0-c16d-components-graph/VERIFICATION.md
material:Carbon,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:carbon_brick,ADR-062 ADR-064,legacy ingotCarbon is the carbon brick; tag forge:ingots/carbon,docs/work/v1.8.0-c16d-components-graph/VERIFICATION.md
block:advbipropellantRocketmotor,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:advanced_bipropellant_rocket_motor,ADR-062 ADR-065,propulsion tier with fuel and oxidizer,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
block:advRocketmotor,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:advanced_rocket_motor,ADR-062 ADR-065,propulsion tier,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
block:bipropellantfueltank,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:bipropellant_fuel_tank,ADR-062 ADR-065,propulsion tier,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
block:bipropellantrocketmotor,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:bipropellant_rocket_motor,ADR-062 ADR-065,propulsion tier,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
block:nuclearcore,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:nuclear_core,ADR-062 ADR-065,propulsion tier,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
block:nuclearfueltank,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:nuclear_fuel_tank,ADR-062 ADR-065,propulsion tier,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
block:nuclearrocketmotor,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:nuclear_rocket_motor,ADR-062 ADR-065,propulsion tier,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
block:oxidizerfueltank,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:oxidizer_fuel_tank,ADR-062 ADR-065,propulsion tier,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
config:ROCKET.canBeFueledByHand,REDESIGNED,v1.8.0,COMMON ROCKET.canBeFueledByHand,ADR-062 ADR-065,hand container exchange with the typed fuel banks,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
config:ROCKET.fuelCapacityMultiplier,REDESIGNED,v1.8.0,COMMON ROCKET.fuelCapacityMultiplier,ADR-027 ADR-062 ADR-065,captured at assembly,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
config:ROCKET.gravityAffectsFuels,REDESIGNED,v1.8.0,COMMON ROCKET.gravityAffectsFuels,ADR-027 ADR-062 ADR-065,captured at assembly,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
config:ROCKET.nuclearCoreThrustRatio,REDESIGNED,v1.8.0,COMMON ROCKET.nuclearCoreThrustRatio,ADR-027 ADR-062 ADR-065,captured at assembly,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
config:ROCKET.rocketBipropellants,REDESIGNED,v1.8.0,COMMON ROCKET.rocketBipropellants Fluid ID list,ADR-027 ADR-062 ADR-065,role-specific Fluid fuel table,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
config:ROCKET.rocketFuels,REDESIGNED,v1.8.0,COMMON ROCKET.rocketFuels Fluid ID list,ADR-027 ADR-062 ADR-065,role-specific Fluid fuel table,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
config:ROCKET.rocketNuclearWorkingFluids,REDESIGNED,v1.8.0,COMMON ROCKET.rocketNuclearWorkingFluids Fluid ID list,ADR-027 ADR-062 ADR-065,role-specific Fluid fuel table,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
config:ROCKET.rocketOxidizers,REDESIGNED,v1.8.0,COMMON ROCKET.rocketOxidizers Fluid ID list,ADR-027 ADR-062 ADR-065,role-specific Fluid fuel table,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
config:ROCKET.rocketsRequireFuel,REDESIGNED,v1.8.0,COMMON ROCKET.rocketsRequireFuel,ADR-062 ADR-065,debit-only fuel requirement switch,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
config:ROCKET.thrustMultiplier,REDESIGNED,v1.8.0,COMMON ROCKET.thrustMultiplier,ADR-027 ADR-062 ADR-065,captured at assembly,docs/work/v1.8.0-c17a-propulsion/VERIFICATION.md
block:altitudeController,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:station_altitude_controller,ADR-062 ADR-065,station orbital distance through the checked station commit,docs/work/v1.8.0-c17b-station/VERIFICATION.md
block:circleLight,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:station_light,ADR-062 ADR-065,decorative light block,docs/work/v1.8.0-c17b-station/VERIFICATION.md
block:concrete,REDESIGNED,v1.8.0,vanilla concrete in the landing pad recipe,ADR-062 ADR-065,vanilla concrete replaces the legacy block,docs/work/v1.8.0-c17b-station/VERIFICATION.md
block:forceField,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:force_field,ADR-062 ADR-065,technical block placed only by the projector,docs/work/v1.8.0-c17b-station/VERIFICATION.md
block:forceFieldProjector,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:force_field_projector,ADR-062 ADR-065,owner-protected bounded field through the protection chain,docs/work/v1.8.0-c17b-station/VERIFICATION.md
block:gravityController,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:station_gravity_controller,ADR-058 ADR-062 ADR-065,block front-end for the station gravity commit,docs/work/v1.8.0-c17b-station/VERIFICATION.md
block:landingPad,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:landing_pad,ADR-062 ADR-065,named landing pads as flight targets with occupancy checks,docs/work/v1.8.0-c17b-station/VERIFICATION.md
block:orientationController,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:station_orientation_controller,ADR-062 ADR-065,station orientation (sky) through the checked station commit,docs/work/v1.8.0-c17b-station/VERIFICATION.md
block:warpMonitor,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:warp_controller,ADR-046 ADR-062 ADR-065,screen over the ADR-046 commands on the classic_controls channel,docs/work/v1.8.0-c17b-station/VERIFICATION.md
block:beacon,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:planetary_beacon,ADR-062 ADR-065,bounded navigation marker per body,docs/work/v1.8.0-c17c-solar-beacon/VERIFICATION.md
block:solararray,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:solar_array,ADR-062 ADR-065,controller-owned multiblock generator,docs/work/v1.8.0-c17c-solar-beacon/VERIFICATION.md
block:solararraypanel,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:solar_array_panel,ADR-062 ADR-065,solar array structure,docs/work/v1.8.0-c17c-solar-beacon/VERIFICATION.md
block:solarGenerator,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:solar_generator,ADR-062 ADR-065,single-block generator,docs/work/v1.8.0-c17c-solar-beacon/VERIFICATION.md
block:solarPanel,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:solar_panel,ADR-062 ADR-065,generator structure part,docs/work/v1.8.0-c17c-solar-beacon/VERIFICATION.md
config:ENERGY.solarGeneratorMultiplier,REDESIGNED,v1.8.0,COMMON energy.solarGeneratorMultiplier,ADR-062 ADR-065,,docs/work/v1.8.0-c17c-solar-beacon/VERIFICATION.md
item:beaconFinder,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:beacon_finder,ADR-062 ADR-065,points to owned beacons; installs in the helmet,docs/work/v1.8.0-c17c-solar-beacon/VERIFICATION.md
asm_rule:GravityHandler.applyGravity(living),REDESIGNED,v1.8.0,planetary gravity on living entities,ADR-062 ADR-066,one transient ARCE modifier on the Forge gravity attribute; no coremod,docs/work/v1.8.0-c18a-life-support/VERIFICATION.md
block:airlock_door,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:airlock_door,ADR-062 ADR-066,sealing door; block half of the small airlock,docs/work/v1.8.0-c18a-life-support/VERIFICATION.md
block:oxygenDetection,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:atmosphere_detector,ADR-062 ADR-066,redstone output by atmosphere state,docs/work/v1.8.0-c18a-life-support/VERIFICATION.md
block:oxygenScrubber,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:co2_scrubber,ADR-062 ADR-066,cartridge-based vent support with a finite fourth debit,docs/work/v1.8.0-c18a-life-support/VERIFICATION.md
block:pipeSealer,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:pipe_seal,ADR-062 ADR-066,sealing block that passes pipes,docs/work/v1.8.0-c18a-life-support/VERIFICATION.md
block:thermiteTorch,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:thermite_torch,ADR-062 ADR-066,light that works in vacuum,docs/work/v1.8.0-c18a-life-support/VERIFICATION.md
config:OXYGEN.scrubberRequiresCartrige,REDESIGNED,v1.8.0,COMMON OXYGEN.scrubberRequiresCartrige,ADR-062 ADR-066,exempts cartridge use only,docs/work/v1.8.0-c18a-life-support/VERIFICATION.md
item:atmAnalyser,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:atmosphere_analyzer,ADR-062 ADR-066,handheld environment readout,docs/work/v1.8.0-c18a-life-support/VERIFICATION.md
item:carbonScrubberCartridge,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:carbon_scrubber_cartridge,ADR-062 ADR-066,CO2 scrubber consumable; the lossless chemical refresh is still open,docs/work/v1.8.0-c18a-life-support/VERIFICATION.md
item:sealDetector,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:seal_detector,ADR-062 ADR-066 ADR-067,reports the seal state of a block,docs/work/v1.8.0-c18a-life-support/VERIFICATION.md
item:smallAirlockDoor,MERGED,-,block:airlock_door,ADR-062 ADR-066,the airlock door block item,
item:thermite,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:thermite,ADR-062 ADR-066,thermite torch ingredient,docs/work/v1.8.0-c18a-life-support/VERIFICATION.md
material:Thermite,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:thermite,ADR-062 ADR-066,legacy dustThermite is the thermite item; tag forge:dusts/thermite,docs/work/v1.8.0-c18a-life-support/VERIFICATION.md
block:suitWorkStation,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:suit_workstation,ADR-062 ADR-066,installs suit upgrades and tanks,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
config:CATEGORY_GENERAL.jetPackForce,REDESIGNED,v1.8.0,COMMON equipment.jetPackForce,ADR-062 ADR-066,,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
config:CATEGORY_GENERAL.lowGravityBoots,REDESIGNED,v1.8.0,COMMON equipment.lowGravityBoots,ADR-062 ADR-066,,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
config:CLIENT.EnableAtmosphericNausea,REDESIGNED,v1.8.0,CLIENT effects.atmosphericNausea,ADR-062 ADR-066,,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
config:OXYGEN.spaceSuitO2Buffer,REDESIGNED,v1.8.0,COMMON OXYGEN.spaceSuitO2Buffer,ADR-025 ADR-062 ADR-066,finite built-in reserve under D1,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
config:OXYGEN.suitTankCapacity,REDESIGNED,v1.8.0,COMMON OXYGEN.suitTankCapacity,ADR-025 ADR-062 ADR-066,tank creation multiplier under D1,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
event:PlanetEventHandler.fogColor(RenderFogEvent),REDESIGNED,v1.8.0,atmosphere-density fog and the anti-fog visor,ADR-062 ADR-066,,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item:basicLaserGun,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:basic_laser_gun,ADR-062 ADR-066,ranged mining through break events and protection,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item:jackHammer,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:jackhammer,ADR-062 ADR-066,fast mining tool,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item:jetPack,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:jetpack,ADR-062 ADR-066,chest attachment using suit hydrogen,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item_variant:itemUpgrade/0,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:hover_upgrade,ADR-062 ADR-066,jetpack mode,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item_variant:itemUpgrade/1,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:flight_speed_upgrade,ADR-062 ADR-066,jetpack speed,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item_variant:itemUpgrade/2,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:bionic_leg_upgrade,ADR-062 ADR-066,movement speed,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item_variant:itemUpgrade/3,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:padded_landing_boots,ADR-062 ADR-066,fall protection,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item_variant:itemUpgrade/4,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:anti_fog_visor,ADR-062 ADR-066,removes atmospheric nausea,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item_variant:itemUpgrade/5,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:earthbright_visor,ADR-062 ADR-066,night vision in space,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item_variant:pressureTank/0,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:low_pressure_tank,ADR-062 ADR-066,suit gas capacity,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item_variant:pressureTank/1,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:pressure_tank,ADR-062 ADR-066,suit gas capacity,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item_variant:pressureTank/2,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:high_pressure_tank,ADR-062 ADR-066,suit gas capacity,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
item_variant:pressureTank/3,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:super_high_pressure_tank,ADR-062 ADR-066,suit gas capacity,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
keybinding:toggleJetpack,REDESIGNED,v1.8.0,jetpack toggle key with a bounded server-checked toggle intent,ADR-062 ADR-066,,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
packet:PacketLaserGun,REDESIGNED,v1.8.0,vanilla item use with a server-side loaded-chunk trace,ADR-062 ADR-066,no custom packet,docs/work/v1.8.0-c18b-equipment/VERIFICATION.md
advancement:beer,IMPLEMENTED,v1.8.0,advancement classic/seat_on_tnt,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:blockpresser,IMPLEMENTED,v1.8.0,advancement classic/block_press,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:crystalline,IMPLEMENTED,v1.8.0,advancement classic/crystallizer,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:dilithium,IMPLEMENTED,v1.8.0,advancement classic/dilithium,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:electrifying,IMPLEMENTED,v1.8.0,advancement classic/electrolysis,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:feeltheheat,IMPLEMENTED,v1.8.0,advancement classic/arc_furnace,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:givingitallshesgot,IMPLEMENTED,v1.8.0,advancement classic/station_warp,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:holographic,REDESIGNED,v1.8.0,advancement classic/formed_multiblock,ADR-062 ADR-066,new trigger: first formed classic multiblock,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:moonlanding,IMPLEMENTED,v1.8.0,advancement classic/moon_landing,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:rollin,IMPLEMENTED,v1.8.0,advancement classic/rolling,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:root,IMPLEMENTED,v1.8.0,advancement classic/root,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:spindoctor,IMPLEMENTED,v1.8.0,advancement classic/lathe,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:suitedup,IMPLEMENTED,v1.8.0,advancement classic/suited_up,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:warp,IMPLEMENTED,v1.8.0,advancement classic/warp_core,ADR-062 ADR-066,rebuilt with modern triggers,docs/work/v1.8.0-c18c-research/VERIFICATION.md
advancement:wenttothemoon,REDESIGNED,v1.8.0,advancement classic/moon_surface_visit,ADR-062 ADR-066,new trigger: first arrival on the Moon's surface,docs/work/v1.8.0-c18c-research/VERIFICATION.md
block:observatory,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:observatory,ADR-051 ADR-062 ADR-066,ground survey creating ADR-051 asteroid instances,docs/work/v1.8.0-c18c-research/VERIFICATION.md
block:planetAnalyser,IMPLEMENTED,v1.8.0,advancedrocketrycommunity:astrobody_data_processor,ADR-062 ADR-066,turns survey data into research,docs/work/v1.8.0-c18c-research/VERIFICATION.md
config:CLIENT.advancedVFX,REDESIGNED,v1.8.0,CLIENT effects.advancedVisuals,ADR-062 ADR-066,,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.atmBarModeX,REDESIGNED,v1.8.0,CLIENT hud.environment.anchorX,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.atmBarModeY,REDESIGNED,v1.8.0,CLIENT hud.environment.anchorY,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.atmBarX,REDESIGNED,v1.8.0,CLIENT hud.environment.x,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.atmBarY,REDESIGNED,v1.8.0,CLIENT hud.environment.y,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.hydrogenBarModeX,REDESIGNED,v1.8.0,CLIENT hud.hydrogen.anchorX,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.hydrogenBarModeY,REDESIGNED,v1.8.0,CLIENT hud.hydrogen.anchorY,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.hydrogenBarX,REDESIGNED,v1.8.0,CLIENT hud.hydrogen.x,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.hydrogenBarY,REDESIGNED,v1.8.0,CLIENT hud.hydrogen.y,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.overworldSkyOverride,REDESIGNED,v1.8.0,CLIENT sky.overworldOverride,ADR-062 ADR-066,defined; the Overworld has no ARCE sky yet so both values show the vanilla sky,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.oxygenBarModeX,REDESIGNED,v1.8.0,CLIENT hud.oxygen.anchorX,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.oxygenBarModeY,REDESIGNED,v1.8.0,CLIENT hud.oxygen.anchorY,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.oxygenBarX,REDESIGNED,v1.8.0,CLIENT hud.oxygen.x,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.oxygenBarY,REDESIGNED,v1.8.0,CLIENT hud.oxygen.y,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.PlanetSkyOverride,REDESIGNED,v1.8.0,CLIENT sky.planetOverride,ADR-062 ADR-066,,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.StationSkyOverride,REDESIGNED,v1.8.0,CLIENT sky.stationOverride,ADR-062 ADR-066,,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.suitPanelModeX,REDESIGNED,v1.8.0,CLIENT hud.suit.anchorX,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.suitPanelModeY,REDESIGNED,v1.8.0,CLIENT hud.suit.anchorY,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.suitPanelX,REDESIGNED,v1.8.0,CLIENT hud.suit.x,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
config:CLIENT.suitPanelY,REDESIGNED,v1.8.0,CLIENT hud.suit.y,ADR-062 ADR-066,client-only positions,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
event:RenderComponents.renderPostSpecial(Post),REDESIGNED,v1.8.0,bounded client render layer for worn jetpack tank and visor mounts,ADR-062 ADR-066,,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
event:RocketEventHandler.onScreenRender(Post),REDESIGNED,v1.8.0,rocket HUD readout from synchronized flight state,ADR-062 ADR-066,,docs/work/v1.8.0-c18d-presentation/VERIFICATION.md
```

## Appendix B: unmerged worktrees

Generated from `git worktree list --porcelain` on 2026-10-13. A worktree is unmerged when its HEAD
is not an ancestor of `codex/v1.8.0-classic-content` at `165bb1f5`. "Uncommitted changes present"
means `git status --porcelain` was not empty; I did not touch those worktrees.

| Worktree under `D:/GitHub/` | Branch | HEAD | Group in section 3 |
|---|---|---|---|
| `arce-v180-checkpoint-compare-20261007` | `codex/v1.8.0-checkpoint-compare-20261007` | `cde841d0` | integrated by another commit |
| `arce-v180-checksum-depth-20261010-24` | `fix/v1.8.0-checksum-relative-depth` | `0cefe86e` | integrated by another commit |
| `arce-v180-checksum-input-review-20261010-18` | `(detached)` | `5049c27a` | integrated by another commit (uncommitted changes present) |
| `arce-v180-checksum-input-revision-20261010-20` | `fix/v1.8.0-checksum-ordering` | `804c4a18` | integrated by another commit |
| `arce-v180-checksum-inputs-20261010` | `fix/v1.8.0-checksum-input-bounds` | `5049c27a` | integrated by another commit |
| `arce-v180-checksum-revision-review-20261010-21` | `(detached)` | `804c4a18` | integrated by another commit |
| `arce-v180-inventory-test-applicability-20261010` | `fix/v1.8.0-inventory-test-applicability` | `5c0dee24` | integrated by another commit |
| `arce-v180-inventory-test-review-20261010` | `(detached)` | `5c0dee24` | integrated by another commit |
| `arce-v180-laser-owner-fixture-20261009` | `fix/v1.8.0-laser-owner-registration-fixture` | `0b02c513` | integrated by another commit |
| `arce-v180-laser-owner-fixture-review-20261009` | `(detached)` | `0b02c513` | integrated by another commit |
| `arce-v180-nc1-computation-20261005` | `codex/v1.8.0-nc1-computation` | `451bddb6` | integrated by another commit |
| `arce-v180-power-carrier-format-20261007` | `codex/v1.8.0-power-carrier-format-20261007` | `7a10f520` | integrated by another commit |
| `arce-v180-propulsion-numbers-20261005` | `codex/v1.8.0-propulsion-numbers` | `9b50488a` | integrated by another commit |
| `arce-v180-seal-spatial-boundary-20261009` | `test/v1.8.0-seal-spatial-boundary` | `770b3134` | integrated by another commit |
| `arce-v180-seal-spatial-review-20261009` | `(detached)` | `fd585a98` | integrated by another commit |
| `arce-v180-seal-spatial-review-corrected-20261009` | `(detached)` | `770b3134` | integrated by another commit |
| `arce-v180-sleep-json-core-20261009-53` | `codex/v1.8.0-sleep-json-core` | `a1ea5b35` | integrated by another commit |
| `arce-v180-strict-phase-diagnostics-20261009` | `fix/v1.8.0-strict-phase-diagnostics` | `945e07c6` | integrated by another commit |
| `arce-v180-strict-phase-review-20261009` | `(detached)` | `945e07c6` | integrated by another commit |
| `arce-v180-tau-holder-observation-20261007` | `codex/v1.8.0-tau-holder-observation-20261007` | `4a9e608d` | integrated by another commit |
| `arce-v180-transfer-wait-history-20261007` | `codex/v1.8.0-transfer-wait-history-20261007` | `f43ecefb` | integrated by another commit |
| `arce-v180-classic-central-20261007` | `codex/v1.8.0-classic-central-20261007` | `5e878778` | pre-kernel hatch work |
| `arce-v180-classic-save-integration-20261007` | `codex/v1.8.0-classic-save-integration-20261007` | `cb0a0019` | pre-kernel hatch work |
| `arce-v180-claude-hatch-binding20-20261008` | `codex/v1.8.0-claude-hatch-binding20-20261008` | `932600d9` | pre-kernel hatch work |
| `arce-v180-guarded-owners-20261007` | `codex/v1.8.0-guarded-owners-20261007` | `4ce6b228` | pre-kernel hatch work |
| `arce-v180-hatch-type-injection-20261007` | `codex/v1.8.0-hatch-type-injection-20261007` | `32243eed` | pre-kernel hatch work |
| `arce-v180-native-storage-20261011-01` | `test/v1.8.0-native-storage-ownership` | `23729a00` | native and IOWorker probes |
| `arce-v180-proto-full-origin-20261011-28` | `test/v1.8.0-native-proto-promotion` | `0fcd67b4` | native and IOWorker probes |
| `arce-v180-claude-audio-clarification06-20261008` | `codex/v1.8.0-claude-audio-clarification06-20261008` | `b000cae5` | Claude draft contracts |
| `arce-v180-claude-audio-lifetime-20261008` | `codex/v1.8.0-claude-audio-lifetime-20261008` | `3a607583` | Claude draft contracts |
| `arce-v180-claude-audio-oracle05-20261008` | `codex/v1.8.0-claude-audio-oracle05-20261008` | `6923dae6` | Claude draft contracts |
| `arce-v180-claude-audio-start04-20261008` | `codex/v1.8.0-claude-audio-start04-20261008` | `e7c1f7b3` | Claude draft contracts |
| `arce-v180-claude-equipment-bounds-20261008` | `codex/v1.8.0-claude-equipment-bounds-20261008` | `9903b3f6` | Claude draft contracts |
| `arce-v180-claude-equipment-clarification06-20261008` | `codex/v1.8.0-claude-equipment-clarification06-20261008` | `2f828a28` | Claude draft contracts |
| `arce-v180-claude-equipment-commit04-20261008` | `codex/v1.8.0-claude-equipment-commit04-20261008` | `0c6bc521` | Claude draft contracts |
| `arce-v180-claude-equipment-witness05-20261008` | `codex/v1.8.0-claude-equipment-witness05-20261008` | `10eb068f` | Claude draft contracts |
| `arce-v180-claude-graph-access-20261008` | `codex/v1.8.0-claude-graph-access-20261008` | `c9b2c001` | Claude draft contracts |
| `arce-v180-claude-graph-access04-20261008` | `codex/v1.8.0-claude-graph-access04-20261008` | `0179645c` | Claude draft contracts |
| `arce-v180-claude-thermite-contract-20261008` | `docs/v1.8.0-claude-thermite-contract-20261008` | `d9d97eed` | Claude draft contracts |
| `arce-v180-claude-thermite-correction02-20261008` | `docs/v1.8.0-claude-thermite-correction02-20261008` | `a8133ec1` | Claude draft contracts |
| `arce-v180-claude-airlock-next19-20261008` | `codex/v1.8.0-claude-airlock-next19-20261008` | `5bbad2b5` | airlock analysis documents |
| `arce-v180-claude-airlock-triage18-20261008` | `codex/v1.8.0-claude-airlock-triage18-20261008` | `c7b99a2d` | airlock analysis documents |
| `arce-v180-bootstrap-fixture-20261010-49` | `fix/v1.8.0-bootstrap-fixture-isolation` | `8062781c` | C19 validator chain |
| `arce-v180-bootstrap-organization-20261010-108` | `fix/v1.8.0-bootstrap-scenario-organization` | `be2abdff` | C19 validator chain |
| `arce-v180-final-input-fixture-20261010-97` | `fix/v1.8.0-final-input-fixture-isolation` | `df91c714` | C19 validator chain |
| `arce-v180-final-review-fixture-20261010-101` | `fix/v1.8.0-final-review-fixture-isolation` | `d34ed03d` | C19 validator chain |
| `arce-v180-g4-malformed-adr-20261010-64` | `fix/v1.8.0-g4-malformed-adr` | `d605be33` | C19 validator chain |
| `arce-v180-git-metadata-20261010-124` | `fix/v1.8.0-git-metadata-cost` | `a8dcd5f7` | C19 validator chain |
| `arce-v180-manual-decomposition-20261010-92` | `fix/v1.8.0-manual-scenario-decomposition` | `17bb40bc` | C19 validator chain |
| `arce-v180-manual-fixture-20261010-86` | `fix/v1.8.0-manual-fixture-isolation` | `f2587b54` | C19 validator chain |
| `arce-v180-object-session-20261010-34` | `fix/v1.8.0-provenance-object-session` | `607c626d` | C19 validator chain |
| `arce-v180-object-session-review-20261010-37` | `(detached)` | `607c626d` | C19 validator chain |
| `arce-v180-packet-observation-20261010-40` | `(detached)` | `607c626d` | C19 validator chain |
| `arce-v180-packet-organization-20261010-119` | `fix/v1.8.0-packet-scenario-organization` | `f87ff4c3` | C19 validator chain |
| `arce-v180-packet-seed-20261010-43` | `fix/v1.8.0-packet-fixture-seed-checkout` | `e28fd8a6` | C19 validator chain |
| `arce-v180-python-suite-20261010-161` | `fix/v1.8.0-python-suite-cost` | `82f82b1f` | C19 validator chain |
| `arce-v180-seed-review-20261010-45` | `(detached)` | `e28fd8a6` | C19 validator chain |
| `arce-v180-seed-terminal-20261010-46` | `(detached)` | `e28fd8a6` | C19 validator chain |
| `arce-v180-source-blob-session-20261010-73` | `fix/v1.8.0-source-blob-session` | `18bde6da` | C19 validator chain |
| `arce-v180-tree-transport-20261010-172` | `fix/v1.8.0-tree-transport` | `82f82b1f` | C19 validator chain (uncommitted changes present) |
| `arce-v180-validation-admission-20261010-139` | `fix/v1.8.0-validation-artifact-admission` | `d7f4e69b` | C19 validator chain |
| `arce-v180-windows-observer-20261010-132` | `fix/v1.8.0-windows-command-observer` | `7f8c9203` | C19 validator chain |
| `arce-v180-w11b2-rocket-integration` | `codex/v1.8.0-w11b2-rocket-integration` | `a31f89a0` | active W task |
| `arce-v180-w27a-bay-design` | `codex/v1.8.0-w27a-bay-design` | `9fffc35b` | active W task |
| `arce-v180-w27a-review` | `(detached)` | `9fffc35b` | active W task |
| `arce-v180-w28-gametest-scheduling` | `codex/v1.8.0-w28-gametest-scheduling` | `9ee037dc` | active W task (uncommitted changes present) |
| `arce-v180-w29-anvil-slot` | `codex/v1.8.0-w29-anvil-slot` | `d2820e6f` | active W task |

67 worktrees, 55 distinct HEADs.
