# v1.3 authority and compatibility review boundary

This is an implementation/evidence inventory, not a final candidate security
approval. Supported consumers run in the same JVM and are trusted mod code;
the host contains supported failures, not arbitrary hostile side effects.

| Boundary | Implemented mechanism | Evidence / remaining limitation |
|---|---|---|
| Registry mutation | Owner namespace, exact type/item claims, bounds, receiving-thread/window and frozen atomic definitions | Unit tests and real MOD-bus conflict fixture; not independent third-party ownership certification |
| Rocket callbacks | Exact registered type, server/thread/loaded target, no identity fields in external body, bounded versioned envelope | Actual scan/disassembly fault tests; no source mutation allowed by contract |
| Failed restore | Checked no-drop cleanup plus full readback, retained rocket authority and retry | Nine public fault modes plus recovery records; no generic side-effect or storage-power-cut rollback |
| Slow callback | 5 ms returned-time check, bounded diagnostic frequency | Deterministic and real finite-delay tests; cannot interrupt a callback that never returns |
| Suit provider | Detached bounded NBT, checked debit/commit and disabled invalid provider until restart | Production life-support/canister tests; not arbitrary capability sandboxing |
| Fuel/component/satellite registration | Frozen declarative values; no retained arbitrary fuel/reward callback | Whole-item batch/remainder and snapshotted task semantics; native Forge item behavior is still trusted code |
| Environment reads | Owning server thread/lifetime, bounded metadata lookup, no terrain/chunk read or dirtying | Source, unit, GameTest and native unloaded-query observations |
| Player/menu requests | Existing owner/distance/exact-live-BlockEntity/state checks, bounded intent IDs and catalog freshness | Terminal stale-menu/replay regressions; candidate real-client network/multiplayer still missing |
| Data recovery | Unknown/future/bounded invalid roots retained before lossy item decoding | Targeted persistence and drop/place tests; arbitrary corrupt native storage/no-drop destruction is outside guarantee |

Detailed signatures, limits and permitted use are in the
[API guide](../../PUBLIC-API-GUIDE.md) and
[compatibility matrix](../../API-COMPATIBILITY.md). The
[latest bounded review](../../work/v1.3.0-compatibility/VERIFICATION.md) reports
no unresolved scoped finding; this is not a complete global Critical/High
defect census or inherited-Gate approval.

## Candidate review still required

- Repeat privilege, distance, unloaded-target, malformed/oversized, replay and
  frequency cases from actual supported client/server builds and two players.
- Audit genuine interrupted storage stages and shared machine/rocket authority;
  do not substitute clean restart or manually staged recovery records.
- Inspect final API/classifier/host/consumer classpaths and runtime sides;
  test actual permitted external integrations rather than only the fixture.
- Revalidate the exact release archive for credentials, unexpected files,
  provenance, notices and absence of unapproved dependencies or client-only
  common linkage. No new upstream content is imported by this handoff.

Resource duplication, corrupt saves and authority violations remain blocking
defects; they cannot be renamed as deferred performance testing. See
[Gate status](GATE-STATUS.md) and [known issues](KNOWN-ISSUES.md).
