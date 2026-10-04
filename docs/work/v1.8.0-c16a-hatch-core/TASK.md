# C16a-03a controller bank domain and codec

Status: verified (pure model/codec only). Milestone: v1.8.0. Owner: delegated author; independent
reviewer: delegated bank-domain reviewer. Root owns central integration.
Worktree: `D:/GitHub/arce-v180-hatches-20261003`.
Branch: `codex/v1.8.0-c16a-hatch-core`.
Base: `cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6`.
Seed: 2,873 verified integrated-03 files, manifest SHA-256
`48dc3d5aa10672519ecbe79adcb86f659e66e367b19a5d0f3b23ed24880fc8e5`,
with accepted ADR-064 revision 3 and reviewed leaf/acceptance overlays.
Seeded unrelated changes are not the author's diff.

Observable outcome: bounded controller-owned bank values, canonical absolute
position/kind identity and a strict schema-1 resource codec that preserves
Item/Fluid metadata. Dependencies: ADR-064 revision 3 and
[C16a-03 acceptance](../v1.8.0-c16a-hatches/ACCEPTANCE.md).

Exclusive write scope: new production/test `machine/classic/resource/**`,
own `docs/work/v1.8.0-c16a-hatch-core/**` and fresh task Temp evidence.
Central registries, configuration, bootstrap, build, protocols, provenance,
generated resources, status and existing modules are read-only.

Non-goals: hatch/controller BlockEntities, capabilities, formation, menus,
rendering, world writes, C16b/c machines, C17/C18, cross-file transaction
frameworks or a change to the existing process-journal balance limit.
Actual shared Java API/encoding/structural bounds are proposals until reviewed.

Verification: canonical coordinate/key boundaries, UUID/schema/revision and
aggregate bank limits, metadata round trips and defensive ownership, invalid,
unknown and oversized roots retained rather than normalized, and input/output
resource conservation. Author runs focused Java-17 unit tests after a root slot
grant. Root and an independent reviewer inspect the exact owned-file diff and
replay relevant checks. No native or version Gate claim follows from this leaf.

- [x] Domain/value model and strict codec with focused tests.
- [x] Frozen actual-diff handoff and independent replay.
- [x] Reviewed model-only API/encoding admitted for later separately reviewed facade implementation.

Command policy: no Java while exclusive root GT/native is running. Later at
most two scoped jobs, workers 2 and a 2-GiB Gradle heap. No commits/tags/push,
unrelated changes, or protected document-bundle access.

Checkpoint: author packet 02 freezes 13 owned files and 35 passing JUnit tests;
its retained-payload regression has one observed failure and remains archived.
Independent static review finds 1 High: original positive ItemStack int counts
can wrap to a plausible byte count during native serialization. Revision 02 is
restricted to pre-serialization quantity validation and targeted regressions.
Independent replay and actual shared API admission are still pending.

Final model checkpoint: independent revision-02 replay passes 39 tests and
repeats the same 84 quantity observations against original/corrected source.
The original High is reproduced and addressed, not erased. A separate API
review preserves one Low about native callback wording; the root
[qualification](API-ACCEPTANCE.md) explicitly overrides that wording. Its
independent final disposition reports no unresolved findings, SHA-256
`566bccdb314270a299635a191ce001df1f96373a6ee436f16ef2ee9a0ccddca9`.
Under the owner's conditional authorization, root admits only these 14 Java
files' pure model/codec boundary, including depth 16 / nodes 8,192. The reviewed
qualification stays frozen at
`94d70229a6c2315aae207b88d411b890001bdeb85336327458c9ffc0a9f758a3`;
its pending-review header is historical, resolved by this checkpoint.
Root integration build passes 1,622 JUnit /312 suites and DataGen writes zero
changes. Physical hatches, capabilities, controllers, writers and native
preservation remain separate unimplemented work; no ledger closure or Gate is
granted by this model-only verification.
