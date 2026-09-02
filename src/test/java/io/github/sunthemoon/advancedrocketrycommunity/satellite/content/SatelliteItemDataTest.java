package io.github.sunthemoon.advancedrocketrycommunity.satellite.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class SatelliteItemDataTest {
    @Test
    void blankAndBoundItemsDecodeWithoutImplicitMutation() {
        CompoundTag root = new CompoundTag();
        SatelliteIdentity identity = new SatelliteIdentity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SatelliteIds.DATA_SATELLITE
        );

        assertEquals(SatelliteItemData.DecodeStatus.EMPTY, SatelliteItemData.readTag(root).status());
        SatelliteItemData.writeTag(root, identity);

        SatelliteItemData.DecodeResult decoded = SatelliteItemData.readTag(root);
        assertEquals(SatelliteItemData.DecodeStatus.VALID, decoded.status());
        assertEquals(identity, decoded.identity().orElseThrow());
    }

    @Test
    void futureSchemaIsPreservedAndFailsClosed() {
        CompoundTag root = new CompoundTag();
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", SatelliteItemData.SCHEMA_VERSION + 1);
        future.putString("future_payload", "preserve-exactly");
        root.put(SatelliteItemData.DATA_KEY, future);
        CompoundTag before = root.copy();

        assertEquals(SatelliteItemData.DecodeStatus.FUTURE, SatelliteItemData.readTag(root).status());
        assertEquals(before, root);
    }

    @Test
    void malformedAndOversizedPayloadsFailClosed() {
        CompoundTag wrongType = new CompoundTag();
        wrongType.putString(SatelliteItemData.DATA_KEY, "not-a-compound");
        assertEquals(SatelliteItemData.DecodeStatus.INVALID, SatelliteItemData.readTag(wrongType).status());

        CompoundTag malformed = new CompoundTag();
        CompoundTag data = validData();
        data.putString("definition_id", "not a resource location");
        malformed.put(SatelliteItemData.DATA_KEY, data);
        assertEquals(SatelliteItemData.DecodeStatus.INVALID, SatelliteItemData.readTag(malformed).status());

        CompoundTag oversized = new CompoundTag();
        CompoundTag large = validData();
        large.putByteArray("padding", new byte[SatelliteLimits.MAX_RECORD_NBT_BYTES + 1]);
        oversized.put(SatelliteItemData.DATA_KEY, large);
        assertEquals(SatelliteItemData.DecodeStatus.INVALID, SatelliteItemData.readTag(oversized).status());
        assertTrue(oversized.contains(SatelliteItemData.DATA_KEY));
    }

    private static CompoundTag validData() {
        CompoundTag data = new CompoundTag();
        data.putInt("schema_version", SatelliteItemData.SCHEMA_VERSION);
        data.putUUID("satellite_id", UUID.randomUUID());
        data.putUUID("owner_id", UUID.randomUUID());
        data.putString("definition_id", ModIdentity.id("data_satellite").toString());
        return data;
    }
}
