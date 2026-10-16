package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowOperatorGameTests {
    private EscrowOperatorGameTests() { }
    @GameTest(template = "empty")
    public static void permissionTwoDiagnosticsAreBoundedReadOnlyAndNeverLoadChunks(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer(); var capture = new Output();
        var source = server.createCommandSourceStack().withSource(capture).withPermission(2);
        var dispatcher = server.getCommands().getDispatcher();
        var data = ResourceEscrowSavedData.existing(server);
        var before = data == null ? null : EscrowCodec.encode(data.ledger());
        int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
        for (String command : List.of("status", "inspect " + UUID.randomUUID(), "retry " + UUID.randomUUID())) {
            capture.lines.clear(); dispatcher.execute("arce escrow " + command, source);
            helper.assertTrue(!capture.lines.isEmpty() && capture.lines.size() <= 5
                    && capture.lines.stream().allMatch(line -> line.length() < 1200), "Bounded command response");
            var denied = dispatcher.parse("arce escrow " + command, source.withPermission(1));
            helper.assertTrue(denied.getReader().canRead(), "Permission one must not parse escrow commands");
        }
        var after = ResourceEscrowSavedData.existing(server);
        helper.assertTrue(before == null ? after == null : after != null && before.equals(EscrowCodec.encode(after.ledger())),
                "Diagnostics must not create or mutate a ledger");
        helper.assertTrue(chunks == helper.getLevel().getChunkSource().getLoadedChunksCount(), "Diagnostics never load chunks");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void settlementRequiresPlayerOrUnsuppressedExactConsoleProvenance(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer(); var dispatcher = server.getCommands().getDispatcher();
        // Pin the source construction used by DedicatedServer$1 -> ConsoleInput -> handleConsoleInputs.
        var input = new net.minecraft.server.ConsoleInput("arce escrow status", server.createCommandSourceStack());
        helper.assertTrue(input.source.source == server && input.source.getEntity() == null
                && input.source.withSuppressedOutput() != input.source, "Console entry stack provenance");
        helper.assertTrue(EscrowCommandActor.require(input.source).kind() == EscrowCommandActor.Kind.CONSOLE, "Typed console actor");
        var player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "escrowActor"));
        var playerStack = player.createCommandSourceStack().withPermission(2);
        helper.assertTrue(EscrowCommandActor.require(playerStack).id().equals(player.getUUID()), "Player audit UUID retained");

        helper.setBlock(new net.minecraft.core.BlockPos(1, 1, 1), net.minecraft.world.level.block.Blocks.COMMAND_BLOCK);
        var block = (net.minecraft.world.level.block.entity.CommandBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(1, 1, 1));
        var rcon = new net.minecraft.server.rcon.RconConsoleSource(server);
        var tick = server.getFunctions().getGameLoopSender().withPermission(4);
        var load = server.getFunctions().getGameLoopSender().withPermission(4);
        helper.assertTrue(tick.source == server && load.source == server && tick.hasPermission(4) && load.hasPermission(4)
                && tick.withSuppressedOutput() == tick && load.withSuppressedOutput() == load,
                "Tick/load functions share console identity but suppress output even at permission four");
        var rejected = List.of(tick, load, input.source.withSuppressedOutput(), playerStack.withSuppressedOutput(),
                block.getCommandBlock().createCommandSourceStack().withPermission(4), rcon.createCommandSourceStack().withPermission(4),
                input.source.withSource(new Output()), input.source.withEntity(player));
        var data = ResourceEscrowSavedData.existing(server); var before = data == null ? null : EscrowCodec.encode(data.ledger());
        for (var source : rejected) {
            try { EscrowCommandActor.require(source); helper.fail("Untrusted settlement actor accepted"); }
            catch (IllegalArgumentException refusal) { helper.assertTrue(refusal.getMessage().equals(EscrowCommandActor.REFUSAL), "Explicit provenance refusal"); }
            for (String choice : List.of("accept-current", "abandon", "settle-floor")) {
                String command = "arce escrow " + choice + " " + UUID.randomUUID();
                helper.assertTrue(dispatcher.execute(command, source) == 0, "Untrusted preview refused");
                helper.assertTrue(dispatcher.execute(command + " confirm " + "0".repeat(64), source) == 0, "Untrusted confirm refused");
            }
        }
        // Execute both function forms through the actual function manager, not only Brigadier.
        for (String name : List.of("tick", "load")) {
            var outcomes = new ArrayList<Integer>();
            var commands = new ArrayList<String>(); var returns = new ArrayList<String>();
            var source = server.getFunctions().getGameLoopSender().withPermission(4);
            String command = "arce escrow accept-current " + UUID.randomUUID();
            var expected = List.of(command, command + " confirm " + "0".repeat(64));
            var function = net.minecraft.commands.CommandFunction.fromLines(ModIdentity.id("escrow_actor_" + name), dispatcher, source,
                    expected);
            int executed = server.getFunctions().execute(function, source, new net.minecraft.server.ServerFunctionManager.TraceCallbacks() {
                @Override public void onCommand(int depth, String text) { commands.add(text); }
                @Override public void onReturn(int depth, String text, int result) { returns.add(text); outcomes.add(result); }
                @Override public void onError(int depth, String message) { helper.fail("Function execution error: " + message); }
                @Override public void onCall(int depth, net.minecraft.resources.ResourceLocation id, int size) { helper.fail("Unexpected nested function"); }
            });
            helper.assertTrue(executed == 2 && commands.equals(expected) && returns.equals(expected), "Exactly two function commands executed and returned");
            helper.assertTrue(outcomes.equals(List.of(0, 0)), "Permission-four function preview/confirm refused: " + outcomes);
        }
        // Diagnostics remain available to non-player permission-two sources, including RCON.
        rcon.prepareForCommand();
        helper.assertTrue(dispatcher.execute("arce escrow status", rcon.createCommandSourceStack().withPermission(2)) == 1,
                "RCON permission-two status remains available");
        helper.assertTrue(rcon.getCommandResponse().length() < 3600, "RCON output bound");
        for (String command : List.of("inspect", "retry")) {
            rcon.prepareForCommand();
            dispatcher.execute("arce escrow " + command + " " + UUID.randomUUID(), rcon.createCommandSourceStack().withPermission(2));
            helper.assertTrue(!rcon.getCommandResponse().isEmpty() && rcon.getCommandResponse().length() < 1200
                    && !rcon.getCommandResponse().contains(EscrowCommandActor.REFUSAL), "Permission-two RCON diagnostics remain bounded");
        }
        var after = ResourceEscrowSavedData.existing(server);
        helper.assertTrue(before == null ? after == null : after != null && before.equals(EscrowCodec.encode(after.ledger())),
                "Rejected actors do not create or mutate escrow");
        helper.succeed();
    }
    static final class Output implements CommandSource {
        final List<String> lines = new ArrayList<>();
        @Override public void sendSystemMessage(Component message) { lines.add(message.getString()); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }
}
