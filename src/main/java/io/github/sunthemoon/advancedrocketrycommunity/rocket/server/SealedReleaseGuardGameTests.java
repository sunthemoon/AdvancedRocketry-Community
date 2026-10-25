package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedReleaseGuardGameTests {
    @GameTest(template = "rocket_test", batch = "w152h_sealed_guard", timeoutTicks = 200)
    public static void sealedClaimRefusesReleasePlanAndStalePlanAdmission(GameTestHelper helper) { run(helper, true); }

    @GameTest(template = "rocket_test", batch = "w152h_release_guard", timeoutTicks = 200)
    public static void releaseFenceRefusesSealedAdmissionWithoutMutation(GameTestHelper helper) { run(helper, false); }

    private static void run(GameTestHelper helper, boolean sealedFirst) {
        var level = helper.getLevel(); var server = level.getServer();
        var transfers = RocketRuntime.installedTransfers();
        var journal = RocketTransferSavedData.get(server);
        var origin = helper.absolutePos(new BlockPos(3, 2, 3));
        var passenger = UUID.randomUUID();
        var fixture = RocketSourceRetirementFixtures.countdown(helper, origin, origin.offset(1, 0, 1), List.of(passenger), 0);
        var record = fixture.record(); journal.put(record);
        RocketSourceRetirementFixtures.settled(transfers).add(record.transferId());
        // H1 may retain an offline MT assignment after that rider left ME. This is also a valid empty sealed D.
        var flight = RocketTransferEntities.stationarySource(record, level.getGameTime());
        var rocket = fixture.spawn(helper, level, flight.withPassengers(flight.passengers().remove(passenger)), false);
        var release = new FixtureOwner(helper, transfers);
        final boolean[] done = {false}, tested = {false}, authorized = {true}, cancelled = {false};
        var pacer = new GameTestTickPacer(server);
        var ticks = new int[1]; var waiting = new String[] {"entity load"};
        helper.onEachTick(() -> {
            if (done[0]) { return; }
            try {
                pacer.pace();
                helper.assertTrue(++ticks[0] < 195, "Cross-guard fixture stalled at " + waiting[0]
                        + "; actionable=" + journal.actionable(record.logicalRocketId()) + "; tested=" + tested[0]);
                waiting[0] = "entity load";
                if (!RocketTransferEntities.loadedEntity(rocket)) { return; }
                waiting[0] = "checked journal acknowledgement";
                if (!journal.retryChecked(server)) { return; }
                if (tested[0]) {
                    waiting[0] = "sealed cancellation";
                    if (sealedFirst && !cancelled[0]) { return; }
                    rocket.discard(); journal.remove(record.transferId());
                    RocketSourceRetirementFixtures.settled(transfers).remove(record.transferId());
                    RocketTransferEntities.releaseFlightTickets(server, record);
                    release.close();
                    done[0] = true; helper.succeed(); return;
                }
                var owner = release.owner;
                waiting[0] = "positive release-plan control";
                var plan = owner.world.plan(owner.sidecar.root(), record.transferId());
                if (plan == null) { return; }
                var sidecar = owner.sidecar.root(); var before = rocket.flightData().orElseThrow();
                var data = RocketTransactionSavedData.get(server); var entries = data.entries();
                Object service;
                try { var field = RocketRuntime.class.getDeclaredField("service"); field.setAccessible(true); service = field.get(null); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                SealedDisassemblyCoordinator sealed = RocketSourceRetirementFixtures.field(service, "sealed");
                if (sealedFirst) {
                    waiting[0] = "sealed admission";
                    var id = sealed.begin(rocket, () -> authorized[0], result -> cancelled[0] = result == SealedDisassembly.Result.REJECTED);
                    // The same-tick inspection allowance may already belong to lazy discovery. Do not reset it for a fixture.
                    if (id == null) { return; }
                }
                try {
                    if (sealedFirst) {
                        helper.assertTrue(RocketRuntime.sealedFrozen(rocket), "Sealed RAM claim did not start");
                        helper.assertTrue(owner.world.plan(sidecar, record.transferId()) == null, "Release planning crossed sealed claim");
                        helper.assertTrue(!owner.world.boundary(plan.batch()) && !owner.coordinator.admit(plan), "Stale release plan crossed sealed boundary");
                        helper.assertTrue(!owner.coordinator.fenced(record.logicalRocketId()), "Refused release installed a fence");
                    } else {
                        helper.assertTrue(owner.coordinator.admit(plan) && RocketRuntime.releaseFenced(rocket), "Release fence did not start");
                        helper.assertTrue(sealed.begin(rocket, () -> true, result -> helper.fail("Refused request completed")) == null,
                                "Release fence admitted a sealed request");
                        helper.assertTrue(!RocketRuntime.sealedClaimed(rocket), "Refused sealed request left a claim");
                    }
                    helper.assertTrue(owner.sidecar.root().equals(sidecar) && data.entries().equals(entries)
                            && rocket.flightData().orElseThrow().equals(before), "Cross-guard changed sidecar, journal or entity");
                    tested[0] = true;
                } finally {
                    authorized[0] = false;
                    if (!sealedFirst) {
                        // Only this fixture's unpublished B0 exists; closing releases its RAM fence without a sidecar write.
                        helper.assertTrue(owner.coordinator.release(record.transferId()).isPresent(), "Fixture lost its release");
                        release.close();
                    }
                }
            } catch (RuntimeException | Error failure) {
                authorized[0] = false;
                release.close();
                throw failure;
            }
        });
    }

    /** Earlier GameTests replace the journal root; a production owner binds its journal for the server session. */
    private static final class FixtureOwner implements AutoCloseable {
        private final RocketTransferService transfers;
        private final RocketReleaseAccess.Owner previous;
        final RocketReleaseAccess.Owner owner;
        private boolean closed;

        FixtureOwner(GameTestHelper helper, RocketTransferService transfers) {
            this.transfers = transfers;
            previous = RocketSourceRetirementFixtures.field(transfers, "releases");
            helper.assertTrue(previous == null || idle(previous), "Cross-guard fixture requires an idle cached release owner");
            var server = helper.getLevel().getServer();
            owner = new RocketReleaseAccess.Owner(server, RocketSourceRetirementFixtures.recovery(transfers),
                    RocketSourceRetirementFixtures.live(transfers), RocketSourceRetirementFixtures.settled(transfers));
            helper.assertTrue(RocketSourceRetirementFixtures.field(owner.world, "journal") == RocketTransferSavedData.get(server),
                    "Cross-guard owner must use the installed fixture journal");
            helper.assertTrue(idle(owner) && owner.sidecar.pendingRoots().isEmpty(),
                    "Cross-guard fixture must not service another release");
            install(owner);
        }

        private static boolean idle(RocketReleaseAccess.Owner value) {
            java.util.Map<?, ?> work = RocketSourceRetirementFixtures.field(value.coordinator, "work");
            return work.isEmpty();
        }

        private void install(RocketReleaseAccess.Owner value) {
            try { var field = RocketTransferService.class.getDeclaredField("releases"); field.setAccessible(true); field.set(transfers, value); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }

        @Override public void close() {
            if (closed) { return; }
            closed = true;
            owner.close();
            install(previous);
        }
    }
    private SealedReleaseGuardGameTests() { }
}
