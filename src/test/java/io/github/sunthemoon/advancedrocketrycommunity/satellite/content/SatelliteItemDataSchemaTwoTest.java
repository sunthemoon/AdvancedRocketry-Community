package io.github.sunthemoon.advancedrocketrycommunity.satellite.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-049 §6: data identities keep the schema-1 bytes; other kinds use schema 2 with their component list. */
final class SatelliteItemDataSchemaTwoTest {
    @Test
    void dataIdentitiesKeepSchemaOneAndNewKindsCarryComponents() {
        CompoundTag data = new CompoundTag();
        SatelliteItemData.writeTag(data, new SatelliteIdentity(UUID.randomUUID(), UUID.randomUUID(), SatelliteIds.DATA_SATELLITE));
        CompoundTag written = data.getCompound(SatelliteItemData.DATA_KEY);
        assertEquals(1, written.getInt("schema_version"));
        assertFalse(written.contains("kind"));
        assertFalse(written.contains("components"));

        List<ResourceLocation> components = List.of(id("a:chassis"), id("a:survey"), id("a:panel"), id("a:unit"));
        SatelliteIdentity survey = new SatelliteIdentity(UUID.randomUUID(), UUID.randomUUID(), id("a:survey_satellite"),
                SatelliteKind.SURVEY, components);
        CompoundTag root = new CompoundTag();
        SatelliteItemData.writeTag(root, survey);
        assertEquals(2, root.getCompound(SatelliteItemData.DATA_KEY).getInt("schema_version"));
        assertEquals(survey, SatelliteItemData.readTag(root).identity().orElseThrow());
    }

    @Test
    void malformedSchemaTwoIdentitiesAreInvalidAndFutureOnesArePreserved() {
        CompoundTag root = new CompoundTag();
        SatelliteItemData.writeTag(root, new SatelliteIdentity(UUID.randomUUID(), UUID.randomUUID(), id("a:m"),
                SatelliteKind.ASTEROID_MINER, List.of(id("a:chassis"), id("a:drill"))));
        for (var mutation : List.<java.util.function.Consumer<CompoundTag>>of(
                tag -> tag.putString("kind", "data"),
                tag -> tag.putString("kind", "orbital_laser"),
                tag -> tag.remove("components"),
                tag -> tag.put("components", new ListTag()),
                tag -> tag.put("components", tooMany()),
                tag -> {
                    ListTag list = new ListTag();
                    list.add(StringTag.valueOf("not an id"));
                    tag.put("components", list);
                },
                tag -> {
                    tag.putInt("schema_version", 1);
                })) {
            CompoundTag copy = root.copy();
            mutation.accept(copy.getCompound(SatelliteItemData.DATA_KEY));
            assertEquals(SatelliteItemData.DecodeStatus.INVALID, SatelliteItemData.readTag(copy).status());
        }
        CompoundTag future = root.copy();
        future.getCompound(SatelliteItemData.DATA_KEY).putInt("schema_version", 3);
        assertEquals(SatelliteItemData.DecodeStatus.FUTURE, SatelliteItemData.readTag(future).status());

        assertThrows(IllegalArgumentException.class, () -> new SatelliteIdentity(UUID.randomUUID(), UUID.randomUUID(),
                id("a:x"), SatelliteKind.DATA, List.of(id("a:chassis"))));
        assertThrows(IllegalArgumentException.class, () -> new SatelliteIdentity(UUID.randomUUID(), UUID.randomUUID(),
                id("a:x"), SatelliteKind.SOLAR, List.of()));
    }

    private static ListTag tooMany() {
        ListTag list = new ListTag();
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            ids.add("a:c" + index);
        }
        ids.forEach(raw -> list.add(StringTag.valueOf(raw)));
        return list;
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
