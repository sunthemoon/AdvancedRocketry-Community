package io.github.sunthemoon.advancedrocketrycommunity.satellite.command;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RawTickCommandTest {
    private static final String COMMAND = "arce satellite release-test perf ticks raw";
    private final String previousProperty = System.getProperty(ReleaseTestCommands.PROPERTY);
    private final SatelliteManager previousManager = UnstartedServer.get(SatelliteRuntime.class, null, "manager");
    private final ReleaseTestCommands commands = new ReleaseTestCommands();
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    private final List<String> replies = new ArrayList<>();

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @AfterEach void restore() {
        MinecraftForge.EVENT_BUS.unregister(commands);
        if (previousProperty == null) { System.clearProperty(ReleaseTestCommands.PROPERTY); }
        else { System.setProperty(ReleaseTestCommands.PROPERTY, previousProperty); }
        if (previousManager == null) { SatelliteRuntime.clear(); }
        else { SatelliteRuntime.install(previousManager); }
    }

    private void register(boolean enabled) {
        System.setProperty(ReleaseTestCommands.PROPERTY, Boolean.toString(enabled));
        commands.register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.DEDICATED, null));
    }

    private MinecraftServer server(int tick, long[] vanilla) {
        var server = UnstartedServer.owned();
        UnstartedServer.set(MinecraftServer.class, server, "tickCount", tick);
        UnstartedServer.set(MinecraftServer.class, server, "tickTimes", vanilla);
        return server;
    }

    private CommandSourceStack source(MinecraftServer server, int permission) {
        var sink = new CommandSource() {
            @Override public void sendSystemMessage(Component component) { replies.add(component.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        return new CommandSourceStack(sink, Vec3.ZERO, Vec2.ZERO, null, permission, "raw-test",
                Component.literal("raw-test"), server, null);
    }

    @Test void rawCommandIsAbsentWithoutHooksAndInheritsPermissionTwo() throws Exception {
        register(false);
        assertNull(dispatcher.getRoot().getChild("arce"));
        register(true);
        var gate = dispatcher.getRoot().getChild("arce").getChild("satellite").getChild("release-test");
        var ticks = gate.getChild("perf").getChild("ticks");
        assertNotNull(ticks.getCommand(), "the original aggregate command remains available");
        assertNotNull(ticks.getChild("raw").getCommand());
        var server = server(1, new long[100]);
        for (int permission = 0; permission <= 4; permission++) {
            assertEquals(permission >= 2, gate.canUse(source(server, permission)));
        }
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute(COMMAND, source(server, 1)));
        assertEquals(1, dispatcher.execute(COMMAND, source(server, 2)));
        assertEquals(List.of("ARCE_RELEASE_TEST ticks_raw tick=1 entries=0 data="), replies);
    }

    @Test void endSnapshotIncludesPostWorkAndRemainsStableDuringNextTick() throws Exception {
        register(true);
        // The production manager's accessor clones this ring; do not run its world services in this unit test.
        var manager = UnstartedServer.allocate(SatelliteManager.class);
        long[] post = new long[100];
        post[1] = 7;
        UnstartedServer.set(SatelliteManager.class, manager, "postTickNanos", post);
        SatelliteRuntime.install(manager);
        long[] vanilla = new long[100];
        vanilla[1] = 100;
        var server = server(1, vanilla);
        commands.captureRawTicks(new TickEvent.ServerTickEvent(TickEvent.Phase.START, () -> true, server));
        dispatcher.execute(COMMAND, source(server, 2));
        assertEquals("ARCE_RELEASE_TEST ticks_raw tick=1 entries=0 data=", replies.remove(0));
        commands.captureRawTicks(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
        dispatcher.execute(COMMAND, source(server, 2));
        assertEquals("ARCE_RELEASE_TEST ticks_raw tick=1 entries=1 data=1:107", replies.remove(0));
        vanilla[1] = 999; post[1] = 999;
        UnstartedServer.set(MinecraftServer.class, server, "tickCount", 2);
        dispatcher.execute(COMMAND, source(server, 2));
        assertEquals("ARCE_RELEASE_TEST ticks_raw tick=2 entries=1 data=1:107", replies.remove(0));
        assertEquals(EventPriority.LOWEST, ReleaseTestCommands.class
                .getMethod("captureRawTicks", TickEvent.ServerTickEvent.class)
                .getAnnotation(SubscribeEvent.class).priority());
    }

    @Test void anotherServerAndStoppedServerNeverReusePreviousTimings() throws Exception {
        register(true);
        SatelliteRuntime.clear();
        var first = server(1, new long[100]);
        var second = server(1, new long[100]);
        commands.captureRawTicks(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, first));
        dispatcher.execute(COMMAND, source(second, 2));
        assertEquals("ARCE_RELEASE_TEST ticks_raw tick=1 entries=0 data=", replies.remove(0));
        commands.clearRawTicks(new ServerStoppedEvent(second));
        dispatcher.execute(COMMAND, source(first, 2));
        assertEquals("ARCE_RELEASE_TEST ticks_raw tick=1 entries=1 data=1:0", replies.remove(0));
        commands.clearRawTicks(new ServerStoppedEvent(first));
        dispatcher.execute(COMMAND, source(first, 2));
        assertEquals("ARCE_RELEASE_TEST ticks_raw tick=1 entries=0 data=", replies.remove(0));
        assertNull(UnstartedServer.get(ReleaseTestCommands.class, commands, "rawVanilla"));
        assertNull(UnstartedServer.get(ReleaseTestCommands.class, commands, "rawPost"));
    }
}
