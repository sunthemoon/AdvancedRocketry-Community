# J21 finding map: every real-client finding, its task, its commit and its current disposition

Status: **DRAFT**, for Root's review. Written by a Claude session for
[TASK W405](../TASK-W405-J21-FINDING-MAP.md) (package K19, Root decision R541) on 2026-11-20, on branch
`codex/v1.8.0-w405` from the shared commit `b957cd5ef`. This record changes no status, accepts no residual and
approves no Gate. It is the input for row J21 of [CAMPAIGN-CHECKLISTS-J](CAMPAIGN-CHECKLISTS-J.md) and for the
recheck batches that package K16 regenerates ([CAMPAIGN-PREP-BREAKDOWN](CAMPAIGN-PREP-BREAKDOWN.md), choice C42).

## 1. Result

The three reports hold 74 findings once compound findings are split into parts with different outcomes.

| Disposition (choice C42) | 2026-10-06 | 2026-10-10 (H12) | 2026-11-04 to 11-07 (H13) | Total |
|---|---|---|---|---|
| Fixed | 13 | 3 | 40 | 56 |
| Known issue | 3 | 0 | 4 | 7 |
| Matches the design | 0 | 0 | 8 | 8 |
| Recheck on C4 (no fix and no accepting decision) | 0 | 2 | 1 | 3 |
| Total | 16 | 5 | 53 | 74 |

- Of the 56 Fixed findings, 38 were seen fixed in a real client after the merge. The other 18 were not seen, or only
  in part, so C42's last disposition applies on top of them.
- C4 therefore owes a recheck to 21 findings: those 18 and the 3 that have no fix and no decision. Section 4 gives
  the steps.
- Section 5 lists 24 informational observations and residuals for Root to accept or reject one by one. None is
  accepted here.
- No numbered finding of the 2026-10-06 report is without a later record. Two unnumbered observations are, and row
  J21 itself does not name that report (section 6).

Row J21 step 9 asks for fresh evidence on C4 for every Fixed finding, whether or not an earlier round saw the fix.
The "recheck owed" column below marks only the findings where C42 requires it; it does not shorten J21.

## 2. Sources and conventions

| Short name | Report (outside Git, read only) | SHA-256 of `REPORT.md` | Build under test |
|---|---|---|---|
| A | `D:/GitHub/ARCE-Task-Evidence/pre-v18-client-20261006/REPORT.md` | `4bcee6e655da2a5cf50ab61bae324cb8c72398224dc6dd40bfbf205377b30aa3` | v1.7 development JAR of `55da6a58` |
| B (H12) | `D:/GitHub/ARCE-Task-Evidence/v18-client-20261010/REPORT.md` | `6b20e44ccf171de2c9372e788c24475e4227eb6ec26b3390e9f7dbf81c0dc85a` | v1.8 development JAR of `d1272416` |
| H13 | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md` (eight rounds) | `c0aefd90b7c44d5c94f94868f18da7041518cbb74d04b8f233dd13e1bb93e2c3` | Rounds 1 to 4: `6f3bd461b`. Round 5: `21757171d`. Round 6: `6b3925c45`. Round 7: `15fa195a1`. Round 8: `b35b8d087` (the tree of `dcc0baa82`) |

- **IDs.** Report A's findings are `A-F1` to `A-F10`, as W87 named them, and `A-S5` is its section 5 (the
  first-connection timeout). Report B's are `B-1` to `B-5`. H13's are the report's own numbers: round 1's findings
  1 to 5 are written `1-1` to `1-5`, and later rounds already use `2-1` and so on. `7-D1` is the side note in
  round 7's step D1.
- **Parts.** A finding whose parts ended differently is split with a letter (`3-7a`), so that each row has exactly
  one disposition.
- **Severity** is the report's own wording, translated. "Information" marks the informational observations.
- **Merge** is the commit on the shared branch that brought the task in, taken from the [QUEUE](../QUEUE.md). All
  cited merge commits are ancestors of `b957cd5ef` (checked with `git merge-base --is-ancestor`).
- **Seen in a client** means a later real-client round reported the fixed behaviour. All such rounds used one
  client, offline profiles, `en_us` and GUI scale 2 unless the cell says otherwise.
- The three reports' offline Forge 47.4.22 profiles are not campaign profiles (J21's preconditions).

## 3. The map

### 3.1 Report A, 2026-10-06 (before v1.8)

| ID | Severity | Finding | Disposition | Task, merge, record and proving check | Seen in a client after the merge | Recheck owed |
|---|---|---|---|---|---|---|
| A-F1 | High | Railgun launch and arrival effects never reach clients. | Fixed | W87, merged `55c2c25e`, [W87](W87.md) (`fb3325ee`). A GameTest of the server's event admission and its refusal of an unknown ID. | Yes: H13 round 4, step E2, END_ROD particles at launch and at arrival. A second, observing client has not seen them. | No |
| A-F2 | High | A rocket landing on the Moon stands in the air at y 80. | Fixed | W156, merged `5586e19e6` under ADR-075 (drafted by W165), [W156](W156.md). 13 GameTests in batch `moon_landing_rule` and the native upgrade cases. | Yes: H13 round 2, step R3 (`BELOW_ROCKET_IS_SOLID` at y 24), and round 5, step P3. | No |
| A-F3 | Medium | The rocket's position reaches clients every 20 ticks, so the trail runs ahead of it. | Fixed | W189 (item B5), merged `f0aa16a2b`, [W189](W189.md). Two-tick tracking in flight; `RocketTrackingGameTests` and six native checks. | No. Later rounds flew rockets but did not judge the tracking. | Yes |
| A-F4 | Low | The accretion disc is invisible from above. | Fixed | W87, merged `55c2c25e` (`b6b19930`). Unit test `AccretionDiscRendererTest`. | Not confirmed: round 4, step E4, saw the ring during generation, but the report does not say from which side. | Yes |
| A-F5 | Medium | Text overflows on the four endgame screens (English, GUI scales 2 and 3). | Fixed | W87, merged `55c2c25e` (`30811345`, `11735e77`), then W293 `605886211`, W310 `3c3b136eb` and W316 `dddab7338`. Unit tests of the fitting helper. | Partly: rounds 7 and 8, step B1, at scale 2 in English. Scale 3 and Chinese were checked only on the warp controller. | Yes, for scale 3 and Chinese |
| A-F6 | Medium | Text overlaps on the satellite terminal and the satellite builder. | Fixed | W87 (`b9cfd466`), then W293 `605886211` and W310 `3c3b136eb`; [W293](W293.md), [W310](W310.md). | Partly: rounds 7 and 8, step B1, at scale 2 in English. The builder's top status line is still cut short, with the full text on hover (residual N13). | Yes, for scale 3 and Chinese |
| A-F7 | Low | Refusal and progress messages are hidden behind an open screen. | Fixed | W87 (`927a5875`), a client-side mirror of the action bar; W311 `a2c076659` and W316 `dddab7338` keep a refusal until the next action. Unit test `ScreenMessageStateTest`. | Partly: rounds 7 and 8 saw the owner's own refusals inside the railgun, elevator and flight screens. A non-owner's `UNAUTHORIZED` and the elevator's "Ride starting" were not seen. | Yes, for those two cases |
| A-F8a | Low | A station at Cygnus X-1 shows a pale blue-grey disc below it. | Fixed | Commit `90716084` (2026-10-14, ADR-066 section 7.1; the QUEUE's open-bug list and [W43](W43.md)). | Yes: round 4, step E4, a black disc with a bright rim and a tilted ring, again after a restart. | No |
| A-F8b | Low | The Earth disc is flat, without shading, with visible polygon edges. | Known issue | [KNOWN-ISSUES v1.8.0](../../../releases/v1.8.0/KNOWN-ISSUES.md), first section: "星球、黑洞/吸积盘、地球盘由代码几何绘制，不使用 45 条旧天空资产；其纹理细节、轮廓、明暗与经典贴图不同。" The owner's verdict is row J12. | Not applicable | No (J12 judges it) |
| A-F9 | For the maintainer | The Moon is fixed at midnight; the Sun never shows. | Known issue | [KNOWN-ISSUES v1.8.0](../../../releases/v1.8.0/KNOWN-ISSUES.md), KI-V180-MOON-01: "v1.8 月球使用固定午夜的维度时间/天空外观规则，不显示随普通日夜周期推进的月球白昼。" Owner decision D12 in [OWNER-DECISIONS-20261031](OWNER-DECISIONS-20261031.md). | Not applicable | No (J13 confirms it) |
| A-F10a | Low | The life-support HUD covers the right column of F3. | Fixed | W87 (`559ae79b`), merged `55c2c25e`. Unit test `ClientReadoutTest`. | No | Yes |
| A-F10b | Low | The laser drill shows the body's raw ID. | Fixed | W87 (`559ae79b`); W310 `3c3b136eb` shares the formatter. | Yes: round 7, step B1, `Orbit body: Cygnus X-1`. | No |
| A-F10c | Low | `data get entity` names the rocket by its menu key. | Fixed | W189 (item B6), merged `f0aa16a2b`. The task's scope names the entity name; its delivery line in the QUEUE does not restate it. | No | Yes |
| A-F10d | Low | The rocket assembler's failure text shows developer wording (`coordinate_space=absolute`). | Fixed | W189 (item B6), merged `f0aa16a2b`: translated failures with bounded operator details. `RocketIssueFeedbackGameTests`. | No | Yes |
| A-F10e | Low | Most screens draw no slot frames in the player inventory. | Fixed | W281 `ed9af76b2` and W293 `605886211`, on H13's findings 1-3, 4-3 and 5-1. | Yes: round 5 (step F3) and round 7 (step B1). | No |
| A-S5 | Known issue | The first connection of a session sometimes times out; a retry in the same process succeeds. | Known issue | [KNOWN-ISSUES v1.0.0](../../../releases/v1.0.0/KNOWN-ISSUES.md): "Initial native connection attempts intermittently time out in retained local development sessions. Same-process Direct Connection succeeds in some recorded retries; the cause is unresolved." The v1.8.0 file does not repeat it (residual N1). | Seen again in B (r01) and in H13 rounds 1 and 8. | No (row A18 counts it) |

### 3.2 Report B, 2026-10-10 (H12)

| ID | Severity | Finding | Disposition | Task, merge, record and proving check | Seen in a client after the merge | Recheck owed |
|---|---|---|---|---|---|---|
| B-1 | Low | The solar generator's English line `Weather transmission: 1000 per mille` overflows. | Fixed | W87 (`f24e3c25`), merged `55c2c25e`. | Yes: H13 round 1, step S3, `1000‰` inside the panel (scale 2). | No |
| B-2 | Low | A jackhammer disabled by the server gives no feedback on a block it cannot finish. | Fixed | The start notice `d95aa9b5` and W87's rate limit `c184c7d1`, merged `55c2c25e`. Unit test `JackhammerFeedbackTest` and a GameTest of START bursts. | No. Round 1 used the jackhammer only while enabled. | Yes |
| B-3 | Information | The oxygen vent uses 20 FE per tick, so a room without steady power loses its air quickly. | Recheck on C4 | No task and no decision. The report calls it a design question. | Not applicable | Yes |
| B-4 | Known, PLANNED | A vanilla torch keeps burning in a vacuum. | Fixed | W33, merged `f2f8aa2c`, [W33](W33.md): unlit torches under ADR-066 section 3.2. | Yes: H13 round 2, an outdoor torch on the Moon became `unlit_torch` at once. | No |
| B-5 | Information | The client configuration reloads only when the file is written in place, not when an editor replaces it by rename. | Recheck on C4 | No task for the client file. W295 found the same watcher limit for the COMMON file and documented it there (finding 4-1); no user text covers the client file. | Not applicable | Yes |

Report B's second half of B-2 (the client tooltip cannot show that the server disabled the tool, because COMMON
configuration is not synchronised) is residual N4.

### 3.3 H13 rounds 1 to 4 (2026-11-04, JAR of `6f3bd461b`)

| ID | Severity | Finding | Disposition | Task, merge, record and proving check | Seen in a client after the merge | Recheck owed |
|---|---|---|---|---|---|---|
| 1-1 | Low | Classic controller screens always read "Waiting for power". | Fixed | W281, merged `ed9af76b2`, [W281](W281.md). Five unit tests and two GameTests. | Yes: round 5 (step F2), round 6 (step R1). | No |
| 1-2 | Medium | The electrolyzer screen's slots disagree with the server. | Fixed | W280, merged `ccce8a51e`, [W280](W280.md). Two GameTests with a loopback synchroniser. | Yes: round 5 (step F5), rounds 6 and 7. | No |
| 1-3 | Low | No inventory slot frames and crowded text on the rolling machine, precision assembler and suit workstation. | Fixed | W281, merged `ed9af76b2`. | Yes: round 5 (step F3), also on a dedicated server. | No |
| 1-4 | Information | Right-clicking a tank with an unrelated item reports a failed exchange. | Fixed | W281, merged `ed9af76b2`: the tank shows its contents. | Yes: round 5 (step F4). | No |
| 1-5a | Information | The emptied canister goes to the first free slot, not the hand. | Fixed | W282, merged `396b8561f`, [W282](W282.md). | Yes: round 7, step F1 (off hand). | No |
| 1-5b | Information | Refilled oxygen sits in the chestplate, not the module; the module's kind reads `low_pressure_tank`. | Matches the design | [W282](W282.md): the active buffer and the shared module family discriminator follow ADR-066 and the O1 design. | Not applicable | No |
| 2-1 | High | A client start failed once in four with `Atmosphere boundary compilation rejected`. | Fixed | W284, merged `f80807008`, [W284](W284.md). 16 registry tests; a packaged server reached `Done`. | Yes: round 6, step A1, 13 clean starts. The fault was intermittent, so the code change is the main proof. | No |
| 2-2 | Medium-high | A fall of about 27 blocks on the Moon is kicked as flying. | Fixed | W285, merged `db92a3a38`, [W285](W285.md). Six GameTests and four unit cases. | Yes: round 6, step M1 (a 39-block fall, 16 jumps). | No |
| 2-3 | Medium | The atmosphere analyzer reads "Non-breathable" in a sealed, supplied room. | Fixed | W286, merged `514455633`, [W286](W286.md). A GameTest that failed before. | Yes: round 7, step F1. | No |
| 2-4 | Medium | Leaving a seat by sneaking keeps the player on the rocket's seat list. | Fixed | W287, merged `77c62ef3b`, [W287](W287.md); W300 `b827a8fde` and W300F `ff555a84f` cover the other ways to leave. Nine and 19 GameTests. | Yes: round 7, step C1. | No |
| 2-5 | Medium-low | A lit torch in a sealed, supplied room is unlit after the world loads again. | Fixed | W288, merged `78623f424`, [W288](W288.md). Three reload GameTests that failed before. | No | Yes |
| 2-6 | Low | The first rocket block must be sneak-placed on the assembler. | Fixed | W289, merged `32535e963`, [W289](W289.md). | Yes: round 7, step C1. | No |
| 2-7 | Low | Interface text: the cut-off station button, "required fuel 0" replies, internal data in chat, the monitor's raw ID, the linker's "Loader" wording, the fluid loader's overflowing status, generic fuel-cell texts. | Fixed | W289, merged `32535e963`; W293 `605886211` fits the station button. | Partly: round 7, step B1, saw the button, the replies, the chat line and the fuel-cell text. The monitor, the linker wording and the fluid loader's full status were not seen. | Yes, for those three |
| 2-8 | Information | The monitor shows `Stale binding` after a round trip. | Fixed | W289, merged `32535e963`: the binding follows the logical rocket. | No (round 7, section 5). | Yes |
| 3-1 | Medium-high | On a 0 % station a survival player off the platform is kicked as flying. | Fixed | W285, merged `db92a3a38`. | Yes: round 6, step S1, inside the station region. Outside the region is 6-1. | No |
| 3-2 | Medium | A rocket returning to the Overworld landed on the sea. | Fixed | W290, merged `624d4de2e`, [W290](W290.md). Seven GameTests, six failing before. | No (round 7, section 5). | Yes |
| 3-3 | Medium-low | `/arcepad select` from another body gives the wrong reason; the station button silently drops the chosen pad. | Fixed | W291, merged `a88dd3207`, [W291](W291.md); W311 `a2c076659` shows the refusal on the console. | Yes: round 7, step F1, and round 8, step A3. | No |
| 3-4 | Low | Every rider sits at the rocket's base, not at the seat block. | Fixed | W292, merged `4c5c53025`, [W292](W292.md). | Yes: round 7, step C1. | No |
| 3-5 | Low | Machine casing swallows every right-click, so controllers must be sneak-placed. | Fixed | W289, merged `32535e963`. | No. Round 7 tried only the assembler. | Yes |
| 3-6 | Low | Truncated text, raw IDs and layout faults on the satellite terminal, builder, bay, flight console and warp controller. | Fixed | W293, merged `605886211`, [W293](W293.md); W310 `3c3b136eb`. 13 unit cases and two menu GameTests. | Partly: rounds 7 and 8, step B1. The `PENDING_DISCOVERY` refresh after a claim was not retested. | Yes, for that refresh |
| 3-7a | Information | `/arce atmosphere status` says `VACUUM` on Venus and Tau Ceti g. | Fixed | W294, merged `e46964be7`, [W294](W294.md) item 1. A unit test and a GameTest. | Yes: round 7, step E1. | No |
| 3-7b | Information | The warp list offers `space`. | Fixed | W308, merged `89f91481e`, [W308](W308.md) (Root decision R356); W309 `4bed1cb0c`; W310 removes it from the star map. | Yes: round 7, step D1, and round 8, step B1. | No |
| 3-7c | Information | The warp list offers `cygnus_x1` in a world that never visited it. | Matches the design | [W294](W294.md) item 2: ADR-057 section 1 gives Cygnus X-1 no discovery requirement. W317 `2493ccbc2` adds the stranded-rocket warning. | Not applicable (the warning: round 8, step B2) | No |
| 3-7d | Information | The star map quotes abstract fuel in mB. | Fixed | W294, merged `e46964be7`, item 3. `RocketStarMapQuoteTest`. | Yes: round 7, step E1. | No |
| 3-8 | Information | Venus without a helmet costs no more health than Mars. | Matches the design | [W294](W294.md) item 4: ADR-034 allows one 2 HP attempt per 20 ticks and does not stack hazards. | Not applicable | No |
| 4-1 | Medium-low | Editing an endgame switch in a running server's COMMON file has no effect, against the operator guide. | Known issue | W295, merged `a1cb9b6fe`, [W295](W295.md). [ENDGAME-OPERATOR-GUIDE](../../../ENDGAME-OPERATOR-GUIDE.md): "To change a switch reliably, stop the server, edit that file, and start the server again. ... Forge's file watcher can miss edits, leaving the previous values loaded." | Not applicable | No (J21 step 6 checks the documented behaviour) |
| 4-2 | Low | The last Received slot hides the energy bar on the railgun and elevator screens. | Fixed | W293, merged `605886211`. | Yes: round 7, step B1 (the railgun gauge). | No |
| 4-3 | Low | Truncated drill and generator status, a tooltip past the screen, a stale "Last stop", no slot frames on endgame screens. | Fixed | W293, merged `605886211`. | Yes: round 7, step B1. | No |
| 4-4 | Information | Elevator bind and ride audit lines always write `owner=-`. | Fixed | W294, merged `e46964be7`, item 6. Three strengthened elevator GameTests. | No. It is a server log line; no later round read it. | Yes |

### 3.4 H13 rounds 5 to 8 (2026-11-04 to 2026-11-07)

| ID | Severity | Finding | Disposition | Task, merge, record and proving check | Seen in a client after the merge | Recheck owed |
|---|---|---|---|---|---|---|
| 5-1 | Low | The electrolyzer screen: the charge slot hangs out of the panel, the status overlaps it, no slot frames. | Fixed | W293, merged `605886211`. | Yes: round 7, step B1. | No |
| 5-2 | Information | A new single-player world shows vanilla's two "experimental settings" warnings. | Known issue | W296, merged `81cb6fb4a`, [W296](W296.md) (Root decision R354, documentation only). [README](../../../../README.md): "every single-player world made with the mod shows two warnings, once each. They are expected." | Not applicable | No |
| 5-3a | Information, art | Port kinds cannot be told apart on a formed structure. | Fixed | W277, merged `f1469f09c`, [W277](W277.md): every port face carries its type icon. | Yes: round 7, step A1. | No |
| 5-3b | Information, art | A formed machine is drawn over by the classic OBJ model. | Matches the design | The formed-only renderers come from W233 (Root decision R235, QUEUE row W233). The owner's art approval of 2026-11-08 covers W275's and W277's textures and does not name the overlay (residual N9). | Not applicable | No |
| 5-4 | Information, art | The nitrogen canister keeps the old icon. | Fixed | W277, merged `f1469f09c`. | Yes: round 7, step A1. | No |
| 5-5 | Information | `device inspect` shows `settled=false` for a laser drill after a restart. | Matches the design | [W294](W294.md) item 7: ADR-055 section 3, recomputed at the next contact and not saved. Root decision R355 keeps the wording. | Not applicable | No |
| 6-1 | Medium | A player adrift outside every station region is returned to the Overworld and kicked in the same tick. | Fixed | W301, merged `a9e1cdcf1`, [W301](W301.md); W303 `34a1e742f` for the elevator and the rejoin pin. Connected-player GameTests. | Yes: round 7, step F1. | No |
| 6-2 | Low | That return puts the player inside the rocket parked at the spawn. | Fixed | W301, merged `a9e1cdcf1`. | Yes: round 7, step F1, two blocks from the rocket. | No |
| 6-3 | Information | A slow-falling descent is also bounded by the airborne allowance. | Known issue | W301, merged `a9e1cdcf1`. [CELESTIAL-DATA-GUIDE](../../../CELESTIAL-DATA-GUIDE.md): "a continuous slow-falling descent longer than the allowance can still be kicked with `Flying is not enabled on this server`." | Not applicable | No |
| 6-4a | Information | On a 0 % platform a standing player breaks blocks five times slower. | Fixed | W294, merged `e46964be7`, item 8 (`dabdf8251`). A GameTest in batch `low_gravity_flight`. | Yes: round 7, step E1. | No |
| 6-4b | Information | That player reports `OnGround 0b`, so jumping does nothing. | Matches the design | [W294](W294.md) item 8: the other ground-dependent mechanisms were checked and are correct; W285 keeps 0 % jumping unchanged. | Not applicable | No |
| 7-1 | Medium-low | A rider whose high seat was released while offline rejoins at seat height and falls into the rocket. | Fixed | W312, W312F and W312F2, merged `dcc0baa82`; [W312](W312.md), [W312F](W312F.md), [W312F2](W312F2.md). GameTests on a real rocket, including a 2 g case. | Yes: round 8, step A1, three cases. | No |
| 7-2 | Medium-low | A rider in a high seat cannot reopen the flight console. | Fixed | W311 and W311F, merged `a2c076659`; [W311](W311.md), [W311F](W311F.md). | Yes: round 8, step A2, ten right-clicks. | No |
| 7-3 | Low | Text under the terminal's arrow buttons, the bay notice on a slot frame, the station button's bracket on its edge. | Fixed | W310 `3c3b136eb` and W316 `dddab7338`; [W310](W310.md), [W316](W316.md). | Yes: round 8, step B1. | No |
| 7-4 | Low | Raw IDs in the black-hole generator, railgun and elevator hovers and the star map. | Fixed | W310, merged `3c3b136eb`. 11 unit tests. | Yes: round 8, step B1. | No |
| 7-5 | Low | A launch refused by a named pad shows the wrong reason on the console for about three seconds. | Fixed | W311, merged `a2c076659`. | Yes: round 8, step A3. | No |
| 7-6a | Information | Endgame refusals vanish on a timer. | Fixed | W316, merged `dddab7338`: a refusal stays until the next action. | Yes: round 8, step B1. | No |
| 7-6b | Information | The status line stays `OK [OK]` beside a refusal. | Matches the design | [W316](W316.md): the status line is the device's state; the notice carries the refusal. | Not applicable | No |
| 7-6c | Information | The elevator refusal `ELEVATOR_RULE` does not name the rule. | Recheck on C4 | Unchanged. [W310](W310.md) says it needs a menu-data change. No decision accepts it (residual N10). | Not applicable | Yes |
| 7-D1 | Side note | The star map lists Space under Earth. | Fixed | W310, merged `3c3b136eb`. | Yes: round 8, step B1. | No |
| 8-1 | Low | A block inside the rocket's box cannot be used from outside. | Known issue | W321, merged `b0b576620`, [W321](W321.md) (Root decision R380 keeps W311's box). [rocket-propulsion](../../../rocket-propulsion.md): "Place loaders, chests and other devices that need hand interaction outside the rocket's rectangular outline, including the full fin span." | Not applicable | No |
| 8-2 | Low | `/arcepad list` on a deleted station answers with the registration refusal. | Fixed | W321, merged `b0b576620`: `list_refused` in both languages, with tests that failed before. | No. No client round followed round 8. | Yes |
| 8-3 | Information | The warp countdown notice does not count down, lingers after a cancel and covers the panel border at scale 3. | Fixed | W321, merged `b0b576620`: the notice takes the outcome and sits inside the panel. It still does not tick each second (residual N11). | No | Yes |
| 8-4 | Information | The railgun hover's `Destination` line shows the body's path name `moon`. | Matches the design | ADR-054 section 4's endpoint label; [W310](W310.md) keeps the prefix as it is. | Not applicable | No |

## 4. Rechecks on C4

Each batch name is the original request batch beside its report (`scripts/batches/` for A and B,
`scripts/batchesN/` for H13 round N). K16 regenerates them for C4's viewports. "J21 step" is a step of row J21 in
[CAMPAIGN-CHECKLISTS-J](CAMPAIGN-CHECKLISTS-J.md).

| ID | Steps (scene, action, expected result) | Original batch | Checklist row that covers it |
|---|---|---|---|
| A-F3 | A fuelled rocket on a pad, a second viewpoint 20 blocks away on the ground. Launch and record ASCENT. The rocket and its trail rise together, without the rocket holding still and jumping. | `r03-rocket-a.json`, `r03-rocket-b.json` | J20 step 1. J21 does not name it. |
| A-F4 | A black-hole generator generating at Cygnus X-1. View the ring from two blocks below and from five blocks above. It is visible from both. | A: `r02-screens-*`. H13: `m62_bh_views_b11` | H06 step 4 inspects the effect, but no row asks for the view from above. |
| A-F5 | Railgun, black-hole generator, laser drill and laser target screens with their longest reason lines, at GUI scale 3 in English and Chinese. Every line stays inside its box; hover shows the full text. | A: `r02-screens-a` to `-e`, `r03-pdrill*`. H13: `eg_bh`, `eg_drill_8`, `eg_rail_a`, `eg_target` (batches7) | H06 step 5; J16 step 7. |
| A-F6 | Satellite terminal and builder at scale 3 in both languages, idle and after a build. No text overlaps a button or a slot. | H13: `b1_terminal`, `b1_builder` (batches7) | J21 step 5 ("full GUI text/IDs"); A13 for core screens. |
| A-F7 | (1) A non-owner clicks Launch on the owner's railgun with its screen open. `UNAUTHORIZED` shows inside the screen. (2) Press Ride on an elevator. "Ride starting" is readable with the screen open. | `r03-perm-a.json` to `-c.json`, `r02-elevator-ride.json` | J16 steps 2 and 5. |
| A-F10a | On the Moon in a full suit, press F3. The life-support HUD hides while the debug screen is open and returns after. | `r04-f3.json` | J14 step 6. |
| A-F10c | Run `data get entity` on an assembled rocket. Its name is not `menu.advancedrocketrycommunity.rocket_flight`. | None (a console command in r02) | J20 step 7 in part; no row names the entity name. |
| A-F10d | Assemble a rocket whose structure is invalid (for example a blocked target). The player's message is translated and has no `coordinate_space=absolute`. | None | J20 step 7. |
| B-2 | Server with `equipment.classicEnabled=false`, client default. Hold the jackhammer on obsidian for 2.5 seconds. The "disabled" notice appears at the start, at most once per 20 ticks; no progress, drop or durability loss. | `r02-d03-jackhammer-disabled.json` | J16 step 4; J21 step 2. |
| B-3 | A sealed room with one vent holding 40,000 FE and no supply. Time the room until it loses air. Record the rate (the report measured 20 FE per tick, about 100 seconds). | `b11-moon-powered.json` | J21 step 2. |
| B-5 | Edit the client configuration (a sky switch) once in place and once by rename. Record which edit the client applies without a restart. | None (edited by hand) | J21 step 2. |
| 2-5 | A lit torch in a sealed, supplied Moon room. Stop the server, start it, join. The torch is still lit; an outdoor torch is still put out. | `c70` to `c73`, `c80`, `c81` (batches2) | J21 step 4 ("torch reload"). |
| 2-7 | (1) Open the monitor bound to a rocket bound for the Moon: the destination is a name, not an ID. (2) Select the monitor with the linker: the text names the monitor. (3) Open the fluid loader: the status fits, with the full text on hover. | `c20`, `c22`, `c60`, `c61` (batches2); `c1_fluid_loader` (batches7) | J21 step 4; J23 and J25 for the loader and linker labels. |
| 2-8 | Bind a monitor to a rocket, fly to the Moon and back, open the monitor. It still shows the rocket, not `Stale binding`. | `c50_return_flight`, `c53` (batches2) | J21 step 4 does not name it; no row does. |
| 3-2 | Occupy the first Overworld landing point near a sea and return a second rocket from the station. It does not land on water. | `f04_fly_u20-to-earth`, `f05_landed_u21-earth`, `f11_visit_u22-earth-rocket-on-water` (batches3) | J21 step 5 ("no unintended sea landing"). |
| 3-5 | Hold a multiblock controller and right-click the top of a machine casing without sneaking. The controller is placed. | `e20b_place_controllers_sneaking` (batches3), without the sneak | J21 step 5 ("casing permits intended placement"). |
| 3-6 | Launch a data satellite's discovery mission from the terminal and claim it. The status line leaves `PENDING_DISCOVERY` at once. | `e02_terminal_press_s06-launch-mars`, `e02_terminal_press_s07-claim-mars` (batches3) | J21 step 5. |
| 4-4 | Bind an elevator pair and ride it. In the server log, the `space_elevator` `bind`, `ride_request` and `ride_commit` lines carry the device owner, not `owner=-`. | `m42_bind`, `m45_ride_l20-ride-down` (batches4) | J21 step 6 ("owner-display cases"). |
| 7-6c | An elevator terminal whose anchor is on another body than the station's orbit. Press Bind. Record the refusal text; today it is "An elevator endpoint rule fails [ELEVATOR_RULE]". | `eg_elevator` (batches7 and batches8) | J21 step 8 does not name it; no row does. |
| 8-2 | Create a station, register a pad, delete the station, then run `/arcepad list <that station>`. The reply says the station is unknown or cannot be visited. | `b3_register_b`, `chat_b3-07-deleted-pad-list` (batches8) | J21 step 8 ("removed-station `/arcepad list` wording"). |
| 8-3 | Quote and confirm a warp, then cancel after three seconds, at GUI scales 2 and 3. The notice shows the cancellation, not "in 10 s", and stays inside the panel. | `warp8_countdown_b2-en2-cd`, `warp8_countdown_b2-en3-cd` (batches8) | J21 step 8 does not name it; F07 covers warp feedback. |

## 5. Informational observations and residuals for Root

Root accepts or rejects each one. None is accepted by default, and none is counted as closed above.

| No. | Source | Observation or residual | State today |
|---|---|---|---|
| N1 | A-S5; B; H13 rounds 1 and 8 | The first-connection timeout is listed for users only in the v1.0.0 known-issues file. | Not in `docs/releases/v1.8.0/KNOWN-ISSUES.md`. Row A18 measures its rate. |
| N2 | B-3 | The vent's 20 FE per tick. | A balance question; no decision. |
| N3 | B-5 | The client configuration reloads only on an in-place write. | No user text for the client file. |
| N4 | B-2 | The client tooltip cannot show that the server disabled the jackhammer. | The report says a code comment calls it a design trade-off; no record decides it. |
| N5 | 1-5b | Refilled oxygen in the suit's buffer; the module kind `low_pressure_tank`. | Matches the design (W282). |
| N6 | 3-7c | Cygnus X-1 is always a warp target. | Matches the design (W294); W317's warning added. |
| N7 | 3-8 | Hazards do not stack on Venus. | Matches the design (ADR-034). |
| N8 | 5-5 | `settled=false` after a restart reads like "unsettled". | Matches the design; R355 keeps the wording. |
| N9 | 5-3b | Formed machines are drawn over by the classic OBJ model. | By W233's design; the owner's art approval does not name it. |
| N10 | 7-6c | `ELEVATOR_RULE` does not name the rule. | Unchanged; needs a menu-data change. |
| N11 | 8-3 | The warp notice still does not tick each second while the screen is open. | W321 left it. |
| N12 | 6-4b, 7-6b, 8-4 | `OnGround 0b` at 0 %; the status line `OK [OK]` beside a refusal; the label `@ moon`. | Each matches its design (W294, W316, ADR-054). |
| N13 | Rounds 7 and 8 | The satellite builder's top status line "Insert cha... [INVALID_COMPONENTS]" is cut short, with hover. | Still present in round 8. |
| N14 | 6-3, 8-1, 4-1, 5-2 | Slow falling is bounded; devices inside the rocket's outline; restart to change COMMON switches; the experimental-world warnings. | Each is documented for users, but none is in the v1.8.0 known-issues file. |
| N15 | Round 8, section 5 | The logout exit was tested only from the top seat with one rider; the side seat and several riders were not. | Untested in a client. |
| N16 | Round 8, section 5 | The seated console was tested with one rocket; a rocket whose origin is 12 to 15 blocks into a section (W311's client fallback) was not built. | Untested in a client; J20's preconditions pin it. |
| N17 | Round 8, sections 3 and 5 | Blocks inside the rocket's box were tried with one chest, three positions, survival only. | Partly tested. |
| N18 | Rounds 7 and 8, section 5 | Chinese and GUI scale 3 were checked only on the warp controller and the flight console. | Untested elsewhere. |
| N19 | Round 7, section 5; round 8, section 5 | W303's elevator arrival, W309's refusal to create a station at Space, W300F's second loaded copy and the Tau Ceti e route warning were not in any round. | Untested in a client. |
| N20 | Round 3, section 5 | Entering a body's dimension by any means, an operator teleport included, counts as a visit and unlocks it. | Read from code; not tested and not decided. |
| N21 | Rounds 1 to 8 | Vanilla's "moved too quickly" and "moved wrongly" warnings after long teleports and seat exits; one `ARCE_FIRST_EVENT_HELD` WARN at each world entry. | No effect seen. |
| N22 | Round 7, section 2 | The dedicated server's console prints language keys for the mod's item names. | The client shows names. |
| N23 | A, section 3 (v1.3 row) | The fuel loader reports only on the action bar. | No record mentions it (section 6). |
| N24 | W312 (R364), W303 (R352) | A rider saved before W312 falls once at login; a guarded player pulled back by the rejoin guard avoids the floating kick only until the guard's 200-tick deadline. | Each is stated in its QUEUE row; neither is listed for users. |

## 6. The 2026-10-06 report and later records

- Every numbered finding has a later record: F1, F4 to F7 and F10 in [W87](W87.md); F2 in [W156](W156.md) and
  ADR-075; F3 and the rocket parts of F10 in the QUEUE's row W189; F8 and F9 in the QUEUE's open-bug list and the
  v1.8.0 known-issues file; section 5's timeout in row A18.
- Two unnumbered observations have none: the fuel loader's action-bar-only text (section 3, the v1.3 row), and
  F10's missing slot frames as a finding of that report. W87's record sets the slot frames aside as "general
  inventory-slot art"; W281 and W293 fixed them later on H13's findings without naming report A.
- Row J21 names only reports B and H13, in both [FINAL-CAMPAIGN-PLAN](FINAL-CAMPAIGN-PLAN.md) and
  [CAMPAIGN-CHECKLISTS-J](CAMPAIGN-CHECKLISTS-J.md). Of report A, only the fixed-midnight Moon appears there (step 9).
  Report A's rechecks rest on rows J12, J13, J14, J16, J20, H06 and A18, and two of them have no row at all: the
  disc seen from above (A-F4) and the rocket's entity name (A-F10c). Root may want J21's preconditions to name
  report A.

## 7. This task

- **Done.** This record, in one commit on `codex/v1.8.0-w405`. Docs only; no other file changed. Nothing was pushed.
- **Read.** The three reports and the batch lists beside them; the QUEUE rows W33, W87, W87F, W156, W165, W189,
  W233, W277 to W330 and its open-bug list; records W87 and W294 in full; the plan's section 7.1; checklist rows
  A18, H06, J12 to J14, J16, J20 and J21; the four user documents quoted above.
- **Commands.** `sha256sum` on the three reports (values in section 2). `git merge-base --is-ancestor <commit> HEAD`
  for every merge commit cited in section 3: all are ancestors. `python -B scripts/validate_repository.py`: the
  result is at the end of this section.
- **Choices.** Compound findings are split only where their parts ended differently. "Seen in a client" follows the
  later rounds' own "status of earlier findings" sections; a fix the report does not mention is counted as not seen.
  A documented limit counts as a Known issue when a user document states it, even where the v1.8.0 known-issues
  file does not (N14).
- **Not done.** The other fix records (W280 to W293, W295 to W321) were read through their QUEUE rows, not in full,
  so a proving test is named only where the QUEUE or the record names it. The screenshots were not opened. The
  checklists other than those listed were searched by heading only, so "no row" in section 4 means none was found
  that way.
- **Risks.** A-F10c rests on W189's scope line, not on a delivery statement. The merge commits come from the QUEUE's
  text; ancestry was checked, the merge content was not.

Validator result: `python -B scripts/validate_repository.py` ended with "Summary: 45 passed, 0 pending, 0 warnings, 0 failed", with this record staged.
