# C18a airlock current-seed diagnostic amendment

Date: 2026-10-07. Status: in-progress; failure evidence only, not repair.
Author/integrator: Root. Base: `ff56fd7e2d394bc4dc31005b3be05186597f7cd1`.
Owned worktree: `D:/GitHub/arce-v180-airlock-seed-inputs-20261007`.

## Scope and basis

The source-bound failed replay and independent audit in
[RESULT-40](../v1.8.0-ci/RESULT-40.md) leave three required native failures open.
The current airlock snapshot classifies the seed OPEN but does not capture its
block identity or native sky/height operands. Root reads the separate
[source investigation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-initial-supply-source-investigation-20261007-01/REPORT-01.md)
and [bounded amendment proposal](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-current-seed-diagnostic-amendment-20261007-01/REPORT-01.md).
Their SHA-256 values are respectively
`3d70cb8c9d89444562696857a7ae998f95dd451c2108724555c58a9fa5908bb8` and
`5fa103b27f611479d76a42aabde74d9964fe9f19b38cfc0733f038cd94ee08ce`.
Neither proves a unique cause. This amendment adds only missing current inputs.

Exact write scope: existing `AirlockDoorGameTests.java` under the GameTest
package, the successor note in `SUPPLY-DIAGNOSTIC-TASK-01.md`, this task,
own `SEED-INPUTS-SOURCE-02.md`, and the Root implementation log.
No production, config, registry declaration, network, schema, save, resource,
asset, native hook, authority or accepted policy changes. No other test family.
Keep all seven subjects, six-case order, assertions/messages, deadlines,
controlled calls, scan/dirty limits, native operations and cleanup unchanged.

## Read and output contract

Append one private current-seed segment after the existing seven classifications
and two door-half snapshots. Existing observations and the failure-only call
site remain unchanged. Check build height, nonloading `getChunkNow` and loaded
availability before one direct seed BlockState read. Output only its stable
block registry ID, isAir and, for the actual own airlock, the five fixed door
properties from that same state. Do not read another counterpart for this group.

Then sample `canSeeSky` once and `getHeight(MOTION_BLOCKING_NO_LEAVES)` once,
with a loaded-availability recheck before height; output both native operands
and the primitive seed-y comparison. These are later diagnostic samples,
including when the seed is not air or sky is true. They do not assert that the
original production short-circuit branch read both operands or that sequential
native samples are atomic. Native getter/cache internals are not qualified here.

Check namespace/path lengths before constructing the key text; IDs longer than
256 characters emit `OVERSIZED_BLOCK_ID`, null keys emit `UNREGISTERED`.
The added segment is at most 1,024 characters, else emit its explicit oversize
sentinel. Only fixed ASCII labels, bounded registry ID, door enums, primitive
booleans/integers appear. No property enumeration, arbitrary state/NBT dump,
exception content, traversal, loading request/ticket, retry, tick, mutation,
scan scheduling or resource debit. Missing build/loaded availability has an
explicit sentinel. A separate RuntimeException/AssertionError/LinkageError
fallback preserves all old observations and the original assertion; do not
catch VM exhaustion or ThreadDeath.

## Verification and non-goals

Different-author actual-diff review and independent applicable checks precede
publication. Removing only two imports, one append and the new private helper
must restore the normalized fixed-base Java file exactly. Source-level checks
cover scope, bounds, guards and original test preservation; these do not compile
or execute native getters. Local C is below 10 GB, so no local Java/Gradle/native
work. Fixed committed-source hosted clean build, twice DataGen/cleanliness and
full unfiltered GameTests remain required. A successful run need not execute
this failure branch; no operand evidence is inferred without its raw output.
No first-OPEN scan provenance, unique cause, production repair, restart/client
proof, content delivery, R-021 acceptance or v1.8 G0-G9 closure is claimed.
