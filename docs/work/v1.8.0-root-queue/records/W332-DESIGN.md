# W332 cut list: native cases for R-042 (NOT_LOADED and the held pending pool)

Author: a headless Claude session (Claude Opus 5.5), 2026-11-10, worktree `D:/GitHub/arce-v180-w332`, branch
`codex/v1.8.0-w332`, based on the shared head `ef1d16fcc`. Scope: [TASK-W332](../TASK-W332-NOT-LOADED-AND-POOL-NATIVE.md),
Root decisions R392 and R404, W327's audit sections 1 and 4 and the C1 report's section 7
([INTEGRATED-C1-REPORT-20261109](INTEGRATED-C1-REPORT-20261109.md)). This list is committed before any code.

**Base.** The shared head already contains W328, W329, W328F, W328G and W334 (merge `0a877b4c6`), so every case below
runs against W328's pre-start refusal and W329's protective reads. Production admission stays off throughout: every new
fixture is a release-test command or start property behind the complete release-test gate
(`EscrowTestEndpoints.enabled`: the owner thread, `releaseTestHooks`, a dedicated server without RCON and only
facility-owned connections), and no production path calls it.

## 1. Harness

- **Runner.** A new `scripts/run_v180_r042_native.py`. It reuses the forced-stop runner's `Session` (start, console
  commands, status, pending, end), its fixture launch and status parser, the W331 candidate pin and
  `bay_integrated_evidence` (raw snapshots, receipt and registry readers, the typed receipt seeder). W325's and W331's
  tables, oracles and tests are not edited.
- **Candidate as an argument.** `--jar`, `--jar-sha256` and `--candidate-commit` are required. Before any Java start the
  runner refuses unless the JAR's SHA-256 matches and `git diff --quiet <candidate> HEAD -- src/main src/generated
  build.gradle settings.gradle gradle.properties` exits 0 (W331's `candidate_pin`). It rechecks the JAR at the end and
  records the `candidate` object in `results.json`. A driver also records `bay_integrated_evidence.py pin` per invocation.
- **Worlds.** Fresh flat worlds, as W325's runner makes them. Every start is a separate process. Each case has a first
  start (S0) and two restarts (S1, S2). A case passes only when all three starts and their disk oracles pass.
- **Error audit.** Every start, the first included, fails on any ERROR or FATAL line except the ones the case injects:
  vanilla's single `Error loading saved data: <name>` line for a load-failure start, and the single `[ARCE-BETA-2000]`
  refusal line for a refused start.
- **Disk oracles.** After every stop: W325's bounded raw snapshot (data files, entity regions, player data,
  `level.dat`), and the SHA-256 of the receipt file, the ledger file and the journals.
- **Game time.** Where a case shows that something does not advance, the runner brackets the window with the vanilla
  console command `time query gametime`, so the window is stated in ticks, not wall time.
- **Heavy-JVM lock.** A detached driver takes the backslash lock per invocation (atomic `cmd /c mkdir`), releases only
  its own token in its `finally`, waits at least 90 seconds before the next attempt, checks 10 GiB free on C: and D:,
  calls Python by its full path and deletes the disposable server copy.

## 2. Gated fixtures (Java)

1. **Load failure seam.** Start property `advancedrocketrycommunity.releaseTestLoadFailure` = `BAY_RECEIPTS` or
   `RESOURCE_ESCROW`. The two load-only lookups (`BayReceiptSavedData.existing`, `ResourceEscrowSavedData.existing`)
   pass vanilla a loader that first asks the seam. Vanilla calls that loader only when it actually reads the file (the
   first lookup of the session, then it caches the result). With the property naming the file's type, `releaseTestHooks`
   set, a dedicated server and the gate open, the seam logs one INFO `ARCE_RELEASE_LOAD_FAILURE type=<TYPE>
   injected=true` and throws. Vanilla's `DimensionDataStorage` catches it, logs its own ERROR and caches the miss, which
   is exactly what a present file that fails to load leaves; W328's startup classification then reports
   `reason=LOAD_FAILED`. With the property set and the gate closed, it logs one WARN `ARCE_RELEASE_LOAD_FAILURE refused`
   and loads normally. Without the property, or without hooks, or off a dedicated server, it does nothing.
   - The decision is a pure function, and a unit test covers every input: absent or foreign property, hooks off, not
     dedicated, closed gate (refused, no throw) and open gate (throws).
   - The file bytes stay valid, so the pre-start check passes them, as the audit's section 1.1 case 1 requires.
2. **R-042 probe commands**, `arce bay release-test r042 ...`, registered beside the other bay release-test commands only
   when the release-test property is set, each passing `EscrowTestEndpoints.requireEnabled` before it reads anything:
   - `cell <name> <owner>`: at a fixed cell position, five ordinary stationary rockets of that owner, each bound to one
     real device holding an operator-loaded root: a fluid fuel loader (1,000 mB rocket fuel; rocket bank empty), a fluid
     cargo loader (5,000 mB water; rocket tank empty), a fluid cargo unloader (empty; rocket tank 5,000 mB), an item cargo
     loader (10 items; rocket 60) and an item cargo unloader (60 items; rocket 10). These are W330's and W95's fixture
     shapes with the owner as a parameter.
   - `attempt <cell> <kinds>`: for each kind, the whole rocket and device envelopes, the encoded ledger (or `absent`)
     and the shared authority's counts before; then the gated per-device switch is opened, the device makes one real
     attempt (`admitFromQueue` for the loader, `attempt` for cargo), the switch is closed and everything is read again.
     One line per kind: status, admitted, unchanged, pending, retained, ledger.
   - `report <cell>`: per kind, the rocket and device amounts, markers and epochs, the device's lock, removal
     protection and refusal.
   - `plain <pos>`: an ordinary, fully fuelled rocket without a bay (motor, seat, guidance computer, tank), not launched.
     It flies through the existing `arce rocket release-test launch <rocket> moon <checkpoint>`.
   - `insert <pos>`: a satellite bay block holding an empty root of the bay fixture's owner, one real insertion of a
     fresh data package through the bay's item handler (the capability every surface uses), the remainder and the root
     reported, then the block removed without drops.
   - `rider board|login|sneak|state <logical>`: the W254 facility rider placed and seated on an ordinary rocket, its
     login after a restart, the vanilla sneak input packet (the client's dismount control), and its state.

## 3. Cases

Names in the result rows below are the runner's case names. "Refused" always means a refusal status, no admission, and
both envelopes, the ledger and the authority counts unchanged.

### 3.1 `U_BAY`: bay receipts, unknown presence (TASK item 2, first trigger)

**Historical correction (smoke 2), superseded by the W343 correction below:** the original ACL fixture was NOT_COVERED.
Sections 3.1 and 3.2 retain that original design for the historical smoke records.

- **Setup.** A typed seed of two REJECTED, unreleased receipts (W325's seeder; retained 2, pending 0).
- **S0.** Normal start: one `ARCE_BAY_RECEIPTS_INDEX state=LOADED receipts=2`, no NOT_LOADED WARN. Stop. The runner
  hashes every file under the world folder (manifest M0).
- **S1, refused.** The runner adds two deny entries for the server's user (the current user SID): full access on the
  receipt file, and list-folder on the `data` folder (this folder only). Both are needed: Windows grants read-attributes
  on a file to a caller who may list its folder. The harness's own token has SeBackupPrivilege enabled and bypasses
  deny entries (checked on this host), so this one start runs under a basic-user token of the same user
  (`runas /trustlevel:0x20000`), with `stop` piped to the console so that a start that is not refused also ends. The
  runner restores the access lists in a `finally` and checks that no deny entry is left.
  - Oracle: one ERROR `[ARCE-BETA-2000] Beta world data check blocked startup; type=BAY_RECEIPTS;
    reason=advancedrocketrycommunity_bay_receipts presence is unknown...`, whose hint starts with the storage
    instruction ("Fix storage access to the world folder and restart; ..."); no `Done (` line; no
    `ARCE_BAY_RECEIPTS_INDEX` line; a non-zero exit; and after the restore, manifest M1 equal to M0 (same files, same
    bytes).
- **S2.** Normal start after the restore: LOADED with 2 receipts, no NOT_LOADED WARN, the receipt file's bytes unchanged.
- **Does not show.** A production account without backup privilege (the basic-user token stands in for it); other
  causes of a failed status check (network storage, filter drivers); an unknown `data` folder.

### 3.2 `U_LEDGER`: the escrow ledger, unknown presence

- As 3.1, with the ledger file. S0 creates the ledger with one real admitted fuel fill in cell `S` and waits for it to
  complete. S1 is refused with `type=RESOURCE_ESCROW`. S2 starts with no `ARCE_ESCROW_LEDGER_NOT_LOADED` and the fill's
  result (rocket bank 1,000 mB, loader empty) still on disk.
- **Does not show.** As 3.1. It is included because W328 changed the ledger's pre-start check too.

### 3.3 `N_BAY`: bay receipts present but not loaded (TASK item 2, second trigger; audit section 1.2)

- **Setup.** A typed seed of two REJECTED receipts.
- **S0, normal.** LOADED with 2 receipts. The runner builds cell `A` (owner P1, not attempted); a `plain` rocket L
  launched to the Moon with the `transit_prepared` checkpoint (`ARCE_RELEASE_TEST_FLIGHT_PAUSED`); a registered
  satellite X of the bay fixture owner (`arce satellite release-test launch <owner> earth`, SUCCESS); and a stale
  assembly record A (W331's `stale-assembly`, in a dimension that never loads). `save-all flush`, normal stop. On disk:
  the transfer journal names L, the registry names X, the transaction journal names A.
- **S1, `releaseTestLoadFailure=BAY_RECEIPTS`.**
  - Logs: one `ARCE_RELEASE_LOAD_FAILURE type=BAY_RECEIPTS injected=true`, one vanilla `Error loading saved data:
    advancedrocketrycommunity_bay_receipts`, one WARN `ARCE_BAY_RECEIPTS_NOT_LOADED reason=LOAD_FAILED`, one INFO
    `ARCE_BAY_RECEIPTS_INDEX state=NOT_LOADED receipts=0`, and no other ERROR.
  - Ordinary flight without a bay: two status reads of L at least 600 game ticks apart both show no claim, T PREPARED,
    the rocket in TRANSIT at the same altitude and locked.
  - Satellites: a new release-test satellite launch answers `DEPLOYMENT_PENDING`; decommissioning X answers
    `DEPLOYMENT_PENDING` with X still present.
  - Assembly recovery: one WARN `ARCE_ROCKET_RECOVERY_FENCED transaction=A`.
  - Package insertion: `insert` returns the whole stack and the bay stays empty.
  - Fuel and cargo: `attempt A ALL` refuses all five kinds. Expected statuses from the code: `ESCROW_PENDING` for the
    loader and refusals for the cargo kinds; the exact statuses are recorded and pinned after the first smoke.
  - Retirement: `retire-rejected` of a seeded REJECTED receipt answers the `not_loaded` refusal.
  - After the stop: the receipt file's bytes unchanged from S0; the journals still name L and A; the registry still
    names X; no ledger file.
- **S2, no property (recovery).** One `ARCE_BAY_RECEIPTS_INDEX state=LOADED receipts=2`, no NOT_LOADED WARN, no fence
  WARN for A. L settles with no claim and no T, at rest. A new satellite launch succeeds and X's decommission succeeds.
  `insert` is accepted. `attempt A ALL` admits all five kinds, and `report A` then shows each transfer completed exactly
  once with no markers left. After the stop the transfer journal no longer names L.
- **Does not show.** A really corrupted file (the seam throws over valid bytes); a file that keeps failing, which needs
  the whole-world offline procedure; the station-warp rule; the operator diagnostics.

### 3.4 `L_LEDGER`: the escrow ledger present but not loaded (TASK item 3, W329's hold)

- **S0, normal.** Cells `S` (owner P2) and `A` (owner P1). One admitted fuel fill in `S` creates the ledger and
  completes. A `plain` rocket L stands at its origin, and the W254 rider is seated on it (`rider board`). `save-all
  flush`, normal stop. The ledger file's SHA-256 is G0.
- **S1, `releaseTestLoadFailure=RESOURCE_ESCROW`.**
  - Logs: one injection line, one vanilla `Error loading saved data: advancedrocketrycommunity_resource_escrow`, one WARN
    `ARCE_ESCROW_LEDGER_NOT_LOADED reason=LOAD_FAILED`, and no other ERROR. `arce escrow status` reports
    `ledger=not-loaded`.
  - No launch: `arce rocket release-test launch <L> moon countdown` answers a refusal code.
  - Fuel and cargo: `attempt A ALL` refuses all five kinds, and `report A` shows every device locked, protected from
    removal and refusing with `ESCROW_PENDING`. Cell `S` still shows its completed fill.
  - Vanilla dismount: the rider logs in. If the login seats it, the sneak input packet leaves it unseated. If the login
    does not seat it under the hold, the runner records that, and this row is NOT_COVERED with the reason.
  - After the stop: the ledger file's bytes equal G0.
- **S2, no property (recovery).** No NOT_LOADED WARN. `attempt A ALL` admits all five kinds and they complete. L's
  launch with the `countdown` checkpoint is accepted (then paused). After the stop the ledger file has changed.
- **Does not show.** A really corrupted ledger; settlement commands, disassembly and bay deployment under the hold.

### 3.5 `P_SERVER`: the server pool held by released and REPAIR receipts (TASK item 4)

- **Setup.** A typed seed of 64 receipts within every cap: eight rockets of eight receipts; owners O1 to O4, two rockets
  (16 receipts) each; rockets 0 and 1 released REPAIR (`REGISTRY_SIGNATURE_CONFLICT`), rockets 2 and 3 released REJECTED
  (`INTERRUPTED`), rockets 4 to 7 durable REPAIR (`TRANSFER_AUTHORITY_CONFLICT`). Cell `A`'s owner P1 and the bay
  fixture's owner are not among O1 to O4, so the server cap, not an owner cap, decides.
- **Every start (S0, S1, S2).**
  - `pending=64 retained=64`.
  - `attempt A ALL` refuses all five kinds with `ESCROW_FULL`, with no ledger file before or after.
  - A bay deployment (the W325 fixture, a new rocket each start) lands with the data bay unconsumed and no receipt
    naming its rocket; the bay's deployment status is recorded.
  - No online release: `retire-rejected` answers `released` for a released REJECTED receipt and `not_rejected` for a
    durable REPAIR and a released REPAIR receipt.
  - After the stop: the receipt file's bytes equal the seed's.
- **Does not show.** Receipts written by real deployments (typed seeds of valid rows, as W325 used); the offline
  whole-world repair that ends them.

### 3.6 `P_OWNER`: one owner's two fully loaded rockets (TASK item 4, per owner)

- **Setup.** 16 receipts of the bay fixture's owner on two rockets: one released REPAIR, one durable REPAIR. Cell `A`
  belongs to that owner and cell `B` to another owner P2.
- **Every start.** `pending=16`; `attempt A ALL` refuses all five kinds with `OWNER_PENDING_FULL`; the bay fixture lands
  unconsumed with no receipt naming its rocket; `retire-rejected` answers `not_rejected`; the receipt file is unchanged.
- **S2 only.** `attempt B ALL` admits all five kinds of the other owner, and they complete.
- **Does not show.** The bay admitting another owner while this owner is full: the bay fixture's owner is fixed. W325's
  `W325_OTHER_OWNER_ROOM` covers that union with INTENT seeds.

## 4. Disabled-protection checks

One scratch export of the tested commit, never committed, with four mutations, each active only when the start property
`advancedrocketrycommunity.w332Scratch` names its family. The runner passes it with `--scratch <family>`. Each family's
oracle must fail on that JAR:

| Family | Mutation | Expected oracle failure |
|---|---|---|
| U (`U_BAY`) | The pre-start check reads an unknown presence as absent | S1 is not refused |
| N (`N_BAY`) | `BayReceipts.start` reads LOAD_FAILED as NO_FILE | S1 has no NOT_LOADED lines; effects missing |
| L (`L_LEDGER`) | `EscrowRuntime.missingLedger` answers NO_FILE | devices not locked or the launch admitted |
| P (`P_SERVER`) | `BayReceipt.pending()` counts only INTENT and REGISTERED | pending is 0 and admission succeeds |

The scratch export is deleted after its JAR is copied out. Its runs are negative controls, not candidate evidence.

## 5. Not in this task

Production behaviour and admission; D6 timing, the 256-coordinate saturation and the downgrade (W336); the bay's other
native rows (W331) and GameTest rows (W333); the risk register, the status files and QUEUE; any candidate run (the
cases run on C2 with the rest of the matrix, R386).

## 6. Corrections after the smokes, before any candidate run

These were added after native smokes 1 and 2 on local JARs. No oracle was weakened; each correction is a harness or
fixture defect, or a case this host cannot reach.

1. **Historical W332 observation, superseded by the W343 correction below:** `U_BAY` and `U_LEDGER` were NOT_COVERED
   with the ACL fixture, and W332 removed them from its runner.
   What was tried (smokes 1 and 2, and probes under `D:/GitHub/ARCE-Task-Evidence/root-queue/W332/acl-probe/`):
   - A deny entry for the server's user on the receipt file alone does not make the status check fail. The harness
     token has SeBackupPrivilege enabled and bypasses deny entries. A basic-user token of the same user
     (`runas /trustlevel:0x20000`) also still reads the file's attributes, because Windows grants read-attributes to a
     caller who may list the folder.
   - Adding a list-folder deny on `data` makes the basic-user stat fail (`AccessDeniedException`, so UNKNOWN), but then
     vanilla 1.20.1 refuses the start itself: its world-folder validator walks every directory before the server is
     created and fails with `AccessDeniedException: .\world-U_BAY\data` ("Failed to start the minecraft server"). That is
     before `ServerAboutToStartEvent`, so W328's pre-start check never runs, and the JVM did not exit within the runner's
     420-second window.
   - Those ACL experiments did not reach UNKNOWN while allowing vanilla's walk. They do not establish that every
     possible Windows trigger is impossible. At W332's conclusion only its cited unit tests covered this refusal.
   - The disabled-protection check of family U (section 4) is dropped with the family.
2. **Admission attempts wait for the transfer journal** (smoke 1). Fresh cells and fresh restarts answered
   `TRANSFER_JOURNAL_UNPROVED` before the journal's checked write covered their rockets. The probe now waits, as
   W330's cap probe does, so a refusal is the admission's own answer.
3. **A rocket without bays has empty `packages` and `consumed` fields** in W325's status line (smoke 1). The runner
   reads the plain rocket with its own pattern; W325's pattern is unchanged.
4. **The insertion probe has its own feedback line** (smoke 2); the runner waits for it.
5. **`N_BAY`'s S2 decommission** (smoke 3). X was registered by the release-test satellite launch, which gives it a
   mission. Without the deployment fence, its decommission reaches the registry's own mission check and answers
   `MISSION_BUSY` with X kept. S2 now accepts that or SUCCESS with X removed; S1 still requires `DEPLOYMENT_PENDING`.
6. **`L_LEDGER`'s dismount row** (section 3.4) took its NOT_COVERED branch in smoke 2: the rider's login under the
   hold did not seat it (seat 0 stayed assigned). The hold starts only at startup and every mount is vetoed under it,
   so no player can be seated on a rocket during the session; the vanilla dismount cannot be exercised natively.

## 7. W343 labelled correction (2026-11-10, W340 finding F3)

W340's independent probes disproved the broader host-impossibility inference in section 6. A retained Windows handle
with `FileDispositionInfo.DeleteFile=true` leaves the directory entry visible to vanilla's walk while making the
no-follow attribute query fail. Clearing that disposition before closing the handle restores access without deleting
the file. No production seam or ACL change is needed.

The restored `U_BAY` and `U_LEDGER` cases use that harness-only handle on valid current-schema bytes. A separate Java
probe uses the pinned packaged JAR and installed libraries to observe the actual vanilla validator, actual ARCE
presence query, migration diagnostic, managed type and `isPresenceUnknown()`. S1 then starts the native server with the
same handle held. Its oracle requires one typed `ARCE-BETA-2000` refusal with the storage instruction first, no READY or
receipt-index line, and identical SHA-256 manifests for every world file after restoration. The handle is cleared in
a `finally` before closing, and the file hash is verified. S2 and S3 are clean recovery starts, so the restored cases
include a second clean restart in addition to the refused restart. An unreachable native refusal remains NOT_COVERED;
the metadata probe alone cannot pass the native case. Actual W343 results belong in [W343](W343.md).

W343 also tightens L_LEDGER's S2 oracle to require a present, changed ledger, and the P cases to require data-bay menu
`NONE / DEPLOYMENT_CAPACITY` (server) or `NONE / OWNER_PENDING_FULL` (owner), in addition to all existing byte, package
and receipt checks. W332's older smoke results retain their original identity and limitations.

**W343 first native observation:** both U types reach the exact refusal on this host. Pinned Forge/vanilla exits with
code 0 after propagating the startup exception and its known pre-Level shutdown error, as in the existing W157 runner;
the harness audits all six diagnostics and their exception identities. The original non-zero-exit assumption in section
3.1 was incorrect. However, vanilla updates `/Data/LastPlayed` in `level.dat` and rotates `level.dat_old` before this
Forge event. The full-world manifest assertion remains strict: these changes fail it. Recovery observations are retained
separately, and neither a passing metadata probe nor unchanged managed files can turn that failure into a case PASS.
