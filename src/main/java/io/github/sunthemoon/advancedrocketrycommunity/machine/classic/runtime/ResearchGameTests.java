package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
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
        var level = helper.getLevel();
        helper.assertTrue(ModBlocks.LATHE.get().getTicker(level, ModBlocks.LATHE.get().defaultBlockState(), ModBlockEntities.LATHE.get()) == null
                && ModBlocks.OBSERVATORY.get().getTicker(level, ModBlocks.OBSERVATORY.get().defaultBlockState(), ModBlockEntities.OBSERVATORY.get()) != null
                && ModBlocks.ASTROBODY_DATA_PROCESSOR.get().getTicker(level, ModBlocks.ASTROBODY_DATA_PROCESSOR.get().defaultBlockState(),
                        ModBlockEntities.ASTROBODY_DATA_PROCESSOR.get()) != null, "Only the research controllers may tick");
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
    /** W21c review L5: the block-entity ticker and the END-tick scheduler alone pay each natural tick; no direct call. */
    @GameTest(template = "empty", batch = "research_natural", timeoutTicks = 400)
    public static void productionTickerAndSchedulerPayEachNaturalTick(GameTestHelper helper) {
        UUID owner = UUID.randomUUID(); var survey = fixture(helper, false, Direction.NORTH, owner, 0);
        var server = helper.getLevel().getServer(); var service = ResearchRuntime.service(survey.level); int[] baseline = new int[3];
        steps(helper, ticking(List.of(survey), true), then(() -> {
            night(survey); helper.assertTrue(service.intent(survey.controller, owner, 0), "Natural START refused"); survey.flush(); survey.feed(10_000);
        }), waitTicks(server, 2), then(() -> {
            baseline[0] = server.getTickCount(); baseline[1] = service.work(survey.controller).orElseThrow().surveyProgress();
            baseline[2] = survey.controller.storedEnergy();
            helper.assertTrue(baseline[1] >= 1, "The first natural tick after the flushed START did not pay");
        }), waitTicks(server, 40), then(() -> {
            try (survey) {
                int ticks = server.getTickCount() - baseline[0], paid = service.work(survey.controller).orElseThrow().surveyProgress() - baseline[1];
                helper.assertTrue(ticks >= 40 && paid == ticks && baseline[2] - survey.controller.storedEnergy() == ResearchService.ENERGY_PER_TICK * ticks,
                        "Natural ticks " + ticks + " paid " + paid + " survey ticks");
                // The GameTest runs after the Level tick and before the server's END tick, so this tick's ticker queued it.
                helper.assertTrue(survey.controller.researchStatus() == ResearchStatus.QUEUED, "Ticker did not queue: " + survey.controller.researchStatus());
            }
            helper.succeed();
        }));
    }
    /** W21a design section 4: breaking a device keeps the job, pauses it with SOURCE_MISSING and refunds nothing. */
    @GameTest(template = "empty", batch = "research_break", timeoutTicks = 400)
    public static void breakingAnActiveSourceKeepsThePausedJobWithoutRefundOrDuplicate(GameTestHelper helper) {
        UUID owner = UUID.randomUUID(); var source = fixture(helper, false, Direction.NORTH, owner, 0);
        var replacement = fixture(helper, false, Direction.NORTH, owner, 16);
        var service = ResearchRuntime.service(source.level); var data = SatelliteMissionSavedData.get(source.level.getServer());
        var broken = new Object() { GroundSurveyJob job; int fe; ResearchControllerMenu menu; };
        steps(helper, ticking(List.of(source, replacement), true), then(() -> {
            night(source); helper.assertTrue(service.intent(source.controller, owner, 0), "Source START refused"); source.flush(); source.feed(1_000);
            for (int tick = 0; tick < 3; tick++) { helper.assertTrue(service.advance(source.controller), "Source advance refused"); }
            broken.job = service.work(source.controller).orElseThrow(); broken.fe = source.power.get(0).energyStored();
            broken.menu = new ResearchControllerMenu(1, replacement.controller, helper.makeMockPlayer()); broken.menu.broadcastChanges();
            helper.assertTrue(broken.menu.status() == ResearchStatus.IDLE, "A loaded source read as missing: " + broken.menu.status());
            helper.assertTrue(!ClassicRemovalPolicy.blocks(source.level, source.origin), "An ACTIVE source refused an ordinary break");
            source.level.destroyBlock(source.origin, false);
            helper.assertTrue(data.groundSurveyQuery(jobs -> jobs.job(broken.job.jobId())).orElseThrow().equals(broken.job),
                    "Breaking the source changed the job");
            helper.assertTrue(data.instance(broken.job.instanceId()).orElseThrow().state() == InstanceState.PENDING
                    && data.groundSurveyQuery(jobs -> jobs.owned(owner)).size() == 1, "Breaking the source resolved or duplicated the job");
            helper.assertTrue(source.power.get(0).energyStored() == broken.fe && data.account(owner).balance() == 0,
                    "Breaking the source refunded FE or credited research");
        }), waitTicks(source.level.getServer(), 20), then(() -> {
            try (source; replacement) {
                helper.assertTrue(data.groundSurveyQuery(jobs -> jobs.job(broken.job.jobId())).orElseThrow().equals(broken.job)
                        && source.power.get(0).energyStored() == broken.fe, "The orphaned job progressed or was paid");
                broken.menu.broadcastChanges();
                helper.assertTrue(broken.menu.status() == ResearchStatus.SOURCE_MISSING, "Orphaned job read " + broken.menu.status());
                helper.assertTrue(service.intent(replacement.controller, owner, 5), "Owner rebind after a break refused"); replacement.feed(100);
                var rebound = service.work(replacement.controller).orElseThrow();
                helper.assertTrue(rebound.jobId().equals(broken.job.jobId()) && rebound.instanceId().equals(broken.job.instanceId())
                        && rebound.surveyProgress() == 3 && service.advance(replacement.controller)
                        && service.work(replacement.controller).orElseThrow().surveyProgress() == 4, "Rebind after a break lost or reset paid work");
                helper.assertTrue(data.groundSurveyQuery(jobs -> jobs.owned(owner)).size() == 1, "Rebind after a break duplicated the job");
            }
            helper.succeed();
        }));
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
        var anchor = helper.absolutePos(new BlockPos(5 + x, 6, 5));
        // GameTest runs in a normal generated world. Keep observation fixtures above its terrain;
        // an unintended hillside/earlier structure is not the deliberately tested sky obstruction.
        var origin = new BlockPos(anchor.getX(), helper.getLevel().getMaxBuildHeight() - 12, anchor.getZ());
        return new ResearchFixture(helper.getLevel(), origin, processor, facing, owner);
    }
    static void night(ResearchFixture fixture) {
        fixture.level.setWeatherParameters(100_000, 0, false, false); fixture.level.setRainLevel(0); fixture.level.setThunderLevel(0);
        fixture.level.setDayTime(18_000); fixture.level.updateSkyBrightness();
    }
    static void ready(GameTestHelper helper, List<ResearchFixture> fixtures, boolean sky, Runnable action) {
        steps(helper, ticking(fixtures, sky), then(action));
    }
    /** One step of a research GameTest: true moves on to the next step in the same tick, false waits one paced tick. */
    @FunctionalInterface interface Step { boolean run(); }
    /**
     * Registers the test's only {@code onEachTick}, at test start. Vanilla iterates its schedule map while it runs
     * that callback, and scheduling from inside it inserts entries mid-iteration (the W21c CI crash). A step therefore
     * never schedules; it returns false to be called again on the next tick.
     */
    static void steps(GameTestHelper helper, Step... steps) {
        var pacer = new GameTestTickPacer(helper.getLevel().getServer()); int[] next = {0};
        helper.onEachTick(() -> {
            while (next[0] < steps.length) {
                if (!steps[next[0]].run()) { pacer.pace(); return; }
                next[0]++;
            }
        });
    }
    static Step ticking(List<ResearchFixture> fixtures, boolean sky) {
        return () -> fixtures.stream().allMatch(fixture -> fixture.ticking()
                && (fixture.controller.processor() || !sky || fixture.level.canSeeSky(fixture.lens())));
    }
    static Step then(Runnable action) { return () -> { action.run(); return true; }; }
    /** Waits the given number of natural server ticks, counted from the tick on which this step is first reached. */
    static Step waitTicks(net.minecraft.server.MinecraftServer server, int ticks) {
        int[] start = {-1};
        return () -> {
            if (start[0] < 0) { start[0] = server.getTickCount(); }
            return server.getTickCount() - start[0] >= ticks;
        };
    }
    private ResearchGameTests() { }
}
