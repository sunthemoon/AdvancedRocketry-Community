package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.ReleaseTestServers;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * W346: the save-boundary entries (hold, autosave, restore) exist only with the release-test property and only for
 * permission 2; each is refused by the complete {@link EscrowTestEndpoints} gate before it resolves a dimension or reads
 * the journal, with RCON enabled or with a connected player the facility did not create, and holds nothing then. The
 * open gate's control shows the refusal is the gate's. The hold bookkeeping refuses a Level already saving off and
 * restores only a Level it holds.
 */
final class BaySaveBoundaryReleaseTestGateTest {
    private static final String HOOKS = EscrowTestEndpoints.HOOKS;
    @TempDir Path directory;
    private final ReleaseTestServers servers = new ReleaseTestServers();
    private final Map<String, String> saved = new HashMap<>();

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @AfterEach void restoreProperties() {
        servers.close();
        saved.forEach((key, value) -> { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } });
    }

    private void property(String key, String value) {
        if (!saved.containsKey(key)) { saved.put(key, System.getProperty(key)); }
        System.setProperty(key, value);
    }

    private static List<String> commands() {
        return List.of("arce bay release-test save-boundary hold advancedrocketrycommunity:moon",
                "arce bay release-test save-boundary autosave " + UUID.randomUUID(),
                "arce bay release-test save-boundary restore advancedrocketrycommunity:moon");
    }

    private static CommandDispatcher<CommandSourceStack> registered() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        new BayReleaseTestCommands().register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.DEDICATED, null));
        return dispatcher;
    }

    @Test void theEntriesAreAbsentWithoutTheReleaseTestProperty() {
        property(HOOKS, "false");
        assertNull(registered().getRoot().getChild("arce"), "the bay release-test tree is registered without the property");
    }

    @Test void theEntriesNeedPermissionTwo() throws Exception {
        property(HOOKS, "true");
        var dispatcher = registered();
        MinecraftServer open = servers.server(directory, false);
        for (String command : commands()) {
            assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(command, source(open, 1)), command);
        }
        assertTrue(BaySaveBoundaryReleaseTestCommands.heldFor(open).isEmpty());
    }

    @Test void everyEntryIsRefusedByTheGateBeforeItsFirstEffect() throws Exception {
        property(HOOKS, "true");
        var dispatcher = registered();
        MinecraftServer rcon = servers.server(directory, true);
        MinecraftServer foreign = servers.server(directory, false);
        servers.join(foreign, UUID.randomUUID());
        for (MinecraftServer closed : List.of(rcon, foreign)) {
            assertFalse(EscrowTestEndpoints.enabled(closed));
            for (String command : commands()) {
                var refused = assertThrows(IllegalArgumentException.class, () -> dispatcher.execute(command, source(closed, 2)), command);
                assertTrue(refused.getMessage().startsWith("Escrow fixture requires"), command + ": " + refused.getMessage());
            }
            assertTrue(BaySaveBoundaryReleaseTestCommands.heldFor(closed).isEmpty(), "a refused entry held a Level");
        }
        // Control: the open gate lets each entry past it; with no world it then fails at its first world or journal access.
        MinecraftServer open = servers.server(directory, false);
        assertTrue(EscrowTestEndpoints.enabled(open));
        for (String command : commands()) {
            try { dispatcher.execute(command, source(open, 2)); }
            catch (Throwable past) {
                assertFalse(past instanceof IllegalArgumentException && String.valueOf(past.getMessage()).startsWith("Escrow fixture requires"),
                        command + " was refused by an open gate");
            }
        }
        assertTrue(BaySaveBoundaryReleaseTestCommands.heldFor(open).isEmpty(), "an entry held a Level it could not resolve");
    }

    @Test void theHoldRefusesALevelAlreadySavingOffAndRestoresOnlyWhatItHolds() {
        var holds = new BaySaveBoundaryReleaseTestCommands.Holds();
        var saveOff = new AtomicBoolean(true);
        assertFalse(holds.hold("moon", saveOff.get(), () -> fail("an already save-off Level was changed")));
        assertFalse(holds.holds("moon"));
        assertFalse(holds.restore("moon", () -> fail("a Level it never held was restored")));
        saveOff.set(false);
        assertTrue(holds.hold("moon", saveOff.get(), () -> saveOff.set(true)));
        assertTrue(saveOff.get() && holds.holds("moon"));
        assertFalse(holds.hold("moon", saveOff.get(), () -> fail("a held Level was held twice")));
        assertFalse(holds.restore("overworld", () -> fail("another Level was restored")));
        assertTrue(holds.restore("moon", () -> saveOff.set(false)));
        assertFalse(saveOff.get() || holds.holds("moon"));
        assertFalse(holds.restore("moon", () -> fail("a restored Level was restored again")));
    }

    private static CommandSourceStack source(MinecraftServer server, int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission, "gate-test",
                Component.literal("gate-test"), server, null);
    }
}
