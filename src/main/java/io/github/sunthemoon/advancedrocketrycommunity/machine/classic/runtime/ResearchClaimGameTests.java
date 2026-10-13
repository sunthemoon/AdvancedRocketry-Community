package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.*;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResearchClaimGameTests {
    @GameTest(template = "empty", batch = "research_processor_copy", timeoutTicks = 400)
    public static void processorCopiesAndRebindCannotResetPaidAnalysisOrCredits(GameTestHelper helper) {
        UUID owner = UUID.randomUUID(); var original = ResearchGameTests.fixture(helper, true, Direction.NORTH, owner, 0);
        var copy = ResearchGameTests.fixture(helper, true, Direction.NORTH, owner, 16);
        var target = ResearchGameTests.fixture(helper, true, Direction.NORTH, owner, 32);
        ResearchGameTests.ready(helper, List.of(original, copy, target), false, () -> {
            try (original; copy; target) {
                var data = SatelliteMissionSavedData.get(original.level.getServer()); var service = ResearchRuntime.service(original.level);
                long now = original.level.getServer().overworld().getGameTime(); UUID job = UUID.randomUUID();
                var source = new GroundSurveyJob.Device(UUID.randomUUID(), original.level.dimension().location(), 0);
                var instance = instance(owner, job, data.groundSurveyQuery(jobs -> jobs.previewTime(now)));
                helper.assertTrue(data.groundSurveys(jobs -> jobs.start(new GroundSurveys.Start(job, owner, source, instance, 20, 20, 200, 20, 120), now)).changed(), "Processor fixture START refused");
                original.flush();
                for (int tick = 0; tick < 20; tick++) { helper.assertTrue(data.groundSurveys(jobs -> jobs.advanceSurvey(job, source, 24_000, now)).changed(), "Processor fixture completion refused"); }
                copy.controller.load(original.controller.saveWithFullMetadata()); copy.controller.onLoad();
                helper.assertTrue(!service.intent(copy.controller, owner, 4) && copy.controller.identity().orElseThrow().collision(), "Idle processor copy claimed");
                helper.assertTrue(service.intent(original.controller, owner, 4), "Original processor claim refused"); original.flush(); original.feed(20);
                helper.assertTrue(service.advance(original.controller), "Original analysis could not pay");
                helper.assertTrue(!service.intent(target.controller, owner, 4), "Second processor claimed RUNNING analysis");
                helper.assertTrue(!service.intent(target.controller, UUID.randomUUID(), 5), "Foreign analysis rebind succeeded");
                helper.assertTrue(service.intent(target.controller, owner, 5), "Owner analysis rebind refused"); target.feed(3_980);
                helper.assertTrue(service.work(target.controller).orElseThrow().analysis().progress() == 1 && service.work(original.controller).isEmpty(), "Rebind reset/stole progress");
                helper.assertTrue(!service.advance(target.controller) && target.controller.researchStatus() == ResearchStatus.WAITING_SAVE
                        && target.controller.storedEnergy() == 3_980, "Rebind claim paid before flush"); target.flush();
                for (int tick = 1; tick < 200; tick++) { helper.assertTrue(service.advance(target.controller), "Rebound analysis advance refused"); }
                helper.assertTrue(data.account(owner).balance() == 120 && target.controller.storedEnergy() == 0, "Rebound credit/FE differs");
                copy.controller.load(target.controller.saveWithFullMetadata()); copy.controller.onLoad();
                helper.assertTrue(!service.intent(copy.controller, owner, 4) && !service.intent(copy.controller, owner, 5)
                        && data.account(owner).balance() == 120 && data.instance(instance.instanceId()).isPresent(), "PROCESSED processor copy reset a credit or instance");
            }
            helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "research_account_full", timeoutTicks = 200)
    public static void fullAccountRefusesTheKernelDebitAndPreservesTheRegistry(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        try (var fixture = ResearchGameTests.fixture(helper, true, Direction.NORTH, owner, 0)) {
            var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE); UUID job = UUID.randomUUID();
            var source = new GroundSurveyJob.Device(UUID.randomUUID(), fixture.level.dimension().location(), 0);
            registry.restoreAccount(new ResearchAccount(1, owner, 1_000_000, 1_000_000, 0));
            var jobs = registry.groundSurveys();
            helper.assertTrue(jobs.start(new GroundSurveys.Start(job, owner, source, instance(owner, job, 0), 20, 20, 1, 20, 120), 0).changed(), "Full account fixture START refused");
            registry.markPersisted(); for (int tick = 0; tick < 20; tick++) { jobs.advanceSurvey(job, source, 24_000, 0); }
            helper.assertTrue(jobs.claim(job, owner, fixture.controller.device(), 0).changed(), "Full account claim refused"); registry.markPersisted(); fixture.feed(20);
            var before = registryState(registry);
            boolean paid = fixture.controller.paidResearchAdvance(20, () -> {
                var result = jobs.advanceAnalysis(job, fixture.controller.device(), 0);
                helper.assertTrue(result.code() == GroundSurveys.Code.ACCOUNT_FULL, "Full account refusal differs"); return result.changed();
            });
            helper.assertTrue(!paid && fixture.power.get(0).energyStored() == 20 && before.equals(registryState(registry)), "Account overflow paid or changed registry state");
        }
        helper.succeed();
    }
    private static List<?> registryState(SatelliteMissionRegistry registry) {
        return List.of(registry.groundSurveys().all(), registry.instances(), registry.accounts(),
                registry.satellites(), registry.missions(), registry.logicalGameTime(), registry.lastObservedGameTime(),
                registry.saveEpoch(), registry.epochToWrite(), registry.scheduledCount(),
                registry.reservedBytes("GROUND_SURVEYS"), registry.reservedBytes("INSTANCES"), registry.reservedBytes("ACCOUNTS"));
    }
    private static AsteroidInstance instance(UUID owner, UUID job, long now) {
        return new AsteroidInstance(2, UUID.randomUUID(), owner, CelestialIds.EARTH_ID, ModIdentity.id("small_asteroid"),
                "0123456789abcdef", "0123456789abcdef", 1, List.of(new RewardEntry(ResourceLocation.tryParse("minecraft:iron_ore"), 1)),
                now, OptionalLong.empty(), InstanceState.PENDING, InstanceOrigin.ground(job), Optional.empty());
    }
    private ResearchClaimGameTests() { }
}
