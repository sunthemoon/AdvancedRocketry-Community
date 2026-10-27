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

    /** The bay payloads of a snapshot, in block order. */
    static List<CompoundTag> bayPayloads(RocketStructureSnapshot snapshot) {
        var payloads = new ArrayList<CompoundTag>();
        for (RocketBlock block : snapshot.blocks()) {
            block.blockEntityPayload().filter(payload -> SatelliteBayRocketAdapter.ID.equals(payload.adapterId()))
                    .ifPresent(payload -> payloads.add(payload.data()));
        }
        return payloads;
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
        var level = helper.getLevel();
        long now = level.getGameTime();
        var blocks = new ArrayList<RocketBlock>();
        blocks.add(new RocketBlock(new RocketPosition(0, 0, 0), state("minecraft:iron_block")));
        for (int i = 0; i < packages.size(); i++) {
            blocks.add(new RocketBlock(new RocketPosition(0, i + 1, 0), state("advancedrocketrycommunity:rocket_satellite_bay"),
                    new RocketBlockEntityPayload(SatelliteBayRocketAdapter.ID, root(owner, packages.get(i)))));
        }
        var stats = new RocketStats(blocks.size(), 100, 1_000, 1_000, 1, 1, 1, 0);
        var propulsion = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), position(origin), blocks,
                List.of(new RocketPosition(0, 1, 0)), stats, now, propulsion);
        var destination = snapshot.relocated(UUID.randomUUID(), level.dimension().location(), position(target), now);
        UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID();
        var fuel = RocketFuelState.empty(propulsion).fill(1_000).state();
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                level.dimension().location(), level.dimension().location(), 500, now);
        var flight = RocketFlightData.initial(logical, 1_000, 1, plan.sourceBody(), plan.sourceDimension(), position(origin), now)
                .withFuel(fuel, now).withPlan(plan).startCountdown(now).completeCountdown(now).beginTransit(transfer, now);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(),
                position(target), now);
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
