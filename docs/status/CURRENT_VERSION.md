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
latest_code_commit: ed1c29fe0 (W155 merged: the journal lease keeps the named transfer's history, closing W153-R7; before it, W153 merged C17a-04, the linker and the rocket monitoring station)
latest_hosted_regression: the last green run is 40092320937 at 8dee8680 (the W153 merge). Every completed hosted regression since the failure at 2426f00d on 2026-10-22 is green; W139 stays open until no hosted log shows ARCE_LAUNCH_DISCARD (QUEUE)
ledger_planned_units: 17
asset_review_items: 154
required_gates_approved: none
last_updated: 2026-10-24
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
  governed flights) are merged with admission disabled. Before admission: W11b3, the P1-N3 fix and
  the remaining N2 cuts, and W90's measured cold-destination costs.
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
- **P1-N3 (2026-10-18), first leaf merged.** N3-A1 (W98, `8f6dd016`) closes I1 to I3: the W91K cuts now keep the rocket FUELED at its origin on both restarts. N3-A2 (W116, merged `81c987f9`) closes W98-R3, R10 and R12; W98-R6 stays a named residual (Root decision R42). Open: W98-R1 (before governed admission) and N3-B (I6, I7). The original finding: W91K's native cuts showed that a hard kill after an ordinary
  fail-back's journal removal is durable, and before the source rocket's chunk is saved, leaves the rocket in
  its saved in-flight image with no transfer record, which nothing recovers. It predates W91 and affects
  ordinary flights in every world. W96's design note is frozen with its independent review's amendments (Root
  decision R34); W98 implements its first leaf. The review also inferred a no-crash variant (a passenger who
  reconnects during ascent), which W98 confirms or refutes with a GameTest first.
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
- **Owner decisions.** Eleven are pending; QUEUE.md lists them with what each one blocks.
- **Performance at the bounds (W128, 2026-10-21).** On the development host, the satellite registry's checkpoint, coalesced flush and autosave at a 16.7 MB root take about 450 to 900 ms at P95 and P99, and the rocket transaction journal's checkpoint at its 64 MiB bound about 4.1 s, against docs/17's 50 and 100 ms; the cost is server-thread encoding and exists today in ordinary autosave at those sizes. Root decision R51 pauses the bay chain at W27d1 until an off-thread writer design (W129) and its ADR.
- **Risks.** R-021 and the risks the W slices added (R-028 to R-040) stay open. Root registered R-039 (the bay receipt store) before W27c1 and R-040 (the checked transaction journal and its unreadable-root hold) with W123.
- **Earlier intermittent GameTests.** W73 fixed the landing-pad cancellation test, W74 the
  six-host natural-beds test, W76 the first-Moon-landing and spawn-egg tests, and W78 the sealed
  sleep-room test. Hosted runs confirm each fix as they accumulate. The open one is the escrow
  proof timeout above.

No v1.8 Required Gate is approved. Only the owner marks a Gate or the version as passed.
