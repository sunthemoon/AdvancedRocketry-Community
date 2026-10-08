# CL18A-THERMITE-DATA-03 author handoff

2026-10-08. Delegated Claude author, session a729002d-d36b-41d0-b067-5a6038c17927; not Root,
reviewer or approver. Branch codex/v1.8.0-claude-thermite-data-20261008, HEAD 101c7882,
clean at start (per the session's Git snapshot). ADOPTION-04.md existed, so dispatch held.

## Scope: four NEW files, nothing else edited

- `datagen/V180ThermiteData.java`: only the contract section 5 public interface; paths, tint
  and JSON stay private. Client: 6 JSON + 2 PNG. Server: 6 JSON. No tag or language output.
- `datagen/V180ThermiteLanguage.java`: immutable three-key English/Chinese map.
- `V180ThermiteDataTest.java`: 11 A0 tests, 407 lines, below 24 KiB by line-length bounds.
- This handoff.

## Choices for review

- Shapeless recipes with tag-only ingredients. The thermite result omits `count` (default 1);
  the torch result has count 4.
- Each unlock is one OR group: tag `inventory_changed` criteria plus `recipe_unlocked`. The only
  reward is the recipe itself.
- Loot: two distinct tables with `survives_explosion`, no functions and no `random_sequence`
  (station-light precedent).
- The wall east variant omits `y` (0), as ROTATION-01 records. The torch item's layer0 is
  `block/thermite_torch`.
- Art uses one tint, 0xF0B080. Dust: a low mound in rows 8-14 with two loose grains. Torch:
  columns 7-8, rows 6-15, with a bright tip, a lighter cap and a shaft that darkens downward.
  I read no template JSON, UV or bitmap.

## NEW origin

I drew both grids in this session from the NEW declaration's descriptions. I copied no legacy,
Minecraft, Forge, other-mod or project grid. I saw V180MaterialArt's DUST grid while reading its
encoder but did not reuse it. Originality and final bytes/SHA-256 need an art review.

## Tests added, none run

The tests cover the partition, the tag-only oracle, unlocks, references and loot. They derive
wall yaw from `Direction.toYRot()` and check it against 0/90/180/270. They also cover fresh
results, bounded JSON/PNG parsing, CRC, alpha, single tint, geometry, hand-computed pixels, the
four flag combinations with repeat bytes, and the language maps.
Nothing was compiled or executed. All Gates are unchanged.

## Root needs and open issues

Root still owns: wiring, holders, tabs, the thermite tag and umbrella, generated outputs, press
byte-identity, A1, S1 and V1/V2. Unverified: that Direction's static initialization is safe in
plain JUnit, and that my hand-computed pixels match the encoder.

## Tool actions and release

Read 30: TASK, my own memory note, DISPATCH, ADOPTION-04, CONTRACT-04, the 17 live-main files,
four sources, the declaration, two reports and ROTATION-01. Glob 4, Grep 18 (line-length size
checks), Write 5 (this file twice), Edit 0. No shell, Git, JVM, network, archive, cache or
memory write. I did not check SHA-256 values. No process remains. I made no HEAD, index,
branch, stash or worktree change. All process, read, write, source, HEAD, index, branch and
worktree interests are released.
