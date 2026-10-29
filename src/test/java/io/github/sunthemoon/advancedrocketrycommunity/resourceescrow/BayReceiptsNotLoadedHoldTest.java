package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W128R-R2, R49 Q3: the NOT_LOADED part of the gate lock's bay part, on the real {@link BayReceipts} started from a real
 * receipt file that exists but did not load, over a real transfer journal. While the index is NOT_LOADED, exactly the
 * logical rockets with a PREPARED transfer record are held (only a rocket in flight can carry a blocking receipt), and
 * every rocket is held while the transfer journal itself is not operational. A proven-absent file holds none.
 */
final class BayReceiptsNotLoadedHoldTest {
    @TempDir Path temp;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void notLoadedHoldsOnlyPreparedRecordsAndEveryRocketWithoutAnOperationalJournal() throws Exception {
        Path file = temp.resolve("advancedrocketrycommunity_bay_receipts.dat");
        Files.write(file, new byte[] {0x1f, (byte) 0x8b, 0x08, 0x00, 0x13, 0x37}); // exists, and no root loads from it
        var presence = CheckedSavedDataFile.presence(file);
        assertNotEquals(CheckedSavedDataFile.Presence.ABSENT, presence);
        var receipts = receipts();
        var prepared = record(72); var spawned = record(1_072).destinationSpawned(UUID.randomUUID());
        UUID inFlight = prepared.logicalRocketId(), landing = spawned.logicalRocketId(), other = UUID.randomUUID();
        var journal = new RocketTransferSavedData();
        journal.put(prepared); journal.put(spawned);
        assertTrue(journal.operational());

        assertFalse(receipts.holds(inFlight, () -> journal), "before ServerStartingEvent the bay part is false");
        receipts.start(null, presence, file, 0); // the storage lookup returned no root: the file did not load
        assertEquals(EscrowAdmissionAuthority.Delegate.State.NOT_LOADED, receipts.state());
        assertTrue(receipts.holds(inFlight, () -> journal), "a PREPARED record's rocket is held while NOT_LOADED");
        assertTrue(receipts.holds(landing, () -> journal), "W196: a released receipt can lock an advanced transfer");
        assertTrue(receipts.holds(other, () -> journal), "W196: a released receipt can lock a landed rocket without T");

        CompoundTag invalid = new RocketTransferSavedData().save(new CompoundTag());
        invalid.putInt("schema_version", 99);
        var blocked = RocketTransferSavedData.load(invalid);
        assertFalse(blocked.operational(), "fixture: the journal did not load");
        for (UUID logical : List.of(inFlight, landing, other)) {
            assertTrue(receipts.holds(logical, () -> blocked), "every rocket is held while the transfer journal is not operational");
        }

        // Control: a proven-absent receipt file (NO_FILE) holds no rocket, whatever the journal says.
        var absent = receipts();
        absent.start(null, CheckedSavedDataFile.Presence.ABSENT, temp.resolve("absent.dat"), 0);
        assertEquals(EscrowAdmissionAuthority.Delegate.State.NO_FILE, absent.state());
        for (UUID logical : List.of(inFlight, landing, other)) {
            assertFalse(absent.holds(logical, () -> journal));
            assertFalse(absent.holds(logical, () -> blocked));
        }
    }

    private static BayReceipts receipts() {
        var authority = new EscrowAdmissionAuthority();
        authority.registerIndex(EscrowAdmissionAuthority.Consumer.PROPULSION, () -> new EscrowAdmissionAuthority.Index(true, List.of()));
        return new BayReceipts(authority.register(EscrowAdmissionAuthority.Consumer.BAY), CheckedSavedDataFile::atomicMove);
    }

    /** A PREPARED Earth-to-Moon record of a fresh logical rocket, landing at x = {@code arrivalX}. */
    private static RocketTransferRecord record(int arrivalX) {
        UUID transfer = UUID.randomUUID(), logical = UUID.randomUUID();
        var origin = new RocketPosition(8, 80, 8); var arrival = new RocketPosition(arrivalX, 80, 8);
        ResourceLocation iron = ResourceLocation.tryParse("minecraft:iron_block");
        var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(iron, Map.of())));
        var stats = new RocketStats(2, 200L, 1_000L, 1_000L, 1, 1, 1, 0);
        var source = RocketStructureSnapshot.create(UUID.randomUUID(), RocketFlightPlanner.EARTH.dimensionId(), origin, blocks,
                List.of(new RocketPosition(0, 1, 0)), stats, 0L);
        var destination = source.relocated(UUID.randomUUID(), RocketFlightPlanner.MOON.dimensionId(), arrival, 160L);
        var fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        var plan = RocketFlightPlanner.plan(stats, fuel, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, transfer, 0L).plan();
        var flight = RocketFlightData.initial(logical, stats.fuelCapacity(), stats.seatCount(), RocketFlightPlanner.EARTH.bodyId(),
                        RocketFlightPlanner.EARTH.dimensionId(), origin, 0L)
                .withFuel(fuel, 0L).withPassengers(RocketPassengerManifest.empty(1)).withPlan(plan).startCountdown(0L)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(transfer, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        var arrived = flight.arriveAtDestination(flight.fuel().debit(transfer, plan.requiredFuel()).state(),
                RocketFlightPlanner.MOON.bodyId(), RocketFlightPlanner.MOON.dimensionId(), arrival, 400L);
        return RocketTransferRecord.create(transfer, logical, UUID.randomUUID(), UUID.randomUUID(), source, destination, flight,
                arrived, plan.requiredFuel(), 0L);
    }
}
