# v1.8.0 Gate status — C6 draft

The [authoritative status](../../status/CURRENT_VERSION.md) keeps the release
acceptance cursor at **v1.0.0 IN_PROGRESS** and active v1.8.0 development at
**IN_PROGRESS / IMPLEMENTING**. This report changes neither. C6 is a test
candidate, not a stable release or an acceptance of ADR-060's expiry obligations.

```yaml
version: v1.8.0
status: IN_PROGRESS
phase: IMPLEMENTING
build: 1.20.1-1.8.0-dev
candidate_commit: c8fa16e16be87554f26c402546733df6555d15e6
candidate_jar_sha256: 9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4
required_gates: [G0, G1, G2, G3, G4, G5, G6, G7, G8, G9]
gates:
  G0: IN_PROGRESS
  G1: IN_PROGRESS
  G2: IN_PROGRESS
  G3: IN_PROGRESS
  G4: BLOCKED
  G5: BLOCKED
  G6: BLOCKED
  G7: BLOCKED
  G8: BLOCKED
  G9: BLOCKED
overall: IN_PROGRESS
independent_candidate_reviewer: ""
human_approved_by: ""
human_approved_at: ""
```

Statuses describe remaining acceptance, not new approvals. Requirements are from
[release gates](../../06-RELEASE-AND-ACCEPTANCE-GATES.md) and the
[v1.8 version document](../../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md).

| Gate | Evidence available | Still waiting for | Owner decision/arrangement |
|---|---|---|---|
| G0 Identity/license/provenance | C6 artifact scans; P12 closed, all 62 records APPROVED by delegated AI reviewer Root (R683/R685; [W470](../../work/v1.8.0-root-queue/records/W470.md)) | Final candidate-wide identity/notices/licence/manifest/exclusion/source-offering audit and visible identity receipts; W279 excluded-authorship question remains separate | P12 is decided, not missing; independent final review and owner P11 still PENDING |
| G1 Reproducible build | C6 hosted clean Java 17 build and fixed JAR hash; K01 source/API/fixture artifacts delivered ([W424](../../work/v1.8.0-root-queue/records/W424.md), [W426/R592](../../work/v1.8.0-root-queue/records/W426.md)) | Independent clean reproduction, final classifier/runtime/config manifest and reconciliation of local-vs-host bytes; no replacement of the tested hash | Final reviewer/installer and P11 PENDING |
| G2 Data/resources | C6 first/repeat DataGen clean; W420 graph witness and R682/ADR-062 revision 8 zero-PLANNED ledger closure | Complete candidate resource/graph/manifest audit and real-client missing-resource coverage; 43 sessions are a subset, not all assets | Scope decision is made; final P11 PENDING |
| G3 Automated behaviour | C6 6,766 units / 1,886 GameTests; [W466/R671](../../work/v1.8.0-root-queue/records/W466.md) maps 89 named rows / 86 methods | Final requirement/class inventory, exclusions/failed-attempt review, log-exception expiry and four intermittent items in [release evidence](RELEASE-EVIDENCE.md); aggregate success does not close them | P11 PENDING; no Gate approval from hosted success |
| G4 Dedicated/sides | C6 dedicated GameTests, selected native campaign and preparation cell starts | Phase 3 A01/A02/I01–I04 and inherited dedicated rows, four-cell packaged/client campaign, authenticated two-player consistency; launch-mode V1 is not that campaign | Owner accounts/UUIDs, node/equipment/time under P10; P11 PENDING |
| G5 Persistence/recovery | Attempt 2 74 PASS / 4 FAILED; X03 separate both-origin pass R632; X01 FAIL (72/82 states pass, 10 fail); X02 classifications retained; K02 89 sealed cuts with passing attempts, flight 7 PASS / 3 FAIL ([W461](../../work/v1.8.0-root-queue/records/W461.md), [W465](../../work/v1.8.0-root-queue/records/W465.md)) | A03/A04/I03 and broader source/schema/repeat-upgrade evidence; three unreachable pauses, four unmatched cuts, unselected loader cuts, player/client recovery and six unsigned scopes remain open | P1/P2/P3 decided R679/R680/R681; signatures never pass downgrade failures; P11 PENDING |
| G6 Security/authority | C6 automated and selected native bounds/authority checks; W402/R546 policies; four narrow signatures R691 | Independent source/receipt security audit, I11/H02/D09 and J02–J04/J07/J27–J29 V2; six unsigned risk scopes with prerequisites in [residual drafts](../../work/v1.8.0-root-queue/records/RESIDUAL-RISK-SIGNOFF-DRAFTS.md); X01/X02 limits remain | All six switches stay off (R682); four signatures are not admission or G6 acceptance; P11 PENDING |
| G7 Performance | Fixed docs/17 budgets, diagnostic tools and native development-host timings | Phase 5 REF, A09 hooks-only run (R648), I02/I05/I09/I10/J05/J08–J11, qualified GPU frame times and uninterrupted four-hour soak; K10 railgun shortfall needs REF resolution | O7/O8 selected by Root R684; accounts, node, second GPU and time still needed; P11 PENDING |
| G8 Manual flow | [W471](../../work/v1.8.0-root-queue/records/W471.md)/[W472](../../work/v1.8.0-root-queue/records/W472.md): 43 real-GPU sessions; no step PASS (R686–R688/R690) | Remaining V1 per-step oracle review, languages/scales/Forge/JEI, audio/frame tools, second GPU, I01 video; J01/J06/J23–J26 attach-mode owner account and all V2; every row actual PENDING | O4 selected R684; owner resources O5/O9–O12 and P11 still needed |
| G9 Docs/release | Reconciled draft set; downgrade/upgrade/admission/guard/hold issues published; recorded decisions R679–R685/R691 | Complete reports/index/checksums/classifier manifest, public installation/save support, uninvolved installation, independent audit, prerequisite disposition and all remaining P11 evidence | Owner's one confirmation for tag, main merge, release and Gate/version approval remains PENDING |


Decision IDs are from the [owner final packet](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md).
Evidence identities and recorded native states are from [C6-SWITCH-REPORT](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md)
and [FINAL-CAMPAIGN-PLAN](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md).
The first three v1.8 prerequisite releases remain unapproved under
[ADR-060](../../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md).
No Gate passes. [Release evidence](RELEASE-EVIDENCE.md) defines candidate,
worker/decision/risk/package identifiers and lists each unsigned scope and
intermittent closure condition. R691 signs only R-039, R-042 and R-044's
unsupported loader root/shared denial pool. R-021/R-030/R-035/R-040/R-041/R-043
stay unsigned. Completing this table is not permission to create a tag.
