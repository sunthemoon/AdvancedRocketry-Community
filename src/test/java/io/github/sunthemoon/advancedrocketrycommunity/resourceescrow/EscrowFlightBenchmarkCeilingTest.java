package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowFlightBenchmarkCeilingTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void exactCeilingUsesTheRealEncoderButCannotBecomeAnAdmittedWorldRoot() {
        var root = EscrowFlightBenchmarkCeiling.create();
        assertEquals(1_048_576, RocketNbtSize.uncompressedBytes(EscrowCodec.encode(root)));
        assertEquals(64, root.records().size()); assertEquals(256, root.floors().size());
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(root));
        for (var op : root.records()) {
            assertTrue(RocketNbtSize.uncompressedBytes(EscrowCodec.operation(op)) <= EscrowLimits.OP_BYTES);
        }
    }
}
