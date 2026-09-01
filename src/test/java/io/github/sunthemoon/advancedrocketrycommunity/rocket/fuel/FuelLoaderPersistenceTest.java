package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class FuelLoaderPersistenceTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000651");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000652");

    @Test
    void missingPayloadStartsEmptyAndUnclaimed() {
        FuelLoaderPersistence.DecodeResult decoded = FuelLoaderPersistence.decode(new CompoundTag());

        assertEquals(FuelLoaderPersistence.DecodeStatus.VALID, decoded.status());
        assertEquals(FuelLoaderPersistence.ItemState.EMPTY, decoded.itemState());
        assertEquals(0L, decoded.bufferedUnits());
        assertNull(decoded.ownerId());
        assertNull(decoded.targetRocketId());
    }

    @Test
    void activeBufferRoundTripsWithOwnerAndTarget() {
        CompoundTag parent = new CompoundTag();
        parent.put(FuelLoaderPersistence.DATA_KEY, FuelLoaderPersistence.encode(
                FuelLoaderPersistence.ItemState.EMPTY,
                RocketFlightLimits.FUEL_CELL_UNITS,
                OWNER,
                TARGET
        ));

        FuelLoaderPersistence.DecodeResult decoded = FuelLoaderPersistence.decode(parent);

        assertEquals(FuelLoaderPersistence.DecodeStatus.VALID, decoded.status());
        assertEquals(RocketFlightLimits.FUEL_CELL_UNITS, decoded.bufferedUnits());
        assertEquals(OWNER, decoded.ownerId());
        assertEquals(TARGET, decoded.targetRocketId());
    }

    @Test
    void futureAndMalformedShapesArePreservedAndBlocked() {
        CompoundTag futureParent = new CompoundTag();
        CompoundTag future = FuelLoaderPersistence.encode(
                FuelLoaderPersistence.ItemState.FUEL_CELL,
                0L,
                OWNER,
                null
        );
        future.putInt("schema_version", FuelLoaderPersistence.SCHEMA_VERSION + 1);
        future.putString("opaque", "future");
        futureParent.put(FuelLoaderPersistence.DATA_KEY, future);

        CompoundTag malformedParent = new CompoundTag();
        CompoundTag malformed = FuelLoaderPersistence.encode(
                FuelLoaderPersistence.ItemState.EMPTY,
                0L,
                OWNER,
                null
        );
        malformed.putLong("buffered_units", RocketFlightLimits.FUEL_CELL_UNITS + 1L);
        malformedParent.put(FuelLoaderPersistence.DATA_KEY, malformed);

        FuelLoaderPersistence.DecodeResult futureDecoded = FuelLoaderPersistence.decode(futureParent);
        FuelLoaderPersistence.DecodeResult malformedDecoded = FuelLoaderPersistence.decode(malformedParent);

        assertEquals(FuelLoaderPersistence.DecodeStatus.FUTURE, futureDecoded.status());
        assertEquals(future, futureDecoded.preservedData());
        assertEquals(FuelLoaderPersistence.DecodeStatus.INVALID, malformedDecoded.status());
        assertEquals(malformed, malformedDecoded.preservedData());
    }

    @Test
    void bufferedFuelCannotCoexistWithAnInventoryItem() {
        CompoundTag parent = new CompoundTag();
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("schema_version", FuelLoaderPersistence.SCHEMA_VERSION);
        malformed.putInt("item_state", FuelLoaderPersistence.ItemState.FUEL_CELL.networkId());
        malformed.putLong("buffered_units", 1L);
        malformed.putUUID("owner_id", OWNER);
        parent.put(FuelLoaderPersistence.DATA_KEY, malformed);

        FuelLoaderPersistence.DecodeResult decoded = FuelLoaderPersistence.decode(parent);

        assertEquals(FuelLoaderPersistence.DecodeStatus.INVALID, decoded.status());
        assertEquals(malformed, decoded.preservedData());
    }
}
