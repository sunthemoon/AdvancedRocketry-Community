# v1.8.0 review continuation — verification

Date: 2026-10-03. Branch: `codex/v1.8.0-classic-content`. Base HEAD:
`cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6`. This evidence binds an
**uncommitted development snapshot**, not a fabricated implementation commit,
candidate or release. Version remains **IN_PROGRESS / IMPLEMENTING**.

## Completed scope

- C15b review round 2: no new actionable finding; 37 independent JUnit tests
  and 62 selected GameTests passed. Its previous native seam packet was audited,
  not independently replayed. [Report](../v1.8.0-c15b-surfaces/reviews/REVIEW-02.md).
- C15c review fixes: custom sky forwards and displays bounded lightning tint,
  honours the hide-flash option, scopes the cooldown weakly to a client world
  and resets on clock rewinds. Tau Ceti fallback and DataGen share the canonical
  six-profile set; the v1.8 provider still writes its two new profiles because
  earlier output providers are not registered during v1.8 generation.
- Owner-selected landing-ground radius 224 preserves existing rocket limits.
  Exact selector-centred rectangle tests and a 255-by-4-by-1 RocketBounds
  regression replace the erroneous square-footprint assumption. Independently
  enumerated 67,736 admitted rectangles give a conservative feature envelope
  of 218.728 blocks. Eight generated JSONs have ten intended radius changes.
  Independent final run: 38 JUnit and 42 selected GameTests passed.
  [Follow-up](../v1.8.0-c15c-worlds/reviews/REVIEW-02.md).
- ADR-063 revisions 4–6 and ADR-061 revision 7 accepted under the owner's
  no-unresolved-Critical/High/Medium authorization and explicit expansion choice.
- ADR-064 revision 2 accepted after two independent contract reviews and the
  explicit controller-owned-resource and existing-gas-giant nitrogen choices.
  This freezes requirements only; it does not implement any C16 machine.
  [Dispositions](../v1.8.0-c16-contract/review-dispositions.md).
- README/runtime description, planetary guide, provenance record and canonical
  progress documents now describe the implemented C15 scope accurately.

## Tests added or amended

New `FlashCooldownTest` has three world/timeline/interval boundary cases.
`SkyMathTest` adds bounded lightning-color and color-scaling checks.
`LandingGroundTest` adds the actual thin-rocket regression and exhaustively
checks selector-centred rectangles, chunk admission and diagonal feature reach.
Its filter boundary and both codec assertions remain exact. Resource tests
expect radius 224; profile and failed-reload tests require both Tau Ceti fallback
profiles. `ModMetadataTest` retains an exact full-description assertion.
No production test is removed, timeout increased, exception ignored, budget
relaxed or assertion weakened to obtain these results.

## Actual execution

Windows / Java 17.0.7 / Minecraft 1.20.1 / pinned Forge 47.4.10. Java 8 is
present on the default PATH; every Gradle command explicitly selected Java 17.
Gradle runs used the available offline cache. Root scheduled independent heavy
checks separately from its final full suite.

| Command/check | Exit/result |
|---|---|
| `gradlew.bat --offline clean build test runData runGameTestServer` (baseline) | 0; 1,441 JUnit /271 suites; 384 required GameTests |
| Same full command plus `--rerun-tasks` after initial fixes | 1; 1,445 JUnit /one stale metadata-description assertion; GameTests not reached; failure retained |
| Same full command after exact metadata expectation correction, before radius expansion was compiled | 0; 1,445 JUnit /272 suites; 384 required GameTests; not evidence for radius 224 |
| `gradlew.bat --offline runData` after the expansion | 0; exposed a removed-profile-writer integration mistake; the writer was restored before final testing |
| `gradlew.bat --offline runData` after the writer correction | 0; both Tau Ceti sky files restored byte-identically; eight intended generated-data changes retained |
| **Final `gradlew.bat --offline clean build test runData runGameTestServer`** | **0; 6m35s; 1,446 JUnit /272 suites, zero failures/errors/skips; all 384 required GameTests passed, seed 0** |
| `capture_runtime.py runtime-after.json runtime-start.json` | 0; all 2,524 runtime/build input paths, sizes and SHA-256 values unchanged across DataGen |
| `python -B scripts/validate_repository.py --require-approved-identity` | 0; 45 checks passed |
| `python -B scripts/validate_v1plus_planning.py` | 0 |
| `python -B scripts/validate_bootstrap_provenance.py` | 0 |
| `python -B scripts/validate_v180_content_ledger.py --require-accepted` | 0 |
| `python -B scripts/validate_v180_content_ledger.py --closure` | **1; 200 PLANNED content rows, 156 REVIEW assets** |
| `python -B scripts/validate_v120_machine_resources.py` | 0 |
| `python -B scripts/validate_v090_resources.py` | 0 |
| `python -B scripts/check_client_imports.py` | 0 |
| `python -B -m unittest tests.test_validate_v180_content_ledger tests.test_v1plus_planning tests.test_screen_generated_art tests.test_vanilla_derivation` | 0; 85 tests |
| `git diff --check` | 0 |
| `git diff --exit-code` | **1; intentional uncommitted changes**, not a clean-worktree/G2 PASS |

`runtime-start.json` was captured during final test compilation, before
DataGen; the first inline snapshot attempt failed in the Windows Python batch
shim, and the retained standalone capture script corrected that orchestration
error. No runtime source was edited during the final full suite. Hash equality
proves DataGen idempotence for this snapshot; it does not replace the required
clean committed candidate check. Full logs include intentional existing fault
injection diagnostics and deprecation warnings; they are not claimed error-free.

## Evidence and identity

`root-evidence.zip` retains all four root JUnit XML snapshots, Gradle logs and
exit records, baseline/final GameTest logs, Python checks, diff records, the
runtime manifests and capture/result scripts. `source-identity.json` binds the
changed non-artifact files; `evidence-files.json` hashes root ZIP members.
`checksums.txt` hashes all new ZIPs and the development build artifacts.

Independent raw evidence:

- `../v1.8.0-c15b-surfaces/reviews/round-02-evidence.zip`
- `../v1.8.0-c15c-worlds/reviews/rounds-01-02-evidence.zip` (original review,
  probes, annotation selection, fixed runs, failed first expansion run, final
  captured inputs and documentation-only supplement)
- `../v1.8.0-c16-contract/reviews/rounds-01-02-evidence.zip` (contract reports
  and independently compiled kernel probes, not C16 runtime tests)

Main development JAR SHA-256:
`a0082a3e5f3b1dfdc41567606a4bdcb05996bd47b24347efd266a4b375396f14`.
This artifact is not a release candidate and no tag/commit/push was created.
The pre-existing untracked user documentation bundle was not read, modified
or placed in an archive. No upstream source/asset, registry ID, save schema or
network protocol is introduced by these review fixes.

## Remaining scope, risks and Gate status

- C16a–d, C17a–c, C18a–d runtime work and C19 closure remain unfinished.
  Accepted ADR-064 does not turn their 200 PLANNED ledger rows into delivered
  content. Asset review must also resolve the 156 REVIEW rows.
- The expanded ground applies only to newly generated chunks. Explored Tau
  Ceti chunks keep the earlier terrain/plants, including the old 160 reserve;
  the selector still validates each site. No retroactive clearing is attempted.
- No actual full-size thin-rocket flight, multi-seed landing campaign or new
  packaged-server restart was executed in this continuation. Earlier S1
  evidence retains its original tested commit and radius; it is not relabelled
  as evidence for this snapshot. Dedicated GameTest sidedness passed.
- No V0/V1/V2, real-client lightning observation, artwork human acceptance,
  real multiplayer, reference-hardware performance, soak or full progression
  result was produced. Inherited acceptance gaps remain unchanged.
- **Required G0–G9 are not all satisfied.** No PASSED, READY_FOR_AUDIT,
  candidate, release or human Gate approval is asserted.

Next current-version work: implement the explicit C16a leaf tasks in the
[implementation log](../v1.8.0-implementation-log.md), independently verify
each vertical slice, then continue C16b–C19. Rollback must preserve the
pre-existing user bundle and historical evidence; worldgen rollback cannot
undo chunks already generated with the new radius.
