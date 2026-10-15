package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ArcCuttingData;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180C16cMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicResourcesCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.research.ResearchPatterns;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** Natural server ticks, production managers, and a disposable remote layout, not an NBT round trip. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicScaleGameTests {
    private static final List<String> KINDS = List.of("lathe", "arc_furnace", "cutting_machine", "crystallizer",
            "chemical_reactor", "precision_laser_etcher", "centrifuge", "observatory", "astrobody_data_processor");
    private static final List<String> RECIPES = List.of("lathe_iron_rod", "arc_furnace_titanium", "cutting_silicon_wafer",
            "crystallizing_dilithium", "chemical_reacting_rocket_fuel", "laser_etching_basic_circuit_plate",
            "centrifuging_enriched_lava");
    private static final int COPIES = 16, IDLE_TICKS = 100, TIMEOUT = 2_400;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("arce_w70_scale",
            Comparator.comparingLong(ChunkPos::toLong));

    @GameTest(template = "empty", batch = "classic_scale", timeoutTicks = TIMEOUT)
    public static void sixteenOfEveryKindStayIdleThenHalfResumeAfterRealUnload(GameTestHelper helper) {
        var run = new ScaleRun(helper);
        try { run.build(); } catch (RuntimeException | Error failure) { run.close(); throw failure; }
        // Register once at setup. The state machine never mutates vanilla's schedule from a callback.
        helper.onEachTick(() -> {
            if (run.closed) { return; }
            try {
                helper.assertTrue(helper.getTick() < TIMEOUT - 2, "Scale stage did not finish: " + run.stage + " "
                        + run.machines.stream().filter(m -> !m.controller.formed() || m.active && m.seededResources != null && !m.complete())
                        .limit(12).map(m -> m.label() + "=" + m.controller.reason() + "/" + m.controller.firstDiagnostic()).toList());
                run.tick();
            } catch (RuntimeException | Error failure) {
                AdvancedRocketryCommunity.LOGGER.info("W70 stopped at stage {}: {}", run.stage, failure.getMessage());
                run.close(); throw failure;
            }
        });
    }

    private static final class ScaleRun implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final ClassicMachineManager manager;
        final GameTestTickPacer pacer;
        final List<Machine> machines = new ArrayList<>();
        final Set<ChunkPos> chunks = new LinkedHashSet<>(), unloaded = new HashSet<>();
        final Map<BlockPos, Snapshot> snapshots = new HashMap<>();
        final List<Long> idleCosts = new ArrayList<>(), activeCosts = new ArrayList<>();
        final Consumer<ChunkEvent.Unload> witness = this::unloaded;
        final long previousDay;
        final boolean previousRain, previousThunder;
        ClassicMachineManager.WorkCounters counters;
        long previousNanos, previousTicks, searches, candidates, revalidations;
        int stage, stageTick;
        boolean closed, listening;

        ScaleRun(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel(); manager = ClassicRuntime.manager(level);
            pacer = new GameTestTickPacer(level.getServer()); previousDay = level.getDayTime();
            previousRain = level.isRaining(); previousThunder = level.isThundering();
        }
        void build() {
            for (int kind = 0; kind < KINDS.size(); kind++) {
                for (int copy = 0; copy < COPIES; copy++) {
                    int cell = kind * 4 + copy / 4;
                    // Four independent footprints per chunk; all chunks are far outside the GameTest tickets.
                    var chunk = new ChunkPos(1_600 + cell % 6, 1_600 + cell / 6);
                    if (chunks.add(chunk)) { ticket(chunk, true); level.getChunk(chunk.x, chunk.z); }
                    BlockPos origin = new BlockPos(chunk.getMinBlockX() + 3 + copy % 2 * 8,
                            level.getMaxBuildHeight() - 12, chunk.getMinBlockZ() + 2 + copy / 2 % 2 * 8);
                    var machine = new Machine(this, kind, copy, origin); machines.add(machine); machine.build();
                }
            }
            helper.assertTrue(machines.size() == 144 && chunks.size() == 36, "Scale fixture cardinality differs");
            counters = manager.beginCountingForTest(machines.stream().map(m -> m.origin).collect(java.util.stream.Collectors.toSet()));
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ChunkEvent.Unload.class, witness);
            listening = true;
        }
        void ticket(ChunkPos chunk, boolean add) {
            if (add) { level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk); }
            else { level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, chunk); }
        }
        boolean ready() {
            return machines.stream().allMatch(m -> m.controller.formed() && level.isPositionEntityTicking(m.origin)
                    && (m.kind != 7 || level.canSeeSky(m.origin.offset(0, 3, 1))));
        }
        void next(int value) { stage = value; stageTick = level.getServer().getTickCount(); }
        int elapsed() { return level.getServer().getTickCount() - stageTick; }
        void tick() {
            switch (stage) {
                case 0 -> {
                    if (!ready()) { pacer.pace(); return; }
                    next(1);
                }
                case 1 -> {
                    // Let formation-triggered lookups and chunk load notifications drain before measuring steady idle.
                    if (elapsed() < 20) { return; }
                    helper.assertTrue(counters.revalidations >= 144 && counters.searches >= 112 && counters.candidates > 0,
                            "Counters did not witness initial formation and recipe lookup");
                    AdvancedRocketryCommunity.LOGGER.info("W70 formed 16 of each of 9 kinds: initial searches={} candidates={} revalidations={}",
                            counters.searches, counters.candidates, counters.revalidations);
                    baseline(); next(2);
                }
                case 2 -> {
                    helper.assertTrue(counters.ticks == previousTicks + 1, "Idle sample missed a production manager tick");
                    helper.assertTrue(counters.searches == searches && counters.candidates == candidates
                            && counters.revalidations == revalidations, "Idle work at sample " + elapsed()
                            + ": searches=" + (counters.searches - searches) + " candidates=" + (counters.candidates - candidates)
                            + " revalidations=" + (counters.revalidations - revalidations));
                    machines.forEach(Machine::assertIdle); sample(idleCosts);
                    if (elapsed() < IDLE_TICKS) { return; }
                    AdvancedRocketryCommunity.LOGGER.info("W70 idle: {} consecutive ticks, 144 formed controllers, searches=0 candidates=0 revalidations=0", IDLE_TICKS);
                    report("idle", idleCosts);
                    level.setWeatherParameters(100_000, 0, false, false); level.setRainLevel(0); level.setThunderLevel(0);
                    level.setDayTime(18_000); level.updateSkyBrightness();
                    machines.stream().filter(m -> m.active).forEach(Machine::start);
                    SatelliteMissionSavedData.get(level.getServer()).flush(level.getServer());
                    next(3);
                }
                case 3 -> {
                    sample(activeCosts);
                    if (machines.stream().filter(m -> m.active).anyMatch(m -> m.progress() < 8)) { return; }
                    machines.stream().filter(m -> m.active).forEach(m -> {
                        helper.assertTrue(!m.complete(), m.label() + " completed before unload"); m.assertPaid();
                    });
                    chunks.forEach(chunk -> ticket(chunk, false)); next(4);
                }
                case 4 -> {
                    if (unloaded.size() != chunks.size() || chunks.stream().anyMatch(this::loaded)) { pacer.pace(); return; }
                    helper.assertTrue(snapshots.size() == 144, "Missing unload snapshots");
                    for (var m : machines) {
                        m.assertPaid();
                        if (m.active) { m.check(m.progress() >= 8 && !m.complete(), "Not mid-job at actual unload"); }
                    }
                    for (int kind = 0; kind < KINDS.size(); kind++) {
                        int index = kind; var active = machines.stream().filter(m -> m.kind == index && m.active).toList();
                        AdvancedRocketryCommunity.LOGGER.info("W70 unloaded {}: active={} progress={}..{} storedFE={} injectedFE={}",
                                KINDS.get(kind), active.size(), active.stream().mapToInt(Machine::progress).min().orElseThrow(),
                                active.stream().mapToInt(Machine::progress).max().orElseThrow(), active.stream().mapToInt(Machine::energy).sum(),
                                active.stream().mapToInt(m -> m.injected).sum());
                    }
                    next(5);
                }
                case 5 -> {
                    helper.assertTrue(chunks.stream().noneMatch(this::loaded), "Production reloaded a fixture chunk");
                    machines.forEach(m -> snapshots.get(m.origin).assertSame(m, false));
                    if (elapsed() < 20) { return; }
                    chunks.forEach(chunk -> ticket(chunk, true));
                    for (var chunk : chunks) { level.getChunk(chunk.x, chunk.z); }
                    for (var m : machines) {
                        var old = m.controller; var oldHatches = List.copyOf(m.hatches);
                        m.bind();
                        helper.assertTrue(m.controller != old && m.hatches.stream().noneMatch(oldHatches::contains),
                                m.label() + " reused old block entities instead of reading disk");
                        snapshots.get(m.origin).assertSame(m, true);
                    }
                    AdvancedRocketryCommunity.LOGGER.info("W70 native unload/reload: {} chunks, {} controllers, 72 active jobs retained",
                            unloaded.size(), snapshots.size());
                    next(6);
                }
                case 6 -> {
                    if (!ready()) { pacer.pace(); return; }
                    baseline(); next(7);
                }
                case 7 -> {
                    sample(activeCosts);
                    for (var m : machines) {
                        if (m.active) { m.assertPaid(); m.feed(m.totalEnergy); }
                        else { m.assertIdle(); }
                    }
                    if (machines.stream().filter(m -> m.active).anyMatch(m -> !m.complete())) { return; }
                    machines.forEach(Machine::assertCompleted); next(8);
                    snapshots.clear(); machines.forEach(m -> snapshots.put(m.origin, new Snapshot(m)));
                }
                case 8 -> {
                    machines.forEach(m -> { m.assertCompleted(); snapshots.get(m.origin).assertSame(m, true); });
                    if (elapsed() < 40) { return; }
                    report("active", activeCosts);
                    for (int kind = 0; kind < KINDS.size(); kind++) {
                        int index = kind;
                        AdvancedRocketryCommunity.LOGGER.info("W70 completed {}: 8 completed, 8 idle, total consumedFE={}", KINDS.get(kind),
                                machines.stream().filter(m -> m.kind == index).mapToInt(m -> m.injected).sum());
                    }
                    AdvancedRocketryCommunity.LOGGER.info("W70 conservation: 56 recipe batches and 16 research jobs completed exactly once; 72 stayed idle");
                    close(); helper.succeed();
                }
                default -> throw new IllegalStateException("Scale stage");
            }
        }
        boolean loaded(ChunkPos chunk) { return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null; }
        void unloaded(ChunkEvent.Unload event) {
            if (event.getLevel() != level || !chunks.contains(event.getChunk().getPos()) || stage != 4) { return; }
            unloaded.add(event.getChunk().getPos());
            machines.stream().filter(m -> new ChunkPos(m.origin).equals(event.getChunk().getPos())).forEach(m -> {
                snapshots.put(m.origin, new Snapshot(m));
            });
        }
        void baseline() {
            previousTicks = counters.ticks; previousNanos = counters.nanos;
            searches = counters.searches; candidates = counters.candidates; revalidations = counters.revalidations;
        }
        void sample(List<Long> costs) {
            if (counters.ticks > previousTicks) { costs.add((counters.nanos - previousNanos) / (counters.ticks - previousTicks)); }
            previousTicks = counters.ticks; previousNanos = counters.nanos;
        }
        void report(String phase, List<Long> costs) {
            var sorted = costs.stream().sorted().toList();
            AdvancedRocketryCommunity.LOGGER.info("W70 {} manager tick observations (ns): samples={} mean={} p95={} p99={} max={}; no timing assertion",
                    phase, sorted.size(), (long) sorted.stream().mapToLong(Long::longValue).average().orElseThrow(),
                    sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1), sorted.get((int) Math.ceil(sorted.size() * 0.99) - 1), sorted.get(sorted.size() - 1));
        }
        @Override public void close() {
            if (closed) { return; } closed = true;
            if (listening) { MinecraftForge.EVENT_BUS.unregister(witness); }
            manager.endCountingForTest();
            try {
                for (var chunk : chunks) { level.getChunk(chunk.x, chunk.z); }
                for (var m : machines) {
                    level.setBlock(m.origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    if (m.controller != null) { manager.forget(m.controller); }
                    for (BlockPos position : m.cells) { level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); }
                    level.getEntitiesOfClass(ItemEntity.class, new AABB(m.origin).inflate(7)).forEach(ItemEntity::discard);
                }
            } finally {
                chunks.forEach(chunk -> ticket(chunk, false));
                level.setDayTime(previousDay); level.setWeatherParameters(6_000, 6_000, previousRain, previousThunder); level.updateSkyBrightness();
            }
        }
    }

    private static final class Machine {
        final ScaleRun run;
        final int kind, copy;
        final boolean active;
        final BlockPos origin;
        final UUID owner = UUID.randomUUID();
        final List<BlockPos> cells = new ArrayList<>();
        final List<ClassicHatchBlockEntity> hatches = new ArrayList<>();
        ClassicControllerBlockEntity controller;
        ClassicMachineRecipe recipe;
        UUID jobId;
        int injected, totalEnergy;
        CompoundTag seededResources;
        List<ItemStack> expectedItems = List.of();
        Machine(ScaleRun run, int kind, int copy, BlockPos origin) {
            this.run = run; this.kind = kind; this.copy = copy; this.origin = origin; active = copy % 2 == 0;
        }
        String label() { return KINDS.get(kind) + "[" + copy + "]"; }
        void check(boolean value, String message) { run.helper.assertTrue(value, label() + ": " + message); }
        void build() {
            var pattern = kind >= 7 ? ResearchPatterns.definition(kind == 8) : MultiblockPatternJsonCodec.decode((switch (kind) {
                case 0 -> V180ClassicMachineData.lathePattern(); case 1 -> V180ArcCuttingData.arcPattern();
                case 2 -> V180ArcCuttingData.cuttingPattern(); default -> V180C16cMachineData.pattern(KINDS.get(kind));
            }).toString().getBytes(StandardCharsets.UTF_8));
            for (var entry : pattern.cells().entrySet()) {
                var p = entry.getKey(); var a = pattern.controllerAnchor();
                var position = origin.offset(p.x() - a.x(), p.y() - a.y(), p.z() - a.z()); cells.add(position);
                check(new ChunkPos(position).equals(new ChunkPos(origin)), "Footprint crossed the fixture chunk");
                BlockState state = Blocks.AIR.defaultBlockState(); var matcher = entry.getValue();
                if (matcher instanceof PatternMatcher.Controller) {
                    state = block(KINDS.get(kind)).setValue(ClassicControllerBlock.FACING, Direction.NORTH);
                } else if (matcher instanceof PatternMatcher.ExactBlock exact) {
                    state = BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.tryParse(exact.blockId())).defaultBlockState();
                } else if (matcher instanceof PatternMatcher.Port port) {
                    state = block(port.channel().equals("energy_input") ? "power_input_plug" : port.channel() + "_hatch");
                } else if (matcher instanceof PatternMatcher.BlockTag tag) {
                    state = switch (tag.tagId()) {
                        case "advancedrocketrycommunity:motors" -> block("motor");
                        case "advancedrocketrycommunity:coils" -> block("copper_coil");
                        case "advancedrocketrycommunity:arc_furnace_shell_parts" -> block("blast_brick");
                        case "advancedrocketrycommunity:observatory_shell_parts" -> block("machine_casing");
                        case "minecraft:slabs" -> Blocks.STONE_SLAB.defaultBlockState();
                        case "advancedrocketrycommunity:machine_clearance" -> Blocks.AIR.defaultBlockState();
                        default -> throw new IllegalArgumentException("Unmapped fixture tag: " + tag.tagId());
                    };
                }
                if (kind == 1 && p.equals(new PatternPosition(1, 1, 0))) { state = block("item_input_hatch"); }
                if (kind == 1 && p.equals(new PatternPosition(3, 1, 0))) { state = block("item_output_hatch"); }
                if (kind == 7 && p.equals(new PatternPosition(1, 0, 0))) { state = block("power_input_plug"); }
                run.level.setBlock(position, state, Block.UPDATE_ALL);
            }
            bind();
            if (controller instanceof ResearchControllerBlockEntity research) { research.placed(owner); }
            run.manager.observe(controller);
        }
        void bind() {
            controller = (ClassicControllerBlockEntity) run.level.getBlockEntity(origin); check(controller != null, "Controller absent");
            hatches.clear();
            for (BlockPos position : cells) { if (run.level.getBlockEntity(position) instanceof ClassicHatchBlockEntity hatch) { hatches.add(hatch); } }
            hatches.sort(Comparator.comparingLong(h -> h.getBlockPos().asLong()));
        }
        List<ClassicHatchBlockEntity> ports(String channel) { return hatches.stream().filter(h -> h.kind().patternRole().equals(channel)).toList(); }
        IItemHandler items(String channel) { return ports(channel).get(0).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("Missing item capability")); }
        IFluidHandler fluids(ClassicHatchBlockEntity hatch) { return hatch.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(() -> new IllegalStateException("Missing fluid capability")); }
        CompoundTag resources() { return ClassicResourcesCodec.encode(controller.checkpoint().resources()); }
        int energy() { return ports("energy_input").stream().mapToInt(ClassicHatchBlockEntity::energyStored).sum(); }
        GroundSurveyJob researchJob() { return SatelliteMissionSavedData.get(run.level.getServer()).groundSurveyQuery(jobs -> jobs.job(jobId)).orElseThrow(); }
        void start() {
            if (kind >= 7) { startResearch(); }
            else {
                recipe = (ClassicMachineRecipe) run.level.getRecipeManager().byKey(ModIdentity.id(RECIPES.get(kind))).orElseThrow();
                var inputs = recipe.resolveInputs();
                for (int i = 0; i < inputs.size(); i++) {
                    var stack = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(inputs.get(i).get(0))), recipe.rows().itemInputs().get(i).count());
                    if (recipe.retainsLens() && i == 3) { stack.getOrCreateTag().putString("w70_lens", label()); }
                    check(items("item_input").insertItem(i, stack, false).isEmpty(), "Item seed refused");
                }
                for (int i = 0; i < recipe.rows().fluidInputs().size(); i++) {
                    var row = recipe.rows().fluidInputs().get(i);
                    check(fluids(ports("fluid_input").get(i)).fill(new FluidStack(BuiltInRegistries.FLUID.get(row.fluidId()), row.amount()),
                            IFluidHandler.FluidAction.EXECUTE) == row.amount(), "Fluid seed refused");
                }
                var outputs = new ArrayList<ItemStack>();
                for (int i = 0; i < recipe.rows().itemOutputs().size(); i++) { outputs.add(recipe.output(i)); }
                ClassicChanceSelection.choose(recipe.rows().chanceOutputs(), ClassicChanceSelection.seed(
                        controller.checkpoint().resources().machineId(), recipe.rows().jsonSignature(), 1), id -> ClassicMachineRecipe.stackLimit(id, 1))
                        .forEach(row -> outputs.add(new ItemStack(BuiltInRegistries.ITEM.get(row.itemId()), row.count())));
                expectedItems = List.copyOf(outputs); totalEnergy = recipe.rows().processingTicks() * recipe.rows().energyPerTick();
            }
            seededResources = resources(); feed(40 * (kind >= 7 ? ResearchService.ENERGY_PER_TICK : recipe.rows().energyPerTick()));
        }
        void startResearch() {
            var research = (ResearchControllerBlockEntity) controller; var service = ResearchRuntime.service(run.level);
            var data = SatelliteMissionSavedData.get(run.level.getServer());
            if (kind == 8) {
                UUID id = UUID.randomUUID(); long now = run.level.getServer().overworld().getGameTime();
                var source = new GroundSurveyJob.Device(UUID.randomUUID(), run.level.dimension().location(), origin.asLong());
                var instance = new AsteroidInstance(2, UUID.randomUUID(), owner, CelestialIds.EARTH_ID, ModIdentity.id("small_asteroid"),
                        "0123456789abcdef", "0123456789abcdef", 1, List.of(new RewardEntry(ModIdentity.id("raw_rutile"), 1)),
                        data.groundSurveyQuery(jobs -> jobs.previewTime(now)), OptionalLong.empty(), InstanceState.PENDING, InstanceOrigin.ground(id), Optional.empty());
                check(data.groundSurveys(jobs -> jobs.start(new GroundSurveys.Start(id, owner, source, instance, 20, 20,
                        ResearchService.ANALYSIS_TICKS, ResearchService.ENERGY_PER_TICK, ResearchService.CREDIT), now)).changed(), "Analysis input fixture refused");
                for (int tick = 0; tick < 20; tick++) { check(data.groundSurveys(jobs -> jobs.advanceSurvey(id, source, 24_000, now)).changed(), "Analysis input did not become ready"); }
            }
            check(service.intent(research, owner, kind == 7 ? 0 : 4), "Research start refused: " + research.researchStatus());
            jobId = service.work(research).orElseThrow().jobId();
            totalEnergy = (kind == 7 ? ResearchService.SURVEY_TICKS : ResearchService.ANALYSIS_TICKS) * ResearchService.ENERGY_PER_TICK;
        }
        void feed(int limit) {
            for (var hatch : ports("energy_input")) {
                if (injected < limit) { injected += hatch.getCapability(ForgeCapabilities.ENERGY).orElseThrow(() -> new IllegalStateException("Missing energy capability")).receiveEnergy(limit - injected, false); }
            }
        }
        int progress() { return kind < 7 ? controller.progressTicks() : kind == 7 ? researchJob().surveyProgress() : researchJob().analysis().progress(); }
        boolean complete() {
            return kind < 7 ? controller.checkpoint().lastTransaction().isPresent() && controller.checkpoint().job().isEmpty()
                    : kind == 7 ? researchJob().phase() == GroundSurveyJob.Phase.READY : researchJob().analysis().state() == GroundSurveyJob.AnalysisState.CREDITED;
        }
        void assertIdle() {
            check(controller.formed() && !controller.repairRequired() && controller.checkpoint().job().isEmpty()
                    && controller.checkpoint().batchOrdinal() == 0 && controller.checkpoint().lastTransaction().isEmpty() && energy() == 0, "Idle controller changed");
            controller.checkpoint().resources().banks().forEach(bank -> check(bank.key().kind().isItem()
                    ? bank.items().stream().allMatch(ItemStack::isEmpty) : bank.fluid().isEmpty(), "Idle resources changed"));
            if (kind >= 7) { check(SatelliteMissionSavedData.get(run.level.getServer()).groundSurveyQuery(jobs -> jobs.owned(owner)).isEmpty(), "Idle research gained a job"); }
        }
        void assertPaid() {
            if (!active || seededResources == null) { return; }
            int paid = complete() ? totalEnergy : progress() * (kind >= 7 ? ResearchService.ENERGY_PER_TICK : recipe.rows().energyPerTick());
            check(injected == energy() + paid, "FE conservation: injected=" + injected + " stored=" + energy() + " paid=" + paid);
            if (kind < 7 && !complete()) {
                check(controller.consumedEnergy() == paid && resources().equals(seededResources), "In-flight inputs/outputs/paid progress changed");
            }
        }
        void assertCompleted() {
            if (!active) { assertIdle(); return; } assertPaid();
            check(complete() && injected == totalEnergy && energy() == 0, "Job did not complete with exact energy");
            if (kind >= 7) {
                var data = SatelliteMissionSavedData.get(run.level.getServer()); var job = researchJob();
                check(data.groundSurveyQuery(jobs -> jobs.owned(owner)).size() == 1 && data.account(owner).balance() == (kind == 8 ? ResearchService.CREDIT : 0), "Research job/credit duplicated");
                check(data.instance(job.instanceId()).orElseThrow().state() == InstanceState.AVAILABLE, "Research instance lost");
                return;
            }
            check(controller.checkpoint().batchOrdinal() == 1, "Batch ran more than once");
            for (var hatch : ports("item_input")) {
                var bank = controller.checkpoint().resources().bank(hatch.bankKey()).orElseThrow();
                for (int slot = 0; slot < 4; slot++) {
                    var remaining = bank.item(slot);
                    check(recipe.retainsLens() && slot == 3 ? remaining.getCount() == 1 && remaining.is(BuiltInRegistries.ITEM.get(ModIdentity.id("laser_lens")))
                            && remaining.getOrCreateTag().getString("w70_lens").equals(label()) : remaining.isEmpty(), "Input/lens mismatch at slot " + slot);
                }
            }
            for (var hatch : ports("fluid_input")) { check(fluids(hatch).getFluidInTank(0).isEmpty(), "Fluid input remained"); }
            for (var hatch : ports("item_output")) {
                var bank = controller.checkpoint().resources().bank(hatch.bankKey()).orElseThrow();
                for (int slot = 0; slot < 4; slot++) { check(ItemStack.matches(bank.item(slot), slot < expectedItems.size() ? expectedItems.get(slot) : ItemStack.EMPTY), "Output differs at slot " + slot); }
            }
            var expectedFluids = new HashMap<String, Integer>(); var actualFluids = new HashMap<String, Integer>();
            recipe.rows().fluidOutputs().forEach(row -> expectedFluids.merge(row.fluidId().toString(), row.amount(), Integer::sum));
            for (var hatch : ports("fluid_output")) { var fluid = fluids(hatch).getFluidInTank(0);
                if (!fluid.isEmpty()) { actualFluids.merge(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString(), fluid.getAmount(), Integer::sum); }
            }
            check(actualFluids.equals(expectedFluids), "Fluid output conservation differs");
        }
    }

    private record Snapshot(UUID machine, Optional<ClassicCheckpoint.Job> job, long ordinal, Optional<UUID> transaction,
                            CompoundTag resources, Map<BlockPos, Integer> energy, GroundSurveyJob research, CompoundTag identity) {
        Snapshot(Machine m) {
            this(m.controller.controllerState().machineInstanceId(), m.controller.checkpoint().job(), m.controller.checkpoint().batchOrdinal(),
                    m.controller.checkpoint().lastTransaction(), m.resources(), energyOf(m), m.jobId == null ? null : m.researchJob(),
                    m.controller instanceof ResearchControllerBlockEntity r ? r.identity().orElseThrow().encode() : null);
        }
        static Map<BlockPos, Integer> energyOf(Machine m) {
            var energy = new HashMap<BlockPos, Integer>(); m.ports("energy_input").forEach(h -> energy.put(h.getBlockPos(), h.energyStored())); return Map.copyOf(energy);
        }
        void assertSame(Machine m, boolean live) {
            m.check(machine.equals(m.controller.controllerState().machineInstanceId()) && job.equals(m.controller.checkpoint().job())
                    && ordinal == m.controller.checkpoint().batchOrdinal() && transaction.equals(m.controller.checkpoint().lastTransaction())
                    && resources.equals(m.resources()) && energy.equals(energyOf(m)), "Unload/reload or completed idle changed resources/job/progress/FE");
            if (research != null) { m.check(research.equals(m.researchJob()), "Research snapshot changed while unloaded/reloading"); }
            if (identity != null) { m.check(identity.equals(((ResearchControllerBlockEntity) m.controller).identity().orElseThrow().encode()), "Research identity changed"); }
            if (live) { m.check(!m.controller.repairRequired(), "Reload requires repair"); }
        }
    }
    private static BlockState block(String name) { return BuiltInRegistries.BLOCK.get(ModIdentity.id(name)).defaultBlockState(); }
    private ClassicScaleGameTests() { }
}
