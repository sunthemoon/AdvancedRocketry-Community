package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class OxygenVentPersistenceTest {
    @Test
    void schemaOneRoundTripsEveryFixedField() {
        CompoundTag parent = new CompoundTag();
        parent.put(OxygenVentPersistence.DATA_KEY, OxygenVentPersistence.encode(
                2, 1, 3_000, 20_000, 19
        ));

        OxygenVentPersistence.DecodeResult decoded = OxygenVentPersistence.decode(parent);

        assertEquals(OxygenVentPersistence.DecodeStatus.VALID, decoded.status());
        assertEquals(2, decoded.inputCount());
        assertEquals(1, decoded.outputCount());
        assertEquals(3_000, decoded.oxygenUnits());
        assertEquals(20_000, decoded.energy());
        assertEquals(19, decoded.oxygenPhase());
    }

    @Test
    void futureSchemaPayloadIsCopiedWithoutInterpretation() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 2);
        future.putString("future_payload", "keep-exactly");
        CompoundTag parent = new CompoundTag();
        parent.put(OxygenVentPersistence.DATA_KEY, future);

        OxygenVentPersistence.DecodeResult decoded = OxygenVentPersistence.decode(parent);

        assertEquals(OxygenVentPersistence.DecodeStatus.FUTURE, decoded.status());
        assertEquals(future, decoded.preservedFutureData());
        assertNotSame(future, decoded.preservedFutureData());
    }

    @Test
    void malformedOrOversizedFieldsRemainBlocking() {
        CompoundTag invalid = OxygenVentPersistence.encode(0, 0, 0, 0, 0);
        invalid.putInt("oxygen_units", AtmosphereLimits.VENT_OXYGEN_CAPACITY + 1);
        CompoundTag parent = new CompoundTag();
        parent.put(OxygenVentPersistence.DATA_KEY, invalid);

        OxygenVentPersistence.DecodeResult decoded = OxygenVentPersistence.decode(parent);

        assertEquals(OxygenVentPersistence.DecodeStatus.INVALID, decoded.status());
        assertEquals(invalid, decoded.preservedFutureData());
        assertThrows(IllegalArgumentException.class, () -> OxygenVentPersistence.encode(
                0, 0, AtmosphereLimits.VENT_OXYGEN_CAPACITY + 1, 0, 0
        ));
    }
}
