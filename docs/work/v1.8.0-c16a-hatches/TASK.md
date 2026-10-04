# C16a-03 controller-owned hatches and family boundary

Date: 2026-10-03. Status: in-progress, contract exploration; runtime planned.
Owner: root integrator. Branch `codex/v1.8.0-classic-content`, base `cd63c5ff`.
Scope: accepted ADR-064 sections 1/11, plus the
[owner-confirmed fluid retention](OWNER-DECISIONS.md). No ledger row is delivered.

Outcome: five family hatch IDs expose only loaded formed controller-owned banks
through exact instance/generation bindings. Item/Fluid resources, revisions and
process journals share the controller snapshot; power plugs alone own their FE.
Unformation/unload retains resources. Item-hatch removal drops its assigned
items at the controller once; fluid-hatch removal retains its bank for rebuild.
Controller removal drops remaining items and drains fluid under the agreed rules.
Unsupported/unavailable roots refuse ordinary removal and never load a chunk.

Existing kernel reuse: MultiblockPartBinding, LoadedPartBindingGateway,
MultiblockLifecycleCoordinator, pattern/catalog/dirty queues and process journals.
Family capabilities must enforce FORMED explicitly: the generic existing
ControllerBindingView also permits WAITING_UNLOADED, which is not sufficient
for the classic capability contract. Existing v1.2 ports retain their behavior.

Root exclusive prospective write scope: new machine/classic domain/resources/
hatch adapters/tests and corresponding new providers/GameTests, central
integration files and this task. No signature-worker recipe/rolling/precision/
electrolyzer files, tank/pump scopes or protected document bundle may be changed.
No new generic LibVulpes framework or later-version functionality.

Before C16b/c writers start, independently review/freeze exact controller/bank/
hatch interfaces and codecs, per-type role keys/pattern cells, chance state and
reload/lookup admission. Existing process/journal schemas stay unchanged;
new roots have the ADR's independent versions and bounds. Bank recreation must
not duplicate retained fluid or silently discard an unassigned bank.

The owner clarification is proposed in
[PROPOSED-CHANGE-001](PROPOSED-CHANGE-001.md); the corresponding bank/epoch/native
payload boundaries are in [the reviewed leaf](LEAF-CONTRACT-DRAFT.md). The
amendment and corrected absolute-position/kind identity passed independent
contract review and [acceptance](ACCEPTANCE.md). This does not freeze Java APIs
or authorize dependent writers. Bank-domain revision 2 independently passes39
tests after the native-count High regression; the separate callback qualification
is reviewed. Only the [model/codec API](../v1.8.0-c16a-hatch-core/API-ACCEPTANCE.md)
is admitted, not these physical adapters.

Verification: bank/aggregate schema limits; loaded-instance/generation/conflict/
transaction checks; formation and all motor tiers; unload/removal/rebuild and
same-snapshot conservation; cross-chunk native restart; mandatory regression,
provenance and independent actual-diff review. Full version Gates remain open.

- [x] Exact public/internal leaf interface and persistence contract reviewed
  ([technical adoption02](TECHNICAL-CONTRACT-02.md)); implementation API/native
  admission and Q1 charged-power removal remain separate and open.
- [x] Bounded domain/resource models and tests (model/codec scope only).
- [ ] Forge hatches and controller adapter integration.
- [ ] Actual formation/capability/removal and cross-chunk restart proof.

Q1 is subsequently resolved by the owner:[retained charge in the plug Item](OWNER-POWER-REMOVAL-01.md).
Implementation/native admission is still separate; earlier proposed/pending
statements below and in frozen packets remain dated history.
- [ ] Independent implementation review; eligible C16b/c delegation.

### C16a-03b adapter preparation — 2026-10-04

Status: planned; independent technical API/root freeze still required. The
[read-only preparation](preparation-03b/MEMO.md) captures84 unchanged named
inputs and verifies all14 bank files against their frozen revision02 identities.
It proposes five physical kinds and one guarded loaded-owner boundary, with
a test-only host rather than a fake persistent gameplay controller. Existing
v1.2 ports remain unchanged. A separately admitted lathe is the proposed first
real consumer; it does not alone prove Fluid reattachment.

Remaining freeze groups: guarded operation authority; exact controller/hatch
and active-plan fields; formation publication/rollback/stale-generation repair;
larger-offer slicing, FE and Item-drop failure handling; callback/lifecycle
admission; actual shared guarded-save writer. Preserve the agreed assigned-bank
removal rule and inactive-bank retention; no historical owner field or broader
inactive-removal policy is silently added. No runtime/API/ledger/Gate admission
follows from this preparation or its static arithmetic checks.

The [adapter/lathe proposal01](ADAPTER-CONTRACT-01.md) now freezes a complete
proposed Java/field/cut inventory over86 unchanged inputs for independent
technical review. It is not accepted: charged-plug removal, array axes and
Fluid-bearing lathe compatibility remain explicit choices. A separate bounded
survival-dependency inspection is in progress. No implementation writer begins
from these proposed interfaces and no Bank qualification is broadened.
