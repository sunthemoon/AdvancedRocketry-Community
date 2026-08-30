# TEST-REPORT — v0.1.0

## Environment

```yaml
date: 2026-08-31
os: Windows 11
minecraft: 1.20.1
forge: 47.4.10
java: 17.0.8
gradle: 8.8
python: 3.13
artifact_sha256: 07f5c108233ba14dad518a64f4141caa70f2338166b139b31415d6f284b8e6ea
```

## Automated results

| Check | Result | Observed result |
|---|---|---|
| `gradlew clean build` — first run | PASS | Main JAR 95,924 bytes, SHA-256 `07f5c108…` |
| `gradlew clean build` — second run | PASS | Byte-identical main JAR |
| Python test suite | PASS (combined rerun) | 548 current methods: CI passed the prior 547/547; new Git-blob portability case is included in the 22/22 focused v0.1.0 rerun; final-head CI pending |
| JAR audit | PASS | 112 entries; version/metadata/license/resource audit passed |
| Client package-boundary scan | PASS | No common/server import of `net.minecraft.client.*` |
| Strict repository validator | PASS | 20 passed, 0 pending, 0 warnings, 0 failed |
| Exact upstream audit generate/verify | PASS | Stable 18-file output at commit `c5cd5af…` |
| Minimal import generate/verify | PASS | 10/10 source and transformed target hashes match |
| Generated-resource manifest verify | PASS | 27 generated targets match |
| v0.1.0 asset validator | PASS | 10 imported + 27 generated; 14 references; zero missing/case collisions |
| `gradlew runData` | PASS | 39 generated-resource files byte-identical before/after; `git diff --check` clean |
| `gradlew runGameTestServer` | PASS | 3/3 GameTests pass |
| Packaged server lifecycle | PASS | First start/status/save/stop plus same-world restart |
| Packaged matching-client cycles | PASS | Same client joined/disconnected before and after restart |
| Packaged client visual/log review | PASS | zh_cn/en_us, effective scales 3/2, project/linkage findings zero |
| Sources JAR repeat task | PASS | Two forced reruns byte-identical: 77,075 bytes, SHA-256 `33021af8…` |
| Blocking GitHub Actions | PENDING | Run URLs will be recorded before `PASSED` |

## GameTests

The three v0.1.0 GameTests cover:

1. all five content registry entries;
2. machine-casing placement/orientation and inert interaction boundary;
3. machine-casing break/drop behavior.

The first drop test implementation used the GameTest helper's no-drop destroy
operation and failed honestly. It was corrected to invoke the level's normal
`destroyBlock(..., true)` behavior; the rerun passed 3/3 without weakening the
drop assertion.

## Packaged dedicated-server result

The non-player lifecycle run and the later matching-client run both used the
accepted JAR hash. The final matching-client evidence records:

```yaml
session_id: v002-bc17a36c59b68bb86a6603fa
schema_version: 4
manual_player_cycles: true
same_player_verified: true
first_cycle:
  exit_code: 0
  player_join_observed: true
  player_leave_observed: true
  project_errors: 0
  project_warnings: 0
  client_linkage_failures: 0
restart_cycle:
  exit_code: 0
  player_join_observed: true
  player_leave_observed: true
  project_errors: 0
  project_warnings: 0
  client_linkage_failures: 0
same_world_verified: true
```

## Retained failed attempts

Failures were not relabeled as passes:

| Attempt | Result | Resolution |
|---|---|---|
| Initial GameTest drop check | FAIL | Corrected the test action to exercise normal drop semantics, then reran all GameTests |
| First matching-client server installation | FAIL | Three Forge installer attempts timed out while obtaining the mappings artifact; full attempt logs remain in the ignored disposable session |
| Explicit installer-only recovery | PASS | Reused only the validated partial downloads, completed installation, then performed both player cycles |
| First final Python suite | FAIL (3/547) | Later-version worktree was incorrectly used for historical v0.0.2 provenance, and two mocks targeted the superseded mutable helper |
| Focused historical-boundary rerun | PASS | Historical evidence now validates at accepted commit `9359257…`; all four affected/control methods pass |
| PR head `c14d7c5` baseline CI | FAIL | Checksum inventory was computed from CRLF working-tree bytes while Git stores the six evidence text files as LF |
| PR head `c14d7c5` governance CI | FAIL | The same checksum issue plus obsolete v0.0.2 review-packet generation against the v0.1.0 head |
| CI recovery validation | PASS locally | Checksums now bind Git-canonical LF bytes; v0.0.2 packet unit fixtures use the archived commit, and governance forbids regenerating retired review inputs at later-version heads |
| PR head `5370392` Forge CI | PASS | Forge 47.4.10 baseline and 47.4.23 compatibility both pass; accepted main JAR hash reproduced on Linux |
| PR head `5370392` governance CI | FAIL after 547/547 tests passed | Four audit CSV files recorded Windows-smudged upstream worktree byte counts/hashes instead of raw Git blob bytes |
| Audit portability recovery | PASS locally | Audit reads bounded blobs from the exact Git tree, CSV uses LF, regenerated 18-file manifest verifies, and 7/7 audit tests include a clean CRLF materialization case |

No timeout was enlarged and no assertion was removed. The recovery command
used `--resume-install-session`, which refuses a directory containing server
runtime state.

## Client log review

| Run | Broad WARN | ERROR | FATAL | Project WARN/ERROR/FATAL | Linkage failures |
|---|---:|---:|---:|---:|---:|
| zh_cn | 32 | 0 | 0 | 0 | 0 |
| en_us | 10 | 0 | 0 | 0 | 0 |

The broad warnings originate in Forge language-provider metadata, Forge union
resource URLs, first-run Forge config correction, Vanilla sound/shader state,
or a launcher-supplied Netty property. They are not project logger findings.

## Final verification pending

Before `PASSED`, the blocking pull-request CI must run the complete 547-method
suite in one invocation, reproduce the main JAR on Linux, and pass build,
DataGen clean-worktree, GameTest, packaged-server, provenance, resource, release
evidence, and governance checks. Exact run URLs replace the pending entries.
