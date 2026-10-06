# Solar surface fixture source checkpoint

Date: 2026-10-06. Scope: the existing surface GameTest's setup and restoration
under the [reviewed task](../v1.8.0-c17c-solar-generator/SURFACE-FIXTURE-TASK-01.md).
No production, persistence, public API, resource or workflow behavior changes.

## Published source and design

Root commits the independently reviewed one-file postimage as
`dd4192bc72fdf941c2b4fedd40b02c2dd15cdd16`, parent
`a34de0ad5edb0a2b3efe6ad2bb76c17e40fafc43`, then normally merges and non-force
pushes `e0c601e4d58cb906d79f744070316c0a2d810c06`. The main and source worktree
postimage, and both fixed Git objects, have identical 36,145-byte source / SHA
`e188d1cde57fb8d64c33e717333f5cc72609ff2fd23fca58ac25bb5de5d8e484`.
The [publication receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-solar-surface-publication-20261006-01/PUBLICATION-01.json)
records commit/merge/push exits, fixed-source identities and verified remote SHA.

The only changed source is
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/SolarGeneratorGameTests.java`.
Its actual delta is 82 additions /26 deletions; the resulting class is 482 lines.
The original test X/Z and already-loaded chunk are retained. The registered
generator uses Y=maxBuildHeight-2, its roof Y=maxBuildHeight-1. Bounds and loaded
state are checked before reading; both exact states and time/weather are
captured before mutation, and existing BlockEntity data is refused.

All six bounded restoration actions are attempted on synchronous and existing
asynchronous exits. Success occurs only after restoration and readback;
cleanup-only errors fail, while earlier assertions remain primary with cleanup
errors attached. Idempotence prevents duplicate attempts, not failed-operation
recovery. Original native sky/day/weather/roof/night, stored export and menu
oracles, diagnostics, ten GameTest annotations and 40-tick deadline stay intact.
There is no added ticket/loading, lighting drain/seed or heightmap substitute.

## Actual independent review and remaining verification

The [different-agent actual-source review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/solar-surface-source-review-20261006-5c7261/REVIEW-01.md),
SHA `0bd3fd8b2432d7c13329aeaf441c3a16642d13edf645973bda8b6e5bca336d4a`,
finds no introduced Critical/High/Medium/Low in these exact postimages before
their subsequent commit. Seven fresh static controls pass. Eight cleanup-order
cases are Python models, not Java/native execution. One expressly granted cached
Java 17 compilation of the candidate plus 13 fixed dependencies exits 0 in
5.2492554 seconds, with complete bounded streams and deprecated-API notes only.
It uses proc:none, empty sourcepath, 256 MiB and a 120-second bound. Input/spec
and measured cached-main identities do not drift. No JVM tests or native world
are run locally; C remains below the full-build capacity threshold.

An outer quoted-version preflight failed before any Java/helper launch; that
tool-only failure is retained separately. The actual compiler executes once,
not a retry. Its 24 class outputs /208,871 logical bytes and home/sourcepath
directories remain retained; no cleanup or physical-space claim is made here.
Root normally retires only its clean merged sparse source worktree after reader
release. The [retirement receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-solar-surface-publication-20261006-01/WORKTREE-RETIREMENT-01.json)
records exit 0 and verified absence, retaining the source branch/history.
This does not remove compiler outputs, sealed evidence or old refused targets;
physical reclaimed space is unmeasured.

The unchanged unfiltered [hosted workflow](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37484998908)
binds source `e0c601e4d58cb906d79f744070316c0a2d810c06`, attempt 1,
job `112342646260`. The dated [metadata observation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/solar-surface-hosted-observer-20261006-01/OBSERVATION-01.json)
records IN_PROGRESS, clean build running; later steps have no result yet.
This is not a completed build/GameTest result or evidence of native restoration.
No workflow, assertion, timeout, budget or test selection was changed.

Native light publication/restoration, restart/client, whole C17c delivery,
R-021 and G0-G9 remain open. The previous two-required-failure cohort and all
older failed observations remain failed; no unique historical cause is closed.
