package io.github.sunthemoon.advancedrocketrycommunity.rocket.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class RocketStatsCalculatorTest {
    @Test
    void serverResolverProducesExactBoundedTotals() {
        List<RocketBlock> blocks = List.of(
                block("test:engine", null),
                block("test:tank", null),
                block("test:seat", new CompoundTag()),
                block("test:guidance", null),
                block("test:hull", null)
        );

        RocketStats stats = RocketStatsCalculator.calculate(blocks, state -> switch (state.blockId().getPath()) {
            case "engine" -> new RocketBlockMetrics(100, 1_000, 0, true, false, false);
            case "tank" -> new RocketBlockMetrics(50, 0, 500, false, false, false);
            case "seat" -> new RocketBlockMetrics(20, 0, 0, false, true, false);
            case "guidance" -> new RocketBlockMetrics(30, 0, 0, false, false, true);
            default -> RocketBlockMetrics.structural(10);
        });

        assertEquals(new RocketStats(5, 210, 1_000, 500, 1, 1, 1, 1), stats);
        assertTrue(stats.hasFlightComponents());
        assertTrue(stats.hasSufficientThrust());
    }

    @Test
    void insufficientThrustAndMissingComponentsRemainExplicit() {
        RocketStats stats = new RocketStats(1, 20, 10, 0, 1, 0, 0, 0);
        assertFalse(stats.hasFlightComponents());
        assertFalse(stats.hasSufficientThrust());
    }

    @Test
    void resolverCannotReturnNullOrNegativeContributions() {
        List<RocketBlock> blocks = List.of(block("test:hull", null));
        assertThrows(
                NullPointerException.class,
                () -> RocketStatsCalculator.calculate(blocks, ignored -> null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RocketBlockMetrics(0, 0, 0, false, false, false)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RocketBlockMetrics(1, 2, 0, false, false, false)
        );
    }

    private static RocketBlock block(String id, CompoundTag blockEntityData) {
        RocketBlockState state = new RocketBlockState(ResourceLocation.tryParse(id), Map.of());
        if (blockEntityData == null) {
            return new RocketBlock(new RocketPosition(0, 0, id.hashCode()), state);
        }
        return new RocketBlock(
                new RocketPosition(0, 0, id.hashCode()),
                state,
                new RocketBlockEntityPayload(
                        ResourceLocation.tryParse("advancedrocketrycommunity:test"),
                        blockEntityData
                )
        );
    }
}
