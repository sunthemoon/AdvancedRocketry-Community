# v1.0.0 two-client console observations

Date: 2026-09-06, Asia/Shanghai. Uncommitted development worktree on
`codex/v1.0.0-stable-core`, base `34b2e99`. No production code, resource,
schema, permission, protocol or budget change in this slice. The preceding
goal turn made progress by fixing and verifying selected-route quotations.

## Tested input and environment

The unchanged development JAR is 1,253,352 bytes, SHA-256
`6953ecfe64ae937080f8bc99eb000791e76e24cc36d6d786976a08658f3064fc`,
flight protocol 4. Its [implementation checks](../v1.0.0-flight-quote/VERIFICATION.md)
include 385 Java cases, all 44 GameTests, DataGen and packaged restart. The
input audit checks the same 734 source hashes and JAR. The required short
Gradle checks are also executed again below; no long workload is repeated.

One fresh local Forge 47.4.10 dedicated server and two actual native Windows
clients, JEI absent, GUI scale 2, 1280x720 interaction screenshots. Both client
logs report NVIDIA GeForce RTX 3070 Laptop GPU, OpenGL 4.6.0, NVIDIA 566.36.
This is not a software renderer, mocked network client or two-player GameTest.
Profiles are offline loopback identities, not authenticated V2 evidence:

- Owner `V100Visual1`: `62bbb9cb-b2fa-39aa-9a6b-430b71f5493f`, non-OP after fixture setup.
- Operator `V100Visual2`: `6482f9f6-ff26-3046-b7ba-86db04b972f8`.
- Server PID 18904; owner client 26176; operator client 17820. All three are
  task-owned and exited 0. No remote host or launcher credential was used.

## Successful observed subcase: another player's Moon launch

The original rocket actually traveled from Earth to station Beta in an earlier
unsuccessful cancellation attempt below. Its landed entity is
`f2dab608-e17f-4434-9438-a553f8215b14`, logical rocket
`08c24ffa-1ad3-4e15-92fe-70041abd3620`, fuel 670, origin `1024,128,0`.
No fuel reset, replacement rocket or fabricated transfer result was used.
Trusted fixture commands load only the known station area and place the two
players on its existing platform; launch/cancel themselves use real clients.

1. [Owner's open menu](verification/attempt-1/11-owner-station-menu.png) shows
   Space Station to **Earth**, LANDED, fuel 670/1000, quote 330. The other
   player's avatar is visible. The owner leaves this menu open.
2. [Operator's menu](verification/attempt-1/13-operator-moon.png) selects
   **Moon**, quote 247. At 02:33:24 the server records that operator's successful
   LAUNCH, request `ba1d6bdd-a439-4b48-bb7a-b12156a0dcec`.
3. [The owner's already-open menu](verification/attempt-1/probe-2-01-owner-active.png)
   now shows Space Station to **Moon**, COUNTDOWN 52 ticks, quote 247 and
   disabled route controls. The owner's editable choice had been Earth.
4. The owner uses Tab then Enter on the countdown interface. At 02:33:25 the
   server records that non-OP owner's successful CANCEL, request
   `52c6b99f-2186-413a-a14c-3a37a54c9139`, destination Moon, station null,
   required fuel 247. This is a processed C2S intent, not merely a posted key.
5. [Owner after cancellation](verification/attempt-1/probe-2-02-owner-after.png)
   returns to its prior Earth selection and quote 330; the
   [operator](verification/attempt-1/probe-2-03-operator-after.png) retains Moon
   and quote 247. Both show FUELED and fuel 670/1000.
6. At 02:34:29 an explicit server report confirms FUELED, fuel 670, passengers 0,
   `transfer=none`, five blocks and the same landed entity/logical identity.
   No inventory/cargo comparison, passenger transfer or restart assertion is
   claimed for this interaction session.

This verifies cross-client **body-target** synchronization and owner cancellation.
It does not yet verify a station-UUID target in a stable two-open-menu scenario.

## Retained unsuccessful observations

### Initial second-client handshake

The first automatic connection of client 17820 starts at 02:20:52 and ends
with the native [Timed out screen](verification/attempt-1/second-client-disconnection.png).
The server sends Forge login/registry messages but does not complete that
connection; it logs disconnection at 02:21:23. Native logs were copied before
reconnection. A `jcmd 17820 Thread.print` snapshot shows the render thread in
the FPS limiter and Netty waiting in its selector, not a Java deadlock finding.
It is only a point-in-time snapshot, not a root-cause diagnosis.

After that connection was visibly terminal, the **same live client process**
returned to the server list and manually reconnected to the same loopback port.
Both clients then joined successfully; no timeout was increased and no process
was restarted. This retains, rather than resolves, the initial connection
failure and the earlier [compatibility handshake issue](../v1.0.0-compatibility/VERIFICATION.md).

### First body/station interaction attempt

Initial owner capture `02-owner-menu.png` shows creative inventory, not the
flight menu. A focus request returned false. After closing that screen and
repositioning, `08-owner-menu.png` does show the actual Moon console.
By the first timed probe the owner was underwater and its menu had closed:
server position later confirms `384.5,52,380.5`, outside interaction range.
The operator's station-Beta LAUNCH succeeds and the rocket lands with fuel 670;
no CANCEL is received. Those probe filenames are not proof of cancellation.

The test floor under the owner's Earth position is later confirmed air, while
the operator's floor remains stone; the setup log originally reports filling
100 stone blocks. Why that support block disappeared is **unresolved**. It is
not attributed to a mod defect, player input or chunk generation without more
evidence, and no production fix or successful rollback claim is based on it.

### Fixture/command mistakes

- A conditional floor-presence wait times out; the later air check explains
  why its success marker was absent.
- An incorrect `release-test flight-report` command is rejected; the registered
  subcommand is `report`. Another report cannot resolve the unloaded station
  entity, and an Overworld report rejects a Space entity. These failures remain
  in action checkpoints and native logs.
- Initial station positioning at Y=101 is below its platform. Reading
  `StationLimits` establishes platform Y=127 and landing Y=128; the subsequent
  successful subcase positions players at 128 on the existing platform.

These are failed setup/observation steps, not evidence of a successful game
interaction. The retained timed-probe scripts execute in about 3 seconds each;
the full setup/diagnostic session is approximately 15 minutes, not a soak run.

## Commands, shutdown and audit boundary

Interpreter: `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe`,
`PYTHONUTF8=1`. Java: `C:\Program Files\Java\jdk-17.0.7\bin\java.exe`.
Exact executed scripts and checkpoints are in [attempt-1](verification/attempt-1/).

| Execution | Actual result |
|---|---|
| `python C:/Users/Administrator/AppData/Local/Temp/arce-console-duo-ui-session.py` | Exit 1, summary FAIL; native clients/server each exit 0 |
| `jcmd.exe 17820 Thread.print` | Exit 0; full diagnostic retained |
| `python .../arce-duo-probe.py` | Exit 0; intended cancellation not achieved |
| `python .../arce-duo-probe-2.py` | Exit 0; actual body-target update and owner CANCEL confirmed independently above |
| `gradlew.bat clean build --no-daemon` | Exit 0, 16 s; 385 Java cases in 73 suites, zero failures/errors/skips; JAR reproduces the same hash |
| `gradlew.bat test runData --no-daemon` | Exit 0, 21 s |
| `git diff --exit-code -- src/generated/resources` / `git diff --check` | Both exit 0 |
| `git diff --exit-code` | Exit 1 for intended uncommitted worktree; not clean CI/G2 approval |
| `gradlew.bat runGameTestServer --no-daemon` | Exit 0, 1 min 33 s; all 44 required tests pass |

The existing strict server-stop audit flags
`ARCE_TRANSFER_RETURNED_TO_SOURCE` at WARN with `reason=countdown_cancelled`.
It is retained as a failed driver audit, not suppressed or relabeled PASS.
The revised test driver archives all three native logs and its final summary
even when that audit raises. It does not change the audit's acceptance rules.
The initial handshake failure and failed action waits also preclude a blanket
successful-run claim. `ops-after-test.json` contains only the second player.

`V100-VISUAL-CANCEL-DUO-BODY` is verified; the parent remains in-progress.
Station-target cancellation, pointer-only cancellation, other scales, late
closed-menu updates, passengers/reconnects, authenticated V2 and release Gates
remain open. v1.0 stays IN_PROGRESS. No long stability suite, remote deployment,
candidate tag or human approval was performed.

The [scoped evidence audit](audit-result.txt) verifies source/JAR identity,
native process/log receipts, the processed actor-bound intents, owner non-OP
status, PNG receipts, current JUnit/GameTest results and bundle checksums. Its
result explicitly preserves `driver_status=FAIL`; a valid archive is not a
successful whole-run or release verdict. The output file is excluded from its
own checksum manifest to avoid a self-referential hash.
