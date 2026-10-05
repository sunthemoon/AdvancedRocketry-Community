# Station-light and destination-readiness hosted results

Date: 2026-10-06. Both runs are FAILED. No release or ledger acceptance.
The later [committed replay](RESULT-04.md) supersedes this record's pending
replay and current compile-only checkpoint; original results remain unchanged.

## Station-light source: 7ae88260

Source `7ae88260eaec44cb8692d82cd8d4545715efbddb`,
[run 37338384386](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37338384386),
attempt 1, successfully executes clean build, artifact audit and two DataGen runs.
Root parses 342 distinct JUnit XML suites /1,861 testcase elements /zero failures,
errors or skips, including the eight new lamp unit tests. First DataGen writes
788 outputs; the second writes zero. Both tracked/untracked checks are clean,
so the provisional lamp resource bytes match actual Forge generation.

Unfiltered GameTest completes 482 tests with three required failures:

- `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`:
  No rocket at Tau Ceti f.
- `lamppreservesboundarypriorityanddoesnotloadfarchunks`:
  Invalid, duplicate or over-capacity atmosphere boundary registration.
- `actuallootisoneselfitemwithseedednativeexplosiondecay`:
  Lamp loot identity/payload changed.

The original five lamp tests run in this cohort; no full lamp-native PASS is
claimed. Gravity's earlier failure is preserved at its own source/run, not
declared resolved because it is not among this run's failures.

Artifact 11357637175: 1,710,036 compressed bytes, SHA-256
`43d30333228e6fe6156066bd9bb661c00aadf0c5ad914be45652c235cfc5d422`.
[Complete retrieval receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-7ae882-attempt1-20261006-01/RETRIEVAL-01.json).

## Destination-readiness source: 37ef84c2

This cohort's tested source `37ef84c26d33d5f369478bd5060dc97b9dc7fbdd`,
[run 37338649321](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37338649321),
attempt 1, fails `compileJava` at the new GameTest's line 44: generic inference
for `TicketType.create` / `Comparator.naturalOrder`. This cohort supplies no
unit-test execution, artifact audit, DataGen or GameTest result. Later steps are
skipped; the always-run raw upload succeeds. Conditional JAR upload is skipped.

Artifact 11358002229: 9,002 compressed bytes, SHA-256
`6bdab33cf28f7583ff00943dd55e1f77cc9ae4875f25614b74242c4b11cd6f2e`.
[Complete retrieval receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-37ef84-attempt1-20261006-01/RETRIEVAL-01.json).

## Verification boundary and remaining work

Root's reviewed bounded collector exits zero for both distinct cohorts, validates
API source/attempt and archive digest/CRC, and retains logs/XML via receipt
mappings. Root rehashes all retained files with zero mismatch. Archive/JAR bytes
are not retained for independent container replay. Recorded build-side audits
must not be called independent JAR verification. The
[different-agent actual-raw review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-cohort-review-20261006-afb832/REVIEW-RESULTS-01.md),
SHA-256 `1637aa41abdd5eee8fe47788857c88100ae0b12cc240060b9e3018d428ddc361`,
independently verifies all 367 + 9 retained payloads, derives the XML/DataGen
totals and compares three native log streams with the same three-failure set.
It does not re-download/replay the ZIP/CRC or actual JAR container.

[Fixture corrections](FIXTURE-CORRECTIONS-01.md) preserve deadlines, test
selection, seeded/count/boundary assertions and original failure evidence.
The original Low empty-tag oracle finding and corrected successor review remain
separate. Exact reviewed corrections are published at
`3963c097909602ca058641ef62fb8e5fb05717b3`; bounded gravity diagnostics are
integrated/non-force pushed at `cfdcd8f546d7005b11c7bb1c8635eb3d1ee03339`.
At this checkpoint fresh committed replay was pending; its actual failed
terminal result is recorded separately in RESULT-04. No correction PASS is inferred.
No local full build/GameTest/server runs while C is below 10 GB. New scratch
is D-only; no world/build/ZIP copy is
created by retrieval. Full v1.8 implementation, physical machines, restart/crash,
real GPU/client/multiplayer, R-021 and G0-G9 remain open.
