# Laser save-fixture source handoff

Status: READY_FOR_REVIEW, uncommitted and runtime-unverified.

## Scope and design

Base `adcd46d2300b74be60f0621ce1b76598f233dce8`, isolated branch
`test/v1.8.0-laser-save-fixture`. Only the first LaserTarget GameTest and its
dedicated private observer/cleanup support changed. The three task records are
new. Production save routing, schemas, public watcher and other six tests/shared
helpers are unchanged. Root remains the sole integrator/committer.

The placement call performs one ordinary target server tick, then strictly checks
AWAITING, no root record, inactive and no exact save observation before yielding.
The 25-tick checkpoint accepts either an unregistered AWAITING/inactive target or
an exact registered record backed by the observed serialized target. It does not
treat elapsed time as evidence that no native save happened. The later explicit
serialized Save and OK/active assertion remain, and also require that correlation.

The Save observer admits exact Level identity, chunk, registered BE type, native
UUID tags, coordinates, saved owner, BYTE false frozen flag and the existing
persisted-reader non-frozen/empty-transit result. It stores only a boolean and
first observation tick, not observed NBT/events. Admission is bounded to 1,024
entries, at most two passes; overflow provides no evidence and cannot pass the
registration oracle. Save posting precedes native writes: this is not disk
durability or restart proof.

Original protection, extract-only capability, 7 minus 2 equals 5 drops, retirement,
returning-copy freeze, inactive copy and no-reregistration assertions are retained.
The original 25-tick delay and 300-tick timeout are unchanged. Cleanup replaces the
old blanket nearby-item discard with identity-delta references from the owned
target's synchronous native removal, bounded to 16 nearby items. It does not
remove pre-existing items or reset the shared root.

Setup exceptions close immediately while retaining the original exception;
sequence failure/timeout closes through the dedicated one-test AfterBatch hook.
Owned ServerStopping runs cleanup at HIGHEST before the ordinary Endgame stop
handler disables actions. Close is idempotent; nested finally blocks unregister
both consumers and clear the single static fixture context even if world cleanup
throws. Loaded-chunk and exact BE-object checks prevent cleanup from replacing
another target or forcing a chunk. Only the owned target buffer is emptied during
failure cleanup. Native cleanup exceptions remain failures, not suppressed passes.

## Source identity and checks

Java postimage: 34,283 bytes, 619 lines, SHA256
`b06b800fee5994bec3da8af8278337c18bf2c466e53400d5bbffa0065f31fb1f`.
The single-source diff is 256 insertions / 55 deletions, including indentation of
the original first-test sequence inside setup protection. The class exceeds the
500-line recommendation because it retains seven existing test cases and adds
one 142-line private fixture, not production/domain responsibilities. It remains
below 800 lines; no broader test framework was added.

Actual checks: `python -B check01.py` exit 0 (13 source checks + 17 Python finite
specification controls); `git diff --check` exit 0. Source controls compare the
other six tests/helpers, original annotations/oracles, precise admission and
cleanup routes against the fixed Git object. Models are examples of the proposed
oracle, not execution of Java/Forge. Actual source patch, input pins, command
outputs and final manifest/checksums are in the thin external author directory:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/laser-save-fixture-author-20261006-2de9f4`.

Read-only inspection failures are retained: a guessed EndgameLifecycleGameTests
locator returned an rg error/exit 1; a later guessed GuardedChunkSavesGameTests
locator also failed inside a composite command whose later Git diff returned 0.
Neither missing-file read is counted as successful inspection. No test/compile
failure has been concealed or replaced with a pass.

## Remaining verification and boundaries

No javac, Gradle, GameTest, native server/client, hosted run, commit or Gate result
was executed by this worker. Root needs different-agent actual-source review,
compilation and hosted GameTests before recording verified delivery. A reviewed
but unverified source checkpoint for CI is not that delivery.

The single scene assumes normal synchronous server-thread Save/removal callbacks.
The item query creates a native local result list before checking its 16-item
bound; this is not an adversarial entity-allocation guarantee. Exact reference
cleanup is limited to admitted drops; abnormal native query/cleanup failures may
leave world residue but still clear listeners/context and fail visibly. Cleanup
does not force an unloaded chunk. Actual callback order, failure cleanup and the
full existing test must be verified in the GameTest host. This changes no ordinary
save policy, first-event authority, resource transaction or Required Gate status.

Rollback scope is this one GameTest file and the three task records; no production
migration, registry/resource generation or original-world operation is involved.
