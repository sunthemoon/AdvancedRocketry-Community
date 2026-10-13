package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.*;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketPropulsionInteraction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.Map;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native entity/player/journal round trips with fresh recovery owners, not process-restart evidence. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketResourceRecoveryGameTests {
    private static final TicketType<ChunkPos> FIXTURE_TICKET = TicketType.create("arce_resource_recovery_test",
            Comparator.comparingLong(ChunkPos::toLong), 200);
    private RocketResourceRecoveryGameTests() { }

    @GameTest(template = "rocket_test", batch = "resource_arrival_recovery", timeoutTicks = 400)
    public static void drainedArrivalSurvivesReloadAndDisassemblyRetiresJournal(GameTestHelper helper) {
        var fixture = new Fixture(helper, false);
        fixture.whenReady(() -> {
            fixture.land();
            fixture.assertBalances(2_000, 0, 0);
            for (int i = 1; i <= 2; i++) {
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                helper.assertTrue(RocketPropulsionInteraction.exchange(fixture.player, fixture.rocket,
                        InteractionHand.MAIN_HAND) == InteractionResult.CONSUME, "Arrival drain refused");
                fixture.player.getInventory().add(fixture.player.getItemInHand(InteractionHand.MAIN_HAND));
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                fixture.assertBalances(2_000 - i * 1_000, 0, i * 1_000);
            }
            helper.assertTrue(fixture.rocket.flightData().orElseThrow().state() == RocketFlightState.ASSEMBLED,
                    "Fully drained arrival did not exercise ASSEMBLED state");
            fixture.reload();
            fixture.recover(RocketTransferRecoveryReport.Status.RECOVERED);
            fixture.assertBalances(0, 0, 2_000);
            fixture.manager.requestDisassembly(fixture.player, fixture.rocket);
            helper.assertTrue(fixture.rocket.isAlive(), "Unacknowledged retirement disassembled an arrival");
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport.acknowledgeFixture(helper);
            fixture.manager.requestDisassembly(fixture.player, fixture.rocket);
            helper.assertTrue(fixture.rocket.isRemoved(), "Empty arrival disassembly refused");
            helper.assertTrue(fixture.journal().find(fixture.record.transferId()).isEmpty(),
                    "Disassembly retained reconstructable arrival resources");
            assertSnapshotBlocks(helper, new io.github.sunthemoon.advancedrocketrycommunity.rocket.forge
                    .ServerLevelRocketTransactionWorld(fixture.moon,
                            io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters.defaults(),
                            fixture.owner), fixture.record.destinationSnapshot());
            fixture.reloadJournalAndPlayer();
            helper.assertTrue(fixture.manager.recoverTransfer(fixture.earth.getServer(), fixture.record.transferId())
                    .status() == RocketTransferRecoveryReport.Status.NOT_FOUND, "Retired arrival recovered again");
            fixture.assertBalances(0, 0, 2_000, 0);
            clearSnapshotBlocks(fixture.moon, fixture.record.destinationSnapshot());
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", batch = "resource_source_recovery", timeoutTicks = 400)
    public static void offlinePassengerRecoveryLocksBothBanksAcrossRepeatedReloads(GameTestHelper helper) {
        var fixture = new Fixture(helper, true);
        fixture.whenReady(() -> {
            fixture.source();
            fixture.player.getInventory().setItem(0, new ItemStack(ModItems.HYDROGEN_CANISTER.get()));
            fixture.player.getInventory().setItem(1, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
            fixture.player.getInventory().setItem(2, new ItemStack(ModItems.EMPTY_CANISTER.get()));
            for (int cycle = 0; cycle < 3; cycle++) {
                fixture.recover(RocketTransferRecoveryReport.Status.WAITING_FOR_PASSENGERS);
                fixture.assertBalances(2_000, 1_000, 2_000);
                helper.assertTrue(!fixture.manager.resourceMutationAllowed(fixture.rocket), "Passenger wait unlocked resources");
                for (int slot = 0; slot < 3; slot++) {
                    fixture.player.getInventory().selected = slot;
                    var fuel = fixture.rocket.flightData().orElseThrow().fuel();
                    var inventory = fixture.player.getInventory().save(new net.minecraft.nbt.ListTag());
                    fixture.player.refusal = null;
                    helper.assertTrue(RocketPropulsionInteraction.exchange(fixture.player, fixture.rocket,
                            InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "Pending fill/unload accepted");
                    helper.assertTrue(fuel.equals(fixture.rocket.flightData().orElseThrow().fuel())
                            && inventory.equals(fixture.player.getInventory().save(new net.minecraft.nbt.ListTag())),
                            "Pending exchange changed a bank or player container");
                    helper.assertTrue(fixture.player.refusal != null && fixture.player.refusal.getContents()
                            instanceof net.minecraft.network.chat.contents.TranslatableContents message
                            && message.getKey().endsWith("propulsion.recovery_pending"), "Missing clear recovery refusal");
                    fixture.assertBalances(2_000, 1_000, 2_000);
                }
                fixture.manager.requestDisassembly(fixture.player, fixture.rocket);
                helper.assertTrue(!fixture.rocket.isRemoved(), "Pending source disassembly accepted");
                fixture.reload();
                fixture.assertBalances(2_000, 1_000, 2_000);
            }
            // Login itself must perform the reconnect. A post-login fixture teleport would
            // unmount the passenger after recovery has already retired the source record.
            var passenger = new ResourcePlayer(fixture.earth, fixture.offline);
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            fixture.earth.getServer().getPlayerList().placeNewPlayer(connection, passenger);
            try {
                fixture.manager.onInstalled();
                fixture.manager.onPlayerLoggedIn(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(passenger));
                helper.assertTrue(passenger.getVehicle() == fixture.rocket, "Offline passenger did not reconnect to source");
                helper.assertTrue(fixture.journal().find(fixture.record.transferId()).isEmpty(),
                        "Passenger reconnect did not retire source recovery authority");
                helper.assertTrue(fixture.manager.resourceMutationAllowed(fixture.rocket), "Reconnected source stayed locked");
                fixture.assertBalances(2_000, 1_000, 2_000);
                fixture.player.getInventory().selected = 2;
                helper.assertTrue(RocketPropulsionInteraction.exchange(fixture.player, fixture.rocket,
                        InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                        "Reconciled source did not resume lossless container exchange");
                fixture.assertBalances(1_000, 1_000, 3_000);
            } finally {
                passenger.stopRiding();
                fixture.earth.getServer().getPlayerList().remove(passenger);
                fixture.rocket.discard();
                clearTransferJournal(fixture.earth);
            }
            helper.succeed();
        });
    }

    static final class Fixture {
        final GameTestHelper helper;
        final ServerLevel earth;
        final ServerLevel moon;
        final UUID owner = UUID.randomUUID();
        final UUID offline = UUID.randomUUID();
        final RocketTransferRecord record;
        RocketManager manager = new RocketManager();
        RocketEntity rocket;
        ResourcePlayer player;

        Fixture(GameTestHelper helper, boolean bipropellant) {
            this.helper = helper;
            earth = helper.getLevel();
            moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon Level unavailable");
            clearTransferJournal(earth);
            BlockPos origin = new BlockPos(3, 2, 3);
            placeLegalRocket(helper, origin);
            helper.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get());
            helper.setBlock(origin.east(), ModBlocks.ROCKET_FUEL_TANK.get());
            helper.setBlock(origin.north(), ModBlocks.ROCKET_FUEL_TANK.get());
            if (bipropellant) {
                helper.setBlock(origin, ModBlocks.BIPROPELLANT_ROCKET_MOTOR.get());
                for (var at : java.util.List.of(origin.west(), origin.east(), origin.north())) {
                    helper.setBlock(at, ModBlocks.BIPROPELLANT_FUEL_TANK.get());
                }
                helper.setBlock(origin.south(), ModBlocks.OXIDIZER_FUEL_TANK.get());
                helper.setBlock(origin.south(2), ModBlocks.OXIDIZER_FUEL_TANK.get());
                helper.setBlock(origin.above(3), ModBlocks.ROCKET_SEAT.get());
            }
            var snapshot = successfulSnapshot(helper, origin);
            clearSnapshotBlocks(earth, snapshot);
            var sourceOrigin = snapshot.sourceOrigin();
            var destinationOrigin = new RocketPosition(sourceOrigin.x(),
                    Math.max(moon.getMinBuildHeight() + 16, sourceOrigin.y()), sourceOrigin.z());
            var destination = snapshot.relocated(UUID.randomUUID(), moon.dimension().location(),
                    destinationOrigin, earth.getGameTime());
            clearSnapshotBlocks(moon, destination);
            var fuel = RocketFuelState.empty(snapshot.propulsion().orElseThrow()).withFluidBank(false,
                    ModIdentity.id(bipropellant ? "hydrogen" : "rocket_fuel"), null, bipropellant ? 2_000 : 3_000);
            if (bipropellant) { fuel = fuel.withFluidBank(true, ModIdentity.id("oxygen"), null, 1_000); }
            UUID logical = UUID.randomUUID();
            UUID transfer = UUID.randomUUID();
            long now = earth.getGameTime();
            var base = new RocketFlightPlan(3, transfer, CelestialIds.EARTH_ID, CelestialIds.MOON_ID,
                    earth.dimension().location(), moon.dimension().location(), 417, now);
            var catalog = bipropellant ? RocketFluidFuelCatalog.defaults() : new RocketFluidFuelCatalog(Map.of(
                    new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.MONO, ModIdentity.id("rocket_fuel")),
                    new RocketFluidFuelCatalog.Efficiency(417, 1_000)));
            var plan = RocketPropulsionQuoter.quote(fuel, base, catalog).plan();
            var flight = RocketFlightData.initial(logical, 3_000, snapshot.stats().seatCount(), CelestialIds.EARTH_ID,
                    earth.dimension().location(), snapshot.sourceOrigin(), now).withFuel(fuel, now);
            if (bipropellant) {
                flight = flight.withPassengers(flight.passengers().assign(offline).orElseThrow());
            }
            var transit = flight.withPlan(plan).startCountdown(now).completeCountdown(now).beginTransit(transfer, now);
            var arrival = transit.arriveAtDestination(fuel.debit(plan).state(), CelestialIds.MOON_ID,
                    moon.dimension().location(), destination.sourceOrigin(), now);
            record = RocketTransferRecord.create(transfer, logical, owner, UUID.randomUUID(), snapshot,
                    destination, transit, arrival, plan.requiredFuel(), now);
            moon.getChunkAt(new BlockPos(destination.sourceOrigin().x(), destination.sourceOrigin().y(),
                    destination.sourceOrigin().z()));
            player = new ResourcePlayer(earth, owner);
        }

        void whenReady(Runnable action) {
            var pacer = new GameTestTickPacer(earth.getServer());
            helper.startSequence().thenWaitUntil(() -> {
                keepFixtureRegionLoaded(earth, record.sourceSnapshot());
                keepFixtureRegionLoaded(moon, record.destinationSnapshot());
                var origin = record.sourceSnapshot().sourceOrigin();
                long chunk = ChunkPos.asLong(origin.x() >> 4, origin.z() >> 4);
                boolean ready = earth.areEntitiesLoaded(chunk) && moon.areEntitiesLoaded(chunk);
                if (!ready) { pacer.pace(); }
                helper.assertTrue(ready, "Resource recovery fixture entity chunks not ready");
            }).thenExecute(action);
        }

        private static void keepFixtureRegionLoaded(ServerLevel level,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot snapshot) {
            var chunks = new java.util.HashSet<ChunkPos>();
            for (var block : snapshot.blocks()) {
                var absolute = snapshot.sourceOrigin().add(block.position());
                var at = new BlockPos(absolute.x(), absolute.y(), absolute.z());
                var chunk = new ChunkPos(at);
                if (chunks.add(chunk)) {
                    level.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, chunk);
                    level.getChunkAt(at);
                }
            }
        }

        void land() {
            rocket = ModEntities.ROCKET.get().create(moon);
            rocket.initializeTransferred(record.destinationSnapshot(), record.logicalRocketId(), owner,
                    record.destinationFlightData().land(moon.getGameTime()));
            helper.assertTrue(moon.addFreshEntity(rocket), "Arrival entity admission failed");
            journal().put(record.destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                    .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED));
            player = new ResourcePlayer(moon, owner);
            player.setPos(rocket.position());
            recover(RocketTransferRecoveryReport.Status.RECOVERED);
        }

        RocketTransferSavedData journal() { return RocketTransferSavedData.get(earth.getServer()); }

        void source() {
            rocket = ModEntities.ROCKET.get().create(earth);
            rocket.setUUID(record.sourceEntityId());
            rocket.initializeTransferred(record.sourceSnapshot(), record.logicalRocketId(), owner, record.sourceFlightData());
            helper.assertTrue(earth.addFreshEntity(rocket), "Source entity admission failed");
            journal().put(record);
            player.setPos(rocket.position());
        }

        void recover(RocketTransferRecoveryReport.Status expected) {
            var installed = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport
                    .recoverFixture(helper, () -> RocketRuntime.recoverTransfer(earth.getServer(), record.transferId()));
            helper.assertTrue(installed.status() == expected, "Installed recovery status differs: " + installed);
            var report = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport
                    .recoverFixture(helper, () -> manager.recoverTransfer(earth.getServer(), record.transferId()));
            helper.assertTrue(report.status() == expected, "Recovery status differs: " + report);
        }

        void reloadJournalAndPlayer() {
            earth.getServer().saveEverything(false, true, true);
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport.acknowledgeFixture(helper);
            var restored = RocketTransferSavedData.load(journal().save(new CompoundTag()));
            journal().stop();
            helper.assertTrue(restored.operational(), "Journal round trip blocked");
            earth.getServer().overworld().getDataStorage().set(RocketTransferSavedData.DATA_NAME, restored);
            var playerData = player.saveWithoutId(new CompoundTag());
            player = new ResourcePlayer(player.serverLevel(), owner);
            player.load(playerData);
            manager = new RocketManager();
        }

        void reload() {
            var level = (ServerLevel) rocket.level();
            var data = rocket.saveWithoutId(new CompoundTag());
            rocket.discard();
            rocket = ModEntities.ROCKET.get().create(level);
            rocket.load(data);
            helper.assertTrue(level.addFreshEntity(rocket), "Reloaded rocket admission failed");
            reloadJournalAndPlayer();
            player.setPos(rocket.position());
        }

        void assertBalances(long primary, long oxidizer, long containers) { assertBalances(primary, oxidizer, containers, 1); }

        void assertBalances(long primary, long oxidizer, long containers, int expectedEntities) {
            int count = 0;
            long actualPrimary = 0;
            long actualOxidizer = 0;
            for (var level : java.util.List.of(earth, moon)) {
                for (var entity : level.getAllEntities()) {
                    if (entity instanceof RocketEntity candidate && !candidate.isRemoved()
                            && candidate.assemblyTransactionId().filter(record.logicalRocketId()::equals).isPresent()) {
                        count++;
                        var fuel = candidate.flightData().orElseThrow().fuel();
                        actualPrimary += fuel.amount();
                        actualOxidizer += fuel.propulsion().orElseThrow().oxidizer().amount();
                    }
                }
            }
            long actualContainers = 0;
            for (var stack : player.getInventory().items) {
                var handler = stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM)
                        .resolve().orElse(null);
                if (handler != null) { actualContainers += (long) handler.getFluidInTank(0).getAmount() * stack.getCount(); }
            }
            helper.assertTrue(count == expectedEntities && actualPrimary == primary && actualOxidizer == oxidizer
                    && actualContainers == containers, "Resource authority differs: entities=" + count + " primary="
                            + actualPrimary + " oxidizer=" + actualOxidizer + " containers=" + actualContainers);
        }
    }

    static final class ResourcePlayer extends ServerPlayer {
        Component refusal;
        ResourcePlayer(ServerLevel level, UUID id) { super(level.getServer(), level, new GameProfile(id, "ARCEResource")); }
        @Override public void displayClientMessage(Component message, boolean actionBar) { refusal = message; }
        @Override public void sendSystemMessage(Component message) { }
    }
}
