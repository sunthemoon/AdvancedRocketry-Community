# V150-WARP-02 verification

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Base: `a001dc1` (ADR-044 revision 3 accepted). Contract:
[ADR-044](../../decisions/ADR-044-STATION-WARP-LOGICAL-RELOCATION.md) §2, §3 and §6.
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`.
No release candidate, tag or human Gate approval. Independent review pending.

## Implemented scope

- **Station root schema 4** (`format_epoch = v1.5.0-station-warp`, constant
  `StationLimits.REGISTRY_SCHEMA_VERSION`):
  - root 4 requires a `warp_energy` list;
  - roots 1-3 must not carry one, and a root that does is rejected;
  - the epoch is checked per schema (2: `v0.9.0-beta`, 3: `v1.5.0-orbital-station`,
    4: `v1.5.0-station-warp`).
- **Migration:** the pre-start migration (ADR-040) upgrades root 3 to root 4 by
  adding only an empty list, and chains roots 1 and 2 through record 1 → 2.
  Runtime load of anything but root 4 stays blocked and preserved. The backup
  manifest records station 3 → 4.
- **Balance entries:**
  - each entry has exactly `station_id` and `energy`, is at most 64 bytes, and
    holds 1..10,000,000 FE;
  - a zero, negative or over-bound value, a duplicate, an unknown station or
    reservation, a wrong tag type, an extra key, or more than 4,096 entries
    blocks the registry unchanged;
  - entries are encoded in station-UUID order.
- **Registry model:**
  - balances live beside the stations;
  - `relocateChecked(expected, replacement, expectedBalance, cost)` checks that
    the replacement is exactly one orbit relocation, the cost is positive and
    covered, and the live state and balance equal the observed ones; it then
    swaps the state and the balance with no fallible step between them;
  - `creditWarpEnergy` caps a balance at 10,000,000 and credits only committed
    stations;
  - deleting a station drops its balance.
- **Relocation predicate:** `StationState.isOrbitRelocationOf` is true when the
  orbit differs and every other field (region, environment, identity, owner,
  team) is equal. It is independent of `sameAuthorityAs`. `withOrbitBody` keeps
  the configured gravity.
- **Checked relocation:**
  - `StationRegistrySavedData.checkedRelocation(observed, target, cost)` rejects
    a quarantined registry (`UNAVAILABLE`), a changed station (`STALE`), the same
    orbit (`UNCHANGED`) and an uncovered cost (`INSUFFICIENT_ENERGY`), all before
    any write;
  - otherwise the candidate is the live registry with the relocated state and the
    debited balance, and it must decode to exactly that: every other state,
    reservation and balance unchanged;
  - it is written by ADR-041's checked replacement and published once.
- **Growth and gravity** candidates now also verify that every balance is
  unchanged. A free relocation through the growth/gravity path is rejected before
  any write.
- **`foldWarpCredits`** is one ordinary mutation (dirty, never a flush):
  - it reports credited and refused energy;
  - it refuses credits to missing stations and beyond the cap;
  - it refuses a new entry while the encoded registry is within 256 KiB of its
    4 MiB bound. A cheap upper bound avoids encoding on small registries.
- **Audit logging:** a deletion that drops energy logs
  `ARCE_STATION_WARP_ENERGY_DROPPED`; refused credits log
  `ARCE_STATION_WARP_CREDIT_REFUSED`.
- **Plan update:** the plan's `save_schema` line now reads "station root 4 /
  record 2 / reservation 1; other managed roots 2" (ADR-044 §6).
- **API:** the API JAR is byte-identical to the previous slice
  (`97d1aaad…`).

Not in this slice (WARP-03 onward):
- the warp core block, pending-credit map and fold schedule;
- config;
- commands, confirmation, countdown and commit scheduling;
- the in-motion rocket port and the admission change;
- the deletion rocket guard change;
- UI.

The fold has no production caller yet; WARP-03 adds the core that feeds it.

## Tests

- `StationWarpRelocationTest` (14): success, retry, insufficient energy, spending
  the whole balance, the free-relocation rejection, growth/gravity balance
  invariance, model publish rules, credit caps, deletion and cell reuse, fold
  accounting, and the entry headroom (a registry grown past 3.75 MB).

  Each crash-cut row of ADR-044 §3 is a fault-injection test, and each asserts
  that every readable registry holds (old orbit, old balance) or (new orbit,
  debited balance):
  - cut before replacement;
  - refused replacement, then a retry;
  - replaced despite an error;
  - unreadable torn outcome (quarantine; the next start fails closed);
  - publish failure after replacement.
- `StationWarpSchemaTest` (6):
  - root 3 → 4 lossless and idempotent (new synthetic fixture
    `migrations/v150/stations-v3.snbt`, original test data);
  - runtime refusal of root 3;
  - per-schema shape and epoch rules, and root 5 as the future case;
  - balance round trip;
  - eleven malformed balance cases plus a list of integers;
  - the entry byte bound and the headroom arithmetic.
- **Updated for the intended root-4 change**, with exact values and no assertion
  removed:
  - pinned root 3 → 4 in `SavedDataSchemaMigratorTest`, `StationWorldMigrationTest`
    and `BetaOperationalReportTest`;
  - `StationSchemaMigrationTest` expects root 4 with an empty list;
  - its future case moves to schema 5, with root-3 epoch and missing-list cases
    added;
  - the populated fixture now has a real balance, and the legacy variant drops it
    (legacy roots have no balances).

## Native evidence

The harness `native_station_warp_schema_check.py` copies the world left by the
STATION-04 second native run: root 3, the 768-wide witness station with a member
and an invitation, the Mars neighbor, and markers. It first verifies that the
world's managed files equal that run's recorded restart state. Two finite
dedicated-server processes then ran with the WARP-02 host JAR and the unchanged
consumer fixture.

1. **upgrade**:
   - `[ARCE-BETA-1002] managed=4, migrated=1`;
   - the station file read before the first save equals the root-3 payload with
     only the root version, the epoch and `warp_energy=[]` changed;
   - `admin inspect` shows both regions unchanged;
   - every other authority is unchanged (satellite clock normalized);
   - exactly one new backup holds the four root-3-era managed files
     byte-for-byte, with station 3 → 4 and the others 2 → 2;
   - the STATION-04 backup is unchanged.
2. **restart**: `migrated=0, backup=none`; the payload is identical and both
   backup trees are unchanged.

Both phases had 0 ERROR, 0 FATAL and 0 project WARN (15 and 10 WARN lines from
offline mode, loader and config defaults). The source world is unchanged. PASS
on attempt 01.

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew compileJava` | Exit 0 |
| focused `gradlew test` (station/migration filters) runs 01-03 | 01: 12/149 failed; 02: 2/169 failed; 03 (two new classes): passed. Recorded in `focused-runs.txt` (full logs not captured) |
| `gradlew clean build test runData runGameTestServer` run 01 | Exit 0, 3 m 8 s; `:test` executed, 1,001 JUnit / 180 suites, 0 failures; all 240 GameTests passed; `src/generated` unchanged |
| `python native_station_warp_schema_check.py` attempt 01 | Exit 0; PASS as above |

Main JAR `ccd7b5290fed052f3cc611c9077cecd573b10a46c6261f446f53881dfc4eb323`
(the one used by the native run).

## Evidence archive

Source directory: `arce-v150-warp02-*` under the Windows Temp directory (named in
`evidence-archives.json`). The disposable `station-server/` copy is excluded; each
phase's logs, decoded state and the migration backups are under `native/`.

`root-checks.zip` holds:
- the full Gradle log, JUnit XML and GameTest logs;
- `focused-runs.txt`;
- the native harness, its output and log;
- the source, generated-file and artifact hashes;
- the packaging script.

The member index, `source-identity.json`, `links.json` and `SHA256SUMS.txt` bind
this directory and the staged tree.

## Risks

- The balance has no producer until WARP-03, so the fold and headroom paths are
  exercised only by unit tests.
- Candidate verification decodes and compares the full registry. That is linear
  in stations and bounded by the ORBIT-04 measurement (172 ms median checked
  update at 4,096 stations); a warp commit timing is a WARP-05 item.
- Crash cuts are simulated by fault injection, not by power loss.
