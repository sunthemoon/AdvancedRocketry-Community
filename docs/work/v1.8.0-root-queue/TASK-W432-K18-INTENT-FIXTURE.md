# TASK W432: package K18, a never-shipped intent fixture mod for the refusal branches

Root: Claude. Date: 2026-11-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w432`, branch `codex/v1.8.0-w432` from the shared branch |
| Decision | Root decision R599 (QUEUE row W432) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K18 and choice C14, as R546 refined it (a refusal is proved by the tool's own receipt and the unchanged server state read back, and by the server log only where the code writes one); [W402](records/W402.md) section 6 and its access matrix `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K13/access-matrix.csv` (every menu and client-to-server channel, its policy, distance check and refusal); the [network pin](../../../src/test/resources/network-protocols.txt); the step lists' rows C08, D08, D09, E03, E05, E07, G03 and I11; the existing independent fixture `compat-test-mod/` (its separate Gradle build, its consumer-boundary checks and W426's fixture-owned access transformer) as the model of a mod that is built apart from the product |
| Outputs | A new folder `intent-test-mod/` with its own Gradle build, sources and tests, modelled on `compat-test-mod/`; its README; outside Git, the built JAR and its SHA-256 under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K18/`; the record `docs/work/v1.8.0-root-queue/records/W432.md` |
| Write scope | `intent-test-mod/` and the record. No change to `src/`, the host build files, `compat-test-mod/` or the product JAR. |
| Lock | The Gradle build of the fixture is heavy: take the heavy-JVM lock with the WORKER-RULES loop (C6 attempt 2 yields it for 120 seconds after each family), hold it only while Gradle runs. |
| Not allowed | Anything an ordinary client could not send: the mod sends only well-formed packets of the pinned channels with stale, out-of-range, unauthorised or late values; no packet forging beyond those fields, no server-side code in the product, no use outside a test world. Starting a client or server (qualification is a campaign step). |

## What to build

1. A client-side test mod, never shipped, that registers for the eight pinned client-to-server channels as the product
   does and, on a command or key the campaign triggers, sends one bounded case per refusal branch the access matrix
   lists: stale (a container or entity ID that is no longer valid), out of range (beyond the code's distance), without
   permission (a non-owner or non-operator), and late (after the menu closed). Each case is bounded (a fixed number of
   packets and a rate limit) and writes its own receipt line to the client log (case, channel, values sent, time).
2. A case table in the README mapping each case to its access-matrix row, the checklist row it serves, the expected
   refusal (the server's message, log line, or "unchanged state" where the code is silent), and how the campaign reads
   back the unchanged state. A branch the tool cannot reach stays NOT_COVERED with the reason and its A1 test named.
3. A separate Gradle build with a boundary check like `compat-test-mod`'s (no product classes shaded, only the
   product's published network constants or its API used, the archive's metadata complete), and unit tests for the
   case builder (bounds, rate limit, each case's values).
4. Build the JAR under the lock, record its SHA-256, run the unit tests and `python -B scripts/validate_repository.py`,
   which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
