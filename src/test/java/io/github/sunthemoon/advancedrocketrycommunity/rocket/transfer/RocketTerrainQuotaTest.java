package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketTerrainQuotaTest {
    private static final ResourceLocation MOON = ResourceLocation.tryParse("advancedrocketrycommunity:moon");
    private static final RocketWarmupSettings SETTINGS = RocketWarmupSettings.DEFAULTS;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void preparationSharesBothQuotasWithoutAReservationOrWarmupTicket() {
        var f = new RocketWarmupRegistryTest(); var player = UUID.randomUUID();
        for (int i = 0; i < 8; i++) {
            assertTrue(f.registry.claimTerrain(UUID.randomUUID(), i < 2 ? player : null,
                    UUID.randomUUID(), 10, SETTINGS).isPresent());
        }
        assertEquals(8, f.registry.allocationCount()); assertEquals(2, f.registry.count(player));
        assertFalse(f.registry.canAllocate(UUID.randomUUID(), null, SETTINGS));
        assertFalse(f.registry.canAllocate(UUID.randomUUID(), player, SETTINGS));
        assertTrue(f.registry.claimTerrain(UUID.randomUUID(), null, UUID.randomUUID(), 10, SETTINGS).isEmpty());
        assertTrue(f.registry.live().isEmpty()); assertTrue(f.registry.reservations(UUID.randomUUID()).isEmpty());
        assertTrue(f.tickets.calls.isEmpty());
        var ready = f.new Rocket(null);
        assertEquals(RocketWarmupRegistry.Outcome.PROCEED,
                ready.decide(RocketWarmupRegistryTest.pad(MOON, 0), true, 11).outcome());
        assertTrue(f.registry.live().isEmpty(), "Ready admission cannot steal a reserved terrain slot");
    }

    @Test void completionPreservesCapturedStartRequestAndDeadlineWithoutBinding() {
        var f = new RocketWarmupRegistryTest(); var rocket = f.new Rocket(null); var request = UUID.randomUUID();
        var slot = f.registry.claimTerrain(rocket.logical, null, request, 100, new RocketWarmupSettings(8, 2, 200)).orElseThrow();
        assertTrue(f.registry.startedBy(request));
        assertTrue(f.registry.completeTerrain(slot, rocket.request(request, RocketWarmupRegistryTest.pad(MOON, 0)), 150));
        var warm = f.registry.warmup(rocket.logical).orElseThrow();
        assertEquals(100, warm.started()); assertEquals(200, warm.lifetime()); assertEquals(request, warm.request());
        assertEquals(RocketWarmupRegistry.State.WARMING, warm.state()); assertNull(warm.transfer());
        assertEquals(1, f.registry.allocationCount());
        assertEquals(List.of(warm.region()), f.registry.reservations(UUID.randomUUID()));
        f.registry.tick(299, new RocketWarmupRegistryTest.Probe());
        assertTrue(f.registry.warmup(rocket.logical).isPresent());
        f.registry.tick(300, new RocketWarmupRegistryTest.Probe());
        assertTrue(f.registry.live().isEmpty()); assertEquals(0, f.tickets.count(rocket.logical));
    }

    @Test void expiredAndStaleCompletionsCannotCreateAuthorityOrReleaseANewerSlot() {
        var f = new RocketWarmupRegistryTest(); var rocket = f.new Rocket(null); var id = UUID.randomUUID();
        var old = f.registry.claimTerrain(rocket.logical, null, id, 100, SETTINGS).orElseThrow();
        var request = rocket.request(id, RocketWarmupRegistryTest.pad(MOON, 0));
        assertFalse(f.registry.completeTerrain(old, request, 700));
        assertTrue(f.registry.releaseTerrain(old));
        var next = f.registry.claimTerrain(rocket.logical, null, UUID.randomUUID(), 700, SETTINGS).orElseThrow();
        assertFalse(f.registry.completeTerrain(old, request, 701)); assertFalse(f.registry.releaseTerrain(old));
        assertTrue(f.registry.startedBy(next.request())); assertEquals(1, f.registry.allocationCount());
        assertTrue(f.tickets.calls.isEmpty()); assertTrue(f.registry.reservations(UUID.randomUUID()).isEmpty());
        f.registry.clear(true, 701); assertEquals(0, f.registry.allocationCount());
    }

    @Test void boundLaunchCannotBeReplacedByTerrainPreparation() {
        var f = new RocketWarmupRegistryTest(); var rocket = f.new Rocket(null);
        var decision = rocket.decide(RocketWarmupRegistryTest.pad(MOON, 0), true, 1);
        f.registry.afterBegin(decision, true, 1);
        var calls = List.copyOf(f.tickets.calls);
        assertTrue(f.registry.claimTerrain(rocket.logical, null, UUID.randomUUID(), 2, SETTINGS).isEmpty());
        assertEquals(calls, f.tickets.calls);
        assertEquals(RocketWarmupRegistry.State.BOUND, f.registry.warmup(rocket.logical).orElseThrow().state());
    }

    @Test void terrainDemandPinsTheExistingFootprintAndCandidateBounds() {
        var columns = new HashSet<ChunkPos>();
        for (int i = 0; i < 16; i++) { columns.add(new ChunkPos(i, 0)); }
        var demand = RocketLandingPadSelection.awaiting(1, columns, 7);
        assertEquals(16, demand.terrainColumns().size()); assertFalse(demand.success());
        assertEquals(0, demand.chunksLoaded()); assertTrue(demand.snapshot().isEmpty());
        assertNull(demand.refusalKey());
        columns.add(new ChunkPos(16, 0));
        assertThrows(IllegalArgumentException.class, () -> RocketLandingPadSelection.awaiting(1, columns, 7));
        assertThrows(IllegalArgumentException.class, () -> RocketLandingPadSelection.awaiting(1, java.util.Set.of(new ChunkPos(0, 0)), 8));
    }
}
