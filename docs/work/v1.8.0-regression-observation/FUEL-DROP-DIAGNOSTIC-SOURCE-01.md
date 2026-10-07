# Fuel-loader native drop count: source-only diagnostic

Date: 2026-10-07. Author/integrator: Root. Base source:
`22f7d1cae0735a8b8c072165e7a3cf8089e3719c`.
Status: source candidate, independent review/native replay pending.

The only Java change appends `count=` plus the already-returned list's size
to the existing one-ItemEntity assertion message. The exact predicate remains
`drops.size() == 1`; the same native destruction, unfiltered same-AABB query,
drop extraction, source metadata, BlockItem placement and all other assertions
remain byte-identical. All six tests and twenty-tick deadlines are unchanged.

No new native/world query, filtering, tag/inventory access, retry, production
change or resource mutation. Decimal int formatting is bounded and does not
identify a causal defect or distinguish the individual entities. The preceding
failure remains source-bound evidence, not an observed production duplication.

Own isolated worktree: `D:/GitHub/arce-v180-fuel-drop-diagnostic-20261007`.
Write scope: this record and `FuelLoaderPlacementGameTests.java` only. Root
publishes reviewed exact source with a new source-bound hosted regression;
no author Java/native command executes locally while C has under 10 GB free.
Actual static source checks are retained under D's project-parent evidence.
No Gate, risk, contract, ledger or physical hatch activation is changed.
