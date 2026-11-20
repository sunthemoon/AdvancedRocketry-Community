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
| G0 Identity/license/provenance | C6 artifact/provenance scans; recorded D7 and later art approvals | Final candidate identity, licence, manifest/exclusion and packaged-content review; accurate public notices | Existing approvals retain their narrow scope; final P11 review remains pending |
| G1 Reproducible build | C6 hosted Java 17 clean build, fixed host JAR hash | Independent clean reproduction, API/sources classifiers and metadata reconciliation; distinguish byte differences instead of replacing tested JAR | Release reviewer/installer roles and P11 |
| G2 Data/resources | C6 first/repeat DataGen diffs are empty and status checks are clean; automated reference checks | Archive graph/resource/asset reports on C6, reconcile complete content inventory and real-client missing-resource findings | Final P11, not an additional scope waiver |
| G3 Automated behaviour | C6 6,766 JUnit tests and 1,886 GameTests; hosted governance success | Final requirement/class inventory, skips/exclusions and failed-attempt review; preserve log-exception expiry | P11; no Gate approval from hosted success |
| G4 Dedicated/sides | C6 dedicated GameTests and source/bytecode boundary scans | A01/A02/I01–I04, two-player consistency, four-cell Forge × JEI runs and inherited DS rows | Packet P10 account/equipment schedule; P11 |
| G5 Persistence/recovery | Reviewed designs and historical short native evidence, not C6 results | J22 all 78 families; separate X01 S3/X02 S5; A03/A04/I03 and other upgrade/restart rows; exact schema/refusal/repair evidence | P1 R-044 signature, P2 upgrade limitation, P3 narrow X03 guidance; signatures do not turn downgrade failures into passes |
| G6 Security/authority | W402 access inventory and R546 dispositions; C6 automated authority/bounds checks | Independent matrix wording/security audit, I11/H02/D09 and J02–J04/J07/J27–J29 V2; applicable risk scopes | P4–P8 bay/cargo, P9 fuel, and residual-risk signatures; admission remains off |
| G7 Performance | Diagnostic tools and budget definitions only for final acceptance | Phase 5 reference measurements, I02/I05/I09/I10 and J05/J08–J11, full uninterrupted four-hour soak | P10 O7/O8/O11 and equipment arrangements; no budget reduction |
| G8 Manual flow | Tracked A–J procedures and C6-pinned preparation inputs | V1 on real GPUs, second-GPU subset, two real-client V2, I01 video and all media/actual/tester/date fields | P10 O4/O5/O7–O12; P11 final judgment |
| G9 Docs/release | This draft set and existing known issues | Complete reports/checksums/manifests, accurate README/changelog/install/save notes, uninvolved installation, independent audit, prerequisite disposition and human approval | P11 Gate/version/release/tag/main decisions, explicitly reserved to owner |

Decision IDs are from the [owner final packet](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md).
Evidence identities and pending native states are from [C6-SWITCH-REPORT](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md)
and [FINAL-CAMPAIGN-PLAN](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md).
The first three v1.8 prerequisite releases remain unapproved under
[ADR-060](../../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md).
Completing this table is not permission to create a tag.
