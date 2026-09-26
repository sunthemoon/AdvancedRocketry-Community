package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import java.util.UUID;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class PrecisionAssemblerPortMigrationPersistenceTest {
    private static final UUID MACHINE_ID = UUID.fromString("d1c149c5-a5c1-4a17-b51f-c1b42e97949e");

    @Test
    void markerRoundTripsMachineAndFixedChannel() {
        var marker = new PrecisionAssemblerPortMigrationPersistence.Marker(
                MACHINE_ID, PrecisionAssemblerChannels.output(1));
        CompoundTag encoded = PrecisionAssemblerPortMigrationPersistence.encode(marker);
        var decoded = PrecisionAssemblerPortMigrationPersistence.decode(parentWith(encoded));
        assertEquals(MultiblockNbtStatus.SUPPORTED, decoded.status());
        assertEquals(marker, decoded.value().orElseThrow());
    }

    @Test
    void unknownSchemaAndOversizedPayloadRemainPreserved() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 2);
        future.putString("future_payload", "keep");
        var decoded = PrecisionAssemblerPortMigrationPersistence.decode(parentWith(future));
        assertEquals(MultiblockNbtStatus.UNSUPPORTED_SCHEMA, decoded.status());
        assertEquals(future, decoded.preservedRoot().orElseThrow());
        assertNotSame(future, decoded.preservedRoot().orElseThrow());

        CompoundTag oversized = future.copy();
        oversized.put("payload", new ByteArrayTag(
                new byte[PrecisionAssemblerPortMigrationPersistence.MAX_ROOT_BYTES]));
        decoded = PrecisionAssemblerPortMigrationPersistence.decode(parentWith(oversized));
        assertEquals(MultiblockNbtStatus.INVALID_DATA, decoded.status());
        assertEquals(oversized, decoded.preservedRoot().orElseThrow());
    }

    @Test
    void malformedMarkersAndUnknownChannelsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new PrecisionAssemblerPortMigrationPersistence.Marker(MACHINE_ID, "item_input_99"));
        CompoundTag valid = PrecisionAssemblerPortMigrationPersistence.encode(
                new PrecisionAssemblerPortMigrationPersistence.Marker(
                        MACHINE_ID, PrecisionAssemblerChannels.input(0)));
        CompoundTag bad = valid.copy();
        bad.putString("machine_id", "not-a-uuid");
        assertInvalid(bad);
        bad = valid.copy();
        bad.putString("channel", "item_output_9");
        assertInvalid(bad);
        bad = valid.copy();
        bad.putString("extra", "blocked");
        assertInvalid(bad);
    }

    private static CompoundTag parentWith(Tag root) {
        CompoundTag parent = new CompoundTag();
        parent.put(PrecisionAssemblerPortMigrationPersistence.ROOT, root);
        return parent;
    }

    private static void assertInvalid(CompoundTag root) {
        var result = PrecisionAssemblerPortMigrationPersistence.decode(parentWith(root));
        assertEquals(MultiblockNbtStatus.INVALID_DATA, result.status());
        assertEquals(root, result.preservedRoot().orElseThrow());
    }
}
