# v1.0.0 passenger logout cleanup

2026-09-06, Windows, Java 17.0.7, Forge 47.4.10, branch
`codex/v1.0.0-stable-core`, development worktree based on `34b2e99`.
No remote access, authentication setup, long-load suite, commit, tag or
release approval. Accepted v0.9 evidence is unchanged.

## Completed scope and design

The pending reconnect queue previously removed offline players only when their
four-per-tick slot was processed. The installed manager had no logout listener.
A player logging out and back in before that slot could retain the previous
session's non-extending wait deadline.

The actual Forge logout event now passes the departing UUID through the
manager, flight and transfer services to remove its transient queue entry
immediately. It does not load SavedData, change durable passenger assignments,
reset request replay/rate limits, activate chunks or change queue budgets.
Save formats and flight protocol 5 are unchanged.

## Modified implementation and tests

- `AdvancedRocketryCommunity`: register the logout listener.
- `RocketManager`, `RocketFlightService`, `RocketTransferService`,
  `RocketTransferRecoveryService`: UUID-only cleanup delegation.
- `RocketPassengerContinuedUseGameTests`: one new Forge test, using the
  installed production manager's login handler to enqueue FakePlayers in an
  unloaded entity chunk. Logout is dispatched through the real Forge event
  bus, testing registration and the complete cleanup delegation. It checks
  immediate removal, preservation of the other pending player, subsequent
  enqueue, no teleport/mount and preservation of the durable seat.
- `RocketPassengerReconnectQueueTest`: one new unit test showing a completed
  session cannot donate its deadline to a subsequent session.
- CHANGELOG, community provenance, implementation log and this evidence.

The Forge test observes private transient state by reflection and copies the
map before checking it; it never modifies that state through reflection.
This avoids adding a public runtime API solely for the test. The field names
are an explicit test-maintenance dependency.

## Actual commands and results

Commands use `gradlew.bat`, `--no-daemon`, Java 17 and the full local TEMP path.

| Command | Exit | Observed result |
|---|---:|---|
| `runGameTestServer` (first fixture) | 1 | 1m 44s; FakePlayer has no channel for Forge's tier-sync login listener. Not a product reproduction. |
| `runGameTestServer` (corrected setup, before fix) | 1 | 1m 35s; only the new regression fails: `Logout left the previous session's pending reconnect`. |
| `clean build` | 0 | 29s. |
| `test runData runGameTestServer` | 0 | 1m 32s; 406 Java tests across 79 XML suites, zero failures/errors/skips; all 51 required GameTests pass. |
| `python collect.py` | 0 | Source inventory, XML copies, artifact identity and Git results archived. |
| `git diff --check` | 0 | No whitespace errors. |
| `git diff --exit-code -- src/generated` | 0 | No generated-resource changes. |
| `git diff --exit-code` | 1 | The worktree contains inherited and new uncommitted changes; not a clean candidate Gate pass. |

The fixture correction changes only login setup to the existing production
handler: FakePlayer cannot perform Forge's network synchronization. Logout
still traverses the actual event bus and all assertions are retained. Both
failed runs and their original native logs remain archived. The five changed
production files before the fix match the previous readiness source inventory
(`before-runtime-identity.json`).

The clean GameTest bootstrap logs a vanilla missing `server.properties` error
before creating its test server. The command and behavior tests pass; this is
not presented as a strict packaged-server log-scan pass. No filters, assertions,
timeouts or performance budgets were weakened.

## Artifact and evidence

- Development JAR SHA-256:
  `569f41ab59d953e3b4fad3c1b00588705b260fc0b73f1deaf829024bc603e381`.
- Size: 1,278,474 bytes; 747 source inputs in `source-inventory.json`.
- Results: `build-results.json`, `java-results/`, `clean-build.txt`,
  `after-checks.txt`, `after-gametest-native.log`.
- Retained failures: `before-gametest*.txt`, `before-gametest*-native.log`.
- Integrity: `SHA256SUMS.txt` covers this report and retained evidence.

## Unfinished scope, risks and Gate status

This is a Forge event-lifecycle regression, not a real TCP disconnect/rejoin or
asynchronous entity-I/O experiment. No native clients, packaged-server restart,
compatibility matrix or long stability test was run on this new artifact.
Older artifact evidence is not relabeled. The source fix does not change
persistence, but final-candidate dedicated/restart evidence remains required.

`V100-PASSENGER-LOGOUT-CLEANUP` is verified for the stated immediate-cleanup
scope. Its broader entity-loading parent remains in progress. v1.0 remains
`IN_PROGRESS`; not all Required Gates are met. The next work remains v1.0
connected-player recovery verification and candidate release preparation,
with long-load execution deferred as requested and independent/human approval
still outstanding.
