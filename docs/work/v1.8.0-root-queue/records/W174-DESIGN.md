# W174: the gas charge pad's feasibility design (ADR-066 section 4, D4)

**Proposed design, revision 4, 2026-10-28. Not implementation, risk acceptance, owner decision or Gate evidence.**
Revision 1 was Claude's design at `f9dbf78e5`, based on shared head `171c1d71d`. Revision 2 was a fresh Codex
implementer session on `codex/v1.8.0-w174`, addressing Root decision R134 and findings W174-R1 to W174-R8. Its second
review returned APPROVE-WITH-CHANGES. Revision 3 was a fresh Claude implementer session from `cf656dfe8`, addressing
Root decision R136 and W174-R9 to W174-R15. Its third review returned APPROVE-WITH-CHANGES. Revision 4 is a fresh
Codex implementer session from `2b155bd57` on the same branch, addressing W174-R16 to W174-R19. The task is
`docs/work/v1.8.0-root-queue/TASK-W174-GAS-CHARGE-PAD-DESIGN.md`, read from the shared checkout; its Revision 4/R140
section is this session's assignment. Root owns ADR-066, the risk register, the ledger and the owner question.
Under R140, Root checks these four changes against the third review instead of a fourth full review, then freezes
the design until the owner's D4 choice. Sections 12 to 14 preserve the earlier records; section 15 records revision 4.

**Write scope:** this record only, plus new probes and logs under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W174/revision-4/` (earlier revision folders remain unchanged). No production,
ADR, risk, status, ledger or other worktree changes are authorized. Finding labels below identify revisions, not
independent closure verdicts. Revision 1's feasibility conclusion was blocked by its review. Revision 4 keeps revision
3's text where it still holds; each change carries the label of the finding it answers.

**Inputs.**

| Input | Where | SHA-256 |
|---|---|---|
| W172 inventory, section 3, item B1 | `D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md` | `134fb14c0e588758639cad574f63fa2af18e615e8ac5622832b1bccf043e64ca` |
| Independent revision-1 review and named design-model tests | `D:/GitHub/ARCE-Task-Evidence/root-queue/W174-review/REVIEW.md` | `f390b483c4e3dcdf42b446f76a311e0476a67b7339b0194a20e13dd06681913c` |
| Second independent review, of revision 2, and its probes | `D:/GitHub/ARCE-Task-Evidence/root-queue/W174-review2/REVIEW.md` | `c8683ad1dd1ce83aae179cbf5c3990d2c160447512a04c60c4630f9ef9bdddb3` |
| Third independent review, of revision 3, and its probes | `D:/GitHub/ARCE-Task-Evidence/root-queue/W174-review3/REVIEW.md` | `5db15d26e173b106113855eba61503fc9ac826ad2a7491c75a87efd58b3166c1` |
| Forge 47.4.10 mapped jar, read as bytecode | the Gradle cache file that W80 and W159 used | `95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e` |
| ADR-066 sections 2 and 4, and section 9 item 4 (D4) | [ADR-066](../../../decisions/ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md) | not hashed |
| ADR-065, "Proof admission" (the hand-off bounds and the pre-U request expiry) | [ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md) | not hashed |

In code citations, `J` means `src/main/java/io/github/sunthemoon/advancedrocketrycommunity` at `171c1d71d`. The
source tree is unchanged at `2b155bd57`; revision 4 checks it there.

**Names used below.**

- *T* is one charge transaction, *a* its amount and *g* its gas. An amount is 1 to 100 units, and one unit is one mB,
  the C16 canister mapping (section 2.4).
- The *bank* is the pad's single gas store. *O(T)* is the pad's outbox entry for T. It lives in the pad's
  block-entity root, in the pad's chunk.
- *R(T)* is the player's receipt for T. It lives in the player file (section 3.1).
- **W174-R1:** *Q(T)* is the source acknowledgement marker, retaining O(T)'s exact fields in the same bounded
  source entry with phase ACK instead of OWED. It is not another gas asset or another delivery obligation.
- D, PS1, C, PF, A, PS2 and P are the protocol stages of section 4.2. K0 to K8 are the crash cuts of section 5.
  F1 to F9 are the bytecode facts of section 2.1.
- **W174-R9/R10:** pad admission is *open* at a tick when the predicate of section 4.5 holds there. A barrier
  *consumer* is a production caller of `EscrowBarrier` (section 4.5 lists all of them).
- **W174-R12:** a pad's *incarnation* is a RAM token minted when its block entity is loaded or placed, and dropped
  when it unloads or is removed. A position's *absence generation* is a RAM counter that advances whenever a pad
  block entity appears at, or leaves, that position (section 4.1).
- A *chunk proof* is a completed ordinary terrain proof of the escrow barrier (section 2.2). A *player-file proof*
  is a matching streaming readback of the installed `playerdata/<uuid>.dat` (section 2.3).
- The *integrated owner* is the player that an integrated (single-player or LAN) server reports through
  `MinecraftServer.isSingleplayerOwner`.

## 1. Answer

1. **W174-R1/R2/R3: a revised protocol is proposed, not a passed feasibility gate.** For supported players whose
   authoritative store is the installed `playerdata/<uuid>.dat` (dedicated players and LAN guests), the five
   primitives exist, but the pad-owned acknowledgement lifecycle and shared proof admission still require code,
   independent review and S2 qualification. Each step maps onto a merged mechanism (section 3); the proposed glue
   needs no new lifecycle hook, no new Access Transformer line and no Mixin. It does
   use the escrow chunk store, which relies on Access Transformer lines that W11a2 already merged.
   **W174-R9 to R12:** revision 3 binds pad admission to the existing "due" predicates of every barrier consumer
   instead of to every waiting rocket entry, and refuses D while admission is closed (section 4.5). It includes W158
   and governed in-flight captures, and bounds the rest (section 4.5). It quantifies the sealed start delay of the
   pad drain wait (section 4.5) and makes proof currency per transaction entry and incarnation (sections 4.1 and 4.2).
   The glue still needs no new hook, AT or Mixin. It adds read-only accessors to `EscrowBarrier`, a neutral demand
   view in `resourceescrow`, and one change to the sealed coordinator's existing `attemptRoom` (section 4.5).
   **W174-R16 to R19:** revision 4 adds a per-terrain-slot watch hold, includes the entire residual drain latency
   in the timing expression, withdraws the eager-save no-livelock claim, and exposes budget Level membership.
   Neither finite IO nor fair turns guarantee completion; the local-write and retained-proof holds join section 10.
2. **It needs two changes to the proposed protocol text of ADR-066 section 4.**
   - The receipt lives in the player's own persisted data, `ForgeData.PlayerPersisted.arce_gas_charge_receipts`, not
     on the worn chest.
   - Equipment is therefore never pending. The rule against carrying, replacing or extracting pending equipment is
     replaced by a stated conservation claim: an owed charge behaves like a canister the player owns (section 7).

   Section 3.1 shows why a receipt on the chest can be neither observed nor held with merged mechanisms, unless the
   death interaction changes.
3. **W174-R6: the gate does not hold for the integrated owner.** Vanilla preferentially loads that player from the
   `Player` tag of `level.dat` when present, with playerdata fallback when absent (facts F3 and F4). Refusal is an
   enforced runtime predicate even in the fallback case, not a claim that this player is unreachable. No merged
   mechanism observes a durable `level.dat`
   write. The pad must refuse automatic charging for that player, so single-player worlds need the alternative
   interaction of section 9, which the owner must choose. **W174-R13:** moving a world from dedicated to
   single-player hosting also rolls back the host's player file through a stale `level.dat` `Player` tag. This is a
   vanilla partial rollback of every item, in both directions, and needs a whole-world repair step before the move
   (sections 4.7, 7 and 10).
4. **D4 therefore cannot close through this note alone.** The TASK's stop condition (a new hook, Access Transformer
   or Mixin) is not triggered. Point 3 still changes the interaction for single-player worlds, so section 9 is
   written for the owner, with a recommendation. Root takes the two protocol changes of point 2 into an ADR-066 note.

## 2. Facts this design relies on

### 2.1 Pinned bytecode (Forge 47.4.10)

These facts were read in revision 1 with `javap -c -p` from JDK 17.0.7 on the jar named above, and independently
checked by the revision-1 review. Revision 2 inspected those records and the source, not a new bytecode run. The dumps are in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W174/bytecode/`. Numbers are bytecode offsets. Revision 3 added F9 from a
new `ChunkMap` dump in `.../W174/revision-3/bytecode/`, and relies on the second review's dumps for F3 and R13.

| Fact | Content | Where |
|---|---|---|
| F1 | `PlayerDataStorage.save` serializes the player (8), writes a temporary file (33), calls `Util.safeReplaceFile` (85) and then fires `PlayerEvent.SaveToFile` (97). Every exception in that range is caught and logged at WARN. | `save`; W80 and W159 recorded the same order |
| F2 | `PlayerDataStorage.load` reads only `<uuid>.dat` and never falls back to `.dat_old`. A missing or unreadable file gives a fresh player. | `load`, 2 to 41 |
| F3 | **W174-R15 item 1:** `PlayerList.load` loads the integrated owner from `WorldData.getLoadedPlayerTag()`, the `Player` tag of `level.dat`, when that tag exists. When the tag is null, the owner falls back to `PlayerDataStorage.load` (24 to 28 and 67 to 72), as every other player always does. | `load`, 7 to 72 |
| F4 | `IntegratedPlayerList.save` caches the owner's tag for the next `level.dat` write and then writes `playerdata` as usual. `DedicatedServer.isSingleplayerOwner` always returns false. | `save`, 8 to 31; `isSingleplayerOwner`, 0 |
| F5 | `ServerPlayer.restoreFrom` copies `ForgeData.PlayerPersisted` from the old body to the new one on every clone, that is, after a death and on the End exit. The copy is outside the keep-everything branch. | `restoreFrom`, 296 to 328 |
| F6 | `PlayerList.saveAll()` is public. `PlayerList.save(ServerPlayer)` and `MinecraftServer.playerDataStorage` are protected, so saving one player alone would need new access. | declarations |
| F7 | The armor slot of the inventory menu refuses a pickup only when the player is not creative and the stack reports Curse of Binding through `ItemStack.getEnchantmentLevel`. | `InventoryMenu$1.mayPickup`, 13 to 32; `EnchantmentHelper.hasBindingCurse` |
| F8 | `Inventory.save` stores armor at `Slot` 100 plus its index, so the worn chest is the `Inventory` element with `Slot` 102. | `Inventory.save`, 114 to 122 |
| F9 | **W174-R12, new in revision 3:** `ChunkMap.processUnloads` eagerly saves up to 20 visible chunks per tick while the tick has time left (178 to 244). `saveChunkIfNeeded` skips a chunk within 10,000 ms of wall time after its previous eager save (48 to 77 and 94 to 110). `ChunkMap.save` stores nothing unless the chunk is unsaved (12 to 19), clears the flag (22) and posts `ChunkDataEvent.Save` (119 to 144), which advances the escrow save monitor's slot generation. | `ChunkMap`, dumped in revision 3's evidence |

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
- **W174-R10:** every capture, ordinary or governed, also shares the rest of `EscrowBarrierBudget`: one capture per
  server tick (`lastCapture`), `PENDING = 32`, `PER_TYPE = 16` per Level window, `LEVELS = 64` and the 16 MiB byte
  budget, of which a reservation needs 4 MiB of room. The budget's Level table only grows: `reserve` puts an entry
  for each Level it serves and nothing removes one for the barrier's lifetime. A Level's staged captures are forced
  together; the force waits for every staged store (`EscrowBarrier.java:204-205`), and a later capture goes into the
  next batch.
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
| The source acknowledgement (W174-R1) | A replaces O(T) with Q(T); PS2 proves Q(T) and no O(T); only then may the source unlock and P prune R(T) | 2.2, with new pad-owned state, not a merged pad implementation |

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
  changes only while the bank and the entire source-entry section (OWED and ACK) are empty (W174-R1);
- two canister slots, one for input and one for output, each holding at most one item;
- `next_revision`, a nonnegative long that only increases;
- **W174-R1:** at most eight source entries total, O(T) plus Q(T), not eight of each. Each has a transaction UUID,
  a revision, an owner UUID, a gas and an amount of 1 to 100. These fields are immutable; its phase changes only
  OWED -> ACK. A Q entry never returns to OWED and never credits gas. The four-per-owner limit includes both phases.

**W174-R1/R3:** RAM records the load incarnation and PS1 coverage per entry, plus at most one unresolved
capture per chunk and two server-wide (4.5). Pending acknowledgement identity is in Q(T), not only this RAM index.
**W174-R12:** revision 2's "state generation" is withdrawn; no whole-root counter takes part in proof currency.
The incarnation token is minted in the block entity's existing load and placement paths and dropped on unload and
removal. The pad index keeps the absence generation of a position only while a pad is registered there (at most
4,096) or one of the two pad tickets is an orphan capture of it, so it stays bounded.
Unload drops the live index/proof authority, not a persisted entry. If unload saves Q(T), reload reconstructs its
hold and requires fresh PS2. If no store installed Q(T), restart loads O(T), and the durable R(T) repeats A, not C.
Removal before PS2 is refused. An unexpected forced removal invalidates the incarnation; it does not authorize P.
R(T)'s saved source identity supplies any later absent/replaced-source proof request (4.6).

**The receipt root** `ForgeData.PlayerPersisted.arce_gas_charge_receipts`, schema 1, holds at most eight receipts.
Each has a transaction UUID, a revision, the pad UUID, the dimension (canonical ASCII, at most 128 bytes), the block
position, a gas and an amount. **W174-R13:** each receipt also carries the owner UUID, copied from O(T). The
proposed bounds are 4 KiB encoded, depth 4 and 96 nodes, with exact keys and types and no duplicate transaction.
With the owner field and the position as three integers, eight receipts take about 91 nodes; L2 checks the exact
count and size at the maximum dimension length. RAM-only state records whether a player-file proof covered each
receipt. The codec never reads or changes other keys under `PlayerPersisted`.

A transaction's identity is the pair of UUID and revision, as the ADR asks. A transaction is never retried under a new
identity: a waiting outbox keeps its identity until it is acknowledged.

### 4.2 Stages

| Stage | Condition | Action, on the server thread | Result |
|---|---|---|---|
| D | A pad pass (4.3). The pad is enabled, unpowered and not quarantined. One eligible player (4.4) was chosen. The source-entry section and the owner's per-pad cap have room. **W174-R9:** pad admission is open at this tick (4.5), and the pad holds no OWED entry that PS1 has not covered. | Set a to the smallest of 100, the bank, and the player's room minus what this pad already owes them. Debit the bank and append O(T) in one step. | Nothing durable yet; D and C are mutually exclusive in this pass (W174-R4). At most one unproved outbox per pad. |
| PS1 | The pad has an outbox that no proof covered, and no proof in flight. Pad-side admission (4.5) allows a proof. | `beginOrdinaryTerrain` on the pad's chunk. The validator requires the pad's block entity at its position, a decodable root with the same identity, and every captured outbox with identical fields. | **W174-R12:** a current result (4.2, below) proves each captured outbox that is still OWED with the same fields, one entry at a time. |
| C | O(T) is proved, R(T) is absent, and the cadence/player selection of 4.3 admits this one entry (W174-R4). The owner is eligible again and stands on this pad. Their receipt root is valid and has room. | Credit exactly a to a detached copy of the worn chest (4.4) and append R(T) to a detached copy of the receipt root. Validate both, then publish both with no callback in between. | R(T) is live and not yet proved. |
| PF | An online player has a receipt that is not yet proved. | The shared prompt clock calls `saveAll()` at most once per 100 ticks, and never while a scan is open. A `SaveToFile` for that player requests a scan of the installed file's UUID and receipt root. | Each R(T) found with exact fields is proved. Anything else stays unproved. |
| A (W174-R1) | R(T) is proved, and the original pad is loaded. **W174-R13:** the holder's UUID, R(T)'s owner and O(T)'s owner are equal. | Replace O(T) with Q(T), retaining all identity/value fields. | Acknowledgement is RAM-only; removal and gas switching remain held. |
| PS2 (W174-R1/R3) | The pad holds Q(T) and shared admission allows a capture. | Validate the coherent source root, same pad identity, exact Q(T), and absence of O(T). A confirmed, current result permits clearing this Q entry on the owner thread. | The ACK state is durably installed before the hold clears; clearing Q cannot restore O. |
| P (W174-R1) | A current positive PS2 or orphan-source proof covers this exact R(T); the owner is online and supported. **W174-R13:** the holder's UUID equals R(T)'s owner, and Q(T)'s owner when Q is present. | Recheck live receipt equality and the currency of 4.2 below, then prune in the proof-consumption step and release the proof. | The next ordinary player save carries the prune. Offline or stale results require a fresh source proof later. |

Three orderings carry the conservation argument:

- C never runs before PS1 has proved O(T), so a receipt never exists without a durable debit;
- A never runs before PF has proved R(T);
- P never runs before PS2 has proved the acknowledgement.

**W174-R1/R6:** receipt deletion cannot rely on loaded/live source absence. Only the captured/forced/read-back
terrain root permits P. Initial login may mark exact receipts from the supported player's installed file proved;
dimension join, death, End clone and reconnect callbacks must not promote arbitrary live RAM receipts. If load origin
is uncertain, require PF instead. The host predicate is checked before this classification too. C may precede PF,
but neither admission nor proof completion has a guaranteed end-to-end latency.

**W174-R1/R3:** a PS2 callback validates its currency (below) before any publication. Clearing its exact Q and,
for an online supported player with that exact valid receipt, pruning R are one callback-free owner transition. That transition's own Q deletion is an
expected monotonic edit, not permission to ignore an intervening mutation. If P cannot run in that transition,
release the proof and require a fresh orphan proof later. No stored marker is an instruction to refund the bank.

**W174-R12: proof currency per transaction entry and incarnation.** A capture can cover several entries of one
chunk. Its result is judged separately for each transaction T it covers. It is current for T only when all of these
hold on the owner thread:

- the barrier's own checks: `Result.confirmed()` and `Proof.valid()`, which include the save monitor's slot
  generation, so a vanilla save of the chunk between capture and consumption still fails the attempt
  (`SUPERSEDING_SAVE`);
- the pad's attempt checks of 4.5: server and Level lifetime, the chunk FULL, the attempt token and the inclusive
  deadline;
- for PS1 and PS2: the same pad incarnation as at capture, and the live root still holds T's entry with identical
  fields in the captured phase (OWED for PS1, ACK for PS2);
- for an orphan proof (4.6): the position's absence generation unchanged since capture, and T still absent from the
  live state there;
- for P: also the exact live receipt and the owner rule of the P row.

Nothing else invalidates the result for T: not the bank, not a canister slot, not fluid input, and not another
entry's D, A or Q clearing. Those edits only change entries other than T, or no entry at all. Safety does not need
whole-root equality. Entry fields are immutable, phases only move OWED, then ACK, then absent, and at most one
capture per chunk is unresolved. The incarnation and absence generation catch replacement and removal. A new entry
written after the capture, such as another owner's D, is simply not covered; it waits for the next PS1.

**W174-R12/R18: superseding saves under continuous input.** Per-entry currency excludes unrelated RAM edits,
not native saves: continuous fluid or hopper input dirties the chunk again after capture, and a save inside the
proof window still fails it with `SUPERSEDING_SAVE`. F9's 10,000 ms eager-save cooldown limits frequency, not phase.
Revision 3's one-in-eight-to-ten estimate and no-livelock claim are withdrawn. A minimum 20-tick retry delay and a
fair queue supply neither a quiet interval nor a deterministic completion bound.

For example, ten pads in separate FULL chunks of one Level capture at ticks 0, 20, ..., 180 and repeat every 200
ticks. Each chunk stays dirty; its eager saves occur one tick after its captures, at 1, 201, ... for pad 0. At
20 TPS each respects the 10-second cooldown, one submission per Level per 20 ticks, and at most two unresolved
tickets if completion is at capture + 21. Every pad gets its turn and every IO completes, yet all proofs fail.
The third review's 100-attempt trace has zero confirmations and can repeat indefinitely. Tick time and wall time
must also be tested separately: at low TPS even a 20-tick batch wait can span another eager-save opportunity.
No retry jitter or delay change is claimed to prove liveness, and the native generation check stays unchanged.

PS1, PS2 and orphan progress is conditional on an admitted proof interval without a superseding save and with IO
delivery before the logical deadline. Local writes can therefore hold O/Q, removal/gas switching and receipt
cleanup indefinitely even without rocket contention or failed IO. Section 10 registers that boundary. Operators
can quiesce local automation and retry; this is not a guaranteed quiet interval if other saves continue. If that
does not restore progress, use validated coherent offline repair, never refund/discard O or erase a receipt.

### 4.3 Scheduling and the fair queue

- **W174-R1/R4:** loaded pads join a server-lifetime index and leave it on removal/unload. Pending source entries
  stay in the root; index removal is never acknowledgement. There is no Level scan or static collection. The cap
  is 4,096 registered pads. An overflow pad shows QUEUED and does not charge; a loaded BE retries registration
  at most once per 100 ticks with a position-derived phase, without another unbounded waiting index. Available
  slots admit retriers; fairness applies to registered pads, not a latency guarantee for overflow pads.
- **W174-R4:** a round-robin cursor examines at most 32 registered pads per tick across all Levels and resumes
  where it stopped. Twenty ticks is a minimum spacing, not a promise of service every 20 ticks (4,096 pads need
  at least 128 ticks per sweep). A due pass chooses one eligible UUID cyclically after the last serviced UUID.
  It executes either one C for that player's oldest eligible proved O(T), or one D if no such C is ready, never
  both and never a loop over returning owners/outboxes. Reconciliation of an existing R is not C and transfers no gas.
- Both D and C obey this pass token; C pays the exact original amount, at most 100, or waits without splitting or
  replacing T. Failed/full/ineligible owners do not monopolize the UUID cursor. Older creditable obligations have
  priority over new D for that owner; other owners get their cyclic turns. Unselected obligations remain owed.
  No elapsed-time or restart catch-up accumulates tokens. On load, the first D/C is delayed at least 20 server ticks;
  unload/reload cannot reset a same-session cooldown into a burst. Proof and receipt upkeep do not invoke C outside
  this queue. Four returning 100-unit obligations require at least four passes spanning 60 ticks; simultaneous
  owners never both receive gas in one pass.
- Receipt upkeep (A, P and the orphan rows of 4.6) runs for each online player with receipts at most once per 100
  ticks, with at most 16 receipt checks per tick. It considers only pads whose chunk is already FULL and never loads
  a chunk.
- **W174-R10:** pad passes, receipt upkeep and every pad capture submission run once per server tick in the
  existing `TickEvent.ServerTickEvent`, phase END, at `EventPriority.LOWEST`. That is after every NORMAL-priority END
  handler, including the merged barrier consumers (`RocketManager.onServerTick`, `EscrowRuntime.tick` with
  `EscrowBarrier.tick` and its callbacks, and `RocketRejoinEvents.tick` for W158). This uses an existing event, not
  a new lifecycle hook.

### 4.4 Eligibility and the credit target

An eligible player is a connected, real, living (`deathTime` 0) `ServerPlayer` in the pad's Level, inside the platform
box. The player is neither a spectator, nor in creative mode, nor the integrated owner (4.7). Creative players are
refused because they can create any stack, so conservation means nothing for them.

**W174-R5:** mirror the separate branches of `SuitEquipmentService.read`, not their conjunction. Both require the
actual worn CHEST slot, count 1, `EquipmentNbt.safe`, valid relevant owned roots, a detached candidate, and an
unchanged live stack/reference/tag immediately before publication:

- Built-in: `SpaceSuitArmorItem` in its native CHEST slot, `ArmorEquipment.modules != null` and
  `SpaceSuitOxygen.read` VALID. Use its valid module tanks and active buffer; do not require the enchanted root.
- Enchanted native: not built-in, `classicEquipmentEnabled`, `catalog.find(item) == null` and
  `EnchantedSuitOxygen.eligible(stack, CHEST)` (native armor slot and enchantment checks), with its owned oxygen
  root readable. Do not call built-in module/oxygen validity a prerequisite. This branch has no module tanks;
  it accepts oxygen into the active buffer only and refuses hydrogen.
- An external catalog entry takes precedence over enchantment, including a disabled provider; it cannot fall back
  to native enchantment to bypass refusal. External ADR-025 providers are refused here. No arbitrary capability
  callback participates in the credit publication.

The built-in credit target follows a fixed order (W174-R5); the enchanted branch has only its active oxygen buffer.
**W174-R15 item 2:** both native branches fill the active buffer only up to the configured target
(`suitOxygenTarget`, at most 2,000), as the merged canister refill does for both
(`J/compat/atmosphere/SuitEquipmentService.java:119`).

- **Oxygen:** the installed OXYGEN tank; else an installed EMPTY tank, which becomes OXYGEN; else the active buffer up
  to the configured target (`suitOxygenTarget`, at most 2,000).
- **Hydrogen:** the installed HYDROGEN tank; else an installed EMPTY tank. Never the active buffer.

Room is the target's maximum minus its stored amount. C credits exactly a or nothing. If the room has shrunk below a,
C waits and O(T) stays owed. Filling an EMPTY tank needs a small editor beside `ArmorEquipment.setStored`, which
matches an existing gas only. `ModuleLayout` still refuses two tanks of one gas.

### 4.5 Proof admission for pads

**W174-R2: policy before quota.** `availableOrdinaryTerrain` is only the final native hint. It does not implement
`SealedProofAdmission` or `SealedProofTurns`. Pad proofs therefore pass the shared policy below before that hint,
and its binding and tests are prerequisites to enabling pad proof submissions. **W174-R10:** revision 2 put this
adapter beside the two rocket classes. Revision 3 moves the seam to a neutral view and covers every consumer.

**W174-R10: every barrier consumer.** A search of `J` at `cf656dfe8` for calls of `begin`, `beginJoint`,
`beginOrdinary` and `beginOrdinaryTerrain` finds the production consumers below. The other callers
(`EscrowChainReleaseProbe`, `EscrowDestinationProbe`, `EscrowPhaseProbe`, `EscrowReleaseProbe`,
`EscrowSourceClockProbe`, `EscrowTestReconciliation` and `EscrowPinnedQueueGameTestSupport`) each call
`EscrowTestEndpoints.requireEnabled`, so they run only in gated tests.

| Consumer | Entry | Shares with a pad | Pad admission |
|---|---|---|---|
| W98 ordinary retirements (`RocketOrdinaryRetirement`) | `beginOrdinary` through `RocketRetirementWorld` | the ordinary sub-quota and every budget item of 2.2 | **Included.** Closed while any entry is PROVING or holds a reservation, or meets the due expression of `ordinaryTurn` (`RocketOrdinaryRetirement.java:421-424`): WAITING, and either its admission tick or `now + 1 >= notBefore`. The expression is evaluated over every pending entry, because an entry that waits before its turn check has been withdrawn from the turns map (`waiting` calls `fail`, which calls `withdraw`). |
| W152 sealed disassembly (`SealedDisassemblyCoordinator`, `SealedProofBatch`) | `beginOrdinary` and `beginOrdinaryTerrain` through `SealedDisassemblyWorld` | as above; an active attempt reserves all four ordinary captures of its Level | **Included.** Closed while an attempt is active, while the existing sealed waiting predicate holds (`SealedDisassemblyCoordinator.java:77-78`), and while the `SealedProofTurns` refused-caller window is open. Rule 3 below adds the drain wait. |
| W158 release proofs (`RocketReleaseCoordinator`) | `beginOrdinary` through `RocketReleaseWorld.java:148-149` | as above | **Included.** Closed while any release entry is in stage B0, is capturing, has a started B1 attempt, or is in B1 with `now + 1 >= notBefore`. Only B0 and B1 capture. W158 cannot merely be bounded: a refused capture drops a B0 admission (`B0_FAILED`, `RocketReleaseCoordinator.java:262` and 276) and delays B1 by its 200-tick retry. |
| Governed escrow: F1 (`EscrowFlightCheckpoint.java:154`), `EscrowGovernedDestination.java:161`, `EscrowGovernedReturn.java:127`, `EscrowGovernedTerminal.java:103`, `EscrowReconciliationDriver.java:206`, `EscrowSettlementRecovery.java:133` and `EscrowPreF2Cancellation.java:85` | `begin` and `beginJoint` | the capture tick, `PENDING`, `PER_TYPE`, `LEVELS`, bytes and the Level batch; not the ordinary sub-quota | **Included while in flight:** closed while the barrier holds any reservation that is not a pad ticket. **W174-R16:** after IO, the per-slot watch gate below protects retained terrain proofs through their consumers' final releases, including C6-to-C7 and O2-to-O3/O4. Other interference is stated below. |

**W174-R9: the open predicate (option (a) of the second review).** Pad admission is open for a pad at tick t only
when all of these hold, evaluated in the pad service step of 4.3:

1. the barrier holds no reservation other than the pads' own tickets (its reservation count equals the pad IO
   count). This covers every consumer's in-flight IO, including rocket IO left behind by an expired rocket attempt;
2. no W98, W152 or W158 condition of the table above holds;
3. **W174-R19:** query the budget's actual Level membership and size: `containsLevel(level) || levelCount() < 32`.
   If the pad's Level has no entry in that table, fewer than 32 of its 64 entries must be in use (a new
   W174-R10 bound: the table never shrinks, so pads may never take its last 32 entries from rockets and governed
   escrow).

A capture additionally needs the W174-R16 per-slot gate below, the native hint `availableOrdinaryTerrain` for the
pad's chunk and the pad limits of rule 4 below. D needs admission open, but not those capture limits.

**W174-R16: protect retained terrain authority, not just pending IO.** `EscrowBarrier.tick` releases its IO
reservation before delivering a successful proof. `EscrowSaveMonitor.captureWatch` pins entity captures only;
`ordinaryAvailable(..., false, chunk)` does not protect a retained governed terrain proof. Such a proof still
authorizes C7 or O3/O4 while a checked ledger write waits. PS1, PS2 and every orphan capture must therefore also
require that the target terrain slot has **no unreleased watch** in the runtime's monitor.

- Expose a neutral owner-thread `terrainSlotHeld(level, chunk)` query through `EscrowProofDemand`, backed by
  `EscrowSaveMonitor`'s existing `Slot(level, false, chunk)` and positive reference count. It creates no watch,
  capture, Level service or ticket and performs no world lookup. This deliberately includes ordinary and already
  invalid terrain watches until their owners release them; it is conservative, bounded by the existing 128-slot
  table, and needs no second lifetime index or per-consumer publication.
- The capture owns the watch from acquisition, failure releases it, and successful IO hands it to `Proof`.
  `Proof.release()` ends that ownership; split joint proofs keep the original watch until the last split releases.
  C6/O2 completion, zero IO reservations, a writer waiting/UNKNOWN state, or pad unload is **not** release authority.
  Consumer invalidation/abort releases via its existing cleanup, not a new timeout. Stale/duplicate releases must
  retain the existing handle identity checks so they cannot clear a replacement slot's watch.
- The pad checks this predicate immediately before native reservation on the owner thread, without a callback
  between check and begin. All three capture routes use that same entry point. They do not acquire/release a
  foreign watch or weaken `Proof.valid()`/generation checks. A governed capture arriving after a pad may invalidate
  the pad as before; precedence is not reversed. Existing ordinary/native APIs remain unchanged for non-pad callers.
- This is a **same-slot** pad hold, not server-wide demand: another chunk or Level may proceed if all other limits
  allow. D remains subject to its existing open predicate/one-unproved-entry limit; it may create that one entry
  before a local watch clears. C and PF need no terrain capture. O/Q and receipts remain held if their proof waits.
- During runtime stopping, deny every pad submission first. Barrier quiescence does not release proofs retained by
  a checked writer; preserve their holds while stop drains those writes. Consumer cleanup and final runtime monitor
  clear end the holds only with pad admission already closed. Chunk/Level unload alone is not permission to erase
  another consumer's watch. A restarted runtime reconstructs no watch authority from the old runtime.

The hold may be indefinite if the consumer never releases (for example a stalled checked writer). Section 10
registers scope, diagnostics and recovery. It is not covered by the one-lost-submission-tick argument below.

A rocket entry that waits on a condition of its own (`SURVIVOR_UNLOADED`, `CREW_WAITING`, slot holds,
`SLOT_UNLOADED` and `SAVE_OFF`, polled every 20 ticks; `CENSUS_LIMIT`, every 200) closes admission only on the ticks
where its due expression is true. Those are two ticks of every 20 for a 20-tick poll, and two of every 200 for
`CENSUS_LIMIT`. A native-window BUSY retries one tick later, so it stays due on every tick; that is real contention,
and pads yield to it. The second review's option (b), a server-wide hold of all pads for every waiting entry, is not
chosen: one offline crew member or one unloaded survivor would stop every pad on the server. Pads still stop
everywhere while rocket demand covers every tick: continuous BUSY contention, sealed work back to back, or at least
ten polling entries whose due ticks cover all twenty phases. Section 10 registers that hold.

**W174-R9: D while admission is closed.** D needs admission open at its tick (D row of 4.2), and a pad never holds
more than one OWED entry without PS1 coverage. A stall that begins after a D therefore leaves at most one unproved
outbox on that pad, and no further D runs there until admission reopens. While admission is closed at the pad's
pass, the menu and the action bar show a localized reason ("charging paused: rocket proofs have priority"). C, A,
P, the canister slots and safe withdrawal are unaffected; C needs no capture.

**W174-R10: what a pad capture can take from a consumer that is not yet due.** The pad service runs after every
merged consumer has ticked (4.3). In tick t those consumers have already made the captures they were due to make,
and the barrier has already delivered tick t's callbacks. A pad capture at t then takes, at most:

- **the capture of tick t.** Only work that runs between ticks with the same tick count, chiefly packet tasks, can
  find it taken. Their existing response applies: W98 retries a busy capture one tick later; W152 and W158 capture
  only from their own tick, which has already run; governed callers report "not started" (for F1, the
  `BARRIER_AVAILABLE` precondition or `f1.refused.BARRIER_BEGIN`) and are tried again by their caller. A pad
  captures only while no other reservation exists, and every other reservation closes pad admission until it ends,
  so each governed capture loses at most one tick to a pad;
- **one of the four ordinary captures of its Level's current 20-tick window**, and two of the eight
  `ORDINARY_PENDING` server-wide while its IO runs. A W98 attempt that becomes due in that window and Level, and
  starts after the pad's tick, still has 21 capture opportunities within its inclusive 100-tick deadline by
  ADR-065's window-phase arithmetic when no other consumer uses those windows: three in the shared window, sixteen
  in the next four, and at least two in the window that opens at the deadline. W158 needs at most three slots per
  batch and captures one at a time. It can lose a capture to a pad only when other ordinary consumers that became
  due after the pad fill the rest of that window, which those consumers can already do to it. Its existing
  `B0_FAILED` and retry outcomes then apply, and the tests below assert that boundary;
- **one of the sixteen terrain captures per Level window, two of `PENDING = 32`, and its actual capture bytes.**
  Two pad chunks at the 4 MiB decoded ceiling would still leave room for two concurrent 4 MiB governed
  reservations under the 12 MiB admission line, instead of four. Typical chunks are far smaller;
- **its Level batch.** A capture that another consumer stages in the same Level and batch is forced with the pad's.
  The shared force tick is no later than its own would be, because the pad's earlier staging set it; the force
  does wait for the pad's own store. A pad store that never completes holds that batch's force (`allOf`); W116's
  `forceMixed` separates failures, not hangs. A later capture goes into the next batch, and at most two pad tickets
  exist, so at most two batches can be held per server run. Their consumers fail by their own deadlines or
  retries. Section 10 registers this with the stalled-IO row.

**W174-R16/R17:** these bullets describe quota and submission interference, not a wall-clock completion guarantee.
The per-slot gate prevents pad-induced invalidation of already-retained terrain proofs; batch drain latency still
has all the terms below. Rule 3 keeps sealed attempts from starting while any pad IO is unresolved. The second
review's simplified force/readback model keeps all 21 submissions, not necessarily confirmations, in every case
with pads. The actual mixed-service/deadline tests below remain required.

**W174-R10: the seam stays one-way (AGENTS 3.4).** A neutral view, `EscrowProofDemand`, lives in `resourceescrow`
and is owned by `EscrowRuntime` for the server's lifetime. It imports no rocket or pad type.

- W98, W152 and W158 each register one read-only demand source with it, in their existing construction or bind on
  the server thread. Each source answers the conditions of the table above, reading their own state; the W158
  source is registered by `RocketReleaseWorld`, which already uses `EscrowRuntime`.
- The pad coordinator publishes its unresolved IO ticket count into the same view.
- **W174-R19:** `EscrowBarrier` gains three read-only owner-thread accessors: its reservation count,
  `levelCount()` and `containsLevel(level)`, the latter two reading the same private budget table. Completion
  releases reservations, not membership. Neither the runtime store map nor native availability is a substitute.
  Evaluate membership/size and reserve in one owner-thread step; no callback may insert a new Level between them.
- **W174-R16:** the view also exposes `terrainSlotHeld(level, chunk)` from the existing monitor (above), preserving
  watch lifetime and requiring no consumer dependency on pad types. Missing/closed query service refuses capture.
- The sealed coordinator's existing `attemptRoom` (`SealedDisassemblyCoordinator.java:287-289`) also requires the
  view's pad IO count to be zero. That is rule 3. Apart from registering the three read-only sources, it is the only
  change inside rocket code.

Rocket code then reads pad load, and pad code reads rocket demand, only through `resourceescrow`; neither imports
the other. A missing source or view denies pad proofs. It never falls back to calling the barrier directly. No new
Forge lifecycle hook, AT or Mixin is involved.

**The rules (revision 2, kept where they still hold).**

1. Service active sealed and ordinary rocket work in its existing order before considering pad capture. A sealed
   attempt keeps **all four** ordinary captures of its Level and its next-tick inspection priority. Pad PS1, PS2
   and absent-source proofs are all lower-priority consumers; none bypass this rule through receipt upkeep.
2. **W174-R9:** admit a new pad capture only while the open predicate holds. This replaces revision 2's
   "no capture while any rocket proof is active or waiting server-wide". A pad is not a fake ordinary rocket UUID:
   it neither consumes `ordinaryStarted` nor extends or clears the rocket turn. The ordinary return entitled to the
   post-sealed opportunity runs before another sealed attempt under the existing policy. It is due during that
   hand-off, so a pad cannot slip into it, even if numerical quota is available.
3. Account already-submitted pad captures as outstanding work in shared admission. In particular, the sealed
   coordinator's `attemptRoom` must see them until their terminal callback; an expired logical pad attempt is
   not zero outstanding IO. New rocket demand closes pad admission immediately, waits for those existing captures
   to drain, then takes the native opportunities before any pad retry. Running IO is not revoked. Waiting for
   admission does not start a sealed 100-tick attempt or suspend its existing pre-U expiry.
4. **W174-R16:** only while the open predicate holds and the terrain slot has no unreleased watch may the pad queue
   use `availableOrdinaryTerrain` and the atomic native
   reservation. Keep at most one pad submission per Level per 20 ticks, two unresolved captures server-wide, and
   one per chunk. One captured chunk may cover the bounded registered pads there, with exact per-pad validators.
   Failed/expired/superseded attempts retry no sooner than 20 ticks after logical termination and only after the
   original IO terminates. No quota, rocket deadline, retry window or service-lag allowance increases.

Existing ADR-065 and R-035 availability residuals remain; the pad design does not claim to fix them.

**W174-R11/R17: the sealed start delay caused by rule 3.** Let demand become visible at tick d, after a pad
capture at c <= d - 1. The barrier schedules batch dispatch by c + 20 under continuing per-tick service; it does
not promise completed forcing by then. Dispatch waits for every own-store future in the batch, then for
`worker.synchronize(true)`, then the single reader's queue and readback, then owner-thread delivery and the next
sealed service that can observe the released ticket (`EscrowBarrier.java:204-228`, 309-326).

For each outstanding pad ticket i, define **R_i as all residual drain latency after its scheduled batch dispatch**:
remaining own-store/batch wait + force + reader queue + readback + owner delivery + subsequent sealed service.
Any late dispatch caused by missing barrier service must also be included; it is not free time. The latest service
time observing both tickets released governs rule 3, not the first completion. For c = d - 1 with on-time batch
dispatch, the drain component is bounded by `19 + max(R_i)`. Equivalently, for one ticket with all terms in ticks:

```text
drain <= 19 + max(0, S - 20) + F + Q + B + O + H
```

S is staging-to-completion of all relevant stores, F force latency, Q reader-queue delay, B readback, O owner
delivery, H service delay until the sealed coordinator observes release. These timing symbols are not protocol
O(T)/Q(T). Add dispatch lateness if applicable, and take the latest completion across the two tickets. There is
**no numeric runtime upper bound** on those terms, even when IO eventually succeeds. The measured pad/no-pad
start delta δ must also include any subsequent scheduling/window interaction; do not substitute readback B for δ.

The unchanged second-review probe gives 20/24 ticks extra for its readback parameter 1/5, 21 submissions in all
25,600 cases, and maximum total start delay 26. Those remain results of its simplified model: it omits store wait,
force duration, reader contention and later delivery/service. They are not 20/24-tick runtime guarantees. The third
review's finite successful example captures at 199, sees demand at 200, dispatches at 219, finishes stores at 239,
force at 240, readback at 245 and owner delivery/service at 246: δ is 46, not `19 + 5 = 24`.

- **ADR-065's hand-off bounds are unchanged, with their original assumptions.** While demand is continuously due
  or active, no new pad capture starts. After existing IO drains, later continuously-demanded hand-offs retain the
  native-opportunity-relative 2-tick lag (up to 16 ticks after a sealed attempt), W152K's 4-tick case and W152L's
  two-wave residual. A 20/200-tick backoff can reopen pad admission and create another idle-to-due edge; a later
  retry is not automatically free of pad drain. Measure each such edge and any overlap with ordinary work; finite
  IO does not imply pad IO finishes before an already-running ordinary attempt. No inherited deadline is extended.
- **The 200-tick pre-U expiry.** `SealedDisassembly` rejects a request older than 200 ticks before retention
  (`J/rocket/sealed/SealedDisassembly.java:83` and 93), and the PREPARE proof precedes retention (`prepare()`,
  line 129). Take a request with p ticks before its first attempt can start, a pad delay δ, and an attempt that needs
  up to 101 ticks with its inclusive deadline. A first attempt that succeeds needs p + δ + 101 ≤ 200. Here δ is
  the **measured total added start delay**, not readback alone: δ = 24 requires p ≤ 75; δ = 46 requires p ≤ 53.
  A first attempt that fails at age a, followed by the 1-tick superseding-save retry and a full second attempt,
  needs p + δ_total + a + 1 + 101 ≤ 200, that is a ≤ 98 - p - δ_total. Include added delay at every reopened
  admission edge in δ_total, without double-counting overlap. A 24-tick total leaves a ≤ 74 - p, while 46 leaves
  a ≤ 52 - p. Other retry/backoff costs must also be charged at their actual duration. There is no guarantee that
  headroom remains: even finite IO can let the request expire as `REJECTED`; the player asks again. These are
  conditional arithmetic checks, not measured service timing or an expiry waiver.
- **The own-Level refinement is not adopted.** Pad IO in another Level does not share the sealed Level's batch, but
  it still holds up to two of the eight server-wide `ORDINARY_PENDING`, its bytes and a Level-table entry. The model
  suggests that this does not cost a submission under healthy or stalled IO, but it does not model every resource.
  Rule 3 stays server-wide until the L1b tests measure both variants; Root may then adopt the refinement with that
  evidence and a review.
- **Stalled pad IO** keeps rule 3 closed: no sealed attempt starts anywhere until the ticket's IO ends or the server
  stops. Section 10 registers this as a server-wide rocket unavailability beside R-035's residuals.

**Required mixed-workload tests (W174-R2, extended by W174-R9 to R11).** Run the actual shared admission and turn
classes, the demand view and the native budget, with a 21-slot sealed plan, pads continuously ready, ordinary
retirements waiting, same-Level and different-Level demand, and each 20-tick window phase.

- No pad capture occurs during sealed activity or its waiting hand-off. Pad work must not take any reserved capture
  or worsen the inherited ordinary and sealed hand-off bounds. Cover the anonymous refusal window, the two-wave
  crew-waiting residual, native BUSY and backoff, and the global capture tick.
- **W174-R11/R17:** compare rocket-only and pad traces including captures 1 to 40 ticks before demand. Instrument
  staging, scheduled/actual dispatch, every batch store completion, force, reader enqueue/start/end, owner delivery
  and sealed service in their real event order. Inject each delay separately and together, with two pad tickets in
  the same/different Levels; include the 24/26/46 examples, queued reads, slow successful IO and stalled IO. Check
  the full residual-drain expression, measured total start delta, submissions versus confirmations separately, and
  pre-U expiry/headroom using the real `SealedDisassembly`. Retain the 21-submission check under the simplified
  model's assumptions; do not claim eventual successful IO guarantees confirmation by age 100. Include fresh edges
  after 20/200-tick backoffs, continuous-demand hand-offs and the own-Level variant in the same matrix.
- **W174-R9:** a long-waiting retirement (`SURVIVOR_UNLOADED`, and separately `CENSUS_LIMIT`) with pads ready. Pads
  capture between its due ticks and never on them, PS1 and PS2 complete, and when the survivor loads, its attempt
  starts at its next poll with the same capture trace as without pads. With sealed work back to back, D stays at
  zero, no new outbox appears and the localized reason is shown. With ten polling entries covering every phase,
  assert a retained pad hold, not fabricated progress.
- **W174-R10:** W158 B0 and B1 in the pad's Level and in another Level, including a B0 admitted in the same tick as a
  pad pass, and a W158 batch sharing a window with a pad capture and a W98 attempt: no `B0_FAILED` that the
  rocket-only trace lacks, except the asserted fill-the-window boundary above. Governed F1 preparation, a
  pre-F2 cancellation and a governed return, each with a pad capture in the same tick and in the same Level batch:
  at most one lost submission tick per governed capture, with drain delay accounted separately, and unchanged governed margins (the W90a margin GameTests run with
  pads ready). A W98 census of 21 slots in one Level after a pad capture. The Level-table rule at 31, 32 and 33
  entries. **W174-R19:** assert new/existing membership decisions independently at all three sizes, including
  reservation release (membership remains), runtime shutdown and a fresh budget; query without adding entries.
- **W174-R16:** hold actual C6 joint terrain proofs after IO completion through a delayed C7 writer, and actual
  O2 proofs through delayed O3/O4 writes. With reservation count zero, attempt same-chunk PS1, PS2 and orphan
  captures: no pad reservation, serialization or generation increment; the consumer's proof/write lease remains
  valid. Other chunks/Levels can capture subject to existing limits. Exercise split-proof partial/final release,
  failed/aborted/UNKNOWN writes, superseded-but-unreleased watches, stale/duplicate release after replacement,
  chunk unload and stopping while a writer drains. After final release the pad may capture; quiescence alone must
  not expose held authority. Run the unchanged monitor, barrier, reconciliation and settlement suites as well.
- **W174-R18:** with rockets absent and finite IO, registered pads resume round-robin attempts, not guaranteed
  successful proofs; the eager-save trace of 4.2 must retain obligations through all failed attempts, then complete
  after a verified quiet interval. Slow or never-completed IO is a separate
  unbounded-availability case, not a deadline extension; include one stalled pad store sharing a batch with a
  governed capture.

**W174-R3: pad-owned attempt lifecycle.** The barrier does not expire attempts. A new bounded pad coordinator owns
`WAITING -> ACTIVE -> CONFIRMED/FAILED/EXPIRED`, a monotonic attempt token and an inclusive 100-tick logical deadline
from accepted first capture (age 100 is allowed; age 101 is expired). Before submission it registers an IO ticket,
so even a synchronous callback cannot escape accounting. A refused submission retires that ticket without IO.
Every result checks, on the owner thread, server/Level lifetime, FULL chunk, load incarnation, attempt token, deadline,
and **(W174-R12)** the per-transaction currency of 4.2 instead of a whole-root generation. A late or mismatched result releases any returned proof
once and changes no source, receipt, bank or equipment. Accepted proofs are consumed under those checks and released;
there is no transferable proof authority cached across reload. Duplicate completions cannot release capacity twice.

Logical expiry, unload, unexpected removal, player-session invalidation or service close invalidates the relevant
authority and releases already-returned proofs. **It does not cancel or forget submitted IO.** A server-lifetime
ticket, at most two in total, retains only immutable identity and completion bookkeeping until the barrier delivers
its terminal result. Only then does it release the pad IO count; the barrier owns its native budget/watch release.
No replacement capture for that chunk may start meanwhile, including after reload. Stop rejects further publication
and quiesces the existing runtime; a new server reconstructs O/Q/R, never prior tickets. A never-completed store or
force may hold capacity until server closure, and persistent failure can retain the source indefinitely across
restarts. Tests cover completion at ages 100/101, synchronous failure, duplicate callback, delayed/never-completed
futures, unload/reload, replaced pads and completion after close.

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
| Q(T), PS2 pending or reloaded (W174-R1) | any | Never C; hold removal, prove Q and absence of O, then clear Q. P only under a current proof. |
| O(T) and Q(T) absent, original valid pad present (W174-R1) | R(T) names this pad | Hold until a fresh terrain capture/force/readback proves the same pad root excludes T. Live or loaded absence alone never permits P. |
| Another valid pad, or no pad, at R(T)'s position (W174-R1) | R(T) present | Hold until a fresh terrain proof positively establishes the original source identity is absent in this FULL chunk. Only then P, with one diagnostic per source/receipt episode. No refund/re-credit or inference from a missing BE. |
| The pad at R(T)'s position is quarantined | R(T) present | Hold. |
| any (W174-R13) | R(T)'s owner differs from the holder's UUID, or from O/Q(T)'s owner | Hold and never use it: no C suppression, no A, no orphan request, no P. One WARN per holder per server session. It occupies one of the holder's eight receipts until whole-world repair. |

Disconnected owners wait for login. Only the saved owner UUID can receive C, and only the saved pad identity can
acknowledge. **W174-R13:** "R(T) absent" in the C row means absent among the receipts that the holder owns; a
foreign receipt never stands in for delivery.

**W174-R1:** every orphan request is keyed by the receipt's original pad UUID, dimension, position, transaction,
revision, owner, gas and amount, even if no BE exists. The captured terrain must be structurally valid and show the
exact same coherent source root without T, or the original identity absent. **W174-R12:** revision 2's "and the
replacement state unchanged" is replaced by the per-transaction currency of 4.2. The position's absence generation
must be unchanged since capture, and T must still be absent there. A replacement pad's bank, slots, fluid input and
other entries may change meanwhile.
IO failure, unreadable terrain, missing disk slot or an unloaded chunk is not positive absence. The regular terrain
capture can install and prove an already-loaded chunk's actual absence; it does not load an absent chunk. A receipt
upkeep request uses the same two-ticket/turn admission, not another orphan queue. Before P recheck the exact live
receipt, supported player session, loaded block/BE incarnation (including an absence generation), and proof validity.
A replacement/unload between capture and callback invalidates it; a later attempt recaptures. No-pad or replaced-pad
cleanup is never an exception to PS2 ordering. Operator root edits/partial restores remain outside the claim (7).

### 4.7 Refusals for unsupported player roots

| Case | Behaviour | Data |
|---|---|---|
| The integrated owner (F3, F4; W174-R6) | Check `server.isSingleplayerOwner(player.getGameProfile())` before D, C, login proof promotion, PF acceptance, A and P. Refuse automatic charging/reconciliation even for obligations created on a dedicated server. The menu/action bar shows a localized reason; canister slots still work. **W174-R13:** a pad that owes this player stays held against removal and gas switching for as long as this UUID hosts. | Preserve O/Q/R exactly in the mod's own logic; no host receipt is pruned based on playerdata. **W174-R13:** vanilla itself may not preserve them: a world moved from dedicated hosting can load a stale `level.dat` `Player` tag and overwrite the host's playerdata (section 7). Resume on a supported dedicated/guest session, after the whole-world repair step of section 7. |
| A receipt root that is future, malformed, over its bounds or duplicated, or a `PlayerPersisted` that is not a compound | No D, C or P, and no proof promotion/A based on that root (W174-R1/R6). One WARN per player, per case, per server session. Outboxes owed to the player wait. | The raw tag is never rewritten; vanilla keeps saving it unchanged. |
| A chest root that the existing readers refuse (FUTURE, INVALID or unsafe) | No C. | The chest is not touched. |
| An external provider chest | No D and no C (4.4). | Unchanged. |
| A failed player-file proof (IO, CHANGED, TIMEOUT, OVERSIZED or MISMATCH) | R(T) stays unproved, O(T) stays owed, and the player keeps the gas. OVERSIZED logs one WARN per transition. | Unchanged. |

There is no player-file veto, no chunk-save refusal for a player root, and no exception used as save control. A player
whose receipts cannot be proved holds at most eight receipts, and at most four outboxes on any one pad (section 8).

### 4.8 Source rules

- **W174-R1:** ordinary breaking, explosions and entity destruction are refused while the pad holds O(T), Q(T)
  awaiting positive PS2, or a quarantined
  root. The oxygen vent does the same for protected data through `onDestroyedByPlayer`, `canEntityDestroy`,
  `onBlockExploded` and `getDestroyProgress` (`J/atmosphere/vent/OxygenVentBlock.java`). The pad's push reaction is
  `BLOCK`, and its block entity overrides `onlyOpCanSetNbt()`.
- Only with no O/Q hold, breaking drops the block and the canister slots (W174-R1). The bank's gas is lost, as the vent drops only its
  item slots and scrubbers in `onRemove`. Root may choose to keep the bank in the item instead; this design does not
  depend on it.
- A loaded root that does not decode is kept raw, and the pad is quarantined (no pass, no break), as W14 and W33 do.
  An outgoing root that fails `BoundedNbt.fits` uses `GuardedChunkSaves`, within R-027's scope.
- **W174-R12:** canister, fluid and output-slot operations change only the bank and the slots. They never touch a
  source entry, so their RAM edits do not invalidate a proof (4.2), and they may continue while a capture is in
  flight. **W174-R18:** the native saves they provoke can still supersede it indefinitely.
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
proved only under 4.2's supported initial-load check; Q entries require fresh PS2 (W174-R1/R6).

| Cut | Pad chunk on disk | Player file on disk | Continuation | Gas |
|---|---|---|---|---|
| K0: before D | old bank | old | none | unchanged |
| K1: after D, before any store of the chunk | old bank, no O(T); if a vanilla save stored the chunk, this is K2 | old | none, because D was RAM only | unchanged |
| K2: after PS1's store, or any vanilla store, before C | bank minus a, O(T) | old | PS1 again, then C when the owner stands on the pad | a is owed once |
| K3: after C, before the install | bank minus a, O(T) | old, no R(T) | as K2: C runs once more | credited once, because the RAM credit died |
| K4: after the install, before the scan result | O(T) | R(T) and the credited chest | R(T) is proved at login; A, PS2, P | credited once |
| K5: after A, before any ACK store (W174-R1) | O(T) | R(T) | Repeat A; ordinary break refused while live Q awaits proof | credited once |
| K6: after PS2 or vanilla ACK store, before P (W174-R1) | Q(T), or no entry if subsequent cleanup saved | R(T) | Re-prove Q/no O under 4.6; never credit Q | credited once |
| K7: after P, before the next player save (W174-R1) | Q(T) or no entry; never the old O(T) | R(T) | Fresh positive source proof before repeat P | credited once |
| K8: after that save (W174-R1) | Q(T) or no entry | no R(T) | Prove/clear leftover Q if any; never C | credited once |

**W174-R1 additional native cuts.** At K5 attempt ordinary break, explosion and entity destruction before PS2:
each must refuse, preserve Q/R and leave the old O/R pair safe after a player-only save and external kill. At K6,
after PS2 but before a save of Q's cleanup, break normally, save the pruned player receipt, withhold the terrain
removal save and kill: restart may restore Q, never O, so it must not credit again. Also cover unload after A
before/after ACK installation, kill the first recovery between repeat A and PS2, and complete an old proof after
reload/replacement. For forced absent/replaced sources, hold R before the positive terrain proof, save the player
and kill while old O remains on disk; no prune or second credit is permitted. After positive absence, prune is safe
with respect to the original outbox; any destroyed bank is an explicit sink, not a refund. These are specified cuts,
not executed S2 results.

Failure branches inside PF:

- **Temporary-write failure.** F1 fires no event, so no scan runs. R(T) stays unproved, and the next prompt save
  retries. A crash here is K3.
- **Backup or replacement failure that returns normally.** The event fires, and the scan reads the old installed file
  without R(T): MISMATCH, then a retry. A crash here is K3. W80's rule R1 holds, because the event never proves.
- **W174-R8: a crash inside `safeReplaceFile`,** after `.dat` was renamed to `.dat_old` and before the new file
  moved in (or install and backup restoration both fail). Vanilla can load a fresh player (F2), losing the whole
  player inventory. This applies to the first credit save, a later logout/autosave, and the prune save K7 -> K8.
  With durable O, a remains owed; with durable Q/no O, it is no longer owed and the missing inventory is lost.
  If a third store kept the chest while O survives, replay can duplicate a. A missing file reports IO, not proof.
  Restoring `.dat_old` alone is partial rollback and is not the repair recommendation. L4 tests early and late holes
  separately, including interrupted recovery; exact conservation is not claimed for lost/rolled-back player files.

Interleavings:

- **W174-R3/R8: death between C and PF.** R(T) moves to the new body (F5); the chest drops unless keepInventory
  retains it. Invalidate the old live publication/session token and rescan the replacement, preserving the receipt.
  A receipt proof establishes delivery history, not the durability of a dropped item. Test death with and without
  keepInventory and End cloning separately. Dimension join is not initial disk login and cannot promote RAM R.
- **Logout between C and PF.** The logout save installs R(T), and an offline scan can prove it. P waits for the next
  login.
- **The chest moved to a container between C and the install, then a crash** after the container's chunk was saved.
  The container holds the credited chest, and the player file predates C, so the restart is K3 and the owner can
  receive a again. This is the torn save that section 7 states. The native matrix asserts this boundary as documented
  behaviour; it is not counted as an exactly-once pass.
- **The pad's chunk unloaded after D.** The unload stores O(T) without a proof, and PS1 runs again after the reload.
- **W174-R1/R3: unload after A.** Persisted Q reconstructs the removal hold; otherwise old O and R repeat A.
  Neither a vanished queue entry nor a callback for the former BE incarnation allows P.
- **Server stop.** The reader closes as W159F made it, and pending proofs are dropped. A restart continues by the
  table.
- **W174-R12: continuous input or another owner's D during a capture.** The result for T stays current (4.2). A
  vanilla eager save inside the window fails the attempt (F9); the next attempt recaptures. Neither changes a cut.
- **W174-R13: a hosting-kind switch.** This is not a crash cut but a vanilla rollback between two sessions; section 7
  states it. The K0 to K8 continuations assume that the host's installed player file is the one the host's next
  session loads, which a stale `level.dat` `Player` tag breaks.

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

**W174-R3/R6:** shared reader requests carry consumer, UUID, session/attempt token and immutable expectations;
late results cannot cross consumers or mutate a new player instance. Keep W80's existing four-result-action budget
and deadlines. W80-first means priority at the next scan start, not preemption of an open pad scan. A pad scan already
using the 10-second ceiling can consume W80's login window; preserve the 200-tick protected disconnect and retained
obligation, test arrival during that scan, and do not claim unchanged latency. Continuous W80 scans can starve pad
PF. Queue no more than one coalesced request per tracked player, using existing W80 bounds and a proposed cap of
128 pad request identities; excess pad requests stay in receipts and retry without a separate overflow list.

## 7. What stays non-atomic

- **W174-R1/R8: two files, ordered but not joined.** The K0-K8 invariant applies to coherent, available installed
  files and a chest remaining in that player's saved inventory, with no excluded third-store/rollback effects
  (**W174-R13:** including no hosting-kind switch without the repair step below).
  Delivery is once or remains owed; Q is acknowledgement, never gas. This is not unconditional global exactly-once
  conservation. Positive source proof before every prune also applies to ordinary removal, which is not an accepted
  tearing exception.
- **The credited chest is an ordinary item afterwards.** After C, the chest may leave the player at once. Suppose it
  reaches another store, such as a container, an item entity or another player, and that store is saved while the
  player's file is older. A crash then duplicates or loses that chest with all its contents, a included. This is the
  torn-save behaviour of every vanilla item move (ADR-054). The outcome equals refilling the chest from a canister in
  the player's inventory and then moving it: an owed outbox behaves like a canister the player owns. This statement
  proposes replacing the ADR's rule that pending equipment cannot move (section 1, point 2); it is not accepted here.
  **W174-R8:** the reverse save order loses a: the player saves R after dropping/moving the chest but its destination
  does not save. If the destination saves while the receipt does not, replay creates a. Death drops have the same
  two directions; keepInventory differs. Consumption, deliberate destruction and `/clear` are sinks, not replay rights.
- **The bank's canister and fluid routes** move gas between the pad and an inventory or a pipe like any container
  (ADR-054), outside this protocol.
- **Power loss.** The terrain proof forces the region worker. The player readback proves the installed file for
  process stops only, not an fsync of vanilla's player data, which is W80's boundary.
- **Partial restores.** Restoring only a region file, only a player file, or `.dat_old` can mint or lose gas. A
  consistent whole-world backup stays the recovery path, as for R-035.
- **W174-R1/R8:** operator `/data` on a pad, command replacement and third-party movers bypassing removal policy
  cannot authenticate source history, even at the same position. They are outside ordinary-removal conservation.
  Actual positive terrain proof is still required before orphan pruning; a warning never substitutes for it.
  Missing player-file replacement holes in section 5 are separate vanilla losses, including after acknowledgement.
- **The integrated owner** is not covered at all (section 9).
- **W174-R13: switching between dedicated and single-player hosting rolls back the host's player file.** Vanilla
  does this to every item, not only to gas. The bytecode chain, from the second review's dumps (offsets in
  brackets):
  1. A dedicated server's `saveAllChunks` passes `PlayerList.getSingleplayerData()`, which is null there [0 to 1],
     to `saveDataTag` [155 to 161].
  2. `PrimaryLevelData.createTag` then substitutes its `loadedPlayerTag` [4 to 12], and `setTagData` writes it as
     `Player` [505 to 513]. `loadedPlayerTag` is assigned only in the constructor and by the data-fixer upgrade.
     A `Player` tag from an earlier single-player session therefore survives dedicated hosting unchanged.
  3. When the world is next opened locally, `PlayerList.load` gives that stale tag to the integrated owner (F3,
     [7 to 35]). `IntegratedPlayerList.save` caches it and then calls `PlayerList.save` [14 to 31], which overwrites
     `playerdata/<uuid>.dat` at the first save.

  The consequences run in both directions:
  - **Loss.** Receipts and credited chests written during dedicated hosting vanish with the rest of that inventory.
    If O(T) is still durable, a later dedicated session credits a again, because the owner's file lacks R(T); if
    only Q or nothing remains, a is lost.
  - **Gain.** Suppose the stale tag holds an R(T) and its credited chest from a single-player session between two
    dedicated periods (the host refusal of 4.7 preserved them). If R(T) was pruned and the chest consumed during the
    second dedicated period, the next local opening brings both back: a returns, with every other item of that old
    inventory.
  - **Another player's data.** The integrated owner is whoever opens the world locally, so the stale tag can load
    into a different UUID. The receipt's owner field and the owner rule of 4.6 keep such a receipt from ever
    acknowledging, pruning or standing in for delivery. The items that came with it remain a vanilla duplicate.

  **Repair step.** Before a world that was hosted on a dedicated server is opened in single-player, a validated
  whole-world repair removes the stale `Player` tag from `level.dat`, or replaces it with the intended host's
  current playerdata compound. With the tag null, the host falls back to `playerdata/<uuid>.dat` (F3). Hosting kinds
  must not alternate without this step. The mod cannot perform or enforce it: no merged mechanism observes or edits
  `level.dat` at that point, and adding one would need a new hook. The step is therefore operator guidance in the
  risk text (section 10), not a protocol guarantee. L0R should consider the same rollback for W80 obligations
  (section 11).

## 8. Bounds

| Bound | Value | Source |
|---|---|---|
| Bank | one gas, 0 to 16,000 mB | ADR-066 section 4 |
| Charge (W174-R4) | at most 100 in one C or D, one eligible player, minimum 20 ticks between passes; no catch-up | ADR-066 section 4 |
| Players per pass | one, in UUID order | ADR-066 section 4 |
| Pad passes | at most 32 per tick, through a fair resumable queue | ADR-066 section 4 |
| Source entries (W174-R1) | O plus Q at most 8 per pad; **proposal:** at most 4 per UUID per pad | the ADR; one UUID cannot fill all eight, but one unpaid entry can hold removal/gas switching indefinitely |
| Receipts | at most 8 per player | the ADR, moved from the equipment to the player |
| Pad root | 64 KiB, depth 16, 1,024 nodes, 8 item slots | ADR-066 section 2 |
| Receipt root | **proposal:** 4 KiB, depth 4, 96 nodes | new |
| Registered pads | **proposal:** 4,096 per server | new |
| Receipt upkeep | **proposal:** each online player at most once per 100 ticks, at most 16 checks per tick | new |
| Pad proofs (W174-R2/R3) | at most 1 submission per Level per 20 ticks, 1 unresolved per chunk, 2 unresolved IO tickets server-wide | new, inside unchanged native quota and shared rocket turn policy |
| Pad proof authority (W174-R3) | inclusive 100 ticks from accepted capture; IO retains its ticket after expiry | caller-owned proposal, not a barrier deadline |
| Player readback | W80's ceilings, one scan at a time, a prompt save at most once per 100 ticks | merged |
| Pad scan requests (W174-R3) | **proposal:** 128 identities, one coalesced request per player | new; overflow remains in persisted receipts |
| D admission (W174-R9) | only while pad admission is open; at most one unproved outbox per pad | new |
| Pad admission (W174-R9/R10/R19) | the open predicate of 4.5: no non-pad reservation, no due or active W98, W152 or W158 demand; actual budget Level membership/size check | binds existing due predicates; no quota changes |
| Pad terrain-slot gate (W174-R16) | no capture while any terrain watch remains on that slot; query existing at-most-128-slot monitor, no new index | per-slot hold survives IO completion until final watch release |
| Pad service phase (W174-R10) | once per server tick, `ServerTickEvent` END at `EventPriority.LOWEST` | existing event |
| Barrier Level table (W174-R10) | a pad may add a Level entry only while fewer than 32 of the 64 are in use | new; the table never shrinks |
| Sealed start delay from rule 3 (W174-R11/R17) | drain component `19 + max(R_i)` per idle-to-due edge, R_i includes all residual stages through service; no numeric runtime guarantee | 20/24 are simplified-probe results only; measure total start delta and repeated edges (4.5) |
| Proof liveness with local writes (W174-R18) | no guaranteed quiet interval or completion bound, even with finite IO and fair turns | phase-locked eager saves can fail every attempt (4.2) |
| Receipt fields (W174-R13) | adds the owner UUID; the 4 KiB, depth 4 and 96-node proposal still holds (about 91 nodes at eight receipts) | new |

**W174-R3/R4:** no throughput bound is inferred from average transaction duration. The pass token enforces the
charge ceiling. Quota contention, W80 priority, unavailable players, full receipts, equipment room and IO can make
completion unbounded. **W174-R16/R18:** retained slot watches and repeated superseding saves also make completion
unbounded. The revision-1 150-tick estimate is withdrawn as a service/conservation argument.

## 9. The alternative interaction, for the owner (D4)

The automatic protocol cannot cover the integrated owner. The owner chooses one of these options for single-player
worlds. The owner may also choose O1 for every player, instead of sections 4 to 8.

- **O1: a finite canister exchange (this design's recommendation, not an owner/Root decision).** The pad fills an empty
  canister with 1,000 mB from its bank (the output slot of section 4.8). The player then uses the canister. The
  existing whole-canister refill puts 1,000 units into the active buffer. A new player-local whole-canister refill puts
  exactly 1,000 units into the installed tank of that gas, or into an installed EMPTY tank, when it has room, and
  returns one empty canister. It mirrors `SuitEquipmentService.fillOneCanister` and commits one detached chest.
  - *Conservation claim:* the pad's step is a single-store container operation, and the refill is player-local in one
    file. Moves between them have vanilla torn-save behaviour (ADR-054). There is no cross-store protocol, no proof
    and no prompt save.
  - *Interaction change:* the integrated owner does not charge by standing on the pad, and tank gas arrives in steps
    of 1,000 units.
  - **W174-R5/R6/R7/R8 refinement:** L6 implements the missing player-local editor, not just the already-merged
    active-buffer refill. Select OXYGEN/HYDROGEN by the actual filled canister; use one existing matching tank or
    one EMPTY tank, rejecting duplicate-gas layouts, unsupported roots and less than 1,000 free units. Enchanted
    chests accept only oxygen active-buffer refill. Revalidate connected player, slot/count/tag identity and config
    on the server thread. Prepare the detached chest and inventory exchange, including a guaranteed slot/merge for
    one empty canister; if no room exists, refuse without debit. Do not copy the existing refill's fallback that
    drops the empty item, since that would introduce an item-entity store. Publish without intervening callbacks.
    Test stacked canisters, exact capacity, partial room, changed inventory and full return inventory. Pad filling
    likewise commits its bank and output together. O1 needs no new lifecycle hook, AT, Mixin, receipt or readback,
    but transfers between pad and player still have vanilla container tearing. If automatic feasibility fails at
    implementation/review, stop it and bring this explicit O1-for-all choice to the owner; do not silently enable O2.
- **O2: automatic charging with a stated non-crash-atomic limitation.** For the integrated owner only, the pad debits
  and credits in one tick, as the legacy pad did. A crash between the save of the pad's chunk and the next `level.dat`
  write can duplicate or lose gas charged since the last common save. This is what D4 calls an alternate interaction
  "with a stated non-crash-atomic limitation". It must never be described as crash atomic.
  **W174-R8:** a saved debit with an older host snapshot loses the unmatched charge; a saved host credit with an
  older pad creates it. Exposure is the sum of unmatched charges, not just one 100-unit operation. O2 is not
  recommended or scheduled for implementation without a separate owner-selected leaf and risk review.

Under O1, a single-player world has no other way to fill jetpack hydrogen without the new canister-to-tank refill,
because tanks have no other merged supported survival source (section 2.4); operator imports and third-party mods
are not acquisition evidence. O1 is a genuine alternative, not a closure of automatic pad parity.
**W174-R13:** O1 is player-local, so it has no cross-store obligation to lose, but the vanilla rollback of a
hosting-kind switch (section 7) still rolls back the refilled chest with the rest of the host's inventory.

## 10. Proposed risk-register text, for Root

Root writes the register's wording. This is the content for a new entry after R-042, together with an addition to
R-027's scope for the pad root. Each protection states its scope, duration, log volume and recovery (AGENTS.md section
3.3). **W174-R7:** register this scope and review the design before the first persistence/protection leaf begins,
including L1a with D disabled. All table text below is proposed; this task does not edit or accept the register.

| Protection | Scope | Duration | Log volume | Recovery |
|---|---|---|---|---|
| Removal/gas-switch refusal with O or unproved Q (W174-R1/R3) | One pad, including after unload/reload | Until receipt and positive source acknowledgement complete; indefinite for an absent/ineligible owner, lack of equipment room, or failed IO. **W174-R9/R13/R16/R18:** also while admission stays closed, a terrain watch remains held, local saves keep superseding attempts, W80 priority starves PF, or an entry's owner hosts the world as integrated owner | No per-break log; one WARN per pad/load and reason transition, not per retry; repeated supersessions use the episode rule below | Eligible owner returns, adequate room and IO, released foreign proof and a quiet proof interval; otherwise validated whole-world repair. Never refund/discard an outbox |
| **W174-R9/R10:** pad proof admission closed by other barrier consumers | Every pad of the server: no PS1, PS2 or orphan proof, and no D (at most one unproved outbox per pad remains) | While W98, W152 or W158 demand is due or active on every tick (continuous BUSY contention, sealed work back to back, or at least ten polling entries covering all twenty phases), while any non-pad reservation is outstanding, or, for pads in a Level without a budget entry, while 32 or more of the 64 entries are used. Not bounded by a timer | Localized player reason only; no log per waiting tick | Rocket demand ends or thins out; orderly restart empties the Level table. Obligations stay owed |
| **W174-R16:** retained terrain-watch hold | Pad PS1/PS2/orphan captures in that terrain slot only; existing O/Q removal/gas-switch and receipt holds persist. D can leave at most one unproved outbox per pad; other slots are unaffected by this rule | Until the last watch owner releases, including C6-to-C7 and O2-to-O3/O4 writer waits; potentially indefinite even at zero pending IO. Invalid/ordinary watches are conservatively included | Localized waiting reason, no per-tick/capture-attempt log; no new foreign-writer diagnostic | Let the consumer finish or use its existing abort/repair procedure. Stop denies pad admission before writer drain and final monitor clear. Never force-release a foreign proof to unblock a pad; coherent offline repair if needed |
| **W174-R18:** repeated superseding local saves | Each affected chunk's PS1/PS2/orphan progress; its pads' O/Q removal/gas-switch holds and affected player receipt slots | Potentially indefinite with working storage and fair turns; cooldown is not a quiet-interval guarantee | One WARN per affected pad/receipt-upkeep identity per load/session supersession episode, one recovery INFO. Failed retry/WAITING transitions do not open new episodes; no per-attempt log. Repeated separately resolved episodes over unlimited play have no total cap | Quiesce local writers and retry during an observed quiet interval; if other saves prevent progress, validated coherent offline repair. No generation-check waiver, refund or receipt deletion |
| Quarantine of an undecodable or copied pad root | One pad block | Until compatible decoding or coherent repair; decoding alone cannot fix a copied identity | One WARN per pad per load | Compatible jar for supported schema; whole-world repair for copied/edited identity; preserve raw root |
| Save refusal for an oversized outgoing pad root (W174-R7) | Whole terrain chunk; after 256 distinct denied coordinates, subsequent terrain writes throughout the Level (R-027) | Until Level closure; no online reset; retained dirty state does not prevent unload | Existing EventBus/ChunkMap ERROR pair and stack per native retry, with **no total log cap** | Offline whole-world backup and scoped repair, restart, native saving and conservation checks; do not delete player/source files |
| Refusal of a player whose receipt root is unsupported | That player's automatic charging | Until an offline repair or a compatible jar | One WARN per player, per case, per session | An offline repair of a whole-world copy; the raw root is preserved |
| Refusal of the integrated owner (W174-R6) | That player's automatic charge and receipt reconciliation; retain existing obligations. **W174-R13:** every pad that owes this player stays held | By design while that UUID is host, including a world moved from dedicated hosting | Localized message, no per-tick log | O1 for new supply; reconcile old obligations in supported hosting, after the repair step of the next row, or validated whole-world repair |
| **W174-R13:** vanilla rollback on a hosting-kind switch (not a mod protection; disclosed with this entry) | The host's whole player file: every item, receipts and credited chests included; a stale tag can also load into another UUID | From the first save of a single-player session that loads a stale `level.dat` `Player` tag; permanent once saved | None from the mod; vanilla logs nothing | Before opening a dedicated-hosted world in single-player, a validated whole-world repair removes the stale `Player` tag or replaces it with the host's current playerdata. Do not alternate hosting kinds without it. Afterwards, only a consistent whole-world backup restores coherence |
| **W174-R13:** receipt whose owner differs from its holder | That receipt, and one of the holder's eight receipt slots | Until whole-world repair | One WARN per holder per server session | Validated whole-world repair; never used for A, P or delivery |
| Full entries/receipts or unprovable player file (W174-R3) | New charging at that pad/for that player | Unbounded until reconciliation/repair; restart alone may not help. A receipt whose dimension was deleted, or whose chunk is never loaded again, holds its slot forever; eight of them stop that player's automatic charging everywhere | One WARN per OVERSIZED transition; no per-tick retry log | Healthy IO and supported bounded data, the owner online while the original chunk is loaded, or validated whole-world repair; do not erase receipts |
| Expired but unresolved pad IO (W174-R2/R3) | At most two pad tickets plus inherited native ordinary capacity. **W174-R11:** blocks every sealed attempt server-wide (rule 3). **W174-R10:** also holds the force of any other consumer's capture staged in the same Level batch, at most two batches per server run | Logical authority ends after age 100; IO occupancy lasts to terminal completion/server closure and may recur indefinitely | One WARN per ticket IO failure/expiry, not per waiting tick; repeated completed failures over unlimited play have no total cap. **W174-R18:** `SUPERSEDING_SAVE` instead uses the episode suppression above, not a per-ticket WARN | Restore storage health, orderly restart and re-prove retained entries; never release occupancy to submit unlimited replacement IO |
| **W174-R11/R17:** sealed start delay from pad drain (an availability residual beside R-035's) | A sealed start in any Level at each idle-to-due edge, including reopened 20/200-tick backoffs | No numeric runtime bound even for eventually successful IO. Drain expression `19 + max(R_i)` includes stores, force, queue, readback, owner delivery/service and late dispatch; measured total start delta reduces the unchanged pre-U headroom | No new polling log; existing ticket-failure/expiry diagnostics apply | Resolve drain/service delays and retry a request that expires as `REJECTED`; do not extend its deadline. Stalled storage may require orderly restart/repair as above |
| Shared readback priority (W174-R3) | Pad PF and guarded W80 players | Open scan up to existing time ceiling; W80 keeps 200-tick disconnect; perpetual W80 demand can starve pads | Existing W80 diagnostics and bounded pad transitions; no new poll log | Healthy IO/retry; protected W80 reconnect retains obligations |

**Proposed entry text.** Automatic gas charging writes an outbox in the pad's chunk and a receipt in the player's
`ForgeData.PlayerPersisted`. Each is deleted only after the other is proved durable, through the escrow terrain proof
and W80's player-file readback. **W174-R1/R3/R7:** O becomes an identity-preserving ACK marker, and removal stays
held until positive PS2; even an absent/replaced source needs positive terrain proof before receipt cleanup.
These holds can be indefinite, not merely 100 ticks. Save refusal can affect the whole chunk and then Level and has
unbounded total native retry logs. A
player whose receipt root cannot be read cannot use automatic charging, and the integrated owner never can. After a
credit, the suit chest is an ordinary item with vanilla torn-save behaviour. While receipts await a proof, the server
saves all online players at most once per 100 ticks, a cadence shared with W80, so the cost grows with the number of
online players and the size of their files. Restoring a single file, power loss and charging of the integrated owner
are outside the claim. Missing-player-file replacement holes and third-store movement can lose or duplicate gas;
ordinary pad removal is not such an exception. Recovery is a consistent whole-world backup, never the deletion of a
single file or root. No player-file veto, new AT or Mixin is proposed. **W174-R9 to R13/R16 to R19:** pad proofs yield
to the demand and IO rules above and to unreleased terrain watches in their own slot, even after IO completes.
Rocket demand that is due on every tick therefore pauses all pads on the server, including their D. Retained
terrain authority can hold same-slot pad proofs indefinitely; local-write supersession can do the same despite
fair turns and finite IO. Diagnostics are bounded per wait/supersession episode, and recovery may need quiescing
local writes or coherent offline repair. The Level-table check needs actual membership, not only size. Each
idle-to-due edge can incur the entire pad drain and service latency, with no numeric runtime bound; use the measured
total delay in pre-U headroom, including reopened backoffs. Stalled pad IO stops sealed disassembly server-wide
until the IO ends or the server restarts. Moving
a world from dedicated to single-player hosting without first repairing its `level.dat` `Player` tag rolls back the
host's whole player file, in both directions.

## 11. Implementation leaves

**W174-R7: all leaves below are planned, not implemented or verified.** L0 is the prerequisite: Root records the
ADR-066 protocol changes and section 10 risks, and completes the independent-review/R140 check described in L0 **before implementation of any
persistence/protection leaf**, including L1a, L2 and L3. Before integration, an independent session must review each
persistence/save-refusal diff (L1a, L1b, L2, L3, L4 and L6's inventory mutation); a combined review is allowed only
if none of those diffs was integrated earlier. D disabled is not a waiver. The owner's D4 interaction decision is
required before player-facing release. Each leaf has its own TASK, local commits and WORKER-RULES checks.

The automatic route is L0 -> L2 -> L3, with L1a after L0 and L5 after L1a's IDs; L1b depends on L1a/L2/L3, then L4.
**W174-R15 item 3:** L0R below must be resolved before shared-reader L3 integration. An unresolved L0R therefore
blocks L3 integration, and with it the automatic route; recording it does not unblock it. Admission stays off until L4 and review qualify;
no Gate PASS follows automatically. If the owner chooses O1 for all, L0 -> L1a/L6 -> L5 and their inventory/restart
tests form a separately scoped finite-exchange slice; L1b's automatic protocol is not silently implemented. Sizes
are estimates of main lines, with test lines in brackets, across small classes, not permission for giant classes.

**L0: Root's risk/design prerequisite (W174-R7).** S, one ADR note and risk entry; no code.
- *Scope:* section 10's full chunk/Level/log/recovery effects, Q and player-receipt changes, and owner D4 options.
  **W174-R16 to R19:** include retained-slot and local-write holds, full drain latency/reopened edges and the
  membership seam before implementing the affected policy.
- *Verification:* **R140** uses the third independent review plus Root's check of these four corrections, rather
  than a fourth full review. Root freezes the checked design until D4; L0 registration and AGENTS 3.3 still precede
  any persistence/protection leaf. This revision does not record that check or D4 as completed.

**L0R: W80 host admission restriction, separately assigned by Root (W174-R6).** S, about 80 to 150 lines (about 200).
- *Scope:* the existing W80 residual, not an implemented W174 fix: an offline dedicated-server passenger can later
  open that same world as integrated host. `RocketRejoinGuards.join`/`RocketRejoinPlacement.verify` have no host
  exclusion. Require an actual `isSingleplayerOwner` refusal before placement/readback/VERIFIED publication for
  pending host obligations; invalidate a pending placement lease, retain all durable entries, and use a localized
  protected disconnect rather than crash, delete or falsely acknowledge. No-pending hosts remain unaffected by W80.
  Recovery is opening a consistent world in supported dedicated hosting and completing the obligation, or validated
  whole-world repair. A fresh proof must use the supported store, not a pre-switch callback.
  **W174-R15 item 4:** when the disconnected player is the integrated owner, that disconnect stops the whole
  integrated server (`ServerGamePacketListenerImpl.onDisconnect`, offsets 113 to 136, calls `MinecraftServer.halt`).
  So while a W80 obligation is pending for that UUID, the world cannot be played locally by it at all; Root and the
  owner judge whether that is acceptable. **W174-R13:** L0R should also consider the stale `level.dat` `Player` tag
  rollback of section 7 for W80 obligations, since it can restore a host file from before a placement.
- *Tests:* persisted PENDING created with the UUID offline on dedicated, then the same UUID as integrated owner;
  no P2/entry deletion from a matching playerdata file, retained obligation on restart, and eventual supported-hosting
  recovery. Test the guard seam and an integrated-server reproduction; a dedicated GameTest alone cannot prove it.
  Root owns its ADR/risk registration and independent review before integration. W174's own 4.7 check does not wait
  for or rely on this separate W80 fix. An unresolved L0R must be recorded and never called unreachable, and it
  still blocks L3 integration (W174-R15 item 3).

**L1a: block, block entity and menu.** M, about 600 to 800 lines (about 400).
- *Scope:* the `gas_charge_pad` block, block entity and menu; the pad root codec; the bank; the input and output
  canister slots; the fill-only fluid capability; the source rules of 4.8, including quarantine and the removal
  refusal; registry entries in `ModBlocks`, `ModItems`, `ModBlockEntities` and `ModMenuTypes`; menu operations under
  ADR-066 section 2. D is not implemented here.
- *Tests:* codec bounds and raw preservation; canister and fluid routes (stacks of 16, refusal of partial amounts,
  simulate and execute, a full destination inventory); the removal refusal with an outbox fixture; `onlyOpCanSetNbt`;
  quarantine; a restart that preserves bank, slots and outboxes exactly. **W174-R1/R7:** include Q fixtures,
  removal refusal through PS2 and inherited whole-chunk save refusal; this persistence leaf needs prior L0 and review.

**L1b: the charge coordinator (W174-R1 to R6, R9 to R13, R16 to R19).** L, about 1,300 to 1,800 lines (about 1,700), split
into source state, queue, equipment editor, proof lifecycle and shared admission classes.
- *Scope:* the pad index and fair queue (4.3), eligibility and target order (4.4) with the EMPTY-tank editor, the
  stages D to P, pad-side proof admission (4.5), receipt upkeep and the reconciliation table (4.6), the refusals of
  4.7 and their localized reasons. D's production admission is gated off. **W174-R9 to R12:** the open predicate and
  D's refusal with its localized reason; the per-transaction currency with incarnation tokens and absence
  generations; the `EscrowProofDemand` view in `resourceescrow`, about 100 to 150 of the added lines. Outside the pad
  package, it needs the three read-only demand sources registered by W98, W152 and W158, the **W174-R19** three
  read-only `EscrowBarrier` accessors (reservation count, Level count and membership), the **W174-R16** monitor-backed
  `terrainSlotHeld(level, chunk)` query with its capture gate, and rule 3's one condition in
  `SealedDisassemblyCoordinator.attemptRoom`. Those rocket
  and escrow edits are part of the persistence review of section 11's prerequisites, and every existing rocket and
  escrow suite runs unchanged. **W174-R13:** the owner rule for A, P and orphan handling, and the WARN for a foreign
  receipt.
- *Tests:* unit tests for target order, room, caps, UUID order, cursor fairness, every reconciliation row and every
  refusal, including the integrated owner through a test seam, because a GameTest server cannot be an integrated
  owner; GameTests with real players on a pad, real proofs, the prompt save and the readback, death and logout between
  C and PF, and a chest moved to a container. Add every 4.5 mixed-policy/deadline test and 4.6 orphan proof case,
  four returning outboxes, multiple owners, unload cooldown and capped-index re-admission. Positive built-in and
  enchanted-native chest tests must both pass; provider precedence, unsupported roots and hydrogen-on-enchanted
  refusal must preserve data. A/Q/PS2 and stale callbacks must remain safe through unload/removal/stop.
  **W174-R9 to R11:** the extended mixed-workload matrix of 4.5. **W174-R12:** continuous fluid input and hopper
  input, and a second owner's D, each during PS1, PS2 and an orphan capture: without a native superseding save the
  result for T stays current and is consumed; another entry written during capture waits for its own PS1;
  a replacement or unload still invalidates. **W174-R16 to R19:** all added 4.5 mixed-service, retained C6/O2,
  delayed-writer, release/stop, full-drain and Level-membership cases. Run 4.2's fair ten-pad phase-locked eager-save
  trace for PS1, PS2 and orphan captures, with at least 100 attempts, zero confirmations during supersession,
  unchanged obligations and bounded episode logs; then quiesce input and verify completion in an observed quiet
  interval. Vary tick and wall clocks independently (20 TPS and sustained low TPS); report failures rather than
  assert a success rate. No random retry or generation-check relaxation is a liveness proof.
  **W174-R13:** a receipt whose owner differs from its holder is
  never used and never pruned, and a matching holder still completes A and P.

**L2: the receipt codec.** S, about 150 to 200 lines (about 250).
- *Scope:* the strict schema-1 codec of the receipt root, its bounds, duplicate refusal and raw preservation; a
  detached publish that rewrites only its own key and leaves every other `PlayerPersisted` key, and a foreign parent,
  untouched; the classification used by 4.7.
- *Tests:* every bound at its limit with eight entries and maximum-length dimensions, every malformed shape,
  future schema and foreign parent; exact unrelated sibling preservation and round trips through real save, death
  with/without keepInventory and End clone (W174-R5/R8). **W174-R13:** the owner field at its exact node and byte
  count; a missing or malformed owner refuses the root as section 4.7 does.

**L3: the readback adapter (W174-R3/R6).** M, about 300 to 450 lines (about 400).
- *Scope:* move W80's reader and prompt clock into one server-lifetime service, with W80's requests first (section 6).
  Add a second expectation to the streaming scanner: the UUID and
  `ForgeData.PlayerPersisted.arce_gas_charge_receipts`, returning the set of proved transactions. It reuses the
  existing byte budget, skipping, depth count and change check. W80's behaviour, ceilings, deadlines and tests stay
  unchanged except L0R's separately reviewed host restriction. No new reader thread or offline player writer.
- *Tests:* scanner fixtures (absent, present, duplicated and oversized receipt roots, depth, a foreign parent); the
  shared clock (one `saveAll` per 100 ticks across both users, none during an open scan); W159's overlap GameTest; the
  full W80 rejoin suites, unchanged. Also test W80 arrival during a slow pad scan, retained protected disconnect,
  mixed-consumer fairness boundary, stale result after logout/relogin/clone and host refusal before proof promotion.

**L4: S2 cuts (W174-R1/R3/R8).** L, about 800 lines of Python runner and 400 lines of Java fixture.
- *Scope:* a native runner in the W159 style for the cuts K1 to K8, the three PF failure branches, death, logout, the
  moved-chest boundary and a pad-chunk unload, plus the explicit ordinary-removal/absent-source cuts of section 5,
  Q installation/cleanup, late callback/reload and missing-player-file holes during first, later and prune saves.
  Each case has a seed start and two restarts; externally kill the first recovery where work remains. No graceful
  save may substitute for a cut. Check exact transactions/receipts, unchanged unrelated data and strict full logs.
- *Oracle (W174-R8):* in the coherent-store cases, `initial gas + external inputs = bank + actual gas in all
  inspected stores + unfulfilled obligations + explicit consumption/destruction sinks`. Pair O(T) with the exact
  transaction/revision, pad, owner, gas and amount, not just receipt count. An O with a matching installed R is
  delivered and contributes zero obligation; Q also contributes zero. An unpaid O contributes a once. At K2 the
  sum is `(B-a) + 0 + a`; at K4 it is `(B-a) + a + 0`, not `(B-a) + a + a`. Maintain separate physical-gas totals,
  credit-event history and receipt state; history is not another asset. A persisted sink after credit does not make
  O repayable. For moved-chest/death third-store cuts assert the documented +a/-a outcome (and complete chest
  contents) rather than pretending exact conservation; missing-file rows assert actual file loss and whether an
  obligation still exists. Ordinary pad breaking has its declared bank-gas sink but no second charge.
- *Execution plan:* enumerate these cases and obtain Root's native-start allocation before running them. The old
  16-case/48-start and 0.78-hour estimate is withdrawn; its 60-start proposal is not silently enlarged or used to
  omit new cases. No native run is authorized/executed by this design task. Keep all existing runtime quotas and
  deadlines, machine lock/resource checks and owned cleanup; report incomplete rows if the eventual allocation ends.
- **W174-R13:** the dedicated-to-single-player switch needs an integrated client, so it is not a case of this
  dedicated runner. A separate integrated reproduction, beside L0R's, records the rollback in both directions and
  shows that the repair step of section 7 prevents it.

**L5: DataGen art and data.** S, about 150 to 250 lines.
- *Scope:* NEW original art from a v1.8 provider; the asset row `textures/blocks/gaschargertop.png` stays REVIEW,
  and no AR 16x file is imported, following W172's recommendation on owner question D3. Also the blockstate, block and
  item models, loot table, mineable and tool tags, and a recipe as a reviewed proposal for Root. English and Chinese
  text covers the name, the menu, every reason of 4.7 and **(W174-R9/R16/R18)** the admission, retained-slot and
  repeated-supersession pauses of 4.5/10. Output goes to
  `src/generated/v1.8`.
- *Tests:* language completeness, the resource graph and provenance screening.

**L6: O1 player-local whole-canister refill (W174-R5/R6/R7).** S to M, about 200 to 300 lines (about 300).
- *Dependencies/scope:* owner's O1 decision and L0; use the equipment editor contracts of 4.4/9, not automatic
  receipts/readback. Delivers installed oxygen/hydrogen/EMPTY-tank filling and guaranteed empty-canister return.
- *Tests:* section 9's whole-amount/return-space/precedence cases, unchanged roots on refusal, same-player save/load,
  integrated-host player-local save/restart, bank/output restart and separately documented container-transfer cuts.
  No claim that these tests close the automatic S2 gate.

The automatic main-code leaves L1a/L1b/L2/L3/L5 total about 2,500 to 3,500 lines, plus L4 test infrastructure
(revision 3: 2,450 to 3,400; **W174-R16 to R19** add about 50 to 100 lines and 300 test lines to L1b). L0R and owner-selected L6 are
additional scoped work, not hidden inside the earlier W172 estimate.

## 12. Revision-1 session record (historical)

**Revision-2 correction:** the results below describe revision 1 only. Review W174-R1/R2 blocked its conclusion;
sections 4 to 11 above supersede the old protocol, admission, timing, risk ordering and accounting claims.

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
- The proposed caps are estimates, not measurements; the 150-tick service estimate is withdrawn by W174-R3/R4.
- The claim that a busy Level slows pads but not rockets is superseded by W174-R2 and section 4.5.
- The W80 host-unreachability claim is withdrawn by W174-R6: dedicated-to-integrated world transfer reaches it.
  Section 11's L0R is a required separately assigned enforcement/regression, not a proven pre-existing guard.

**All current-version Required Gates satisfied: no.** This is a design note. The next v1.8 step is the fresh
read-only review of this note, then Root's ADR note and risk entry and the owner's D4 choice, then leaves L2 and L3
under revision 2's section 11 prerequisites.

## 13. Revision-2 execution record (R134)

**Revision-3 correction:** this record describes revision 2. Findings W174-R9 to R15 changed sections 1 to 11
afterwards (section 14); the only edits inside this record are the two W174-R14 corrections marked below.

**Completed scope.** Revised this design only, on clean branch `codex/v1.8.0-w174` from
`f9dbf78e5fdb1fe16f9ac6825b8e805074bb377e`. The task, complete independent review, both named review probes,
W80/W157-W160 records and relevant pinned source were inspected. The roadmap acceptance cursor remains v1.0;
v1.8 development is under ADR-060 and its G0-G9 gates remain incomplete. No other worktree was changed, no agent
was launched, and no merge/push/tag or Gate approval was made. Other active implementers target separate trees.

| Finding | Revision and evidence boundary |
|---|---|
| W174-R1 | Sections 4.1/4.2/4.6/4.8 and 5 retain Q until PS2, require positive orphan proof and reject stale incarnations; native cuts expanded. Executable examples pass, not S2 qualification. |
| W174-R2 | Section 4.5 uses shared turn/reservation admission and yields full sealed windows. **W174-R14 (revision-3 correction):** the 20-phase quota probe is only a construction check: its pad captures only after the rocket already holds all 21 captures, and both release in the same tick, so trace equality holds by construction. It cannot show a pad capture made before demand, IO drain, pads in other Levels, or W158 and governed consumers. The 11 turn assertions exercise merged classes only. The second review's `SealedPadProbe`, which captures pads 1 to 40 ticks before demand (rerun in revision 3), is the closer model, and the implementation tests of section 4.5 are the actual evidence. Adapter wiring/full mixed fairness is future implementation. |
| W174-R3 | Section 4.5 owns the inclusive deadline, invalidates authority but retains IO tickets; sections 6/10 state unbounded failure duration and reader latency. Delayed/never-completion examples pass. |
| W174-R4 | Sections 4.3/8 budget one D or C and one UUID per pass; returning-obligation and cyclic-owner examples pass. |
| W174-R5 | Section 4.4 has distinct built-in/enchanted predicates; positive equipment tests are specified in L1b, not executed here. |
| W174-R6 | Sections 4.7/11 require the actual host predicate and scope the pre-existing W80 host residual to L0R; enforcement code/integrated reproduction remains unimplemented. |
| W174-R7 | L0 precedes every persistence/protection implementation; independent review precedes integration, explicitly including L1a. Root has not registered/accepted this note's risks. |
| W174-R8 | L4 counts only unpaid obligations, separates physical gas/history/sinks and tests both tearing directions and later missing-file holes. No native cuts were run. |

**Key decisions.** Acknowledgement phase shares the eight source-entry slots and never credits/refunds gas.
Conservative idle-only pad admission preserves existing rocket turns rather than promising pad liveness under
perpetual rocket demand. No new lifecycle hook, AT or Mixin is identified as necessary for this revised proposal;
the integrated host still cannot use this automatic proof. O1 has a concrete player-local refill leaf and guaranteed
empty-canister return. The owner has not chosen O1/O2 or accepted the weaker cross-store boundary.

**Files and tests.** Only this repository record changed. External revision-2 files are small Python/Java probes
and logs, not production tests or a sealed release package. The Python model abstracts successful native proofs and
one transaction; the quota probe compiles the real unchanged budget and limits with only scalar dependencies
stubbed. The turn probe compiles the real unchanged admission/turn classes with the two retirement constants pinned
from source. These cannot prove Forge lifecycle, native durability, equipment correctness or full scheduler fairness.

**Actual commands and results so far.** Evidence prefix below is
`D:/GitHub/ARCE-Task-Evidence/root-queue/W174/revision-2/`.

| Command/check | Result |
|---|---|
| `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git worktree list`; `Get-CimInstance Win32_Process` | Clean assigned base; other sessions observed, not stopped or delegated to. |
| `Get-Content`/`rg` on task, review, records and cited sources; `Get-FileHash -Algorithm SHA256` | Review and W172 hashes match the input table. Initial short version filename/glob lookups failed and were corrected to actual filenames; not verification passes. |
| `python -B D:/GitHub/ARCE-Task-Evidence/root-queue/W174-review/probes/design_model_checks.py` | Exit 0, 6 tests pass, including revision-1 counterexamples; `review-model-rerun.log`. Original review evidence unchanged. |
| `python -B <evidence>/design_model_checks.py` | Exit 0, 15 revision-2 examples pass; `revision-2-model.log`. |
| JDK 17.0.7 `javac -d <evidence>/classes <two probes> <EscrowBarrierBudget.java> <EscrowLimits.java> <SealedProofAdmission.java> <SealedProofTurns.java>` | Exit 0; `javac.log`. `javac` was absent from PATH, so the pinned absolute JDK path was used. |
| JDK 17.0.7 `java -cp <evidence>/classes io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BudgetPolicyProbe` | Exit 0; 20 phases retain the 21-capture trace, plus original counterexample and cross-Level capture-tick assertions; `budget-policy.log`. **W174-R14:** a construction check only (see the W174-R2 row above). |
| JDK 17.0.7 `java -cp <evidence>/classes io.github.sunthemoon.advancedrocketrycommunity.rocket.server.TurnsProbe` | Exit 0; 11 checks pass; `turns.log`. |
| `git diff --check` | Exit 0 for the revised note. Two earlier patch context mismatches made no edits and were corrected. |
| `python -B scripts/validate_repository.py --require-approved-identity` before commit | Exit 0: 45 passed, 0 pending, 0 warnings, 0 failed; `validator-precommit.log`. |
| Verified-path PowerShell cleanup of this session's `<evidence>/classes` | Tool policy rejected the command before launch. No alternate deletion attempted; 10 class files / 18,209 bytes remain for Root. The chained validator did not run in that rejected call and was run separately. |
| OpenPet `thinking` event | Runtime unreachable; continued without app-data edits. |

No Gradle, GameTest, native server, full regression, GPU or two-client execution is needed/claimed for TASK-W174's
design-only verification. No heavy-JVM lock, source/world copy or archive was created.

**Probe source identities.** All are under the evidence prefix above; the retained class directory is a temporary
compile output, not an acceptance dependency. Its exact cleanup path is
`D:/GitHub/ARCE-Task-Evidence/root-queue/W174/revision-2/classes/`.

| Artifact | SHA-256 |
|---|---|
| `design_model_checks.py` | `f2b79dec13b6c19dd105f4341732df74599478348b19ce58501e2fabaa71c0d3` |
| `BudgetPolicyProbe.java` | `5680532bab51627bebc3efc2d79346f207dccc0aafb2f65e7ad8edda3fc88d3d` |
| `TurnsProbe.java` | `36dd298100382ab23aaa6e84f2ac1e5a073e704c4a971352bdbb9dbbbf6fcf76` |

**Not completed / known risks.** Fresh independent review, Root's ADR/risk entry, L0R's W80 host restriction,
owner D4 choice, all production leaves and their native/real-client validation remain. Ordinary acknowledgement
cleanup is intended to be safe, but only model probes were run. Unbounded IO/player-absence holds, pad starvation,
shared-reader login latency, vanilla third-store tearing, missing-player-file holes and partial-restore/power-loss
exclusions are explicit; no risk is accepted here.

**All current-version Required Gates satisfied: no.** Recommend only the fresh v1.8 W174 revision-2 review, then
Root's L0/L0R and owner decision before the applicable implementation leaves.

### Committed-source verification

Revision-2 design commit: `7e7a153087a8733e3d716e2809a771042a551f95`. On that clean record commit,
`python -B scripts/validate_repository.py --require-approved-identity` exited 0: **45 passed, 0 pending,
0 warnings, 0 failed**. Evidence is `validator-7e7a15308.log`, `validator-7e7a15308-source.txt` and
`validator-7e7a15308-exit.txt` under the revision-2 evidence prefix. `git diff --exit-code`,
`git diff --cached --exit-code` and `git show --format= --check HEAD` also exited 0. The source/scripts/tests/build
diff from revision 1 is empty. The staged-stat check contained this one record only; the commit was local, not pushed.

This final verification addition changes no design or probe. The validator is repeated on its final record commit;
`validator-final-source.txt`, `validator-final.log` and `validator-final-exit.txt` identify that run. Exact probe
commands are in `COMMANDS.md` (SHA-256 `340a64651ec2d850d8691c22443627c2c42149878fc7ecbb879a49430349b1ed`).
The revision-2 evidence directory is below 100 KB, including the 18,209-byte refused-cleanup remainder; it contains
no server/world copy or archive. The required fresh independent design review and all version gates remain open.

## 14. Revision-3 execution record (R136)

**Revision-4 correction (W174-R16 to R19):** this historical record is superseded for retained-proof interference,
the incomplete readback-only bound, the eager-save retry/no-livelock claim and Level-membership accessors by
sections 4, 8, 10 and 11 above. Its probe results remain unchanged simplified-model evidence, not runtime guarantees.

**Completed scope.** A fresh Claude implementer session revised this design only, on the clean branch
`codex/v1.8.0-w174` from `cf656dfe81d5228e22e1ee0d6d22e860731e8324`. It read the whole TASK, WORKER-RULES, both
reviews, revisions 1 and 2 of this note, ADR-065's "Proof admission" paragraph, and the merged source and bytecode
that the findings cite. No other worktree or the shared checkout was changed, no agent was started, and nothing was
pushed, merged or tagged.

| Finding | Revision 3 | Evidence boundary |
|---|---|---|
| W174-R9 (High) | Option (a): pad admission binds to the existing due predicates of W98, W152 and W158 plus every non-pad reservation (4.5). D needs admission open and at most one unproved outbox per pad; the player sees a localized reason (4.2, 4.5, L5). The remaining server-wide stall is registered (10). | Design and source reading; the long-waiting retirement test is specified for L1b, not run. |
| W174-R10 | Every production barrier consumer listed, with source lines (4.5). W98, W152 and W158 are included; governed in-flight work is included and the rest bounded: one tick per governed capture, budget shares, batch coupling. A new Level-table bound (fewer than 32 of 64 entries) and the END/`LOWEST` service phase (4.3). One-way seam `EscrowProofDemand` in `resourceescrow`. | Source reading; mixed tests with W158 and governed captures specified for L1b. |
| W174-R11 | Healthy-IO bound 19 + R ticks once per idle-to-demand edge; ADR-065 hand-off bounds unchanged; pre-U retry headroom shrinks by the delay; the own-Level refinement is analysed and not adopted; stalled IO registered as server-wide (4.5, 8, 10). | The second review's model rerun unchanged gives identical output (20 and 24 ticks, 21 submissions). The pre-U arithmetic is an estimate. |
| W174-R12 | Revision 2's whole-root "state generation" withdrawn. Currency per transaction entry, incarnation and absence generation (4.1, 4.2, 4.6, 4.8). Superseding eager saves under continuous input quantified as a retry cost from new fact F9. | Bytecode read for F9; the one-in-eight-to-ten rate is an estimate; tests specified for L1b. |
| W174-R13 | The dedicated-to-single-player rollback stated in both directions with its bytecode chain and the whole-world repair step (7, 4.7, 10). Receipts carry the owner UUID; A, P and orphan handling require the holder to match (4.1, 4.2, 4.6). L0R asked to consider W80 (11). | Offsets rechecked in the second review's dumps; no integrated reproduction run (L4 note). |
| W174-R14 | Section 13 now calls revision 2's quota probe a construction check and points to `SealedPadProbe` and the L1b tests. | Wording. |
| W174-R15 | F3 fallback wording; the `suitOxygenTarget` ceiling for both native chests; an unresolved L0R blocks L3; the owner's protected disconnect halts the integrated server. | Wording, with `SuitEquipmentService.java:119` and `onDisconnect` 113-136 checked. |

**Key decisions.**

- Option (a) rather than (b) for W174-R9, because (b) lets one offline crew member stop every pad on the server.
- Rule 3 stays server-wide, because cross-Level pad IO still shares `ORDINARY_PENDING`, bytes and the Level table.
- D needs admission open and allows at most one unproved outbox per pad. This throttles a single player's charge
  rate below the ADR's 100-per-20-ticks ceiling while PS1 is outstanding; the ceiling remains a ceiling, not a
  promise (8).
- The hosting-switch repair is operator guidance, not a protocol step, because the mod has no merged way to observe
  or edit `level.dat` at that point.
- No new lifecycle hook, Access Transformer or Mixin is identified as necessary, so the TASK's stop condition is not
  triggered. The design does need read-only `EscrowBarrier` accessors, three read-only demand sources in rocket code
  and one added condition in the sealed coordinator's `attemptRoom`; L1b owns them under review.

**Files and tests.** Only this record changed in the repository. The revision-3 evidence holds a `ChunkMap`
bytecode dump, the unchanged copies of the second review's two probe sources, their rerun log, an empty `javac.log`,
`COMMANDS.md` and the validator logs. No repository test was added or changed.

**Commands and results.** Evidence prefix `D:/GitHub/ARCE-Task-Evidence/root-queue/W174/revision-3/`; exact
commands are in its `COMMANDS.md`.

| Command or check | Result |
|---|---|
| `git status`, `git log`, `git worktree list`; `git diff --stat 171c1d71d cf656dfe8 -- src scripts tests build.gradle` | Clean at `cf656dfe8`; the source diff is empty, so section 2's `J` citations hold. |
| `sha256sum` of both reviews | Match the TASK and the input table. |
| JDK 17.0.7 `javap -c -p` of `net.minecraft.server.level.ChunkMap` on the pinned jar | Exit 0; jar hash matches; fact F9. |
| JDK 17.0.7 `javac` of the second review's `SealedPadProbe` and `AdmissionBridge` with the unchanged `EscrowBarrierBudget`, `EscrowLimits` and `SealedProofAdmission`; `java ... SealedPadProbe` | Both exit 0; the log is identical to the second review's after line-ending normalization. |
| `rm -rf` of this session's `classes/` (11 files, 19,221 bytes) | Removed. |
| `git diff --check`; `git diff --cached --stat`; `git ls-files --eol` | Exit 0; this note is the only staged file; LF endings. |
| `python -B scripts/validate_repository.py --require-approved-identity`, with this note staged | Exit 0: 45 passed, 0 pending, 0 warnings, 0 failed; `validator-precommit.log`. It ran again after this row was filled in, with the same result (`validator-precommit2.log`). |

No Gradle, GameTest, native server, heavy-JVM lock, world copy or archive was used or created.

| Artifact under the evidence prefix | SHA-256 |
|---|---|
| `bytecode/net.minecraft.server.level.ChunkMap.txt` | `50f2fc78f90ba3f50ba4dbcc62098ae93da98acfbbfd3008b48ee28d88b2df57` |
| `sealed-pad-probe-rerun.log` | `08f5290253c3252f1f1a13d114f8207aabf3b3f1c58c72dfe1593c7b660a221c` |
| `rerun-sources/SealedPadProbe.java` | `67eb6cf1df211fc692193fad615fddabb19e67e92ea910085ea29fba63c3c194` |
| `rerun-sources/AdmissionBridge.java` | `05068c2e550c483e421d0433e2822da47850cdc948601503d11f1c0381ae0ae8` |
| `COMMANDS.md` | `aa66b0f2f513087ade6d687acb897e46873134f1bfbd1922d47329e231849d6f` |

**Not completed.** The fresh read-only review of revision 3, Root's ADR-066 note and risk entries (L0), L0R, the
owner's D4 choice, every implementation leaf, and all native, integrated and real-client validation. The design-level
claims of 4.5 (one lost tick per governed capture, the W98 window arithmetic, the eager-save retry rate and the pre-U
headroom) are derived from source reading and the rerun model, not from runs of the real services.

**Known risks.** Pads can stall server-wide under dense rocket demand; stalled pad IO blocks sealed disassembly
server-wide and can hold two foreign batches; the drain wait delays the first sealed attempt after idle time; the
hosting-kind rollback depends on an operator repair step the mod cannot enforce. All are registered as proposals in
section 10, and none is accepted here.

**All current-version Required Gates satisfied: no.** This is a design note. The next v1.8 step is the fresh
read-only review of revision 3, then Root's L0 and L0R and the owner's D4 choice before any implementation leaf.

### Committed-source verification (revision 3)

Revision-3 design commit: `160ffc59aee271734456463ea80eb76dcb1b882c`. On that clean record commit,
`python -B scripts/validate_repository.py --require-approved-identity` exited 0: **45 passed, 0 pending,
0 warnings, 0 failed**. The evidence is `validator-160ffc59a.log`, `validator-160ffc59a-source.txt` and
`validator-160ffc59a-exit.txt` under the revision-3 evidence prefix. `git diff --exit-code`,
`git diff --cached --exit-code` and `git show --format= --check HEAD` also exited 0. The commit changes this one
record only, and it is local, not pushed.

This addition changes no design or probe. The validator is repeated on the final record commit; its
`validator-final-source.txt`, `validator-final.log` and `validator-final-exit.txt` identify that run. The
revision-3 evidence directory stays below 300 KB, most of it the `ChunkMap` dump and the validator logs, and holds
no server or world copy, archive or compiled class.

## 15. Revision-4 execution record (R140)

**Completed scope.** This fresh Codex implementer session started clean at
`2b155bd57fce50449e551a6405bc3d681bdc7109` on `codex/v1.8.0-w174`. It read the task, this design, the complete
third review and its probes, and checked the cited monitor, budget, barrier, consumer and stop lifetimes in the
unchanged source. Only this record changed in Git. Other worktrees and active agents were inspected, not modified,
stopped or delegated to. No push, merge, tag, ADR/risk acceptance, ledger or status change was made.

| Finding | Revision-4 design change | Verification boundary |
|---|---|---|
| W174-R16 | Section 4.5's same-slot gate excludes every unreleased terrain watch, including retained C6/O2 proofs after IO completes. Ownership, split release, invalid/ordinary watches, unload and stop are explicit; sections 8/10 register the conservative local hold. | Unchanged monitor/budget probe reproduces the old counterexample and checks the proposed read-only predicate. Actual C6/C7/O2/O3/O4 writer-delay tests are specified for L1b, not executed. |
| W174-R17 | Section 4.5 includes stores, force, reader queue, readback, owner delivery/service and dispatch lateness; use latest of two tickets and measured total delta, including reopened backoffs, for pre-U headroom. Sections 8/10 withdraw a numeric runtime guarantee. | Five timing-model tests include 1,152 residual-term combinations and 24/26/46/55-tick examples. The unchanged 25,600-case sealed probe still reports 21 submissions; it omits the extra runtime stages. |
| W174-R18 | Section 4.2 withdraws the success-rate/no-livelock claim; local-save phase locking can hold PS1/PS2/orphans indefinitely. Sections 10/11 add episode-bounded diagnostics, quiescing/repair and periodic/low-TPS tests. | The review's timing trace reruns unchanged. New models cover 100 fair attempts for each capture route with zero confirmations, plus tick/wall-clock separation and a quiet-interval control. Not a native liveness proof. |
| W174-R19 | Section 4.5 and L1b expose budget membership as well as count. Existing Levels remain eligible at 32/33; only new Levels need count below 32. | Real budget entries survive reservation release; query controls test 31/32/33 and a fresh budget without inserting entries. Production accessor remains unimplemented. |

**Key decisions.** A read-only query of the existing at-most-128-slot monitor is simpler than publishing every
consumer's proof-dependent lifetime into a new index. The conservative hold includes invalid and ordinary terrain
watches until release, but never blocks another slot by itself. It does not change native watch/generation checks,
budgets or deadlines. Finite successful IO and fair attempts are not completion guarantees. The protocol, eight-entry
bounds, 16,000 mB bank and 100-unit/20-tick ceiling remain unchanged. No new lifecycle hook, AT or Mixin is needed by
these proposed corrections; D4 and the automatic route remain unapproved.

**Files and tests.** One repository file: this design/record. External probes and logs are under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W174/revision-4/` (the prefix below). No production or repository test
changed. The monitor probe compiles the unchanged classes with inert external-type stubs; reflection supplies only
the proposed read-only query in that probe, not in the production design. Its C6/O2 labels denote held-watch roles,
not execution of those consumers. It does not exercise split-proof or writer/stop orchestration. Python timing
examples are arithmetic/schedule models, not service or native executions. L1b retains those implementation tests.

| Actual command/check | Result |
|---|---|
| `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git worktree list`; `Get-CimInstance Win32_Process` | Clean assigned branch/base; other active implementers target separate trees. |
| `Get-Content`, `rg`, `Get-FileHash -Algorithm SHA256` on the task/review/source | Third-review hash matches the input table. Initial short version filename and PowerShell wildcard `rg` arguments failed; corrected to actual filenames and `-g` filters. An obsolete ADR-064 filename lookup was likewise corrected; these were lookup errors, not verification passes. |
| `git diff --exit-code 171c1d71d..HEAD -- src scripts tests build.gradle` | Exit 0; production baseline unchanged. |
| `& <prefix>/run_checks.ps1` | Exit 0. Exact paths/commands and input hashes in the script and `CHECK-COMMANDS.txt`. Results below. |
| `python -I -B <review3>/probes/design_timing_checks.py` | Exit 0; original due/drain/phase-lock examples reproduced in `review3-timing-rerun.log`. |
| `python -I -B <prefix>/probes/run_monitor_probe.py` (JDK 17.0.7 `javac` and `java`) | Both compile/run exit 0; original R16 counterexample, six held-route checks, slot/release controls and Level membership checks pass; `monitor-probe.log`. |
| `python -I -B <prefix>/probes/revision4_timing.py` | Exit 0: 5 tests pass, including 1,152 combinations; `revision4-timing.log`. |
| JDK 17.0.7 `javac`/`java` of unchanged review-2 `SealedPadProbe`/`AdmissionBridge` with budget/limits/admission | Exit 0; output identical to review 3 after newline normalization; `sealed-pad-probe.log`. |
| `python -I -B <review2>/probes/rev2_model_check.py 24` | Exit 0; historical revision-1 counterexample retained, revised 60/87-state checks pass; `rev2-model-check-d24.log`. |
| `python -B scripts/validate_repository.py --require-approved-identity` before commit | Exit 0: 45 passed, 0 pending, 0 warnings, 0 failed; `validator-precommit.log`. Committed-source run follows below. |
| `git diff --check` | Exit 0. |
| Verified-path PowerShell `Remove-Item` of this session's probe output only | Tool policy rejected the command before execution. No alternative deletion attempted. Remainders listed below for Root. |
| OpenPet `thinking` | Runtime unreachable; continued without editing app data. |

| Artifact under the prefix | SHA-256 |
|---|---|
| `probes/run_monitor_probe.py` | `68f72748b2913268fafe0d419e4f473d97d4748a33e69a819f222acad9d8b4c7` |
| `probes/revision4_timing.py` | `3bf817ec6b232b8ce7762183d3cf11bc5126bf4cc11fd18417276af69ca07241` |
| `run_checks.ps1` | `4c9d78901c2aa14fec30764da160192a86b3420dbd4acf5227b5c56f4ee686d0` |
| `CHECK-COMMANDS.txt` | `a7ef8ef105a760faa67fe369b4dbaee1b98e2614919ee2774840af124386c956` |

**Cleanup remainder.** Under the revision-4 prefix only: `monitor-classes/` (25 files, 38,573 bytes),
`monitor-generated/` (8 files, 8,736 bytes) and `sealed-classes/` (11 files, 19,221 bytes). These are disposable
probe classes/inert stubs, not acceptance dependencies. No server/world copy, export tree or archive was created.
No Gradle, GameTest, native server or heavy-JVM lock was run; the task explicitly requests design-only validation.
Prior revision and sealed-review evidence remains unchanged.

**Unfinished scope / known risks.** Root's R140 check and subsequent freeze, the owner's D4 choice, L0 risk/ADR
registration, L0R and all production, actual mixed-service, S2 and real-client leaves remain. The scoped watch and
local-save holds, full drain latency and backoff edges are disclosed proposals, not accepted risks. Inherited
server-wide IO/demand holds, W80 priority, unsupported host and vanilla tearing/rollback exclusions remain.

**All current-version Required Gates satisfied: no.** This revision establishes no G0-G9 result. The next v1.8
action is Root's four-finding check against review 3, then freeze until D4 as R140 requests, not a fourth full review
or automatic implementation/admission.
