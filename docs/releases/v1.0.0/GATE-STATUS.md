# v1.0.0 Gate review entry point

**IN_PROGRESS.** The authoritative mutable status is
[docs/status/GATE_STATUS.md](../../status/GATE_STATUS.md); this document does
not create a second approval record. Candidate identity and reviewer/maintainer
signatures are absent. No v1.0 Gate is approved.

| Gate | Evidence available | What still prevents acceptance |
|---|---|---|
| G0 | Existing provenance and artifact audits | Final candidate license/source/distributable review and approval |
| G1 | Development builds and source/JAR inventories | Clean reviewed candidate and reproducible release identity |
| G2 | Unchanged generated resources | Candidate-bound data/assets checks and clean candidate tree |
| G3 | 406 Java and 51 Forge tests on the recorded development snapshot | Candidate-bound full required automation |
| G4 | Older packaged servers and real-client sessions | Final-candidate dedicated startup/join/restart and side checks |
| G5 | Older Beta upgrade and forced-stop matrices; new focused regressions | Candidate-bound migration/recovery and pending native load-order verification |
| G6 | Focused authority regressions and older multiplayer evidence | Independent candidate security review and complete multiplayer coverage |
| G7 | Bounded algorithms and historical tests | v1.0 reference-load measurements; no four-hour v1.0 result |
| G8 | Earlier native GPU screenshots and two-client observations | Candidate-bound full visual/player flow and human acceptance |
| G9 | This development handoff, README and changelog | Installation test, complete evidence, signatures, tag/rebuild/download verification |

Criteria remain [G0-G9](../../06-RELEASE-AND-ACCEPTANCE-GATES.md) and the
[v1.0 version requirements](../../versions/V1.0.0-COMMUNITY-MVP.md).
Missing evidence is not an exemption. Historical v0.9 approval and ADR-013
are not extended into v1.0.
