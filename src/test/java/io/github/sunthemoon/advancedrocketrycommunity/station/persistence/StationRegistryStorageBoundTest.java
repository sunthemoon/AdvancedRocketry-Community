package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Review F2/F3: growth stops before the registry can outgrow its 4 MiB bound (ordinary saves never
 * fail on size), balances use the reserved headroom, and a fold is validated as a whole.
 */
final class StationRegistryStorageBoundTest {
    private static final long GROWTH_LIMIT =
            (long) StationLimits.MAX_REGISTRY_NBT_BYTES - StationLimits.WARP_ENERGY_HEADROOM_NBT_BYTES;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void growthStopsAtTheBalanceHeadroomAndSavesKeepWorking() {
        StationRegistrySavedData full = new StationRegistrySavedData();
        List<UUID> stations = new ArrayList<>();
        int refusals = 0;
        int index = 0;
        // Fully populated records (maximum name, 32 members, 32 invitations) until growth is refused.
        while (refusals == 0 && index < StationLimits.MAX_STATIONS) {
            UUID stationId = new UUID(44, index);
            try {
                full.reserve(stationId, UUID.randomUUID(), "S".repeat(StationLimits.MAX_NAME_LENGTH),
                        ModIdentity.id("earth"), 0);
                full.commit(stationId);
                stations.add(stationId);
                for (int member = 0; member < StationLimits.MAX_MEMBERS; member++) {
                    UUID joining = new UUID(45, index * 64L + member);
                    full.invite(stationId, joining);
                    full.acceptInvitation(stationId, joining);
                    full.invite(stationId, new UUID(46, index * 64L + member));
                }
            } catch (IllegalStateException refused) {
                assertEquals("Station registry is at its storage bound", refused.getMessage());
                refusals++;
            }
            index++;
        }
        assertEquals(1, refusals, "Growth must be refused before the station limit");
        assertTrue(stations.size() < StationLimits.MAX_STATIONS);
        long encoded = StationNbtSize.uncompressedBytes(full.save(new CompoundTag()));
        assertTrue(encoded <= GROWTH_LIMIT, "Growth passed the headroom: " + encoded);
        assertThrows(IllegalStateException.class, () -> full.reserve(UUID.randomUUID(), UUID.randomUUID(), "Late",
                ModIdentity.id("earth"), 0), "Creation is refused too");

        // Balances use the reserved headroom: one fold may add many entries, the next new entry is refused.
        Map<UUID, Integer> credits = new LinkedHashMap<>();
        stations.subList(0, stations.size() - 1).forEach(stationId -> credits.put(stationId, 1_000));
        var fold = full.foldWarpCredits(credits);
        assertEquals(1_000L * (stations.size() - 1), fold.credited());
        long withBalances = StationNbtSize.uncompressedBytes(full.save(new CompoundTag()));
        assertTrue(withBalances > GROWTH_LIMIT && withBalances <= StationLimits.MAX_REGISTRY_NBT_BYTES,
                "Balances must fit inside the headroom: " + withBalances);
        UUID last = stations.get(stations.size() - 1);
        assertEquals(new StationRegistrySavedData.WarpCreditFold(10L, 20L),
                full.foldWarpCredits(Map.of(stations.get(0), 10, last, 20)),
                "An existing entry is credited; a new one is refused inside the headroom");
        StationRegistrySavedData restarted = StationRegistrySavedData.load(full.save(new CompoundTag()));
        assertTrue(restarted.operational());
        assertEquals(full.warpEnergyBalances(), restarted.warpEnergyBalances());
    }

    @Test
    void aFoldIsValidatedAsAWholeBeforeAnythingIsCredited() {
        StationRegistrySavedData data = new StationRegistrySavedData();
        UUID first = new UUID(1, 1);
        UUID second = new UUID(1, 2);
        for (UUID stationId : List.of(first, second)) {
            data.reserve(stationId, UUID.randomUUID(), "Fold", ModIdentity.id("earth"), 0);
            data.commit(stationId);
        }
        data.setDirty(false);
        Map<UUID, Integer> negative = new LinkedHashMap<>();
        negative.put(first, 100);
        negative.put(second, -1);
        Map<UUID, Integer> nullValue = new HashMap<>();
        nullValue.put(first, 100);
        nullValue.put(second, null);
        Map<UUID, Integer> tooLarge = new LinkedHashMap<>();
        tooLarge.put(first, 100);
        tooLarge.put(second, StationLimits.MAX_WARP_ENERGY + 1);
        Map<UUID, Integer> nullKey = new HashMap<>();
        nullKey.put(first, 100);
        nullKey.put(null, 1);
        for (Map<UUID, Integer> invalid : List.of(negative, nullValue, tooLarge, nullKey)) {
            assertThrows(IllegalArgumentException.class, () -> data.foldWarpCredits(invalid));
            assertEquals(0, data.warpEnergy(first), "A partial fold credited the first station");
            assertTrue(!data.isDirty());
        }
        Map<UUID, Integer> tooMany = new HashMap<>();
        for (int index = 0; index <= StationLimits.MAX_PENDING_WARP_CREDITS; index++) {
            tooMany.put(new UUID(2, index), 1);
        }
        assertThrows(IllegalArgumentException.class, () -> data.foldWarpCredits(tooMany));
        assertEquals(new StationRegistrySavedData.WarpCreditFold(100L, 0L), data.foldWarpCredits(Map.of(first, 100)));
    }
}
