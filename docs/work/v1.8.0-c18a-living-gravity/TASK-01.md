# C18a living gravity and fall

Date: 2026-10-07. Parent status: in progress. Both the living-gravity and
gravity-scaled fall units under accepted ADR-066 section 3.3 remain required.
Leaf A (living tick) is technically adopted; source assignment
and runtime qualification remain separate. Leaf B (fall) has an unresolved contract finding and is not
assigned. Separating the leaves does not resolve that finding or deliver either
ledger unit.

## Observable result

Leaf A extends the production `CelestialGravityController` to non-player living
entities through the already registered native living-tick listener. Server players retain area field,
station-position override, Level profile and default-1 precedence. Non-players
use only their actual Level profile/default 1, never player fields or station
overrides. It does not register or implement a fall listener. Leaf B must still
apply fall distance once using the ARCE resolution before vanilla damage,
without reading the complete gravity attribute as a second multiplier and
without repeated scaling along native vehicle/passenger propagation.

This slice does not implement non-living gravity, non-player area fields,
atmosphere exposure/spawning, padded boots, other equipment or a new registry,
asset, schema, network protocol or save-veto mechanism. It does not complete
C18 or any version Gate.

## Leaf A implementation inputs for review

- Add COMMON `environment.classicGravityEnabled`, default true. It governs
  the new non-player Level gravity in leaf A. The proposed leaf-B boundary also
  leaves fall handling unchanged when disabled, but no fall handling is
  implemented by A. Disabled non-player ticks remove only the owned ARCE
  modifier. Legacy player gravity remains enabled and keeps
  its existing precedence. Re-enable resolves current state on the next tick.
  Use the existing in-memory test switch mechanism, not watched-file edits.
- Preserve the existing constructors and modifier UUID. An additive constructor
  accepts a BooleanSupplier switch; production wiring supplies the config accessor.
- Handle logical-server/main-thread events only. Preserve creative, spectator
  and FakePlayer player-gravity behavior. Missing gravity attributes skip
  attribute changes; do not add attributes to foreign entities.
- Use one shared resolution method and a transient MULTIPLY_TOTAL modifier with
  amount `multiplier - 1`. Valid factors are finite 0..4. At factor 1 remove the
  owned UUID before any same-amount shortcut; a same-amount wrong-operation
  modifier must be replaced. A matching owned permanent modifier must also
  become transient; a matching valid transient remains idempotent. Preserve
  base value and every other UUID.
- Resolve at the actual current Level/position. No per-entity factor cache,
  static collection, entity/world scan, motion rewrite, new ticket or forced
  chunk load is allowed. Native slow falling, no-gravity and hurt behavior stay
  native. Leaf A does not modify fall distances or damage. Valid transient
  modifiers must not persist in entity NBT; verify
  save/reload and recomputation rather than assume it from method names.

## Ownership and write scopes

The integrator owns shared event/config/status/ledger changes and performs all
commits/pushes. Source workers require a separate worktree and explicit task
assignment after independent contract review. AGENTS and unrelated work remain
excluded.

Worker source/test scope:

1. `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/service/CelestialGravityController.java`.
2. Matching existing `src/test/java/.../celestial/service/CelestialGravityControllerTest.java`.
3. New `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/LivingGravityGameTests.java`.
4. Only the worker's new source/handoff records in this task directory; this
   contract and later adoption/integration records remain integrator-owned.

Integrator source/test scope:

- `AdvancedRocketryCommunity.java`: bind the config supplier to the existing
  tick listener's controller. Do not register a fall listener in leaf A.
- `config/CommonConfig.java`: additive key/accessor/test-switch membership.
- New matching `ClassicGravityConfigTest.java`.

No build, registry, other test, station/field or equipment change is assigned.
Additional shared fixture changes require allocation before writes.

## Acceptance and actual verification required

- Independent actual contract and source reviews; preserve all existing tests,
  assertions and budgets. Do not treat static checks as executed Java tests.
- Unit tests cover bounds/null, owned modifier identity/operation/cleanup,
  conversion of matching owned permanent residue, foreign modifiers/base retention and
  switch defaults/disable behavior.
- Genuine registered native tests cover mob Level gravity and short movement,
  retained player precedence, no non-player field/station query,
  disable/re-enable and actual entity NBT
  save/reload with unrelated permanent modifier retained, including an initially
  matching owned permanent modifier that must not survive the save. Existing player
  station/field/planetary-exposure suites are mandatory regressions.
- Keep fixed-source Java 17 clean build, full unit suite, repeated deterministic
  DataGen with tracked/untracked cleanliness, unfiltered GameTests and targeted
  packaged/restart evidence. Real-client/GPU/multiplayer and full campaign Gates
  remain separate. No heavy local check starts below the 10 GB free-space rule.
- Both ledger units remain PLANNED until committed runtime source and actual
  scoped evidence support their delivery. A fixture/controller method alone
  does not establish production event wiring or persistence.

## Leaf B: retained requirements and open finding

The independent review [REVIEW-01.md](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-contract-independent-review-20261007/REVIEW-01.md)
found M1: mounted/passenger propagation is unspecified. The native vehicle
branch recursively invokes passengers' fall handling, and the living Forge
event precedes its inherited call. This proves a propagation boundary, not a
numerical double-scaling incident. A single listener is not sufficient evidence
that each living entity's distance is scaled once per physical landing.

Before assigning B, specify and verify living mount/living rider, player
precedence, non-living vehicle, nested passengers and cancellation/immunity
propagation, including the actual arguments entering inherited calls. Do not
weaken once-only scaling, suppress native passenger damage, add hidden markers
or extend ADR-068's hatch hooks to living entities. Any major semantic or new
platform interception decision requires its own ADR and owner confirmation.

The proposed event boundary also retains canceled-event immutability, unchanged
damage multiplier and no direct damage invocation. Finite nonnegative distance
uses double multiplication with Float.MAX_VALUE saturation; malformed distances
or factors leave the event unchanged. These requirements and native fall tests
are deferred, not waived. The fall ledger unit remains PLANNED.

## Evidence and authority

The [read-only task proposal](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-readiness-20261007/TASK-PROPOSAL-01.md)
and [primary inspection report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-readiness-20261007/REPORT-01.md)
bind source `7d7b474eb74a73d6cfb3ffa7271bc22f2f94b678`. Two permitted mapped-Forge
class reads establish gravity-attribute use and the cancellable fall event before
native damage; they do not prove transient serialization or runtime tests.

This technical leaf uses the existing conditional authority for independently
reviewed contracts without unresolved Critical/High/Medium. It introduces no
owner-level interaction, resource, API/HUD, schema or save-risk adjustment.
Independent contract review and explicit adoption precede source assignment;
no implementation or Gate authority is inferred from this draft alone.

## Integrator disposition, 2026-10-07

Root adopts only leaf A under the user's conditional authorization:
“授权无未解决 Critical/High/Medium 的审核定稿继续实现；重大语义调整仍另行确认”.
The [narrow final review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-contract-independent-review-20261007/REVIEW-03.md)
checks task generation `ea12f008...` and its three related progress records;
it establishes no new scoped Critical/High/Medium/Low. L2 is addressed in the
requirements, not claimed fixed in unimplemented source. Earlier reports stay
unchanged. This disposition does not adopt leaf B, resolve M1, amend ADR-068,
accept save refusal or change a ledger/Gate. Source assignment is limited to
the write scopes above after publication of this task; all actual tests and
committed-source evidence remain required.
