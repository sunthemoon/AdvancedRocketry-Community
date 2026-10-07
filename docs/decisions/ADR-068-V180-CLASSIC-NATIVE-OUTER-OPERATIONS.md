# ADR-068 - Ordinary-player outer operations for classic empty hatches

```yaml
status: PROPOSED
revision: 2
date: 2026-10-07
owner: sunthemoon
deciders: [sunthemoon]
target_version: v1.8.0
revisits: [ADR-064]
scope: C16a-03b-02-EMPTY
```

## Context

The empty-removal admission source at
`7d7b474eb74a73d6cfb3ffa7271bc22f2f94b678` is not a physical placement/removal
bridge. Its successful automatic regression does not establish native birth,
cancellation, removal, emission or restart. The
[physical lifecycle task](../work/v1.8.0-c16a-hatches/EMPTY-LIFECYCLE-TASK-01.md)
still requires complete native and persistence witnesses.

The [fixed native investigation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-native-outer-hook-feasibility-20261007/REPORT-01.md)
examines mapped Forge 1.20.1-47.4.10. Placement approval is inside the native
operation, before later notification/stat callbacks; creative source restoration
is after item use. Break approval precedes tool mutation, removal and drops.
The inspected public callbacks do not form an exception-safe terminal pair.
An inner BlockItem hook ends too early; first-use routing changes native
interaction priority. HEAD/RETURN callbacks miss a thrown exit. Access
Transformers change visibility/final flags, not control flow.

These findings motivate a narrowly version-bound interception exception. They
do not prove that every possible Forge extension has been exhausted or that an
exception automatically rolls back native mutations. No old coremod or native
method body is copied.

## Proposed exception

The maintainer selected the narrowly scoped interception option on 2026-10-07;
the exact reply and its conditions are in the
[owner decision record](../work/v1.8.0-c16a-hatches/OUTER-HOOK-OWNER-DECISION-01.md).
This resolves the architecture question only. Final independent review and the
operation/save/recovery contracts below still precede implementation; the ADR
remains PROPOSED and does not authorize physical activation or accept R-021.

Intercept only these three invocation sites, preserving the original receiver,
arguments and native return/throwable, and delegate exactly once inside ordinary
Java `try/finally`:

| Enclosing method | Target | Exact observed matches |
| --- | --- | --- |
| `ServerGamePacketListenerImpl.handleUseItemOn(ServerboundUseItemOnPacket)` | `ServerPlayerGameMode.useItemOn(ServerPlayer, Level, ItemStack, InteractionHand, BlockHitResult)` | 1 |
| `ServerPlayerGameMode.tick()` | `ServerPlayerGameMode.destroyBlock(BlockPos)` | 1 |
| `ServerPlayerGameMode.destroyAndAck(BlockPos, int, String)` | `ServerPlayerGameMode.destroyBlock(BlockPos)` | 1 |

The `tick` invocation completes delayed ordinary mining; intercepting only
`destroyAndAck` would omit that route. Exact JVM descriptors, bytecode/line facts
and archive/member hashes are retained in the linked investigation and
[authored proposal](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-native-outer-hook-adr-proposal-20261007/PROPOSED-ADR-068-01.md).

Do not overwrite native methods, repost events, recurse into game-mode dispatch,
replace permission checks or substitute a source stack. Unrelated operations
delegate unchanged. This exception would cover only the selected ordinary-player
routes. Direct mod/API calls, automation, alternate game modes, charged power
hatches, bound controller/resource operations and complete machine writers gain
no authority from it. Their data-changing admission remains separately required.

## Operation and persistence requirements

The private bridge must be server-thread-only, nonloading and owned by the
observed Level/chunk lifecycle. For this single-cell leaf, retain at most one
active operation per observed chunk and one selected target. No static mutable
world/player collection, ThreadLocal authority, public ticket factory or reusable
operation token is introduced. Reentrancy, owner replacement and lifecycle
retirement must refuse borrowing another operation's authority.

Normal return, known cancellation and uncertain partial outcome are distinct.
A native success value is not an owner/metadata receipt. A cancellation event or
snapshot restoration call is not successful rollback evidence. Only actual
source/target/owner/metadata witnesses and completed retention permit the
corresponding result. On an exception or changed witness, do not refund, replay,
fabricate empty state, erase protected data or mark a successful birth/removal.

Finally releases the held operation on every exit and preserves the original
result/throwable. Cleanup must not mask a native exception. A separately adopted
protective disposition must be established before release if an outcome remains
uncertain; indefinitely retaining a busy operation is not recovery.

The [save-design investigation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-empty-save-design-20261007/REPORT-01.md)
and [pre-serialization facts](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-empty-hatch-pre-serialization-native-followup-20261007/REPORT-01.md)
require both consumers of the same qualified held operation:

1. Before owner EMIT/inherited serialization, prevent incomplete emission only
   for a genuinely new-empty qualified birth phase. Busy/preparing state alone
   is insufficient. Ordinary retained/pending/rejected owners are unchanged.
2. At chunk-save inspection, authenticate that same still-held operation and
   defer the whole save through the existing common protection boundary.
   Preserve preexisting sticky refusal. Deliberate deferral must not be caught
   and converted into a new permanent denial.
3. Complete coherent retention/expectation, authenticate unchanged cancellation
   or install explicit uncertainty protection before releasing the scope.

Returning early from serialization is not persisted empty data. Vanilla can
catch a BlockEntity serialization exception and omit its output, so throwing
inside an owner is not a demonstrated chunk-save veto. No observer-only pause,
sticky reset, tick expiry or constructor success substitutes for these joins.

## Risk and compatibility decisions still required

Transient deferral is limited to the containing chunk's save attempt during the
qualified synchronous operation. A new persistent refusal may outlive that
operation; its trigger, object/chunk/Level impact, duration, logging bound and
offline recovery must be independently reviewed and recorded in the risk
register before implementation. Existing R-021 refusal/escalation and paired
ERROR behavior are not accepted, closed or extended by this draft.

The exact unknown-outcome recovery, diagnostic byte/count cap, private birth/
cancel/removal/save descriptors and observation provenance remain unfrozen.
No data-loss allowance or protective mechanism is adopted here.

Bind the actual bundled interception dependency, Forge/Minecraft/Java versions,
production mappings/refmap and packaged metadata. Require exactly one match at
each separately selected site with production-enforced minimum/maximum checks.
Debug-only expectations are insufficient. Missing/duplicate targets and
conflicting transforms must not silently permit partial physical activation.
The choice between startup failure and an explicitly disabled physical leaf
remains open. A 47.4.23 compatibility lane needs separate evidence; this is not
automatic support for other versions.

## Verification before activation

- Independent review of the architecture choice, final ADR/risk scope and exact
  private operation/save contract precedes source assignment and registration.
- Inspect actual transformed packaged classes and production mappings; prove
  all three matches and refusal for missing, duplicate or conflicting hooks.
- Test one delegation, return/throwable preservation and finally on every exit;
  pure handler tests do not prove native ordering or installed authority.
- Genuine registered player tests cover survival/creative placement, native
  block/first-use priority, permission/adventure/spectator refusal, DENY/cancel,
  count/tag preservation, both removal callers, tool/drop failures, reentrancy,
  replacement/unload and throwing item/block/event/notification/stat callbacks.
- Test both save consumers, both LOADs, sticky precedence, generated/disk
  Proto/FULL provenance, uncertain outcomes, ordinary unload/final save,
  conservation and restart on an eligible measured host.
- Keep fixed-source build, unit, repeated DataGen, unfiltered GameTests and
  dedicated/client evidence. No existing assertion, budget or Required Gate is
  waived, and no new ledger delivery follows from this ADR alone.

## Decision status and expiry

The maintainer's outer-hook architecture choice is now confirmed, as recorded
above; revision 2 supersedes revision 1's unanswered-choice statement. This file
is still **PROPOSED**, not final implementation authorization or Gate approval.
Acceptance requires a different-agent review of the final text and resolution
of its remaining bounded operation/save/recovery/dependency contracts. The
selected interception option alone does not freeze those contracts, accept or
extend R-021, or authorize physical activation. The original revision/proposal
and primary evidence remain available unchanged in Git/external records.

If accepted, the exception is limited to v1.8.0 and must be re-evaluated before
v1.9.0, supported-version expansion or target changes. An incompatible leaf
needs an explicit disable/recovery policy preserving existing IDs and saved
data, not deletion or a silent fallback. The no-transformation alternative
remains continued event-only research without activating unproved hatches.
