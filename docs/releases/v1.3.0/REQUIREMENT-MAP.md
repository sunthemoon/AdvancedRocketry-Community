# v1.3 requirement disposition

This maps the [version plan](../../versions/V1.3.0-PUBLIC-API-COMPATIBILITY.md)
to implementation/evidence, without checking its acceptance boxes. Source and
examples are indexed in the [API inventory](../../API-COMPATIBILITY.md).
The original reports are pinned in [evidence-index.json](evidence-index.json).

## Scope and contract interpretation

| Version requirement | Implemented scope / evidence | Boundary or outstanding requirement |
|---|---|---|
| Sections 3/6: API inventory, internal separation, version policy | API 1.7, exact 27-class classifier and independent consumer; ADR-021 through ADR-029 | Not all Java `public` types are supported; no arbitrary mod binary guarantee |
| Sections 3/6: movable classification and BlockEntity lifecycle | Existing movable/immovable tags plus registered exact types and `canMove`; actual capture/restore/readback/failure cleanup | Registration cannot override forbidden blocks; no new unbounded world predicate |
| Sections 3/6: atmosphere boundary | State-only loading-time compilation, real room scanner and invalidation | Not per-position/world-dependent callbacks |
| Sections 3/6: suit/oxygen | Detached owned payloads, production life-support/canister integration | Not an arbitrary capability tank mirror; real-player HUD still unverified |
| Sections 3/6: fuel/engine/components | Frozen numeric definitions, item-fuel batches/remainders, actual scanner/loader | No new flight authority, fluid drain or NBT-dependent fuel callback |
| Sections 3/6: environment/gravity/body context | Server-lifetime immutable metadata query, catalog and indexed station lookup | Configured values, not entity physics, room oxygen or permission |
| Sections 3/6: satellite extension | Declarative payload/default research mission and existing terminal/service | Not custom mission/reward callbacks or future satellite gameplay |
| Sections 3/6: compatibility mod and documentation | Standalone classifier consumer, seven families, deliberate fault cases, support/deprecation policy | Shared test sources are not separately authored third-party mods |
| Section 6: compatibility "handshake" | ADR-021 exact-major/minimum-minor metadata check; existing channel handshakes remain separate | No new network protocol or automatic loader fallback is claimed |
| Section 8: old tags/config and namespace/version | Existing host tags/config IDs and vanilla payload format remain; external payloads have stable provider ID/version | No wholesale namespace rewrite or 1.12.2 world importer |
| Section 13: controlled environment cache | ADR-028 uses existing bounded indexed catalogs/registries; no per-position cache | Fresh queries read current metadata; no new cache is needed or promised |
| Section 17: whole-transaction rollback | Checked cleanup of host-owned placement, retained authority/journal and retry when unavailable | Arbitrary same-JVM side effects, external allocation and power-loss atomicity are not rollback guarantees |

## Test and acceptance requirements

| Plan section / requirement | Evidence available | Unproven broader scope |
|---|---|---|
| 9: API-only compilation and five registration families | Independent classpath audit, expected internal-import rejection, actual loading events; all seven families now represented | Candidate rebuild and an actual external modpack |
| 9: adapter round-trip, throwing/slow/oversized isolation | Production-command GameTests, exact cargo/authority/retry; finite returned-time tests | Non-returning callback preemption is outside contract |
| 9: missing external mod persistence | Actual fixture uninstall/reinstall for stored rocket data; fuel/satellite missing-data checks | Arbitrary third-party item/block destruction or normalization |
| 9: no client-only API linkage and dedicated loading | Archive/dependency checks and packaged server runs | Actual integrated-client/optional-client matrix |
| 10: fresh host+fixture startup/restart | Consumer packaging and current compatibility evidence | Real-player join and two-player synchronization |
| 10: custom container Earth-Moon-return conservation | External-flight report with exact saved cargo and route fuel | Historical artifact; no candidate flight replay; fuel disposal is explicit under ADR-023 |
| 10: controlled uninstall and recovery policy | External-recovery report and migration guide | Staged journal plus clean S1, not process-kill S2 |
| 10: conflicting external IDs | Duplicate owned ID and type claim rejected on actual MOD bus | Two attempts from one fixture, not two separately authored mods |
| 10/12: permission, hostile input, no chunk loading, registry freeze | Bounded source/unit/GameTest coverage and service reuse | Candidate real-client packet and multiplayer evidence |
| 11: names/icons, assembler feedback, equipment feedback, optional client absence | [Unexecuted manual cases](MANUAL-TEST.md) | All actual v1.3 V1/V2 outcomes remain missing |
| 12: NBT limits, partial commit, reward/resource replay | Adapter envelope, loader/terminal quarantine, owner/replay tests and native checks | No generic arbitrary-mod sandbox or cross-file crash atomicity |
| 13: boundary-only serialization and finite queries | Existing service placement and bounded query design; scoped source review | Candidate profiling is still needed |
| 13: slow adapter warning/limit | Three finite slow-return phases, once-per-adapter warning and deterministic budget unit tests | Scheduling overshoot and startup warnings are retained, not benchmark acceptance |
| 13: near-zero no-extension cost | Design avoids added per-tick global serialization | Not measured against a defined baseline/reference workload |
| 14: registrations, docs, internal exclusion, missing-mod policy | Per-slice implementation and independent review; all scoped evidence listed above | Candidate-bound replay and final approval remain outstanding |
| 14: crash recovery without duplication; zero Critical/High | No unresolved scoped finding in the latest bounded compatibility review | Not a complete candidate defect census or S2 proof; inherited gaps remain |
| 15/19: standard evidence and G0-G9 | This handoff and immutable original records | Not a candidate, Gate PASS, tag or release |

Complete the missing scenarios at their approved scheduling point. A row's
narrow implementation evidence cannot be expanded into its broader unproven
scope, and a missing result is not silently converted to a non-goal.
