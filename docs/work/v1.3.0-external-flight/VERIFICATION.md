# V130-ROCKET-03C: external cargo Earth-Moon round trip

## Scope and status

Verified bounded development slice; v1.3 remains IN_PROGRESS. One five-block rocket with the independent API-only cargo fixture,
one fuel tank, one Earth-Moon-Earth trip, landing restarts and final production
disassembly/native-container restart. This is not the old 20-trip/restart matrix,
forced power loss, passenger/client coverage, arbitrary providers or a load test.

- Baseline: `7c543d7072ae89c9b497d9699c674507f3759643` (verified ROCKET-03B).
- Active plan: `06a5ea6`, scoped to v1.3 under ADR-020/022 and ADR-018 scheduling.
- No Java, fixture, public API, save or network changes are planned. Existing
  release-test commands provide one initial fuel fill and actual production
  flights/disassembly; the return must use remaining fuel, not an implicit top-up.
- Relocation changes snapshot UUID/location/time/hash and physical entity UUID;
  it preserves the relative blocks, payload, logical identity and owner.
- Landing retains a bound COMMITTED transfer reservation. Return replaces it;
  production disassembly releases it. Stored journal copies are not live cargo.

## Known inherited fuel-disassembly gap

Independent source review found that a fuel tank is a capacity-only plain block.
Production disassembly restores the structure snapshot and removes the entity,
without exporting/refunding remaining flight fuel. Reassembly starts empty. No
documented disposal contract was found; the existing refueled-disassembly test
checks entity/journal cleanup, not remaining-fuel preservation.

Consequently this slice checks exact per-leg fuel debits and persistence through
final landing/pre-disassembly, and external cargo conservation through teardown.
It must not be described as end-to-end fuel conservation. The inherited behavior
is tracked separately as `V130-ROCKET-04`; source review does not by itself claim
a new runtime reproduction or approve resource loss.

## Build evidence

Windows / Java 17.0.7 / Gradle 8.8 / Forge 47.4.10. Commands executed in the
primary checkout with the unchanged host/fixture sources from the baseline.

| Command | Actual result |
|---|---|
| `gradlew.bat clean build test runData runGameTestServer publishMavenJavaPublicationToLocalProjectRepositoryRepository --offline --no-daemon --no-build-cache` | Exit 0 / 2m9s; 130 suites, 663 JUnit, zero failures/errors/skips; all 134 Required GameTests passed; DataGen written 0 |
| `git diff --exit-code -- src/generated` | Exit 0, generated resources unchanged |
| `gradlew.bat -p compat-test-mod clean build --offline --no-daemon --no-build-cache` | Exit 0 / 18s; all 10 tasks executed, including the two consumer-boundary verifiers; `test` is NO-SOURCE |
| `python -B -m unittest discover -s tests -p 'test_v130_adapter_*smoke.py' -v` | Exit 0; 42 passed in 0.372s, including 19 new flight runner checks |
| Same Python command after timestamp correction | Exit 0; 43 passed in 0.348s, including 20 flight runner checks |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0; 45 passed, no pending/warnings/failures; before final evidence integration |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0; 11 plans and 33-input inventory valid |

Complete [host output](host-integration.txt), [native GameTest log](gametest-native.txt),
[JUnit XML](junit-full.zip), [standalone output](consumer-integration.txt) and
[consumer reports](consumer-reports/) are retained. Full XML was archived before
any focused rerun. Existing intentional save-fault diagnostics remain visible.

| Artifact | SHA-256 |
|---|---|
| Host | `3f8be1e0adce70b1c25fc1eeeffeac5b7f398016abf6670af11b42c52c84c200` |
| API classifier | `06568c596efb623c80eddfbdd41afd695451f47ceb935b165d149349157e4020` |
| Sources | `7b1e565e5ce326605d739effd5d37721d65e6fb41f4dba859ede53233c4d755f` |
| Standalone fixture | `8144ded5d03764cdd6b1eda8ff5e778f7dd87c564ebde3e5dfe20d382629085f` |

All four artifacts remain byte-identical to ROCKET-03B. The baseline GameTests
do not themselves establish external-cargo flight; the separate packaged runner
and native readback below establish the scoped observations.

## Runner integration

The four-process runner and focused negative tests were integrated as
`566c92fcde0a8fd3a1e7421036284de114edf6bb` from isolated contribution
`3965e66a880b25d4e3ccd6d9d632b211139b3359`. Runner SHA-256 is
`6d8e01633daacc78f32a57d24960e103358e5d8c19af8420540b1ceaf2d0e3a3`;
tests SHA-256 is `09dcbab19d80133bba5a9fbdf1cf333694e2e3dc384c5065d4e176b054214983`.
Runtime execution is recorded separately below; no v1.3 Required Gate approval
is inferred from the builds or runner integration.

## First runtime attempt

FAIL. The first two JVMs exited 0, each after a 22-tick observation. Outbound and
return live flight checks completed, but the runner stopped after the second
clean shutdown with `Configuration changed between processes`, before its second
native-disk capture. Disassembly and native-container restart were not launched.
The original world, logs and failure result are retained. Configuration differences
were independently diagnosed as the generated timestamp comment on line 2 of
`server.properties`; all other bytes and both TOMLs were equal. See
[raw-byte difference](config-diff.json). Supplementary stopped-world readback
preserved the Earth cargo, two exact debits, 256 remaining fuel and bound
COMMITTED reservation; it does not retroactively complete the failed driver.

The [failed-attempt archive](failed-attempt.zip) contains 72 entries including its
original manifest, native captures, logs and supplementary readback. Every listed
source and ZIP entry was SHA-256 verified. The original world remains unchanged.

## Narrow correction and independent review

`8130bc83e20430560f2a702e2a83b6a935330eb4` integrates worker correction
`5cd46401723ea4c824d43c0c6dc3e50ce1738784`. Comparison now validates the generated
header/timestamp and excludes only that timestamp line. Raw configuration bytes
and hashes are retained; all remaining properties and TOMLs remain exact.
Regression checks reject changed values, unknown keys/comments/extra lines,
line-ending changes and TOML drift. No timeout or production assertion changed.

Corrected runner SHA-256:
`3396dd3fdfce004b19abb4c5a7413fafe64061274373a1fc26944a39ad26d939`;
tests: `a5968da7ce4995284f7fade1ffcbc6c652e76f06ee6c8f95d834e60547d03a5a`.
Independent source review has no unresolved concrete finding. Independent
[original 19-test](independent-python/result.json) and
[corrected 20-test](independent-correction/result.json) runs passed with unchanged
source hashes and clean worker state. Actual failed-run configurations also pass
the corrected comparison. The fresh four-process runtime attempt is recorded below.

## Corrected packaged runtime

PASS, one fresh world, approximately two minutes (00:31:10-00:33:10 local time).
The original failed world was not reused. All four JVMs and the driver exited 0;
the independent native-NBT/provenance/status/log postcheck also exited 0.
The exact [Python command](runtime-command.txt), [driver summary](runtime-summary.json),
[independent observations](independent-observations.json) and
[configuration check](configuration-postcheck.json) are available separately.

| Process | Result | Observation ticks | Persisted authority |
|---|---|---|---|
| Outbound, Moon landing | PASS / exit 0 | 21 | One landed rocket, one COMMITTED transfer, no world cargo |
| Moon restart, Earth landing | PASS / exit 0 | 22 | One landed rocket, replaced COMMITTED transfer, no world cargo |
| Earth restart, production disassembly | PASS / exit 0 | 21 | One native cargo container, no rocket, both journals empty |
| Native container restart | PASS / exit 0 | 22 | Same exact container inventory, no rocket, both journals empty |

The observed origins were Earth `(264,101,264)`, Moon `(8,80,8)`, then Earth
`(0,66,32)`. The return is not the original launch location. Evidence follows
those actual footprints, including chunk-boundary/negative chunks, instead of
assuming a single region. Relative block/palette/anchor/stat data and the exact
external adapter/version/envelope remain equal across relocation. Snapshot and
physical entity identities change as specified; logical identity and owner persist.

One initial 1000-unit fill supplies two positive 372-unit debits: 628 at Moon,
256 at Earth. Each distinct transfer UUID appears exactly once in the cumulative
ledger; there is no return refuel. The 17 named diamonds and 64 iron ingots retain
their exact slots/counts/metadata through both landings, production disassembly
and native-container restart. The final plain tank has no remaining-fuel export;
ROCKET-04 remains open, and no teardown fuel-conservation claim is made.

Independent checks bind the actual installed host/fixture JARs, one provider
registration per process, normal Forge status and native region/entity/journal
files. Stdout, debug and latest logs have no ERROR/FATAL/client-linkage failure;
ordinary first-run defaults, Forge platform and loopback-offline warnings remain
visible. The endpoint was closed after completion. No further Java run was used
for the readback.

The [corrected runtime archive](corrected-runtime.zip) contains 113 entries:
four-phase raw captures, native checksum manifest, nine executed source/helper
snapshots, commands/process identities, independent scripts/results and their
outer manifest. Full installed libraries/worlds and input JARs are not duplicated
in the archive. Every listed source and ZIP entry was SHA-256 verified.

A separate read-only configuration postcheck initially hit Windows shim quoting
before evaluating evidence; its diagnostic is preserved in the archive. Running
the same checks from a script passed without changing server state or assertions.
This did not cause another Minecraft run.

| Archive | Entries | SHA-256 |
|---|---:|---|
| First failed attempt | 72 | `df3690aafdfd835461e71b4787b4093a58aba586bfe266044fb12cf7a12e2dbd` |
| Corrected runtime | 113 | `e175321d8eac0340d06cfe3b18792b87f4f3a34533b984d12a193c281db4a9f4` |

Outer [SHA256SUMS](SHA256SUMS) covers all files in this evidence directory except
itself. Source copies/ZIP entries and working/indexed Git bytes are checked before
the evidence commit; original native evidence is not normalized to pass checks.

## Remaining scope and acceptance

This is bounded S1 development evidence. Release hooks supply operator fuel/launch;
player fueling/UI, passengers, arbitrary mods, forced power loss, real clients,
remote operation and long load are not covered. Cargo/drop observations are limited
to touched chunks and loaded rockets, not an unbounded full-world scan. No save,
network, public API or asset provenance changed. No release tag was created.

ROCKET-03A/B/C's independent consumer coverage is complete within these boundaries.
ROCKET-04 and atmosphere/equipment, component, environment and satellite providers
remain unfinished. Complete G0-G9 and inherited acceptance are not satisfied or
human-approved; ADR-018 continues to defer full original-feature acceptance.
