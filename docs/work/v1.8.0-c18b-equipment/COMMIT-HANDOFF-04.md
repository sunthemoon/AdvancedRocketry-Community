# CL18B-EQUIPMENT-COMMIT-04: author handoff

Date: 2026-10-08. Author: Claude (Opus 5.5), fresh delegated worker, not Root; no persona, no nested
delegation. Session ID is not visible to the author; Root records it from its dispatch. Status:
**author-returned, unreviewed**. No verdict, acceptance or reviewer authority is claimed.

## Checkout

Worktree `D:/GitHub/arce-v180-claude-equipment-commit04-20261008`, branch
`codex/v1.8.0-claude-equipment-commit04-20261008`. HEAD `9903b3f6` with a clean status is taken from
the session start-up context; the author ran no Git command to confirm it. TASK-04 was read in the
Root checkout. The author computed no hash, including REVIEW-03's stated SHA-256 `031780d4…5cd21c`.

## Completed scope

Two NEW files, exactly the TASK-04 `write_scope`:

- `docs/work/v1.8.0-c18b-equipment/COMMIT-PROPOSAL-04.md`: successor clauses for publication order
  (B3-M1), refusal scope (B3-M2), retained-input ownership (B3-M3) and withdrawal wording (B3-L1),
  with affected and new planned observations (BT10-BT34).
- `docs/work/v1.8.0-c18b-equipment/COMMIT-HANDOFF-04.md` (this file).

The proposal was written once and then rewritten once in full before return to keep a safe margin
under 12 KiB; the author could not measure bytes without a command and estimates about 10 KB for it
and about 5 KB for this file. Root records exact bytes. Nothing was written to the Root-captured leaf
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18b-equipment-commit04-claude-author-20261008-01` or anywhere
else.

## Not completed or not done

- No quarantine, save veto, truncation, R-021 acceptance, bound/schema/numeric/config change,
  equipment enablement or workstation rewrite (forbidden by TASK-04).
- New dependencies SQ-1..SQ-3 (fixed-source callback and assignment proofs) and RP-6 (retained-input
  ownership) are named, not resolved. RP-1..RP-5, BND-OPEN-1..3, CONTRACT-02 D/E2-OPEN items,
  REVIEW-02 M2 and other L1 parts stay open.
- Observed, not addressed: materialization failure during destruction drops (B§5.5) is outside
  REVIEW-03's findings; the proposal leaves B§5.5 unchanged.
- The BOUNDS-HANDOFF-03 author-read deviation stays open; this task's reads do not cure it.

## Key decisions (proposals only)

1. All materialization (views, hand-outs, preflight rebuilds) moves before the final recheck;
   publication assigns prepared references only; dirty mark and neighbour notification follow the
   last assignment. The callback-free-rebuild alternative is not taken.
2. Refusal oracle split: R-a byte identity only without intervening external change; R-b zero
   operation-owned publication with newer external state kept, judged against a hook-recorded state.
3. Retained claim narrowed to "ARCE never writes, grows or replaces the reference". Byte identity is
   bound to RP-6, which covers both load-caller aliases and aliases created by emitting the reference
   at save.
4. REMOVE goes to slot 1 only; withdrawal or quick-move from slot 1 is a separate operation.

## Reads (all full-file reads with the Read tool)

Main checkout governance, in full before any write: `AGENTS.md`, `PROJECT-CONFIG.md`, `PRODUCT.md`,
`docs/01`, `docs/04`, `docs/versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md`,
`docs/versions/V1.0.0-COMMUNITY-MVP.md`, `docs/05`, `docs/06`, `docs/16`, `docs/17`, `docs/14`.

Task inputs: TASK-04 (Root checkout); REVIEW-03 (reviewer-01 leaf). At the fixed base in this
worktree: BOUNDS-PROPOSAL-03, BOUNDS-TEST-DESIGN-03, BOUNDS-HANDOFF-03, CONTRACT-02, TEST-DESIGN-02,
`docs/work/v1.8.0-c18-contract/OXYGEN-WITNESS-CLARIFICATION-01.md`. Reserve proposals from their D
leaves: `c18-oxygen-reserve-proposal-20261007/PROPOSAL-01.md` and
`c18-oxygen-reserve-proposal-successor-20261007/PROPOSAL-02.md`.

Locating calls (Glob only, narrowly scoped): the equipment work directory; a RESERVE/OXYGEN-WITNESS
name pattern under `docs/work`; the `v1.8.0-c18-contract` directory; reserve `PROPOSAL-*.md` under
`ARCE-Task-Evidence/v1.8.0/c18-oxygen-reserve-proposal-*`. No Grep. All returned results; none
failed.

Not opened: any repository source, mapped Minecraft/Forge source, cache, JAR or upstream file (not
granted); OWNER-DECISIONS, TASK-03, REVIEW-02 and other evidence files (not required by TASK-04).

## Commands and tools

Tools used: Read, Glob, Write. **No shell, Git, Python, JVM, Gradle, runtime, network, install,
credential, cache, JAR, archive, hash or seal command was run, and no agent was started.**

## Unrun tests

Every BT case, including new BT26, BT27, BT28 and BT34, and every revision-02 case is **UNVERIFIED**.
`./gradlew clean build`, `./gradlew test`, `./gradlew runData`, `git diff --exit-code`,
`./gradlew runGameTestServer`, S1, S2, V1 and V2 were not run: not waived, not NOT_APPLICABLE.

## Limits

- Platform behaviour cited as motivation (callbacks during stack construction and capability
  attachment, neighbour notification on a dirty mark, capability comparison in stack equality, save
  consumers keeping the emitted tag) is author knowledge, not inspected source. It is stated as
  dependencies SQ-1..SQ-3 and RP-6, not as fact.
- Usage: client budget display showed about USD 1.8 spent before this file, under the USD 4.00 cap.
  The author has no clock tool and did not measure elapsed time against the 15-minute limit.

## Gate and next step

No v1.8 G0-G9 Required Gate is satisfied. Next: a different-agent delta review of
COMMIT-PROPOSAL-04 against REVIEW-03; Root disposition of SQ-1..SQ-3 and RP-6. Native or
implementation leaves only after scoped freeze.

## Interest release

No process, helper, lock, Git index, HEAD or branch interest is held; nothing runs in the
background. All process, read and write interests are released, including any interest in this
worktree's HEAD and index. The author will not edit these files again; corrections go to a new
Root-registered task.

Handoff path: `D:/GitHub/arce-v180-claude-equipment-commit04-20261008/docs/work/v1.8.0-c18b-equipment/COMMIT-HANDOFF-04.md`
