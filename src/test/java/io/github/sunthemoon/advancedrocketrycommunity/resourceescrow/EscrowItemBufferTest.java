package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowItemBufferTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void wholeTypedBufferSignsSlotOrderCountTagsIdentityAndEmptySlots() {
        var buffer = buffer(64); var empty = new EscrowItemBuffer(java.util.Collections.nCopies(4, new CompoundTag()));
        assertEquals(buffer, EscrowItemBuffer.decode(buffer.encode()));
        assertNotEquals(buffer.signature(id(1)), empty.signature(id(1)));
        assertNotEquals(buffer.signature(id(1)), buffer.signature(id(2)));
        assertNotEquals(buffer.signature(id(1)), buffer(63).signature(id(1)));
        var moved = new ArrayList<>(buffer.slots()); java.util.Collections.swap(moved, 0, 1);
        assertNotEquals(buffer.signature(id(1)), new EscrowItemBuffer(moved).signature(id(1)));
        var typed = new ArrayList<>(buffer.slots()); var tag = new CompoundTag(); tag.putInt("x", 1); typed.get(0).put("tag", tag);
        var ints = new EscrowItemBuffer(typed); typed.get(0).getCompound("tag").putByte("x", (byte) 1);
        assertNotEquals(ints.signature(id(1)), new EscrowItemBuffer(typed).signature(id(1)));
        buffer.slots().get(0).putByte("Count", (byte) 0); buffer.encode().getList("slots", 10).getCompound(0).putByte("Count", (byte) 0);
        assertEquals(64, buffer.slots().get(0).getByte("Count"));
        System.out.println("ITEM_PIN empty=" + empty.signature(id(1)) + " stone64=" + buffer.signature(id(1)));
        assertEquals("fcc07e48cb05383e1c926e8ba0c830ca7540eb099f9f5c7193dacdd265766597", empty.signature(id(1)));
        assertEquals("039fa74bd4074fd42e86c8a15bbd88d9a314a508b9b2c3210ca57098523e9c64", buffer.signature(id(1)));
        var payload = new EscrowItemCargoPayload(cursor(), stack(-1), 3, 26).encode();
        System.out.println("ITEM_PIN payload=" + RocketNativeResources.signature(payload));
        assertEquals("e3edc3fbf8c4b7e47b7f2adad2b9ef80b17a9fc37701763afa501e2faf9b4c84", RocketNativeResources.signature(payload));
    }

    @Test void canonicalKeyOrderDoesNotChangeTheBufferOrPayloadDigest() {
        var a = stack(-1); var tagA = new CompoundTag(); tagA.putInt("a", 1); tagA.putInt("b", 2); a.put("tag", tagA);
        var b = stack(-1); var tagB = new CompoundTag(); tagB.putInt("b", 2); tagB.putInt("a", 1); b.put("tag", tagB);
        var first = new EscrowItemBuffer(List.of(a, new CompoundTag(), b, new CompoundTag()));
        var second = new EscrowItemBuffer(List.of(b, new CompoundTag(), a, new CompoundTag()));
        assertEquals(first.signature(id(1)), second.signature(id(1)));
        assertEquals(RocketNativeResources.signature(new EscrowItemCargoPayload(cursor(), a, 0, 26).encode()),
                RocketNativeResources.signature(new EscrowItemCargoPayload(cursor(), b, 0, 26).encode()));
    }

    @Test void fourLocalMaximaFitTheSignatureProjectionButDoNotWaiveFutureDeviceBounds() {
        for (var max : List.of(EscrowItemStackCodecTest.bytes(4_096), EscrowItemStackCodecTest.depth(16), EscrowItemStackCodecTest.nodes(512))) {
            var buffer = new EscrowItemBuffer(java.util.Collections.nCopies(4, max));
            assertEquals(buffer, EscrowItemBuffer.decode(buffer.encode()));
            assertEquals(buffer.signature(id(1)), EscrowItemBuffer.decode(buffer.encode()).signature(id(1)));
            EscrowItemCapacityTest.report("buffer_local_maxima", buffer.encode());
        }
        for (int count : List.of(0, 3, 5)) {
            assertThrows(IllegalArgumentException.class, () -> new EscrowItemBuffer(java.util.Collections.nCopies(count, new CompoundTag())));
        }
        var bad = stack(-1); bad.putByte("Count", (byte) 0);
        assertThrows(IllegalArgumentException.class, () -> new EscrowItemBuffer(List.of(bad, new CompoundTag(), new CompoundTag(), new CompoundTag())));
        var wrongType = buffer(1).encode(); wrongType.putString("slots", "not a list");
        assertThrows(IllegalArgumentException.class, () -> EscrowItemBuffer.decode(wrongType));
    }
}
