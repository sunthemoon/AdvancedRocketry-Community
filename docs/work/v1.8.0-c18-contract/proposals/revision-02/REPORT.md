# C18 contract revision 2 handoff

Date: 2026-10-03. Delegated read-only contract author; root checkout
`D:/GitHub/AdvancedRocketry-Community`, HEAD
`cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6` with unrelated active dirty work.
The original revision-1 proposal remains unchanged. Revision 2 is **PROPOSED**,
not accepted, independently re-reviewed, implemented or Gate-complete.

## Completed scope

- New [ADR-066 revision 2](ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md),
  SHA-256 `7c6da4ab6f3974d537813ed3662f1f072f86c3cf13990b1d1afb685045ecb976`.
- [Per-finding author dispositions](review-01-dispositions.md)
  for independent revision-1 H1/M1-M4; technical amendments and retained evidence
  boundaries are explicit. Independent review decides whether findings close.
- Exact 104 current PLANNED units in unit-coverage.csv (C18a 28, C18b 23,
  C18c 19, C18d 34); all exact unit IDs remain unchanged. Ordered first-match
  asset expansion: 155 C18d paths (77 REVIEW, 77 IMPORT, 1 REGENERATE).
- 34 factual AR source files re-hashed against the pinned legacy manifests;
  no upstream code/assets copied and no LibVulpes source inspected.
- C17 revision-2 agreement: two HEAD slots with beacon_finder enabled-only
  equipment summary; combined station sky payload schema 2, celestial_snapshot
  channel 4 and C17 rocket_flight channel 9 with one shared migration/bump.

## Pending decisions and work

D1/D5 policy choices are owner-confirmed. D2 ground-owned registry jobs versus
survey-satellite prerequisite and D3 world-first versus per-player milestones
remain owner choices. D4 automatic pad/player cross-file durability remains
unproven; an interaction-changing alternative is not implicitly authorized.
All exact pattern/recipe fixtures, device/equipment codecs, new checked journal
landing writer, advancement receipt writer, safe-arrival adapter, protocol leaf
formats, per-asset origin/NEW screening and runtime implementations remain work.
Numerical balance proposals are not automatically accepted by D1/D5 confirmation.

## Dependencies / factual sources

Accepted ADR-024/025/034/037/049/050/051/052/054/057/058/059 and classic
ADR-061/062/063/064, their exact identities in source-files.json; current suit,
vent, environment/BodyContext, satellite origin codec/invariant, station access
index and checked relocation, rocket journal and sky/network models.
C15 materials, C16 special chemical recipes/fluid conversion/kernel/graph and
C17 shared finder/sky/travel authority are prerequisites. Root exclusively owns
central registration/protocol/build/status integration.
Pinned approved upstream AR commit:
`c5cd5af62fc07cd4e0d24f06a16033f181c47c04`; approved Temp archive's upstream/ar
tree only, validated against legacy-manifest/java-files.csv and assets.csv.

## Actual commands / evidence

- Read/static PowerShell Get-Content, rg, git status/rev-parse and SHA-256 checks;
  corrected nonexistent guessed paths rather than treating them as evidence.
- `python collect_coverage.py`: exit 0, regenerated exact unit/asset/source
  artifacts, count summary in coverage-summary.json.
- `python verify_handoff.py`: exit 0, exact current PLANNED ID/batch set,
  all coverage fields nonempty, asset/source counts, immutable revision 1 and
  revision/decision/compatibility markers checked; author static check only.
- No Gradle, native test, GameTest, crash fixture, source copy, asset import,
  GPU/client run, commit, tag or push. No root-repository writes by this worker.
- FILE-HASHES.json binds all handoff artifacts except itself. Actual static
  verification details and author diff are in static-checks.json/revision-01-to-02.diff.

Modified files / tests: Temp proposal and response/evidence files only; no runtime
or test-source modifications. Known risk: unproven durability adapters, pending
owner choices, exact migrations/field maps and resource provenance need review
and measured execution before implementation/release.
Current version Required Gates: **not met by this work**. Next v1.8 work is
independent revision-2 re-review and explicit unresolved decisions, then scoped
implementations and actual Required Gate evidence.
