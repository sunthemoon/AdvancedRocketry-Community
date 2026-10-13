package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Registry-only Forge verification. Device blocks/FE and loaded-tick scheduling are a separate slice. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GroundSurveyRegistryGameTests {
    private GroundSurveyRegistryGameTests() { }

    @GameTest(template = "empty", batch = "ground_survey_registry")
    public static void savedRegistryCompletesAndCreditsExactlyOnce(GameTestHelper helper) throws Exception {
        UUID owner = UUID.randomUUID(), job = UUID.randomUUID(), instance = UUID.randomUUID();
        GroundSurveyJob.Device source = device(), processor = device();
        var data = SatelliteMissionSavedData.create(0);
        var start = start(owner, job, instance, source);
        helper.assertTrue(data.groundSurveys(jobs -> jobs.start(start, 0)).changed(), "Ground job did not start");
        var file = Files.createTempFile("arce-ground-gametest-", ".dat");
        try {
            data.flush(file);
            for (int tick = 1; tick <= 20; tick++) {
                final int now = tick;
                helper.assertTrue(data.groundSurveys(jobs -> jobs.advanceSurvey(job, source, 24_000, now)).changed(),
                        "Survey advance was refused");
            }
            helper.assertTrue(data.instance(instance).orElseThrow().state() == InstanceState.AVAILABLE,
                    "Final survey advance did not publish availability");
            helper.assertTrue(data.groundSurveys(jobs -> jobs.claim(job, owner, processor, 21)).changed(), "Claim refused");
            data.flush(file);
            data = SatelliteMissionSavedData.load(data.save(new CompoundTag()));
            helper.assertTrue(data.groundSurveys(jobs -> jobs.advanceAnalysis(job, processor, 22)).changed(),
                    "Durable analysis claim could not advance");
            helper.assertTrue(data.account(owner).balance() == 120, "Credit amount changed");
            CompoundTag credited = data.save(new CompoundTag());
            helper.assertTrue(!data.groundSurveys(jobs -> jobs.advanceAnalysis(job, processor, 23)).changed(),
                    "Analysis replay credited twice");
            helper.assertTrue(credited.equals(data.save(new CompoundTag())), "Replay changed the registry");
            helper.assertTrue(SatelliteMissionSavedData.load(credited).account(owner).balance() == 120,
                    "Credit was not retained on reload");
        } finally { Files.deleteIfExists(file); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "ground_survey_registry")
    public static void partialCancellationRoundTripsWithoutAnInstance(GameTestHelper helper) throws Exception {
        UUID owner = UUID.randomUUID(), job = UUID.randomUUID(), instance = UUID.randomUUID();
        GroundSurveyJob.Device source = device();
        var data = SatelliteMissionSavedData.create(0);
        data.groundSurveys(jobs -> jobs.start(start(owner, job, instance, source), 0));
        var file = Files.createTempFile("arce-ground-cancel-", ".dat");
        try {
            data.flush(file);
            data.groundSurveys(jobs -> jobs.advanceSurvey(job, source, 24_000, 1));
            helper.assertTrue(data.groundSurveys(jobs -> jobs.cancel(job, owner, false, 2)).changed(), "Cancel refused");
            helper.assertTrue(data.instance(instance).isEmpty(), "Cancellation retained its pending instance");
            var loaded = SatelliteMissionSavedData.load(data.save(new CompoundTag()));
            helper.assertTrue(loaded.operational() && loaded.groundSurveys().get(0).surveyProgress() == 1,
                    "Cancellation lost its partial progress or blocked reload");
            helper.assertTrue(loaded.account(owner).balance() == 0, "Cancellation paid research");
        } finally { Files.deleteIfExists(file); }
        helper.succeed();
    }

    private static GroundSurveyJob.Device device() {
        return new GroundSurveyJob.Device(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"), 0);
    }

    private static GroundSurveys.Start start(UUID owner, UUID job, UUID instanceId, GroundSurveyJob.Device source) {
        ResourceLocation system = ResourceLocation.tryParse("advancedrocketrycommunity:earth");
        AsteroidInstance instance = new AsteroidInstance(2, instanceId, owner, system,
                ResourceLocation.tryParse("advancedrocketrycommunity:small_asteroid"), "0123456789abcdef",
                "0123456789abcdef", 1, List.of(new RewardEntry(ResourceLocation.tryParse("minecraft:iron_ore"), 64)),
                0, OptionalLong.empty(), InstanceState.PENDING, InstanceOrigin.ground(job), Optional.empty());
        return new GroundSurveys.Start(job, owner, source, instance, 20, 20, 1, 20, 120);
    }
}
