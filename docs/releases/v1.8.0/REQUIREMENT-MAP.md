# v1.8.0 requirement map — candidate C6 draft

Snapshot: 2026-11-21. **IN_PROGRESS; no requirement or Gate is approved here.**
The [version document](../../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md) is the
requirement source. IDs below use its section and item order; G0–G9 are its
section 19 Required Gates. There is one row for every checklist item, followed
by the non-checklist delivery obligations. Process recommendations are not
additional acceptance tests.

C6 is commit `c8fa16e16be87554f26c402546733df6555d15e6`, JAR SHA-256
`9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`.
“C6 hosted run passed” describes automated evidence only, never acceptance.
“Document PENDING” means the named document/audit must be completed against C6.
“Campaign PENDING” and “Native PENDING on C6” must not inherit earlier results.
All A–J IDs refer to the 117-row [final campaign plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md),
sections 4 and 6. Exact steps are in its tracked checklists A–J.

## Checklist requirements

| ID | Requirement | Evidence that proves the requirement; current evidence state | Source record |
|---|---|---|---|
| 2.1 | v1.2 kernel release prerequisite | Inherited G0–G9 evidence and owner approval; owner decision PENDING, not a development exception pass | [ADR-060](../../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md) |
| 2.2 | v1.3 compatibility release prerequisite | Inherited G0–G9 evidence and owner approval; owner decision PENDING | [ADR-060](../../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md) |
| 2.3 | v1.7 release prerequisite and frozen contracts | Inherited H01–H07 plus final audit/approval; campaign and owner decision PENDING | [v1.7 handoff](../v1.7.0/RELEASE-EVIDENCE.md), [plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md) §4.8 |
| 2.4 | Item-decidable upstream inventory | Accepted ADR-061/062/063 and preparation inventory; documented development prerequisite, final candidate G0 audit PENDING | [ADR-060 acceptance](../../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md), [preparation](../../work/v1.8.0-preparation/VERIFICATION.md) |
| 9.1 | Unique registry IDs | C6 Forge construction during build/DataGen/GameTest plus ledger uniqueness checks: C6 hosted run passed; final audit PENDING | [W62 §3](../../work/v1.8.0-root-queue/records/W62.md), [C6 identity](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) §2.1 |
| 9.2 | All model/texture/sound/lang/recipe references exist | RegisteredContentResourcesTest, resource/provenance audits; C6 hosted run passed; I06 live missing-resource check PENDING | [W62 §3](../../work/v1.8.0-root-queue/records/W62.md), [I06](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 9.3 | Deterministic DataGen | C6 first/repeat DataGen logs, empty generated diffs and clean status receipts: C6 hosted run passed | [C6 run](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) §2.1; archived build receipt described in TEST-REPORT.md |
| 9.4 | Reachable technology/recipe graph | RegisteredItemReachabilityTest, ClassicAdvancementReachabilityTest and PackagedRecipeGraphTest: C6 hosted run passed; archive graph reports and I01 gameplay PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md), [I01](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 9.5 | Each machine has happy/failure/restart tests | W62 per-machine census and C6 hosted machine/recovery classes; C6 hosted run passed, final per-machine evidence reconciliation PENDING; I02/C02 native co-existence/restarts PENDING | [W62 §4](../../work/v1.8.0-root-queue/records/W62.md), [I02](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 9.6 | Full-content dedicated sidedness | C6 source and bytecode scans, dedicated GameTests: C6 hosted run passed; A01/I04 packaged client join PENDING | [C6 run](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) §2.1, [A01](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-A.md) |
| 9.7 | Complete provenance | C6 packaged provenance audit: hosted run passed; owner D7 and W275/W277 approvals already recorded, not candidate-wide G0 approval; final manifest audit PENDING | [2026-11-04 decision](../../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261104.md), [2026-11-08 decision](../../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261108.md), [W71](../../work/v1.8.0-root-queue/records/W71.md) |
| 10.1 | Dedicated base-to-endgame technology flow | I01 recorded route; campaign PENDING; O4 route arrangement owner decision PENDING | [I01](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md), [packet P10/O4](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md) |
| 10.2 | Many machines coexist and unload | I02 plus C02; campaign PENDING | [I02](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md), [C02](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-C.md) |
| 10.3 | Upgrade placeholder/old IDs | I03 all-old-ID v1.7 fixture, two restarts; campaign PENDING, K02 input still needed | [I03](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 10.4 | JEI present and absent | I04 real recipe and usage display; campaign PENDING | [I04](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 10.5 | Forge × JEI four-cell smoke | I04, Forge 47.4.10/47.4.23 × absent/JEI 15.56.0.205; campaign PENDING; full modpacks NOT_COVERED in v1.8 under accepted D9 | [D9](../../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261031.md), [I04](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 10.6 | Long production without duplication | I05 production totals with save/restart and I10 joint soak; campaign PENDING | [I05/I10](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 11.1 | Presentation sampling, high-risk full inspection | I06 and K13 classification, J15 listening; campaign PENDING | [I06](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md), [W402](../../work/v1.8.0-root-queue/records/W402.md), [W423 C6 K13](../../work/v1.8.0-root-queue/records/W423.md) |
| 11.2 | OBJ/multiblock/armour on real GPU | I07 plus second GPU subset E06/H06/I07/A12; campaign and equipment decision PENDING | [I07](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md), [packet O9](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md) |
| 11.3 | Languages, GUI scales, resource reload | I08, A13 and J14/J26; campaign PENDING, effective display cells follow C2/R535 | [I08](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md), [display convention](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-A.md) |
| 11.4 | Zero missing textures/models | I06 complete client-log scan and visual inspection; campaign PENDING; deliberate invalid-pack I07 observations recorded separately | [I06/I07](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 11.5 | Candidate technology-flow video | I01 video with build hash, tester and UTC times; campaign/owner O4 PENDING | [I01](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md), [packet](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md) |
| 12.1 | Uniform new-machine packet policy | ClassicControlsChannelGameTests and holder-policy tests: C6 hosted run passed; I11/K18 V2 refusals and independent matrix wording review PENDING | [W402 §6/10](../../work/v1.8.0-root-queue/records/W402.md), [I11](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 12.2 | Automation conserves resources | ProcessPortGameTests and machine conservation classes: C6 hosted run passed; I05/I10 campaign PENDING; cargo/loader native families PENDING on C6 | [W62 §3](../../work/v1.8.0-root-queue/records/W62.md), [C6 report](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) §§4–8 |
| 12.3 | Endgame permissions retained | I11/H02 owner, non-owner and member variants; campaign PENDING, automated endgame authority evidence in C6 hosted run | [I11](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md), [H02](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-H.md) |
| 12.4 | Recipe/data-pack overload bounds | DataPackOverLimitReloadGameTests and serializer bounds: C6 hosted run passed; final security audit checks ADR-076 limits/expiry, document PENDING | [W62 §3](../../work/v1.8.0-root-queue/records/W62.md), [ADR-076](../../decisions/ADR-076-DATA-PACK-RELOAD-ENTRIES-OUTSIDE-THE-MOD.md) |
| 12.5 | Unreviewed imports blocked from JAR | C6 provenance/JAR audits: hosted run passed; final G0 package audit PENDING, no human approval inferred from validator success | [W71](../../work/v1.8.0-root-queue/records/W71.md), [C6 identity](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) §2.1 |
| 13.1 | Many idle/active block entities | I02 scaled reference layout, C01 and I10; campaign PENDING | [I02/I10](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md), [C01](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-C.md) |
| 13.2 | Complex OBJ/multiblock rendering | I09 and A12 real frame times versus baseline; campaign PENDING | [I09](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 13.3 | Resource load time and memory | I09 three reloads, raw time/heap/cache records; campaign PENDING | [I09](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 13.4 | Large technology/JEI response | I09 per-category opening/search timing plus I04 correctness; campaign PENDING | [I09/I04](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 13.5 | Four-hour reference soak within D9 scope | I10, A08/A09, I05 and J05/J08–J11; campaign PENDING, O7 account arrangement PENDING; not four hours per cell | [I10](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md), [D9](../../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261031.md) |
| 14.1 | No UNKNOWN classic matrix entries | Final PORTING_MATRIX/ledger reconciliation using W71 counts; document PENDING, candidate audit still required | [W71](../../work/v1.8.0-root-queue/records/W71.md), [matrix](../../PORTING_MATRIX.md) |
| 14.2 | No in-scope MISSING; deferred/rejected items have ADRs | Final matrix/ledger/ADR-062 closure audit; document PENDING; ordinary ledger validation is not strict closure | [W71](../../work/v1.8.0-root-queue/records/W71.md), [ADR-062](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) |
| 14.3 | All imports have complete provenance | Candidate G0 audit against approved records, manifests and packaged bytes; document PENDING; automated C6 audit exists | [W71](../../work/v1.8.0-root-queue/records/W71.md), [asset decisions](../../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261108.md) |
| 14.4 | Complete chain reachable and playable | Hosted recipe graphs plus I01/I04 and video; campaign PENDING, owner O4 PENDING | [I01/I04](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 14.5 | Resource/server/visual/performance acceptance | C6 hosted, J22 native, all A–J campaign results and independent audit; native/campaign/owner decision PENDING | [plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md), [packet P11](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md) |
| 14.6 | Zero Critical/High | Candidate-wide findings register and independent audit after all runs; document and owner decision PENDING; no clean verdict from test counts | [plan §5–7](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md), [packet P11](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md) |

## Required Gates (section 19)

| ID | Requirement | Planned evidence and current state | Source record |
|---|---|---|---|
| G0 | Identity/license/provenance | C6 artifact/provenance checks exist; final candidate-wide manifest/licence/identity audit PENDING | [release rules](../../06-RELEASE-AND-ACCEPTANCE-GATES.md), [W71](../../work/v1.8.0-root-queue/records/W71.md) |
| G1 | Reproducible build | C6 hosted clean Java 17 build passed; independent reproduction, classifier identity and final artifact reconciliation PENDING | [C6 identity](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) §2.1, [plan K01](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md) |
| G2 | Data/generated resources | C6 first/repeat DataGen clean; final retained resource reports and candidate audit PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md), [C6 identity](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| G3 | Automated behaviour | C6 6,766 units and 1,886 GameTests passed; candidate row/class reconciliation and retained failures/exclusions review PENDING | [C6 run](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) §2.1; TEST-REPORT.md |
| G4 | Dedicated server/sides | A01/A02, I01–I04 and V2; campaign PENDING | [plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md) §§4–5 |
| G5 | Persistence/recovery | J22 all 78 families, separate X01/X02 and A03/A04/I03 upgrades; native/campaign PENDING, P1/P2/P3 decisions PENDING | [C6 report](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md), [packet](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md) |
| G6 | Security/authority | I11/H02/D09, J02–J04/J07/J27–J29, independent C6 security audit; campaign/owner signatures PENDING | [W402](../../work/v1.8.0-root-queue/records/W402.md), [fuel readiness](../../work/v1.8.0-root-queue/records/FUEL-ADMISSION-REPORT.md) |
| G7 | Performance | Phase 5 reference workload, I02/I05/I09/I10 and J05/J08–J11; campaign PENDING, O7/O8/O11 arrangements PENDING | [plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md), [packet P10](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md) |
| G8 | Manual flow | V1/V2 phases, I01 video, all A–J manual rows with media/hash/tester/date; campaign/owner arrangements PENDING | [plan §6](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md), [packet P10](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md) |
| G9 | Documentation/release | Complete report set, installation by uninvolved tester, checksums, final independent audit and P11; document/owner decision PENDING | [release rules](../../06-RELEASE-AND-ACCEPTANCE-GATES.md), [packet P11](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md) |

## Non-checklist delivery obligations

These rows keep prose requirements visible instead of treating them as implicitly passed.

| ID | Requirement | Planned evidence and current state | Source record |
|---|---|---|---|
| 1.1 | Classic breadth of machines/equipment/assets | Content ledger and final matrix audit plus I01/I06/I07; document/campaign PENDING | [ledger](../../work/v1.8.0-content-ledger.csv), [plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md) |
| 1.2 | Understandable recipes/JEI/research/tutorial progression | I01/I04, recipe-graph and progression reports; campaign/document PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md), [checklist I](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 1.3 | Recognizable models/sounds with clear provenance | I06/I07/J15 and approved source records; campaign PENDING | [W402](../../work/v1.8.0-root-queue/records/W402.md), [owner audio decision](../../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261104.md) |
| 3.1 | Batched remaining machines/multiblocks | Ledger, batch records and I02; document/campaign PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md) |
| 3.2 | Items/armour/components/tools/recipes/advancements | Recipe graph plus I01/I06/I07/J14/J17; campaign PENDING | [plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md) |
| 3.3 | OBJ/JSON/textures/GUI/audio/languages | I06–I09/J15; campaign PENDING | [checklist I](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md), [checklist J](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-J.md) |
| 3.4 | Research/technology/balance loop | I01/J19 and graph report; campaign PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md), [plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md) |
| 3.5 | Per-batch provenance/hash/license/transforms | Approved records plus final C6 JAR audit; document PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md) |
| 3.6 | Close PORTING_MATRIX entries individually | W71 derived counts and Root's matrix audit; document PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md) |
| 3.7 | ADRs for missing/redesigned/rejected content | ADR-062 disposition/impact audit; document PENDING | [ADR-062](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) |
| 4.1 | No bulk-copy substitute for migration | G0 per-file manifests and batch audit; document PENDING | [ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md) |
| 4.2 | No unclear/incompatible community assets | C6 provenance audit plus G0 human review; document PENDING | [ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md) |
| 4.3 | No exact old-ID/recipe-value parity promise | Publish ADR-061/062 identities and redesigned values; document PENDING | [ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md), [ADR-062](../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) |
| 4.4 | No direct 1.12.2 world upgrade promise | Migration/install instructions explicitly state this boundary; document PENDING | [compatibility promise](../../../PROJECT-CONFIG.md), [roadmap §6](../../16-POST-1.0-VERSION-ROADMAP.md) |
| 5.1 | Stable namespace/registry IDs | ADR-061 identity table, I03 old-ID comparison; campaign PENDING | [ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md), [I03](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 5.2 | Every batch has a source manifest | C6 provenance audit and retained manifest index; document PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md) |
| 5.3 | Machines use v1.2 kernel or exception ADR | Final per-machine architecture/exception census against ADR-061/062; document PENDING | [contracts](../../work/v1.8.0-contract-coverage.md) |
| 5.4 | DataGen/validator-checkable content | C6 hosted data/resource checks passed; archive reports PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md) |
| 5.5 | No unreachable nodes/circular hard locks | Hosted recipe-graph tests passed; I01 playability and graph report PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md), [I01](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 8.1 | Missing Mapping plan for new/renamed registry IDs | ADR-061 additive-ID policy, I03 unchanged placeholders; any actual rename requires explicit mapping before release; document/campaign PENDING | [W62 10.3](../../work/v1.8.0-root-queue/records/W62.md), [I03](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 8.2 | Scan representative worlds before removal | I03/A03/B01/E10 manifests and final disposition audit; campaign PENDING, no permission to remove IDs | [plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md) |
| 8.3 | Placeholder-to-final identity migration | I03 every old ID, state/use check and two restarts; campaign PENDING; identity-preserving replacement is not an invented remap | [I03](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 8.4 | Recipe change does not rewrite inventories | I03 pre/post inventory census and I05 resource totals; campaign PENDING | [checklist I](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 8.5 | Missing content dependency is explicit, not silently deleted | D10 missing-provider/mod refusal/reinstall on copies; campaign PENDING; final content-disposition audit must confirm applicable dependencies | [D10](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-D.md) |
| 15.1 | Archive updated matrix | W71 counts plus committed Root matrix update; document PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md) |
| 15.2 | Archive batch provenance manifests | W71 provenance index plus approved records; document PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md) |
| 15.3 | Archive recipe graph | W71 collector from C6 graph reports; document PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md) |
| 15.4 | Archive progression report/video | W71 witness report plus I01 recording; document/campaign PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md), [I01](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md) |
| 15.5 | Archive asset validation | C6 audits and I06/I07, W71 collector; document/campaign PENDING | [W71](../../work/v1.8.0-root-queue/records/W71.md) |
| 15.6 | Archive scoped performance/compatibility report | I04/I10 under D9; campaign PENDING; full modpack NOT_COVERED in v1.8 | [D9](../../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261031.md) |
| 17.1 | Asset batches independently reversible | Manifest/disposition review documents batch rollback without silent ID/data removal; document PENDING | [ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md) |
| 17.2 | Registry removal has migration first | I03 and additive-ID audit; document/campaign PENDING | [W62 10.3](../../work/v1.8.0-root-queue/records/W62.md) |
| 17.3 | Disabled/deferred machine scope has approval and impact | ADR-062 and packet P4–P9 per-switch decisions; document/owner decision PENDING | [packet](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md) |
| 17.4 | Disputed sources isolated, not distributed | G0 manifest/JAR audit against exclusions; document PENDING | [ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md) |
| 19.cmd1 | clean build | C6 hosted build passed; clean release reproduction PENDING | [C6 identity](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| 19.cmd2 | test | C6 archived JUnit XML: 6,766 tests, zero failure/error/skip; hosted run passed | [C6 identity](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md); TEST-REPORT.md archive receipt |
| 19.cmd3 | runData | C6 first/repeat hosted generation passed | [C6 identity](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| 19.cmd4 | git diff --exit-code after generation | Both C6 archived generated diffs empty; status receipts explicitly PASS clean; hosted run passed | [C6 identity](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |
| 19.cmd5 | runGameTestServer | C6 hosted four-shard union 1,886 tests passed; no native restart implication | [C6 identity](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) |

## Requirements with no planned evidence

No wholly unmapped checklist item or Required Gate was found: every row above has
a named automated family/class, campaign ID, document audit or owner decision.
This is a **planning map**, not proof that a generic audit has already enumerated
all subcases. Root must still complete the candidate-wide per-machine census
(9.5/5.3), matrix/disposition audit (14.1/14.2), package/provenance audit (G0),
and independent final audit (14.6/G9). Their deliverables and responsible release
roles remain PENDING; this draft does not appoint a reviewer or installer.

Keep these narrower non-coverage boundaries explicit: B11.2 and B17.5 have
reasoned unreachable native shapes, ordinary loader F9 is excluded, C05/H04 real
third-party mods are NOT_COVERED under Root's O6/D9 scope, and 48 unreferenced
textures are outside the campaign under R546. The exact shapes are not passes.
See [C6 report](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) §§3/8/13,
[fuel inventory](../../work/v1.8.0-root-queue/records/FUEL-ADMISSION-REPORT.md) F12/F22–F29,
and [plan §7.3](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md).
Unbuilt K02/K05/K16/K18 and other marked campaign inputs are preparation gaps,
not missing planned coverage or permission to claim acceptance.
