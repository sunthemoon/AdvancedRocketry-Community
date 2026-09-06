# v1.0.0 passenger entity-readiness boundary

2026-09-06, Windows, branch `codex/v1.0.0-stable-core`, development worktree
based on `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`. v0.9 acceptance is
unchanged. No release candidate, commit/tag, human approval, authentication
setup, remote access, copied upstream asset/code or long-load suite.

## Finding and implementation

The new Forge regression first fails with `Login moved the player before
entity storage was ready`. The prior implementation checks the old manifest
and can teleport/mount before the player's entity chunk is available.

Login now waits for player entity storage, then for the recorded authority's
entity storage before lookup or duplicate pruning. A nearby committed arrival
in an adjacent loading chunk is observed without activating it, allowing its
latest manifest to become visible before deciding that a newly boarded player
is unrelated. Related journal entries can use the existing bounded, server-
recorded chunk activation. No packet supplies chunk coordinates.

Empty candidate sets and unbound committed candidates go through the existing
durable recovery/rebinding service; login does not adopt an unconfirmed copy.
After selection, the current settled authority's seat assignment is checked
again before moving the joining player. Flight snapshots and save schemas
remain unchanged; flight protocol remains 5.

The lifecycle-owned queue retains UUIDs and start ticks only, not players,
levels or chunks. Capacity 128; four round-robin attempts per server tick;
200-tick wait window. Duplicate offers do not extend the deadline. Completion,
disconnection observation and manager shutdown remove pending work. Overflow
and expiry emit bounded diagnostics rather than silently increasing limits.
This is a transient retry mechanism, not a new persistence subsystem.

## Tests and commands

- Six pure Java queue tests: budget/fairness, non-extending duplicate offers,
  capacity without eviction, completion/clear, clock rollback, invalid inputs
  and immutable batch lists.
- One Forge test: a known passenger in an explicitly unloaded entity chunk
  must not move or activate that distant chunk. Once positioned in the loaded
  rocket chunk, the production login listener restores their seat. It invokes
  the listener at both boundaries; it does not simulate a real network stall
  or prove native asynchronous queue draining by itself.
- The older legacy FakePlayer fixture now explicitly starts at its saved
  vehicle position instead of the default `(0,0,0)`.

Before `runGameTestServer --no-daemon`: exit 1, 1m 12s, 50 tests with only the
new readiness regression failing. Original native log retained.
First `clean build --no-daemon`: exit 0, 34 s. After correcting the legacy
fixture position, second clean build passes in 33 s; `test runData
runGameTestServer --no-daemon` passes in 1m 41s. A final build/check follows
the review additions for adjacent chunks and missing committed bindings.

## Scope and remaining work

Changed runtime files: `RocketPassengerReconnectQueue`, `RocketTransferEntities`,
`RocketTransferRecoveryService`, `RocketTransferService`. Tests:
`RocketPassengerReconnectQueueTest`, `RocketPassengerContinuedUseGameTests`,
`RocketPassengerPersistenceGameTests`; supporting changelog, provenance and
implementation records updated. No unrelated inherited changes reverted.

Final `clean build --no-daemon` passes (exit 0, 35 s), followed by `test runData
runGameTestServer --no-daemon` (exit 0, 2m 29s). All 405 Java tests in 79 suites
pass without failures/errors/skips, and all 50 required GameTests pass. Raw
XML, complete command logs and original native GameTest bytes are archived.
The exact final artifact is 1,276,810 bytes, SHA-256
`a3fb6c733ad5a930b34f4eb59b03acf2338875bcf3273004375873cc26a27a23`, with 747
hashed source inputs. Data generation is unchanged (`git diff --exit-code --
src/generated`: 0); `git diff --check`: 0. Full `git diff --exit-code`: 1 for
the development-tree changes, not a clean-candidate G2 pass.

The final JAR also starts and stops a packaged dedicated server over a fresh
copy of the previous continued-use world (PID 24124, Java and driver exit 0).
Its strict log scanner passes. The same Earth rocket UUID, full snapshot,
two seat assignments, fuel 618 and 17 diamonds survive; the Moon loader still
has 118 buffered units. Read-only resave inspection matches the complete prior
rocket projection and finds no player RootVehicle. Original fixture file
hashes are unchanged. This is a headless restart, not a multiplayer timing test.
Python `collect.py`, world inspection and `audit.py` all exit 0; the audit
explicitly records `native_deferred_queue_timing: NOT_TESTED`.

The load-order parent remains IN_PROGRESS: real connected-player
deferred draining, adjacent-chunk loading order and slow-storage expiration
still need scoped runtime evidence. The previous two-client return evidence
belongs to its older artifact and is not relabeled as testing this queue.
Full v1.0 Required Gates remain incomplete; reference load is still deferred.
