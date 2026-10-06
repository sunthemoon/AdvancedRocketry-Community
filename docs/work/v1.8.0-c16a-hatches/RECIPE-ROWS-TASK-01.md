# C16a-03b-RECIPE-ROWS-01: immutable lathe recipe rows

Date: 2026-10-07. Status: in-progress. Author/integrator: Root; independent
source reviewer is assigned separately. Base: `86c40c56b8ac3815522cc6e248e16307809bc4de`.

## Outcome and contract

Implement exactly the three immutable section 5 row records from the adopted
[technical inventory](OWNER-MODES-INVENTORY-ACCEPTANCE-01.md): `ClassicItemInput`,
`ClassicItemOutput`, and `ClassicFluidRow`, in `machine.classic.adapter`.
The exact JAVA-SIGNATURES postimage is
`8decf75d3ad1610c99241fd0795a3cdcb144b26cb58448861d6bdc6acb2ef366`.
Counts are 1..64, fluid amounts 1..16,000, and output/fluid IDs are canonical
ResourceLocations of at most 128 characters. The input owns a canonical JSON
String of at most 4,096 characters in the existing bounded vanilla item/tag
ingredient subset. Parse only that shallow grammar, before canonicalization;
arbitrarily nested text must not reach a recursive parser. Do not resolve tags
or consult registries in these data constructors.

The shared ingredient validator remains the grammar authority. Registered,
nonempty and native maximum-stack checks, bound alternatives, recipe-wide
JSON/signature/timing validation and guarded planning are later boundaries.
These records alone do not authorize a recipe, checkpoint or resource mutation.

## Write scope and exclusions

Root writes only the three new production records, the new focused
`ClassicRecipeRowsTest`, and this task. Existing kernel/Bank/value/recipe classes,
registry, network, resources, build, owner AGENTS and other-agent work are
read-only. All code is original NEW/MIT, not copied from upstream.

No GuardTicket substitute, partial validated-recipe implementation, native
getter, writer, codec registration, chance/lens rule, machine identity,
physical hatch, lathe formation, acquisition recipe or future-version system
is introduced. Full C16 and all Required Gates remain incomplete.

## Verification

Verify the exact record surface, retained canonical input, item/tag/alternative
grammar, collection/text/ID/count/amount boundaries, malformed and deeply nested
input, and construction without registered resources or tag binding. Run a
bounded cached Java 17 compile and the actual Jupiter subjects with fresh output
and process-local TEMP/TMP under `D:/GitHub/ARCE-Task-Evidence/v1.8.0`.
No local full Gradle/GameTest/native run while C is below 10 GB.

Different-agent actual-source review precedes integration publication. Preserve
raw commands, failures, streams, input hashes and the distinction between an
uncommitted development check and a fixed-commit replay. No ledger delivery or
Gate approval follows merely from the record tests.
