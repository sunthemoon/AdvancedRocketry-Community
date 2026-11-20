# v1.8.0 release evidence — candidate C5 draft

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
is accepted in this report.

## Candidate and receipt identities

| Field | C5 identity / state |
|---|---|
| Candidate commit | `9172113ad4ecfb9047432f02b97d240fc06130b6` (R555) |
| Host JAR | `advancedrocketry-community-1.20.1-1.8.0-dev.jar`, 15,946,403 bytes |
| Host JAR SHA-256 | `e5cd2beb4a38b4ea741c5103b18ba224d3f102ef6231adbc3a0849ad4f1e35c8` |
| Platform | Minecraft 1.20.1, Java 17, Forge 47.4.10 baseline / 47.4.23 compatibility |
| Declared optional client support | JEI absent or JEI 15.56.0.205; all four Forge × JEI cells must run |
| Hosted regression | 44681973284 attempt 1; clean build, 6,751 JUnit tests, 1,867 GameTests in four shards; evidence results only |
| Governance | 44681973374 attempt 1; succeeded, not release approval |
| Native selection | Eight-family pre-run, then all 78 families, separate X01 S3 and X02 S5: PENDING results |
| Final client/reference campaign | 117 A–J rows, PENDING; results cannot be copied from earlier candidates |
| Save/schema inventory | Accounting activated, ledger root 3; complete per-store schema/DataVersion census PENDING |
| Network/API inventory | Exact per-channel pins and API/classifier identity PENDING final manifest; no guessed single global protocol |
| Reviewer / installer / owner approval / release tag | PENDING / PENDING / PENDING / not created |

Identity sources: [C5-SWITCH-REPORT §2.1](../../work/v1.8.0-root-queue/records/C5-SWITCH-REPORT.md),
[W410](../../work/v1.8.0-root-queue/records/W410.md), and
[QUEUE R555](../../work/v1.8.0-root-queue/QUEUE.md). `TEST-REPORT.md` records
the independently inspected build/union archive hashes, unit counts and shards.
The document snapshot base is `45065aa8dd678b71d4eb7de4254d8a586ee4bcc0`;
it is not the tested candidate commit. Later documentation/tool commits and
historical C1–C4 results must not replace C5's tested identity.

## Document index

Filenames below identify this draft set. New report names remain code spans
until integrated; Markdown links point only to already committed shared-source
records. This avoids links to uncommitted, external or imagined evidence.

| Report | Purpose | Completion boundary |
|---|---|---|
| `REQUIREMENT-MAP.md` | Every version checklist item, Required Gate and prose delivery obligation → named evidence/decision/document | Planning coverage, not acceptance; candidate-wide audits still pending |
| `GATE-STATUS.md` | G0–G9 blockers, awaited evidence and owner dependencies | All IN_PROGRESS or BLOCKED; no passed Gate |
| `TEST-REPORT.md` | C5 hosted build/units/shards/governance; 78 native families and 117 campaign-row boundary | Native/campaign actuals pending |
| `MANUAL-TEST.md` | All 117 A–J rows, procedures and per-result metadata | All actuals, testers, dates and media pending |
| `MIGRATION-REPORT.md` | Source/candidate/schema/ID census, copy-only upgrade/refusal/repeat procedures | Complete source fixtures, output manifests and candidate outcomes pending |
| `RECOVERY-MATRIX.md` | Stop/restart, resource invariants, storage holds and repair boundaries | Native/X01/X02/X03/V2 outcomes pending |
| `PERFORMANCE.md` | Unchanged docs/17 budgets, load, reference/real-GPU measurements and four-hour soak | Every measurement pending |
| `SECURITY-REVIEW.md` | W402 access policy, R546 decisions, abuse matrix and independent review scope | Independent C5 policy/threat/result audit pending |
| `VISUAL-VALIDATION-REPORT.md` | Real-GPU environment, complete/high-risk inventory and visual/audio checks | V1, second-GPU subset and linked V2 pending |
| `MULTIPLAYER-REPORT.md` | Authenticated two-client actions, permissions, contention, passengers and reconnect | Every V2 result pending |
| `videos-or-links.md` | Hashed progression/visual/V2/measurement media catalogue | No C5 media receipt yet |
| [KNOWN-ISSUES.md](KNOWN-ISSUES.md) | Public support boundary and disclosed limitations | Existing file retained; final candidate reconciliation needed |
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
2. **Candidate identity and reproduction.** Retain C5 hosted artifacts, run attempts,
   Java/Gradle/Forge versions, exact commands/exit codes, source/API classifiers,
   schema/protocol/configuration manifest and independent clean reproduction.
   Differences in locally rebuilt bytes must be explained, not substituted for
   the tested host JAR. Complete final license/notices/README/mod metadata and
   credential/package scans, including the requested visible identity evidence.
3. **Content closure.** Reconcile PORTING_MATRIX with the complete ledger and
   accepted dispositions; no UNKNOWN or in-scope MISSING, every deferred/rejected
   item has ADR and player impact. C5 ordinary ledger validation still reports
   five PLANNED rows; inspect scope before closure, never call that a strict
   closure success. Archive per-machine happy/failure/restart and kernel/exception
   census, graph/reachability/progression reports, asset references and approved
   provenance/manifests on this candidate. Existing D7/art approvals are retained.
4. **Native recovery/security.** Complete J22's 78-family run and separate X01 S3/
   X02 S5, plus loader cuts not selected by wrappers where required. Fill every
   row and non-coverage reason from C5. Retain failed attempts, ordinary F9 and
   unreachable bay-shape exclusions, historical downgrade failures, the actual
   metadata changes, and root-3 migration/refusal/recovery observations. No E4
   result is real OS/power-loss proof. X03 requires both origins and controls.
5. **Migration.** Supply immutable stable/beta/RC and representative v1.x source
   fixtures with truthful played/reconstructed origin; K02's all-old-ID v1.7
   manifest, copy-only upgrade, two restarts, repeated migration, future/downgrade
   handling and unchanged source hashes. A startable world is not sufficient.
6. **Final campaign.** Finish preparation inputs still marked K01–K21 in the
   checklists, then fill all 117 row records with actuals, expected/actual comparison,
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
9. **Owner packet.** P1 R-044, P2 upgrade limitation and P3 X03 guidance remain
   pending; P4–P8 bay/cargo and P9 fuel require separate evidence-backed
   recommendations/signatures or continued disablement. P10's account/time/GPU/
   node choices O4/O5/O7–O12 remain pending. All six production switches are off.
   Any approved enabling commit changes the candidate and needs requalification
   and no-hook smoke; gated-path results are not production-admission proof.
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
[C5 switch report](../../work/v1.8.0-root-queue/records/C5-SWITCH-REPORT.md) and
[campaign plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md)
define the pending decisions, technical work and repetition boundaries. No
remaining v1.8 Gate is satisfied merely by adding this document set.
