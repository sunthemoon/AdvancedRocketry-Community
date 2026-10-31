package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6TerminalProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void checkedCaptureIsBoundedCompleteAndDoesNotMutateItsPayload() throws Exception {
        var journal = new RocketTransferSavedData(); journal.put(GovernedTransferFixture.create().record());
        var payload = journal.save(new net.minecraft.nbt.CompoundTag()); var before = payload.copy();
        var raw = java.util.Base64.getDecoder().decode(EscrowLoaderS6TerminalProbe.capture(payload));
        var decoded = net.minecraft.nbt.NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(raw)));
        assertEquals(before, decoded); assertEquals(before, payload);
        var oversized = payload.copy(); oversized.putByteArray("extra", new byte[65_536]);
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6TerminalProbe.capture(oversized));
        var empty = payload.copy(); empty.getList("transfers", 10).clear();
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6TerminalProbe.capture(empty));
    }

    @Test void onlyTheSelectedPostLandingProofBoundaryCanPause() {
        for (int index = 0; index < 2; index++) {
            for (var side : new String[] {"BEFORE", "AFTER"}) {
                var cut = "LF_S6_F6_PROOF_" + side + "_" + index;
                var point = side.equals("BEFORE") ? "BARRIER_STORE_BEFORE" : "BARRIER_READBACK_AFTER";
                assertTrue(EscrowLoaderS6TerminalProbe.selected(cut));
                assertTrue(EscrowLoaderS6TerminalProbe.boundary(cut, point, true, index));
                assertFalse(EscrowLoaderS6TerminalProbe.boundary(cut, point, false, index));
                assertFalse(EscrowLoaderS6TerminalProbe.boundary(cut, point, true, 1 - index));
                assertFalse(EscrowLoaderS6TerminalProbe.boundary(cut, point, true, 2));
                assertFalse(EscrowLoaderS6TerminalProbe.boundary(cut, point + "_OTHER", true, index));
            }
        }
        assertFalse(EscrowLoaderS6TerminalProbe.selected(null));
        assertFalse(EscrowLoaderS6TerminalProbe.selected("LF_S6_F7_ACK"));
    }
}
