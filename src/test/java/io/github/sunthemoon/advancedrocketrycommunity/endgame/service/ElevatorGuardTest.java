package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.model.ElevatorPair;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.OutboxEntry;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitPayload;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorStationGuard;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-059 section 5 from the endgame side: what pins a station against a warp and against its deletion. */
final class ElevatorGuardTest {
    private static final ResourceLocation RAILGUN = ResourceLocation.tryBuild("advancedrocketrycommunity", "railgun");
    private static final ResourceLocation ANCHOR = ResourceLocation.tryBuild("advancedrocketrycommunity",
            "elevator_anchor");
    private static final ResourceLocation TERMINAL = ResourceLocation.tryBuild("advancedrocketrycommunity",
            "elevator_terminal");
    private static final ResourceLocation MOON = ResourceLocation.tryBuild("advancedrocketrycommunity", "moon");
    private static final UUID OWNER = new UUID(1L, 1L);

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void pairsPinAgainstWarpsAndCargoOfTheRegionAgainstDeletion() {
        EndgameService service = new EndgameService(() -> EndgameSettings.DEFAULTS, Set::of,
                () -> TransitLimits.DEFAULTS);
        ElevatorGuard guard = new ElevatorGuard(service);
        ResourceLocation space = CelestialIds.SPACE_LEVEL.location();
        StationRegistryModel stations = new StationRegistryModel();
        UUID stationId = UUID.randomUUID();
        stations.reserve(stationId, OWNER, "Guarded", CelestialIds.MOON_ID, 0L);
        StationState station = stations.commit(stationId);
        assertEquals(ElevatorStationGuard.Decision.UNAVAILABLE, guard.warp(null, station), "no root: fail closed");
        assertEquals(ElevatorStationGuard.Decision.UNAVAILABLE, guard.delete(null, station));
        service.startForTest(EndgameSavedData.create(), true);
        assertEquals(ElevatorStationGuard.Decision.ALLOWED, guard.warp(null, station));
        assertEquals(ElevatorStationGuard.Decision.ALLOWED, guard.delete(null, station));

        long inside = new BlockPos(station.landingPad().x() + 6, 128, station.landingPad().z() + 6).asLong();
        long outside = new BlockPos(station.region().maximumX() + 1, 128, station.region().maximumZ()).asLong();
        UUID terminal = UUID.randomUUID();
        UUID anchor = UUID.randomUUID();
        UUID railgunIn = UUID.randomUUID();
        UUID railgunOut = UUID.randomUUID();
        service.barrier(root -> {
            root.register(terminal, TERMINAL, OWNER, space, inside, false, 2048, 64);
            root.register(anchor, ANCHOR, OWNER, MOON, 0L, false, 2048, 64);
            root.register(railgunIn, RAILGUN, OWNER, space, inside + 1, false, 2048, 64);
            root.register(railgunOut, RAILGUN, OWNER, space, outside, false, 2048, 64);
            return null;
        });
        ElevatorPair pair = new ElevatorPair(UUID.randomUUID(), stationId, terminal, anchor, CelestialIds.MOON_ID, MOON,
                0, 0, 64, 0L, OWNER);
        service.barrier(root -> {
            root.pairs().add(pair);
            return null;
        });
        assertEquals(ElevatorStationGuard.Decision.BOUND, guard.warp(null, station), "a bound station");
        assertEquals(ElevatorStationGuard.Decision.BOUND, guard.delete(null, station));
        service.barrier(root -> root.pairs().remove(pair.pairId()));
        assertEquals(ElevatorStationGuard.Decision.ALLOWED, guard.warp(null, station), "after the unbind");

        TransitPayload payload = TransitPayload.of(List.of(new ItemStack(Items.IRON_INGOT))).orElseThrow();
        service.barrier(root -> {
            root.registerTransit(TransitRecord.registered(railgunOut, new OutboxEntry(1L, railgunOut, payload, 1, 20,
                    EndgameSystem.RAILGUN), OWNER, root.saveEpoch(), 0L));
            return null;
        });
        assertEquals(ElevatorStationGuard.Decision.ALLOWED, guard.delete(null, station), "cargo outside the region");
        service.barrier(root -> {
            root.registerTransit(TransitRecord.registered(railgunOut, new OutboxEntry(2L, railgunIn, payload, 1, 20,
                    EndgameSystem.RAILGUN), OWNER, root.saveEpoch(), 0L));
            return null;
        });
        assertEquals(ElevatorStationGuard.Decision.BOUND, guard.delete(null, station), "cargo for the region");
        assertEquals(ElevatorStationGuard.Decision.ALLOWED, guard.warp(null, station), "cargo does not pin a warp");
        service.barrier(root -> root.remove(railgunIn));
        assertEquals(ElevatorStationGuard.Decision.BOUND, guard.delete(null, station),
                "a removed destination's tombstone still stands in the region");
    }
}
