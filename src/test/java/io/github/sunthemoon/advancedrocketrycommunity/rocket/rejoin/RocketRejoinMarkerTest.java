package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.UUID;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketRejoinMarkerTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void markerIsBoundedAndExact() throws Exception {
        var value = new RocketRejoinMarker.Value(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var encoded = RocketRejoinMarker.encode(value);
        var bytes = new ByteArrayOutputStream(); NbtIo.write(encoded, new DataOutputStream(bytes));
        assertTrue(bytes.size() <= RocketRejoinMarker.MAX_BYTES); assertEquals(value, RocketRejoinMarker.decode(encoded));
        encoded.putInt("schema", 2); assertNull(RocketRejoinMarker.decode(encoded));
        encoded.putInt("schema", 1); encoded.putString("extra", "unknown"); assertNull(RocketRejoinMarker.decode(encoded));
    }
}
