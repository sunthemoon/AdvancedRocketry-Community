# Bounded power-carrier format prerequisite

Task: C16a-03b-POWER-FORMAT-01. Date: 2026-10-07. Status: IN_PROGRESS.
Author/integrator: Root. Reviewer: separate session required.
Branch: `codex/v1.8.0-power-carrier-format-20261007`.
Worktree: `D:/GitHub/arce-v180-power-carrier-format-20261007`.
Base: `16b69bfcb1a2aa0547dae782ade6678229c34c60`.

## Scope and accepted dependency

Implement only a package-private `ClassicPowerCarrierFormat` over an already
exclusively owned, stable native Tag, its focused data-only test and this task.
The exact carrier shape is frozen in the accepted owner-mode inventory's
SCHEMA-PROPOSAL.json, SHA256
`63557630c960312d02f8064aca29d63f52cd129f05d8cf27df62a45a7195f277`,
lines 224-249. Authority is the existing GUARD-INVENTORY-ACCEPTANCE-01 and
OWNER-MODES-INVENTORY-ACCEPTANCE-01, not this format helper.

The `arce_power_plug` root is an exact Compound, schema1, bounded to 1,024
named-empty-root bytes/depth4/nodes16 under the existing accounting convention.
Required fields are exact Int schema_version=1,
String phase=ready|pending and Int energy=0..10,000. Ready has only those three
fields. Pending additionally has both operation_id and entity_id, each exact
IntArray of four words. Unknown/missing/future/coerced/malformed data refuse.

The helper returns only detached immutable format data. It does not implement
the frozen inspect(ItemStack,ClassicPlacementTicket), classify an absent Item
as empty, check real Item identity/count/foreign authority metadata, admit
placement or expose usable energy. Pending stays a data phase, not a usable
reservoir. No public API, persisted field, codec writer, root limit or operation
policy changes. No ItemStack, ticket, registry, service, world or callback scope
is fabricated. No source tree or third-party asset is imported.

## Write boundaries and verification

Only the new production file, matching new test and this task may change.
Root-owned main status/registration/build/AGENTS and all other worktrees remain
excluded. New evidence belongs under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/power-carrier-format-check-20261007/`.

Check exact keys/native types, ready/pending IDs, inclusive energy bounds,
malformed shapes/budget refusal, nonmutation and detached values. Bounded
cached direct Java17 compilation/Jupiter is allowed with real existing NBT
dependencies, task-local D outputs and no bootstrap, installation or fake
authority. Record actual results/failures before declaring verification. Full
local Gradle/GameTest/server remains prohibited while C: is below 10 GB.
Independent actual-source review and a committed-source replay precede a
verified data-only integration. Full hosted/native/Gates remain separate.

Real placement source leases, Item-level inspection, charged operation cuts,
creative ingress, removal/drop/rollback, physical blocks and save/restart are
explicit non-goals. The three owner/comparator/observer private checkpoints are
dependencies for later physical work, not passing prerequisites inferred here.
