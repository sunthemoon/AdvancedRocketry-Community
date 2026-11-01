# C18d K3: analyser, beacon, observatory and solar-array models

Implementation source: `58eca601af5fd1f263201ca8000cd1eccd6920c9`. Root R240 applies owner decision D8 to
seven history-clear upstream assets. See [W236](../v1.8.0-root-queue/records/W236.md)
for the pre-implementation projection design, executed commands, actual test
results and remaining acceptance. No Gate or human source approval is claimed.

`tools/import/v180-k3-plan.json` and `docs/provenance/v1.8.0-c18d-k3.json`
reproduce seven byte-identical upstream files, four NEW missing MTLs and one
byte copy of the existing NEW planetary-beacon front texture. The authoring
commit is `b44b6af9d11c602beb067f63be1eda5f052049d9`. Every history and
derivation check remains enforced and CLEAR; provenance is PENDING_HUMAN_REVIEW.
The original beacon renderer binds an out-of-batch beacon.png; this batch
instead binds the existing project-owned controller art at beacon_new.png.
This is an explicit visual substitution, not clearance of the legacy texture.

All four models render only formed machines, matching the legacy canRender
condition. Research controllers reuse the classic transient formed byte.
Beacon and array controllers add only that bounded visual byte via vanilla
update packets, notify on transitions and never persist or trust it as server
formation. Root must obtain a separate read-only projection review before merge.
No channel, registry identity, save schema, collision or matching change.
The array model covers its front controller row, not its optional collectors.
The observatory retains its closed idle shell; beacon spin groups use their
idle socket. Animation and open-observatory research projection are excluded.

Fixtures pin resource hashes, geometry, materials, scale, four-facing placement,
light samples, bounds, idle adaptation and fallbacks. Full sources count toward
the unchanged 65,536-object / 262,144-set-vertex and 16-MiB-set budgets before
adaptation. K3 contributes 4,012 budget vertices; the nine-model catalog has
11,700. New GameTests use distant regions outside template force footprints,
own their tickets and clean up on terminal outcomes without connected players.

Root decision R248 applies the independent review's orientation corrections:
fixture fix `09061ca5d7ff011ffcaaf74c26ea538d60671da7` and orientation test
`5be49083e8eef2e8cfbf9b2c87da5cd4abdc1d60`. The solar array (W236-R1) and the
observatory (W236-R2) now follow the analyser and beacon convention: each
model is its 1.12 renderer's placement mirrored across the pattern's width.
`K3ObjModelTest` compares every drawn position in all four facings with the
legacy transforms transcribed from upstream `c5cd5af6`; against `2e90a59b2`'s
fixtures it fails for exactly those two models. Model bounds, placed boxes and
render bounds are unchanged; real-client texture orientation remains open.

At the implementation commit, the unfiltered unit suite and clean build each
pass 5,952 tests in 1,083 classes without failures, errors or skips. Datagen
passes with no tracked changes. The full unfiltered GameTest run passes all
1,577 required tests, including all four K3 projection tests, and the unchanged
runtime-log checker passes. Import verify passes; the draft-applied disposable
copy passes 126 importer/ledger/OBJ Python tests and both strict validators.
No failure outside this diff or host-lag-only waiver is claimed. Additional
batches can change ordering and load in other runs, so the hosted regression
remains necessary. W236 retains the source-bound logs and their hashes.

Real Forge physical-client parse/bake, texture orientation, lighting, coplanar
overlap, controller-row solar placement, resource reload/fallback and V1/V2
remain final-campaign work. The etcher, K4/K5, maintainer provenance approval,
hosted branch regression and all-version Required Gate acceptance remain open.
`event:ClientProxy.modelBakeEvent(ModelBakeEvent)` remains REDESIGNED.
