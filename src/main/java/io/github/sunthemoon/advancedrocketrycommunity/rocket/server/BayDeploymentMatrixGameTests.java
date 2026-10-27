package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayPackage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W128R-R2 A1: D0 on the live coordinator for a rocket with one DATA and one non-DATA (solar) bay, its source in TRANSIT
 * and T PREPARED. F5 refuses every bay with TARGET_NOT_ALLOWED for a missing body, a known non-orbitable body and the
 * legacy {@code space} body, before kind validation (the solar package would otherwise get its own research refusal, and
 * the data definition allows {@code space}); a captured source body that differs from both the source Level's own and
 * the destination is the one the DATA receipt records. A refused B14 request rejects every bay with ASSEMBLY_JOURNAL_UNPROVED. Each case asserts the
 * real receipts, rejection map, claim, registry and packages; nothing durable changes on a refusal.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayDeploymentMatrixGameTests {
    static final String MATRIX = "bay_deployment_matrix";
    static final String B14 = "bay_deployment_b14";
    private BayDeploymentMatrixGameTests() { }

    @GameTest(template = TEMPLATE, batch = MATRIX, timeoutTicks = 400)
    public static void f5RefusesAMissingBodyForDataAndSolarBays(GameTestHelper helper) {
        new Matrix(helper, ModIdentity.id("w128r_missing_body"), Expect.F5).start();
    }

    @GameTest(template = TEMPLATE, batch = MATRIX, timeoutTicks = 400)
    public static void f5RefusesANonOrbitableBodyForDataAndSolarBays(GameTestHelper helper) {
        new Matrix(helper, ModIdentity.id("tau_ceti"), Expect.F5).start();
    }

    @GameTest(template = TEMPLATE, batch = MATRIX, timeoutTicks = 400)
    public static void f5RefusesTheLegacySpaceBodyForDataAndSolarBays(GameTestHelper helper) {
        new Matrix(helper, CelestialIds.SPACE_ID, Expect.F5).start();
    }

    @GameTest(template = TEMPLATE, batch = MATRIX, timeoutTicks = 600)
    public static void theCapturedSourceBodyIsTheOneTheDataReceiptRecords(GameTestHelper helper) {
        // Captured Mars: neither the source Level's own body (Earth) nor the destination (the Moon).
        new Matrix(helper, ModIdentity.id("mars"), Expect.CAPTURED).start();
    }

    @GameTest(template = TEMPLATE, batch = B14, timeoutTicks = 600)
    public static void aRefusedCheckpointRequestRejectsEveryBayWithNoReceipt(GameTestHelper helper) {
        new Matrix(helper, RocketFlightPlanner.EARTH.bodyId(), Expect.REFUSED_REQUEST).start();
    }

    private enum Expect { F5, CAPTURED, REFUSED_REQUEST }

    private static final class Matrix {
        final GameTestHelper helper;
        final ResourceLocation body;
        final Expect expect;
        final UUID owner = UUID.randomUUID();
        final ItemStack data, solar;
        Flight flight;
        BayClaim claim;
        List<String> before;
        String solarCode;
        int stage, waits;
        boolean sawIntent, closed;

        Matrix(GameTestHelper helper, ResourceLocation body, Expect expect) {
            this.helper = helper; this.body = body; this.expect = expect; data = dataPackage(owner); solar = solarPackage(owner);
        }
        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 560, "Bay matrix fixture exceeded its bounded wait at stage " + stage);
                if (stage == 0) { begin(); } else { observe(); }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void begin() {
            var server = helper.getLevel().getServer();
            BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3)), target = helper.absolutePos(new BlockPos(14, 2, 3));
            if (!helper.getLevel().isPositionEntityTicking(origin) || !helper.getLevel().isPositionEntityTicking(target)) { return; }
            var catalog = SatelliteRuntime.celestialCatalog().orElseThrow();
            if (body.getPath().equals("tau_ceti")) {
                helper.assertTrue(catalog.get(body).filter(definition -> !definition.capabilities().orbitable()).isPresent(),
                        "Fixture: " + body + " is not a known non-orbitable body");
            } else if (body.getPath().equals("w128r_missing_body")) {
                helper.assertTrue(catalog.get(body).isEmpty(), "Fixture: the missing body exists");
            } else if (expect == Expect.CAPTURED) {
                helper.assertTrue(catalog.get(body).filter(definition -> definition.capabilities().orbitable()).isPresent(),
                        "Fixture: " + body + " is not a known orbitable body");
            }
            flight = transit(helper, origin, helper.getLevel().dimension().location(), position(target), owner, List.of(data, solar),
                    RocketEscrowSectionPatch.NEUTRAL, body, null);
            before = BayDeploymentGameTests.signatures(flight.record());
            if (expect == Expect.CAPTURED) {
                var identity = SatelliteBayPackage.decode(solar.copyWithCount(1).serializeNBT()).identity().orElseThrow();
                var validation = SatelliteRuntime.launchService().orElseThrow().validate(server, owner, identity, body);
                helper.assertTrue(validation.request().isEmpty(), "Fixture: the solar package must be refused by its own validation");
                solarCode = validation.result().code().name();
            }
            var journal = RocketTransferSavedData.get(server);
            journal.put(flight.record());
            if (expect == Expect.REFUSED_REQUEST) {
                bays(helper).refuseCheckpointForTests(flight.record().logicalRocketId());
                RocketTransactionSavedData.get(server).setDirty();
            }
            bays(helper).ascentComplete(server, journal, flight.record(), flight.source());
            claim = claim(helper, flight.record().logicalRocketId()).orElseThrow();
            helper.assertTrue(claim.stage() == BayClaim.Stage.GROUP && claim.members().size() == 2, "D0 did not hold L with both bays");
            stage = 1;
        }

        void observe() {
            var server = helper.getLevel().getServer();
            var session = bays(helper).sessionForTests();
            UUID logical = flight.record().logicalRocketId();
            if (claim(helper, logical).isPresent()) {
                helper.assertTrue(BayReceipts.holdsRocket(server, logical), "A claim must hold the gate lock");
                // Keep the transaction journal unacknowledged, so B14 asks for a checkpoint and this L's request is refused.
                if (expect == Expect.REFUSED_REQUEST) { RocketTransactionSavedData.get(server).setDirty(); }
                var receipt = session.find(satellite(data)).orElse(null);
                if (receipt != null && receipt.phase() == BayReceipt.Phase.INTENT) {
                    sawIntent = true;
                    helper.assertTrue(receipt.orbit().equals(body.toString()), "The INTENT does not record the captured body: " + receipt.orbit());
                }
                return;
            }
            helper.assertTrue(claim.stage() == BayClaim.Stage.ENDED && claim.handoff().orElseThrow() == BayClaim.Handoff.READY_SOURCE,
                    "The claim did not end with READY_SOURCE: " + claim.handoff());
            var dataMember = member(satellite(data)); var solarMember = member(satellite(solar));
            var registry = SatelliteMissionSavedData.get(server);
            helper.assertTrue(registry.satellite(satellite(data)).isEmpty() && registry.satellite(satellite(solar)).isEmpty(), "W27d1 registered X");
            helper.assertTrue(before.equals(BayDeploymentGameTests.signatures(flight.record().sourceSnapshot())), "The packages in T changed");
            if (expect == Expect.CAPTURED) {
                var rejected = session.find(satellite(data)).orElseThrow();
                helper.assertTrue(sawIntent && dataMember.state() == BayMember.State.REJECTED
                        && rejected.phase() == BayReceipt.Phase.REJECTED && rejected.reason().equals("DEPLOYMENT_UNAVAILABLE")
                        && session.acknowledged(rejected.op()) && rejected.orbit().equals(body.toString()),
                        "The DATA bay did not go INTENT to REJECTED on the captured body: " + rejected);
                helper.assertTrue(solarMember.state() == BayMember.State.REFUSED && solarMember.reason().equals(solarCode)
                        && session.find(satellite(solar)).isEmpty(), "The solar bay was not refused by its own validation");
            } else {
                String reason = expect == Expect.F5 ? "TARGET_NOT_ALLOWED" : "ASSEMBLY_JOURNAL_UNPROVED";
                for (var member : List.of(dataMember, solarMember)) {
                    helper.assertTrue(member.state() == BayMember.State.REFUSED && member.reason().equals(reason),
                            "Every bay must be refused with " + reason + ": " + member.state() + " " + member.reason());
                    UUID satellite = member.satellite().orElseThrow();
                    helper.assertTrue(session.find(satellite).isEmpty(), "A refusal wrote a receipt");
                    helper.assertTrue(session.rejections().get(satellite).orElseThrow().reason().equals(reason),
                            "The runtime rejection was not remembered for " + satellite);
                }
                helper.assertTrue(expect != Expect.REFUSED_REQUEST || !claim.checkpointRequested(),
                        "A refused request was recorded as sent");
            }
            close();
            helper.succeed();
        }

        BayMember member(UUID satellite) {
            return claim.members().stream().filter(member -> member.satellite().orElseThrow().equals(satellite)).findFirst().orElseThrow();
        }

        void close() {
            if (closed) { return; }
            closed = true;
            var server = helper.getLevel().getServer();
            var session = bays(helper).sessionForTests();
            if (flight != null) {
                var journal = RocketTransferSavedData.get(server);
                RocketTransferRecord current = journal.find(flight.record().transferId()).orElse(null);
                if (current != null) {
                    current.destinationEntityId().map(helper.getLevel()::getEntity).ifPresent(net.minecraft.world.entity.Entity::discard);
                    journal.remove(current.transferId());
                }
                RocketTransferEntities.releaseFlightTickets(server, flight.record());
                flight.source().discard();
                bays(helper).forgetForTests(flight.record().logicalRocketId());
            }
            for (var pkg : List.of(data, solar)) {
                session.rejections().remove(satellite(pkg));
                session.find(satellite(pkg)).ifPresent(receipt -> BayReceiptTestHooks.remove(server, receipt.op(), outcome -> { }));
            }
        }
    }
}
