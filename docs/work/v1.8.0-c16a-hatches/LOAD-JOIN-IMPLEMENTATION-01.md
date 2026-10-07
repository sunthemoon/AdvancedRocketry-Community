# Classic owner LOAD and native save integration

Task: C16a-03b-LOAD-JOIN-01. Status: IN_PROGRESS. Integrator: Root.
Source checkpoint: `60f1564528de782fa15269884fd1355d3c0b9ff1` with the
39 postimages identified in the independently reviewed owner successor02 map.
No runtime activation, delivery marking or Required Gate approval is made here.

## Limited technical disposition

The user's recorded authority is: "授权无未解决 Critical/High/Medium 的审核定稿继续实现；重大语义调整仍另行确认".
Root adopts the private outgoing-checkpoint seam and its successful LOAD join
amendment for implementation only. The original seam's missing recording join
is superseded by the separately reviewed amendment, not by a status-only test.
The independent amendment report is
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/observer-load-join-independent-review-20261007/REPORT-01.md`,
SHA-256 `bc3bbdd3d0e3d9e560afb568c138b8a68b3a1bc4fb01030f945b53910e3b8201`.
It reports no introduced Critical/High/Medium/Low in the proposed amendment;
real source, provider, lifetime and native save verification remain required.

The adopted amendment is `PROPOSED-AMENDMENT-01.md`, SHA-256
`a02998ec2618106a2709c8e531b6d2cb8e767bad5a444b1a3f4dac0e1886bc87`.
The retained comparison proposal is `PROPOSED-SEAM-01.md`, SHA-256
`49124a456f55f5da95cb22a35a6959af9e5a3b7bb5e1d732529058aa0309a475`.
This disposition changes no public API, persisted ID/schema, quota, resource
semantics, sticky save-denial rule or R-021 acceptance. It cannot supply a
missing placement expectation, FULL witness or successful guarded LOAD.

## Disjoint source ownership

- Owner worker: existing `arce-v180-guarded-owners-20261007` worktree; only
  `ClassicControllerBlockEntity`, `ClassicHatchBlockEntity`, `GuardTicket`,
  `ClassicFamilyService`, `ClassicOwnerState` if needed for availability, their
  focused tests and the worker's own task record. Implement the actual two-LOAD
  completion and private outgoing comparison joins. All other successor02
  postimages remain unchanged. No fake Root provider/service/block joins.
- Comparison worker: separate `arce-v180-checkpoint-compare-20261007` worktree;
  only `ClassicRootBundle`, new `ClassicNbtExactComparison`, focused
  `ClassicCheckpointComparisonTest` and its own task record. Its RootBundle
  starting postimage is the exact successor02 file, not a guessed older body.
  No owner, guard, registry or world writes.
- Root: `arce-v180-classic-central-20261007` worktree; real chunk observation,
  typed save-protection bridge, Level/event lifecycle and subsequent physical
  block/registration integration. Main status, contract records and commits
  remain Root-exclusive. AGENTS.md is user-owned and excluded.

These are original source changes, not upstream imports. Workers may read
adjacent actual source and accepted contracts; only Root stages/commits/pushes.
No shared worktree write scope overlaps. Owner and comparison code must be
independently reviewed before verified integration. Native dependencies are
implemented, never replaced by test stubs or synthetic authority.

## Checks and non-goals

Check actual diffs, private witness fences and exact managed-root equality;
run bounded cached Java checks only when their real dependencies are available.
C: has less than 10 GB free, so full local Gradle/GameTest/native/client/server
runs are prohibited until the existing space rule is satisfied. New temporary
outputs belong under `D:/GitHub/ARCE-Task-Evidence/v1.8.0/`; ended disposable
outputs are cleaned only within each creator's checked literal directory.
Hosted clean build, data generation/no-drift, GameTests and native restart are
still required. The latest full CI's Tau Ceti f failure is not waived.

This task does not implement later-version content, close C17/C18 product
choices, change charged-power conservation, install dependencies, relax tests,
reset save denials or mark any version Gate passed.
