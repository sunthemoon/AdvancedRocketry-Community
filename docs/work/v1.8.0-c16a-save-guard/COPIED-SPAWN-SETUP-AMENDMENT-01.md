# Copied-world spawn setup amendment

Date: 2026-10-04. Owner: Root. Status: IN_PROGRESS.
Baseline: `23bec1eb1452e8c8ef37c004606aaed3a2394063`.

Write scope: the existing lifecycle Python script, its focused tests and this
task record. No Java, production save behavior, schema, public contract, assets,
registration, source world or sealed prior evidence changes. Both preceding
native failures remain failures. This is disposable test setup, not repair.

Independent pinned primary inspection establishes that the retained world's
spawn is (256, 74, -32), chunk (16, -2), and the target chunk (11, 11) is at
Chebyshev distance 13. The native START ticket has level 22 and no timed expiry;
its propagated holder level is 35, within ChunkLevel.MAX_LEVEL 45. Losing the
entity-ticking loaded predicate does not require this holder's removal. These
facts identify a fixture eligibility conflict under standard ticket behavior;
they do not assign the missing marker a unique runtime cause.

After confirming the initial forced target is loaded, issue the single fixed
console command `setworldspawn 256 74 -256 0` in the copied runtime. Native
setDefaultSpawnPos removes the old START ticket and adds the new radius-11
ticket. The new spawn chunk (16, -16) has distance 27 and derived level 49,
outside the target's START retention range. Keeping the explicit forced ticket
through setup prevents an earlier target unload from substituting for the
later fresh-event observation. The existing bounded command barrier requires
one fresh exact authoritative native success line; it is not durability proof.

Preserve the original total 60-second unload deadline, fresh real-event marker,
strict live tank/AIR restoration and exact compressed terrain/resource roots.
Verify original input world, libraries and artifacts unchanged. A new native
cohort uses the already pinned Source23 JAR, whose Java inputs are unchanged;
separately bind these two new tool postimages and review their actual diff.

No arbitrary sleep, timeout extension, fake event/ticket or success substitution
is introduced. Other ticket sources, callback completion, production recovery,
first-save writer, cross-store durability, charged conservation and all Required
Gates remain outside this diagnostic scope. R-021 remains open and unaccepted.
