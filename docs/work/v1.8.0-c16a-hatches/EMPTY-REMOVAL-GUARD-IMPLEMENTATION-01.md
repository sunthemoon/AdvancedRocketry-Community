# Empty non-power hatch removal admission implementation

Date: 2026-10-07. Status: in-progress; source implementation not yet delivered.
Task: C16a-03b-02-EMPTY admission dependency. Integrator: Root.

## Limited technical adoption

Owner authority is the recorded statement: "授权无未解决 Critical/High/Medium
的审核定稿继续实现；重大语义调整仍另行确认". Its prior repository association is
[LOAD-JOIN-IMPLEMENTATION-01](LOAD-JOIN-IMPLEMENTATION-01.md). This disposition
applies only to the internal empty-removal admission dependency below, not to
the broader physical lifecycle proposal, save-risk acceptance or release Gates.

Root adopts [EMPTY-REMOVAL-GUARD-AMENDMENT-01](EMPTY-REMOVAL-GUARD-AMENDMENT-01.md)
for implementation, exact reviewed SHA-256
`01edb2acb70c0aa2e444aa48aa9cf629b4a8717d6422aec011b2fbc8588b1384`.
The [different-agent final review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-empty-removal-guard-amendment-independent-review-20261007/REVIEW-01.md),
SHA-256 `dae568d42ac7e569603c34d9f5ca5bc1f4624b190077965fe3bb7bd862baa6ed`,
reports no unresolved Critical/High/Medium/Low in this isolated specification.
The original draft's Low acquisition-versus-joined validation ambiguity is
resolved by the actual reviewed revision. The proposal file remains byte-exact;
this separate record supplies its limited implementation disposition.

The prior broader review's F2 is addressed at specification level only. It must
still be implemented and verified. F1's owner pre-serialization/save join and
placement F3 remain open, as do genuine birth/removal outcome observations.
No native before-tool/unbind operation, drop/resource writer, outgoing metadata
retirement, registration or installed lifecycle is accepted by this disposition.

## Disjoint source assignment

Base is `1e7e4952ef380c586343bbe48bbc9113b74156e0`, whose source equals the
audited `7ab1b0879527f4d8d3e88f09f9360015175b911a` cohort. The worker uses
`D:/GitHub/arce-v180-empty-removal-guard-20261007`, independent branch
`codex/v1.8.0-empty-removal-guard-20261007`.

Write scope: the existing adapter files `ClassicHatchBlockEntity`, `GuardTicket`,
`ClassicAccessCoordinator`, `ClassicChunkObservation`, `ClassicSaveProtection`;
focused same-package tests and the worker's own
`EMPTY-REMOVAL-GUARD-SOURCE-01.md` task record. No other source file is assigned.
Existing OwnerState/LoadedWorld/FrameCodec may be read, not edited. Root owns
this disposition, the reviewed amendment, canonical status/ledger and eventual
integration. AGENTS.md, registry/event activation, builds and unrelated work
are excluded. Only Root stages, commits and normally pushes.

Implement the private completed LOAD token, acquisition-only phase, exact
observer selection and fully joined fresh one-hatch LIFECYCLE entry. Preserve
all other acquisition profiles and LOAD-only retained matching. No guard can
be externally usable before attachment; no callbacks run under the observer
lock; no captured identity is refreshed to pass. Scope exit and every failure
release the guard. Native success remains separate from admission.

## Verification boundaries

Review the literal implementation diff and maintain the existing declaration
test's assertions while accommodating the hatch-only private token replacement.
Add focused checks for the new private entry/profile and the actual local
subjects the existing harness can instantiate. Do not create a fake installed
owner/service/guard or claim reflection/storage checks prove native admission.
Real positive/negative installed native cases remain required before physical
activation, including callback retirement, stale/foreign joins, preparing LOAD,
pending/rejected/bound/power owners, occupied guards, selected-entry/Capture ABA,
attach-once failures and all guard-release paths.

C: remains below 10 GB; no local full Gradle, bootstrap, native/server or
GameTest starts. Small read-only/static checks use task-local D evidence.
Actual compilation/build/DataGen/GameTest uses an eligible measured host after
reviewed source publication, with exact commit association. Native restart and
physical qualification are separate. No test, timeout, budget or oracle is
weakened, and no delivery or Required Gate is marked by this source assignment.
