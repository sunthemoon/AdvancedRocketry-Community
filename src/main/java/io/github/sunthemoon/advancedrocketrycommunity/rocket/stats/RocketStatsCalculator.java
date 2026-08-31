package io.github.sunthemoon.advancedrocketrycommunity.rocket.stats;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

public final class RocketStatsCalculator {
    private RocketStatsCalculator() {
    }

    public static RocketStats calculate(
            Collection<RocketBlock> blocks,
            Function<RocketBlockState, RocketBlockMetrics> resolver
    ) {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(resolver, "resolver");
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("Cannot calculate stats for an empty rocket");
        }

        long mass = 0L;
        long thrust = 0L;
        long fuelCapacity = 0L;
        int engines = 0;
        int seats = 0;
        int guidance = 0;
        int blockEntities = 0;
        for (RocketBlock block : blocks) {
            RocketBlockMetrics metrics = Objects.requireNonNull(
                    resolver.apply(block.state()),
                    "resolver result"
            );
            mass = Math.addExact(mass, metrics.mass());
            thrust = Math.addExact(thrust, metrics.thrust());
            fuelCapacity = Math.addExact(fuelCapacity, metrics.fuelCapacity());
            engines = Math.addExact(engines, metrics.engine() ? 1 : 0);
            seats = Math.addExact(seats, metrics.seat() ? 1 : 0);
            guidance = Math.addExact(guidance, metrics.guidance() ? 1 : 0);
            blockEntities = Math.addExact(
                    blockEntities,
                    block.blockEntityPayload().isPresent() ? 1 : 0
            );
        }
        return new RocketStats(
                blocks.size(),
                mass,
                thrust,
                fuelCapacity,
                engines,
                seats,
                guidance,
                blockEntities
        );
    }
}
