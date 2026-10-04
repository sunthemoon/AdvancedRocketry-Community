# C18 revision-3 decision-only handoff

Date: 2026-10-03. Delegated C18 author; root repository read-only.
This handoff is not independent review, self-acceptance, runtime delivery or Gate.

## Completed scope / exact identity

[ADR-066 revision 3](ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md)
remains **PROPOSED**, accepted_at/acceptance_basis empty. SHA-256:
`ae4ac5a20983eac80881eeb63bbde83db60665537be48116fee3dc5aa5ce3034`.
The [decision-only diff](revision-02-to-03.diff), SHA-256
`1b0ac53d364c4e4c445a4278852bc8d2e31ed132d3eb70b2aca8f8d052544e86`,
shows only revision/decision-status, selected-versus-unselected branch and
accurate retained admission statements. No numeric, schema, wire, authority,
resource, migration or verification behavior is newly introduced.

The [owner receipt](OWNER-DECISIONS-03.md) records:
D1/D5 previously confirmed; D2 newly selects independent ground-owned jobs
without a survey-satellite prerequisite; D3 newly preserves world-first Moon/
warp milestones with recorded winning participants rather than per-player firsts.
D4 durable pad/player writer proof remains unprovided; no alternate interaction
or retuning is selected. Root's actual owner-receipt source is
`docs/work/v1.8.0-c18-contract/OWNER-DECISIONS.md`, SHA-256
`ed4352ee786435afa6573f3aede578528e9ae78914cbae2147fccae98f4d028f`.

Original revision-2 provenance:
`C:/Users/Administrator/AppData/Local/Temp/arce-v180-c18-contract-r2-72caa8107091470eb65132167af7121c/ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md`,
SHA-256 `7c6da4ab6f3974d537813ed3662f1f072f86c3cf13990b1d1afb685045ecb976`.
Original proposal/report/hash manifest remain unchanged. Only new Temp companion
files were written, with [FILE-HASHES.json](FILE-HASHES.json) binding them.

## Static checks actually run

Read current git status/HEAD, root CURRENT_VERSION, COMPLETION-PLAN, version/
budget docs, C18 TASK and actual OWNER-DECISIONS receipt, relevant implementation
log lines and immutable revision-2 proposal. HEAD remains
`cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6` with retained dirty concurrent work;
active development is v1.8 IMPLEMENTING while the acceptance cursor remains v1.0.
No current root status was changed or represented as release-complete.

Ran own `python -B probe.py`: **exit 0**, result in
[static-checks.json](static-checks.json). This is an author static scope check,
not an independent verdict. It actually verifies:

- root owner receipt and original proposal/report/hash-manifest identities;
- PROPOSED/empty acceptance metadata and accurate D2/D3 selected status;
- complete unchanged sections 1-5 (including D4 and all equipment/numeric
  balance), ground origin codec/invariants, work/processor behavior, detailed
  world-first native protocol, reachability/assets/verification and shared
  dependency/wire IDs; hashes of those byte-identical regions are recorded;
- byte-identical unit/asset/source/summary companions from revision 2; current
  exact 104 C18 PLANNED IDs/batches remain equal (28/23/19/34), not runtime closure.

Coverage/source companions preserve the reviewed historical revision-2 snapshot:
[unit-coverage.csv](unit-coverage.csv), [asset-coverage.csv](asset-coverage.csv),
[source-files.json](source-files.json), [coverage-summary.json](coverage-summary.json).
No new source fact/license approval or asset handling outcome is inferred.
The probe emits the actual diff; final independent review decides its eligibility.
No Gradle, native, GameTest, server/crash, benchmark, GPU or multiplayer work.

## Uncompleted scope / risks / acceptance

Exact per-leaf patterns/recipes/codecs, ADR-025/051 amendments, protected world
adapters, C17 shared schema/protocol/source receipt integration, checked
source/progression/player writers, native migration/save ordering/restart tests,
asset-origin screening and full C18 runtime/Gates remain required. D4 is still a
technical feasibility gate; a changed interaction requires separate owner
confirmation. This decision-only update supplies no new durability proof.

Changed files/tests: this fresh Temp ADR/receipt/report/probe/diff/evidence and
unchanged copied coverage companions only; no root/runtime/test-source/asset
modifications, subagents, OpenPet, commit/tag/push or heavy process.
Current Required Gates: **not met by this work**.
Next v1.8 scope: independent final decision-only diff review, then root alone
may accept the eligible canonical ADR under existing conditional authorization;
author drafting does not grant that acceptance. Runtime work remains separate.
