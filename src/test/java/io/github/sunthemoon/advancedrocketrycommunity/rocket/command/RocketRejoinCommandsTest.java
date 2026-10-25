package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketReleaseResult;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketRejoinCommandsTest {
    private static final String RELEASE = "arce rocket recover 00000000-0000-0000-0000-000000000160 release-absent";
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission,
                "Test", Component.literal("Test"), null, null);
    }

    @Test void literalRequiresThreeAndReplayedParseChecksBeforeAnyServiceRead() throws Exception {
        var calls = new AtomicInteger();
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        RocketRejoinCommands.register(dispatcher, (server, transfer) -> {
            calls.incrementAndGet();
            return new RocketReleaseResult(RocketReleaseResult.Status.ADMITTED, transfer, "B0");
        }, (server, player) -> { fail("Release must not inspect the sidecar separately"); return null; });
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(RELEASE, source(2)));
        assertEquals(0, calls.get());
        var compiled = dispatcher.parse(RELEASE, source(4));
        // CommandFunction replays the compiled context with the execution source, without re-running requires.
        var command = compiled.getContext().build(RELEASE).getCommand();
        assertNotNull(command);
        assertEquals(0, command.run(compiled.getContext().build(RELEASE).copyFor(source(2))));
        assertEquals(0, calls.get());
        for (int level : new int[] {3, 4}) {
            assertEquals(1, command.run(compiled.getContext().build(RELEASE).copyFor(source(level))));
        }
        assertEquals(2, calls.get());
    }

    @Test void refusalReturnsZeroAndPendingReturnsExistingRelease() throws Exception {
        for (var status : RocketReleaseResult.Status.values()) {
            var dispatcher = new CommandDispatcher<CommandSourceStack>();
            RocketRejoinCommands.register(dispatcher, (server, transfer) ->
                    new RocketReleaseResult(status, UUID.randomUUID(), "S1"),
                    (server, player) -> { fail("Release must not run inspection"); return null; });
            assertEquals(status == RocketReleaseResult.Status.ADMITTED || status == RocketReleaseResult.Status.PENDING ? 1 : 0,
                    dispatcher.execute(RELEASE, source(3)));
        }
    }

    @Test void viewRechecksExecutionPermissionAndOnlyCallsTheReadPort() throws Exception {
        var calls = new AtomicInteger(); var dispatcher = new CommandDispatcher<CommandSourceStack>();
        RocketRejoinCommands.register(dispatcher, (server, transfer) -> { fail("Inspection cannot admit"); return null; },
                (server, player) -> { calls.incrementAndGet(); return new io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinInspection(0, java.util.List.of()); });
        for (String command : java.util.List.of("arce rocket rejoins", "arce rocket rejoins 00000000-0000-0000-0000-000000000160")) {
            assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(command, source(2)));
            var parsed = dispatcher.parse(command, source(4)).getContext().build(command);
            int before = calls.get();
            assertEquals(0, parsed.getCommand().run(parsed.copyFor(source(2)))); assertEquals(before, calls.get());
            assertEquals(0, dispatcher.execute(command, source(3))); assertEquals(before + 1, calls.get());
        }
    }
}
