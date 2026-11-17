package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.ReleaseTestServers;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BayDeploymentSessionTest {
    @TempDir Path directory;
    private final ReleaseTestServers servers = new ReleaseTestServers();
    private final String hooks = System.getProperty(EscrowTestEndpoints.HOOKS);

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @AfterEach void restore() {
        servers.close();
        if (hooks == null) { System.clearProperty(EscrowTestEndpoints.HOOKS); }
        else { System.setProperty(EscrowTestEndpoints.HOOKS, hooks); }
    }

    @Test void productionAdmissionConstantIsFalse() {
        assertFalse(BayDeploymentSwitches.PRODUCTION_ADMISSION);
    }

    @Test void ordinaryProductionAdmissionNeedsTheOwnersDecision() throws Exception {
        System.clearProperty(EscrowTestEndpoints.HOOKS);
        assertFalse(session(servers.server(directory, false)).admissionEnabled(),
                "R459 keeps bay production admission off until the owner's switch decision");
    }

    @Test void dedicatedAdmissionStillUsesTheExistingReleaseTestGate() throws Exception {
        for (boolean rcon : new boolean[] { false, true }) {
            var server = servers.server(directory, rcon);
            var session = session(server);
            for (boolean enabled : new boolean[] { false, true, false, true }) {
                System.setProperty(EscrowTestEndpoints.HOOKS, Boolean.toString(enabled));
                assertEquals(enabled && !rcon, session.admissionEnabled());
                assertEquals(EscrowTestEndpoints.enabled(server), session.admissionEnabled());
            }
            servers.join(server, UUID.randomUUID());
            assertFalse(session.admissionEnabled(), "A foreign connection still closes the release-test gate");
        }
    }

    @Test void gameTestAdmissionStillOpensWithoutReleaseTestHooks() {
        System.clearProperty(EscrowTestEndpoints.HOOKS);
        var server = UnstartedServer.allocate(GameTestServer.class);
        UnstartedServer.set(MinecraftServer.class, server, "serverThread", Thread.currentThread());
        assertTrue(session(server).admissionEnabled());
    }

    @Test void closingTheSessionStillRefusesAdmissionWithTheGateOpen() throws Exception {
        System.setProperty(EscrowTestEndpoints.HOOKS, "true");
        var server = servers.server(directory, false);
        var session = session(server);
        assertTrue(session.admissionEnabled());
        session.close();
        assertTrue(EscrowTestEndpoints.enabled(server));
        assertFalse(session.admissionEnabled());
    }

    @Test void admissionStillRequiresTheServerThreadEvenAfterClose() throws Exception {
        System.setProperty(EscrowTestEndpoints.HOOKS, "true");
        var server = servers.server(directory, false);
        var session = session(server);
        UnstartedServer.set(MinecraftServer.class, server, "serverThread", new Thread());
        assertThrows(IllegalArgumentException.class, session::admissionEnabled);
        session.close();
        assertThrows(IllegalArgumentException.class, session::admissionEnabled);
    }

    private static BayDeploymentSession session(MinecraftServer server) {
        return new EscrowRuntime().bayReceipts(server).session(server);
    }
}
