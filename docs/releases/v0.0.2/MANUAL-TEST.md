# MANUAL-TEST — v0.0.2 Forge Bootstrap

```yaml
status: PARTIAL_AUTOMATED
test_date: 2026-08-28
tester: packaged-server automation only
build: 1.20.1-0.0.2-dev
tested_implementation_commit: 7567dbb60332526789ee3b2824c582ff1909203e
artifact_sha256: 58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944
packaged_client_tested: false
```

## MANUAL-V002-001 — Packaged-client metadata and world start

**Preconditions**

- Use an isolated official Forge 1.20.1-47.4.10 client on Java 17.
- Install only the exact distributable JAR identified above.
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
`58622a5ad3795d89b087b05f40ed6b4c458602bdf2d07c17176f280722392944`.
Both headless cycles verified status identity, saved, exited 0, and reused the
same `world/level.dat`; full-log review found zero ERROR, zero project WARN, and
zero client-class linkage findings in both cycles. No packaged client joined,
so three-way hash equality,
join/disconnect, and restart/reconnect remain untested.

The existing committed dedicated-server evidence is a headless baseline, not a
manual-player readiness claim. A future matching-client run must use
`run_dedicated_server_smoke.py --manual-player-cycles` and retain its schema-v2
summary plus both full logs. The summary binds the exact server artifact,
loopback port, cycle IDs, timestamps, exit codes, raw-log hashes, player
join/leave observations, and one stable world identity with pre/post
`level.dat` snapshots. Project-source ERROR or WARN and client-class linkage
findings block harness success.

## MANUAL-V002-003 — Declared mismatch policy

**Steps**

1. Create a second isolated Forge 47.4.10 client without the project JAR.
2. After the two-cycle harness exits, use the exact third-start command in
   `docs/work/v0.0.2-test-machine-handoff.md` section 6; do not try to reuse the
   harness session through its CLI.
3. Observe the retained loopback server's compatibility indicator and message.
4. Attempt one connection and record the actual result without assuming it,
   then save and stop the server cleanly and retain the third-start log.

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
mismatch-policy cases. Their required owner/reviewer, risk, expiry, and recovery
record is [`ADR-005`](../../decisions/ADR-005-V0.0.2-G4-APPLICABILITY.md); the
ADR remains `PROPOSED` until both decisions receive an explicit human review.

## Evidence checklist

- [ ] Three-way source/server/client JAR hash equality.
- [ ] Current rendered README screenshot from the tested checkout.
- [ ] Full-window packaged-client Mods page screenshot.
- [ ] Packaged-client single-player in-world screenshot and selected log.
- [x] Final-JAR packaged-server first-start and restart excerpts.
- [ ] Matching-client first join, disconnect, restart, and reconnect evidence.
- [ ] Missing-project-mod indicator, message, and connection result.
- [ ] Human decisions for both scoped G4 applicability proposals.

See [`evidence/dedicated-server/`](evidence/dedicated-server/) for completed
server evidence and
[`../../work/v0.0.2-test-machine-handoff.md`](../../work/v0.0.2-test-machine-handoff.md)
for the isolated packaged-client procedure. The version-scoped evidence helper
is [`../../../scripts/collect_v002_manual_evidence.py`](../../../scripts/collect_v002_manual_evidence.py).
No `evidence/client/` bundle exists yet, and helper validation never marks a
Gate or the version `PASSED`.

The helper scans complete raw client/server logs and archived excerpts rather
than trusting entered finding counts, binds the two server excerpts to the
harness summary by cycle ID, filename, and SHA-256, and enforces lifecycle
marker order. It accepts honest `FAIL`/`BLOCKED` archives by default. The
`--require-acceptance-ready` flag additionally requires every fixed observation
to be `PASS`, both scoped decisions to be `ACCEPT_NOT_APPLICABLE`, the bound
harness evidence, and all other mechanical checks. Its success means only
`READY_FOR_HUMAN_GATE_REVIEW`.

PNG evidence is restricted to full-window RGB/RGBA files within the documented
dimension, pixel, chunk, per-file, and 40 MiB aggregate limits. Unknown,
textual, and other nonessential ancillary chunks are rejected; human pixel
review remains required because structural validation cannot identify visible
private information.
