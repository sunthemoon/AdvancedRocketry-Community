# Ordinary thermite: survival crafting supplement 10 handoff

Delegated Claude implementer, 2026-10-08, base 230afbf0f3e27675d23ba98f1de0b560ccdff306.
Status: READY_FOR_REVIEW candidate only; uncompiled and unrun.

## Scope

Complete: NEW `gametest/ThermiteAcquisitionGameTests.java`, batch
`thermite_acquisition`, template `empty`, unchanged 20-tick limits.

- Success: survival FakePlayer, own `InventoryMenu`. Marked dust is moved by
  PICKUP clicks into the 2x2 grid. Checks: result slot and `ResultContainer`
  recipe ID `thermite`; PICKUP take; cursor one untagged thermite; one of each
  input consumed; marked aluminum leftover intact; recipe book award. Leftover
  shift-clicked home; thermite and one marked stick placed by clicks; result
  `thermite_torch` x4 taken; both inputs consumed; final inventory counts.
- Failure: Al+Al, Al+Fe+stick, thermite+Fe, stick alone. No result; PICKUP
  left/right and QUICK_MOVE take nothing; marked grid exactly preserved. Valid
  Al+Fe with a different cursor stack: take refused, inputs/cursor/result
  unchanged, no recipe awarded.

Incomplete or not claimed: the C2S `handleContainerClick` path (FakePlayer
handler ignores it; `ConnectedTestPlayers` forces creative); crafting-table
`CraftingMenu`; real client, pickup entity, S1, restart, V1/V2.

## Method/API assumptions (unverified by compilation)

Vanilla/Forge 1.20.1: `InventoryMenu.slotsChanged` -> `CraftingMenu.slotChangedCraftingGrid`
with the server RecipeManager; `ResultSlot.onTake` consumes one per slot and
awards via `RecipeHolder.awardUsedRecipes`; `Slot.tryRemove` refuses a
mismatched cursor; QUICK_MOVE from grid fills slot 9 first. Game rule
`doLimitedCrafting` assumed default false. No custom crafting helper,
manual shrink or output award; inventory stocking uses `Slot.set` only.

## Files and tools

Written: the two scoped NEW files only. Tools: Read, Glob, Grep, Write. No
shell, Git, Gradle, JVM or network.

Governance reads were partial under the 18-minute cap: ADR-060 lines 1-120,
PROJECT-CONFIG lines 1-80, doc 14 lines 1-80, RUNTIME-CONTRACT-04 and
ADOPTION-04 fully. `docs/versions/v1.8.0.md` does not exist. Unread: PRODUCT,
docs 01/04/05/06/16/17, UPSTREAM, NOTICE, docs 02/08, provenance. The
supplied AGENTS.md has no section 11. No upstream content was copied.

## Unrun checks

`./gradlew clean build`, `runData`/no diff, `runGameTestServer`, strict
repository/provenance checks, independent actual-diff review.

## Residual risks

- Uncompiled; a wrong API assumption fails the test, not production.
- A FakePlayer constructor may touch spawn chunks (existing fixture pattern).
  It also leaves one PlayerAdvancements map entry per run; listeners are stopped.
- Recipe-advancement reward side effects on the FakePlayer were not observed.
- Server menu evidence does not prove client behavior, source-world
  preservation, crash atomicity or packaged S1. No Gate/policy/asset acceptance.

## Release

All process, read, write, source, HEAD, index, branch and worktree interests
are released. Further changes need a new task or leaf.
