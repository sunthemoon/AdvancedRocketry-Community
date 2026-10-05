# Failure-observer source publication

Date: 2026-10-06. Status: implemented-unverified.
Tasks: V180-SOLAR-OBS-01 and V180-PLANETARY-OBS-01.

## Published source

| Source | Source commit | Integration commit |
| --- | --- | --- |
| Solar stage/cache observation | `3caa46f52f66b3b72b3c96e48ed02c6cde5a4953` | `d4728b3914a5aa5df3e4094b132f7b8e6fcc7570` |
| Planetary state before cleanup | `d2e28a43bd2e1fd8901cf4f9593588d774d4934d` | `35a146fbbe1f2de94160f82307d041d2cd26e472` |

Root commits both independently reviewed single-file deltas in isolated
worktrees, merges, normally pushes and verifies the remote at `35a146fb`.
Both committed blobs match their released postimages; the
[publication receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/observer-ci-root-20261006-01/PUBLICATION-01.json)
records full hashes/commit roles. No production/schema/resource change occurs.

## Actual checks and pending cohort

[Different-agent actual-source review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/regression-observers-source-review-20261006-87a3d1/REVIEW-01.md),
SHA `6188de265ab99e4694c5958d926b8cb4016d81a489557b5f5106798a25a8d57b`,
finds no introduced C/H/M/L and completes 41 bounded controls. Exact inverses
recover both fixed058 preimages: 26 planetary/55 Solar assertions, 14 test
declarations, original delays/deadlines and cleanup remain unchanged. Original
failures are rethrown; observer/logger failure cannot turn them into success.
Solar attempts one 338-byte scalar message; planetary at most two, normal
payload at most 1,170 bytes. Bounds exclude logger prefix/backend/transport.

One independent cached javac succeeds, exit 0 /5.9740758 seconds, Java 17,
256 MiB/120-second limit, no annotation processing and empty sourcepath:
two candidates plus 13 fixed dependencies. Deprecated notes and 141 unchanged
input pins remain recorded. This is not a clean build, JVM/native test or Gate.

[Hosted run 37369035893](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37369035893),
attempt 1 /job 111961222914, binds exact source `35a146fb`. The last
[API observation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/observer-ci-root-20261006-01/OBSERVATION-02.json)
reports QUEUED, no step/result. Later outcomes require a separate record.
[Preceding complete cohort](../v1.8.0-ci/RESULT-07.md) stays FAILED with three
required failures; adding diagnostics establishes no unique cause or repair.

## Cleanup and unfinished work

Root normally retires both clean merged worktrees; branches/history remain.
The first shell stops after Solar retirement on a postcheck regex error, without
a standalone raw export. A literal-string successor confirms Solar absence and
retires planetary: 9,490 files /536,946,720 logical bytes.
[Retirement receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/observer-ci-root-20261006-01/WORKTREE-RETIREMENT-02.json)
qualifies the original tool observation; physical reclaimed space is unmeasured.
The reviewer's guessed-executable cleanup fails before its native body. A
separately authorized same-shell correction removes only 25 classes /233,024
logical bytes and 12 empty directories, with all three scratch targets absent
and old seals unchanged, as attributed in its
[cleanup report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/regression-observers-compile-cleanup-correction-20261006-91bc02/REPORT-01.md).
This is factual attribution, not another-agent technical cleanup approval.
No old policy-refused target is retried or declared cleaned.

Actual hosted diagnostics/outcome, evidenced fixes, native recovery, real
clients, remaining content, ledger closure, R-021 and G0-G9 remain open.
