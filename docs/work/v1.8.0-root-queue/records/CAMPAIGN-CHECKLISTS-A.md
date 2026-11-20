# Final campaign checklists A: Stable Core MVP

Status: planned, not executed. These are A01 to A18 of
[FINAL-CAMPAIGN-PLAN](FINAL-CAMPAIGN-PLAN.md), section 4.1, expanded for P4 by W396.
No result or Gate is approved. The campaign plan remains the scheduling authority.

## Run conventions and prerequisites

- (C1, R535) Pin C5 commit `9172113ad4ecfb9047432f02b97d240fc06130b6`,
  hosted release `advancedrocketry-community-1.20.1-1.8.0-dev.jar`, SHA-256
  `e5cd2beb4a38b4ea741c5103b18ba224d3f102ef6231adbc3a0849ad4f1e35c8`.
  Its location is `D:/GitHub/ARCE-Task-Evidence/root-queue/C5/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar`.
  Hosted regression run `44681973284` attempt `1` and governance run
  `44681973374` identify these inputs (not new verdicts). R555 and C5-SWITCH-REPORT section 2.1 supersede the plan's old "not built" text. Sources/API
  JARs and local Maven publication still **needs package K01** at that same commit,
  with local-build labels/hashes; report any locally rebuilt release-hash difference
  without replacing the hosted tested JAR. Do not silently repin to a later candidate.
- P2 names the immutable fixtures: the **v0.9 Beta world archive**, **a v1.0 world**,
  **representative v1.3 to v1.7 worlds**, **the API fixture mod against C5**,
  **three rockets of 64, 512 and 2,048 blocks**, **the many-machine layout**, and
  **the reference workload world of docs/17 section 4**. A fresh-world row uses a
  new disposable world, not an upgraded fixture. Record the copy identity and
  before/after hashes of every immutable source. The relevant K02-K21 run manifests below must supply
  actual paths, dimensions, coordinates, owners, recipes, resource ledgers and
  pack hashes; these names alone are not a placement script.
- Unless a row says otherwise: Java 17; Minecraft 1.20.1; packaged C5 server and
  matching real client; Forge **47.4.10**, JEI **absent**. Matrix overrides are
  Forge **47.4.23** and JEI **15.56.0.205**. Record each cell separately.
- DS uses one real client on the development PC and a loopback packaged server;
  isolated offline loopback is permitted in phase 3 only. V1 needs a real GPU,
  not LLVMpipe. V2 uses two independent premium accounts A (owner) and B
  (passenger/non-owner), signed in by the owner, with `online-mode=true`,
  `white-list=true`, `enforce-whitelist=true`, `enable-rcon=false`, and access
  restricted to those clients. The owner supplies account UUIDs, never credentials.
- All rows here use ordinary disposable worlds, **not** O2's marked bay/cargo/fuel
  campaign world. Keep production admission switches off. JVM test properties
  are absent except A04's isolated checkpoint probes. A05/A15 are core passenger
  tests, not permission to enter a disabled bay or enable gated cargo/fuel.
- `[H]` means the harness can drive the action after P3; `[P]` needs a human hand
  or judgment. A scripted real-client observation still needs its media reviewed.
  Console commands below omit `/`; player chat commands include `/`.
- Stop/restart means console `save-all flush`, then `stop`, wait for JVM exit,
  then launch the same copied world with the recorded Forge launcher command.
  Phase 3 restart rows require **two restarts** of that same copy, with state
  comparison after each. Do not claim that a clean restart is a forced-stop test.
- Every row inherits [master test plan](../../../05-MASTER-TEST-PLAN.md) section 11's
  result form and [quality budgets](../../../17-V1PLUS-QUALITY-BUDGETS.md) sections
  3, 4 and 8. Product ERRORs and unexplained repeated WARNs remain failures.
  Preserve failed attempts. Missing fixtures/tools are NOT_COVERED, never PASS.

### Evidence convention for every row

Use `D:/GitHub/ARCE-Task-Evidence/final-campaign/9172113ad/` (outside Git).
Archive these checklists as run in `checklists/`. V1 goes in
`phase2-v1/<machine>/<round>/`; DS/S2 in `phase3-ds/<row>/`; V2 in
`phase4-v2/<sitting>/`; REF/SOAK in `phase5-ref/<run>/`. Use screenshots
`<row ID>-<step>-<client>-<language>-<scale>.png` and videos
`<row ID>-<client>.mkv`; keep native logs in the row/round folder. Give every
result the candidate commit and JAR hash, Forge/JEI versions, machine, tester
(including harness script commit), UTC start/end, PASS/FAIL/NOT_COVERED, evidence
reference and SHA-256, finding or non-coverage reason. Record it in
`results/RESULTS.md` and `results/results.json`; findings go in `results/FINDINGS.md`.
Finish the campaign's `SHA256SUMS.txt` only after collecting the files.

### Accepted procedures and delivered tooling (applies to every A-J row)

Sources: `CAMPAIGN-PREP-BREAKDOWN.md` (accepted C1-C44/R535),
`W399.md`, `W400.md`, `W402.md` (R546 is in `../QUEUE.md` row W402),
`W403.md` and `W406.md`. Row-specific additions below are
preparation/action steps, not executed results. Remaining inputs explicitly name
their package. Nothing in this pass removes an action or supplies a new limit.

**Display cells (C2 as changed by R535).** Use windowed **1280 by 720 at
effective scales 2 and 3**, both languages, and native-resolution fullscreen
at scale 4 **only if offered** (1920 by 1080 offers it). Record effective scale
and actual dimensions on every screenshot: requesting 4 at 1280 by 720 clamps
to 3 and is not a scale-4 result. This mapping replaces generic "2/3/4 in both
modes" wording throughout A-J. No supported minimum is declared; smaller
windows are not tested. Inaccessible key controls in a tested cell are findings
under docs/17 section 8. K16 regenerates controls; never replay old pixel positions.
J14 retains its explicit 1-6 HUD investigation, recording unavailable scales.

**Prepared runtime cells (W400).** Use independent copies, never the templates
as writable servers. Each manifest pins runtime, server/client templates,
candidate/JEI copies and hashes; JEI is client-only.

| Cell | Manifest (outside Git) | Manifest SHA-256 |
|---|---|---|
| `47.4.10-nojei` | `D:/GitHub/ARCE-Task-Evidence/root-queue/P3/cells/47.4.10-nojei/cell.json` | `be0ea5d9e7b1086fa416c371fee5113e235159de5d2f464f512bdf6c32389cca` |
| `47.4.10-jei` | `D:/GitHub/ARCE-Task-Evidence/root-queue/P3/cells/47.4.10-jei/cell.json` | `27afd57cfa430089fa89d7586187d4335bc0db260adab3ef79516718cebd3e58` |
| `47.4.23-nojei` | `D:/GitHub/ARCE-Task-Evidence/root-queue/P3/cells/47.4.23-nojei/cell.json` | `956c5a625b77719182c43f453d9ef7952e36e0361c3075d24106c0647c542c6f` |
| `47.4.23-jei` | `D:/GitHub/ARCE-Task-Evidence/root-queue/P3/cells/47.4.23-jei/cell.json` | `23e8f1404e34f5ee4bb4b3bf28c8456284da39f939a4054f13123ecb95fafa4b` |

**Harness commands and session fields (W399).** Read the
tracked guide `scripts/campaign_client/README.md`.
Bind `$session`, `$run`, `$ops`, `$csv`, `$clientPid` and
`$frameJson` to the row's K16/K11 session manifest under the evidence root;
they are not invented fixture filenames. Then use:

```powershell
python -B scripts/run_campaign_client_session.py check $session
python -B scripts/run_campaign_client_session.py session $session --jvm-lock D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm
python -B scripts/run_campaign_client_session.py request $run "@$ops" --mode attach --players $playerName
python -B scripts/run_campaign_client_session.py frames $csv --pid $clientPid --threshold-ms 50 --output $frameJson
```

The request example is for attach; match `--mode` to the actual session.
Set `schema_version=1`, `mode=launch|attach`, `cell` to a manifest above,
a fresh `run_dir`, Java 17 `java`, `singleplayer`, bounded
`time_limit_seconds` and `input_interval_seconds>=0.05`. Each client has
`player_name`, `language`, `gui_scale`, `window_size`; attach additionally
has owner-declared `pid`, `account_label`, `game_dir`. Server fields are
`online-mode`, `white-list`, `enforce-whitelist`, `server-ip`,
`server-port`, `whitelist`, `ops`, `jvm_properties`, `max_heap`.
Whitelist/ops entries contain `name` and canonical `uuid`. Phase 4 sets all
three booleans true, restricts access, uses O10's two UUIDs and keeps RCON off.
Default heap is 3G; REF never exceeds 4G. Properties are individual
`jvm_properties` entries, not a shell string. Marker creation is still K11.
Attach never signs in, steals focus or closes clients; the owner foregrounds one
window per batch. Preserve `results.jsonl`, `summary.json`, `attach.json`
where applicable, native F2 frames and redacted logs in `evidence/`; index
them in the plan's row folders. `COMPLETE` alone proves no pass.
A window appearing is not a join receipt; no automatic reconnect hides failures.
Historical regex waits need conversion to literal substrings. Live launch,
input, window sizing and cleanup still **needs package K16** qualification.

**Delivered K13 inventories/resource packs (W402).** The directory is
`D:/GitHub/ARCE-Task-Evidence/final-campaign/9172113ad/fixtures/K13/`.
Use its tables for C30/C38/I11, not older inventories. These hashes identify
prepared inputs, not successful live resource loads. K13 still owes live JEI
counts and the audio-enabled profile; scenes/batches are the row's K05/K14/K16
inputs. The long-text pack lengthens translations while preserving placeholders;
it is not a fabricated different font.

| File | SHA-256 | Use |
|---|---|---|
| `classification.csv` | `a34f4246da91c11f0d2ef1325938cff19496ecec2f532b21a8811169b2048297` | I02/I06/I08 complete high-risk and sampled classes (C30). |
| `screens.csv` | `ffeceeb9340c2d08fef7669218d372acb7c2891606507d4879248686eea0219f` | Every-screen/every-language navigation, including eleven unnamed elsewhere. |
| `sound-producers.csv` | `84cafa56eaa18848e21dd7f87497b26a5a720e7d66aa164105834d2785ef48cf` | J12-J16 actual C5 producers and previews (C38). |
| `access-matrix.csv` | `3888f04c844db2deeb2cec16770f2d2f417a7414de031126672ae481d111d882` | I11 holder/policy/receipt branches, not blanket ownership. |
| `k13-long-text-pack.zip` | `ae33c19e54517030aa3ad51232effb4c2fb6b7baa4c4b4900c5e393f64ad5a30` | J16 long-text/reason/hover stress; actual load **needs package K16**. |
| `k13-missing-resource-pack.zip` | `81c43eea624037097b995d69d4317ee38a2b269f9fe99685c635acb35e88e1cf` | I07's seven missing/corrupt resource controls; actual load **needs package K14**. |

**Refusals (C14 corrected by R546; every refusal step in A-J).** For each
negative intent, retain the intent tool's own receipt with sender/menu/target,
sequence and time/tick where available, and authoritative before/after state
read back (resources, authority, rights, binding and loaded chunks as relevant).
Use a server log only for branches whose code emits one; never demand or invent
a log line for a silent branch. An input timestamp is not a server receipt.
K18 must qualify that receipt/readback method; an unreachable or uninstrumented
branch is NOT_COVERED with its reason and named A1 test, never proved by a
disabled button, closed UI or unchanged screenshot alone. Normal interaction
and command refusals retain their response plus state readback; when their
receipt cannot be established, keep that gap explicit. No product logging is
added. `suit_motion` targets only the sender's worn jetpack: no distance step
applies (R546). Keep other applicable sender/permission/state/load/rate checks.

**Matched samples (C5/C10 as changed by R535).** Back-to-back comparisons use
the same complete world copy, cell, machine, settings, viewport and sampling
plan, with the measured feature present/absent and two baseline runs. Discard
a two-minute warm-up, retain ten minutes of short-run samples (procedure
parameters, not budgets); long/specific windows in A08/H07 remain as specified.
For C5 pair Vanilla and Forge empty scenes too. Use W399's frame definitions:
average FPS is frame count / summed frame duration, 1% low is the reciprocal
mean of the slowest ceiling-1% frames; retain first stall, longest frame,
tool/version and raw CSV. The 50 ms argument is reporting only: no FPS floor,
stall limit or percentage allowance exists. A difference beyond the two
baselines' spread triggers Root review, **not** automatic failure or Medium
severity; severity follows docs/17 section 3. REF's absolute budgets still apply.

**Observers and evidence utilities (W403).** E is
`D:/GitHub/ARCE-Task-Evidence/final-campaign/9172113ad/`; keep the plan's
nine folders, row results, native media and hashes. Bind `$identity` to JSON
with `git_commit`, `jar_sha256`, `forge`, `java`; bind `$phase` to
the completed phase folder. K06/K13 already created fixture directories: the
creator refuses an existing root, so use `--check` once the complete root and
identity exist; never overwrite them. Root creation/identity reconciliation still
**needs package K15** integration, with all existing fixtures preserved.

```powershell
python -B scripts/run_campaign_observer.py root D:/GitHub/ARCE-Task-Evidence/final-campaign 9172113ad --identity $identity --drive C:/
python -B scripts/run_campaign_observer.py root D:/GitHub/ARCE-Task-Evidence/final-campaign 9172113ad --identity $identity --drive C:/ --check
python -B scripts/run_campaign_observer.py process $serverPid $rssCsv --duration 600 --interval 1
python -B scripts/run_campaign_observer.py host $hostJson --provider $provider --plan $plan --disk $disk --run-json $runFields
python -B scripts/run_campaign_observer.py jfr-file $recording $jfrJson --jfr $jdkJfr
python -B scripts/run_campaign_observer.py seal $phase
python -B scripts/run_campaign_observer.py verify $phase
```

Bind remaining variables to K15's measured run manifest, not arbitrary PIDs.
Seal/verify only completed folders; never overwrite a seal. A correction is a new
file/recorded reseal as the plan requires, not an edit of sealed evidence.
For REF use `bash scripts/campaign_observe/ref_acceptance.sh <provider> <plan> <evidence-folder> <backups-folder> <new-host.json> <run-fields.json> <repo>`;
manual acceptance items remain open even on exit 0 (owner O11).

For a harness-owned server, K15 calls
`sample_ticks(channel, csv_path, duration=600, threshold_ms=500)` with a
bounded console channel; it issues `arce satellite release-test perf ticks`
or read-only `arce endgame release-test refload status`. Those diagnostics
need release-test hooks/permission 2 on a separately documented diagnostic run;
ordinary no-hook controls stay no-hook. W403 exports **window statistics**,
not ordered ticks: no whole-run p95/p99 or consecutive-spike claim from their
averages. Required raw-tick integration still **needs package K15** (R544's W407
follow-up), as do queue, all-Forge-ticket and packet/cache-internal observations.
Vanilla `forceload query` alone cannot prove all tickets absent.

Wrap only a harness-owned `Popen` in `CampaignJvm(popen, "server")`;
allowlisted `GC.heap_info`, `GC.class_histogram -all`, `JFR.start` and
`JFR.stop` provide heap/GC/JFR data. Use bounded server JFR (normally 600 s,
128 MiB retention) and separate restart segments. For disposable owned clients
only, measure heap/histogram before/after reload and dimension changes, outside
frame windows. Histograms include unreachable objects and are growth proxies,
not proof of cache release, cache keys or GPU allocations; unavailable direct
cache/allocation readback is NOT_COVERED pending K15. Never attach diagnostics
to owner-signed-in clients or inspect JVM arguments.

## A01 - Fresh packaged server lifecycle

**Header:** Phase 3; DS; development PC, one real client/account A; ordinary world,
switches off, no JVM test properties. **Sources:** [v1.0 section 10.1](../../../versions/V1.0.0-COMMUNITY-MVP.md)
and [manual M01](../../../releases/v1.0.0/MANUAL-TEST.md).

**Preconditions:** Fresh client/server directories installed from the documented
distribution; P1 identity; baseline Forge, JEI absent. Record the EULA decision,
actual launcher command, server properties and initially empty world directory.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Start the packaged server with its Forge-generated launcher. | Server reaches readiness; correct C5 mod version; no client-only class-loading failure. |
| 2 | [H] Connect A using the matching clean client. | A enters the new world; client Mods entry agrees with the installed candidate. |
| 3 | [H] Issue `save-all flush`. | Save succeeds. |
| 4 | [H] Issue `stop`. | Server exits normally; retain exit code and complete log. |
| 5 | [H] Start the same server again. | Same world opens, not a replacement world. |
| 6 | [H] Reconnect A. | Correct version and state; no side-loading failure. |
| 7 | [H] Repeat steps 3 to 6 for the second restart. | The same world remains usable. |

**Evidence to keep:** `phase3-ds/A01/` installation manifest, launch commands,
all start/stop logs and exits, client log, Mods/world screenshots and world identity.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A02 - Fresh-world core flow

**Header:** Phases 2 (visible flow) and 3 (server flow); DS, V1; development PC,
one real GPU client/account A; ordinary world, switches off, no test properties.
**Sources:** [v1.0 section 10.2](../../../versions/V1.0.0-COMMUNITY-MVP.md),
[M02-M06, M08-M09](../../../releases/v1.0.0/MANUAL-TEST.md), and
[visual scenes, section 13](../../../15-REMOTE-LINUX-DEVELOPMENT-AND-VISUAL-VALIDATION.md).

**Preconditions:** A new world, not the Beta copy; baseline Forge/JEI absent.
The core scene **needs package K16** to inventory craftable materials, electrolyzer recipe, powered
oxygen room, suit, legal rocket, invalid variant, fuel, station materials,
research/mission inputs and their placement. These sources do not specify their
coordinates or all quantities; record those inputs before execution.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Craft the fixture's core materials. | Recipes yield their recorded outputs with the recorded input debit. |
| 2 | [H] Craft the electrolyzer. | One machine is obtained; no missing name/icon. |
| 3 | [H] Process one recorded electrolyzer batch. | GUI is readable; exact recipe debit/output agrees with the fixture recipe. |
| 4 | [H] Equip the supplied life-support equipment. | Server equipment state and oxygen HUD agree. |
| 5 | [H] Enter vacuum outside the supplied room. | Protection/debit follows equipment oxygen, not a client-only safe indication. |
| 6 | [H] Enter the supplied sealed room. | Room protection and oxygen feedback match server supply. |
| 7 | [H] Open the recorded room boundary. | Sealed-room protection is revoked. |
| 8 | [H] Restore that boundary. | Protection returns only after valid sealed/supplied state. |
| 9 | [H] Inspect the invalid rocket variant in the assembler. | Actionable rejection; source blocks/items unchanged. |
| 10 | [H] Assemble the legal rocket. | Exactly one rocket authority; structure and inventory match the recorded source. |
| 11 | [H] Load the recorded legal fuel. | Only accepted fuel is consumed; loader and rocket totals reconcile. |
| 12 | [H] Attempt the fixture's invalid fuel. | Refusal without item loss or unauthorized fuel gain. |
| 13 | [H] Board the legal rocket. | Seat assignment and mounted position match. |
| 14 | [H] Select the Moon in the rocket console. | Server fuel quote is visible and matches the selected target. |
| 15 | [H] Confirm launch. | Moon arrival; one debit, one rocket, intact structure/inventory/seats. |
| 16 | [H] Leave the landed rocket. | Current seat membership is removed. |
| 17 | [H] Board it again. | Current seat state, not a stale trip manifest, is used. |
| 18 | [H] Refuel through the ordinary loader. | Recorded item/buffer/rocket fuel ledger balances. |
| 19 | [H] Launch the return to Earth. | Earth arrival without duplicate rocket/inventory or stale-seat teleport. |
| 20 | [H] Create a station through the station controls. | One owned station with a stable identity. |
| 21 | [H] Travel to that station. | Authorized landing and correct station environment. |
| 22 | [H] Start the fixture's basic satellite mission through its powered terminal. | Components/package/chip binding and mission target match the recorded definition. |
| 23 | [H] Claim the completed mission. | One captured research reward and discovery result. |
| 24 | [H] Repeat the claim. | No second reward or resource debit. |
| 25 | [P] Review the flow's screens and world views. | Electrolyzer, oxygen, rocket, station and research feedback agree with the server. |

**Accepted preparation/action steps (W408):**

(C2) Run the effective display cells in A's common procedure. **Needs package K16**: C5 core scene, exact recipe/resource/position manifest and regenerated controls. Apply R546 to invalid-fuel/structure and replay refusals.

**Evidence to keep:** V1 screenshots at steps 3, 5-8, 10, 14-15, 19-24; client
and server logs, `results.jsonl`, `summary.json`, resource ledgers and identity
projections in the phase-2 round and `phase3-ds/A02/`. A06 supplies the separate
two-account invitation/rejection/ownership matrix; A02 alone does not cover it.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A03 - Beta-world upgrade and repeated reopen

**Header:** Phase 3; DS; development PC and client/account A; ordinary copied
world, switches off, no test properties. **Sources:** [v1.0 section 10.3](../../../versions/V1.0.0-COMMUNITY-MVP.md),
[M11](../../../releases/v1.0.0/MANUAL-TEST.md), and
[migration procedure](../../../releases/v1.0.0/MIGRATION-REPORT.md).

**Preconditions:** P2 **v0.9 Beta world archive**, 8,403,030 bytes, SHA-256
`9022b2fdf4537168bc33f1db06f96c8d5ce150c221177cd4e0890b037f9bed1c`.
Baseline Forge/JEI absent; original archive and file manifest remain read-only.
The fixture includes paused/running electrolyzers, supplied Moon room, Earth
and Moon rockets, station rights, active/ready/claimed missions and research.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Hash the original archive and each original world file. | Archive matches the stated hash; retain the complete file baseline. |
| 2 | [H] Copy the stopped fixture to a new run directory. | Original is not the server's writable world. |
| 3 | [H] Open the copy with hash-checked C5. | Supported migration or an explicit retained failure; no silent loss. |
| 4 | [H] Capture field-level machine, atmosphere, rocket, station, mission, research and schema state. | IDs and resources reconcile; legitimate elapsed processing/oxygen consumption is accounted for. |
| 5 | [H] Save/stop the copy using the run convention. | Complete saved state and clean exit. |
| 6 | [H] Reopen the same copy. | No second migration debit, reward, duplication or reset. |
| 7 | [H] Compare the same field projections. | Identity equality and resource ledgers hold. |
| 8 | [H] Repeat steps 5 to 7 for the second reopen. | Idempotent reopen again. |
| 9 | [H] Continue a machine batch and an authorized flight/mission action. | Gameplay remains usable, not merely a successful startup. |
| 10 | [H] Rehash the original fixture and backup. | Every original hash remains unchanged. |

**Evidence to keep:** `phase3-ds/A03/` archive/file hashes, copy manifest,
all three open logs, pre/post projections and comparisons, gameplay receipts,
backup verification and any failed copy. Do not expect the old report's
tick-dependent final machine progress on a different-duration C5 run.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A04 - Durable transaction checkpoints and forced recovery

**Header:** Phase 3; S2; development PC packaged server, **no connected players**;
ordinary isolated copies, switches off. **Sources:** [v1.0 section 10.4](../../../versions/V1.0.0-COMMUNITY-MVP.md),
[recovery matrix](../../../releases/v1.0.0/RECOVERY-MATRIX.md),
[transaction contract](../../v1.0.0-transaction-recovery/VERIFICATION.md) and
[commands](../../v1.0.0-transaction-recovery/COMMANDS.md).

**Preconditions:** Baseline Forge/JEI absent; fresh per-case copies of the P2
core rocket fixture and its baseline summary, retargeted to C5. The historical
five-block fixture contains a tank, motor, chest, seat and guidance computer;
chest slot 0 has 17 diamonds and slot 26 has 64 iron ingots, with no third item.
Assembly/disassembly probe properties are
`-Dadvancedrocketrycommunity.releaseTestHooks=true` and
`-Dadvancedrocketrycommunity.transactionCheckpoint=TYPE:PHASE:PROGRESS`.
Checkpoint selection is single-use and dedicated-server-only; normal recovery
has no selected checkpoint or injected fault. The runner retains the release-
test hook property for its bounded operator reports on recovery processes.
Use the existing [transaction runner](../../../../scripts/run_v100_transaction_forced_stop.py)
and [flight runner](../../../../scripts/run_v100_flight_forced_stop.py), not a
graceful stop masquerading as a cut. Their C5-compatible baselines **needs package K02**.

**Command templates for the campaign, not commands run while preparing this
file:** Bind `$sourceServer`, `$baselineSummary`, `$candidateJar`,
`$candidateVersion`, `$candidateCommit` (full SHA), `$java17`, `$txnSession`,
`$flightSession`, `$txnEvidence` and `$flightEvidence` to the P1/P2 manifest.
Session/evidence targets must be fresh, owned and separate from the immutable
source. Record fully expanded arguments in the run evidence. Omitting `--case`
selects the runner's complete matrix; do not retain its old version default.

```powershell
python -B scripts/run_v100_transaction_forced_stop.py $sourceServer `
  --session-dir $txnSession --baseline-summary $baselineSummary `
  --evidence-dir $txnEvidence --artifact $candidateJar `
  --base-commit $candidateCommit --expected-version $candidateVersion --java $java17
python -B scripts/run_v100_flight_forced_stop.py $sourceServer `
  --session-dir $flightSession --baseline-summary $baselineSummary `
  --evidence-dir $flightEvidence --base-commit $candidateCommit `
  --expected-version $candidateVersion --java $java17
```

The flight source copy must already contain the hash-checked C5 JAR. Run only
when Root schedules the isolated S2 slot; these templates do not take the C5
campaign's occupied heavy-JVM lock or authorize a run during W396 preparation.

**Case expansion:** Run each transaction tuple once: assembly
`SNAPSHOT_VALIDATED:0`, `LOCKED:0`, `EXTRACTING:1` through `EXTRACTING:5`,
`EXTRACTED:5`, `SPAWNED:5`, `COMMITTED:5`, `ROLLING_BACK:2`, `ROLLED_BACK:2`,
`FAILED:1`; disassembly `SNAPSHOT_VALIDATED:0`, `LOCKED:0`, `RESTORING:1`
through `RESTORING:5`, `RESTORED:5`, `COMMITTED:5`, `ROLLING_BACK:2`,
`ROLLED_BACK:2`, `FAILED:1` (25 cases). The runner injects the documented
rollback fault after mutation 2, and one failed undo for FAILED. Flight cases
are `ASSEMBLED`, `FUELED`, `COUNTDOWN`, `ASCENT`, `TRANSIT_PREPARED`,
`DESTINATION_SPAWNED`, `PASSENGERS_TRANSFERRED`, `SOURCE_REMOVED`, `DESCENT`,
`LANDED` (10 cases). Record any C5 incompatibility; never suppress a case.

| Step | Action, for each case | Expected observation |
|---|---|---|
| 1 | [H] Launch the owned per-case server with that runner's recorded arguments. | C5 hash and intended case are logged. |
| 2 | [H] Wait for the exact persisted checkpoint marker. | Transaction marker is `ARCE_RELEASE_TRANSACTION_PAUSED`; on-disk phase/progress matches. Its 20-second pause limit is unchanged. |
| 3 | [H] Force-stop only that owned JVM with the runner. | Live process dies nonzero, without a prior graceful stop. |
| 4 | [H] Archive the staged journal and raw world state. | Evidence matches the selected cut before recovery starts. |
| 5 | [H] Restart without checkpoint/fault selection. | Production recovery selects one authority, preserving structure, inventory, fuel, passengers and journal semantics. |
| 6 | [H] Compare the recovered field projection with the case oracle. | No duplicate rocket/drops or simultaneous source blocks and entity; all chest slots/metadata match exactly. |
| 7 | [H] Complete the transaction runner's normal entity disassembly when applicable. | Restored contents equal the baseline, not just a matching item substring. |
| 8 | [H] Save/stop normally. | Clean exit and durable recovered state. |
| 9 | [H] Restart that recovered copy again. | No repeated recovery/debit/reward; transaction cases retain zero rockets and zero journal entries after normal cleanup. |
| 10 | [H] Recheck the raw saved state. | Case authority, resources and identities remain correct. |

Flight expectations: PREPARED cuts at COUNTDOWN/ASCENT/TRANSIT_PREPARED keep
the source; DESTINATION_SPAWNED/PASSENGERS_TRANSFERRED keep the destination
and remove the source; SOURCE_REMOVED keeps destination; DESCENT/LANDED are
committed. Before transfer, retain original fuel; after transfer, debit once.
Keep ASSEMBLED/FUELED as settled states. Use the runner's exact current oracle,
not the historical JAR's schema numbers. Preserve the reconnect bounds named
in the release matrix: 128 pending players, four attempts/tick, 200-tick wait.

**Accepted preparation/action steps (W408):**

(C3) Qualify both v1.0 forced-stop runners on C5 themselves; J22 is not a substitute. Any oracle change must be reviewed and state its new check. **Needs package K02**: retargeted baselines and deterministic adjacent-chunk arrival, current-seat selection and native-expiry controls. Record those as three separate observations; any control still unavailable is NOT_COVERED with that reason, not covered by durable cuts.

**Evidence to keep:** `phase3-ds/A04/<case>/` command lines, PIDs, cut receipts,
exit codes, staged journal, raw pre/post NBT, authority/resource comparisons and
both restart logs; aggregate all 35 outcomes. These are explicitly flushed
durable checkpoints, not disk-write power-loss, genuine storage-latency or
2,048-block performance evidence. J22 remains the separate C5 restart campaign.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A05 - Owner/passenger flight disconnect and continued use

**Header:** Phase 4, sitting 1; V2; two real GPU clients/accounts A and B on
the authenticated dedicated server; ordinary world, switches off, no properties.
**Sources:** [v1.0 section 10.5](../../../versions/V1.0.0-COMMUNITY-MVP.md),
[M05/M07](../../../releases/v1.0.0/MANUAL-TEST.md) and
[multiplayer report](../../../releases/v1.0.0/MULTIPLAYER-REPORT.md).

**Preconditions:** P2 core legal rocket with at least two permitted seats, A
owner, B passenger; baseline Forge/JEI absent. Capture logical/entity IDs,
inventory, fuel, seat assignments and positions before boarding. O5 supplies
the second operator or authorized scripted input to a signed-in window.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [P] Board A. | One authorized seat assignment. |
| 2 | [P] Board B. | Distinct valid seat; both clients agree on passengers. |
| 3 | [P] Launch toward the Moon from A's console. | One server quote/debit and active plan. |
| 4 | [P] Disconnect B while the server reports flight in progress. | A/rocket continue; retain exact flight phase at disconnect. |
| 5 | [P] Reconnect B with the same signed-in account. | Authorized current-seat recovery only when entities are ready; no duplicate player/rocket. |
| 6 | [P] Inspect the landed rocket on both clients. | Same dimension, logical rocket, inventory and seats; stable passenger positions. |
| 7 | [P] Use B's console LEAVE control after landing. | B is no longer in the current seat manifest. |
| 8 | [P] Disconnect B away from the old seat. | Retain the actual departed position. |
| 9 | [P] Reconnect B again. | B stays departed, not teleported by the old flight manifest. |
| 10 | [P] Board B again. | Current seat assignment restores once. |
| 11 | [P] Launch the normally refueled return from A's console. | Both clients see the same Earth arrival; no duplicated inventory or stale seat. |

**Accepted preparation/action steps (W408):**

(C3) Keep the three native readiness/current-seat/expiry observations separate as A04 requires. **Needs package K02** for their genuine controls and **needs package K16** for the two-seat scene; owner O5 supplies coordinated real input.

**Evidence to keep:** `phase4-v2/sitting-1/` both videos/logs, server phase and
seat receipts, disconnect/rejoin UTC times and saved resource projections.
Historical 618 fuel/118 buffered values are not generic C5 expectations; compute
this fixture's ledger from its actual quote. Controlled readiness instrumentation
does not count as a genuine disk-arrival or expiry observation.

**Additional native recovery subcases from the release recovery matrix:** Use
P2's separately prepared adjacent-chunk/current-seat and expiry fixtures. These
are not supplied by the old debugger-assisted run; without a repeatable native
fixture and bounded observer, record the subcase NOT_COVERED. Keep the documented
128-player queue capacity, four attempts/tick and 200-tick wait unchanged.

| Step | Action, on each separately backed-up subcase | Expected observation |
|---|---|---|
| 1 | [H] Capture the journal, chunk/entity readiness, current seat and player-position baseline. | Actual state is recorded before the reconnect, not inferred from a screenshot. |
| 2 | [P] Reconnect the authorized account during the fixture's genuine entity-not-ready interval. | No teleport based solely on block-chunk readiness. |
| 3 | [H] Observe actual adjacent-chunk entity arrival in the readiness variant. | Recovery uses the current authorized seat after entities become ready, exactly once. |
| 4 | [H] Observe the unchanged 200-tick deadline in the separate expiry variant. | Unresolved entry expires without a stale/unauthorized move; queue stays bounded. |
| 5 | [P] Disconnect the account while pending in the logout variant. | Its pending queue entry is removed, without moving that disconnected player or another account. |
| 6 | [P] Rejoin that account once its actual target is ready. | Fresh authorized recovery, no leaked previous queue state. |
| 7 | [H] Compare both players' positions, seats and saved rocket projection. | Single authority and no extra recovery/debit; keep the native outcome even if readiness/expiry was not reached. |

Keep the corresponding raw readiness/queue/tick and real connection evidence
with A05. The source gives no genuine latency-injection setup or exact adjacent
fixture coordinates; these inputs **needs package K02**, rather than converting the
historical JDI false-return experiment into a claimed native observation.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A06 - Non-operator station permissions and ownership

**Header:** Phase 4, sitting 1; V2; A and B real clients, B non-OP; ordinary
world, switches off, no properties. **Sources:** [v1.0 section 10.6](../../../versions/V1.0.0-COMMUNITY-MVP.md)
and [M08](../../../releases/v1.0.0/MANUAL-TEST.md).

**Preconditions:** Baseline/JEI absent; P2 station owned by A, B initially
neither member nor invitee; UUIDs and membership baseline captured. B remains
non-operator throughout. Use the current candidate's station controls; sources
do not specify an ownership-transfer command spelling.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [P] Attempt B's arrival/control request at A's private station. | Refusal; no unauthorized travel, ownership or resource change. |
| 2 | [P] Invite B from A's station controls. | One invitation for B, not general access. |
| 3 | [P] Decline the invitation on B. | No membership; invitation is resolved. |
| 4 | [P] Invite B again. | Fresh invitation. |
| 5 | [P] Accept on B. | B becomes member of this station only. |
| 6 | [P] Request B's permitted station arrival. | Authorized arrival; no administrator privileges granted. |
| 7 | [P] Attempt an owner-only action from B. | Non-owner denial with unchanged station/resources. |
| 8 | [P] Transfer station ownership from A to B through the supported control. | Exactly one new owner; actual former-owner membership policy is recorded, not invented. |
| 9 | [P] Attempt an owner-only action from A. | Authorization follows current ownership and documented membership, not old menu state. |

**Accepted preparation/action steps (W408):**

(C2) Use A's corrected effective viewports/scales. **Needs package K16**: station invitation/ownership-transfer controls and scene. Owner O10 supplies UUIDs; permission refusals use R546's common evidence rule.

**Evidence to keep:** Both-client screenshots of invitation/decline/accept/transfer,
server ownership/member projections, refusal receipts and logs in sitting 1.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A07 - Online authentication and whitelist

**Header:** Phase 4, entry check; V2; two independently signed-in real accounts;
ordinary world, switches off, no test properties. **Sources:** [v1.0 section 10.7](../../../versions/V1.0.0-COMMUNITY-MVP.md)
and [manual closing note](../../../releases/v1.0.0/MANUAL-TEST.md).

**Preconditions:** Baseline Forge/JEI absent; restricted dedicated endpoint;
record `online-mode=true`, `white-list=true`, `enforce-whitelist=true`,
`enable-rcon=false`. Owner signs into the launcher outside all automation.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Record the effective server properties without credentials. | Online authentication and whitelist settings match preconditions. |
| 2 | [P] Connect whitelisted A. | Normal authentication succeeds with A's intended UUID. |
| 3 | [P] Connect whitelisted B. | A second independent UUID is authenticated. |
| 4 | [P] Disconnect B. | A remains connected. |
| 5 | [H] Remove B from this disposable server's whitelist. | B is not allowed to rejoin. |
| 6 | [P] Attempt B's rejoin. | Whitelist refusal, not an offline identity fallback. |
| 7 | [H] Restore B's whitelist entry. | Original permitted set restored. |
| 8 | [P] Reconnect B. | Both authenticated clients enter in the five-minute phase-entry check. |

**Evidence to keep:** Redacted properties, allowed UUIDs, join/refusal receipts,
UTC times, both client logs and server log in sitting 1. Never retain tokens,
passwords or launcher credential screens. No third account is needed.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A08 - Four-hour reference soak

**Header:** Phase 5; SOAK; owner's exclusive Debian 12 reference node, normal
user, 4 vCPU/8 GiB; two connected real clients/accounts A/B or owner-confirmed
verified equivalent under O7; ordinary world, switches off, no test properties.
**Sources:** [v1.0 sections 10.8/13.5](../../../versions/V1.0.0-COMMUNITY-MVP.md),
[performance report](../../../releases/v1.0.0/PERFORMANCE.md),
[Gate remaining items](../../../status/GATE_STATUS.md) and quality budgets section 4.

**Preconditions:** P2 **reference workload world of docs/17 section 4**; P6
node acceptance, no competing destructive or performance run; baseline/JEI
absent; Java heap at most `-Xmx4G`; loopback server reached over SSH tunnel.
Minimum load: two players/equivalent, 16 oxygen devices/sealed volumes, ten
station records, 100 satellites/tasks, two active rockets, one 2,048-block
assembly/disassembly, one dimension transfer, periodic saves and restarts.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Record the workload's initial identities and item/fluid/fuel totals. | A complete accounting baseline, not just entity counts. |
| 2 | [H] Start the recorded host/tick/RSS/GC/forced-chunk samplers. | Raw timestamped samples; include CPU steal, swap and disk state. |
| 3 | [P] Connect A and B, or the approved equivalent. | Required active workload is present for the run. |
| 4 | [H] Execute the P2 workload schedule for at least four hours. | All minimum load components exercised; saves/restarts recorded, not removed to improve timings. |
| 5 | [H] Capture the final resource projection. | No unexplained gain/loss; legitimate production/consumption reconciles. |
| 6 | [H] Analyze the complete sample interval. | No OOM/watchdog, unexpected forced chunks or sustained monotonic memory growth; A09 budgets apply. |

**Accepted preparation/action steps (W408):**

(C4, R535) Read back shipped defaults of `atmosphere.maxVolumeCells` and `atmosphere.maxInspectionsPerLevelTick` from the generated config without changing them. Use 16 rooms with one recorded footprint at most 4,096 blocks. Retain vanilla autosave, a flushed save every 30 minutes and one orderly restart at two sampled hours; collect four sampled hours, excluding restart downtime, and compare memory across the restart. Footprint, save/restart intervals are procedure parameters, not new budgets. **Needs package K17**: combined world, room layout and schedule; **needs package K15**: live sampler/queue qualification. Owner O7 still supplies the two connections/equivalent decision.

**Evidence to keep:** `phase5-ref/<run>/A08/` workload schedule, real elapsed
interval, node/JVM record, all logs and raw samples, GC/RSS plots, restart events
and exact accounting differences. An interrupted/failed soak is retained and
repeated as a full new four-hour run; partial intervals are not added together.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A09 - Reference tick and memory distribution

**Header:** Phase 5; REF; accepted normal-user reference node and two workload
accounts/equivalent as A08; ordinary world, switches off, no properties.
**Sources:** [v1.0 section 13.1](../../../versions/V1.0.0-COMMUNITY-MVP.md)
and quality budgets section 4.

**Preconditions:** P2 reference workload, baseline/JEI absent, `Xmx4G` or less;
same measured load as A08. Record sampling tool/version and interval before run.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Sample the running reference workload, including save/restart event markers. | Raw per-tick and RSS data, not empty-server numbers. |
| 2 | [H] Compute mean, P95 and P99 MSPT for the stated interval. | Mean <=25 ms, P95 <=50 ms, P99 <=100 ms. |
| 3 | [H] Evaluate RSS and non-startup/save spikes. | RSS does not sustain above 5.5 GiB; single non-startup/save spikes must not persist over 500 ms. |
| 4 | [H] Compare watchdog, OOM and forced-chunk observations. | Zero watchdog/OOM and zero unexpected forced chunks. |

**Accepted preparation/action steps (W408):**

(W403) Use the common observer commands and preserve raw-tick/ticket coverage gaps; window-percentile averages cannot supply this row's whole-run distribution. **Needs package K15** for raw-tick and all-ticket integration and **needs package K17** for the reference world.

**Evidence to keep:** Raw tick/RSS/GC series, interval/event markers,
percentile calculation and all budget comparisons in `phase5-ref/<run>/A09/`.
Share A08 samples by hash/reference, not by relabeling a different workload.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A10 - 2,048-block assembly/disassembly timing

**Header:** Phase 5; REF; accepted reference node, harness and workload accounts;
ordinary copy, switches off, no properties. **Source:** [v1.0 section 13.2](../../../versions/V1.0.0-COMMUNITY-MVP.md).

**Preconditions:** P2 **three rockets of 64, 512 and 2,048 blocks**, selecting
the legal 2,048-block structure; baseline/JEI absent; reference-node budgets.
Snapshot block count and source inventory are recorded; do not substitute the
five-block recovery fixture. No specific operation-duration limit is given here.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Begin per-tick and operation-duration recording. | Measurement includes the main-thread spike, not only total wall time. |
| 2 | [H] Assemble the 2,048-block structure through the normal assembler. | One complete 2,048-block snapshot, no loss or duplication; record duration and max tick. |
| 3 | [H] Disassemble through the supported normal operation. | Exact structure/inventory restored once; record duration and max tick. |
| 4 | [H] Compare both timings against the general reference budgets. | Report all numbers and failures; no invented separate duration threshold. |

**Accepted preparation/action steps (W408):**

**Needs package K04**: exact 2,048-block legal rocket/layout and operation receipts; **needs package K15**: raw tick/operation observation. Keep the original general reference budgets; no separate operation-duration limit exists.

**Evidence to keep:** `phase5-ref/<run>/A10/` fixture count/layout hash,
operation start/end receipts, raw tick samples, spike analysis and conservation.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A11 - Sixteen atmosphere devices and bounded queues

**Header:** Phase 5; REF; accepted reference node with workload accounts;
ordinary copy, switches off, no properties. **Source:** [v1.0 section 13.3](../../../versions/V1.0.0-COMMUNITY-MVP.md).

**Preconditions:** P2 reference workload with 16 powered/supplied atmosphere
devices and several sealed rooms; baseline/JEI absent. Manifest must state room
volumes, footprints, hard scan limits and per-tick budgets. Source gives no
exact number of rooms beyond several; C4 selects 16 with the common footprint.
No additional queue threshold exists.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Activate all 16 fixture devices. | Device count and supplies match the manifest. |
| 2 | [H] Observe the queued scans until they settle within the recorded limits. | No unbounded queue/work or unintended chunk loading; retain actual queue/tick figures. |
| 3 | [H] Open the selected room boundaries. | Invalidated protection and bounded revalidation work. |
| 4 | [H] Restore those boundaries. | Correct supplied-room recovery without queue growth across repeated invalidations. |
| 5 | [H] Compare resource totals and tick samples. | Finite oxygen/energy debit, A09 budgets and no unexplained resource change. |

**Accepted preparation/action steps (W408):**

(C4, R535) Use the same 16 rooms, recorded at-most-4,096-block footprint and unchanged generated `atmosphere.maxVolumeCells`/`atmosphere.maxInspectionsPerLevelTick` defaults as A08. **Needs package K17**: room/boundary coordinates; **needs package K15**: queue and loaded-chunk readback. No additional queue threshold exists.

**Evidence to keep:** `phase5-ref/<run>/A11/` device/room layout, queue and
tick samples, loaded-chunk observations, before/after oxygen/energy accounting.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A12 - Real-GPU rocket scale, frame times and cache lifecycle

**Header:** Phase 2; V1; primary real GPU machine/account A, O9's reduced
second-GPU tour separately identified; ordinary worlds, switches off, no properties.
**Sources:** [v1.0 sections 11.2-11.3/13.4/13.6](../../../versions/V1.0.0-COMMUNITY-MVP.md),
quality budgets section 8 and [visual report](../../../releases/v1.0.0/VISUAL-VALIDATION-REPORT.md).

**Preconditions:** P2 three legal 64/512/2,048-block rockets, single- and
multi-rocket scenes; baseline/JEI absent; O8 frame-time tool and version approved.
Record OS, CPU, GPU, driver, resolution, window mode, GUI scale, JVM and packs.

| Step | Action, repeated separately for each rocket size | Expected observation |
|---|---|---|
| 1 | [H] Capture a Vanilla empty-scene baseline on this machine. | Average FPS, 1% low, memory and first-stall method recorded. |
| 2 | [H] Capture the matching Forge empty-scene baseline. | Same settings and measured baseline, not F3 alone for 1% low. |
| 3 | [H] Display the selected rocket for the first time while recording frame times. | Correct model/textures/orientation, block count and seats; retain first-display stall. |
| 4 | [H] Launch the selected rocket. | Stable passenger/model positions in flight; retain average FPS/1% low and spikes. |
| 5 | [H] Follow its dimension transfer. | Destination geometry/seat state correct; old dimension cache released. |
| 6 | [H] Remove the settled test rocket through the supported operation. | Its cache/resources become releasable; no lingering geometry. |
| 7 | [H] Reload client resources with F3+T. | Buffers/caches rebuild correctly without retained old entries or persistent growth. |
| 8 | [H] Display the P2 multi-rocket scene. | Same correctness and frame-time/memory measurements with multiple rockets. |
| 9 | [P] Review recordings and cache observations against the baselines. | No missing textures, obvious offsets, unstable passengers or continuous OpenGL errors; record unacceptable stalls without inventing an absolute FPS limit. |

**Accepted preparation/action steps (W408):**

(C5, R535; W399/W403) Follow A's paired Vanilla/Forge baselines, twice-run spread and frame-reader definitions; 50 ms is reporting only, with no FPS/stall limit. Keep docs/17 section 8's correctness list unchanged. Use owned-client heap/histogram proxies outside timed windows; direct cache-release/GPU-allocation proof still **needs package K15**, otherwise NOT_COVERED. **Needs package K04** for all three rockets/multiple-rocket scene; owner O8 confirms capture tool/version (PresentMon recommended), O9 the second GPU/subset.

**Evidence to keep:** `phase2-v1/<machine>/<round>/` per-size media, raw
`frametime/` samples, baseline/settings records, client memory/cache observations
and logs. An F3 screenshot alone is not cache-release or 1% low evidence.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A13 - Core screens at supported scales

**Header:** Phase 2; V1; real GPU PC/account A; ordinary world, switches off,
no properties. **Sources:** [v1.0 section 11.4](../../../versions/V1.0.0-COMMUNITY-MVP.md)
and remote/visual rules section 13.

**Preconditions:** P2 core-flow scene from A02; baseline/JEI absent. Run the
following for each of electrolyzer, oxygen/life-support, rocket, station and
satellite/research interfaces in English and Chinese, using C2/R535's effective
1280-by-720 scales 2/3 and offered native-fullscreen scale 4.

| Step | Action, for each interface/language/scale/window cell | Expected observation |
|---|---|---|
| 1 | [H] Apply the recorded language, GUI scale and window mode in Options. | Effective settings and actual resolution captured. |
| 2 | [H] Open the selected production interface. | All critical labels, numbers, tooltips and controls are visible. |
| 3 | [H] Activate its recorded valid control. | Control is reachable; corresponding server-authoritative state changes once. |
| 4 | [H] Trigger its recorded invalid-state feedback. | Reason is readable, not clipped or hidden by other controls. |
| 5 | [P] Review native screenshots. | No inaccessible critical control or critical overlap in any tested cell. |

**Accepted preparation/action steps (W408):**

(C2, R535) Execute 1280 by 720 at effective scales 2/3 and native fullscreen at offered scale 4, both languages; record actual scale and unavailable combinations, not requested values. No supported minimum exists. **Needs package K16**: core scene/control manifest and regenerated pixel positions.

**Evidence to keep:** Per-cell screenshots using the row-step-client-language-scale
naming, actual window dimensions in the step table, server receipts and client log.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A14 - Earth, Moon and Space presentation

**Header:** Phase 2; V1; real GPU PC/account A; ordinary world, switches off,
no properties. **Source:** [v1.0 section 11.5](../../../versions/V1.0.0-COMMUNITY-MVP.md).

**Preconditions:** A02 core route and authorized station; baseline/JEI absent;
normal audio enabled, server body/environment/time recorded at each viewpoint.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Capture Earth sky, particles and rocket/machine models. | Presentation corresponds to Earth and server machine/flight state. |
| 2 | [P] Listen to the Earth scene's relevant sounds. | Sound source/state agrees with the server; retain audible recording. |
| 3 | [H] Travel to the Moon. | Moon sky/environment/models replace Earth correctly. |
| 4 | [P] Review Moon particles and sound. | Correct state, no lingering Earth effect. |
| 5 | [H] Travel to the authorized station in Space. | Space/station presentation agrees with the current orbit context. |
| 6 | [P] Review Space models, particles and sound. | No stale Moon/ground scene; no missing assets or persistent errors. |

**Evidence to keep:** Viewpoint/body/time receipts, native screenshots,
audio/video and client/server logs. E06 supplies the additional full sky matrix.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A15 - Two observers of one rocket and its passengers

**Header:** Phase 4, sitting 1; V2; authenticated real accounts A/B and GPU
clients; ordinary world, switches off, no properties. **Sources:** [v1.0 section 11.6](../../../versions/V1.0.0-COMMUNITY-MVP.md)
and [M12](../../../releases/v1.0.0/MANUAL-TEST.md).

**Preconditions:** P2 legal two-seat rocket; baseline/JEI absent; synchronized
UTC clocks; views and server entity/seat projection tied to the same instants.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Capture both clients viewing the same settled rocket. | Same logical/entity identity, model and passenger count. |
| 2 | [P] Board the permitted players. | Each view agrees with actual server seat assignments. |
| 3 | [P] Launch from the owner console. | Both views show the same active plan and phase. |
| 4 | [H] Capture both views after dimension transfer. | One rocket, consistent passengers, stable positions and correct destination. |
| 5 | [P] Reconnect B after landing. | Same current entity/visual state, no extra rocket or stale seat. |

**Evidence to keep:** Paired native screenshots/video and simultaneous server
receipts in sitting 1; both client logs. Reuse A05 captures only with exact
step/time references and their hashes.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A16 - Forge and JEI visual lanes

**Header:** Phase 2; V1; real GPU PC/account A; ordinary copies, switches off,
no properties. **Sources:** [v1.0 section 11.7](../../../versions/V1.0.0-COMMUNITY-MVP.md),
remote/visual rules section 13 and master test plan section 12.

**Preconditions:** P3 profiles for **47.4.10/JEI absent**, **47.4.10/JEI
15.56.0.205**, **47.4.23/JEI absent**, **47.4.23/JEI 15.56.0.205**, with
matching server Forge and the same C5 hash. Preserve separate world copies.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Run A02, A12-A14 on baseline Forge with JEI absent. | Full baseline core visual flow has its own media/results. |
| 2 | [H] Run the same baseline flow with JEI 15.56.0.205 present. | JEI presence does not break core screens, flight or skies. |
| 3 | [H] Run core machine/oxygen/rocket/station/satellite smoke on compatibility Forge without JEI. | Key smoke usable, no class-loading/resource error. |
| 4 | [H] Run that compatibility smoke with JEI present. | Key smoke usable with matching JEI. |
| 5 | [P] Review each cell's screenshots and logs. | A failure is assigned to its lane; untested Forge versions do not redefine the baseline. |

**Accepted preparation/action steps (W408):**

(W400) Use exactly the four named cells/manifests in A's common procedure. **Needs package K16**: first live start/window/input qualification and per-cell core smoke; preparation alone is no join/visual result.

**Evidence to keep:** One profile/install manifest and media/log set per
Forge/JEI cell; actual test subset and linked A-row step results. I04 remains
the full four-cell v1.8 recipe/usage matrix; this row does not replace it.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A17 - Core saved state and client re-entry

**Header:** Phase 3; DS; development PC, one real client/account A; ordinary
copy, switches off, no properties. **Sources:** [M10](../../../releases/v1.0.0/MANUAL-TEST.md)
and remote/visual rules section 13.

**Preconditions:** A02 populated new world, baseline/JEI absent; machine,
room, fueled/landed rocket, station, satellite and research state inventoried.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Capture the complete pre-restart identity/resource projection. | Inventories, fluid, fuel, oxygen, progress, missions, rights and seats included. |
| 2 | [H] Save/stop the server. | Clean exit, no live-copy snapshot claim. |
| 3 | [H] Restart the same world. | Correct candidate and world identities. |
| 4 | [H] Reconnect A. | Current entity/seat/room state visible and usable. |
| 5 | [H] Compare saved and live state. | Stable identities, no replayed rewards or unexplained resource difference; elapsed processing is reconciled. |
| 6 | [H] Repeat steps 2 to 5 for the second restart. | Same conservation and continued-use outcome. |

**Accepted preparation/action steps (W408):**

(C2) Use the corrected effective display cells when recording re-entry. **Needs package K16** for populated core scene, projections and controls.

**Evidence to keep:** `phase3-ds/A17/` all logs/exits, pre/post raw state and
field differences, re-entry screenshots and resource accounting.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## A18 - First-connection timeout review

**Header:** Phase 3 DS trials and phase 4 V2 trials; development PC, one then
two real clients/accounts A/B; ordinary worlds, switches off, no properties.
**Sources:** [multiplayer report](../../../releases/v1.0.0/MULTIPLAYER-REPORT.md),
[retained timeout obligation](../../../status/GATE_STATUS.md), and section 2 of
`D:/GitHub/ARCE-Task-Evidence/v18-client-20261010/REPORT.md` (external historical report).

**Preconditions:** P3 baseline and compatibility profiles, initially JEI absent;
record JEI state for each trial. C6 supplies ten fresh-copy/process/game-directory
trials per no-JEI lane plus every phase-4 A/B first join, as procedure parameters. V2 uses normal online authentication, not offline UUID substitutes.

| Step | Action, separately for each Forge lane and connection trial | Expected observation |
|---|---|---|
| 1 | [H] Start a fresh recorded server/client trial. | Pin Forge, C5, account and first-join conditions. |
| 2 | [H] Attempt the first connection on A. | Record success/timeout and latency without automatic retry hiding the first result. |
| 3 | [H] Retry in the same process only if the first attempt timed out. | Record the retry separately and retain both complete logs. |
| 4 | [H] Attempt B's first join in the phase-4 trial. | Independent first-attempt result; normal authentication. |
| 5 | [H] Reconnect after the recorded server restart. | Reconnect outcome remains distinct from first-join outcome. |
| 6 | [H] Summarize timeout counts over all attempted first joins per lane. | Numerator, denominator, timings and circumstances, not a claim of zero incidence from a retry. |

Historical context only: the 47.4.22 harness run had one initial timeout in
r01, a successful same-process retry, and four successful first joins in r02/r03.
These are not C5/47.4.10/47.4.23 results or an approved tolerance. A timeout
remains a finding until Root dispositions it; sources give no acceptable rate.

**Accepted preparation/action steps (W408):**

(C6; R542 N1) Run ten first joins on each Forge lane with JEI absent, each on a fresh server copy, fresh client process and fresh game directory, plus every A/B first join in phase 4. Ten is a procedure parameter. W399 launch mode and W400's no-JEI cells supply the reset mechanism; no automatic retries. Report timeouts/trials separately per lane and retain each failed attempt even if a retry succeeds. No tolerated rate exists: each timeout remains a finding until Root dispositions it (KI-V180-CONNECT-01). Live harness qualification still **needs package K16**.

**Evidence to keep:** `phase3-ds/A18/` and the paired V2 sitting's trial table,
all first/failed/retry/restart logs, screenshots, UTC durations and per-lane counts.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## Open questions for Root

- **Answered:** C1 identity, W400 cell paths/hashes, W399 session/reset/frame commands, C2/R535 viewports, C4 room/schedule parameters, C5/C10/R535 sampling/review and C6 first-join trials/reset. W403 observers/seals are delivered but not fully live-qualified.
- **A01-A18:** sources/API JARs **needs package K01**; current core/world/control/batch and live harness qualification **needs package K16**; immutable Beta/historical/forced-stop baselines **needs package K02**.
- **A04/A05:** genuine adjacent-chunk/current-seat/expiry controls each **needs package K02**, otherwise the separate observation is NOT_COVERED.
- **A08-A11:** combined world/layout **needs package K17**; raw-tick/queue/all-ticket/live observer integration **needs package K15**. O7/O11 supply connections/equivalent decision and node acceptance.
- **A12:** legal three-size/multi-rocket fixtures **needs package K04**; direct cache/allocation proof **needs package K15**, not a histogram inference. O8 confirms capture/version (PresentMon recommended); O9 the second GPU/subset.
- **A18:** no trial-count decision remains: ten fresh no-JEI trials per lane plus every V2 A/B first join; live qualification **needs package K16**, O10 supplies UUIDs. No tolerated timeout rate exists.
