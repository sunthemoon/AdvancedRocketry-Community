# V130-ROCKET-04: explicit fueled disassembly

## Scope

Verified bounded development slice under accepted [ADR-023](../../decisions/ADR-023-EXPLICIT-FUELED-DISASSEMBLY.md).
Fuel-bearing rockets require a separate exact-state, server-validated disposal
confirmation; empty rockets keep immediate teardown. This is deliberate disposal,
not fuel recovery. No Java public API, packet or save schema changes.

- Baseline: `da4e49a` (verified external-cargo round trip).
- Decision: `c47af3d`; implementation: `a8b427a`; packaged runner: `094705e`.
- Lifecycle-owned offers: at most 64, one per player, 200 server ticks; shared
  bounded intent limiter, one-use random token, no renewal on repeated interaction.
- Binding: player/entity/owner/snapshot/hash/full flight state/position. Submission
  rechecks alive/non-spectator, authority, range, loaded entity, dimension, state
  and journal. No arbitrary chunk loading or client-authoritative amount.
- Failures do not pre-empty fuel. Successful restoration/removal reports the
  confirmed amount. Operator release-test hooks require `discard-fuel <units>`;
  this is separate from the ordinary player's suggested-command confirmation.
- Main-hand interaction only offers consent; offhand and repeated clicks do not
  confirm. Existing operator diagnostic permissions remain unchanged.

v1.3 is IN_PROGRESS. This report does not approve G0-G9, release/tag the mod,
claim arbitrary-power-loss atomicity or perform the deferred full parity campaign.

## Commands and development checks

Windows / JDK 17.0.7 / Gradle 8.8 / Forge 47.4.10. Gradle commands below used
`--offline --no-daemon --no-build-cache`.

| Command | Actual result |
|---|---|
| `gradlew.bat compileJava` | Exit 0, 17s, initial implementation |
| Focused `test` for confirmation/maintenance/limiter | Exit 1, 18s: test called nonexistent `RocketFlightData.assembled`; corrected to the real `initial` factory |
| `gradlew.bat test runData runGameTestServer` | Exit 0, 1m58s; first seven consent GameTests plus existing suite, all 141 Required passed |
| `gradlew.bat runData` after registering the v1.3 provider | Exit 0, 26s; two new locale files written |
| `gradlew.bat clean build test runData runGameTestServer publishMavenJavaPublicationToLocalProjectRepositoryRepository` | Exit 0, 2m47s; 672 JUnit / 132 suites, zero failures/errors/skips; all 145 Required GameTests passed; DataGen written 0 |
| `gradlew.bat build test runData publishMavenJavaPublicationToLocalProjectRepositoryRepository` | Exit 0, 36s; final licensing metadata refresh, all 672 JUnit passed again; DataGen written 0 |
| `gradlew.bat -p compat-test-mod clean build` | Exit 0, 20s; all 10 tasks executed, positive classifier boundary and negative internal-import checks passed; JUnit `test` is NO-SOURCE |
| `python -B -m unittest discover -s tests -p 'test_v130_adapter_*smoke.py' -v` | Exit 0; 44 tests in 0.375s, including the new explicit-disposal driver check |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0; 45 checks passed, zero pending/warnings/failures; before final evidence-document integration |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0; 11 plans and 33-input inventory valid |
| `git diff --exit-code -- src/generated` | Exit 0 after DataGen/implementation commit; previous version outputs unchanged |
| `git diff --exit-code` after implementation/runner commits | Exit 0; user documentation bundle and new evidence remain untracked, not silently included |
| Final scoped Markdown link check / planning validator | Exit 0; 1,289 links resolve; 11 plans and 33-input inventory valid after evidence integration |

The initial factory compile failure is retained here from tool output; no raw log
file was captured for that command. [Development output](development-tests.txt),
[clean integration output](host-integration.txt), [native GameTest log](gametest-native.txt),
[final packaging output](packaging-refresh.txt), [full JUnit XML](junit-full.zip),
[counts](junit-summary.json), [standalone output](consumer-integration.txt) and
[consumer reports](consumer-reports/) retain the successful observations.

The final `.gitignore` modification notice was added while the initial clean
build was running. A subsequent build/test/publication refresh included it.
Entry-by-entry comparison of all three JARs proves that only
`META-INF/THIRD-PARTY-NOTICES.md` changed between those builds; no executable,
test, language or gameplay resource bytes changed. Full JUnit XML was archived
before the independent focused rerun. See [artifact identities](artifact-identities.json).

The first archive helper used a nonexistent `reports/consumer-boundary` directory;
it failed after archiving tests/copying artifacts. Correcting to the build's actual
`reports/consumer` directory completed the archive after verifying, not replacing,
the existing bytes. This was an evidence-copy error, not a build/runtime failure.

## Test coverage and localization

Nine new JUnit tests cover bounded consent capacity, expiry/backward time, replay,
wrong player/token, replacement, lifecycle cleanup, immutable binding/invalid
quotes, and actual packaged locale keys/argument counts/bytes. Existing limiter
and transaction tests remain intact.

Eleven new Required GameTests exercise zero fuel; ordinary-player command access;
exact disposal and cargo restoration; replay, changed fuel, owner/range failures;
logout/manager restart; occupied targets and restoration rollback; actual entity
main/offhand interaction; shared limiter integration; countdown state; player
eligibility and discarded-entity lookup. The existing landed-reservation cleanup
test now requires explicit consent and checks the reported 1,000-unit disposal.
The discarded-entity case is not an actual chunk-unload test. Pending-journal
refusal is source-reviewed and reuses existing transaction protection.

Inspection found that adding text to the historical v0.5 provider alone did not
reach the active DataGen output. v1.3 now has its own additive provider/output;
all previous version resources stay unchanged. Both locales are verified inside
the real packaged host, not just in provider source. The manual
[client checklist](MANUAL-CLIENT-CHECK.md) is NOT_RUN under ADR-018.

## Final artifacts

| Artifact | SHA-256 |
|---|---|
| Host | `65af2c91dc8ed3e4174c5ee58e6d1b8fbcde5ae127f308fee1cc009cf695663b` |
| API classifier | `695e5d91cd9a1ffc883163e96d0d8cc581e7ed72827d185231f56273205be5c3` |
| Sources | `c72cb3d2738fa4b7e7c02976ea71052a0a4dc84d1f9e3d979504206be337fca0` |
| Independent fixture | `8144ded5d03764cdd6b1eda8ff5e778f7dd87c564ebde3e5dfe20d382629085f` |

The fixture remains byte-identical to ROCKET-03B/C. Public API classes/signatures
remain unchanged; the classifier's archive hash changes with bundled licensing
metadata. No source/art copied from another mod or official game resources.

## Independent review and focused rerun

Separate read-only reviewer against exact `094705e31e82f0e0ed3c886aeb1c311fa1566428`:

```text
gradlew.bat test --rerun-tasks
  --tests '*RocketDisassemblyConfirmationsTest'
  --tests '*RocketDisassemblyLanguageTest'
  --tests '*RocketIntentRateLimiterTest'
  --tests '*RocketTransactionsTest'
  --tests '*RocketTransactionExceptionTest'
  --offline --no-daemon --no-build-cache
```

Exit 0 / 42.856s; 32 tests across five suites, zero failures/errors/skips.
All 20 selected source files and all four artifacts remained byte-identical.
Review identified a misleading repeated-offer time display; it was corrected to
show remaining time without extending token expiry before the final build. No
unresolved source finding remains. The [independent report](independent-review/REVIEW.md),
[command result](independent-review/result.json) and
[integrity comparison](independent-review/comparison.json) retain the actual rerun.

## Bounded packaged runtime

The [recorded command](runtime-command.txt) uses immutable JAR copies and a fresh
libraries-only local server. All 104 Forge library files matched the prior verified
inventory before and after copying. No existing world was reused, no network
authentication result is inferred and no remote machine was used.

Four normal packaged server JVMs completed in approximately 132 seconds:

| Phase | Observation ticks | Exit | Observed behavior |
|---|---:|---:|---|
| Moon landing | 22 | 0 | One initial fill, real outbound leg, cargo and 628 fuel retained |
| Earth landing | 22 | 0 | Exact landing-state restart, real return without refuel, 256 fuel retained |
| Disassembly | 21 | 0 | Restart preserved the full entity state; implicit disposal rejected, 257-unit opt-in rejected; exact state/authority unchanged; explicit 256-unit disposal restored cargo and released landed reservation |
| Container restart | 21 | 0 | Native restored BlockEntity inventory persisted, no rocket or pending journal authority remained |

The [summary](runtime-summary.json) and [raw archive](packaged-runtime.zip) retain
all phase logs, commands, configs, status, launch records, native region/entity
captures and saved journals. All 81 files in the original manifest were verified;
all 92 archive entries were read back and compared with their original bytes.
No runtime ERROR/FATAL, project warning or client-linkage diagnostic was observed.
Normal Forge startup/configuration and explicit loopback offline-mode warnings
remain in the record. Observation windows are finite S1 evidence, not a soak or
performance result.

Separate [native audit](independent-review/native-review.json) reread the actual
Anvil/entity/SavedData captures, all three log variants, JAR provenance and the
81-file manifest. It confirms the refusal sequence, unchanged full entity SNBT
and transfer inspection, exact successful 256-unit disposal, five restored blocks,
reservation release and exact native cargo after restart with both journals empty.
No unresolved source or captured-runtime finding remains. The reviewer did not
launch another packaged JVM; independent readback is distinct from root execution.
The final independent report and its 18 files were copied byte-for-byte. The
evidence directory's `SHA256SUMS` is checked against both filesystem and Git-index
bytes; the unrelated user documentation bundle remains untouched and untracked.

## Remaining boundaries

No V1/V2 click/layout evidence, actual chunk-unload race, forced-power-loss,
arbitrary provider, long-load, remote or all-original-feature acceptance is claimed.
Existing crash recovery boundaries are unchanged. Fuel recovery is not implemented;
confirmed remaining fuel is deliberately discarded. Other v1.3 providers and full
G0-G9 remain outstanding. Continue atmosphere/equipment providers, not full release
acceptance, according to the maintained v1.3 plan and ADR-018 schedule.
