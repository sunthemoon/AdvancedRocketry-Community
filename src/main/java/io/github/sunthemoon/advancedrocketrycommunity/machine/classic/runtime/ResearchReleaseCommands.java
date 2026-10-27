package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Phase;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in disposable native verification only. Permission 4, fixed positions, no player-provided NBT. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class ResearchReleaseCommands {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("arce-w21c-native-owner".getBytes(StandardCharsets.UTF_8));
    private static BlockPos position(int index) { return new BlockPos(8 + 16 * index, 300, 8); }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("advancedrocketrycommunity.releaseTestHooks")
                || !Boolean.getBoolean("advancedrocketrycommunity.researchVerificationPaused")) { return; }
        var node = Commands.literal("research-test").requires(source -> source.hasPermission(4));
        for (String action : new String[]{"setup", "start", "snapshot", "claim", "rebind", "cut-survey", "cut-credit", "replay"}) {
            node.then(Commands.literal(action).executes(context -> run(context.getSource(), action, 0)));
        }
        node.then(Commands.literal("step").then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1_000))
                .executes(context -> run(context.getSource(), "step", IntegerArgumentType.getInteger(context, "ticks")))));
        event.getDispatcher().register(Commands.literal("arce").then(node));
    }
    private static int run(CommandSourceStack source, String action, int ticks) {
        MinecraftServer server = source.getServer(); var level = server.overworld(); var service = ResearchRuntime.service(level);
        var data = SatelliteMissionSavedData.get(server);
        try {
            if (action.equals("setup")) {
                level.setDayTime(18_000); level.setWeatherParameters(100_000, 0, false, false); level.setRainLevel(0); level.updateSkyBrightness();
                for (int i = 0; i < 3; i++) {
                    var fixture = new ResearchFixture(level, position(i), i != 0, Direction.NORTH, OWNER);
                    if (!fixture.controller.formed()) { throw new IllegalStateException("Native fixture did not form: " + fixture.controller.reason()); }
                    var block = fixture.controller.getBlockState();
                    if (!new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE).isCorrectToolForDrops(block)
                            || new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE).isCorrectToolForDrops(block)) {
                        throw new IllegalStateException("Research controller tool tags differ from the classic iron-tool tier");
                    }
                    fixture.feed(i == 0 ? 10_000 : 4_000);
                }
            } else if (action.equals("start")) {
                var controller = controller(server, 0);
                if (!service.intent(controller, OWNER, 0)) { throw new IllegalStateException("Native START: " + controller.researchStatus()); }
                long epoch = data.saveEpoch(); int before = controller.storedEnergy();
                // ADR-074 item 6: the failure is injected on the bound target by a same-target policy seam and awaited;
                // the live writer is never bound to another path.
                boolean failed = data.failOneWriteForTests(server)
                        != io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryProof.Outcome.COMMITTED;
                if (!failed || data.saveEpoch() != epoch || service.advance(controller)
                        || controller.researchStatus() != ResearchStatus.WAITING_SAVE || controller.storedEnergy() != before) {
                    throw new IllegalStateException("Failed flush did not keep WAITING_SAVE without payment");
                }
                AdvancedRocketryCommunity.LOGGER.info("ARCE_RESEARCH_PROBE failed_flush=WAITING_SAVE epoch={} fe={} progress=0", epoch, before);
                data.flush(server);
            } else if (action.equals("claim") || action.equals("rebind")) {
                var target = controller(server, action.equals("claim") ? 1 : 2); int before = target.storedEnergy();
                if (!service.intent(target, OWNER, action.equals("claim") ? 4 : 5)) { throw new IllegalStateException("Native claim/rebind refused"); }
                var job = service.work(target).orElseThrow(); int progress = job.analysis().progress();
                if (service.advance(target) || target.researchStatus() != ResearchStatus.WAITING_SAVE || target.storedEnergy() != before
                        || service.work(target).orElseThrow().analysis().progress() != progress) { throw new IllegalStateException("New claim paid before save"); }
                AdvancedRocketryCommunity.LOGGER.info("ARCE_RESEARCH_PROBE {}=WAITING_SAVE job={} processor={} progress={} fe={}",
                        action, job.jobId(), target.device().id(), progress, before); data.flush(server);
            } else if (action.equals("step") || action.startsWith("cut-")) {
                var job = job(server); var controller = controller(server, job.phase() == Phase.ACTIVE ? 0
                        : job.analysis().claim().orElseThrow().processor().pos() == position(1).asLong() ? 1 : 2);
                int count = action.equals("step") ? ticks : 1;
                for (int i = 0; i < count; i++) {
                    if (controller.storedEnergy() < 20) {
                        var plug = controller.loadedHatch(controller.controllerState().partPositions().iterator().next());
                        if (plug == null) { throw new IllegalStateException("Native plug unavailable"); }
                        plug.getCapability(ForgeCapabilities.ENERGY).orElseThrow(() -> new IllegalStateException("Native FE"))
                                .receiveEnergy(10_000, false);
                    }
                    if (!service.advance(controller)) { throw new IllegalStateException("Native paid advance: " + controller.researchStatus()); }
                }
                if (action.startsWith("cut-")) {
                    snapshot(server, action);
                    // Hold this command before END-tick coalescing. The harness kills its own process here;
                    // if it does not, the bounded wait ends and the verification fails rather than hanging.
                    java.util.concurrent.locks.LockSupport.parkNanos(15_000_000_000L);
                    throw new IllegalStateException("Native crash-cut process was not stopped in time");
                }
            } else if (action.equals("replay")) {
                var job = job(server); var target = controller(server, job.analysis().claim().orElseThrow().processor().pos() == position(1).asLong() ? 1 : 2);
                int balance = data.account(OWNER).balance(), fe = target.storedEnergy();
                if (service.advance(target) || service.intent(target, OWNER, 4) || data.account(OWNER).balance() != balance
                        || target.storedEnergy() != fe) { throw new IllegalStateException("Credit replay changed balance/FE"); }
            }
            snapshot(server, action); return 1;
        } catch (Exception failure) {
            AdvancedRocketryCommunity.LOGGER.error("ARCE_RESEARCH_TEST_FAILED action={}", action, failure); return 0;
        }
    }
    private static ResearchControllerBlockEntity controller(MinecraftServer server, int index) {
        var controller = loaded(server, index);
        if (controller == null) { throw new IllegalStateException("Native research controller not loaded: " + position(index)); }
        return controller;
    }
    /** Null while a fixture chunk is still loading after a restart; forced chunks load after the server is ready. */
    private static ResearchControllerBlockEntity loaded(MinecraftServer server, int index) {
        BlockPos position = position(index); var chunk = server.overworld().getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(position) instanceof ResearchControllerBlockEntity controller ? controller : null;
    }
    private static GroundSurveyJob job(MinecraftServer server) {
        return SatelliteMissionSavedData.get(server).groundSurveyQuery(jobs -> jobs.owned(OWNER)).stream().findFirst().orElseThrow();
    }
    private static void snapshot(MinecraftServer server, String action) {
        var json = new JsonObject(); json.addProperty("action", action);
        boolean ready = true;
        for (int i = 0; i < 3; i++) {
            // An unloaded fixture reads as not ready; its missing fields fail any comparison that needs them.
            var controller = loaded(server, i);
            if (controller == null) { ready = false; continue; }
            json.addProperty("device" + i, controller.device().id().toString());
            json.addProperty("fe" + i, controller.storedEnergy());
            ready &= controller.formed() && server.overworld().isPositionEntityTicking(position(i));
        }
        var observatory = loaded(server, 0);
        ready &= observatory != null && server.overworld().canSeeSky(observatory.getBlockPos().offset(0, 3, 1)); json.addProperty("ready", ready);
        var data = SatelliteMissionSavedData.get(server); var owned = data.groundSurveyQuery(jobs -> jobs.owned(OWNER));
        if (!owned.isEmpty()) {
            var job = owned.get(0); json.addProperty("job", job.jobId().toString()); json.addProperty("instance", job.instanceId().toString());
            json.addProperty("source", job.source().id().toString()); json.addProperty("phase", job.phase().name());
            json.addProperty("survey_progress", job.surveyProgress()); json.addProperty("analysis_progress", job.analysis().progress());
            json.addProperty("analysis", job.analysis().state().name());
            json.addProperty("processor", job.analysis().claim().map(claim -> claim.processor().id().toString()).orElse(""));
        }
        json.addProperty("balance", data.account(OWNER).balance()); json.addProperty("epoch", data.saveEpoch());
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RESEARCH_SNAPSHOT {}", json);
    }
    private ResearchReleaseCommands() { }
}
