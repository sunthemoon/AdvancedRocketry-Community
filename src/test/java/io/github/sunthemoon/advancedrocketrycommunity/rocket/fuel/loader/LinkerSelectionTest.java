package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTestTags.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LinkerSelectionTest {
    private static final ResourceLocation MOON = ResourceLocation.tryParse("advancedrocketrycommunity:moon");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aSelectionHoldsOneDeviceItsLevelAndItsPositionOnly() {
        assertEquals("arce_linker", LinkerSelection.KEY);
        var selection = new LinkerSelection(UUID.randomUUID(), MOON, new BlockPos(-5, 70, 9));
        assertEquals(Set.of("schema_version", "device", "level", "x", "y", "z"), selection.encode().getAllKeys());
        assertEquals(selection, LinkerSelection.decode(selection.encode()));
        var edge = new LinkerSelection(UUID.randomUUID(), ResourceLocation.tryParse("x:" + "a".repeat(126)),
                new BlockPos(29_999_999, 2_047, -30_000_000));
        assertEquals(edge, LinkerSelection.decode(edge.encode()));
        invalid(() -> new LinkerSelection(UUID.randomUUID(), MOON, new BlockPos(0, 2_048, 0)));
        assertThrows(NullPointerException.class, () -> new LinkerSelection(null, MOON, BlockPos.ZERO));
    }

    @Test void futureUnknownAndArbitraryDataIsInert() {
        CompoundTag valid = new LinkerSelection(UUID.randomUUID(), MOON, BlockPos.ZERO).encode();
        schemaVersions(valid, LinkerSelection::decode);
        CompoundTag entity = valid.copy(); entity.putInt("entity", 7);
        invalid(() -> LinkerSelection.decode(entity));
        CompoundTag list = valid.copy(); list.put("devices", new ListTag());
        invalid(() -> LinkerSelection.decode(list));
        CompoundTag nested = valid.copy(); nested.put("tag", new CompoundTag());
        invalid(() -> LinkerSelection.decode(nested));
        for (String key : new String[] {"device", "level", "x", "y", "z"}) {
            CompoundTag missing = valid.copy(); missing.remove(key);
            invalid(() -> LinkerSelection.decode(missing));
        }
        CompoundTag level = valid.copy(); level.putString("level", "moon");
        invalid(() -> LinkerSelection.decode(level));
        CompoundTag device = valid.copy(); device.putString("device", UUID.randomUUID().toString());
        invalid(() -> LinkerSelection.decode(device));
    }

    @Test void envelopeIsOneKibibyteDepthEightAndThirtyTwoNodes() {
        CompoundTag valid = new LinkerSelection(UUID.randomUUID(), MOON, BlockPos.ZERO).encode();
        bounds(valid, LinkerSelection::bounded, 1_024, 8, 32);
        invalid(() -> LinkerSelection.decode(padTo(valid, "pad", 1_025)));
    }
}
