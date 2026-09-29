# V150-STATION-04 verification

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Base: `e3d136f264e881b2da4516c94d0446242179ea77` (STATION-03 review fix).
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`.
No release candidate, tag, real-client result or human Gate approval.
Independent review of this slice is pending.

## Outcome

A copy of the retained genuine v1.4 world went through three finite
dedicated-server processes:

1. **v14-team**: the pinned v1.4 host (`576eaaf8…`) ran its own
   `arce station admin transfer` on station `30499d22-70c4-43d8-bdfc-2f400eeea04e`
   ("Legacy copy witness", cell 0,0, orbit Moon). The previous owner became a
   member, so the world now holds a v1.4-written nonempty team at root schema 2 /
   record 1. The v1.4 phase created no migration backup and changed nothing but
   that owner and member list.
2. **upgrade**: the v1.5 host migrated the world before start (`managed=4,
   migrated=1`). Then:
   - the bare console `arce station expand` was rejected ("A player is required");
   - `execute as <owner> run arce station expand` was rejected as not the local player;
   - a connected member was rejected (UNAUTHORIZED);
   - the connected new owner requested, got the warning naming the station and
     `-256,-256..255,255` to `-384,-384..383,383`, and the stored file was still
     512 wide;
   - the owner confirmed. The station file read **while the server was still
     running** already equalled the complete expected expanded registry
     (`checked-write-before-save.dat`);
   - `admin inspect` showed the 768 region and the unchanged neighbor
     (`768,-256..1279,255`); a repeat request reported already expanded.
   After `save-all flush` and stop, the station payload equals the v1.4-written
   payload with only root version/epoch, record versions and the target region
   changed. Members, invitations, owner, pad, orbit and neighbor are unchanged.
   The one backup holds exactly the four v1.4-written managed files byte-for-byte,
   with per-file schemas (station 2→3, others 2→2).
3. **restart**: `migrated=0, backup=none`; the expanded region, neighbor and
   membership persist; the owner's repeat is idempotent and the member is still
   rejected; the station payload is identical to the upgrade output and the
   backup tree is unchanged.

The original source world inventory is unchanged after all three processes.
Every production expansion command left the Space loaded-chunk count unchanged.
All phases: 0 ERROR, 0 FATAL, 0 project WARN. Remaining WARNs are offline mode,
loader, and Forge config defaults (15 / 11 / 10).

## How players were simulated

After the STATION-03 review, expansion accepts only a connected player's own
command source. The opt-in fixture probe (`-Darce_adapter_test.stationSmoke=true`,
`arce_station_probe join|run|leave`, documented in
[`compat-test-mod/README.md`](../../../compat-test-mod/README.md)) connects a
mock `ServerPlayer` with the chosen UUID over an embedded channel, as vanilla
`GameTestHelper.makeMockServerPlayerInLevel` does. It loads only that player's
own chunk, moves the player into Space in creative mode (as the vanilla mock is),
and runs only the two expansion commands from that player's own command source.
It uses no host classes and writes no host data itself.

Connecting players is real gameplay: the celestial authority recorded their first
Earth visit and the Space discovery/visit, all at game time 939. The harness allows
exactly that and requires every other celestial field and all other authorities
to be unchanged (satellite mission clock excepted, as before).

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew publishMavenJavaPublicationToLocalProjectRepositoryRepository` | Exit 0 |
| `gradlew -p compat-test-mod clean build -ParceVersion=1.20.1-1.5.0-dev` | Exit 0; fixture `630f50aa98a5a9718e507fa184ec00286a9e9bcbf40c2ac5aeceb0acc6206967` contains the probe |
| native harness attempt 01 | Exit 1; v1.4 phase passed; harness bug (method name shadowed by a field) at the first `join` |
| native harness attempt 02 | Exit 1; all expansion checks passed; the "other authorities unchanged" oracle rejected the probe players' visit records |
| native harness attempt 03 | Exit 0; PASS as above |
| `gradlew clean build test runData runGameTestServer` | Exit 0, 139 s; 961 JUnit / 173 suites, 0 failures; all 238 GameTests passed; generated files unchanged |
| `git diff --check` and the repository/planning/provenance validators | See the evidence archive |

Both failed attempts are retained with their logs and native outputs. Attempt 02's
fix narrowed the oracle to the observed visit-only change, not a blanket exclusion.
Host JARs are byte-identical to the STATION-03 fix round (main
`5225de27c1095858e7cff10ec830f38a5dbc18fb3d46e6e03e7ccb0cbaa6ea17`, API unchanged).

## Evidence archive

Source directory:

```text
C:/Users/Administrator/AppData/Local/Temp/arce-v150-station-native-88ac79b6f33b4516a3ada0b5aa418818
```

`native-checks.zip` (302 members, SHA-256
`c1925e02b64737abaeeb34001d70b8d2db4429f21372769fc678a1de142de7a7`) holds the
harness and its patch helpers, all three attempts' native outputs (per-phase
stdout, latest/debug logs, launch/status/commands/probe reports, decoded before/
after NBT, the checked write read before save, the migration backup copy, source
inventory and summary), the publish/fixture build logs, the full Gradle log,
JUnit XML and GameTest logs, and `packaging/archive_evidence.py`. The disposable
`station-server/` runtime copies (libraries, mods, worlds) and bytecode caches are
excluded; each phase's logs and managed data are retained under `native/`.
`native-checks-files.json` indexes the ZIP; `native-summary.json`,
`root-checks.json` and `evidence-archives.json` summarize it; `source-identity.json`
binds every other staged file to its blob, `links.json` lists validated local
Markdown targets and `SHA256SUMS.txt` covers this directory.

The packaging step asserted that the only changed source files are the fixture's
`AdapterTestMod.java` and `StationExpansionProbe.java`, that the installed v1.5
host and fixture JARs match `build/libs` and `compat-test-mod/build/libs`, that
the summary and all three phases pass with the source unchanged, and that the full
run has 961/173 passing XML and 238 passing GameTests. After the documentation
edits, `validate_repository.py --require-approved-identity` (45 passed),
`validate_v1plus_planning.py`, `validate_bootstrap_provenance.py`, the planning
regression suite (15) and `git diff --check` all exited 0 (logs in `packaging/out/`,
not in the ZIP).

## Not covered; risks

- No real networked client, invitations, multiplayer UI or long load. Native
  invitations stay empty because only players can invite; synthetic fixtures cover them.
- Rocket entities in chunk data are not compared; only managed authorities are.
- Block contents of the added ring were not inspected natively; production code
  never touches blocks (unit/GameTest evidence), and the platform check is GameTest-only.
- Crash cuts and power loss during the checked write are not exercised.
- Independent review of this slice is pending; G0-G9 remain open.
