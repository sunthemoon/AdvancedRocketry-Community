package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketBlockMetrics;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStatsCalculator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsionComponent.*;

/** Bounded, order-independent calculation over captured blocks only. */
public final class RocketPropulsionCalculator {
    public record Result(RocketStats stats, RocketPropulsion propulsion) { }
    private RocketPropulsionCalculator() { }

    public static Result calculate(List<RocketBlock> blocks,
            Function<RocketBlockState, RocketBlockMetrics> metrics,
            Function<RocketBlockState, RocketPropulsionComponent> classification,
            RocketPropulsion.Settings settings) {
        if (blocks.isEmpty() || blocks.size() > RocketLimits.MAX_BLOCKS) {
            throw new IllegalArgumentException("Invalid propulsion structure size");
        }
        try {
            var counts = new java.util.EnumMap<RocketPropulsionComponent, Long>(RocketPropulsionComponent.class);
            boolean externalPower = false;
            Map<RocketPosition, RocketPropulsionComponent> geometry = new HashMap<>();
            for (var block : blocks) {
                var kind = classification.apply(block.state());
                counts.merge(kind, 1L, Math::addExact);
                geometry.put(block.position(), kind);
                var value = metrics.apply(block.state());
                externalPower |= kind == EXTERNAL && (value.thrust() > 0 || value.fuelCapacity() > 0);
            }
            long mono = count(counts, BASIC_MONO) + count(counts, ADVANCED_MONO);
            long bip = count(counts, BIPROPELLANT) + count(counts, ADVANCED_BIPROPELLANT);
            long nuclear = count(counts, NUCLEAR);
            if ((mono > 0 ? 1 : 0) + (bip > 0 ? 1 : 0) + (nuclear > 0 ? 1 : 0) > 1) {
                refuse(RocketValidationCode.MIXED_PROPULSION_MODES);
            }
            var mode = bip > 0 ? RocketPropulsion.Mode.BIPROPELLANT : nuclear > 0
                    ? RocketPropulsion.Mode.NUCLEAR : RocketPropulsion.Mode.MONO;
            boolean typedTank = count(counts, BIPROPELLANT_TANK) + count(counts, OXIDIZER_TANK)
                    + count(counts, NUCLEAR_TANK) > 0;
            if (externalPower && (bip > 0 || nuclear > 0 || typedTank)) {
                refuse(RocketValidationCode.EXTERNAL_COMPONENT_ABSTRACT_ONLY);
            }
            if (mode != RocketPropulsion.Mode.MONO && count(counts, MONO_TANK) > 0
                    || mode != RocketPropulsion.Mode.BIPROPELLANT
                    && count(counts, BIPROPELLANT_TANK) + count(counts, OXIDIZER_TANK) > 0
                    || mode != RocketPropulsion.Mode.NUCLEAR
                    && count(counts, NUCLEAR_TANK) + count(counts, CORE) > 0) {
                refuse(RocketValidationCode.WRONG_PROPULSION_TANK_ROLE);
            }
            RocketStats raw = RocketStatsCalculator.calculate(blocks, metrics);
            if (externalPower || mono + bip + nuclear == 0) { mode = RocketPropulsion.Mode.ABSTRACT; }
            if (mode == RocketPropulsion.Mode.ABSTRACT
                    && raw.fuelCapacity() > (long) RocketLimits.MAX_BLOCKS * 1_000) {
                refuse(RocketValidationCode.FUEL_CAPACITY_EXCEEDED);
            }
            long primary = raw.fuelCapacity();
            long oxidizer = 0;
            long thrust = raw.thrust();
            long numerator = 1, denominator = 1;
            if (mode == RocketPropulsion.Mode.MONO || mode == RocketPropulsion.Mode.ABSTRACT) {
                long advancedBase = Math.multiplyExact(count(counts, ADVANCED_MONO), 5_000);
                long advanced = RocketPropulsionNumbers.scaleHostRounded(advancedBase, settings.thrustUnits());
                thrust = Math.addExact(Math.subtractExact(thrust, advancedBase), advanced);
                if (mode == RocketPropulsion.Mode.MONO && advancedBase > 0) {
                    primary = RocketPropulsionNumbers.scaleHostRounded(primary, settings.capacityUnits());
                }
                if (thrust > 0 && mode != RocketPropulsion.Mode.ABSTRACT) {
                    numerator = Math.subtractExact(Math.multiplyExact(thrust, 5), Math.multiplyExact(advanced, 2));
                    denominator = Math.multiplyExact(thrust, 5);
                }
            } else {
                long tanks = mode == RocketPropulsion.Mode.BIPROPELLANT ? count(counts, BIPROPELLANT_TANK)
                        : count(counts, NUCLEAR_TANK);
                primary = RocketPropulsionNumbers.scaleHostRounded(tanks * 1_000, settings.capacityUnits());
                oxidizer = RocketPropulsionNumbers.scaleHostRounded(count(counts, OXIDIZER_TANK) * 1_000,
                        settings.capacityUnits());
                if (primary == 0 || mode == RocketPropulsion.Mode.BIPROPELLANT && oxidizer == 0) {
                    refuse(RocketValidationCode.MISSING_PROPULSION_TANK);
                }
                if (mode == RocketPropulsion.Mode.BIPROPELLANT) {
                    long basic = RocketPropulsionNumbers.scaleHostRounded(count(counts, BIPROPELLANT) * 1_000,
                            settings.thrustUnits());
                    long advanced = RocketPropulsionNumbers.scaleHostRounded(count(counts, ADVANCED_BIPROPELLANT) * 5_000,
                            settings.thrustUnits());
                    thrust = Math.addExact(basic, advanced);
                    numerator = Math.addExact(Math.multiplyExact(basic, 5), Math.multiplyExact(advanced, 3));
                    denominator = Math.multiplyExact(thrust, 5);
                } else {
                    long connected = 0;
                    for (var block : blocks) {
                        if (geometry.get(block.position()) != NUCLEAR) { continue; }
                        if (!"down".equals(block.state().properties().getOrDefault("facing", "down"))) {
                            refuse(RocketValidationCode.NUCLEAR_CORE_SUPPORT_REQUIRED);
                        }
                        RocketPosition cursor = block.position().add(new RocketPosition(0, 1, 0));
                        // Every column belongs to one nozzle; the captured structure is the hard bound.
                        for (int step = 0; step < blocks.size() && geometry.get(cursor) == CORE; step++) {
                            connected++;
                            cursor = cursor.add(new RocketPosition(0, 1, 0));
                        }
                    }
                    if (connected == 0) { refuse(RocketValidationCode.NUCLEAR_CORE_SUPPORT_REQUIRED); }
                    // Capture each core's bounded support rating, then sum connected ratings.
                    long support = Math.multiplyExact(connected,
                            RocketPropulsionNumbers.scaleHostRounded(100_000, settings.coreUnits()));
                    var capped = RocketPropulsionNumbers.nuclearSupport(nuclear * 3_500, support);
                    thrust = RocketPropulsionNumbers.scaleHostRounded(capped.effectiveThrust(), settings.thrustUnits());
                    numerator = capped.workingNumerator(); denominator = capped.workingDenominator();
                }
            }
            long gcd = java.math.BigInteger.valueOf(numerator).gcd(java.math.BigInteger.valueOf(denominator)).longValueExact();
            var propulsion = new RocketPropulsion(mode, primary, oxidizer, numerator / gcd, denominator / gcd, settings);
            var stats = new RocketStats(raw.blockCount(), raw.mass(), thrust, primary, raw.engineCount(),
                    raw.seatCount(), raw.guidanceCount(), raw.blockEntityCount());
            return new Result(stats, propulsion);
        } catch (RocketSnapshotException refused) { throw refused;
        } catch (ArithmeticException overflow) {
            refuse(RocketValidationCode.PROPULSION_AGGREGATE_OVERFLOW); throw overflow;
        } catch (IllegalArgumentException invalid) {
            refuse(RocketValidationCode.PROPULSION_CAPACITY_EXCEEDED); throw invalid;
        }
    }
    private static long count(Map<RocketPropulsionComponent, Long> counts, RocketPropulsionComponent kind) {
        return counts.getOrDefault(kind, 0L);
    }
    private static void refuse(RocketValidationCode code) { throw new RocketSnapshotException(code, code.name()); }
}
