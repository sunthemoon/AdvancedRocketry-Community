package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real research profiles, registered plugs and server authority; test tickets are never production tickets. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResearchGameTests {
    @GameTest(template = "empty", batch = "research_formation", timeoutTicks = 200)
    public static void bothPatternsFormInAllRotationsAndRejectAWrongPart(GameTestHelper helper) {
        for (boolean processor : new boolean[]{false, true}) {
            for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                try (var fixture = fixture(helper, processor, facing, UUID.randomUUID(), 0)) {
                    helper.assertTrue(fixture.controller.formed(), "Research formation " + processor + "/" + facing + ": " + fixture.controller.reason());
                    helper.assertTrue(fixture.controller.onlyOpCanSetNbt(), "Research root is not operator-only");
                    BlockPos wrong = fixture.world(new PatternPosition(1, 0, 1));
                    fixture.level.setBlock(wrong, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                    ClassicRuntime.manager(fixture.level).changed(wrong); ClassicRuntime.manager(fixture.level).tick();
                    helper.assertTrue(!fixture.controller.formed(), "Wrong cell formed " + processor + "/" + facing);
                    helper.assertTrue(fixture.controller.firstDiagnostic().isPresent(), "Missing cell lacks a diagnostic");
                }
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "research_lifecycle", timeoutTicks = 200)
    public static void powerOwnershipSurvivesGenerationReloadAndRejectsOverlap(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        try (var first = fixture(helper, true, Direction.NORTH, owner, 0)) {
            helper.assertTrue(first.controller.formed(), "First processor did not form"); first.feed(1_000);
            var plug = first.power.get(0); var claim = plug.ownerClaim().orElseThrow();
            try (var second = new ResearchFixture(first.level, first.origin.offset(-2, 0, 0), true, Direction.SOUTH, owner)) {
                helper.assertTrue(!second.controller.formed(), "Overlapping processor stole the plug");
                helper.assertTrue(plug.ownerClaim().orElseThrow().machineInstanceId().equals(claim.machineInstanceId()), "Owner claim changed on overlap");
            }
            // The overlap's cleanup removed the shared plug. Rebuild the first fixture's power cell.
            first.level.setBlock(plug.getBlockPos(), ModBlocks.POWER_INPUT_PLUG.get().defaultBlockState(), Block.UPDATE_ALL);
            first.level.setBlock(plug.getBlockPos().above(), Blocks.STONE_SLAB.defaultBlockState(), Block.UPDATE_ALL); first.revalidate();
            var current = (ClassicHatchBlockEntity) first.level.getBlockEntity(plug.getBlockPos());
            var saved = first.controller.saveWithFullMetadata(); var identity = first.controller.identity().orElseThrow();
            first.controller.onChunkUnloaded(); first.controller.load(saved); first.controller.onLoad(); first.revalidate();
            helper.assertTrue(first.controller.formed() && identity.equals(first.controller.identity().orElseThrow()), "Reload lost research identity/formation");
            var hatchRoot = current.saveWithFullMetadata(); var root = hatchRoot.getCompound(ClassicHatchBlockEntity.ROOT);
            root.getCompound("binding").putLong("generation", first.controller.controllerState().generation() + 2);
            current.load(hatchRoot); first.revalidate();
            helper.assertTrue(current.multiblockBinding().orElseThrow().generation() == first.controller.controllerState().generation(), "Research generation did not reconcile");
            first.level.setBlock(first.origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(current.ownerClaim().isEmpty(), "Removal retained a research plug owner");
            var stale = current.saveWithFullMetadata(); stale.getCompound(ClassicHatchBlockEntity.ROOT).put("claim", root.getCompound("claim").copy());
            stale.getCompound(ClassicHatchBlockEntity.ROOT).put("binding", new CompoundTag()); current.load(stale);
            helper.assertTrue(current.ownerClaim().isEmpty(), "Absent research owner did not retire a stale claim");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "research_jobs", timeoutTicks = 400)
    public static void surveyAndAnalysisUseExactFeAndCreditOnce(GameTestHelper helper) {
        UUID owner = UUID.randomUUID(); var survey = fixture(helper, false, Direction.NORTH, owner, 0);
        var processor = fixture(helper, true, Direction.NORTH, owner, 16);
        ready(helper, List.of(survey, processor), true, () -> {
            try (survey; processor) {
                night(survey); var service = ResearchRuntime.service(survey.level); var data = SatelliteMissionSavedData.get(survey.level.getServer());
                helper.assertTrue(service.intent(survey.controller, owner, 0), "Survey START refused: " + survey.controller.researchStatus());
                var job = service.work(survey.controller).orElseThrow(); UUID instance = job.instanceId();
                helper.assertTrue(data.resourceQuery(resources -> resources.available(owner, job.snapshot().system())).isEmpty(), "PENDING instance was visible");
                survey.feed(10_000); helper.assertTrue(!service.advance(survey.controller) && survey.controller.researchStatus() == ResearchStatus.WAITING_SAVE
                        && survey.controller.storedEnergy() == 10_000, "Unflushed START debited FE"); survey.flush();
                for (int tick = 0; tick < 1_000; tick++) { if (survey.controller.storedEnergy() == 0) { survey.feed(10_000); }
                    helper.assertTrue(service.advance(survey.controller), "Survey advance refused: " + survey.controller.researchStatus()); }
                helper.assertTrue(survey.injected == 20_000 && survey.controller.storedEnergy() == 0, "Survey FE is not 20,000");
                helper.assertTrue(data.resourceQuery(resources -> resources.available(owner, job.snapshot().system())).stream().anyMatch(value -> value.instanceId().equals(instance)), "Completed instance is not visible");
                helper.assertTrue(service.intent(processor.controller, owner, 4), "Analysis claim refused"); processor.feed(4_000);
                helper.assertTrue(!service.advance(processor.controller) && processor.controller.researchStatus() == ResearchStatus.WAITING_SAVE
                        && processor.controller.storedEnergy() == 4_000, "Unflushed claim debited FE"); processor.flush();
                for (int tick = 0; tick < 200; tick++) { helper.assertTrue(service.advance(processor.controller), "Analysis advance refused"); }
                helper.assertTrue(processor.injected == 4_000 && processor.controller.storedEnergy() == 0 && data.account(owner).balance() == 120, "Analysis FE/credit differs");
                helper.assertTrue(!service.intent(processor.controller, owner, 4) && !service.advance(processor.controller)
                        && data.account(owner).balance() == 120, "Replay credited research twice");
                var loaded = SatelliteMissionSavedData.load(data.save(new CompoundTag()));
                helper.assertTrue(loaded.account(owner).balance() == 120 && loaded.instance(instance).orElseThrow().state() == InstanceState.AVAILABLE, "Completion/credit did not round-trip");
            }
            helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "research_copy", timeoutTicks = 400)
    public static void idleCopiesAndCompletedCopiesCannotStartOrStealJobs(GameTestHelper helper) {
        UUID owner = UUID.randomUUID(); var survey = fixture(helper, false, Direction.NORTH, owner, 0);
        var copy = fixture(helper, false, Direction.NORTH, owner, 16);
        ready(helper, List.of(survey, copy), true, () -> {
            try (survey; copy) {
                var service = ResearchRuntime.service(survey.level); night(survey);
                CompoundTag copied = survey.controller.saveWithFullMetadata(); copy.controller.load(copied); copy.controller.onLoad();
                helper.assertTrue(copy.controller.identity().orElseThrow().collision() && !service.intent(copy.controller, owner, 0), "Idle copy START was not quarantined");
                helper.assertTrue(service.intent(survey.controller, owner, 0), "Original could not START"); survey.flush();
                for (int tick = 0; tick < 1_000; tick++) { if (survey.controller.storedEnergy() == 0) { survey.feed(10_000); }
                    helper.assertTrue(service.advance(survey.controller), "Original advance refused"); }
                var data = SatelliteMissionSavedData.get(survey.level.getServer()); var completed = data.groundSurveys().stream().filter(job -> job.ownerId().equals(owner)).findFirst().orElseThrow();
                copy.controller.load(survey.controller.saveWithFullMetadata()); copy.controller.onLoad();
                helper.assertTrue(!service.intent(copy.controller, owner, 0) && completed.equals(data.groundSurveys().stream().filter(job -> job.jobId().equals(completed.jobId())).findFirst().orElseThrow()), "READY copy rewrote the original");
                CompoundTag quarantined = copy.controller.saveWithFullMetadata(); copy.controller.onChunkUnloaded(); copy.controller.load(quarantined); copy.controller.onLoad();
                helper.assertTrue(copy.controller.identity().orElseThrow().collision(), "Copied root lost quarantine on reload");
            }
            helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "research_rebind", timeoutTicks = 400)
    public static void explicitOwnerRebindKeepsPaidProgressAndCancelRemovesOnlyPending(GameTestHelper helper) {
        UUID owner = UUID.randomUUID(); var source = fixture(helper, false, Direction.NORTH, owner, 0);
        var replacement = fixture(helper, false, Direction.NORTH, owner, 16);
        ready(helper, List.of(source, replacement), true, () -> {
            try (source; replacement) {
                night(source); var service = ResearchRuntime.service(source.level); var data = SatelliteMissionSavedData.get(source.level.getServer());
                helper.assertTrue(service.intent(source.controller, owner, 0), "Source START refused"); source.flush(); source.feed(20);
                helper.assertTrue(service.advance(source.controller), "Source advance refused"); var job = service.work(source.controller).orElseThrow();
                helper.assertTrue(!service.intent(replacement.controller, UUID.randomUUID(), 5), "Non-owner rebind succeeded");
                helper.assertTrue(service.intent(replacement.controller, owner, 5), "Owner rebind refused");
                var rebound = service.work(replacement.controller).orElseThrow();
                helper.assertTrue(rebound.jobId().equals(job.jobId()) && rebound.instanceId().equals(job.instanceId()) && rebound.surveyProgress() == 1, "Rebind reset paid work");
                helper.assertTrue(service.work(source.controller).isEmpty(), "Old source still owns the job");
                helper.assertTrue(service.intent(replacement.controller, owner, 1), "Owner cancellation refused");
                helper.assertTrue(data.instance(job.instanceId()).isEmpty() && data.account(owner).balance() == 0, "Cancel paid or retained PENDING");
            }
            helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "research_roots", timeoutTicks = 200)
    public static void retainedUnknownDeviceRootKeepsClaimsInert(GameTestHelper helper) {
        try (var fixture = fixture(helper, true, Direction.NORTH, UUID.randomUUID(), 0)) {
            fixture.feed(100); var plug = fixture.power.get(0); var claim = plug.ownerClaim().orElseThrow();
            var outer = fixture.controller.saveWithFullMetadata(); var raw = outer.getCompound(fixture.controller.rootKey()); raw.putInt("schema_version", 99);
            fixture.controller.load(outer); fixture.controller.onLoad(); fixture.revalidate();
            helper.assertTrue(fixture.controller.repairRequired() && raw.equals(fixture.controller.saveWithFullMetadata().get(fixture.controller.rootKey())), "Unknown identity was normalized");
            helper.assertTrue(plug.ownerClaim().orElseThrow().equals(claim), "Retained invalid owner claim was retired");
            helper.assertTrue(!fixture.controller.paidResearchAdvance(20, () -> true) && plug.energyStored() == 100, "Invalid root debited FE");
            helper.assertTrue(!ClassicControllerMenu.canOpen(helper.makeMockPlayer(), fixture.controller), "Unconnected player passed menu authority");
        }
        helper.succeed();
    }
    static ResearchFixture fixture(GameTestHelper helper, boolean processor, Direction facing, UUID owner, int x) {
        return new ResearchFixture(helper.getLevel(), helper.absolutePos(new BlockPos(5 + x, 6, 5)), processor, facing, owner);
    }
    static void night(ResearchFixture fixture) {
        fixture.level.setWeatherParameters(100_000, 0, false, false); fixture.level.setDayTime(18_000); fixture.level.updateSkyBrightness();
    }
    static void ready(GameTestHelper helper, List<ResearchFixture> fixtures, boolean sky, Runnable action) {
        var pacer = new GameTestTickPacer(helper.getLevel().getServer()); boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) { return; }
            if (fixtures.stream().allMatch(fixture -> fixture.ticking() && (fixture.controller.processor() || !sky || fixture.level.canSeeSky(fixture.lens())))) {
                done[0] = true; action.run();
            } else { pacer.pace(); }
        });
    }
    private ResearchGameTests() { }
}
