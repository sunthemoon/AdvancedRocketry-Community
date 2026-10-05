# V180-CI-01: hosted short-cycle development regression

Date: 2026-10-05. Status: implemented-regression-failed. Owner/integrator: Root.

## Outcome and scope

Run real Java 17 /Forge 47.4.10 clean build, fresh unit tests, DataGen with clean
tracked/untracked output and unfiltered GameTests for the exact pushed v1.8
development commit on an ordinary GitHub-hosted Linux runner. Check at least
10,000,000,000 free bytes on execution filesystems before setup and each heavy
step. Local C remains below that floor; no local heavy execution is authorized.

Python TMPDIR/TMP/TEMP and Java java.io.tmpdir are explicitly bound to the
sampled runner temporary directory for the job. The clean build disables task
output-cache reuse; restored dependency caches are not fresh JUnit evidence.

Root write scope is the new workflow, host preflight, focused host tests and this
task record. Existing workflows, user AGENTS, production behavior, schemas,
registries, assets, accepted ADRs and ledger are unchanged. Independent reviewers
are read-only and use their own D evidence leaves. Root owns publication and
later current-status synchronization. No existing sealed packet is recopied.

## Verification and evidence

Host unit tests cover the exact space boundary, separate filesystem refusal,
root/missing UID/non-Linux rejection, inspection failure and no directory
creation. Independent review must check actual workflow triggers, fixed commit,
blocking command/pipeline failures, execution checks and report retention.
Local static/unit results are not an observed hosted build.

The workflow triggers only on relevant pushes to
`codex/v1.8.0-classic-content`, not every documentation change. It has read-only
repository permissions, no persistent checkout credential, no self-hosted node,
deployment or secrets. Existing action versions are frozen to their observed
full upstream tag commit hashes. A single 45-minute job runs sequentially;
there is no timeout or test weakening and no automatic cancellation of a
running regression. Published reports bind the actual checkout SHA and artifact
hash. Runtime/build trees stay only on the disposable hosted runner; upload
contains compact logs/results, with one separately named external JAR.

Repository/source review and actual committed hosted results remain required.
Failed runs are retained, not rewritten as passes. Current packaged/native
recovery, first-save/hold/writer admission, R-021, full progression, GPU/multiplayer
and all G0–G9 remain separate. This task does not declare v1.8 complete.

## Source-review correction

The [original independent review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-ci-review-20261005-64ec13/REVIEW-01.md)
finds two Medium issues (fresh-test cache reuse and unbound process temporary
directories) and one Low (an annotated tag object described as a commit pin).
Its original 12 tests/eight independent controls and findings remain unchanged.
The focused correction disables the build cache, binds/records effective temp
and uses the dereferenced Gradle action commit. Two new static wiring tests cover
cache and temp bindings. The original apply-patch attempt failed its task-text
context check before changing files; the separate corrected patch is retained
as a later operation. Corrected independent review and actual hosted execution
remain pending, not inferred from these changes.

[Root tag observations](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-ci-root-20261005-01/ACTION-TAG-OBSERVATIONS-02.json)
record all five primary repository API refs and the Gradle annotated-tag chain.
The initial lightweight-only tag check failed; the bounded fresh dereference
resolved v6 to commit `3f5f9adaf7d9fecd50b5935e54106014257a94e6`.

## First hosted attempt and context correction

The corrected four-file source was independently reviewed with 14 tests and
six controls, then published in `33b690f001f5ecca88589d44582369a8d73f1785`.
[Run 37318904441](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37318904441)
failed workflow validation before any job, test or Java execution. Its annotation
identifies five unsupported `runner.temp` expressions in job-level `env`.
The [primary context table](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#context-availability)
allows `runner` in step-level `env`, not job-level `env`.

The follow-up moves the five bindings into the initial preflight step, where
they cover its Python process, then persists them through `GITHUB_ENV` for later
steps. The space floor, fresh-test cache flag, timeout and unfiltered tests are
unchanged. A new context regression and revised temp-wiring test fail against
the original workflow (15 tests: one failure, one error); follow-up results are
recorded separately. The earlier hosted failure and source reviews remain
unchanged. Independent correction review and actual next hosted results are
still required; neither static checks nor a parsed workflow establish build,
DataGen, GameTest or any version Gate success.

The first post-patch test attempt still had one test-harness error: its new
step-env parser omitted the YAML `run: |` block marker. That attempt is retained
as `host-tests04-green.*` despite the prematurely chosen filename; the later
parser correction and actual result use a new numbered receipt.

The [independent context-fix review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-ci-context-review-20261005-7c029e/REVIEW-01.md)
finds a Medium failure path: checkout or preflight refusal skips `GITHUB_ENV`
export, so the unconditional upload's missing `env.EVIDENCE_DIR` expands its
first path to `/`. Its 15 focused tests pass, while two of eight independent
controls fail on that same issue. No root scan or upload was executed.
The separate repair binds that upload path directly to the supported step-level
`runner.temp` context. A new regression fails against the vulnerable wiring
(16 tests, one failure), then is replayed against the repair. The other three
workspace-relative result paths, preflight rules and product-test assertions
remain unchanged. This repair still needs independent review and hosted replay.

## Committed hosted observation

The failure-upload repair completed different-agent source review with no
introduced C/H/M/L and was committed/non-force pushed at
`516317a583d1626d114dc5e1d4a3670cb79d0b5a`. Its hosted run executed real build,
unit and DataGen checks but failed one required GameTest. The separate
[result record](RESULT-01.md) retains the exact source, counts, raw receipt and
independent result audit. Earlier future-tense review requirements above are
historical checkpoints, superseded only for those actual checks. Full regression,
failure correction/replay, native/client acceptance and all Gates remain open.
