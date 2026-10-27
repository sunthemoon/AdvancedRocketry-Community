# W174: the gas charge pad's feasibility design (ADR-066 section 4, D4)

**Proposed design, 2026-10-28. Not implementation, risk acceptance, owner decision or Gate evidence.** Implementer:
Claude (headless), a new session on branch `codex/v1.8.0-w174`, created from the shared head `171c1d71d`. The task is
`docs/work/v1.8.0-root-queue/TASK-W174-GAS-CHARGE-PAD-DESIGN.md`, which was read from the shared checkout, where it is
not yet committed. Root owns ADR-066, the risk register, the ledger and the owner question. A fresh read-only review of
this note follows. Section 12 is the record of this session.

**Inputs.**

| Input | Where | SHA-256 |
|---|---|---|
| W172 inventory, section 3, item B1 | `D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md` | `134fb14c0e588758639cad574f63fa2af18e615e8ac5622832b1bccf043e64ca` |
| Forge 47.4.10 mapped jar, read as bytecode | the Gradle cache file that W80 and W159 used | `95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e` |
| ADR-066 sections 2 and 4, and section 9 item 4 (D4) | [ADR-066](../../../decisions/ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md) | not hashed |

In code citations, `J` means `src/main/java/io/github/sunthemoon/advancedrocketrycommunity` at `171c1d71d`.

**Names used below.**

- *T* is one charge transaction, *a* its amount and *g* its gas. An amount is 1 to 100 units, and one unit is one mB,
  the C16 canister mapping (section 2.4).
- The *bank* is the pad's single gas store. *O(T)* is the pad's outbox entry for T. It lives in the pad's
  block-entity root, in the pad's chunk.
- *R(T)* is the player's receipt for T. It lives in the player file (section 3.1).
- D, PS1, C, PF, A, PS2 and P are the protocol stages of section 4.2. K0 to K8 are the crash cuts of section 5.
  F1 to F8 are the bytecode facts of section 2.1.
- A *chunk proof* is a completed ordinary terrain proof of the escrow barrier (section 2.2). A *player-file proof*
  is a matching streaming readback of the installed `playerdata/<uuid>.dat` (section 2.3).
- The *integrated owner* is the player that an integrated (single-player or LAN) server reports through
  `MinecraftServer.isSingleplayerOwner`.

## 1. Answer

1. **The feasibility gate holds for players whose authoritative store is the installed `playerdata/<uuid>.dat`.**
   That is every player on a dedicated server and every guest in a LAN world. Each protocol step maps onto a merged
   mechanism (section 3). The design needs no new lifecycle hook, no new Access Transformer line and no Mixin. It does
   use the escrow chunk store, which relies on Access Transformer lines that W11a2 already merged.
2. **It needs two changes to the proposed protocol text of ADR-066 section 4.**
   - The receipt lives in the player's own persisted data, `ForgeData.PlayerPersisted.arce_gas_charge_receipts`, not
     on the worn chest.
   - Equipment is therefore never pending. The rule against carrying, replacing or extracting pending equipment is
     replaced by a stated conservation claim: an owed charge behaves like a canister the player owns (section 7).

   Section 3.1 shows why a receipt on the chest can be neither observed nor held with merged mechanisms, unless the
   death interaction changes.
3. **The gate does not hold for the integrated owner.** Vanilla loads that player from the `Player` tag of
   `level.dat`, not from `playerdata/<uuid>.dat` (facts F3 and F4). No merged mechanism observes a durable `level.dat`
   write. The pad must refuse automatic charging for that player, so single-player worlds need the alternative
   interaction of section 9, which the owner must choose.
4. **D4 therefore cannot close through this note alone.** The TASK's stop condition (a new hook, Access Transformer
   or Mixin) is not triggered. Point 3 still changes the interaction for single-player worlds, so section 9 is
   written for the owner, with a recommendation. Root takes the two protocol changes of point 2 into an ADR-066 note.

## 2. Facts this design relies on

### 2.1 Pinned bytecode (Forge 47.4.10)

These facts were read in this session with `javap -c -p` from JDK 17.0.7 on the jar named above. The dumps are in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W174/bytecode/`. Numbers are bytecode offsets.

| Fact | Content | Where |
|---|---|---|
| F1 | `PlayerDataStorage.save` serializes the player (8), writes a temporary file (33), calls `Util.safeReplaceFile` (85) and then fires `PlayerEvent.SaveToFile` (97). Every exception in that range is caught and logged at WARN. | `save`; W80 and W159 recorded the same order |
| F2 | `PlayerDataStorage.load` reads only `<uuid>.dat` and never falls back to `.dat_old`. A missing or unreadable file gives a fresh player. | `load`, 2 to 41 |
| F3 | `PlayerList.load` loads the integrated owner from `WorldData.getLoadedPlayerTag()`, the `Player` tag of `level.dat`, whenever that tag exists. It loads every other player through `PlayerDataStorage.load`. | `load`, 7 to 72 |
| F4 | `IntegratedPlayerList.save` caches the owner's tag for the next `level.dat` write and then writes `playerdata` as usual. `DedicatedServer.isSingleplayerOwner` always returns false. | `save`, 8 to 31; `isSingleplayerOwner`, 0 |
| F5 | `ServerPlayer.restoreFrom` copies `ForgeData.PlayerPersisted` from the old body to the new one on every clone, that is, after a death and on the End exit. The copy is outside the keep-everything branch. | `restoreFrom`, 296 to 328 |
| F6 | `PlayerList.saveAll()` is public. `PlayerList.save(ServerPlayer)` and `MinecraftServer.playerDataStorage` are protected, so saving one player alone would need new access. | declarations |
| F7 | The armor slot of the inventory menu refuses a pickup only when the player is not creative and the stack reports Curse of Binding through `ItemStack.getEnchantmentLevel`. | `InventoryMenu$1.mayPickup`, 13 to 32; `EnchantmentHelper.hasBindingCurse` |
| F8 | `Inventory.save` stores armor at `Slot` 100 plus its index, so the worn chest is the `Inventory` element with `Slot` 102. | `Inventory.save`, 114 to 122 |

Vanilla runs every player save on the server thread: the autosave, a logout and `saveAll`. The design depends on this
only for the statement that no save runs inside one owner-thread step.

### 2.2 Merged chunk-side mechanisms

- `EscrowBarrier.beginOrdinaryTerrain` (`J/resourceescrow/EscrowBarrier.java:135`), added for W152's sealed terrain,
  captures one terrain slot through `EscrowChunkStore.captureTerrainSlot`. It stores the slot on the vanilla region
  worker, forces that worker, reads the slot back and confirms on the owner thread. A `Predicate<CompoundTag>`
  validator checks the captured root and the readback. The barrier and the store are server-lifetime services
  (`EscrowRuntime.of(server).barrier()` and `store(level)`), not test-gated.
- Its quota is the ordinary sub-quota of `EscrowBarrierBudget`: `ORDINARY_PENDING = 8` server-wide and
  `ORDINARY_PER_LEVEL = 4` per 20-tick window (`CADENCE`). W98's retirements, W158's release proofs and W152's sealed
  disassembly share it. A sealed attempt reserves all four slots of its Level until it ends (ADR-065, N3-B2, "Proof
  admission").
- `GuardedChunkSaves` is the existing whole-chunk save refusal for an outgoing device root that fails
  `BoundedNbt.fits` (ADR-066 section 2 and its W03 and W14 notes; R-026 and R-027).

### 2.3 Merged player-file mechanisms (W80 and W157 to W160)

- **Read trigger only.** Rule R1 of [W80-DESIGN](W80-DESIGN.md) section 4: `RocketRejoinProtection.saved` turns
  `PlayerEvent.SaveToFile` into a read request, and the event proves nothing by itself.
- **Streaming readback** (`J/rocket/rejoin/RocketRejoinPlayerFile.java`). It opens the installed `<uuid>.dat` through
  default NIO, which on Windows shares read, write and delete. It refuses links and files that are not regular, reads
  only the keys it needs, and skips every other subtree by its declared length with an explicit depth count. It
  compares size and modification time before and after. Its ceilings are 64 MiB on disk, 512 MiB inflated, depth 512
  and 10 seconds.
- **One reader** (`RocketRejoinReader`): one daemon thread runs one scan at a time, and `close()` interrupts and
  joins it.
- **Prompt saves** (`RocketRejoinPlacement.tick` and `RocketRejoinSaveClock`): `saveAll()` runs at most once per 100
  ticks, only while a proof is awaited, and never while a scan is open.
- **Windows overlap** (W80-DESIGN section 4, "Windows overlap"; `RocketRejoinOverlapGameTests` of [W159](W159.md)).
  A scan starts only after a save event, the prompt save never runs while a scan is open, and the time limit closes
  every handle. The GameTest holds a scan open across two consecutive vanilla saves of one player and passes.

### 2.4 Merged equipment and canister code

- A canister holds exactly 1,000 mB (`GasCanisterHandler.CAPACITY`, ADR-064 section 5) and refills 1,000 suit units
  (`AtmosphereLimits.OXYGEN_UNITS_PER_CANISTER`). One unit is therefore one mB, and the pad adds no conversion.
- The built-in chest keeps its active oxygen, 0 to 2,000 units, under `arce_space_suit_oxygen`, and its modules under
  `arce_suit_equipment`. Installed pressure tanks carry `arce_pressure_tank` (`TankData`: gas EMPTY, OXYGEN or
  HYDROGEN, the stored amount, and a snapshotted maximum of at most 32,000).
- `ArmorEquipment.setReserve` and `setHydrogen` edit a detached chest. `SuitEquipmentService` publishes a detached tag
  only after checking that the live chest is still the stack it read.
- The enchanted native chest has only `arce_enchanted_suit_oxygen`, 0 to 2,000 units. External ADR-025 providers keep
  their own payload.
- No merged route fills a chest's pressure tank. The existing oxygen canister refill
  (`SuitEquipmentService.fillOneCanister`) writes the active buffer only. ADR-066 section 5.1 says that empty tanks
  acquire gas from charge only, so the pad is today the only planned source of tank gas, including jetpack hydrogen.

## 3. Mapping the protocol steps

| Step named by ADR-066 | Mechanism in this design | Merged basis |
|---|---|---|
| The pad's debit and outbox at the source | D: the bank loses a and O(T) is added, in one owner-thread step | device-root rules of ADR-066 section 2 |
| The durable chunk observation | PS1: an ordinary terrain proof of the pad's chunk whose validator finds O(T) | `beginOrdinaryTerrain` (2.2) |
| The worn chest's credit and the receipt in the player file | C: the chest's credit and R(T) in `ForgeData.PlayerPersisted`, in one owner-thread step | the detached commit of `SuitEquipmentService`; F1 and F5 |
| The player-file observation | PF: a prompt `saveAll`, `SaveToFile` as a read trigger, and a streaming readback that finds R(T) | 2.3 |
| The source acknowledgement | A and PS2: O(T) is removed, and a second terrain proof shows it gone; then P prunes R(T) | 2.2 |

### 3.1 Why the receipt cannot stay on the chest

The literal mapping puts R(T) in the chest's `arce_suit_equipment` root, and PF would look for it at the `Inventory`
element with `Slot` 102 (F8). This fails for two reasons.

- **Absence proves nothing.** Once the chest leaves the player, the next player file lacks R(T) whether or not the
  credit happened. The chest can be taken off, dropped, put in a container or dropped at death. After a crash, an
  outbox whose receipt is missing would credit again while the credited chest sits in a container. That is a real
  double credit, not a torn save.
- **Holding the chest changes the interaction.** The only merged block on taking armor off is the binding-curse check
  (F7). An item override could report a pretend curse while a receipt is pending, but that stops neither creative
  players, `/item`, `/clear` and other mods, nor death drops. Keeping a pending chest through a death would need
  `LivingDropsEvent` filtering and a carry across `PlayerEvent.Clone`, which makes the chest soulbound, an interaction
  the owner has not approved. The enchanted native chest is not this mod's item, so it could not report the curse.

`ForgeData.PlayerPersisted` avoids both problems:

- it is written in the same file, by the same `PlayerDataStorage.save` call, as the worn chest (F1);
- it never moves with an item;
- Forge copies it on every clone (F5), so R(T) survives a death and the End exit;
- W37 already keeps `arce_planet_respawn_backup` there (ADR-066, W37 note).

The ADR's bound of eight receipts per equipment piece becomes eight receipts per player.

## 4. The protocol

### 4.1 Records

**The pad root** `arce_gas_charge_pad`, schema 1, stays within ADR-066 section 2's device bounds: 64 KiB, depth 16,
1,024 nodes and eight item slots. It holds:

- an identity: a UUID made at the first placement, with the dimension and block position where it was made;
- the bank: a gas (`EMPTY`, `OXYGEN` or `HYDROGEN`) and an amount of 0 to 16,000 mB, where EMPTY holds 0; the gas
  changes only while the bank and the outbox section are empty;
- two canister slots, one for input and one for output, each holding at most one item;
- `next_revision`, a nonnegative long that only increases;
- at most eight outboxes. Each has a transaction UUID, a revision, an owner UUID, a gas and an amount of 1 to 100.
  Every field is immutable once written.

RAM-only state records, for each outbox, whether a completed PS1 covered it. It also records, for each pad, whether a
proof is in flight and which outboxes were acknowledged but not yet covered by PS2.

**The receipt root** `ForgeData.PlayerPersisted.arce_gas_charge_receipts`, schema 1, holds at most eight receipts.
Each has a transaction UUID, a revision, the pad UUID, the dimension (canonical ASCII, at most 128 bytes), the block
position, a gas and an amount. The proposed bounds are 4 KiB encoded, depth 4 and 96 nodes, with exact keys and types
and no duplicate transaction. RAM-only state records whether a player-file proof covered each receipt. The codec never
reads or changes other keys under `PlayerPersisted`.

A transaction's identity is the pair of UUID and revision, as the ADR asks. A transaction is never retried under a new
identity: a waiting outbox keeps its identity until it is acknowledged.

### 4.2 Stages

| Stage | Condition | Action, on the server thread | Result |
|---|---|---|---|
| D | A pad pass (4.3). The pad is enabled, unpowered and not quarantined. One eligible player (4.4) was chosen. The outbox section and the owner's per-pad cap have room. | Set a to the smallest of 100, the bank, and the player's room minus what this pad already owes them. Debit the bank and append O(T) in one step. | Nothing durable yet. |
| PS1 | The pad has an outbox that no proof covered, and no proof in flight. Pad-side admission (4.5) allows a proof. | `beginOrdinaryTerrain` on the pad's chunk. The validator requires the pad's block entity at its position, a decodable root with the same identity, and every captured outbox with identical fields. | On success, every captured outbox counts as proved. |
| C | O(T) is proved. The owner is eligible again and stands on this pad. Their receipt root is valid and has room. | Credit a to a detached copy of the worn chest (4.4) and append R(T) to a detached copy of the receipt root. Validate both, then publish both with no call in between. | R(T) is live and not yet proved. |
| PF | An online player has a receipt that is not yet proved. | The shared prompt clock calls `saveAll()` at most once per 100 ticks, and never while a scan is open. A `SaveToFile` for that player requests a scan of the installed file's UUID and receipt root. | Each R(T) found with exact fields is proved. Anything else stays unproved. |
| A | R(T) is proved, and the pad is loaded. | Remove O(T) from the pad root. | O(T) is gone in RAM only. |
| PS2 | The pad has acknowledged outboxes and no proof in flight. | The next terrain proof, coalesced with any PS1, requires the acknowledged outboxes to be absent. | The acknowledgement is durable. |
| P | PS2 covered O(T), and the owner is online. | Remove R(T) from the live receipt root. | The next ordinary player save carries the prune. |

Three orderings carry the conservation argument:

- C never runs before PS1 has proved O(T), so a receipt never exists without a durable debit;
- A never runs before PF has proved R(T);
- P never runs before PS2 has proved the acknowledgement.

Neither side therefore deletes its record before the other side's record is durable. A receipt found in the live data
at login came from the installed file (facts F2 and F3, for every player but the integrated owner), so it counts as
proved without a scan. A credit arrives about one proof window after D, so the player sees it before PF finishes.

### 4.3 Scheduling and the fair queue

- Loaded pads join a server-lifetime index when their block entity loads, and leave it on removal and on chunk
  unload. There is no Level scan and no static collection. The proposed cap is 4,096 registered pads; a pad beyond
  the cap shows QUEUED and does not charge.
- Each pad is due once per 20 ticks. A round-robin cursor serves at most 32 pad passes per tick across all Levels and
  resumes where it stopped. One pass runs D for one player and C for the pad's proved outboxes whose owners stand on
  it.
- On the platform, the 1 x 1 x 2 box above the pad, eligible players are served in UUID order, cyclically after the
  last one served, one per pass.
- Receipt upkeep (A, P and the orphan rows of 4.6) runs for each online player with receipts at most once per 100
  ticks, with at most 16 receipt checks per tick. It considers only pads whose chunk is already FULL and never loads
  a chunk.

### 4.4 Eligibility and the credit target

An eligible player is a connected, real, living (`deathTime` 0) `ServerPlayer` in the pad's Level, inside the platform
box. The player is neither a spectator, nor in creative mode, nor the integrated owner (4.7). Creative players are
refused because they can create any stack, so conservation means nothing for them.

The worn chest must be the built-in space suit chest or the enchanted native chest of ADR-066 section 5.1, in its
slot, with a count of 1 and every owned root valid. That is, `ArmorEquipment.modules` is not null, `SpaceSuitOxygen`
reads VALID, and `EnchantedSuitOxygen` is readable. External ADR-025 provider chests are refused in v1.8. This is the
conservative reading: ADR-066 section 4 rules out an arbitrary capability callback, and the provider commit was
reviewed for whole canisters only.

The credit target follows a fixed order:

- **Oxygen:** the installed OXYGEN tank; else an installed EMPTY tank, which becomes OXYGEN; else the active buffer up
  to the configured target (`suitOxygenTarget`, at most 2,000).
- **Hydrogen:** the installed HYDROGEN tank; else an installed EMPTY tank. Never the active buffer.

Room is the target's maximum minus its stored amount. C credits exactly a or nothing. If the room has shrunk below a,
C waits and O(T) stays owed. Filling an EMPTY tank needs a small editor beside `ArmorEquipment.setStored`, which
matches an existing gas only. `ModuleLayout` still refuses two tanks of one gas.

### 4.5 Proof admission for pads

So that pads cannot starve rocket proofs, a pad starts a terrain proof only when `availableOrdinaryTerrain` is true.
At most one pad proof per Level per 20-tick window, and at most two server-wide, may be in flight. Rocket work
therefore keeps at least three of the four slots of a Level and six of the eight server-wide. One proof covers every
pad in its chunk. These are pad-side admission rules: the barrier's budget and its 100-tick deadline do not change. A
failed or superseded proof retries no sooner than 20 ticks later.

### 4.6 Reconciliation

A pad takes part only when its chunk is FULL, its root is valid, and its recorded identity position equals its actual
position. A pad whose recorded position differs, such as a copy made with operator NBT, is quarantined: it never pays
and never acknowledges.

| Pad state | Owner's receipt | Action |
|---|---|---|
| O(T) not proved | any | PS1 first. |
| O(T) proved | R(T) absent | C at the owner's next eligible pass; the owner must stand on the pad. |
| O(T) proved | R(T) not proved | Await PF. |
| O(T) proved | R(T) proved | A, then PS2. |
| O(T) acknowledged, PS2 pending | any | Await PS2, then P. |
| O(T) absent | R(T) names this pad | Orphan: prune. R(T) is only made after PS1, so O(T)'s absence is a durable acknowledgement. |
| Another pad, or no pad, at R(T)'s position | R(T) present | The original pad was removed by a path that bypassed its removal refusal, together with its outboxes. Prune, with one WARN. |
| The pad at R(T)'s position is quarantined | R(T) present | Hold. |

Disconnected owners wait for login. Only the saved owner UUID can receive C, and only the saved pad identity can
acknowledge.

### 4.7 Refusals for unsupported player roots

| Case | Behaviour | Data |
|---|---|---|
| The integrated owner (F3, F4) | No D and no C for that player. The menu and the action bar show a localized reason. The canister slots still work. | Nothing is written. |
| A receipt root that is future, malformed, over its bounds or duplicated, or a `PlayerPersisted` that is not a compound | No D and no C for that player, with one WARN per player, per case, per server session. Outboxes owed to the player wait. | The raw tag is never rewritten; vanilla keeps saving it unchanged. |
| A chest root that the existing readers refuse (FUTURE, INVALID or unsafe) | No C. | The chest is not touched. |
| An external provider chest | No D and no C (4.4). | Unchanged. |
| A failed player-file proof (IO, CHANGED, TIMEOUT, OVERSIZED or MISMATCH) | R(T) stays unproved, O(T) stays owed, and the player keeps the gas. OVERSIZED logs one WARN per transition. | Unchanged. |

There is no player-file veto, no chunk-save refusal for a player root, and no exception used as save control. A player
whose receipts cannot be proved holds at most eight receipts, and at most four outboxes on any one pad (section 8).

### 4.8 Source rules

- Ordinary breaking, explosions and entity destruction are refused while the pad holds an outbox or a quarantined
  root. The oxygen vent does the same for protected data through `onDestroyedByPlayer`, `canEntityDestroy`,
  `onBlockExploded` and `getDestroyProgress` (`J/atmosphere/vent/OxygenVentBlock.java`). The pad's push reaction is
  `BLOCK`, and its block entity overrides `onlyOpCanSetNbt()`.
- With no outbox, breaking drops the block and the canister slots. The bank's gas is lost, as the vent drops only its
  item slots and scrubbers in `onRemove`. Root may choose to keep the bank in the item instead; this design does not
  depend on it.
- A loaded root that does not decode is kept raw, and the pad is quarantined (no pass, no break), as W14 and W33 do.
  An outgoing root that fails `BoundedNbt.fits` uses `GuardedChunkSaves`, within R-027's scope.
- The pad uses no FE. A redstone signal, or `lifeSupport.classicDevicesEnabled=false`, stops D and the input slot. A,
  PS2, P, C for proved outboxes, and the output slot (safe withdrawal) continue. The block, the block entity and their
  IDs stay registered.
- Input: a filled canister in the input slot adds 1,000 mB through `GasCanisterHandler` when the gas matches and the
  bank has room. A fill-only fluid capability accepts the bank's gas up to the bank's room. Output: an empty canister
  in the output slot is filled from 1,000 mB of bank. These are single-store operations inside the pad's chunk.

## 5. Crash cuts on both sides of each save

The saves are PS1's store of the pad's chunk, PF's install of the player file and PS2's store of the pad's chunk.
"Disk" means what a restart loads. A restart rebuilds RAM from the pad root and the receipt root. Loaded outboxes count
as not proved until a fresh PS1, because a vanilla save may have stored them without a proof. Loaded receipts count as
proved (4.2).

| Cut | Pad chunk on disk | Player file on disk | Continuation | Gas |
|---|---|---|---|---|
| K0: before D | old bank | old | none | unchanged |
| K1: after D, before any store of the chunk | old bank, no O(T); if a vanilla save stored the chunk, this is K2 | old | none, because D was RAM only | unchanged |
| K2: after PS1's store, or any vanilla store, before C | bank minus a, O(T) | old | PS1 again, then C when the owner stands on the pad | a is owed once |
| K3: after C, before the install | bank minus a, O(T) | old, no R(T) | as K2: C runs once more | credited once, because the RAM credit died |
| K4: after the install, before the scan result | O(T) | R(T) and the credited chest | R(T) is proved at login; A, PS2, P | credited once |
| K5: after A, before PS2's store | O(T) | R(T) | A again, which is idempotent | credited once |
| K6: after PS2's store, before P | no O(T) | R(T) | orphan: prune | credited once |
| K7: after P, before the next player save | no O(T) | R(T) | orphan: prune | credited once |
| K8: after that save | no O(T) | no R(T) | none | credited once |

Failure branches inside PF:

- **Temporary-write failure.** F1 fires no event, so no scan runs. R(T) stays unproved, and the next prompt save
  retries. A crash here is K3.
- **Backup or replacement failure that returns normally.** The event fires, and the scan reads the old installed file
  without R(T): MISMATCH, then a retry. A crash here is K3. W80's rule R1 holds, because the event never proves.
- **A crash inside `safeReplaceFile`,** after `.dat` was renamed to `.dat_old` and before the new file moved in.
  Vanilla then loads a fresh player (F2), and the player loses the whole file. The pad still owes a, which a newly
  worn chest can receive. An operator who restores `.dat_old` restores a file from before or after C, and the table
  then applies to that file.

Interleavings:

- **Death between C and PF.** The credited chest drops, and R(T) moves to the new body (F5). Any later save of either
  body proves it. Unlike W80, no session invalidation is needed, because R(T) does not depend on the body.
- **Logout between C and PF.** The logout save installs R(T), and an offline scan can prove it. P waits for the next
  login.
- **The chest moved to a container between C and the install, then a crash** after the container's chunk was saved.
  The container holds the credited chest, and the player file predates C, so the restart is K3 and the owner can
  receive a again. This is the torn save that section 7 states. The native matrix asserts this boundary as documented
  behaviour; it is not counted as an exactly-once pass.
- **The pad's chunk unloaded after D.** The unload stores O(T) without a proof, and PS1 runs again after the reload.
- **Server stop.** The reader closes as W159F made it, and pending proofs are dropped. A restart continues by the
  table.

## 6. The Windows overlap rule

The pad adds no second reader. Leaf L3 (section 11) turns W80's reader and prompt clock into one server-lifetime
service that W80 and the pad share:

- one daemon scan at a time for either user, with W80's scans first, because a guarded W80 player has a 200-tick
  login deadline;
- one prompt clock: `saveAll()` at most once per 100 ticks while either user awaits a proof, and never while a scan is
  open;
- a pad scan starts only after a `SaveToFile` for that player, and the 10-second limit closes its handle.

Vanilla's own saves (the autosave, logouts, and other mods calling `saveAll`) can still overlap a scan. W159's overlap
GameTest covers exactly that case and stays a required test.

## 7. What stays non-atomic

- **Two files, ordered but not joined.** The pad's chunk and the player file are written separately. Exactly-once
  holds per transaction against the history of those two files: every cut of section 5 ends with a credited once in
  the player's history, or owed once by a durable outbox.
- **The credited chest is an ordinary item afterwards.** After C, the chest may leave the player at once. Suppose it
  reaches another store, such as a container, an item entity or another player, and that store is saved while the
  player's file is older. A crash then duplicates or loses that chest with all its contents, a included. This is the
  torn-save behaviour of every vanilla item move (ADR-054). The outcome equals refilling the chest from a canister in
  the player's inventory and then moving it: an owed outbox behaves like a canister the player owns. This statement
  replaces the ADR's rule that pending equipment cannot move (section 1, point 2).
- **The bank's canister and fluid routes** move gas between the pad and an inventory or a pipe like any container
  (ADR-054), outside this protocol.
- **Power loss.** The terrain proof forces the region worker. The player readback proves the installed file for
  process stops only, not an fsync of vanilla's player data, which is W80's boundary.
- **Partial restores.** Restoring only a region file, only a player file, or `.dat_old` can mint or lose gas. A
  consistent whole-world backup stays the recovery path, as for R-035.
- **The integrated owner** is not covered at all (section 9).

## 8. Bounds

| Bound | Value | Source |
|---|---|---|
| Bank | one gas, 0 to 16,000 mB | ADR-066 section 4 |
| Charge | at most 100 units per pass, and one pass per pad per 20 ticks | ADR-066 section 4 |
| Players per pass | one, in UUID order | ADR-066 section 4 |
| Pad passes | at most 32 per tick, through a fair resumable queue | ADR-066 section 4 |
| Outboxes | at most 8 per pad; **proposal:** at most 4 per owner per pad | the ADR; the stricter cap stops one unprovable player from filling a pad |
| Receipts | at most 8 per player | the ADR, moved from the equipment to the player |
| Pad root | 64 KiB, depth 16, 1,024 nodes, 8 item slots | ADR-066 section 2 |
| Receipt root | **proposal:** 4 KiB, depth 4, 96 nodes | new |
| Registered pads | **proposal:** 4,096 per server | new |
| Receipt upkeep | **proposal:** each online player at most once per 100 ticks, at most 16 checks per tick | new |
| Pad proofs | at most 1 per Level per 20 ticks, and 2 in flight server-wide | new, inside the unchanged barrier budget |
| Player readback | W80's ceilings, one scan at a time, a prompt save at most once per 100 ticks | merged |

A transaction takes roughly 150 ticks: about one proof window, up to 100 ticks until the next prompt save, a scan,
and another proof window. With four outboxes per owner, one player receives about 400 units per 150 ticks, below the
ADR's ceiling of 100 units per 20 ticks.

## 9. The alternative interaction, for the owner (D4)

The automatic protocol cannot cover the integrated owner. The owner chooses one of these options for single-player
worlds. The owner may also choose O1 for every player, instead of sections 4 to 8.

- **O1: a finite canister exchange (Root's recommendation for the integrated owner).** The pad fills an empty
  canister with 1,000 mB from its bank (the output slot of section 4.8). The player then uses the canister. The
  existing whole-canister refill puts 1,000 units into the active buffer. A new player-local whole-canister refill puts
  exactly 1,000 units into the installed tank of that gas, or into an installed EMPTY tank, when it has room, and
  returns one empty canister. It mirrors `SuitEquipmentService.fillOneCanister` and commits one detached chest.
  - *Conservation claim:* the pad's step is a single-store container operation, and the refill is player-local in one
    file. Moves between them have vanilla torn-save behaviour (ADR-054). There is no cross-store protocol, no proof
    and no prompt save.
  - *Interaction change:* the integrated owner does not charge by standing on the pad, and tank gas arrives in steps
    of 1,000 units.
- **O2: automatic charging with a stated non-crash-atomic limitation.** For the integrated owner only, the pad debits
  and credits in one tick, as the legacy pad did. A crash between the save of the pad's chunk and the next `level.dat`
  write can duplicate or lose gas charged since the last common save. This is what D4 calls an alternate interaction
  "with a stated non-crash-atomic limitation". It must never be described as crash atomic.

Under O1, a single-player world has no other way to fill jetpack hydrogen without the new canister-to-tank refill,
because tanks have no other source (section 2.4).

## 10. Proposed risk-register text, for Root

Root writes the register's wording. This is the content for a new entry after R-042, together with an addition to
R-027's scope for the pad root. Each protection states its scope, duration, log volume and recovery (AGENTS.md section
3.3).

| Protection | Scope | Duration | Log volume | Recovery |
|---|---|---|---|---|
| Removal refusal while a pad holds an outbox | One pad block | Until every outbox is acknowledged; indefinite if the owner never returns | None per attempt; the player who tries gets a message | The owner returns and stands on the pad; otherwise an offline repair of a whole-world copy |
| Quarantine of an undecodable or copied pad root | One pad block | Until a compatible jar or an offline repair restores the root | One WARN per pad per load | A compatible jar or a whole-world repair; the raw root is preserved |
| Save refusal for an oversized outgoing pad root | The whole terrain chunk, and the Level once 256 denials saturate (R-027) | Until the Level closes | R-027's existing ERROR pair per native retry | As R-027 |
| Refusal of a player whose receipt root is unsupported | That player's automatic charging | Until an offline repair or a compatible jar | One WARN per player, per case, per session | An offline repair of a whole-world copy; the raw root is preserved |
| Refusal of the integrated owner | That player in that world | By design | None; a localized message | Section 9's interaction |
| Full outbox or receipt sections, or an unprovable player file | New charging at that pad, or for that player | Until reconciliation | One WARN per OVERSIZED transition | Automatic once the proofs complete, or a smaller player file |

**Proposed entry text.** Automatic gas charging writes an outbox in the pad's chunk and a receipt in the player's
`ForgeData.PlayerPersisted`. Each is deleted only after the other is proved durable, through the escrow terrain proof
and W80's player-file readback. A pad with an outbox cannot be broken by ordinary means, possibly indefinitely. A
player whose receipt root cannot be read cannot use automatic charging, and the integrated owner never can. After a
credit, the suit chest is an ordinary item with vanilla torn-save behaviour. While receipts await a proof, the server
saves all online players at most once per 100 ticks, a cadence shared with W80, so the cost grows with the number of
online players and the size of their files. Restoring a single file, power loss and charging of the integrated owner
are outside the claim. Recovery is a consistent whole-world backup, never the deletion of a single file or root.

## 11. Implementation leaves

Each leaf is one task with its own commits and the WORKER-RULES checks. L1b and L4 need an independent persistence
review before they merge. Root registers the risk entry and the ADR-066 note before L1b merges, and brings the owner's
section 9 choice before any player-facing release. Production admission of D stays off until L4 qualifies. The order
is L2 and L3, then L1a and L5, then L1b, then L4. Sizes are main lines, with test lines in brackets.

**L1a: block, block entity and menu.** M, about 600 to 800 lines (about 400).
- *Scope:* the `gas_charge_pad` block, block entity and menu; the pad root codec; the bank; the input and output
  canister slots; the fill-only fluid capability; the source rules of 4.8, including quarantine and the removal
  refusal; registry entries in `ModBlocks`, `ModItems`, `ModBlockEntities` and `ModMenuTypes`; menu operations under
  ADR-066 section 2. D is not implemented here.
- *Tests:* codec bounds and raw preservation; canister and fluid routes (stacks of 16, refusal of partial amounts,
  simulate and execute, a full destination inventory); the removal refusal with an outbox fixture; `onlyOpCanSetNbt`;
  quarantine; a restart that preserves bank, slots and outboxes exactly.

**L1b: the charge coordinator.** L, about 800 to 1,100 lines (about 900).
- *Scope:* the pad index and fair queue (4.3), eligibility and target order (4.4) with the EMPTY-tank editor, the
  stages D to P, pad-side proof admission (4.5), receipt upkeep and the reconciliation table (4.6), the refusals of
  4.7 and their localized reasons. D's production admission is gated off.
- *Tests:* unit tests for target order, room, caps, UUID order, cursor fairness, every reconciliation row and every
  refusal, including the integrated owner through a test seam, because a GameTest server cannot be an integrated
  owner; GameTests with real players on a pad, real proofs, the prompt save and the readback, death and logout between
  C and PF, and a chest moved to a container.

**L2: the receipt codec.** S, about 150 to 200 lines (about 250).
- *Scope:* the strict schema-1 codec of the receipt root, its bounds, duplicate refusal and raw preservation; a
  detached publish that rewrites only its own key and leaves every other `PlayerPersisted` key, and a foreign parent,
  untouched; the classification used by 4.7.
- *Tests:* every bound at its limit, every malformed shape, a future schema, a foreign parent, and a round trip through
  a real player save and a clone.

**L3: the readback adapter.** M, about 250 to 400 lines (about 300).
- *Scope:* move W80's reader and prompt clock into one server-lifetime service, with W80's requests first (section 6).
  Add a second expectation to the streaming scanner: the UUID and
  `ForgeData.PlayerPersisted.arce_gas_charge_receipts`, returning the set of proved transactions. It reuses the
  existing byte budget, skipping, depth count and change check. W80's behaviour, ceilings, deadlines and tests stay
  unchanged.
- *Tests:* scanner fixtures (absent, present, duplicated and oversized receipt roots, depth, a foreign parent); the
  shared clock (one `saveAll` per 100 ticks across both users, none during an open scan); W159's overlap GameTest; the
  full W80 rejoin suites, unchanged.

**L4: S2 cuts.** M to L, about 600 lines of Python runner and 300 lines of Java fixture.
- *Scope:* a native runner in the W159 style for the cuts K1 to K8, the three PF failure branches, death, logout, the
  moved-chest boundary and a pad-chunk unload. Each case has a seed start and two restarts, and the first recovery is
  killed externally where work remains. The oracle sums the bank, the outboxes and every credited amount in player
  files and third stores; it checks exact receipts, unchanged unrelated data and a strict log scan.
- *Plan:* about 16 cases of three starts, 48 starts, with a cap of 60 including pilots. W80's estimate formula gives
  `(48 * 20 + 900) * 1.5 / 3600 = 0.78` heavy-lock hours, plus one `runData` and one GameTest run.

**L5: DataGen art and data.** S, about 150 to 250 lines.
- *Scope:* NEW original art from a v1.8 provider; the asset row `textures/blocks/gaschargertop.png` stays REVIEW,
  and no AR 16x file is imported, following W172's recommendation on owner question D3. Also the blockstate, block and
  item models, loot table, mineable and tool tags, and a recipe as a reviewed proposal for Root. English and Chinese
  text covers the name, the menu and every reason of 4.7. Output goes to `src/generated/v1.8`.
- *Tests:* language completeness, the resource graph and provenance screening.

The leaves total about 1,950 to 2,750 main lines, in line with W172's estimate of 1,500 to 2,500 plus native cuts.

## 12. Record of this session

**Done.** This note is the only repository change. The session read the TASK, WORKER-RULES, ADR-066 sections 1 to 4
and 9, [W80-DESIGN](W80-DESIGN.md) and [W80](W80.md), the records of [W157](W157.md), [W158](W158.md),
[W159](W159.md) and [W160](W160.md), section 3 item B1 of the W172 inventory, and the merged code that sections 2 and
4 cite. No agent was started, and nothing was pushed or merged.

**Commands and results.**

| Command or check | Result |
|---|---|
| `git status`, `git log -3`, `git worktree list` | Clean on `codex/v1.8.0-w174` at `171c1d71d`. Other worktrees were listed and not touched. |
| `git status --short` in the shared checkout | The TASK file is untracked there; it was read, not changed. |
| `Get-FileHash -Algorithm SHA256` on the W172 inventory and the mapped jar | Both match the hashes in the header and in W80. |
| `javap -c -p -classpath <mapped jar>` for `ServerPlayer`, `PlayerDataStorage`, `InventoryMenu$1`, `EnchantmentHelper`, `Inventory`, `PlayerList`, `MinecraftServer`, `Entity`, `IntegratedPlayerList` and `DedicatedServer` | All exit 0. The output is in `W174/bytecode/`, with `SHA256SUMS.txt` and `COMMANDS.txt`. |
| Read and Grep on the sources cited in sections 2 and 4 | Confirmed every cited method, constant and limit at `171c1d71d`. |
| `git diff --cached --check`, with this note staged | Exit 0. The note is the only staged file, stored with LF line endings. |
| `python -B scripts/validate_repository.py --require-approved-identity`, with this note staged | Exit 0: 45 passed, 0 pending, 0 warnings, 0 failed. It ran again after this row was filled in, with the same result. |

No Gradle, GameTest, server or heavy-JVM lock was used, and no unit probe was needed beyond the bytecode reads.

**Choices where the TASK or the ADR left room.** The receipt location, the conservation statement and the integrated
owner's refusal (section 1). The per-owner outbox cap, the receipt-root bounds, the pad index cap, the upkeep budget
and the pad-side proof admission are judgement values, marked as proposals in section 8. External provider chests are
refused. The bank is lost when an empty-outbox pad breaks, following the vent.

**Not done.** No production code, test, asset, ADR, risk register, status file or ledger changed. The fresh read-only
review, Root's ADR-066 note, the risk entry and the owner's D4 choice are still required.

**Known risks.**
- The conservation statement of section 7 is weaker than the ADR's proposed text during the window between C and the
  player-file install, and only Root and the owner can accept that.
- The proposed caps and the 150-tick cycle are estimates, not measurements.
- Pads share the ordinary proof quota with rocket work; a busy Level slows pads, not rockets.
- W80's merged readback has no integrated-owner check. This note found no W80 path that places the integrated owner,
  because admission names only offline players and an integrated server runs only while its owner is connected. That
  reasoning is not covered by a test, and Root may want an assertion.

**All current-version Required Gates satisfied: no.** This is a design note. The next v1.8 step is the fresh
read-only review of this note, then Root's ADR note and risk entry and the owner's D4 choice, then leaves L2 and L3.
