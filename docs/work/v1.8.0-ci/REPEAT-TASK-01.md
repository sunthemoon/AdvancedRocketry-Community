# V180-CI-02: repeat DataGen in the hosted regression

Date: 2026-10-05. Integrator/author: Root. Status: implemented-unverified.
Basis: `5f1cbefc30d59a35eed80023697a1f4a0c4687fc`.

The ordinary station-light contract requires two deterministic DataGen runs.
The existing workflow's single run does not satisfy that check. Append one
second `runData --no-daemon --stacktrace`, followed by tracked diff and complete
clean-worktree checks, in the same existing step. Each second-run log has a
distinct `repeat-*` name and is retained by the existing always-run upload.
First-run failure still stops the step; no old assertion, timeout, space floor,
test filter, permission, cache rule, artifact path or action pin changes.

Root's write scope is `.github/workflows/v180-development.yml`, the added method
in `tests/test_check_v180_ci_host.py`, and this task record. Other source/asset/
status changes are independent scopes and are not this CI task's diff. No
additional runtime, secret, deployment, dependency or self-hosted worker is added.

The new static regression checks two actual command/check sets and the three
new raw log names. All original methods remain unchanged. The first actual
local run has 17 tests /1 error: the new method referred to nonexistent `ROOT`.
Root corrects it to the existing `self.workflow`; the separate replay has
17 tests /0 failures/errors/skips, exit 0. Both raw receipts remain in
[the Root CI evidence leaf](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-ci-root-20261005-01/),
as `host-tests08.*` and `host-tests09.*`. This is a test-harness correction,
not an original production regression or an executed second DataGen run.

Different-agent actual-diff review and a new committed hosted replay are
required. Old run 37322207759 performed one DataGen run and remains failed;
its 16 tooling tests are not silently relabelled as the new 17-test cohort.
Neither these static checks nor a future cache hit establishes native/client,
full progression, R-021 or any G0-G9 acceptance. Local C below 10 GB still
prohibits full build/GameTest/native execution. New scratch stays on D.
