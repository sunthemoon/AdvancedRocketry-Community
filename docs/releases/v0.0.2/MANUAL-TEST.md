# MANUAL-TEST — v0.0.2 Forge Bootstrap

```yaml
status: PASSED
test_date: 2026-08-30
tester: local packaged-client tester plus repository owner sunthemoon
build: 1.20.1-0.0.2-dev
tested_implementation_commit: d6c8464b0e75fe10d64fcb579ab08345f7d4cd3b
final_g0_record_commit: db9ce96113712dd93e8db05736b3a9ed764e41a8
artifact_sha256: cd5ae579bae1bc21c1f67df2c3e00f196e0ee4a9ead01653c926b88ca37f32ad
artifact_identity_scope: POST_PROVENANCE_APPROVAL_G0_APPROVED
packaged_client_tested: true
```

## MANUAL-V002-001 — Packaged-client metadata and world start

**Preconditions**

- G0 is complete. Bootstrap provenance is `THIRD_PARTY_APPROVED`; the rebuilt
  implementation and JAR are bound above; the exact source/resource report and
  rendered README review are `APPROVED` in record commit `3d82740`.
- Use only the selected `cd5ae579...` artifact. A changed JAR, reviewed source,
  packaged manifest, or README invalidates the affected binding and requires a
  fresh review rather than reuse of these results.
- Pull a clean descendant documentation checkpoint containing the identity
  block above. The checkout commit bound by the evidence collector may be later
  than the artifact-producing implementation commit, but it must validate the
  exact approved G0 record.
- Use an isolated official Forge 1.20.1-47.4.10 client on Java 17.
- Install only that newly selected post-provenance-approval distributable JAR.
- Use the schema-5 collector template and the fixed matching profile directory
  under ignored `build/`; its `mods/` directory must contain exactly that JAR.
- Create the game directory below the repository, resolve it to an absolute
  path, and paste that exact absolute path into the launcher. A relative
  `build/...` launcher value is not bound to the repository checkout.
- Do not use ForgeGradle `runClient` as packaged release evidence.

**Steps**

1. Capture the matching profile's canonical `before` inventory before launch.
2. Render the root `README.md` from the exact tested checkout and capture its
   title, unofficial statements, status, target versions, and license.
3. Start the packaged client and open the Mods list.
4. Inspect name, version, description, credits, license, and logo.
5. Create and enter a disposable single-player world.
6. Review the full client log from that profile, then archive only privacy-
   reviewed evidence.
7. Keep the same client process open for MANUAL-V002-002; do not relaunch and
   rotate its `logs/latest.log` between the world and player-cycle observations.

**Expected**

- The rendered README and metadata match the approved project identity, retain
  the unofficial/Minecraft statements, and state that the build has no playable
  rocket systems.
- The complete client raw log has zero broad ERROR/FATAL and zero project-source
  WARN/FATAL findings; every non-project WARN has an explicit reviewed disposition.

**Actual**

`PASS`. The isolated packaged client loaded the exact `cd5ae579...` JAR, showed
the approved name, version, description, credits, license, and logo, and entered
the disposable `V002 Disposable` world. The matching-client raw log contains
zero broad ERROR/FATAL, zero project WARN/ERROR/FATAL, and zero client-class
linkage findings. The full-window Mods page, world screenshot, reviewed README
render, selected log, and profile inventories are in [`evidence/client/`](evidence/client/).

## MANUAL-V002-002 — Packaged dedicated server and matching client

**Preconditions**

- Use the same isolated Forge 1.20.1-47.4.10 packaged client and exact JAR
  selected for MANUAL-V002-001.
- Use the loopback-only, harness-owned disposable server session; the source,
  server, and client JAR copies must have one shared SHA-256.
- Bind `artifacts.client` to the exact JAR inside the matching profile; a
  byte-identical copy elsewhere is not a substitute.
- Do not edit the harness world, lifecycle logs, or generated summary.

**Steps**

1. Install Forge 47.4.10 in an isolated loopback-only server directory and copy
   the selected post-provenance-approval JAR.
2. Verify source, server, and client JAR SHA-256 equality.
3. Start the server and connect with the isolated matching client.
4. Disconnect, save, stop, restart the same world, reconnect, and stop again.
5. After the harness completes, fully exit the matching client and wait for its
   Java process and raw log writes to stop.
6. Capture the matching profile's unchanged canonical `after` inventory.

**Expected**

- The server identifies Minecraft 1.20.1, protocol 763, and exact mod version
  `1.20.1-0.0.2-dev` without loading client classes.
- The matching client connects twice, both shutdowns finish, every bound raw
  log has zero broad ERROR/FATAL and zero project-source WARN/FATAL findings, and every
  non-project WARN has an explicit reviewed disposition.

**Actual**

`PASS`. Source, server, and matching-client copies all hash to `cd5ae579...`.
Harness `v002-0e0386b4d3c113c3d3e76f62` records the same redacted player joining
and leaving both cycles, a clean save/stop, the same world identity, and a
successful reconnect after restart. Complete client and server log audits found
zero project WARN/ERROR/FATAL and zero client-class linkage findings.

The schema-4 summary binds the exact server artifact,
loopback port, cycle IDs, timestamps, exit codes, raw-log hashes, player
join/leave observations, the same redacted player identity across both cycles,
and one stable world identity with pre/post `level.dat` snapshots plus the
canonical startup-properties identity. Project-source ERROR, WARN, or FATAL and
client-class linkage findings block harness success.

## MANUAL-V002-003 — Declared mismatch policy

**Preconditions**

- Complete and retain the MANUAL-V002-002 harness session before the direct
  third server start.
- Use a separate isolated Forge 1.20.1-47.4.10 client with no project JAR and
  no other mods; its game directory must be distinct and non-nested relative to
  the matching profile. Do not alter the matching-client profile.

**Steps**

1. Create a second isolated Forge 47.4.10 client with an empty `mods/`
   directory and capture its canonical `before` inventory only after the
   matching `after` snapshot.
2. Only after its `before` snapshot succeeds, launch that Forge-only profile and
   leave it at the title screen.
3. After the two-cycle harness exits, use the exact third-start command in
   `docs/work/v0.0.2-test-machine-handoff.md` section 6; do not try to reuse the
   harness session through its CLI.
4. Add the same `127.0.0.1:<recorded-port>` endpoint to this distinct profile's
   empty multiplayer list without copying the matching profile's `servers.dat`.
5. Observe the retained loopback server's compatibility indicator and message.
6. Attempt one connection and record the actual result without assuming it,
   then save and stop the server cleanly and retain the helper-owned third-start
   full log plus its schema-2 run/timestamp/Java/exit/log/properties/server-mods
   receipt.
7. Fully exit the client, wait for its process and log writes to stop, capture
   the missing-project-mod profile's unchanged canonical `after` inventory, and
   retain its own raw client log under that profile's `logs/`.

**Expected**

- The actual compatibility indicator, complete message, and attempted-
  connection result are recorded without preselecting acceptance or rejection.
- The retained server saves and stops cleanly after the observation; any
  ambiguous or contradictory behavior remains an open finding.
- The evidence contains either an ordered logger-anchored server connection
  marker or the exact client `ConnectScreen` marker for this session's
  `127.0.0.1:<port>`. The client marker may use the normal bracketed Forge log
  format or a complete three-line Log4j XmlLayout event that preserves the
  exact logger and message. A UI result with neither marker remains `BLOCKED`
  because the helper cannot mechanically prove that the connection was
  attempted.

**Actual**

`PASS_WITH_OBSERVED_COMPATIBILITY_LIMITATION`. The isolated Forge-only profile
had an empty `mods/` inventory and showed Forge's red incompatible-server marker
with the message that the server has additional mods. The single logger-anchored
connection attempt was nevertheless accepted into the world; client and server
records preserve the join and leave. The server then saved and stopped cleanly.
This is the observed Forge behavior, not a claimed rejection contract.

## Scoped G4 applicability decisions

```yaml
project_state_synchronization:
  proposed_status: NOT_APPLICABLE
  rationale: >-
    v0.0.2 defines no project packet or project-owned mutable player/world
    state; a reviewer must decide whether a separate synchronization comparison
    has an observable subject in this bootstrap milestone.
  decision: ACCEPT_NOT_APPLICABLE
  reviewed_by: "sunthemoon"
  reviewed_at: "2026-08-30"
  notes: "No project packet or mutable player/world state exists in v0.0.2."
two_player_consistency:
  proposed_status: NOT_APPLICABLE
  rationale: >-
    v0.0.2 has no playable content, project packets, shared player state,
    permissions, inventories, or interactions to compare between players.
  decision: ACCEPT_NOT_APPLICABLE
  reviewed_by: "sunthemoon"
  reviewed_at: "2026-08-30"
  notes: "No project-owned multiplayer state exists in v0.0.2."
chunk_unload_behavior:
  proposed_status: NOT_APPLICABLE
  rationale: >-
    v0.0.2 defines no project block, entity, block entity, SavedData, chunk
    ticket, or chunk-bound operation; a reviewer must decide whether chunk-
    unload behavior has an observable project subject in this milestone.
  decision: ACCEPT_NOT_APPLICABLE
  reviewed_by: "sunthemoon"
  reviewed_at: "2026-08-30"
  notes: "No project chunk-bound object or operation exists in v0.0.2."
configuration_mismatch:
  proposed_status: NOT_APPLICABLE
  rationale: >-
    v0.0.2 exposes only the bootstrap lifecycle logging option and no gameplay,
    packet, persistence, or authority behavior controlled by project
    configuration; a reviewer must decide whether a distinct mismatch case is
    applicable.
  decision: ACCEPT_NOT_APPLICABLE
  reviewed_by: "sunthemoon"
  reviewed_at: "2026-08-30"
  notes: "No compatibility-bearing project configuration exists in v0.0.2."
optional_client_dependency_absence:
  proposed_status: NOT_APPLICABLE
  rationale: >-
    v0.0.2 declares no optional runtime or client-only dependency; the clean
    packaged profile contains only Forge and the project JAR.
  decision: ACCEPT_NOT_APPLICABLE
  reviewed_by: "sunthemoon"
  reviewed_at: "2026-08-30"
  notes: "v0.0.2 declares no optional client dependency."
```

These accepted classifications do not replace the completed matching-client or
mismatch-policy cases. Their owner, version scope, expiry, risk, mitigation, and
recovery record is [`ADR-005`](../../decisions/ADR-005-V0.0.2-G4-APPLICABILITY.md).

## Evidence checklist

- [x] Three-way source/server/client JAR hash equality.
- [x] Distinct matching and missing-project-mod profile paths, four canonical
  inventory snapshots, exact matching JAR, and empty missing-mod inventory.
- [x] Current rendered README screenshot from the tested checkout.
- [x] Final G0 full distributable source/resource inventory-history decision is
      recorded for the selected implementation commit and tree, with the
      verified report archived at its commit-named tracked path.
- [x] Full-window packaged-client Mods page screenshot.
- [x] Packaged-client single-player in-world screenshot and selected log.
- [x] Current tested-JAR packaged-server first-start and restart excerpts.
- [x] Matching-client first join, disconnect, restart, and reconnect evidence.
- [x] Missing-project-mod indicator, message, and connection result.
- [x] Helper-owned third-start server ready/save/stop log and schema-2 run receipt.
- [x] Human decisions for all five scoped G4 applicability proposals.

See [`evidence/dedicated-server/`](evidence/dedicated-server/) for completed
server evidence and
[`../../work/v0.0.2-test-machine-handoff.md`](../../work/v0.0.2-test-machine-handoff.md)
for the isolated packaged-client procedure. The version-scoped evidence helper
is [`../../../scripts/collect_v002_manual_evidence.py`](../../../scripts/collect_v002_manual_evidence.py).
The canonical [`evidence/client/`](evidence/client/) bundle is strictly valid
and `READY_FOR_HUMAN_GATE_REVIEW`; repository owner `sunthemoon` separately
approved G8/G9 on 2026-08-30.

The schema-5 helper scans complete raw client/server logs and archived excerpts
rather than trusting entered finding counts. It binds two distinct, non-nested
client game directories, four ordered canonical `mods/` inventory snapshots,
the exact matching-profile JAR, an empty missing-project-mod profile, and each
client raw log to its corresponding profile. It rejects a cross-profile hard
link or other shared physical raw-log file. It binds the matching-player server excerpts
to the schema-4 harness summary by cycle ID, filename, SHA-256, and the same
redacted player identity, binds the third mismatch-server startup to the same
artifact, canonical harness-owned `server.properties.v002-startup` identity,
semantically verified active critical `server.properties`, loopback port, exact
`Preparing level "world"` marker, world identity, and an exact singleton server
`mods/` inventory containing only the project JAR. The schema-2 third-cycle
receipt binds a create-once run ID, ordered start/end timestamps, monotonic
duration, Java 17, process exit code, prior/fresh log hashes, the harness summary
and both cycle hashes. The missing-mod before/after snapshots must bracket that
receipt's start/end timestamps. Hash or physical-file reuse of either harness
log is rejected. Strict raw-log decoding accepts deterministic strict UTF-8 or
strict GB18030/GBK bytes; undecodable logs fail. It
accepts an exact
client-loopback connection marker when Forge rejects before the server logs the
attempt, and enforces all lifecycle marker order. It accepts honest
`FAIL`/`BLOCKED` archives by default. Such incomplete archives are build-local
diagnostics only: write them under ignored `build/`, never at or into the
canonical committed evidence path. Do not publish or commit any screenshot
until its visible pixels have completed human privacy review. The
`--require-acceptance-ready` flag additionally requires every fixed observation
to be `PASS`, all five scoped decisions to be `ACCEPT_NOT_APPLICABLE`, the bound
harness evidence, zero broad FATAL and client-class linkage findings across all
raw logs, and all other mechanical checks. Its success means only
`READY_FOR_HUMAN_GATE_REVIEW`.

The canonical client-evidence destination is implicitly acceptance-ready even
if the strict flag is omitted. Strict collection and strict or canonical
validation require the record's exact source commit—not merely the current
worktree—to contain digest-bound `THIRD_PARTY_APPROVED` bootstrap provenance
and an exact-record `APPROVED` final-G0 source/resource review. The README visual
review may remain pending until this session captures its canonical screenshot.
Non-strict ignored `build/` bundles remain permitted to preserve honest failure
diagnostics, but are not acceptance artifacts and must later pass strict
validation before use in human Gate review.

Profile `captured_at` values are local self-attestation, not trusted timestamps.
The before/after binding does not replace human confirmation that the intended
profile was selected and no temporary mod change occurred between captures.

`REQUIRE_ADDITIONAL_TEST` is intentionally blocking rather than a second way
to reach strict readiness. If a reviewer selects it, amend ADR-005 and the
collector schema with that reviewer-defined protocol and evidence before
running the case; do not relabel an applicable test as `NOT_APPLICABLE`.

PNG evidence is restricted to full-window RGB/RGBA files at least 640x360 with
no more than 16,777,216 decoded pixels, 16 MiB per file, 40 MiB in aggregate,
and 128 PNG chunks per file. Log excerpts are limited to 200 lines and 64 KiB
each. One fixed nine-byte `pHYs` chunk with axes from `1` through
`2,147,483,647` and unit `0` or `1` is permitted alongside recognized
color/transparency chunks. Unknown, textual, and other nonessential ancillary
PNG chunks are rejected; human pixel review
remains required because structural validation cannot identify visible private
information.

All manual-session outputs are create-once. Preserve a failed attempt by moving
the verified `build/v0.0.2-manual` directory to a fresh timestamped sibling
below the same ignored `build/` root only after every client/server process has
stopped, then begin again from a new template and new absolute launcher
directories. The exact safety-checked PowerShell procedure is in the handoff
document; never merge inputs from two attempts or delete the failed evidence.
