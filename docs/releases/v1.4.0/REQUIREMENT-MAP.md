# v1.4 requirement coverage and acceptance gaps

This maps [the version plan](../../versions/V1.4.0-PLANETARY-EXPANSION.md) to
development evidence, not checked release boxes. Artifact identities and original
reports are pinned by [evidence-index.json](evidence-index.json).

| Requirement | Implemented scope and evidence | Remaining boundary |
|---|---|---|
| Versioned definitions, defaults, capabilities, IDs/graph/numeric bounds | [DATA-01](../../work/v1.4.0-data01/VERIFICATION.md), ADR-031; optional physical Level, strict schema 2, legacy decode | No runtime dimension allocator; candidate data/asset audit |
| Atomic celestial/route reload and rollback | [DATA-02/03](../../work/v1.4.0-data02/VERIFICATION.md); bounded bytes/depth/count, coherent generation/cache, native rejected candidates | Not a transaction across satellite/vanilla reload listeners; final candidate integration |
| Stable mappings and old identities | [MAP-01](../../work/v1.4.0-map01/VERIFICATION.md), ADR-032; checked lifetime reservations | Initial adoption records present configuration, cannot reconstruct pre-ledger remaps |
| Mars/Venus contrasting explorable worlds and non-landable gas giant | [MAP-02](../../work/v1.4.0-map02/VERIFICATION.md), ADR-033; generated terrain, real Levels, bounded landing, actual flights/stations | Real-player progression/video and candidate multi-planet playthrough |
| Gravity, atmosphere, temperature, sunlight, radiation placeholder | Existing environment plus [ENV](../../work/v1.4.0-env/VERIFICATION.md), ADR-034; opt-in exposure, equipment and supplied rooms | Radiation is metadata, not damage; real-player equipment/oxygen balance |
| Star map, relations, environment, server-filtered reachability | [NAV](../../work/v1.4.0-nav/VERIFICATION.md), ADR-035; bounded schematic layout, quote reasons, station selection, generation refresh | Pure layout/GameTests are not live UI packets, scale/language readability or V1/V2 |
| Discovery/research and shared policy | [DISC](../../work/v1.4.0-discovery/VERIFICATION.md), ADR-037; shared knowledge/private accounts, retained fee snapshot, membership separate | Two UUIDs or FakePlayers are not two real clients; real terminal/menu flow |
| Sky, sun, stars, fog, ambience, lifecycle | [SKY](../../work/v1.4.0-sky/VERIFICATION.md), ADR-036; original cached geometry/profile resources and conservative fallback | No GPU, shader, audio or cross-world visual acceptance; generic shared-Space sky |
| Asset batches and references | [Provenance](../../provenance/v1.4.0-development-metadata.md); 33-file SKY batch followed by the [34-file discovery/recovery inventory](../../work/v1.4.0-mig-claims/generated-resources.json), original code/data, runtime vanilla IDs only | No wholesale classic asset import; candidate license/reference audit still open |
| Existing-world upgrade and station orbit preservation | [MIG-02](../../work/v1.4.0-mig-worlds/VERIFICATION.md); authentic v1.3 development world copied, upgraded, removed/restored and restarted | Original terminal/accounts are historical; Moon station/rocket were created on the copy by old runtime; not all historic worlds/subsystems |
| Discovery persistence and removed-content replay | [MIG-01](../../work/v1.4.0-mig-claims/VERIFICATION.md), ADR-038; receipt/discovery/completion barriers, bounded recovery | Capacity/unknown/future data can block; operator restore, not deletion or automatic eviction |
| Native interruption recovery | [MIG-03](../../work/v1.4.0-mig-cuts/VERIFICATION.md); four actual owned-process cuts, unedited restart, independent two-store readback | Not arbitrary crash points, disk/power loss or atomic terminal/chunk/rocket storage |
| Security: forged destinations, undiscovered arrivals, invalid/huge packs | DATA/MAP/NAV/DISC guards and tests; no terrain loaded by menu quotes | Real malicious-client packet sequence/candidate-wide assessment remains |
| Performance: bounded graph/cache, no per-frame geometry build, 100 bodies, side isolation | DATA-02 100 bodies/101 routes; NAV 100/128 nodes; SKY cache and packaged dedicated startup | Finite tests do not establish MSPT/FPS/latency, memory plateau or load endurance |

New logical bodies/routes do not require a Java enum, but new physical Levels
still require valid startup dimension data. Discovery alone creates neither a
route nor a gas-giant surface. Removing definitions retains progress, station
identities and binding reservations; it does not remove native dimensions.
The version plan's deprecation intent is implemented by retention/refusal, not
a new public `deprecated` lifecycle field. Undiscovered metadata is public;
server filtering controls arrival/readiness, not secrecy of catalog nodes.

## Evidence that cannot be inferred

- Preparation ran unchanged v1.3 code, not v1.4 features. Earlier DATA/MAP/ENV/
  NAV/SKY/DISC artifacts differ from the final MIG production artifact.
- MAP-02's original upgrade fixture was pre-planet v1.4, not v1.3; MIG-02 adds
  the separately identified actual v1.3 development-world copy.
- Whole-world original-content parity, real players, two GPU categories,
  gameplay video, reference-load and candidate-wide migration/compatibility
  remain outstanding. The [porting matrix](../../PORTING_MATRIX.md) is not complete.
- No final `READY_FOR_AUDIT`, `PASSED`, candidate identity or human decision is
  implied. Full campaign scheduling follows ADR-018; [G0-G9](GATE-STATUS.md) stay open.
