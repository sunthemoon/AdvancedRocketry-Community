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
 * W27d1 and W170 A1 on the real installed services: a hand-fuelled rocket with a data bay and a solar bay launches from
 * Earth. At ascent_complete its claim holds T in TRANSIT; the data bay's INTENT is durable, decommission of its satellite
 * is fenced and operator recovery answers RETRY_LATER. D2 registers the data satellite fresh, with its initial mission, and
 * replays the solar satellite, which an earlier launch registered (a GameTest cannot grant the 120 lifetime research a
 * fresh solar launch needs). Both are REGISTERED in one generation, consumed (S' and T'), proved and retired; the claim
 * hands READY_SOURCE and T' continues to the Moon (section 8.1 test 7 with consumption). Each satellite is registered
 * once and each bay consumed once by its own operation.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayDeploymentFlightGameTests {
    private BayDeploymentFlightGameTests() { }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 1_000)
    public static void aDataAndSolarBayRocketRegistersBothOnceAndLandsWithBothBaysConsumed(GameTestHelper helper) {
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
        int stage, waits, ownerSatellites;
        boolean sawIntent, sawRegistered, probedRecovery, closed;

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
            registerSolar(level.getServer(), solar, owner); // the bay replays it as IDEMPOTENT
            ownerSatellites = SatelliteMissionSavedData.get(level.getServer()).ownerSatellites(owner);
            helper.assertTrue(ownerSatellites == 1, "The solar fixture was not registered once");
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
                if (intent != null && intent.phase() == BayReceipt.Phase.REGISTERED && session.acknowledged(intent.op())) {
                    sawRegistered = true;
                    var solarReceipt = session.find(satellite(solar)).orElseThrow();
                    helper.assertTrue(solarReceipt.phase() == BayReceipt.Phase.REGISTERED && solarReceipt.preexisting()
                            && !intent.preexisting() && solarReceipt.srcAfter().equals(intent.srcAfter())
                            && solarReceipt.dstAfter().equals(intent.dstAfter()), "The group's REGISTERED receipts disagree");
                }
                return;
            }
            helper.assertTrue(claim.stage() == BayClaim.Stage.ENDED && claim.handoff().orElseThrow() == BayClaim.Handoff.READY_SOURCE,
                    "The claim did not end with READY_SOURCE: " + claim.stage() + " " + claim.reason());
            helper.assertTrue(sawIntent && sawRegistered, "INTENT and the acknowledged REGISTERED group were not both observed");
            var ops = claim.members().stream().map(member -> member.receipt().orElseThrow().op()).toList();
            helper.assertTrue(claim.members().stream().allMatch(member -> member.state() == BayMember.State.RETIRED),
                    "Both members must retire");
            helper.assertTrue(session.find(satellite(data)).isEmpty() && session.find(satellite(solar)).isEmpty(),
                    "The receipts were not retired");
            var registry = SatelliteMissionSavedData.get(server);
            var missionId = io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager.initialMissionId(satellite(data));
            helper.assertTrue(registry.satellite(satellite(data)).isPresent() && registry.mission(missionId)
                    .filter(mission -> mission.satelliteId().equals(satellite(data))).isPresent(), "The data satellite and its initial mission");
            helper.assertTrue(registry.satellite(satellite(solar)).isPresent() && registry.ownerSatellites(owner) == ownerSatellites + 1,
                    "Each satellite must be registered exactly once");
            helper.assertTrue(ops.containsAll(consumedOps(transferRecord().sourceSnapshot())) && consumedOps(transferRecord()
                    .sourceSnapshot()).containsAll(ops), "T' must carry both bays consumed by their own operations");
            stage = 3;
        }

        void land() {
            var server = helper.getLevel().getServer();
            var record = RocketTransferSavedData.get(server).find(transfer).orElse(null);
            if (record == null || record.phase() != RocketTransferPhase.COMMITTED) { return; }
            var moon = server.getLevel(CelestialIds.MOON_LEVEL);
            var destination = moon == null ? null : (RocketEntity) moon.getEntity(record.destinationEntityId().orElseThrow());
            if (destination == null || destination.flightData().orElseThrow().state() != RocketFlightState.LANDED) { return; }
            var landed = consumedOps(destination.snapshot().orElseThrow());
            helper.assertTrue(landed.size() == 2 && landed.stream().allMatch(java.util.Objects::nonNull)
                    && claim.members().stream().allMatch(member -> landed.contains(member.receipt().orElseThrow().op())),
                    "The landed rocket must carry both bays consumed, never a package: " + landed);
            helper.assertTrue(!before.equals(BayDeploymentGameTests.signatures(destination.snapshot().orElseThrow())), "No package came back");
            helper.assertTrue(SatelliteMissionSavedData.get(server).ownerSatellites(owner) == ownerSatellites + 1,
                    "The landing registered nothing more");
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
            for (var pkg : List.of(data, solar)) {
                session.find(satellite(pkg)).ifPresent(receipt -> BayReceiptTestHooks.remove(server, receipt.op(), outcome -> { }));
                session.rejections().remove(satellite(pkg));
            }
            var registry = SatelliteMissionSavedData.get(server);
            registry.satellite(satellite(solar)).ifPresent(state -> registry.decommission(state.satelliteId(), owner, true, true));
            for (BlockPos relative : blocks) { clearBay(helper.getLevel(), helper.absolutePos(relative)); }
        }
    }
}
