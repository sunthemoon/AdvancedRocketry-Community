package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRowGameTestFiles.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W333 N2: actual checked writes and all four durability consumers, followed by a new decoded session. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteEpochRowGameTests {
    private SatelliteEpochRowGameTests() { }

    @GameTest(template = "empty", batch = "w333_registry_rows", timeoutTicks = 200)
    public static void unknownCheckpointResolvesFresh(GameTestHelper helper) throws Exception { run(helper, true, "checkpoint", 1); helper.succeed(); }
    @GameTest(template = "empty", batch = "w333_registry_rows", timeoutTicks = 200)
    public static void unknownCoalescedWriteResolvesFresh(GameTestHelper helper) throws Exception { run(helper, true, "coalesced", 1); helper.succeed(); }
    @GameTest(template = "empty", batch = "w333_registry_rows", timeoutTicks = 200)
    public static void unknownAutosaveResolvesFresh(GameTestHelper helper) throws Exception { run(helper, true, "autosave", 1); helper.succeed(); }
    @GameTest(template = "empty", batch = "w333_registry_rows", timeoutTicks = 200)
    public static void failedCheckpointRetriesFresh(GameTestHelper helper) throws Exception { run(helper, false, "checkpoint", 1); helper.succeed(); }
    @GameTest(template = "empty", batch = "w333_registry_rows", timeoutTicks = 200)
    public static void secondUnknownSkipsAnotherEpoch(GameTestHelper helper) throws Exception { run(helper, true, "checkpoint", 2); helper.succeed(); }
    @GameTest(template = "empty", batch = "w333_registry_rows", timeoutTicks = 200)
    public static void waitingConsumersRemainFencedUntilResolution(GameTestHelper helper) throws Exception {
        for (String entry : List.of("checkpoint", "coalesced", "autosave")) { run(helper, true, entry, 1); }
        helper.succeed();
    }

    private static void run(GameTestHelper h, boolean unknown, String entry, int attempts) throws Exception {
        try (var f = new SatelliteRowGameTestFiles(h)) {
            f.seed();
            var data = f.data;
            long epoch = data.saveEpoch();
            h.assertTrue(data.resources(m -> m.claimResource(id(11), OWNER, id(90), rewards -> null, 1000)).success(), "Initial claim failed");
            h.assertTrue(f.checkpoint(f.cut(unknown)).outcome() == (unknown ? SatelliteRegistryProof.Outcome.UNKNOWN
                    : SatelliteRegistryProof.Outcome.FAILED), "Checkpoint did not reach its intended cut");
            h.assertTrue(data.reconcile(id(90), id(11), DeliveryReconciliation.ReceiptView.PERSISTED,
                    Optional.empty(), 1001).changed(), "Delivery acknowledgement was not recorded");
            h.assertTrue(data.resources(m -> m.startResource(start(id(2), id(12)), 1001)).success(), "Later mission start failed");
            h.assertTrue(data.groundSurveys(g -> g.start(ground(1, 1001), 1001)).code() == GroundSurveys.Code.SUCCESS, "Later ground start failed");
            h.assertTrue(data.groundSurveys(g -> g.claim(id(100), OWNER, device(300), 1001)).code() == GroundSurveys.Code.SUCCESS,
                    "Analysis claim failed");
            h.assertTrue(data.saveEpoch() == epoch && data.mission(id(12)).orElseThrow().startEpoch() == epoch
                    && ((MissionPayload.Resource) data.mission(id(11)).orElseThrow().payload()).ackEpoch().orElseThrow() == epoch,
                    "An unpublished attempt advanced durable/stamped epochs");
            h.assertTrue(decide(data, DeliveryReconciliation.ReceiptView.PERSISTED) == DeliveryReconciliation.Action.WAIT,
                    "Receipt drop ran before acknowledgement was durable");
            h.assertTrue(data.groundSurveys(g -> g.advanceSurvey(id(101), device(201), 24000, 1002)).code() == GroundSurveys.Code.WAITING_SAVE,
                    "Ground survey advanced before start durability");
            h.assertTrue(data.groundSurveys(g -> g.advanceAnalysis(id(100), device(300), 1002)).code() == GroundSurveys.Code.WAITING_SAVE,
                    "Ground analysis advanced before claim durability");
            h.assertTrue(data.resources(m -> m.claimResource(id(12), OWNER, id(90), rewards -> null, 2001)).code()
                    == SatelliteOperationCode.AWAITING_WORLD_SAVE, "A mission missing from the committed file paid a reward");
            data.applyLimits(new RegistryLimits(1024, 64, 1, 3072, 4096, 256, 2048, 16, 10, 2));
            data.completeDue(10000);
            h.assertTrue(data.mission(id(11)).isPresent() && data.isDirty() && data.flushPending(), "Uncovered acknowledgement was pruned");
            if (attempts == 2) {
                h.assertTrue(f.checkpoint(f.cut(true)).outcome() == SatelliteRegistryProof.Outcome.UNKNOWN, "Second attempt was not UNKNOWN");
                h.assertTrue(data.saveEpoch() == epoch, "Second UNKNOWN advanced durable epoch");
            }
            if (entry.equals("checkpoint")) {
                h.assertTrue(f.checkpoint(ATOMIC).outcome() == SatelliteRegistryProof.Outcome.COMMITTED, "Resolution checkpoint failed");
            } else {
                data.writePolicy(ATOMIC);
                if (entry.equals("autosave")) { data.save(f.file.toFile()); } else { data.flush(f.file); }
            }
            h.assertTrue(data.saveEpoch() == epoch + (unknown ? attempts + 1 : 1), "Resolution reused a carried epoch: " + entry);
            h.assertTrue(!data.isDirty() && !data.flushPending(), "Successful fresh capture did not settle flags");
            f.assertPayloadEqual();
            try (var loaded = new RegistryClose(f.reload())) {
                var disk = loaded.data();
                h.assertTrue(decide(disk, DeliveryReconciliation.ReceiptView.PERSISTED) == DeliveryReconciliation.Action.DROP_RECEIPT,
                        "Durable acknowledgement did not release the receipt");
                h.assertTrue(decide(disk, DeliveryReconciliation.ReceiptView.NONE) == DeliveryReconciliation.Action.NONE,
                        "Reload rematerialized an already acknowledged reward");
                h.assertTrue(disk.resources(m -> m.claimResource(id(12), OWNER, id(90), rewards -> null, 10001)).code()
                        == SatelliteOperationCode.SUCCESS, "Covered later mission could not claim");
                h.assertTrue(disk.groundSurveys(g -> g.advanceSurvey(id(101), device(201), 24000, 10001)).code() == GroundSurveys.Code.SUCCESS,
                        "Covered survey could not advance");
                h.assertTrue(disk.groundSurveys(g -> g.advanceAnalysis(id(100), device(300), 10001)).code() == GroundSurveys.Code.SUCCESS,
                        "Covered analysis could not advance");
            }
        }
    }

    private static DeliveryReconciliation.Action decide(SatelliteMissionSavedData data, DeliveryReconciliation.ReceiptView receipt) {
        return DeliveryReconciliation.decide(DeliveryReconciliation.view(true, data.mission(id(11)), id(90), data.saveEpoch()), receipt);
    }
}
