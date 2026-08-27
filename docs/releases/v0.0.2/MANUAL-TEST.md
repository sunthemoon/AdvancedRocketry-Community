# MANUAL-TEST — v0.0.2 Forge Bootstrap

```yaml
status: PARTIAL_AUTOMATED
test_date: 2026-08-27
tester: packaged-server automation only
build: 1.20.1-0.0.2-dev
tested_implementation_commit: 05ef786c3df567517e28d1cb17bb1c74e57a4cc2
artifact_sha256: 827c07b34745cc5e6f484beb398b718cf87bd50e8d5be4f3c12679adc0973dcd
packaged_client_tested: false
```

## MANUAL-V002-001 — Packaged-client metadata and world start

**Preconditions**

- Use an isolated official Forge 1.20.1-47.4.10 client on Java 17.
- Install only the exact distributable JAR identified above.
- Do not use ForgeGradle `runClient` as packaged release evidence.

**Steps**

1. Start the packaged client and open the Mods list.
2. Inspect name, version, description, credits, license, and logo.
3. Create and enter a disposable single-player world.
4. Review the full client log, then archive only privacy-reviewed evidence.

**Expected**

- Metadata matches the approved project identity and states that the build has
  no playable rocket systems.
- The client reaches the world without a project-source ERROR.

**Actual**

`NOT_EXECUTED`. No packaged-client screenshot or log is claimed. Earlier
ForgeGradle client diagnostics do not load the physical release JAR and are
excluded from acceptance.

## MANUAL-V002-002 — Packaged dedicated server and matching client

**Steps**

1. Install Forge 47.4.10 in an isolated loopback-only server directory and copy
   the final JAR.
2. Verify source, server, and client JAR SHA-256 equality.
3. Start the server and connect with the isolated matching client.
4. Disconnect, save, stop, restart the same world, reconnect, and stop again.

**Expected**

- The server identifies Minecraft 1.20.1, protocol 763, and exact mod version
  `1.20.1-0.0.2-dev` without loading client classes.
- The matching client connects twice and both shutdowns finish without a
  project-source ERROR.

**Actual**

`PARTIAL`. The packaged-server portion passed with the final artifact. Source
and server copies share SHA-256
`827c07b34745cc5e6f484beb398b718cf87bd50e8d5be4f3c12679adc0973dcd`.
Both headless cycles verified status identity, saved, exited 0, and reused the
same `world/level.dat`; the selected logs contain no ERROR or client-class
linkage finding. No packaged client joined, so three-way hash equality,
join/disconnect, and restart/reconnect remain untested.

## MANUAL-V002-003 — Declared mismatch policy

**Steps**

1. Create a second isolated Forge 47.4.10 client without the project JAR.
2. Observe the retained loopback server's compatibility indicator and message.
3. Attempt one connection and record the actual result without assuming it.

**Actual**

`NOT_EXECUTED`. The declared `displayTest="MATCH_VERSION"` behavior still needs
packaged observation.

## Scoped G4 applicability decisions

```yaml
two_player_consistency:
  proposed_status: NOT_APPLICABLE
  rationale: >-
    v0.0.2 has no playable content, project packets, shared player state,
    permissions, inventories, or interactions to compare between players.
  human_review_decision: ""
  reviewed_by: ""
  reviewed_at: ""
optional_client_dependency_absence:
  proposed_status: NOT_APPLICABLE
  rationale: >-
    v0.0.2 declares no optional runtime or client-only dependency; the clean
    packaged profile contains only Forge and the project JAR.
  human_review_decision: ""
  reviewed_by: ""
  reviewed_at: ""
```

These proposals do not approve G4 and do not replace the matching-client or
mismatch-policy cases.

## Evidence checklist

- [ ] Three-way source/server/client JAR hash equality.
- [ ] Full-window packaged-client Mods page screenshot.
- [ ] Packaged-client single-player in-world screenshot and selected log.
- [x] Final-JAR packaged-server first-start and restart excerpts.
- [ ] Matching-client first join, disconnect, restart, and reconnect evidence.
- [ ] Missing-project-mod indicator, message, and connection result.
- [ ] Human decisions for both scoped G4 applicability proposals.

See [`evidence/dedicated-server/`](evidence/dedicated-server/) for completed
server evidence and
[`../../work/v0.0.2-test-machine-handoff.md`](../../work/v0.0.2-test-machine-handoff.md)
for the isolated packaged-client procedure.
