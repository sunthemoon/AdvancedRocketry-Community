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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
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
    public static void internalMoveTickWithSneakingRiderKeepsTheSeat(GameTestHelper helper) {
        run(helper, f -> {
            try (var ignored = RocketRuntime.internalPassengerMove(f.player)) {
                helper.assertTrue(f.player.getVehicle() == f.rocket
                        && RocketRuntime.passengerExits().orElseThrow().state.internal(f.player),
                        "Internal-move control did not retain its rider and scope");
                f.sneakTick();
                f.assertSeatKept("Native tick during an internal move discarded the seat");
            }
        });
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 80)
    public static void landedSneakWithActiveTransferKeepsTheSeat(GameTestHelper helper) {
        run(helper, f -> {
            var flight = f.rocket.flightData().orElseThrow();
            // Domain construction forbids this shape. Exercise the input guard without weakening that invariant.
            var previous = replaceField(flight, "activeTransferId", UUID.randomUUID());
            try {
                helper.assertTrue(flight.state() == RocketFlightState.LANDED && flight.activeTransferId().isPresent(),
                        "Stationary active-transfer control was not established");
                f.sneakTick();
                f.assertSeatKept("A landed active transfer lost its seat");
            } finally { replaceField(flight, "activeTransferId", previous); }
        });
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 80)
    public static void rejoinRequiredAndGuardedSneakKeepsTheSeat(GameTestHelper helper) {
        run(helper, f -> {
            var server = helper.getLevel().getServer();
            var previous = RocketRejoinEvents.get(server);
            var data = RocketRejoinSavedData.load(RocketRejoinCodec.encode(rejoinRoot(f.playerId)));
            server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), data);
            try {
                helper.assertTrue(RocketRejoinProtection.required(server, f.playerId)
                        && !RocketRejoinProtection.guarded(f.player) && f.player.getVehicle() == f.rocket,
                        "Pending rejoin control was not established");
                f.sneakTick();
                f.assertSeatKept("A pending rejoin obligation lost its seat");
                helper.assertTrue(f.player.startRiding(f.rocket, true), "Pending rider did not remount");
                var cancel = new Object() {
                    @SubscribeEvent public void mount(EntityMountEvent event) {
                        if (event.isDismounting() && event.getEntityMounting() == f.player) { event.setCanceled(true); }
                    }
                };
                // Keep the rider through guard installation so the following native tick reaches the input guard.
                MinecraftForge.EVENT_BUS.register(cancel);
                try { data.guards(server).join(f.player); }
                finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
                helper.assertTrue(RocketRejoinProtection.guarded(f.player) && f.player.getVehicle() == f.rocket,
                        "Active rejoin control did not retain its rider");
                f.sneakTick();
                f.assertSeatKept("An actively guarded rejoin lost its seat");
            } finally {
                data.close();
                server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), previous);
            }
        });
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 80)
    public static void unreadableTransactionRootSneakKeepsTheSeatWithoutThrowing(GameTestHelper helper) {
        run(helper, f -> {
            var data = RocketTransactionSavedData.get(helper.getLevel().getServer());
            var previous = replaceField(data, "unreadable", true);
            try {
                helper.assertTrue(RocketRuntime.sealedFrozen(f.rocket) && f.player.getVehicle() == f.rocket,
                        "Unreadable transaction control was not frozen and ridden");
                f.sneakTick();
                helper.assertTrue(!f.player.isPassenger(), "Frozen ground sneak did not use vanilla dismount");
                f.assertSeatKept("Unreadable transaction root discarded the seat");
            } finally { replaceField(data, "unreadable", previous); }
            f.reload(); f.reconnect();
            helper.assertTrue(f.player.getVehicle() == f.rocket, "Protected seat was not retained across reload/reconnect");
        });
    }

    @GameTest(template = "rocket_test", batch = "rocket_seat_roster_refusal", timeoutTicks = 80)
    public static void updateRefusalDuringSneakKeepsTheSeatWithoutThrowing(GameTestHelper helper) {
        run(helper, f -> {
            var data = RocketTransactionSavedData.get(helper.getLevel().getServer());
            var previous = replaceField(data, "unreadable", false);
            var refuse = new Object() {
                @SubscribeEvent public void mount(EntityMountEvent event) {
                    if (event.isDismounting() && event.getEntityMounting() == f.player) {
                        replaceField(data, "unreadable", true);
                    }
                }
            };
            MinecraftForge.EVENT_BUS.register(refuse);
            try {
                helper.assertTrue(!RocketRuntime.sealedFrozen(f.rocket), "Refusal control was frozen before input");
                f.player.setShiftKeyDown(true);
                helper.getLevel().tickNonPassenger(f.rocket);
                helper.assertTrue(RocketRuntime.sealedFrozen(f.rocket) && !f.player.isPassenger(),
                        "Dismount callback did not exercise the update refusal");
                f.assertSeatKept("Refused update discarded the previous seat");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(refuse);
                replaceField(data, "unreadable", previous);
                f.player.setShiftKeyDown(false);
            }
            f.reload(); f.reconnect();
            helper.assertTrue(f.player.getVehicle() == f.rocket, "Refused update lost the saved reconnect seat");
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

    private static Object replaceField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            var previous = field.get(target); field.set(target, value); return previous;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static RocketRejoinRoot rejoinRoot(UUID player) {
        var release = UUID.randomUUID(); var version = new RocketRejoinRoot.Version("a".repeat(64), "PREPARED", null);
        var batch = new RocketRejoinRoot.Batch(release, 0, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                RocketRejoinRoot.Hold.H1, "minecraft:overworld", new RocketRejoinRoot.Position(0, 64, 0),
                new RocketRejoinRoot.Position(-1, 64, -1), new RocketRejoinRoot.Position(1, 66, 1),
                new RocketRejoinRoot.Resources("b".repeat(64), "c".repeat(64)), version, version, List.of(player),
                List.of(new RocketRejoinRoot.Slot("minecraft:overworld", 0, 0)));
        return RocketRejoinRoot.empty().admit(batch, List.of(new RocketRejoinRoot.Entry(release, player, 0,
                RocketRejoinRoot.Placement.PENDING, RocketRejoinRoot.Seat.PENDING, null)));
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

        void confirm() { RocketJournalGameTestSupport.confirmDetached(helper, journal, directory.resolve(
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.ROCKET_TRANSFERS.fileName())); }
        ServerPlayer join() { return FirstEventGameTestPlayers.join(helper.getLevel().getServer(), playerId,
                "W287Passenger", helper.getLevel(), helper.absolutePos(ARRIVAL)); }
        void sneakTick() {
            player.setShiftKeyDown(true);
            // Exercise the native vehicle/passenger ordering, not a synthetic mount event.
            helper.getLevel().tickNonPassenger(rocket);
            helper.assertTrue(!player.isShiftKeyDown(), "Native passenger tick did not consume sneak input");
        }
        void assertSeatKept(String reason) {
            helper.assertTrue(rocket.flightData().orElseThrow().passengers().assignment(playerId).isPresent(), reason);
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
