# Historical resource inventory test applicability11

Date: 2026-10-10. Status: IN_PROGRESS. Current v1.8 qualification tooling only.
Preparation base: Main 26fef44e616a5f550431823e57791eca6a0b0727. Root owns one new
exclusive source worktree/branch and central records; no Claude.

## Scope and observable outcome

Source worktree: D:/GitHub/arce-v180-inventory-test-applicability-20261010.
Branch: fix/v1.8.0-inventory-test-applicability. Source write_scope is exactly
tests/test_validate_repository.py. No scripts/production predicate, allowlist,
resource, Java/API, build, sleep, memory-window or deadline change is allowed.
Root central scope is this task/checkpoint, CURRENT_VERSION, COMPLETION-PLAN
and the v1.8 implementation log. User AGENTS and inherited/peer work are excluded.

The helper explicitly describes v0.0.2/v0.1.0 coverage and is dispatched by
check_repository_contents only for current_version v0.0.2. Its original live-tree
test scans later-version content unconditionally. Parent/source08 actual results
and the read-only inventory09 audit establish the mismatch; this task corrects
the test input's applicability, not the resource policy or a Gate threshold.

Replace the live-tree assertion with a checked inventory from the fixed accepted
historical bootstrap commit, retaining an empty-unlisted assertion and verifying
the finite expected input set rather than accepting a vacant inventory. Exercise
the documented v0.1 managed prefixes with deterministic inputs. Preserve all
existing undeclared-resource and cache tests, extend text/binary/root negatives,
and verify the twelve later resource paths remain outside that historical policy.
Add real check_repository_contents orchestration coverage for historical versus
later version dispatch, including continued rejection of unapproved binaries.
Do not skip, delete a negative test, accept extra paths, filter away undeclared
historical input, catch a failed assertion, or weaken expected rejection.

## Non-goals and verification

No comprehensive current-tree provenance contract is invented. Existing bootstrap
and later resource validators retain their own responsibilities. Nested Git,
historic snapshot cost and full strict/Python qualification are not fixed here;
their original 180-second outer windows and failures stay explicit.
No upstream files/assets are copied and no source/world/JAR archive is retained.

Run parent baseline before edits. Run targeted inventory/input tests and the
complete repository test file before committing. At fixed actual source commit,
run complete file, adjacent Python and one strict180 command with bounded raw
receipts. Fresh independent Codex review must inspect the actual diff and rerun
relevant tests in its own checkout/Temp, without inherited author conclusions.
Standard required Gradle/build/test/two DataGen/diff/native and provenance/ledger
qualification, if executed, uses a separately declared <=100 MiB leaf; record
actual source/input identities, disk admission and results rather than alias PASS.

Root Python evidence leaf is new, <=4 MiB retained:
D:/GitHub/ARCE-Task-Evidence/v1.8.0/inventory-test-root-20261010-11.
New Temp/scratch and ordinary interpreter caches in its exclusive checkout belong
to this task. Retire only exact owned outputs after all own commands end, once,
with containment/reparse/ownership checks and preserved receipts. Older refused
targets, peer/global data and source checkouts remain untouched. A finite retained
leaf size is not offline JSON memory/quota qualification.

## Review and status discipline

Exploration worker inventory_test_boundary11 is read-only, with no execution,
writes or process authority. Source and execution identities are recorded only
after actual commits/runs. Tests/source integration may address this finite
applicability failure without proving whole strict, current provenance, content
or G0-G9. All v1.8 obligations remain until their own evidence is obtained.
