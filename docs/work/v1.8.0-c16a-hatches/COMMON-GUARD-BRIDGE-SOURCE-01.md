# Common save-guard bridge source checkpoint

Date: 2026-10-07. Status: **implemented-unverified for event/native behavior**.
[Task](COMMON-GUARD-BRIDGE-TASK-01.md): C16a-03b-COMMON-GUARD-01.
Root normally commits and pushes the two Java files and task at
`826f5f20fc26a7be6bfc3af5d91f8a81bd7df71c`, after the documentation-only
`ff6282681177a9c95583de74936de431b4f9eb07` checkpoint.

## Exact implemented scope

The existing common helper now records an observed denial without chunk lookup,
defers only one save attempt after checking existing permanent refusal, and
closes existing Level states after the server's writer/close attempt. Terminal
resolution failures are isolated per Level; only the first RuntimeException
is retained and rethrown after the remaining Levels have been attempted.
No replacement/default state, online reset, ordinary-unload close or new writer
is introduced. Five NEW/MIT GameTests cover observation, first-wins, nonsticky
deferral, permanent priority and bounded reasons; each resets only its disposable
fixture coordinate. No synthetic stop closes the shared GameTest Level.

[Original independent source review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c16-common-bridge-source-review-20261007-482a19/REVIEW-01.md),
SHA `911c80febe71f72fb469c30e01067cf3f18420ab3b3185a0cdcafa13c70b06e4`,
finds two qualified Lows: optional terminal state was silently skipped and the
task signature section was incorrect. The separately
[reviewed exact correction](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c16-common-bridge-correction-review-20261007-f7425a/REVIEW-01.md),
SHA `8a6c3df0c9d9faa6aba1cdc38a6b6c77d73bfb0f5b1d1ed6c43a1d394cbc7f02`,
addresses both with no unresolved introduced C/H/M/L in its narrow static scope;
11 finite controls pass. Original review/inspection failures remain preserved.
The different-agent native-declaration analysis does not substitute for execution.

## Actual development verification

[One admitted offline execution](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c16-common-bridge-offline-execution-20261007-c18-5e71af/REPORT-01.md),
SHA `cf9542893b887cf144391b6aa261cbd43a1d854b7d9be395196ff68cb5bab271`,
compiles five actual project sources plus the unchanged Jupiter harness with
Java17 and 26 pinned cached JARs: javac exits 0. The subsequent pure-domain
runtime uses only fresh classes and six JUnit JARs: all five unchanged
`ChunkSaveDenialsTest` subjects pass once, with zero failures, aborted/skipped
tests or container failures. Outer execution exits 0; no retry, timeout,
overflow or input drift occurs. Compiler streams and Jupiter stderr are empty.
The static preparation's missing-Dist failure remains historical.

This is a frozen **uncommitted development observation**, not a fixed-commit
delivery test. Root publishes the exact reviewed/compiled postimages afterward;
it does not relabel that earlier execution as a committed full build.
[Root raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-c16-common-bridge-result-audit-20261007-01/AUDIT-02.json),
SHA `3c525f443ea8282fbf9c27acf29bee4122957f0f9f2bc36f18e47b8741737675`,
exits 0, checking source/task pins, raw child streams and individual subjects,
and rehashing all thirteen class files /37,960 bytes. Original audit01 exits 1
with Root's `KeyError(path)` receipt-field assumption; its helper/streams/exit
remain unchanged. Audit02 uses the actual `name` field. No Java rerun occurs.

Root's actual publication tool `954bd8` stages only the three task paths
(222 additions), checks staged scope/whitespace, commits, normally pushes and
verifies remote equality plus unchanged owner AGENTS. It is not an invented
exported original shell receipt. New temporary outputs are D-local. C remains
below 10 GB, so no local full Gradle/GameTest/native run is admitted.

## Remaining qualification

The unchanged hosted workflow is triggered by source-path pushes; no new run
identity/result has been observed for this checkpoint in this record. New
GameTests are compiled **but unexecuted**. The previous exact full regression
remains [RESULT-13](../v1.8.0-ci/RESULT-13.md), including its required Tau failure,
not a pass or metrics rebound to this new source.

Capability attachment, actual event/terminal iteration, missing/throwing
providers, final native save/unload, dynamic teardown, late callbacks and
packaged restart remain unverified. The helper does not enforce a family's
actually-held coherent operation; no family caller is introduced. GuardTicket,
full frame/hash/native codec, physical hatches, controller/lathe, conservation,
content ledger and all G0-G9 remain open. R-021 is not accepted or closed.
The separate [class-output cleanup addendum](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c16-common-bridge-output-cleanup-20261007-c18-4d8270/ADDENDUM-01.md),
SHA `a303533480808ee0bf6a370f5c2fe8a4ff684e6a827e47385d101c010fef05b7`,
records one deletion request rejected by tool policy before OS execution.
All thirteen class files /37,960 logical bytes remain hash-matched; no retry,
alternate deletion or physical reclaim is claimed. Its initial read-only
precheck error is preserved. Old refused targets and sealed evidence are unchanged.
