package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W287: native sneak input releases ground seats; lifecycle detaches retain durable membership. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketSeatRosterGameTests {
    private static final String BATCH = "rocket_seat_roster";
    private static final BlockPos ORIGIN = new BlockPos(3, 2, 3), ARRIVAL = new BlockPos(9, 2, 3);

    private RocketSeatRosterGameTests() { }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 80)
    public static void landedSneakSaveReloadAndReconnectDoesNotReseat(GameTestHelper helper) {
        run(helper, f -> {
            f.sneakTick();
            helper.assertTrue(!f.player.isPassenger(), "Native sneak did not detach the player");
            f.reload();
            f.reconnect();
            helper.assertTrue(f.player.getVehicle() == null, "Ground sneak reseated the player on reconnect");
            helper.assertTrue(f.rocket.flightData().orElseThrow().passengers().assignments().isEmpty(),
                    "Saved ground sneak retained the seat");
        });
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 80)
    public static void fluidLoaderIsNotOccupiedAfterLandedSneak(GameTestHelper helper) {
        run(helper, f -> {
            var device = ARRIVAL.south(3);
            helper.setBlock(device, ModBlocks.FLUID_FUEL_LOADER.get());
            var at = helper.absolutePos(device);
            try {
                var loader = (FluidFuelLoaderBlockEntity) helper.getLevel().getBlockEntity(at);
                var binding = new LoaderBinding(f.record.logicalRocketId(), helper.getLevel().dimension().location(),
                        f.rocket.getUUID(), helper.absolutePos(ARRIVAL), null);
                var root = new LoaderRoot(f.record.ownerId(), UUID.randomUUID(), binding,
                        LoaderTank.of(net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 1_000),
                        LoaderEscrowSection.neutral());
                var saved = new CompoundTag(); saved.put(LoaderRoot.KEY, root.encode()); loader.load(saved);
                helper.assertTrue(loader.attempt() == FluidFuelLoaderStatus.ROCKET_BUSY, "Occupied control was not busy");
                f.sneakTick();
                // The abstract-fuel fixture cannot accept fluid; importantly it is no longer occupied.
                helper.assertTrue(loader.attempt() == FluidFuelLoaderStatus.INCOMPATIBLE_FLUID,
                        "An empty ground rocket was still refused as occupied: " + loader.attempt());
            } finally {
                helper.getLevel().removeBlockEntity(at); helper.setBlock(device, Blocks.AIR);
            }
        });
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 80)
    public static void landedDisconnectWithSneakInputStillReseatsOnLogin(GameTestHelper helper) {
        run(helper, f -> {
            // Input not yet consumed by a native entity tick must not turn logout into LEAVE.
            f.player.setShiftKeyDown(true);
            f.disconnect(); f.reload(); f.login();
            helper.assertTrue(f.player.getVehicle() == f.rocket, "Landed disconnect lost the retained seat");
        });
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 80)
    public static void inFlightDisconnectStillReseatsOnLogin(GameTestHelper helper) {
        run(helper, f -> {
            f.inFlight(); f.disconnect(); f.reload(); f.login();
            helper.assertTrue(f.player.getVehicle() == f.rocket, "In-flight disconnect lost the retained seat");
        });
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 80)
    public static void inFlightSneakRetainsTheReconnectSeat(GameTestHelper helper) {
        run(helper, f -> {
            f.inFlight(); f.sneakTick(); f.reconnect();
            helper.assertTrue(f.player.getVehicle() == f.rocket, "In-flight sneak discarded recovery membership");
        });
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 80)
    public static void leaveSeatStillPersistsRemovalAndDoesNotReseat(GameTestHelper helper) {
        run(helper, f -> {
            var service = new RocketFlightService(null, null);
            f.player.containerMenu = new RocketFlightMenu(1, f.player.getInventory(), f.rocket);
            var result = service.request(f.player, f.rocket.getId(), RocketFlightAction.LEAVE,
                    f.rocket.flightData().orElseThrow().currentTarget().orElseThrow(), UUID.randomUUID());
            helper.assertTrue(result.success() && !f.player.isPassenger(), "LEAVE SEAT no longer succeeds");
            f.reload(); f.reconnect();
            helper.assertTrue(f.player.getVehicle() == null, "LEAVE SEAT reseated on login");
        });
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 80)
    public static void serverDismountWithSneakFlagDoesNotDiscardMembership(GameTestHelper helper) {
        run(helper, f -> {
            f.player.setShiftKeyDown(true);
            try (var ignored = RocketRuntime.internalPassengerMove(f.player)) { f.player.stopRiding(); }
            helper.assertTrue(f.rocket.flightData().orElseThrow().passengers().assignment(f.playerId).isPresent(),
                    "Trusted server detach discarded the seat");
            f.player.setShiftKeyDown(false); f.reconnect();
            helper.assertTrue(f.player.getVehicle() == f.rocket, "Trusted server detach prevented reconnect");
        });
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 80)
    public static void oldUnriddenLandedAssignmentRemainsRecoverable(GameTestHelper helper) {
        run(helper, f -> {
            f.player.stopRiding(); f.reload(); f.reconnect();
            helper.assertTrue(f.player.getVehicle() == f.rocket,
                    "Load guessed that an unridden retained assignment was stale");
            f.sneakTick();
            helper.assertTrue(f.rocket.flightData().orElseThrow().passengers().assignment(f.playerId).isEmpty(),
                    "Boarding and sneaking out did not clear the legacy assignment");
        });
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 80)
    public static void canceledSneakDismountKeepsTheSeat(GameTestHelper helper) {
        run(helper, f -> {
            var cancel = new Object() {
                @SubscribeEvent public void mount(EntityMountEvent event) {
                    if (event.isDismounting() && event.getEntityMounting() == f.player) { event.setCanceled(true); }
                }
            };
            MinecraftForge.EVENT_BUS.register(cancel);
            try {
                f.sneakTick();
                helper.assertTrue(f.player.getVehicle() == f.rocket
                        && f.rocket.flightData().orElseThrow().passengers().assignment(f.playerId).isPresent(),
                        "A canceled dismount removed membership");
            } finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
        });
    }

    private static void run(GameTestHelper helper, Consumer<Fixture> body) {
        helper.startSequence().thenWaitUntil(() -> {
            for (var relative : List.of(ORIGIN, ARRIVAL, ARRIVAL.south(3))) {
                helper.assertTrue(helper.getLevel().areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(
                        helper.absolutePos(relative)).toLong()), "Seat fixture entity sections are not loaded");
            }
        }).thenExecute(() -> {
            try (var fixture = new Fixture(helper)) { body.accept(fixture); }
            catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }).thenSucceed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final UUID playerId = UUID.randomUUID();
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final HashSet<UUID> live = new HashSet<>(), settled = new HashSet<>();
        final RocketTransferRecoveryService recovery = new RocketTransferRecoveryService(live, settled);
        final RocketSourceRetirementFixtures.Flight flight;
        final Path directory;
        RocketTransferRecord record;
        RocketEntity rocket;
        ServerPlayer player;

        Fixture(GameTestHelper helper) throws IOException {
            this.helper = helper;
            directory = Files.createTempDirectory("arce-w287-");
            flight = RocketSourceRetirementFixtures.countdown(helper, helper.absolutePos(ORIGIN),
                    helper.absolutePos(ARRIVAL), List.of(playerId), 0);
            record = flight.record();
            rocket = ModEntities.ROCKET.get().create(helper.getLevel());
            rocket.initializeTransferred(record.destinationSnapshot(), record.logicalRocketId(), record.ownerId(),
                    record.destinationFlightData().land(helper.getLevel().getGameTime()));
            RocketTransferEntities.positionAtOrigin(rocket, record.destinationSnapshot().sourceOrigin());
            helper.assertTrue(helper.getLevel().addFreshEntity(rocket), "Landed rocket was not admitted");
            record = record.destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                    .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
            journal.put(record); settled.add(record.transferId()); confirm();
            player = join();
            helper.assertTrue(player.startRiding(rocket, true), "Player did not mount the ground rocket");
        }

        void confirm() { RocketJournalGameTestSupport.confirmDetached(helper, journal, directory.resolve("transfers.dat")); }
        ServerPlayer join() { return FirstEventGameTestPlayers.join(helper.getLevel().getServer(), playerId,
                "W287Passenger", helper.getLevel(), helper.absolutePos(ARRIVAL)); }
        void sneakTick() {
            player.setShiftKeyDown(true);
            rocket.tick(); // The native Level ticks the vehicle before Player.rideTick consumes sneak input.
            player.rideTick();
            player.setShiftKeyDown(false);
        }
        void disconnect() {
            helper.getLevel().getServer().getPlayerList().remove(player); recovery.onPlayerLoggedOut(playerId); player = null;
        }
        void login() { player = join(); recovery.onPlayerLoggedIn(player, journal); }
        void reconnect() { disconnect(); login(); }
        void reload() {
            var saved = rocket.saveWithoutId(new CompoundTag());
            if (player != null) { try (var ignored = RocketRuntime.internalPassengerMove(player)) { player.stopRiding(); } }
            rocket.discard();
            rocket = ModEntities.ROCKET.get().create(helper.getLevel()); rocket.load(saved);
            helper.assertTrue(helper.getLevel().addFreshEntity(rocket), "Reloaded rocket was not admitted");
            helper.assertTrue(rocket.operational(), "Reloaded seat data was blocked");
        }
        void inFlight() {
            player.stopRiding(); rocket.discard();
            record = flight.record(); journal.remove(record.transferId()); journal.put(record); confirm();
            settled.clear(); live.add(record.transferId());
            rocket = flight.spawn(helper, helper.getLevel(), flight.countdown().completeCountdown(helper.getLevel().getGameTime()), false);
            helper.assertTrue(player.startRiding(rocket, true), "In-flight passenger did not mount");
        }
        @Override public void close() {
            journal.abort(); recovery.clear();
            if (player != null) { player.setShiftKeyDown(false); player.stopRiding(); helper.getLevel().getServer().getPlayerList().remove(player); }
            if (rocket != null) { rocket.discard(); }
            RocketTransferEntities.releaseFlightTickets(helper.getLevel().getServer(), record);
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
            } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
    }
}
