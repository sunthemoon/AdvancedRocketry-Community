package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.*;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Gameplay regressions use decoded schema-1 entities, never native assembly fuel. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketLegacyFuelGameTests {
    private RocketLegacyFuelGameTests() { }

    @GameTest(template = "rocket_test", batch = "legacy_scalar_board", timeoutTicks = 40)
    public static void decodedLegacyRocketBoardsAndLeaves(GameTestHelper helper) {
        var rocket = decodedRocket(helper);
        var player = owner(helper, rocket);
        var manager = new RocketManager();
        try {
            intent(manager, player, rocket, RocketFlightAction.BOARD);
            helper.assertTrue(player.getVehicle() == rocket && rocket.flightData().orElseThrow().passengers()
                    .assignment(player.getUUID()).isPresent(), "Legacy boarding failed");
            intent(manager, player, rocket, RocketFlightAction.LEAVE);
            helper.assertTrue(player.getVehicle() == null && rocket.flightData().orElseThrow().passengers()
                    .assignment(player.getUUID()).isEmpty(), "Legacy leaving failed");
        } finally { player.stopRiding(); rocket.discard(); }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "legacy_scalar_loader", timeoutTicks = 40)
    public static void decodedLegacyRocketRefuelsFromAnInputLoader(GameTestHelper helper) {
        var rocket = decodedRocket(helper);
        try { refuel(helper, rocket); }
        finally { rocket.discard(); }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "legacy_scalar_flight", timeoutTicks = 500)
    public static void decodedLegacyRocketLaunchesAndCompletesFlight(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        var level = helper.getLevel();
        var moon = level.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon missing");
        clearTransferJournal(level); primePadChunks(moon);
        driver.after(40, () -> {
            var rocket = decodedRocket(helper);
            refuel(helper, rocket);
            var fuel = rocket.flightData().orElseThrow().fuel();
            rocket.updateFlightData(rocket.flightData().orElseThrow().withFuel(
                    fuel.fill(fuel.remainingCapacity()).state(), level.getGameTime()));
            UUID logical = rocket.assemblyTransactionId().orElseThrow();
            long before = rocket.flightData().orElseThrow().fuel().amount();
            var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, UUID.randomUUID());
            assertPendingLaunch(helper, result, rocket);
            awaitFlightState(helper, driver, moon, logical, RocketFlightState.LANDED, 350, () -> {
                var landed = findLogicalRocket(moon, logical);
                helper.assertTrue(landed != null && landed.flightData().orElseThrow().fuel().propulsion().isEmpty(),
                        "Scalar flight changed representation or lost its rocket");
                var remaining = landed.flightData().orElseThrow().fuel();
                helper.assertTrue(remaining.amount() == before - result.requiredFuel()
                        && remaining.committedDebits().size() == 1, "Scalar flight debit differs");
                helper.assertTrue(level.getEntity(rocket.getUUID()) == null, "Scalar source survived transfer");
                landed.discard(); clearTransferJournal(level); helper.succeed();
            });
        });
    }

    @GameTest(template = "rocket_test", batch = "legacy_scalar_recovery", timeoutTicks = 100)
    public static void decodedLegacyRocketRecoversKeepSource(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        var level = helper.getLevel();
        clearTransferJournal(level);
        primePadChunks(level.getServer().getLevel(CelestialIds.MOON_LEVEL));
        driver.after(40, () -> {
            var rocket = decodedRocket(helper);
            var fuel = rocket.flightData().orElseThrow().fuel();
            rocket.updateFlightData(rocket.flightData().orElseThrow().withFuel(fuel.fill(fuel.capacity()).state(), level.getGameTime()));
            long before = rocket.flightData().orElseThrow().fuel().amount();
            UUID transfer = UUID.randomUUID();
            assertPendingLaunch(helper, RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, transfer), rocket);
            level.getServer().saveEverything(false, true, true);
            var recoveryManager = new RocketManager();
            var report = RocketJournalGameTestSupport.recoverFixture(helper,
                    () -> recoveryManager.recoverTransfer(level.getServer(), transfer));
            helper.assertTrue(report.action().orElse(null) == RocketTransferRecoveryAction.KEEP_SOURCE,
                    "Legacy source recovery did not KEEP_SOURCE: " + report);
            helper.assertTrue(rocket.isAlive() && rocket.flightData().orElseThrow().state() == RocketFlightState.FUELED
                    && rocket.flightData().orElseThrow().fuel().amount() == before
                    && rocket.flightData().orElseThrow().fuel().committedDebits().isEmpty(), "KEEP_SOURCE changed scalar resources");
            helper.assertTrue(RocketTransferSavedData.get(level.getServer()).find(transfer).isEmpty(), "Recovery retained completed journal entry");
            rocket.discard(); helper.succeed();
        });
    }

    static RocketEntity decodedRocket(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(pos.getX(), pos.getY(), pos.getZ()),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(
                        ResourceLocation.tryParse("minecraft:iron_block"), Map.of())),
                        new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(
                                ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                List.of(new RocketPosition(0, 1, 0)), new RocketStats(2, 100, 10_000, 10_000, 1, 1, 1, 0), level.getGameTime());
        var payload = new CompoundTag(); payload.putInt("schema_version", 1);
        payload.putUUID("assembly_transaction_id", UUID.randomUUID()); payload.putUUID("owner_id", UUID.randomUUID());
        payload.put("snapshot", RocketSnapshotNbtCodec.encode(snapshot));
        var parent = new CompoundTag(); parent.put("RocketEntityData", payload);
        var rocket = ModEntities.ROCKET.get().create(level);
        helper.assertTrue(rocket != null, "Legacy entity factory missing"); rocket.load(parent);
        helper.assertTrue(rocket.operational() && rocket.flightData().orElseThrow().fuel().propulsion().isEmpty(), "Schema-1 scalar entity did not decode");
        helper.assertTrue(level.addFreshEntity(rocket), "Legacy entity not added");
        return rocket;
    }

    static FakePlayer owner(GameTestHelper helper, RocketEntity rocket) {
        var player = new FakePlayer((net.minecraft.server.level.ServerLevel) rocket.level(),
                new GameProfile(rocket.ownerId().orElseThrow(), "LegacyFuel"));
        player.setPos(rocket.position()); return player;
    }

    static void intent(RocketManager manager, FakePlayer player, RocketEntity rocket, RocketFlightAction action) {
        manager.requestFlightIntent(player, rocket.getId(), action, new TravelTarget.BodySurface(CelestialIds.EARTH_ID), UUID.randomUUID());
    }

    static void refuel(GameTestHelper helper, RocketEntity rocket) {
        var pos = rocket.blockPosition().offset(4, 0, 0);
        var level = (net.minecraft.server.level.ServerLevel) rocket.level();
        level.setBlockAndUpdate(pos, io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.FUEL_LOADER.get().defaultBlockState());
        var loader = (io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderBlockEntity) level.getBlockEntity(pos);
        loader.assignOwner(rocket.ownerId().orElseThrow());
        helper.assertTrue(loader.itemHandler().insertItem(0, new ItemStack(ModItems.ROCKET_FUEL_CELL.get()), false).isEmpty(), "Loader rejected input");
        helper.assertTrue(loader.saveWithoutMetadata().getCompound(
                io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderStorage.DATA_KEY).getInt("slot_role")
                == io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderData.Role.INPUT.ordinal(),
                "Fixture is not queued INPUT fuel");
        long before = rocket.flightData().orElseThrow().fuel().amount();
        FuelLoaderGameTests.tick(loader);
        helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() > before
                && rocket.flightData().orElseThrow().fuel().propulsion().isEmpty(), "Loader failed scalar fill");
        level.removeBlock(pos, false);
    }

    static void afterLanding(GameTestHelper helper, java.util.function.Consumer<RocketEntity> check) {
        var driver = new GameTestTickDriver(helper); var level = helper.getLevel();
        var moon = level.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon missing"); clearTransferJournal(level); primePadChunks(moon);
        driver.after(40, () -> {
            var rocket = decodedRocket(helper); var fuel = rocket.flightData().orElseThrow().fuel();
            rocket.updateFlightData(rocket.flightData().orElseThrow().withFuel(fuel.fill(fuel.capacity()).state(), level.getGameTime()));
            UUID logical = rocket.assemblyTransactionId().orElseThrow();
            assertPendingLaunch(helper, RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, UUID.randomUUID()), rocket);
            awaitFlightState(helper, driver, moon, logical, RocketFlightState.LANDED, 350, () -> {
                var landed = findLogicalRocket(moon, logical);
                helper.assertTrue(landed != null, "No landed scalar fixture");
                try { check.accept(landed); }
                finally { landed.discard(); clearTransferJournal(level); }
                helper.succeed();
            });
        });
    }
}
