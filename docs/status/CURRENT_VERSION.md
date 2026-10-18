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
latest_code_commit: e80069c6 (W90a merged: governed flight readiness)
latest_hosted_regression: run 39091589526 at e80069c6 (the W90a merge) failed only EscrowProofRetryGameTests.supersededEntityProofRetriesTheSameFrontierAfterBackoff at its 200-tick timeout (W94's forced-supersession test; W94B fixes it), as run 39085135243 at 0a3964e7 did, and every other GameTest passed; Repository governance passed (run 39091589547); the last fully passing regression is 39049214708 at d9a7c0e9
ledger_planned_units: 19
asset_review_items: 154
required_gates_approved: none
last_updated: 2026-10-18
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
- The D3-A escrow core and its rocket integration (W11b2: settlement, ordinary reconciliation and
  governed flights) are merged with admission disabled. Before admission: W11b3, W90b (the
  destination warm-up), part 1's F3 and N2 (W91), and W83. W90a (governed readiness) and P1-F8 (W92, with W92F's
  follow-ups) are merged.

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

- **Ledger.** 19 units are still PLANNED: C17a 6 and C17c 1
  (the monitoring station, the five loader variants and the linker, which wait for the escrow
  chain and the bay), C18a 1 (the oxygen charger, owner decision D4) and C18d 11 (the ten sounds,
  which wait for the owner's listen, and `modelBakeEvent`, which waits for an OBJ import). The
  backlog is in QUEUE.md.
- **Assets.** 154 legacy assets are still under REVIEW (ADR-066 section 7.3).
- **S1.** W11b1 and W44 have native dedicated-server restart evidence as development evidence.
  No S1 campaign has run.
- **Intermittent GameTest.** Hosted run 39047343116 at `b01f441e` failed
  `EscrowCoreGameTests.ownTerrainAndEntityStoresConfirmTheExactFrozenFrontier` once with
  `SUPERSEDING_SAVE` (a test-timing race, W36's class); the next run passed. W94 (merged `ec7214c5`)
  lets such proof tests retry only that reason; 30 of 30 local batch runs passed. Its two new
  forced-supersession GameTests then timed out on hosted run 39060903571 at `8c3f5717`, and one of them again
  on runs 39085135243 at `0a3964e7` and 39091589526 at `e80069c6`; W94B fixes them.
- **S2.** W11b2's crash cuts passed natively as development evidence (the W11b2 records). The
  S2 campaign waits for W11b3.
- **V1 and V2.** No slice has real-GPU or two-client evidence.
- **C19.** W58 audited it (`records/W58.md`). Merged since: the validator chain and the G4 fix
  (C19-A), the portable evidence links (W60), correction files in the Markdown check and the
  R32-03 tests (W61), and the v1.8 import root (W52F). Hosted governance run 38671573901 passed
  end to end at `0e368a10` for the first time on this branch (the full Python suite, the upstream
  and vanilla checks, the art screen and the strict validator). The ledger closure, the final
  `PORTING_MATRIX` and the release evidence remain.
- **Owner decisions.** Eleven are pending; QUEUE.md lists them with what each one blocks.
- **Risks.** R-021 and the risks the W slices added (R-028 to R-037) stay open.
- **Earlier intermittent GameTests.** W73 fixed the landing-pad cancellation test, W74 the
  six-host natural-beds test, W76 the first-Moon-landing and spawn-egg tests, and W78 the sealed
  sleep-room test. Hosted runs confirm each fix as they accumulate. The open one is the escrow
  proof timeout above.

No v1.8 Required Gate is approved. Only the owner marks a Gate or the version as passed.
