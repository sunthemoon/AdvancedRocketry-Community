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
| `gradlew clean build test runData runGameTestServer` | Exit 0, 139 s; 961 JUnit / 173 suites, 0 failures, **restored FROM-CACHE** (`src/main` and `src/test` were unchanged since the STATION-03 fix round, whose run executed them); all 238 GameTests executed and passed; generated files unchanged |
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

## Not covered; risks (first run)

The first run above left invitations and ring blocks uncovered. Both are covered
by the second run below. Rocket identities and the missing-orbit-body case are
reassigned to V150-MIG-01 by
[ADR-042](../../decisions/ADR-042-STATION-04-NATIVE-SCOPE.md), because this fixture
world contains neither.

- No real networked client, multiplayer UI or long load.
- Crash cuts and power loss during the checked write are not exercised.

## Independent re-review and second native run

A second independent review of `e3d136f` and `89600ae` (unmodified report, logs,
mutation runs and evidence re-verification archived in `independent-review-2.zip`)
found no Critical or High issues, and confirmed the evidence hashes and that the
harness oracles are not vacuous. Findings and handling:

| ID | Severity | Finding | Handling |
|---|---|---|---|
| M1 | Medium | STATION-04 native acceptance only partly covered; the rocket/context TODO was dropped | Second run adds a v1.4-written invitation and ring/gap block markers; rocket identities and the missing-orbit-body case moved to MIG-01 by ADR-042; TODO restored as a MIG-01 obligation |
| L1 | Low | The player's own source is also used silently by `/function`, advancement-reward functions and silent mod calls | Expansion and gravity now also require a non-silent source. For a player source, `withSuppressedOutput()` returns the same object exactly when it is already silent. A mod calling a command non-silently as the player remains trusted code |
| L2 | Low | No test of the player-list check | The GameTest now rejects a FakePlayer with the owner's UUID, the logged-out owner's stale player object and a silent own source (all `NOT_LOCAL_PLAYER`) |
| L3 | Low | "961 JUnit passed" was restored from cache | Stated in the table above; the second run executed `:test` |

Second native run (`native-checks-2.zip`), same retained v1.4 world copy:

1. **v14-team** (pinned v1.4 host, rebuilt fixture): the connected owner ran the
   v1.4 host's `arce station invite <station> probe1` for a second connected player
   (reply "Invitation recorded for probe1"). The console placed four markers
   (gold, diamond and emerald blocks in the ring at 300,128,0, -320,130,40 and
   10,129,-370; a lapis block in the gap at 500,128,0) using forceload and setblock.
   Then the admin transfer made the old owner a member. The v1.4-written payload has
   owner = successor, members = [old owner], invitations = [invitee], and no other
   change. The only celestial change is the probe players' visit records (time 933).
2. **upgrade**: migration 1; all markers pass `execute if block` after migration
   and after expansion; the invitee's and the member's requests are rejected; the
   console and `execute as <owner>` are rejected; the owner expands and the file is
   expanded before save-all. The stored payload equals the v1.4-written payload with
   versions bumped and only the target region widened, invitation included.
3. **restart**: no migration or backup; markers intact; region, members and
   invitation retained; the repeat is idempotent and the member is rejected.

Every phase has 0 ERROR and 0 project WARN; the source world is unchanged. One
earlier attempt failed because `setblock` needs a loaded chunk; it is retained in
`attempt-01-failed/`. Forceload replies were read from the log: "Marked chunk" /
"Unmarked chunk" / "No chunks were marked" only, never "not loaded".

`independent-review-2.zip` excludes the reviewer's three full repository exports
(`tree-89600ae/`, `mutA/`, `mutB/` with their build output, about 1.5 GB) and
its re-extracted copies of already committed evidence ZIPs (`fixchecks/`,
`native/`). The exports can be regenerated from the commit and the archived
`mutate.py`; the mutation logs and summaries are included. `review-2.json` records
both archives. `review-2-source-identity.json` and `review-2-links.json` bind this
fix commit, whose base is `f27b11a`.

Full required run for this fix: exit 0, 172 s; `:test` executed, 967 JUnit /
174 suites, 0 failures; all 239 GameTests passed; generated files unchanged. The
main JAR `8f2d12347e21998083585c2aaa32f950d7d40adfd720a51e3bb24a0f4c0b0a29` equals
the one used by the native run; the API JAR is unchanged.
