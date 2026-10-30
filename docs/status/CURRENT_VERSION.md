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
latest_code_commit: a95a8d543 (W215 merged: 55 audited mod classes of the governed relaunch path are initialized on `ServerStartedEvent`, 17.6 to 87.3 ms of startup before logins; the first relaunch's API cost fell from 80 to 118 ms to 46 to 65 ms, and first-flight checkpoint P99 meets 100 ms in all nine timing-only and seven of nine full-observation cases, with new strip 102.789 ms and generated contained 103.517 ms still over; W217 takes W215-R1 and the R-035 wording). Before it, 6b8002e84 (W213 merged, record only: the ground-survey START near the bound really costs about 300 ms idle, which W216 takes). Before it, 4fe1298ee (W212 merged, test-side: the governed relaunch's remaining checkpoint cost is first use in a process, 30 to 38 ms of it class loading; later relaunches meet docs/17, and first-use P99 stays at 99 to 125 ms, which W215 takes). Before it, a4ae5ecaf (W214 merged: a stable tie-break for W210's JFR frame list, with R-040 and ADR-074 notes applied). Before it, cf90b64ef (W210 merged: the journal keeps the exact encoded size of each immutable resident, so C1 admission encodes only the candidate; the near-bound C1 retain fell from 318.235 to 0.518 ms and its allocation from 390 MB to 27 KB, with the sizing moved to first writes, metadata updates and loads; R-040 and ADR-074 wording follows as W214). Before it, bbfb43249 (W209 merged: the escrow-monitor disarm GameTest no longer launches inside the periodic autosave window and fails at once if its launch ends before PREPARED; test-side only). Before it, 3d9894def (W207 merged: governed cold requests answer `DESTINATION_LOADING` while landing terrain is prepared within the warm-up quotas, a cold destination at capacity is refused before any terrain load, and the exact F1 record projection is reused; cold request ticks 458 to 872 ms down to 52 to 70 ms and the refused allocation 210 down to 1.7 ms; R-035 and W90-DESIGN amended; the checkpoint-bearing P99 of 119 to 164 ms stays over docs/17, which W212 takes). Before it, 4cd9ada9d (W208 merged: R-040 near-bound sealed-operation availability measured natively, test-side only; quiet near-bound roots seal, traffic, C1 UNKNOWN and C1 expiry end ROLLBACK_PROVED with every resource conserved, and the first C1 retain stalls the server thread 302 to 485 ms, which W210 takes). Before it, 06f33d02e (W204 merged: N3-C's native refusal, stop and named-pad cuts, and `LandingPadService.claim` keeping an old claim until its transfer's absence is acknowledged; R-035 updated). Before it, c01c37336 (W206 merged: W90's cold-destination warm-up measured through the real launch entry, test-side only; flight work meets docs/17's mean, P95 and P99, but request spikes, a 2,081 ms capacity burst and a 125 to 133 ms checkpoint-bearing P99 exceed it, which W207 takes). Before it, be0570136 (W198 merged: N3-B4, the combined terrain matrix on the integrated I6 path, test-side only; all 89 rows in 357 counted starts, replayed with the oracle strengthened after review). Before it, 00d179351 (W202 merged: NBT bytes counted without buffering and the journal root sized once, in the codec's own limit check; the callback-inclusive P99 168.25 to 63.83 ms and the worst proof tick 242.56 to 63.99 ms in the synthetic fixture; R-035 updated). Before it, 8cc3e7297 (W201 merged: the held-proof fixture's endpoints kept in distinct chunks, tests only; before it c545d7d06 merged W200: I6 admission preflight paced on server ticks and the checked candidate encoding reused, with R-035 updated). Before them, ef191528f (W199 merged: I6 cold preparation bounded to one asynchronous ticket start per tick and the write-back shortcut only with an encodability witness; preparation P99 327 to 41 ms; R-035 updated). Before it, 0cd4f1195 (W196 merged: W27d4, released flight, with R-042 and R-043 updated and ADR-065's W27d4 note). Before it, 406a4538c (W197 merged: the worst-case I6 tick-cost measurement, tests and scripts only; it found preparation and proof-spike overruns, which W199 investigates). Before it, 45a423a2c (W164 merged: N3-B3, the I6 integration, with R-035, R-040, ADR-065 and ADR-022 updated). Before it, f0aa16a2b (W189 merged: two-tick rocket tracking in flight, translated rocket failures, the Moon slow-fall floor). Before it, c0b0daab8 (W195 merged: the paired empty-slot proofs guarded from the autosave, tests only). Before it, 9ede46652 (W194 merged: the discovery fixture's satellite writer hand-offs, tests only). Before it, 85934cf34 (W193 merged: bounded classic-recipe refusals, vanished tags treated as absent, PROPOSED ADR-076). Before it, 14d93687f (W185 merged: W27d3, copies, the absence census and offline login, with R-043 updated). Before it, bdbfb56df (W190 merged: v1.8 build propagation for two native smokes, harness only). Before it, 04f746b27 (W186 merged: loaded sealed discovery finishes a chunk after its own publication, within the existing budget). Before it, 72f6d7b92 (W187 merged: autosave guards for 13 native-capture fixtures, tests only). Before it, 3f1043f24 (W188 merged with W181: JEI tag recipes on dedicated servers, one JEI marker per category and the v1.8 Forge by JEI matrix, which passed all four cells). Before it, 8bf894553 (W182 merged: menu and flight-intent authority, W179-F1 to F3). Before it, 5ac4cbd96 (W184 merged: autosave guards for two native-capture fixtures, tests only). Before it, 61c2f6245 (W170 merged: W27d2, the registered half of bay deployment, with R-043 registered). Before it, 0aec00998 (W179 merged: the menu-authority matrix and the over-limit data-pack reload, tests only. Before it, 7c9e5f7e9 merged W176: the fluid fuel loader's native-save protection, with R-044 registered. Before it, 7f0cffe26 merged W180's remaining canonical identifier bounds, and c48b10313 merged W177: content parsers bound the canonical identifier, the fit check counts it, and W168-R5's guards are in; reviewed APPROVE. Before it, 10c8d6956 merged W178's payload replay fixture, 02d705aa3 merged W175, and 016539ce0 merged W168: the ground survey's reservation proof skips the whole-root encode only when every record's reservation is proven by encoding. Mission reservations now cover the state quarantined from discovery. R-030 updated. Before it, 4b3812db4 merged W128R: W27d1, the bay deployment coordinator with admission off, after two reviews and R130's test fixes; R-042 registered. Before it, 5c4682d64 merged W169's K6 history walk, and 89c01155a merged W166 and W166b: test fixtures only, closing the three causes of the intermittent hosted GameTest failures. The six sealed fixture classes that wait out a deliberate veto or hold and its 200-tick retry now pace their ticks. The retired Moon arrival is discarded by UUID in its held column. The shared pad cleanup waits for entity sections. The boarding fixture avoids the periodic autosave. Before it, 5586e19e6 merged W156 under ADR-075: new Moon arrivals land on the heightmap with ADR-033's support rule, and recorded Moon destinations at y 80 or above keep the legacy recheck. It merged after the upgrade cases, the predecessor smoke, all 29 predecessor-matrix cases and the whole unit suite on the merged source. Before it, ade485a15 merged W152L under R127: test and record only. The refused-window contention is bounded at one extra sealed cycle, and W152K-R1 is closed as a recorded residual. Before it, a03fdd048 merged W129bc: ADR-074 slices 2 and 3, the satellite registry's off-thread writer and the acknowledged barrier callers, after a re-review APPROVE-WITH-CHANGES; R-041 registered, with W129bc-R3 kept as a conservative choice under R125. Before it, e1e7b1799 merged W152K under R123: ineligible holds no longer spend a refused caller's window, and the window's expiry is an absolute server tick. Its review's package rerun never got the lock, so Root ran the three packages on the merged source: 1,608 tests, 0 failures. W152K-R1, which predates W152K, went to W152L. Before it, bb0eda86f merged W129aM: ADR-074 slice 1, the off-thread checked writer core and the rocket transaction journal writer, with N3-B2's sealed journal port waiting on `WRITE_PENDING`; risk entries in aa48d4041; W129aM-R1, a release-test clock issue, follows under R120. Before it, 0e2cd5645 merged W152J: a refused new fail-back announces a RAM-only, bounded demand and a native-window `BUSY` keeps its turn, closing W152H-R1, W152J-R1 and W152J-R2. Under R119, W152J-R4 is an accepted residual for one-shot countdown cancels)
latest_hosted_regression: run 41044358637 at a11b1530 (W213 and W215 merged; W215 initializes the audited relaunch classes at server start) passed on its first attempt; the job took 64.0 of its 75 minutes. Before it, run 41016186534 at ca515b2a (W214 and W212 merged, with the R-040 and ADR-074 notes) passed on its first attempt; the job took 71.1 of its 75 minutes. Before it, run 41005660396 at d54f8742 (W210 merged) passed on its first attempt, including `GroundSurveyScaleTest`; the job took 59.0 of its 75 minutes. Before it, run 40997060383 at c2cd7d14 (W209 merged, test-side) passed on its first attempt, including the escrow-monitor batch `monitor_governed_cutoff`; the job took 68.9 of its 75 minutes. Before it, run 40991022048 at 063a64a6 (W208 and W207 merged, with the R-035 and W90-DESIGN amendments) passed on its first attempt; the job took 57.4 of its 75 minutes. Root cancelled run 40990606457 at 4ec500d3 (W208 alone), which this run covers. Before it, run 40974478388 at 6637e950 (W204 merged, with its records) passed on its first attempt; the job took 70.1 of its 75 minutes. Before it, run 40949512958 at 53329dd7 (W198 merged, with W205's status corrections) passed on its first attempt, including W196's two-claim case; the job took 71.3 of its 75 minutes. Before it, run 40935016605 at 64df7a4f (W202 merged) passed. Before it, run 40904199282 at 30e99dc4 (W200 and W201 merged) passed on its first attempt, including W196's two-claim case `outsideCopyDuringPartialCaptureHoldsOnlyItsClaim`, which closes the integration fault W201 took; the job took 65.5 of its 75 minutes. Before it, run 40869564018 at 7ac2d1be (W196 merged, after W164) failed one GameTest, W196's own R170 case `outsideCopyDuringPartialCaptureHoldsOnlyItsClaim` ("Two-claim proof fixture exceeded its wait at stage 2"), which passed on W196's branch before W164 merged; the job took 73 of its 75 minutes. Run 40878728018 at 124deb40 (W199 merged) failed the same test the same way, so it is an integration fault on the shared head; W201 takes it. Before it, run 40847576719 at 2cf4b56e (W197 merged) passed on its first attempt. Before it, run 40831732029 at f4b865d2 (W189 and W164 merged) passed on its first attempt. Before it, run 40821450330 at 3386c3a6 (W194 and W195 merged) passed on its first attempt. Before it, run 40819986743 at c43bdade (W185 and W193 merged) passed on its first attempt. Before it, run 40798480031 at 05f07d7d (W190 merged, harness only) failed one GameTest, `twoTrulyEmptySlotsReceiveIndependentOwnStoreAbsenceProofs` ("Empty-slot proofs were not retained together"), queued as W195. Before it, run 40796849236 at 499cc84b (W186 merged) passed on its first attempt. Before it, run 40781587260 at 76cbc386 (W187 merged, the first with the autosave guards) passed on its first attempt, with all GameTests and the log check. Before it, run 40766562652 at 597cde73 (W182 merged) failed only `LandingPadGameTests.allFiveRecoveryPhasesAndFourPresenceCutsReleaseClaimsAfterReload` (recovery cut PREPARED/3, NOT_FOUND), the autosave-family case that W187 guards (`44b878d54` on its branch); run 40772206508 at 0b532bf9 (W188 merged) then failed only `ordinaryL2WithoutReceiptCompletesTwoPassengerFlights` (an F1 discard in the same family, which W187 guards in `513d833d7`); run 40781587260 at 76cbc386 (W187 merged) is the first with the guards and decides. Before it, run 40725066567 at 29c0411d (W184 merged) passed on its second attempt, with all GameTests and the log check against its 213 expectations. Its first attempt failed one GameTest, `LandingPadGameTests.allFiveRecoveryPhasesAndFourPresenceCutsReleaseClaimsAfterReload` (a retirement proof abandoned; W187 takes it). Before it, run 40699110375 at a9375878 (W170 merged, W27d2) passed on its first attempt: clean build, unit tests, runData with a clean tree, all 1,500 GameTests and the log check against its 213 expectations (FATAL 0, ERROR 178, WARN 526). Before it, run 40681756481 at 2df03f6f (W179 merged, after W176) passed on its first attempt: clean build, unit tests, runData with a clean tree, all 1,482 GameTests and the log check against its 207 expectations (FATAL 0, ERROR 178, WARN 517). Before it, run 40647807076 at ad8e360c (W176 merged) had no passing attempt. Attempts 1 and 2 were cancelled by the 75-minute job limit after slow, then hung, ForgeGradle setup downloads, before any test result. Attempt 3 failed one GameTest that W176 does not touch: `SealedLazyGameTests.loadedDiscoveryFindsCarriersBeyondTheHintBoundWithoutReplayingNativeCells` did not finish within its 400 ticks (W184 diagnoses it). Before it, run 40645890576 at 4dcbe163 (W180 and W174's design merged) passed on its first attempt. Before it, run 40631995130 at 39675456 (W178 and W177 merged) passed on its first attempt, with all GameTests and the log check green. Before it, run 40594797791 at cf27381a (W175 merged, after W168 and W173) passed on its first attempt, with all GameTests and the log check green. Before it, run 40590963985 at e8f5b2d1 (W168 merged, after W128R) passed on its first attempt: clean build, unit tests, runData with a clean tree, all 1,449 GameTests and the log check against its 189 expectations (FATAL 0, ERROR 135, WARN 510). Before it, run 40574854142 at d8e0217a (W128R merged, W27d1) passed on its first attempt: clean build, unit tests, runData with a clean tree, all 1,449 GameTests and the log check against its 189 expectations (FATAL 0, ERROR 135, WARN 510). Before it, run 40560620171 at d69a8d0d (W166 and W166b, then W169) passed on its first attempt: clean build, unit tests, runData with a clean tree, all 1,433 GameTests and the log check against its 183 expectations (FATAL 0, ERROR 135, WARN 504). Before it, run 40553739287 at dd080faa (W166 and W166b merged) failed one GameTest, a new intermittent: `dismountBetweenCancelledCheckpointAndReplacementRefusesWrite` ("Definite F7 callback lost the first retry backoff"). That fixture's check expects the retry exactly 100 ticks after the tick it observes. The run on W128R's branch at `36d568db0`, which contains dd080faa, passed all 1,449 GameTests and the log check, and W166's two cause-1 fixtures passed in both runs. A task follows if it recurs (QUEUE). Before it, run 40511598164 at 0dc3d988 (W156 merged, after W152K, W129bc and W152L) passed on its first attempt: clean build, unit tests, runData with a clean tree, all 1,433 GameTests and the log check against its 183 expectations (FATAL 0, ERROR 135, WARN 504). Before it, run 40503217683 at 3a8893a0 (W152K and W129bc merged) passed on its first attempt: clean build, unit tests, runData with a clean tree, all 1,420 GameTests and the log check against its 181 expectations (FATAL 0, ERROR 135, WARN 505). Before it, run 40499957973 at 51ecfded (W152K merged) failed only two GameTests, both 400-tick timeouts of W166's cause 1 (unpaced ticks against wall-clock proof I/O): `tickingHoppersDropperAndCapabilityConsumerCannotAccessRetainedContainers` and `tankAndBayStaySealedUnderTickingAutomation`. Clean build, unit tests and runData passed; the two stay open until W166 and W166b merge (QUEUE W166). Before it, run 40450905608 at 9ae10679 (the production code of bb0eda86 plus a governance test fixture) passed on its first attempt: clean build, unit tests, runData with a clean tree, all 1,415 GameTests and the log check against its 181 expectations. Two of the three runs since W152J merged failed, each on different GameTests and none repeated. Run 40442135512 at bb0eda86 (W152J and W129aM merged) failed `sameSeatBoardsOnlyAfterS2AndSurvivesReloadWithoutRevivingOldCopies` ("S2 completed before the retained-rider departure was exercised": the W158 fixture missed the pending B1 capture it waits for). Run 40441960965 at 5796ebc0 (W152J merged) failed four: the sealed hopper test timed out after `BARRIER_VETO` and `DEADLINE` right after the intentional chunk-save failure batch, then a governed border crossing (DESTINATION_LOADING) and two relogin fixtures ("No value present"). The eleven runs before W152J, from 0a3fb94d to 0e78fd4b, all passed. The boarding fixture is the one W161 changed. A read-only diagnosis found three test-side causes, none from W152J; the failures stay open until the W166 fixtures merge (QUEUE W166). Repository governance failed twice in the K6 import check (`3a8893a0`, whose rerun passed, and `6fc6cd9f`). W169 traced it to git 2.56 on GitHub's new runner image, which changed `git log --follow`. Its fix, an independent history walk that reproduces the committed record on git 2.55 and 2.56, merged at `5c4682d64`. W139 stays open until no hosted log shows ARCE_LAUNCH_DISCARD (QUEUE)
ledger_planned_units: 17
asset_review_items: 154
required_gates_approved: none
last_updated: 2026-10-26
```

## Current state

Since the Root handover on 2026-10-11, the Root queue has merged its slices into the shared
branch. Each merge records its hosted run and independent review in
[QUEUE.md](../work/v1.8.0-root-queue/QUEUE.md) and in the per-batch `VERIFICATION.md` files.

- C16b and C16c are complete on the classic kernel. The C16d recipe graph is strict, and the
  all-item check passes.
- C17a propulsion is complete. Complete in C17b: the station controls, pads, force field and
  notices. Complete in C17c: the solar array and the beacon.
- Complete in C18: most C18a devices, all C18b equipment including the laser gun, the C18c
  observatory and processor with all 17 advancements, and the C18d HUD, sounds and client
  settings.
- The D3-A escrow core and W11b2 rocket integration are merged with admission disabled.
  W11b3's loader, binding and disabled admission implementation is merged (W82, W93 and W95/W95F);
  W153 delivered the full linker and monitor (`8dee8680`). N3-A and N3-B1/B2/B3, including
  W98-R1's fix (W151, `8cc3a0ed1`), are merged. N3-B4 (W198, reviewed at `ca25c912e`) merged at `be0570136`;
  its fixture evidence does not qualify a later integrated source. The remaining pre-admission work is
  N3-C's refusal and named-pad native cuts (W204), B3-S6's real-loader representatives, and W90's
  cold, border-crossing and maximum-margin readiness/cost measurements plus a warm-up process restart.
  W176 (`7c9e5f7e9`) ran nine B3-D S1 rows: six passed and three historical-JAR downgrade rows failed;
  the full S1 campaign and downgrade-risk disposition remain open. Accounting-policy reconciliation,
  applicable risk dispositions, the reference-node workload, joint four-hour soak and real V1/V2
  evidence also remain open. The scoped fuel-residual acceptance of 2026-10-26 neither implements
  signed per-fluid accounting nor enables fuel, cargo or bay admission. Details and merge evidence
  are in [QUEUE.md](../work/v1.8.0-root-queue/QUEUE.md); no Required Gate is approved.
  Merged: W83b (arrival footprint readiness), W90a and W90b (governed readiness and the destination
  warm-up), P1-F8 (W92, with W92F's
  follow-ups), and part 1's N2 and P1-F3 (W91 and its review follow-ups W91R, with
  its native timing W91N and W91N2: no missed step, and the three whole-root escrow consumers completed in 0.9
  to 3.0 s under eight-flight traffic).

The hosted regression runs on the exact pushed commit:
- a clean build with the unit tests;
- the artifact audit;
- `runData` twice, with a clean tree;
- every GameTest;
- the GameTest log check.

It does not run:
- the full Python suite;
- the strict repository validator;
- a dedicated server;
- a restart;
- a real client.

What is still open:

- **Ledger.** 17 units are still PLANNED: C17a 4 and C17c 1
  (the five loader variants, which wait for the escrow chain's admission and the bay; W153 delivered
  the monitoring station and the linker), C18a 1 (the oxygen charger, owner decision D4) and C18d 11 (the ten sounds,
  which wait for the owner's listen, and `modelBakeEvent`, which waits for an OBJ import). The
  backlog is in QUEUE.md.
- **Assets.** 154 legacy assets are still under REVIEW (ADR-066 section 7.3).
- **S1.** W11b1 and W44 have native dedicated-server restart evidence as development evidence.
  No S1 campaign has run.
- **P1-N3 implementation and evidence.** N3-A1 (W98, `8f6dd016`) and N3-A2 (W116, `81c987f9`)
  are merged; their W91K reruns retain a stationary FUELED survivor over both restarts. W151
  (`8cc3a0ed1`) implements W98-R1's generation-scoped governed capture exclusion. N3-B1/I7
  (W150, `ef5d60c24`), N3-B2's retained sealed disassembly (W152 through W152L, `ade485a15`)
  and N3-B3's I6/no-T integration (W164, `45a423a2c`) are merged. N3-B4 (W198, reviewed at `ca25c912e`)
  merged at `be0570136`: the strengthened oracle replays 357 retained archives across 89 rows,
  with 178 clean-restart observations; the original harness failure remains a failed attempt.
  This same-Level, already-loaded, schema-1/scalar-fuel fixture is not a maximum modern propulsion
  bank or real-player campaign, and is not evidence for a later integrated JAR. N3-C remains open
  under W204: N2 refusal and named-pad retention/release cuts are not supplied by N3-A or N3-B4.
  W98-R6's offline-crew wait, unsupported-storage R5(c), external-save and playerdata limitations
  remain named residuals. W96 records the original ordinary fail-back crash finding and its design;
  [QUEUE.md](../work/v1.8.0-root-queue/QUEUE.md) records the successor fixes and scoped evidence.
  These implementations do not enable governed admission or close the remaining acceptance work.
- **Intermittent GameTest.** Hosted run 39047343116 at `b01f441e` failed
  `EscrowCoreGameTests.ownTerrainAndEntityStoresConfirmTheExactFrozenFrontier` once with
  `SUPERSEDING_SAVE` (a test-timing race, W36's class); the next run passed. W94 (merged `ec7214c5`)
  lets such proof tests retry only that reason; 30 of 30 local batch runs passed. Its two new
  forced-supersession GameTests then timed out on hosted run 39060903571 at `8c3f5717`, and one of them again
  on runs 39085135243 at `0a3964e7` and 39091589526 at `e80069c6`. W94B (merged `38b437a7`) sets their timeout
  from the retry arithmetic with per-phase budgets; 30 of 30 loaded runs passed. A second natural supersession
  can still exhaust the existing 200-tick tests (residual). The hosted failures from `843ea4dc` to
  `f1223c02` came from delay, not supersession: fixture generation and queued chunk writes inside the
  200 ticks. W111 (merged `ead8d323`) prepares every remote fixture area before the batch and gives the two
  retry tests their own batch; the hosted runs at `d9f031d4`, `2e44edcc` and `dcebe3a8` passed them.
- **S2.** W11b2's crash cuts passed natively as development evidence (the W11b2 records). Of
  W11b3's loader cut families, B3-S1 and B3-S2 (all 21 cuts, two restarts each) have native
  evidence (W97 and W97B, merged `fe25244b`); B3-S3 matched 22 of its 23 cuts (W105, merged `bd1c78b9`, and W107's D2-C4-before, merged `9f6668aa`); W107 also passed 15 B3-S4 and B3-S5 cuts. C1 then passed with R36's gated fixture save (W107B, merged `d6319f9f`), and W107C (merged `f2df941c`) passed E1, E2, R1's saturated floors and all seven R3 cases natively; E4 is an in-process residual. W107C found **F1**, a production defect that blocks fuel admission: a named fill is held at L1 forever because the transfer fence locks the rocket's own landed record while loader reconciliation waits for it to settle, with no persisted reason; W121's design, amended by Root decision R48 after its review, is implemented by W125 (merged with W125F as `88bcebfc`), and D2 (W130, merged as `3bce8373`) retires a landed rocket's transfer record before its first fill, so a rocket refuelled away from its launch site no longer needs that site loaded to launch (F1b); W130F (merged as `6d1230f0`, R59) bounds the retirement's tickets. W125F found **F3**: an assembled rocket filled through escrow keeps `ASSEMBLED`, so its launch returned `INVALID_STATE`; W133 fixed it at the launch boundary (R55; merged as `695f76b0`), and W133F (merged as `49d9629c`) showed natively that an escrow-filled assembled rocket and a landed rocket whose record W130 retired both pass `prepare` and reach a governed COUNTDOWN that survives two restarts. B3-S6 remains.
- **V1 and V2.** No slice has real-GPU or two-client evidence.
- **C19.** W58 audited it (`records/W58.md`). Merged since: the validator chain and the G4 fix
  (C19-A), the portable evidence links (W60), correction files in the Markdown check and the
  R32-03 tests (W61), and the v1.8 import root (W52F). Hosted governance run 38671573901 passed
  end to end at `0e368a10` for the first time on this branch (the full Python suite, the upstream
  and vanilla checks, the art screen and the strict validator). The ledger closure, the final
  `PORTING_MATRIX` and the release evidence remain.
- **Owner decisions.** At `254c89bda`, the owner table in
  [QUEUE.md](../work/v1.8.0-root-queue/QUEUE.md) has 14 decision rows: four explicitly marked
  decided and ten without a decided annotation. These are row counts, not a count of individual
  pending choices: rows group choices, and later delivery records contain additional decisions
  and questions. The table and those records must be read together. The scoped fuel-residual
  acceptance and ADR-074 acceptance are already decided; ADR-075 was also accepted and W156
  merged (`5586e19e6`), so its old “acceptance before code” wording is superseded.
  On 2026-10-30 Root added a fifteenth row: the risk scope to accept, or keep disabled, before each
  admission switch (W203's question 4).
- **Performance at the bounds.** W128's 2026-10-21 synchronous baseline remains historical evidence:
  on the development host, the satellite registry's checkpoint, coalesced flush and autosave at a
  16.7 MB root took about 450 to 900 ms at P95 and P99, and the rocket transaction journal's
  checkpoint at its 64 MiB bound about 4.1 s, against docs/17's 50 and 100 ms. R51's pause has
  ended: ADR-074 was accepted on 2026-10-26, W129aM (`bb0eda86f`) and W129bc (`a03fdd048`)
  merged the off-thread checked writers, and W27d1 through W27d4 are merged. The successor
  [W129a record](../work/v1.8.0-root-queue/records/W129a.md) reports B2 request P99 about 0.22 ms at the bound at `415e361b` (0.33 ms in its first measurement);
  [W129bc](../work/v1.8.0-root-queue/records/W129bc.md) reports B1's worst request/publication
  P99 14.020 ms and B3's development-host measurements in both modes. Request cost is not
  completion latency: at the 64 MiB bound, a sealed journal step can outlast its unchanged
  100-tick proof window and retry. R-040's near-bound completion-latency and availability risk,
  reference-node qualification and joint soak remain open; low request times do not close them.
- **Risks.** The [risk register](../11-RISK-REGISTER.md) controls each risk's scope and disposition;
  R-021 and the applicable W-slice risks are not closed by implementation or CI. The fuel/bay chain
  includes R-035, R-039's receipt store, R-040's checked transaction journal and unreadable-root hold,
  and R-041 through R-044, including registry-writer, bay-protection and loader-save/downgrade risks.
  The owner's 2026-10-26 R-035 decision accepts only the named UNKNOWN settlement-loss accounting,
  measured capacities and P1-F8 crash-window residuals, with W83 and W90 as prerequisites, as
  recorded in [QUEUE.md](../work/v1.8.0-root-queue/QUEUE.md). It is not blanket acceptance of later
  risk additions, closure of R-035, or admission approval. The signed-accounting prerequisite still
  needs scoped reconciliation; R-040's near-bound latency and R-044's three failed downgrade rows
  retain their evidence and outstanding disposition.
- **Earlier intermittent GameTests.** W73 fixed the landing-pad cancellation test, W74 the
  six-host natural-beds test, W76 the first-Moon-landing and spawn-egg tests, and W78 the sealed
  sleep-room test. Hosted runs confirm each fix as they accumulate. The open ones are the escrow
  proof timeout above and the relogin timeout below.
- **Intermittent relogin GameTest (2026-10-25).** The first attempt of hosted run 40152525151 at `32c8ded4` timed out
  `EscrowGovernedSourceFlightGameTests.logoutDuringAscentAndLoginAfterLandingCompletesHandoffAndCleanup` at 600 ticks:
  the passenger logged in after landing and the handoff never completed. Two later fixtures then failed with
  "Terminal cut requires exclusive owner-thread setup", because the first one cleans up only on success. A rerun of
  the same commit passed, and W158F's local full run passed it. W161 (merged at `20f1859e9`) found the cause in
  the fixture: it waited for the dispatcher's remount, which also starts F6, and then dismounted, so it invalidated
  its own terminal capture and paid the 100-tick retry. The fixture now runs the installed F5 executor and dismounts
  in the same callback, and terminal-cut fixtures clean up on failure or timeout. The 600-tick timeout itself was
  not reproduced; this stays open until hosted runs show no recurrence.

No v1.8 Required Gate is approved. Only the owner marks a Gate or the version as passed.
