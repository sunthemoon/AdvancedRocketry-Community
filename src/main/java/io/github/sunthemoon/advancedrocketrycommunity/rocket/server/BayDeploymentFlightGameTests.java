package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W27d1 A1 on the real installed services: a hand-fuelled rocket with a data bay and a solar bay launches from Earth.
 * At ascent_complete its claim holds T in TRANSIT; the data bay's INTENT is durable, decommission of its satellite is
 * fenced, operator recovery answers RETRY_LATER, then D2 writes REJECTED (DEPLOYMENT_UNAVAILABLE). The solar package's
 * owner lacks its research, so D0 refuses it at runtime with validation's own code. After the last REJECTED acknowledgement the
 * claim hands READY_SOURCE and T continues to the Moon with both packages byte-equal; nothing is registered.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayDeploymentFlightGameTests {
    private BayDeploymentFlightGameTests() { }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 1_000)
    public static void aDataAndSolarBayRocketWaitsInTransitIsRefusedAndLandsWithBothPackages(GameTestHelper helper) {
        new Flight(helper).start();
    }

    private static final class Flight {
        final GameTestHelper helper;
        final UUID owner = UUID.randomUUID(), transfer = UUID.randomUUID(), logical = UUID.randomUUID();
        final ItemStack data = dataPackage(owner), solar = solarPackage(owner);
        final BlockPos origin = new BlockPos(2, 2, 2);
        final List<BlockPos> blocks = List.of(origin, origin.above(), origin.above(2), origin.west(), origin.east(), origin.east().above());
        RocketEntity source;
        BayClaim claim;
        List<String> before;
        String solarRefusal;
        int stage, waits;
        boolean sawIntent, probedRecovery, closed;

        Flight(GameTestHelper helper) { this.helper = helper; }
        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 960, "Bay flight exceeded its bounded wait at stage " + stage);
                switch (stage) {
                    case 0 -> launch();
                    case 1 -> awaitClaim();
                    case 2 -> hold();
                    default -> land();
                }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void launch() {
            var level = helper.getLevel();
            helper.setBlock(origin, ModBlocks.ROCKET_MOTOR.get());
            helper.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get());
            helper.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
            helper.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get());
            placeBay(helper, origin.east(), owner, data);
            placeBay(helper, origin.east().above(), owner, solar);
            var absolute = helper.absolutePos(origin);
            var scan = new RocketStructureScanTask(new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()),
                    level.dimension().location(), new RocketPosition(absolute.getX(), absolute.getY(), absolute.getZ()),
                    UUID.randomUUID(), level.getGameTime());
            var snapshot = scan.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK).snapshot().orElseThrow();
            helper.assertTrue(bayPayloads(snapshot).size() == 2, "The rocket snapshot did not capture both bays");
            before = BayDeploymentGameTests.signatures(snapshot);
            source = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(source != null, "Rocket type unavailable");
            source.initialize(snapshot, logical, owner);
            helper.assertTrue(level.addFreshEntity(source), "Rocket spawn failed");
            var flight = source.flightData().orElseThrow();
            source.updateFlightData(flight.withFuel(flight.fuel().fill(flight.fuel().capacity()).state(), level.getGameTime()));
            // The solar package's owner has no lifetime research: validation refuses it, and D0 must keep that code.
            var identity = io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayPackage.decode(
                    solar.copyWithCount(1).serializeNBT()).identity().orElseThrow();
            var validation = io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime.launchService().orElseThrow()
                    .validate(level.getServer(), owner, identity, io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds.EARTH_ID);
            helper.assertTrue(validation.request().isEmpty(), "The solar fixture must be refused by validation");
            solarRefusal = validation.result().code().name();
            var launched = RocketRuntime.requestAdminFlight(source, RocketDestination.MOON, transfer);
            RocketJournalGameTestSupport.assertPendingLaunch(helper, launched, source);
            stage = 1;
        }

        void awaitClaim() {
            var found = claim(helper, logical);
            if (found.isEmpty()) {
                helper.assertTrue(source.flightData().orElseThrow().state() != RocketFlightState.TRANSIT,
                        "T reached TRANSIT without a bay claim");
                return;
            }
            claim = found.get();
            helper.assertTrue(claim.transfer().equals(transfer) && claim.members().size() == 2, "Wrong claim " + claim.members().size());
            stage = 2;
        }

        void hold() {
            var server = helper.getLevel().getServer();
            var session = bays(helper).sessionForTests();
            if (claim(helper, logical).isPresent()) {
                var origin = transferRecord().sourceSnapshot().sourceOrigin();
                helper.assertTrue(source.flightData().orElseThrow().state() == RocketFlightState.TRANSIT
                        && source.getY() == origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS, "The held source left TRANSIT");
                helper.assertTrue(transferRecord().phase() == RocketTransferPhase.PREPARED, "T advanced while held");
                helper.assertTrue(!RocketEscrowGate.idle(source) && RocketEscrowGate.escrowIdle(source), "The bay part did not hold L");
                if (!probedRecovery) {
                    probedRecovery = true;
                    helper.assertTrue(RocketRuntime.recoverTransfer(server, transfer).status() == RocketTransferRecoveryReport.Status.RETRY_LATER,
                            "Operator recovery of a held T must answer RETRY_LATER");
                }
                // The INTENT's acknowledgement and D2 run in one server tick after this test's tick, so the test sees INTENT
                // queued or captured; the member state machine reaches REJECTED only from an acknowledged INTENT.
                var intent = session.find(satellite(data)).orElse(null);
                if (intent != null && intent.phase() == BayReceipt.Phase.INTENT) {
                    sawIntent = true;
                    helper.assertTrue(SatelliteMissionSavedData.get(server).decommission(satellite(data), owner, true, true).code()
                            == SatelliteOperationCode.DEPLOYMENT_PENDING, "Decommission was not fenced from INTENT");
                    helper.assertTrue(bay(origin().east()).phase() == SatelliteBayStatus.Phase.INTENT, "The menu does not show INTENT");
                }
                return;
            }
            helper.assertTrue(claim.stage() == BayClaim.Stage.ENDED && claim.handoff().orElseThrow() == BayClaim.Handoff.READY_SOURCE,
                    "The claim did not end with READY_SOURCE");
            var dataMember = claim.members().stream().filter(member -> member.satellite().orElseThrow().equals(satellite(data))).findFirst().orElseThrow();
            var solarMember = claim.members().stream().filter(member -> member.satellite().orElseThrow().equals(satellite(solar))).findFirst().orElseThrow();
            helper.assertTrue(dataMember.state() == BayMember.State.REJECTED && dataMember.reason().equals("DEPLOYMENT_UNAVAILABLE"),
                    "The data bay was not REJECTED with DEPLOYMENT_UNAVAILABLE: " + dataMember.state() + " " + dataMember.reason());
            helper.assertTrue(sawIntent, "The data bay's INTENT was never observed before its outcome");
            helper.assertTrue(solarMember.state() == BayMember.State.REFUSED && solarMember.reason().equals(solarRefusal),
                    "The solar bay was not refused at D0 with its validation code: " + solarMember.state() + " " + solarMember.reason());
            var rejected = session.find(satellite(data)).orElseThrow();
            helper.assertTrue(rejected.phase() == BayReceipt.Phase.REJECTED && session.acknowledged(rejected.op())
                    && rejected.transfer().equals(transfer) && rejected.rocket().equals(logical), "The REJECTED receipt is not exact");
            helper.assertTrue(session.find(satellite(solar)).isEmpty(), "A runtime refusal wrote a receipt");
            helper.assertTrue(bay(origin().east()).phase() == SatelliteBayStatus.Phase.REJECTED
                    && bay(origin().east()).reason() == SatelliteBayStatus.Reason.DEPLOYMENT_UNAVAILABLE, "Data bay menu status");
            helper.assertTrue(bay(origin().east().above()).phase() == SatelliteBayStatus.Phase.NONE
                    && bay(origin().east().above()).reason() == SatelliteBayStatus.Reason.fromName(solarRefusal), "Solar bay menu status");
            stage = 3;
        }

        void land() {
            var server = helper.getLevel().getServer();
            var record = RocketTransferSavedData.get(server).find(transfer).orElse(null);
            if (record == null || record.phase() != RocketTransferPhase.COMMITTED) { return; }
            var moon = server.getLevel(CelestialIds.MOON_LEVEL);
            var destination = moon == null ? null : (RocketEntity) moon.getEntity(record.destinationEntityId().orElseThrow());
            if (destination == null || destination.flightData().orElseThrow().state() != RocketFlightState.LANDED) { return; }
            helper.assertTrue(before.equals(BayDeploymentGameTests.signatures(destination.snapshot().orElseThrow())),
                    "The landed rocket's packages are not byte-equal to the launch");
            var registry = SatelliteMissionSavedData.get(server);
            helper.assertTrue(registry.satellite(satellite(data)).isEmpty() && registry.satellite(satellite(solar)).isEmpty(),
                    "W27d1 registered a satellite");
            close();
            helper.succeed();
        }

        BlockPos origin() { return origin; }
        SatelliteBayBlockEntity bay(BlockPos relative) {
            return (SatelliteBayBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        }
        io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord transferRecord() {
            return RocketTransferSavedData.get(helper.getLevel().getServer()).find(transfer).orElseThrow();
        }

        void close() {
            if (closed) { return; }
            closed = true;
            var server = helper.getLevel().getServer();
            var journal = RocketTransferSavedData.get(server);
            journal.find(transfer).ifPresent(record -> {
                var moon = server.getLevel(CelestialIds.MOON_LEVEL);
                record.destinationEntityId().map(id -> moon == null ? null : moon.getEntity(id)).ifPresent(net.minecraft.world.entity.Entity::discard);
                RocketTransferEntities.releaseFlightTickets(server, record);
                journal.remove(transfer);
            });
            if (source != null) { source.discard(); }
            var session = bays(helper).sessionForTests();
            session.find(satellite(data)).ifPresent(receipt -> BayReceiptTestHooks.remove(server, receipt.op(), outcome -> { }));
            session.rejections().remove(satellite(solar));
            for (BlockPos relative : blocks) { clearBay(helper.getLevel(), helper.absolutePos(relative)); }
        }
    }
}
