# C15c implementation review round 1 — dispositions

Reviewed source `cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6`; root fixes are an
uncommitted, hash-bound source snapshot, not a fabricated commit.
[Independent report](reviews/REVIEW-01.md): 3 Medium, 1 Low; no Critical/High.

| Finding | Disposition | Regression/evidence |
|---|---|---|
| M1 — custom planetary sky ignores the mushroom flash | Fixed: forward remaining flash time to the renderer, bounded blue-white sky/horizon tint, honour the vanilla hide-flash option | `SkyMathTest`; independently checked source forwarding; V1 remains open |
| M2 — cooldown leaks across worlds/time rewinds | Fixed: weak world-identity-scoped cooldown, reset on world change/rewind, no unloaded-world retention | three `FlashCooldownTest` cases; independent old-code reproduction and updated tests |
| M3 — radius 160 does not cover legitimate thin rocket footprints | Fixed and independently verified after owner selected expansion without restricting rockets; radius 224 covers the maximum pad/rectangular footprint plus diagonal feature reach | exhaustive selector-centred rectangular footprint `LandingGroundTest`; actual valid 255-by-4-by-1 regression; independent 67,736-rectangle probe, conservative envelope 218.728; new generation only, explored-chunk seam disclosed |
| L1 — first rejected resource reload lacks Tau Ceti fallback profiles | Fixed: six canonical built-ins; the v1.8 provider writes its two profiles from that shared source, earlier generated outputs remain immutable | `SkyProfileTest`, `SkyProfileReloadTest`, `SkyResourcesTest` |

The reviewer independently reran the amended snapshot: 37 JUnit tests and 42
required GameTests passed. The selected GameTests include a review-only
shallow-water observation probe and adapter tests; they are not the whole
384-test version suite. The shallow-water tree concern was disproved by that
probe, not accepted as a defect.

The [final M3 follow-up](reviews/REVIEW-02.md) independently passed 38 JUnit
tests and the same 42 selected GameTests after exact resource assertions were
updated to 224. Its first failed run (two stale 160 assertions) is retained.
Eight generated JSONs contain only ten intended scalar changes from 160 to
224. Original and follow-up raw evidence, source hashes and probes are in
`reviews/rounds-01-02-evidence.zip`.

ADR-063 revision 6 is accepted under the owner's conditional authorization
after the reviewed fixes and explicit radius-expansion decision. No release,
candidate, asset originality or real-client acceptance follows from these runs.
