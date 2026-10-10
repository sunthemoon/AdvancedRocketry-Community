# C19 final review-input fixture97

Date: 2026-10-10. Version: v1.8.0 under ADR-060. Frozen implementation assignment.

Root is sole source implementer and Main integrator. Base is committed
17bb40bcb3b3490469a6433f2d7de2f1baa9b620. Fresh branch
fix/v1.8.0-final-input-fixture-isolation uses
D:/GitHub/arce-v180-final-input-fixture-20261010-97. Source write scope is exactly
tests/test_prepare_v002_final_g0_review_inputs.py, new
tests/final_review_input_fixture.py and new tests/test_final_review_input_fixture.py.
Root separately owns these task/review/checkpoint records, implementation log,
CURRENT_VERSION and COMPLETION-PLAN. No production or adjacent fixture changes.

Preserve all original 19 test methods, assertions, selection identities and
original repository-construction/module-load/helper statements. Relocate the
GitFixture helper and constants with explicit facade re-exports. Add a class
lifecycle mixin which builds one normal repository seed, registers cleanup before
construction and makes a full physical copy for every normal case. Keep the
tool_after_base case and callers without a class seed on original construction.
Every case keeps fresh UUID module identity, its own cleanup and mutable globals.
Do not use hardlinks, Git alternates, result caches or modified production policy.
The existing scenario class remains over 500 lines: inspect its scenario-only
responsibility, do not add methods there or combine a decomposition slice.

Add five independent focused tests for physical/raw copy fidelity, mutable
repository/module isolation, special construction, failed class construction
cleanup and partial case-copy cleanup. No test removal or weakened assertion.
Root runs the new five and original 19 once each; fresh independent actual-diff
review repeats both after execution release. Wait for final peer read cutoff,
then stage only the three source files, inspect cached stat/check, commit and
normally push. Run the committed five once and literal complete discovery once.
Original per-command deadline stays 180 seconds; no failure becomes qualification.

Root external leaf is D:/GitHub/ARCE-Task-Evidence/v1.8.0/
c19-final-input-fixture-root-20261010-97; own sibling runtime prefix is
c19-final-input-fixture-runtime-20261010-97-. Use pinned Python
D:/python/pyenv/pyenv-win/versions/3.13.15/python.exe, SHA256
85b71d8c6ec1905935f74be0c9869aae198d00e98f39df699ec66f9c5a84cecd.
Analyses use -I -X utf8 -B; authored tests use -X utf8 -B. Every read/file <=1 MiB,
combined target streams <=256 KiB, own evidence leaf <=4 MiB. Check C:/D: >=10 GiB.
Bind HEAD/tree/full status, bounded scoped index, all scripts/tests Python inputs,
Main full status, tasks, own helpers and executable before/after commands. Freeze
those inputs during execution; retain original argv/PID/UTC/wait and full streams.
Only original owned process termination is allowed. Distinct <=10-second
postdeadline original-owned wait/drain does not extend or qualify the command.
Remove only fresh empty own runtimes nonrecursively; retain nonempty uninspected.
Do not execute old sealed helpers, alter sealed packets or clean inherited work.

No Main source integration, Gradle/native/client/resource execution, upstream
import, sleep/writer activation, ADR acceptance, delivery ledger or Gate approval.
Current G0-G9 and required Gradle/native/visual/restart checks remain open.
