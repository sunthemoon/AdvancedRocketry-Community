# Waiting-owner test-source candidate 05

Date: 2026-10-09. **IN PROGRESS; NO FULL REGRESSION ACCEPTANCE.**
Source 0b02c513b573cf474690e8d0801152acebf8d692, parent
770b3134cae99ab603ccde610c3731a98d63f524, src tree
a7b7d83a67fb02403f5aaee9c2d21987a06b1f6a. Root normally commits/pushes the
sole changed file after author release, full postimage/diff review, clean index
and cached-stat/check. Main source is unchanged; no qualification is borrowed
from the two differently failed 770b3134 cohorts.

## Scope and source review

[Task05](LASER-OWNER-REGRESSION-TASK-05.md) is published before source edits.
Only LaserTargetGameTests.java changes, 182 inserted/27 removed, postimage
43081 bytes/774 lines. SHA-256
21bf7ec8a29d287b4e47ba23ca59c3745c019719cd2b09acc9520f47c71bc7c6;
Git blob 2a33f3a94adf1abf9be8aded512b9040c459bfd3. The existing 200-tick
annotation and intended-new-owner assertion remain. No imports, production,
config, persistence/schema, scheduling, budget, registry/build or assets change.
The >500-line responsibility check finds one narrowly scoped native regression
fixture helper; the class remains below 800. No upstream material is imported.

The real target's unregistered/inactive/awaiting-save/original-owner identity is
asserted and the existing operator command executed in the initiating call before
yielding. Bounded command/registration/failure receipts retain exact owners,
target ID/identity, Level/position, ticks, result and live/root observations.
At most four 256-character replies are retained. Guarded onEachTick registration
checks exact record/live identity. Closure prevents later owned callbacks from
replacing a failed prerequisite; owned player/block/listener cleanup also occurs
after batch timeout and same-server stop. First failure is rethrown with diagnostic
or cleanup errors suppressed onto it. No shared-root reset or ambient-item deletion.

The save event remains explicitly injected, not an outgoing native owner tag,
writer, durable file or restart proof. Ordinary native saves are not stopped.
Original source/failed receipts and schedule hypothesis remain separately retained.

[Author05 REPORT](D:/GitHub/ARCE-Task-Evidence/v1.8.0/laser-owner-fixture-author-20261009-05/REPORT.md)
is source authoring only, not independent/runtime approval. Root reads the actual
complete postimage and diff, report/static/command records and verifies all four
manifest entries; five physical files total 28147 bytes. Author ran static
diff/index checks only, no JVM/tests. Initial broad Root inventory was truncated;
full stable independent04 report was subsequently read in bounded displays.
Root's diagnosis02 INPUTS.tsv lookup failed; actual INPUT-HASHES.json is used.
Neither lookup/display failure is a test result.

## Fresh execution and independent review

Root exclusive [qualification leaf05](D:/GitHub/ARCE-Task-Evidence/v1.8.0/laser-owner-fixture-root-20261009-05)
records new source/config/helpers and pre-execution absent build/.gradle/run-data/
generated .cache. Java17.0.7 and installed Python3.13.15, no-daemon/offline/no
build-cache, forced clean build/explicit test/two DataGen and empty diffs,
unfiltered GameTest, ledger/provenance/strict checks run serially. Current bounds
and source/input/disk/raw receipts remain explicit; no old label is overwritten.
Execution is in progress. Actual outcomes will be added only after receipt review.

Fresh independent Codex /root/laser_owner_fixture_independent05 has a distinct
fixed detached checkout at this SHA. Its initial source/contract review is
read-only; JVM execution remains held while Root's cohort runs. Its exclusive
assignment/evidence leaf is separate from source author05. No self-review,
Main central edit, HEAD movement or implicit production repair is permitted.

The independent770 hanging-layer item-provenance issue, COMMON watcher exception,
strict timeouts and unwaived native ERRORs remain open. Root770 final scratch
cleanup launch was refused; that target is excluded from all later cleanup.
Whole detector/sleep/resource/persistence/dedicated restart/client/survival and
all v1.8 G0-G9 remain open. **All Required Gates satisfied: NO.**
