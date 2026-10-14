package io.github.sunthemoon.advancedrocketrycommunity.persistence;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class CanonicalNbtDigestTest {
    @Test void callerSelectsDepthAndTheExistingNc1DigestIsUnchanged() {
        var payload = new CompoundTag(); var cursor = payload;
        for (int depth = 1; depth < 4; depth++) { var child = new CompoundTag(); cursor.put("c", child); cursor = child; }
        assertEquals(RocketNativeResources.signature(payload), CanonicalNbtDigest.of(payload, 4096, 4));
        cursor.put("c", new CompoundTag());
        assertThrows(IllegalArgumentException.class, () -> CanonicalNbtDigest.of(payload, 4096, 4));
        assertEquals(RocketNativeResources.signature(payload), CanonicalNbtDigest.of(payload, 4096, 35));
    }
    @Test void byteLimitIsExactAndHashDoesNotDependOnInsertionOrder() {
        var a = new CompoundTag(); a.putByteArray("z", new byte[256]); a.putString("a", "first");
        var b = new CompoundTag(); b.putString("a", "first"); b.putByteArray("z", new byte[256]);
        int bytes = RocketNbtSize.uncompressedBytes(a);
        assertEquals(CanonicalNbtDigest.of(a, bytes, 2), CanonicalNbtDigest.of(b, bytes, 2));
        assertThrows(IllegalArgumentException.class, () -> CanonicalNbtDigest.of(a, bytes - 1, 2));
        assertThrows(IllegalArgumentException.class, () -> CanonicalNbtDigest.of(a, bytes, 1));
    }
}
