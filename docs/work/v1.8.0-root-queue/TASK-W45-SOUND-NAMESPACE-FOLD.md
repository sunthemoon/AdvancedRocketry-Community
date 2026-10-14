# TASK W45: fold the sound definitions into the mod namespace

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w45-sound-fold` (from the shared branch, plus Root's `build.gradle` commit and this task) |
| Branch | `codex/v1.8.0-w45-sound-fold` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W45.md` (new) |
| Specification | ADR-066 section 7.1 (the sound bullet); `records/W16.md` ("Sound definition namespace") |

## The current state

- The historical v0.1 `src/generated/resources/assets/advancedrocketrycommunity/sounds.json` holds only
  `ui_select`.
- W16 put the ten v1.8 definitions in
  `src/generated/v1.8/resources/assets/advancedrocketrycommunity_v180/sounds.json`, because two files at
  the same path collide in the jar.
- So each v1.8 event's sound location is `advancedrocketrycommunity_v180:<id>`, and `/playsound`
  needs that name rather than the registry ID.

## Root's decision and change

- One current `sounds.json` in the mod namespace holds all eleven definitions: `ui_select` and the
  ten v1.8 events.
- The registry IDs stay exactly as they are.
- Every event's sound location equals its registry ID, `advancedrocketrycommunity:<id>`.
- Root's commit on this branch excludes the v0.1 copy
  (`assets/advancedrocketrycommunity/sounds.json` under `src/generated/resources`) from both
  `processResources` and `sourcesJar`. Do not edit `build.gradle`. If the exclusion needs a change,
  stop and report.

## What to do

1. The v1.8 datagen (`V180SoundDefinitions`) writes
   `src/generated/v1.8/resources/assets/advancedrocketrycommunity/sounds.json` with all eleven
   definitions. Copy `ui_select` exactly from the v0.1 file: same sound files, volume, pitch, stream
   flags and subtitle.
2. Remove the `advancedrocketrycommunity_v180` sounds file. The v1.8 language files stay in their
   namespace.
3. Update `ClassicSound` and every other place that builds a sound location, so each location
   equals the registry ID.
4. Check that each OGG file the definitions name exists at its resource path. Do not move, add or
   re-encode audio.
5. Keep the immutable v0.1 generated file unchanged on disk; only the build excludes it.
6. Update `docs/provenance/v1.8.0-c18d-sounds.json` and any guide that names the old location.
7. **Tests:**
   - a unit test that reads the generated mod-namespace `sounds.json` and checks it has exactly
     the eleven keys, each with existing sound files;
   - a check, in the existing artifact or resource audit if one fits, that the built jar has exactly
     one `assets/advancedrocketrycommunity/sounds.json`, the v1.8 one, and no
     `advancedrocketrycommunity_v180/sounds.json`;
   - a GameTest or unit test showing each registered `ClassicSound` event's location equals its
     registry ID.

## Commands

- The whole unit suite once.
- `runData` under the lock, then `git diff --exit-code -- src/generated` after committing.
- `build` once under the lock, to check the jar contents.
- `runGameTestServer` under the lock, then the log checker.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- New audio, the manual listen (owner), producers (W42) and OBJ models.
- Status files and the ledger (Root).
