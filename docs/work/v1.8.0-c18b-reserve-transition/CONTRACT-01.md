# C18b reserve transition 15: limited pure calculation contract

2026-10-08. Root proposal, not yet adopted. Current source association:
ead0ece21da8ccd189c4df5512e3a335b7f171ee. This is a two-file calculation/test
leaf under accepted ADR-066 D1, not acceptance of the full equipment drafts.
The 2026-10-07 oxygen implementation-readiness report recommends such a leaf;
the new leaf15 independent readiness investigation is a separate observation.

## Scope and interfaces

NEW production file:
src/main/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/life/SuitReserveTransition.java
and matching NEW src/test/java/.../atmosphere/life/SuitReserveTransitionTest.java.
No current caller, registry, service, configuration, NBT, network or asset changes.
Java 17; only existing pure life-support types, AtmosphereLimits and java.*.
No generic equipment or gas framework. Implementation and final source review
remain separate from this contract review.

The final utility has no instances or mutable state and declares:

```java
public static Result tick(PlayerLifeSupportInput input, int reserveUnits,
        int reserveCapacity, int targetUnits, boolean reserveEligible);
public record Result(PlayerLifeSupportDecision decision,
        int reserveUnits, int transferredUnits) { }
```

`input` is nonnull and already validated by its existing record constructor.
Reserve capacity is any nonnegative int, reserve balance is 0..capacity, and
target is 1..AtmosphereLimits.SUIT_OXYGEN_CAPACITY (2,000). These are scalar
calculation domains, not tank tiers, saved maximum sets or a chosen game config.
Invalid reserve/capacity/target throws IllegalArgumentException before calculation;
null input throws NullPointerException. No clamping or saturation of invalid data.
Result construction requires nonnull decision, nonnegative reserve balance and
transferredUnits in 0..2,000. These field checks do not certify an installed item
or the provenance of a manually constructed result.

## Calculation

The input active balance A is unchanged except for the existing engine's debit
and an admitted calculation-time transfer. Reserve R and target T are inputs.
Transfer may occur only when all are true: reserveEligible; complete suit;
base atmosphere not breathable; volume not BREATHABLE; original vacuumPhase
equals TICKS_PER_OXYGEN_OR_DAMAGE - 1. PENDING remains the engine's existing
nonbreathable state, not an additional refusal or exemption.

Then x = min(max(0, T - A), R); otherwise x = 0. Construct a new immutable
PlayerLifeSupportInput preserving every original field except oxygenUnits=A+x,
and call the unchanged PlayerLifeSupportEngine.tick exactly once. Return its
entire decision, reserveUnits=R-x and transferredUnits=x. No phase reset before
the engine and no separate duplicated protection, damage or debit algorithm.
At x=0 the engine's ordinary active-only decision is preserved, including its
scheduled debit; this is not an active no-op promise for reserve-ineligible ticks.
Integer arithmetic must not sum active plus the potentially int-max reserve.
The transfer itself is bounded by the 2,000 active buffer. Conservation checks
use long: original A+R equals decision.oxygenUnits+remaining R+oxygenConsumed.

reserveEligible is caller-supplied mathematical admission, not actual player or
item authority. The future adapter must separately prove nonexempt-player,
built-in current chest, enabled setting, root/native shape, capacity/config and
joint publication eligibility. Exempt-player handling remains outside this
helper, as in the existing service. A computed decision is not a committed
oxygen payment and must never grant live protection on failed publication.

## Verification

Actual JUnit tests cover scheduling across all 20 phases; each volume state;
ambient protection; complete, partial and missing suits; eligibility false;
active empty/partial/full; target below/equal/above active; reserve zero,
insufficient, exact and excess; reserve/capacity at Integer.MAX_VALUE; illegal
domain values and null input/result decision; result field validation; long
conservation and exact existing-engine decision equivalence. Tests include
successive scheduled transitions and preserve all existing engine assertions.
Inputs and comparison oracles are derived independently from the contract,
not by calling the implementation to generate expected data. Loops are finite.

Before source authoring Root records independent contract review/disposition,
publishes the limited contract and assigns a clean isolated worktree. The worker
writes only the two new source/test files and its new handoff, with no JVM or
Git writes. Root commits the returned source; a different agent reads actual
diff and reruns focused real JUnit via Gradle on measured eligible disks.
Root then performs applicable integrated build/data/native short regression.
Intermediate source-only results do not close a content unit or Gate.

## Authority and non-goals

The user delegates recommended technical decisions to Root (2026-10-08 chat:
"需要确认的点由你进行确认即可，按照你建议的方案来，全权交给你了"). The earlier
condition requires independent reviewed final contracts without unresolved
Critical/High/Medium; major semantic changes remain separately confirmed.
This leaf selects no tank balance, refill interaction, persistent key set,
workstation route, motion carrier, save veto, quarantine or R-021 risk.
It does not enable gameplay, a reserve writer, public provider expansion, D4,
charging, module install/removal, recipes, client summary, S1/S2/restart or V1/V2.
Existing API/HUD remains 2,000. Full equipment contracts stay proposed and their
independent findings remain open. The version remains IN_PROGRESS.
