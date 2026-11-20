package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.InstanceState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResearchRestartGameTests {
    private ResearchRestartGameTests() { }

    /** Census F2: reopen the observatory, power plugs and paid registry one step before survey completion. */
    @GameTest(template = "empty", batch = "w415_observatory_restart", timeoutTicks = 400)
    public static void f2PaidObservatoryRestartCompletesSurveyExactlyOnce(GameTestHelper helper) {
        paidRestart(helper, false);
    }

    /** Census F2: reopen the processor, power plugs and paid registry one step before analysis credit. */
    @GameTest(template = "empty", batch = "w415_processor_restart", timeoutTicks = 400)
    public static void f2PaidProcessorRestartCompletesAnalysisAndCreditsExactlyOnce(GameTestHelper helper) {
        paidRestart(helper, true);
    }

    private static void paidRestart(GameTestHelper helper, boolean analysis) {
        UUID owner = UUID.randomUUID();
        var survey = ResearchGameTests.fixture(helper, false, Direction.NORTH, owner, 0);
        var processor = ResearchGameTests.fixture(helper, true, Direction.NORTH, owner, 16);
        ResearchGameTests.ready(helper, List.of(survey, processor), true, () -> {
            var server = survey.level.getServer();
            var originalData = SatelliteMissionSavedData.get(server);
            ResearchControllerBlockEntity restored = null;
            SatelliteMissionSavedData loadedData = null;
            try (survey; processor) {
                ResearchGameTests.night(survey);
                var service = ResearchRuntime.service(survey.level);
                helper.assertTrue(service.intent(survey.controller, owner, 0), "Survey START refused");
                survey.flush();
                int surveySteps = analysis ? 1_000 : 999;
                for (int tick = 0; tick < surveySteps; tick++) {
                    if (survey.controller.storedEnergy() == 0) { survey.feed(10_000); }
                    helper.assertTrue(service.advance(survey.controller), "Paid survey advance refused");
                }
                var fixture = analysis ? processor : survey;
                if (analysis) {
                    helper.assertTrue(survey.injected == 20_000 && survey.controller.storedEnergy() == 0,
                            "Survey setup did not pay exactly 20,000 FE");
                    helper.assertTrue(service.intent(processor.controller, owner, 4), "Analysis claim refused");
                    processor.flush(); processor.feed(4_000);
                    for (int tick = 0; tick < 199; tick++) {
                        helper.assertTrue(service.advance(processor.controller), "Paid analysis advance refused");
                    }
                }
                // Leave enough FE for several replays, so an empty buffer cannot hide a double debit.
                fixture.feed(80);
                var before = service.work(fixture.controller).orElseThrow();
                var instance = originalData.instance(before.instanceId()).orElseThrow();
                var account = originalData.account(owner);
                helper.assertTrue(before.surveyProgress() == surveySteps && before.analysis().progress() == (analysis ? 199 : 0)
                                && before.phase() == (analysis ? GroundSurveyJob.Phase.READY : GroundSurveyJob.Phase.ACTIVE)
                                && instance.state() == (analysis ? InstanceState.AVAILABLE : InstanceState.PENDING)
                                && fixture.injected == (analysis ? 4_080 : 20_080) && fixture.controller.storedEnergy() == 100
                                && account.balance() == 0 && account.lifetimeEarned() == 0 && account.lifetimeSpent() == 0,
                        "Pre-restart paid progress, FE, instance or research account differs");
                var identity = fixture.controller.identity().orElseThrow();
                CompoundTag controllerNbt = fixture.controller.saveWithFullMetadata();
                var plugNbt = fixture.power.stream().map(BlockEntity::saveWithFullMetadata).toList();
                originalData.awaitForTests(server);
                CompoundTag registryNbt = originalData.save(new CompoundTag());
                loadedData = SatelliteMissionSavedData.load(registryNbt);
                helper.assertTrue(loadedData.operational() && loadedData != originalData, "Registry was not freshly reopened");
                fixture.controller.onChunkUnloaded();
                fixture.level.removeBlockEntity(fixture.origin);
                server.overworld().getDataStorage().set(SatelliteMissionSavedData.DATA_NAME, loadedData);
                for (int index = 0; index < fixture.power.size(); index++) {
                    var old = fixture.power.get(index);
                    old.onChunkUnloaded(); fixture.level.removeBlockEntity(old.getBlockPos());
                    var plug = (ClassicHatchBlockEntity) BlockEntity.loadStatic(old.getBlockPos(), old.getBlockState(), plugNbt.get(index));
                    helper.assertTrue(plug != null && plug != old, "Power plug was not freshly reopened");
                    fixture.level.setBlockEntity(plug); plug.onLoad(); fixture.power.set(index, plug);
                }
                restored = (ResearchControllerBlockEntity) BlockEntity.loadStatic(fixture.origin,
                        fixture.controller.getBlockState(), controllerNbt);
                helper.assertTrue(restored != null && restored != fixture.controller, "Controller was not freshly reopened");
                fixture.level.setBlockEntity(restored); restored.onLoad(); fixture.revalidate();
                helper.assertTrue(restored.formed() && restored.identity().orElseThrow().equals(identity)
                                && restored.storedEnergy() == 100 && service.work(restored).orElseThrow().equals(before)
                                && loadedData.instance(before.instanceId()).orElseThrow().equals(instance)
                                && loadedData.account(owner).equals(account), "Joint restart changed paid work, FE, data or credit");
                helper.assertTrue(service.advance(restored), "First restored advance refused: " + restored.researchStatus());
                var completed = loadedData.groundSurveyQuery(jobs -> jobs.job(before.jobId())).orElseThrow();
                helper.assertTrue(restored.storedEnergy() == 80 && completed.surveyProgress() == 1_000
                                && completed.analysis().progress() == (analysis ? 200 : 0)
                                && completed.phase() == (analysis ? GroundSurveyJob.Phase.PROCESSED : GroundSurveyJob.Phase.READY)
                                && completed.analysis().state() == (analysis ? GroundSurveyJob.AnalysisState.CREDITED : GroundSurveyJob.AnalysisState.WAITING)
                                && completed.snapshot().equals(before.snapshot())
                                && loadedData.instance(before.instanceId()).orElseThrow().state() == InstanceState.AVAILABLE
                                && loadedData.instance(before.instanceId()).orElseThrow().yield().equals(instance.yield())
                                && loadedData.account(owner).balance() == (analysis ? 120 : 0)
                                && loadedData.account(owner).lifetimeEarned() == (analysis ? 120 : 0)
                                && loadedData.account(owner).lifetimeSpent() == 0,
                        "Restored completion did not pay one 20 FE step with exact data/credit");
                CompoundTag settled = loadedData.save(new CompoundTag());
                for (int replay = 0; replay < 3; replay++) {
                    helper.assertTrue(!service.advance(restored), "Completed work advanced twice");
                }
                helper.assertTrue(restored.storedEnergy() == 80 && settled.equals(loadedData.save(new CompoundTag()))
                                && loadedData.groundSurveyQuery(jobs -> jobs.owned(owner)).size() == 1,
                        "Replay debited FE, duplicated data or credited research twice");
                var settledReload = SatelliteMissionSavedData.load(settled);
                helper.assertTrue(settledReload.operational()
                                && settledReload.groundSurveyQuery(jobs -> jobs.job(before.jobId())).orElseThrow().equals(completed)
                                && settledReload.account(owner).equals(loadedData.account(owner)), "Completed state did not survive another load");
            } finally {
                if (restored != null) { restored.onChunkUnloaded(); }
                if (loadedData != null) { loadedData.awaitForTests(server); loadedData.close(); }
                server.overworld().getDataStorage().set(SatelliteMissionSavedData.DATA_NAME, originalData);
                originalData.setDirty();
            }
            helper.succeed();
        });
    }
}
