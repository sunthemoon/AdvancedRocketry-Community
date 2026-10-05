# C17b station-light source checkpoint

Date: 2026-10-05. Status: implemented-unverified, not ledger delivery.
The containing source/resource commits are development checkpoints for hosted
execution. Only a later result bound to the actual pushed commit can establish
compiler, JUnit, DataGen or GameTest outcomes.

## Reviewed implementation

The [independent source review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17-station-light-source-review-20261005-37ad6b/REVIEW-01.md)
releases the four author sources, two author records and six Root central
registration/DataGen diffs. Root imports the exact six author postimages via
`apply_patch`; all hashes match the reviewed `OWN-FILES-01.json`.
The ordinary light has no BE, owner, active tick, energy or network channel.
Eight JUnit and five GameTest methods are authored but have not executed.

## Provisional resource preparation

The original unexecuted helper has a Medium final-LF incompatibility. A separate
two-line correction is independently reviewed; the original remains unchanged.
Root runs only corrected `prepare_resources02.py`, SHA-256
`7475eabf1fc88c076c5328d82f1700f5844277be5ef24be58f59b2b0927fe552`,
with provider pin `83b887af7bfc93b1f834d4f0bfe7a009aef13a2762b0139935471a56b0406d11`.
Actual Python exit is 0: seven new outputs and three existing shared changes.
The three shared preimages match fixed `5f1cbefc30d59a35eed80023697a1f4a0c4687fc`;
only one bilingual key and one pickaxe entry are added.

[RESOURCE-DRAFT-02.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17-station-light-root-20261005-01/RESOURCE-DRAFT-02.json)
records every path, size/hash, preimage, grid, tint and Python zlib 1.3.1. Fresh
`prepare-resources02.stdout`, `.stderr` and `.exit` retain the actual command
result. Its status is explicitly `PROVISIONAL_NOT_FORGE_DATAGEN`. No Java,
Gradle or native process ran; Python compression cannot prove Java byte equality.

## Remaining verification

Independent actual-output review, fresh committed clean build/JUnit, two actual
DataGen runs with clean tracked/untracked output, unfiltered GameTests and
artifact/API checks remain required. The two previous hosted failures predate
this light and are not lamp results. Survival-player acquisition/break, ordinary
restart and real GPU/multiplayer evidence remain separate. R-021, shared writers,
typed propulsion, the PLANNED ledger entry and G0-G9 are unchanged.
