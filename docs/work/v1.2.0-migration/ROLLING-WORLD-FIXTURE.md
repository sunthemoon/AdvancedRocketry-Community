# Rolling Machine saved-world fixture

```yaml
task: V120-MIG-03B2
status: verified
scope: current_candidate_save_and_restart_fixture
executed_at: 2026-09-26
build: 1.20.1-1.1.1-dev
implementation_base_commit: 3dbe6884e6d8bff3c676c4f7cebee0824b507602
tested_implementation_commit: null
uncommitted_worktree: true
artifact_sha256: 6077fd10695ef387edfda8de6543e51cc4b9deb94d0005fe398dc23ad8ada63a
```

## Source and scope

This fixture is a **new candidate-world scenario**, not an extraction from the
historical Rolling run. The historical [summary](../v1.2.0-rolling-machine/packaged-restart/summary.json)
retains final-region hash `7b7b17caf3cb4dfbe7cff3e2cdf37e824224ffadbae3b85a150341c97d01bb2b`,
but that region was not available for extraction. Its evidence has not been changed.

The new disposable server copied only the existing Forge 47.4.10 `libraries` tree,
then installed the exact candidate JAR above. It generated a fresh world on
`127.0.0.1:56062`, Java 17.0.7, offline mode, 512–1024 MiB heap. No previous server
world or configuration was copied. The [baseline helper](rolling-task/run_baseline.py)
performed real first start, status query, save, clean stop, and same-world restart
using existing dedicated-server harness functions; it did not run the installer.

The [baseline summary](rolling-restart/baseline/summary.json) and
[scenario summary](rolling-restart/scenario/summary.json) bind the same world marker,
startup configuration and JAR. The build includes uncommitted root-worktree changes,
so the base commit is **not** described as the tested implementation commit.

## Observed result

The existing Rolling S2 harness created the controller at `(132,80,132)` and four
ports in chunk `(8,8)`, seeded two iron ingots, 500 mB water and 4000 FE, then:

| Checkpoint | Progress | Input | Water | Energy | Output | Revision |
|---|---:|---:|---:|---:|---:|---:|
| Redstone-paused, flushed before forced stop | 11/100 | 2 iron ingots | 500 mB | 3780 FE | 0 | 6 |
| Same-world forced-stop recovery | 11/100 | 2 iron ingots | 500 mB | 3780 FE | 0 | 6 |
| Resume completed, idle wait, final clean restart | 0 | 0 | 400 mB | 2000 FE | 8 iron bars | 7 |

The complete paused report matched exactly after restart. Final reports matched
after the idle wait and clean restart. Formation remained `FORMED`, generation 1;
the final process was `IDLE`, with no journal and transaction marker
`36940a00-82f2-4f56-8880-4413a8edb968`. The extracted controller and every port binding
share machine UUID `c070794b-4e9c-4bf8-9001-ba991274a598`; lifecycle, binding, process
and port resource roots are schema 1.

The forced process returned exit 1, as required by the harness; both subsequent
server processes exited 0. The baseline's two processes also exited 0. No blocking
project log finding was reported. Baseline first-start has 25 non-project warnings
(primarily generated default configuration); its project ERROR/WARN/FATAL and
client linkage counts are all zero. Full logs are retained in
[full-logs](rolling-restart/full-logs/), not only filtered output.

## Bounded fixture

The [extractor](../../../scripts/v120_rolling_world_fixture.py) checks the final
region's exact size and hash before extracting one unchanged zlib chunk. The
[manifest](fixtures/rolling/manifest.json) binds source summary, region, artifact,
implementation metadata, chunk hash and machine UUID. Verification rejects extra
files, linked paths/parents, changed resources, bindings, process, transform or
DataVersion. Limits: region 16 MiB, summary 64 KiB, manifest 16 KiB, compressed chunk
128 KiB and expanded NBT 1 MiB; trailing compressed bytes are rejected.

| Object | Bytes | SHA-256 |
|---|---:|---|
| Final saved region | 3002368 | `e6f27d28197813de6ed5aaddb3f694310e6c1b532b3299fb3fd282578a97dc9d` |
| Pre-forced-stop region | 3002368 | `d2246df51315ce203df1d60b0c35cacdad9dc9a0ec765277ee1b36fd6d19f72e` |
| Scenario summary | See archive | `051d44f004d1a66d22f88dccf5caa8a94cdd8805fb43c3536ea40b1d4d763090` |
| Extracted chunk `(8,8)` | 6677 | `385f2c53b18a1de138b1e3a6581456fde97a41e99d542f959283a02b795e8274` |

Verification compares parsed NBT values, not numeric tag widths; strict Java codec
tests remain separate. The extracted chunk is a representative input for later
migration/recovery tests, not a complete bootable world.

## Actual commands and results

Executed in the delegated Rolling worktree; paths below are repository-relative
except the read-only source runtime/JAR and Java executable.

```powershell
python -B docs/work/v1.2.0-migration/rolling-task/run_baseline.py `
  D:/GitHub/AdvancedRocketry-Community/.gradle/mig03b-before-clean-20260926/dedicated-server-smoke/prec04b-removal-legacy-20260926 `
  D:/GitHub/AdvancedRocketry-Community/build/libs/advancedrocketry-community-1.20.1-1.1.1-dev.jar `
  --java 'C:/Program Files/Java/jdk-17.0.7/bin/java.exe'
python -B scripts/run_v120_rolling_restart_smoke.py build/rolling-fixture-server `
  --baseline-summary docs/work/v1.2.0-migration/rolling-restart/baseline/summary.json `
  --evidence-dir docs/work/v1.2.0-migration/rolling-restart/scenario `
  --tested-commit 3dbe6884e6d8bff3c676c4f7cebee0824b507602 --tested-worktree `
  --java 'C:/Program Files/Java/jdk-17.0.7/bin/java.exe'
python -B scripts/v120_rolling_world_fixture.py --create-from-region build/rolling-fixture-server/world/region/r.0.0.mca
python -B scripts/v120_rolling_world_fixture.py --verify
python -B -m unittest tests.test_v120_rolling_world_fixture -v
git diff --check
```

Baseline, Rolling S2, extraction and verification exited 0; all **16 tests passed**.
Root integration independently reran the combined 40-test fixture suite and
checked the archived log hashes; see the [integration review](rolling-task/REVIEW.md).
The [unit-test transcript](rolling-restart/fixture-tests.txt) includes archive,
tamper/rehashed Item/Fluid/Energy/process/transform/DataVersion, duplicate identity,
foreign binding, provenance, overwrite, path safety and decode-bound checks.
The combined Rolling and pre-existing Precision fixture suite also passed all
22 tests. Five raw-log hashes were recomputed against their summaries; baseline
and scenario world/artifact identities matched. The 11 retained evidence files
are indexed by [SHA256SUMS](rolling-restart/SHA256SUMS); the duplicate generated
`.log` is excluded in favor of the byte-identical tracked-ready `.txt` transcript.

## Limits and remaining acceptance

- This is candidate-save → same-candidate restart evidence, **not** a previous
  Rolling version upgrade or a reconstructed historical fixture.
- Forced stop happened after an observed `save-all flush`; no arbitrary write-cut,
  host power-loss, cross-chunk Rolling resource atomicity or dropped-item claim.
- No Java source, schema or gameplay behavior changed. This task did not rebuild
  the old worker HEAD or rerun Gradle; root integration owns candidate build checks.
- No remote, soak, real-player, GPU, multiplayer or full-machine campaign was run.
  Those remain scheduled by ADR-018. G0–G9 and full v1.2.0 acceptance remain open.
- Disposable full world lives in the worker `build/rolling-fixture-server`; the
  repository retains only bounded chunk plus raw and summarized execution evidence.
