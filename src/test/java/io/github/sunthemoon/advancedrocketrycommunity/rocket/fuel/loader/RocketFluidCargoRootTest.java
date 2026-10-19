package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTestTags.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Shares the existing exact-native-size fixtures, without changing the fuel-loader tests. */
class RocketFluidCargoRootTest {
    private static final ResourceLocation WATER = ResourceLocation.tryParse("minecraft:water");
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static RocketFluidCargoRoot full(RocketCargoRole role) {
        var binding = new LoaderBinding(UUID.randomUUID(), ResourceLocation.tryParse("x:" + "a".repeat(126)),
                UUID.randomUUID(), new BlockPos(-30_000_000, -2_048, 29_999_999), UUID.randomUUID());
        var escrow = new RocketEscrowSection(UUID.randomUUID(), Long.MAX_VALUE,
                List.of(new EscrowPrefix.Marker(UUID.randomUUID(), Long.MAX_VALUE, role.markerRole(), Long.MAX_VALUE)));
        return new RocketFluidCargoRoot(UUID.randomUUID(), UUID.randomUUID(), role, binding,
                LoaderTank.of(WATER, new CompoundTag(), 16_000), escrow);
    }

    private static RocketFluidCargoRoot.Read read(Tag root, RocketCargoRole role) {
        CompoundTag outer = new CompoundTag(); outer.put(RocketFluidCargoRoot.KEY, root);
        return RocketFluidCargoRoot.read(outer, role);
    }

    @Test void bothRolesRoundTripClaimedUnclaimedAndMarkedRoots() {
        assertEquals("arce_rocket_fluid_cargo", RocketFluidCargoRoot.KEY);
        for (var role : RocketCargoRole.values()) {
            for (var root : List.of(full(role), RocketFluidCargoRoot.placed(null, UUID.randomUUID(), role, LoaderTank.EMPTY),
                    RocketFluidCargoRoot.placed(UUID.randomUUID(), UUID.randomUUID(), role, LoaderTank.of(WATER, null, 1)))) {
                assertEquals(root, RocketFluidCargoRoot.decode(root.encode(), role));
                assertEquals(root.signature(), root.tank().signature(root.device()));
                assertEquals(RocketFluidCargoRoot.Status.VALID, read(root.encode(), role).status());
                assertTrue(RocketFluidCargoRoot.bounded(root.encode()));
            }
            var root = full(role);
            assertEquals(root.tank(), root.withBinding(null).tank());
            assertEquals(root.escrow(), root.withTank(LoaderTank.EMPTY).escrow());
            assertTrue(root.marked());
        }
    }

    @Test void rootAndEscrowEnvelopePredicatesPinAllThreeExactBounds() {
        for (var role : RocketCargoRole.values()) {
            var root = full(role);
            bounds(root.encode(), RocketFluidCargoRoot::bounded, 32_768, 20, 2_048);
            // A full marker consumes enough bytes that a 128-node padding also trips the byte gate.
            // Use a small frame to isolate all three gates; valid maximal markers are checked separately.
            bounds(new CompoundTag(), RocketFluidCargoRoot::escrowBounded, 256, 8, 128);
            for (Tag tag : List.of(padTo(root.encode(), "p", 32_769), nodesTo(root.encode(), "n", 2_049))) {
                assertThrows(IllegalArgumentException.class, () -> RocketFluidCargoRoot.decode(tag, role));
            }
            for (int bytes : new int[] {32_767, 32_768, 32_769}) {
                assertEquals(bytes <= 32_768, RocketFluidCargoRoot.bounded(padTo(root.encode(), "p", bytes)));
            }
            assertTrue(RocketFluidCargoRoot.escrowBounded(padTo(root.escrow().encode(), "p", 255)));
            assertTrue(RocketFluidCargoRoot.bounded(nodesTo(root.encode(), "n", 2_047)));
            assertTrue(RocketFluidCargoRoot.escrowBounded(nodesTo(new CompoundTag(), "n", 127)));
            CompoundTag insideDepth = root.encode(); insideDepth.put("p", chain(18));
            assertTrue(RocketFluidCargoRoot.bounded(insideDepth));
            CompoundTag escrowDepth = new CompoundTag(); escrowDepth.put("p", chain(6));
            assertTrue(RocketFluidCargoRoot.escrowBounded(escrowDepth));
            CompoundTag deep = root.encode(); deep.put("p", chain(20));
            assertEquals(RocketFluidCargoRoot.Status.INVALID, read(deep, role).status());
        }
    }

    @Test void maximumNativeTanksFitBothCargoRootsAndTheUnchangedCarrier() {
        var base = LoaderTank.of(WATER, new CompoundTag(), 16_000).encode();
        for (var role : RocketCargoRole.values()) {
            var root = full(role);
            for (int delta : new int[] {-1, 0, 1}) {
                var nativeTags = List.of(padTo(new CompoundTag(), "p", 8_192 - (int) size(base).bytes() + 4 + delta),
                        chain(LoaderTank.MAX_DEPTH - 1 + delta),
                        nodesTo(new CompoundTag(), "n", LoaderTank.MAX_NODES - 3 + delta));
                for (var nativeTag : nativeTags) {
                    CompoundTag encoded = root.encode(); encoded.getCompound("tank").put("Tag", nativeTag);
                    if (delta > 0) {
                        assertEquals(RocketFluidCargoRoot.Status.INVALID, read(encoded, role).status());
                    } else {
                        var decoded = RocketFluidCargoRoot.decode(encoded, role);
                        assertEquals(encoded, decoded.encode());
                        var carrier = new LoaderCarrier(root.owner(), decoded.tank());
                        assertEquals(carrier, LoaderCarrier.decode(carrier.encode()));
                    }
                }
            }
        }
    }

    @Test void amountsAtAndAroundCapacityAndZeroHaveNoCoercion() {
        for (var role : RocketCargoRole.values()) {
            for (int amount : new int[] {-1, 0, 1, 15_999, 16_000, 16_001}) {
                var raw = full(role).encode(); raw.getCompound("tank").putInt("Amount", amount);
                assertEquals(amount > 0 && amount <= 16_000 ? RocketFluidCargoRoot.Status.VALID
                        : RocketFluidCargoRoot.Status.INVALID, read(raw, role).status());
            }
            var empty = full(role).withTank(LoaderTank.EMPTY);
            assertEquals(empty, RocketFluidCargoRoot.decode(empty.encode(), role));
        }
    }

    @Test void absenceFutureNestedFutureAndMissingRegistryAreDistinct() {
        assertEquals(RocketFluidCargoRoot.Status.ABSENT, RocketFluidCargoRoot.read(new CompoundTag(), RocketCargoRole.LOAD).status());
        for (var role : RocketCargoRole.values()) {
            for (int version : new int[] {2, Integer.MAX_VALUE}) {
                var raw = full(role).encode(); raw.putInt("schema_version", version);
                raw.putByteArray("future", new byte[65_536]);
                assertEquals(RocketFluidCargoRoot.Status.FUTURE, read(raw, role).status());
            }
            for (String nested : List.of("binding", "resource_escrow")) {
                var raw = full(role).encode(); raw.getCompound(nested).putInt("schema_version", 2);
                assertEquals(RocketFluidCargoRoot.Status.FUTURE, read(raw, role).status());
            }
            var missing = full(role).encode(); missing.getCompound("tank").putString("FluidName", "absentmod:fluid");
            assertEquals(RocketFluidCargoRoot.Status.UNREGISTERED_FLUID, read(missing, role).status());
        }
    }

    @Test void missingMistypedUnknownAndMismatchedFieldsAreProtectedNotRepaired() {
        for (var role : RocketCargoRole.values()) {
            var base = full(role).encode();
            for (String field : base.getAllKeys()) {
                var raw = base.copy(); raw.put(field, IntTag.valueOf(-1));
                assertEquals(RocketFluidCargoRoot.Status.INVALID, read(raw, role).status(), field);
                if (!field.equals("binding")) {
                    raw = base.copy(); raw.remove(field);
                    assertEquals(RocketFluidCargoRoot.Status.INVALID, read(raw, role).status(), field);
                }
            }
            for (int version : new int[] {-1, 0}) {
                var raw = base.copy(); raw.putInt("schema_version", version);
                assertEquals(RocketFluidCargoRoot.Status.INVALID, read(raw, role).status());
            }
            var raw = base.copy(); raw.putLong("schema_version", 1);
            assertEquals(RocketFluidCargoRoot.Status.INVALID, read(raw, role).status());
            raw = base.copy(); raw.putInt("extra", 1);
            assertEquals(RocketFluidCargoRoot.Status.INVALID, read(raw, role).status());
            var other = role == RocketCargoRole.LOAD ? RocketCargoRole.UNLOAD : RocketCargoRole.LOAD;
            assertEquals(RocketFluidCargoRoot.Status.INVALID, read(base, other).status());
            assertEquals(RocketFluidCargoRoot.Status.INVALID, read(IntTag.valueOf(1), role).status());
        }
    }

    @Test void markersMustHaveTheFixedDirectionAndAtMostOneEntry() {
        for (var role : RocketCargoRole.values()) {
            var raw = full(role).encode();
            var markers = raw.getCompound("resource_escrow").getList("markers", Tag.TAG_COMPOUND);
            markers.getCompound(0).putString("role", role == RocketCargoRole.LOAD ? "DESTINATION" : "SOURCE");
            assertEquals(RocketFluidCargoRoot.Status.INVALID, read(raw, role).status());
            raw = full(role).encode(); markers = raw.getCompound("resource_escrow").getList("markers", Tag.TAG_COMPOUND);
            markers.add(markers.getCompound(0).copy());
            assertEquals(RocketFluidCargoRoot.Status.INVALID, read(raw, role).status());
            var neutral = RocketFluidCargoRoot.placed(null, UUID.randomUUID(), role, LoaderTank.EMPTY);
            assertFalse(neutral.marked());
            assertEquals(neutral, RocketFluidCargoRoot.decode(neutral.encode(), role));
        }
    }
}
