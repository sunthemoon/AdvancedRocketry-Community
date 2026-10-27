package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayPackage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.SatelliteBayRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** W27d1 A1 fixtures: owned packages of real definitions, bay roots, bays and a PREPARED record in TRANSIT. */
final class BayDeploymentGameTestSupport {
    static final String BATCH = "bay_deployment";
    static final String TEMPLATE = "rocket_test";

    private BayDeploymentGameTestSupport() { }

    /** A DATA package of the shipped data satellite, whose definition allows Earth. */
    static ItemStack dataPackage(UUID owner) {
        ItemStack stack = new ItemStack(ModItems.DATA_SATELLITE_PACKAGE.get());
        SatelliteItemData.write(stack, new SatelliteIdentity(UUID.randomUUID(), owner, ModIdentity.id("data_satellite")));
        return stack;
    }

    /** A solar package of the shipped solar satellite; a fresh owner lacks its 120 lifetime research. */
    static ItemStack solarPackage(UUID owner) {
        ItemStack stack = new ItemStack(ModItems.SATELLITE_PACKAGE.get());
        SatelliteItemData.write(stack, new SatelliteIdentity(UUID.randomUUID(), owner, ModIdentity.id("solar_satellite"),
                SatelliteKind.SOLAR, List.of(ModIdentity.id("satellite_chassis"), ModIdentity.id("solar_transmitter_module"),
                        ModIdentity.id("satellite_solar_module"))));
        return stack;
    }

    /** W170: a survey package of the shipped survey satellite, a non-data kind with no research requirement. */
    static ItemStack surveyPackage(UUID owner) {
        ItemStack stack = new ItemStack(ModItems.SATELLITE_PACKAGE.get());
        SatelliteItemData.write(stack, new SatelliteIdentity(UUID.randomUUID(), owner, ModIdentity.id("survey_satellite"),
                SatelliteKind.SURVEY, List.of(ModIdentity.id("satellite_chassis"), ModIdentity.id("survey_scanner_module"),
                        ModIdentity.id("satellite_solar_module"), ModIdentity.id("data_storage_unit"), ModIdentity.id("satellite_battery"))));
        return stack;
    }

    static UUID satellite(ItemStack pkg) {
        return SatelliteBayPackage.decode(pkg.copyWithCount(1).serializeNBT()).identity().orElseThrow().satelliteId();
    }

    static CompoundTag root(UUID owner, ItemStack pkg) {
        return SatelliteBayRoot.encode(new SatelliteBayRoot.Contents(owner, pkg.copyWithCount(1).serializeNBT(), null));
    }

    /** A world bay holding {@code pkg}, loaded as an operator's NBT would load it. */
    static SatelliteBayBlockEntity placeBay(GameTestHelper helper, BlockPos relative, UUID owner, ItemStack pkg) {
        helper.setBlock(relative, ModBlocks.ROCKET_SATELLITE_BAY.get());
        var bay = (SatelliteBayBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        CompoundTag outer = new CompoundTag();
        outer.put(SatelliteBayRoot.KEY, root(owner, pkg));
        bay.load(outer);
        return bay;
    }

    static void clearBay(ServerLevel level, BlockPos absolute) {
        level.removeBlockEntity(absolute);
        level.setBlock(absolute, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /**
     * W170: registers the shipped solar package's satellite as an earlier launch would (the bay then replays it as
     * IDEMPOTENT); no GameTest can grant the 120 lifetime research a fresh solar launch needs.
     */
    static void registerSolar(net.minecraft.server.MinecraftServer server, ItemStack solar, UUID owner) {
        var identity = SatelliteBayPackage.decode(solar.copyWithCount(1).serializeNBT()).identity().orElseThrow();
        var code = io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData.get(server).launchIdle(
                time -> io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState.launchIdle(identity.satelliteId(),
                        identity.definitionId(), owner, time, io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds.EARTH_ID,
                        new io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint(identity.components(), false,
                                new io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats(4, 720, 0, 0, 10)),
                        new io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState.Solar(100, Optional.empty())),
                server.overworld().getGameTime()).code();
        if (code != io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode.SUCCESS) {
            throw new IllegalStateException("Solar registry fixture refused: " + code);
        }
    }

    /** W170: each bay root's consumed operation, in block order; null for a bay that still holds its package. */
    static List<UUID> consumedOps(RocketStructureSnapshot snapshot) {
        var ops = new ArrayList<UUID>();
        for (CompoundTag root : bayPayloads(snapshot)) {
            ops.add(SatelliteBayRoot.decode(root).contents().orElseThrow().consumed().orElse(null));
        }
        return ops;
    }

    /** The bay payloads of a snapshot, in block order. */
    static List<CompoundTag> bayPayloads(RocketStructureSnapshot snapshot) {
        var payloads = new ArrayList<CompoundTag>();
        for (RocketBlock block : snapshot.blocks()) {
            block.blockEntityPayload().filter(payload -> SatelliteBayRocketAdapter.ID.equals(payload.adapterId()))
                    .ifPresent(payload -> payloads.add(payload.data()));
        }
        return payloads;
    }

    /** An INTENT for {@code pkg}, the package of T's bay {@code index}, matching T, its owner, its hashes and its captured body. */
    static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt intent(net.minecraft.server.MinecraftServer server,
            RocketTransferRecord record, ItemStack pkg, int index) {
        CompoundTag tag = pkg.copyWithCount(1).serializeNBT();
        var identity = SatelliteBayPackage.decode(tag).identity().orElseThrow();
        long tick = server.getTickCount();
        return new io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt(UUID.randomUUID(),
                io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks.nextSequence(server),
                record.logicalRocketId(), new RocketPosition(0, index + 1, 0), identity.satelliteId(), identity.ownerId(),
                identity.definitionId().toString(), identity.kind(), record.sourceFlightData().plan().orElseThrow().sourceBody().toString(),
                RocketNativeResources.signature(tag), record.transferId(), record.sourceSnapshot().contentHash(), null,
                record.destinationSnapshot().contentHash(), null,
                io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt.Phase.INTENT, "NONE", false, false, 0, tick, tick, 0);
    }

    /** The receipt store has no queued, captured or unknown write. */
    static boolean storeIdle(net.minecraft.server.MinecraftServer server) {
        return !io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks.store(server)
                .map(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptStore::holdsWork).orElse(false);
    }

    /** The installed transfer service's bay dispatch: the claimed set that classification and final boundaries fill. */
    static RocketTransferBayDispatch dispatch() {
        return RocketSourceRetirementFixtures.recovery(RocketRuntime.installedTransfers()).bays();
    }

    static BayTransferRecovery bays(GameTestHelper helper) {
        var bays = RocketRuntime.installedBays();
        helper.assertTrue(bays.isPresent(), "The installed manager has no bay coordinator for this session");
        return bays.get();
    }

    static Optional<BayClaim> claim(GameTestHelper helper, UUID logical) { return bays(helper).claimForTests(logical); }

    /** A PREPARED record with bays, its source in TRANSIT at altitude over {@code origin}, Earth to Moon in this Level. */
    record Flight(RocketTransferRecord record, RocketEntity source) { }

    static Flight transit(GameTestHelper helper, BlockPos origin, BlockPos target, UUID owner, List<ItemStack> packages,
            RocketEscrowSectionPatch wrapper) {
        return transit(helper, origin, helper.getLevel().dimension().location(), position(target), owner, packages, wrapper,
                RocketFlightPlanner.EARTH.bodyId(), null);
    }

    /**
     * The same flight with the plan's captured source body, the destination in {@code targetDimension} (which need not be
     * this Level), and an optional passenger whose seat the manifest of T and of the source assigns.
     */
    static Flight transit(GameTestHelper helper, BlockPos origin, ResourceLocation targetDimension, RocketPosition target, UUID owner,
            List<ItemStack> packages, RocketEscrowSectionPatch wrapper, ResourceLocation sourceBody, @javax.annotation.Nullable UUID passenger) {
        var level = helper.getLevel();
        long now = level.getGameTime();
        var blocks = new ArrayList<RocketBlock>();
        blocks.add(new RocketBlock(new RocketPosition(0, 0, 0), state("minecraft:iron_block")));
        for (int i = 0; i < packages.size(); i++) {
            blocks.add(new RocketBlock(new RocketPosition(0, i + 1, 0), state("advancedrocketrycommunity:rocket_satellite_bay"),
                    new RocketBlockEntityPayload(SatelliteBayRocketAdapter.ID, root(owner, packages.get(i)))));
        }
        // Counts match the blocks: one seat anchor on the base block and one block entity per bay.
        var stats = new RocketStats(blocks.size(), 100, 1_000, 1_000, 1, 1, 1, packages.size());
        var propulsion = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), position(origin), blocks,
                List.of(new RocketPosition(0, 0, 0)), stats, now, propulsion);
        var destination = snapshot.relocated(UUID.randomUUID(), targetDimension, target, now);
        UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID();
        var fuel = RocketFuelState.empty(propulsion).fill(1_000).state();
        var plan = new RocketFlightPlan(3, transfer, sourceBody, RocketFlightPlanner.MOON.bodyId(),
                level.dimension().location(), targetDimension, 500, now);
        var idle = RocketFlightData.initial(logical, 1_000, 1, plan.sourceBody(), plan.sourceDimension(), position(origin), now);
        if (passenger != null) { idle = idle.withPassengers(idle.passengers().assign(passenger).orElseThrow()); }
        var flight = idle.withFuel(fuel, now).withPlan(plan).startCountdown(now).completeCountdown(now).beginTransit(transfer, now);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(),
                target, now);
        RocketEntity source = ModEntities.ROCKET.get().create(level);
        helper.assertTrue(source != null, "No source entity");
        var record = RocketTransferRecord.create(transfer, logical, owner, source.getUUID(), snapshot, destination, flight, arrival,
                500, now);
        source.initializeTransferred(snapshot, logical, owner, flight);
        RocketTransferEntities.positionAtAltitude(source, snapshot.sourceOrigin());
        source = wrapper.apply(level, source);
        helper.assertTrue(level.addFreshEntity(source), "Source was not published");
        return new Flight(record, source);
    }

    /** Neutral, or a source re-created from its saved tag with a bound escrow wrapper (a rocket filled through escrow). */
    interface RocketEscrowSectionPatch {
        RocketEntity apply(ServerLevel level, RocketEntity rocket);
        RocketEscrowSectionPatch NEUTRAL = (level, rocket) -> rocket;
        RocketEscrowSectionPatch BOUND = (level, rocket) -> {
            CompoundTag saved = new CompoundTag();
            rocket.saveWithoutId(saved);
            CompoundTag wrapper = saved.getCompound("RocketEntityData").getCompound("resource_escrow");
            wrapper.putUUID("ledger_id", UUID.randomUUID());
            wrapper.putLong("resource_epoch", 1);
            RocketEntity bound = ModEntities.ROCKET.get().create(level);
            bound.load(saved);
            return bound;
        };
    }

    static RocketBlockState state(String id) { return new RocketBlockState(ResourceLocation.tryParse(id), Map.of()); }
    static RocketPosition position(BlockPos pos) { return new RocketPosition(pos.getX(), pos.getY(), pos.getZ()); }
}
