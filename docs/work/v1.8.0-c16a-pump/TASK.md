# C16a-06 pump

Status: development-verified (module/regression/clean-stop native scope). Version: v1.8.0. Owner: delegated pump implementer;
root owns central integration and final independent review assignment.
Branch: `codex/v1.8.0-c16a-pump`; worktree:
`D:/GitHub/arce-v180-pump-20261003`; base `cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6`.
Verified motor final-02 source overlay: 2,827 inputs, manifest SHA-256
`d5f73cf1c23f1beeb6c7d63628129a8c50f5dfa75a995d0829551f54b70b0367`.
Seeded unrelated changes are not the worker's implementation diff.

Outcome: accepted ADR-064 sections 7/11, `pump` and schema-1 `arce_pump`;
owner-bound server authority, bounded loaded-only search and exact water/lava
source drain, energy/fluid conservation and full ADR-054 section-5 protection.
Dependencies: verified C16a-04 fluids and existing protection APIs. No tank,
hatch, machine-family, C17 or later-version implementation. No persisted search
frontier or arbitrary chunk load.

Exclusive write scope: new `machine/pump/**` production/tests, `PumpGameTests.java`,
`V180Pump*.java` providers, own task documents and own fresh Temp evidence.
Central registries/config/bootstrap/DataGen/language/provenance/build/status,
generated output, existing Java and the root checkout are read-only; propose
integration patches in the task directory. Record NEW art before generation.
No commits/tags/push or protected document-bundle access.

Validation: pure search/budget/cooldown/codec boundaries; actual loaded source
drain, every protection layer and event, absent owner, rejected/unloaded/full
destination and resource conservation; restart reconstruction, mandatory root
checks and independent actual-diff review. Root integrates before Forge/native
tests requiring registration. At most two parallel Java-17 compile/scoped unit
jobs, workers 2 and 2-GiB Gradle heap. Root coordinates GT/native; performance
benchmarks remain exclusive and no GPU/Required Gate is inferred.

- [x] Domain/codec and automated tests (reviewed module scope).
- [x] Forge adapters, original providers and integration handoff.
- [x] Root integration, actual GameTests and native persistence.
- [x] Independent module/native review and portable evidence (scoped development delivery, not version acceptance).

2026-10-03 checkpoint: isolated author source is frozen at 20 scoped JUnit
tests in five suites (0 failures/errors/skips); 17 registered GameTests are
authored, not executed. Root's read-only review identified an external-callback
live-load mutation route; the author added busy-load refusal and a compiled
world regression, preserving earlier command results. Independent review uses
a separate registered Temp candidate; root/native/visual integration is pending.

Revision 2 checkpoint: 22 author JUnit tests pass; 18 authored registered
GameTests compile but were not executed by the author. Independent revision-1
review reported three Medium findings (input-callback reentrancy, shared ID
bound, oversized-fixture cleanup). The actual failing probe logs are retained;
the author's corrections still require independent replay before closure.

Final independent revision-2 checkpoint: 22 JUnit, runData, the unchanged ID
probe and all 433 required GameTests passed with no open findings; see
[review](reviews/REVIEW-02.md). Root integrated the exact 23 author files and
central proposals. Packaged native restart and actual-client checks are still
unverified; ledger units and version Gates remain open.

Packaged checkpoint: reviewed hookr3 adds seven unit checks; reviewed harness
has25 passing Python tests. Actual Pump02 completes four phases and two clean
same-world restarts on58a5ab97…, preserving all seven typed roots, reconstructing
partial search and consuming100FE for1,000mB lava. An independent stopped-region
[audit](../v1.8.0-c16a-integration/reviews/NATIVE-AUDIT-02.md) has no new findings;
it is not a replay or crash proof. Root complete units pass1,622, while fullGT03
fails12 Signature cases. Thus the integrated-regression checkbox remains open;
real-client visuals/performance and ledger/version acceptance are not claimed.

Candidate11 checkpoint:1,638 JUnit /313 suites and all459 required GT pass;
DataGen written0. Fresh Pump04 passes four native phases/two clean restarts on
ea311ed0… . Independent stopped-byte audit finds no evidence inconsistency and
all2,940 source inputs remain unchanged. The pump ledger unit is now REDESIGNED
with [dedicated development evidence](VERIFICATION.md); only this scope is
verified. Earlier failed runs remain immutable; real-client/performance/Gates
and unrelated Signature/physical-hatch work remain open.
