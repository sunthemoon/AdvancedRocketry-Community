# ADR-062 revision 8 draft: production-admission deferrals

Date: 2026-11-23. Status: **PROPOSED**; **applied on 2026-11-24** as ADR-062 revision 8 (Root decision R682, [OWNER-DECISIONS-20261124](OWNER-DECISIONS-20261124.md)), with the status paragraph, the date and two sentences about pending answers changed as the ADR's review history states. Assignment: [W457 / R653](../TASK-W457-ADR062-REVISION-8.md).
This is a branch-only draft, not an amendment to the accepted ADR or the authoritative ledger.
Root holds it until the owner answers P4 to P8. Nothing here enables a switch or approves a Gate.

## Basis and application boundary

- [ADR-062](../../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md), sections 1, 3, 6 and 7,
  and revisions 6 and 7, define the current disposition and asset boundaries.
- [W412](W412.md) and [MP-01](../../../releases/v1.8.0/MATRIX-PROVENANCE-AUDIT.md) identify the five PLANNED rows;
  [QUEUE](../QUEUE.md), W412/R562 and W457/R653, assign the admission-off revision rather than a delivery claim.
- [The owner decision of 2026-11-13](OWNER-DECISIONS-20261113.md) keeps all five switches off temporarily.
  It does not accept this revision or answer the final packet.
- [The final packet draft](OWNER-FINAL-PACKET-DRAFT.md), P4 to P8, reserves each switch decision to the owner.
  R653's task calls for the all-off proposal. At this draft's base commit, the packet table still says undecided
  and [C6-SWITCH-REPORT](C6-SWITCH-REPORT.md), sections 3 to 7, still contains recommendation placeholders.
  Those placeholders are not owner answers or evidence of approval.
- C6-SWITCH-REPORT sections 3.1, 4.1, 5.1, 6.1 and 7.1 supply the live-with-off behavior below. Its sections 3.4,
  4.4 to 7.4, 9, 12 and 13 identify the evidence boundaries. [W451](W451.md) and QUEUE's R641 supply the later
  passive-restart observation and next-operation requirement; the report's pending tables are not that result.

The first fenced block is the exact proposed ADR append, using repository-relative code-span references so it
can be appended without rebasing links. The second block is the exact five-row ledger replacement, including
its existing header. Do not apply either block before the owner decision. A different answer for any switch
requires Root to revise the affected proposal and rows rather than treat this all-off draft as accepted.

## Exact proposed ADR append

````markdown
## Revision 8 — production admission deferred to v1.9.0 (2026-11-23)

**Status: PROPOSED.** Acceptance awaits the owner's separate answers to P4 to P8 in
`docs/work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md`. This text proposes the outcome in which all five
answers keep admission off. The owner said "五个开关暂不开启" in the Claude Code Root session of 2026-11-13,
recorded in `docs/work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261113.md`; that temporary instruction is not
acceptance of revision 8. Root records the final answers, date, channel and scope before marking it ACCEPTED.

### Scope and reason

Only the five units below change from PLANNED to DEFERRED, with plan `v1.9.0`, target `-`, an empty delivery-evidence
cell and ADR-062 in the decision column. Their code, registered IDs, recipes, resources, persistence and protective
reads already ship in v1.8; their production admission does not. Deferral names the admission workflow, not missing
code or removal of the blocks. v1.9 is the reconsideration target, not automatic enablement or a promise of PASS.
W412's MP-01 and Root decisions R562/R653 require this explicit scope decision instead of counting shipped code as
accepted behavior. The five owner packet answers are the acceptance basis, not a mechanical closure result.

| Unit | Former batch | Packet item | Production admission kept off | Player impact |
|---|---|---|---|---|
| `block_variant:loader/1` — rocket satellite bay | C17c | P4 | `BayDeploymentSwitches.PRODUCTION_ADMISSION` | The bay does not admit a new satellite-package deployment on reaching orbit. Existing satellite-launch/terminal paths are not removed. |
| `block_variant:loader/2` — rocket item unloader | C17a | P6 | `EscrowCargoSwitches`, `ITEM_CARGO_UNLOAD` | No new escrow item-unload operation from a docked rocket is admitted through this device. |
| `block_variant:loader/3` — rocket item loader | C17a | P5 | `EscrowCargoSwitches`, `ITEM_CARGO_LOAD` | No new escrow item-load operation into a docked rocket is admitted through this device. |
| `block_variant:loader/4` — rocket fluid unloader | C17a | P8 | `EscrowCargoSwitches`, `FLUID_CARGO_UNLOAD` | No new escrow fluid-unload operation from a docked rocket is admitted through this device. |
| `block_variant:loader/5` — rocket fluid loader | C17a | P7 | `EscrowCargoSwitches`, `FLUID_CARGO_LOAD` | No new escrow fluid-load operation into a docked rocket is admitted through this device. |

### What remains live with admission off

These are the off-state behaviors recorded in `docs/work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md`, the first
subsection of each switch's section. Craftability there is recorded from the C3 historical baseline, not a new C6
runtime verification by this revision.

- **Satellite bay (section 3.1).** `rocket_satellite_bay` remains registered and craftable. Its root protection and
  save refusal (R-038), package-insertion fences and NOT_LOADED handling (R-042) remain active. Recovery of receipts
  already in a world runs on every server (R-043). The register's statement that production worlds create no receipt
  file with deployment off (R-039) does not remove recovery or protection for a world that already has receipts.
- **Item loader (section 4.1).** `rocket_item_loader` remains registered and craftable. Device protection (R-035),
  reconciliation of existing ledger records on every server, and refusal under either NOT_LOADED state remain
  active. The no-ledger statement for production admission off (R-031/R-035) does not exempt existing escrow history
  from migration or protective reads, including section 12's accounting boundary.
- **Item unloader (section 5.1).** `rocket_item_unloader` remains registered and craftable; its device protection,
  existing-ledger reconciliation, NOT_LOADED refusal and migration/protective reads remain as for the item loader.
- **Fluid loader (section 6.1).** `rocket_fluid_loader` remains registered and craftable; the same device protection,
  existing-ledger reconciliation, NOT_LOADED refusal and migration/protective reads remain active.
- **Fluid unloader (section 7.1).** `rocket_fluid_unloader` remains registered and craftable; the same device
  protection, existing-ledger reconciliation, NOT_LOADED refusal and migration/protective reads remain active.

Players can still craft and retain these blocks, but crafting one does not grant its deferred admission workflow.
Keeping a switch off does not disable store loading, migration, shared recovery, data-retention protections or
their refusal consequences for existing worlds. No block, item, inventory, recipe, stable ID or saved record is
deleted or renamed. No new saved form or migration is introduced by this disposition-only revision. Existing
schema migrations and refusal/recovery rules remain mandatory; there is no permission to discard data.

### Conditions for later admission

Enabling any one unit requires a new reviewed ADR-062 revision with the reason, exact unit, player impact and the
owner's explicit per-switch decision. The admission change must have its own commit and hosted regression, and a
new candidate must pin the source commit, JAR hash and configuration. Root identifies and reruns the affected native,
restart, accounting/recovery, real-client and reference-node rows on that candidate; old C3/C6 evidence is not
silently relabelled as evidence for it. Other switches stay off unless separately authorized.

For cargo, R641 additionally requires testing the **next load, unload, disassembly or launch** on the five
lost-ledger late-loss states of X01-05, X01-06, X01-07, X01-09 and X01-11 before production admission can be enabled.
`docs/work/v1.8.0-root-queue/records/W451.md` observed unchanged typed quantities through two passive restarts, but
lost ledger authority/history; it did not attempt those next operations or exercise the `LEDGER_BEHIND` refusal.
That observation is not a conservation verdict for continued use. X01 stays FAIL and the five failed oracles stay
failed. This next-operation test is a later-enablement prerequisite, not new v1.8 work under this revision.

The applicable reference-node and real-client rows remain prerequisites, not waived by deferral. For the bay they
include V1 menu statuses and OWNER_PENDING_FULL, V2 exits/online passengers/S3, D6/barrier and D3/registry-writer
costs, real R-043 waiting times and at least four hours of soak (C6-SWITCH-REPORT section 3.4). For all four cargo
kinds they include V1/V2, the shared cargo work budget, W90's cold-destination/readiness check, reference-node
benchmarks and the four-hour soak (sections 4.4 to 7.4 and 9). Reference means the 4 vCPU / 8 GiB / Debian 12 host
and unchanged budgets of `docs/17-V1PLUS-QUALITY-BUDGETS.md`, not development-host timings. Real-client steps require
the owner's accounts and an authorized way to exercise admission; the release-test gate refuses real clients.
Outstanding accounting, repair, downgrade and residual-risk decisions remain separate and need their own evidence
and signatures. Fuel-loader admission (P9) is not one of these five units and is unchanged.

### Change control and ledger form

This revision supersedes sections 1, 3, 5 and 6 only for the five units' v1.8 admission commitments. In particular,
section 3's statement that deferred content registers no v1.8 ID and holds no world data does **not** apply to these
already-shipped units. Their IDs, resources and save/recovery responsibilities remain. The original 25 post-2.0
deferrals and all rejected/merged/delivered units retain their decisions. Revisions 6 and 7's asset dispositions,
provenance requirements and import-allowlist digest are unchanged; this revision imports or excludes no asset.

The notes of each of these five rows carry the exact marker `production_admission=off@v1.8.0` as a complete
semicolon-delimited field. The validator permits DEFERRED/`v1.9.0` only with that marker and ADR-062 in the decision
column. It still requires the player-impact ADR, target `-`, no v1.8 delivery evidence, and accepted referenced ADRs
at closure. Other DEFERRED rows keep plan `post-2.0`; no general future-version exception is introduced.

If the owner accepts all five proposed deferrals, the ledger has 30 DEFERRED, 214 IMPLEMENTED, 257 REDESIGNED,
94 MERGED, 58 REJECTED and zero PLANNED rows (653 units). The 898 assets remain 89 IMPORTED, 444 REGENERATE and
365 EXCLUDE. A scratch-ledger closure pass only verifies this proposed form. It neither records the owner's answer
nor accepts behavior, risk, a version, a release or any G0-G9 Required Gate. Section 7's revision/review requirement
continues to apply to every later deferral or revival.
````

## Exact proposed ledger rows

Replace only `block_variant:loader/1` through `/5` in
[the ledger](../../v1.8.0-content-ledger.csv); do not append duplicate rows. The empty final field is deliberate:
these are deferred admissions, not delivered-in-v1.8 evidence claims. The bay retains ADR-049 as well as ADR-062.

```csv
unit_id,disposition,plan,target,decision,notes,evidence
block_variant:loader/1,DEFERRED,v1.9.0,-,ADR-049 ADR-062,production_admission=off@v1.8.0; satellite bay deployment deferred; P4; registered craftable block and existing receipt recovery retained; ADR-062 revision 8,
block_variant:loader/2,DEFERRED,v1.9.0,-,ADR-062,production_admission=off@v1.8.0; rocket item unload admission deferred; P6; registered craftable device and existing ledger protections retained; ADR-062 revision 8,
block_variant:loader/3,DEFERRED,v1.9.0,-,ADR-062,production_admission=off@v1.8.0; rocket item load admission deferred; P5; registered craftable device and existing ledger protections retained; ADR-062 revision 8,
block_variant:loader/4,DEFERRED,v1.9.0,-,ADR-062,production_admission=off@v1.8.0; rocket fluid unload admission deferred; P8; registered craftable device and existing ledger protections retained; ADR-062 revision 8,
block_variant:loader/5,DEFERRED,v1.9.0,-,ADR-062,production_admission=off@v1.8.0; rocket fluid load admission deferred; P7; registered craftable device and existing ledger protections retained; ADR-062 revision 8,
```

Root must record the owner's actual answers and acceptance before applying the matching ADR and ledger changes.
The proposed append's status must not be changed to ACCEPTED merely because the existing ADR header is ACCEPTED
or the scratch validator passes. If needed, Root updates the ADR revision metadata and review history in the
owner-authorized application commit. The authoritative ADR, ledger, asset plan and state remain unchanged here.
