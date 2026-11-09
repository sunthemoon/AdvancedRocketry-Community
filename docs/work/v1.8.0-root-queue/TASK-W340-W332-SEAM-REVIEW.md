# TASK W340: read-only review of W332's gated load-failure seam and probes

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: you
change no checkout.

| Item | Value |
|---|---|
| Checkout | `D:/GitHub/arce-v180-w340`, detached at W332's branch head `2bfc56944` (W332's commits `b18de4fc5..28612996a` with the shared head `de59b0f7b` merged), created by Root |
| Decision | Root decision R411 (QUEUE row W340). AGENTS.md section 13 requires a read-only review by another session before a change on a persistence load path merges; W332 was implemented by a Claude session, so the review is by Codex. |
| Read first | In the checkout: [TASK-W332](TASK-W332-NOT-LOADED-AND-POOL-NATIVE.md), `records/W332-DESIGN.md` and `records/W332.md` under `docs/work/v1.8.0-root-queue/`, and the R-042 row of `docs/11-RISK-REGISTER.md` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W340/REVIEW.md`; logs and scratch files under `D:/GitHub/ARCE-Task-Evidence/root-queue/W340/` |

## Why

W332 added `ReleaseTestLoadFailure`, called from the load-only lookups `BayReceiptSavedData.existing` and
`ResourceEscrowSavedData.existing`, so that a native case can make vanilla's single read of the receipt or ledger file
fail. It also added gated probe commands (`R042ReleaseTestCommands`, `R042AdmissionProbe`). These ship in the
production JAR. Before they merge and C2 is built from them, another engine checks that production behaviour is
unchanged and that the cases prove what W332 claims.

## Check

1. **Production behaviour.** With the property unset, the two lookups behave exactly as before: same loader, same
   result, no extra log, no state. Confirm that nothing in production sets the property, that the seam has no static
   state, and that the lambda changes nothing about when vanilla calls the loader or caches its result.
2. **The gate.** The seam throws only when the property names that file type, the release-test hooks are set, the
   server is dedicated, and `EscrowTestEndpoints.enabled` is true; with the gate closed it logs one WARN and loads
   normally. Do `ReleaseTestLoadFailureTest` and `R042ReleaseTestGateTest` fail if any condition is dropped? Are the
   probe commands registered only with the other release-test commands, with permission 2 and the gate checked before
   any read or write?
3. **The injected state is the real one.** Vanilla catches the loader's exception, logs its own ERROR and caches the
   miss. Is that the same state a present file that fails to decode leaves, so that `N_BAY` and `L_LEDGER` show R-042's
   real NOT_LOADED behaviour, including the pre-start check passing the file's valid bytes?
4. **The probes.** `R042AdmissionProbe` attempts fuel, cargo, insertion, plain-rocket and rider steps. Can a probe
   change saved state in a way that a later restart in the same case misreads, or that hides a refusal? Does the
   runner's oracle check the receipt and ledger files byte for byte where W332 says it does?
5. **The coverage claims.** W332 left `U_BAY` and `U_LEDGER` (unknown presence) NOT_COVERED on this host, the vanilla
   dismount row NOT_COVERED, and the bay's other-owner admission to W325. Are the reasons accurate? Is there a way to
   reach unknown presence on Windows that W332 missed, without changing production code?

Give a verdict, APPROVED or CHANGES REQUIRED, with findings ranked by severity, each with a file and line, a concrete
failure scenario and a fix.

## Run

- `gradlew.bat --offline test --tests "*ReleaseTestLoadFailureTest" --tests "*R042ReleaseTestGateTest" --tests "*BayReceipt*" --tests "*EscrowLedger*"`
  under the heavy-JVM lock.
- `python -B -m unittest discover -s tests -p "test_run_v180_r042_native.py"`, with TEMP and TMP on D:.
- Any probe you need, in your own scratch directory under the output folder, never in the checkout. No native server
  run is required.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Take the lock per step (backslash lock path, as WORKER-RULES says) and release only your own token; other workers share
it. Before you finish, delete your own scratch trees and confirm with `git status` that the checkout is unchanged.

## Not in this task

Editing, committing or pushing anything; W338's review of W331; W339's driver work; native runs.
