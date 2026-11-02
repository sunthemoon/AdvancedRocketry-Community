# Landing-pad classic texture (W261-fix, R289)

R289 replaces R287's selection. The pinned original upstream
`blockstates/landingpad.json` uses `minecraft:cube_all` with
`advancedrocketry:blocks/rocketpad_` on all faces. The modern provider and
inherited block item use that same tile through `minecraft:block/cube_all`.
The 566-byte import is an exact MIT byte copy, SHA-256
`8293011124c1d6e61c078dcb53c49d847c24ad46709d0d500023e105efb85f3a`.
Its name is free in the retained art roots; no `_classic` suffix is needed.
The pending record and LF plan replace the unsealed noedge import.

The other fifteen exact source paths, including `rocketpad_noedge`, are
EXCLUDE: launch pad connected texture; block:launchpad was redesigned into
the rocket assembler with no pad block (R289). No connected state is added.
IDs, recipes, geometry, collision, landing selection and occupancy do not change.

The NEW `landing_pad.png` and generator remain byte-for-byte unchanged.
Proposed disposition: KEEP as retained, unbound original art pending Root's
art-disposition review; it is neither a live face nor an automatic fallback.
Human provenance approval and real-GPU V1 remain pending. Inspect a pad
grid, every face, particles, inventory/hand views, assembled snapshots and
resource reload on a real client. No Gate or version approval is asserted.

The [task record](../v1.8.0-root-queue/records/W261.md) reports commands,
results and limitations. Root integrated the central-file changes on the
W261 branch and merged them with W260's by a row-level merge of the asset plan.
