# Exact-source hosted allocation failure and separate rerun

Date: 2026-10-06. Source: `35a146fbbe1f2de94160f82307d041d2cd26e472`.
Workflow: unchanged `.github/workflows/v180-development.yml`.
[Run 37369035893](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37369035893).

## Attempt 1: failed without product execution

Run conclusion is FAILED; job/check `111961222914` is CANCELLED, with no
executed steps and zero artifacts. The complete check annotations attribute
failure to GitHub not acquiring a hosted runner. They do not establish the
underlying capacity cause, cancellation initiator or any Java/test defect.

[Different-agent metadata observation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17-ci-terminal-observer-20261006-9d574a/REVIEW-01.md),
SHA `cb63e13f85d5961ec62f9ab0305bd0e75599b77343f3a725083ebf8edd9033a3`,
records six bounded GETs, exit 0, 21 checks and 14 unchanged named inputs.
Its selected [API receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17-ci-terminal-observer-20261006-9d574a/OBSERVATION-01.json),
SHA `c6d4e368f7d02981f88e218a984cee0e1e368c3c329d09e1be9055deb05a5871`,
retains all four annotations and the complete empty step/artifact inventories.
No logs, XML or JAR download occurs. This is execution-availability evidence,
not a product regression cohort, passed diagnostic fixture or Gate.

## Separate ordinary rerun

Root makes exactly one ordinary full-workflow rerun request after that
allocation failure, through the [documented API](https://docs.github.com/en/rest/actions/workflow-runs#re-run-a-workflow).
The [request receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/observer-ci-root-rerun-20261006-01/RERUN-01.json),
SHA `59c9edc0856d8fa85f4c493174f640706aff8581d634bc06de0d4f2b4d5b8d05`,
records HTTP 201 /exit 0. No workflow, source, budgets, assertions, permissions
or debug logging change; no failed test is selected away. The immediate API
response still shows attempt 1 queued, not proof of the new attempt number.

A subsequent [Root API observation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/observer-ci-root-20261006-01/OBSERVATION-04.json),
SHA `aeab681a827b28b0385d08fc856a283fb1f38e7782eee8504e1ef7ec0a7d6839`,
binds **attempt 2**, job `111969601905`, IN_PROGRESS with clean build running.
Setup/host/tooling steps report success; build/DataGen/GameTest conclusions and
artifact evidence are not yet available. Later outcomes require a new record.

Attempt 1 remains FAILED/no product execution. The preceding fully executed
[058cd67d cohort](RESULT-07.md) remains FAILED with three required GameTest
failures and its original metrics. Neither rerun acceptance nor a future
success can erase those observations. Diagnostics are not a production fix;
native recovery, actual clients, remaining content, R-021 and G0-G9 stay open.
