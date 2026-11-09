# TASK W332: native cases for R-042 (NOT_LOADED and the held pending pool)

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w332`, branch `codex/v1.8.0-w332`, created by Root from the shared head |
| Decision | Root decisions R392 and R404 (QUEUE row W332), from W327's audit `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/risk/AUDIT.md` (sections 1 and 4) and the C1 report [INTEGRATED-C1-REPORT-20261109](records/INTEGRATED-C1-REPORT-20261109.md) section 7 |
| Builds on | W325's bay runners and `scripts/bay_integrated_evidence.py`, W330's cargo owner-cap probe and wrapper, the W329 ledger GameTests, and W328's pre-start check (merged with W329 once W334 lands; if it is not merged when you start, base the NOT_LOADED cases on the shared head and say which behaviour you saw) |
| Record | `docs/work/v1.8.0-root-queue/records/W332.md` and the cut list `records/W332-DESIGN.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W332/` |

## Why

The owner asked Root to check R-042's effects on the whole server. The audit checked them by reading code. These cases
show them on a dedicated server with two restarts each, so that the owner's per-switch report rests on observations.
They run on the next candidate C2 with the rest of the matrix (R386). Production admission stays off: every fixture is
reachable only on a GameTest server or a gated release-test server.

## What to do

1. **Cut list first** (`records/W332-DESIGN.md`, committed before the code): each case, how it is set up, the oracle
   after each restart, and what it does not show.
2. **Bay receipts NOT_LOADED, both triggers.**
   - **Unknown presence.** After W328 this refuses startup. Show the refusal (`ARCE-BETA-2000`, the storage hint first),
     that no world file changed, and a clean start once access is restored. Make the status check fail without a
     missing file, for example with a deny entry in the receipt file's or data folder's access list for the server's
     user; restore the access list afterwards. If this host cannot produce it, say what was tried and leave it
     NOT_COVERED.
   - **Present but not loaded.** A receipt file that passes the pre-start check and then does not load. Use an existing
     gated seam if one reaches it; otherwise add a gated test fixture, refused unless the release-test gate is on, with
     a unit test for the refusal. Show, on one server, the effects the audit lists in section 1.2: an ordinary flight
     with no bay stops advancing, a satellite launch and decommission answer DEPLOYMENT_PENDING, assembly recovery is
     fenced, package insertion is refused, and fuel and each cargo kind are refused. Show the logs (one WARN with
     `reason=LOAD_FAILED` and one INFO per start) and recovery by a restart that loads the file.
3. **The escrow ledger NOT_LOADED** (W329's hold). The same approach for the ledger: the protective answers on a native
   server (no launch, no fuel or cargo change, devices locked, vanilla dismount still works), the one WARN per start,
   and recovery by a restart that loads the ledger.
4. **The pending pool held by released and REPAIR receipts.**
   - Seed released and durable REPAIR bay receipts within the real caps (64 per server, 8 per rocket, 16 per owner), as
     W325 seeded INTENT receipts, and show that after each of two restarts they still hold their slots, and that a new
     fuel fill, each of the four cargo kinds and a bay deployment are refused with no resource or ledger change.
   - Per owner: two fully loaded rockets of one owner refuse that owner's fuel, cargo and bay admission, and another
     owner is admitted.
   - Show that no online command releases them (the retirement command refuses released and REPAIR receipts).
5. **The candidate as an argument**, as W330 did: candidate commit and JAR SHA-256 are required arguments, recorded in
   each invocation, with the production-equality check.
6. **A native smoke on a locally built JAR** (not a candidate) for each case family, both restarts, and for each
   family one run with the protection disabled in a scratch copy that the oracle rejects; then discard the scratch
   copy. These smokes are not candidate evidence.

## Verification

- Python tests for every runner and oracle you add or change; existing bay and cargo runner tests unchanged.
- `./gradlew build` with every unit test if any Java changes; any new GameTest alone, then the full GameTest run with
  `scripts/check_gametest_log.py`. An addition to `scripts/gametest_expected_log.json` may name only lines your new
  tests emit.
- All heavy steps under the heavy-JVM lock (backslash lock path, as WORKER-RULES says), taken per step and released
  between steps; other workers share it. Call Python by its full path in any detached script.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production behaviour and admission; D6 timing, the 256-coordinate saturation and the downgrade (W336); the bay's other
native rows (W331) and GameTest rows (W333); the risk register, the status file and QUEUE.
