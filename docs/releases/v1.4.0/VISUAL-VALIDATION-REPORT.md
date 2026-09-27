# v1.4 visual validation status

Status: **NOT_STARTED for candidate V1/V2 acceptance**. No real-GPU screenshots,
gameplay video, audio review or two-real-client result was produced by this
handoff. No V0 software rendering run is relabeled V1.

Implemented code/resource coverage includes cached planetary sky/sun/stars,
conservative fog, bounded ambience and console navigation/discovery feedback.
Pure geometry, resource identity, GameTest and dedicated side-isolation checks
cannot establish visible correctness, readability or audible behavior.

Execute [manual procedures](MANUAL-TEST.md) and original SKY/NAV/DISC cases on
the chosen candidate. Record at least two real GPU categories, drivers, GUI
scales/languages, packs/rendering mods, world transitions and raw media. V2 must
include two actual clients with independent accounts; server UUID fixtures do
not qualify. Include missing/invalid profile fallback and inherited UI checks.

Full acceptance remains deferred under ADR-018 until original machinery and
dimensions are implemented. G8 stays open; no reviewer or maintainer visual
approval is assigned.
