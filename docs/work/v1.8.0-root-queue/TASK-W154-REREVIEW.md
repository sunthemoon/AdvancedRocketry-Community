# TASK W154-rereview: check Root decision R95 against W154-R1 and W154-R2

Root: Claude. Date: 2026-10-24. This continues your W154 review session (`W154-review/REVIEW.md`, SHA-256
`6facedc6…`, verdict BLOCK on W154-R1 and W154-R2). Same rules: read-only, no commit, write only to your output folder,
no other agents.

## Root decision R95 (2026-10-24), replacing R94 items 2 and 3

1. **One materializer for every adapter**, built-in and external. Restore into a target constructed for the final
   position and bound to the server level, but not installed, while C stays installed. Verify by recapture. Publish
   the prepared instance in one step, so no empty or partly restored instance is ever reachable through the world,
   and only then send the neighbour and comparator updates. A notification the target makes during restore (for
   example `setChanged` reaching `updateNeighbourForOutputSignal`) arrives at a position where the world still holds
   C, which answers inert. This is meant to close W154-R1 for every consumer that reaches the target through the world.
2. **A failed restore** (false, a throw, over the 5 ms check or a mismatched recapture) discards the target, which was
   never installed. C stays exactly as it was; the member is held and logged once, with no retry. No reference to the
   target was ever obtainable through the world, so W154-R2 does not depend on Forge's `LazyOptional` invalidation.
3. **Provider contract** (API documentation and changelog; method signatures unchanged): restore receives a target bound
   to the server level at its final position before it is installed. It must work from the target and the payload, must
   not look itself up through the world, hand out references to the target, or move resources to or from other
   objects. A provider that needs an installed host fails restore safely (held, nothing lost). That is the only
   compatibility effect, and R-035 records it. The `src/adapterTest` fixture provider gets a test for success and for
   every failure class.
4. **Built-ins:** the prepare your review found implementable; a null or server-bound Level per host is the
   implementer's choice, as long as item 1 holds.
5. **W152's first commit is a probe** of the publication sequence for the four payload hosts, the plain members and the
   external fixture. It records the callback order (constructor and capability attachment, `onPlace`, `onLoad`,
   `removeBlockEntity` and its neighbour notification) and the first moment a capability is reachable. If no
   one-step publication exists without a Mixin, W152 stops and reports.
6. Your other notes stand as implementation requirements: `onlyOpCanSetNbt()` for C, the ordering around
   `Level.removeBlockEntity`, and the R1 and R2 regressions adapted to item 2: assert that no reference to a failed
   target was obtainable through the world.

## What to check

Whether R95 closes W154-R1 and W154-R2, whether item 3's contract is sound and enough, and whether anything R95 adds is
unsound (for example a provider whose restore needs the installed host, capability attachment at construction, or
the publication step itself). Keep it short: one verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK), findings only where
something remains, appended as a new file `D:/GitHub/ARCE-Task-Evidence/root-queue/W154-review/REREVIEW.md`; its
SHA-256 on the last line of your answer.
