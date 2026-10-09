# Passive observation source review checkpoint 07

Date: 2026-10-09. Status: implemented-unverified; not integrated or runtime-ready.
This is the narrow [source-only assignment](OBSERVATION-ASSIGNMENT-05.md), not
implementation of the owner's [actual-sleep target](OWNER-DECISION-01.md).

## Committed source and actual compilation

The isolated source branch codex/v1.8.0-sleep-boundary-observation contains
e72b41dea67404b5abba48bb499a1b4d5d743389 and additive attribution revision
0c05cc27112f5f4a71faa66bc16b5f7f4390f345. Only the two assigned adapterTest files
are added from b9bec789978de0df67c93e976239d6d36e41532b. The latter fixture/trace
contain 476/357 lines. Root read both complete fixed blobs and the additive diff.
The latter source was normally pushed; neither candidate is merged into Main.

The author executed the exact isolated compileAdapterTestJava --no-daemon
--no-build-cache --rerun-tasks at both committed candidates. Both returned 0,
each with seven executed tasks; the final receipt spans
2026-10-09T04:55:08.2880230Z through 04:55:24.2625966Z with installed JDK 17.0.7,
Gradle 8.8 and own child TEMP/TMP after >10 GiB disk preflight. Root read actual
compile-02 command.json and push.json, not independently executed those commands.
Six new fixture GameTests exist, but none is claimed executed from compilation.

## Actual source correction required

SleepBoundaryTrace.append first retains a new JsonObject, then converts the
complete root/current JSON into a String and UTF-8 array before checking the
128 KiB/512 KiB caps. Frame metadata and native chat serialization likewise
precede size rejection. This bounds accepted output, not pre-allocation trace
memory. Measurement fields cover a capture prefix rather than complete serialized
capture. The author confirms these limitations; Root requires a correction rather
than documentation-only acceptance or expanded caps.

[Correction task 08](D:/GitHub/ARCE-Task-Evidence/v1.8.0/SLEEP-OBSERVATION-SOURCE-CORRECTION-TASK-08.md)
has SHA-256 7ae9af7b58679cd66101066bcb7b13848011bb9e8e453c9354b6e6afde8f66de.
It preserves the same two-file boundary, native decisions, trace limits and
compile-only permission. Fixes must be new commits with separate actual receipts;
the two original cohorts remain unchanged. The complete independent source
review is still in progress in separate detached worktrees and has independently
identified the same mechanism. Its final severity, other findings and corrected
source qualification are not prefilled here.

Independent review additionally identifies a setup guard leak: INVOKING is set
before trace/fixture construction, outside the responsible try/finally. An
initialization failure can retain the server-thread marker and reject later
invocations. [Addendum 10](D:/GitHub/ARCE-Task-Evidence/v1.8.0/SLEEP-OBSERVATION-SOURCE-CORRECTION-ADDENDUM-10.md)
requires exact release after every acquired-marker setup failure within the same
scope. Both are Medium test-fixture findings; no failure injection was executed,
and neither is claimed a proven production vulnerability. Final complete review
and additive candidate remain pending.

## Evidence and unfinished scope

Unsealed development evidence is in
[author leaf](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-observation-source-20261009-05/PROGRESS.md),
[independent leaf](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-observation-source-independent-20261009-06/),
and [Root review notes](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-observation-root-review-20261009-07/PROGRESS.md).
They are intermediate records, not a sealed delivery evidence package.
Task 06 additionally owns fixed read/disposable compilation outputs in
D:/GitHub/arce-v180-sleep-observation-review-final-20261009 at 0c05cc27; the
original e72b41 worktree HEAD was not moved.

[Driver design task 09](D:/GitHub/ARCE-Task-Evidence/v1.8.0/SLEEP-OBSERVATION-DRIVER-DESIGN-TASK-09.md)
is read-only planning, SHA-256
ff83fa693a4469dd7540cd074d1284b876d6f07d29f06c52065f3a225928435f.
No executable parser/driver is frozen, and no tests/build/DataGen/GameTest,
server/world/client or native observation are launched by these assignments.
Main's current behavioral regression remains bound to 3b18a6bc, not either new
adapter candidate. No shipping dimension, spawn/time/air policy, API, schema,
asset, ledger or Required Gate changes. B1/R1/M1/M2/D1 and the
[unanswered ancillary choice](OWNER-QUESTION-02.md) remain open.
