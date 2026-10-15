package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.command.ResourceRestartReleaseFixture.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketPropulsionInteraction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in, bounded native restart observer; absent properties leave ordinary servers untouched. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class ResourceRestartReleaseProbe {
    private static ResourceRestartReleaseProbe active;
    private final MinecraftServer server;
    private final String scenario;
    private final String stage;
    private final ResourceRestartReleaseFixture players = new ResourceRestartReleaseFixture();
    private Player owner;
    private Player passenger;
    private int ticks;
    private int step;
    private boolean done;

    private ResourceRestartReleaseProbe(MinecraftServer server, String scenario, String stage) {
        this.server = server; this.scenario = scenario; this.stage = stage;
        require(server.isDedicatedServer() && server.getPlayerList().getPlayerCount() == 0
                && server.getWorldData().getLevelName().equals("world-W63-" + scenario), "Not a disposable W63 server");
        require(List.of("F1", "F2", "F2_TWO").contains(scenario), "Unknown W63 scenario");
        require(List.of("seed", "check", "disassemble", "empty", "reconnect-first", "reconnect-second")
                .contains(stage), "Unknown W63 stage");
        for (var level : levels()) {
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 5; z++) { level.setChunkForced(x, z, true); }
            }
        }
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("advancedrocketrycommunity.releaseTestHooks")
                || System.getProperty("advancedrocketrycommunity.resourceRestartStage") == null) { return; }
        active = new ResourceRestartReleaseProbe(event.getServer(),
                System.getProperty("advancedrocketrycommunity.resourceRestartCase"),
                System.getProperty("advancedrocketrycommunity.resourceRestartStage"));
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        if (active != null && active.server == event.getServer()) { active.players.close(); active = null; }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (active == null || active.server != event.getServer() || event.phase != TickEvent.Phase.END) { return; }
        active.players.pump();
        if (active.done) { return; }
        try { active.advance(); }
        catch (RuntimeException failure) {
            active.done = true;
            AdvancedRocketryCommunity.LOGGER.error("ARCE_W63_FAILURE case={} stage={} step={} reason={}",
                    active.scenario, active.stage, active.step, failure.toString(), failure);
        }
    }

    private List<ServerLevel> levels() { return List.of(server.overworld(), server.getLevel(CelestialIds.MOON_LEVEL)); }
    private RocketTransferSavedData journal() { return RocketTransferSavedData.get(server); }
    private List<RocketEntity> rockets() {
        var result = new ArrayList<RocketEntity>();
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof RocketEntity rocket && !rocket.isRemoved()) { result.add(rocket); }
            }
        }
        return result;
    }

    private void advance() {
        require(++ticks < 1_600, "W63 stage timed out: " + stage + "/" + step);
        if (levels().stream().anyMatch(level -> !level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(ORIGIN).toLong()))) {
            return;
        }
        if (step == 0) {
            if (!journal().retryChecked(server)) { return; }
            if (stage.equals("seed")) { seed(server, !scenario.equals("F1"), scenario.equals("F2_TWO")); }
            step = 1;
            return;
        }
        if (!journal().acknowledgedCurrent()) { return; }
        if (stage.equals("empty")) {
            require(rockets().isEmpty() && journal().entries().isEmpty(), "Disassembled arrival rebuilt or journal retained");
            ensureOwner(levels().get(1));
            require(containerFluid() == 2_000, "Disassembly restart lost withdrawn fuel");
            complete("entities=0 primary=0 oxidizer=0 containers=2000 journal=0"); return;
        }
        var candidates = rockets();
        if (candidates.isEmpty() && ticks < 200) { return; }
        require(candidates.size() == 1, "Expected exactly one live rocket, found " + candidates.size());
        var rocket = candidates.get(0);
        require(rocket.operational() && rocket.assemblyTransactionId().filter(LOGICAL::equals).isPresent(), "Wrong rocket identity");
        if (scenario.equals("F1")) { arrival(rocket); }
        else { source(rocket); }
    }

    private void arrival(RocketEntity rocket) {
        if (!RocketRuntime.resourceMutationAllowed(rocket)) { return; }
        ensureOwner((ServerLevel) rocket.level());
        if (stage.equals("seed") && step == 1) {
            balances(rocket, 2_000, 0);
            require(rocket.flightData().orElseThrow().state() == RocketFlightState.LANDED, "Arrival not LANDED");
            require(containerFluid() == 0, "Owner seed inventory not empty");
            for (int bucket = 1; bucket <= 2; bucket++) {
                owner.getInventory().selected = bucket - 1;
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                require(RocketPropulsionInteraction.exchange(owner, rocket, InteractionHand.MAIN_HAND)
                        == InteractionResult.CONSUME, "Arrival bucket withdrawal refused");
                balances(rocket, 2_000 - bucket * 1_000, 0);
                require(containerFluid() == bucket * 1_000, "Bucket withdrawal not conserved");
            }
        }
        balances(rocket, 0, 0);
        require(containerFluid() == 2_000, "Arrival owner inventory lost or duplicated fuel");
        require(rocket.flightData().orElseThrow().state() == RocketFlightState.ASSEMBLED, "Drained state differs");
        if (step == 1) {
            require(journal().entries().size() == 1 && journal().find(TRANSFER).orElseThrow().phase().name().equals("COMMITTED"),
                    "Arrival reservation missing");
        }
        if (stage.equals("disassemble")) {
            var snapshot = rocket.snapshot().orElseThrow();
            RocketRuntime.requestDisassembly(owner, rocket);
            if (step == 1 && !rocket.isRemoved()) {
                // The first player request starts durable reservation retirement and refuses
                // until its real asynchronous write acknowledges. Retry only after that ack.
                require(journal().find(TRANSFER).isEmpty() && !journal().acknowledgedCurrent(),
                        "Disassembly did not begin journal retirement");
                step = 2;
                return;
            }
            require(rocket.isRemoved(), "Arrival disassembly refused");
            require(journal().find(TRANSFER).isEmpty(), "Disassembly retained COMMITTED record");
            for (var block : snapshot.blocks()) {
                var at = snapshot.sourceOrigin().add(block.position());
                require(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(rocket.level()
                        .getBlockState(new net.minecraft.core.BlockPos(at.x(), at.y(), at.z())).getBlock())
                        .equals(block.state().blockId()), "Disassembly did not restore a snapshot block");
            }
            complete("entities=0 primary=0 oxidizer=0 containers=2000 journal=0 restored=" + snapshot.blocks().size());
        } else { complete("entities=1 primary=0 oxidizer=0 containers=2000 journal=1 state=ASSEMBLED"); }
    }

    private void source(RocketEntity rocket) {
        if (rocket.flightData().orElseThrow().state() != RocketFlightState.FUELED) { return; }
        ensureOwner((ServerLevel) rocket.level());
        balances(rocket, 2_000, 1_000);
        if (!stage.equals("seed")) { require(containerFluid() == 1_000, "Restart changed owner fuel containers"); }
        if (stage.startsWith("reconnect")) {
            var selectedPassenger = stage.equals("reconnect-first") ? FIRST : SECOND;
            var absentPassenger = stage.equals("reconnect-first") ? SECOND : FIRST;
            require(server.getPlayerList().getPlayer(absentPassenger) == null, "Recorded passenger sessions overlap");
            if (passenger == null) {
                passenger = players.login(server, server.overworld(), selectedPassenger);
                return;
            }
            if (passenger.getVehicle() != rocket) { return; }
            AdvancedRocketryCommunity.LOGGER.info("ARCE_W63_RECONNECT online={} absent={} mounted=true",
                    selectedPassenger, absentPassenger);
            if (scenario.equals("F2")) {
                if (!journal().entries().isEmpty() || !RocketRuntime.resourceMutationAllowed(rocket)) { return; }
                // Keep the pre-restart filled canister: the test supplies its empty
                // exchange container in a different slot, never over saved fuel.
                owner.getInventory().selected = 1;
                require(owner.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "Reconnect fixture slot is occupied");
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EMPTY_CANISTER.get()));
                require(RocketPropulsionInteraction.exchange(owner, rocket, InteractionHand.MAIN_HAND)
                        == InteractionResult.CONSUME, "Mutation refused after reconnect");
                balances(rocket, 1_000, 1_000);
                require(containerFluid() == 2_000, "Reconnect exchange did not conserve owner fuel");
                complete("entities=1 primary=1000 oxidizer=1000 containers=2000 journal=0 unlocked=true passenger=mounted");
            } else {
                require(journal().entries().size() == 1 && !RocketRuntime.resourceMutationAllowed(rocket),
                        "Nonoverlapping passenger sessions unexpectedly retired or unlocked source");
                complete("entities=1 primary=2000 oxidizer=1000 containers=1000 journal=1 unlocked=false passenger=mounted nonoverlap=true");
            }
            return;
        }
        require(!RocketRuntime.resourceMutationAllowed(rocket), "Offline source permits mutation");
        var record = journal().find(TRANSFER).orElseThrow();
        require(record.phase().name().equals("PREPARED")
                && record.sourceFlightData().fuel().equals(rocket.flightData().orElseThrow().fuel()), "Source vector differs from journal");
        if (stage.equals("seed")) {
            refuse(rocket, new ItemStack(ModItems.EMPTY_CANISTER.get()), "withdrawal");
            refuse(rocket, new ItemStack(ModItems.HYDROGEN_CANISTER.get()), "fill");
            var at = ORIGIN.east(3);
            server.overworld().setBlockAndUpdate(at, ModBlocks.FUEL_LOADER.get().defaultBlockState());
            var loader = (FuelLoaderBlockEntity) server.overworld().getBlockEntity(at);
            loader.assignOwner(OWNER);
            require(loader.itemHandler().insertItem(0, new ItemStack(ModItems.ROCKET_FUEL_CELL.get()), false).isEmpty(),
                    "Loader fixture input refused");
            var before = loader.saveWithoutMetadata();
            FuelLoaderBlockEntity.serverTick(server.overworld(), at, loader.getBlockState(), loader);
            balances(rocket, 2_000, 1_000);
            require(before.equals(loader.saveWithoutMetadata()), "Pending loader changed stored resources");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_W63_LOADER status={} item={} buffered={} target={}",
                    loader.status().diagnosticKey(), loader.itemHandler().getStackInSlot(0), loader.bufferedUnits(), loader.targetRocketId());
            // TASK-W63 Root decision (2026-10-15): generic no-target feedback is
            // accepted here; exclusion and resource conservation remain mandatory.
            require(loader.status().diagnosticKey().equals("waiting_for_rocket")
                    && loader.targetRocketId().isEmpty() && loader.bufferedUnits() == 0,
                    "Pending loader selected a target, buffered fuel, or changed its waiting status");
        }
        var loader = (FuelLoaderBlockEntity) server.overworld().getBlockEntity(ORIGIN.east(3));
        require(loader != null && loader.targetRocketId().isEmpty() && loader.bufferedUnits() == 0
                && loader.itemHandler().getStackInSlot(0).is(ModItems.ROCKET_FUEL_CELL.get())
                && loader.itemHandler().getStackInSlot(0).getCount() == 1, "Offline restart changed loader resources");
        require(containerFluid() == 1_000, "Offline source owner fuel differs");
        complete("entities=1 primary=2000 oxidizer=1000 containers=1000 journal=1 unlocked=false vector=exact");
    }

    private void refuse(RocketEntity rocket, ItemStack stack, String operation) {
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var before = owner.getInventory().save(new ListTag());
        owner.message = null;
        require(RocketPropulsionInteraction.exchange(owner, rocket, InteractionHand.MAIN_HAND) == InteractionResult.FAIL,
                "Pending hand " + operation + " accepted");
        require(before.equals(owner.getInventory().save(new ListTag())), "Pending hand operation changed inventory");
        require(owner.message != null && owner.message.getContents() instanceof TranslatableContents message
                && message.getKey().equals("message.advancedrocketrycommunity.propulsion.recovery_pending"),
                "Pending hand operation lacks recovery_pending message");
        balances(rocket, 2_000, 1_000);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W63_HAND operation={} result=FAIL message={} inventory=unchanged",
                operation, ((TranslatableContents) owner.message.getContents()).getKey());
    }

    private void ensureOwner(ServerLevel level) {
        if (owner != null) { return; }
        owner = players.login(server, level, OWNER);
        owner.teleportTo(level, 72.5, 100, 73.5, 0, 0);
    }
    private long containerFluid() {
        long amount = 0;
        for (var stack : owner.getInventory().items) {
            var handler = stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElse(null);
            if (handler != null) { amount += (long) handler.getFluidInTank(0).getAmount() * stack.getCount(); }
        }
        return amount;
    }
    private void balances(RocketEntity rocket, long primary, long oxidizer) {
        var fuel = rocket.flightData().orElseThrow().fuel();
        require(fuel.amount() == primary && fuel.propulsion().orElseThrow().oxidizer().amount() == oxidizer,
                "Wrong live bank vector: " + fuel.summary());
    }
    private void complete(String detail) {
        done = true;
        server.getPlayerList().saveAll();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W63_RESULT case={} stage={} {}", scenario, stage, detail);
    }
}
