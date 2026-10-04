package io.github.sunthemoon.advancedrocketrycommunity.machine.menu;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraftforge.network.NetworkRegistry;
import org.junit.jupiter.api.Test;

class MachineMenuNetworkTest {
    @Test
    void onlyExactRequiredProtocolIsAccepted() {
        assertEquals("1", MachineMenuNetwork.protocolVersion());
        assertTrue(MachineMenuNetwork.acceptsProtocol("1"));
        for (String version : new String[] {null, "", "0", "2", "01", "1 ", "1.0",
                NetworkRegistry.ABSENT.version(), NetworkRegistry.ACCEPTVANILLA}) {
            assertFalse(MachineMenuNetwork.acceptsProtocol(version));
        }
    }
}
