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
latest_code_commit: 254bff121 (W347 merged, R417 and R420: the W159 Temp and Replace fixtures open their fault windows only when no autosave falls inside them; with W344 merged before it at `8eca70809`, the bay exit fixture's loading outside the measured ticks, and W346 with W349 at `6dc594c8e`, B6.5's gated save-boundary control, its native case with the inventory check and the 58th driver family; their branch runs 42823540208, 42824122723 and 42824294274 passed with 1,856, 1,855 and 1,857 GameTests, and their dispatched governance runs passed). Earlier entries are in the implementation log `docs/work/v1.8.0-implementation-log.md`, section "Commit and hosted regression history (moved 2026-11-10)".
latest_hosted_regression: run 42831910740 at ade1538c3 (W344, W346 with W349 and W347 merged, with Root's records; the build of candidate C2) passed on its first attempt: build 11.7 minutes, shards 0 to 3 in 13.6, 17.2, 21.5 and 17.1 minutes, 1,858 GameTests, with 3, 3, 2 and 3 `server-overloaded` entries against the per-shard maximum of 8; governance run 42831910850 there passed. Earlier entries are in the implementation log `docs/work/v1.8.0-implementation-log.md`, section "Commit and hosted regression history (moved 2026-11-10)".
ledger_planned_units: 16
asset_review_items: 95
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
  its fixture evidence does not qualify a later integrated source. W204's N3-C refusal, stop and
  named-pad native cuts and acknowledged-absence fix are merged (`06f33d02e`), as recorded in
  [W204](../work/v1.8.0-root-queue/records/W204.md). W90 now has scoped cold, border-crossing and
  maximum-margin readiness/cost measurements and a fresh request after a clean process restart:
  [W206](../work/v1.8.0-root-queue/records/W206.md) supplies the baseline, and
  [W207](../work/v1.8.0-root-queue/records/W207.md), merged at `3d9894def`, supplies the request-cost
  remedy and closes W206-R1's missing fresh-request check. Pre-admission evidence still includes
  B3-S6's real-loader representatives under W211 and W90's remaining first-use checkpoint budget:
  after W215, the full-observation new-strip and generated-contained cases still exceed 100 ms.
  The detailed evidence boundaries and later follow-ups are retained under the open items below.
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

- **Ledger.** 5 units are still PLANNED: C17a 4 and C17c 1, the five loader variants, which wait for the escrow
  chain's admission and the bay (W153 delivered the monitoring station and the linker). On 2026-11-09 the owner chose
  to target enabling all five in v1.8 after an integrated test on one candidate JAR
  ([OWNER-DECISIONS-20261109](../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261109.md)): W325 (the bay) and
  W326 (the four cargo kinds) ran it on candidate C1, the hosted build of `70dd5d43e`. The rerun on candidate C2,
  the hosted build of `ade1538c3` (JAR SHA-256 `1eea72ac442ea181...`, shared run 42831910740), started on
  2026-11-10 with the 58-family campaign driver (R421). W327's read-only audit of
  R-042, R-043 and R-044 found 0 High, 6 Medium and 5 Low: the bay's NOT_LOADED lock can start on any world whose
  receipt-file presence cannot be proved and then holds every rocket, flight and satellite launch for the session;
  released and REPAIR receipts hold shared pending slots until offline repair; the D6 wait figures are estimates;
  the downgrade rewrite and the 256-coordinate pool are shared by all devices. Root corrected R-035, R-038, R-042,
  R-043 and R-044 (R390), and W328 (R391) fixes the misleading NOT_LOADED warning and the escrow ledger's unknown
  presence. Production admission stays off, and no residual risk is accepted. On 2026-11-04 Root closed the
  ten sounds as IMPLEMENTED after the owner's listen (D5, R305) and the gas charge pad on O1 for every player (option
  A, R307); W174's automatic charging moves to v1.9. `modelBakeEvent` is REDESIGNED since the K1 to K4 imports. The
  backlog is in QUEUE.md.
- **Assets.** No REVIEW or IMPORT file remains: since ADR-062 revision 7 (W274, merged at `f14e2f178`, R304) the asset
  plan has 89 IMPORTED, 365 EXCLUDE and 444 REGENERATE files. The owner accepted all eight groups of W268's proposal
  on 2026-11-04, G7 by choosing the NEW audio
  ([OWNER-DECISIONS-20261104](../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261104.md)). The owner's D7 review
  passed the 30 pending v1.8 provenance records, which W276 (merged at `13c0cd849`, R306) set to APPROVED, and on
  2026-11-08 the owner declined per-record comparison sheets for them (R384). The owner also reported stand-in art:
  W275 (merged at `8e6728547`) replaced it on the rolling machine, the precision assembler, their ports and the gas
  containers, and W277 (merged at `f1469f09c`, R308, R314 and R332) replaced the remaining vanilla stand-in faces, the
  drawer-like side, the identical port sides and the nitrogen canister. On 2026-11-08 the owner accepted both in full
  ([OWNER-DECISIONS-20261108](../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261108.md)), and W324 (R383,
  merged at `70dd5d43e`) set their two records to APPROVED, so no v1.8 provenance record is pending. Excluding a
  file does not clear its authorship; W279
  records whom the owner plans to ask about the AR 16x texture set.
- **S1.** W11b1 and W44 have native dedicated-server restart evidence as development evidence.
  [W176](../work/v1.8.0-root-queue/records/W176.md), merged at `7c9e5f7e9`, also ran nine B3-D
  S1 rows: six passed and three historical-JAR downgrade rows failed. Those downgrade rows rewrote
  level.dat/level.dat_old metadata before journal refusal; preserved terrain, entities and journals
  do not turn them into passes. The full S1 campaign, current-candidate integrated evidence and
  R-044's downgrade-risk disposition remain open; the partial rows are not campaign completion.
- **P1-N3 implementation and evidence.** N3-A1 (W98, `8f6dd016`) and N3-A2 (W116, `81c987f9`)
  are merged; their W91K reruns retain a stationary FUELED survivor over both restarts. W151
  (`8cc3a0ed1`) implements W98-R1's generation-scoped governed capture exclusion. N3-B1/I7
  (W150, `ef5d60c24`), N3-B2's retained sealed disassembly (W152 through W152L, `ade485a15`)
  and N3-B3's I6/no-T integration (W164, `45a423a2c`) are merged. N3-B4 (W198, reviewed at `ca25c912e`)
  merged at `be0570136`: the strengthened oracle replays 357 retained archives across 89 rows,
  with 178 clean-restart observations; the original harness failure remains a failed attempt.
  This same-Level, already-loaded, schema-1/scalar-fuel fixture is not a maximum modern propulsion
  bank or real-player campaign, and is not evidence for a later integrated JAR. N3-C's assigned
  shared-chain cuts are now supplied by [W204](../work/v1.8.0-root-queue/records/W204.md), merged
  at `06f33d02e`: N2_REFUSED and N2_STOPPED preserve source, T, pad and live membership, and the
  final N2_PAD_MOVED and N2_PAD_UNKNOWN cuts each pass with two restarts. The original MOVED
  finding remains historical: a contender reused a pad before the old transfer's absence was
  acknowledged. The merged fix keeps the old claim until absence is proved by the acknowledged
  journal view or, before the session's first write, the validated load; failed or UNKNOWN writes
  cannot use that load fallback. The named-pad evidence proves one request-driven old-claim
  reconciliation, not an automatic index sweep or replay of a lost return-completion callback.
  The failed UNKNOWN fixture start remains failed. This uncrewed assembled-rocket/persisted-pad
  fixture is not a player-built physical pad, station UI, maximum-resource or real-client campaign,
  and does not replace W211's missing real-loader N2 representative or later integrated-JAR checks.
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
  Separately, [W209](../work/v1.8.0-root-queue/records/W209.md), merged at `bbfb43249`, corrects
  the monitor disarm fixture: periodic autosave could discard F1 before PREPARED, after which
  the test waited to its 2,400-tick deadline. The shared fixture now avoids that launch window
  and fails immediately if the request ends before PREPARED, keeping all original assertions
  and timeouts. The autosave-boundary reproduction went from 0 of 3 to 10 of 10 passes; shared
  hosted run 40997060383 at `c2cd7d14` also passed the batch, as recorded in
  [QUEUE.md](../work/v1.8.0-root-queue/QUEUE.md). This is a test-side correction, not a repair
  of an accepted flight's outcome. It does not prevent arbitrary explicit saves or IO stalls,
  close the earlier proof/relogin residuals, or erase their failed historical runs.
- **S2.** W11b2's crash cuts passed natively as development evidence (the W11b2 records). Of
  W11b3's loader cut families, B3-S1 and B3-S2 (all 21 cuts, two restarts each) have native
  evidence (W97 and W97B, merged `fe25244b`); B3-S3 matched 22 of its 23 cuts (W105, merged `bd1c78b9`, and W107's D2-C4-before, merged `9f6668aa`); W107 also passed 15 B3-S4 and B3-S5 cuts. C1 then passed with R36's gated fixture save (W107B, merged `d6319f9f`), and W107C (merged `f2df941c`) passed E1, E2, R1's saturated floors and all seven R3 cases natively; E4 is an in-process residual. W107C found **F1**, a production defect that blocked fuel admission before the fixes described below: a named fill is held at L1 forever because the transfer fence locks the rocket's own landed record while loader reconciliation waits for it to settle, with no persisted reason; W121's design, amended by Root decision R48 after its review, is implemented by W125 (merged with W125F as `88bcebfc`), and D2 (W130, merged as `3bce8373`) retires a landed rocket's transfer record before its first fill, so a rocket refuelled away from its launch site no longer needs that site loaded to launch (F1b); W130F (merged as `6d1230f0`, R59) bounds the retirement's tickets. W125F found **F3**: an assembled rocket filled through escrow keeps `ASSEMBLED`, so its launch returned `INVALID_STATE`; W133 fixed it at the launch boundary (R55; merged as `695f76b0`), and W133F (merged as `49d9629c`) showed natively that an escrow-filled assembled rocket and a landed rocket whose record W130 retired both pass `prepare` and reach a governed COUNTDOWN that survives two restarts. B3-S6 is merged as evidence under
  [W211](../work/v1.8.0-root-queue/TASK-W211-B3S6-LOADER-REUSED-FAMILIES.md) at `4ea26eb6e` (with `2afe2d968`),
  after an interim and a final independent review ([TASK-W211-REVIEW](../work/v1.8.0-root-queue/TASK-W211-REVIEW.md),
  APPROVE-WITH-CHANGES) and hosted run 41141894835. Its record `docs/work/v1.8.0-root-queue/records/W211.md`
  maps every S7 representative: A (E3, unloaded E5, three R2, seven G3, G1 veto and saturation), B (O1 to O4, O3J,
  two-loader chains, veto, abandon and normalization) and C (governed F1 to F8, sticky reservation and successor
  refill) pass with two restarts each; held E5 and D2-F0 map to earlier real-loader evidence. Across 356 native starts,
  88 attempts passed and 31 failed; every failed attempt stays failed, and all 264 passed-attempt images replay through
  the corrected oracle. The eleven ordinary F9, P1-F8 and N2 forms are out of reach for loader-funded rockets, because
  the product routes a rocket with a ledger binding only to the governed launch (`RocketGovernedAdmission`, pinned by
  three routing tests); they are not passes. Their governed analogues were **NOT_COVERED** at W211's merge and are now covered: a failed or vetoed governed
  destination publication (W220, since covered by two rows merged at `00b158482`) and governed named-pad crash windows (W221, since covered by seven rows merged at `8260b2b17`). Residuals: the first BI O-veto attempt
  stays FAILED, its sixteen extra guarded saves now explained by W222 (`8d3eceb8c`: vanilla's incremental save sweep
  retrying the guarded chunk, each refused correctly) and its oracle corrected,
  and E4's in-process failures stay unobservable. R198 and R200 added two release-gated test seams in production source
  (three O3J allowlist names; one journal-policy link). R202's three persisted-phase rows use fixture-owned stores and
  claim nothing about when production writes those phases. W176's three failed downgrade rows retain their recorded status.
- **V1 and V2.** No slice has real-GPU or two-client evidence. W269 (merged `f85573938`) provides the campaign-only
  gate the bay exit's two-client V2 session needs (W254-V2-CHECKLIST section 0.1); it runs no campaign. On 2026-11-04
  the owner suspended the final campaign: for now a complete step-by-step test in single-player is enough, and
  multiplayer and performance (the two-client V2 sessions, the docs/17 reference node and the joint four-hour soak)
  come last. Nothing is marked passed or waived by this. At the owner's request Root runs that single-player test with
  the real-client scripts on drive D: (R309), on the hosted JAR of run 41630143345. The scripted single-player test so
  far: round 1 (setup, materials, power, fluids, nine processing machines, equipment) passed all six steps; round 2
  (restart persistence, life support, a rocket to the Moon and back, the monitoring station) passed with findings,
  among them an intermittent client start failure; round 3 (satellites and discovery, the observatory, the station and
  its controls, an interstellar warp, Mars, Venus, Tau Ceti f and g, a restart) passed with findings, among them a
  kick on a zero-gravity station and a landing on the sea; round 4 (the laser drill in both modes, the railgun, the
  space elevator, the black-hole generator at Cygnus X-1, the gravity field, force field, beacon and microwave
  receiver, two restarts) passed with findings, among them a config switch that did not apply until a restart; round
  5, on the JAR of `21757171d`, ran an integrated single-player world (gas pad, a Moon round trip, Save and Quit) and
  upgraded round 4's world, and confirmed round 1's findings 1 to 4 fixed by W280 and W281; round 6, on the JAR of
  `6b3925c45`, confirmed W284 (13 clean starts) and W285 (no kick after Moon falls or off a 0 % station platform) and
  found that a 0 g player returned to the Overworld from outside every station region is kicked on arrival. Findings
  are queued as W280 to W282, W284 to W296 and W301; the report is
  `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`.
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
  On 2026-11-04 the owner answered the brief of 2026-11-03
  ([OWNER-DECISIONS-20261104](../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261104.md)): D5 and D7 passed, all
  eight W268 groups are accepted, the gas charge pad's automatic charging moves to v1.9 (option A), sleeping off-world
  keeps the existing respawn point, and the campaign is suspended in favour of a single-player step-by-step test. The
  risk decisions (W203-Q4 and D14) remain open.
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
  100-tick proof window and retry. R-040's previously unmeasured availability is now partly
  characterized by [W208](../work/v1.8.0-root-queue/records/W208.md), merged at `4cd9ada9d`:
  quiet small/near-bound roots reached SEALED in 3.063/12.056 seconds, while near-bound bounded
  traffic, C1 UNKNOWN and C1 expiry reached ROLLBACK_PROVED in 34.497/22.195/19.309 seconds.
  Those are safe rollbacks, not successful disassemblies. C3 UNKNOWN/expiry controls reached
  SEALED in 25.155/22.961 seconds but started small and grew at election, so they are not
  end-to-end near-bound samples. The interrupted drain had no completion callback and did not
  drain the final target; recovery restored D and removed U. Its workload P99 was 315.868 ms,
  still over 100 ms. W208's corrected payload auditor replays all 27 observations, and its
  C1/C3 expiry regression checks release at entered+120; these close W208-R1/R3, not availability.
  [W210](../work/v1.8.0-root-queue/records/W210.md), merged at `cf90b64ef`, addresses W208-R2's
  synchronous C1 sizing by keeping exact identity-bound resident sizes and encoding the candidate.
  Near-bound first retain fell from 318.235 to 0.518 ms, with allocation 390,304,672 to 26,864 bytes.
  Cost moves to first writes, loads and metadata updates rather than disappearing; the small-root
  retain sample increased from 0.826 to 1.312 ms. W210 reran only quiet small/near roots and two
  recoveries each: completion was 3.074/3.082 seconds and 11.915/11.850 seconds before/after,
  while near-root IO remained about three seconds per write, three writes per operation.
  Setup-inclusive small-root P99 131.037 ms and near-root maximum 5,463.814 ms remain recorded.
  W210 did not rerun interrupted, traffic, UNKNOWN or expiry native cases; it does not close
  W208's interrupted P99, sustained storage-fault, stuck-IO, watchdog or power-loss gaps.
  [W214](../work/v1.8.0-root-queue/records/W214.md), merged at `a4ae5ecaf`, fixes the diagnostic
  top-frame tie-break; its applied R-040/ADR-074 notes distinguish off-thread checked-file writing
  from synchronous admission/write/load sizing and retain these limits and historical results.
  Persistent U can still be held without a time bound; low request cost is not a completion guarantee.

  W90's governed destination work is separate from the earlier I6 benchmarks.
  [W206](../work/v1.8.0-root-queue/records/W206.md), merged at `c01c37336`, measured cold,
  border-crossing and maximum-margin product entries but found request and checkpoint overruns.
  [W207](../work/v1.8.0-root-queue/records/W207.md), merged at `3d9894def`, moves cold landing
  terrain preparation into the existing bounded warm-up quotas, refuses a cold request at capacity
  before terrain loading and reuses the exact F1 projection. New-terrain request ticks fell from
  458-872 to 52-70 ms and the refused ninth allocation from about 210 to 1.7 ms; its fresh request
  after clean process restart closes W206-R1, not crash-durability or full reference qualification.
  W207's 119-164 ms checkpoint-bearing P99 remains its historical failure, not a current all-pass.
  [W212](../work/v1.8.0-root-queue/records/W212.md), merged at `4fe1298ee`, attributes the remaining
  excess to first use in a process, including about 30-38 ms of ClassLoad intervals; recurring
  serialization work was small and no production cache remedy was made. Its first-use checkpoint
  P99 remained about 99-125 ms, while all 48 later cohorts met the numeric budgets.
  [W215](../work/v1.8.0-root-queue/records/W215.md), merged at `a95a8d543`, initializes 55 audited
  mod classes on ServerStarted before logins, without executing a launch. The hook runs in every
  world, including admission-disabled worlds; a missing class deliberately aborts startup.
  Measured startup cost is 17.6-87.3 ms and corrected Metaspace growth about 335 KB, not a retained-heap
  guarantee. First relaunch API cost fell from 80.159-117.557 to 46.279-65.358 ms. All nine timing-only
  first-flight checkpoint cases and seven of nine full-observation cases meet 100 ms; new strip
  102.789 ms and generated contained 103.517 ms still fail. Each first checkpoint cohort has only
  23 ticks, so nearest-rank P99 equals its maximum; observation cost is not subtracted and the
  timing-only results do not erase the failures. [W217](../work/v1.8.0-root-queue/records/W217.md),
  merged at `c3406e85d`, closes W215-R1 with a bounded bytecode-derived dependency regression and
  applied R-035 wording. It detects a newly reachable unlisted class or a listed class no longer
  used, requires initializer re-audit, and does not claim that static reachability proves first-use cost.

  [W213](../work/v1.8.0-root-queue/records/W213.md), merged at `6b8002e84`, found that the ground-survey
  START spike was real whole-root encoding/compression/readback work, not a faulty timer. Its hosted
  570.710 ms median over the unchanged 500 ms limit remains a historical failed result, even though
  the rerun passed. [W216](../work/v1.8.0-root-queue/records/W216.md), merged at `15f9e052e`, retains
  the reservation proof when sufficient, otherwise uses exact known resident sizes and encodes only
  changed candidates, with the original whole-root fallback when any resident measurement is UNKNOWN.
  In its near-bound START fixture the idle wall median fell from 286.2 to 1.4 ms, with over 99.9 percent
  less allocation and unchanged limits. Eager per-record measurements move cost to writes/restores:
  R-030 records a diagnostic restore increase from about 171 to 296 ms and about 339 to 584 MB allocated;
  that comparison disables eager sizing in the same current registry, not in an old production binary.
  [W218](../work/v1.8.0-root-queue/records/W218.md), merged at `be87c6386`, closes W216-R1/R2 with
  applied R-030 wording and an ordinary-survey/analysis regression using real known resident sizing.
  Its isolated medians are 131.3/81.0 microseconds under the unchanged 1 ms limit; the original scale
  test is unchanged. The fallback's cost/frequency, per-tick concurrency, native startup, reference
  load and long-soak effects remain unqualified. These Windows development-host observations do not
  establish the Debian reference-node workload, joint four-hour soak or any Required Gate.
- **Risks.** The [risk register](../11-RISK-REGISTER.md) controls each risk's scope and disposition;
  R-021 and the applicable W-slice risks are not closed by implementation or CI. The fuel/bay chain
  includes R-035, R-039's receipt store, R-040's checked transaction journal and unreadable-root hold,
  and R-041 through R-044, including registry-writer, bay-protection and loader-save/downgrade risks.
  The owner's 2026-10-26 R-035 decision accepts only the named UNKNOWN settlement-loss accounting,
  measured capacities and P1-F8 crash-window residuals, with W83 and W90 as prerequisites, as
  recorded in [QUEUE.md](../work/v1.8.0-root-queue/QUEUE.md). It is not blanket acceptance of later
  risk additions, closure of R-035, or admission approval. The signed-accounting prerequisite still
  needs scoped reconciliation; neither W204's shared-chain cuts nor W207/W215's narrower cost
  remedies authorize admission. R-040's limited W208/W210 measurements narrow the formerly
  unmeasured near-bound gap but leave interrupted, traffic, UNKNOWN/expiry, persistent storage-fault
  and shutdown availability plus owner disposition open. R-030 retains the unknown-size whole-root
  fallback, eager restore costs and concurrency/reference/native/soak limits after W216/W218.
  R-035 retains W215's unconditional startup and fatal missing-class policy and its two failing
  full-observation first-use cases after W217. R-044's three failed downgrade rows remain failed.
  The owner's separate per-interaction acceptance or continued-disable decision is still pending
  for fuel, each cargo kind and the bay; the earlier scoped acceptance is not reopened or broadened.
  W27e's three decisions are made: the neutral-source limit (D1a), a safe passenger exit (D1b, Q1 = A;
  W230-S1, S2 (W242, `b1c5a6ba5`) and the S3 native cuts (W254, W257 and W262, `bd0b3d080`) merged) and 16
  pending operations per owner (D1c-N, R244, merged with W238); W27e still needs its integrated B1 to B18
  evidence on one pinned JAR and the passenger exit's two-client V2 session. The full S1 campaign, current-source integration, reference workload, joint four-hour
  soak and real V1/V2 remain open; no Required Gate is approved by these implementations or tests.
- **Earlier intermittent GameTests.** W73 fixed the landing-pad cancellation test, W74 the
  six-host natural-beds test, W76 the first-Moon-landing and spawn-egg tests, and W78 the sealed
  sleep-room test. Hosted runs confirm each fix as they accumulate. The open ones are the escrow
  proof timeout above and the relogin, planetary flight, bay hold, two adapter, three 2026-11-04, five 2026-11-05 and one 2026-11-06 entries below.
- **Intermittent relogin GameTest (2026-10-25).** The first attempt of hosted run 40152525151 at `32c8ded4` timed out
  `EscrowGovernedSourceFlightGameTests.logoutDuringAscentAndLoginAfterLandingCompletesHandoffAndCleanup` at 600 ticks:
  the passenger logged in after landing and the handoff never completed. Two later fixtures then failed with
  "Terminal cut requires exclusive owner-thread setup", because the first one cleans up only on success. A rerun of
  the same commit passed, and W158F's local full run passed it. W161 (merged at `20f1859e9`) found the cause in
  the fixture: it waited for the dispatcher's remount, which also starts F6, and then dismounted, so it invalidated
  its own terminal capture and paid the 100-tick retry. The fixture now runs the installed F5 executor and dismounts
  in the same callback, and terminal-cut fixtures clean up on failure or timeout. The 600-tick timeout itself was
  not reproduced; this stays open until hosted runs show no recurrence.
- **Intermittent planetary flight GameTest (2026-11-03).** The first attempt of hosted run 41559401551 at `a50576dca`
  (W269's branch) failed `PlanetaryWorldGameTests.earthMarsVenusEarthKeepsOneRocketAndExactFuelDebits`: leg 0's
  transfer retirement waited about nine seconds on a natural superseding save inside leg 1's fixed 270-tick landing
  budget, so leg 1 timed out six ticks into its ascent. The second attempt passed. W270 (R300, merged at `53c304929`)
  starts each leg's landing budget at the new transfer's preparation and bounds the preparation wait separately; its
  forced-supersession GameTest fails the same way against the old fixture. This stays open until hosted runs show no
  recurrence.
- **Bay hold fixture retry (2026-11-03).** W270's local full run passed all 1,656 GameTests, but the strict log check
  failed on one `ARCE_TRANSFER_RETIREMENT_RETRY site=RECOVERED_SOURCE reason=DEADLINE` in `bay_deployment_hook81`
  (`BayDeploymentHoldGameTests`): the fixture did not pace its waiting ticks, so its ordinary recovery missed the
  100-tick partial-census deadline about 0.3 seconds after the survivor became active, then proved on attempt 2.
  Hosted runs have not logged it. W271 (R301, merged at `48643e8db`) paces that fixture and
  `BayDeploymentGameTests.Checkpoint`, whose recovery retried the same way under a one-second readback stall; with the
  stall both retries are gone. This stays open until hosted runs show no recurrence.
- **Adapter component GameTest (2026-11-03).**
  `RocketComponentGameTests.externalComponentsDriveActualAssemblyAndZeroFuelDisassembly` timed out with `Original
  component block not yet published` in two local full runs (W248 on 2026-11-02, W271 on 2026-11-03); no hosted
  failure is recorded. Its pending disassembly was rejected at the designed 200-tick request lifetime together with
  three others in the batch, which resubmitted, but the fixture stopped calling the submission's `advance` after the
  first admission and never saw the rejection. W272 (R302, merged at `2fc77c3c9`) keeps both affected fixtures
  advancing while they wait and adds two GameTests that force the rejection; this stays open until local and hosted
  runs show no recurrence.
- **Adapter capture-throw GameTest stray button (2026-11-03).** In three of about 25 loaded focused runs of the
  adapter batches during W272, `AdapterFaultGameTests.throwingCapturePreservesSource` failed with `Duplicate inventory
  drops found: [1 stone_button ...]`: the same item, at the same x and age every time, landed just inside the test's
  region. W273 (R303, merged at `0c0d093d7`) found the source: the GameTest framework's start button, which
  `GameTestInfo.spawnStructure` places on a command block outside the template, is destroyed by terrain water while
  still supported, and usually settles just outside the region; no rocket path is involved. The fixture now removes
  its own start control without drops before rocket operations, with the region and the duplicate-drop assertion
  unchanged, and 20 loaded repeats passed. This stays open until loaded and hosted runs show no recurrence.
- **Rocket reconnect scheduling GameTest (2026-11-04).**
  `RocketReconnectSchedulingGameTests.reconnectWaitsForItsQueuedSpawnWithoutRecoveringTheSource` failed with `Own
  spawn did not queue` in shard 2 of both attempts of W274's branch run 41776065592. That commit had the shared code
  of `0c0d093d7`, which passed in W275's branch run and the earlier shared runs. W283 (merged at `9b28f6200`) found
  the cause in the fixture: it waited only for the destination origin while the product waits for every
  occupancy-census column. This stays open until runs show no recurrence.
- **Escrow live-return dismount GameTest (2026-11-04).**
  `EscrowGovernedLiveReturnGameTests.dismountBetweenCancelledCheckpointAndReplacementRefusesWrite` failed with
  `Definite F7 callback lost the first retry backoff` in the second attempt of the same run, and again in the first
  attempt of W280's branch run 41811465266 (client menu change only), whose second attempt passed. It failed again in
  both attempts of W285's branch run 41843054472. W283 (merged at `9b28f6200`) found the cause in the observer: an
  earlier F6 proof retry legitimately leaves the history at two failures, and the observer expected exactly one; it
  now expects one advance from the prior count. This stays open until runs show no recurrence.
- **Classic scale GameTest under local load (2026-11-04).**
  `ClassicScaleGameTests.sixteenOfEveryKindStayIdleThenHalfResumeAfterRealUnload` failed once in W281's local full run
  with `Scale stage did not finish: 7` (eight lathes and four cutting machines still RUNNING) under 24
  `server-overloaded` warnings; it passed in W281's hosted branch run. It failed again in W280's loaded local run,
  together with `ArcCuttingGameTests.sawmillOffRefusesNewWoodJobsAndPausesAndResumesAnActiveJob` (`Batch did not
  complete exactly once`). W283 could not reproduce either in 23 loaded runs each, so neither is fixed; both stay open
  until loaded runs show no recurrence.
- **Bay deployment matrix GameTest (2026-11-05).**
  `BayDeploymentMatrixGameTests.aRefusedCheckpointRequestRejectsEveryBayWithNoReceipt` failed with `The published
  destination is missing from the loaded fixture` in shard 3 of the second attempt of run 41870324437 (W285 on top of
  `e8bbbc7f7`), a change that does not touch bays. W298 (R336, merged at `9911fdaab`) found the cause in the observer:
  the transfer service records `DESTINATION_SPAWNED` before its acknowledgement adds the destination entity, and the
  observer treated the recorded ID as a published entity; a sibling test forces that window. This stays open until
  runs show no recurrence.
- **Adapter registration GameTest (2026-11-05).**
  `AdapterRegistrationGameTests.externalAdapterRetriesRejectedPendingDisassembly` failed with `Disassembly dropped
  duplicate inventory items` in shard 3 of W290's branch run 41891733393, a change that does not touch adapters. W299
  (R337, merged at `0df955deb`) reproduced W273's cause under load: terrain water broke the GameTest framework's start
  button and its item landed in the region, with no rocket code involved. The registration and component fixtures now
  remove their start controls as the fault fixture does, and every adapter drop assertion names the items. This stays
  open until runs show no recurrence.
- **Adapter restore-throw GameTest under local load (2026-11-05).**
  `AdapterFaultGameTests.throwingRestoreCleansPartialInventoryAndRetries` timed out once with `Unfinished adapter
  transition: ASSEMBLY` in W299's 20 loaded local runs on `d780f1665` (run stress-03): the inventory adapter's capture
  exceeded the 5 ms callback budget under artificial CPU load. It did not recur in the next 20 loaded runs and has no
  hosted failure. No fix is queued unless it recurs; it stays open until loaded runs show no recurrence.
- **Sealed world-protection GameTest placement (2026-11-05).**
  `SealedWorldProtectionGameTests.retainedCarrierSurvivesNativeBreakPistonExplosionFluidFireAndSupportUpdates` timed
  out in shard 3 of both attempts of W298's integration run 41958063402, and
  `BayDeploymentLockGameTests.aKilledRocketLeaksItsRejectedReceiptUntilTheOperatorRetiresIt` then failed on its
  leftover record: W298's added GameTest moved the test, and ambient water at the new place refilled the fence cell
  after the test's explosion, so the retried native proof never completed (W251's cause in another fixture). W302
  (R342, merged at `04da447ee`) encloses that fixture and four sibling sealed fixtures; a matched run of the same
  batches failed before and passed after. This stays open until runs with W298 show no recurrence.
- **Bay matrix pending-spawn GameTest under local load (2026-11-05).** W298's new sibling
  `BayDeploymentMatrixGameTests.aRefusedCheckpointRequestPreservesPackagesWhileDestinationSpawnAwaitsAcknowledgement`
  failed once in W288's local full run with `The pending-spawn regression missed PREPARED source authority`: the
  fixture expects to find the transfer still in PREPARED when it drives the destination spawn, and under local load
  the transfer had moved on. It failed the same way in shard 3 of the shared run 42403168945 at `b0b576620`
  (2026-11-08), while the same tree passed in W321's branch run. The fixture races the installed transfer
  service for the destination spawn. W322 (R381, merged at `52debd6c3`) holds the service's own spawn of that
  transfer while the fixture drives it; it stays open until runs show no recurrence.
- **Bay exit packet descent GameTest (2026-11-06).** `tickingConnectionFallAt2Point1BlocksPerTickCompletesItsLanding`
  failed with `Packet descent ended before its landing: case=FALL_2_1` in shard 1 of the first attempt of W288's
  integration run 42057329524 (a torch change); the second attempt passed. W262 fixed an earlier failure of this case.
  No fix is queued unless it recurs; it stays open until runs show no recurrence.
- **Bay exit FIFO GameTest in local full runs (2026-11-06).**
  `BayPassengerExitGameTests.twoHookBypassesDrainInCaptureOrder` (batch `bay_exit_fifo`) failed once in W309's local
  full run with `Exact first winner changed`: the first safe exit was candidate 29, after 29 probes, where the test
  expects candidate 5. It failed the same way, at the same place, in both of W300F's local full runs. Neither W309
  nor W300F changes passenger-exit code, and the test passed in their hosted branch runs and in the shared runs.
  The cause is not established; a fixture whose nearer candidates are blocked at that placement would explain it,
  as in W304 and W307. W313 (R361, merged at `34017039b`) found the cause at that placement: falling gravel refilled
  the feet of the first eight ground columns through the fixture's open shaft. The exact-ordinal scenarios now run
  in a sealed, restored area, and the matched full run selected candidates 5 and 8. It stays open until later local
  full runs show no recurrence.
- **Sleep in vacuum GameTest in local full runs (2026-11-07).**
  `SleepRespawnGameTests.sleepIsDeniedInVacuumOnAPlanetAndInSpace` (`defaultBatch`) failed once in W314's local full
  run with `The vacuum fixture lies inside a station`. It passed in W314's focused default-batch run and in the hosted
  run 42275141335 at the same code. It failed the same way in W311F's local full run. The batch count passed 768,
  so every batch moved; the Moon iteration checks for a station at its column in Space Level coordinates, so a
  station left there by another test would explain it. W315 (R366, merged at `2dc3980de`) reproduced it at W314's
  placement: a station left by `EscrowMonitorGameTests` covered the Moon column. The Moon bed no longer checks
  stations, and the matched full run passed. W318 removes the stations five escrow tests leave. It stays open until
  later local full runs show no recurrence.
- **Fixture start button in flowing water, local full runs (2026-11-08).** `flowingWaterCannotDropFixtureStartButton`
  failed once in W321's first local full run after observing one dropped stone button, and passed in W321's second
  full run and in its hosted run 42400089907. It failed again in W322's local full run, on a stone button 80 ticks
  old, older than the test itself. W323 (R382, merged at `d4b561a83`) clears and logs items already in the observed
  regions before the operation; it stays open until runs show no recurrence.
- **Bay exit fixture area, hosted branch run (2026-11-09).** `BayPassengerExitGameTests.unassignedRiderGetsOnlyLocalPlacement`
  (batch `bay_exit_local`) failed in shard 3 of both attempts of run 42612205968 on the branch carrying W328 to W328G:
  the fixture area found a block entity at the same cell. W334 identified it as a generated `simple_dungeon` loot
  chest inside the test's own columns, 23 blocks above the height vanilla clears (correcting this item's earlier
  "two columns outside"); W329's new batches had moved the test onto it. W334 (R402, merged with W328 to W328G at
  `0a877b4c6`) makes the fixture select a window free of block entities inside its own allocation and refuse if none
  exists. The branch run 42670495292 passed on attempt 2 with 1,852 GameTests, and the first shared runs after the
  merge, 42685248428 at `877a8dcf2` and 42685682973 at `ef1d16fcc`, passed. It stays open until more shared runs
  show no recurrence.
- **Bay exit readiness timeout, hosted branch run (2026-11-10).**
  `BayPassengerExitGameTests.earlierDelayedCaptureDoesNotPreemptTheImmediateExit` (batch
  `bay_exit_delayed_one_box`) failed in shard 3 of W341's branch run 42723245823 with "Didn't succeed or fail
  within 400 ticks": the 400 ticks passed in 1.6 seconds of wall time while the fixture still waited for its
  chunks' entities to load. The branch changed only Python, and the same Java passed on the shared head (run
  42723457801). W344 (R414, merged at `8eca70809` after branch run 42823540208 passed) moves the fixture's
  loading into a bounded setup wait that spends no measured tick; it stays open until later runs show no
  recurrence.
- **Player backup count, hosted shared run (2026-11-10).** Shard 0 of shared run 42748839833 at `3a82236e8`
  passed all 398 tests but failed its log check: batch `rocket_rejoin_replace_failure` logged two failed player
  backups (20 WARN, 20 ERROR, 2 aborts) where the W159 expectations allow one. Its prompt ticks were
  `[12000, 12100]`, and vanilla autosaves every player when `tickCount % 6000 == 0`, so the autosave at tick
  12,000 fell inside the window in which the fixture blocks the backup (W347 confirmed it from the stack).
  No production defect is indicated. W347 (R417, merged at `254bff121` after branch run 42824294274
  passed) opens those fixtures' windows only when no autosave falls inside them; it stays open until later
  runs show no recurrence.
- **Satellite registry coalesced flush, local full run (2026-11-10).**
  `SatelliteRegistryTickGameTests.coalescedAndAutosaveRequestsArePublishedOnLaterTicks` failed once in W342's
  first local full run with "The commit was not requested by the coalesced flush", 47 minutes after W342's
  new batch had finished. It passed in a filtered run right after that batch and in W342's second full run.
  W347 established the cause: the batch started one to three ticks after the autosave of tick 84,000,
  whose registry write and the same tick's coalesced-flush request left a request pending under the
  writer's 10-tick cadence, and that request captured the test's change. No production defect is
  indicated. W350 (R420) opens the test's window only when the writer is idle; it stays open until W350
  merges and later runs show no recurrence.
- **I/O-completion budgets, local full run (2026-11-10).** W350's local full GameTest run had three failures in
  tests it did not change, between 22:53 and 22:56: both `EscrowProofRetryGameTests` superseded-proof tests
  ("Retry fixture proof exceeded its explicit force/IO completion budget") and
  `SealedSpecialHostAutomationGameTests.tankAndBayStaySealedUnderTickingAutomation` (400-tick timeout), with an
  unexpected `ARCE_DISASSEMBLY_DURABILITY_HELD` `PREPARE` `DEADLINE` warning in batch `w152_special_resources`.
  All four wait for disk I/O within tick budgets, and none falls near an autosave tick. The cause is not
  established. W350's hosted branch run and later runs decide whether they recur; it stays open until then.
- **Monitor cold warm-up check, local full run (2026-11-09).**
  `EscrowMonitorGameTests.offlineMonitorWarmsAColdDestinationAndLaunchesOnlyOnAFreshEdge` (batch
  `monitor_governed_cold`) failed once in W330's local full run with `Offline owner lost its attributed warm-up`. The
  test expects the governed warm-up on the tick after `DESTINATION_LOADING`; there, pad selection took two ticks and
  the warm-up started after the check. It passed in W330's hosted branch run 42623722922. W335 (R403) establishes
  what `DESTINATION_LOADING` promises and fixes the check: it promises bounded terrain preparation, not a selected
  warm-up, and the test now waits only while that preparation exists (merged at `70745784c` after branch run
  42691451822 passed; no production finding). It stays open until later runs show no
  recurrence.

No v1.8 Required Gate is approved. Only the owner marks a Gate or the version as passed.
