# C19 manual-evidence phase observation60

Date: 2026-10-10. Version: v1.8.0, ADR-060. Status: sealed finite two-case observation; original wait0.

Root owns this task and central records. Fixed read-only checkout is
D:/GitHub/arce-v180-bootstrap-fixture-20261010-49, clean commit
8062781ca0cfc3157212f6f313c509cd0e522c7c. Observe the unchanged
ManualEvidenceTests methods test_ready_bundle_redacts_private_log_data_and_validates_strictly
and test_dirty_worktree_is_rejected_before_collection. Use sys.setprofile to
measure authored setUp, body, cleanup and selected collector/helper calls, with
real subprocess starts. Existing authored mocks remain as written; observation
must not replace methods, assertions, subprocesses, Git settings or validator
behavior. Record inclusive nested timings without adding them as disjoint cost.

Write scope: this task and Root's own new external leaf
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-manual-phase-root-20261010-60.
Own fresh TEMP/TMP sibling is c19-manual-phase-runtime-20261010-60. No source
edit, old evidence/helper execution, inherited TEMP cleanup, build/native/client
run, integration, ADR acceptance, ledger or Required Gate decision.

Run once using the fixed Python3.13.15 executable with -X utf8 -B and the
original 180-second command ceiling. Bind source HEAD/tree, tracked status,
scoped index, relevant raw Python inputs, helpers and Python before/after.
Read/file cap1 MiB; combined child streams256 KiB; leaf4 MiB. Check both disks
have at least10 GiB before launch. Capture original child handle wait exit,
complete raw streams, argv/PID/UTC and finite phase rows. A timeout remains a
failed180-second command; an explicitly separate10-second post-timeout owned
wait/drain interval may observe termination, never qualify execution. Do not
terminate descendants or unowned processes. Only authored fixture cleanup in
the fresh runtime is permitted. No retry or elapsed-budget increase.

Report actual timings/counters, source bindings and limitations. A finite
instrumented observation is not a whole-suite result, old-timeout explanation,
environment/ABA/descendant guarantee or resource acceptance. Seal the exact
payload inventory without rerunning helpers after sealing.

Actual result is recorded in checkpoint68: two cases/2.8540849 seconds, no
overflow or active frames, complete streams and unchanged finite bindings.
Sealed leaf14 files/145668 bytes; no source or Gate change.
