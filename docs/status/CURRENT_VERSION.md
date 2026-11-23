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

The accepted Beta identity, approvals and published artifact remain immutable in
[v0.9.0 GATE-STATUS](../releases/v0.9.0/GATE-STATUS.md); the
[v1.0 implementation log](../work/v1.0.0-implementation-log.md) records inherited acceptance work.
The v1.0 stabilization scope excludes v1.1+ features; development checks are neither a frozen
release-candidate identity nor stable approval.

## Active development checkout

The acceptance cursor stays at the earliest unfinished release Gate; v1.8 development proceeds
separately under [ADR-060](../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md).

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
latest_code_commit: b32a0cb14 (W463 merged, R665: the flight runner's opt-in settled disassembly protocol, with tests; before it W461, R664, the native runner's opt-in exact UNKNOWN expectation; every campaign preparation package is merged, K02 as partial with its flight follow-up W465 queued under R666; scripts, tests and records only, `src/main` equals candidate C6 and stays frozen there, R600). Earlier entries are in the implementation log `docs/work/v1.8.0-implementation-log.md`, section "Commit and hosted regression history (moved 2026-11-10)".
latest_hosted_regression: run 45195645440 at 60ead06cf passed (W461's and W463's merges and R664, R665); governance run 45195645531 passed. Candidate C6's own run is 44770334930 at `c8fa16e16` (attempt 1). Earlier entries are in the implementation log `docs/work/v1.8.0-implementation-log.md`, section "Commit and hosted regression history (moved 2026-11-10)".
ledger_planned_units: 5
asset_review_items: 0
required_gates_approved: none
last_updated: 2026-11-23
```

## Current state

Candidate C6 is the accounting-enabled candidate at `c8fa16e16be87554f26c402546733df6555d15e6`
(Root decision R581), with JAR SHA-256
`9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`.
In this file, W numbers identify tasks and their records, R numbers without a hyphen identify Root
decisions in [QUEUE](../work/v1.8.0-root-queue/QUEUE.md), and R- numbers identify entries of the
[risk register](../11-RISK-REGISTER.md); K numbers identify the final campaign's
[preparation packages](../work/v1.8.0-root-queue/records/CAMPAIGN-PREP-BREAKDOWN.md).
P numbers identify items in the owner's final packet, O numbers its resource arrangements,
and D numbers owner-brief decisions unless explicitly described as bay-deployment steps.
Codes such as P1-F8, R5(c), F9, N2, E4 and BI O-veto are the original design/matrix row identifiers
in the linked task evidence, not additional owner-packet items.

- **Candidate evidence.** C6's hosted run passed 1,886 GameTests; the native integrated campaign's
  attempt 2 ended with 74 PASS and 4 FAILED of 78 families, and the separately rerun
  `cargo-X03-REPAIR` passed both fresh-root-3 and upgraded-root-2 world origins after the Windows
  path-length harness failure. The original campaign remains FAILED, including the R-044
  `cargo-G2-REUSE`, `w336-downgrade` and `accounting-root3-old-jar` families; identities, native
  evidence paths and fingerprints are in [C6-SWITCH-REPORT section 2.1](../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md#21-candidate-c6).
- **Crash and storage-fault evidence.** X01 is the application-level crash replay: with W448's
  harness repairs, 72 of 82 states pass both starts and 10 Windows late-loss states fail; W451
  observed no quantity lost or created in its lost-history states, but authority/history loss
  remains and the next operation is untested. X02 is the storage-fault matrix: retaining the two
  original bay-receipt images as R636 requires gives 49 SEEN_HELD, 14 CAUGHT_LATER, 5 NOT_CAUGHT,
  0 INCOMPLETE and 4 NOT_APPLICABLE; [C6-SWITCH-REPORT section 13](../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md#13-x01-to-x03-c6-results-recorded-dispositions-and-residuals)
  retains the original runs, the alternative repaired-image tally and R636/R639 classifications.
- **Preparation packages.** The final campaign packages are delivered; delivery is not campaign
  execution, and the accepted partial packages retain their uncovered steps under R638, R642,
  R647, R655 and R661. K02 is accepted as partial ([W447](../work/v1.8.0-root-queue/records/W447.md), R661): its flight
  matrix has 5 PASS and 5 FAIL, its sealed native cuts on a diagnostic JAR built from C6 have 81 PASS and
  8 FAIL of 89, and four of W437's legacy disassembly rows remain NOT_REACHABLE_ON_C6. K20's checklist second pass is merged ([W456](../work/v1.8.0-root-queue/records/W456.md), R654), K12 is accepted
  as partial ([W454](../work/v1.8.0-root-queue/records/W454.md), R655), and [QUEUE](../work/v1.8.0-root-queue/QUEUE.md) records each package's boundary.
- **Assets and accounting.** The asset plan has no REVIEW or IMPORT entries and retains
  89 IMPORTED, 365 EXCLUDE and 444 REGENERATE files under
  [ADR-062 revision 7](../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md);
  provenance approval remains separate. Signed per-fluid accounting is active
  (`EscrowAccountingFormat.ENABLED = true`, activation `5951d6df2`), so native worlds write
  root-3 ledgers; [FUEL-ADMISSION-REPORT](../work/v1.8.0-root-queue/records/FUEL-ADMISSION-REPORT.md) lists 31 prerequisites as
  15 DONE, 11 PARTIAL and 5 OPEN, without enabling admission.
- **Owner's packet and release boundary.** [OWNER-FINAL-PACKET-DRAFT](../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md)
  carries C6's results under R652: P1's R-044 text is ready to sign unchanged, P3's operator
  guidance is recommended yes, and P4 to P9 are recommended no for v1.8. The bay, the cargo
  loading/unloading switches for items and fluids, and the separate fuel loader remain off;
  the final real-client/reference-node campaign, remaining evidence, owner decisions and
  release documentation are still required, and no Required Gate is approved.

The hosted regression checks the exact pushed commit with a clean build and unit tests,
the artifact audit, two clean-tree `runData` runs, every GameTest and the GameTest log checker.
It does not run the full Python suite, strict repository validator, dedicated server,
restart or real client; those need their own evidence.

## Open items

### Release, content and owner decisions

- **Acceptance and release.** The inherited v1.0 cursor and v1.8's G0-G9 remain open under
  [ADR-060](../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md) and the
  [v1.8 version requirements](../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md);
  development implementation and CI do not settle inherited adjacent-chunk, expiry, storage-ordering
  or candidate-acceptance gaps. Close only with the required evidence and owner approval,
  including the [release evidence](../releases/v1.8.0/RELEASE-EVIDENCE.md), final
  [PORTING_MATRIX](../PORTING_MATRIX.md) and content-ledger closure.
- **Ledger and switch dispositions.** The PLANNED units counted above are the propulsion batch
  C17a's item/fluid cargo loaders and unloaders and the orbital-equipment batch C17c's satellite
  bay; their admission and final ledger disposition remain open. W457's ADR-062 revision 8 draft
  is held on `codex/v1.8.0-w457` under R656 until the owner answers P4 to P8, after which Root
  applies the corresponding enabled or deferred rows as [QUEUE](../work/v1.8.0-root-queue/QUEUE.md) specifies.
- **Residual-risk signatures and switches.** W203's question 4 (W203-Q4) and D14 remain open for the applicable
  R-021, R-030, R-035, R-039, R-040 and R-041 to R-044 scopes in the
  [risk register](../11-RISK-REGISTER.md); the owner's 2026-10-26 R-035 acceptance
  covers only its named UNKNOWN settlement-loss accounting, capacities and P1-F8 crash windows
  with W83/W90 prerequisites, not later additions or blanket admission. Close each fuel,
  cargo-kind or bay decision only with its exact evidence-backed risk scope signed by the
  owner, or an explicit continued-disable disposition in the
  [owner's packet](../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md).
- **Downgrade limit.** [R044-SIGNOFF-DRAFT sections 5 and 6](../work/v1.8.0-root-queue/records/R044-SIGNOFF-DRAFT.md)
  covers transfer-journal schema 3 and ledger root 3, complete-world-backup recovery and
  `ARCE-BETA-2001` refusal despite exit code 0; C6 repeated the boundary failures. P1
  awaits the owner's signature or disposition, and signature does not convert any failed
  downgrade row into a pass.
- **Operator guidance and upgrade limits.** P2's interrupted-upgrade temporary-file/backup
  limitation and P3's [X03-D whole-world operator guidance](../work/v1.8.0-root-queue/records/X03-D-OPERATOR-GUIDANCE.md)
  still await the owner's decision in the [packet](../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md), although
  X03's candidate rerun condition is met. Close with the explicit decisions and published
  known-issue/operator text, not a general refund, reconstruction or fuel-repair permission.
- **Provenance and excluded authorship.** P12 awaits review of 62 records: W414's 58 final
  batch records are PENDING_HUMAN_REVIEW and the remaining c17a records lack review blocks,
  with the review-sheet location and evidence in [W414](../work/v1.8.0-root-queue/records/W414.md). Close P12 only with the
  owner's recorded visual/license review; [W279](../work/v1.8.0-root-queue/records/W279.md)'s AR 16x authorship question
  separately remains unsettled until a sourced attribution is obtained, since exclusion
  does not establish authorship.
- **Campaign resources and reserved decisions.** Under the
  [owner's 2026-11-17 delegation](../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261117.md), Root chooses design and
  sequencing, while P10 still needs the packet's O4, O5 and O7 to O12 arrangements for
  route/recording, client operators and accounts, GPU/timing tools, reference-node access
  and availability. Close those arrangements through the owner's recorded answers;
  destructive tests, risk signatures and P11's Gate/version/release approval, tags and
  `main` merge remain reserved to the owner.

### Campaign coverage and performance

Here S1 means packaged dedicated-server validation and S2 means forced-stop/recovery validation;
V1 means a real-GPU client and V2 means two real clients.
The reference node (REF) is the owner's Debian 12 machine with 4 vCPU and 8 GiB, governed by
[docs/17 section 4](../17-V1PLUS-QUALITY-BUDGETS.md#4-参考服务器预算).

- **Native and named-test coverage.** The C6 native results replace the old wait for a
  candidate run, not the remaining NOT_COVERED rows: [W455](../work/v1.8.0-root-queue/records/W455.md)/R651 leaves the
  named hosted GameTest rows open because shards publish aggregate rather than per-method
  results, and [FUEL-ADMISSION-REPORT F22/F23](../work/v1.8.0-root-queue/records/FUEL-ADMISSION-REPORT.md) retains older
  unselected loader cuts. Close with exact candidate-bound named evidence and required
  S1/S2 integration/restart coverage, preserving failed and structurally unreachable rows
  rather than treating fixture evidence from an earlier JAR as acceptance.
- **Partial packages and checklist integration.** K02's eight UNKNOWN native cuts that failed in W447
  pass on C6 with the exact UNKNOWN log expectation ([W461](../work/v1.8.0-root-queue/records/W461.md), R664), so every
  sealed native row has a passing C6 attempt; of its five failed flight checkpoints,
  [W462](../work/v1.8.0-root-queue/records/W462.md) classifies three as NOT_REACHABLE_ON_C6 and two as harness failures; with
  the corrected disassembly protocol [W463](../work/v1.8.0-root-queue/records/W463.md) still has 5 PASS and 5 FAIL, since C6
  rejects the disassembly after a recovered DESCENT or LANDED once the empty server unloads the rocket's chunk
  ([W464](../work/v1.8.0-root-queue/records/W464.md): a harness condition, the rocket stays intact), and W465 reruns the ten
  cases with the rocket's chunk kept loaded at both ends, as a requesting player would (R663, R665, R666,
  [QUEUE](../work/v1.8.0-root-queue/QUEUE.md)); the K02/K12 checklist fill and the final
  audit's remaining checklist check follow under [W459 M9 and QUEUE R658](../work/v1.8.0-root-queue/records/W459.md); K12's J05, J08, J09, J10, J11 and J17 gaps
  remain as accepted under [W454/R655](../work/v1.8.0-root-queue/records/W454.md). Close only when those deliveries and
  checklist updates have evidence for the covered steps and explicitly retain the
  uncovered steps, including the reference-node flush bracket and external-stop recipes.
- **Off-switch client gaps.** [W453/R647](../work/v1.8.0-root-queue/records/W453.md) leaves the connected-safe fuel
  controller, arbitrary-owner cap rows and listed J24/J27/J29 steps NOT_COVERED, while the
  held rocket, seat pins and account-owned package setup require campaign clients.
  Close only with the stated live-client evidence, or the owner's explicit off-switch
  disposition; a controller/probe change would require a new candidate, not silent
  fixture NBT edits.
- **V1, V2 and compatibility.** No slice has final real-GPU or two-client evidence;
  [W269](../work/v1.8.0-root-queue/records/W269.md) supplies the marked-world gate but is not an executed campaign, and
  the earlier single-player rounds are development findings rather than candidate acceptance.
  Close with C6-bound [campaign checklists](../work/v1.8.0-root-queue/records/CAMPAIGN-PREP-BREAKDOWN.md), including the
  bay passenger exit, menus, seat positions, sky/landing appearance, resource reload,
  Forge-by-JEI compatibility and real progression recording; software rendering is not V1/V2.
- **Reference workload and soak.** The docs/17 workload, reference-node measurements
  and joint four-hour soak remain unqualified, and K10's short Windows railgun windows
  were below [ADR-054](../decisions/ADR-054-ENDGAME-AUTHORITY-PROTECTION-AND-AUDIT.md)'s
  requirement under R635. Close with reference-node results and the retained budgets,
  with a repair or owner-approved ADR disposition if the throughput deficit persists;
  A09's per-tick distribution requires R648's separate hooks-only run.
- **Journal completion and storage availability.** [W208](../work/v1.8.0-root-queue/records/W208.md) and [W210](../work/v1.8.0-root-queue/records/W210.md)
  improve scoped near-bound measurements but leave interrupted/traffic/UNKNOWN/expiry,
  sustained storage-fault, stuck-I/O, watchdog, power-loss and shutdown availability
  unqualified, with the retained sealed transaction (U in W208's matrix) able to remain
  held without a time bound.
  Close with those measured workloads and the R-040 disposition; low request cost,
  safe rollback and off-thread writing alone are not completion guarantees.
- **Governed-destination first use.** [W215](../work/v1.8.0-root-queue/records/W215.md) still fails the full-observation
  new-strip and generated-contained first-checkpoint budgets, and its unconditional
  startup initialization/fatal missing-class policy remains in R-035 after
  [W217](../work/v1.8.0-root-queue/records/W217.md). Close with the missing budget/reference qualification and scoped
  disposition; timing-only results and the dependency guard do not erase those failures.
- **Survey fallback and restore costs.** [W216](../work/v1.8.0-root-queue/records/W216.md) and [W218](../work/v1.8.0-root-queue/records/W218.md)
  retain R-030's unknown-size whole-root fallback, eager restore cost and unqualified
  fallback frequency, per-tick concurrency, native startup, reference load and long-soak
  effects. Close with that qualification and disposition, not isolated START medians.
- **Shared-chain evidence boundaries.** [W204](../work/v1.8.0-root-queue/records/W204.md) proves request-driven old-pad
  claim reconciliation, not an automatic sweep or replay of a lost return-completion
  callback; offline-crew waits, unsupported-storage R5(c), external saves/playerdata
  limits and liveness under continuous external saves remain in
  [FUEL-ADMISSION-REPORT](../work/v1.8.0-root-queue/records/FUEL-ADMISSION-REPORT.md). Close with the required targeted
  evidence or scoped owner disposition; uncrewed/already-loaded fixtures do not establish
  physical-pad, maximum-resource or real-player coverage.
- **Loader recovery limits.** [W211](../work/v1.8.0-root-queue/records/W211.md)'s ordinary F9/P1-F8/N2 forms are
  structurally unreachable for loader-bound rockets, not passes, and its first BI O-veto
  attempt, failed downgrade rows and unobservable in-process E4 failures keep their
  verdicts; governed publication/pad analogues have successor evidence in W220/W221.
  Close only the remaining acceptance scope with the exact covered variants and
  residual disposition, preserving fixture-owned persisted-phase and test-seam limits.
- **Bay protection and exit risks.** [W327's audit](../work/v1.8.0-root-queue/TASK-W327-ADMISSION-RISK-AUDIT.md) and the
  [risk register](../11-RISK-REGISTER.md) retain the session-wide NOT_LOADED
  launch hold when file presence cannot be proved, released/REPAIR receipts occupying
  shared pending slots until offline repair, the shared coordinate pool and estimated
  deployment-step D6 wait bounds. Close through the relevant measured campaign coverage and owner
  disposition, including the safe passenger exit's V2 session, not from the already
  decided neutral-source/exit/per-owner-limit design choices.
- **Crash/fault residuals.** [W451/R641](../work/v1.8.0-root-queue/records/W451.md) leaves X01's late-loss outcomes failed
  and the next cargo load/unload, disassembly or launch untested; that next-operation
  test is a precondition for later enablement, not additional v1.8 implementation.
  [C6-SWITCH-REPORT section 13](../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) retains X02's original bay-image
  diagnostic omission, satellite retry/oracle mismatch, ledger STALE rollback and
  uncovered stores; close only with applicable evidence and explicit scoped
  disposition, without claiming application-level simulation proves real power-loss safety.

### Intermittent tests and observations

Each item below remains open: a merged fix or a passing rerun is not an explicit closure of
the stated recurrence condition, and historical failed attempts remain failed.

- **Escrow proof and monitor timing.** [W94](../work/v1.8.0-root-queue/records/W94.md), [W94B's follow-up](../work/v1.8.0-root-queue/QUEUE.md),
  [W111](../work/v1.8.0-root-queue/records/W111.md) and [W209](../work/v1.8.0-root-queue/records/W209.md) corrected the supersession/fixture-generation
  and pre-PREPARED autosave windows, but a second natural supersession can still exhaust
  the existing proof budget and arbitrary explicit saves/I/O stalls are not prevented.
  Close only with the remaining proof-budget/recurrence evidence, not W209's scoped
  reproduction fix.
- **Relogin (2026-10-25).** The 600-tick handoff timeout was not reproduced;
  [W161](../work/v1.8.0-root-queue/records/W161.md) corrected the capture/dismount ordering and failure cleanup.
  It stays open until hosted runs show no recurrence.
- **Planetary flight (2026-11-03).** [W270](../work/v1.8.0-root-queue/records/W270.md) corrected the landing-budget
  observer after run 41559401551's leg timeout with a forced-supersession control.
  It stays open until hosted runs show no recurrence.
- **Bay hold retry (2026-11-03).** [W271](../work/v1.8.0-root-queue/records/W271.md) paces the recovery fixtures after
  the local `bay_deployment_hook81` DEADLINE retry and its stalled-readback reproduction.
  It stays open until hosted runs show no recurrence.
- **Adapter component (2026-11-03).** [W272](../work/v1.8.0-root-queue/records/W272.md) keeps pending-disassembly
  submissions advancing after the local `Original component block not yet published` timeouts.
  It stays open until local and hosted runs show no recurrence.
- **Adapter capture stray button (2026-11-03).** [W273](../work/v1.8.0-root-queue/records/W273.md) removes the fixture
  start control without drops after the loaded `throwingCapturePreservesSource` failures.
  It stays open until loaded and hosted runs show no recurrence.
- **Reconnect scheduling (2026-11-04).** [W283](../work/v1.8.0-root-queue/records/W283.md) matches the installed
  occupancy-census readiness after run 41776065592's `Own spawn did not queue` failures.
  It stays open until runs show no recurrence.
- **Live-return dismount (2026-11-04).** [W283](../work/v1.8.0-root-queue/records/W283.md) measures retry-history advance
  from its prior count after the repeated `Definite F7 callback lost the first retry backoff`
  failures. It stays open until runs show no recurrence; W375 closed a separate
  autosave-sensitive live-return observation, not this entry.
- **Classic scale and sawmill under load (2026-11-04).** [W283](../work/v1.8.0-root-queue/records/W283.md) did not
  reproduce the scale-stage and `Batch did not complete exactly once` failures in its
  loaded repetitions, so neither is fixed. Both stay open until loaded runs show no recurrence.
- **Bay deployment matrix (2026-11-05).** [W298](../work/v1.8.0-root-queue/records/W298.md) corrected the observer's
  assumption that a recorded destination ID is already a published entity after run
  41870324437. It stays open until runs show no recurrence.
- **Adapter registration (2026-11-05).** [W299](../work/v1.8.0-root-queue/records/W299.md) applies start-control removal
  to the registration/component fixtures after run 41891733393's duplicate-item failure.
  It stays open until runs show no recurrence.
- **Adapter restore under load (2026-11-05).** [W299](../work/v1.8.0-root-queue/records/W299.md) records the
  `throwingRestoreCleansPartialInventoryAndRetries` ASSEMBLY timeout when capture
  exceeded its callback budget. It stays open until loaded runs show no recurrence;
  no fix is queued unless it recurs.
- **Sealed world protection (2026-11-05).** [W302](../work/v1.8.0-root-queue/records/W302.md) encloses the fixture and
  siblings after ambient water caused the protection timeout and a following bay-lock
  failure in run 41958063402. It stays open until runs with W298 show no recurrence.
- **Bay pending spawn (2026-11-05).** [W322](../work/v1.8.0-root-queue/records/W322.md) holds the service's competing
  destination spawn after the local and run 42403168945 PREPARED-authority race.
  It stays open until runs show no recurrence.
- **Bay packet descent (2026-11-06).** [QUEUE's W288/W262 history](../work/v1.8.0-root-queue/QUEUE.md)
  records run 42057329524's `FALL_2_1` landing failure followed by a passing rerun.
  It stays open until runs show no recurrence; no fix is queued unless it recurs.
- **Bay exit FIFO (2026-11-06).** [W313](../work/v1.8.0-root-queue/records/W313.md) seals/restores the exact-ordinal
  area after falling gravel changed the first winner in local full runs.
  It stays open until later local full runs show no recurrence.
- **Sleep in vacuum (2026-11-07).** [W315](../work/v1.8.0-root-queue/records/W315.md) fixes the Moon/Space station
  comparison and [W318](../work/v1.8.0-root-queue/records/W318.md) cleans up leaked stations after local full-run failures.
  It stays open until later local full runs show no recurrence.
- **Flowing-water fixture button (2026-11-08).** [W323](../work/v1.8.0-root-queue/records/W323.md) clears and logs
  pre-existing items after the old stone button caused local full-run failures.
  It stays open until runs show no recurrence.
- **Bay exit fixture area (2026-11-09).** [W334](../work/v1.8.0-root-queue/records/W334.md) selects a block-entity-free
  window inside its allocation after both attempts of run 42612205968 found a dungeon
  chest. It stays open until more shared runs show no recurrence.
- **Bay exit readiness (2026-11-10).** [W344](../work/v1.8.0-root-queue/records/W344.md) separates bounded loading from
  measured ticks after run 42723245823 exhausted its wait while entity sections loaded.
  It stays open until later runs show no recurrence.
- **Player backup count (2026-11-10).** [W347](../work/v1.8.0-root-queue/records/W347.md) avoids the autosave inside
  the deliberately blocked backup window that doubled the log counts in run 42748839833.
  It stays open until later runs show no recurrence.
- **Satellite coalesced flush (2026-11-10).** [W350](../work/v1.8.0-root-queue/records/W350.md) opens the test window
  only with an idle writer after an autosave request captured the change in a local full run.
  It stays open until later runs show no recurrence.
- **I/O-completion budgets (2026-11-10).** [W350](../work/v1.8.0-root-queue/records/W350.md) records local failures of
  both proof-retry tests and sealed tank/bay automation, plus the durability-held warning;
  the hosted batches passed but the cause is unestablished.
  It stays open until later local full runs show no recurrence.
- **Monitor cold warm-up (2026-11-09).** [W335](../work/v1.8.0-root-queue/records/W335.md) corrects what
  DESTINATION_LOADING promises after the premature warm-up check failed locally.
  It stays open until later runs show no recurrence.
- **Concurrent flight (2026-11-17).** [W378](../work/v1.8.0-root-queue/records/W378.md) could not reproduce run
  44109049691's post-spawn stall and added diagnostics; [W385](../work/v1.8.0-root-queue/records/W385.md)'s pre-spawn
  ticket fix does not explain it. It stays open until a cause is found or later runs show no recurrence.
- **Terminal-journal unit acknowledgement (2026-11-17).** [QUEUE's W372D/W372R
  observations](../work/v1.8.0-root-queue/QUEUE.md) retain the single failure without its XML, followed by
  isolated and complete passing runs; Root's slow-write reading is not a reproduced cause.
  The observation remains open, and a recurrence requires a diagnostic task.
- **Writer benchmark unit readback (2026-11-13).** [QUEUE R455](../work/v1.8.0-root-queue/QUEUE.md) records
  run 43395387417's first-attempt readback-count failure and passing second attempt,
  with the check dependent on runner speed. It stays open until later runs show no
  recurrence or that dependence is removed.
- **Warm-up capacity (2026-11-20).** [W394](../work/v1.8.0-root-queue/records/W394.md) adds diagnostics after W392's
  filtered-run eight-pad refusal; W392R found no leftover or batch-order cause.
  It stays open until the cause is known or runs show no recurrence.
- **Light transmission (2026-11-21).** [QUEUE's W433/R602](../work/v1.8.0-root-queue/QUEUE.md) retains run
  44861941486's opaque-control failures and the isolated branch's passing run 44886451261.
  It stays open until W433 merges after the C6 freeze and shared runs pass.

Only the owner marks a Gate or the version as passed.
