# Raw root native-fidelity source checkpoint

Date: 2026-10-06. Scope: the four existing files assigned by the independently
reviewed [task](RAW-FIDELITY-TASK-01.md), not the physical hatch/controller or
shared save lifecycle.

## Published source and design

Root commits the exact reviewed postimages as
`070288b6b6d00801282b2d9209ac6a959f1c46da`, based on
`35a146fbbe1f2de94160f82307d041d2cd26e472`. The normal merge and non-force push
publish `a34de0ad5edb0a2b3efe6ad2bb76c17e40fafc43`; the remote SHA is verified
in the [publication receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/raw-fidelity-root-publication-20261006-01/PUBLICATION-01.json).

`ClassicRootBundle` preflights every managed root and the aggregate projection
with the unchanged structural budgets and native-lossless `BoundedNbt` check
before any copy. `ClassicNbtShape` refuses null native array backing, compound
keys and list children before dereference. Unsafe whole bundles remain borrowed
and reject before destination mutation; they are not normalized or truncated.
Canonical NaNs, signed zeros, infinities, existing roots/limits and raw-preservation
rules remain unchanged. No new schema, public API, registry ID, persistence
writer, GuardTicket consumer, save policy or R-021 disposition is introduced.

Package prefix: `io/github/sunthemoon/advancedrocketrycommunity/`.

| Source suffix | SHA-256 |
|---|---|
| `src/main/java/.../machine/classic/adapter/ClassicRootBundle.java` | `024d8618cd5dd0836c7e4bb72dd1b936c24d6b436bd4a9bade0960e79a0c9718` |
| `src/main/java/.../machine/classic/adapter/ClassicNbtShape.java` | `ae0bd49352964407cdd0c81f62a5b96e48e7036ab9fd949fb3fc0064950c2680` |
| `src/test/java/.../machine/classic/adapter/ClassicRootBundleTest.java` | `1812564a874fe94e2550e0ad3a3569cea3e364d678e1e332495a2fd738ba3ea8` |
| `src/test/java/.../machine/classic/adapter/ClassicNbtShapeTest.java` | `b149816e860dc7376390076ac570ec05dade7f8767060699ff147f6ff4e912ae` |

## Actual review and development tests

The [author handoff](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c16a-raw-fidelity-author-20261006-c18-4cd703/HANDOFF-03.md),
SHA `9c739ea62488f5ddfeed76daee84da7d121ad05dff1d19ccd2feede5a7a0f2d0`,
and [different-agent actual-source review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c16a-raw-fidelity-source-review-20261006-6b10ea/REVIEW-01.md),
SHA `e826e44a2861473037feb09417c893052cdd89744e2cc88739199040b009d786`,
bind these exact postimages before their subsequent commit. The reviewer finds
no unresolved introduced Critical/High/Medium/Low in this four-file delta.
These are development replays, not pre-existing fixed-commit native evidence.

Both separately granted cached Java 17 compilations and Jupiter executions
exit 0, each running **75/75** tests with zero failures, aborts, skips or container
failures. The cohort is RootBundle 19, Shape 13 and unchanged ordering/bytes/hash
8/20/15. Twelve new subjects are added; all original 20 RootBundle/Shape bodies
and the other subjects are retained without relaxed assertions or budgets.
One unchanged modified-UTF negative control emits an ERROR/UTFDataFormatException
while passing; these are not no-ERROR native-server passes. Earlier candidate
and reviewer-tool failures remain in their original records.

The new [complete hosted workflow](../v1.8.0-ci/RESULT-09.md) has completed
with failure. Root independently audits its raw XML and terminal records:
fresh clean build executes 1,895 JUnit tests /0FES, including all 75 named
helper subjects above. DataGen/repeat trees are clean, but two required GameTests
fail. Independent source review and these tests do not close physical persistence, conservation,
runtime delivery, the content ledger or any version Gate.

## Temporary output disposition

Root normally retires only the clean merged sparse source worktree, preserving
the branch/history. The [retirement receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/raw-fidelity-root-publication-20261006-01/WORKTREE-RETIREMENT-01.json),
SHA `c91688c002e091db3fb60e8281004505fe4e49c66e95c859a766ef818196b73f`,
records exit 0, verified absence and 2,904 regular files /10,768,401 logical bytes;
physical reclaimed space is unmeasured. Author/reviewer compiled outputs and
old policy-refused cleanup targets are not removed by that source retirement.
The reviewer's separate [cleanup outcome](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c16a-raw-fidelity-review-cleanup-20261006-b409a7/REPORT-02.md)
records a tool-policy refusal before the native removal body, with no OS exit
or deletion: 29 class files /131,810 logical bytes and 14 directories remain.
There is no alternate mechanism or policy change to remove those refused targets.
Thin logs, results, hashes and original failures remain available.
