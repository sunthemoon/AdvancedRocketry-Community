package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.ReleaseTestCommands;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * W27d1's gated native fixture for {@code scripts/run_v180_bay_forced_stop.py} (W27A-DESIGN section 9): OP-only, and
 * only on a release-test dedicated server with test-owned players. It builds and launches the neutral, hand-fuelled
 * Earth-to-Moon rocket with one data bay and one solar bay, reports the claim, receipts, transfer and rocket, and fills
 * the shared pending capacity with inert INTENT receipts. The deployment itself runs only through the coordinator.
 */
public final class BayReleaseTestCommands {
    /** The fixture owner: the bays, both packages and the rocket. */
    static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-000000128d01");
    private static final UUID FILL_OWNER = UUID.fromString("00000000-0000-4000-8000-000000128d02");
    private static final Logger LOGGER = LogUtils.getLogger();

    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(ReleaseTestCommands.PROPERTY)) { return; }
        BayCopyReleaseTestCommands.register(event);
        BayReleasedTestCommands.register(event);
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("bay").then(Commands.literal("release-test")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("fixture").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> {
                            // The gate decides before the position is resolved against a Level (W128R-R1).
                            EscrowTestEndpoints.requireEnabled(context.getSource().getServer());
                            return fixture(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"), false);
                        })
                        // W170 B4: the second bay holds a survey package, a non-data kind that needs no research.
                        .then(Commands.literal("survey").executes(context -> {
                            EscrowTestEndpoints.requireEnabled(context.getSource().getServer());
                            return fixture(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"), true);
                        }))))
                // W170 B16: a paused receipt replacement continues without a stop (its guard decides at the move).
                .then(Commands.literal("release").executes(context -> release(context.getSource())))
                // W170-R22's B16 changes while D7's guard is paused: motion, a joining copy and its leave, a reload.
                .then(Commands.literal("nudge").then(Commands.argument("logical", UuidArgument.uuid())
                        .executes(context -> nudge(context.getSource(), UuidArgument.getUuid(context, "logical")))))
                .then(Commands.literal("copy").then(Commands.argument("logical", UuidArgument.uuid())
                        .executes(context -> copy(context.getSource(), UuidArgument.getUuid(context, "logical")))))
                .then(Commands.literal("discard-copy").then(Commands.argument("logical", UuidArgument.uuid())
                        .executes(context -> discardCopies(context.getSource(), UuidArgument.getUuid(context, "logical")))))
                .then(Commands.literal("reload").then(Commands.argument("logical", UuidArgument.uuid())
                        .executes(context -> reload(context.getSource(), UuidArgument.getUuid(context, "logical")))))
                .then(Commands.literal("status").then(Commands.argument("logical", UuidArgument.uuid())
                        .executes(context -> status(context.getSource(), UuidArgument.getUuid(context, "logical")))))
                .then(Commands.literal("fill").then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(context -> fill(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("pending").executes(context -> pending(context.getSource()))))));
    }

    private static int fixture(CommandSourceStack source, BlockPos origin, boolean survey) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        ServerLevel level = server.overworld();
        if (!level.isPositionEntityTicking(origin)) {
            LOGGER.info("ARCE_RELEASE_BAY_FIXTURE_WAIT origin={}", origin.toShortString());
            return 0;
        }
        var data = BayDeploymentGameTestSupport.dataPackage(OWNER);
        var solar = survey ? BayDeploymentGameTestSupport.surveyPackage(OWNER) : BayDeploymentGameTestSupport.solarPackage(OWNER);
        List<BlockPos> blocks = List.of(origin, origin.above(), origin.above(2), origin.west(), origin.east(), origin.east().above());
        level.setBlock(origin, ModBlocks.ROCKET_MOTOR.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState(), Block.UPDATE_ALL);
        for (var bay : List.of(new Object[] {origin.east(), data}, new Object[] {origin.east().above(), solar})) {
            level.setBlock((BlockPos) bay[0], ModBlocks.ROCKET_SATELLITE_BAY.get().defaultBlockState(), Block.UPDATE_ALL);
            CompoundTag outer = new CompoundTag();
            outer.put(SatelliteBayRoot.KEY, BayDeploymentGameTestSupport.root(OWNER, (net.minecraft.world.item.ItemStack) bay[1]));
            ((SatelliteBayBlockEntity) level.getBlockEntity((BlockPos) bay[0])).load(outer);
        }
        var scan = new RocketStructureScanTask(new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()),
                level.dimension().location(), new RocketPosition(origin.getX(), origin.getY(), origin.getZ()), UUID.randomUUID(),
                level.getGameTime());
        var snapshot = scan.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK).snapshot().orElseThrow();
        // The packages live only in the rocket: the world bays are removed without drops.
        for (BlockPos position : blocks) {
            level.removeBlockEntity(position);
            level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        RocketEntity rocket = ModEntities.ROCKET.get().create(level);
        UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID();
        rocket.initialize(snapshot, logical, OWNER);
        if (!level.addFreshEntity(rocket)) { throw new IllegalStateException("The bay fixture rocket was not added"); }
        var flight = rocket.flightData().orElseThrow();
        if (Boolean.getBoolean("advancedrocketrycommunity.bayOfflinePassenger")) {
            flight = flight.withPassengers(flight.passengers().assign(BayCopyReleaseTestCommands.PASSENGER).orElseThrow());
        }
        rocket.updateFlightData(flight.withFuel(flight.fuel().fill(flight.fuel().capacity()).state(), level.getGameTime()));
        var launched = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, transfer);
        LOGGER.info("ARCE_RELEASE_BAY_FIXTURE logical={} transfer={} rocket={} data={} solar={} launch={} packages={}", logical,
                transfer, rocket.getUUID(), BayDeploymentGameTestSupport.satellite(data), BayDeploymentGameTestSupport.satellite(solar),
                launched.code(), signatures(snapshot));
        source.sendSuccess(() -> Component.literal("Bay fixture launched"), false);
        return 1;
    }

    private static int status(CommandSourceStack source, UUID logical) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        var bays = RocketRuntime.installedBays().orElseThrow();
        var claim = bays.claimForTests(logical);
        var session = bays.sessionForTests();
        var registry = SatelliteMissionSavedData.get(server);
        String receipts = session.receipts().stream().filter(receipt -> receipt.rocket().equals(logical))
                .map(receipt -> receipt.op() + ":" + receipt.satellite() + ":" + receipt.phase() + ":" + receipt.reason() + ":"
                        + session.acknowledged(receipt.op()) + ":" + registry.satellite(receipt.satellite()).isPresent())
                .collect(Collectors.joining(","));
        var record = RocketTransferSavedData.get(server).findByLogicalRocket(logical).orElse(null);
        RocketEntity rocket = null;
        for (ServerLevel level : server.getAllLevels()) {
            for (RocketEntity candidate : level.getEntities(ModEntities.ROCKET.get(),
                    entity -> entity.assemblyTransactionId().filter(logical::equals).isPresent())) { rocket = candidate; }
        }
        RocketEntity found = rocket;
        // W170: each bay root's consumed operation ("-" while it holds its package), and the fixture owner's satellites.
        String consumed = found == null ? "NONE" : BayDeploymentGameTestSupport.consumedOps(found.snapshot().orElseThrow()).stream()
                .map(op -> op == null ? "-" : op.toString()).collect(Collectors.joining("+"));
        LOGGER.info("ARCE_RELEASE_BAY_STATUS logical={} claim={} hold={} reason={} receipts=[{}] transfer={} rocket={} state={} y={} "
                        + "packages={} locked={} consumed={} owned={}", logical, claim.map(value -> value.stage().name()).orElse("NONE"),
                claim.map(value -> value.hold().name()).orElse("NONE"), claim.map(io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim::reason).orElse("NONE"), receipts,
                record == null ? "NONE" : record.phase().name(), found == null ? "NONE" : found.getUUID(),
                found == null ? "NONE" : found.flightData().map(data -> data.state().name()).orElse("NONE"),
                found == null ? "NONE" : String.valueOf(found.getY()),
                found == null ? "NONE" : signatures(found.snapshot().orElseThrow()),
                io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts.holdsRocket(server, logical), consumed,
                registry.operational() ? registry.ownerSatellites(OWNER) : -1);
        source.sendSuccess(() -> Component.literal("Bay status reported"), false);
        return 1;
    }

    /** Inert INTENT receipts of rockets that never fly: each holds one pending slot, at most 8 per invented rocket. */
    private static int fill(CommandSourceStack source, int count) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        String hash = RocketNativeResources.hash("w27d1-fill");
        long tick = server.getTickCount();
        int queued = 0;
        UUID rocket = UUID.randomUUID();
        for (int n = 0; n < count; n++) {
            if (n % 8 == 0) { rocket = UUID.randomUUID(); }
            var receipt = new BayReceipt(UUID.randomUUID(), BayReceiptTestHooks.nextSequence(server), rocket, new RocketPosition(0, n % 8, 0),
                    UUID.randomUUID(), FILL_OWNER, ModIdentity.id("data_satellite").toString(), SatelliteKind.DATA,
                    ModIdentity.id("earth").toString(), hash, UUID.randomUUID(), hash, null, hash, null, BayReceipt.Phase.INTENT, "NONE",
                    false, false, 0, tick, tick, 0);
            if (BayReceiptTestHooks.put(server, receipt, outcome -> { }).queued()) { queued++; }
        }
        LOGGER.info("ARCE_RELEASE_BAY_FILLED requested={} queued={}", count, queued);
        source.sendSuccess(() -> Component.literal("Bay pending capacity filled"), false);
        return queued;
    }

    private static int release(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime.of(server).bayReceipts(server).commitCutForTests(server)
                .orElseThrow().release();
        LOGGER.info("ARCE_RELEASE_BAY_RELEASE_REQUESTED");
        source.sendSuccess(() -> Component.literal("Bay commit cut released"), false);
        return 1;
    }

    /** The claim's established source of L in T's source Level, or a refusal (W170 B16). */
    private static RocketEntity claimedSource(MinecraftServer server, UUID logical) {
        var claim = RocketRuntime.installedBays().orElseThrow().claimForTests(logical).orElseThrow();
        var record = RocketTransferSavedData.get(server).find(claim.transfer()).orElseThrow();
        var level = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        if (level == null || !(level.getEntity(claim.source().orElseThrow()) instanceof RocketEntity rocket)) {
            throw new IllegalStateException("The claim's source is not loaded");
        }
        return rocket;
    }

    /** B16 motion: one block east and back, so the final image equals the proved one and only a motion observer sees it. */
    private static int nudge(CommandSourceStack source, UUID logical) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        RocketEntity rocket = claimedSource(server, logical);
        double x = rocket.getX(), y = rocket.getY(), z = rocket.getZ();
        rocket.setPos(x + 1.0D, y, z);
        rocket.setPos(x, y, z);
        LOGGER.info("ARCE_RELEASE_BAY_NUDGED logical={} rocket={} x={} y={} z={}", logical, rocket.getUUID(), x, y, z);
        source.sendSuccess(() -> Component.literal("Bay source nudged"), false);
        return 1;
    }

    /** B16 join: a neutral copy of L, from T's current source snapshot and flight data, joins the source Level. */
    private static int copy(CommandSourceStack source, UUID logical) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        var record = RocketTransferSavedData.get(server).findByLogicalRocket(logical).orElseThrow();
        ServerLevel level = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        RocketEntity copy = ModEntities.ROCKET.get().create(level);
        copy.initializeTransferred(record.sourceSnapshot(), logical, record.ownerId(), record.sourceFlightData());
        boolean added = level.addFreshEntity(copy);
        LOGGER.info("ARCE_RELEASE_BAY_COPY logical={} copy={} added={}", logical, copy.getUUID(), added);
        source.sendSuccess(() -> Component.literal("Bay copy added"), false);
        return added ? 1 : 0;
    }

    /** B16 leave: every other loaded rocket naming L in the source Level is discarded; the claim's source stays. */
    private static int discardCopies(CommandSourceStack source, UUID logical) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        RocketEntity exact = claimedSource(server, logical);
        var copies = ((ServerLevel) exact.level()).getEntities(ModEntities.ROCKET.get(),
                rocket -> rocket != exact && rocket.assemblyTransactionId().filter(logical::equals).isPresent());
        copies.forEach(RocketEntity::discard);
        LOGGER.info("ARCE_RELEASE_BAY_COPY_DISCARDED logical={} count={}", logical, copies.size());
        source.sendSuccess(() -> Component.literal("Bay copies discarded"), false);
        return copies.size();
    }

    /**
     * B16 leave and join: the source leaves its Level through {@code remove(UNLOADED_TO_CHUNK)}, whose bay hook reports at
     * once, and joins again at once from its saved tag. A real chunk unload calls {@code setRemoved} instead, which only
     * the per-tick observation sees (W170-R46; {@code BaySourceProofObserveTest} covers that path).
     */
    private static int reload(CommandSourceStack source, UUID logical) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        RocketEntity rocket = claimedSource(server, logical);
        ServerLevel level = (ServerLevel) rocket.level();
        CompoundTag saved = new CompoundTag();
        if (!rocket.save(saved)) { throw new IllegalStateException("The claim's source did not save"); }
        rocket.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var loaded = net.minecraft.world.entity.EntityType.loadEntityRecursive(saved, level, entity -> entity);
        boolean added = loaded != null && level.addFreshEntity(loaded);
        LOGGER.info("ARCE_RELEASE_BAY_RELOADED logical={} rocket={} added={}", logical, loaded == null ? "NONE" : loaded.getUUID(), added);
        source.sendSuccess(() -> Component.literal("Bay source reloaded"), false);
        return added ? 1 : 0;
    }

    private static int pending(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        var authority = BayReceiptTestHooks.authority(server);
        boolean work = BayReceiptTestHooks.store(server).map(store -> store.holdsWork()).orElse(false);
        LOGGER.info("ARCE_RELEASE_BAY_PENDING pending={} retained={} work={}", authority.pendingCount(), authority.retainedCount(), work);
        source.sendSuccess(() -> Component.literal("Bay pending reported"), false);
        return 1;
    }

    private static String signatures(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot snapshot) {
        return BayDeploymentGameTestSupport.bayPayloads(snapshot).stream().map(RocketNativeResources::signature)
                .collect(Collectors.joining("+"));
    }

}
