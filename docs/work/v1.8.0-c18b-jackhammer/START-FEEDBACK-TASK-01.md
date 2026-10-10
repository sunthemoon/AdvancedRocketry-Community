# Jackhammer disabled-start feedback repair

Date: 2026-10-11 (Asia/Taipei). Root is the implementer and integrator.
Base: `063edab0afc983fb9e665b213658188dd5c94347`.
Status: PREPARED; implementation, independent review and verification follow.

## Player outcome and boundaries

The client report at `D:/GitHub/ARCE-Task-Evidence/v18-client-20261010/REPORT.md`
records that disabled jackhammer feedback waits for native break completion.
The current item's `onBlockStartBreak` callback remains the final break refusal;
it is not the ordinary player's initial mining action callback.

Add an informational server action-bar notification using the already registered
bilingual disabled message when the native Forge LeftClickBlock action is START,
the player's main-hand item is the jackhammer, and the server's adopted
`equipment.classicEnabled` switch is false. Send one notification per START;
STOP, ABORT and client hold do not send this notification. Do not add state,
cooldowns, config synchronization, a packet, block access or chunk loading.

This presentation repair is within accepted ADR-066 section 5.3 and the adopted
ordinary tool leaf. It must not cancel or modify the event, replace final
mining/melee refusal, grant native admission, alter protection, loot, durability,
acquisition, balance, item data, recipes, art or saved schema. Existing final-hook
chat is retained. The unsynchronized COMMON tooltip is not repaired by this task.

## Ownership

Root's isolated checkout is
`D:/GitHub/arce-v180-jackhammer-feedback-20261011-01`, branch
`fix/v1.8.0-jackhammer-start-feedback`. Source write scope is only:

- New `equipment/tool/JackhammerFeedback.java` and
  `equipment/tool/JackhammerFeedbackGameTests.java` under the existing common package.
- Narrow packet-drain support in the existing `equipment/tool/JackhammerFixture.java`.
- This task and a new leaf verification/implementation record in this directory.

Root alone may later update the canonical plan and current status after committed
source, independent actual-diff review and original execution evidence exist.
The Main mixed implementation log, owner AGENTS.md, inherited work, all other
worktrees/processes and sealed evidence are excluded. No Claude or delegated JVM.

## Verification

Commit the candidate, then independently review its actual diff. Exercise the
installed production listener through an ordinary connected ServerPlayer's native
game-mode START on a hard target; inspect actual outbound action-bar packets before
break completion. Cover repeated START, STOP/ABORT, survival/creative disabled
refusal, an enabled control, a different main-hand tool with an off-hand jackhammer,
and a canceled native event with result flags preserved. Restore overrides and
temporary listeners in finally blocks. Test fixture packets are not a real-client
rendering result.

Run Java 17 `clean build`, separate `test`, `runData`, separate
`git diff --exit-code`, unfiltered `runGameTestServer`, the unchanged strict log
checker, whitespace and relevant repository validators. Use fresh owned D-drive
TEMP/TMP and evidence output, >=10 GiB free space, a new private process job,
900-second Gradle deadlines, 180-second checker deadline, 10-second post-exit capture,
and 4 MiB per captured stream. The job's optional 6 GiB aggregate commit cap is
an isolation setting, not a peak-memory qualification or release budget change.
Retain exits, complete output, XML, original logs, source SHA and hashes.

Remove only this task's ended disposable build/run-data/TEMP output after absolute
containment, reparse and process checks. Keep the compact evidence and source
checkout. No rejected-target retry, input-world deletion or process acquisition.

No persistence format changes require a new migration test. Existing tool save,
repair and continuation evidence remains historical; this task does not replace
the current version's dedicated restart, real-client V1/V2, survival progression,
performance or full G0-G9 obligations. Hosted storage CME remains open.
