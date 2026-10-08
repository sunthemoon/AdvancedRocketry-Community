# C18a-SEAL-INSTALLED-RUNTIME-02

Date: 2026-10-08. Implementer/integrator: Root. Base:
`80328a2d25c0c695cc9c8b09ee79910bfc458ec9`.

## Scope and authority

Continue the already accepted ADR-067 revision 1 detector leaf. Verify its
installed startup catalog through the actual registered item and native
`ServerPlayerGameMode.useItemOn`, using the existing external state-boundary
fixture and ordinary embedded connected players. This is development-hosted
verification, not packaged dedicated-server, real-player S2 or GPU V1/V2 proof.
No Claude assistance is used. Independent Codex actual-diff review is read-only.

The existing local compiled-catalog test is retained. A new adapter test must
not compile or install a local replacement catalog, replace the detector
runtime, use host implementation classes, or introduce another registration
event. It uses the fixture's existing public API registration and checks both
native hands, closed/open full-collision states, held data and reply recipients.
Record actual assertions and command outcomes without assuming success.

## Write scope

Root owns the new isolated worktree
`D:/GitHub/arce-v180-seal-installed-runtime-20261008`, branch
`test/v1.8.0-seal-installed-runtime`.

- NEW `src/adapterTest/java/io/github/sunthemoon/arceadaptertest/SealDetectorRuntimeGameTests.java`.
- This task and NEW `INSTALLED-RUNTIME-VERIFICATION-02.md` in this directory.
- NEW compact evidence archives for this task in this directory.
- Narrow integration records in `docs/status/COMPLETION-PLAN.md`,
  `docs/status/CURRENT_VERSION.md` and `docs/work/v1.8.0-implementation-log.md`.

External evidence belongs only to the new leaf
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/seal-installed-runtime-root-20261008-01`.
Reviewer evidence uses a separate new leaf and disposable checkout. Existing
worktrees, dirty main AGENTS.md and inherited untracked outputs remain untouched.
No other source, central registration/build, asset, provenance or ledger write.

## Not implemented or accepted here

No production behavior change, physical hatch writer/hook, recovery policy,
oxygen authority, persistent schema, public API or dependency expansion.
This slice does not finish all detector actor/lifecycle/query-order, custom-rule
precedence, recipe-unlock, reload/unload, packaged restart, multiplayer or visual
obligations. It cannot deliver a content-ledger unit or approve a Required Gate.
ADR-068 remains PROPOSED and R-021 OPEN; no O1/O2/O3 owner decision is inferred.

## Verification protocol

Commit the actual source candidate before hosted verification. Independently
review its diff and rerun applicable checks on that committed candidate. Run
Java 17 clean build with no build cache, twice DataGen followed by empty diffs,
the complete unfiltered GameTest server, ledger, provenance, strict repository
validation and whitespace checks. Retain errors/failures separately from test
counts; do not change assertions, timeouts or budgets to manufacture success.
Check C: and D: free space >=10 GiB before sustained commands and place process
TEMP/TMP/java.io.tmpdir in owned D: evidence. Preserve raw logs, XML, commands,
source/artifact identities and any corrected attempts.

Cleanup only new ended disposable outputs after resolved containment/ownership,
reparse and process checks, with one native PowerShell attempt. Never retry a
denied cleanup or modify sealed prior evidence. Package only reproducibility
inputs and evidence, not an entire reproducible source/build/world copy.
