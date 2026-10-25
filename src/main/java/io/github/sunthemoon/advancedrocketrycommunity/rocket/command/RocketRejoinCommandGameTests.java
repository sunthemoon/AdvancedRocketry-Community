package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Actual vanilla sources and function dispatch, with a counting admission boundary and unchanged sidecar. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRejoinCommandGameTests {
    private RocketRejoinCommandGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_rejoin_commands")
    public static void compiledFunctionsRecheckEveryOriginAndNestedExecute(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getFunctionCompilationLevel() == 4, "This matrix requires compile level 4");
        var calls = new AtomicInteger();
        // Use a private dispatcher so no production command is replaced during the test.
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        RocketRejoinCommands.register(dispatcher, (s, transfer) -> {
            calls.incrementAndGet(); return new RocketReleaseResult(RocketReleaseResult.Status.ADMITTED, transfer, "B0");
        }, (s, player) -> { throw new AssertionError("Release called inspection"); });
        var build = net.minecraft.commands.CommandBuildContext.simple(server.registryAccess(), server.getWorldData().enabledFeatures());
        net.minecraft.server.commands.ExecuteCommand.register(dispatcher, build);
        net.minecraft.server.commands.FunctionCommand.register(dispatcher);
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "W160Operator"));
        var console = server.createCommandSourceStack();
        var rcon = new net.minecraft.server.rcon.RconConsoleSource(server).createCommandSourceStack();
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.COMMAND_BLOCK);
        var block = ((CommandBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1))).getCommandBlock().createCommandSourceStack();
        var cartEntity = new net.minecraft.world.entity.vehicle.MinecartCommandBlock(helper.getLevel(), 1, 2, 1);
        var cart = cartEntity.getCommandBlock().createCommandSourceStack();
        var loop = server.getFunctions().getGameLoopSender();
        var sources = List.of(player.createCommandSourceStack().withPermission(2), player.createCommandSourceStack().withPermission(3),
                player.createCommandSourceStack().withPermission(4), console, rcon, block, cart, loop);
        String command = "arce rocket recover " + UUID.randomUUID() + " release-absent";
        var sidecar = RocketRejoinEvents.get(server); var before = RocketRejoinCodec.encode(sidecar.root());
        long completions = sidecar.completions(); var pending = sidecar.pendingRoots();
        for (var source : sources) {
            for (String text : List.of(command, "execute positioned ~ ~ ~ run execute positioned ~ ~ ~ run " + command)) {
                var function = CommandFunction.fromLines(ModIdentity.id("w160_source"), dispatcher, console.withPermission(4), List.of(text));
                int prior = calls.get();
                server.getFunctions().execute(function, source);
                helper.assertTrue(calls.get() == prior + (source.hasPermission(3) ? 1 : 0), "Compiled origin permission changed");
            }
        }
        // Exercise actual tick/load, scheduled callback, reward and operator /function paths, not source labels.
        var original = server.getServerResources().managers().getFunctionLibrary();
        for (boolean nested : List.of(false, true)) {
            String text = nested ? "execute positioned ~ ~ ~ run execute positioned ~ ~ ~ run " + command : command;
            var id = ModIdentity.id("w160_function");
            var function = CommandFunction.fromLines(id, dispatcher, console.withPermission(4), List.of(text));
            var library = new ServerFunctionLibrary(4, dispatcher) {
                @Override public Optional<CommandFunction> getFunction(ResourceLocation requested) {
                    return id.equals(requested) ? Optional.of(function) : Optional.empty();
                }
                @Override public Map<ResourceLocation, CommandFunction> getFunctions() { return Map.of(id, function); }
                @Override public java.util.Collection<CommandFunction> getTag(ResourceLocation tag) {
                    return tag.equals(new ResourceLocation("minecraft", "tick")) || tag.equals(new ResourceLocation("minecraft", "load"))
                            ? List.of(function) : List.of();
                }
            };
            try {
                server.getFunctions().replaceLibrary(library);
                int prior = calls.get();
                server.getFunctions().tick(); // load once and tick once, both using the real level-2 sender.
                new net.minecraft.world.level.timers.FunctionCallback(id).handle(server,
                        server.getWorldData().overworldData().getScheduledEvents(), server.overworld().getGameTime());
                new net.minecraft.advancements.AdvancementRewards(0, new ResourceLocation[0], new ResourceLocation[0],
                        new CommandFunction.CacheableFunction(function)).grant(player);
                helper.assertTrue(calls.get() == prior, "Automatic function crossed the admission boundary");
                for (var operator : List.of(player.createCommandSourceStack().withPermission(3), console, rcon)) {
                    int count = calls.get();
                    dispatcher.execute("function " + id, operator);
                    helper.assertTrue(calls.get() == count + 1, "Operator function lost its permission");
                }
            } finally { server.getFunctions().replaceLibrary(original); }
        }
        helper.assertTrue(before.equals(RocketRejoinCodec.encode(sidecar.root())) && completions == sidecar.completions()
                && pending.equals(sidecar.pendingRoots()), "Origin matrix wrote the sidecar");
        cartEntity.discard(); helper.succeed();
    }
}
