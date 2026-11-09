package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptCommitCut;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.ReleaseTestServers;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W331: the new bay release-test entries (the decommission, deployment-status and stale-assembly probes, and the trio,
 * exit survey and exit offline fixture variants) exist only with the release-test property, and each is
 * refused by the complete {@link EscrowTestEndpoints} gate before its first read or write, with RCON enabled or with a
 * connected player the facility did not create. The open gate's control shows the refusal is the gate's. The new cut
 * value B15_D1 is a one-shot INTENT replace-then-throw that stays inert without the hooks property.
 */
final class BayGapReleaseTestGateTest {
    private static final String HOOKS = EscrowTestEndpoints.HOOKS;
    @TempDir Path directory;
    private final ReleaseTestServers servers = new ReleaseTestServers();
    private final Map<String, String> saved = new HashMap<>();

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @AfterEach void restore() {
        servers.close();
        saved.forEach((key, value) -> { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } });
    }

    private void property(String key, String value) {
        if (!saved.containsKey(key)) { saved.put(key, System.getProperty(key)); }
        System.setProperty(key, value);
    }

    private static List<String> commands() {
        UUID id = UUID.randomUUID();
        return List.of("arce bay release-test decommission " + id, "arce bay release-test deployment-status " + id,
                "arce bay release-test stale-assembly " + id,
                "arce bay release-test fixture 8 2 8 trio", "arce bay release-test exit fixture 8 2 8 survey",
                "arce bay release-test exit fixture 8 2 8 offline");
    }

    @Test void theNewEntriesAreAbsentWithoutTheReleaseTestProperty() {
        property(HOOKS, "false");
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        new BayReleaseTestCommands().register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.DEDICATED, null));
        assertNull(dispatcher.getRoot().getChild("arce"), "the bay release-test tree is registered without the property");
    }

    @Test void everyNewEntryIsRefusedByTheGateBeforeItsFirstEffect() throws Exception {
        property(HOOKS, "true");
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        new BayReleaseTestCommands().register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.DEDICATED, null));
        MinecraftServer rcon = servers.server(directory, true);
        MinecraftServer foreign = servers.server(directory, false);
        servers.join(foreign, UUID.randomUUID());
        for (MinecraftServer closed : List.of(rcon, foreign)) {
            assertFalse(EscrowTestEndpoints.enabled(closed));
            for (String command : commands()) {
                var refused = assertThrows(IllegalArgumentException.class, () -> dispatcher.execute(command, source(closed)), command);
                assertTrue(refused.getMessage().startsWith("Escrow fixture requires"), command + ": " + refused.getMessage());
            }
        }
        // Control: the open gate lets each entry past it; with no world it then fails at its first world or runtime access.
        MinecraftServer open = servers.server(directory, false);
        assertTrue(EscrowTestEndpoints.enabled(open));
        for (String command : commands()) {
            try { dispatcher.execute(command, source(open)); }
            catch (Throwable past) {
                assertFalse(past instanceof IllegalArgumentException && String.valueOf(past.getMessage()).startsWith("Escrow fixture requires"),
                        command + " was refused by an open gate");
            }
        }
    }

    @Test void theB15D1CutIsAnIntentReplaceThenThrowAndInertWithoutTheHooks() throws Exception {
        var cut = BayDeploymentCuts.Cut.valueOf("B15_D1");
        assertEquals(BayReceipt.Phase.INTENT, cut.phase);
        assertEquals(BayReceiptCommitCut.Point.MOVED, cut.point);
        assertEquals(BayReceiptCommitCut.Mode.UNKNOWN, cut.mode);
        assertFalse(cut.removal() || cut.release(), "B15_D1 is a receipt write cut, not a removal or a release");
        property(BayDeploymentCuts.PROPERTY, "B15_D1");
        property(HOOKS, "false");
        assertNull(BayDeploymentCuts.configured(servers.server(directory, false)), "the cut armed without the hooks property");
    }

    private static CommandSourceStack source(MinecraftServer server) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 4, "gate-test", Component.literal("gate-test"),
                server, null);
    }
}
