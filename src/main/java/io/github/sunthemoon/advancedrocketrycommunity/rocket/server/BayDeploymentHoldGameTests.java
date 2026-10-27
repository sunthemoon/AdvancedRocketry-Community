package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W128R-R2 A1 with the session's real coordinator, receipt store, registry and transfer journal. A durable INTENT whose
 * restart row meets a blocked registry holds unchanged with no write (B10's hold half); one whose satellite exists with a
 * different signature gets a durable REPAIR with its exact acknowledgement and then holds. A held claim with a real bay
 * package satisfies section 8.1 tests 6, 1, 2 and 3: a final boundary adds T in the same call, only the station region T
 * overlaps is refused, another record recovers meanwhile, and T never gets a flight ticket. The restart claims enter
 * through the coordinator's gated seam, exactly as ServerStartingEvent builds them.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayDeploymentHoldGameTests {
    static final String HOLD = "bay_deployment_hold";
    static final String HOOK = "bay_deployment_hook81";
    private BayDeploymentHoldGameTests() { }

    @GameTest(template = TEMPLATE, batch = HOLD, timeoutTicks = 400)
    public static void aRestartIntentWithABlockedRegistryHoldsUnchangedWithoutAWrite(GameTestHelper helper) {
        new Restart(helper, Mode.BLOCKED).start();
    }

    @GameTest(template = TEMPLATE, batch = HOLD, timeoutTicks = 400)
    public static void aRestartIntentWhoseSatelliteDiffersWritesRepairWithItsExactAcknowledgement(GameTestHelper helper) {
        new Restart(helper, Mode.CONFLICT).start();
    }

    @GameTest(template = TEMPLATE, batch = HOOK, timeoutTicks = 1_200)
    public static void aHeldClaimWithARealBayFollowsSection81Tests6123(GameTestHelper helper) {
        new Restart(helper, Mode.HOOK).start();
    }

    private enum Mode { BLOCKED, CONFLICT, HOOK }

    private static final class Restart {
        final GameTestHelper helper;
        final Mode mode;
        final UUID owner = UUID.randomUUID(), otherOwner = UUID.randomUUID();
        final ItemStack pkg;
        RocketTransferRecord record;
        Flight other;
        BayReceipt durable, settled;
        int stage, waits, quiet, pending;
        boolean closed;

        Restart(GameTestHelper helper, Mode mode) { this.helper = helper; this.mode = mode; pkg = dataPackage(owner); }
        void start() { helper.onEachTick(this::tick); }

        MinecraftServer server() { return helper.getLevel().getServer(); }
        UUID logical() { return record.logicalRocketId(); }
        UUID satellite() { return BayDeploymentGameTestSupport.satellite(pkg); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < (mode == Mode.HOOK ? 1_150 : 380), "Bay hold fixture exceeded its bounded wait at stage " + stage);
                switch (stage) {
                    case 0 -> setup();
                    case 1 -> restart();
                    case 2 -> held();
                    case 3 -> { if (mode == Mode.HOOK) { stations(); } else { unchanged(); } }
                    case 4 -> recovers();
                    case 5 -> cleanup();
                    default -> { }
                }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void setup() {
            var server = server();
            BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
            if (!helper.getLevel().isPositionEntityTicking(origin)) { return; }
            var target = mode == Mode.HOOK
                    ? new RocketPosition(origin.getX() + 40_000, 80, origin.getZ() + 40_000)
                    : position(helper.absolutePos(new BlockPos(14, 2, 3)));
            var flight = transit(helper, origin, mode == Mode.HOOK ? CelestialIds.SPACE_LEVEL.location() : helper.getLevel().dimension().location(),
                    target, owner, List.of(pkg), RocketEscrowSectionPatch.NEUTRAL, RocketFlightPlanner.EARTH.bodyId(), null);
            flight.source().discard(); // a restart has no source authority
            record = flight.record();
            var journal = RocketTransferSavedData.get(server);
            if (mode == Mode.CONFLICT) {
                // X exists with another owner before any receipt fences it: the restart row's signature conflict.
                helper.assertTrue(SatelliteMissionSavedData.get(server).launchIdle(t -> SatelliteState.launchIdle(satellite(),
                        ModIdentity.id("solar_satellite"), otherOwner, t, ModIdentity.id("earth"), new SatelliteBlueprint(
                                List.of(ModIdentity.id("primary")), false, new SatelliteStats(4, 720, 0, 0, 10)),
                        new SatelliteKindState.Plain(SatelliteKind.GAS_HARVESTER)), 0).code() == SatelliteOperationCode.SUCCESS,
                        "The conflicting registry fixture was refused");
            }
            helper.assertTrue(BayReceiptTestHooks.put(server, intent(server, record, pkg, 0), outcome -> { }).queued(), "INTENT refused");
            journal.put(record); // the same tick: T is held from its first classification
            if (mode == Mode.HOOK) {
                // Section 8.1 test 6: a final boundary meets HELD and adds T to the claimed set in the same call.
                var dispatch = dispatch();
                helper.assertTrue(!dispatch.ids().contains(record.transferId()), "T was claimed before any boundary");
                helper.assertTrue(!RocketTransferEscrowRules.flightAllowed(server, journal, record, dispatch,
                        RocketTransferBayRecoveryGate.Action.TICK_SOURCE), "A held T passed the flight boundary");
                helper.assertTrue(dispatch.ids().contains(record.transferId())
                                && !RocketSourceRetirementFixtures.live(RocketRuntime.installedTransfers()).contains(record.transferId()),
                        "The boundary's HELD did not add T to the claimed set in the same call");
            }
            if (mode != Mode.CONFLICT) { bays(helper).blockRegistryForTests(logical()); }
            stage = 1;
        }

        void restart() {
            var session = bays(helper).sessionForTests();
            var receipt = session.find(satellite()).orElseThrow();
            if (!storeIdle(server()) || !session.acknowledged(receipt.op())) { return; }
            durable = receipt;
            pending = BayReceiptTestHooks.authority(server()).pendingCount();
            var begun = bays(helper).restartForTests(List.of(durable));
            helper.assertTrue(begun.size() == 1 && begun.get(0).origin() == BayClaim.Origin.RESTART
                    && begun.get(0).transfer().equals(record.transferId()), "The restart claim was not built for T");
            stage = 2;
        }

        void held() {
            var claim = claim(helper, logical()).orElseThrow();
            var member = claim.members().get(0);
            if (member.state() != BayMember.State.HELD || !storeIdle(server())) { return; }
            var session = bays(helper).sessionForTests();
            var receipt = session.find(satellite()).orElseThrow();
            var registry = SatelliteMissionSavedData.get(server());
            if (mode == Mode.CONFLICT) {
                helper.assertTrue(receipt.phase() == BayReceipt.Phase.REPAIR && receipt.reason().equals("REGISTRY_SIGNATURE_CONFLICT")
                        && receipt.op().equals(durable.op()) && session.acknowledged(receipt.op()),
                        "No exactly acknowledged REPAIR (REGISTRY_SIGNATURE_CONFLICT): " + receipt);
                helper.assertTrue(member.reason().equals("REGISTRY_SIGNATURE_CONFLICT") && claim.hold() == BayClaim.Hold.REPAIR
                        && claim.reason().equals("REGISTRY_SIGNATURE_CONFLICT"), "The claim does not hold for offline repair");
                var existing = registry.satellite(satellite()).orElseThrow();
                helper.assertTrue(existing.ownerId().equals(otherOwner), "The conflicting satellite changed");
            } else {
                helper.assertTrue(receipt.equals(durable) && session.acknowledged(receipt.op()), "A blocked registry rewrote INTENT: " + receipt);
                helper.assertTrue(member.reason().equals("REGISTRY_BLOCKED") && claim.hold() == BayClaim.Hold.REPAIR
                        && claim.reason().equals("REGISTRY_BLOCKED"), "No REGISTRY_BLOCKED hold: " + claim.reason());
                helper.assertTrue(registry.satellite(satellite()).isEmpty(), "A blocked-registry hold registered X");
            }
            settled = receipt;
            stage = 3;
        }

        /** The hold is stable: no write, the pending charge, the lock, T and its package unchanged, for 20 ticks. */
        void unchanged() {
            assertHeld();
            if (++quiet < 20) { return; }
            stage = 5;
        }

        void assertHeld() {
            var server = server();
            var session = bays(helper).sessionForTests();
            var claim = claim(helper, logical()).orElse(null);
            helper.assertTrue(claim != null && claim.hold() == BayClaim.Hold.REPAIR && claim.stage() == BayClaim.Stage.OUTCOMES,
                    "The held claim ended or changed");
            helper.assertTrue(session.find(satellite()).orElseThrow().equals(settled) && storeIdle(server), "The held receipt was written again");
            helper.assertTrue(BayReceiptTestHooks.authority(server).pendingCount() == pending, "The pending charge changed");
            helper.assertTrue(BayReceipts.holdsRocket(server, logical()), "The gate lock released L");
            var current = RocketTransferSavedData.get(server).find(record.transferId()).orElseThrow();
            helper.assertTrue(current.phase() == RocketTransferPhase.PREPARED && current.checksum().equals(record.checksum()),
                    "T changed while held");
            helper.assertTrue(dispatch().ids().contains(record.transferId()), "T left the claimed set");
            helper.assertTrue(RocketTransferFenceGameTests.flightTickets(server, record.transferId()) == 0,
                    "A held restart claim's T received a flight ticket (section 8.1 test 3)");
        }

        /** Section 8.1 test 1: T's destination is in Space; only a station region it overlaps is refused. */
        void stations() {
            assertHeld();
            var service = RocketRuntime.installedTransfers();
            var destination = record.destinationSnapshot().sourceOrigin();
            String diagnostics = service.journalDiagnostics(server());
            helper.assertTrue(service.stationRegionInMotion(server(), destination.x() - 256, destination.z() - 256,
                    destination.x() + 255, destination.z() + 255), "The station region T overlaps was not refused: " + diagnostics);
            helper.assertTrue(!service.stationRegionInMotion(server(), destination.x() + 4_096, destination.z() + 4_096,
                    destination.x() + 4_607, destination.z() + 4_607), "A held claim refused an unrelated station: " + diagnostics);
            // Section 8.1 test 2: a second, unclaimed record recovers while T stays held.
            other = transit(helper, helper.absolutePos(new BlockPos(10, 2, 10)), helper.absolutePos(new BlockPos(14, 2, 12)),
                    UUID.randomUUID(), List.of(), RocketEscrowSectionPatch.NEUTRAL);
            RocketTransferSavedData.get(server()).put(other.record());
            stage = 4;
        }

        void recovers() {
            assertHeld();
            if (RocketTransferSavedData.get(server()).find(other.record().transferId()).isPresent()) { return; }
            var data = other.source().flightData().orElseThrow();
            helper.assertTrue(data.state() == RocketFlightState.FUELED
                    && other.source().getY() == other.record().sourceSnapshot().sourceOrigin().y(),
                    "The unclaimed record did not get the ordinary stationary recovery while T was held");
            stage = 5;
        }

        /** Root's cleanup of a hold that this slice keeps until offline repair: forget the claim, then remove the receipt. */
        void cleanup() {
            var server = server();
            if (settled != null) {
                bays(helper).forgetForTests(logical());
                helper.assertTrue(BayReceiptTestHooks.remove(server, settled.op(), outcome -> { }).queued(), "Fixture removal refused");
                settled = null;
                return;
            }
            if (!storeIdle(server)) { return; }
            if (mode == Mode.CONFLICT) {
                helper.assertTrue(SatelliteMissionSavedData.get(server).decommission(satellite(), otherOwner, true, true).code()
                        == SatelliteOperationCode.SUCCESS, "The registry fixture was not removed");
            }
            close();
            helper.succeed();
        }

        void close() {
            if (closed) { return; }
            closed = true;
            var server = server();
            var journal = RocketTransferSavedData.get(server);
            if (record != null) {
                bays(helper).forgetForTests(logical());
                if (journal.find(record.transferId()).isPresent()) { journal.remove(record.transferId()); }
                RocketTransferEntities.releaseFlightTickets(server, record);
                if (settled != null || durable != null) {
                    bays(helper).sessionForTests().find(satellite())
                            .ifPresent(receipt -> BayReceiptTestHooks.remove(server, receipt.op(), outcome -> { }));
                }
            }
            if (other != null) {
                if (journal.find(other.record().transferId()).isPresent()) { journal.remove(other.record().transferId()); }
                RocketTransferEntities.releaseFlightTickets(server, other.record());
                other.source().discard();
            }
        }
    }
}
