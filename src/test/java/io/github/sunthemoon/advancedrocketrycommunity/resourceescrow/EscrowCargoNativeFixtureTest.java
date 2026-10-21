package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoNativeFixtureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void nativeFixtureCountsEveryTankWithoutChangingGuidanceOrFuelStats() {
        for (boolean large : List.of(false, true)) {
            for (var tanks : List.of(List.of(FluidStack.EMPTY), List.of(new FluidStack(Fluids.WATER, 5000), FluidStack.EMPTY))) {
                var snapshot = EscrowCargoPreparationGameTests.snapshot(ResourceLocation.tryParse("minecraft:overworld"),
                        BlockPos.ZERO, large, tanks);
                assertEquals(tanks.size(), snapshot.stats().blockEntityCount());
                assertEquals(1, snapshot.stats().guidanceCount());
                assertEquals(2000, snapshot.stats().fuelCapacity());
                assertEquals(large ? 2048 : tanks.size() + 1, snapshot.blocks().size());
                assertEquals(tanks.size(), snapshot.blocks().stream().filter(block -> block.blockEntityPayload().isPresent()).count());
            }
        }
    }
}
