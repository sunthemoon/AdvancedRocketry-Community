# v1.0.0 passenger position and native reconnection evidence

Date: 2026-09-06 (Asia/Shanghai). Branch: `codex/v1.0.0-stable-core`.
Development worktree based on `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`;
not a frozen candidate, tag, release or human Gate approval.

## Result and boundary

The native client rider-height defect is fixed and verified. The broader
passenger/persistence task remains **IN_PROGRESS**: the subsequent short
dedicated restart **failed**, and continued use/return was not exercised.
There was no long-load suite, remote host access, authentication setup or
access to launcher credentials. Prior user-reported PCL login is not newly
claimed evidence here. No v1.1+ feature was implemented.

## Observed defect and implementation

Before artifact:
`46cb577653981a50638ff3d2bc32d0407dc4adaa5d7092fe73e65ae82bcd229c`,
flight protocol 4. The owner and passenger used real BOARD controls; the owner
launched to Moon, and the passenger was disconnected during ascent. After
landing, the owner's actual F3 display remained at **Y=129.15**, while server
position was **Y=81.15**. The reconnected passenger displayed Y=81.15.
See `verification/native/14-owner-look-down.png`, `15-owner-debug-settled.png`,
`15-passenger-debug.png` and server receipts 91/92. The attempted owned-window
mouse movement did not change pitch: its filename is not evidence that camera
look control succeeded. The old run's `COMPLETE_WITH_OBSERVATIONS` is a driver
lifecycle result, not a passing visual verdict.

`RocketEntity.positionRider` previously required server-only `flightData` even
on the client, so the client skipped passenger positioning. The fix publishes
one capacity integer and a fixed array of 16 optional passenger UUIDs using
the rocket's own `SynchedEntityData`. The client receives seat identity, not
the full flight authority or persistent NBT. Dynamic entity data uses Forge's
documented synchronization mechanism; see [Forge entity networking](https://docs.minecraftforge.net/en/1.20.x/networking/entities/).

`RocketPassengerPosition` shares the existing seat geometry: primary seat at
the rocket center, secondary seats on a 0.35-block radius, all at +1.15 Y.
The zero-capacity pending-metadata fallback follows the center. Capacity and
indices are bounded; vacant metadata slots are cleared. Server invalid-flight
guard, authorization, fuel calculation, transfer logic and save schema remain
unchanged. Exact-match flight protocol advances to **5** for entity metadata
compatibility; prior plan packet lengths remain 22/38 bytes. New content is
community-authored, with no imported upstream code/assets.

## Fixed native run

Artifact: `e7511d8896b2d700d7834325c390886f6841397c47bf16b445c02e191234dba3`
(1,257,973 bytes). `verification/source-inventory.json` binds 740 source inputs.
Fresh disposable Forge 47.4.10 dedicated world; two actual Windows clients,
1280 x 720, RTX 3070 Laptop GPU, separate offline profiles. Both players were
creative and non-OP after fixture setup; both ops snapshots are empty. This
does not establish authenticated or survival/equipment acceptance.

- Owner: `62bbb9cb-b2fa-39aa-9a6b-430b71f5493f`, seat 0.
- Passenger: `6482f9f6-ff26-3046-b7ba-86db04b972f8`, seat 1.
- Source entity: `193edd8f-526d-4314-bdb7-f28ca34e9755`.
- Logical rocket: `0ce64af6-4ee0-4c9b-b019-08c2b512bfe4`.
- Landed entity: `dc69c542-80f6-47e6-98d8-6bbb7f4eb6e1`.
- Transfer: `d4064015-8648-4ce4-aac1-f022d5cf6e2c`.

Two actual BOARD successes and one owner LAUNCH success are actor-bound in
`fixed-native/boarding-records.json`, `flight-records.json` and server logs.
Cost is 382; fuel goes from 1000 to 618. The six-block fixture has two seats.
Source and landed manifests preserve both UUID/seat assignments. Source and
destination snapshot hashes differ because their origins differ; no false
cross-dimension snapshot-hash equality is asserted. Cargo was not independently
checked in this slice.

At 04:25:01 countdown completed and the harness explicitly kicked only the
passenger; departure precedes destination spawn at 04:25:06 and landing at
04:25:10. Native owner screenshot `06b-owner-moon-coordinates.png` now shows
**Y=81.15**, matching the server while the passenger is still offline. At
04:26:21 the same passenger profile/PID reconnected through the native Direct
Connect screen, with no server teleport or mount repair. Server `on vehicle`
checks bind both players to the same landed UUID. Screenshots
`09b-passenger-coordinates.png` and `09c-owner-coordinates.png` show both at
**Y=81.15**; passenger client X=8.15 and owner X=8.5 reflect their seat offsets.
Server passenger Pos reports X=8.5, so this evidence establishes matching
height and vehicle/manifest identity, not equality of all client/server XYZ.

Both clients joined initially without retry in this run; that does not
resolve earlier artifact/run handshake failures. The fixed driver completed
04:23:14–04:26:57, all three owned Java exits 0, cleanup errors empty, strict
log scan passed. Raw native logs, F2 PNGs, PID-specific control helpers,
requests, action receipts and final options are preserved in `fixed-native/`.
The before run's three Java exits were also 0; its visual failure is retained.

## Failed short restart: unresolved, not waived

After both clients disconnected, the dedicated server saved/stopped normally.
The known Moon chunk had been explicitly force-loaded for this fixture.
`arce-passenger-fixed-restart.py` then started that stopped server with the
same artifact, expecting the landed entity UUID to reload before requesting
its exact report/manifest.

Instead, at 04:33:14 recovery reported COMMITTED, source_count=0,
destination_count=0, `REBUILD_DESTINATION`, `RECOVERED`, with a project WARN.
It created entity `c7e43cc6-26eb-4268-a262-72501b30a7e2` under the same logical
rocket, with LANDED state, fuel 618 and the same destination snapshot hash.
Waiting for the old UUID timed out after the original 30-second bound. The
harness exited 1 and its finally block aborted its owned Java process (exit 1).
No manifest-report or post-restart client assertion was reached.

The raw `restart/summary.json` retains `IN_PROGRESS` because the harness lacks
an exception-status assignment; the terminal traceback and exits establish
**FAIL**, not an unfinished running test. That raw summary was not rewritten.
The strict scanner still flags the recovery warning. No timeout, assertion or
warning filter was relaxed, and no replacement run is counted as passing.
Investigating vehicle persistence/recovery and unique authority is remaining
work; this observation alone does not establish lost cargo, duplication or
the underlying cause. The failed attempt changed its disposable world, which
must not be reused as an untouched pre-restart fixture.

## Tests and actual commands

Java 17.0.7; Python 3.13.15 under `D:\python\pyenv\pyenv-win\versions`.
Commands run from repository root unless the owned fixture script specifies
its dedicated directory. Logs are under `verification/`.

| Command | Actual result |
| --- | --- |
| `gradlew.bat test --tests '*RocketPassengerPositionTest' --tests '*RocketFlightPlanPacketTest' --no-daemon` | Exit 0, 14 cases, 19 s; `targeted.txt` |
| Initial `gradlew.bat clean build --no-daemon` | Exit 0, 24 s; `clean-build-initial.txt`; before preserving the server-null guard, not the fixed native artifact |
| Final `gradlew.bat clean build --no-daemon` | Exit 0, 32 s; `clean-build.txt`; 395 tests, 76 XML suites, zero failed/error/skipped |
| `gradlew.bat test runData --no-daemon` | Exit 0, 23 s; `test-runData.txt`; generator wrote 0 files |
| `gradlew.bat runGameTestServer --no-daemon` | Exit 0, 1 m 27 s; all 44 required tests passed; `gametest.txt`, raw `gametest-native.log` |
| Before native driver + board/flight/reconnect probes | Command exits 0; all 3 Java exits 0, but owner-height visual failure |
| Fixed native driver + board/flight/reconnect probes | Command exits 0; all 3 Java exits 0; corrected height and native passenger reconnection |
| `python .../arce-passenger-fixed-restart.py` | Exit 1; owned Java exit 1; old entity UUID not observed after recovery rebuild |
| `git diff --check` | Exit 0 |
| `git diff --exit-code -- src/generated` | Exit 0, including all generated outputs |
| `git diff --exit-code` | Exit 1: inherited and new development changes; clean-tree Gate not passed |

New `RocketPassengerPositionTest` covers pending metadata, all primary
capacities, secondary seat geometry, and invalid bounds (four tests).
`RocketFlightPlanPacketTest` now requires protocol 5. Native before/after
observations, rather than pure math tests alone, verify the callback defect.
Archived Java XML is from the final build. All seven owned native PIDs from
before/fixed/restart runs were checked absent in `process-cleanup.txt`.

`python docs/work/v1.0.0-passenger-duo/verification/audit.py` verifies source,
artifact, log, receipt, PNG and checksum integrity. Its explicit result is
`PASS_EVIDENCE_INTEGRITY_WITH_FAILED_RESTART`, never persistence or release
approval. F3 coordinates were visually inspected, not derived by that audit.
The checksum list excludes itself and the generated audit output.

## Files, remaining work and Gate decision

Product changes: `RocketEntity.java`, new `RocketPassengerPosition.java`,
`RocketFlightNetwork.java`; tests: new `RocketPassengerPositionTest.java` and
updated `RocketFlightPlanPacketTest.java`. Changelog, v1 provenance,
implementation log and current-version next action describe this slice.
Other existing worktree changes were preserved.

**Not all Required Gates are satisfied.** v1.0.0 remains IN_PROGRESS; no Gate
approval, commit, tag or release. Passenger restart/continued use and broader
V1/V2, frozen-candidate and human acceptance remain open. Next work is limited
to v1.0 passenger restart recovery and subsequent continued-use/return coverage,
without long-load testing or expanding into a later version.
