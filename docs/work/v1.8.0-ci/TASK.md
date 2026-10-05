# V180-CI-01: hosted short-cycle development regression

Date: 2026-10-05. Status: implemented-unverified. Owner/integrator: Root.

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
