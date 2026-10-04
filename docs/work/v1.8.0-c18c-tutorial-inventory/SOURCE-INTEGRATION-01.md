# C18c-01 reviewed source integration

Date: 2026-10-04. Integrator: Root. Status: IMPLEMENTED_UNVERIFIED.
Base: `73fc017ac3b121b34216e660e66b0cb583ff289a`.

## Scope and independent review

The six frozen author postimages and two reviewed central registration lines
are integrated without semantic changes. Six stable inventory-only tutorial
advancements and twelve translations per language are generated. Existing
language keys and all unrelated named inputs remain unchanged. No source asset
is copied, recipe reward is added, ledger unit is closed or Gate is approved.

Independent actual-source report:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/atis-d0836db7f9/REVIEW-01.md`, SHA-256
`969f05acd0b157f2f195ba08c24d22385bde9ebe8c85c66275c3f9da5f720681`.
It records zero introduced Critical/High/Medium/Low findings for these six
files and two central lines. Independent primary/static and standalone model
controls do not substitute for registered Forge/native execution.

## Actual integrated development check

Root ran the following on the reviewed uncommitted integration, with Java 17,
offline Forge dependencies, two workers, Xmx2G and process-local D temporary
directories. This is an intermediate development check, not a clean-build
artifact or a commit-bound full regression cohort.

```text
gradlew.bat --offline --no-daemon --max-workers=2
  -Dorg.gradle.jvmargs="-Xmx2G -Djava.io.tmpdir=<D task tmp>"
  test --tests '*.ClassicInventoryAdvancementsTest'
       --tests '*.ClassicInventoryAdvancementResourcesTest' runData
```

Actual exit 0 in 42.175350 seconds. The copied raw XML reports 12 tests in two
suites, zero failures/errors/skips. Integrated v1.8 DataGen has 777 files: six
new advancement JSON files, and exactly twelve added keys in each of the two
existing languages. Old translations are unchanged. The exact six author
postimages remain unchanged; two central files each have only one added line.
The resulting named-input manifest contains 3,009 inputs, not the whole repo.

Evidence directory:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-integration-01/`.
Raw command/log/result, copied XML, JSON/language postchecks and
`C18-REVIEWED-SOURCE-MANIFEST-01.json` are separate immutable files there.
All new helper output and TEMP/TMP/TMPDIR/Java temp use the project-parent D
directory. Global environment/registry settings are unchanged.

## Open verification and native-draft finding

Three registered inventory-listener GameTests are compiled but not yet run by
this checkpoint. Fresh committed clean build, complete JUnit, repeated DataGen,
all required GameTests and packaged two-run save/load remain to be executed.
Real-client V1/V2 and the six whole-unit ledger obligations remain separate.

The first native leaf draft was independently reviewed separately from this
source. Its fixed SHA-256 is
`5f21c97023477d762bb317d56c43083756abf8d3f790bb5601090806f844a02a`.
The report at
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/atnc-b8773371d9/REVIEW-01.md`, SHA-256
`3cab6768b1ae36d2a228cf5921abdf9df2ef57a5ad43a5da2223ea7f2aa7521d`,
finds one Medium: fresh tutorial progress does not prove ownership of an
offline fixed UUID's pre-existing vanilla files. This is a source-backed
contract counterexample, not observed native loss. No fixture is implemented
or enabled under that draft. A versioned ownership correction and independent
review are required before a test-only join/save hook may be implemented.

Current v1.8 status remains IN_PROGRESS /IMPLEMENTING; 186 PLANNED units,
154 REVIEW assets and G0-G9 remain open. R-021 and guarded save-writer admission
are unaffected by this inventory-only source integration.
