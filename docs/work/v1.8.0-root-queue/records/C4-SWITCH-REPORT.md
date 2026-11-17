# Candidate C4: risks and evidence per switch for the satellite bay and the four cargo kinds

| Item | Value |
|---|---|
| Status | **DRAFT; all C4 results are PENDING.** Historical evidence, including the failed `cargo-G2-REUSE` and `w336-downgrade` checks, is not C4 evidence. Nothing here accepts a risk, enables production admission or passes a Gate, version or release. |
| Written by | W351 wrote the original report; its structure and rows are retained from [C3-SWITCH-REPORT](C3-SWITCH-REPORT.md). |
| Retargeted by | W374, on 2026-11-17, under R470 and [TASK-W374](../TASK-W374-C4-REPORT-DRAFT.md). |
| Filled by | PENDING |
| Base | The drafting checkout is `ae83cfb215478341821b9ab3c92f922e85005dd2` on `codex/v1.8.0-w374`. This is not C4's identity; section 2.1 is unfilled. |
| Owner request | [OWNER-DECISIONS-20261109](OWNER-DECISIONS-20261109.md) requests one candidate and separate risks/evidence for each switch. [OWNER-DECISIONS-20261117](OWNER-DECISIONS-20261117.md) delegates design to Root and reserves the final yes-or-no decisions described in section 10. |
| Earlier reports | [C3-SWITCH-REPORT](C3-SWITCH-REPORT.md) is the historical source for every retained row and fact in sections 1 to 11 unless a newer source is named. [INTEGRATED-C1-REPORT-20261109](INTEGRATED-C1-REPORT-20261109.md) remains earlier history. |
| Out of scope | The fuel loader's admission is separate. It appears only where it shares a risk, notably the accounting and common stores. No code, script, register, ADR, status file or QUEUE is changed. |

## 1. Names used in this report

- **C4** is the planned accounting-enabled candidate; its identity and results are PENDING (section 2.1).
  **C1**, **C2** and **C3** are historical candidates, not sources of C4 results. Their identities and outcomes are
  recorded in [C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), sections 1 and 2.
- **Wnnn** names a task and its record in this folder, for example [W331](W331.md). **Rnnn** without a hyphen is a Root
  decision, recorded in [QUEUE](../QUEUE.md) or an owner-decision record. **R-0nn** with a hyphen is an entry of the
  [risk register](../../../11-RISK-REGISTER.md).
- A **native case** runs the packaged mod on a dedicated server, stops it at a chosen point (a **cut**), and checks the
  stopped world and two restarts. Starts are numbered S0, S1, S2 and so on. A **smoke** is a native case on a locally
  built JAR during development. A smoke shows that a fixture and its oracle work; it is not candidate evidence.
- An **A1** row is a GameTest in the hosted regression run of the candidate's commit. A GameTest is not native restart
  evidence.
- A **family** is one entry of the
  [campaign driver](../../../../scripts/run_v180_integrated_campaign.py). Family, case and test names are preserved
  for Root's filler, for example `forced-W331-D`, `cargo-C-S4-LOAD` or `r042-N_BAY`. The C4 run's selection and
  family count are PENDING; [QUEUE](../QUEUE.md), W363F/R455, records the earlier 71-family selection.
- Bay row IDs come from [W325-MATRIX](W325-MATRIX.md): B1.1 to B18.7, CAP.1 to CAP.3 and EXIT.0 to EXIT.10 are native
  rows, and F2.1 to N2.6 are GameTest rows. Cargo row IDs come from [W326-MATRIX](W326-MATRIX.md): N001 to N120 are
  native cuts, Q01 to Q04 the per-owner cap, X01 to X03 residuals, and C-E-A01 to C-E-A14 and D-E-A01 to D-E-A12
  GameTest rows. M01 to M09 are the changed-boundary rows that [W326](W326.md) indexes.
- Bay deployment steps D0 to D7 follow [W27A-DESIGN](W27A-DESIGN.md), as do the letters L (a rocket's logical ID), T
  (its transfer record, T′ after consumption), S (the source rocket, S′ after consumption) and X (a satellite).
- **NOT_LOADED** means that a store's file exists but did not load, or that its presence could not be proved.
  **NO_FILE** means that its absence was proved. The two stores here are the bay receipt index (`BAY_RECEIPTS`) and the
  escrow ledger (`RESOURCE_ESCROW`), which the fuel loader and all four cargo kinds share.
- **V1** is a check with one real client and **V2** a check with two real clients. The **reference node** is the
  performance host of [docs/17](../../../17-V1PLUS-QUALITY-BUDGETS.md). All three belong to the owner's final campaign.
- **M1 to M6** and **L1 to L5** are the findings of W327's audit.
- Result words after Root fills this draft are PASS, FAIL and NOT_COVERED (no evidence for that exact row).
  Every C4 result cell is exactly PENDING, optionally with its case count. Historical C1 and smoke columns retain
  their historical results. A reasoned unreachable row is not an executable pass. Section 11 describes the fill boundary.

## 2. The candidate and what changed since C1

### 2.1 Candidate C4

Root must fill this identity from the same candidate's sealed build, hosted run and campaign evidence. The task
requires these fields to stay PENDING; the drafting checkout and worker smokes are not substitutes.

| Identity or evidence | C4 |
|---|---|
| Candidate commit | PENDING |
| Hosted regression run and attempt | PENDING |
| Governance run and attempt | PENDING |
| Candidate JAR SHA-256 | PENDING |
| Campaign run, selection and evidence location | PENDING |

The intended candidate carries the separately reviewed accounting activation (section 12), the work since C3
(sections 2.2 and 2.3), and all five admission switches still off. This is a target, not a claim that activation,
integration, candidate packaging or verification has finished. [QUEUE](../QUEUE.md), W372/R466/R468/R469, records
activation still in progress at this drafting snapshot.

C3's outcome remains in [C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), sections 2.1 and 11: its 58 families ran, with
56 passing and the two historical-JAR downgrade families failing under R-044. Its hosted run passed 1,859 GameTests.
No historical pass or failure is copied into a C4 result. `cargo-G2-REUSE` and `w336-downgrade` retain their old
failed checks; the new ledger-root-3 downgrade boundary has no result yet (section 9.3 and section 12).

#### 2.1.1 C2's campaign is history, not this report's candidate evidence

The ledger paragraph and open items of [CURRENT_VERSION](../../../status/CURRENT_VERSION.md) and [QUEUE](../QUEUE.md)
rows W351 to W355 (R424 to R429) record C2's outcome at `ade1538c356d4cab914bbbffdf3c1469fe3288d7`:
**49 families passed, 6 failed and 3 did not run** in its main campaign. The six failed families were:

| C2 family | Cause and classification | Fix or disposition before C3 |
|---|---|---|
| `cargo-C-S3` | R424: the first case, `FC_LOAD_R1_255`, crashed while the gated fixture built a flight plan on an unfueled rocket. W330 had changed the builder to use the rocket's actual fuel; this was a test-fixture regression, before a production cargo step. | [W352](W352.md), merged as `c7bca5eed`, restores C1's synthetic 1,000 mB water fuel only for native R1. All ten cases passed natively on its corrected JAR. |
| `cargo-C-S4-LOAD` | R425: 18 of 20 cases passed, then `FS_LOAD_VETO_DESTINATION_SAVED` failed because the runner rejected the exact ERROR injected by its source-save veto. `FS_LOAD_BIND_VETO` did not run. This was a harness gap, not a product defect. | [W353](W353.md), merged as `b4dd5f320`, accepts only that exact injected event in that case at start 0; all other ERROR/FATAL checks remain. All 20 cases then passed on the C2 JAR. |
| `cargo-B3-S1-REUSE` | R426: `LF_M_N3_FULL` failed after three passing cases. Its synthetic LOADED bay index had no backing store, so W128's deliberate protective hold returned ESCROW_PENDING before the queue. This was a stale fixture, not a production regression. | [W354](W354.md), merged as `266df4cc3`, binds an empty, unwritten generation-zero bay store for the synthetic refusal cuts and checks that no BAY file appears. All seven cases passed with the forced stop and both restarts. |
| `cargo-B3-S6-REUSE` | R427: 88 of 97 cases ran; 87 passed and `LF_S6_F9_RESPAWN` failed at its first start because recovery kept the source. W211 R203 already records ordinary F9 as structurally out of reach under the preservation policy. This was a campaign selection error. | [W355](W355.md), merged as `26b1ecda9`, selects 96 cases and explicitly reports F9 as NOT_COVERED, leaving the underlying cases and oracles unchanged. The nine later cases passed on C2 after the main run. |
| `cargo-G2-REUSE` | R428: six of nine campaign rows passed; only the three historical-JAR downgrade checks failed because the old JAR rewrote `level.dat` and `level.dat_old` before refusing. | Known R-044 residual, not a new finding; no fix task or waiver. The unchanged checks are expected to fail until the owner's decision. |
| `w336-downgrade` | R429: the same three old-JAR checks failed the whole-world byte-preservation oracle; 43 other files were unchanged and the restored candidate's two later starts were clean. | The same R-044 residual; no fix task or waiver. The unchanged checks are expected to fail until the owner's decision. |

The three families skipped after runner failures, `cargo-C-S5`, `cargo-B3-S4-REUSE` and `cargo-B3-S5-REUSE`, then
passed on C2 under suffix `attempt2`; the nine later S6 cases also passed there. These follow-up passes do not erase
the main run's failures. **C2 and C3 results are history, cited from C3's report, not C4 evidence.** On C3 the four families
fixed after C2 passed: `cargo-C-S3` (10 cases), `cargo-C-S4-LOAD` (20), `cargo-B3-S1-REUSE` (7) and
`cargo-B3-S6-REUSE` (96 selected cases, with ordinary F9 NOT_COVERED).

### 2.2 Production changes between C1 and C4 preparation

**C1 to C2.** The original draft's comparison remains:

`git diff 70dd5d43e ade1538c3` over `src/main`, `src/generated` and the three build files changes 56 files. Apart from
GameTests and gated release-test fixtures, the changes are these:

- **W328 and W328F.** The pre-start check decides every managed file's presence with a no-follow status check, before it
  reads any file, and refuses startup (`ARCE-BETA-2000`) when a presence is unknown. The escrow ledger's startup
  classification treats only a proven absence as NO_FILE. Both NOT_LOADED WARNs name a reason. The refusal's hint now
  leads with the storage instruction, and the ledger lookup answers NOT_LOADED off the server thread.
- **W329.** While the ledger is NOT_LOADED, the read-only checks answer protectively: a rocket is busy, protected, not
  a neutral bay source, logically locked and frozen, and every device holding a root is locked.
- **W332.** The gated load-failure seam `ReleaseTestLoadFailure`, consulted by the two load-only lookups. It does
  nothing without its release-test property.
- **Gated release-test fixtures and probes.** W330 (the cargo owner cap and load settlement cuts), W331 (the bay gaps),
  W332 (the R-042 probes) and W346 (the save-boundary control).

**C2 to C3.** The required comparison
`git diff --stat ade1538c3 9a15e1f42 -- src/main src/generated build.gradle settings.gradle gradle.properties`
changes eight files, with 217 insertions and 25 deletions. They are GameTest classes, GameTest-only support and gated
release-test probes only: W350's registry test windows (`67c310530`), W352's C-S3 R1 fixture fuel (`c7bca5eed`) and
W354's backed bay store for the loader refusal cuts (`266df4cc3`). No generated resource or Gradle file changes.
W353 (`b4dd5f320`) and W355 (`26b1ecda9`) change Python and tests only. These are the task's recorded classifications,
consistent with the file comparison; they do not describe a production admission change.

**C3 to the C4 preparation work.** The following are recorded changes, not C4 verification:

- [W363](W363.md), R444/R451/R455 in [QUEUE](../QUEUE.md), adds the governed-flight bay refusal and fixes restart-slot
  starvation: a waiting restart group does not take the tick's R2 slot. Section 3 gives the native and GameTest rows.
- [W361](W361.md), [W362](W362.md), [W364](W364.md) and [W366](W366.md) implement accounting Slices A to D.
  [W365](W365.md) supplies versioned readers/oracles. R449, R452, R453 to R455 and R462 to R465 in
  [QUEUE](../QUEUE.md) record integration and reviews. They do not establish activated native evidence (section 12).
- [QUEUE](../QUEUE.md), W371/R464/R470, records the delivered bay constant
  `BayDeploymentSwitches.PRODUCTION_ADMISSION = false`; `BayDeploymentSession.admissionEnabled()` answers for
  that constant or the existing release-test gate. Its merge/hosted checks still require Root's confirmation.
- The four cargo constants stay false through `EscrowCargoSwitches.production`. R459
  ([OWNER-DECISIONS-20261117](OWNER-DECISIONS-20261117.md)) makes every switch ready but does not enable one.
- W372's activation is separate from admission. Its run, unresolved fixture work and R469 capacity-fixture
  decision are reported as recorded, not as a finished C4 feature or a Gate pass (section 12).

### 2.3 Harness changes outside the JAR

The C1-to-C3 history below comes from [C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 2.3.

- W337 wrote the campaign driver. W339 added W336's 17 families, W345 added W332's six R-042 families, and W346 added
  `forced-W346-R`.
- W331 made every bay runner take the candidate commit and JAR SHA-256 as arguments and check production equality
  against that commit.
- W341, from W338's review, made the forced-stop runner compare the complete receipt set, require an unlocked settled
  rocket and check released charges. It also made every runner that copies the candidate recheck the installed copy
  before Java starts and before a result is accepted.
- W330 extended the cargo controllers and the fluid and item settlement oracles for the new load cuts, and made the
  cargo wrapper require the candidate pins.
- W343 and W345 strengthened the R-042 oracles. W349 added a stopped-world inventory scan to W346's case.
- W353 adds the exact injected load-veto ERROR qualification (section 2.1.1). W355 excludes ordinary F9 from the
  integrated selection and reports it as NOT_COVERED, without changing the underlying S6 cases or oracles
  ([W355](W355.md)).

Since C3, [W358](W358.md) and [W363](W363.md) add the selected bay gap families; R451 withdraws
`forced-W358-B6-BOUND` without altering its failed history. [W360](W360.md) adds fourteen item cuts, four R-042
families and two item-hold GameTests. [W365](W365.md) versions the accounting oracles.
[QUEUE](../QUEUE.md), W370/R463/R467, records the replay builder/runner delivered with synthetic tests, not a native
X01 run; W369's recorder and the remaining X02 work are still separate. Sections 12 and 13 give the evidence plan.

### 2.4 Summary per switch

| Switch | C1 native rows | C1 GameTest rows | Rows with a C4 family or named test | Remaining limits | C4 result |
|---|---|---|---|---|---|
| Bay (`block_variant:loader/1`) | 88 PASS, 27 NOT_COVERED | 13 PASS, 31 NOT_COVERED | Every retained C3 row plus W358's six selected gap families and W363's three families and two GameTests; section 3. | B11.2 and B17.5 are reasoned unreachable, not native passes. The final campaign and hosted results are awaited. | PENDING |
| ITEM_CARGO_LOAD (`loader/3`) | 6 PASS | 6 PASS | All retained rows, seven new load cuts in D-S2-LOAD, and the shared R-031/presence/item-hold rows. | X01/X02 simulation, its real-storage residuals and deferred client/performance evidence. | PENDING |
| ITEM_CARGO_UNLOAD (`loader/2`) | 24 PASS | 11 PASS | All retained rows, seven new unload cuts in D-S2, and the shared R-031/presence/item-hold rows. | As for item load. | PENDING |
| FLUID_CARGO_LOAD (`loader/5`) | 44 PASS | 6 PASS, 1 NOT_COVERED | All retained rows plus the shared new rows and accounting evidence of section 12. | Activated accounting evidence, X01/X02, and deferred client/performance evidence. | PENDING |
| FLUID_CARGO_UNLOAD (`loader/4`) | 46 PASS | 9 PASS, 3 NOT_COVERED | All retained rows plus the shared new rows, accounting and planned X03 repair evidence. | As for fluid load, plus X03's tool, review and native evidence. | PENDING |

The historical C1 gaps and C3 outcome remain in [C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 2.4.
Sections 3 to 9 retain all its scenario, risk and timing rows. Sections 12 and 13 add accounting and the selected
X01-to-X03 approach. The inventory is not a coverage verdict: Root fills results only from C4's own evidence.

## 3. The satellite bay's deployment admission (`block_variant:loader/1`)

### 3.1 The switch, and what is live with it off

- **The switch.** W371 supplies `BayDeploymentSwitches.PRODUCTION_ADMISSION = false`.
  Admission is the constant or the existing release-test gate, and remains off in production
  ([QUEUE](../QUEUE.md), W371/R464/R470; section 2.2).
- **Root recommendation: PLACEHOLDER — PENDING ROOT REVIEW for the bay.** No enablement is recommended by this draft.
- **Live with the switch off.** The bay is craftable at C3 (historical baseline) (its recipe `rocket_satellite_bay` is in
  `src/generated/v1.8`). So the code for its root protection and save refusal (R-038), the package insertion fences and
  NOT_LOADED (R-042) is active in production now, and recovery of receipts already in a world runs on every server
  (R-043; W327, section 4). The register states that production worlds create no receipt file while deployment is off
  (R-039).

### 3.2 Scenarios

**Changes common to every native bay row since C1.** W331 made the runners take the candidate as an argument. W341
added the complete receipt-set comparison and the installed-JAR rechecks. W337 runs every case through the campaign
driver. "Common" in the table means that only these changes apply. A family named twice means that the case is selected in both families, as at C1. C4 results are awaited.

#### Native rows of W325-MATRIX

| Row | Scenario | C1 | Changed since C1 | C4 family (case) | C4 |
|---|---|---|---|---|---|
| B1.1 | INTENT staged only | PASS | Common | `forced-A` (`B1_STAGED`) | PENDING |
| B1.2 | INTENT replaced before the callback; interrupted restart and same-operation retry | PASS | Common | `forced-A` (`B1_MOVED`) | PENDING |
| B2.1 | Registration in RAM, registry staged only | PASS | Common | `forced-B` (`B2_STAGED`) | PENDING |
| B2.2 | Registry replaced before the callback; INTENT promotion | PASS | Common | `forced-B` (`B2_MOVED`) | PENDING |
| B3.1 | REGISTERED staged only, source not forced, no passengers | PASS | Common | `forced-B` (`B3_STAGED`) | PENDING |
| B3.2 | REGISTERED replaced, source not forced, no passengers | PASS | Common | `forced-B` (`B3_MOVED`) | PENDING |
| B3.3 | REGISTERED replaced, source forced, no passengers | PASS | Common | `forced-B` (`B3_SAVED`) | PENDING |
| B3.4 | REGISTERED replaced, source present, offline seat, copies at both ends | PASS | Common | `forced-CD` (`W185_B3_PRESENT`) | PENDING |
| B3.5 | REGISTERED replaced, source absent, offline seat, copies at both ends | PASS | Common | `forced-CD` (`W185_B3_ABSENT`) | PENDING |
| B3.6 | REGISTERED staged only, source present, offline seat | PASS | Common | `forced-E` (`W325_B3_STAGED_PRESENT`) | PENDING |
| B3.7 | REGISTERED staged only, source absent, offline seat | PASS | Common | `forced-E` (`W325_B3_STAGED_ABSENT`) | PENDING |
| B3.8 | REGISTERED staged only, source present, online passenger | PASS | Common | `forced-E`, `forced-E-retry` (`W325_B3_STAGED_ONLINE`) | PENDING |
| B3.9 | REGISTERED staged only, source absent, online passenger | PASS | Common | `forced-E`, `forced-E-retry` (`W325_B3_STAGED_ONLINE_ABSENT`) | PENDING |
| B3.10 | REGISTERED replaced, source present, online passenger | PASS | Common | `forced-E`, `forced-E-retry` (`W325_B3_MOVED_ONLINE`) | PENDING |
| B3.11 | REGISTERED replaced, source absent, online passenger | PASS | Common | `forced-E`, `forced-E-retry` (`W325_B3_MOVED_ONLINE_ABSENT`) | PENDING |
| B3.12 | A restart waits for the checked startup journal, then resumes | PASS | Common | `forced-CD` (`B3_JOURNAL_HELD`) | PENDING |
| B4.1 | Idle non-data X decommissioned on the first restart tick (narrower than a decommission before the kill) | PASS | Common; W341 also checks this survey case's stopped world after both restarts | `forced-B` (`B4_DECOMMISSION`) | PENDING |
| B4.2 | X decommissioned on the first restart tick, source absent, offline seat | PASS | Common; W341 survey-case checks | `forced-CD` (`W185_B4_ABSENT`) | PENDING |
| B4.3 | X absent on the initial disk, source present, offline seat | NOT_COVERED | W331 built a pre-kill decommission; its composition carries T′ (the B5.2 mixed state) | `forced-W331-D` (`W331_B4_GONE_X_S_PRESENT`) | PENDING |
| B4.4 | X live, source present, offline seat | PASS | Common; W341 survey-case checks | `forced-E` (`W325_B4_LIVE_X_PRESENT_S`) | PENDING |
| B4.5 | X live, source absent, offline seat | PASS | Common; W341 survey-case checks | `forced-E` (`W325_B4_LIVE_X_ABSENT_S`) | PENDING |
| B4.6 | X absent, source present, online passenger | NOT_COVERED | W331 | `forced-W331-D` (`W331_B4_GONE_X_S_PRESENT_ONLINE`) | PENDING |
| B4.7 | X absent, source absent, online passenger | NOT_COVERED | W331 | `forced-W331-D` (`W331_B4_GONE_X_S_ABSENT_ONLINE`) | PENDING |
| B4.8 | X live, source present, online passenger | NOT_COVERED | W331 | `forced-W331-D` (`W331_B4_LIVE_X_S_PRESENT_ONLINE`) | PENDING |
| B4.9 | X live, source absent, online passenger | NOT_COVERED | W331 | `forced-W331-D` (`W331_B4_LIVE_X_S_ABSENT_ONLINE`) | PENDING |
| B4.10 | X absent on the initial disk, source absent, offline seat | NOT_COVERED | W331 | `forced-W331-D` (`W331_B4_GONE_X_S_ABSENT`) | PENDING |
| B5.1 | Mixed S′ and T, neutral copies at both ends, offline seat | PASS | Common | `forced-CD` (`W185_B5_SOURCE_AFTER`) | PENDING |
| B5.2 | Mixed S and T′, neutral copies at both ends, offline seat | PASS | Common | `forced-CD` (`W185_B5_JOURNAL_AFTER`) | PENDING |
| B5.3 | Boundary before either D5 image, offline seat | PASS | Common | `forced-CD` (`W185_B3_PRESENT`) | PENDING |
| B5.4 | Boundary after both D5 images, offline seat | PASS | Common | `forced-CD` (`W185_B7_SAVED`) | PENDING |
| B6.1 | Source not forced at D5's completion | PASS | Common | `forced-B` (`B6_D5`) | PENDING |
| B6.2 | Source forced at D5's completion | PASS | Common | `forced-B` (`B6_SAVED`) | PENDING |
| B6.3 | Source absent, original T, offline seat | PASS | Common | `forced-CD` (`W185_B3_ABSENT`) | PENDING |
| B6.4 | Source absent, consumed T′, offline seat | PASS | Common | `forced-CD` (`W185_B6_AFTER_ABSENT`) | PENDING |
| B6.5 | Destination absent after D7; reconstruction from the consumed record | NOT_COVERED | W331's case reaches the consumed-source recovery that production chooses when S′ is on disk; W338 (finding 3) showed the destination rebuild is reachable; W342 added three GameTests; W346, with W349's inventory scan, built the native save-boundary case | `forced-W331-R` (`W331_B6_DESTINATION_ABSENT`, keeps the consumed source) and `forced-W346-R` (`W346_B6_SAVE_BOUNDARY`, rebuilds the destination from T′) | PENDING |
| B6.6 | Protected duplicate | PASS | Common | `forced-CD` (`W185_B6_PROTECTED`) | PENDING |
| B6.7 | Conflicting or unrecognized duplicate | PASS | Common | `forced-CD` (`W185_B6_UNEXPECTED`) | PENDING |
| B6.8 | Non-neutral source refused, packages byte-equal, menu reason | NOT_COVERED | W363 addresses W358's governed-flight finding. R451 requires the reason in the launching session only; no reason survives restart. Packages stay byte-equal, with no claim, receipt or registration. [W363](W363.md). | `forced-W363-B6-GOVERNED` (`W363_B6_GOVERNED`); GameTest `BayDeploymentGameTests.anEscrowBoundSourceIsRefusedWithBaySourceProtectedAndKeepsItsPackages` remains direct-D0 evidence only. | PENDING |
| B7.1 | D6 complete before retirement, loaded source, offline seat | PASS | Common | `forced-CD` (`W185_B7_SAVED`) | PENDING |
| B7.2 | D6 complete before retirement, cold source chunk, offline seat | PASS | Common | `forced-CD` (`W185_B7_COLD`) | PENDING |
| B7.3 | Login while retirement is queued: no settlement, fresh proofs | NOT_COVERED | W331; after the handoff the normal transfer carries the rider to the destination, unlike the GameTest's handoff-time view | `forced-W331-L` (`W331_B7_LOGIN_QUEUED`) | PENDING |
| B8.1 | Retirement staged only, no passengers | PASS | Common | `forced-B` (`B8_STAGED`) | PENDING |
| B8.2 | Retirement replaced before the callback, no passengers | PASS | Common | `forced-B` (`B8_MOVED`) | PENDING |
| B8.3 | Retirement staged only, offline seat | PASS | Common | `forced-E` (`W325_B8_STAGED_OFFLINE`) | PENDING |
| B8.4 | Retirement replaced, offline seat | PASS | Common | `forced-E`, `forced-E-retry` (`W325_B8_MOVED_OFFLINE`) | PENDING |
| B8.5 | Retirement staged only, online passenger | PASS | Common | `forced-E`, `forced-E-retry` (`W325_B8_STAGED_ONLINE`) | PENDING |
| B8.6 | Retirement replaced, online passenger | PASS | Common | `forced-E`, `forced-E-retry` (`W325_B8_MOVED_ONLINE`) | PENDING |
| B8.7 | Partial multi-REGISTERED group retirement, before replacement | NOT_COVERED | W331 (three bays: two register, one research-locked); W341's complete receipt set closes W338's finding 1 for this group | `forced-W331-G` (`W331_B8_TRIO_STAGED`) | PENDING |
| B8.8 | Partial multi-REGISTERED group retirement, after replacement | NOT_COVERED | W331; W341 | `forced-W331-G` (`W331_B8_TRIO_MOVED`) | PENDING |
| B9.1 | Registration refused after INTENT, REJECTED staged only | PASS | Common | `forced-A` (`B9_STAGED`) | PENDING |
| B9.2 | REJECTED replaced before the callback | PASS | Common | `forced-A` (`B9_MOVED`) | PENDING |
| B9.3 | A refused package later succeeds with the same operation | PASS | Common | `forced-GH-retry`, `forced-H` (`W325_B9_RETRY`) | PENDING |
| B10.1 | Blocked registry, unreleased INTENT hold | PASS | Common | `forced-A`, `forced-A-retry` (`B10_BLOCKED`) | PENDING |
| B10.2 | Coherent blocked INTENT, release flag staged only | PASS | Common | `released` (`B10_BLOCKED_STAGED`) | PENDING |
| B10.3 | Coherent blocked INTENT, release flag replaced | PASS | Common | `released` (`B10_BLOCKED_MOVED`) | PENDING |
| B10.4 | Coherent REPAIR, release flag staged only | PASS | Common | `released` (`B10_REPAIR_STAGED`) | PENDING |
| B10.5 | Coherent REPAIR, release flag replaced | PASS | Common | `released` (`B10_REPAIR_MOVED`) | PENDING |
| B10.6 | Incoherent signature authority stays frozen | NOT_COVERED | W358 builds the incoherent REPAIR seed; receipt authority and packages stay frozen. [W358](W358.md). | `forced-W358-B10-INCOHERENT` (`W358_B10_INCOHERENT`); released-conflict GameTests remain separate. | PENDING |
| B11.1 | Shared server pending saturation, bay-only fill of 64 | PASS | Common | `forced-A`, `forced-A-retry` (`B11_FULL`) | PENDING |
| B11.2 | Per rocket: 7 escrow and 1 bay against the limit of 8 | NOT_COVERED | Reasoned unreachable: active escrow prevents launch, and bayNeutral precedes D1 even for a historical seven-operation root. [W358](W358.md), feasibility. | None natively; F3.2 checks the synthetic authority boundary, not launch. | PENDING |
| B11.3 | Retained 4,096 per server, independent of pending | PASS | Common | `forced-F` (`W325_RETAINED_GLOBAL`) | PENDING |
| B11.4 | Retained 128 per owner, independent of pending | PASS | Common | `forced-F` (`W325_RETAINED_OWNER`) | PENDING |
| B11.5 | REPAIR operations keep their charges across restarts | PASS | Common | `forced-F` (`W325_REPAIR_FULL`) | PENDING |
| B11.6 | Released operations keep saturated capacity across restarts | PASS | Common | `forced-F` (`W325_RELEASED_FULL`) | PENDING |
| B11.7 | UNKNOWN, captured and successor copies counted once across restarts | NOT_COVERED | W331 retains captured/successor coverage. W358 holds UNKNOWN across a retry; the unacknowledged successor does not survive restart. [W358](W358.md). | `forced-W331-U` (`W331_B11_UNION`), plus `forced-W358-B11-UNKNOWN` (`W358_B11_UNKNOWN`). | PENDING |
| B11.8 | A REJECTED re-arm is refused at full capacity and later reuses its operation | NOT_COVERED | W358 fills/unfills its fixture owner's receipts, checks refusal, then later same-operation consumption. [W358](W358.md). | `forced-W358-B11-REARM` (`W358_B11_REARM`). | PENDING |
| B11.9 | Mixed consumers: 63 escrow and 1 bay, and the reverse | NOT_COVERED | W358 uses checked typed seeds for both directions: loaded mixed capacity, not live creation of 63 fills. [W358](W358.md). | `forced-W358-B11-ESCROW-MAJOR` (`W358_B11_ESCROW_MAJOR`) and `forced-W358-B11-BAY-MAJOR` (`W358_B11_BAY_MAJOR`). | PENDING |
| B12.1 | D1 receipt, definite failure before replacement | PASS | Common | `forced-A` (`B12_FAILED`) | PENDING |
| B12.2 | D1 receipt UNKNOWN, exact retry | PASS | Common | `forced-A` (`B12_UNKNOWN`) | PENDING |
| B12.3 | D4 receipt UNKNOWN, exact retry | PASS | Common | `forced-B` (`B12_D4`) | PENDING |
| B12.4 | D7 receipt UNKNOWN, exact retry | PASS | Common | `forced-B` (`B12_D7`) | PENDING |
| B12.5 | D4 receipt, definite failure before replacement | PASS | Common | `forced-G` (`W325_D4_FAILED`) | PENDING |
| B12.6 | D7 receipt, definite failure before replacement | PASS | Common | `forced-G`, `forced-GH-retry` (`W325_D7_FAILED`) | PENDING |
| B13.1 | Registry checkpoint definitely FAILED, then a later successful checkpoint | PASS | Common | `forced-B` (`B13_FAILED`) | PENDING |
| B13.2 | Registry checkpoint UNKNOWN, then a later successful checkpoint | PASS | Common | `forced-B` (`B13_UNKNOWN`) | PENDING |
| B13.3 | Durable INTENT at restart: first-tick launch and decommission fences | PASS | Common | `forced-B` (`B2_STAGED`) | PENDING |
| B14.1 | Unproved transaction journal, stopped before the checkpoint completes | PASS | Common | `forced-A` (`B14_T1`) | PENDING |
| B14.2 | A stop drains the journal request without the D1 callback | PASS | Common | `forced-A`, `forced-A-retry` (`B14_STOP`) | PENDING |
| B14.3 | The journal request fails with no assembly record | NOT_COVERED | W331, through a definite failure of the checkpoint D0 requested; the REFUSED answer stays GameTest-only | `forced-W331-T` (`W331_B14_FAILED`) | PENDING |
| B14.4 | A stale assembly record of L remains at D0 | NOT_COVERED | W331; the stale record names L in a dimension that never loads | `forced-W331-T` (`W331_B14_STALE`) | PENDING |
| B15.1 | D4 receipt replaced, then an exception and readback | PASS | Common | `forced-B` (`B15_D4`) | PENDING |
| B15.2 | D7 receipt replaced, then an exception and readback | PASS | Common | `forced-B` (`B15_D7`) | PENDING |
| B15.3 | D1 receipt replaced, then an exception and readback | NOT_COVERED | W331 (new gated cut value `B15_D1`) | `forced-W331-W` (`W331_B15_D1`) | PENDING |
| B15.4 | Registry replaced, then an exception and readback | NOT_COVERED | W331 | `forced-W331-W` (`W331_B15_REGISTRY`) | PENDING |
| B15.5 | Transaction journal replaced, then an exception and readback | NOT_COVERED | W331; the oracle requires D0's own requested write | `forced-W331-T` (`W331_B15_JOURNAL`) | PENDING |
| B16.1 | A captured retirement is invalidated by a superseding source save | PASS | Common | `forced-CD` (`B16_SAVE`) | PENDING |
| B16.2 | ... by a late duplicate join | PASS | Common | `forced-CD` (`B16_JOIN`) | PENDING |
| B16.3 | ... by the source leaving and reloading | PASS | Common | `forced-CD` (`B16_LEAVE`) | PENDING |
| B16.4 | ... by source motion | PASS | Common | `forced-CD` (`B16_MOTION`) | PENDING |
| B17.1 | Unresolved work ahead of a healthy group still lets it progress | PASS | Common | `forced-A`, `forced-A-retry` (`B17_FAIR`) | PENDING |
| B17.2 | A REPAIR group ahead of healthy groups | PASS | Common | `forced-F` (`W325_REPAIR_FAIR`) | PENDING |
| B17.3 | An unloaded group ahead of healthy groups | NOT_COVERED | W363's readiness seam holds one group's end. Its second form leaves a healthy restart group behind it; R451 fixes the R2-slot starvation this exposed. [W363](W363.md). | `forced-W363-B17-UNLOADED` (`W363_B17_UNLOADED`) and `forced-W363-B17-UNLOADED-RESTART` (`W363_B17_UNLOADED_RESTART`). | PENDING |
| B17.4 | Partial D1, immutable group hashes | NOT_COVERED | W331; W341 | `forced-W331-G` (`W331_B17_PARTIAL_D1`) | PENDING |
| B17.5 | A mixed REGISTERED, REJECTED and REPAIR group | NOT_COVERED | Reasoned unreachable: repair stops D1/D2, REGISTERED members publish together, and REJECTED is not a pending restart member. No forged triple is substituted. [W358](W358.md). | None natively; the exact triple has no named test. [W358](W358.md) lists the guard evidence. | PENDING |
| B18.1 | Clean stop with an active INTENT | PASS | Common | `forced-A` (`B18_INTENT`) | PENDING |
| B18.2 | Clean stop with an active REJECTED | PASS | Common | `forced-A` (`B18_REJECTED`) | PENDING |
| B18.3 | Clean stop at the REGISTERED replacement | PASS | Common | `forced-B` (`B18_D4`) | PENDING |
| B18.4 | Clean stop at the retirement replacement | PASS | Common | `forced-B` (`B18_D7`) | PENDING |
| B18.5 | Crash teardown with no callback completion | PASS | Common | `forced-A` (`B18_CRASH`) | PENDING |
| B18.6 | Clean stop with a successor write queued | NOT_COVERED | W331 | `forced-W331-W` (`W331_B18_SUCCESSOR`) | PENDING |
| B18.7 | Clean stop with an unresolved UNKNOWN write | NOT_COVERED | W331 | `forced-W331-W` (`W331_B18_UNKNOWN`) | PENDING |
| CAP.1 | 16 pending operations of one owner refuse bay admission, packages unchanged | PASS | Common | `forced-E`, `forced-E-retry` (`W325_OWNER_FULL`) | PENDING |
| CAP.2 | Another owner keeps bay admission room | PASS | Common | `forced-E`, `forced-E-retry` (`W325_OTHER_OWNER_ROOM`) | PENDING |
| CAP.3 | The owner cap refuses a loader before native capture | NOT_COVERED | W358's real loader is refused before capture, with unchanged native envelopes and queue/admission counts, and no ledger. [W358](W358.md). | `forced-W358-CAP-OWNER` (`W358_CAP_OWNER`); F3.8 remains separate. | PENDING |
| EXIT.0 | Control: held passenger, no exit | PASS | Common (the exit runner has the candidate argument and installed-JAR check) | `exit` (`X0_CONTROL`) | PENDING |
| EXIT.1 | Sneak-input exit, live fall offer | PASS | Common | `exit` (`X1_OFFER`) | PENDING |
| EXIT.2 | Ticked-connection descent, detached player data flushed | PASS | Common | `exit` (`X2_DESCENT`) | PENDING |
| EXIT.3 | Correction window before the tracker acknowledges | PASS | Common | `exit` (`X3_CORRECTION`) | PENDING |
| EXIT.4 | Landing timed like a real client: one protected FALL, exit flushed | PASS | Common | `exit` (`X4_LANDING`) | PENDING |
| EXIT.5 | Fixed 200-tick expiry with a ticked connection, exit flushed | PASS | Common | `exit` (`X5_EXPIRY`) | PENDING |
| EXIT.6 | Trusted movement revokes the slot without a remount | PASS | Common | `exit` (`X6_TRUSTED`) | PENDING |
| EXIT.7 | A same-Level teleport revokes protection; a later landing is unprotected | PASS | Common | `exit` (`X7_REVOKED`) | PENDING |
| EXIT.8 | Held-menu SAFE exit onto a loaded pad, exit flushed | PASS | Common | `exit` (`X8_SAFE_MENU`) | PENDING |
| EXIT.9 | Same-tick protected landing, mapped-observer smoke | PASS | Common | `exit` (`X9_FIRST_TICK`) | PENDING |
| EXIT.10 | Same-tick teleport-revoked landing, mapped-observer smoke | PASS | Common | `exit` (`X10_FIRST_TICK_REVOKED`) | PENDING |

The bay exit GameTests changed too (W334 chooses an exit area free of block entities; W344 waits for setup readiness
without advancing the test clock). They are hosted regression tests, not W325 rows, and both changes are test-side only.

#### Native rows added since C1

None of these rows existed at C1. Their smokes ran on locally built JARs.

| Scenario | Added by | Evidence before C3 | C4 family | C4 |
|---|---|---|---|---|
| B6.5 at the source-save boundary: the source's removal saved, the destination never saved, then a kill; restart 1 rebuilds the destination from T′, restart 2 keeps it | W346, with W349's stopped-world inventory scan after W348's review | PASS on W346's JAR (`5ef21e2aa`) and again on W349's JAR (`56668d542`); a scratch JAR with W342's mutation M1 failed at restart 1, as intended | `forced-W346-R` | PENDING |
| Bay receipts present but not loaded: every rocket held, satellite launch and decommission DEPLOYMENT_PENDING, insertion refused, all five kinds refused; recovery at S2 | W332, W343 | PASS (W332 smoke 4; W343 `smoke-N`) | `r042-N_BAY` | PENDING |
| Escrow ledger present but not loaded: bay admission refused with the four cargo kinds and fuel | W332, W343 | PASS; the vanilla-dismount row stays NOT_COVERED | `r042-L_LEDGER` | PENDING |
| The shared pool full server-wide: the bay menu shows DEPLOYMENT_CAPACITY | W332, W343 | PASS at S0, S1 and S2 | `r042-P_SERVER` | PENDING |
| One owner full: the bay menu shows OWNER_PENDING_FULL | W332, W343 | PASS; the bay uses a fixed owner, so its admission of another owner is not shown here (CAP.2 covers it) | `r042-P_OWNER` | PENDING |
| Receipt presence unknown at the pre-start check: startup refused, then clean recovery | W332 could not reach it; W340 showed a delete-pending handle reaches it; W343 restored it; W345 applied Root decision R416's metadata boundary | PASS on W345's JAR; whole-world byte equality stays false because vanilla updates `level.dat` | `r042-U_BAY` | PENDING |
| Ledger presence unknown at the pre-start check | As above | As above | `r042-U_LEDGER` | PENDING |
| Waiting-time observations for R-043 (section 9.2) | W336 | All nine timing profiles PASS with both restarts | `w336-timing-B12_D4`, `-B12_D7`, `-B13_FAILED`, `-B13_UNKNOWN`, `-B16_MOTION`, `-B10_BLOCKED`, `-RECONNECT`, `-TURNS-2`, `-TURNS-3`, `-D6_RETRY` | PENDING |
| Unsupported bay roots in the per-Level 256-coordinate pool, with 255 fuel-loader denials as background | W336 | PASS with both restarts | `w336-saturation-bay`; the fuel control `w336-saturation-fuel` uses bay denials as background | PENDING |
| Downgrade to W176's old JAR with a placed bay and actual receipts | W336 | W176's whole-world oracle FAILED on all three old-JAR attempts (unwaived); complete-backup restoration and both candidate starts passed. The historical-JAR downgrade checks are expected to fail under R-044 until the owner decides it (R429); W339 keeps a non-PASS status from being reported as PASS | `w336-downgrade` | PENDING |

#### GameTest rows of W325-MATRIX

The retained test inventory and historical execution come from [C3-SWITCH-REPORT](C3-SWITCH-REPORT.md),
section 3.2. Each named test must be confirmed again in C4's hosted shard results. Class names are short; W333's
packages are in [W333-DESIGN](W333-DESIGN.md).

| Row | Variant | C1 | Changed since C1 | Test in C4's hosted run | C4 |
|---|---|---|---|---|---|
| F2.1 | DATA replay after a removed definition; no recreated initial mission | PASS | None | `SatelliteRegistryProofGameTests.removedDefinitionReplayExtractsDataPackageWithoutRecreatingInitialMission` | PENDING |
| F2.2 | Fresh DATA deterministic initial mission | NOT_COVERED | W333 | `BayMissionRowGameTests.initialMissionProofReplayAndPromotionMatrix` (NONE) | PENDING |
| F2.3 | Cancel the initial mission before D3 or D4, then replay and promotion | NOT_COVERED | W333 | Same method (CANCEL) | PENDING |
| F2.4 | Claim the initial mission before D3 or D4 | NOT_COVERED | W333 | Same method (CLAIM) | PENDING |
| F2.5 | Prune the initial mission's history before D3 or D4 | NOT_COVERED | W333 | Same method (PRUNE) | PENDING |
| F2.6 | Replace the initial mission before D3 or D4 | NOT_COVERED | W333 | Same method (REPLACE) | PENDING |
| F2.7 | A later coalesced flush or autosave succeeds after a failed checkpoint | NOT_COVERED | W333 | Same method (coalesced and autosave variants) and `SatelliteReplayBarrierRowGameTests.failedCheckpointThenLaterWriteStillProvesReplay` | PENDING |
| F2.8 | Changed owner, definition or kind conflicts | NOT_COVERED | W333 | `SatelliteReplayRowGameTests.identityConflictsPrecedeReplay` | PENDING |
| F3.1 | The bay fills the shared 64; a loader is refused until capacity is released | PASS | None | `BayLoaderSaturationGameTests.bayIntentsFillingTheSharedPendingSlotsStopALoaderUntilTheyResolve` | PENDING |
| F3.2 | 63 escrow and 1 bay, the reverse, and 7 escrow and 1 bay per rocket | NOT_COVERED | W333 | `BayAdmissionRowGameTests.mixedConsumersShareGlobalAndRocketLimits` | PENDING |
| F3.3 | One count over durable, captured, successor and UNKNOWN copies | NOT_COVERED | W333 | `BayAdmissionRowGameTests.writerCopiesChargeOnePendingSlot` | PENDING |
| F3.4 | 64 REPAIR or released operations keep capacity after a reload | NOT_COVERED | W333 | `BayAdmissionRowGameTests.repairAndReleasedReceiptsStayFullAfterReload` | PENDING |
| F3.5 | A REJECTED re-arm is refused when full and later admitted with the same operation | NOT_COVERED | W333 | `BayAdmissionRowGameTests.rejectedRearmWaitsForExactCapacityRelease` | PENDING |
| F3.6 | Independent retained limits of 4,096 and 128; mixed-store startup | NOT_COVERED | W333 | `BayAdmissionRowGameTests.retainedLimitsAreIndependentAndStartupIndexesBothStores` | PENDING |
| F3.7 | A failed or UNKNOWN retirement keeps its reservation | NOT_COVERED | W333 | `BayAdmissionRowGameTests.failedAndUnknownRetirementKeepReservations` | PENDING |
| F3.8 | 16 per owner: unchanged refusal, room for other owners | PASS | None | `BayLoaderSaturationGameTests.sixteenOwnedBaysRefuseBeforeLoaderCaptureAndLeaveOtherOwnersCapacity` | PENDING |
| F4.1 | Matching DATA identity, removed definition, terminal extraction | PASS | None | `SatelliteRegistryProofGameTests.removedDefinitionReplayExtractsDataPackageWithoutRecreatingInitialMission` | PENDING |
| F4.2 | Non-data terminal extraction, idempotent | PASS | None | `SatelliteKindTerminalGameTests.terminalLaunchesAnIdleSurveySatelliteAndConsumesAReplay` | PENDING |
| F4.3 | Removed components, changed stats or research, changed or removed target | NOT_COVERED | W333 | `SatelliteReplayRowGameTests.replayBypassesChangedDependenciesAtTerminalAndService` | PENDING |
| F4.4 | Every satellite, mission, account and byte quota full | NOT_COVERED | W333 | `SatelliteReplayRowGameTests.replayBypassesFullQuotasAtTerminalAndService` | PENDING |
| F4.5 | Dirty matching X, failed persistence, exact barrier | NOT_COVERED | W333 | `SatelliteReplayBarrierRowGameTests.dirtyReplayKeepsPackageUntilExactAcknowledgement` | PENDING |
| F4.6 | Replace then throw, exact frozen readback | NOT_COVERED | W333 | `SatelliteReplayBarrierRowGameTests.replaceThenThrowCompletesReplayOnceFromFrozenReadback` | PENDING |
| F4.7 | Receipt-phase launch and decommission fences | PASS | None | `SatelliteRegistryProofGameTests.terminalLaunchAndDecommissionWaitForExactReceiptPhases` | PENDING |
| F4.8 | Conflict, unauthorized caller, blocked registry, other-operation fence | NOT_COVERED | W333 | `SatelliteReplayRowGameTests.identityConflictsPrecedeReplay` and the barrier fixture | PENDING |
| F5.1 | Missing physical body, DATA and solar | PASS | None | `BayDeploymentMatrixGameTests.f5RefusesAMissingBodyForDataAndSolarBays` | PENDING |
| F5.2 | Body that cannot be orbited, DATA and solar | PASS | None | `BayDeploymentMatrixGameTests.f5RefusesANonOrbitableBodyForDataAndSolarBays` | PENDING |
| F5.3 | Legacy Space body, DATA and solar | PASS | None | `BayDeploymentMatrixGameTests.f5RefusesTheLegacySpaceBodyForDataAndSolarBays` | PENDING |
| F5.4 | The captured source body is the one the DATA receipt records | PASS | None | `BayDeploymentMatrixGameTests.theCapturedSourceBodyIsTheOneTheDataReceiptRecords` | PENDING |
| F5.5 | Station orbit differs from the destination and changes later | NOT_COVERED | W333 | `BayStationRowGameTests.capturedOrbitSurvivesLaterStationChangeForBothKinds` | PENDING |
| F5.6 | Undiscovered allowed body: DATA discovery against the non-data gate | NOT_COVERED | W333 | `SatelliteReplayRowGameTests.undiscoveredAllowedBodyAdmitsDataButNotNonData` | PENDING |
| F5.7 | REGISTERED replay after the physical catalog entry is removed | NOT_COVERED | W333 | `BayMissionRowGameTests.initialMissionProofReplayAndPromotionMatrix` (REGISTERED_REMOVED variants) | PENDING |
| RT.1 | Coalesced and autosave requests publish on later ticks | PASS | W347 found why this test failed once in W342's local run; W350 opens its measured window only when the registry writer is idle. W350 is in C3, so its hosted run includes the corrected window | `SatelliteRegistryTickGameTests.coalescedAndAutosaveRequestsArePublishedOnLaterTicks` | PENDING |
| RT.2 | Launch and decommission wait for the commit acknowledgement | PASS | None | `SatelliteRegistryTickGameTests.aTerminalLaunchAndDecommissionCompleteOnTicksAfterTheirCommits` | PENDING |
| RT.3 | A DATA action shows its stored result only after its commit | PASS | None | `SatelliteRegistryTickGameTests.aDataActionShowsItsStoredResultAfterItsCommit` | PENDING |
| RT.4 | A clean schema-4 reload leaves the literal and save epochs unchanged | NOT_COVERED | W333 | `SatelliteTransportRowGameTests.cleanSchemaFourReloadIsAnObservation` | PENDING |
| RT.5 | Tagged origins, surveys, accounts and blocked roots across every write entry and outcome | NOT_COVERED | W333; a GameTest cannot stop its server, so the close drain is not a stopped-server autosave | `SatelliteTransportRowGameTests.allWriteEntriesPreserveTaggedAndBlockedRoots` | PENDING |
| RT.6 | Replace then throw without a duplicate epoch or a lost pending flush | NOT_COVERED | W333 | `SatelliteTransportRowGameTests.replaceThenThrowAdvancesOnceAndKeepsLaterPendingMutation` | PENDING |
| RT.7 | Obsolete callbacks and stale hashes cannot obtain a permit | NOT_COVERED | W333 | `SatelliteTransportRowGameTests.obsoleteSessionProofCannotCoverCurrentWatch` | PENDING |
| N2.1 | UNKNOWN, then mission, claim or survey changes, a fresh checkpoint and a reload | NOT_COVERED | W333 | `SatelliteEpochRowGameTests.unknownCheckpointResolvesFresh` | PENDING |
| N2.2 | The same, resolved by a coalesced flush | NOT_COVERED | W333 | `SatelliteEpochRowGameTests.unknownCoalescedWriteResolvesFresh` | PENDING |
| N2.3 | The same, resolved by an autosave | NOT_COVERED | W333 | `SatelliteEpochRowGameTests.unknownAutosaveResolvesFresh` | PENDING |
| N2.4 | A definite FAILED retries with a fresh capture | NOT_COVERED | W333 | `SatelliteEpochRowGameTests.failedCheckpointRetriesFresh` | PENDING |
| N2.5 | A second UNKNOWN skips another carried epoch | NOT_COVERED | W333 | `SatelliteEpochRowGameTests.secondUnknownSkipsAnotherEpoch` | PENDING |
| N2.6 | Every waiting consumer stays fenced until resolution; no rematerialized reward | NOT_COVERED | W333 | `SatelliteEpochRowGameTests.waitingConsumersRemainFencedUntilResolution` | PENDING |

W333 also ran each family against a scratch copy with one protection disabled, and every family's new tests failed
there ([W333](W333.md), "Negative protection checks").

#### GameTests added since C1

| Scenario | Added by | Test in C4's hosted run | C4 |
|---|---|---|---|
| B6.5 main case: a consumed, committed group with neither entity rebuilds the destination from T′, with the consumed operations, no package back and no receipt | W342 | `BayRebuildDestinationGameTests.aConsumedCommittedGroupWithNeitherEntityRebuildsTheDestinationFromTPrime` | PENDING |
| B6.5 control: the same state with the source present keeps the consumed source | W342 | `BayRebuildDestinationGameTests.theSameConsumedStateWithItsSourcePresentKeepsTheConsumedSource` | PENDING |
| B6.5 control: an unconsumed committed record rebuilds the destination with the packages aboard | W342 | `BayRebuildDestinationGameTests.anUnconsumedCommittedRecordRebuildsItsDestinationWithThePackagesAboard` | PENDING |
| The bay neutral-source check fails while the ledger is NOT_LOADED, and passes under NO_FILE | W329 | One test of each pair in `EscrowLedgerNotLoadedGameTests` (batches `w329_ledger_not_loaded` and `w329_ledger_no_file`); the other 14 tests are shared (section 8.4) | PENDING (2) |

#### GameTests added since C3

| Scenario | Added by and source | Test in C4's hosted run | C4 |
|---|---|---|---|
| A governed flight refuses its packaged bays at ascent completion with BAY_SOURCE_PROTECTED, preserving packages without a deployment claim. | [W363](W363.md), R444/R451. | `BayGovernedFlightGameTests.aGovernedFlightRefusesItsBaysWithBaySourceProtectedAndKeepsItsPackages` | PENDING |
| A restart group waiting on an absent Level leaves the R2 slot for the healthy restart group behind it. | [W363](W363.md), R451; the first native restart-form smoke stays failed in history. | `BayResumeFairnessGameTests.aWaitingRestartGroupDoesNotBlockTheRestartGroupBehindIt` | PENDING |

### 3.3 Applicable risks

Every entry below is in the [risk register](../../../11-RISK-REGISTER.md). The register entries now carry W327's
corrections M1 to M6, L3 and L4, as reported by [C3-SWITCH-REPORT](C3-SWITCH-REPORT.md). "Live now" means that the risk applies with
the switch off.

| Entry | What applies to the bay | Scope | Duration | Recovery | Evidence |
|---|---|---|---|---|---|
| R-038 | An unsupported bay root or package identity protects the bay: no player removal, no drop, no extraction or deployment. A root that native saving cannot reproduce refuses its terrain chunk's save. Live now. | One bay; its terrain chunk; the Level after 256 distinct refused coordinates, a pool that every protected device family shares (R-044) | Until offline repair; a restart alone does not clear it. W336 saw a restart reset the in-memory pool and the Level saturate again once the roots loaded. | Stop the server, back up the whole world, repair the bay root on a copy, and verify one clean save and two restarts | `w336-saturation-bay`; code cited in W327's section 3.1 |
| R-039 | The receipt store. A future or invalid root refuses world start. Since W328, an unknown presence of any managed file refuses world start (`ARCE-BETA-2000`). A full or unavailable store refuses new receipts, and blocking receipts refuse package insertion. | The whole world (startup refusal); the server's bay admission (store unavailable); one satellite's packages (insertion) | Startup refusal until the cause is fixed. An UNKNOWN write retries the same candidate every 200 ticks; after three failures the store is unavailable until a checkpoint commits. | Automatic after storage repair; fix storage access and restart; a supporting JAR or whole-world offline repair for an invalid root | B12.1 to B12.6, B15.1 to B15.3, B18.1 to B18.7; `r042-U_BAY`; W328 and W328F unit tests; `w336-timing-B12_D4` and `-B12_D7` |
| R-040 | The transaction journal. D0 waits for the journal write that covers L's absence. An unreadable journal rejects every in-session deployment at D0 and refuses all assembly and disassembly. | One rocket held in TRANSIT (write wait); the server (unreadable journal) | No limit for the write wait; the session until repair for an unreadable journal | Automatic for the write wait; whole-world offline repair for an unreadable journal | B14.1 to B14.4, B15.5 |
| R-041 | The satellite registry. D0 rejects every bay of a new deployment with REGISTRY_BLOCKED, D3 waits, and restart INTENT rows hold for the session. A registry outage affects every satellite. | One deployment; one claim; every satellite during an outage | D3 has no upper bound; restart holds last the session | Automatic once the registry commits; a restart | B2.1, B2.2, B10.1, B13.1 to B13.3, B15.4; the F2, F4, RT and N2 GameTests; `w336-timing-B13_FAILED`, `-B13_UNKNOWN` and `-B10_BLOCKED` |
| R-042 | The coordinator holds a rocket while its deployment is pending. NOT_LOADED holds every rocket. The shared pending pool (64 per server, 8 per rocket, 16 per owner) serves the fuel loader, the four cargo kinds and the bay. | One rocket and the stations that overlap its snapshot (a claim); the whole server (NOT_LOADED, a full pool) | Until the claim ends, with no timeout; the session for NOT_LOADED and runtime holds; until offline repair, across restarts, for durable REPAIR receipts, released receipts and the pool slots they keep | Automatic when the claim's steps complete; a restart for runtime holds and a transient NOT_LOADED; otherwise whole-world offline repair. Released and REPAIR receipts have no online release. | B1 to B4, B9 to B11, B17, B18 and CAP rows; the F3 GameTests; the six `r042-*` families; section 9.1 |
| R-043 | Registration, consumption, copy recovery and released flight. Every wait lacks a timeout. A stale copy outside the census, or a single-file rollback, can bring back a consumed package or register a satellite twice (the register's risk D items 1 to 4). Recovery of existing receipts is live now. | One rocket and its packages; the server (the D6 turn queue, the shared cargo work budget); chunks (tickets on up to 2 + 32 columns per claim) | No timeouts; tickets end up to 400 ticks after their last renewal | Automatic retries; a restart reclassifies; whole-world offline repair for conflicts | B5 to B8, B16, B10.2 to B10.5, EXIT.0 to EXIT.10, B6.5 (W342, W346); the ten `w336-timing-*` families; section 9.2 |
| R-035 | The transfer journal: when it is unreadable, blocked or unacknowledged, all transfers pause and D0, RESUME, D5, D6 and released flights wait. Also the shared check columns and the exclusion of R-032. | The server | Until the journal is acknowledged; for an unreadable journal, the session until repair | As R-035 describes | B3.12 and the forced-stop rows that restart through the journal |
| R-044, shared pool | Bay denials count toward the per-Level pool of 256 coordinates that about 15 device families share (W327-M5). | The Level | Until the Level closes; it saturates again after a restart once the roots load (W336) | As R-038 | `w336-saturation-bay`, and `w336-saturation-fuel`, which uses bay denials |
| R-044, downgrade |An older JAR rewrites `level.dat` before its transfer-journal guard refuses, and removes the registry IDs of newer blocks, including `rocket_satellite_bay` (W327-M4). Historical-JAR downgrade checks are expected to fail under R-044 until the owner decides it (R428, R429). Ledger root 3 is a second old-JAR refusal reason even without a governed transfer record; metadata rewriting on that path is not yet tested ([R044-SIGNOFF-DRAFT](R044-SIGNOFF-DRAFT.md), section 3; R458).| The world | Permanent unless the world is restored from a complete backup | Restore the complete pre-downgrade backup | `w336-downgrade`; W176's whole-world verdict stays FAILED |
| R-032 | Unobservable vanilla write failures, for example a stale source entity of an earlier flight, which R-043 names as the stale-copy trigger. | One rocket and its packages | Permanent once it happens | A whole-world backup | Cannot be shown natively; not accepted |

The register also names these bay residuals, none accepted: the neutral-source limit (BAY_SOURCE_PROTECTED, for W27f);
a source rocket pushed by a piston (SOURCE_AUTHORITY_CONFLICT, for W27e); the passenger-exit residuals of R264, R274,
R283 and R288; the passenger-leave permit N4 (W27e); conflict latches that are not cleared online; unknown REPAIR
reasons; and W196-R21.

### 3.4 Remaining work

**Disposition of C3's native gaps.** Every original gap row is retained below. Sources are
[W358](W358.md) and [W363](W363.md), with R444/R451/R455 in [QUEUE](../QUEUE.md).
These dispositions are mappings, not C4 results.

| Row | Disposition since C3 | Evidence to fill or limit | C4 |
|---|---|---|---|
| B6.8 | Governed-flight refusal is recorded; its reason is session-only under R451. | `forced-W363-B6-GOVERNED`; `forced-W358-B6-BOUND` is excluded and keeps its failed smokes. | PENDING |
| B10.6 | The incoherent REPAIR fixture is built. | `forced-W358-B10-INCOHERENT` | PENDING |
| B11.2 | The native launch shape is unreachable under production rules. | W358's argument; F3.2 checks the synthetic authority boundary only. | PENDING |
| B11.7, UNKNOWN part | The held writer crosses a retry with UNKNOWN/captured/successor copies. | `forced-W358-B11-UNKNOWN`; the unacknowledged successor is not restored. | PENDING |
| B11.8 | Owner fill/unfill and two relaunches are built. | `forced-W358-B11-REARM` | PENDING |
| B11.9 | Both loaded mixed-consumer directions use typed seeds. | `forced-W358-B11-ESCROW-MAJOR` and `forced-W358-B11-BAY-MAJOR` | PENDING |
| B17.3 | Both live-behind-held and restart-behind-held forms are built; R451 fixes slot starvation. | `forced-W363-B17-UNLOADED` and `forced-W363-B17-UNLOADED-RESTART` | PENDING |
| B17.5 | The requested triple-phase live group is unreachable without forged receipts. | W358's producer/guard argument is not a native pass. | PENDING |
| CAP.3 | The owner-full fixture now visits the real loader before capture. | `forced-W358-CAP-OWNER` | PENDING |

**Limits of the covered rows.**

- B6.5: `forced-W331-R` passes KEEP_SOURCE with the consumed source on disk; it does not reconstruct the destination
  ([W331](W331.md), "Findings from the smokes", item 4; [W355](W355.md), audit limit 2). W346's different
  source-save-boundary case is the destination-reconstruction evidence, not a relabelling of W331's case.
- B11.7: W358 now supplies UNKNOWN separately from W331's captured/successor union. Its negative control tests cap
  refusal, not every copy-deduplication branch ([W358](W358.md), native results).
- B6.5: W346's case does not show the natural timing of an autosave against the flight, a crash inside one Level's
  save, the order where the destination saves first, or passengers ([W346](W346.md), "Not done").
- R-042 families: [W360](W360.md) argues the vanilla-dismount shape of `r042-L_LEDGER` unreachable through supported
  production flow; it is not an executable pass. N_BAY and L_LEDGER inject a failure over valid bytes, not persistent
  real corruption. U and the new post-prestart X presence fixtures are Windows-only; X gives native post-check
  evidence (section 9.1). The P_OWNER bay owner is fixed, so another owner's bay admission is not demonstrated
  there ([W332](W332.md), "Not done", carried by [W343](W343.md) and [W345](W345.md); [W355](W355.md), audit limit 3).
- Downgrade: W176's whole-world verdict stays FAILED and is not waived. The historical-JAR checks are expected to
  fail under R-044 until the owner's decision (R428, R429). Downgrading needs the pre-upgrade backup.

**Deferred by the owner.** Multiplayer and performance stay last, and the owner confirms when the full campaign starts
([OWNER-DECISIONS-20261109](OWNER-DECISIONS-20261109.md)). For the bay this means:

- V1: the bay menu statuses and the OWNER_PENDING_FULL text.
- V2: exits, online passengers and S3. The release-test gate refuses real clients, so the owner must decide how the
  real-client cases are driven (R-042, R-043).
- The reference node: D6 and barrier cost, D3 and registry-writer cost, and the real waiting times of section 9.2.
- A soak of at least four hours.

The C1 report estimated about 0.5 to 1 day to prepare the bay's part of the campaign and about half a day for an
R-043 step-timing measurement plan.

## 4. ITEM_CARGO_LOAD (`block_variant:loader/3`)

### 4.1 The switch, and what is live with it off

- **The switch.** `EscrowCargoSwitches.production(ITEM_CARGO_LOAD)` returns false
  ([C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 2.2; R459 makes no admission change).
- **Root recommendation: PLACEHOLDER — PENDING ROOT REVIEW for ITEM_CARGO_LOAD.**
- **Live with the switch off.** The item loader is craftable at C3 (historical baseline) (`rocket_item_loader`), so its device protection
  (R-035's cargo paragraph) already applies. Reconciliation of existing ledger records runs on every server, and the
  kind is refused under either NOT_LOADED state (W327, section 4). The register states that production worlds have no
  ledger while admission is off (R-031, R-035). This historical statement does not exempt a world with existing
  escrow history from migration or protective reads (section 12).

### 4.2 Scenarios

**Changes common to every cargo native row since C1.** W330 changed the gated cargo cut probes and the settlement-cut
implementations in the JAR, and the cargo controllers and settlement oracles in Python; the wrapper now requires the
candidate commit and JAR SHA-256. W341 added the installed-JAR rechecks. W337 runs the families through the wrapper.
"Common" means only these changes.

| Rows | Cases (boundary) | C1 | Changed since C1 | C4 family | C4 |
|---|---|---|---|---|---|
| N091 to N095 | `IC_LOAD_K2` (K2), `IC_LOAD_K3` (C2_DESTINATION_AFTER), `IC_LOAD_K4` (source-only store), `IC_LOAD_K5` (destination-only store), `IC_LOAD_K9` (K9) | PASS (5) | Common | `cargo-D-S1` | PENDING (5) |
| N101 | `IC_PARTIAL_APPLY` (C2_DESTINATION_AFTER) | PASS | Common | `cargo-D-S1` | PENDING |
| Load settlement, a C1 gap (Root decision R395) | 13 cases: `IS_LOAD_O1_AFTER`, `IS_LOAD_O2_ROCKET_BEFORE`, `IS_LOAD_O2_ROCKET_AFTER`, `IS_LOAD_O2_DEVICE_BEFORE`, `IS_LOAD_O2_DEVICE_AFTER`, `IS_LOAD_O3_AFTER`, `IS_LOAD_O3J_AFTER`, `IS_LOAD_O4_AFTER`, `IS_LOAD_PRUNE_AFTER`, `IS_LOAD_BIND_AFTER`, `IS_LOAD_BIND_VETO`, `IS_LOAD_ABANDON_ROCKET`, `IS_LOAD_ABANDON_DEVICE` | No case at C1 | W330. Smoke: `IS_LOAD_O3_AFTER` passed with both restarts, and a scratch JAR with pruning disabled was rejected; W330 did not smoke the other 12 cases. W355's audit later confirms all 13 passed on the historical second candidate | `cargo-D-S2-LOAD` | PENDING (13) |
| Q03, per-owner cap | `CQ_ITEM_LOAD`: 16 durable pending operations charge owner A; this kind's real device is refused before preparation; owner B completes once | NOT_COVERED | W330; smoke PASS (section 8.2) | `cargo-C-Q` | PENDING |
| Shared pool, added since C1 | Unsupported item-loader roots against 255 fuel-loader denials | No case at C1 | W336; smoke PASS | `w336-saturation-item-load` | PENDING |

#### Item settlement cuts added since C3

Each row is from [W360](W360.md), row results. Its development smoke is not a C4 result. Pre-O1 retains REPAIR
without inventing a choice; veto keeps the frozen intent and locked endpoints.

| Row | Case | Source | C4 family | C4 |
|---|---|---|---|---|
| Load O1_BEFORE | `IS_LOAD_O1_BEFORE` | [W360](W360.md) | `cargo-D-S2-LOAD` | PENDING |
| Load BIND_BEFORE | `IS_LOAD_BIND_BEFORE` | [W360](W360.md) | `cargo-D-S2-LOAD` | PENDING |
| Load O3_BEFORE | `IS_LOAD_O3_BEFORE` | [W360](W360.md) | `cargo-D-S2-LOAD` | PENDING |
| Load O3J_BEFORE | `IS_LOAD_O3J_BEFORE` | [W360](W360.md) | `cargo-D-S2-LOAD` | PENDING |
| Load O4_BEFORE | `IS_LOAD_O4_BEFORE` | [W360](W360.md) | `cargo-D-S2-LOAD` | PENDING |
| Load PRUNE_BEFORE | `IS_LOAD_PRUNE_BEFORE` | [W360](W360.md) | `cargo-D-S2-LOAD` | PENDING |
| Load VETO_DESTINATION_SAVED | `IS_LOAD_VETO_DESTINATION_SAVED` | [W360](W360.md) | `cargo-D-S2-LOAD` | PENDING |

| GameTest row | Scenario | C1 | Changed since C1 | Test in C4's hosted run | C4 |
|---|---|---|---|---|---|
| D-E-A01 | Native load and unload, partial counts, queued competitor, typed readback, L2 and C8 | PASS | None | `ItemCargoReconciliationGameTests.realLoadAndUnloadRetireAndServeTheQueuedCompetitor` | PENDING |
| D-E-A02 | Named F1 fence | PASS | None | `ItemCargoReconciliationGameTests.namedTransferRemainsPreparedBehindTheExistingF1Fence` | PENDING |
| D-E-A03 | An unavailable item isolates its component | PASS | None | `ItemCargoReconciliationGameTests.unavailableSavedItemHoldsOnlyItsNativeComponent` | PENDING |
| D-E-A04 | A mismatched frozen replay leaves both endpoints unchanged | PASS | None | `ItemCargoReconciliationGameTests.mismatchedFrozenReplayDigestHoldsWithBothEndpointsUnchanged` | PENDING |
| D-E-A05 | Load neutral normalization and veto | PASS | None | `ItemCargoSettlementGameTests.heldLoadNormalizesItsNeutralRocketOnlyAfterTheVetoClears` | PENDING |
| D-E-A12 | Two item-mutated flights, then the latest disassembly | PASS | None | `ItemCargoFlightGameTests.twoCargoMutatedFlightsThenTheLatestCargoDisassemblesExactlyOnce` | PENDING |

The shared rows of section 8 also apply to this kind: M01 to M09, the B3 and G2 reuse families, the six R-042 families,
W329's GameTests plus W360's two item-device holds, the downgrade family, and X01/X02 (section 13).

### 4.3 Applicable risks

| Entry | What applies to this kind | Scope | Duration | Recovery | Evidence |
|---|---|---|---|---|---|
| R-035, cargo scope (W11c, W11d) | One active cargo operation per rocket; settlement O1 to O4 and C0 to C8 REPAIR; rockets with settlement history, a READY head or legacy scalar fuel are refused; cut-E2 (an entity record over the inline-sector limit keeps its operation and endpoints locked); device protection, live now; the shared journal-writer stall. Mod chest subclasses, containers with unresolved loot tables and over-cap stacks do not move. | One device and one rocket; its terrain chunk; the Level after 256 coordinates (shared pool); the journal-writer stall is R-035's server-wide transfer availability | Device protection until its cause is resolved (a restart alone does not clear it); escrow locks until proved reconciliation or operator settlement; no timeouts | Automatic retry for retryable IO; audited settlement; whole-world offline repair (keep every file and hash, repair a copy, two saves and restarts, replace the whole world) | N091 to N095, N101; `cargo-D-S2-LOAD`; D-E rows above |
| R-035, ledger presence and NOT_LOADED (W328, W329) | A ledger that did not load, or whose presence becomes unknown after the pre-start check, holds the whole server: no rocket launches, fills, loads or unloads cargo, or is disassembled, and every device holding a root is locked. An unknown presence at the pre-start check refuses world start. | The whole server; the world for the startup refusal | The session | Fix storage access and restart; a restart that loads the ledger or proves it absent clears it | `r042-L_LEDGER`, `r042-U_LEDGER`; W329's GameTests; W328's unit tests |
| R-031 | A future or invalid ledger root refuses world start. | The world | Until a supporting JAR or offline repair | Whole-world offline repair | [W360](W360.md): `r042-R031_FUTURE` and `r042-R031_INVALID`; C4 results in section 8.3 are pending. The smoke used root 2; C4 must verify the activated boundary. |
| R-039, shared admission authority | The authority answers UNAVAILABLE for every consumer until both stores have loaded. The pool of 64 per server, 8 per rocket and 16 per owner is shared with the fuel loader and the bay. | The server; one owner | While the pool is full; while either store is NOT_LOADED | Slots free as operations resolve; offline repair where bay receipts hold them (R-042) | `CQ_ITEM_LOAD`; `r042-P_SERVER`, `r042-P_OWNER` |
| R-042, bay effects on cargo | Bay receipts NOT_LOADED refuse cargo binding and admission on the whole server; W332 observed the answer `TRANSFER_JOURNAL_UNPROVED`. Released and REPAIR bay receipts can hold the pool: eight such rockets refuse every cargo operation on the server, and two full rockets of one owner refuse that owner. | The whole server; one owner | The session (NOT_LOADED); until offline repair, across restarts (held pool) | A restart for a transient NOT_LOADED; whole-world offline repair | `r042-N_BAY`, `r042-P_SERVER`, `r042-P_OWNER` |
| R-043, shared cargo work budget | One budget per tick (16 MiB and 16,384 visits) serves the preparation of all four cargo kinds and the bay's D4 snapshot job and D5 copies. Each D5 copy takes the whole tick's budget (W327-L3). | The server | Per tick, with no fairness, while bay recovery runs | None needed | No C3 family measures it; W327 notes that it needs concurrent bay and cargo load |
| R-044, shared pool | Item-loader denials count toward the per-Level pool of 256 coordinates. | The Level | Until the Level closes; it saturates again after a restart once the roots load | Whole-world offline repair | `w336-saturation-item-load` |
| R-044, downgrade |The old JAR removes the registry ID of `rocket_item_loader`, among others. Historical-JAR downgrade checks are expected to fail under R-044 until the owner decides it (R428, R429). Ledger root 3 is a second old-JAR refusal reason even without a governed transfer record; metadata rewriting on that path is not yet tested ([R044-SIGNOFF-DRAFT](R044-SIGNOFF-DRAFT.md), section 3; R458).| The world | Permanent unless restored from a complete backup | Restore the complete pre-downgrade backup | `w336-downgrade` |
| Not accepted (R-035) | R-031, R-032, single-file rollback, OS crash and power loss, and UNKNOWN settlement loss. | One rocket to the world | Permanent once it happens | A whole-world backup | X01 and X02, NOT_COVERED |

### 4.4 Remaining work

- C4 must run the fourteen now-built W360 item cuts, the R-031/post-check presence families and the two item-hold
  GameTests. C3's missing-fixture description no longer applies ([W360](W360.md)).
- X01/X02 use R457's non-destructive E4 simulation. Results and real-storage residuals are in section 13; no new
  destructive run is authorized ([OWNER-DECISIONS-20261117](OWNER-DECISIONS-20261117.md)).
- The shared cargo work budget remains unmeasured. The new R-031 families do not accept its risk.
- Deferred V1/V2, reference-node benchmarks, W90's cold-destination/readiness check and the four-hour soak remain
  as recorded in [C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 4.4. Root prepares them; real-client account
  sessions and risk signatures remain the owner's part of the final packet.

## 5. ITEM_CARGO_UNLOAD (`block_variant:loader/2`)

### 5.1 The switch, and what is live with it off

- **The switch.** `EscrowCargoSwitches.production(ITEM_CARGO_UNLOAD)` returns false
  ([C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 2.2; R459 makes no admission change).
- **Root recommendation: PLACEHOLDER — PENDING ROOT REVIEW for ITEM_CARGO_UNLOAD.**
- **Live with the switch off.** The item unloader is craftable at C3 (historical baseline) (`rocket_item_unloader`); otherwise as in section
  4.1.

### 5.2 Scenarios

"Common" has the meaning of section 4.2.

| Rows | Cases (boundary) | C1 | Changed since C1 | C4 family | C4 |
|---|---|---|---|---|---|
| N096 to N100 | `IC_UNLOAD_K2` (K2), `IC_UNLOAD_K3` (C2_DESTINATION_AFTER), `IC_UNLOAD_K4` (source-only store), `IC_UNLOAD_K5` (destination-only store), `IC_UNLOAD_K9` (K9) | PASS (5) | Common | `cargo-D-S1` | PENDING (5) |
| N102 | `IC_PARTIAL_STORE` (source-only store) | PASS | Common | `cargo-D-S1` | PENDING |
| N103, N104 | `IR_CONTAINER` and `IR_LOOT` (before an offline damage edit); W143's earlier failures, rerun afresh at C1 | PASS (2) | Common | `cargo-D-S2` | PENDING (2) |
| N105 to N117 | `IS_O1_AFTER`, `IS_O2_ROCKET_BEFORE`, `IS_O2_ROCKET_AFTER`, `IS_O2_DEVICE_BEFORE`, `IS_O2_DEVICE_AFTER`, `IS_O3_AFTER`, `IS_O3J_AFTER`, `IS_O4_AFTER`, `IS_PRUNE_AFTER`, `IS_BIND_AFTER`, `IS_BIND_VETO`, `IS_ABANDON_ROCKET`, `IS_ABANDON_DEVICE` | PASS (13) | Common; W330 extended the shared item settlement oracle for the load cuts | `cargo-D-S2` | PENDING (13) |
| N118 to N120 | `IR_DISASSEMBLY` (K1), `IR_REBUILD` (rebuild refused), `IR_ROOT` (root preserved) | PASS (3) | Common | `cargo-D-S2` | PENDING (3) |
| Q04, per-owner cap | `CQ_ITEM_UNLOAD` | NOT_COVERED | W330; smoke PASS | `cargo-C-Q` | PENDING |
| Shared pool, added since C1 | Unsupported item-unloader roots against 255 fuel-loader denials | No case at C1 | W336; smoke PASS | `w336-saturation-item-unload` | PENDING |

#### Item settlement cuts added since C3

Each row is from [W360](W360.md), row results. Its development smoke is not a C4 result. Pre-O1 retains REPAIR
without inventing a choice; veto keeps the frozen intent and locked endpoints.

| Row | Case | Source | C4 family | C4 |
|---|---|---|---|---|
| Unload O1_BEFORE | `IS_O1_BEFORE` | [W360](W360.md) | `cargo-D-S2` | PENDING |
| Unload BIND_BEFORE | `IS_BIND_BEFORE` | [W360](W360.md) | `cargo-D-S2` | PENDING |
| Unload O3_BEFORE | `IS_O3_BEFORE` | [W360](W360.md) | `cargo-D-S2` | PENDING |
| Unload O3J_BEFORE | `IS_O3J_BEFORE` | [W360](W360.md) | `cargo-D-S2` | PENDING |
| Unload O4_BEFORE | `IS_O4_BEFORE` | [W360](W360.md) | `cargo-D-S2` | PENDING |
| Unload PRUNE_BEFORE | `IS_PRUNE_BEFORE` | [W360](W360.md) | `cargo-D-S2` | PENDING |
| Unload VETO_DESTINATION_SAVED | `IS_VETO_DESTINATION_SAVED` | [W360](W360.md) | `cargo-D-S2` | PENDING |

| GameTest row | Scenario | C1 | Changed since C1 | Test in C4's hosted run | C4 |
|---|---|---|---|---|---|
| D-E-A01 | Native load and unload, partial counts, queued competitor, typed readback, L2 and C8 | PASS | None | `ItemCargoReconciliationGameTests.realLoadAndUnloadRetireAndServeTheQueuedCompetitor` | PENDING |
| D-E-A02 | Named F1 fence | PASS | None | `ItemCargoReconciliationGameTests.namedTransferRemainsPreparedBehindTheExistingF1Fence` | PENDING |
| D-E-A03 | An unavailable item isolates its component | PASS | None | `ItemCargoReconciliationGameTests.unavailableSavedItemHoldsOnlyItsNativeComponent` | PENDING |
| D-E-A04 | A mismatched frozen replay leaves both endpoints unchanged | PASS | None | `ItemCargoReconciliationGameTests.mismatchedFrozenReplayDigestHoldsWithBothEndpointsUnchanged` | PENDING |
| D-E-A06 | Unload survivor proofs, typed readback, O3J, O4, pruning and a READY launch | PASS | None | `ItemCargoSettlementGameTests.heldUnloadProvesBothSurvivorsPrunesThemAndLaunchesFromReady` | PENDING |
| D-E-A07 | Unload neutral source normalization and veto | PASS | None | `ItemCargoSettlementGameTests.heldUnloadNormalizesItsNeutralSourceOnlyAfterTheVetoClears` | PENDING |
| D-E-A08 | ABANDON with the rocket missing | PASS | None | `ItemCargoSettlementGameTests.abandonProvesTheMissingRocketAndTheSurvivingDevice` | PENDING |
| D-E-A09 | ABANDON with the device missing | PASS | None | `ItemCargoSettlementGameTests.abandonProvesTheMissingDeviceAndSurvivingRocket` | PENDING |
| D-E-A10 | An unavailable item blocks before O2 normalization and readback | PASS | None | `ItemCargoSettlementNativeGameTests.unavailableNativeItemHoldsOnlyItsSettlementBeforeO2` | PENDING |
| D-E-A11 | The latest item remainder is restored exactly once | PASS | None | `ItemCargoLandedDisassemblyGameTests.retainedLegacyRecordDisassemblesExactlyTheLatestCargoOnce` | PENDING |
| D-E-A12 | Two item-mutated flights, then the latest disassembly | PASS | None | `ItemCargoFlightGameTests.twoCargoMutatedFlightsThenTheLatestCargoDisassemblesExactlyOnce` | PENDING |

The shared rows of section 8 apply as listed for section 4.2.

### 5.3 Applicable risks

Every row of section 4.3 applies to this kind, with these differences in evidence:

- R-035's cargo scope is evidenced by N096 to N100, N102 and N103 to N120 and the D-E rows above. Its rule that
  containers with unresolved loot tables do not move is exercised by `IR_LOOT`, and the container rule by
  `IR_CONTAINER`. Latest-cargo disassembly is `IR_DISASSEMBLY`, and the protected-root rule is `IR_ROOT`.
- R-039 is evidenced by `CQ_ITEM_UNLOAD` with the `r042-P_*` families.
- R-044's shared pool is evidenced by `w336-saturation-item-unload`; the downgrade removes `rocket_item_unloader`'s ID.
  The root-3 second refusal reason of section 4.3 also applies ([R044-SIGNOFF-DRAFT](R044-SIGNOFF-DRAFT.md), section 3).

Scope, duration and recovery are as in section 4.3.

### 5.4 Remaining work

As section 4.4: candidate reruns of the now-built item/shared rows, X01/X02 simulation and residuals, the
unmeasured shared budget and deferred campaign. Section 5.2 lists the seven unload cuts.

## 6. FLUID_CARGO_LOAD (`block_variant:loader/5`)

### 6.1 The switch, and what is live with it off

- **The switch.** `EscrowCargoSwitches.production(FLUID_CARGO_LOAD)` returns false
  ([C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 2.2; R459 makes no admission change).
- **Root recommendation: PLACEHOLDER — PENDING ROOT REVIEW for FLUID_CARGO_LOAD.**
- **Live with the switch off.** The fluid loader is craftable at C3 (historical baseline) (`rocket_fluid_loader`); otherwise as in section
  4.1.

### 6.2 Scenarios

"Common" has the meaning of section 4.2.

| Rows | Cases (boundary) | C1 | Changed since C1 | C4 family | C4 |
|---|---|---|---|---|---|
| N001 to N014 | `FC_LOAD_PREPARING`, `FC_LOAD_L1_BEFORE` (K1), `FC_LOAD_L1_REPLACED` (K2), `FC_LOAD_L1_ACK`, `FC_LOAD_C2_SOURCE_AFTER`, `FC_LOAD_C2_DESTINATION_AFTER`, `FC_LOAD_SOURCE_ONLY_STORE`, `FC_LOAD_DESTINATION_ONLY_STORE`, `FC_LOAD_SECOND_FORCE_BEFORE`, `FC_LOAD_C6_JOINT_READBACK`, `FC_LOAD_K8`, `FC_LOAD_L2_REPLACED` (K9), `FC_LOAD_C7_ACKNOWLEDGED`, `FC_LOAD_C8_RELEASED` | PASS (14) | Common | `cargo-C-S1` | PENDING (14) |
| N029 to N032 | `FC_LOAD_D2_C2`, `FC_LOAD_D2_C5`, `FC_LOAD_D2_C6`, `FC_LOAD_D2_C7` | PASS (4) | Common | `cargo-C-S2` | PENDING (4) |
| N037 to N044 | `FC_LOAD_E1_OWN_STORE`, `FC_LOAD_E2_CAPTURE`, `FC_LOAD_E2_ROCKET_EXTERNAL`, `FC_LOAD_E4L1_FAIL`, `FC_LOAD_E4L1_UNKNOWN`, `FC_LOAD_E4L2_UNKNOWN`, `FC_LOAD_E3_CACHE_CLOSE`, `FC_LOAD_E5_STALLED`; E3 and E5 were W138's earlier failures, rerun afresh at C1 | PASS (8) | Common | `cargo-C-S2` | PENDING (8) |
| N045 to N054 | `FC_LOAD_R1_255`, `FC_LOAD_R1_256`, `FC_LOAD_R2_DUPLICATE`, `FC_LOAD_R3_CURSOR`, `FC_LOAD_R3_OTHER`, `FC_LOAD_R3_RELOCATED`, `FC_LOAD_G1_FUTURE`, `FC_LOAD_G1_INVALID`, `FC_LOAD_G3_L1_INFLIGHT`, `FC_LOAD_G3_L2_UNKNOWN` | PASS (10) | Common; W352 restores native R1's synthetic fixture fuel in C3; all ten cases passed on W352's JAR (section 2.1.1) | `cargo-C-S3` | PENDING (10) |
| N075 to N078 | `FF_N1`, `FF_F1_AFTER_CARGO`, `FF_F6_AFTER_CARGO`, `FF_F8_AFTER_CARGO` | PASS (4) | Common | `cargo-C-S5` | PENDING (4) |
| N087 to N090 | `FF_GOVERNED_MISSING`, `FF_GOVERNED_TERMINAL`, `FF_TWO_FLIGHTS`, `FF_CANCEL_AFTER_CARGO`; the last three were W142's earlier failures, rerun afresh at C1 | PASS (4) | Common | `cargo-C-S5` | PENDING (4) |
| Load settlement, a C1 gap (Root decision R395) | 20 cases: `FS_LOAD_O1_BEFORE`, `FS_LOAD_O1_AFTER`, `FS_LOAD_BIND_BEFORE`, `FS_LOAD_BIND_AFTER`, `FS_LOAD_O2_ROCKET_BEFORE`, `FS_LOAD_O2_ROCKET_AFTER`, `FS_LOAD_O2_DEVICE_BEFORE`, `FS_LOAD_O2_DEVICE_AFTER`, `FS_LOAD_O3_BEFORE`, `FS_LOAD_O3_AFTER`, `FS_LOAD_O3J_BEFORE`, `FS_LOAD_O3J_AFTER`, `FS_LOAD_O4_BEFORE`, `FS_LOAD_O4_AFTER`, `FS_LOAD_PRUNE_BEFORE`, `FS_LOAD_PRUNE_AFTER`, `FS_LOAD_ABANDON_ROCKET`, `FS_LOAD_ABANDON_DEVICE`, `FS_LOAD_VETO_DESTINATION_SAVED`, `FS_LOAD_BIND_VETO` | No case at C1 | W330. Smoke: `FS_LOAD_O3_AFTER` passed with both restarts, and a scratch JAR with pruning disabled was rejected; the other 19 cases were not smoked | `cargo-C-S4-LOAD` | PENDING (20) |
| Q01, per-owner cap | `CQ_FLUID_LOAD` | NOT_COVERED | W330; smoke PASS | `cargo-C-Q` | PENDING |
| Shared pool, added since C1 | Unsupported fluid-loader roots against 255 fuel-loader denials | No case at C1 | W336; smoke PASS | `w336-saturation-fluid-load` | PENDING |

| GameTest row | Scenario | C1 | Changed since C1 | Test in C4's hosted run | C4 |
|---|---|---|---|---|---|
| C-E-A01 | Real load and unload; the first ledger; side effects of a disabled switch | PASS | None | `FluidCargoAdmissionGameTests.realLoadAndUnloadReachPreparedWithoutPublishingResources` | PENDING |
| C-E-A02 | Native replay, typed store and readback, L2 and C8, competing devices | PASS | None | `FluidCargoReconciliationGameTests.realLoadAndUnloadRetireAndServeTheQueuedCompetitor` | PENDING |
| C-E-A03 | Named F1 fence | PASS | None | `FluidCargoReconciliationGameTests.namedTransferRemainsPreparedBehindTheExistingF1Fence` | PENDING |
| C-E-A04 | An unavailable fluid isolates its component | PASS | None | `FluidCargoReconciliationGameTests.unavailableSavedFluidHoldsOnlyItsNativeComponent` | PENDING |
| C-E-A05 | Load neutral normalization and veto | PASS | None | `FluidCargoSettlementGameTests.heldLoadNormalizesItsNeutralRocketOnlyAfterTheVetoClears` | PENDING |
| C-E-A11 | Two cargo-mutated flights, a successor mutation and the latest disassembly | PASS | None | `FluidCargoFlightGameTests.twoCargoMutatedFlightsThenTheLatestCargoDisassemblesExactlyOnce` | PENDING |
| C-E-A14 | Exact-source cancellation after cargo | NOT_COVERED | W330 | `CargoRecoveryAcceptanceGameTests.exactSourceCancellationPreservesTheLatestCargo` | PENDING |

The shared rows of section 8 also apply, including W329's device-hold GameTest, which uses a fluid cargo device.

### 6.3 Applicable risks

Every row of section 4.3 applies, with these differences:

- R-035's cargo scope adds the tank and fluid payload rules: a saved fluid that is unavailable holds only its native
  component (`ARCE_ESCROW_FLUID_UNAVAILABLE`, evidenced by C-E-A04). Its other evidence is N001 to N014, N029 to N032,
  N037 to N054, N075 to N078, N087 to N090, `cargo-C-S4-LOAD` and the C-E rows above. The item-only rules about
  containers and stacks do not apply.
- R-035's accounting is implemented in Slices A to D with versioned oracles, but activation and native evidence are
  not established by those unit-tested slices. Section 12 gives the format, holds, migration, shared fuel-loader scope
  and pending evidence ([W364](W364.md), [W366](W366.md), [QUEUE](../QUEUE.md), W372).
- R-039 is evidenced by `CQ_FLUID_LOAD`; R-044's shared pool by `w336-saturation-fluid-load`; the downgrade removes
  `rocket_fluid_loader`'s ID.

Scope, duration and recovery are as in section 4.3.

### 6.4 Remaining work

- Reviewed accounting activation and candidate evidence remain open (section 12).
- X01/X02 simulation/residuals, the unmeasured shared budget and deferred campaign remain open (sections 4.4 and 13).
  W360's R-031 families need C4 results, not a new claim of no family.

## 7. FLUID_CARGO_UNLOAD (`block_variant:loader/4`)

### 7.1 The switch, and what is live with it off

- **The switch.** `EscrowCargoSwitches.production(FLUID_CARGO_UNLOAD)` returns false
  ([C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 2.2; R459 makes no admission change).
- **Root recommendation: PLACEHOLDER — PENDING ROOT REVIEW for FLUID_CARGO_UNLOAD.**
- **Live with the switch off.** The fluid unloader is craftable at C3 (historical baseline) (`rocket_fluid_unloader`); otherwise as in
  section 4.1.

### 7.2 Scenarios

"Common" has the meaning of section 4.2.

| Rows | Cases (boundary) | C1 | Changed since C1 | C4 family | C4 |
|---|---|---|---|---|---|
| N015 to N028 | `FC_UNLOAD_PREPARING`, `FC_UNLOAD_L1_BEFORE` (K1), `FC_UNLOAD_L1_REPLACED` (K2), `FC_UNLOAD_L1_ACK`, `FC_UNLOAD_C2_SOURCE_AFTER`, `FC_UNLOAD_C2_DESTINATION_AFTER`, `FC_UNLOAD_SOURCE_ONLY_STORE`, `FC_UNLOAD_DESTINATION_ONLY_STORE`, `FC_UNLOAD_SECOND_FORCE_BEFORE`, `FC_UNLOAD_C6_JOINT_READBACK`, `FC_UNLOAD_K8`, `FC_UNLOAD_L2_REPLACED` (K9), `FC_UNLOAD_C7_ACKNOWLEDGED`, `FC_UNLOAD_C8_RELEASED` | PASS (14) | Common | `cargo-C-S1` | PENDING (14) |
| N033 to N036 | `FC_UNLOAD_D2_C2`, `FC_UNLOAD_D2_C5`, `FC_UNLOAD_D2_C6`, `FC_UNLOAD_D2_C7` | PASS (4) | Common | `cargo-C-S2` | PENDING (4) |
| N055 to N074 | `FS_O1_BEFORE`, `FS_O1_AFTER`, `FS_BIND_BEFORE`, `FS_BIND_AFTER`, `FS_O2_ROCKET_BEFORE`, `FS_O2_ROCKET_AFTER`, `FS_O2_DEVICE_BEFORE`, `FS_O2_DEVICE_AFTER`, `FS_O3_BEFORE`, `FS_O3_AFTER`, `FS_O3J_BEFORE`, `FS_O3J_AFTER`, `FS_O4_BEFORE`, `FS_O4_AFTER`, `FS_PRUNE_BEFORE`, `FS_PRUNE_AFTER`, `FS_ABANDON_ROCKET`, `FS_ABANDON_DEVICE`, `FS_VETO_DESTINATION_SAVED`, `FS_BIND_VETO` | PASS (20) | Common; W330 extended the shared fluid settlement oracle for the load cuts | `cargo-C-S4` | PENDING (20) |
| N079 to N086 | `FF_DISASSEMBLY_REMOVAL`, `FF_DISASSEMBLY_RESTORATION`, `FF_DISASSEMBLY_RELEASE`, `FF_REBUILD_DESTINATION`, `FF_KEEP_SOURCE`, `FF_REBUILD_SOURCE`, `FF_STALE_PREPARED`, `FF_RECONNECT` | PASS (8) | Common | `cargo-C-S5` | PENDING (8) |
| Q02, per-owner cap | `CQ_FLUID_UNLOAD` | NOT_COVERED | W330; smoke PASS | `cargo-C-Q` | PENDING |
| Shared pool, added since C1 | Unsupported fluid-unloader roots against 255 fuel-loader denials | No case at C1 | W336; smoke PASS | `w336-saturation-fluid-unload` | PENDING |
| X03 | The operator's whole-world repair after REBUILD_SOURCE; `FF_REBUILD_SOURCE` shows a protected FLOOR_CONFLICT result, not a repair | NOT_COVERED | [W367-DESIGN](W367-DESIGN.md), with [QUEUE](../QUEUE.md), W367/R463, plans a narrow whole-copy repair. | None in the existing selection. `cargo-X03-REPAIR` is proposed, not delivered evidence; section 13. | PENDING |

| GameTest row | Scenario | C1 | Changed since C1 | Test in C4's hosted run | C4 |
|---|---|---|---|---|---|
| C-E-A01 | Real load and unload; the first ledger; side effects of a disabled switch | PASS | None | `FluidCargoAdmissionGameTests.realLoadAndUnloadReachPreparedWithoutPublishingResources` | PENDING |
| C-E-A02 | Native replay, typed store and readback, L2 and C8, competing devices | PASS | None | `FluidCargoReconciliationGameTests.realLoadAndUnloadRetireAndServeTheQueuedCompetitor` | PENDING |
| C-E-A03 | Named F1 fence | PASS | None | `FluidCargoReconciliationGameTests.namedTransferRemainsPreparedBehindTheExistingF1Fence` | PENDING |
| C-E-A04 | An unavailable fluid isolates its component | PASS | None | `FluidCargoReconciliationGameTests.unavailableSavedFluidHoldsOnlyItsNativeComponent` | PENDING |
| C-E-A06 | Both survivor proofs, readback, pruning and a READY launch | PASS | None | `FluidCargoSettlementGameTests.heldUnloadProvesBothSurvivorsPrunesThemAndLaunchesFromReady` | PENDING |
| C-E-A07 | Unload neutral source normalization and veto | PASS | None | `FluidCargoSettlementGameTests.heldUnloadNormalizesItsNeutralSourceOnlyAfterTheVetoClears` | PENDING |
| C-E-A08 | ABANDON with the rocket missing | PASS | None | `FluidCargoSettlementGameTests.abandonProvesTheMissingRocketAndTheSurvivingDevice` | PENDING |
| C-E-A09 | ABANDON with the device missing | NOT_COVERED | W330 | `FluidCargoSettlementGameTests.abandonProvesTheMissingDeviceAndSurvivingRocket` | PENDING |
| C-E-A10 | The latest cargo is restored exactly once under a retained legacy record | PASS | None | `FluidCargoLandedDisassemblyGameTests.retainedLegacyRecordDisassemblesExactlyTheLatestCargoOnce` | PENDING |
| C-E-A11 | Two cargo-mutated flights, a successor mutation and the latest disassembly | PASS | None | `FluidCargoFlightGameTests.twoCargoMutatedFlightsThenTheLatestCargoDisassemblesExactlyOnce` | PENDING |
| C-E-A12 | Real cargo, then a stale PREPARED record with both entities and a protected keeper | NOT_COVERED | W330 | `CargoRecoveryAcceptanceGameTests.stalePreparedWithBothEntitiesKeepsProtectedCargoAndRequiresSnapshotIdentity` | PENDING |
| C-E-A13 | A disembarked passenger reconnects after cargo | NOT_COVERED | W330; a simulated reconnect is not V2 | `CargoRecoveryAcceptanceGameTests.disembarkedPassengerReconnectCannotReplayPreCargoAuthority` | PENDING |

The shared rows of section 8 also apply, including W329's device-hold GameTest.

### 7.3 Applicable risks

Every row of section 4.3 applies, with these differences:

- R-035's cargo scope adds the tank and fluid payload rules, as in section 6.3. Its evidence is N015 to N028, N033 to
  N036, N055 to N086 and the C-E rows above. REBUILD_SOURCE ends in a protected FLOOR_CONFLICT that needs the operator's
  whole-world repair (X03).
- The accounting activation/evidence boundary is as in section 6.3; section 12 applies to this kind.
- R-039 is evidenced by `CQ_FLUID_UNLOAD`; R-044's shared pool by `w336-saturation-fluid-unload`; the downgrade removes
  `rocket_fluid_unloader`'s ID.

Scope, duration and recovery are as in section 4.3.

### 7.4 Remaining work

- X03 follows the narrow offline design and Root's R463 decisions; implementation, reviews, native evidence and final
  operator text are still required (section 13).
- Accounting activation/native evidence remain as in section 6.4.
- X01/X02 simulation/residuals, the unmeasured shared budget and deferred campaign remain as in section 4.4.

## 8. Rows shared by the four cargo switches

### 8.1 Changed-boundary rows of W326

| Row | Boundary | C1 | Changed since C1 | C4 coverage | C4 |
|---|---|---|---|---|---|
| M01 | Ordinary prefix and apply, both save orders | PASS | Common | The C-S1, C-S2 and D-S1 families and the reconciliation GameTests | PENDING |
| M02 | Composite binding and floor; shared ledger and floor reuse | NOT_COVERED | W330 added the reuse family | `cargo-B3-S5-REUSE`: missing, replaced and behind ledger, and every distinct, coalesced and saturated floor variant | PENDING |
| M03 | Neutral normalization and veto | PASS | W330 added the load counterparts | C-S4, D-S2, C-S4-LOAD and D-S2-LOAD, and the settlement GameTests | PENDING |
| M04 | O2, O3, O3J, O4 and pruning | PASS | As M03 | As M03 | PENDING |
| M05 | Recognition, reconnect, replay and cancellation; shared flight reuse | NOT_COVERED | W330 added the A1 tests and the reuse family | C-S5; C-E-A12 to A14; `cargo-B3-S6-REUSE` (including governed P1-N2 and P1-F9, not ordinary LF_S6_F9_RESPAWN, which is NOT_COVERED under W211 R203 and R427) | PENDING |
| M06 | Latest-cargo disassembly | PASS | Common | The three C-S5 disassembly windows and `IR_DISASSEMBLY` | PENDING |
| M07 | Creation, storage and shutdown; shared writer reuse | NOT_COVERED | W330 added the reuse families | `cargo-B3-S1-REUSE` (N0, N2 definite and UNKNOWN, N3), `cargo-B3-S4-REUSE`, `cargo-B3-S6-REUSE` (LF_S6_F9_RESPAWN is NOT_COVERED under W211 R203 and R427); C-S5's N1 and C-S2's E3 and E5 | PENDING |
| M08 | Protected roots and native save scope; shared lifecycle reuse | NOT_COVERED | W330 added the reuse families | `cargo-G2-REUSE` (the fuel loader's S1 campaign, with W176's old JAR; its historical-JAR downgrade checks are expected to fail under R-044 until the owner decides it, R428) and `cargo-B3-S6-REUSE` (LF_S6_F9_RESPAWN is NOT_COVERED under W211 R203 and R427); the C-S3 and D-S2 roots | PENDING |
| M09 | Cargo chains that do not apply | PASS (scope only) | None | No cargo chain is admitted, and no chain cut exists | PENDING |

### 8.2 Shared families

| Family | What it runs | Evidence before C3 | C4 |
|---|---|---|---|
| `cargo-C-Q` | The four cap cases of sections 4 to 7. Sixteen durable REPAIR operations on 16 other rockets charge owner A within the real 64, 8 and 16 limits. A's real device is refused with its complete ledger, device and rocket envelopes unchanged, across both restarts; owner B completes once without using A's capacity. | All four passed with both restarts (W330). One cap case also ran on a scratch JAR with the cap check disabled and was rejected, and all 12 retained archives passed the later complete-root comparison. | PENDING (4) |
| `cargo-B3-S1-REUSE` | 7 shared loader cases: creation refusal and baseline (N0, N2, N3). | Not run by W330 or on C1; W354's corrected fixture passed all seven with both restarts before C3 (section 2.1.1) | PENDING (7) |
| `cargo-B3-S4-REUSE` | 15 shared loader cases: storage and reconciliation. | Historical fuel-loader passes; the family also passed on the second candidate after its main run (section 2.1.1) | PENDING (15) |
| `cargo-B3-S5-REUSE` | 8 shared loader cases: ledger missing, replaced or behind, and floor variants. | Historical fuel-loader passes; the family also passed on the second candidate after its main run (section 2.1.1) | PENDING (8) |
| `cargo-B3-S6-REUSE` | 96 selected shared loader S6 cases: checkpoint proofs, recovery, shutdown, settlement and flight. LF_S6_F9_RESPAWN is excluded as NOT_COVERED under W211 R203 and R427. | Historical selected-case passes, including the nine later cases after the second candidate's main run; the excluded ordinary F9 attempts remain failed, not passes (section 2.1.1) | PENDING (96) |
| `cargo-G2-REUSE` | The fuel loader's nine-start S1 campaign, with W176's old JAR and its migration, downgrade, protected-root and two-restart oracles. Its historical-JAR downgrade checks are expected to fail under R-044 until the owner decides it (R428). | Six historical rows passed; three downgrade rows failed and remain unwaived (section 2.1.1) | PENDING |

The shared fuel-loader limits above are those of [W355](W355.md). Ordinary `LF_S6_F9_RESPAWN` is NOT_COVERED
([W211](W211.md), R203; R427), not replaced by the governed P1-F9 evidence. The fuel loader's first BI veto attempt
stays FAILED; its later passing attempt is separate evidence ([W222](W222.md), "Evidence, limits and remaining
work"; [W355](W355.md), audit limit 3). Neither historical failure is relabelled by the C3 selection.

### 8.3 The R-042 families

The original six `r042-*` families remain; section 9.1 retains their historical observations.
[W360](W360.md) adds four families. Its R-031 fixture used supported root 2 plus one as its future form; C4's
activated current/future schema boundary must be pinned rather than treating W360's smoke as activated evidence.
Its R416 boundary preserves every managed/non-metadata file; only the allowed LastPlayed/metadata rotation differs.

| Scenario | Source | C4 family and case | C4 |
|---|---|---|---|
| R-031 future ledger root refuses before READY; restored fixture gets two clean restarts. | [W360](W360.md) | `r042-R031_FUTURE` (`R031_FUTURE`) | PENDING |
| R-031 invalid negative issued_through refuses before READY with the same preservation boundary. | [W360](W360.md) | `r042-R031_INVALID` (`R031_INVALID`) | PENDING |
| Bay presence becomes UNKNOWN after the pre-start check; real status failure gives PRESENCE_UNKNOWN, then recovery. | [W360](W360.md) | `r042-X_BAY` (`X_BAY`) | PENDING |
| Ledger presence becomes UNKNOWN after the pre-start check; the protective hold is followed by recovery. | [W360](W360.md) | `r042-X_LEDGER` (`X_LEDGER`) | PENDING |
| Vanilla dismount during ledger NOT_LOADED is unreachable through supported production flow. | [W360](W360.md), dismount argument. | `r042-L_LEDGER` has no executable subcase: classification precedes login, all later mounts are vetoed, and no supported production ledger detachment exists. This is not a pass. | PENDING |

### 8.4 W329's GameTests

W329's original `EscrowLedgerNotLoadedGameTests` inventory has two batches of eight tests: `w329_ledger_not_loaded` checks each protective read
while the ledger is NOT_LOADED, and `w329_ledger_no_file` checks that NO_FILE answers are unchanged. The reads are
`escrowIdle`, `flightFrozen`, `retirementUnprotected`, `bayNeutral`, `escrowLogicalLocked`,
`RocketCargoDeviceHold.locked` (with a fluid cargo device), `EscrowLoaderAdmission.progress` and the status command. W329
also showed that reverting its production change fails all eight NOT_LOADED tests. C3's execution is historical ([C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 8.4).
C4 must confirm all sixteen again.

| Scenario | Source | C4 hosted inventory | C4 |
|---|---|---|---|
| The sixteen original protective-read/control GameTests. | [W329](W329.md) | `EscrowLedgerNotLoadedGameTests`, the original two batches named above. | PENDING (16) |
| A real item loader is held and removal-protected under NOT_LOADED without NBT changes. | [W360](W360.md) | `EscrowLedgerNotLoadedGameTests.notLoadedItemLoaderIsHeld` | PENDING |
| A real item unloader is held and removal-protected under NOT_LOADED without NBT changes. | [W360](W360.md) | `EscrowLedgerNotLoadedGameTests.notLoadedItemUnloaderIsHeld` | PENDING |

### 8.5 The downgrade family

`w336-downgrade` places all five devices (bay, item loader and unloader, fluid loader and unloader) with claimed roots,
actual bay receipts, a schema-3 transfer journal and a valid empty ledger, then starts W176's old JAR (section 9.3).
Its historical-JAR downgrade checks are expected to fail under R-044 until the owner decides it (R429,
[QUEUE](../QUEUE.md)); both restored-candidate restarts passing does not waive those failures.
C3's failures remain historical ([C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 8.5).
Ledger root 3 adds a second refusal reason even without a governed transfer record; whether that path rewrites
metadata is untested ([R044-SIGNOFF-DRAFT](R044-SIGNOFF-DRAFT.md), section 3). Neither restored-candidate starts
nor a later risk signature turn the old failed downgrade checks into passes.

| Scenario | Evidence to fill | C4 |
|---|---|---|
| The retained five-device downgrade family. | `w336-downgrade`; keep its whole-world oracle unchanged. | PENDING |
| Shared historical-JAR lifecycle/downgrade rows. | `cargo-G2-REUSE`; C3 rows 3 to 5 stay failed history. | PENDING |
| Root-3 ledger downgrade refusal without a governed transfer record. | Planned C4 accounting/downgrade cases, not yet assigned a delivered family; section 12. | PENDING |

### 8.6 Residuals

| Row | Kinds | C1 | Since C1 | C4 |
|---|---|---|---|---|
| X01 | All four | NOT_COVERED: a forced Java-process stop does not simulate OS crash or power loss | R457 selects non-destructive E4 replay; [W368-DESIGN](W368-DESIGN.md) and section 13 define coverage and residuals. | PENDING |
| X02 | All four | NOT_COVERED: unobservable storage, cache and close failures | R457 selects seam fault injection; [W368-DESIGN](W368-DESIGN.md) and section 13 give the three-way classification. | PENDING |
| X03 | FLUID_CARGO_UNLOAD | NOT_COVERED: the operator's whole-world repair after REBUILD_SOURCE | [W367-DESIGN](W367-DESIGN.md), R463, selects a narrow whole-copy offline repair; implementation/native evidence remain open (section 13). | PENDING |

## 9. The owner's three checks

### 9.1 R-042: effects on the whole server

**What holds, by trigger.** The original reachability descriptions are retained from
[C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 9.1. A world with existing escrow or receipt history can encounter
these holds even while new admission stays off. Accounting adds no new Level/chunk save denial (section 12).

| Trigger | Reachability | What holds | Duration | Logs | Recovery | Evidence |
|---|---|---|---|---|---|---|
| Bay receipt file present but not loaded | On a world that has a receipt file | Every rocket, including ordinary flights with no bay and landed or idle rockets; every satellite launch and decommission answers DEPLOYMENT_PENDING; v0.5 assembly and disassembly recovery is fenced; package insertion is refused; fuel answers ESCROW_PENDING; all four cargo kinds are refused; `retire-rejected` is refused | The session | One WARN `ARCE_BAY_RECEIPTS_NOT_LOADED reason=LOAD_FAILED` and one INFO `ARCE_BAY_RECEIPTS_INDEX state=NOT_LOADED` per start; nothing per rocket or transfer | A restart that loads the file; if it keeps failing, the whole-world offline procedure of W11b1 | W327 section 1.2 (M1, M2); `r042-N_BAY` |
| Bay receipt presence unknown at the pre-start check | On any world, including one with no ARCE data | The server does not start (`ARCE-BETA-2000`, typed `BAY_RECEIPTS`). No managed file is read, created or changed; vanilla still updates `level.dat` (W343, W345). | Until storage access is fixed | One ERROR per start, with the storage instruction first | Fix storage access to the world folder and restart; never delete, move or restore single files | W328 and W328F unit tests; `r042-U_BAY` |
| Bay receipt presence becomes unknown after the pre-start check | In a race window that W328 records | As the first row, with `reason=PRESENCE_UNKNOWN` | The session | As the first row | A restart once the status check works | `BayReceiptsStartupReasonTest` (unit); [W360](W360.md) adds `r042-X_BAY`. |
| Escrow ledger present but not loaded | On a world that has a ledger | Every rocket is not idle, protected, not a neutral bay source, logically locked and frozen: no launch, seat boarding, fill, cargo, disassembly, retirement, command kill or teleport. Every fuel loader and cargo device holding a root is locked. All five kinds are refused, and the status command shows `ledger=not-loaded`. The vanilla dismount branch is not cancelled, but W360 shows that supported flow cannot seat a rider during this hold; no executable dismount pass is claimed. | The session | One WARN `ARCE_ESCROW_LEDGER_NOT_LOADED reason=LOAD_FAILED`. `ARCE_TRANSFER_FENCED` repeats on reason changes: in steady state at most two per record per reevaluation cycle, with no rolling-window cap and no session total (W328G). At most one `ARCE_ROCKET_RECOVERY_FENCED` per transaction (at most 256), and at most one bay-claim REPAIR WARN per claim. | A restart that loads the ledger or proves it absent | W329 and its GameTests; `r042-L_LEDGER` |
| Ledger presence unknown at the pre-start check | On any world | The server does not start (`ARCE-BETA-2000`) | Until storage access is fixed | One ERROR per start, storage instruction first | As the second row | W328 unit tests; `r042-U_LEDGER` |
| Ledger presence becomes unknown after the pre-start check | In the same race window | As the fourth row, with `reason=PRESENCE_UNKNOWN` | The session | As the fourth row | A restart once the status check works | `EscrowLedgerStartupTest` (unit); [W360](W360.md) adds `r042-X_LEDGER`. |
| The pending pool held by released or REPAIR bay receipts | Only where such receipts exist; today only the gated test path writes them | With 64 held: every new fuel fill, cargo operation and bay deployment on the server is refused. With one owner's 16 held: that owner is refused everywhere. | Across every restart, until offline repair; there is no online release | No line per slot | Whole-world offline repair | W327-M3; `r042-P_SERVER`, `r042-P_OWNER`; B11.5, B11.6; GameTest F3.4 |

**What the native smokes observed.** These are the local-JAR observations retained from
[C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), not C4 results. Section 8.3 adds W360's newer smoke mappings.

- **`N_BAY`** ([W332](W332.md), smoke 4; rerun by [W343](W343.md)). The receipt file held two REJECTED receipts, and
  the seam made it fail to load at S1. The logs showed one injection line, vanilla's one load ERROR, one WARN with
  `reason=LOAD_FAILED` and one INFO, and no other ERROR. An ordinary flight with no bay stayed PREPARED in TRANSIT for
  618 game ticks, unclaimed and locked. A new satellite launch and X's decommission answered DEPLOYMENT_PENDING, a stale
  assembly record was fenced with one WARN, and package insertion returned the whole stack. Fuel answered
  ESCROW_PENDING, and all four cargo kinds answered `TRANSFER_JOURNAL_UNPROVED`, the answer the cargo path gives when a
  rocket is not idle. `retire-rejected` answered `refused.not_loaded`. The receipt file's bytes were unchanged and no
  ledger appeared. At S2, without the seam, the receipts loaded, the flight came to rest, a launch succeeded, and all
  five kinds were admitted and completed exactly once.
- **`L_LEDGER`** (W332, smoke 2; strengthened by W343). With the ledger failing to load at S1: one WARN with
  `reason=LOAD_FAILED`; the status command reported `ledger=not-loaded`; the launch answered `TRANSFER_JOURNAL_BLOCKED`;
  all five kinds answered ESCROW_PENDING with nothing changed; every device reported locked and protected from removal;
  the ledger's bytes were unchanged. At S2 the ledger loaded and all five kinds completed. W343 added that the final
  ledger must be present and changed. The unseated login is consistent with [W360](W360.md)'s reasoned
  unreachable dismount classification, not an executed dismount pass.
- **`P_SERVER`** (W332; W343 added the exact menu reason). 64 typed receipts (released REPAIR, released REJECTED and
  durable REPAIR, four seed owners) gave `pending=64` at S0, S1 and S2. All five kinds answered ESCROW_FULL, the bay
  fixture landed unconsumed with the menu showing DEPLOYMENT_CAPACITY, and `retire-rejected` refused each kind of
  receipt. The receipt file equalled the seed after every stop.
- **`P_OWNER`** (W332; W343 added the exact menu reason). 16 receipts of one owner on two rockets gave
  OWNER_PENDING_FULL for all five kinds of that owner and in the bay menu, at every start. At S2, another owner's
  fuel loader and four cargo kinds were admitted and completed while the count stayed 16. The bay uses a fixed
  owner, so this case does not show another owner's bay admission ([W332](W332.md), "Not done", carried by
  [W343](W343.md) and [W345](W345.md); [W355](W355.md), audit limit 3).
- **`U_BAY` and `U_LEDGER`** ([W345](W345.md), after W340 and W343). A delete-pending handle made the status check fail.
  Both cases reached the typed refusal with the storage instruction first and all six expected refusal diagnostics, and
  recovered cleanly at S2 and S3 with every managed file's hash unchanged. The only changed files were `level.dat`, whose
  only decoded difference was an increased `/Data/LastPlayed`, and `level.dat_old`, which equalled S0's `level.dat`.
  Root decision R416 accepts exactly that boundary in the oracle; the `whole_world_byte_identical` field stays false. A
  scratch JAR that skipped the pre-start guard reached READY and was rejected.

Each family's oracle also rejected a scratch JAR with its protection disabled (W332 negative 1, W343, W345).

**Where W327's findings stand.** M1 (the unknown-presence trigger), M2 (the missing server-wide effects), M3 (the
pool's duration), L1 (the WARN text) and L4 (claims beyond 256 skipped at restart) are now in the register text or fixed
in code; L2 was fixed by W328, with W329 adding the protective reads; L5's comment was fixed by W328. Correcting the
register's description accepts no risk.

### 9.2 R-043: waiting times

**About the figures.** W336's observations are vanilla game-time intervals, bracketed with `time query gametime` on the
**development host**, on a locally built JAR (production inputs `b3e33720e`, SHA-256 `f9aca893…7cd5ab`), with small
numbers of claims. They are not C4 results and **not reference-node figures**. W336 states that they do not establish
worst-case timings and do not justify extrapolating the 64-claim, 34-slot estimate. C4's planned timing families repeat them on
the candidate, still on the development host.

The rows follow W327's numbering of the waits from D0 to D7 and in recovery. "Bounded" means bounded by code;
"estimated" means a hand calculation with no run behind it; "measured" means a run recorded the time.

| # | Step and wait | Bound in code | Stated figure and its kind | W336 observation (development host) | C4 family |
|---|---|---|---|---|---|
| 1 | D0 (B14): the journal write covering L's absence | None | R-040: P50 84 and P95 244 ticks near the 64 MiB cap (W129aF, measured on a development host) | Not observed | `forced-A` (B14.1, B14.2), `forced-W331-T` |
| 2 | D1 and outcome writes: the exact acknowledgement | None overall; the writer retries every 200 ticks, an outcome write every tick | Bounded per attempt (R-039) | Not observed separately | `forced-A` (B12.1, B12.2) |
| 3 | D3: REGISTRY_BLOCKED | None | None stated | Persisted through a chosen 240-tick hold and both restarts (start windows 399, 250 and 250 ticks); a hold window is not a bound | `w336-timing-B10_BLOCKED` |
| 4 | D3: the first published registry completion | None (writer IO) | R-039's registry-writer measurements (W129b) are of the writer, not of D3 | Registry unacknowledged at game times [151,153] and [151,154], acknowledged at [759,761] in the two B13 cases | `w336-timing-B13_FAILED`, `-B13_UNKNOWN` |
| 5 | D3: REGISTRY_UNPROVED retries | 200 ticks per attempt; none overall | Bounded per attempt | Retry spacing 198 to 205 ticks; REGISTRY_UNPROVED to acknowledgement 201 to 207 ticks | As row 4 |
| 6 | D4: room in the shared cargo work budget | Per tick; none in total under cargo load | None stated | Not observed; needs concurrent cargo load | None |
| 7 | D4: a free receipt store | None | Code rule | An UNKNOWN receipt generation resolved in 186 to 208 ticks | `w336-timing-B12_D4` |
| 8 | D5: T acknowledged, the source loaded, the consumption port available | None | R-035: at most three journal writes when nothing fails (a code argument) | Not observed | B5 rows in `forced-CD` |
| 9 | D5 copies | None; one changed copy per tick server-wide | Code rule | Not observed | B5 and B16 rows |
| 10 | D6 before capture | None | Code rule; the register says these waits have no timeout | Not observed | None |
| 11 | D6 queue: BAY_PROOF_TURN | None; a claim that leaves the line queues again at the back | "About 1 minute" (10 claims of 6 slots) and "about 36 minutes" (64 claims of 34 slots) are **Root's estimates**, now labelled as such in R-043 (W327-M6) | The queued claim's whole wait through READY_SOURCE: 58 to 64 ticks with 2-slot censuses and 83 to 88 ticks with 3-slot censuses (two claims each) | `w336-timing-TURNS-2`, `-TURNS-3` |
| 12 | D6 per slot | None. 20 ticks is the minimum spacing between one Level's forced batches, and at most 32 completions are delivered per tick | "About 20 ticks per slot" and "about 680 ticks for 34 slots": estimates | Service turn of the first holder: 38 to 44 ticks for 2 slots (19 to 22 per slot) and 43 to 48 for 3 slots (14.3 to 16 per slot). Service turn of the queued claim: 17 to 23 ticks (8.5 to 11.5 per slot) and 37 to 43 (12.3 to 14.3 per slot) | As row 11 |
| 13 | D6 retry after a refusal | 200 ticks per attempt; none overall | Bounded per attempt | Three `retry=200` refusals, spaced 201 to 203 ticks | `w336-timing-D6_RETRY` |
| 14 | D6 capture quota, shared with F1, W98 and sealed disassembly | None | Code rule | Not observed; needs competing captures | None |
| 15 | D7 parked until the receipt store is quiet, then a fresh D6 | None | Code rule | B16_GUARD to the requested release: 249 to 253 ticks for a chosen 240-tick pause; not a bound | `w336-timing-B16_MOTION` |
| 16 | D7: an UNKNOWN removal retried with the exact candidate | None | Code rule | An UNKNOWN receipt generation resolved in 182 to 205 ticks | `w336-timing-B12_D7` |
| 17 | D7: a refused guard sends the claim back to D6 | None | Code rule | Not timed | B16 rows |
| 18 | Restart rows: one step per INTENT row | 32 steps per tick, shared round robin; a held row lasts the session | Bounded per tick | Not timed | B1, B2 and B13 rows |
| 19 | RESUME | None | Code rule | Not timed | B3 and B6 rows |
| 20 | Released flight | None | Code rule | Not timed | `released` |
| 21 | Claim end: no pending receipt names L | None; for released or REPAIR receipts, until offline repair | Code rule | Held through S0, S1 and S2 in the P_SERVER and P_OWNER smokes (W332) | `r042-P_SERVER`, `r042-P_OWNER`; B11.5, B11.6 |
| 22 | Reconnect queue | 200 ticks | Bounded | Login to expiry: 198 to 203 and 197 to 202 ticks on the two restarts | `w336-timing-RECONNECT` |
| 23 | Ticket expiry after a claim ends | 400 ticks after the last renewal (vanilla) | Bounded (code and vanilla) | Census columns: 4 columns all loaded at [298,303] and all unloaded by [789,794]; 6 columns at [322,329] and by [797,804]. Other tickets were present, so this is not an isolated bay-ticket expiry. | Recorded with the timing families |

**C4's timing families.** Results and measured intervals are PENDING. The ten selected timing names are retained
in section 3.2 and the table above. Historical C3 intervals remain in [C3-SWITCH-REPORT](C3-SWITCH-REPORT.md),
section 9.2, not as C4 measurements. Root must keep development-host timing separate from a reference-node result.

| Timing evidence | Source/mapping | C4 |
|---|---|---|
| The ten timing profiles with both restarts, interval observations and an explicit upper-bound verdict. | [W336](W336.md); the exact timing families in section 3.2. | PENDING (10) |

**Summary.** Only the per-attempt retry spacings (rows 2, 5 and 13), the 32-step budget (row 18), the reconnect queue
(row 22) and ticket expiry (row 23) are bounded. W336's retry and reconnect observations (rows 5, 13 and 22) lie within
a few ticks of their 200-tick rules. Its column sweeps cannot be compared with the 400-tick expiry, because other
tickets were present.
Every other wait has no upper bound in code; W336's hold windows show only that the waits persisted for the chosen time.
The 36-minute and 1-minute figures remain estimates, and with no failures W327 reads the 36-minute figure as closer to a
lower estimate. W336 measured at most 3 slots per census and 2 claims per queue. The real figures need the reference
node, which the owner placed in the final campaign.

**Coupling with cargo.** Bay recovery can delay cargo preparation by whole ticks, and cargo preparation can delay D4 and
D5 (row 6; W327-L3, now in R-043). No run measures this.

### 9.3 R-044: which risks belong to one device, and which are shared

| Risk | Belongs to | Register | Evidence |
|---|---|---|---|
| An unsupported device root: the device is protected and disabled, and ordinary removal is refused | **One device.** The fuel loader in R-044; each cargo device in R-035's cargo paragraph; the bay in R-038 | R-044, R-035, R-038 | Code (W327 section 3.1); the W336 saturation fixtures place unsupported roots of each device |
| One chunk's terrain save refused | **One chunk**, per device as above | As above | As above |
| The 256-coordinate denial pool | **Shared per ServerLevel** by about 15 device families: suit workstations, tanks, force fields, landing pads, solar arrays, combustion generators, pumps, classic and research machines, unlit torches, life-support devices, the bay, the four cargo devices and the fuel loader. Any family's denials use it up, and the 257th coordinate refuses every later terrain save in the Level. | R-044 (shared-pool text, W327-M5); R-038 and R-035's cargo paragraph refer to it | W336: each of the five bay and cargo controls ran against 255 fuel-loader denials, and the fuel control against bay denials. Terrain without a device saved at 255 and 256 and was refused after the 257th. All 257 unsupported roots stayed byte-identical. Restart 1 reset the in-memory pool; restart 2 loaded the roots, saturated again and refused a change. A scratch JAR without the pool was rejected. |
| The downgrade rewrite |**The world.** It comes from the old JAR's transfer-journal refusal (R-035) Ledger root 3 adds the second refusal boundary (R458); its metadata effect needs C4 evidence.| R-044 (W327-M4 text) |W336 with W176's old JAR: all three refusals changed only `level.dat` and `level.dat_old`, and the 43 other world files stayed identical. The registry lost 13 block and 12 item entries, including all five placed device IDs. Device roots, receipts, the empty ledger, terrain and the transfer journal stayed byte-identical. W176's whole-world verdict stays FAILED; historical-JAR downgrade checks are expected to fail under R-044 until the owner decides it (R428, R429). Restoring the complete backup reproduced every hash, and both later candidate starts were clean and kept all five roots. C3's `w336-downgrade` and `cargo-G2-REUSE` failed the same way (section 2.1). [R044-SIGNOFF-DRAFT](R044-SIGNOFF-DRAFT.md), section 3, states the new boundary without claiming it was tested.|
| The pending pool of 64, 8 and 16 | **Shared** by the fuel loader, the four cargo kinds and the bay | R-039, R-042 | `cargo-C-Q`; CAP rows; `r042-P_SERVER`, `r042-P_OWNER`; F3 GameTests |
| NOT_LOADED of the receipts or the ledger | **The whole server**: every rocket and every device holding a root | R-042, R-035 | `r042-N_BAY`, `r042-L_LEDGER`; W329's GameTests |
| The shared cargo work budget | **Shared** by the four cargo kinds and the bay | R-043 (W327-L3 text) | Not measured |
| The transfer journal | **The whole server**: every transfer | R-035 | B3.12; the cargo cuts that restart through the journal |
| Settlement REPAIR of one operation, cut-E2, one bay claim | **One device and one rocket** | R-035, R-042 | The cargo cuts; the bay B rows |

Device protection is live with every switch off, because all five devices are craftable at C3 (historical baseline) (section 3.1 and
sections 4.1 to 7.1).

## 10. Points for Root

### 10.1 The owner's final packet

[OWNER-DECISIONS-20261117](OWNER-DECISIONS-20261117.md) delegates all design and sequencing to Root. The owner
still decides Gate/version/release passage, tags and the merge to main; signs residual-risk acceptance (R-044 and
each switch's scope); and supplies the real-client account sessions. Root prepares one final packet with a
recommendation and a yes-or-no decision, not a new design questionnaire. R458 postpones the revised R-044 text until
C4 covers both refusal boundaries; signing it does not enable a switch or change a failed result. Each switch's
recommendation is marked PLACEHOLDER in sections 3.1 to 7.1. No recommendation is filled by W374.

### 10.2 Retained observations and dispositions

These points came from W351's original drafting. Root has since disposed of some under R423
([QUEUE](../QUEUE.md), row W351); the historical observations are retained with their disposition. This report
does not change the register or decide admission.

1. R-039 and R-035's ledger paragraph originally said that the pre-start refusal reads, creates and changes no file. W343 and W345
   observed vanilla update `level.dat` (`/Data/LastPlayed`) and rotate `level.dat_old` before the refusal; the managed
   files stay unchanged. Root decision R416 accepts that boundary in the U oracle. R423 corrected the register;
   the C1 report's "it writes nothing" (section 7) remains historical, not the C3 claim.
2. R-043 originally said that no native run had timed a step or used more than 3 slots (W327-M6). W336 has since
   observed several waits natively on the development host (section 9.2); R423 corrected the register's timing
   statement. The limit of 3 observed slots still holds.
3. R-043's summary column still describes the shared 64/8 slots without the 16-per-owner cap. Its later text marks the 64/8
   wording as historical.
4. Under bay NOT_LOADED the cargo path answered `TRANSFER_JOURNAL_UNPROVED`, and under ledger NOT_LOADED the launch
   answered `TRANSFER_JOURNAL_BLOCKED` (W332). Neither answer names the store that failed; an operator sees the cause
   only in the startup WARN. R423 keeps this as an owner-facing limitation, without a production change during
   the second candidate's run.
5. R423 confirmed W327's listing of R-040 for cargo: an unreadable transaction journal refuses disassembly of a
   rocket carrying cargo. The cargo recovery cuts and landed-disassembly tests use that journal.
6. R-044 records W176's 12 removed block and 11 item entries; W336 observed 13 and 12 with its JAR. The count depends
   on the JAR.
7. W329 originally tested only a fluid device's hold. [W360](W360.md) adds the item-loader and item-unloader
   GameTests of section 8.4; their C4 results are awaited.
8. W350's local full run failed three tests it did not touch, including
   `SealedSpecialHostAutomationGameTests.tankAndBayStaySealedUnderTickingAutomation`, and QUEUE keeps that as an open
   item. The branch run 42849035668 passed them. W350 did not establish a cause.

## 11. How this report was made

### W351's original C2 draft (historical method)

The original reading, commands, limits and validator result are preserved in
[C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 11. They are historical method, not commands or verification
performed for C4 by this worker.

### W356's C3 retargeting

[W356](W356.md) records the retargeting and identifier preservation for Root's filler. It filled no candidate
result and accepted no risk.

### Root's fill from C3 (2026-11-12)

[C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 11, records the fill from C3's campaign and hosted shards,
including hand-filled summary/shared rows and failed downgrade checks. Those results stay in C3's report.

### W374's C4 draft

The companion record `docs/work/v1.8.0-root-queue/records/W374.md` lists every added/changed table row, sources,
commands and results. All C3 table rows are retained;
all candidate result columns are reset to PENDING, preserving case counts where unambiguous. No campaign evidence,
hosted artifact or external risk audit is re-derived here. Root must fill from the pinned C4 campaign, hosted
shards and accounting/X reports, retaining failures, exclusions, unreachable classifications and residuals
separately. Plans and local smokes do not become candidate passes.

## 12. Per-fluid signed accounting

### 12.1 Format, holds and recovery

The authority is R-035's accounting paragraph in the [risk register](../../../11-RISK-REGISTER.md), with
[W357-DESIGN](W357-DESIGN.md), [W361](W361.md), [W362](W362.md), [W364](W364.md) and [W366](W366.md).
The register describes Slices A/B as not activated; W372's later activation task is separate and unresolved at
this drafting snapshot ([QUEUE](../QUEUE.md), R466/R468/R469). C4 is intended to carry the reviewed activation,
not merely the account model.

| Subject | Recorded rule and scope | Source |
|---|---|---|
| Saved format | Ledger root 3 has fluid_accounts; new fluid operations and intents use schema 3, and fluid settlement receipts use schema 4 with the final account digest. Item/rowless legacy receipts keep schema-3 UNKNOWN semantics. | [W362](W362.md); [W366](W366.md). |
| Identity and observation | Each fluid key is its registry ID plus native-tag digest; absent and empty tags differ. Fuel observes the loader tank and both banks; cargo observes only the cursor tank. Checked long arithmetic and typed vectors prevent equal scalar totals from hiding a changed key. | [W361](W361.md); [W364](W364.md). |
| Normal completion | Baselines travel with L1; C2 checks typed deltas; checkpoints freeze observations with frontiers. Only zero residual permits ordinary L2, deleting operation and transient row together. C8 prunes markers only. | [W364](W364.md). |
| Operator settlement | O1 freezes per-key signed discrepancies and half-application accounting with the confirmed intent; O2 recomputes typed readback; O3 binds final rows to schema-4 receipts; O4 revalidates before release. Shared adjustments have one owner, not a duplicated amount per receipt. | [W361](W361.md), correction; [W362](W362.md); [W366](W366.md). |
| Missing knowledge | Missing baseline or endpoint is UNKNOWN, never numeric zero. Migrated legacy fluid work remains UNKNOWN_BASELINE: no ordinary C2/L2, and new work cannot enter that legacy component. ABANDON preserves UNKNOWN on the missing side. | R-035 accounting paragraph; [W364](W364.md); [W366](W366.md). |
| Mismatch hold | ACCOUNTING_MISMATCH holds only its connected component. It changes no resource, reconstructs no source and gives no refund; restoring the apparent native state does not auto-clear it. Audited operator settlement or whole-world offline repair is needed. | R-035 accounting paragraph; [W364](W364.md); [W366](W366.md). |
| Migration/start refusal | With activation on, root 2 migrates at pre-start, retaining embedded history as UNKNOWN. A noncanonical source, invalid/full target, root 1 or BACKUP_LIMIT refuses the whole world before start and preserves source bytes. Migration backup is not a complete world backup. | R-035 accounting paragraph; [W362](W362.md); [TASK-W372](../TASK-W372-ACCOUNTING-ACTIVATION.md). |
| Bounds and availability | Rows have a 2,048-byte, depth-5, 65-node, 14-member saved bound; the measured frozen new-format component fits 13. Reservations charge each row once, reducing admitted byte capacity without raising the existing ceilings. No new chunk/Level save refusal or shared-denial coordinate is added. | [W362](W362.md), R447 correction; R-035 accounting paragraph. |
| Logs and duration | Each live held operation has one ARCE_ESCROW_ACCOUNTING_HELD WARN, at most 64 live IDs; IDs are forgotten after leaving the ledger. Release has one INFO; confirmed operator audit is bounded to 64 members/320 key rows. Holds have no auto-clear timeout; startup refusal lasts until repair/support. | R-035 accounting paragraph; [W364](W364.md); [W366](W366.md). |
| Downgrade | Root 3 is an extra old-JAR refusal reason, even without a governed transfer record. Complete-world backup/restore remains required; metadata behavior on this path is not yet tested. | [R044-SIGNOFF-DRAFT](R044-SIGNOFF-DRAFT.md), section 3; R458. |

### 12.2 Evidence per fluid kind and the shared fuel loader

These are evidence requirements/mappings, not candidate results. Unit classes exercise explicit root-3 models
while activation is off; they do not prove the normal root-3 writer, migration or native restarts.

| Evidence row | Applies to | Named test or planned run | Source | C4 |
|---|---|---|---|---|
| Slice A: key identity, signed balance, half application, chains, UNKNOWN and bounded codec/size. | FLUID_CARGO_LOAD, FLUID_CARGO_UNLOAD and fuel loader. | `EscrowFluidAccountTest`, `EscrowFluidAccountCodecTest`, `EscrowFluidAccountSizeTest`. | [W361](W361.md); R443/R447 in [QUEUE](../QUEUE.md). | PENDING |
| Slice B: root/operation/intent/receipt versions, migration refusals, capacity and authority-copy preservation. | Both fluid cargo kinds and fuel loader; all kinds share ledger startup. | `EscrowAccountingFormatTest`, `EscrowAccountingMigrationTest`, `EscrowAccountingDecodeTest`, `EscrowAccountingCapacityMeasurementTest`. | [W362](W362.md); R445 to R449. | PENDING |
| Slice C: typed fuel capture and normal zero-residual reconciliation/retirement. | Fuel loader. | `EscrowAccountingCaptureTest`, `EscrowAccountingReconciliationTest`. | [W364](W364.md). | PENDING |
| Slice C: cursor-only cargo capture, both directions, zero residual and component-local mismatch. | FLUID_CARGO_LOAD and FLUID_CARGO_UNLOAD. | `EscrowAccountingCaptureTest`, `EscrowAccountingReconciliationTest`. | [W364](W364.md). | PENDING |
| Slice D: signed preview/confirm, O1 to O4, known and missing sides, shared ownership, diagnostics and permanent rows. | Both fluid cargo kinds and fuel loader; mixed item components share pending roots. | `EscrowAccountingSettlementTest`; paired schema-4 pruning coverage described by W366. | [W366](W366.md); R462 to R465. | PENDING |
| Slice E part 1: versioned native readers and independent accounting expectations. | Both fluid cargo kinds and fuel loader; root framing also applies to items. | `EscrowAccountingOracleFixtureTest` and W365's reader/oracle tests. | [W365](W365.md); R452. | PENDING |
| Activation: full unit suite, runData/clean generated diff, full GameTest/log checker and independent read-only review. | The shared ledger, both fluid cargo kinds and fuel loader. | W372/R466; candidate hosted run identity remains PENDING. | [TASK-W372](../TASK-W372-ACCOUNTING-ACTIVATION.md); [QUEUE](../QUEUE.md), R468/R469. | PENDING |
| Activation native smoke: fresh fuel fill writes, reads and retires root 3 through two restarts. | Fuel loader. | W372's required existing-family smoke; concrete case and artifact pins await its record. | [TASK-W372](../TASK-W372-ACCOUNTING-ACTIVATION.md). | PENDING |
| Activation native smoke: fresh fluid cargo writes, reads and retires root 3 through two restarts. | Fluid cargo; the actual direction must be named in the result, not inferred for both. | W372's required existing-family smoke; concrete case and artifact pins await its record. | [TASK-W372](../TASK-W372-ACCOUNTING-ACTIVATION.md). | PENDING |
| Slice E part 2 native accounting: fresh normal and settlement/repair paths with exact accounts and two restarts. | FLUID_CARGO_LOAD. | Planned W373 native cases; no assigned case/family identifiers are invented here. | [W365](W365.md), remaining native work; [QUEUE](../QUEUE.md), W372/R466. | PENDING |
| Slice E part 2 native accounting: fresh normal and settlement/repair paths with exact accounts and two restarts. | FLUID_CARGO_UNLOAD. | Planned W373 native cases. | [W365](W365.md); [QUEUE](../QUEUE.md), W372/R466. | PENDING |
| Slice E part 2 native accounting: bank ownership, signed/UNKNOWN settlement, exact retries and restart proofs. | Fuel loader, where it shares the risk. | Planned W373 native cases. | [W365](W365.md); [W366](W366.md), remaining work; [QUEUE](../QUEUE.md), W372/R466. | PENDING |
| Root-2 migration, legacy UNKNOWN and root-3 downgrade boundaries on the final candidate. | The shared ledger and all five switches; fluid accounting and fuel loader share the migration risk. | Planned accounting-native/downgrade cases; exact selection awaits W373 and Root. | [W365](W365.md); [R044-SIGNOFF-DRAFT](R044-SIGNOFF-DRAFT.md), section 3. | PENDING |

### 12.3 Remaining work and activation findings

[QUEUE](../QUEUE.md), W372, records the activation run as blocked, not passed: 6,454 unit tests passed and 135 failed
at the first attempt. W372B sorted those failures into seven format values, 127 old-format fixtures and one finding;
77 originals passed after corrections, with 57 corrections not yet applied. R468 permits only explicit consistent
fixtures or format-value re-pins, never replacing the original behavior assertion with the new refusal.

R469 classifies W372-F2's R8 preview arithmetic as a capacity-fixture issue, not a production defect: use the actual
frozen candidate row and deterministic padding, asserting that the first refusal's overshoot lies within slack.
Both assertion meanings and ceilings remain. W372C's remaining corrections, complete verification, two native
smokes and a different-engine read-only review are still required. This draft does not claim their completion.

W373's full native accounting cases, candidate results, exact retry/restart/log evidence and revised downgrade
sign-off remain outstanding. W365's remaining-work section and W366's activation-boundary note are the sources.
No W372/W373 record exists in this checkout, so those rows cite the tracked TASK/QUEUE and plans instead.
The accounting detects/records discrepancies but does not resolve R-032, real storage/power-loss behavior,
single-file rollback or stale physical copies; section 13 keeps those residuals separate.

## 13. X01 to X03: selected approach, pending evidence and residuals

### 13.1 Decision and implementation boundary

The [isolation plan](X01-X03-ISOLATION-PLAN.md), as amended by R457 in
[OWNER-DECISIONS-20261117](OWNER-DECISIONS-20261117.md), chooses E4: non-destructive application-level simulation
on this PC, not a new VM or power-cut authorization. [W368-DESIGN](W368-DESIGN.md), with R461 in
[QUEUE](../QUEUE.md), adds a gated CheckedFileOps seam because the older committer seam sees only the move.
Windows directory flush is a no-op: acknowledged moves may be replayed as bounded late loss. A loss/duplication
found in such a state is a defect to schedule; neither the residual label nor a signature changes its failed oracle.

| Implementation/evidence row | Source and boundary | C4 |
|---|---|---|
| S1 gated recorder and DIRECT file-operations seam, with a different-engine read-only review and recorded smoke. | [QUEUE](../QUEUE.md), W369/R461; this snapshot records dispatch, not delivery. | PENDING |
| S2 crash-state builder, bounded replay runner and additive restart-only runner modes. | [QUEUE](../QUEUE.md), W370/R463/R467, records delivery at b3f5d0748 and 110 targeted Python tests; no native run was required by its TASK. Its review/inventory/CI integration still belongs to Root. | PENDING |
| S3 X01 on the pinned candidate, two starts per state with unchanged existing oracles. | [W368-DESIGN](W368-DESIGN.md), sections 2, 3 and 7. | PENDING |
| S4 injector/read wrapper/offline faults and S5 X02 classification report. | [W368-DESIGN](W368-DESIGN.md), sections 4 and 7. | PENDING |

### 13.2 X01 replay rows

Each row is from [W368-DESIGN](W368-DESIGN.md), section 3. Recorded/old-side case names below are exact.
S-OLD, S-EMPTY, S-PREFIX, S-TORN, S-STAGED and S-NEW are the in-flight forms; there are at most seven late-loss
forms and sixteen total states per case. OVER_BOUND is reported, not silently sampled. The planned maximum is
192 copies/384 restart starts, not an observed run. Each result must identify the state, derive it before starting,
and retain the state/file hashes and both restart outcomes.

| Row | Store/write | Recorded case | Old-side oracle or boundary | C4 |
|---|---|---|---|---|
| X01-01 | Bay D1 INTENT. | `B1_MOVED` | `B1_STAGED` | PENDING |
| X01-02 | Bay D4 REGISTERED; late loss also includes earlier receipts/registry. | `B3_MOVED` | `B3_STAGED` | PENDING |
| X01-03 | Bay D7 removal; late loss includes D4/registry. | `B8_MOVED` | `B8_STAGED` | PENDING |
| X01-04 | Fluid-load L1. | `FC_LOAD_L1_REPLACED` | `FC_LOAD_L1_BEFORE` | PENDING |
| X01-05 | Fluid-load L2 and earlier L1 late loss. | `FC_LOAD_L2_REPLACED` | `FC_LOAD_K8` | PENDING |
| X01-06 | Fluid-unload L2 and earlier L1 late loss. | `FC_UNLOAD_L2_REPLACED` | `FC_UNLOAD_K8` | PENDING |
| X01-07 | Fluid-load barrier-to-L2 window, seam late loss only. | `FC_LOAD_C6_JOINT_READBACK` | No in-flight seam write; region files remain the kill image. | PENDING |
| X01-08 | Item-load L1. | `IC_LOAD_K2` | New conservation expectation for the earlier ledger generation; not a weakened existing case. | PENDING |
| X01-09 | Item-unload L2. | `IC_UNLOAD_K9` | New conservation expectation for the earlier ledger generation. | PENDING |
| X01-10 | First ledger creation. | `FF_N1` | The earlier fixture has no ledger file. | PENDING |
| X01-11 | Transfer-journal replacement. | `FS_O3J_AFTER` | `FS_O3J_BEFORE` | PENDING |
| X01-12 | Satellite registry. | `R3` | The runner's durable count for staged-only state must be pinned by S2. | PENDING |

### 13.3 X02 fault rows

[W368-DESIGN](W368-DESIGN.md), section 4, runs each fault against the escrow ledger, bay receipts, transfer journal
and satellite registry. Variants are enumerated there; no four-store result may be inferred from a single store.
The report must classify each actual case as SEEN_HELD, CAUGHT_LATER or NOT_CAUGHT, recording the store, effects and
recovery. These are classification names, not pre-filled outcomes. Failed existing oracles stay failed.

| Row | Fault and required evidence | Stores | Classification | C4 |
|---|---|---|---|---|
| X02-F1 | Write IOException, old target, held/retried then recovery. | All four named stores. | PENDING | PENDING |
| X02-F2 | Partial write/full disk, and open failure variant; no target replacement. | All four. | PENDING | PENDING |
| X02-F3 | Force/flush failure before the move. | All four. | PENDING | PENDING |
| X02-F4 | Move failure before rename, sharing violation, and rename-then-throw/readback. | All four. | PENDING | PENDING |
| X02-F5 | Close failure after successful force. | All four. | PENDING | PENDING |
| X02-F6 | Write reports success but loses bytes; staging readback detects or fails to detect it. | All four. | PENDING | PENDING |
| X02-F7 | Acknowledged write/flush lost offline: prior generation, zero length and zeroed tail, with two starts. | All four. | PENDING | PENDING |
| X02-F8 | Cleanup/delete fails after an earlier write/flush failure. | All four. | PENDING | PENDING |
| X02-F9 | In-session readback throws or returns another root after UNKNOWN move. | All four. | PENDING | PENDING |
| X02-F10 | At-rest corruption: flipped byte and half truncation, with two starts. | All four. | PENDING | PENDING |

### 13.4 X03 operator repair rows

[W367-DESIGN](W367-DESIGN.md), with Root's R463 answers in [QUEUE](../QUEUE.md), selects a narrow offline tool
on a complete world copy for the retired, stationary R74/R76 REBUILD_SOURCE shape. It preserves ledger, entities
and terrain, refuses competing authority, and removes only the proved stale transfer record. Existing settlement
cannot fix that exact shape. It invents no operation, account, receipt, refund or zero. Where L2 already removed
the row, historical accounting is an external UNKNOWN audit unless a typed witness supports confirmed signed values.
The proposed family name is a design identifier, not an already selected campaign family.

| Row | Required evidence and scope | Source | C4 |
|---|---|---|---|
| X03 design/reviews | Design read-only review before code and a separate review of the saved-data writer before merge. | [QUEUE](../QUEUE.md), W367/R463. | PENDING |
| X03 planner/apply | Reviewed dry-run-derived file list, whole-copy hashes, all refusals and interrupted/no-effect failures. | [W367-DESIGN](W367-DESIGN.md), sections 2 and 3. | PENDING |
| X03 root 2 | `cargo-X03-REPAIR` proposed case: keep the protected seed's 5,000 mB physical total, repair the copy, then two clean save/restart cycles and real unload/flight. | [W367-DESIGN](W367-DESIGN.md), sections 4 and 5. | PENDING |
| X03 fresh root 3 | Same independent raw-file/identity/authority/conservation checks on activated C4; preserve unrelated account rows and external UNKNOWN history where no baseline survives. | [W367-DESIGN](W367-DESIGN.md), sections 4 and 5; R463. | PENDING |
| X03 migrated retired root 2 | Same procedure after supported root-3 migration; no hand-edited fake activation. A related live account/operation is outside this narrow repair and is refused. | [W367-DESIGN](W367-DESIGN.md), sections 4 and 5. | PENDING |
| X03 negative control | Valid unrelated POI mutation outside the frozen runtime list must fail the independent oracle; the unmutated coordinator also refuses the copy. Playability/conservation alone is insufficient. | [W367-DESIGN](W367-DESIGN.md), section 5. | PENDING |
| X03 operator text | Final procedure and evidence in the owner's final packet; design prose is not approved operator guidance. | [QUEUE](../QUEUE.md), W367/R463; [OWNER-DECISIONS-20261117](OWNER-DECISIONS-20261117.md). | PENDING |

### 13.5 Remaining work and stated residuals

Finish/review the recorder and replay integration, implement/review the fault injector, and run/report S3 and S5 on
the pinned candidate before claiming simulated X01/X02 coverage. X03 still needs design review, tool, independent
oracle, both saved-form paths, negative control, two repair restarts and reviewed operator text. The source designs
give estimates, not measured completion times; W370's synthetic tests are not crash or power-loss evidence.

The following remain uncovered even if every simulated case passes
([W368-DESIGN](W368-DESIGN.md), section 5; R461 in [QUEUE](../QUEUE.md)):

- Real OS page-cache/write-back order, file-system journal recovery, disk/controller firmware and a real power cut.
  An optional real VM power-off is not authorized or executed by this draft.
- Chunk/entity region writes and the escrow barrier's own force/readback. R461 excludes optional region slice S6
  from the first pass; no seam result proves torn-sector behavior.
- Plain SavedData, station ordinary saves after first commit, planetary bindings, migration backups, player/POI
  files, level.dat and configuration writes outside the seam.
- Intermediate late-loss generations, cross-products of unfinished writes, simultaneous faults in two stores,
  and seam stores outside the four selected X02 writers.

X03 repairs only its proved one-record stationary shape; it does not recover lost history, conflicting/missing
identities, other repair causes, resource loss, stale copies or power-loss behavior. A single-file rollback is not
an operational recovery procedure. Complete-world backup/restore, unchanged oracles, residual-risk signatures and
the real-client/performance Gates remain separate.
