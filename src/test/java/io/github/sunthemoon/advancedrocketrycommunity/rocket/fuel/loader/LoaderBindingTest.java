package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTestTags.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoaderBindingTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.tryParse("minecraft:overworld");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static LoaderBinding binding(UUID station) {
        return new LoaderBinding(UUID.randomUUID(), OVERWORLD, UUID.randomUUID(), new BlockPos(12, 64, -7), station);
    }

    @Test void bindingsRoundTripWithAndWithoutAStation() {
        var plain = binding(null);
        assertEquals(Set.of("schema_version", "logical_rocket", "level", "physical_rocket", "x", "y", "z"), plain.encode().getAllKeys());
        assertEquals(plain, LoaderBinding.decode(plain.encode()));
        var station = binding(UUID.randomUUID());
        assertTrue(station.encode().contains("station"));
        assertEquals(station, LoaderBinding.decode(station.encode()));
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(1, 2, 3);
        var copied = new LoaderBinding(UUID.randomUUID(), OVERWORLD, UUID.randomUUID(), mutable, null);
        mutable.set(4, 5, 6);
        assertEquals(new BlockPos(1, 2, 3), copied.locator());
    }

    @Test void levelIdsAndLocatorsKeepTheirLimits() {
        ResourceLocation longest = ResourceLocation.tryParse("x:" + "a".repeat(126));
        var longestBinding = new LoaderBinding(UUID.randomUUID(), longest, UUID.randomUUID(),
                new BlockPos(-30_000_000, -2_048, 29_999_999), UUID.randomUUID());
        assertTrue(size(longestBinding.encode()).bytes() <= LoaderBinding.MAX_BYTES);
        assertEquals(longestBinding, LoaderBinding.decode(longestBinding.encode()));
        invalid(() -> new LoaderBinding(UUID.randomUUID(), ResourceLocation.tryParse("x:" + "a".repeat(127)),
                UUID.randomUUID(), BlockPos.ZERO, null));
        for (BlockPos outside : new BlockPos[] {new BlockPos(30_000_000, 0, 0), new BlockPos(0, 0, -30_000_001),
                new BlockPos(0, 2_048, 0), new BlockPos(0, -2_049, 0)}) {
            invalid(() -> new LoaderBinding(UUID.randomUUID(), OVERWORLD, UUID.randomUUID(), outside, null));
        }
        CompoundTag valid = binding(null).encode();
        for (String level : new String[] {"overworld", "Minecraft:overworld", "minecraft:over world", "x:" + "a".repeat(127), ""}) {
            CompoundTag tag = valid.copy(); tag.putString("level", level);
            invalid(() -> LoaderBinding.decode(tag));
        }
        CompoundTag far = valid.copy(); far.putInt("x", 30_000_000);
        invalid(() -> LoaderBinding.decode(far));
        assertThrows(NullPointerException.class, () -> new LoaderBinding(null, OVERWORLD, UUID.randomUUID(), BlockPos.ZERO, null));
        assertThrows(NullPointerException.class, () -> new LoaderBinding(UUID.randomUUID(), OVERWORLD, null, BlockPos.ZERO, null));
    }

    @Test void futureUnknownMissingAndMistypedBindingsAreRefused() {
        CompoundTag valid = binding(UUID.randomUUID()).encode();
        schemaVersions(valid, LoaderBinding::decode);
        for (String key : new String[] {"logical_rocket", "level", "physical_rocket", "x", "y", "z"}) {
            CompoundTag missing = valid.copy(); missing.remove(key);
            invalid(() -> LoaderBinding.decode(missing));
        }
        CompoundTag uuid = valid.copy(); uuid.putIntArray("logical_rocket", new int[5]);
        invalid(() -> LoaderBinding.decode(uuid));
        CompoundTag station = valid.copy(); station.putString("station", "none");
        invalid(() -> LoaderBinding.decode(station));
        CompoundTag coordinate = valid.copy(); coordinate.putLong("y", 64);
        invalid(() -> LoaderBinding.decode(coordinate));
        CompoundTag entity = valid.copy(); entity.putInt("entity_id", 42);
        invalid(() -> LoaderBinding.decode(entity));
    }

    @Test void envelopeIsFiveHundredTwelveBytesDepthEightAndThirtyTwoNodes() {
        bounds(binding(null).encode(), LoaderBinding::bounded, 512, 8, 32);
        invalid(() -> LoaderBinding.decode(padTo(binding(null).encode(), "pad", 513)));
    }
}
