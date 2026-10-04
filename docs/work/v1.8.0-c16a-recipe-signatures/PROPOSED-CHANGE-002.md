# C16a-01 bounded menu-reason compatibility amendment

Date: 2026-10-03. Status: proposed; independent contract review pending.
Author: root integrator. No public network/menu bytes are changed by this file.

## Existing boundary

Rolling, precision and electrolyzer opening payloads contain only BlockPos.
Their container-data counts are 24, 22 and 7. Existing enum IDs expose a generic
refusal, not the retained bounded recipe subject. A trailer alone cannot refuse
old clients that read just BlockPos. An extra vanilla container-data index is
not a safe negotiation mechanism. The author's menu proposal remains separate
from the source implementation and must be checked against the actual diff.

## Proposed contract

1. Add a required Forge channel `advancedrocketrycommunity:machine_menu` with
   exact protocol string `1`; neither direction accepts missing/vanilla/other
   versions. Register it on both physical sides during common setup. It carries
   no new C2S action or resource payload: its purpose is admission of the
   versioned native menu layout. Existing six gameplay channels and their
   protocol strings stay unchanged. Do not infer admission from a registry
   mismatch, display-name equality or mod-version text.
2. Only after the required-channel compatibility check is in place, append one
   S2C 16-bit reason slot: rolling index 24/count 25; precision index 22/count
   23; electrolyzer index 7/count 8. Keep existing slot indices, enum IDs,
   failure/state fields and server authority unchanged.
3. The bounded reason IDs are 0 generic/none, 1 `recipe_missing`, 2
   `recipe_changed`, 3 `recipe_tags_invalid`, 4 `signature_migration_pending`,
   5 `signature_migration_unproven`, 6 `retained_plan_invalid`. Unknown IDs show
   the generic refusal; arbitrary server subject strings never go on the wire.
   Reasons are display only and do not affect authorization or progression.
4. Opening payloads are exactly BlockPos + unsigned byte schema `2` + unsigned
   byte declared count matching that menu. New constructors reject absent,
   unsupported, mismatched or trailing bytes before constructing a usable
   menu. This is a second local validation boundary, not the old-client
   handshake. Server menu count and opening count must agree.
5. New clients and servers reject peers lacking the required channel or using
   a different protocol. Operators must update both sides for this layout;
   no mixed v1.7/v1.8 menu compatibility is promised. World data compatibility
   and all existing process/resource schemas are unaffected. Any later menu
   incompatibility must advance this channel protocol through a reviewed ADR.

## Evidence and admission

Freeze only after an independent actual-contract review under the owner's
no-open-Critical/High/Medium authorization. Inspect the pinned Forge handshake
implementation to establish that an otherwise empty required SimpleChannel is
advertised and checked; if that mechanism cannot be demonstrated, do not
substitute an unproved admission check or silently extend menu data.

The [pinned primary-source inspection](menu-forge-primary-inspection.json) binds
Forge 47.4.10's sources JAR and the actual Registry/Handshake entry hashes.
The channel list is built from registered instances, not registered messages;
missing peer versions pass `ABSENT` to the predicate. This static observation
supports the proposed mechanism but is not a runtime mixed-client test.
[Forge's SimpleImpl documentation](https://docs.minecraftforge.net/en/1.20.x/networking/simpleimpl/)
also describes exact-version predicates and missing-channel rejection.

Implementation needs bounded opening/ID mapping tests, old/missing/different
channel refusal checks, unchanged existing protocol/slot/enum assertions,
actual registered menu synchronization, server-side refused work conservation,
localization, packaged dedicated behavior, and V1/V2 evidence at the scheduled
acceptance stage. A unit test or this proposal does not prove a real mixed-client
session. Until applicable checks pass, the client-display portion of C16a-01 is
not complete. No version Gate or compatibility waiver is granted here.
