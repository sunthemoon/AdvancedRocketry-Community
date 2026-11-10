package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
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
 * W332: the R-042 release-test entries exist only with the release-test property, and each is refused by the complete
 * {@link EscrowTestEndpoints} gate before its first read or write, with RCON enabled or with a connected player the
 * facility did not create. The open gate's control shows the refusal is the gate's.
 */
final class R042ReleaseTestGateTest {
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
        return List.of("arce bay release-test r042 plain 24 2 8", "arce bay release-test r042 insert 40 2 8",
                "arce bay release-test r042 cell A " + id, "arce bay release-test r042 attempt A ALL",
                "arce bay release-test r042 report A", "arce bay release-test r042 rider state " + id);
    }

    @Test void theEntriesAreAbsentWithoutTheReleaseTestProperty() {
        property(HOOKS, "false");
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        new BayReleaseTestCommands().register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.DEDICATED, null));
        assertNull(dispatcher.getRoot().getChild("arce"), "the bay release-test tree is registered without the property");
    }

    @Test void everyEntryIsRefusedByTheGateBeforeItsFirstEffect() throws Exception {
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

    @Test void theR042RegistrationItselfRequiresPermissionTwo() throws Exception {
        // Register this subtree alone: earlier registrations must not supply its permission predicate.
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        R042ReleaseTestCommands.register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.DEDICATED, null));
        var releaseTest = dispatcher.getRoot().getChild("arce").getChild("bay").getChild("release-test");
        MinecraftServer server = servers.server(directory, false);
        for (int permission = 0; permission <= 4; permission++) {
            var source = source(server).withPermission(permission);
            assertEquals(permission >= 2, releaseTest.canUse(source), "permission " + permission);
        }
    }

    private static CommandSourceStack source(MinecraftServer server) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 4, "gate-test", Component.literal("gate-test"),
                server, null);
    }
}
