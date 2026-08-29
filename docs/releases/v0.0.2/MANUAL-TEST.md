# MANUAL-TEST — v0.0.2 Forge Bootstrap

```yaml
status: PARTIAL_AUTOMATED
test_date: 2026-08-29
tester: packaged-server automation only
build: 1.20.1-0.0.2-dev
tested_implementation_commit: 0fa080fdff3ab025c6b764b02d2d07fa9221c5ae
artifact_sha256: 58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944
artifact_identity_scope: PRE_G0_HEADLESS_BASELINE
packaged_client_tested: false
```

## MANUAL-V002-001 — Packaged-client metadata and world start

**Preconditions**

- Complete the human G0 provenance review first, commit its notice/review
  changes, rebuild, refresh artifact evidence, and obtain successful blocking
  CI for the exact source commit. G0 changes packaged JAR bytes, so evidence
  captured against a pre-approval artifact is invalid.
- Before handoff, the evidence owner must update and commit the identity block
  above with the post-G0 artifact-producing commit and rebuilt artifact SHA-256.
  The tester must pull the later clean documentation checkpoint, verify the
  block no longer says `PRE_G0_HEADLESS_BASELINE`, and stop if it was not
  refreshed; do not edit these fields on the test machine. The clean checkout
  commit bound by the evidence collector may be a later documentation-only
  checkpoint than the artifact-producing implementation commit.
- Use an isolated official Forge 1.20.1-47.4.10 client on Java 17.
- Install only that newly selected post-G0 distributable JAR.
- Do not use ForgeGradle `runClient` as packaged release evidence.

**Steps**

1. Render the root `README.md` from the exact tested checkout and capture its
   title, unofficial statements, status, target versions, and license.
2. Start the packaged client and open the Mods list.
3. Inspect name, version, description, credits, license, and logo.
4. Create and enter a disposable single-player world.
5. Review the full client log, then archive only privacy-reviewed evidence.

**Expected**

- The rendered README and metadata match the approved project identity, retain
  the unofficial/Minecraft statements, and state that the build has no playable
  rocket systems.
- The client reaches the world without a project-source ERROR.

**Actual**

`NOT_EXECUTED`. No packaged-client screenshot or log is claimed. Earlier
ForgeGradle client diagnostics do not load the physical release JAR and are
excluded from acceptance.

## MANUAL-V002-002 — Packaged dedicated server and matching client

**Preconditions**

- Use the same isolated Forge 1.20.1-47.4.10 packaged client and exact JAR
  selected for MANUAL-V002-001.
- Use the loopback-only, harness-owned disposable server session; the source,
  server, and client JAR copies must have one shared SHA-256.
- Do not edit the harness world, lifecycle logs, or generated summary.

**Steps**

1. Install Forge 47.4.10 in an isolated loopback-only server directory and copy
   the selected post-G0 JAR.
2. Verify source, server, and client JAR SHA-256 equality.
3. Start the server and connect with the isolated matching client.
4. Disconnect, save, stop, restart the same world, reconnect, and stop again.

**Expected**

- The server identifies Minecraft 1.20.1, protocol 763, and exact mod version
  `1.20.1-0.0.2-dev` without loading client classes.
- The matching client connects twice and both shutdowns finish without a
  project-source ERROR.

**Actual**

`PARTIAL`. The headless packaged-server portion passed with the current tested
artifact before human G0 approval. Source
and server copies share SHA-256
`58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944`.
Both headless cycles verified status identity, saved, exited 0, and reused the
same `world/level.dat`; full-log review found zero ERROR, zero project WARN, and
zero client-class linkage findings in both cycles. No packaged client joined,
so three-way hash equality,
join/disconnect, and restart/reconnect remain untested.

The existing committed dedicated-server evidence is a headless baseline, not a
manual-player readiness claim. A future matching-client run must use
`run_dedicated_server_smoke.py --manual-player-cycles` and retain its schema-3
summary plus both full logs. The summary binds the exact server artifact,
loopback port, cycle IDs, timestamps, exit codes, raw-log hashes, player
join/leave observations, the same redacted player identity across both cycles,
and one stable world identity with pre/post `level.dat` snapshots plus the
canonical startup-properties identity. Project-source ERROR or WARN and
client-class linkage findings block harness success.

## MANUAL-V002-003 — Declared mismatch policy

**Preconditions**

- Complete and retain the MANUAL-V002-002 harness session before the direct
  third server start.
- Use a separate isolated Forge 1.20.1-47.4.10 client with no project JAR and
  no other mods; do not alter the matching-client profile.

**Steps**

1. Create a second isolated Forge 47.4.10 client without the project JAR.
2. After the two-cycle harness exits, use the exact third-start command in
   `docs/work/v0.0.2-test-machine-handoff.md` section 6; do not try to reuse the
   harness session through its CLI.
3. Observe the retained loopback server's compatibility indicator and message.
4. Attempt one connection and record the actual result without assuming it,
   then save and stop the server cleanly and retain the third-start full log
   plus its exit-code/log-hash receipt.

**Expected**

- The actual compatibility indicator, complete message, and attempted-
  connection result are recorded without preselecting acceptance or rejection.
- The retained server saves and stops cleanly after the observation; any
  ambiguous or contradictory behavior remains an open finding.
- The evidence contains either an ordered logger-anchored server connection
  marker or the exact client `ConnectScreen` marker for this session's
  `127.0.0.1:<port>`. A UI result with neither marker remains `BLOCKED` because
  the helper cannot mechanically prove that the connection was attempted.

**Actual**

`NOT_EXECUTED`. The declared `displayTest="MATCH_VERSION"` behavior still needs
packaged observation.

## Scoped G4 applicability decisions

```yaml
project_state_synchronization:
  proposed_status: NOT_APPLICABLE
  rationale: >-
    v0.0.2 defines no project packet or project-owned mutable player/world
    state; a reviewer must decide whether a separate synchronization comparison
    has an observable subject in this bootstrap milestone.
  decision: PENDING
  reviewed_by: ""
  reviewed_at: ""
  notes: ""
two_player_consistency:
  proposed_status: NOT_APPLICABLE
  rationale: >-
    v0.0.2 has no playable content, project packets, shared player state,
    permissions, inventories, or interactions to compare between players.
  decision: PENDING
  reviewed_by: ""
  reviewed_at: ""
  notes: ""
chunk_unload_behavior:
  proposed_status: NOT_APPLICABLE
  rationale: >-
    v0.0.2 defines no project block, entity, block entity, SavedData, chunk
    ticket, or chunk-bound operation; a reviewer must decide whether chunk-
    unload behavior has an observable project subject in this milestone.
  decision: PENDING
  reviewed_by: ""
  reviewed_at: ""
  notes: ""
configuration_mismatch:
  proposed_status: NOT_APPLICABLE
  rationale: >-
    v0.0.2 exposes only the bootstrap lifecycle logging option and no gameplay,
    packet, persistence, or authority behavior controlled by project
    configuration; a reviewer must decide whether a distinct mismatch case is
    applicable.
  decision: PENDING
  reviewed_by: ""
  reviewed_at: ""
  notes: ""
optional_client_dependency_absence:
  proposed_status: NOT_APPLICABLE
  rationale: >-
    v0.0.2 declares no optional runtime or client-only dependency; the clean
    packaged profile contains only Forge and the project JAR.
  decision: PENDING
  reviewed_by: ""
  reviewed_at: ""
  notes: ""
```

These proposals do not approve G4 and do not replace the matching-client or
mismatch-policy cases. Their required owner/reviewer, risk, expiry, and recovery
record is [`ADR-005`](../../decisions/ADR-005-V0.0.2-G4-APPLICABILITY.md); the
ADR remains `PROPOSED` until all five decisions receive an explicit human
review.

## Evidence checklist

- [ ] Three-way source/server/client JAR hash equality.
- [ ] Current rendered README screenshot from the tested checkout.
- [ ] Full-window packaged-client Mods page screenshot.
- [ ] Packaged-client single-player in-world screenshot and selected log.
- [x] Current tested-JAR packaged-server first-start and restart excerpts.
- [ ] Matching-client first join, disconnect, restart, and reconnect evidence.
- [ ] Missing-project-mod indicator, message, and connection result.
- [ ] Third-start server ready/save/stop log and exit-code/log-hash receipt.
- [ ] Human decisions for all five scoped G4 applicability proposals.

See [`evidence/dedicated-server/`](evidence/dedicated-server/) for completed
server evidence and
[`../../work/v0.0.2-test-machine-handoff.md`](../../work/v0.0.2-test-machine-handoff.md)
for the isolated packaged-client procedure. The version-scoped evidence helper
is [`../../../scripts/collect_v002_manual_evidence.py`](../../../scripts/collect_v002_manual_evidence.py).
No `evidence/client/` bundle exists yet, and helper validation never marks a
Gate or the version `PASSED`.

The helper scans complete raw client/server logs and archived excerpts rather
than trusting entered finding counts, binds the matching-player server excerpts
to the schema-3 harness summary by cycle ID, filename, SHA-256, and the same
redacted player identity, binds the third mismatch-server startup to the same
artifact, canonical harness-owned `server.properties.v002-startup` identity,
loopback port, exact `Preparing level "world"` marker, and world identity. It
accepts an exact
client-loopback connection marker when Forge rejects before the server logs the
attempt, and enforces all lifecycle marker order. It accepts honest
`FAIL`/`BLOCKED` archives by default. The
`--require-acceptance-ready` flag additionally requires every fixed observation
to be `PASS`, all five scoped decisions to be `ACCEPT_NOT_APPLICABLE`, the bound
harness evidence, and all other mechanical checks. Its success means only
`READY_FOR_HUMAN_GATE_REVIEW`.

`REQUIRE_ADDITIONAL_TEST` is intentionally blocking rather than a second way
to reach strict readiness. If a reviewer selects it, amend ADR-005 and the
collector schema with that reviewer-defined protocol and evidence before
running the case; do not relabel an applicable test as `NOT_APPLICABLE`.

PNG evidence is restricted to full-window RGB/RGBA files from 640x360 through
4096x4096, no more than 16 MiB per file, 40 MiB in aggregate, and at most 128
PNG chunks. Log excerpts are limited to 200 lines and 64 KiB each. Unknown,
textual, and other nonessential ancillary PNG chunks are rejected; human pixel
review remains required because structural validation cannot identify visible
private information.
