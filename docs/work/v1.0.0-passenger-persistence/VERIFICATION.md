# v1.0.0 occupied rocket world persistence

Date: 2026-09-06, Asia/Shanghai. Branch `codex/v1.0.0-stable-core`, development
worktree based on `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`.
No frozen candidate, commit, tag, release or human Gate approval.

## Scope and result

**Verified:** new occupied rocket saves stay world-owned across passenger
logout, dedicated restart, two real client logins and a second save. The same
entity UUID, logical identity, snapshot, two seat assignments, fuel and cargo
remain present once. This fixes the cause of the preceding new-world restart
failure, not merely its old-UUID assertion or warning filter.

**Still incomplete:** older player-embedded `RootVehicle` migration,
continued-use/return coverage, initial handshake reliability and the remaining
v1.0 Required Gates. No later-version feature, remote server access,
authentication/launcher setup or long-load suite was undertaken.

## Evidence-led diagnosis

The prior failure is preserved unchanged in
[passenger-duo](../v1.0.0-passenger-duo/VERIFICATION.md). Its stopped fixture's
owner player NBT contains the landed rocket in `RootVehicle`, including the
two assignments and 17 diamonds; the passenger file has no embedded vehicle.
This is not evidence of lost inventory.

Inspection of the locally pinned Forge 47.4.10 mapped runtime showed three
uses of the inherited `hasExactlyOnePlayerPassenger` predicate:

1. `ServerPlayer` embeds that vehicle into player NBT.
2. `Entity.shouldBeSaved` excludes it from world entity saving.
3. `PlayerList.remove` unloads that root vehicle with the last player.

The independent transfer journal then finds no destination in world storage
and reconstructs one, while the older embedded vehicle remains in player NBT.
The native failure therefore exposed conflicting persistence ownership, not
just a changed presentation UUID. The Forge logout event ordering is also
visible in its [official PlayerList patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/patches/minecraft/net/minecraft/server/players/PlayerList.java.patch);
the pinned local bytecode, not that moving branch, was used for this version's
decision. No Minecraft/Forge implementation code was copied into the mod.

## Implementation and compatibility

`RocketEntity` overrides the vanilla single-player-vehicle persistence
predicate to return false, with an explanation at the override. Rockets remain
world-owned regardless of whether zero, one or two players are online.
Passenger assignments and reconnect authority remain in the existing flight
data/journal. This does not change seat capacity, boarding validation or fuel.

`RocketTransferRecoveryService.Result.log` keeps the existing event and fields.
Only successful COMMITTED / KEEP_DESTINATION / zero sources / one destination
with that destination already LANDED emits INFO. Missing entities, rebuilding,
duplicates, interrupted phases, failed status or non-landed state still WARN.
The strict log scanner is unchanged. No failed recovery warning is filtered
out or reclassified as a passing restart.

Save schema, persistent IDs and flight protocol **5** remain unchanged.
Existing embedded player saves are **not** declared migrated by this override;
they have a separate remaining task. No ASM, Access Transformer, new network
message, dependency, copied asset or unbounded lookup was introduced.

## Regression and commands actually executed

Environment: Windows, Oracle Java 17.0.7, Gradle wrapper 8.8, Forge 47.4.10,
Python 3.13.15 from `D:\python\pyenv\pyenv-win\versions`.

| Command / attempt | Result and retained log |
| --- | --- |
| First `gradlew.bat runGameTestServer --no-daemon` | Exit 1, 11 s: new test used incorrect import packages; no behavioral verdict. `before-gametest.txt` |
| Second same command | Exit 1, 40 s: invalid doubly namespaced template ID in new test. `before-gametest-2.txt`, raw native log |
| Third same command, corrected test before product fix | Exit 1, 46 s: new mounted-world-save assertion fails. Three existing tests also fail in the reused world after the preceding crash. `before-gametest-3.txt`, raw native log |
| `gradlew.bat clean build --no-daemon` after fix | Exit 0, 34 s; **397 Java tests / 77 XML suites**, zero failures/errors/skips. `clean-build.txt`, `java-results/` |
| `gradlew.bat test runData runGameTestServer --no-daemon` | Exit 0, 1 m 50 s; generator written 0, **all 45 required GameTests passed**. `after-checks.txt`, raw `after-gametest-native.log` |
| Native world-owned driver and BOARD/flight/reconnect probes | Exit 0 each; three Java exits 0, cleanup errors empty. Native owner initially timed out, then retried in the same process; flight/reconnect assertions succeeded |
| `python .../arce-passenger-world-inspect.py <stopped-world> <saved-world>` | Exit 0: one world rocket, zero player-embedded vehicles |
| Native restart driver on a copy of the stopped server | Exit 0; three Java exits 0; original UUID loaded and both real clients remounted without command repair |
| Same inspector on the resaved restart world | Exit 0; same one world rocket and zero embedded vehicles; projected rocket state identical |
| `git diff --check` | Exit 0 |
| `git diff --exit-code -- src/generated` | Exit 0 |
| `git diff --exit-code` | Exit 1: inherited and new changes remain; not a clean-tree Gate pass |

The three other failures in the third before-run were Earth/station travel
(`No value present`), sixteen vents (warmup convergence), and ten stations
(`PLATFORM_BLOCKED`). They are retained rather than attributed to the passenger
fix. No assertions, timeout or performance bounds were changed. The clean
build removes the failed disposable GameTest world; its following full run
passes all 45, including those tests.

New `RocketPassengerPersistenceGameTests` exercises vanilla player saving and
world-save eligibility at zero/one/two online riders, then one remaining rider,
and checks rocket NBT round-trip identity/snapshot/manifest. It failed on world
exclusion before the override. Two `RocketTransferRecoveryLogTest` cases verify
the INFO payload and exhaustively retain WARN for other phase/action/status/
count combinations and non-landed state. Actual native logout/restart provides
the lifecycle evidence; the GameTest alone is not presented as a full restart.

## Exact artifact and native fixture

JAR SHA-256:
`e8a066ca4be3b4fcf179fad88a72b502ba65d8c8b6695bcba5aca04293739af0`.
Size: **1,262,050 bytes**. `source-inventory.json` binds **742** input files.
The earlier failed native restart used a different artifact; its evidence was
not overwritten or rebound to this build.

Fresh disposable dedicated world, two Windows real-GPU clients (RTX 3070
Laptop GPU), 1280 x 720. Profiles `V100Visual1` and `V100Visual2` are offline,
creative and non-OP after setup; both ops snapshots remain empty. The harness
uses `operator` as the second-window selector only; neither actor is an OP.
This is not a new online-authentication or survival/equipment claim.

- Source: `895687d0-0e34-4375-8b6f-e98b687f1ec3`.
- Logical rocket: `15d78efc-025a-4e27-9ddc-22451f4f0782`.
- Landed/restarted entity: `0316d87e-2e28-4dc5-a6f0-a554759b102a`.
- Transfer: `749583bc-cde1-4521-92db-260698b8ab3c`.
- Destination snapshot hash: `eb0f5513248453439523a73ad78f5fd93b40073c1aec3634e1d23745102c3196`.
- Six blocks, two seats, chest with **17 diamonds**, fuel **1000 - 382 = 618**.

Both clients used actual BOARD buttons, and the owner used LAUNCH. The harness
kicked only the passenger after countdown completed, before destination spawn.
The owner landed while the passenger was offline; the same passenger PID
reconnected through native Direct Connect. Both vehicle checks bind to the
landed UUID. F3 screenshots show owner Y=81.15 and passenger Y=81.15; their
displayed X positions retain the center/secondary-seat offset distinction.
Server passenger X is the vehicle center, so no all-XYZ equality is claimed.

The initial owner login timed out before fixture setup (server recorded
Disconnected); screenshots `00-owner-initial-disconnect.png`, `00-owner-direct.png`
and `00-owner-retry-address.png` preserve the native retry. No client process
was restarted or login wait extended. The passenger initially joined without
retry. This remains handshake reliability evidence against broad acceptance,
even though the subsequent gameplay succeeded.

Native run Java PIDs: server 17268, owner 3452, passenger 25008; all exit 0.
After known Moon chunk activation, both clients closed and the dedicated server
saved/stopped normally at 04:52:00. Bounded read-only NBT inspection archives
the actual player files and entity-region bytes in `saved-world/nbt/` and
derives `projection.json`: exactly one world rocket, no embedded player vehicle.

## Restart, reconnect and resave

The restart harness copied that stopped server into a new owned temporary
directory, preserving the first world. It used the same artifact, not a fresh
replacement world or manually reconstructed rocket. At 04:52:41 recovery found
zero sources and **one** existing destination, KEEP_DESTINATION. The original
entity UUID and exact logical identity loaded; no REBUILD_DESTINATION occurred.

Both new client processes joined the restarted server normally. Commands
`on vehicle if entity ... UUID` confirm both are mounted to the original
rocket, without teleport/mount repair. A loaded Moon entity-count condition
reports one rocket; exact manifest and snapshot block receipts are archived.
`01-owner-restart.png` and `01-operator-restart.png` visually confirm both at
Y=81.15 with their original seat offsets. No flight command was replayed to
produce this recovery result.

Restart Java PIDs: server 19220, owner 12420, passenger 26244; all exit 0.
After closing both clients, normal save/stop completed at 04:54:56. A second
NBT inspection (`resaved-world/`) again finds exactly one world rocket and no
player-embedded vehicle. Its full rocket projection equals the first save,
including both assignments, snapshot blocks, 17 diamonds, fuel and position.
All six owned native Java PIDs were checked absent. Both strict native log
audits pass; no hidden ignore rule was introduced.

## Evidence integrity, files and remaining work

`verification/audit.py` checks exact source/JAR hashes, 397 Java/45 GameTest
results, retained failures, native process exits/log hashes, PNG receipts,
empty ops lists and exact persisted projections. `checksums.txt` binds this
report and evidence (excluding itself and generated audit output). Its scoped
result is `PASS_SCOPED_PERSISTENCE_EVIDENCE`; coordinate observations are manual
image review, not asserted by OCR. This is not independent release review.

Product changes: `RocketEntity.java`, `RocketTransferRecoveryService.java`.
New tests: `RocketPassengerPersistenceGameTests.java`,
`RocketTransferRecoveryLogTest.java`. Changelog, provenance, implementation log
and current-version next action are updated; unrelated work is preserved.

`V100-VISUAL-PASSENGER-WORLD-SAVE` is verified. The parent remains in progress:
`V100-VISUAL-PASSENGER-LEGACY-VEHICLE` must handle a copy of older embedded
player data, then verify continued use/return. Existing embedded data may still
conflict with a rebuilt journal authority; this report does not waive that risk.
All v1.0 Required Gates are **not** satisfied; status remains **IN_PROGRESS**.
