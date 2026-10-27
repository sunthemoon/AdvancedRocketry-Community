package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W170 A0: D4's S' and D' for one and eight bays through the runtime budget; REJECTED bays and every other block stay. */
final class BayConsumedSnapshotsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static Map<RocketPosition, BayReceipt> consumed(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord record,
                                                            int... bays) {
        var map = new LinkedHashMap<RocketPosition, BayReceipt>();
        for (int i : bays) { map.put(bay(i), intent(record, i)); }
        return map;
    }

    private static BayConsumedSnapshots.Result run(BayConsumedSnapshots.Job job) {
        var budget = new RocketCargoWorkBudget();
        long tick = 1;
        while (!job.step(budget, tick)) { tick++; assertTrue(tick < 100, "the job must finish within a few ticks"); }
        return job.result().orElseThrow();
    }

    @Test void oneConsumedBayChangesOnlyItsOwnRootAtBothEnds() {
        var record = record(2);
        var result = run(BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(), consumed(record, 0)).orElseThrow());
        for (var pair : new RocketStructureSnapshot[][] {{record.sourceSnapshot(), result.source()}, {record.destinationSnapshot(), result.destination()}}) {
            var before = pair[0]; var after = pair[1];
            assertEquals(before.snapshotId(), after.snapshotId(), "the snapshot ID stays");
            assertEquals(before.structureDigest(), after.structureDigest(), "the structure stays");
            assertNotEquals(before.contentHash(), after.contentHash(), "the content hash changes");
            for (int i = 0; i < before.blocks().size(); i++) {
                var was = before.blocks().get(i); var now = after.blocks().get(i);
                if (was.position().equals(bay(0))) {
                    var contents = SatelliteBayRoot.decode(now.blockEntityPayload().orElseThrow().data()).contents().orElseThrow();
                    assertEquals(intent(record, 0).op(), contents.consumed().orElseThrow(), "consumed by its own op");
                    assertTrue(contents.packageTag().isEmpty(), "the package is gone");
                    assertEquals(OWNER, contents.owner(), "the bay keeps its owner");
                } else {
                    assertEquals(was, now, "every other block, including the unconsumed bay, is byte-equal");
                }
            }
        }
        var consumedRecord = record.withConsumedSnapshots(result.source(), result.destination());
        assertEquals(record.transferId(), consumedRecord.transferId());
        assertEquals(record.sourceFlightData(), consumedRecord.sourceFlightData());
        assertNotEquals(record.checksum(), consumedRecord.checksum());
    }

    @Test void eightBaysConsumeTogetherAndTheResultIsDeterministic() {
        var record = record(8);
        var all = consumed(record, 0, 1, 2, 3, 4, 5, 6, 7);
        var first = run(BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(), all).orElseThrow());
        var second = run(BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(), all).orElseThrow());
        assertEquals(first.source().contentHash(), second.source().contentHash(), "the group's after hash is reproducible (R3)");
        assertEquals(first.destination().contentHash(), second.destination().contentHash());
        for (int i = 0; i < 8; i++) {
            final int bay = i;
            var block = first.source().blocks().stream().filter(b -> b.position().equals(bay(bay))).findFirst().orElseThrow();
            assertEquals(intent(record, i).op(), SatelliteBayRoot.decode(block.blockEntityPayload().orElseThrow().data())
                    .contents().orElseThrow().consumed().orElseThrow());
        }
        var partial = run(BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(),
                consumed(record, 0, 3)).orElseThrow());
        assertNotEquals(partial.source().contentHash(), first.source().contentHash(), "another member set is another group");
    }

    @Test void aBayThatIsNotTheReceiptsPackageRefusesTheWholeGroup() {
        var record = record(2);
        var wrong = consumed(record, 0, 1);
        var other = intent(record, 0);
        wrong.put(bay(1), new BayReceipt(UUID.randomUUID(), 9, other.rocket(), bay(1), other.satellite(), other.owner(), other.definition(),
                other.kind(), other.orbit(), other.packageSig(), other.transfer(), other.srcBefore(), null, other.dstBefore(), null,
                BayReceipt.Phase.INTENT, "NONE", false, false, 0, 1, 1, 0));
        assertTrue(BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(), wrong).isEmpty(),
                "a package signature that is not the receipt's");
        var missing = consumed(record, 0);
        missing.put(new RocketPosition(0, 1, 0), intent(record, 1));
        assertTrue(BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(), missing).isEmpty(),
                "a position that is not a bay");
        var once = run(BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(), consumed(record, 0)).orElseThrow());
        assertTrue(BayConsumedSnapshots.prepare(once.source(), once.destination(), consumed(record, 0)).isEmpty(),
                "a consumed bay holds no package to consume again");
    }

    @Test void theJobUsesTheTicksOneReconstructionSlot() {
        var record = record(8);
        var budget = new RocketCargoWorkBudget();
        assertTrue(budget.claim(5, new Object()), "another cursor takes tick 5's slot first");
        var job = BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(), consumed(record, 0, 1)).orElseThrow();
        assertFalse(job.step(budget, 5), "the job defers in a tick whose slot is taken");
        assertTrue(job.result().isEmpty());
        assertTrue(job.step(budget, 6), "and runs in the next");
        assertFalse(budget.claim(6, new Object()), "holding that tick's slot itself");
    }
}
