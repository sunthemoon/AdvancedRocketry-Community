# v0.5.0 Performance and fixed limits

The maximum legal 2,048-block synthetic structure completed in 41 service
ticks. It made 10,242 unique world observations, below the fixed 12,289 total
limit, and no tick received more than 256 observations. The focused local run
took 18,911,700 ns of test-process wall time; this value is observational and
is not used to relax a correctness bound.

The BlockEntity-free maximum visual projection encoded to 32,888 bytes and
split into two packets under the 32,768-byte per-packet limit. Encode, chunk,
and decode together took 9,589,700 ns in the focused local run. The 524,288-byte
aggregate cap therefore retained substantial headroom for this maximum-block
single-palette case.

The renderer restores BlockStates only when an entity's content hash changes,
keeps at most 256 entity caches, and performs bounded frustum culling against
the snapshot bounds. The visible cached structure is archived at
[`evidence/client/screenshots/rocket-render.png`](evidence/client/screenshots/rocket-render.png).
Exact measurements and limits are recorded in
[`evidence/performance/summary.json`](evidence/performance/summary.json).
