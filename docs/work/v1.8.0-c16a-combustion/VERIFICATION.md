# C16a-02 combustion generator — verification

Date: 2026-10-03. Branch: `codex/v1.8.0-classic-content`. Base HEAD:
`cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6`. This is an **uncommitted development
snapshot**, not an implementation commit or release candidate. Contract:
accepted ADR-064 revision 2. Version remains **IN_PROGRESS / IMPLEMENTING**.

## Delivered scope

Ledger unit `libvulpes:gameplay/coalGenerator` is delivered as REDESIGNED under
ADR-062 and ADR-064. One row is closed; **199 PLANNED rows and 156 REVIEW assets**
remain, so the C19 ledger closure check still fails as required.

Stable ID `advancedrocketrycommunity:combustion_generator`: furnace fuels,
40 FE/t, 20,000 FE storage, one shared 1,000 FE/t push/pull allowance, preserved
fuel containers and full-buffer/disabled pause. The server owns the bounded
schema-1 root, fuel slot, menu, capability epochs and operation lock. Only six
already-FULL-loaded neighbours are consulted; no chunk ticket or world scan.
The original menu and four original generated PNGs have recipes, loot, tool
tags, eight facing/lit variants and bilingual labels.

Future/corrupt roots remain quarantined with no resource capability. Oversized
roots pass through BE serialization without a copy; a whole-chunk Save event
veto prevents vanilla from silently omitting the BE, restores the dirty flag
and retains the on-disk chunk. This intentional refusal requires offline data
repair; it is not a successful save of malformed input.

Not delivered: C16a-01/03–06, C16b–d, C17–C19, release approval, GPU or real-player
multiplayer evidence. Previous dirty C15 work and the user-owned development
documentation bundle are preserved and excluded from this slice's source delta.

## Actual checks

Windows / Java 17.0.7 / Minecraft 1.20.1 / Forge 47.4.10. Gradle used the cached
offline dependencies and an explicit `JAVA_HOME`; heavy jobs were serialized.

| Command/check | Result |
|---|---|
| `gradlew.bat --offline clean build test runData runGameTestServer` (final `full-03`) | Exit 0; 6m40s; **1,470 JUnit /276 suites**, zero failures/errors/skips; **401 required GameTests**, seed 0 |
| `gradlew.bat --offline runData` before the final build | Exit 0; final generated art matches its canonical byte assertions |
| Final build/DataGen runtime input hash comparison | Java/build/generated inputs unchanged; Python harness-only corrections are separately recorded |
| Original-art screening against vanilla 1.12.2 and 1.20.1 | Exit 0; **36 CLEAR**, no HIT or SUSPECT |
| Independent copied-source JUnit | Exit 0; 37 tests /5 suites (24 generator, 13 COMMON configuration) |
| Independent full GameTest server | Exit 0; 401 required tests, seed 0 |
| Independent pure-domain transition probe | Exit 0; 960,048 transitions |
| Independent packaged JAR comparison | Same entry set, contents and whole-file SHA-256 as root |
| `python -B -m unittest tests.test_run_v180_combustion_smoke` | Exit 0; 5 tests, including report completion and exact chunk-error matching |
| Targeted ledger/planning/art/derivation Python unit tests | Exit 0; 85 tests |
| Final ledger/planning/native-probe Python unit rerun | Exit 0; 78 tests after the ledger documentation correction |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0; 45 checks |
| `python -B scripts/validate_v1plus_planning.py` and `scripts/validate_bootstrap_provenance.py` | Exit 0 each |
| `python -B scripts/validate_v180_content_ledger.py --require-accepted` | Exit 0; 653 units, one additional delivered row |
| Same ledger validator with `--closure` | Exit 1; 199 PLANNED rows and 156 REVIEW assets, not version-complete |
| `python -B scripts/validate_v120_machine_resources.py`, `scripts/validate_v090_resources.py`, `scripts/check_client_imports.py` | Exit 0 each |
| `python -B scripts/run_v180_combustion_smoke.py --runtime-template <retained-runtime> --source-world <retained-world> --v17-jar <handoff-jar> --host-jar build/libs/advancedrocketry-community-1.20.1-1.8.0-dev.jar --fixture-jar <retained-fixture> --work-root <fresh-native-04>` | Exit 0; six packaged-server phases, two repeat restarts and oversized-chunk refusal comparison passed |
| `git diff --check` | Exit 0, including final documentation |
| `git diff --exit-code` | Exit 1; intentional uncommitted development changes, not a clean candidate/G2 PASS |

The new JUnit suites contain 24 cases for domain accounting, bounded/lossless
NBT, exact menu-open payload and generated resources. Seventeen new Forge
GameTests cover real coal/lava, containers, full buffers, push/pull simulation,
shared budgets, reentrancy, retained capability references, invalid/future and
oversized saves, chunk-save veto and dirty retry, explosions, removal drops,
disable/long fuel, stale menus, million-tick menu encoding and occupied-slot
inbound/partial-container-outbound transfers. No timeout, production budget or
forensic-reader bound was relaxed, and no assertion was removed to pass.

## Corrections and retained failed attempts

- Compilation: removed an invalid Slot override; gave each DataGen provider a
  distinct name; supplied the pinned LazyOptional exception supplier.
- The first full build had one resource-test language-root expectation wrong;
  the exact assertion now uses the established v1.8 translation namespace.
- Art screening flagged the first newly authored lit face's geometric likeness
  to a vanilla sea-lantern window. The face was redrawn with a narrow asymmetric
  flame. No detector waiver or threshold change was made.
- Independent review found two High issues: occupied-slot quick-move mutated a
  detached stack; throwing inside BE save could cause vanilla to omit the BE.
  Both production fixes have targeted regressions and independent rechecks;
  see [dispositions](review-dispositions.md).
- Native harness attempts retain their receipts and logs: idempotent forceload
  feedback, the pinned query wording, and a duplicated logger/console report
  race were corrected. Final console feedback fences the report command;
  actual resource assertions are unchanged. The oversized fixture uses two
  4,096-byte arrays, keeping each within the existing forensic reader's limit
  while exceeding the production root's aggregate 8,192-byte limit.
- The first final-document ledger check found a decision-token delimiter error
  and the missing unit ID in this evidence file. Both documentation defects
  were corrected; the accepted check then passed, while closure still fails
  on the actual undelivered rows/assets. The failed receipt is retained.

## Evidence binding

Native S1 first boots a copied historical world with the actual v1.7 packaged
JAR, then installs the current reviewed v1.8 JAR. Coal retains exactly 20,000 FE,
1,600 duration and 1,100 remaining ticks; lava retains 20,000 FE, 20,000 duration,
19,500 remaining ticks and one empty bucket. Both repeated same-world restarts
preserve every root exactly, including future/corrupt roots with three coal.
Exporting 1,000 FE consumes exactly 25 additional burn ticks before the buffer
is full again. The offline oversized fixture is loaded, quarantined and refused
on save; its compressed on-disk chunk remains byte-identical to the patched
input. Historical world and input JAR hashes remain unchanged. Ordinary phases
have no ERROR/FATAL or client-linkage finding; the refusal phase retains **86
intentional ERROR lines**, all matching only the explicit Save-event exception
or the exact chunk `8,8` save failure, and zero FATAL/client-linkage findings.
This is a tested refusal, not a clean-log or successful-save claim.

The main development JAR is 5,010,626 bytes, SHA-256:
`582ac7f5b700b6355793f97ae3773741a743c02eaa7edc07190312969fff056e`.
Sources JAR: `e843e036cd89129089c0bf42b3c4db08effffeb6cdc88fc235dc5ded77a275d4`.
API JAR: `252463ff81bdfc1d48c9e6c3ea397e7ca1bb00f4ebf9e5b804405bd27fe6b2eb`.

`source-identity.json` records the actual pre-task dirty baseline and the final
delta rather than claiming all changes since HEAD belong to this slice.
`root-evidence.zip` retains JARs, JUnit XML, full/failed logs, manifests, art
receipts, native phase receipts and compressed chunk snapshots. Independent
source/retest evidence is in `reviews/round-01-evidence.zip` and its report.
`evidence-files.json` and `checksums.txt` bind the archived members and ZIPs.
The independent final report is [REVIEW-01 raw evidence](reviews/REVIEW-01.raw.txt), SHA-256
`a0d17fbc78a1875ca6589287ce79bc419e84c2456bc4f46b40b56465c30d70f6`.
Historical C15/contract archives are not rewritten. The evidence packet never
includes the excluded user documentation bundle or copied server libraries.

## Acceptance and residual risk

**All version Required Gates satisfied: NO.** No G0–G9 checkbox, acceptance
cursor, candidate commit, tag or release decision is changed. GPU V1 and real
two-client V2 were not performed; ADR-018's full campaign remains deferred.
Native S1 is limited to this generator and does not certify every C16 machine
or the entire upgrade chain. Forge external receiver calls retain ordinary
automation semantics, not arbitrary-crash distributed atomicity. Full Forge
test logs contain intentional negative-case diagnostics; they are not claimed
error-free. The next current-version slice is C16a-01 tag resolution and
signature migration, followed by controller-owned hatch integration.

An oversized root makes the **entire affected chunk unsavable** until backup
and offline repair. Other changes in that chunk are also not durable; this is
not a recovery mechanism for unrelated cross-chunk transactions.
