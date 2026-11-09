# W331 cut list: the satellite bay's native gaps for candidate C2

This cut list is committed before any W331 code. Authority is [TASK-W331](../TASK-W331-BAY-NATIVE-GAPS.md) under Root
decision R398. The contract is [W27A section 9](W27A-DESIGN.md) (B1 to B18), [W127](W127-PLAN.md),
[W230](W230-DESIGN.md) and [W238](W238.md). The starting point is W325's [record](W325.md) and
[matrix](W325-MATRIX.md), whose 27 native NOT_COVERED rows are listed in the TASK.

It plans coverage only. It claims no result, and none of these cases is candidate evidence until it runs on C2.

## 1. Rules this list follows

- **Compose first.** Each row first tries the C1 controls that already exist: the bay cuts (`bayCheckpoint`), the
  registry-writer and transaction-writer cuts (`arce satellite|rocket release-test ... arm <point> <mode>`), the copy
  fixture, the online facility rider, the receipt fill, the first-tick hook and the staging blocker.
- **New fixtures are gated probes beside the existing ones.** Every new command sits under
  `arce bay release-test ...`, which is registered only with `releaseTestHooks`, and calls
  `EscrowTestEndpoints.requireEnabled` before it reads anything. Each gets a unit test of its refusal with the gate
  closed. A new fixture never adds a call from production code; it either adds a command, a fixture variant, or a new
  value of the existing gated `BayDeploymentCuts.Cut` enum that only the existing `writing`/`removing` calls read.
- **No new production behaviour.** Where a row needs a pause or decision that production code does not already offer
  behind a gate, the row stays NOT_COVERED with the reason (section 5).
- **Oracles.** Existing oracles, timeouts and assertions do not change. W331 cases carry `w331=True` in their spec
  and run through their own case function, which reuses the existing helpers but never edits their branches, so W325's
  67 runner tests and 21 evidence tests keep passing unchanged.

## 2. The candidate as an argument

The three matrix runners (`scripts/run_v180_bay_forced_stop.py`, `scripts/run_v180_bay_released_flight.py`,
`scripts/run_v180_bay_exit_cuts.py`) gain two required arguments next to the existing `--jar`:

- `--jar-sha256 <hex>`: the runner refuses unless the JAR's SHA-256 equals it, before Java starts and again at the end;
- `--candidate-commit <sha>`: the production source of the candidate. The runner refuses unless
  `git diff --quiet <candidate> HEAD -- src/main src/generated build.gradle settings.gradle gradle.properties` exits 0.

`--source-commit` keeps its meaning (the clean harness HEAD, as `assert_pin` checks). Each `results.json` records a
`candidate` object: JAR path, SHA-256, candidate commit, harness commit, the exact diff command and its exit code.
`scripts/bay_integrated_evidence.py` gets the shared check (`candidate_pin`) and a command line,
`python -B scripts/bay_integrated_evidence.py pin --jar ... --jar-sha256 ... --candidate-commit ... --output <json>`,
so that a driver can record the same check for any invocation. Python tests fail without each check.

## 3. Rows reached in W331

"F" is the forced-stop runner. Every case has its first start, the cut, and two restarts. "Raw" means a bounded read
of the stopped world's file in Python. The common invariants of `check_settled` (packages, owned satellites, foreign
receipts, no ERROR/FATAL at a restart) apply to every case unless the row names a stricter one.

### 3.1 Family T: the transaction journal at D0 (B14.3, B14.4, B15.5)

D0's B14 step asks the transaction journal for L's absence (`RocketTransactionSavedData.absenceStatus`), requests a
checkpoint on the first UNPROVED or WRITE_REQUIRED, and rejects every bay on a record for L (ASSEMBLY_JOURNAL_PENDING)
or on UNPROVED after its request (ASSEMBLY_JOURNAL_UNPROVED). W325's `B14_T1` makes D0 request by adding an inert
record (`transaction-writer add`) while the writer is paused.

| Row | Composition or fixture | Boundary of the cut | Oracle at the cut | Oracle after each restart |
|---|---|---|---|---|
| B14.3 | F `W331_B14_FAILED`: `transaction-writer arm STAGED FAIL` before the launch, then `add`. Every journal write fails before replacement. | D0's requested checkpoint fails definitely; the claim rejects every bay. Then a one-shot `STAGED PAUSE` re-arm and release lets later writes pass, and the rocket lands. | Claim reasons for L include ASSEMBLY_JOURNAL_CHECKPOINT and then ASSEMBLY_JOURNAL_UNPROVED; at least one `TXW_INJECTED point=STAGED mode=FAIL` before it; no receipt for L; the journal file bytes are unchanged by the failed writes. | LANDED_UNCONSUMED: no claim, no receipt, both packages byte-equal, owned 0. |
| B14.4 | F `W331_B14_STALE`: new gated command `arce bay release-test stale-assembly <logical>` writes one ASSEMBLY record whose transaction ID is L, in the never-loaded `w129a_absent` dimension (as `transaction-writer add` does, so restart recovery never touches it). Order: `arm STAGED PAUSE`, launch, `add`, wait for D0's request, `stale-assembly`, release. | D0 finds the record for L and rejects every bay. | Claim reason ASSEMBLY_JOURNAL_PENDING for L; no receipt for L; no D1. | LANDED_UNCONSUMED; Raw: the journal still holds the record for L (its 16 UUID bytes); no claim and no receipt. |
| B15.5 | F `W331_B15_JOURNAL`: `transaction-writer arm MOVED UNKNOWN` before the launch, then `add`. Each write replaces the file, then throws, and its readback throws. | The bay's D0 journal write is replaced, then throws (UNKNOWN); D0's request gets UNKNOWN again; the claim rejects every bay. Then the one-shot re-arm and release, and the landing. | At least two `TXW_INJECTED point=MOVED mode=UNKNOWN` before the rejection; claim reason ASSEMBLY_JOURNAL_UNPROVED; no receipt for L; the journal file was replaced (its digest changed from before `add`). | LANDED_UNCONSUMED; Raw: the replaced journal reads back at restart (no ERROR); no receipt. |

**Correction after smoke 2 (both before any candidate run).** D0 records its reason on each bay member, which the bay
menu reads (`BayTransferRecovery.reject`); the claim line only reports its wait reasons and the READY_SOURCE handoff. So
family T's oracle waits for that handoff and requires the reason on every bay through `deployment-status`, instead of
a claim line with the reason. For B15.5 the smoke showed exactly one injection: the write that D0 itself requested was
replaced, then unknown, and D0 then rejected every bay. The oracle therefore requires at least one injection between
D0's request and the handoff (the requested write), not two.

### 3.2 Family W: replace-then-throw and the stop (B15.3, B15.4, B18.6, B18.7)

| Row | Composition or fixture | Boundary of the cut | Oracle at the cut | Oracle after each restart |
|---|---|---|---|---|
| B15.3 | F `W331_B15_D1`: new value `B15_D1` of `BayDeploymentCuts.Cut` (phase INTENT, point MOVED, mode UNKNOWN), read only by the existing `writing` call. | D1's INTENT file is replaced, then the committer throws; the writer's readback resolves the exact candidate. | `ARCE_RELEASE_BAY_INJECTED cut=B15_D1 ... point=MOVED`; the group continues and lands with the data bay consumed, one registration. | LANDED: consumed, owned 1, no receipt. |
| B15.4 | F `W331_B15_REGISTRY`: `registry-writer arm MOVED UNKNOWN` before the launch. The kill follows the first injected MOVED failure. | The registry checkpoint file is replaced with X, then the write throws and its readback throws (UNKNOWN); no later write succeeds before the kill. | The data INTENT is acknowledged and not REGISTERED; Raw: the replaced registry file names X (its UUID bytes). | LANDED: the restart reads the actual target, promotes the INTENT once; owned 1. |
| B18.6 | F `W331_B18_SUCCESSOR`: cut `B18_INTENT` (INTENT MOVED PAUSE), then `fill 1` while the write is paused, so a second receipt is queued behind it; then a clean stop. | Clean stop with the paused write and its queued successor. | The stop releases and drains the paused write (`ARCE_RELEASE_BAY_RELEASED`); exit 0; one coordinator stop line and no claim transition after it; Raw: the receipt file holds both INTENT receipts. | INTERRUPTED for L (REJECTED, INTERRUPTED); pending 1 and retained 2 (the fill's INTENT stays pending, L's REJECTED stays retained). |
| B18.7 | F `W331_B18_UNKNOWN`: `registry-writer arm MOVED UNKNOWN` before the launch, then a clean stop while the registry write is unresolved. | Clean stop with an unresolved UNKNOWN write. | Claim REGISTRY_UNPROVED before the stop; one coordinator stop line with the claim unresolved and no claim transition after it; the stop exits; Raw: the registry file names X. | LANDED: the restart reads the actual target and promotes once; owned 1. |

The registry-writer ERROR `ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED outcome=UNKNOWN` is the selected injection in B15.4
and B18.7, as in W325's `B13_UNKNOWN`. Any other first-start ERROR fails the case.

### 3.3 Family P: a non-neutral source (B6.8)

| Row | Composition or fixture | Boundary of the cut | Oracle at the cut | Oracle after each restart |
|---|---|---|---|---|
| B6.8 | F `W331_B6_PROTECTED`: new fixture variant `arce bay release-test fixture <pos> protected`: the same hand-fuelled rocket, whose escrow wrapper carries `resource_epoch=1` with no ledger binding. `RocketEscrowGate.escrowIdle` still allows the launch (no ledger exists), while `bayNeutral` refuses any non-zero epoch. New read-only probe `arce bay release-test deployment-status <satellite>` logs `BayReceipts.deploymentStatus`, the value the bay menu shows. | D0's neutral-wrapper step. | Claim reason BAY_SOURCE_PROTECTED for L; no receipt; the probe reports reason BAY_SOURCE_PROTECTED for the data satellite; packages byte-equal; the rocket lands. | LANDED_UNCONSUMED; packages byte-equal; owned 0. |

The GameTest binds a random ledger ID instead. Natively a ledger-bound rocket cannot launch without a real loader
fill, because `escrowIdle` then refuses; the epoch-only wrapper is the non-neutral state the hand-fuelled fixture can
reach. This is a choice for Root's review.

**Correction after smoke 2: family P is withdrawn and B6.8 stays NOT_COVERED.** The `protected` fixture command threw
before its launch line. The escrow model requires an unbound wrapper to have epoch 0
(`EscrowSettlementIntent.Observation`: "Invalid neutral observation"), so an epoch-only wrapper is an invalid state,
not a reachable non-neutral one; this is the likely cause (the command's exception is logged only at DEBUG). The
variant, its case and its scratch patch are removed from C2's fixtures. A ledger-bound source needs a real
fluid-loader fill before the launch (section 5).

### 3.4 Family D: X absent on the initial disk, and online non-data (B4.3, B4.6 to B4.10)

B4's boundary is "register, decommission idle non-data X, stop before S' save". Decommission needs an acknowledged
REGISTERED receipt (`BayReceiptStore.blocksDecommission`), and no existing cut pauses between D4's acknowledgement and
D5. The earliest gated pause after D4 is `B6_D5` (after D5's publication of S' and T', before D6). There the claim
stays stalled, X can be decommissioned, and S' is not saved unless the harness saves it. So the B4 rows with X absent
use `B6_D5`: T' is durable, S' never reaches the disk, and the copy fixture sets the source on disk to S
(`source-before`) or removes it (`absent`). That is the B5.2 mixed state with X absent, not a pure `(S, T)` state; this
is recorded for Root's review.

New gated command `arce bay release-test decommission <satellite>`: the operator decommission that the first-tick hook
already makes (`registry.decommission(satellite, OWNER, true, true)`), then the release-test registry flush, logging
the result code and whether X remains. New fixture variant `arce bay release-test exit fixture <pos> survey`: the
online rider fixture with the survey second bay (C1's runner refused online plus survey because this did not exist).

| Row | Case | Composition | Oracle at the cut | Oracle after each restart |
|---|---|---|---|---|
| B4.3 | `W331_B4_GONE_X_S_PRESENT` | `B6_D5`, survey, offline seat, copies `source-before`, `decommission` of X, save | decommission SUCCESS; Raw: the registry file lacks X; the copy census and saved-copy disk check as in W185 | LANDED: the survey bay stays consumed, X is not registered again (owned 1), offline seat kept (census 1, assigned) |
| B4.10 | `W331_B4_GONE_X_S_ABSENT` | as B4.3 with copies `absent` | as B4.3, four saved copies, source absent | as B4.3 |
| B4.6 | `W331_B4_GONE_X_S_PRESENT_ONLINE` | as B4.3 with the online survey rider instead of the offline seat | as B4.3; the online rider mounted on seat 0 at the cut | as B4.3; the rider logs in at each restart and keeps seat 0 |
| B4.7 | `W331_B4_GONE_X_S_ABSENT_ONLINE` | as B4.10 with the online survey rider | as B4.6 | as B4.6 |
| B4.8 | `W331_B4_LIVE_X_S_PRESENT_ONLINE` | `B3_MOVED`, online survey rider, copies `both`, save (W325's B4.4 with the online rider) | REGISTERED replaced, rider mounted | LANDED, owned 2 (X live), rider keeps seat 0 |
| B4.9 | `W331_B4_LIVE_X_S_ABSENT_ONLINE` | as B4.8 with copies `absent` | as B4.8 | as B4.8 |

### 3.5 Family G: a multi-REGISTERED partial group (B8.7, B8.8, B17.4)

New fixture variant `arce bay release-test fixture <pos> trio`: three bays in position order, the data package, a
survey package and the research-locked solar package. Two bays register and the solar bay is refused at validation,
so the group is both multi-REGISTERED and partial (W325's reason named the missing second REGISTERED member). The
fixture logs one extra line, `ARCE_RELEASE_BAY_TRIO logical=... data=... survey=... solar=...`.

| Row | Case | Boundary | Oracle at the cut | Oracle after each restart |
|---|---|---|---|---|
| B8.7 | `W331_B8_TRIO_STAGED` | `B8_STAGED`: the group's retirement staged only | two REGISTERED receipts in the file, T in TRANSIT | LANDED: data and survey consumed, solar byte-equal and without receipt, owned 2 |
| B8.8 | `W331_B8_TRIO_MOVED` | `B8_MOVED`: the retirement replaced before its callback | Raw: no receipt for L remains | CONSUMED_REST: stationary consumed source, owned 2, solar byte-equal |
| B17.4 | `W331_B17_PARTIAL_D1` | `B1_MOVED`: the first bay's INTENT replaced while the next bay's INTENT can only queue | Raw: exactly one INTENT for L (data) with its op, `src_before`, `dst_before` and `package_sig` | INTERRUPTED for data; survey and solar without receipt; all three packages byte-equal; Raw: the REJECTED receipt keeps the cut's op and three hashes (immutable group hashes) |

### 3.6 Family R: the destination absent after D7 (B6.5)

T stays COMMITTED after the landing (`landed_reservation_retained`, seen in W325's logs), and the destination Level
saves the landed rocket only at a save or stop. `W331_B6_DESTINATION_ABSENT` runs the ordinary deployment with no cut,
waits for LANDED with T COMMITTED, then kills the server without saving.

- At the cut: no receipt; Raw: no entity naming L in any Moon entity region (the destination is absent).
- After each restart: ordinary destination reconstruction from the consumed T': LANDED, data bay consumed with its
  consumed op unchanged, owned 1, no receipt (no package resurrection); after the restart's stop, Raw: exactly one
  entity naming L in the Moon regions.

**Correction after smoke 3.** The restart found the source on disk (`source_count=1 destination_count=0
action=KEEP_SOURCE`). D6's barrier proof writes the consumed S' to its entity chunk, and the transfer removes the source
only in RAM, so after D7 a durable consumed S' always exists. Ordinary recovery therefore keeps that consumed source,
stationary at its origin, as W27A's B8 row allows ("ordinary restart may choose stationary consumed-source recovery"),
instead of rebuilding the destination. The oracle now requires: at the kill, no Moon entity and exactly the source S'
of L in the Overworld regions; after each restart, CONSUMED_REST with T's source UUID and the landing's consumed op,
owned 1, no receipt, no Moon entity and exactly one Overworld entity of L. A destination rebuild from T' would need S'
absent too, which this list does not manufacture (W27A B7: never delete a forced S' to make an absence). Whether this
reaches B6.5 as worded is for Root's review.

### 3.7 Family L: login while retirement is queued (B7.3)

New fixture variant `arce bay release-test exit fixture <pos> offline`: the facility rider's UUID is assigned seat 0,
but the rider is not placed (offline). `W331_B7_LOGIN_QUEUED` uses `B16_GUARD`. At D7's paused guard the harness logs
the rider in (`exit login`, the real `placeNewPlayer` path), checks the login result, adds a late source copy (the
existing `copy` command) and releases the guard.

- At the cut: after the login, the rider is online and not mounted, T is PREPARED with seat 0 still assigned; the
  guard then refuses (GUARD_REFUSED), a fresh D6 proof precedes READY_SOURCE (the existing `check_fresh_proof`), and
  the rocket lands; after the landing the rider is still not mounted (not pulled back).
- After each restart: LANDED, consumed, owned 1.

**Correction after smoke 3.** Every check during the queued retirement passed (the login was neither routed nor
settled, T stayed PREPARED with seat 0, the guard refused, and a fresh D6 proof preceded READY_SOURCE). After the
handoff, the normal acknowledged transfer carried the assigned online rider to the destination, as W27A section 4.1
says ("routed to the ready source, or the destination after normal acknowledged transfer"); the GameTest checks the
rider only at the handoff, before that transfer. The post-landing oracle now requires the rider aboard the landed rocket
(mounted, transfer COMMITTED, seat 0) instead of standing.

### 3.8 Family U: the admission union, captured and successor copies (part of B11.7)

`W331_B11_UNION` uses `B3_STAGED` (the data op is durable as INTENT and captured as REGISTERED) and queues `fill 1`
while the write is paused (a successor). The oracle requires `pending == 2` and `retained == 2` at the cut: each
unique op counted once. After each restart: LANDED, and pending 0, retained 0 (the successor was never durable).

The UNKNOWN member of B11.7 is not reached: a receipt write's UNKNOWN outcome is retried on the writer's next poll and
no gated control holds it, so B11.7 stays NOT_COVERED as a whole, with this case as partial evidence.

## 4. Smokes and disabled-protection checks

A native smoke runs one case of each family (T, W, P, D, G, R, L, U) on a locally built JAR of the committed source,
under the heavy-JVM lock taken per invocation. More cases run if time allows. Each smoke records the JAR hash and
the candidate check. These smokes are not candidate evidence.

Each family also runs once against a JAR built from a scratch export of the same commit with one protection disabled.
The oracle must fail, and the export is then deleted. The planned patches:

| Family | Disabled protection in the scratch copy | Case run | Expected oracle failure |
|---|---|---|---|
| T | `BayJournalCheckpoint.decide` answers CONTINUE for ASSEMBLY_JOURNAL_PENDING | `W331_B14_STALE` | D0 continues and writes INTENT for L |
| W | `BayRestartRows.row` ignores the registry (always REJECT_INTERRUPTED) | `W331_B15_REGISTRY` | the restart rejects instead of promoting |
| P | D0's step 2 skips `bayNeutral` | `W331_B6_PROTECTED` | the bays deploy |
| D, G | `BayRestartRows.claims` resumes no REGISTERED group (REGISTERED receipts become restart INTENT rows) | `W331_B4_GONE_X_S_ABSENT`, `W331_B8_TRIO_STAGED` | X absent gives REJECTED/INTERRUPTED, or the trio holds, not LANDED |
| R | none in production is narrower than ordinary reconstruction; the check removes the Moon reconstruction by having restart recovery skip a COMMITTED T with an absent destination | `W331_B6_DESTINATION_ABSENT` | the rocket is missing after the restart |
| R (after smoke 3) | the KEEP_SOURCE branch never restores the surviving consumed source (a second scratch JAR) | `W331_B6_DESTINATION_ABSENT` | the restart never settles at the consumed source |
| L | the D7 guard's token check always passes | `W331_B7_LOGIN_QUEUED` | no GUARD_REFUSED, no fresh proof |
| U | none: it adds an oracle to existing controls; its negative check is a Python test with a double-counted pending line | - | - |

If a patch does not apply cleanly to the code as it stands, the record names the substitute patch.

## 5. Rows left NOT_COVERED

| Row | Reason |
|---|---|
| B6.8 | Added after smoke 2 (section 3.3): a non-neutral source that can still launch needs a real ledger binding from a fluid-loader fill before the launch. That needs the loader fixture family (shared with CAP.3); not built in W331. |
| B10.6 | Reachable with a new gated variant of `seed-repair` that writes an incoherent REPAIR row (hashes that disagree with T). Not built in W331: the released-authority coherence rules need their own read and review. |
| B11.2 | A live rocket with seven escrow operations is escrow-locked, so D0 refuses its bays as BAY_SOURCE_PROTECTED before admission (W27A F3's note: production interlocks refuse earlier and are not bypassed). Only the authority unit fixture reaches the shared 8/rocket count. |
| B11.7 | Partial: see section 3.8. |
| B11.8 | Reachable with a gated owner fill/unfill pair (`BayReceiptTestHooks.put`/`remove`) and two relaunches of the REJECTED rocket. Not built in W331 (scope and time). |
| B11.9 | Needs 63 live escrow operations or a typed seed of the escrow ledger file. Reachable, but larger than a bay fixture; not built in W331. |
| B17.3 | No gated control keeps one group's end unloaded while another group runs; the END_LOADING wait ends when the coordinator's own tickets load the end, so the order is a race, not a cut. |
| B17.5 | Production holds a whole claim for repair (W170-R44: a REPAIR claim writes no further INTENT), so one live group cannot hold REGISTERED, REJECTED and REPAIR members; seeding them would forge receipts that name the live L. |
| CAP.3 | Reachable with a gated fluid-loader probe beside the seeded owner-full root (W325's `W325_OWNER_FULL`). Built only if time remains after sections 3 and 4; otherwise not built in W331. |

## 6. Not in this list

Production behaviour and admission, the cargo harness (W330), the shared-risk cases of the W327 audit (W332), the 31
GameTest rows (W333), the risk register, status files and QUEUE, and any run on a candidate.
