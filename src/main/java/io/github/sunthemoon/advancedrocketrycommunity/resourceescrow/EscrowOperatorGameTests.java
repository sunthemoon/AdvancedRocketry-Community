package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.AuditLogCapture;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowOperatorGameTests {
    static final String REFUSED = "Settlement intent refused: " + EscrowCommandActor.REFUSAL;
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
    /** Final review A, F3: a gated ledger holds a real REPAIR operation, so only the provenance gate can refuse it. */
    @GameTest(template = "empty", batch = "escrow_operator_provenance", timeoutTicks = 1_200)
    public static void settlementRequiresPlayerOrUnsuppressedExactConsoleProvenance(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer();
        var position = new BlockPos(690_008, 100, 690_008); var chunk = new ChunkPos(position);
        level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); level.getChunk(chunk.x, chunk.z);
        var data = ResourceEscrowSavedData.forTests(server); var coordinator = data.bind(server);
        var pacer = new GameTestTickPacer(server);
        class Scenario {
            int step; EscrowTestEndpoints.Fixture fixture; RocketEntity rocket; EscrowOperation operation;
            void tick() {
                if (step == 4) { return; }
                pacer.pace(); if (!level.areEntitiesLoaded(chunk.toLong()) || !level.isPositionEntityTicking(position)) { return; }
                if (step == 0) {
                    var journal = RocketTransferSavedData.get(server); if (!journal.retryChecked(server)) { return; }
                    fixture = EscrowTestEndpoints.create(level, position, data.ledger().id());
                    rocket = RocketSettlementIdleGameTests.createRocket(level, position, fixture.logical());
                    var sample = fixture.operation(coordinator.nextSequence()); var fuel = rocket.flightData().orElseThrow().fuel();
                    var after = fuel.withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000);
                    var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level.dimension().location().toString(), rocket.getUUID(), fixture.logical(),
                            EscrowAdapterIds.ROCKET, chunk.x, 0, chunk.z, EscrowEndpoint.Unit.PRIMARY, 0, fuel.resourceSignature(), after.resourceSignature());
                    var payload = sample.payload(); payload.putString("FluidName", ModIdentity.id("rocket_fuel").toString());
                    var guard = journal.acknowledgedGuard(fixture.logical()).orElseThrow();
                    var model = new EscrowOperation(sample.id(), sample.sequence(), rocket.ownerId().orElseThrow(), sample.createdTick(), sample.phase(),
                            sample.reason(), payload, RocketNativeResources.signature(payload), sample.source(), endpoint,
                            new EscrowGuard(fixture.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
                    var capture = EscrowNativeBinding.captureTest(model, rocket, fixture.source(), fuel, after, data.ledger());
                    operation = capture.operation();
                    helper.assertTrue(level.addFreshEntity(rocket), "Rocket fixture publication failed");
                    coordinator.prepareTest(operation, capture.before(), acknowledged -> helper.assertTrue(acknowledged, "Fixture L1 failed")); step = 1;
                } else if (step == 1) { if (coordinator.commitPreparations()) { step = 2; } }
                else if (step == 2) {
                    if (!coordinator.writerIdle()) { return; }
                    fixture.applySource(operation); coordinator.transition(operation.id(), EscrowReason.OTHER_STATE); coordinator.persistTransitions(); step = 3;
                } else if (step == 3) {
                    if (!coordinator.writerIdle() || data.ledger().records().stream().noneMatch(op -> op.id().equals(operation.id())
                            && op.phase() == EscrowOperation.Phase.REPAIR)) { return; }
                    try { provenance(helper, data, coordinator, operation.id()); }
                    catch (com.mojang.brigadier.exceptions.CommandSyntaxException unparsed) { helper.fail("Escrow command did not parse: " + unparsed.getMessage()); }
                    // The REPAIR record stays in this run's gated ledger; its fixture endpoints are removed.
                    rocket.discard(); fixture.bank().discard(); level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                    level.getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); step = 4; helper.succeed();
                }
            }
        }
        var scenario = new Scenario(); helper.onEachTick(scenario::tick);
    }
    private static void provenance(GameTestHelper helper, ResourceEscrowSavedData data, EscrowCoordinator coordinator, UUID repair)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var server = helper.getLevel().getServer(); var dispatcher = server.getCommands().getDispatcher();
        var operator = EscrowRuntime.of(server).settlement();
        // Pin the source construction used by DedicatedServer$1 -> ConsoleInput -> handleConsoleInputs.
        var input = new net.minecraft.server.ConsoleInput("arce escrow status", server.createCommandSourceStack());
        helper.assertTrue(input.source.source == server && input.source.getEntity() == null
                && input.source.withSuppressedOutput() != input.source, "Console entry stack provenance");
        helper.assertTrue(EscrowCommandActor.require(input.source).kind() == EscrowCommandActor.Kind.CONSOLE, "Typed console actor");
        var player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "escrowActor"));
        var playerStack = player.createCommandSourceStack().withPermission(2);
        helper.assertTrue(EscrowCommandActor.require(playerStack).id().equals(player.getUUID()), "Player audit UUID retained");

        helper.setBlock(new BlockPos(1, 1, 1), Blocks.COMMAND_BLOCK);
        var block = (net.minecraft.world.level.block.entity.CommandBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
        var rcon = new net.minecraft.server.rcon.RconConsoleSource(server);
        var tick = server.getFunctions().getGameLoopSender().withPermission(4);
        var load = server.getFunctions().getGameLoopSender().withPermission(4);
        helper.assertTrue(tick.source == server && load.source == server && tick.hasPermission(4) && load.hasPermission(4)
                && tick.withSuppressedOutput() == tick && load.withSuppressedOutput() == load,
                "Tick/load functions share console identity but suppress output even at permission four");
        var substituted = new Output();
        try (var console = new AuditLogCapture("Settlement intent refused: ")) {
            int[] logged = {0};
            var commandBlock = block.getCommandBlock();
            // Unsuppressed rejected stacks report the refusal where their output lands; suppressed stacks report nothing.
            var rejected = List.of(Rejected.suppressed(tick), Rejected.suppressed(load),
                    Rejected.suppressed(input.source.withSuppressedOutput()), Rejected.suppressed(playerStack.withSuppressedOutput()),
                    new Rejected(commandBlock.createCommandSourceStack().withPermission(4), () -> commandBlock.setLastOutput(null),
                            () -> commandBlock.getLastOutput().getString()),
                    // The pinned RconConsoleSource appends "\n" after each message; read the message without that terminator.
                    new Rejected(rcon.createCommandSourceStack().withPermission(4), rcon::prepareForCommand, () -> {
                        String response = rcon.getCommandResponse();
                        return response.endsWith("\n") ? response.substring(0, response.length() - 1) : response;
                    }),
                    new Rejected(input.source.withSource(substituted), substituted.lines::clear, () -> String.join("\n", substituted.lines)),
                    new Rejected(input.source.withEntity(player), () -> logged[0] = console.lines().size(),
                            () -> String.join("\n", console.lines().subList(logged[0], console.lines().size()))));
            var ledger = EscrowCodec.encode(data.ledger()); var previews = operator.previewSnapshot();
            Runnable unchanged = () -> helper.assertTrue(ledger.equals(EscrowCodec.encode(data.ledger())) && coordinator.writerIdle()
                    && previews.equals(operator.previewSnapshot()), "A rejected actor changed the preview cache, ledger or writer");
            for (var source : rejected) {
                try { EscrowCommandActor.require(source.stack()); helper.fail("Untrusted settlement actor accepted"); }
                catch (IllegalArgumentException refusal) { helper.assertTrue(refusal.getMessage().equals(EscrowCommandActor.REFUSAL), "Explicit provenance refusal"); }
                for (String choice : List.of("accept-current", "abandon", "settle-floor")) {
                    // A random operation, as before, and the real REPAIR operation that an accepted actor can preview.
                    for (var selected : List.of(UUID.randomUUID(), repair)) {
                        String command = "arce escrow " + choice + " " + selected;
                        source.refused(helper, dispatcher.execute(command, source.reset()), "Untrusted preview refused");
                        source.refused(helper, dispatcher.execute(command + " confirm " + "0".repeat(64), source.reset()), "Untrusted confirm refused");
                        unchanged.run();
                    }
                }
            }
            // Control: the same preview from an accepted player or the exact console returns 1 and caches a preview.
            String preview = "arce escrow accept-current " + repair;
            helper.assertTrue(dispatcher.execute(preview, playerStack) == 1 && dispatcher.execute(preview, input.source) == 1, "Accepted actor preview refused");
            var cached = operator.previewSnapshot();
            var playerPreview = cached.get(player.getUUID()); var consolePreview = cached.get(EscrowCommandActor.CONSOLE_ID);
            helper.assertTrue(playerPreview != null && playerPreview != previews.get(player.getUUID()) && playerPreview.ids().contains(repair)
                    && consolePreview != null && consolePreview != previews.get(EscrowCommandActor.CONSOLE_ID) && consolePreview.ids().contains(repair),
                    "Accepted actors did not cache their previews");
            helper.assertTrue(ledger.equals(EscrowCodec.encode(data.ledger())) && coordinator.writerIdle(), "A preview wrote the ledger");
            // With both real digests cached, no rejected stack can confirm as either actor.
            for (var source : rejected) {
                for (var digest : List.of(playerPreview.digest(), consolePreview.digest())) {
                    source.refused(helper, dispatcher.execute(preview + " confirm " + digest, source.reset()), "Untrusted confirm with a cached digest refused");
                }
            }
            helper.assertTrue(cached.equals(operator.previewSnapshot()) && ledger.equals(EscrowCodec.encode(data.ledger())) && coordinator.writerIdle(),
                    "A rejected confirm changed the preview cache, ledger or writer");
            // Execute both function forms through the actual function manager, not only Brigadier.
            for (String name : List.of("tick", "load")) {
                var outcomes = new ArrayList<Integer>();
                var commands = new ArrayList<String>(); var returns = new ArrayList<String>();
                var source = server.getFunctions().getGameLoopSender().withPermission(4);
                var expected = List.of(preview, preview + " confirm " + consolePreview.digest());
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
                helper.assertTrue(cached.equals(operator.previewSnapshot()) && ledger.equals(EscrowCodec.encode(data.ledger())) && coordinator.writerIdle(),
                        "A function changed the preview cache, ledger or writer");
            }
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
    }
    /** A rejected stack and, unless its output is suppressed, where its refusal text lands. */
    private record Rejected(CommandSourceStack stack, Runnable clear, Supplier<String> output) {
        static Rejected suppressed(CommandSourceStack stack) { return new Rejected(stack, () -> { }, null); }
        CommandSourceStack reset() { clear.run(); return stack; }
        void refused(GameTestHelper helper, int result, String message) {
            helper.assertTrue(result == 0, message);
            if (output != null) { helper.assertTrue(output.get().endsWith(REFUSED), message + " without the provenance refusal: " + output.get()); }
        }
    }
    static final class Output implements CommandSource {
        final List<String> lines = new ArrayList<>();
        @Override public void sendSystemMessage(Component message) { lines.add(message.getString()); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }
}
