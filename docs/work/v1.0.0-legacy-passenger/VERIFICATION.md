# v1.0.0 legacy passenger vehicle reconciliation

2026-09-06, Windows, branch `codex/v1.0.0-stable-core`, development worktree
based on `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`. No frozen candidate,
commit, tag, release or human Gate approval.

## Scope and diagnosis

The retained passenger-duo world predates world-owned rocket saving. Read-only
inspection found zero world rockets and one owner `RootVehicle`, with two seat
assignments, fuel 618 and 17 diamonds. The original is untouched; the complete
58-file, 7,981,371-byte copy is `verification/legacy-world.zip`, SHA-256
`515fdafe471f542157b51c947037b8fbc31979669b3be27de5b46a01ced99197`.
Its file manifest and raw player/entity NBT projections are retained.

The before artifact `e8a066ca4be3b4fcf179fad88a72b502ba65d8c8b6695bcba5aca04293739af0`
rebuilds destination `00808f92-994e-46a0-811c-82998883587c` from the committed
journal. Owner login then loads embedded `dc69c542-80f6-47e6-98d8-6bbb7f4eb6e1`.
Both real clients mount that older entity. Server commands count **two** rockets
with the same logical identity and snapshot, each containing the 17 diamonds;
normal save preserves both. This is an actual duplication, not a GUI overlap
or an inference from the startup recovery warning.

## Implementation

- Preserve the journal-bound entity ahead of signed UUID ordering; use UUID
  order only as a deterministic fallback when the binding is absent.
- Include owner identity in the existing logical/snapshot/hash match.
- On passenger login, reconcile matching duplicate entities and remount the
  recorded passengers to the keeper. Keep the bounded lookup and manifest.
- Recognize the bound committed destination after refueling as well as landing,
  so a stale LANDED copy cannot replace its newer fuel state.
- Emit `ARCE_TRANSFER_LOGIN_RECONCILED` at WARN for actual duplicate removal.
  Startup rebuilding remains WARN. The strict log scanner is unchanged.

No save schema, public ID or protocol (5) change; no new dependency, copied
upstream implementation/assets, remote access, authentication setup, later
version feature or long-load test. Matching foreign owners or different
logical/snapshot identities are not discarded by this reconciliation.

## Automated verification

Final artifact: `7ba1c6c771d945bf25f101cdc8101afb26f51afd7cfdc5834a07545e0a5c99c4`,
1,265,940 bytes; 744 source inputs in `verification/source-inventory.json`.

| Command / attempt | Actual result |
| --- | --- |
| `gradlew.bat clean build --no-daemon`, attempt 1 | Exit 1, 17 s: nonexistent test fixture fuel method. `clean-build.txt` |
| Same command, attempt 2 | Exit 0, 35 s. `clean-build-2.txt` |
| `gradlew.bat test runData runGameTestServer --no-daemon`, attempt 1 | Exit 1, 1m 56s; 45/46 GameTests pass. FakePlayer has no Netty channel for Forge's unrelated tier-sync login listener. `after-checks.txt`, original native log retained |
| Final `gradlew.bat clean build --no-daemon` | Exit 0, 30 s; 399 Java tests, 78 suites, no failures/errors/skips. `clean-build-3.txt`, archived XML |
| Final `gradlew.bat test runData runGameTestServer --no-daemon` | Exit 0, 1m 43s; all 46 required GameTests pass. `after-checks-2.txt`, native log |
| `git diff --check` | Exit 0 |
| `git diff --exit-code -- src/generated` | Exit 0; datagen unchanged |
| `git diff --exit-code` | Exit 1 for the existing development-tree changes; not a clean candidate/G2 pass |

Two new pure Java tests exercise binding priority and deterministic fallback.
The new Forge regression performs a real transfer, creates a matching stale
vehicle whose signed UUID sorts first, refuels the bound entity to 1000, and
invokes the production rocket login listener twice. It checks mounting,
duplicate removal, journal binding and unchanged newer fuel after both calls.
The corrected fixture calls that listener directly rather than dispatching a
FakePlayer through unrelated Forge network listeners. Native clients separately
exercise real login dispatch; this GameTest alone is not a network test.
No timeout or assertion was relaxed; the failed attempt remains retained.

## Native attempts and remaining evidence

`before-native` refused a copied server-properties backup before starting Java;
`before-harness-2.py` failed Python syntax validation before runtime. Both logs
remain. Corrected `before-native-3` ran two clients and a dedicated server, all
three Java exits 0. Its strict driver exit is 1 because startup repair emits
WARN, in addition to the separately measured duplication.

First after attempt (`after-native`) timed out during the initial owner
handshake before login. Both started Java processes exited 0, but the driver
failed; this is not migration or multiplayer evidence. The initial artifact
was `1bf818f076f8db716d3566ae2be3e5182b8e4512360a8fd2625a3b0a6eaf9df1`, before
the GameTest fixture correction. No timeout increase or authentication change.

Final native run `after-native-2` used dedicated PID 18764 and real client PIDs
26332 / 17912, all Java exits 0. Both initial handshakes succeeded in this
attempt. Login removed the embedded entity and retained journal-bound
`3af01cfc-06ef-41c2-b3dd-c08d5c4bb35a`. Ten archived command/UI receipts prove
one rocket, both players' actual vehicle UUIDs equal to that binding, two
assignments, fuel 618 and 17 diamonds. Two native F2 screenshots were inspected:
both clients show Moon and Y=81.15000 on the NVIDIA hardware renderer, with
their respective seat X offsets. They are position evidence, not complete
rocket art/console visual acceptance.

The migration driver still reports **FAIL / exit 1** because its unchanged
strict scanner detects the initial `REBUILD_DESTINATION` warning. That expected
fixture repair is retained, not filtered or relabeled as a clean-server pass.
Functional reconciliation is established separately by entity/NBT evidence.

After normal stop, raw player/entity inspection shows one world-owned rocket
and no player `RootVehicle`. The subsequent clean dedicated restart (PID
13964, Java/driver exit 0) passes the strict scanner and reports the same UUID,
one entity, same manifest and fuel. A second saved-world inspection exactly
matches the full rocket projection, including cargo. This last restart is
headless, not an additional two-client reconnect claim.

`collect.py` and `audit.py` executed with Python 3.13.15, both exit 0. The scoped
audit verifies 744 input hashes, all 58 untouched original world files against
the ZIP, native log/screenshot hashes, before count 2, after/resaved count 1,
and all original gameplay fields except the deliberately reconciled physical
entity UUID. `audit-result.txt` preserves the strict migration failure beside
the successful clean restart; it is not a release Gate approval.

Continued use/return, other legacy phase/loading orders, initial handshake
reliability, candidate binding and remaining Required Gates are not complete.
Overall v1.0 remains **IN_PROGRESS**. No long stability suite is required to
continue the implementation work; deferred release evidence remains visible.

## Changed files and next scope

Runtime: `RocketTransferAuthorityOrder.java`, `RocketTransferEntities.java`,
`RocketTransferRecoveryService.java`. Tests: `RocketTransferAuthorityOrderTest.java`
and `RocketPassengerPersistenceGameTests.java`. Documentation: changelog,
community provenance, implementation log, current-version action and this
report/evidence directory. Unrelated inherited changes remain untouched.

The next v1.0 task is continued use/return after passenger persistence recovery,
not remote deployment or a later-version expansion. Existing release Gates
and performance budgets are neither waived nor marked passed.
