package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
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
        final boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0] || !RocketTransferEntities.loadedEntity(rocket) || !journal.retryChecked(server)) { return; }
            var owner = transfers.releaseOwner(server);
            var plan = owner.world.plan(owner.sidecar.root(), record.transferId());
            if (plan == null) { return; }
            var sidecar = owner.sidecar.root(); var before = rocket.flightData().orElseThrow();
            var data = RocketTransactionSavedData.get(server); var entries = data.entries();
            Object service;
            try { var field = RocketRuntime.class.getDeclaredField("service"); field.setAccessible(true); service = field.get(null); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            SealedDisassemblyCoordinator sealed = RocketSourceRetirementFixtures.field(service, "sealed");
            boolean[] authorized = {true}; boolean[] cancelled = {false};
            try {
                if (sealedFirst) {
                    var id = sealed.begin(rocket, () -> authorized[0], result -> cancelled[0] = result == SealedDisassembly.Result.REJECTED);
                    helper.assertTrue(id != null && RocketRuntime.sealedFrozen(rocket), "Sealed RAM claim did not start");
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
            } finally {
                authorized[0] = false;
                if (sealedFirst) {
                    sealed.tick(server, false);
                    helper.assertTrue(cancelled[0], "RAM-only fixture request did not cancel");
                } else {
                    // Only this fixture's unpublished B0 exists; closing releases its RAM fence without a sidecar write.
                    helper.assertTrue(owner.coordinator.release(record.transferId()).isPresent(), "Fixture lost its release");
                    owner.close();
                    try { var field = RocketTransferService.class.getDeclaredField("releases"); field.setAccessible(true); field.set(transfers, null); }
                    catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                }
                rocket.discard(); journal.remove(record.transferId());
                RocketSourceRetirementFixtures.settled(transfers).remove(record.transferId());
                RocketTransferEntities.releaseFlightTickets(server, record);
                done[0] = true;
            }
            helper.succeed();
        });
    }
    private SealedReleaseGuardGameTests() { }
}
