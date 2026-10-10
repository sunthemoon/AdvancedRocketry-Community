# C19 bootstrap provenance test organization 108

Date: 2026-10-10. v1.8 development under ADR-060. Root is sole source implementer
and Main integrator. Base d34ed03dac8a1f4c8a5c9a220c7dcd03affa73f2 is clean in
D:/GitHub/arce-v180-final-review-fixture-20261010-101. Fresh source branch
fix/v1.8.0-bootstrap-scenario-organization uses worktree
D:/GitHub/arce-v180-bootstrap-organization-20261010-108.

Source write_scope: tests/test_validate_bootstrap_provenance.py, new
tests/bootstrap_provenance_fixture.py, tests/bootstrap_provenance_cases/*.py,
tests/test_bootstrap_provenance_organization.py and
tests/fixtures/bootstrap_provenance_case_ids.txt. No production changes.
Root separately owns these task/review/checkpoint records, CURRENT_VERSION,
COMPLETION-PLAN and the v1.8 implementation log. AGENTS.md and inherited unknown
untracked files are not assigned and must remain unchanged.

Extract existing fixture/helper methods and scenario groups into non-TestCase
mixins. Keep the original runnable facade class/module, all 95 named selections,
method bodies/decorators, subtests, assertions, helper semantics, module imports,
patch targets, real historical-repository case and direct-entry behavior.
Helpers with __file__ remain directly under tests so their repository root is
unchanged. Lifecycle construction, cleanup registration, full physical per-case
copies and deep manifest copies remain unchanged. No shared mutable case state,
validation/result cache, hardlinks, alternates or policy/budget adjustment.
Each declared class must remain below 500 lines. Add organization checks for
discovery identity, non-runnable groups and fixture/lifecycle composition.

Root runs organization checks and all 95 original selections once each, then
independent Review 109 may repeat them after execution release. Hold source until
the peer's final read cutoff and fully read/rehash its sealed report. Explicit
owned staging/stat/check, commit and normal push precede committed organization
checks and one literal full discovery command. Each original deadline 180 s;
failures stay failures, without retries or timeout/coverage relaxation.

Fresh Root evidence leaf D:/GitHub/ARCE-Task-Evidence/v1.8.0/
c19-bootstrap-organization-root-20261010-108. Own sibling runtime prefix
c19-bootstrap-organization-runtime-20261010-108-. Pinned Python
D:/python/pyenv/pyenv-win/versions/3.13.15/python.exe SHA256
85b71d8c6ec1905935f74be0c9869aae198d00e98f39df699ec66f9c5a84cecd.
Analyses -I -X utf8 -B; authored children -X utf8 -B. Read/file <= 1 MiB,
combined target streams <= 256 KiB, own leaf <= 4 MiB. Check C:/D: >= 10 GiB.
Bind Main/source/base HEAD/tree/full status, bounded scoped tracked index,
all scripts/tests Python and case-ID files, tasks, own helpers and executable
before/after commands. Do not read the raw physical repository index. Freeze
inputs/helpers while commands run. Preserve original Popen argv/PID/UTC/wait
and complete raw pipes. Kill only the original owned child on deadline; separate
<= 10 s postdeadline wait/drain is not qualification. Retain nonempty own runtimes
uninspected; remove only fresh empty runtimes nonrecursively. No descendants,
unowned process operations, old sealed helper execution or sealed packet changes.

This resolves only a test-organization prerequisite for ADR-070; it does not
accept that proposed ADR or integrate source into Main. No Gradle/native/client/
resource/strict execution, upstream/assets import, sleep/writer activation,
ledger delivery, release or Required Gate approval. G0-G9 stay required/open.
Unexecuted standard commands and dedicated/restart/visual work are not N/A/waived.
