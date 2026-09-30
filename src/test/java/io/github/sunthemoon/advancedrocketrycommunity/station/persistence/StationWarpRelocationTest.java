package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData.CheckedUpdate;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-044 §3: a warp is one checked replacement that moves the orbit and debits the balance together. */
final class StationWarpRelocationTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.STATIONS;
    private static final ResourceLocation EARTH = ModIdentity.id("earth");
    private static final ResourceLocation MOON = ModIdentity.id("moon");
    private static final int BALANCE = 5_000_000;
    private static final int NEIGHBOR_BALANCE = 700_000;
    private static final int COST = 2_000_000;
    @TempDir Path root;
    private Path file;
    private StationRegistrySavedData data;
    private StationState station;
    private StationState neighbor;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @BeforeEach
    void createRegistry() {
        file = root.resolve(TYPE.fileName());
        data = new StationRegistrySavedData();
        station = commit("Warp");
        neighbor = commit("Neighbor");
        station = data.invite(station.stationId(), UUID.randomUUID());
        assertEquals(new StationRegistrySavedData.WarpCreditFold(BALANCE + NEIGHBOR_BALANCE, 0L),
                data.foldWarpCredits(Map.of(station.stationId(), BALANCE, neighbor.stationId(), NEIGHBOR_BALANCE)));
        CheckedSavedDataFile.replace(file, TYPE, () -> data.save(new CompoundTag()));
        data.setDirty(false);
    }

    @Test
    void successReplacesTheFileBeforePublishingTheOrbitAndDebitTogether() {
        assertEquals(CheckedUpdate.COMMITTED, data.checkedRelocation(file, station, MOON, COST, (from, to) -> {
            // The live registry keeps the observed orbit and balance while the candidate is committed.
            assertEquals(station, data.find(station.stationId()).orElseThrow());
            assertEquals(BALANCE, data.warpEnergy(station.stationId()));
            CheckedSavedDataFile.atomicMove(from, to);
            assertEquals(BALANCE, data.warpEnergy(station.stationId()));
        }));
        StationState published = data.find(station.stationId()).orElseThrow();
        assertEquals(station.withOrbitBody(MOON), published);
        assertTrue(published.isOrbitRelocationOf(station));
        assertEquals(station.region(), published.region());
        assertEquals(station.environment(), published.environment(), "Configured gravity does not follow the orbit");
        assertEquals(station.invitations(), published.invitations());
        assertEquals(BALANCE - COST, data.warpEnergy(station.stationId()));
        assertEquals(neighbor, data.find(neighbor.stationId()).orElseThrow());
        assertEquals(NEIGHBOR_BALANCE, data.warpEnergy(neighbor.stationId()));

        CompoundTag onDisk = CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow();
        assertEquals(data.save(new CompoundTag()), onDisk);
        StationRegistrySavedData restarted = StationRegistrySavedData.load(onDisk);
        assertEquals(published, restarted.find(station.stationId()).orElseThrow());
        assertEquals(data.warpEnergyBalances(), restarted.warpEnergyBalances());

        // Retrying the same request cannot charge again.
        assertEquals(CheckedUpdate.STALE, data.checkedRelocation(file, station, MOON, COST,
                CheckedSavedDataFile::atomicMove));
        assertEquals(BALANCE - COST, data.warpEnergy(station.stationId()));
        assertFalse(Files.exists(pending()));
    }

    @Test
    void insufficientEnergyAndSameOrbitWriteNothing() throws Exception {
        byte[] original = Files.readAllBytes(file);
        assertEquals(CheckedUpdate.INSUFFICIENT_ENERGY, data.checkedRelocation(file, station, MOON, BALANCE + 1,
                (from, to) -> {
                    throw new AssertionError("Replacement must not be attempted");
                }));
        assertEquals(CheckedUpdate.INSUFFICIENT_ENERGY, data.checkedRelocation(file, neighbor, MOON,
                NEIGHBOR_BALANCE + 1, CheckedSavedDataFile::atomicMove));
        assertEquals(CheckedUpdate.UNCHANGED, data.checkedRelocation(file, station, EARTH, COST,
                CheckedSavedDataFile::atomicMove));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertFalse(data.isDirty());
        assertEquals(station, data.find(station.stationId()).orElseThrow());
        assertEquals(BALANCE, data.warpEnergy(station.stationId()));
        for (int cost : new int[]{0, -1, StationLimits.MAX_WARP_ENERGY + 1}) {
            assertThrows(IllegalArgumentException.class, () -> data.checkedRelocation(file, station, MOON, cost,
                    CheckedSavedDataFile::atomicMove));
        }
    }

    @Test
    void spendingTheWholeBalanceRemovesItsEntry() {
        assertEquals(CheckedUpdate.COMMITTED, data.checkedRelocation(file, station, MOON, BALANCE,
                CheckedSavedDataFile::atomicMove));
        assertEquals(0, data.warpEnergy(station.stationId()));
        assertEquals(Map.of(neighbor.stationId(), NEIGHBOR_BALANCE), data.warpEnergyBalances());
        CompoundTag onDisk = CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow();
        assertEquals(1, onDisk.getList("warp_energy", CompoundTag.TAG_COMPOUND).size());
        assertEquals(data.save(new CompoundTag()), onDisk);
    }

    @Test
    void cutBeforeReplacementIsNoWarpAndNoCharge() {
        Path wrongName = root.resolve("stations-copy.dat");
        assertEquals(CheckedUpdate.WRITE_FAILED, data.checkedRelocation(wrongName, station, MOON, COST,
                (from, to) -> {
                    throw new AssertionError("Replacement must not be attempted");
                }));
        assertUnwarped();
        assertFalse(data.isDirty());
        assertFalse(data.updatesQuarantined());
        assertConsistentPair(CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow(), false);
    }

    @Test
    void refusedReplacementKeepsTheOldFileAndReassertsIt() throws Exception {
        byte[] original = Files.readAllBytes(file);
        assertEquals(CheckedUpdate.WRITE_FAILED, data.checkedRelocation(file, station, MOON, COST, (from, to) -> {
            throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "injected");
        }));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertUnwarped();
        assertTrue(data.isDirty(), "Ordinary saves must reassert the acknowledged authority");
        assertConsistentPair(data.save(new CompoundTag()), false);
        // The warp can be retried once storage works again, and is charged once.
        assertEquals(CheckedUpdate.COMMITTED, data.checkedRelocation(file, station, MOON, COST,
                CheckedSavedDataFile::atomicMove));
        assertConsistentPair(CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow(), true);
    }

    @Test
    void replacementReportedAsFailedButVerifiedOnDiskIsChargedOnce() {
        assertEquals(CheckedUpdate.COMMITTED, data.checkedRelocation(file, station, MOON, COST, (from, to) -> {
            CheckedSavedDataFile.atomicMove(from, to);
            throw new IOException("injected post-replacement error");
        }));
        assertEquals(station.withOrbitBody(MOON), data.find(station.stationId()).orElseThrow());
        assertEquals(BALANCE - COST, data.warpEnergy(station.stationId()));
        assertConsistentPair(CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow(), true);
        assertEquals(data.save(new CompoundTag()), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
    }

    @Test
    void unreadableOutcomeQuarantinesAndOrdinarySavesKeepTheOldPair() throws Exception {
        assertEquals(CheckedUpdate.OUTCOME_UNKNOWN, data.checkedRelocation(file, station, MOON, COST, (from, to) -> {
            Files.write(to, new byte[]{1, 2, 3});
            throw new IOException("injected torn replacement");
        }));
        assertTrue(data.updatesQuarantined());
        assertUnwarped();
        assertTrue(data.isDirty());
        assertConsistentPair(data.save(new CompoundTag()), false);
        assertEquals(CheckedUpdate.UNAVAILABLE, data.checkedRelocation(file, neighbor, MOON, 1,
                CheckedSavedDataFile::atomicMove));
        assertEquals(CheckedUpdate.UNAVAILABLE, data.checkedExpand(file, station, CheckedSavedDataFile::atomicMove));
        // The torn file itself is refused at the next start (fail closed); nothing half-applied is readable.
        assertThrows(RuntimeException.class, () -> CheckedSavedDataFile.readPayload(file, TYPE));
    }

    @Test
    void publishFailureAfterReplacementQuarantinesWithAConsistentPairOnBothSides() {
        UUID lateMember = UUID.randomUUID();
        assertEquals(CheckedUpdate.OUTCOME_UNKNOWN, data.checkedRelocation(file, station, MOON, COST, (from, to) -> {
            CheckedSavedDataFile.atomicMove(from, to);
            // Injected: the live station changes between the replacement and the publish.
            data.addMember(station.stationId(), lateMember);
        }));
        assertTrue(data.updatesQuarantined());
        // On disk: the warped and debited candidate (the state a crash before the next save would keep).
        assertConsistentPair(CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow(), true);
        // In memory: old orbit and old balance; the next ordinary save re-establishes that pair.
        assertEquals(EARTH, data.find(station.stationId()).orElseThrow().orbitBody());
        assertEquals(BALANCE, data.warpEnergy(station.stationId()));
        assertTrue(data.isDirty());
        assertConsistentPair(data.save(new CompoundTag()), false);
    }

    @Test
    void aFreeRelocationCannotUseTheGrowthOrGravityPath() throws Exception {
        byte[] before = Files.readAllBytes(file);
        assertThrows(IllegalArgumentException.class, () -> data.checkedReplace(file, station,
                station.withOrbitBody(MOON), (from, to) -> {
                    throw new AssertionError("Replacement must not be attempted");
                }));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertUnwarped();
    }

    @Test
    void growthAndGravityKeepEveryBalance() {
        assertEquals(CheckedUpdate.COMMITTED, data.checkedExpand(file, station, CheckedSavedDataFile::atomicMove));
        StationState expanded = data.find(station.stationId()).orElseThrow();
        assertEquals(CheckedUpdate.COMMITTED, data.checkedSetGravity(file, expanded, 250,
                CheckedSavedDataFile::atomicMove));
        StationRegistrySavedData restarted = StationRegistrySavedData.load(
                CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
        assertEquals(Map.of(station.stationId(), BALANCE, neighbor.stationId(), NEIGHBOR_BALANCE),
                restarted.warpEnergyBalances());
        assertEquals(data.warpEnergyBalances(), restarted.warpEnergyBalances());
        // A warp after growth keeps the grown region and the configured gravity.
        StationState grown = data.find(station.stationId()).orElseThrow();
        assertEquals(CheckedUpdate.COMMITTED, data.checkedRelocation(file, grown, MOON, COST,
                CheckedSavedDataFile::atomicMove));
        StationState warped = data.find(station.stationId()).orElseThrow();
        assertEquals(grown.region(), warped.region());
        assertEquals(250, warped.environment().gravityMilli());
    }

    @Test
    void modelPublishesOnlyAnExactRelocationWithTheObservedBalance() {
        StationRegistryModel model = new StationRegistryModel();
        model.restoreStation(station);
        model.restoreWarpEnergy(station.stationId(), BALANCE);
        StationState moon = station.withOrbitBody(MOON);
        assertThrows(IllegalArgumentException.class, () -> model.relocateChecked(station, station, BALANCE, COST));
        assertThrows(IllegalArgumentException.class,
                () -> model.relocateChecked(station, moon.withGravityMilli(1), BALANCE, COST));
        assertThrows(IllegalArgumentException.class,
                () -> model.relocateChecked(station, moon.withExpandedRegion(), BALANCE, COST));
        assertThrows(IllegalArgumentException.class,
                () -> model.relocateChecked(station, moon.withMember(UUID.randomUUID()), BALANCE, COST));
        assertThrows(IllegalArgumentException.class, () -> model.relocateChecked(station, moon, BALANCE, 0));
        assertThrows(IllegalArgumentException.class,
                () -> model.relocateChecked(station, moon, BALANCE, BALANCE + 1));
        assertThrows(IllegalStateException.class, () -> model.relocateChecked(station, moon, BALANCE - 1, COST));
        StationState changed = station.withMember(UUID.randomUUID());
        assertThrows(IllegalStateException.class,
                () -> model.relocateChecked(changed, changed.withOrbitBody(MOON), BALANCE, COST));
        assertEquals(station, model.find(station.stationId()).orElseThrow());
        assertEquals(BALANCE, model.warpEnergy(station.stationId()));
        assertEquals(moon, model.relocateChecked(station, moon, BALANCE, COST));
        assertEquals(BALANCE - COST, model.warpEnergy(station.stationId()));
        assertFalse(station.isOrbitRelocationOf(station));
        assertFalse(moon.withGravityMilli(1).isOrbitRelocationOf(station));
        assertTrue(station.isOrbitRelocationOf(moon), "Returning to the previous body is another relocation");
    }

    @Test
    void creditsAreCappedOwnedByCommittedStationsAndDroppedWithTheStation() {
        StationRegistryModel model = new StationRegistryModel();
        model.restoreStation(station);
        assertEquals(0, model.creditWarpEnergy(UUID.randomUUID(), 100));
        assertEquals(StationLimits.MAX_WARP_ENERGY - 100,
                model.creditWarpEnergy(station.stationId(), StationLimits.MAX_WARP_ENERGY - 100));
        assertEquals(100, model.creditWarpEnergy(station.stationId(), 500_000));
        assertEquals(0, model.creditWarpEnergy(station.stationId(), 1));
        assertEquals(StationLimits.MAX_WARP_ENERGY, model.warpEnergy(station.stationId()));
        assertThrows(IllegalArgumentException.class, () -> model.creditWarpEnergy(station.stationId(), -1));
        assertEquals(0, model.creditWarpEnergy(station.stationId(), 0));

        assertTrue(data.delete(station.stationId()).isPresent());
        assertEquals(0, data.warpEnergy(station.stationId()));
        assertEquals(Map.of(neighbor.stationId(), NEIGHBOR_BALANCE), data.warpEnergyBalances());
        StationState reused = commit("Reused");
        assertEquals(0, data.warpEnergy(reused.stationId()), "A new station never inherits a balance");
        assertEquals(data.warpEnergyBalances(),
                StationRegistrySavedData.load(data.save(new CompoundTag())).warpEnergyBalances());
    }

    @Test
    void foldIsOneOrdinaryMutationAndReportsRefusedCredits() {
        data.setDirty(false);
        assertEquals(new StationRegistrySavedData.WarpCreditFold(0L, 0L), data.foldWarpCredits(Map.of()));
        assertFalse(data.isDirty());
        UUID missing = UUID.randomUUID();
        var fold = data.foldWarpCredits(Map.of(station.stationId(), 250_000, missing, 40_000,
                neighbor.stationId(), StationLimits.MAX_WARP_ENERGY));
        assertEquals(250_000L + StationLimits.MAX_WARP_ENERGY - NEIGHBOR_BALANCE, fold.credited());
        assertEquals(40_000L + NEIGHBOR_BALANCE, fold.refused());
        assertTrue(data.isDirty(), "A fold marks the registry dirty for the ordinary save");
        assertEquals(BALANCE + 250_000, data.warpEnergy(station.stationId()));
        assertEquals(StationLimits.MAX_WARP_ENERGY, data.warpEnergy(neighbor.stationId()));
        assertEquals(0, data.warpEnergy(missing));

        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 99);
        StationRegistrySavedData blocked = StationRegistrySavedData.load(future);
        assertEquals(new StationRegistrySavedData.WarpCreditFold(0L, 7L),
                blocked.foldWarpCredits(Map.of(station.stationId(), 7)));
        assertFalse(blocked.isDirty());
    }

    @Test
    void aNewBalanceEntryIsRefusedWhenTheRegistryIsNearItsBound() {
        StationRegistrySavedData full = new StationRegistrySavedData();
        long limit = (long) StationLimits.MAX_REGISTRY_NBT_BYTES - StationLimits.WARP_ENERGY_HEADROOM_NBT_BYTES;
        UUID first = null;
        UUID last = null;
        int index = 0;
        while (StationNbtSize.uncompressedBytes(full.save(new CompoundTag())) <= limit) {
            for (int batch = 0; batch < 64; batch++, index++) {
                UUID stationId = new UUID(44, index);
                full.reserve(stationId, UUID.randomUUID(), "S".repeat(StationLimits.MAX_NAME_LENGTH), EARTH, 0);
                full.commit(stationId);
                for (int member = 0; member < StationLimits.MAX_MEMBERS; member++) {
                    full.invite(stationId, new UUID(45, index * 64L + member));
                    full.acceptInvitation(stationId, new UUID(45, index * 64L + member));
                    full.invite(stationId, new UUID(46, index * 64L + member));
                }
                if (first == null) {
                    first = stationId;
                    assertEquals(1L, full.foldWarpCredits(Map.of(stationId, 1)).credited());
                }
                last = stationId;
            }
        }
        assertTrue(StationNbtSize.uncompressedBytes(full.save(new CompoundTag())) <= StationLimits.MAX_REGISTRY_NBT_BYTES);
        var fold = full.foldWarpCredits(Map.of(first, 10, last, 20));
        assertEquals(10L, fold.credited(), "An existing entry is still credited");
        assertEquals(20L, fold.refused(), "A new entry is refused inside the headroom");
        assertEquals(11, full.warpEnergy(first));
        assertEquals(0, full.warpEnergy(last));
    }

    private void assertUnwarped() {
        assertEquals(station, data.find(station.stationId()).orElseThrow());
        assertEquals(BALANCE, data.warpEnergy(station.stationId()));
        assertEquals(NEIGHBOR_BALANCE, data.warpEnergy(neighbor.stationId()));
    }

    /** Every readable registry holds (old orbit, old balance) or (new orbit, debited balance), never a mix. */
    private void assertConsistentPair(CompoundTag payload, boolean warped) {
        StationRegistrySavedData decoded = StationRegistrySavedData.load(payload);
        assertTrue(decoded.operational());
        assertEquals(warped ? MOON : EARTH, decoded.find(station.stationId()).orElseThrow().orbitBody());
        assertEquals(warped ? BALANCE - COST : BALANCE, decoded.warpEnergy(station.stationId()));
        assertEquals(NEIGHBOR_BALANCE, decoded.warpEnergy(neighbor.stationId()));
        assertEquals(EARTH, decoded.find(neighbor.stationId()).orElseThrow().orbitBody());
    }

    private StationState commit(String name) {
        UUID stationId = UUID.randomUUID();
        data.reserve(stationId, UUID.randomUUID(), name, EARTH, 0);
        return data.commit(stationId);
    }

    private Path pending() {
        return root.resolve(TYPE.fileName() + ".arce-pending");
    }
}
