package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W196 A1: the installed coordinator, real grouped receipt writer and ordinary flight executor. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayReleasedGameTests {
    private BayReleasedGameTests() { }

    @GameTest(template = TEMPLATE, batch = "bay_released_repair", timeoutTicks = 1_200)
    public static void coherentRepairFliesWithItsPackageAndStaysLocked(GameTestHelper helper) {
        new FlightCheck(helper, false).start();
    }

    @GameTest(template = TEMPLATE, batch = "bay_released_blocked", timeoutTicks = 1_200)
    public static void blockedRegistryFlightUsesExactPermitsAndKeepsItsPendingCharge(GameTestHelper helper) {
        new FlightCheck(helper, true).start();
    }

    @GameTest(template = TEMPLATE, batch = "bay_released_mixed", timeoutTicks = 1_200)
    public static void registeredMemberIsConsumedBeforeTheRepairPackageFlies(GameTestHelper helper) {
        new FlightCheck(helper, false, true).start();
    }

    @GameTest(template = TEMPLATE, batch = "bay_released_prepared", timeoutTicks = 1_200)
    public static void releasedPreparedRebuildsItsAbsentSource(GameTestHelper helper) {
        new FlightCheck(helper, RocketTransferPhase.PREPARED).start();
    }
    @GameTest(template = TEMPLATE, batch = "bay_released_spawned", timeoutTicks = 1_200)
    public static void releasedSpawnedRebuildsItsAbsentDestination(GameTestHelper helper) {
        new FlightCheck(helper, RocketTransferPhase.DESTINATION_SPAWNED).start();
    }
    @GameTest(template = TEMPLATE, batch = "bay_released_passengers", timeoutTicks = 1_200)
    public static void releasedPassengerPhaseRebuildsItsAbsentDestination(GameTestHelper helper) {
        new FlightCheck(helper, RocketTransferPhase.PASSENGERS_TRANSFERRED).start();
    }
    @GameTest(template = TEMPLATE, batch = "bay_released_removed", timeoutTicks = 1_200)
    public static void releasedRemovedPhaseRebuildsItsAbsentDestination(GameTestHelper helper) {
        new FlightCheck(helper, RocketTransferPhase.SOURCE_REMOVED).start();
    }
    @GameTest(template = TEMPLATE, batch = "bay_released_committed", timeoutTicks = 1_200)
    public static void releasedCommittedRebuildsItsAbsentDestination(GameTestHelper helper) {
        new FlightCheck(helper, RocketTransferPhase.COMMITTED).start();
    }

    @GameTest(template = TEMPLATE, batch = "bay_released_missing", timeoutTicks = 1_200)
    public static void releasedWithoutJournalStaysLockedForOfflineRepair(GameTestHelper helper) {
        var fixture = new Flight[1]; var row = new BayReceipt[1]; var stage = new int[] {0};
        helper.onEachTick(() -> {
            var server = helper.getLevel().getServer(); var coordinator = bays(helper);
            if (stage[0] == 0) {
                var origin = helper.absolutePos(new BlockPos(3, 2, 3));
                if (!helper.getLevel().isPositionEntityTicking(origin)) { return; }
                var owner = UUID.randomUUID(); var pkg = dataPackage(owner);
                fixture[0] = transit(helper, origin, helper.absolutePos(new BlockPos(14, 2, 3)), owner,
                        List.of(pkg), RocketEscrowSectionPatch.NEUTRAL);
                row[0] = intent(server, fixture[0].record(), pkg, 0).withReleased();
                // Deliberately no journal: this is an offline-repair fixture, never a successful recovery.
                helper.assertTrue(BayReceiptTestHooks.put(server, row[0], outcome -> { }).queued(), "Receipt refused");
                stage[0] = 1;
            } else if (stage[0] == 1 && storeIdle(server)) {
                coordinator.restartForTests(List.of(row[0])); stage[0] = 2;
            } else if (stage[0] == 2) {
                var claim = coordinator.claimForTests(row[0].rocket()).orElseThrow();
                if (!claim.reason().equals("TRANSFER_AUTHORITY_CONFLICT")) { return; }
                helper.assertTrue(BayReceipts.holdsRocket(server, row[0].rocket()) && !RocketEscrowGate.idle(fixture[0].source()),
                        "Missing T unlocked released receipt");
                helper.assertTrue(coordinator.sessionForTests().current(row[0].op()).filter(row[0]::equals).isPresent()
                        && coordinator.sessionForTests().acknowledged(row[0].op()), "Missing T changed receipt");
                helper.assertTrue(RocketTransferSavedData.get(server).find(row[0].transfer()).isEmpty(), "Missing T was recreated");
                fixture[0].source().discard(); coordinator.forgetForTests(row[0].rocket());
                helper.assertTrue(BayReceiptTestHooks.removeReleasedFixture(server, row[0].op()).queued(), "Cleanup refused");
                stage[0] = 3;
            } else if (stage[0] == 3 && storeIdle(server)) { stage[0] = 4; helper.succeed(); }
        });
    }

    private static final class FlightCheck {
        private final GameTestHelper helper;
        private final boolean blocked;
        private final boolean mixed;
        private RocketTransferPhase restartPhase;
        private final UUID owner = UUID.randomUUID();
        private final ItemStack pkg;
        private Flight fixture;
        private BayReceipt receipt;
        private BayReceipt registered;
        private List<CompoundTag> expectedPayloads;
        private int stage, pending, waits;
        private boolean closed;

        FlightCheck(GameTestHelper helper, boolean blocked) {
            this(helper, blocked, false);
        }
        FlightCheck(GameTestHelper helper, boolean blocked, boolean mixed) {
            this.helper = helper; this.blocked = blocked; this.mixed = mixed; pkg = dataPackage(owner);
        }
        FlightCheck(GameTestHelper helper, RocketTransferPhase restartPhase) {
            this(helper, false, false); this.restartPhase = restartPhase;
        }
        void start() { helper.onEachTick(this::tick); }
        private void tick() {
            if (closed) { return; }
            var server = helper.getLevel().getServer();
            try {
                helper.assertTrue(++waits < 1_150, "Released flight did not finish at stage " + stage);
                var journal = RocketTransferSavedData.get(server);
                var coordinator = bays(helper);
                switch (stage) {
                    case 0 -> {
                        var origin = helper.absolutePos(new BlockPos(3, 2, 3));
                        if (!helper.getLevel().isPositionEntityTicking(origin)) { return; }
                        var other = dataPackage(owner);
                        fixture = transit(helper, origin, helper.absolutePos(new BlockPos(14, 2, 3)), owner,
                                mixed ? List.of(pkg, other) : List.of(pkg), RocketEscrowSectionPatch.NEUTRAL);
                        receipt = intent(server, fixture.record(), pkg, 0);
                        if (!blocked) { receipt = receipt.withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", server.getTickCount()); }
                        if (restartPhase != null) { receipt = receipt.withReleased(); }
                        helper.assertTrue(BayReceiptTestHooks.put(server, receipt, outcome -> { }).queued(), "Fixture receipt refused");
                        expectedPayloads = bayPayloads(fixture.record().sourceSnapshot());
                        if (mixed) {
                            var intent = BayRegisteredGameTests.sequenced(intent(server, fixture.record(), other, 1), receipt.seq() + 1);
                            var after = BayRegisteredGameTests.consumed(fixture.record(), List.of(intent));
                            registered = BayRegisteredGameTests.registered(intent, after.source().contentHash(), after.destination().contentHash());
                            helper.assertTrue(BayReceiptTestHooks.put(server, registered, outcome -> { }).queued(), "REGISTERED fixture refused");
                            expectedPayloads = bayPayloads(after.source());
                        }
                        var record = fixture.record();
                        if (restartPhase != null) {
                            if (restartPhase != RocketTransferPhase.PREPARED) {
                                record = record.destinationSpawned(UUID.randomUUID());
                                while (record.phase() != restartPhase) {
                                    record = record.advance(RocketTransferPhase.values()[record.phase().ordinal() + 1]);
                                }
                            }
                            fixture.source().discard();
                        }
                        journal.put(record);
                        if (blocked) { coordinator.blockRegistryForTests(fixture.record().logicalRocketId()); }
                        stage = 1;
                    }
                    case 1 -> {
                        if (!storeIdle(server) || !coordinator.sessionForTests().acknowledged(receipt.op())) { return; }
                        pending = BayReceiptTestHooks.authority(server).pendingCount();
                        coordinator.restartForTests(mixed ? List.of(receipt, registered) : List.of(receipt));
                        stage = 2;
                    }
                    case 2 -> {
                        var current = coordinator.sessionForTests().current(receipt.op()).orElseThrow();
                        if (!current.released() || !coordinator.sessionForTests().acknowledged(receipt.op())) { return; }
                        helper.assertTrue(current.equals(receipt.withReleased()), "Release changed more than its flag");
                        var t = journal.find(receipt.transfer()).orElseThrow();
                        if (mixed) {
                            helper.assertTrue(coordinator.sessionForTests().current(registered.op()).orElseThrow().equals(registered.withReleased())
                                    && coordinator.sessionForTests().acknowledged(registered.op()), "Mixed flags were not one acknowledged group");
                            helper.assertTrue(BayReleasedAuthority.payloads(t.sourceSnapshot(), List.of(receipt, registered))
                                    && BayReleasedAuthority.payloads(t.destinationSnapshot(), List.of(receipt, registered)),
                                    "REGISTERED consumption was not complete before release");
                        }
                        if (coordinator.claim(server, journal, t).kind() != RocketTransferBayRecoveryGate.Kind.RELEASED_FLIGHT) { return; }
                        var boundary = coordinator.beforeAction(server, journal, t, RocketTransferBayRecoveryGate.Action.TICK_SOURCE);
                        helper.assertTrue(boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.PERMIT, "No exact flight permit");
                        helper.assertTrue(boundary.token().valid(server, journal, t, RocketTransferBayRecoveryGate.Action.TICK_SOURCE), "Fresh permit refused");
                        helper.assertTrue(!boundary.token().valid(server, journal, t, RocketTransferBayRecoveryGate.Action.TICK_SOURCE), "Permit reused");
                        if (restartPhase == null) {
                            var saved = coordinator.beforeAction(server, journal, t, RocketTransferBayRecoveryGate.Action.TICK_SOURCE);
                            fixture.source().saveWithoutId(new CompoundTag());
                            helper.assertTrue(!saved.token().valid(server, journal, t, RocketTransferBayRecoveryGate.Action.TICK_SOURCE), "Save did not invalidate permit");
                            var moved = coordinator.beforeAction(server, journal, t, RocketTransferBayRecoveryGate.Action.TICK_SOURCE);
                            double x = fixture.source().getX();
                            fixture.source().setPos(x + 1, fixture.source().getY(), fixture.source().getZ());
                            helper.assertTrue(!moved.token().valid(server, journal, t, RocketTransferBayRecoveryGate.Action.TICK_SOURCE),
                                    "Source motion did not invalidate permit");
                            fixture.source().setPos(x, fixture.source().getY(), fixture.source().getZ());
                        }
                        for (var action : List.of(RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL,
                                RocketTransferBayRecoveryGate.Action.SETTLE_SOURCE, RocketTransferBayRecoveryGate.Action.RECOVER_BY_ID,
                                RocketTransferBayRecoveryGate.Action.ELECT_DUPLICATES)) {
                            helper.assertTrue(coordinator.beforeAction(server, journal, t, action).kind()
                                    == RocketTransferBayRecoveryGate.BoundaryKind.HELD, "Unexpected action permit: " + action);
                        }
                        stage = 3;
                    }
                    case 3 -> {
                        var t = journal.find(receipt.transfer()).orElseThrow();
                        var destination = RocketTransferEntities.findDestination(server, t);
                        if (destination == null || destination.flightData().orElseThrow().state() != RocketFlightState.LANDED) { return; }
                        helper.assertTrue(fixture.source().isRemoved(), "Source survived the released flight");
                        helper.assertTrue(bayPayloads(destination.snapshot().orElseThrow()).equals(expectedPayloads),
                                "Released package or consumed marker changed");
                        helper.assertTrue(BayReceipts.holdsRocket(server, receipt.rocket()) && !RocketEscrowGate.idle(destination)
                                && !RocketEscrowGate.resourcesIdle(destination), "Landing unlocked the rocket");
                        helper.assertTrue(BayReceiptTestHooks.authority(server).pendingCount() == pending, "Pending charge ended");
                        helper.assertTrue(coordinator.sessionForTests().current(receipt.op()).orElseThrow().equals(receipt.withReleased()),
                                "The released receipt changed or retired");
                        helper.assertTrue(io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData
                                .get(server).satellite(receipt.satellite()).isEmpty(), "Released recovery registered the package");
                        destination.discard(); coordinator.forgetForTests(receipt.rocket());
                        helper.assertTrue(BayReceiptTestHooks.removeReleasedFixture(server, receipt.op()).queued(), "Fixture cleanup refused");
                        if (registered != null) { helper.assertTrue(BayReceiptTestHooks.removeReleasedFixture(server, registered.op()).queued(), "Mixed cleanup refused"); }
                        journal.remove(receipt.transfer()); stage = 4;
                    }
                    case 4 -> {
                        if (!storeIdle(server)) { return; }
                        RocketTransferEntities.releaseFlightTickets(server, fixture.record()); closed = true; helper.succeed();
                    }
                    default -> throw new IllegalStateException("Unexpected fixture stage");
                }
            } catch (RuntimeException | Error failure) {
                closed = true;
                if (fixture != null) {
                    coordinatorCleanup();
                    RocketTransferEntities.releaseFlightTickets(server, fixture.record());
                }
                throw failure;
            }
        }
        private void coordinatorCleanup() {
            var server = helper.getLevel().getServer();
            bays(helper).forgetForTests(fixture.record().logicalRocketId());
            if (receipt != null) { BayReceiptTestHooks.removeReleasedFixture(server, receipt.op()); }
            if (registered != null) { BayReceiptTestHooks.removeReleasedFixture(server, registered.op()); }
            var journal = RocketTransferSavedData.get(server);
            journal.find(fixture.record().transferId()).ifPresent(t -> {
                var destination = RocketTransferEntities.findDestination(server, t);
                if (destination != null) { destination.discard(); }
                journal.remove(t.transferId());
            });
            fixture.source().discard();
        }
    }
}
