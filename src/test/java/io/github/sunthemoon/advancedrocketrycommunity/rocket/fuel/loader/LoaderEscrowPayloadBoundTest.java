package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTestTags.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCodec;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowEndpoint;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGuard;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLimits;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowOperation;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Root's W82 decision: intake refuses a Fluid whose native envelope no escrow operation could carry. The intake bound
 * is exactly the payload bound {@link EscrowOperation} enforces, and a payload at that bound still fits a whole
 * operation (12,288 bytes, depth 16, 512 nodes) with the largest endpoints and guard the codec accepts.
 */
class LoaderEscrowPayloadBoundTest {
    private static final ResourceLocation WATER = ResourceLocation.tryParse("minecraft:water");
    private static final String DIGEST = "f".repeat(64);

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    /** Forge's FluidStack envelope of 1,000 mB of water with {@code tag}, built without the tank's own bounds. */
    private static CompoundTag envelope(CompoundTag tag) {
        CompoundTag envelope = new CompoundTag();
        envelope.putString("FluidName", WATER.toString());
        envelope.putInt("Amount", 1_000);
        if (tag != null) { envelope.put("Tag", tag.copy()); }
        return envelope;
    }

    /** A native tag that makes the whole envelope exactly {@code bytes} long. */
    private static CompoundTag bytesTo(int bytes) {
        CompoundTag tag = new CompoundTag();
        long base = size(envelope(tag)).bytes();
        tag.putByteArray("p", new byte[(int) (bytes - base - 8)]);
        assertEquals(bytes, size(envelope(tag)).bytes());
        return tag;
    }

    /** A native tag that makes the whole envelope exactly {@code nodes} nodes. */
    private static CompoundTag nodesToTotal(int nodes) {
        CompoundTag tag = nodesTo(new CompoundTag(), "n", nodes - 3);
        assertEquals(nodes, size(envelope(tag)).nodes());
        return tag;
    }

    /** A native tag that makes the whole envelope exactly {@code depth} deep. */
    private static CompoundTag depthTo(int depth) {
        CompoundTag tag = chain(depth - 1);
        assertNotNull(BoundedNbt.measure(envelope(tag), Integer.MAX_VALUE, depth, 1 << 20));
        assertNull(BoundedNbt.measure(envelope(tag), Integer.MAX_VALUE, depth - 1, 1 << 20));
        return tag;
    }

    /** The longest canonical ID with which {@code endpoint} still encodes within the reference bound. */
    private static String longestId(java.util.function.Function<String, EscrowEndpoint> endpoint) {
        for (int length = 128; length > 4; length--) {
            String id = "a:" + "b".repeat(length - 2);
            try {
                EscrowCodec.endpoint(endpoint.apply(id));
                return id;
            } catch (IllegalArgumentException tooLarge) {
                // Try a shorter ID.
            }
        }
        throw new AssertionError("No endpoint encodes");
    }

    private static EscrowOperation operation(CompoundTag payload) {
        UUID logical = new UUID(-1L, -1L);
        String id = longestId(value -> new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, value, new UUID(-1L, -2L), logical,
                value, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, EscrowEndpoint.Unit.OXIDIZER,
                Long.MAX_VALUE - 1, DIGEST, DIGEST));
        var source = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, id, new UUID(-1L, -3L), null, id,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, EscrowEndpoint.Unit.TANK, Long.MAX_VALUE - 1, DIGEST, DIGEST);
        var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, id, new UUID(-1L, -2L), logical, id,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, EscrowEndpoint.Unit.OXIDIZER, Long.MAX_VALUE - 1,
                DIGEST, DIGEST);
        var guard = new EscrowGuard(logical, new UUID(-1L, -4L), Long.MAX_VALUE, DIGEST);
        return new EscrowOperation(new UUID(-1L, -5L), Long.MAX_VALUE, new UUID(-1L, -6L), Long.MAX_VALUE,
                EscrowOperation.Phase.SETTLING_ABANDON, EscrowReason.ENDPOINT_RECORD_TOO_LARGE, payload,
                RocketNativeResources.signature(payload), source, destination, guard);
    }

    @Test void theIntakeBoundIsTheEscrowPayloadBound() {
        assertEquals(EscrowLimits.FLUID_BYTES, LoaderTank.PAYLOAD_BYTES);
        assertTrue(LoaderTank.EMPTY.escrowCarriable());
        for (CompoundTag tag : new CompoundTag[] {null, new CompoundTag(), bytesTo(LoaderTank.PAYLOAD_BYTES),
                nodesToTotal(LoaderTank.PAYLOAD_NODES), depthTo(LoaderTank.PAYLOAD_DEPTH)}) {
            LoaderTank tank = LoaderTank.of(WATER, tag, 1_000);
            assertEquals(envelope(tag), tank.encode());
            assertTrue(tank.escrowCarriable(), String.valueOf(tag));
            // The amount is an int tag of fixed size: the answer is the same at every amount.
            for (int amount : new int[] {1, 999, LoaderTank.CAPACITY}) {
                assertTrue(tank.withAmount(amount).escrowCarriable());
            }
            EscrowOperation op = assertDoesNotThrow(() -> operation(envelope(tag)));
            CompoundTag encoded = assertDoesNotThrow(() -> EscrowCodec.operation(op));
            assertTrue(BoundedNbt.fits(encoded, EscrowLimits.OP_BYTES, 16, 512));
        }
    }

    @Test void aFluidPastThePayloadBoundStillDecodesButNoOperationCanCarryIt() {
        for (CompoundTag tag : new CompoundTag[] {nodesToTotal(LoaderTank.PAYLOAD_NODES + 1),
                depthTo(LoaderTank.PAYLOAD_DEPTH + 1), nodesToTotal(LoaderTank.MAX_NODES), depthTo(LoaderTank.MAX_DEPTH)}) {
            LoaderTank tank = assertDoesNotThrow(() -> LoaderTank.of(WATER, tag, 1_000));
            assertEquals(tank, LoaderTank.decode(tank.encode()), "the tank codec's own bounds still hold it");
            assertFalse(tank.escrowCarriable());
            assertThrows(IllegalArgumentException.class, () -> operation(envelope(tag)));
        }
        // Bytes have one bound: the tank envelope and the payload share 8 KiB.
        invalid(() -> LoaderTank.of(WATER, bytesTo(LoaderTank.PAYLOAD_BYTES + 1), 1_000));
    }
}
