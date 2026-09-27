# V130-COMP-02 — Item fuels and preserved loader batches

## Scope and identity

Baseline `2043b08`, branch `codex/v1.3.0-public-api`; accepted contract `c20dde3`,
[ADR-027](../../decisions/ADR-027-ROCKET-ITEM-FUELS.md). Production, API-only fixture,
regressions and reader documentation are committed as `5385be6`; the finite runner
and Python updates are `3938a43`. Root owns tracked
writes; independent review and reruns use a separate agent and Temp-only outputs.
The user-provided untracked documentation bundle is outside the write scope.

API **1.5** preserves sixteen exports and adds `RocketFuelDefinition`,
`RocketFuelRegistrar` and `RegisterRocketFuelsEvent`. Owner/thread-bound atomic
loading registration accepts known whole-item fuels with fixed units and optional
one-item remainders. There are no tick-time provider callbacks or world state in
the immutable catalog. Built-in fuel remains 500 units with one empty canister.

Real player/automation inputs, a distinct output role and a captured consumed batch
now drive the existing one-slot loader at 25 units/tick, within six blocks of an
owned eligible loaded rocket. Queued native metadata and pending remainders are
preserved. Loader schema 2 explicitly migrates schema-1 items and active buffers;
the legacy codec and its four tests remain unchanged. Rocket snapshot, entity,
flight and network schemas are unchanged.

Normal native loader drops carry state and ownership; actual BlockItem placement
replaces the carried root instead of letting native recursive merge repair missing
fields. Future/malformed/unresolved/lossy data blocks operation and retains its
original root. Oversized existing roots use opaque save passthrough and refuse
survival removal; arbitrary corrupt-data native serialization is not guaranteed.
No new upstream content or artwork is imported.

## Commands and observed results

Windows, Java 17.0.7, Forge 47.4.10, repository Gradle wrapper and Python 3.13.15
from `D:/python/pyenv/pyenv-win/shims/python.bat` with `PYTHONUTF8=1` and `-B`.

| Actual command | Result |
|---|---|
| `gradlew.bat test --tests '*FuelLoaderPersistenceTest' --console=plain` | Pre-change legacy baseline: exit 0, 4 tests, 16 s |
| `gradlew.bat runData test --tests '*FuelLoader*Test' --tests '*RocketFuelRegistryTest' --console=plain` | Exit 0, 18 focused tests, 45 s |
| `gradlew.bat clean build runData publishMavenJavaPublicationToLocalProjectRepositoryRepository --console=plain` | Final exit 0, 54 s; 739 JUnit in 139 suites, zero failures/errors/skips; DataGen written 0 |
| `gradlew.bat -p compat-test-mod clean build --offline --no-daemon --no-build-cache --console=plain` | Exit 0, 23 s, all ten tasks executed; classifier-only compilation and forbidden internal import checked |
| `gradlew.bat runGameTestServer --console=plain` | Final exit 0, 158 s; all 185 Required passed |
| `python -B -m unittest tests.test_v130_fuel_smoke tests.test_v130_component_smoke tests.test_v130_adapter_recovery_smoke tests.test_v130_adapter_flight_smoke tests.test_v130_atmosphere_boundary_smoke tests.test_v130_suit_equipment_smoke` | Exit 0, 65 checks |
| `python -B scripts/run_v130_fuel_smoke.py <fresh-server> --host-jar <copied-host> --fixture-jar <copied-independent-consumer> --evidence-dir <new-directory> --java <java17> --accept-eula` | Exit 0; four clean processes, approximately 108 s total; observation windows 20/26/21/21 ticks |
| `git diff --check`; staged `git diff --check`; `git diff --exit-code -- src/generated` | Exit 0; generated files stable after staging |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0; 45 checks, no pending/warnings/failures |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0; 11 plans, 33-input inventory |

Four copied JAR identities are in [artifact identities](artifact-identities.json);
[full JUnit XML](junit-full.zip) was
captured before independent focused tests could replace shared Gradle reports.
The [JUnit summary](junit-summary.json), [build evidence](build-evidence.zip),
[consumer classpath](consumer-classpath.json), [consumer artifact](consumer-artifact.json)
and [review history](review-history.zip) retain results and original failures.
The [runtime summary](runtime-summary.json) and [native runtime archive](native-runtime.zip)
retain all four actual process logs and native files. Only the two v1.3 locale files changed; historical
generated resources and acceptance evidence are unchanged.

After evidence staging, the link-only validator resolves all **1,398** Markdown
links and planning validation still passes. Full `git diff --exit-code` and staged
whitespace checks pass. [SHA256SUMS](SHA256SUMS) binds every evidence file; both
working files and staged Git objects are checked against it before committing.

## Native dedicated sequence

The fresh world uses only 104 hash-verified Forge library files from a previously
prepared installation, never its world/configuration. The host and independently
compiled consumer are the only installed mods. The runner checks mod/status
identity, clean exit, stable configuration, raw Anvil/entity/journal data and
original capture checksums. There are no connected players or remote services.

1. **Buffered:** actual hoppers insert one 1,573-unit blaze-powder fuel and one
   metadata-bearing charcoal. The first assembled rocket fills to 1,500; the
   consumed batch retains 73 units and its frozen stick remainder. The out-of-range
   charcoal stays queued. A separate valid legacy loader fixture migrates a
   125-unit buffer and pending empty canister (original total 500).
2. **Registration skipped:** the saved loader roots first remain intact without
   external fuel definitions. A second actual assembled rocket then receives the
   captured 73 plus legacy 125, totaling **198**. Both correct remainders become
   outputs; unregistered queued charcoal is not consumed.
3. **Definitions updated:** the still-unconsumed charcoal now supplies the new
   **301** units and bowl remainder, bringing the second rocket to **499**.
   The first rocket remains exactly 1,500. No extra input is supplied.
4. **Restart:** both complete rocket authorities, all three loader roots and the
   two empty input hoppers retain their exact expected state. No loose item or
   unfinished assembly transaction remains.

The absent-registration process completes a previously captured batch. The changed
registration process tests an **unconsumed** input; it does not independently test
a still-active batch under a changed registration. Unit/native tests separately
cover the persisted immutable batch and no-remainder/output-is-fuel behavior.
The legacy 125-unit buffer is an explicit migration fixture, not a claim to have
observed its earlier item consumption.

## Corrections retained in the record

- A first compile exposed exhaustive historical language switches missing the new
  status. The new locale belongs to the v1.3 generator, not a rewritten v0.6 asset.
- An invalid publication task name failed before task execution. Subsequent commands
  use the repository's actual local-publication task.
- A first full build caught the old exact API minor in the isolated consumer;
  it now requires 1.5 while retaining the same strict version assertion.
- Two Python negative-version fixtures still replaced `1.4`; the new positive
  strings are 1.5. Their explicit mismatch checks were corrected, not removed.
- The first GameTest assumed unknown ForgeCaps always disappear. Pinned Forge
  inspection showed lazy capabilities retain raw data until initialization. Tests
  now separately require exact lazy capability preservation/extraction and reject
  actual native negative-damage normalization. The original failure is retained.
- Independent review found native BlockItem's default-field merge could normalize
  blocked roots. The production replacement and actual future/malformed/scalar
  native drop/place tests address it; normal owner/batch/item tests also pass.
- Independent runner review reproduced an early invalid-Java diagnostic being
  replaced by a missing-output-directory error. The safe creation order and a
  sixth mocked Python test preserve the original failure without starting a JVM.

No assertion, timeout, transfer rate or payload budget was relaxed. Existing
intentional Precision migration save-failure diagnostics remain in GameTest logs.

## Independent verification

The independent verifier reran five focused Gradle suites: **39/39 JUnit** passed,
exit 0 / 51 s, plus **6/6** Python fuel tests. Source hashes for 1,070 files and all
four copied/build artifacts remained unchanged through the rerun. The final
[independent archive](independent-review.zip) retains commands, original reports,
XML, hashes and the bounded raw-data audit.

Readback verified all 61 runtime manifest entries, 104 prepared libraries, 114
consumer classpath JARs, 19 fixture source hashes and the exact nineteen exported
API classes against main-JAR bytes. Actual native Anvil/entity/journal records
confirm both fuel totals, queued metadata, captured batches, correct remainders,
ownership and stable restart; four processes exit 0. This is not just a reuse of
the runner's JSON verdict. No unresolved source finding remains after the recorded
placement, test-assumption and early-diagnostic corrections.

The first packaged process contains a real **`Can't keep up` warning: 2,056 ms /
41 ticks**. It is retained rather than suppressed or rerun away. No ERROR/FATAL or
linkage failure was found, but this run does **not** establish performance or
stability acceptance.

## Remaining acceptance boundaries

This is scoped development A0/A1/S1 evidence, **not** forced-power-loss recovery,
arbitrary external-mod guarantees, native item-mod uninstall, real GPU/multiplayer,
remote stability or long-load acceptance. Loader and rocket files are not an atomic
power-loss transaction. Creative/no-drop/explosion destruction retains native
semantics. Downgrade requires the pre-upgrade world backup, not breaking schema-2
loaders in an old binary.

Full original-machine/dimension acceptance stays deferred under ADR-018. The
version and inherited G0-G9 remain `IN_PROGRESS`; no release/tag or human approval
is created by these checks. Continue the current version's environment/body-context
query slice, then satellite and remaining compatibility work.
