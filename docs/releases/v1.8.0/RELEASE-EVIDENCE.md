# v1.8.0 release evidence — candidate C6 draft

**DRAFT / IN_PROGRESS. This file is incomplete; do not tag or publish a release.**
This is an unofficial Advanced Rocketry community rewrite, not an official
Mojang/Microsoft/Forge or original-project release. “Classic Content Complete
Candidate” is a candidate description, not a claim that full migration or stable
acceptance is complete. The build below is still `1.8.0-dev`.

The [authoritative status](../../status/CURRENT_VERSION.md) keeps the earliest
unfinished acceptance at v1.0.0 IN_PROGRESS and active v1.8.0 development at
IN_PROGRESS / IMPLEMENTING. [ADR-060](../../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md)
permits development, not inherited release approval or an extension of its
candidate-freeze expiry. No Gate, requirement, version, risk or admission switch
is newly accepted in this report. Recorded decisions below retain their exact scopes.

Reconciled on 2026-11-25 under Root decision R693. **C6** names the tested
candidate below; **W** numbers name worker records, **R** numbers without a hyphen
name Root decisions in [QUEUE](../../work/v1.8.0-root-queue/QUEUE.md), and **R-**
numbers name [registered risks](../../11-RISK-REGISTER.md). **P** numbers are
[final-packet items](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md),
**O** numbers are its resource arrangements, and **K** numbers name campaign
preparation packages. V1 means one real-GPU client, V2 two real clients, and REF
the 4-vCPU / 8-GiB Debian 12 reference node. X01 is application-level crash
replay, X02 storage-fault injection, and X03 the narrow whole-world repair.

## Candidate and receipt identities

| Field | C6 identity / state |
|---|---|
| Candidate commit | `c8fa16e16be87554f26c402546733df6555d15e6` (R581) |
| Host JAR | `advancedrocketry-community-1.20.1-1.8.0-dev.jar`, 16,026,312 bytes |
| Host JAR SHA-256 | `9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4` |
| Platform | Minecraft 1.20.1, Java 17, Forge 47.4.10 baseline / 47.4.23 compatibility |
| Declared optional client support | JEI absent or JEI 15.56.0.205; all four Forge × JEI cells must run |
| Hosted regression | 44770334930 attempt 1; clean build, 6,766 JUnit tests, 1,886 GameTests in four shards; evidence results only |
| Governance | 44770334865 attempt 1; succeeded, not release approval |
| Native selection | Attempt 2 ended 2026-11-22: 78 families, **74 PASS / 4 FAILED**, report FAILED (R615/R616/R618). X03's separate both-origin rerun passed (R632); the resulting selection is 75 PASS / 3 FAILED, without overwriting attempt 2. The three downgrade families stay FAILED (R679). [C6 results and fingerprints](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md#21-candidate-c6) bind these results to C6. |
| X01 / X02 | X01 stays FAIL: 72 of 82 derived states pass both starts, 10 late-loss states fail (R637/R641). X02 retains original images: 49 SEEN_HELD / 14 CAUGHT_LATER / 5 NOT_CAUGHT / 0 INCOMPLETE / 4 NOT_APPLICABLE; alternative repaired-image totals are separate, not passes (R636/R639). [C6 section 13](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md#13-x01-to-x03-c6-results-recorded-dispositions-and-residuals) preserves both histories. |
| Final client/reference campaign | All 117 row actuals remain PENDING. Phase 2 rounds 01/02 ran 43 real-GPU launch-mode sessions, with no step scored PASS; 45 V1 rows / 394 steps retain NOT_COVERED or NOT_RUN step dispositions (R686–R688/R690; [W471](../../work/v1.8.0-root-queue/records/W471.md), [W472](../../work/v1.8.0-root-queue/records/W472.md)). Attach mode, packaged-server campaign, V2, REF and soak remain open. |
| Production admission | All six switches stay off in v1.8 (R682). [ADR-062 revision 8](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md#revision-8--production-admission-deferred-to-v190-2026-11-24) defers the five bay/cargo admissions to v1.9; the fuel loader stays off separately. Protection, retained-history recovery and migration remain live. |
| Residual risks | R679 signs only R-044's downgrade limit. R691 signs R-039, R-042, and R-044's unsupported fuel-loader root and shared denial pool; the owner is responsible and Root signs under the [2026-11-24 delegation](../../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261124.md). R-021/R-030/R-035/R-040/R-041/R-043 remain unsigned with preconditions below. No failed result or Gate is passed. |
| Save/schema inventory | Accounting activated, ledger root 3; complete per-store schema/DataVersion census PENDING |
| Network/API inventory | Exact per-channel pins and API/classifier identity PENDING final manifest; no guessed single global protocol |
| Reviewer / installer / owner approval / release tag | Independent final reviewer and uninvolved installer PENDING; provenance review is completed by Root as an AI agent (R683/R685), not a human; owner P11 PENDING; tag not created. |

Identity sources: [C6-SWITCH-REPORT §2.1](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md),
[W423](../../work/v1.8.0-root-queue/records/W423.md), and
[QUEUE R581](../../work/v1.8.0-root-queue/QUEUE.md). `TEST-REPORT.md` records
C6's inspected build archive, unit counts and supplied hosted metadata, plus the unchanged historical C5 build/union receipts.
The original draft snapshot base was `45065aa8dd678b71d4eb7de4254d8a586ee4bcc0`;
it is not the tested candidate commit. Later documentation/tool commits and
historical C1–C5 results must not replace C6's tested identity.

## Document index

Markdown links identify tracked reports; absent manifests and external evidence
remain code spans. Historical reports are not silently relabelled as C6 audits.

| Report | Purpose | Completion boundary |
|---|---|---|
| `REQUIREMENT-MAP.md` | Every version checklist item, Required Gate and prose delivery obligation → named evidence/decision/document | Planning coverage, not acceptance; candidate-wide audits still pending |
| `GATE-STATUS.md` | G0–G9 blockers, awaited evidence and owner dependencies | All IN_PROGRESS or BLOCKED; no passed Gate |
| [TEST-REPORT.md](TEST-REPORT.md) | C6 hosted build/units/shards/governance; 78 native families and 117 campaign-row boundary | Native results and named hosted mappings recorded; campaign row actuals pending |
| [MANUAL-TEST.md](MANUAL-TEST.md) | All 117 A–J rows, procedures and per-result metadata | Round identities/media exist; every row actual remains pending under R690 |
| `MIGRATION-REPORT.md` | Source/candidate/schema/ID census, copy-only upgrade/refusal/repeat procedures | Complete source fixtures, output manifests and candidate outcomes pending |
| [RECOVERY-MATRIX.md](RECOVERY-MATRIX.md) | Stop/restart, resource invariants, storage holds and repair boundaries | Native/X01/X02/X03 results recorded; partial K02 and V2 coverage remain open |
| `PERFORMANCE.md` | Unchanged docs/17 budgets, load, reference/real-GPU measurements and four-hour soak | Every measurement pending |
| `SECURITY-REVIEW.md` | W402 access policy, R546 decisions, abuse matrix and independent review scope | Independent C6 policy/threat/result audit pending |
| [VISUAL-VALIDATION-REPORT.md](VISUAL-VALIDATION-REPORT.md) | Real-GPU environment, complete/high-risk inventory and visual/audio checks | 43 sessions recorded; per-step scoring, remaining V1, second GPU and V2 pending |
| `MULTIPLAYER-REPORT.md` | Authenticated two-client actions, permissions, contention, passengers and reconnect | Every V2 result pending |
| [videos-or-links.md](videos-or-links.md) | Hashed progression/visual/V2/measurement media catalogue | Round 01/02 seals and media recorded; final row-linked catalogue incomplete |
| [KNOWN-ISSUES.md](KNOWN-ISSUES.md) | Public support boundary and disclosed limitations | R679–R682/R691 disclosures exist; retained unchanged by this reconciliation |
| `checksums.txt` | Final committed report and artifact integrity manifest | Absent at this snapshot; Root must create at completion |
| `development-artifacts.json` | Distinct identities for development and tested candidate artifacts | Absent at this snapshot; Root must create without relabelling history |
| `evidence-index.json` | Durable locations, hashes, run identities and per-report evidence | Absent at this snapshot; Root must create from actual receipts |

Required report layout is from [roadmap §8](../../16-POST-1.0-VERSION-ROADMAP.md),
[release gates](../../06-RELEASE-AND-ACCEPTANCE-GATES.md) and
[final campaign plan §6](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md).
The [W71 collector](../../work/v1.8.0-root-queue/records/W71.md) supplies the
content/asset/graph staging procedure, not final acceptance by itself.

## Missing evidence and decisions before this file can be complete

1. **Inherited prerequisites and release roles.** Complete or precisely disposition
   inherited/current obligations under ADR-060 without silently extending it.
   v1.2/v1.3/v1.7 prerequisites remain unchecked. Appoint independent candidate
   reviewer, release owner and an installer uninvolved in development. Archive
   their actual review and installation evidence.
2. **Candidate identity and reproduction.** Retain C6 hosted artifacts, run attempts,
   Java/Gradle/Forge versions, exact commands/exit codes, source/API classifiers,
   schema/protocol/configuration manifest and independent clean reproduction.
   Differences in locally rebuilt bytes must be explained, not substituted for
   the tested host JAR. Complete final license/notices/README/mod metadata and
   credential/package scans, including the requested visible identity evidence.
3. **Content closure.** Reconcile PORTING_MATRIX with the complete ledger and
   accepted dispositions; no UNKNOWN or in-scope MISSING, every deferred/rejected
   item has ADR and player impact. The C6 build's five PLANNED rows are historical:
   R682 and ADR-062 revision 8 now give 653 units, zero PLANNED, 30 DEFERRED,
   214 IMPLEMENTED, 257 REDESIGNED, 94 MERGED and 58 REJECTED. The ledger closure
   check passes; this is a disposition/form check, not runtime or Gate acceptance.
   Archive per-machine happy/failure/restart and kernel/exception
   census, graph/reachability/progression reports, asset references and approved
   provenance/manifests on this candidate. Existing D7/art approvals are retained.
4. **Native recovery/security.** J22's 78-family run and separate X01/X02 runs
   have recorded results above; remaining work is exact uncovered-row disposition,
   including loader cuts not selected by wrappers. [W466/R671](../../work/v1.8.0-root-queue/records/W466.md)
   supplies hosted A1 evidence for 89 rows / 86 methods / 106 mappings, not native
   restart proof. K02 remains partial: [W461/R664](../../work/v1.8.0-root-queue/records/W461.md)
   gives each of 89 sealed cuts a passing C6 attempt, retaining earlier failures;
   [W465/R667](../../work/v1.8.0-root-queue/records/W465.md) records 7 PASS / 3 FAIL
   in the loaded-chunk flight matrix. Three pauses are NOT_REACHABLE_ON_C6
   ([W462](../../work/v1.8.0-root-queue/records/W462.md)); four old cuts are unmatched,
   and passenger/client coverage remains open. Retain failed attempts, ordinary F9 and
   unreachable bay-shape exclusions, historical downgrade failures, the actual
   metadata changes, and root-3 migration/refusal/recovery observations. No E4
   result is real OS/power-loss proof. X03 requires both origins and controls.
5. **Migration.** Supply immutable stable/beta/RC and representative v1.x source
   fixtures with truthful played/reconstructed origin; K02's all-old-ID v1.7
   manifest, copy-only upgrade, two restarts, repeated migration, future/downgrade
   handling and unchanged source hashes. A startable world is not sufficient.
6. **Final campaign.** Preparation packages are delivered, including explicitly
   partial packages; delivery is not execution. Finish phase 2's remaining V1
   steps with their own oracles, depth first per row under R690, then fill all
   117 row records with actuals, expected/actual comparison,
   tester/UTC/runtime/fixture identities and media/log hashes. Complete I01
   progression video, all-new-screen and high-risk visual inspection, en/zh/scales/
   resource reload, second real-GPU subset and authenticated two-real-client V2.
   Repeat I04's four Forge × JEI cells; old W188 results are history only.
7. **Reference budgets.** Complete phase 5 after V2: reference hardware/load
   measurements, idle/active many machines, complex rendering, resource loading,
   JEI response, J05/J08–J11 costs/availability and an uninterrupted four-hour
   joint soak. Report every failure and unchanged docs/17 thresholds. The
   first-event flush and inherited endgame flush follow their explicit design
   follow-up rules if reference budgets fail. D9 moves full modpacks to v1.9,
   not the v1.8 reference load or soak.
8. **Independent candidate audits.** Reconcile K13 access policies and silent
   refusal receipts against source under R546, plus all native/V2/bounds/abuse
   outcomes. Publish candidate-wide findings with severity and closure evidence.
   Zero Critical/High and reviewed Medium dispositions are not established yet.
9. **Owner packet.** P1/P2/P3/P4–P9/P12 are decided as recorded below; do not
   request those choices again. P10 still needs the owner's accounts/UUIDs,
   second GPU machine, reference-node login, second participant and time slots
   (O5/O9–O12). O4/O7/O8 were selected by Root (R684). The six unsigned residual
   scopes and four intermittent items below remain P11 work. All six admission
   switches stay off; later enablement requires a new candidate and qualification.
10. **Release documentation and integrity.** Reconcile public known issues (including
    stale pending-art-review wording), final risks/capacities/operational limits,
    README/changelog/install/dependencies/save support and no-single-file-restore
    guidance. Complete the three absent manifests above, durable storage for
    logs/media/artifacts and their checksums. Preserve sealed originals; corrections
    are separate records. No checksum may rely on mutable build/Temp output.
11. **Human approval and tag ordering.** Only when all reports/evidence are complete
    may independent audit and owner P11 decide Gate/version/release approval.
    Under [AGENTS §5](../../../AGENTS.md), a tag cannot precede this file's
    completion. After explicit approval, the owner controls tag/main/release;
    verify tag rebuild and downloaded artifact equality and correct pre-release/
    stable designation. This draft creates no tag, approval or publication.

The [owner final packet](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md),
[fuel readiness](../../work/v1.8.0-root-queue/records/FUEL-ADMISSION-REPORT.md),
[C6 switch report](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) and
[campaign plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md)
define the pending decisions, technical work and repetition boundaries. No
remaining v1.8 Gate is satisfied merely by adding this document set.

## Recorded decisions and remaining P11 work

| Decision | Recorded outcome and evidence |
|---|---|
| R679 | P1 is signed on the owner's behalf: [R-044 section 5](../../work/v1.8.0-root-queue/records/R044-SIGNOFF-DRAFT.md) and its section 7 signature cover only the two downgrade boundaries; all three C6 downgrade families stay FAILED. [KNOWN-ISSUES](KNOWN-ISSUES.md) publishes complete-world backups and `ARCE-BETA-2001` despite exit 0. |
| R680 | P2 accepts KI-V180-UPGRADE-01 with migration unchanged; staging/backup-cap handling is in [KNOWN-ISSUES](KNOWN-ISSUES.md) and the [operator guide](../../ENDGAME-OPERATOR-GUIDE.md). |
| R681 | P3 approves and publishes [X03-D guidance](../../work/v1.8.0-root-queue/records/X03-D-OPERATOR-GUIDANCE.md) for C6 after both origins pass (R632); it is not a general refund or fuel repair. |
| R682 | P4–P9 keep all six admissions off. [ADR-062 revision 8](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) defers the five bay/cargo ledger units; existing-world protections/recovery remain active. |
| R683/R685 | P12 is closed: all 62 provenance records have APPROVED blocks naming Root under delegation. [W470](../../work/v1.8.0-root-queue/records/W470.md) approved 58 and found eight shared-language whole-file mismatches; R685 judged all 182 owned new keys and 36 other entries for the four c17a records and approved them. This is AI-agent review, not human review or G0 approval. W279's excluded AR 16x authorship question remains separate. |
| R684 | Root selected the scripted route plus manual subset (O4), two connected harness-driven clients for four-hour soak (O7), and Intel PresentMon (O8). External resources remain PENDING under the [delegation](../../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261124.md). |
| R686/R687/R688/R690 | [W471](../../work/v1.8.0-root-queue/records/W471.md) and [W472](../../work/v1.8.0-root-queue/records/W472.md) ran 15 + 28 sessions with no step PASS. Operator/staging failures stay recorded, J13 was interrupted before a runtime start, and two pre-join timeouts remain findings. R690 requires each step's own oracle/evidence and depth-first rows; W474's scenarios are preparation, not results. |
| R691 | [Four narrow signatures](../../work/v1.8.0-root-queue/records/RESIDUAL-RISK-SIGNOFF-DRAFTS.md) are complete for R-039, R-042 and R-044's unsupported loader root/shared 256-coordinate denial pool, after KI-V180-HOLD-01 and KI-V180-GUARD-01 publication. Six scopes below stay unsigned; no scope accepts new admission, power-loss safety or a Gate. |

Phase 2 still needs remaining V1 rows/steps, languages/scales/JEI/Forge cells,
per-step visual judgments, audio listening, frame-time measurements and the second
GPU. J01/J06/J23–J26 need attach mode with the owner's account (R686).
Phase 3's packaged-server/restart campaign and phase 4's authenticated V2 remain
PENDING; reference-node benchmarks and four-hour soak remain phase 5 in the
[five-phase plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md).
Those runs, independent review, uninvolved installation and complete manifests
close the remaining evidence work; an offline launch round cannot substitute.

The six unsigned scopes require the following evidence before Root may sign,
as specified in [the residual drafts](../../work/v1.8.0-root-queue/records/RESIDUAL-RISK-SIGNOFF-DRAFTS.md):

| Scope | Preconditions still PENDING |
|---|---|
| R-021, ordinary machine-root save refusal | Native refusal/repair/writer-success/two-restart resource evidence or explicit equivalent mappings for machine/classic/solar scopes; I02/I05 unload/restart/conservation. KI-V180-GUARD-01 is published, but does not supply that evidence. |
| R-030, registry refusal/quarantine/sizing | G01 startup/load/flush/restarts, J19 survey/analysis/rebind, A09/A08/I10, and canonical-refusal carry-over review/validation or native rerun through C6's startup wrapper. Keep the extended 1,000-mission/UNKNOWN cases distinct. |
| R-035, ordinary transfer/sealed/rejoin and retained history | A04/K02 disposition separating 89 sealed cuts, seven flight passes, three unreachable pauses, four unmatched old cuts and player/client gaps; actual coverage or bounded approved ADR for unmet Required Gates; A10/A17/J10/J11/J17/A09/A08/I10 and explicit intermittent findings. J11 keeps R245's follow-up rule. |
| R-040, transaction writer/shutdown wait | A04/A10/A17 journal recovery and final drain; near-bound/admission/startup and overlapping IO/GC on A09/A08/I10 reference runs; K02/intermittent dispositions. |
| R-041, registry writer/terminal locks/epochs | G01/G04/G07 restart, mission and terminal/replay comparisons, A13/J19 pending/busy/research behavior and A09/A08/I10 IO/GC, using C6's service adapters. |
| R-043, retained bay recovery/waits | J01–J05/J09 real-client observations, two clients/LAN host swap, all 23 REF waits, more than three slots/two claims, capture/work contention; disposition of the unprepared 34-slot/64-claim extreme and K11 client gaps; A09/A08/I10 whole-tick costs and unfinished waits; retained-recovery/log-only/seat disclosure. |

The owner's 2026-10-26 R-035 acceptance remains limited to its named UNKNOWN
settlement-loss accounting, capacities and P1-F8 crash windows with W83/W90
prerequisites; it is not acceptance of the broader unsigned scope above. R691's
four signatures likewise retain their stated exclusions and re-review/expiry
conditions in the residual drafts. Off-state operation does not erase existing
store history, migration, save refusal or server-wide protective holds.

The four intermittent items remain open in [CURRENT_VERSION](../../status/CURRENT_VERSION.md#intermittent-tests-and-observations),
with Root's closure rules R672/R674/R676/R677, rather than a blanket success from later aggregate runs:

| Item | Closure condition |
|---|---|
| Escrow proof/monitor timing | Remaining proof-budget and recurrence evidence; a second natural supersession and arbitrary saves/IO stalls are not prevented by W209's scoped fix. See [W209](../../work/v1.8.0-root-queue/records/W209.md). |
| Terminal-journal unit acknowledgement | The unexplained 2026-11-17 failure without XML remains an observation; a recurrence requires a diagnostic task, not acceptance of Root's unproved slow-write hypothesis. See [QUEUE W372D/W372R](../../work/v1.8.0-root-queue/QUEUE.md). |
| Light transmission | [QUEUE W433/R602](../../work/v1.8.0-root-queue/QUEUE.md) requires the held branch to merge after the C6 freeze and shared runs to pass; its isolated passing branch is not closure. |
| Bay mission matrix | [W468/R673](../../work/v1.8.0-root-queue/records/W468.md) retains the scheduler/fixture timing failure; later runs must establish no recurrence. The fixture fix waits for v1.9 or another candidate. |

First-connection timeouts are additionally open under A18/KI-V180-CONNECT-01,
with no tolerated failure ratio (R690). P11 remains the owner's **one confirmation**
for the tag, `main` merge, publication, and Gate/version approval after the evidence
is complete. No such confirmation or release action has occurred here.
