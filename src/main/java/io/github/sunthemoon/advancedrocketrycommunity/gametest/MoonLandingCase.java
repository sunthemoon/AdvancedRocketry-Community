package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.ASSEMBLY_WAIT_TICKS;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.assembleShapedFueledRocket;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.clearPadRockets;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.clearTransferJournal;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.findLogicalRocket;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.placeLegalRocket;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.primePadChunks;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

/**
 * One ADR-075 case of {@link MoonLandingRuleGameTests}: its turn on the Moon's fixed pads, the blocks it changed, and
 * what it must remove when it passes or fails.
 */
final class MoonLandingCase {
    static final BlockPos ORIGIN = new BlockPos(3, 2, 3);
    static final int FLIGHT_TICKS = 300;

    final GameTestHelper helper;
    final GameTestTickDriver driver;
    final MinecraftServer server;
    final ServerLevel earth;
    final ServerLevel moon;
    final MoonPadArea.Changes changes;
    final List<UUID> logicals = new ArrayList<>();
    final List<UUID> transfers = new ArrayList<>();
    final List<Entity> extras = new ArrayList<>();
    final List<ServerPlayer> players = new ArrayList<>();
    final List<Consumer<LivingAttackEvent>> listeners = new ArrayList<>();

    MoonLandingCase(GameTestHelper helper) {
        this.helper = helper;
        driver = new GameTestTickDriver(helper);
        earth = helper.getLevel();
        server = earth.getServer();
        moon = server.getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable");
        changes = new MoonPadArea.Changes(moon);
    }

    /** Waits for this case's turn on the pads, clears earlier flights from them, then runs {@code body}. */
    void start(Runnable body) {
        driver.after(1, () -> {
            if (!MoonPadArea.turn(helper, this::cleanup)) {
                start(body);
                return;
            }
            clearTransferJournal(earth);
            primePadChunks(earth);
            primePadChunks(moon);
            clearPadRockets(moon);
            body.run();
        });
    }

    void assemble(Consumer<RocketEntity> ready) {
        assemble(ORIGIN, ready);
    }

    void assemble(BlockPos origin, Consumer<RocketEntity> ready) {
        assemble(origin, at -> {
            placeLegalRocket(helper, at);
            helper.setBlock(at.west(), ModBlocks.ROCKET_FUEL_TANK.get());
        }, ready);
    }

    void assemble(BlockPos origin, Consumer<BlockPos> shape, Consumer<RocketEntity> ready) {
        assembleShapedFueledRocket(helper, driver, origin, UUID.randomUUID(), shape, () -> { },
                helper.getTick() + ASSEMBLY_WAIT_TICKS, rocket -> {
                    logicals.add(rocket.assemblyTransactionId().orElseThrow());
                    ready.accept(rocket);
                });
    }

    /** Requests an ordinary Moon flight that must be pending, and acknowledges its PREPARED record. */
    UUID request(RocketEntity rocket) {
        UUID transfer = UUID.randomUUID();
        transfers.add(transfer);
        var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, transfer);
        RocketFlightGameTestFixtures.assertPendingLaunch(helper, result, rocket);
        RocketJournalGameTestSupport.acknowledgeFixture(helper);
        return transfer;
    }

    /** Flies the rocket to the Moon and runs {@code landed} on the LANDED arrival. */
    void launch(RocketEntity rocket, Consumer<RocketEntity> landed) {
        var logical = rocket.assemblyTransactionId().orElseThrow();
        request(rocket);
        RocketFlightGameTestFixtures.awaitFlightState(helper, driver, moon, logical, RocketFlightState.LANDED,
                FLIGHT_TICKS, () -> {
            RocketEntity arrival = findLogicalRocket(moon, logical);
            helper.assertTrue(arrival != null && arrival.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                    "The rocket did not land on the Moon");
            landed.accept(arrival);
        });
    }

    /**
     * Replaces the PREPARED record with one whose destination's lowest block is at {@code lowestY}, built with
     * {@code RocketTransferRecord.create}, as the selector of an earlier rule would have recorded it.
     */
    RocketTransferRecord relocate(UUID transfer, int lowestY) {
        var journal = RocketTransferSavedData.get(server);
        var record = journal.find(transfer).orElseThrow();
        var destination = record.destinationSnapshot();
        var origin = new RocketPosition(destination.sourceOrigin().x(), lowestY - destination.bounds().minimum().y(),
                destination.sourceOrigin().z());
        var relocated = destination.relocated(UUID.randomUUID(), destination.sourceDimension(), origin, moon.getGameTime());
        var arrival = record.destinationFlightData();
        var moved = record.sourceFlightData().arriveAtDestination(arrival.fuel(), arrival.currentBody(),
                arrival.currentDimension(), origin, arrival.stateStartedGameTime());
        var replaced = RocketTransferRecord.create(record.transferId(), record.logicalRocketId(), record.ownerId(),
                record.sourceEntityId(), record.sourceSnapshot(), relocated, record.sourceFlightData(), moved,
                record.requiredFuel(), record.createdAtGameTime());
        // The journal never rebinds a transfer ID to another checksum, so the fixture replaces it in one tick.
        journal.remove(transfer);
        journal.put(replaced);
        RocketJournalGameTestSupport.acknowledgeFixture(helper);
        return replaced;
    }

    /** Removes the source, binds a destination that never spawned, and has recovery rebuild it at its origin. */
    void rebuild(RocketEntity source, UUID transfer, int lowestY, Runnable next) {
        var record = lowestY == MoonLandingRuleGameTests.GROUND + 1 ? RocketTransferSavedData.get(server).find(transfer).orElseThrow()
                : relocate(transfer, lowestY);
        helper.assertTrue(MoonPadArea.lowest(record.destinationSnapshot()) == lowestY,
                "The fixture destination is not at y " + lowestY);
        source.discard();
        var journal = RocketTransferSavedData.get(server);
        journal.put(record.destinationSpawned(UUID.randomUUID()));
        journal.flush(server);
        recover(record, lowestY, new RocketManager(), 1, new GameTestTickPacer(server), next);
    }

    /**
     * W98 (N3-A1): recovery answers RETRY_LATER until the discarded source's own entity slot is read back. One
     * retry per tick, bounded as in {@code RocketTransferRecoveryGameTests}.
     */
    private void recover(RocketTransferRecord record, int lowestY, RocketManager manager, int attempt,
                         GameTestTickPacer pacer, Runnable next) {
        UUID transfer = record.transferId();
        var report = RocketJournalGameTestSupport.recoverFixture(helper,
                () -> manager.recoverTransfer(server, transfer));
        if (report.status() == RocketTransferRecoveryReport.Status.RETRY_LATER && attempt < 40) {
            pacer.pace();
            driver.after(1, () -> recover(record, lowestY, manager, attempt + 1, pacer, next));
            return;
        }
        var journal = RocketTransferSavedData.get(server);
        helper.assertTrue(report.status() == RocketTransferRecoveryReport.Status.RECOVERED
                && report.action().filter(RocketTransferRecoveryAction.REBUILD_DESTINATION::equals).isPresent()
                && report.sourceMatches() == 0 && report.destinationMatches() == 0,
                "Recovery of the y " + lowestY + " destination was " + report.status() + " " + report.action());
        var logical = record.logicalRocketId();
        RocketEntity rebuilt = findLogicalRocket(moon, logical);
        var origin = record.destinationSnapshot().sourceOrigin();
        helper.assertTrue(rebuilt != null && rebuilt.snapshot().orElseThrow().sourceOrigin().equals(origin)
                && rebuilt.getX() == origin.x() + 0.5D && rebuilt.getZ() == origin.z() + 0.5D,
                "Recovery did not rebuild the y " + lowestY + " destination at its recorded origin " + origin);
        helper.assertTrue(count(logical) == 1, "Recovery left more or less than one logical rocket");
        discardLogical(logical);
        journal.remove(transfer);
        journal.flush(server);
        next.run();
    }

    void assertAtCandidate(RocketStructureSnapshot placed, int index) {
        var expected = MoonPadArea.origin(index, placed);
        helper.assertTrue(placed.sourceOrigin().x() == expected.x() && placed.sourceOrigin().z() == expected.z(),
                "The rocket is at " + placed.sourceOrigin() + ", not at candidate " + index + " (" + expected + ")");
    }

    /** Lowest layer at {@code expected}, which is the highest footprint column; supported, below the old floor. */
    void assertOnHighestColumn(RocketStructureSnapshot placed, int expected) {
        int lowest = MoonPadArea.lowest(placed);
        int highest = MoonPadArea.highestColumn(moon, placed);
        helper.assertTrue(lowest == expected && lowest == highest,
                "The rocket's lowest block is at y " + lowest + ", expected y " + expected
                        + " on its highest footprint column at y " + highest);
        helper.assertTrue(MoonPadArea.supported(moon, placed),
                "The rocket is not supported: no sturdy top below its lowest layer, or fluid below it");
    }

    int count(UUID logical) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof RocketEntity rocket && rocket.operational()
                        && rocket.assemblyTransactionId().filter(logical::equals).isPresent()) {
                    count++;
                }
            }
        }
        return count;
    }

    private void discardLogical(UUID logical) {
        var owned = new ArrayList<RocketEntity>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof RocketEntity rocket && rocket.assemblyTransactionId().filter(logical::equals).isPresent()) {
                    owned.add(rocket);
                }
            }
        }
        owned.forEach(rocket -> {
            rocket.ejectPassengers();
            rocket.discard();
        });
    }

    /** Runs once when the case passes or fails, before the next case takes the pads. */
    void cleanup() {
        listeners.forEach(MinecraftForge.EVENT_BUS::unregister);
        players.forEach(player -> {
            player.stopRiding();
            server.getPlayerList().remove(player);
        });
        logicals.forEach(this::discardLogical);
        extras.forEach(Entity::discard);
        var journal = RocketTransferSavedData.get(server);
        transfers.forEach(journal::remove);
        journal.flush(server);
        changes.restore();
    }
}
