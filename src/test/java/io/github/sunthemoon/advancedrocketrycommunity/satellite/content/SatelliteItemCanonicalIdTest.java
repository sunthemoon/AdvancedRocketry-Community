package io.github.sunthemoon.advancedrocketrycommunity.satellite.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class SatelliteItemCanonicalIdTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @ParameterizedTest
    @ValueSource(ints = {118, 119, 128})
    void canonicalDefinitionIdsAreBoundedInBothSchemas(int bareLength) {
        for (int schema : List.of(1, 2)) {
            checkIdentity(schema, "definition_id", bareLength);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {118, 119, 128})
    void canonicalComponentIdsAreBounded(int bareLength) {
        checkIdentity(2, "components", bareLength);
    }

    private static void checkIdentity(int schema, String field, int bareLength) {
        String bare = "i".repeat(bareLength);
        String canonical = ResourceLocation.tryParse(bare).toString();
        assertEquals(bareLength + 10, canonical.length());
        for (String spelling : List.of(bare, canonical)) {
            CompoundTag data = new CompoundTag();
            data.putInt("schema_version", schema);
            data.putUUID("satellite_id", new UUID(1, 2));
            data.putUUID("owner_id", new UUID(3, 4));
            data.putString("definition_id", field.equals("definition_id") ? spelling : "test:survey");
            if (schema == 2) {
                data.putString("kind", "survey");
                ListTag components = new ListTag();
                components.add(StringTag.valueOf(field.equals("components") ? spelling : "test:component"));
                data.put("components", components);
            }
            CompoundTag root = new CompoundTag();
            root.put(SatelliteItemData.DATA_KEY, data);
            CompoundTag before = root.copy();
            var decoded = SatelliteItemData.readTag(root);
            assertEquals(before, root, "reading never rewrites or discards item data");
            if (canonical.length() > 128) {
                assertEquals(SatelliteItemData.DecodeStatus.INVALID, decoded.status(), field + " schema " + schema);
                assertTrue(decoded.identity().isEmpty());
            } else {
                assertEquals(SatelliteItemData.DecodeStatus.VALID, decoded.status());
                var identity = decoded.identity().orElseThrow();
                assertEquals(ResourceLocation.tryParse(canonical), field.equals("definition_id")
                        ? identity.definitionId() : identity.components().get(0));
                CompoundTag rewritten = new CompoundTag();
                SatelliteItemData.writeTag(rewritten, identity);
                assertEquals(schema, rewritten.getCompound(SatelliteItemData.DATA_KEY).getInt("schema_version"));
                assertEquals(decoded, SatelliteItemData.readTag(rewritten));
            }
        }
    }
}
