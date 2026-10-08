# C16a native placement LOAD-order qualification

Date: 2026-10-08. Status: verified test-side development qualification;
v1.8 remains IN_PROGRESS / IMPLEMENTING. This does not accept physical hatch,
save policy, ADR-068, R-021 or any Required Gate.

## Scope, identity and disposition

The [task](NATIVE-LOAD-ORDER-TASK-21.md) adds two required GameTests for native
chest placement through ServerPlayerGameMode.useItemOn in SURVIVAL and CREATIVE.
They observe exact native player/game-mode abilities, installed owner identity,
source stack identity/count/tag, both fresh queues, empty full-metadata native
serialization before a tick, and identity/queue absence after a subsequent
natural tick. Each fixture owns two loaded BE-free cells, one plain native
ServerPlayer and an EmbeddedChannel; terminal outcomes restore cells, remove
only the owned player and release the channel. Queue reflection is read-only
and bounded to 4,096 entries. No direct onLoad call or queue write is used.

| Identity | Commit |
|---|---|
| Base | `355766c6841137c8e8b5977be1fc3e25bc074fa7` |
| Original fixture candidate | `673ef97f4f1c19d1bfa6497b7709770e1d09aa28` |
| Actual corrected/tested source | `3939dd55f8f020a33d7f1c7c5d55bd6caa237c7b` |
| Integrated, normally pushed source | `79cb3e91c84595672b8f4de8b6a4d8b85cf3337a` |

Complete src tree `3a9ae41d52b04d47db0d0f5b29ce97803757aaef` and seven Gradle
inputs match the integration: build.gradle, settings.gradle, gradle.properties,
both wrappers and both gradle/wrapper files. This is equality, not a rerun of
the merge SHA. Only the new GameTest class differs under src from the base.

The pinned mapped Forge 47.4.10 JAR is SHA-256
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`.
LevelChunk.setBlockState dispatches a fresh owner to addAndRegisterBlockEntity;
when isInLevel is true, that method installs it and directly invokes onLoad at
bytecode offset 41. Whole-chunk registerAllBlockEntitiesAfterLevelLoad queues
owners through Level.addFreshBlockEntities instead. The official
[Forge Level patch](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.x/patches/minecraft/net/minecraft/world/level/Level.java.patch)
is a bulk-queue cross-check, not the pinned placement evidence. Raw selected
bytecode and bounded member hashes are retained in both packets.

Tests do not instrument an onLoad callback. The synchronous conclusion joins
the pinned native control flow and the exercised ordinary in-Level placement,
not queue absence alone. The unchanged ClassicHatchBlockEntity.prepareLoaded
and recordCompletedLoad open two fresh LOAD tickets during one invocation;
these are not two native callbacks or world ticks.

This qualifies only LOAD-BINDING-20's U2 scheduling concern for that route/version.
The draft at `932600d9` is unchanged, unmerged and unadopted. Successful hatch
LOAD authority, provisional eligibility, availability fencing and callback-free
terminal binding are not proved. The [two Medium prerequisites](PRIVATE-OPERATION-REVIEW-01.md),
U1/U3-U7 and O1/O2/O3 remain open. No registry/schema/ID, network, hook/dependency,
source cap, guarded save consumer, writer or physical hatch is activated.
No upstream asset or native implementation is copied into mod source.

## Actual verification and preserved failures

Java 17.0.7, Gradle 8.8, offline caches and task-local TEMP/TMP/java.io.tmpdir
were used. Both drives exceeded 10 GiB before sustained commands. Separate
Root and read-only Codex snapshots run at the exact corrected SHA.

| Command/result | Root | Independent Codex |
|---|---|---|
| `gradlew.bat clean build --no-daemon --offline` | exit 0; :test executed | exit 0; also --no-build-cache, all 20 tasks execute |
| JUnit XML | 378 suites / 2,179 actual testcase nodes / zero F/E/S | same |
| `gradlew.bat runGameTestServer --no-daemon --offline` | exit 0; all 548 required tests pass | same |
| `gradlew.bat runData --no-daemon --offline`, twice | exits 0 / 0 | same |
| `git diff --exit-code`, after each DataGen | exits 0 / 0; empty | same |
| `python -B scripts/validate_v180_content_ledger.py --require-accepted` | exit 0 | same |
| `python -B scripts/validate_bootstrap_provenance.py` | exit 0 | same |
| `python -B scripts/validate_repository.py --require-approved-identity` | exit 1; 44 pass / one inherited link failure | same, byte-identical log |
| `git diff --check` | exit 0 | same |

Both corrected logs include the new two-case batch, 62 unwaived ERROR headers
and zero FATAL. Overall runner exit 1 reflects strict validation, not native
failure. Original and corrected commands, exit codes, timestamps and full logs
are retained. Existing tests, assertions, timeouts and budgets are unchanged.

At the original candidate, both Root and independent full native cohorts
complete 548 tests with exactly the two new cases failing: helper mock-player
login calls Channel.pipeline with a null Connection.channel. Placement
assertions are not reached. Each original log retains 64 ERROR / zero FATAL.
The corrected fixture owns its channel and plain ServerPlayer before login
callbacks, removes the helper's unconditional creative override, and adds mode
consistency assertions. This preserves an independently reproduced fixture
defect, not a negative LOAD observation or product defect. The reviewer's first
CMD launcher fails before Gradle because of Windows path spelling; that failed
attempt and zero-test artifacts remain separate and retained.

Terminal cleanup branches are source-reviewed, not separately failure-injected.
Restoration booleans/equality are not asserted, so no broad world-restoration
guarantee is inferred. These are embedded test connections, not genuine C2S
packet, real client or V2 evidence. Historical product failures are not erased
or uniquely explained by these cohorts. No Claude is called.

Independent actual-diff review finds no unresolved introduced C/H/M/Low in
the corrected source. The original Medium fixture reachability defect is
independently reproduced and resolved, not removed from the report. Root reads
the final report, verifies all 70 independent payload hashes, reparses archived
JUnit/native terminals and confirms all three corrected JAR hashes match:
runtime `dd93cb968430eba12fb13539bbdfb29e943af1f16a6b6a8ebb3257a14d9a6b87`,
API `aa9317c6d2a5dfc71f736f7a4de4dca15f6742d6649dd05fa5b3f457fe9ade4b`,
sources `ed57d4d0923c1571b421979a8e6068165b70dc048b755b8d8bdd8faf1c27d2a9`.
These are development outputs, not packaged or release approval.

The first metadata audit identifies an introduced Medium in the new historical
plan excerpt: redirected Windows stdout introduces 465 replacement characters.
Only that new archive section is restored from pinned Git UTF-8 bytes; the
original failed review/collector and sealed source packets remain unchanged.
The correction requires separate archive/input comparison before publication;
it does not rerun or alter source, native tests or Gate claims.

## Evidence and remaining obligations

- [Root retained packet](NATIVE-LOAD-ORDER-ROOT-21.zip): 48 entries, 1,224,310 ZIP bytes;
  SHA-256 `bde1890791dcc451133654d0e9a4086cb92969b9d66ddf400df3b0cc2718bbc9`.
  Inner manifest SHA-256 `76508f39326eaee5cb0c80540c94ee01882f24fa19d50eab38ce923322488534`.
- [Independent source review and retained packet](NATIVE-LOAD-ORDER-INDEPENDENT-21.zip):
  72 entries, 1,241,073 ZIP bytes; exact original ZIP SHA-256
  `99f4aa37dd615a2f169c5889a1ae3ea2f0cfb17c969ee28a5c643b8d5196def9`.
  Inner JSON manifest SHA-256 `d0416e565df6026939fe758513f03892e9039ab57f244d939c38a1c77dcb8600`.

Both ZIP CRCs, entry sets and every copied payload match the sealed originals.
Combined uncompressed retained packets are 23,472,964 bytes, below 100 MB;
each file is below 50 MB. The independent ZIP includes 70 payloads plus its
JSON manifest and SHA256SUMS controller. Two initial packaging collectors
omit a controller/manifest checksum row and fail assertions; their scripts
and exceptions remain in the separate disposition record. The corrected
collector validates all rows/controller bytes and does not alter either sealed
packet or overwrite the already-created Root ZIP.

Original packet locations, unchanged:

```text
D:/GitHub/ARCE-Task-Evidence/v1.8.0/hatch-native-load-order-root-20261008-01
D:/GitHub/ARCE-Task-Evidence/v1.8.0/hatch-native-load-order-independent-20261008-01
D:/GitHub/ARCE-Task-Evidence/v1.8.0/hatch-native-load-disposition-20261008-01
```

Each new packet preserves original payload bytes and excludes full source,
build/server/world/TEMP copies. Root's one native PowerShell cleanup succeeds,
removing only 272,137,479 inventoried logical bytes in this task's ended build,
local .gradle, run-data and both TEMP directories. Prior refused cleanup targets,
other agents, global caches, source/prior worlds, old sealed packets, user
AGENTS.md and inherited untracked work are untouched. Inherited cleanup debt
is not thereby closed. The source worktree remains. Independent cleanup also
succeeds once, removing only its two ended clones and three TEMP directories:
2,808,916,827 logical bytes. No new disposable debt remains for this task;
logical sizes are inventories, not measurements of physical space reclaimed.

R-021, both physical admission prerequisites, uncertainty/coverage/source
policies, Proto/generated origin, final save/disposal/restart, crash atomicity,
prior-world migration, installed continuation, V1/V2, performance, progression,
license/content closure and all Required Gates remain open. The ledger stays
186 PLANNED /154 REVIEW assets. Continue the [C16-C19 plan](../../status/COMPLETION-PLAN.md),
starting with coupled authentic placement/outcome and provisional LOAD/save/
terminal bindings, not later-version implementation. No release tag is created.
