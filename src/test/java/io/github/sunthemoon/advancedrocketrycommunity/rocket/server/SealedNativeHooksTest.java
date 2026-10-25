package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SealedNativeHooksTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void nativeCutObserverRequiresAnExistingServer() throws Exception {
        var method = RocketRuntime.class.getMethod("sealedNativeObserverForReleaseTest", MinecraftServer.class,
                java.util.function.Consumer.class);
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(null, null, (java.util.function.Consumer<String>) label -> fail("Ungated callback")));
        assertInstanceOf(NullPointerException.class, failure.getCause());
    }
}
