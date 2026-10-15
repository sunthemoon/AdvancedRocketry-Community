package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTestTags.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoaderRootTest {
    private static final ResourceLocation WATER = ResourceLocation.tryParse("minecraft:water");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static LoaderRoot full() {
        CompoundTag tag = new CompoundTag();
        tag.putString("batch", "kept");
        var binding = new LoaderBinding(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"), UUID.randomUUID(),
                new BlockPos(3, 64, 3), UUID.randomUUID());
        var locked = new LoaderEscrowSection(UUID.randomUUID(), 2, List.of(new EscrowPrefix.Marker(UUID.randomUUID(), 7, "SOURCE", 2)));
        return new LoaderRoot(UUID.randomUUID(), UUID.randomUUID(), binding, LoaderTank.of(WATER, tag, 4_000), locked);
    }

    private static CompoundTag parent(CompoundTag root) {
        CompoundTag parent = new CompoundTag();
        parent.put(LoaderRoot.KEY, root);
        return parent;
    }

    @Test void placedAndFullRootsRoundTrip() {
        assertEquals("arce_fluid_fuel_loader", LoaderRoot.KEY);
        var placed = LoaderRoot.placed(UUID.randomUUID(), UUID.randomUUID(), LoaderTank.EMPTY);
        assertEquals(Set.of("schema_version", "owner", "device", "tank", "resource_escrow"), placed.encode().getAllKeys());
        assertEquals(placed, LoaderRoot.decode(placed.encode()));
        assertNull(placed.binding());
        assertFalse(placed.escrow().locked());
        var unclaimed = LoaderRoot.placed(null, UUID.randomUUID(), LoaderTank.of(WATER, null, 16_000));
        assertEquals(unclaimed, LoaderRoot.decode(unclaimed.encode()));
        var full = full();
        assertEquals(Set.of("schema_version", "owner", "device", "binding", "tank", "resource_escrow"), full.encode().getAllKeys());
        assertEquals(full, LoaderRoot.decode(full.encode()));
        assertEquals(full.tank().signature(full.device()), full.signature());
    }

    @Test void readDistinguishesAbsentValidFutureInvalidAndUnregistered() {
        var absent = LoaderRoot.read(new CompoundTag());
        assertEquals(LoaderRoot.Status.ABSENT, absent.status());
        assertNull(absent.root());
        assertTrue(absent.status().supported());
        var full = full();
        var valid = LoaderRoot.read(parent(full.encode()));
        assertEquals(LoaderRoot.Status.VALID, valid.status());
        assertEquals(full, valid.root());
        CompoundTag future = full.encode(); future.putInt("schema_version", 2);
        assertEquals(LoaderRoot.Status.FUTURE, LoaderRoot.read(parent(future)).status());
        for (String nested : new String[] {"binding", "resource_escrow"}) {
            CompoundTag tag = full.encode(); tag.getCompound(nested).putInt("schema_version", 2);
            assertEquals(LoaderRoot.Status.FUTURE, LoaderRoot.read(parent(tag)).status(), nested);
        }
        CompoundTag unregistered = full.encode(); unregistered.getCompound("tank").putString("FluidName", "absentmod:fuel");
        assertEquals(LoaderRoot.Status.UNREGISTERED_FLUID, LoaderRoot.read(parent(unregistered)).status());
        CompoundTag wrongType = new CompoundTag(); wrongType.put(LoaderRoot.KEY, StringTag.valueOf("loader"));
        assertEquals(LoaderRoot.Status.INVALID, LoaderRoot.read(wrongType).status());
        for (LoaderRoot.Status status : LoaderRoot.Status.values()) {
            assertEquals(status == LoaderRoot.Status.ABSENT || status == LoaderRoot.Status.VALID, status.supported());
        }
        for (var read : List.of(LoaderRoot.read(parent(future)), LoaderRoot.read(wrongType))) {
            assertNull(read.root());
            assertFalse(read.reason().isBlank());
        }
    }

    @Test void futureUnknownMissingAndInconsistentRootsAreRefused() {
        CompoundTag valid = full().encode();
        schemaVersions(valid, LoaderRoot::decode);
        for (String key : new String[] {"device", "tank", "resource_escrow"}) {
            CompoundTag missing = valid.copy(); missing.remove(key);
            invalid(() -> LoaderRoot.decode(missing));
        }
        // Only a claimed device may hold a binding.
        CompoundTag unowned = valid.copy(); unowned.remove("owner");
        invalid(() -> LoaderRoot.decode(unowned));
        invalid(() -> new LoaderRoot(null, UUID.randomUUID(), full().binding(), LoaderTank.EMPTY, LoaderEscrowSection.neutral()));
        CompoundTag binding = valid.copy(); binding.getCompound("binding").putString("level", "overworld");
        invalid(() -> LoaderRoot.decode(binding));
        CompoundTag markers = valid.copy();
        markers.getCompound("resource_escrow").getList("markers", 10).getCompound(0).putString("role", "DESTINATION");
        invalid(() -> LoaderRoot.decode(markers));
        CompoundTag device = valid.copy(); device.putIntArray("device", new int[2]);
        invalid(() -> LoaderRoot.decode(device));
        CompoundTag bindingType = valid.copy(); bindingType.putString("binding", "rocket");
        invalid(() -> LoaderRoot.decode(bindingType));
    }

    @Test void envelopeIsThirtyTwoKibibytesDepthTwentyAndTwoThousandFortyEightNodes() {
        CompoundTag valid = full().encode();
        bounds(valid, LoaderRoot::bounded, 32_768, 20, 2_048);
        invalid(() -> LoaderRoot.decode(padTo(valid, "pad", 32_769)));
        var over = assertThrows(LoaderDataException.class, () -> LoaderRoot.decode(padTo(valid, "pad", 32_769)));
        assertTrue(over.getMessage().contains("exceeds its envelope"), over.getMessage());
    }
}
