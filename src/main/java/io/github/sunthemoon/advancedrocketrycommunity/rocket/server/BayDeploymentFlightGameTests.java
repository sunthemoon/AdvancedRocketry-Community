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
        new Flight(helper, false, false).start();
    }

    /**
     * W170-R7: D2 refuses after D1. Once the data bay's INTENT is seen, this coordinator sees the registry as blocked for L
     * only, so D2 writes each bay REJECTED (REGISTRY_BLOCKED) and registers nothing. The bay menu shows the durable REJECTED
     * phase with its reason, and T lands with both packages byte-equal to the launch (W27d1's menu assertion, restored).
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 1_000)
    public static void aRegistryRefusalAfterIntentRejectsBothBaysDurablyAndTheMenuShowsIt(GameTestHelper helper) {
        new Flight(helper, true, false).start();
    }

    /**
     * W170-R4's remaining part: an escrow conflict holds the claim for repair once the data bay's INTENT is seen, before D2.
     * D2 then runs for no member: nothing is registered under the held claim, every member waits HELD with its durable
     * INTENT, and the solar satellite registered beforehand stays the owner's only satellite. No INTENT is written after
     * the hold (W170-R44; {@code BayDeploymentGameTests.aClaimHeldForRepairBeforeD1WritesNoIntent} holds before any D1).
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 1_000)
    public static void aClaimHeldForRepairBeforeD2RegistersNoSatellite(GameTestHelper helper) {
        new Flight(helper, false, true).start();
    }

    private static final class Flight {
        final GameTestHelper helper;
        final boolean refuse, conflict;
        final UUID owner = UUID.randomUUID(), transfer = UUID.randomUUID(), logical = UUID.randomUUID();
        final ItemStack data = dataPackage(owner), solar = solarPackage(owner);
        final BlockPos origin = new BlockPos(2, 2, 2);
        final List<BlockPos> blocks = List.of(origin, origin.above(), origin.above(2), origin.west(), origin.east(), origin.east().above());
        RocketEntity source;
        BayClaim claim;
        List<String> before;
        final java.util.Map<ItemStack, UUID> atHold = new java.util.IdentityHashMap<>();
        int stage, waits, ownerSatellites, held;
        boolean sawIntent, sawRegistered, probedRecovery, blocked, closed;

        Flight(GameTestHelper helper, boolean refuse, boolean conflict) { this.helper = helper; this.refuse = refuse; this.conflict = conflict; }
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
                    if (refuse && !blocked) { bays(helper).blockRegistryForTests(logical); blocked = true; } // before D2 runs
                    if (conflict && !blocked) { // before D2 runs, as the escrow hook reports a conflict at a permitted boundary
                        bays(helper).escrowConflict(server, RocketTransferSavedData.get(server), transferRecord());
                        blocked = true;
                        // W170-R44: the receipts that exist at the hold; no INTENT may be written after it
                        for (var pkg : List.of(data, solar)) {
                            session.find(satellite(pkg)).ifPresent(receipt -> atHold.put(pkg, receipt.op()));
                        }
                    }
                }
                if (conflict && blocked) { heldBeforeD2(server); return; }
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
            if (refuse) { assertRejected(server); stage = 3; return; }
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

        /**
         * The held variant while the claim holds: REPAIR, nothing registered, no member past INTENT, and no INTENT written
         * after the hold (W170-R44): a package with no receipt at the hold never gets one, and one with an INTENT keeps that
         * exact operation. Done 40 ticks after every member holds and each INTENT from before the hold is acknowledged.
         */
        void heldBeforeD2(net.minecraft.server.MinecraftServer server) {
            helper.assertTrue(claim.hold() == BayClaim.Hold.REPAIR && claim.reason().equals("ESCROW_AUTHORITY_CONFLICT"),
                    "The escrow conflict did not hold the claim for repair: " + claim.hold() + " " + claim.reason());
            var registry = SatelliteMissionSavedData.get(server);
            helper.assertTrue(registry.satellite(satellite(data)).isEmpty() && registry.ownerSatellites(owner) == ownerSatellites,
                    "D2 registered a satellite under a claim held for repair");
            helper.assertTrue(claim.members().stream().allMatch(member -> member.state() == BayMember.State.CANDIDATE
                    || member.state() == BayMember.State.INTENT_WRITE || member.state() == BayMember.State.INTENT
                    || member.state() == BayMember.State.HELD), "A member went past INTENT under the hold");
            var session = bays(helper).sessionForTests();
            for (var pkg : List.of(data, solar)) {
                var receipt = session.find(satellite(pkg)).orElse(null);
                UUID op = atHold.get(pkg);
                helper.assertTrue(op == null ? receipt == null
                        : receipt != null && receipt.op().equals(op) && receipt.phase() == BayReceipt.Phase.INTENT,
                        "An INTENT was written after the hold: " + receipt);
            }
            boolean settled = claim.members().stream().allMatch(member -> member.state() == BayMember.State.HELD)
                    && atHold.values().stream().allMatch(session::acknowledged);
            helper.assertTrue(before.equals(BayDeploymentGameTests.signatures(transferRecord())), "A held bay's package in T changed");
            if (settled && ++held >= 40) { close(); helper.succeed(); }
        }

        /** The refused variant at the claim's end: both bays durably REJECTED with REGISTRY_BLOCKED, shown by the menu. */
        void assertRejected(net.minecraft.server.MinecraftServer server) {
            var session = bays(helper).sessionForTests();
            helper.assertTrue(sawIntent && blocked && !sawRegistered, "The INTENT and the block before D2 were not observed");
            for (var pkg : List.of(data, solar)) {
                var member = claim.members().stream().filter(candidate -> candidate.satellite().orElseThrow().equals(satellite(pkg)))
                        .findFirst().orElseThrow();
                helper.assertTrue(member.state() == BayMember.State.REJECTED && member.reason().equals("REGISTRY_BLOCKED"),
                        "A bay was not REJECTED by D2 with REGISTRY_BLOCKED: " + member.state() + " " + member.reason());
                var rejected = session.find(satellite(pkg)).orElseThrow();
                helper.assertTrue(rejected.phase() == BayReceipt.Phase.REJECTED && rejected.reason().equals("REGISTRY_BLOCKED")
                        && session.acknowledged(rejected.op()) && rejected.transfer().equals(transfer) && rejected.rocket().equals(logical),
                        "The REJECTED receipt is not exact: " + rejected);
            }
            for (BlockPos at : List.of(origin().east(), origin().east().above())) {
                helper.assertTrue(bay(at).phase() == SatelliteBayStatus.Phase.REJECTED
                        && bay(at).reason() == SatelliteBayStatus.Reason.REGISTRY_BLOCKED,
                        "The bay menu does not show the durable REJECTED: " + bay(at).phase() + " " + bay(at).reason());
            }
            helper.assertTrue(before.equals(BayDeploymentGameTests.signatures(transferRecord())), "A rejected bay's package in T changed");
            var registry = SatelliteMissionSavedData.get(server);
            helper.assertTrue(registry.satellite(satellite(data)).isEmpty() && registry.ownerSatellites(owner) == ownerSatellites,
                    "A refusal registered a satellite");
        }

        void land() {
            var server = helper.getLevel().getServer();
            var record = RocketTransferSavedData.get(server).find(transfer).orElse(null);
            if (record == null || record.phase() != RocketTransferPhase.COMMITTED) { return; }
            var moon = server.getLevel(CelestialIds.MOON_LEVEL);
            var destination = moon == null ? null : (RocketEntity) moon.getEntity(record.destinationEntityId().orElseThrow());
            if (destination == null || destination.flightData().orElseThrow().state() != RocketFlightState.LANDED) { return; }
            if (refuse) {
                helper.assertTrue(before.equals(BayDeploymentGameTests.signatures(destination.snapshot().orElseThrow())),
                        "The landed rocket's packages are not byte-equal to the launch");
                helper.assertTrue(SatelliteMissionSavedData.get(server).ownerSatellites(owner) == ownerSatellites,
                        "The landing registered a satellite");
                helper.assertTrue(bay(origin().east()).phase() == SatelliteBayStatus.Phase.REJECTED, "The durable REJECTED did not stay");
                close();
                helper.succeed();
                return;
            }
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
            if (blocked) { bays(helper).forgetForTests(logical); } // ends the registry block for L
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
