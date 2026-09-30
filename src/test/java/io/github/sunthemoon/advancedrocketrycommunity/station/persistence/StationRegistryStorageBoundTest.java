package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Review F2/F3: growth stops before the registry can outgrow its 4 MiB bound (ordinary saves never
 * fail on size), balances use the reserved headroom, and a fold is validated as a whole.
 */
final class StationRegistryStorageBoundTest {
    private static final long GROWTH_LIMIT =
            (long) StationLimits.MAX_REGISTRY_NBT_BYTES - StationLimits.WARP_ENERGY_HEADROOM_NBT_BYTES;
    private static final String BOUND = "Station registry is at its storage bound";

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

        // Balances use the reserved headroom (WARP review R4): every station can hold an entry, so no
        // energy accepted by a core is refused for want of room.
        Map<UUID, Integer> credits = new LinkedHashMap<>();
        stations.subList(0, stations.size() - 1).forEach(stationId -> credits.put(stationId, 1_000));
        var fold = full.foldWarpCredits(credits);
        assertEquals(1_000L * (stations.size() - 1), fold.credited());
        long withBalances = StationNbtSize.uncompressedBytes(full.save(new CompoundTag()));
        assertTrue(withBalances > GROWTH_LIMIT && withBalances <= StationLimits.MAX_REGISTRY_NBT_BYTES,
                "Balances must fit inside the headroom: " + withBalances);
        UUID last = stations.get(stations.size() - 1);
        assertEquals(new StationRegistrySavedData.WarpCreditFold(30L, 0L),
                full.foldWarpCredits(Map.of(stations.get(0), 10, last, 20)),
                "An existing entry and a new one are both credited inside the headroom");
        long allBalances = StationNbtSize.uncompressedBytes(full.save(new CompoundTag()));
        assertTrue(allBalances <= StationLimits.MAX_REGISTRY_NBT_BYTES, "Balances crossed the bound: " + allBalances);
        StationRegistrySavedData restarted = StationRegistrySavedData.load(full.save(new CompoundTag()));
        assertTrue(restarted.operational());
        assertEquals(full.warpEnergyBalances(), restarted.warpEnergyBalances());
    }

    /**
     * WARP review R2 (M06): invitations alone reach the growth bound. With 4,096 minimal stations the
     * bound is reached through {@code invite} only, which must then refuse, and saves keep working.
     */
    @Test
    void invitationsAloneAreRefusedAtTheStorageBound() {
        StationRegistrySavedData data = new StationRegistrySavedData();
        List<UUID> stations = new ArrayList<>();
        for (int index = 0; index < StationLimits.MAX_STATIONS; index++) {
            UUID stationId = new UUID(51, index);
            data.reserve(stationId, UUID.randomUUID(), "I" + index, ModIdentity.id("earth"), 0);
            data.commit(stationId);
            stations.add(stationId);
        }
        long invited = 0;
        String refusal = null;
        outer:
        for (int slot = 0; slot < StationLimits.MAX_INVITATIONS; slot++) {
            for (int index = 0; index < stations.size(); index++) {
                try {
                    data.invite(stations.get(index), new UUID(52, (long) slot * StationLimits.MAX_STATIONS + index));
                    invited++;
                } catch (IllegalStateException refused) {
                    refusal = refused.getMessage();
                    break outer;
                }
            }
        }
        assertEquals("Station registry is at its storage bound", refusal, "Invitations must hit the growth bound");
        assertTrue(invited > 0);
        long encoded = StationNbtSize.uncompressedBytes(data.save(new CompoundTag()));
        assertTrue(encoded <= GROWTH_LIMIT, "Invitations passed the headroom: " + encoded);
        assertTrue(StationRegistrySavedData.load(data.save(new CompoundTag())).operational());
    }

    /**
     * WARP review R8 (M06, M18, M20): at the bound every growth path is refused on its own, before it
     * changes anything: reserve, commit, addMember, invite, acceptInvitation, transferOwnership and a
     * relocation to a longer orbit ID.
     */
    @Test
    void everyGrowthPathIsRefusedOnItsOwnAtTheBound(@TempDir Path root) {
        StationRegistrySavedData data = new StationRegistrySavedData();
        UUID spare = new UUID(61, 0);
        UUID reservation = new UUID(61, 1);
        UUID member = new UUID(62, 0);
        UUID invited = new UUID(62, 1);
        data.reserve(spare, UUID.randomUUID(), "Spare", ModIdentity.id("earth"), 0);
        data.commit(spare);
        data.invite(spare, member);
        data.acceptInvitation(spare, member);
        data.invite(spare, invited);
        data.reserve(reservation, UUID.randomUUID(), "Reserved", ModIdentity.id("earth"), 0);
        List<UUID> stations = new ArrayList<>();
        for (int index = 0; index < StationLimits.MAX_STATIONS - 8; index++) {
            UUID stationId = new UUID(63, index);
            data.reserve(stationId, UUID.randomUUID(), "G" + index, ModIdentity.id("earth"), 0);
            data.commit(stationId);
            stations.add(stationId);
        }
        // Team entries only (64 bytes of growth each) until one is refused.
        String refusal = null;
        long entry = 0;
        fill:
        for (int slot = 0; slot < 2 * StationLimits.MAX_INVITATIONS; slot++) {
            for (UUID stationId : stations) {
                UUID player = new UUID(64, entry++);
                try {
                    if (slot < StationLimits.MAX_INVITATIONS) {
                        data.invite(stationId, player);
                    } else {
                        data.addMember(stationId, player);
                    }
                } catch (IllegalStateException refused) {
                    refusal = refused.getMessage();
                    break fill;
                }
            }
        }
        assertEquals(BOUND, refusal, "The fill never reached the bound");
        // A balance entry still fits inside the headroom (R4).
        assertEquals(new StationRegistrySavedData.WarpCreditFold(5_000_000L, 0L),
                data.foldWarpCredits(Map.of(spare, 5_000_000)));
        StationState observed = data.find(spare).orElseThrow();
        CompoundTag before = data.save(new CompoundTag());
        refused(() -> data.reserve(new UUID(61, 2), UUID.randomUUID(), "Late", ModIdentity.id("earth"), 0));
        refused(() -> data.commit(reservation));
        refused(() -> data.addMember(spare, new UUID(62, 2)));
        refused(() -> data.invite(spare, new UUID(62, 3)));
        refused(() -> data.acceptInvitation(spare, invited));
        refused(() -> data.transferOwnership(spare, member));
        ResourceLocation longer = ModIdentity.id("b".repeat(100));
        refused(() -> data.checkedRelocation(root.resolve(ManagedSavedDataType.STATIONS.fileName()), observed,
                longer, 1_000, CheckedSavedDataFile::atomicMove));
        assertEquals(before, data.save(new CompoundTag()), "A refused growth changed the registry");
        assertTrue(StationRegistrySavedData.load(before).operational());
    }

    private static void refused(Executable growth) {
        IllegalStateException refused = assertThrows(IllegalStateException.class, growth);
        assertEquals(BOUND, refused.getMessage());
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
