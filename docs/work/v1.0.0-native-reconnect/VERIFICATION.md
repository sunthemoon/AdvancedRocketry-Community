# v1.0.0 controlled native reconnect queue

2026-09-06, Windows, branch `codex/v1.0.0-stable-core`, development tree based
on `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`. This follows the verified
logout fix and release handoff. No product source, resource, save schema or
flight protocol changes; no remote deployment, authentication or long soak.

## Exact artifact and controlled boundary

All three native instances use the same packaged JAR, 1,278,474 bytes, SHA-256
`569f41ab59d953e3b4fad3c1b00588705b260fc0b73f1deaf829024bc603e381`.
All 747 input hashes still match the logout inventory. Source inventories,
instance JARs, logs and the post-test rebuild are rechecked by `audit.py`.

An external Java JDI process attaches only to the owned server's loopback
debug port. It uses [JDI early return](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jdi/com/sun/jdi/ThreadReference.html#forceEarlyReturn(com.sun.jdi.Value))
to return false from the entity-readiness query when its immediate caller is
the production `tryReconnect`. The observed SRG method is `m_143319_(J)Z`;
the archived recovery bytecode confirms this call site. The probe never
redefines classes, sets world fields or changes the JAR. It deliberately
changes readiness responses in this test process: this is not an untouched
runtime, a genuine disk-latency experiment or a performance measurement.

Initial controls return false for four readiness checks per player and then
allow the ordinary method to execute. A separate held-login case disconnects
the owner while pending. The gate is then opened for a final ordinary rejoin.
All breakpoint events resume in `finally`; probe disposal removes instrumentation.
The probe has an eight-minute bound and the interactive control loop seven
minutes after both clients join. Neither deadline was reached or enlarged.

## Native observations

Server PID 1052, owner PID 22016, passenger PID 25840, probe PID 18220.
Forge 47.4.10 / Java 17.0.7, JEI absent; server binds `127.0.0.1:58353`,
debugger binds `127.0.0.1:58354`. Offline mode is confined to this disposable
local fixture. Original server/world remains unchanged; 59 seed files are
hashed before/after. The session ran approximately seven minutes including
startup, UI navigation, capture and normal shutdown, not reference-load soak.

| Case | Actual result |
|---|---|
| Owner initial login | Four false readiness replies; queue offer, tick retries, one move only after ordinary readiness execution, completion and empty queue |
| Passenger initial login | Same sequence independently; one move to the same journal-bound rocket |
| Waiting-player logout | Held owner is enqueued; the watcher sends a native server kick after observing three held checks. Four checks occur before queued kick execution; logout immediately completes the pending UUID and the next queue observation is empty. No move occurs during this held session |
| Final owner rejoin | Same client PID uses native Direct Connection, with the gate open; one further move and immediate completion. The passenger receives no additional move |

The probe records 53 ordered events. Initial position vector observations
stay `(288.5, 64.15, -127.5)` during the held readiness checks; velocity fields
are recorded separately and can change. No recovery move precedes release.
The owner has exactly two moves across its two completed logins, the passenger
one; the interrupted held login has none. Logged sequence and queue transitions
are audited, not merely inferred from a final mounted state.

Native console receipts before and after the held session bind both players'
actual vehicles to `2c03e129-ca61-4718-9453-39f666e94d65`; the world contains
one rocket. The final server player-position receipts are both
`[288.5d, 64.15d, -127.5d]`. Nine native F2 screenshots are retained. The
passenger's final F3 screenshot shows Earth at X 288.150 / Y 64.150 and a
real NVIDIA RTX 3070 Laptop GPU. The owner focus attempt returns false and its
captures have no F3 overlay; no owner F3 coordinates or full visual pass are
claimed. The initial owner image contains an open inventory.

## Stop and saved state

Driver, both native clients, dedicated server and probe all exit 0. Cleanup
errors and action errors are empty. The probe is detached before normal client
closure and server save/stop. No PID is terminated outside owned cleanup.

The saved world has one world-owned rocket and no player RootVehicle. Its
complete projected rocket state equals the seed report: same entity/logical
ID, snapshot `50481230343d03b7ca691f77d50acd20096f2636be43f878b7552995485e442c`,
Earth/LANDED, fuel 618, two seat assignments and the original six-block snapshot
including 17 diamonds. This is a save after native reconnection from the
persisted fixture, not a second post-session dedicated restart.

## Logs, limits and remaining evidence

Existing strict project/error scans pass without filter changes. Eleven
vanilla `Can't keep up!` warnings occur while the debugger is attached and
are retained in full. The breakpoint-heavy run is unsuitable for MSPT/FPS or
reference-load acceptance; no cause of earlier unrelated handshake timeouts
is inferred. This session has no handshake timeout.

The controlled native queue/cleanup scope is verified. Genuine adjacent-chunk
entity-storage ordering, native expiry and final-candidate coverage remain
open. This does not replace the full V1/V2, authentication, security, migration,
performance, independent review or human release Gates. v1.0 is IN_PROGRESS.

## Commands and evidence

- `javac --add-modules jdk.jdi ArceReconnectProbe.java`: exit 0; the exact source
  is archived with the actual session driver and window helper. The compiled
  probe class remains a disposable temp artifact and is excluded by repository
  binary policy.
- `python -u arce-native-reconnect-session.py`: exit 0; `native/summary.json`,
  complete logs, 34 control requests (33 recorded actions plus stop), screenshots
  and probe events retained. The helper never sends global input or captures
  unrelated windows.
- Read-only `arce-passenger-world-inspect.py <stopped-world> <output>`: exit 0;
  original NBT bytes and `saved-world/projection.json` retained.
- `javap -p -c -classpath <JAR> <RocketTransferRecoveryService>`: exit 0;
  `recovery-bytecode.txt` confirms the instrumented call site.
- `gradlew.bat clean build --no-daemon`: exit 0, 16 s, same JAR; Java
  compilation/test results cache-restored, not a fresh isolated R1 build.
- `gradlew.bat test runData runGameTestServer --no-daemon`: exit 0, 1m 29s;
  Java results remain 406 / 79 suites without failures/errors/skips; all 51
  required GameTests run and pass.
- `python audit.py`: exit 0, `PASS_CONTROLLED_NATIVE_QUEUE`; logs/source/instance
  hashes, event ordering, screenshot hashes, unchanged seed and saved projection.
- `python scripts/validate_repository.py --require-approved-identity`: exit 0,
  45 repository checks pass.
- Final Git/Markdown checks and checksum inventory are retained alongside this
  report. Full worktree diff remains nonzero because it contains uncommitted work.
