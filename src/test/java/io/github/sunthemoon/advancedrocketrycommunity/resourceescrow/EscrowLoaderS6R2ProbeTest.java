package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6R2ProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void exactSelectorsRequireReleaseHooks() {
        for (String cut : EscrowLoaderS6R2Probe.CASES) {
            assertTrue(EscrowLoaderS6R2Probe.selected(true, cut));
            assertTrue(EscrowLoaderS6Probe.selected(true, cut));
            assertFalse(EscrowLoaderS6R2Probe.selected(false, cut));
            assertFalse(EscrowLoaderS6Probe.selected(false, cut));
            assertFalse(EscrowLoaderS6R2Probe.selected(true, cut + "_OTHER"));
        }
        assertFalse(EscrowLoaderS6R2Probe.selected(true, null));
        assertFalse(EscrowLoaderS6R2Probe.selected(true, "LF_M_K7"));
    }

    @Test void staleRecordNamesTheRealSourceAndAnEmptyDistinctDestination() {
        var propulsion = FluidFuelLoaderEscrowFixtures.mono(2_000);
        var state = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        var source = RocketStructureSnapshot.create(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(64, 100, 64), List.of(new RocketBlock(new RocketPosition(0, 0, 0), state),
                        new RocketBlock(new RocketPosition(0, 1, 0), state)), List.of(new RocketPosition(0, 1, 0)),
                new RocketStats(2, 100, 1_000, 2_000, 1, 1, 1, 0), 0, propulsion);
        var fuel = RocketFuelState.empty(propulsion).withFluidBank(false, FluidFuelLoaderEscrowFixtures.ROCKET_FUEL, null, 1_000);
        var physical = UUID.randomUUID(); var logical = UUID.randomUUID(); var owner = UUID.randomUUID();
        for (String cut : EscrowLoaderS6R2Probe.CASES) {
            var record = EscrowLoaderS6R2Probe.stale(cut, source, physical, logical, owner, fuel);
            assertSame(source, record.sourceSnapshot());
            assertEquals(physical, record.sourceEntityId());
            assertEquals(logical, record.logicalRocketId());
            assertEquals(owner, record.ownerId());
            assertEquals(fuel, record.sourceFlightData().fuel());
            assertEquals(RocketTransferPhase.PREPARED, record.phase());
            assertEquals(new RocketPosition(72, 100, 64), record.destinationSnapshot().sourceOrigin());
            assertNotEquals(source.snapshotId(), record.destinationSnapshot().snapshotId());
            assertEquals(0, record.destinationFlightData().fuel().propulsion().orElseThrow().primary().amount());
            assertEquals(cut.equals(EscrowLoaderS6R2Probe.RECONNECT) ? 1 : 0, record.sourceFlightData().passengers().assignments().size());
        }
    }
}
