# C16a-S1-GUARD-OBS04: bounded native chunk state observation

Date: 2026-10-04. Status: IN_PROGRESS. Owner and sole integrator: Root.
Baseline: `85afa4139f74dd71f6963596b0d498ac6a0ebcfc`.

## Outcome and scope

Capture the actual fixed overworld chunk (11, 11) save flag and visible-holder
debug state after the existing false loaded predicate, without reacquiring it.
This diagnostic distinguishes additional runtime conditions; it does not
substitute for the actual Unload event, live restoration or clean restart.
The three preceding native cohorts remain FAIL.

The opt-in console-only command has no coordinate arguments. It reads public
`ServerLevel.noSave()` and `ServerChunkCache.getChunkDebugData(ChunkPos)` on
the server thread. Debug text is escaped into at most 256 ASCII bytes with an
explicit truncation flag. The harness sends at most one additional state query
per removal cycle within its unchanged total 60-second unload budget.

Root's write scope is the existing unload-observer class, one pure text helper,
its unit tests, the lifecycle script/tests, this task and current progress docs.
Workers inspect the actual fixture/primary implementation or independently
review the resulting diff; shared-checkout workers do not write source.

## Dependencies and non-goals

Depends on the existing-policy native fixture and copied-spawn setup committed
at `f945dc1c33f09199da9d47663e891acf1cf4a475`. No source/asset import is used.
R-021 and the explanatory ADR proposal remain open; this changes no save policy.

No chunk loading, ticket mutation, future wait/join, broad debug dump, arbitrary
position, observer-event fabrication, new GuardTicket/writer, native codec,
first-save admission, resource or inventory mutation, recipe/registry/network
change or relaxation of deadline/restoration/byte assertions is in scope.
Normal gameplay registers no command unless release-test hooks are enabled.

## Acceptance and verification

- Pure tests cover exact cap, control/quote/backslash/Unicode escaping,
  truncation boundaries and input handling.
- Python controls verify fixed command, bounded exact state-line parsing and
  fresh evidence capture; old state or holder absence is not event proof.
- Independently review the actual source diff and public non-loading API use.
- Run clean build/test/DataGen, generated diff and required GameTests with
  Java 17/Forge47.4.10 and process-local D temporary directories.
- Run the strict copied-world lifecycle probe with the pinned new artifact.
  Preserve actual failure and raw state if the lifecycle remains incomplete.
- Verify source-world/library/artifact preservation, stage exact owned files,
  commit and non-force push verified phases. No version Gate is inferred.

New external helpers/logs/results use
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/guard-observation-04/`.
Historical cleanup failures and source worlds/sealed originals are preserved.

## Source review checkpoint

The independent five-file review is frozen with no introduced Critical, High,
Medium or Low findings. Its report is
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/agor-2b74c058e1/REVIEW-01.md`, SHA-256
`302af9f958a9d03eb97414bed490ddd3bf9e93b2372d146ae6341b5c57cb7b34`.
The internal manifest SHA-256 is
`5ef38ae5b7480529402d0b274a91fa984a56857778d5f8bf38fc6a07f07e4721`.
The reviewer independently reran 120 focused Python cases and eight controls,
and compiled/ran only the pure formatter with Java 17 (1,035,790 assertions).
These qualify source-only integration. Full committed build and strict native
execution remain required; no preceding native failure or Gate is closed.
