# Bounded classic chunk metadata checkpoint

Task: C16a-03b-CHUNK-METADATA-01. Status: independently verified in the
data-only source/unit scope; integrated full/native qualification remains open.
Integration base: `bae82345381164db80bf9741a5d9a51b8618d6ed`.
Published source: `3e2f6f1ba96508305b78cdb27d95ebbeabad6a38`, normally committed
and pushed by Root (actual tool `c9691f`, exit 0), with exact remote equality.
Source and integration reviews precede publication; the first two Java
observations below remain explicitly pre-publication development snapshots.

## Scope and preserved boundaries

The new package-private `ClassicChunkRecords` projects the native
`block_entities` metadata into detached immutable signed-coordinate/type
records. The hard physical count limit is checked `256 * height` before any
entry traversal. Exact native tag types, chunk/build-height membership and
duplicate positions, including foreign entries, are validated. Malformed
input yields a refusal with no partial expected set; a valid empty native
list is distinct from missing data. The two managed BE IDs and three recognized
wrong-ID roots are the already frozen family identities.

No NBT value is copied, decoded, normalized or retained. A managed ID with a
missing or unreadable owned root is only metadata, never FULL or resource
admission. Supplying the actual immutable Level height and maintaining native
observation lifetime are the future consumer's responsibilities.

These are original NEW/MIT files, not upstream copies. No schema, public API,
registry, network, configuration, recipe or asset changes occur. This checkpoint
does not register a capability, service, event, block, owner or save writer.
The separate `ClassicLevelServices`/`ClassicFamilyEvents` candidate, delegated
owner cluster, outgoing-checkpoint comparison and physical hatches are excluded.
It does not change save refusal, R-021, creative conservation, content delivery
or any Required Gate.

## Exact source and independent assessment

| File | Bytes | SHA256 |
| --- | ---: | --- |
| `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/classic/adapter/ClassicChunkRecords.java` | 3,634 | `f52da1bd91c6c621731ac257b405115a07a034076e3e14e7d2154250e59d8d19` |
| `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/machine/classic/adapter/ClassicChunkRecordsTest.java` | 7,174 | `4609a228fdfc6611b5db9980d1c33855e0e09f69c8e568533aa22adbf0846d77` |

The [final independent review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-chunk-records-source-review-20261007/REVIEW-01.md)
is sealed at SHA256
`ae7b83561bdaa830bc07fc50d9f5d3686854eadd6a6cbcb98f29060a3c867873`.
It establishes no Critical/High/Medium or code finding in these two files. Its
one Low is an ambiguous no-Java sentence in the separate central task record,
not a code issue. Root clarifies that sentence after release: the uncompiled
service/event pair is named explicitly, while the metadata-only evidence and
no-clean-build/native limits remain. Neither service/event source is integrated.
The two released source hashes are unchanged by this documentary correction.

## Actual tests and remaining checks

`ClassicChunkRecordsTest` adds ten Jupiter subjects covering managed IDs and
unreadable roots, native list/entry shape, wrong-ID roots, coordinate coercion,
duplicates, chunk/Y boundaries, the exact 256/257 physical limit, immutable
detached output, signed-coordinate/overflow edges and foreign/null tags.

- Root's [original cached replay](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-chunk-records-check-20261007-01/RESULT-01.json)
  has actual javac/Jupiter exits 0/0, ten passed and zero failure/abort/skip or
  container failure, with 44 named inputs unchanged.
- The [independent cached replay](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-chunk-records-source-review-20261007/RESULT-01.json)
  has fresh javac/Jupiter exits 0/0, the exact ten source method identities
  executed and passed, no failure/abort/skip or container failure, with 45 used
  inputs unchanged. Its final union postcheck retains 62 unchanged named inputs.
- Root's subsequent [fixed-commit replay](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-chunk-records-fixed-check-20261007-01/VERIFICATION-01.md)
  runs after publication, compares both source files with their committed Git
  objects and passes the exact ten subjects with javac/Jupiter exits 0/0 and
  46 named inputs unchanged. It is separately sealed, with verification SHA256
  `95d8e01cc4f28bd03efacaa8e05650ca4773c689dc307bd00bdd5f82ff726d4d`.

All three are bounded direct Java development runs; the first two use
uncommitted postimages and the third the published commit. Only the two sources,
unchanged harness and existing cached JARs are used. These are not a clean build,
GameTest, native server, save or restart test.
Each compiler/JVM uses 256 MiB and two CPUs, a 120-second deadline and bounded
streams; temporary/home/crash outputs are under its own external D: evidence
directory. No Blocks bootstrap, project output or new dependency is used.
The reviewer's comment-sensitive failed preflight is retained; it launched no
Java child and does not replace either actual successful run.

Each Root replay removes only its own ended ten class files and eleven empty output directories;
the reviewer removes only its ended ten class files and nine empty class/temp
directories. Raw streams, results, source and cache files remain. No C: or
other-agent cleanup, sealed-member deletion or physical disk-reclaim claim occurs.

The actual raw capture/FULL expectation consumer, installed owner comparison,
unload/final-save retention, quiesce/stop/restart and physical machines remain
unfinished. The [new full regression](../v1.8.0-ci/RESULT-18.md) is FAILED at the
required Tau roundtrip fixture. Its 355 XML suites /1,964 actual cases /0FES
include all ten new metadata cases passing; both DataGen/worktree checks pass.
All 509 GameTests complete with one required failure. These counts belong to
`3e2f6f1b`; the earlier resource-source cohort remains historical at `8d521406`.
No ledger delivery or G0-G9 approval is inferred. v1.8 remains
IN_PROGRESS / IMPLEMENTING.

The exact-source hosted run 37541636327 /attempt 1 /job 112535778550 was observed
in progress at 2026-10-06T22:37:02Z. That historical metadata-only observation is
superseded by the completed FAILED observation at 2026-10-06T23:00:03Z and Root's
402-input raw audit. The new terminal result does not rewrite earlier bytes,
establish independent build-JAR verification or qualify any new native consumer.
