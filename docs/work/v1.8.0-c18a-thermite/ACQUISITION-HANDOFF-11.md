# Ordinary thermite: survival-menu fixture correction 11 handoff

Delegated Claude implementer, 2026-10-08, base
f535a130c536be95d0e46d5c9ccfea81d1b0ef31. Status: READY_FOR_REVIEW candidate
only. The new code was not compiled or run. This supersedes
ACQUISITION-HANDOFF-10.md, which stays unchanged, for fixture lifetime and for
the API attributions below. Input: the sealed independent
[REPORT-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18a-thermite-acquisition10-independent-20261008-01/reviewer-01/REPORT-01.md)
(SHA-256 c0a3e47929e5a7beb2b7ef176874fd01bb01d727c536664ca4d981e6df9a70cb),
Low 1 and Low 2, and its API-LIFECYCLE-01.md.

## Changes

Only `gametest/ThermiteAcquisitionGameTests.java` changed. Both tests now use
the AutoCloseable `SurvivalFixture` in try-with-resources, with `helper.succeed()`
after the block. The test bodies, recipe-book checks, four wrong inputs,
exact inventory, cursor and grid checks, the template and both 20-tick limits
are unchanged. Nothing consumes inputs or awards outputs manually.

- Ownership: on the server thread, the fixture first draws a random UUID. It then
  asserts that `PlayerList.getPlayer(id)` is null and that neither cache has the
  UUID. After construction, it asserts that the cache entries are the same objects
  as `player.getStats()` and `player.getAdvancements()`.
- Setup protection: after construction, any setup failure releases the entries
  and rethrows the setup failure, with cleanup failures added as suppressed.
- Release: every step is attempted, in this order. Clear the cursor, the
  crafting grid and the inventory. Call `stopListening()`. Remove each cache
  entry with `Map.remove(id, entry)` only if the UUID never joined and the entry
  is the player's own object. If the constructor itself threw, an entry is
  removed only because this UUID was absent just before construction. Step
  failures combine into one IllegalStateException. Try-with-resources keeps a
  body failure primary. A failure during release alone fails the test.
- No global cache clearing, `PlayerList.remove`, logout event, save,
  player-data write, production change or static state.

## Evidence-backed API assumptions (Forge 1.20.1-47.4.10 mapped official)

- The `ServerPlayer` constructor inserts entries through `getPlayerStats` and
  `getPlayerAdvancements` (API-ServerPlayer-01.txt L222-232). Each getter puts a
  missing UUID entry (API-PlayerList-01.txt L2201-2292). `PlayerAdvancements`
  keeps a reference to the player (API-PlayerAdvancements-01.txt L23, L65-67).
- Only `PlayerList.remove` deletes these entries, after the logout event and
  `save`, and only if `playersByUUID.get(uuid) == player` (L776-852). Public
  `getPlayer(UUID)` reads `playersByUUID` (L2364-2371). Hence the test-only
  reflection: it finds the single private final instance
  `Map<UUID, ServerStatsCounter>` and the single `Map<UUID, PlayerAdvancements>`
  field by declared generic shape (L33-35), not by name. Any other count fails
  before a player exists.
- `stopListening()` only removes trigger listeners (L80-96).
- Low 2 correction: handoff 10 said that `Slot.tryRemove` refuses a mismatched
  cursor. Wrong. In `AbstractContainerMenu.doClick`, when the cursor holds an item,
  `Slot.mayPlace(carried)` comes first (API-AbstractContainerMenu-01.txt L1184-1187).
  `ResultSlot.mayPlace` returns false (API-ResultSlot-01.txt L25-28). Then
  `ItemStack.isSameItemSameTags(slotItem, carried)` at offset 1038 is false, so
  the click skips `tryRemove` at offset 1064 (L1220-1233).
  `tryRemove(int, int, Player)` never sees the cursor (API-Slot-01.txt L188-229).
  An empty-cursor take does reach `tryRemove` (offset 925, L1175).
- Not re-inspected here; REPORT-01 found them independently: grid changes go
  from `InventoryMenu` to the `CraftingMenu` recipe lookup; `ResultSlot.onTake`
  consumes one item per filled slot and calls `RecipeHolder.awardUsedRecipes`;
  QUICK_MOVE from the grid fills slots 9-44 in order. Forge
  `PlayerAdvancements.award` returns false for a FakePlayer, so the recipe-book
  checks show only the used-recipe award, not advancement rewards.
- Handoff 10's "one PlayerAdvancements map entry per run" is corrected: each
  player adds one stats and one advancement entry.

## Unrun verification

Not run: compilation, `./gradlew clean build`/`test`, `runData` with no diff,
`runGameTestServer`, strict repository and provenance checks, and independent
review of the actual diff. No cleanup fault was injected. Release is not proven
at runtime. S1, restart, C2S and real-client paths, and V1/V2 are out of scope.

## Ownership and residual risks

- `setAccessible` across the Forge game-layer module boundary is unverified. If
  it fails, the failure happens before any player is constructed.
- Shape matching depends on the generic signatures visible in the mapped dev jar.
  GameTests run only in that environment.
- Not owned or released: the constructor's spawn-position search, Forge's
  FakePlayer network handler and dummy connection (not inspected).
- Other existing GameTests that create FakePlayers are unchanged.
- No acceptance, Gate, R-021, ledger or release conclusion.

## Tools and release

All 17 required live-main files were read completely before writing, plus the
candidate Java, handoff 10, RUNTIME-CONTRACT-04, ADOPTION-04, REPORT-01,
API-LIFECYCLE-01 and the API files cited. Tools: Read, Glob, Grep, Edit, Write
only. All interests are released; Root owns Git, compilation, tests and review.
