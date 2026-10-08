# Ordinary empty hatch: coupled contract candidate 23

Date: 2026-10-08. Author/integrator: Root. Status: PROPOSED; not frozen or
source-assignment-ready. Scope: one v1.8.0 private vertical slice.
Base: `a01703366c51a0e06788b95a1793b312dfa8a9a4`. Revision: 2 after the
sealed original independent review; original candidate/report remain preserved.

This successor specifies the placement/removal, LOAD, save and publication
boundaries together. It does not amend the immutable 932600d9 proposal or sealed
evidence, accept ADR-068/R-021, install a dependency/hook or activate a writer.
The [owner's three-site choice](OUTER-HOOK-OWNER-DECISION-01.md) remains
conditional. O1/O2/O3 have no recorded reply/adoption. The two Medium findings
in [the assignment review](PRIVATE-OPERATION-REVIEW-01.md) remain open: concrete
bindings below are design candidates, not evidence that the bindings work.

## 1. Inputs and strength of evidence

Actual private source is the base Git object, not an inherited working draft.
Relevant bodies live in
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/classic/adapter/`:
ClassicChunkObservation, ClassicSaveProtection, ClassicLoadedWorld, GuardTicket,
ClassicAccessCoordinator, ClassicHatchBlockEntity, ClassicOwnerState and
ClassicHatchCheckpoint. GuardedChunkSaves/ChunkSaveDenials are in persistence.
ClassicNativePayload is in machine/classic/resource; OwnedNativeTag is in the
adapter. No public API, durable ID, NBT schema or network packet is added here.

[Qualification 21](NATIVE-LOAD-ORDER-VERIFICATION-21.md) measures the scheduling
of ordinary fresh/bulk native BlockEntity callbacks, not provisional hatch
eligibility. [Qualification 22](PLACEMENT-PROVENANCE-VERIFICATION-22.md) measures
the ordinary and direct-first-use paths, not an authentication mechanism. Both
are actual development regressions at their recorded SHAs. Their pass counts
are not tests of this candidate or its future production transformation.

New task23 primary evidence is explicit JDK17 `javap -p -c -s`, not a game run.
Pinned Forge47.4.10 mapped archive SHA-256:
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`.
Eleven named members are inspected, each <=512KiB; exact identities, commands
and exits are retained with [the disposition](COUPLED-CONTRACT-DISPOSITION-23.md).
These native BCIs are navigation aids, not transformed-production frame pins.

| Primary observation | Consequence for this candidate |
| --- | --- |
| LevelChunk primary constructor instantiates CapabilityProvider.AsField with LevelChunk.class (PC37), initializes it at PC153. ChunkAccess/ProtoChunk do not provide this LevelChunk carrier. | The current AttachCapabilitiesEvent<LevelChunk> type is correct. Attachment-time emptiness does not certify the completed chunk. |
| ChunkSerializer.read FULL branch posts ChunkDataEvent.Load with the actual LevelChunk (constructor PC1199), then returns an ImposterProtoChunk (PC1218). | Existing LevelChunk raw capture covers this branch; claiming the event already contains ImposterProtoChunk would be wrong. |
| The same read method posts the Proto instance and original NBT in the Proto branch (constructor PC1408). | Existing captureEvent ignores that instance and has no origin-transfer implementation. |
| ChunkMap.lambda$protoChunkToFullChunk$34 constructs FULL from non-Imposter Proto (PC50). ChunkEvent.Load.isNewChunk is computed by NOT instanceof ImposterProtoChunk (PC138-149). | Both disk Proto promotion and generation can be marked new. isNewChunk=true cannot authorize an empty/generated origin. |
| LevelChunk's Proto constructor gathers its carrier before setBlockEntity (PC75) and copying pendingBlockEntities (PC89). | Constructor/attachment-time empty maps omit subsequently copied owners/raw records. |
| LevelChunk.setBlockState calls EntityBlock.newBlockEntity (PC443), then addAndRegisterBlockEntity (PC458). | An owned block allocation receipt is a candidate seam before onLoad, but the allocation itself occurs after native world mutation; placement admission must precede this seam. |
| Ordinary ChunkMap unload posts Unload before save (PC76), ignores save's boolean, then calls ServerLevel.unload (PC114). That method clears BlockEntities and unregisters tick containers. | Retained data must survive Unload and the save attempt. An attempted save, including false, is not durable success. No universal disposal/flush ordering is established. |
| Local bundled Mixin0.8.5 Redirect and InjectionInfo expose/enforce lower and upper injected-callback counts. | require=1/allow=1 is a candidate injection check, not proof of native/runtime coverage, mapping, conflict behavior or crash policy. |

## 2. Coupled ownership and finite state

One Level-lifecycle-owned, server-thread-only invocation slot holds the exact
native receiver/player/Level, hand, source ItemStack reference and invocation
identity. It is not a static map, ThreadLocal or same-player permission flag.
Before a target is selected it cannot confer LOAD/save/resource permission.
Nested entry permanently taints the outer operation and suppresses nested
selection; bounded nesting uses saturation, not an ever-growing stack. No
operation history is retained after terminal cleanup. Numeric nesting/frame/
envelope limits are proposed verification inputs, not silently adopted budgets.

Level invocation state is separate from selected-cell state. ENTERED binds the
exact receiver/arguments before delegating; a local originalStarted bit moves
once from false to true immediately before the literal native call. Placement
then runs DELEGATING_UNSELECTED through native priority/earlier callbacks, with
no selected cell and no LOAD/save/resource authority. The owned pre-write seam
may select one cell and transition to DELEGATING_SELECTED without invoking the
outer useItemOn again. Removal can prepare its cell before originalStarted and
then enter DELEGATING_SELECTED when the original destroyBlock starts. No
precomputed placement context is used to unify these different orders.

A placement return/throw without selection closes only this Level invocation,
preserving the original native result/throwable and ordinary saves; it never
manufactures a target or an operation outcome receipt. Nested entry before
selection taints the invocation, so later selection cannot grant authority;
native delegation/cleanup still preserves the original behavior. Tainted or
foreign direct owned-hatch routes have no writer permission. After genuine
selection, existing selected-cell uncertainty rules apply. Every selected path
releases its cell and Level slot in the outer finally; no-selection cleanup
releases the slot only. Closure never delegates the outer call a second time.

One observer-owned selected operation may exist per actual LevelChunk, linked
to that exact Level slot. It captures observer identity, immutable origin,
current expectation revision, the selected position/state/owner/storage and
opaque guard witness. Foreign selection, second target, chunk replacement,
capability-provider change, retirement, nested entry or a callback changing an
already witnessed field prevents normal publication. No lookup loads chunks.

| Selected-cell state | Admission/transition | Visibility and save decision |
| --- | --- | --- |
| IDLE | No selected-cell operation; the Level slot may still be DELEGATING_UNSELECTED. Origin/current expectation admitted independently. | Existing ordinary LOAD/EMIT/retained comparison; sticky protection unchanged. |
| PREPARED | Exact ordinary invocation and real pre-write target selected; bounded pre-image acquired; observer revision held. | Selected chunk refuses gameplay/resource acquisition. Saving defers transiently. |
| WRITING_NATIVE | Placement's inherited placeBlock writes inside the already-delegating useItemOn; removal's original destroyBlock starts once after preparation. Owned allocation may attach one exact birth receipt. | No current-expectation change; no gameplay availability. |
| LOADED_PROVISIONAL | Birth has completed two separate fresh LOAD tickets in the same onLoad invocation. | Receipt exists; availability remains false; saves still defer. |
| POSTCHECK | Native return or throwable captured; phase-correct bounded outcomes and identities checked. | Still held. A native success result alone is insufficient. |
| PUBLISHED | One callback-free compare-and-publish tail commits the selected row and matching retained tuple. | Placement publishes availability last; removal leaves owner retired. Hold is released only after coherent observer state. |
| ABORT_NO_CHANGE | Exact pre-image, source/tool/drop outcome and observer state prove no relevant mutation. | Expectation unchanged; no invented load, refund, inventory write or replacement owner. |
| UNCERTAIN | Mutation/outcome/protection cannot be proved. | O1/O2 policy prerequisite; do not turn it into an ordinary false return or pretend transient deferral repairs data. |

Immutable Origin is never rewritten to the outgoing save. CurrentExpectation
is a separate immutable scalar row map plus monotonic revision, initialized
from a validated origin. One terminal edits exactly the selected row; all other
rows/retained owners remain identical. RetainedOwner binds exact owner/storage,
storage lifetime, checkpoint identity and its bounded encoding. No operation
success is inferred from merely refreshing rawMatches.

## 3. Genuine ordinary entry and placement allocation

The only proposed native interceptions are the three already selected sites:
handleUseItemOn's gameMode.useItemOn call, tick's destroyBlock call, and
destroyAndAck's destroyBlock call. Candidate dependency is Forge's existing
Sponge Mixin0.8.5 @Redirect, with literal original receiver/arguments and direct
one-time delegation inside a private try/finally handler. Do not copy native
bodies, recurse through the enclosing entry, import MixinExtras or install a
fourth interception to make a missing origin/provenance proof disappear.

Mixin config required/refmap and per-site require=1/allow=1 need actual packaged
production tests. The local library exists; build.gradle has no installed Mixin
configuration or annotation-processing setup. Startup conflict/coverage and
runtime protection loss still require O2, independent dependency/ADR review
and actual failure tests. Official primary reference:
[Mixin0.8.5 Redirect](https://github.com/SpongePowered/Mixin/blob/0.8.5/src/main/java/org/spongepowered/asm/mixin/injection/Redirect.java),
[obfuscation/refmaps](https://github.com/SpongePowered/Mixin/wiki/Introduction-to-Mixins---Obfuscation-and-Mixins).

Candidate owned final hatch BlockItem inherits ordinary first-use PASS, and
delegates final useOn/placeBlock to super once. Its private seam receives the
actual BlockPlaceContext returned by native updatePlacementContext
`(BlockPlaceContext) -> BlockPlaceContext`, not a second
replaceability/state computation. PREPARED begins immediately before the owned
super.placeBlock mutation. Bind this exact context, target, item and Level slot;
reject foreign/second target or unloaded chunk before native mutation.

An ordered, gap-free frame grammar is proposed in addition to object identity:
owned useOn -> ForgeHooks.onPlaceItemIntoWorld -> ItemStack.useOn -> genuine
ServerPlayerGameMode.useItemOn -> actual merged private redirect handler.
The production class/method descriptor/call-site profile must be measured and
validated for both survival and creative; qualification22's raw BCIs are not
that profile. Direct BlockItem.place/useOn, callback-invoked ItemStack.useOn,
nested native entry and same-context direct-first-use must be tested as distinct
routes. Arbitrary ancestor matches or a shared outer flag never authorize them.
StackFrame metadata does not provide receiver/locals, and this is not a sandbox
against privileged reflection/bytecode redefinition. Profile drift must not
silently enable physical integration or weaken original native behavior.

Birth identity must be registered by the final owned EntityBlock.newBlockEntity
method immediately after constructing the exact ClassicHatchBlockEntity, before
returning it to LevelChunk's native installation. The receipt includes selected
position/state/operation/observer identities. A separate measured grammar must
bind this allocation to LevelChunk.setBlockState -> Level.setBlock -> the owned
placeBlock and the genuine ordinary invocation. The createBlockEntity/deferred
NBT routes cannot borrow it. Merely finding a new owner at the selected position
is insufficient: callbacks can replace it. No allocation receipt grants
permission until actual installed map/Level/state/provider identities agree.
This owned seam does not intercept another native instruction, but its proposed
grammar and allocation/onLoad ordering are not yet runtime-qualified.

## 4. Two actual LOADs, provisional fence and terminal tail

ClassicLoadedWorld.installed presently requires ordinary observation.rawMatches;
the new birth is absent from origin. GuardTicket.acquire calls that predicate
as well. Add a private exact-birth predicate shared by those two callers, bound
to the held receipt, installed owner, actual non-loading FULL chunk, selected
state, current revision and operation phase. Do not globally OR in "busy" or
replace the ordinary predicate. Ordinary disk owners continue the old route.

ClassicHatchBlockEntity.prepareLoaded keeps its reentrancy guard and first
fresh LOAD, withholds availability, constructs/validates the empty checkpoint,
captures the real candidate and closes that ticket. recordCompletedLoad obtains
a different fresh LOAD, validates the same actual storage/checkpoint/encoding
and epochs, and records the provisional completion. Both happen inside one
onLoad call; the join is not fabricated by a public phase setter or a third
callback. recordValidatedLoad needs a private provisional branch: it must not
insert a normal retained row or mutate CurrentExpectation before the terminal.

The provisional branch of recordCompletedLoad must not call storage.installed.
Ordinary completed-load publication remains unchanged. During successful second
LOAD validation, transfer the actual temporary LoadJoinCandidate's captured
identities into a distinct immutable OperationCompletedLoadReceipt owned by the
selected observer operation. Store the same opaque receipt reference on the
owner for explicit invalidation. Capture lifetime after the normal initial
onLoad retirement; the allocation identity does not bypass that retirement.
The new provisional success predicate requires this completed receipt and the
still-current second LOAD, not storage.available or a premature installed call.
It must return joined=true before that ticket closes.

prepareLoaded's existing finally still clears loadJoinCandidate and resets
preparingLoad. That successful temporary cleanup does NOT invalidate the
operation-owned receipt, whose validation no longer calls loadJoinStillCurrent
or requires a live temporary candidate/preparing flag. Failed LOAD completion
does not transfer a receipt. Subsequent retireAccess/clearLoadJoin invalidates
the owner-side operation receipt alongside the ordinary CompletedLoadJoin;
replacement, checkpoint/encoding/lifetime/epoch change or explicit receipt
withdrawal also prevents publication. No new receipt is inferred from merely
observing the old three-field CompletedLoadJoin. Completed receipt binds
operation/observer/current revision, service identity/epoch, owner/storage/
lifetime, checkpoint identity/encoded bytes and successful two-LOAD completion.
Normal temporary clearing and explicit completed-receipt invalidation are
different transitions and must be tested separately.
No old LOAD or removal GuardTicket is refreshed/required after native mutation;
terminal validation consumes an immutable receipt, not a now-dead gameplay ticket.

All Level/provider/map/native getters and encoding/outcome work occur before
the final tail, revalidating after each possible callback. The observer-locked
tail compares those already captured identities and current private local
storage/service fields, then writes the selected CurrentExpectation row and
matching RetainedOwner/completion tuple and availability last. It contains no
capability lookup, registry query, Level lookup, native getter, serialization,
event, user callback, logging, throwing denial carrier or fresh ticket acquire.
Access/service retirement and phase changes must participate in the same local
identity/revision checks. Existing storage.installed calls owner.isRemoved and
is therefore not this primitive. A new private local publication primitive
must compare the already captured owner/storage lifetime, held operation receipt
and local epochs under the same tail, then set the local gameplayAvailable bit
without another native getter. Its source and synchronization remain unverified.
A separate storage mutation before observer commit,
or an unlocked available=true before the row, violates this contract.

## 5. Removal and phase-correct bounded outcome acquisition

Select removal through existing enterEmptyHatchRemoval/EmptyRemovalSelection
before native mutation, under a fresh LIFECYCLE ticket and normal admitted
owner. Bank empty, zero energy, no binding/handoff/pending/rejected checkpoint
and exact installed identity remain mandatory. Selection proves admission only.
Native removal retires owner/access; terminal closes the dead ticket without
requiring it valid again. Proved removal deletes exactly the selected expected
row and retained entry; no replacement owner or dropped player data is created.

Pre/post source and tool snapshots bind the actual player/hand/stack references,
count and lossless bounded metadata. Post-removal validation is through the
operation's phase-specific local witness: installed-before/absent-after and
retained checkpoint/lifetime, not an invalid installed-owner gameplay guard.
Drop receipts must bind the actual owned native loot result/ItemEntity route
and its quantity/metadata/identity before publication; replayed or merely
nearby equal items are not a receipt. Native drops and inventory updates are
not reimplemented or repaired by this contract.

Concrete outcome binding remains an OPEN technical input. ClassicNativePayload
and OwnedNativeTag reject unsupported ForgeCaps envelopes, invoke native
serialization/decoding (possible callbacks), then bound/check results. Their
resource-envelope limits and hatch root's 4096-byte limit are NOT a general
preallocation/timeout bound for arbitrary source/tool capability serializers.
They also use gameplay/LOAD guard validation inappropriate after retirement.
Do not call them unchanged as a supposed operation witness or strip ForgeCaps.
Before assignment, name the actual private owned drop/item seams, phase checks,
numeric shape/work/count caps and capability policy, preserving ordinary tool
and source scope. Bare-hand-only, empty-tag-only or capability-free tools cannot
silently replace that scope. O3 is still necessary; no cap/policy is adopted here.

Success/FAIL/throwable preserve original native return/throwable exactly. A
native FAIL can follow earlier mutation, as measured in qualification22; no-change
needs full relevant outcomes. Native exception after mutation is uncertainty,
not rollback. Root must not claim cross-file crash atomicity: chunk, player
inventory and dropped-entity stores are independent. O3 must explicitly state
accepted conservation scope before implementation. No invented refund/journal.

## 6. Both save consumers and full lifecycle origin

Late ClassicSaveProtection.chunkSave checks sticky GuardedChunkSaves protection
first, then selected held-operation deferral BEFORE ordinary outgoing inspection.
Deferral must be outside inspectOutgoing's RuntimeException-to-sticky-refusal
catch; otherwise a transient hold accidentally becomes permanent. A later
ordinary save requires exact CurrentExpectation and RetainedOwner equality.
Current code compares outgoing rows to origin and must not simply be relaxed.

Early ClassicHatchBlockEntity.saveAdditional checks a genuine selected birth
receipt/held phase before its inherited saveAdditional or fresh EMIT. A withheld
provisional birth must not attempt ordinary EMIT and poison the chunk. Existing
ordinary loaded/pending/retained/power paths remain unchanged. A nonbirth owner
cannot borrow this suppression. Both consumers use the same observer/operation
identity and state; late-only protection does not address early serialization.

Origin coverage remains a separate OPEN prerequisite, not an assumed fix.
Disk FULL raw capture works at its native event. Disk Proto raw capture needs
an immutable bounded pre-decode observation and authentic transfer to that exact
FULL; generated chunks need a completed bounded scalar origin including copied
live and pending owners. No transfer exists in the current private code, and
ChunkEvent.Load does not expose the predecessor Proto. Three ordinary-player
wrappers do not prove a handoff. Matching coordinates/sections, a timeout,
isNewChunk, empty attachment maps or current outgoing NBT cannot replace it.
No fourth native hook or new persistence schema is authorized by this candidate.
The full generated/disk-Proto/FULL scope stays required rather than being
changed to "previously observed FULL only". Resolve this binding before any
physical implementation assignment or integration; unknown origin grants no
permission and is not an excuse to add blanket refusals to live worlds now.

Chunk unload retires access and keeps observer origin/current/retained state
until the actual final save attempts. Level/server quiesce disallows new work;
final encoding uses retained snapshots without live installed-owner predicates.
Capability invalidation/close may release records only after the real final
writer boundary is qualified; no elapsed-time cleanup. Static ordinary-unload
ordering above does not prove shutdown, save-false handling, durable flush,
other listener invalidation or abnormal termination. Those tests remain required.

## 7. Policies, implementation order and acceptance

O1 must decide uncertain-operation refusal scope/duration, exact preservation,
bounded native/own logging and backup/offline recovery. Existing per-Level sticky
denials saturate after 256 distinct coordinates; that fact is not an adopted
new recovery policy. O2 must decide missing/conflicting hook startup and runtime
protection failure without rewriting native returns. O3 must decide eligible
source/capability bounds and cross-store crash scope. Reply selections alone do
not accept ADR-068/R-021; revise risk/ADR impact and obtain independent review.

Before code assignment: resolve exact provenance/allocation and outcome limits,
origin/handoff and final-writer bindings, both LOAD/save/publication paths,
dependency/mapping/multiplicity and O1/O2/O3 as a useful whole. Do not assign
unused helpers separately. Then implement model/validation -> service ->
persistence -> owned Forge adapters -> bounded actual tests, remaining inactive
until transformed/player/S2/recovery and required visual/package evidence pass.

The [verification matrix](COUPLED-VERIFICATION-MATRIX-23.md) separates primary
facts, previous regressions and unexecuted candidate proofs. All v1.8 Required
Gates remain open. Documentation validation cannot freeze this contract or
authorize physical hatch placement/removal, controller resources or charged power.
