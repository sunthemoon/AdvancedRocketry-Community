package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferConsumption;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** RT.7's consumption half; the companion registry test rejects callbacks from the obsolete registry session. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayPermitRowGameTests {
    private BayPermitRowGameTests() { }

    @GameTest(template = TEMPLATE, batch = "w333_station_rows", timeoutTicks = 400)
    public static void staleBeforeAndAfterHashesCannotPublishCoordinatorConsumption(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var pkg = dataPackage(UUID.randomUUID());
        var owner = io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData.read(pkg).identity().orElseThrow().ownerId();
        var flight = transit(h, h.absolutePos(new BlockPos(3, 2, 3)), h.absolutePos(new BlockPos(14, 2, 3)), owner,
                List.of(pkg), RocketEscrowSectionPatch.NEUTRAL);
        var record = flight.record();
        var journal = RocketTransferSavedData.get(server); journal.put(record); journal.flush(server);
        // Existing gated hold: keep ordinary recovery and D0 from editing this record while the negative steps run.
        bays(h).ascentComplete(server, journal, record, flight.source());
        bays(h).unqueueForTests(record.logicalRocketId());
        var intent = intent(server, record, pkg, 0);
        var after = BayRegisteredGameTests.consumed(record, List.of(intent));
        var registered = BayRegisteredGameTests.registered(intent, after.source().contentHash(), after.destination().contentHash());
        var publication = new BayGroupPublication(server, bays(h).sessionForTests(), null, ignored -> { }, (a, b) -> { });
        var coordinator = new BayConsumptionSteps(server, publication, null);
        var index = new int[1]; var claim = new BayClaim[1]; var ticks = new int[1];
        Runnable close = () -> {
            coordinator.forget(record.logicalRocketId()); flight.source().discard(); journal.remove(record.transferId());
            bays(h).forgetForTests(record.logicalRocketId());
            RocketTransferEntities.releaseFlightTickets(server, record);
        };
        h.onEachTick(() -> {
            try {
                h.assertTrue(++ticks[0] < 350, "Stale consumption fixture exceeded bounded owner ticks");
                if (!journal.actionable(record.logicalRocketId())) { return; }
                if (index[0] == 4) {
                    // Even a previously constructed source permit cannot be used against another snapshot observation.
                    var before = record.sourceSnapshot();
                    var obsolete = before.relocated(before.snapshotId(), before.sourceDimension(), before.sourceOrigin(), before.createdAtGameTime());
                    var permit = new BaySourceConsumption(flight.source().getUUID(), record.logicalRocketId(), owner, obsolete, after.source());
                    boolean refused = false;
                    try { permit.consume(flight.source()); } catch (IllegalStateException expected) { refused = true; }
                    h.assertTrue(refused && flight.source().snapshot().orElseThrow() == before, "Obsolete source permit changed the live source");
                    var exact = new BaySourceConsumption(flight.source().getUUID(), record.logicalRocketId(), owner, before, after.source());
                    h.assertTrue(exact.consume(flight.source()) == after.source(), "Exact source permit control was refused");
                    refused = false;
                    try { exact.consume(flight.source()); } catch (IllegalStateException expected) { refused = true; }
                    h.assertTrue(refused, "Source permit could be used twice");
                    close.run(); h.succeed(); return;
                }
                if (claim[0] == null) {
                    String stale = "0".repeat(64); int i = index[0];
                    var row = new BayReceipt(registered.op(), registered.seq(), registered.rocket(), registered.bay(), registered.satellite(),
                            registered.owner(), registered.definition(), registered.kind(), registered.orbit(), registered.packageSig(), registered.transfer(),
                            i == 0 ? stale : registered.srcBefore(), i == 2 ? stale : registered.srcAfter(),
                            i == 1 ? stale : registered.dstBefore(), i == 3 ? stale : registered.dstAfter(), registered.phase(), registered.reason(),
                            registered.preexisting(), registered.released(), registered.registryEpoch(), registered.createdTick(), registered.updatedTick(), 0);
                    var member = BayMember.restartRegistered(row);
                    claim[0] = BayClaim.restart(record.logicalRocketId(), record.transferId(), List.of(member));
                    var group = BayGroup.resumed(record.logicalRocketId(), record.transferId(), List.of(member)).orElseThrow();
                    claim[0].freeze(group); claim[0].establishSource(flight.source().getUUID()); claim[0].advance(BayClaim.Stage.CONSUME);
                    var consumed = record.withConsumedSnapshots(after.source(), after.destination());
                    h.assertTrue(RocketTransferConsumption.apply(journal, record, consumed, row.srcBefore(), row.dstBefore(), row.srcAfter(), row.dstAfter())
                            == RocketTransferConsumption.Result.HASH_OUTSIDE_GROUP, "Journal admitted stale hash index " + i);
                }
                coordinator.consume(claim[0]);
                h.assertTrue(journal.find(record.transferId()).orElseThrow() == record && flight.source().snapshot().orElseThrow() == record.sourceSnapshot(),
                        "Coordinator published a permit/effect for stale hash index " + index[0]);
                if (claim[0].hold() == BayClaim.Hold.REPAIR) {
                    h.assertTrue(Set.of("TRANSFER_AUTHORITY_CONFLICT", "CONSUMPTION_MISMATCH").contains(claim[0].reason()), "Unexpected stale-hash repair reason");
                    coordinator.forget(record.logicalRocketId()); claim[0] = null; index[0]++;
                }
            } catch (RuntimeException | Error failure) { close.run(); throw failure; }
        });
    }
}
