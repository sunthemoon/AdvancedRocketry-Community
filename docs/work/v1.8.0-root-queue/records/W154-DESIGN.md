# W154: sealed disassembly, revision 2 (R93)

**2026-10-24. Design-only revision; one unseal correctness prerequisite remains.**
R93 resolves revision 1's persisted-representation and publication-order questions:
C carries the existing adapter payload, and unsealing follows acknowledged U absence
without a native-image encoder or disk publication fence. Those are no longer open
requirements. Sections 2-5 specify that protocol, including legacy records.

The unconditional instruction to restore into an already exposed native target is
not sufficient for every admitted adapter: native lifecycle callbacks precede the
restore, and restore failure is not atomic. Section 6 gives a no-crash counterexample
and the smallest correction needed. Under TASK W154B's explicit unsound-decision
exception, this note does **not** certify the entire path implementation-ready or
authorize W152/pre-M1-only enablement. This is a narrower remaining issue than the
superseded encoder/save-fence proposal, not a rejection of R93's torn-save boundary.

## 1. Authority, scope and inspected source

- Assignment: [TASK W154](../TASK-W154-CONTAINER-ISOLATION-DESIGN.md), followed by
  `docs/work/v1.8.0-root-queue/TASK-W154B-SEALED-STAGING-REVISION.md` and R93 in
  `QUEUE.md`, read with `git show 295b2985:<path>` without checking out that commit.
  R93 supersedes revision 1's native-image encoder, outgoing projection, publication
  fence, journal-epoch upgrade and downgrade-guard requirements. Revision 1 remains
  available at commit `967e56aaeecfcfc7016e82edd86907760c54afd6`.
- Worker branch `codex/v1.8.0-w154`, worktree `D:/GitHub/arce-v180-w154`;
  runtime baseline `64facdf7574148bf34afb17135294a15b3d9964c`. The source tree under
  `src` is unchanged between that baseline and shared task commit `295b2985`.
- Sole repository write scope: this note. New diagnostic files are confined to
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W154/revision2/`. No production, repository
  tests, assets, ADR, risk register, status, ledger, build or other-worktree edits;
  no delegation, push or merge. This note is the section-13 task record.
- Mandatory project/version guidance, WORKER-RULES, [W148 sections 3.2 and 5](W148-DESIGN.md),
  its independent review prerequisite 1, W152's six diagnostic probes and the
  ADR-054/061/064/065 boundaries informed this revision. W152's historical tests are
  not W154 runtime results. v1.8 development authorization is not acceptance:
  Required Gates G0-G9 remain unapproved; this note changes no version/Gate status.

**U** is an ordinary disassembly journal record; **D** is its frozen rocket;
**C** is a sealed member in terrain; **N** is the restored native block/BE.
**T**, **P** and **M3** retain W148's transfer record, live reservation and checked
write-back meanings. Count elected resources, recipients and drops, not every
redundant recovery snapshot as spendable resources.

Source references below use prefix `J` for
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`.

| Source | Relevant fact |
| --- | --- |
| `J/rocket/model/RocketBlockEntityPayload.java`; `J/rocket/RocketLimits.java` | Opaque adapter data, not native BE NBT; 262,144 bytes/member, 1,048,576 bytes/snapshot. |
| `J/rocket/forge/ServerLevelRocketTransactionWorld.java:143-203` | Calls native `setBlock`, then adapter restore; failed same-state placement is cleared to air. This is not an unseal helper. |
| `J/rocket/forge/RocketBlockEntityAdapters.java:59-68` | Restore success includes a subsequent capture/equality check; exceptions become false. |
| `J/rocket/forge/VanillaContainerRocketAdapter.java:44-62` | Loads approved payload, then calls `setChanged`; does not merge foreign inventory. |
| `J/api/rocket/RocketBlockEntityAdapter.java`; `J/compat/rocket/ExternalRocketBlockEntityAdapter.java` | Trusted callbacks may change only owned target data; false requests checked cleanup. Loaded server target required; 5 ms returned-time check is not preemption or rollback. |
| `J/rocket/persistence/RocketTransactionSavedData.java` | Schema 2, enum-name type decoding, current whole-root acknowledgement; unknown record decoding preserves and blocks the whole root. |
| `J/resourceescrow/EscrowChunkStore.java`; `AnvilRecordReader.java` | Full original-worker chunk capture/readback with existing bounds; no new serializer is needed. |

Pinned Forge 1.20.1-47.4.10 mapped dependency SHA-256:
`b7b28cc105b1ac08f286b4aef94d70dd3e469c86856f6cb2ca92ceeaad3b70ec`.
Fresh `javap` inspection of `LevelChunk.setBlockState` shows native `onPlace`
(unless snapshot capture suppresses it), BE construction/registration and `onLoad`
before it returns. `BlockEntity.setChanged` calls `updateNeighbourForOutputSignal`,
which directly calls each loaded neighbor state's Forge `onNeighborChange` hook.
These are synchronous call sites, not a claim that a vanilla hopper ticks between
two Java statements. A late chunk-load listener cannot provide pre-registration
isolation; C instead loads inert from construction.

## 2. Exact representation and legacy distinction

### C: one stable registry path and an independent root

Reserve `advancedrocketrycommunity:sealed_rocket_block` for both block and BE type
(separate registries), **without a BlockItem**. Its persisted named compound is
`arce_sealed_rocket_member`; native `id`, `x`, `y`, `z` remain BE metadata.

| Schema-1 field | Encoding and validation |
| --- | --- |
| `schema` | Int, exactly 1. |
| `transaction_id`, `snapshot_id`, `rocket_entity_id` | Native UUID int arrays, exactly four ints each. Never recycled to identify another attempt. |
| `content_hash` | Exactly 64 lowercase hexadecimal characters, the existing full snapshot hash. |
| `dimension`, `position` | ResourceLocation string at most 255 characters; int array of length 3. Must match the actual Level/position. |
| `member_index` | Int 0-2047, identifying the member in U's encoded block order; when U exists, validate all fields and payload against that member. |
| `state` | Compound `{id: string, properties: compound}` using snapshot state encoding. ID at most 255; at most 32 properties; names/values at most 64 Java characters each. Validate registry/state resolution before mutation. |
| `block_entity` | Absent for plain members; otherwise `{adapter: string, data: compound}`, identical to U's member envelope/data. Adapter ID at most 255; data has no root `x/y/z/id` and is at most 262,144 native uncompressed bytes. |

No native image, inventory normalization, loot roll, owner duplicate, pending list,
new resource ledger or per-member delivery receipt is stored. Preserve the captured
payload's values exactly; do not feed its compound directly to native `load`.
The registered adapter remains the only decoder. Snapshot hash is a correlation
value, not a signature or a proof of other members after U retirement.

The complete named-empty-root BE encoding, including metadata and the named member
root, is limited to **278,528 bytes = 262,144 + 16,384 framing bytes**, depth 35 and
262,272 nodes. Apply non-mutating bounded measurement before copying/decoding.
Framing is not subtracted from the existing payload allowance: 32 worst-case
modified-UTF properties take 12,448 bytes; the fixed fields, three bounded IDs,
UUIDs, metadata and envelope fit the remaining 3,936 bytes. Validate the full
encoding as well as each field. Depth/node framing is no tighter than the existing
checked-U envelope; whole-chunk limits below still apply independently.

All members get C, up to 2,048 carriers, even though at most 128 original members
have payloads. Do not apply the original-BE count of 128 to the carrier count.
Preflight the expanded full chunks: repeated state/framing bytes are real costs,
not covered merely by the snapshot's 1 MiB limit. Capacity refusal under existing
storage bounds is not permission to truncate or remove adapter support.

Missing, unsupported future, invalid identity and bounded malformed roots stay
sealed and round-trip their original root/absence, including unknown fields. Do
not synthesize an empty valid member. Unavailable adapters or registry IDs likewise
hold the intact root. Oversized/non-round-trippable input follows ADR-061/064's
existing preservation/save-refusal policy, never replacement with air or an empty
root. Root must record this new carrier scope in R-035/ADR before implementation:
if the existing R-021 guard is reused, its whole-chunk refusal, 257th-coordinate
Level saturation, Level-lifetime duration and unbounded native retry/error logging
must be disclosed, not described as a harmless one-BE hold. No new exception-based
save-control scheme is authorized by this worker note.

### U: schema 2 unchanged, new type `SEALED_DISASSEMBLY`

New ordinary live/no-T disassemblies use the exact enum value
`SEALED_DISASSEMBLY`; all existing U keys and phases remain unchanged. The type is
the durable discriminator, not a RAM flag or an inference from seeing a C nearby.
Assembly and governed/escrow protocols are unchanged. Audit every type switch and
`ASSEMBLY`/else dispatch; the new value must not accidentally enter legacy restore.

An existing `DISASSEMBLY` record remains **legacy native terrain** and uses its
existing recovery. Never rewrite its type or substitute C into its native footprint
on upgrade. Pre-RESTORED-with-D still selects D; RESTORED/COMMITTED or missing D uses
legacy block recovery. This preserves old recovery, not retrospective isolation:
old native contents may already have escaped. Mixed legacy/new roots retain both
types and their own exclusion, with no automatic migration claim.

For new U, the same phase election means ENTITY versus **sealed terrain**, not
immediate native restoration. Old jars use `valueOf` and cannot decode the new
enum; their loader retains the whole undecodable U root non-operational. This is
not a downgrade guarantee: an empty U does not protect C, and old jars do not know
its block/BE IDs. Downgrade is restoration of a matching **whole-world backup** under
ADR-065 section 13, not opening this world with an older jar. No downgrade guard or
root-schema bump is requested.

## 3. C0-C5 and unseal: writes, acknowledgement and crash cuts

This replaces W148 D1-D5's terrain representation, not its survivor census, original
consent, exclusion, own-store proof, predecessor handling or M3 constraints. Cover
both retained-T and ordinary no-T requests; never enable only the pre-M1 save.

| Step | Required order | Durable cut and recovery |
| --- | --- | --- |
| C0: prepare | Validate actor, consent, snapshot, every state/adapter payload and expanded-chunk capacity. Prove exact current D and required old-end absence. T must be checked absent before terrain work; no-T uses current standalone D and checked transaction/transfer absence, never a synthetic T. | Before terrain mutation, the proved D remains the survivor. Failed preparation changes no member; existing M3 applies if T removal was already submitted. |
| C1: retain U | Write `SEALED_DISASSEMBLY/LOCKED`, exact snapshot/owner/entity/region; require current W123 acknowledgement containing that exact record digest. Keep D frozen. | Before the acknowledged write, place nothing. If LOCKED reached disk despite a missing callback, restart selects D and checked rollback; no resource exposure. |
| C2: stage | Set the restoration-start latch. Replace only preflight-empty, still-empty owned positions with complete inert C, in bounded steps. Prove every full C terrain chunk via the original worker's own-store completion, force, readback and unsuperseded generation. | Any partial placement/save, including completed C proof with pre-RESTORED U, selects D if present. Remove only exact owned C, prove empty terrain plus D, then checked-remove U before M3. Missing D instead completes sealed C from U, proves it and takes C3; never fills N here. |
| C3: elect C | With all C proofs and final identity checks valid, submit RESTORED and obtain exact current acknowledgement. Submission is one-way: UNKNOWN never permits M3 or regression. | Disk pre-RESTORED selects the preceding election; disk RESTORED/COMMITTED selects C. Validate matching C, fill only genuinely missing/empty members from U, and retain U until C4/C5. Conflicting native/foreign cells hold untouched. |
| C4: retire D | After RESTORED acknowledgement, remove D without drops and prove full entity-slot absence, including W148's identity/census checks. Revalidate sealed-terrain proofs. | U/C remain authoritative during removal, store, force/readback and acknowledgement delays. Repeat C recovery and D absence proof; never resurrect D/T or expose native contents. |
| C5: retire U | Only after complete valid C proof and D absence proof, remove U and perform W123's checked write. Keep live claims/continuation until current exact absence is acknowledged. | If U survives, run C3/C4. If removal reached disk, C alone owns each member. Pending/UNKNOWN/stale acknowledgements never unseal in the live process; restart re-establishes checked absence from the durable root. |
| U1: lazy unseal | On the server thread, recheck the exact C object/root, coordinator ownership, loaded position, operational/current journal and absence predicates below. Replace C by its original native state and restore through its adapter **only through the corrected local materializer in section 6**. No disk publication fence. | Before native chunk saving, durable C unseals again after restart. After native saving, N loads normally and is never refilled from U/C. Mixed C/N chunks resume only C; absent/foreign/native cells are never reconstructed from a stale RAM task. |

Certified rollback is allowed only before RESTORED submission, with surviving D.
Never erase mismatched C, native inventory, new foreign block or unexpected BE.
Retain U and report the conflict instead. A rollback kill repeats the same bounded
checks; checked U absence must precede any M3 write-back. No saved recovery state is
deleted because a timeout expires. A completed disk-C record is self-contained;
no RAM-only continuation is necessary for durability.

W123 remains the single acknowledgement source. Presence requires
`acknowledgedCurrent()`, its current mutation sequence and exact record digest.
Lazy absence requires the transaction missing from both current entries and the
current acknowledged record map, no pending live owner/removal for it, no conflicting
snapshot/entity transaction and no overlapping operation. A narrow read-only
predicate may compose these facts; a map lookup, root load or COMMITTED return alone
is insufficient. WRITE_REQUIRED requests one aggregate checkpoint then re-queries;
UNKNOWN/NOT_COMMITTED/unreadable/non-operational roots hold. Recheck immediately
before each member, not once for an entire later batch.

C constructor/load/onLoad never unseals or reads an uninitialized journal. It may
submit a bounded hint to the coordinator; overflow does not destroy work. Use a
64-entry global hint queue and a fair, resumable loaded-chunk/BE discovery cursor
for rediscovery, without copying/enumerating the entire loaded world in one tick.
Charge each visited candidate/advance to the shared 256 inspections. Resume only
loaded chunks; lazy unseal adds no ticket or permanent chunk load. Unload/reload or
restart loses only hints, not C. No persistent pending list is needed. C is excluded
from movable/propulsion tags and ordinary replacement/arrival, so even orphaned
unloaded members cannot be assembled as cargo while waiting.

**Conservation boundary:** through C5, every item has one elected, inaccessible
owner; original resources cannot escape and foreign resources cannot enter C.
After successful U1, normal native transfers are allowed. Saving a recipient but
not the donor chunk can then reload an old C and replay the withdrawn amount,
exactly as an unsaved ordinary chest withdrawal reloads its old inventory. R93
explicitly places this case within ADR-054's ordinary container/player torn save.
Do not claim zero duplication for that cut or reintroduce a disk fence to eliminate
it. It does not license U/D resurrection, replay into an existing N, no-crash loss,
or destructive cleanup after a failed restore.

## 4. Access closure while sealed

W152 demonstrated direct Container, cached capability and native-hopper fallback
extraction despite locks/invalidation. Those were six detached diagnostic probes,
not an actual hopper tick. The following closes both extraction and insertion by
not creating the native target during U retention.

| Access | Required C behavior |
| --- | --- |
| Container/menu/retained slots | Does not implement Container or MenuProvider; use/menu requests denied. No native menu/target exists to cache before unseal. Stale C references remain permanently inert. |
| Capabilities and automation | Return empty for every capability, every face and null side, including attached providers; invalidation alone is insufficient. Never expose ItemStack/fluid/energy access to opaque payload. Real hopper pull/push, dropper inventory insertion and pipe consumers cannot exchange with C. A dropper's ordinary world-ejection fallback remains a foreign loose item, never deleted by recovery. |
| Comparator, loot and ticks | No analog output, ticker, scheduled/random action, loot unpacking or native placement/neighbor behavior. Comparator sees no inventory. Adjacent machines continue running. |
| Break, movement and destruction | Survival unbreakable; cancel player break events including creative; explosion immune, `PushReaction.BLOCK`, wither/dragon immune tags, not enderman-holdable; full non-replaceable block with no falling, fire, fluid replacement or support dependence. No drops. |
| Client and pick-block | No item form, clone/pick result or BlockEntityTag export; update tag/packet carries no member root, adapter payload or resource data. The server owns all release decisions. |
| Unload/startup | Persist C, not a full native inventory. Loading is sealed even before journal readiness, with absent/future/corrupt U and during recovery kills. No late event is treated as a retroactive access guard. |
| All admitted adapters/plain blocks | Same original admission and provider interface; opaque tank/bay/container payloads preserved. Plain blocks also stage, so block drops cannot escape U rollback. No shipped-only whitelist or empty-container exemption. |
| Commands/editors | Operator bypass under R-035, not normal automation. `/setblock`, `/fill`, NBT edits, partial backups or moving C may destroy/corrupt authority. They are not a supported release/repair procedure. |

## 5. Work and storage bounds

- Existing snapshot ceilings stay 2,048 blocks, 32,768 bounding volume, 128 payload
  members, 512 palette entries, 262,144 bytes/payload and 1,048,576 bytes/snapshot.
  C's framing envelope is the explicitly authorized new representation cost.
- One **shared 256-inspection cursor per server tick** includes staging, matching,
  rollback, recovery, loaded discovery and unsealing. An unseal attempt charges its
  position and callbacks; do not run 256 providers in addition to that budget.
  Select at most one recovery transaction per tick. Provider callbacks retain the
  5 ms returned-time rejection; this is not a hard wall-clock tick guarantee.
- Preserve 256 U entries and its 64 MiB root limit; ordinary 64 entries, four active attempts, at most 129
  proof slots; 100-tick attempts and original 200-tick destructive request age.
  BUSY retries no earlier than the next tick; cheap readiness/save-off polls 20
  ticks; IO/force/bounds/veto/deadline retries 200 ticks. No deadline releases C/U.
- Shared storage: one capture/server tick, 32 pending, 16/type/Level/20 ticks,
  64 Levels, ordinary eight pending and four/Level/batch, 128 watches, 16 MiB capture
  bytes. Each full slot stays within 1 MiB compressed/4 MiB decoded, depth 64,
  262,144 nodes, with the existing admission headroom/external-record refusals.
  C framing and unrelated chunk contents count. No quota increase or partial proof.
- W148 cold-site preparation/ticket rules remain three steps/demand, at least 200
  ticks apart, existing 400-tick ticket lifetime and four starts/tick shared quota.
  Lazy C work itself is loaded-only. Stop/unload releases transient watches/tickets,
  not durable authority. Journal writes aggregate at most one requested checkpoint
  per owner tick. W123's synchronous writer still costs time; measure worst cases.
  No new server-thread `join/get`, sleeps, native flush wait or whole-Level freeze.

## 6. Remaining correction: restore before native exposure

R93's persistent handoff is sound under its declared ordinary-transfer residual.
The distinct problem is **live initialization**, even without a crash:

1. After C5, `setBlock(N)` registers an initially empty native BE and can run its
   `onLoad` before returning to the adapter call. An admitted extension's native
   hook can accept a foreign item through a normal adjacent consumer.
2. The adapter then loads U/C's original four items, overwriting the accepted
   foreign item. Capture equality can pass: it verifies the original snapshot,
   not the foreign item's history. Both journals are absent; no crash is involved.
3. A second case uses a synchronous neighbor callback during `setChanged` to remove
   one restored item. The later recapture differs. Existing cleanup deletes the
   remaining three; blindly putting the original four back into C duplicates the
   escaped one. A provider that mutates partially then returns false/throws/exceeds
   5 ms likewise cannot be treated as an unchanged target.

These histories are permitted by the inspected ordering, not observations of a
particular released third-party provider. The provider restore itself need not
modify unrelated resources: native lifecycle/neighbor callbacks are separate code.
The current API supplies no global no-reentrancy guarantee. Native fixture tests
are required to establish actual coverage; single-thread execution and update flags
alone do not prove it. This is not ADR-054's crash-between-two-saves residual.

**Smallest correction proposed:** amend only U1 to require a local
prepare/publish/abort materializer. Until prepare succeeds, the world-visible cell
and every externally reachable reference must remain inert C; create/restore/check
the host target without publishing an inventory or running resource-bearing native
lifecycle/neighbor callbacks. Failed prepare discards only that unexposed target,
leaving the exact C intact. Publish the fully initialized target once; subsequently
do not recapture/retry from C on a normal native mutation or callback failure. No
native encoder, outgoing-image projection, second journal or disk fence is needed
for this correction's persistence model.

This is a required behavioral boundary, **not an already demonstrated generic Forge
helper**. A server-bound private target while C remains installed is the narrowest
candidate, but `requireServer` passing does not prove compatibility with providers
that inspect the installed host; construction/capability leakage and deferred
callbacks must also be covered. Direct `LevelChunk.setBlockEntity` can avoid its
registration callback, but does not suppress `setChanged` neighbor callbacks or
prove safe replacement. Do not claim that trick solves the problem.

If host-only materialization cannot preserve every currently admitted adapter's
behavior, Root must explicitly resolve that live-target contract; silently making
detached restore mandatory or excluding such providers violates R93. Keep W152
disabled until this local boundary is implemented/probed or a complete alternative
is approved. Keeping U longer, retrying from a RAM copy, clearing to air or accepting
native initialization loss is not the correction. This is the only unseal design
issue handed back; the old encoder/publication-fence demand is withdrawn.

## 7. Player and operator impact for R-035

The player sees uniform sealed construction blocks, not empty usable chests or
rendered original states. Proposed names: **Sealed Rocket Block** / **火箭封存方块**.
Implementation supplies original v1.8 art, provenance, EN/zh translation, no item
form and no copied official/legacy texture. This design task imports no assets.

The affected footprint and D are unavailable through election/retirement; other
blocks in the same chunks still operate and save. After C5, members unseal lazily,
so already completed N works while unloaded/held C remains sealed. The live request
reports full success/fuel disposal only after all its members complete, not at RAM
U removal. Restart need not reconstruct the notification or expired consent.

There is no finite completion-time promise: save-off, unloaded members, failed
proofs, unknown U/C, missing provider and conflict can hold across restarts. An
attempt/request timeout releases transient capacity, never exposes resources or
discards C. An unreadable journal may hold all carriers; ordinary valid carrier
holds are object/footprint-local, distinct from the oversized-root save-guard scope
described in section 2.

Use bounded identities and reason codes, never payload logging. Proposed coordinator
diagnostics: one hold/recovery transition per tracked operation, rate-limited to
one aggregate line/20 ticks, tracking at most 64 active/hint entries; overflow is a
count, not a per-member warning storm. On rediscovery, rate-limit repeated held
members by Level/reason. Existing storage/save-guard diagnostics retain their own
documented behavior; the coordinator rate limit does not bound native save errors.

Repair readiness/storage and load the recorded region for checked retry. Persistent
unknown data/conflicts need a stopped, complete world backup (terrain, entities,
POI, both journals, escrow and player stores), offline inspection/repair, then
conservation checks and two restarts. Never delete U alone, refill an existing N,
break C, or run a command/editor to bypass its seal. Downgrade requires the compatible
whole-world backup. Root owns ADR/R-035 updates and risk acceptance before production.

## 8. Required implementation tests (not executed runtime results)

| Group | Required cases and assertions |
| --- | --- |
| Codec/A0 | Every existing adapter/payload and tag extension, lazy loot/seed, tank/bay, plain member; maximal payload plus framing, 2,048 carriers/128 payloads, full-chunk overflow; future/malformed/missing root round-trip; unknown adapter/registry; exact state/identity; no client/pick payload. Test schema-2 mixed legacy/new U and actual old-reader blocked-root behavior. |
| Election/A0 | Every C0-C5 write/move/ack cut, stale sequence/digest, unrelated U mutation, sticky UNKNOWN, stop/unload/late duplicate callback, rollback and missing-D fallback; two recovery passes and partial recovery kills. No M3 after RESTORED submission, no N before exact U absence, no foreign-cell overwrite. |
| Local materializer/A0+A1 | Constructor/capability attachment/onPlace/onLoad/neighbor reentrancy; incoming and outgoing resources at each callback; false/throw/timeout/partial restore and post-publication mutations. Include an external adapter that checks live host identity. Assert conservation of separately tagged original AND foreign items; equality with original payload alone is insufficient. These tests must resolve section 6 before enablement. |
| Real automation/GameTest | Actually tick adjacent hopper pull AND push, a powered dropper, and a separate capability consumer attempting insertion AND extraction, all faces/null and simulate/real. Chest, barrel, double chest boundary, lazy loot, tank, bay, external provider. Retain references across invalidation/unload/unseal; C always denies access, foreign source or fallback loose item remains, N behaves normally only after successful release. |
| Lifecycle/GameTest | Menus/retained slots, comparator reads, creative/survival break, explosion, piston, wither/dragon, enderman eligibility, fluid/fire/support changes; no payload or block drops. Unrelated same-chunk activity remains normal. Startup before U ready, unreadable/unknown U, lazy unloaded C, discovery overflow and mixed C/N resume. |
| Native S2 cuts | Before/after LOCKED write and ack; partial C placement/store/force/readback; full C proof; RESTORED move/ack/UNKNOWN; D removal and absence proof; U removal move/ack/UNKNOWN; unseal prepare/publish/failure; C versus N chunk save; clean rollback. **Every cut gets two restarts; kill during the first recovery as an additional cut and inspect after the second.** |
| Native conservation | Multi-chunk footprint and independently saved recipient/player; inventory all D/C/N, loose drops and recipients, identify elected U. Through C5 no extraction/insertion. After unseal with both sides saved, no replay on either restart. Consumer-only-save cut must separately demonstrate/document the R93 ordinary torn-save residual against a vanilla baseline, not be mislabeled an exactly-once pass. |
| Budgets/regressions | Shared 256 counter includes discovery/rollback/unseal, fairness and hint overflow, all proof quotas, save-off, saturation/supersession and maximal U checkpoint cost. Run full unit suite, complete GameTest/checker and build for implementation; original-art DataGen determinism and separate V1/V2 remain required. |

Native evidence must pin the committed implementation/JAR, commands, cut points,
durable files and both restart inventories. An abstract model or successful normal
transaction cannot replace actual isolation/failure tests.

## 9. Executed verification and handoff

Fresh evidence is under `D:/GitHub/ARCE-Task-Evidence/root-queue/W154/revision2/`;
old W154/W152 artifacts and indices were not modified. `unseal_model.py` is an
explicit abstract model: it covers C0-C5 elections, two recoveries, old-C/new-N saves,
the accepted torn-save residual and local unseal counterexamples. It assumes the
callback actions being modeled; it is not Minecraft execution or a proof of a
particular mod's behavior. `LevelChunk.javap.txt`, `Level.javap.txt` and
`BlockEntity.javap.txt` are new pinned-bytecode inspections. The diagnostic manifest
is `D:/GitHub/ARCE-Task-Evidence/root-queue/W154/revision2/EVIDENCE-INDEX.json`, SHA-256
`8d8aa2a3aef4f9fa1671332baf89b8f4ae99d2e018321508e928fcd8004beca8`.

| Actual command/check | Result |
| --- | --- |
| `git status --short`, branch/HEAD/worktree and process inspection | Clean assigned branch at `967e56aae` before editing; other workers separate. No checkout of `295b2985`. |
| `git show 295b2985:<TASK-W154B and QUEUE paths>`, source/document reads | R93 and task read; source call order, bounds and legacy decoder inspected. Guessed filenames/Windows wildcard reads failed and were corrected; no missing source was treated as evidence. |
| `git diff --quiet 64facdf75 295b2985 -- src` | Exit 0: inspected runtime source unchanged through R93. |
| `python -B <evidence>/revision2/unseal_model.py` | 11 abstract checks passed, including tests that assert the counterexamples exist. Not 11 runtime passes. |
| Java 17 `javap -p -c ... <LevelChunk, Level, BlockEntity>`, pinned-JAR SHA-256 | Exit 0; lifecycle/neighbor ordering and dependency identity recorded. |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0: 45 passed, 0 pending, 0 warnings, 0 failed; `revision2/validator.log`. |
| `git diff --check`; `git diff --exit-code -- src scripts tests build.gradle gradle.properties` | Exit 0: whitespace clean and runtime/test/build inputs unchanged. Staged scope/stat is checked separately before commit; final committed checks are recorded externally. |

No Gradle/build/runData/GameTest/native server was started: this TASK explicitly
requires design inspection/probes and repository validation. No runtime Gate was
waived. No heavy-JVM lock, world copy, temporary build or export tree was created.
OpenPet was unreachable; work continued without editing its data.

**Handoff:** R93's carrier format, C0-C5, legacy distinction, lazy scheduling and
ordinary-transfer boundary are specified. A fresh independent review should test
section 6's local-materialization prerequisite before W152 resumes. Production,
runtime/native verification and Root's ADR/R-035/Gate decisions remain undone. The
delivery commit is the Git commit updating this note; no self-referential commit
hash or acceptance conclusion is embedded here.
