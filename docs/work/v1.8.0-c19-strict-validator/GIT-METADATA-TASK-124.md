# C19 exact-commit metadata cost task 124

Date: 2026-10-10. Milestone: v1.8.0 under accepted ADR-060. Owner, implementer
and integrator: Root. Status: IN_PROGRESS. Independent read-only inventory125
uses the unchanged f87ff4c3 source; independent actual-diff review is assigned
after Root's code exists. No Claude or nested delegation.

## Bound identity and outcome

Main entry is 3a639ce9efb4f57a223524684882aa95532d016c on
codex/v1.8.0-classic-content. Source base is
f87ff4c322306aac7c16a4e1a613c8006f1999d0, tree
dce1f2430b47aa3ff0cb3939e3ff5cd209f4fb89. Root creates the absent worktree
D:/GitHub/arce-v180-git-metadata-20261010-124 on the absent branch
fix/v1.8.0-git-metadata-cost. Previous checkouts and their HEAD/source stay fixed.
User-owned AGENTS.md and inherited unknown acquisition rows are not adopted,
modified, staged or cleaned. Root alone owns central records and commits/pushes.

The observable change is in bootstrap provenance `_git_commit_exists`: a
successful existing bounded exact raw-commit read can establish the object's
type and identity without an additional `cat-file -t` process. Retain the
existing metadata probe only to preserve failure diagnostics after raw-read
rejection; metadata cannot rescue an unverified raw object. Inspect and test
the actual error and session-finalization behavior, including failed-session
effects. This is not a cache, approved-result reuse or a timeout cause claim.
Git's documented batch protocol contains exact object name/type/size and raw
content: https://git-scm.com/docs/git-cat-file. Existing code additionally
recomputes object identity and admits validation only after EOF/exit checks.

## Exclusive write scope and non-goals

Exactly two source files in the new worktree:

- scripts/validate_bootstrap_provenance.py
- tests/test_bootstrap_commit_probe.py

Exactly five central records in Main: this TASK, a new
GIT-METADATA-CHECKPOINT-124.md, docs/status/CURRENT_VERSION.md,
docs/status/COMPLETION-PLAN.md and docs/work/v1.8.0-implementation-log.md.
Own external evidence only:
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-git-metadata-root-20261010-124.
Each ordinary file <= 1 MiB, target/command streams <= 256 KiB, own leaf <= 4 MiB.
Actual committed standard-cohort operators/evidence, if launched, require a
separate frozen assignment with original build/test/DataGen/native ceilings.

No fixture/seed/assertion change, object cache, mutable/ref/path/history guard
removal, process protocol/timer/size-limit change, production Minecraft behavior,
asset import, schema/protocol, ADR acceptance, source integration into Main,
G4 fix selection, sleep/save-writer activation, ledger delivery, v1.9 or Gate
approval. A negative diagnostic query retains the existing local runner and
15-second timeout; that legacy runner has no streaming output-byte cap, a
separate existing limitation. This task does not qualify arbitrary Git output
or change that runner. No new fetch.

## Verification contract

Use pinned Python 3.13.15:
D:/python/pyenv/pyenv-win/versions/3.13.15/python.exe, SHA256
85b71d8c6ec1905935f74be0c9869aae198d00e98f39df699ec66f9c5a84cecd.
Own analyses use -I -X utf8 -B; original test children use -X utf8 -B. Each
declared target runs once at unchanged 180 seconds, without target retry or
source mutation during execution. Separate original-owned postdeadline
kill/wait/drain has a total <= 10 seconds and never qualifies the original
outcome. No unowned/descendant process inspection or termination.

Root precommit targets: new test module, complete original bootstrap module,
complete original packet module. Hold actual source through independent
actual-diff review and its declared applicable target checks. After the review
cutoff/report and exact scoped commit/push, Root runs the actual committed new
test module and literal `python -X utf8 -B -m unittest discover -s tests -v`.
Record raw argv/UTC/PID/exit/streams, file/Git bindings before and after, actual
counts, original timeout/failure and separate postdeadline outcomes. Direct
entry and unexecuted modes remain unverified unless explicitly launched.

New tests must exercise exact real commits, missing/wrong-type/tag identities,
raw verification failure not rescued by metadata, repeated reads without cache,
session close failures, and success-path subprocess absence. Existing module
tests and assertions remain unchanged. Static scoped diff, whitespace, class
size and tested-to-committed blob identity checks are required. Independent
findings are evidence, not a predetermined verdict. Actual call counts may
demonstrate avoided probes; elapsed times do not establish historical causes.

Check C:/D: free space >= 10 GiB before full build/GameTest. Target runtimes must
be newly absent ordinary owned directories; nonempty failed runtimes remain
uninspected. Remove only fresh empty runtimes nonrecursively. No world/evidence,
old/peer runtime, cache or checkout cleanup is authorized by this task.

## Delivery boundary

An independently reviewed source candidate may be committed and normally pushed
with incomplete full verification explicitly retained; it is not a completed
slice, Main integration or Gate. All G0-G9 remain required/open and v1.8 remains
IN_PROGRESS / IMPLEMENTING. Original packet/whole timeouts and 62 unwaived ERROR
headers per preceding native log are preserved, not superseded by intent.
The separate G4 d605be33 correction still requires explicit integration choice.
