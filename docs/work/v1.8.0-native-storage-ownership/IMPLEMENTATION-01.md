# Native storage ownership probe 01

Date: 2026-10-11. Status: IN_PROGRESS, development-only experiment.
Base: `64c295e777ae902d98a5b1b886c9067102393e8e`.
Branch: `test/v1.8.0-native-storage-ownership`.

## Scope

One JUnit test submits four fixed tiny root/child NBT trees through native
IOWorker APIs, observes copy/write callbacks and returned reference identity,
then verifies marker values after completion and worker reopen. The input trees
are not mutated after submission. A constructor-only worker subclass and passive
CompoundTag callbacks avoid engine reflection, AT changes, source copying and
product/save changes. No expected-log rule or Gate is relaxed.

This is not a reproduction or repair of CI run 38064322640. It does not identify
the dimension, block entity, entity or callback that modified the failing save.
It is neither a world restart test nor a durability/performance qualification.
Unofficial API indexes were discovery hints only; exact Forge 47.4.10 compilation
and initialization remain unverified before the allocated first execution.

## Evidence interpretation

Positive input/load identity or a virtual write on the submitted object is an
observation about that instance. Unequal identity and absent callbacks do not
prove defensive ownership. The first load has no deterministic pending-window
witness. Copy counts span store/load phases and do not identify which API copied.
A false caller-thread flag with zero writes does not establish another writer
thread. Instrumented subtypes add counter overhead and are not every vanilla tag.
Marker assertions establish only the tested small-record contents.

## Validation preparation

The test has four serial iterations, ten-second future waits, a sixty-second
JUnit timeout and an external original-command deadline of 900 seconds. Native
close is not claimed internally preemptible. Failed temporary storage is retained.
The separately reviewed runtime allocation defines owned-job committed-memory,
storage, capture, environment and terminal-handle limits. No source world is used.

Independent initial review found prospective environment/resource-definition
issues; the external launcher/allocation were revised for a second review.
Seven Python resource-guard tests, including native limit readback on a newly
created non-JVM job, passed. This is not execution of the Java probe. Compilation,
the declared targeted JUnit command, full build, DataGen, GameTestServer and
dedicated/client/restart acceptance are NOT_RUN at this preparation checkpoint.

External evidence:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/native-storage-ownership-20261011-01/`.
Independent reports remain under the named private Temp leaves referenced there.
Old sealed packets, original worlds and user-owned dirty files are unchanged.
The version remains IN_PROGRESS and all G0-G9 Required Gates remain unapproved.
Only current v1.8 storage investigation and fixture validation are suggested next.
