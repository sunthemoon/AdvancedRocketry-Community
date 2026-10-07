# C18d-HUD-ENV-O2-01: worker source record

Date: 2026-10-07. Version: v1.8.0. Status: worker source returned, not integrated.
Author: Claude (model `claude-opus-5-5`) in an interactive Claude Code session that the
owner started on 2026-10-07 to help Codex with the remaining v1.8 work. The session was
not the headless CLI route in the feasibility report, and no Root assignment addendum
existed when authoring started; Root records the claim and decides adoption.
Technical input: [CONTRACT-02](CONTRACT-02.md) and [TASK-01](TASK-01.md), unchanged.

## Basis and write boundary

The source basis is committed `0f9fe72447c6f666a78ad888ee6496a782c6c34f`. Between the
contract basis `2a59cfac` and that commit, `src/` differs only in
`gametest/AirlockDoorGameTests.java`; the HUD, config and client test files are identical.
Authoring used a `git archive` export of that commit in an owned evidence directory on D:.
There was no worktree, branch, index, commit or push, and the main working tree, Codex's
uncommitted files and the other worktrees were not modified.

The worker changes are exactly the three new files that TASK-01 names:

- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/LifeSupportHudLayout.java`
  (193 lines, 8,440 bytes, SHA-256 `ba7a4415a6ffd63e45334e48d4de1ace161b714c1c32e0b372c58c3768cfccf6`);
- `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/client/LifeSupportHudLayoutTest.java`
  (417 lines, 23,533 bytes, SHA-256 `5402593d74f3ddf921bac5ddc9179d9c8d99a670d0d08db8198f7b86f271c948`);
- this record.

## Implemented interface

`LifeSupportHudLayout` is a public final, stdlib-only class (imports `java.util.List` and
`java.util.Objects` only) with the exact nested types and `place` entry of CONTRACT-02.
`PanelSettings` rejects offsets outside -4096..4096 and null anchors; `Size` and `Rect`
reject nonpositive extents; `Rect` does not check containment; `Layout` rejects nulls.
`place` rejects a negative viewport and null arguments before any arithmetic.

Two additions go beyond the frozen interface; Root or the reviewer may ask for removal:

- public `MIN_OFFSET = -4096` and `MAX_OFFSET = 4096`, so the CLIENT offset predicate
  can share the bounds instead of repeating them;
- package-private `arrangements(...)`, the ordered list that `place` scans. It has seven
  entries when both normal panels fit on both axes (requested, below, above, left, right,
  compact vertical, compact horizontal) and only the two compact entries otherwise.
  `place` returns the first entry whose two rectangles lie inside the four-pixel margin
  and are at least four pixels apart on one axis, or the compact vertical stack as UNFIT.

All anchor, clamp, candidate, containment and separation arithmetic is long. Candidates
keep oxygen's size and requested perpendicular coordinate and are never clamped again.

## Tests

`LifeSupportHudLayoutTest` has 18 JUnit 5 cases: all nine anchor pairs for each panel;
inclusive and rejected offset endpoints; exact signed endpoint offsets; independent
clamping; odd CENTER floor rounding; the default top-right pair for a 9-pixel line;
invalid and null inputs; int-max viewports needing long sums; int-max compact extents
with oxygen y 2147483655 and UNFIT; exact margin and four-pixel gap contacts; the exact
below, above, left, right order; no second clamp, visible in the arrangement list; an
oversized normal panel that skips to compact; compact vertical before horizontal;
impossible viewports; and a 21,609-case sweep checking determinism, at most seven
arrangements and selection of the first admissible arrangement.

## Checks run by the worker

These are light checks, not production test evidence:

- `javac --release 17 -Xlint:all` of both files: exit 0, no warnings.
- A reflective runner invoking each `@Test` method (not the JUnit Platform and not
  Gradle; the class has no lifecycle methods): 18 run, 0 failed, exit 0.
- Mutation check: 17 mutants of the helper (int sums, rounding, candidate order, gap and
  margin bounds, validation, compact order, UNFIT mode, re-clamping, skipped separation).
  After one strengthened assertion, every mutant makes at least one case fail. In the
  first run the re-clamp mutant survived, because its result through `place` alone is
  the same; the added assertion checks the arrangement list instead.

The leaf's report holds the exact argv, exit codes and outputs. No Gradle build, unit
test task, runData, GameTest, server or client ran.

## Impact and provenance

No schema, network, save, registry, resource, translation, asset, configuration or
provenance change. The code is original to this repository and copies no upstream or
third-party code. It imports nothing from Minecraft or Forge.

## Root integration needs and open checks

Root still owns the three existing adapter files named in TASK-01:

- `ClientConfig`: the eight `hud.*` CLIENT keys with the contract's Byte, Short, Integer
  or Long predicate and enum anchors, plus `environmentHudSettings()` and
  `oxygenHudSettings()` returning `PanelSettings`;
- `LifeSupportHud`: measure normal and compact sizes with the current Font, call `place`
  once per visible frame, and draw each panel in a pushed, translated and finally popped
  pose;
- `ClientConfigTest`: additive category, key, type, correction and reload assertions.

One adapter detail is left to Root: whether the two-pixel accent stripe counts as part
of the compact two-pixel padding. The compact sizes passed to `place` must match the
drawing either way.

Still required: committed-source Gradle build and unit tests, runData twice with tracked
and untracked cleanliness, full GameTests, a different-agent review of the combined
diff, real-GPU V1 and two-client V2. This record does not deliver content, close C18d
or satisfy any Gate.
