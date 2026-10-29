package io.github.sunthemoon.advancedrocketrycommunity.testsupport;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** No escrow fixture or unrelated test is allowed to supply this bootstrap as a side effect. */
class EscrowBootstrapOrderTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void commonBootstrapPreparesRuntimeBeforeItsFirstExplicitResolution() throws Exception {
        var runtime = assertDoesNotThrow(() -> Class.forName(
                "io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime"));
        var capability = runtime.getField("CAPABILITY").get(null);
        assertNotNull(capability);
        MinecraftBootstrap.initialize();
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestBootstrap.initialize();
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestBootstrap.initialize();
        assertSame(capability, runtime.getField("CAPABILITY").get(null));
    }
}
