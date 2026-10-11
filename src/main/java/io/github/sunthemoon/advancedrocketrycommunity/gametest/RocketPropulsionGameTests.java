package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.*;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketPropulsionInteraction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real extraction, native content, launch and journal debit; loading waits run at server cadence. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketPropulsionGameTests {
    private RocketPropulsionGameTests() { }
    @GameTest(template = "rocket_test", batch = "propulsion_mono", timeoutTicks = 800)
    public static void advancedMonoLaunchDebitsActualRocketFuel(GameTestHelper helper) {
        flight(helper, ModBlocks.ADVANCED_ROCKET_MOTOR.get(), RocketPropulsion.Mode.MONO, 3, 5);
    }
    @GameTest(template = "rocket_test", batch = "propulsion_bipropellant", timeoutTicks = 800)
    public static void bipropellantLaunchDebitsHydrogenAndOxygen(GameTestHelper helper) {
        flight(helper, ModBlocks.BIPROPELLANT_ROCKET_MOTOR.get(), RocketPropulsion.Mode.BIPROPELLANT, 1, 1);
    }
    @GameTest(template = "rocket_test", batch = "propulsion_advanced_bipropellant", timeoutTicks = 800)
    public static void advancedBipropellantLaunchDebitsBothBanks(GameTestHelper helper) {
        flight(helper, ModBlocks.ADVANCED_BIPROPELLANT_ROCKET_MOTOR.get(), RocketPropulsion.Mode.BIPROPELLANT, 3, 5);
    }
    @GameTest(template = "rocket_test", batch = "propulsion_nuclear", timeoutTicks = 800)
    public static void nuclearLaunchDebitsWorkingFluid(GameTestHelper helper) {
        flight(helper, ModBlocks.NUCLEAR_ROCKET_MOTOR.get(), RocketPropulsion.Mode.NUCLEAR, 2, 7);
    }
    @GameTest(template = "rocket_test", batch = "propulsion_basic", timeoutTicks = 800)
    public static void basicRocketKeepsItsMetricsAndAbstractFuelLaunch(GameTestHelper helper) {
        flight(helper, ModBlocks.ROCKET_MOTOR.get(), RocketPropulsion.Mode.MONO, 1, 1);
    }
    @GameTest(template = "rocket_test")
    public static void mixedModesAndWrongTankRolesRefuseBeforeExtraction(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3); placeLegalRocket(helper, origin);
        helper.setBlock(origin.east(), ModBlocks.BIPROPELLANT_ROCKET_MOTOR.get());
        var mixed = scan(helper, origin);
        helper.assertTrue(mixed.status() == RocketScanResult.Status.FAILED
                && mixed.issues().get(0).code() == RocketValidationCode.MIXED_PROPULSION_MODES, "Mixed modes were accepted");
        helper.assertBlockPresent(ModBlocks.ROCKET_MOTOR.get(), origin);
        helper.setBlock(origin.east(), ModBlocks.OXIDIZER_FUEL_TANK.get());
        var role = scan(helper, origin);
        helper.assertTrue(role.status() == RocketScanResult.Status.FAILED
                && role.issues().get(0).code() == RocketValidationCode.WRONG_PROPULSION_TANK_ROLE, "Wrong tank role was accepted");
        helper.assertBlockPresent(ModBlocks.ROCKET_MOTOR.get(), origin); helper.succeed();
    }
    @GameTest(template = "rocket_test")
    public static void explicitIntegratedDefinitionForNewHostIdentityStaysAbstract(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3); placeLegalRocket(helper, origin);
        helper.setBlock(origin, ModBlocks.ADVANCED_BIPROPELLANT_ROCKET_MOTOR.get());
        var registries = net.minecraftforge.registries.ForgeRegistries.BLOCKS;
        try (var registry = new io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.component.RocketComponentRegistry(
                id -> registries.containsKey(id) ? registries.getValue(id) : null)) {
            registry.forOwner("propulsion_test").register(net.minecraft.resources.ResourceLocation.tryParse("propulsion_test:integrated"),
                    java.util.Set.of(ModIdentity.id("advanced_bipropellant_rocket_motor")),
                    new io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketComponentDefinition(123, 4_321, 987, true, false, false));
            var task = new io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask(
                    new ServerLevelRocketScanWorld(helper.getLevel(), RocketBlockEntityAdapters.defaults(), registry.freeze()),
                    helper.getLevel().dimension().location(), new RocketPosition(helper.absolutePos(origin).getX(),
                            helper.absolutePos(origin).getY(), helper.absolutePos(origin).getZ()), UUID.randomUUID(),
                    helper.getLevel().getGameTime());
            var result = task.step(io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
            helper.assertTrue(result.status() == RocketScanResult.Status.SUCCESS, "Explicit new-ID definition refused " + result.issues());
            var snapshot = result.snapshot().orElseThrow();
            helper.assertTrue(snapshot.propulsion().orElseThrow().mode() == RocketPropulsion.Mode.ABSTRACT
                    && snapshot.stats().thrust() == 4_321 && snapshot.stats().fuelCapacity() == 1_987,
                    "Integrated definition was split or retuned");
            helper.assertTrue(snapshot.stats().seatCount() == 1 && snapshot.stats().guidanceCount() == 1,
                    "Integrated role semantics were lost");
            helper.succeed();
        }
    }
    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void wholeContainersRoundTripAndOxidizerOnlyRequiresFreshConsent(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3);
        placeLegalRocket(helper, origin);
        helper.setBlock(origin, ModBlocks.BIPROPELLANT_ROCKET_MOTOR.get());
        helper.setBlock(origin.west(), ModBlocks.BIPROPELLANT_FUEL_TANK.get());
        helper.setBlock(origin.east(), ModBlocks.OXIDIZER_FUEL_TANK.get());
        EntitySections.whenLoaded(helper, () -> {
            UUID owner = UUID.randomUUID();
            var snapshot = successfulSnapshot(helper, origin);
            var assembly = new RocketAssemblyTransaction(new ServerLevelRocketTransactionWorld(helper.getLevel(),
                    RocketBlockEntityAdapters.defaults(), owner), new RocketRegionLockManager(),
                    new RocketOperationLedger(), RocketTransactionJournal.NO_OP).execute(UUID.randomUUID(), snapshot);
            helper.assertTrue(assembly.success(), "Container fixture assembly refused");
            var rocket = (RocketEntity) helper.getLevel().getEntity(assembly.rocketEntityId().orElseThrow());
            var player = new ContainerPlayer(helper.getLevel(), owner);
            player.setPos(rocket.position());
            var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
            player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(ModItems.HYDROGEN_CANISTER.get()));
            helper.assertTrue(RocketPropulsionInteraction.exchange(player, rocket, hand)
                    == net.minecraft.world.InteractionResult.CONSUME, "Hydrogen fill refused");
            helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 1_000
                    && player.getItemInHand(hand).is(ModItems.EMPTY_CANISTER.get()), "Primary container debit differs");
            helper.assertTrue(RocketPropulsionInteraction.exchange(player, rocket, hand)
                    == net.minecraft.world.InteractionResult.CONSUME, "Hydrogen unload refused");
            helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 0
                    && player.getItemInHand(hand).is(ModItems.HYDROGEN_CANISTER.get()), "Hydrogen round-trip lost content");
            player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(ModItems.OXYGEN_CANISTER.get()));
            helper.assertTrue(RocketPropulsionInteraction.exchange(player, rocket, hand)
                    == net.minecraft.world.InteractionResult.CONSUME, "Oxidizer fill refused");
            var manager = new RocketManager(RocketBlockEntityAdapters.defaults());
            manager.requestDisassembly(player, rocket);
            helper.assertTrue(player.token != null && rocket.isAlive(), "Oxidizer-only bypassed consent");
            UUID stale = player.token;
            helper.assertTrue(RocketPropulsionInteraction.exchange(player, rocket, hand)
                    == net.minecraft.world.InteractionResult.CONSUME, "Oxidizer unload refused");
            helper.assertTrue(!manager.confirmDisassembly(player, stale), "Unload did not invalidate quote");
            helper.assertTrue(RocketPropulsionInteraction.exchange(player, rocket, hand)
                    == net.minecraft.world.InteractionResult.CONSUME, "Oxidizer refill refused");
            manager.requestDisassembly(player, rocket);
            helper.assertTrue(manager.confirmDisassembly(player, player.token) && rocket.isRemoved(), "Fresh consent did not restore hardware");
            helper.assertBlockPresent(ModBlocks.BIPROPELLANT_ROCKET_MOTOR.get(), origin);
            helper.assertTrue(!manager.confirmDisassembly(player, player.token), "Consent replay restored twice");
            helper.succeed();
        }, new GameTestTickPacer(helper.getLevel().getServer()), origin);
    }
    private static final class ContainerPlayer extends net.minecraft.server.level.ServerPlayer {
        UUID token;
        ContainerPlayer(ServerLevel level, UUID owner) {
            super(level.getServer(), level, new com.mojang.authlib.GameProfile(owner, "ARCEPropulsion"));
        }
        @Override public void displayClientMessage(net.minecraft.network.chat.Component message, boolean actionBar) { }
        @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) {
            var click = message.getStyle().getClickEvent();
            if (click != null && click.getAction() == net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND) {
                String command = click.getValue(); token = UUID.fromString(command.substring(command.lastIndexOf(' ') + 1));
            }
        }
    }
    private static void flight(GameTestHelper helper, Block motor, RocketPropulsion.Mode mode, long numerator, long denominator) {
        ServerLevel earth = helper.getLevel(); ServerLevel moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable");
        clearTransferJournal(earth); primePadChunks(moon);
        awaitMoon(helper, moon, new GameTestTickPacer(earth.getServer()), 0, () -> {
            BlockPos origin = new BlockPos(3, 2, 3); placeLegalRocket(helper, origin); helper.setBlock(origin, motor);
            if (mode == RocketPropulsion.Mode.NUCLEAR) {
                helper.setBlock(origin.above(), ModBlocks.NUCLEAR_CORE.get());
                helper.setBlock(origin.above(2), ModBlocks.ROCKET_SEAT.get());
                helper.setBlock(origin.above(3), ModBlocks.GUIDANCE_COMPUTER.get());
            }
            helper.setBlock(origin.west(), switch (mode) {
                case BIPROPELLANT -> ModBlocks.BIPROPELLANT_FUEL_TANK.get();
                case NUCLEAR -> ModBlocks.NUCLEAR_FUEL_TANK.get(); default -> ModBlocks.ROCKET_FUEL_TANK.get();
            });
            if (mode == RocketPropulsion.Mode.BIPROPELLANT) { helper.setBlock(origin.east(), ModBlocks.OXIDIZER_FUEL_TANK.get()); }
            var snapshot = successfulSnapshot(helper, origin);
            helper.assertTrue(snapshot.propulsion().orElseThrow().mode() == mode, "Wrong captured mode");
            boolean basic = motor == ModBlocks.ROCKET_MOTOR.get();
            if (basic) {
                helper.assertTrue(snapshot.stats().mass() == 200 && snapshot.stats().thrust() == 1_000
                        && snapshot.stats().fuelCapacity() == 1_000, "Basic metrics changed");
            }
            UUID owner = UUID.randomUUID();
            var assembled = new RocketAssemblyTransaction(new ServerLevelRocketTransactionWorld(earth, RocketBlockEntityAdapters.defaults(), owner),
                    new RocketRegionLockManager(), new RocketOperationLedger(), RocketTransactionJournal.NO_OP)
                    .execute(UUID.randomUUID(), snapshot);
            helper.assertTrue(assembled.success(), "Assembly refused " + assembled.code());
            RocketEntity rocket = (RocketEntity) earth.getEntity(assembled.rocketEntityId().orElseThrow());
            var before = rocket.flightData().orElseThrow();
            var fuel = basic ? before.fuel().fill(1_000).state() : before.fuel().withFluidBank(false,
                    ModIdentity.id(mode == RocketPropulsion.Mode.MONO ? "rocket_fuel" : "hydrogen"), null, 1_000);
            if (mode == RocketPropulsion.Mode.BIPROPELLANT) { fuel = fuel.withFluidBank(true, ModIdentity.id("oxygen"), null, 1_000); }
            rocket.updateFlightData(before.withFuel(fuel, earth.getGameTime()));
            UUID request = UUID.randomUUID(); UUID logical = rocket.assemblyTransactionId().orElseThrow();
            var launch = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, request);
            helper.assertTrue(launch.success(), "Launch refused " + launch.code());
            var plan = rocket.flightData().orElseThrow().plan().orElseThrow();
            long q = plan.requiredFuel(); long efficiency = mode == RocketPropulsion.Mode.MONO && !basic ? 2 : 1;
            long expected = basic ? q : (q * numerator + denominator * efficiency - 1) / (denominator * efficiency);
            helper.assertTrue(plan.propulsionDebit().primary() == expected
                    && plan.propulsionDebit().oxidizer() == (mode == RocketPropulsion.Mode.BIPROPELLANT ? expected : 0), "Incorrect captured vector");
            awaitLanding(helper, earth, moon, logical, request, mode, expected,
                    new GameTestTickPacer(earth.getServer()), 0);
        });
    }
    private static void awaitMoon(GameTestHelper helper, ServerLevel moon, GameTestTickPacer pacer, int attempts, Runnable ready) {
        ChunkPos pad = new ChunkPos(io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel.FIXED_FEET_POSITION);
        if (moon.areEntitiesLoaded(pad.toLong()) && moon.isPositionEntityTicking(pad.getMiddleBlockPosition(0))) { ready.run(); return; }
        helper.assertTrue(attempts < 400, "Moon pad did not become entity-ready"); pacer.pace();
        helper.runAfterDelay(1, () -> awaitMoon(helper, moon, pacer, attempts + 1, ready));
    }
    private static void awaitLanding(GameTestHelper helper, ServerLevel earth, ServerLevel moon, UUID logical, UUID request,
            RocketPropulsion.Mode mode, long expected, GameTestTickPacer pacer, int attempts) {
        RocketEntity arrived = findLogicalRocket(moon, logical);
        if (arrived != null && arrived.flightData().orElseThrow().state() == RocketFlightState.LANDED) {
            var flight = arrived.flightData().orElseThrow(); var fuel = flight.fuel();
            helper.assertTrue(fuel.amount() == 1_000 - expected && fuel.committedDebits().equals(java.util.List.of(request)), "Primary debit not exactly once");
            helper.assertTrue(fuel.propulsion().orElseThrow().oxidizer().amount()
                    == (mode == RocketPropulsion.Mode.BIPROPELLANT ? 1_000 - expected : 0), "Oxidizer debit differs");
            helper.assertTrue(RocketFlightNbtCodec.decode(RocketFlightNbtCodec.encode(flight)).data().orElseThrow().equals(flight), "Native flight codec lost resources");
            helper.assertTrue(findLogicalRocket(earth, logical) == null, "Source retained duplicate authority");
            helper.assertTrue(RocketTransferSavedData.get(earth.getServer()).find(request).orElseThrow().phase()
                    == RocketTransferPhase.COMMITTED, "Transfer not terminal");
            arrived.discard(); helper.succeed(); return;
        }
        helper.assertTrue(attempts < 350, "Propulsion flight did not land"); pacer.pace();
        helper.runAfterDelay(1, () -> awaitLanding(helper, earth, moon, logical, request, mode, expected, pacer, attempts + 1));
    }
}
