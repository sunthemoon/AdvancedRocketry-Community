# V130-ROCKET-03B independent packaged execution

## Result and scope

PASS for the six fixed short clean-process observations, not release approval.
The integrated runner and separate raw-evidence postcheck both exited 0.
Execution ran from 2026-09-26 15:53:44 through 15:55:43 UTC on Java 17.0.7,
Forge 47.4.10, Minecraft 1.20.1, offline loopback port 63541.

The initial attempt remains separately retained as a FAIL; this corrected run
used a new world and only reused previously hash-checked runtime libraries.
No initial world/config/mods state was copied. No tests, timeouts or production
expectations were relaxed during either execution.

## Identities

- Integrated source: `959c1f7a44e7a7a07979d2fab185c08647b459f9`.
- Runner SHA-256: `d6f69534c9d2f2907b5513158c8cd214a8f013ec2a3595ecb68b684d019845d6`.
- Host SHA-256: `3f8be1e0adce70b1c25fc1eeeffeac5b7f398016abf6670af11b42c52c84c200`.
- Fixture SHA-256: `8144ded5d03764cdd6b1eda8ff5e778f7dd87c564ebde3e5dfe20d382629085f`.
- The immutable inputs and final installed JARs match those hashes.
- `source-snapshot/` preserves the executed runner and imported helpers;
  `script-identities.json` lists actual repository sources/hashes, unchanged
  in the final check.

Exact Python launch is in `launch-command.txt`. Every JVM command is captured
in its phase's `launch.json`; these are normal packaged `forgeserver` launches
with the existing release-test-hook property, never a substituted manager or
development GameTest launch.

## Actual observations

| Phase | JVM PID | Exit | Observation ticks | WARN | ERROR |
| --- | ---: | ---: | ---: | ---: | ---: |
| assemble | 2112 | 0 | 22 | 25 | 0 |
| entity-restart | 22512 | 0 | 22 | 10 | 0 |
| provider-skipped | 16216 | 0 | 22 | 10 | 0 |
| mod-uninstalled | 7796 | 0 | 22 | 14 | 2 |
| provider-reinstalled | 20484 | 0 | 22 | 10 | 0 |
| container-restart | 14624 | 0 | 28 | 10 | 0 |

Counts above are from full per-process stdout; debug/latest logs were audited
separately and retained. The two uninstall ERRORs are exact Forge
`GameData/REGISTRIES` diagnostics: the `minecraft:block` unidentified-mapping
header with only `arce_adapter_test:cargo_container`, and the subsequent
continue-processing summary. They are explicitly reported, not zeroed out.
All other ERROR/FATAL, project warning/error and linkage findings are absent.
No tick-lag warning appeared. Baseline warnings include new config defaults,
Forge library metadata, offline mode, asset URL schemes and uninstall notices.

- Assembly used the real production command, catalog, manager and tick service.
  The four-block rocket contains a separately packaged non-Chest/Barrel cargo BE.
- Raw entity persistence retained adapter `arce_adapter_test:cargo_inventory`,
  payload version 1, 17 diamonds with the exact custom display-name metadata,
  and 64 iron ingots. Its UUID was `661210d6-d752-4853-832c-7b803f0591ed`;
  content hash was `2218b1eda790356d630b63c931c261d77a22b76d57b298832ad44c0ba6d45d56`.
- Provider-skipped registration retained the fixture block/BE/tag but refused
  disassembly as `UNSUPPORTED_BLOCK_ENTITY`, with no world mutation.
- The synthetic EXTRACTING journal and complete opaque entity data survived
  actual fixture JAR removal unchanged. Recovery reported CONFLICT, and the
  existing journal prevented a competing disassembly with REGION_BUSY.
- Reinstalling the exact JAR restored all four blocks and exact native cargo
  inventory, removed the rocket, and cleared the journal. Another clean restart
  retained the native two-slot BE inventory with no repeated recovery or drops.
- Actual Forge status mod sets and debug loaded-JAR sources matched every phase.
  Registration occurred once with API 1.1 except intentional skip/uninstall.

`independent_postcheck.py` reread raw region/entity/journal files rather than
accepting runner PASS booleans. Its output, exit and observations are in
`independent-postcheck-stdout.txt`, `independent-postcheck-exit.json` and
`independent-observations.json`. The owned Python/JVM PIDs were absent and the
endpoint was closed afterward. No unrelated process was stopped.

## Evidence and limitations

- `recovery-evidence/`: six full stdout/debug/latest logs, exact launch/commands,
  status, phase results, actual NBT region/entity/journal/level captures and hashes.
- Native `SHA256SUMS` SHA-256:
  `93f0a3595dc84ff3a9a7fd6ea4de9266c6d380a0ae5c78e881a7d13a3e9eb3d5`.
- Summary SHA-256:
  `f66fd68ad0346bd01128c853f5aeec56ee6a9a3be28c9deac36bc6dad8a47f6f`.
- Supplemental observation SHA-256:
  `db4e7983e4d5924c9105f2f2fba6d2546a9a9b5ddb7bb2e728e450018d6b94e5`.

Archive `recovery-evidence/`, `source-snapshot/` and all top-level supplemental
files. Exclude `server/` and `artifacts/`; their relevant captured bytes and
identities are already included. Both disposable worlds are retained in Temp.

This is one bounded external-container fixture, six clean processes and a
synthetic pre-commit journal. It is not an actual crash/power-loss test,
long-load, remote/client, broad third-party-mod compatibility or full original
feature acceptance. No repository edits, Gradle, new production hooks or Gate
approval were performed by this verifier. Current-version remaining work and
Required Gate decisions remain with the integrator.
