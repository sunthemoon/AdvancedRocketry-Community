# v1.0.0 visual evidence

**Final-candidate V1/V2 acceptance remains incomplete.**

The [console presentation report](../../work/v1.0.0-console-presentation/VERIFICATION.md)
contains real native GPU observations on `46cb5776...`, including the fuel
panel and active route marker. The
[continued-use report](../../work/v1.0.0-passenger-continued-use/VERIFICATION.md)
contains two-client Earth return/seat observations on `b41db06e...`.
Recorded native hardware includes an NVIDIA GeForce RTX 3070 Laptop GPU.
Each source report retains its own environment, window dimensions, screenshots,
interaction receipts and limitations. Full identities are in
[evidence-index.json](evidence-index.json).

These are scoped observations, not a full current-candidate visual/performance
pass. In particular, the continued-use screenshot named
`passenger-stays-departed-f3.png` has no F3 overlay; position evidence comes from
the server receipt, not an invented overlay.

Still required on the candidate:

- Record CPU/GPU/driver, resolution, GUI scale and Forge baseline/compatibility.
- Show 64-, 512- and 2048-block rockets at first display, in flight and after
  dimension changes; check models, textures, seat positions and cache release.
- Inspect electrolyzer, life-support, rocket, station and satellite screens at
  supported scales; no inaccessible controls or critical text overlap.
- Check Earth/Moon/space skies, particles, sounds and two-client agreement.
- Record baseline/frame-time comparison, FPS/1% low, first-display stalls,
  resource reload and memory/cache observations under the quality budgets.

Xvfb/LLVMpipe remains V0 only. ADR-013 expires at v1.0 and cannot approve these
missing observations. The detailed criteria are in
[quality budgets](../../17-V1PLUS-QUALITY-BUDGETS.md).
