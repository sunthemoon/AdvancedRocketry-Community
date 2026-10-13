package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** One ServerLevel's bounded lifecycle, catalog cache and fair recipe lookup queues. */
public final class ClassicMachineManager {
    private static final int MAX_CONTROLLERS = 16_384;
    private final ServerLevel level;
    private final MultiblockPatternCatalogManager patterns;
    private final MultiblockFootprintIndex footprints = new MultiblockFootprintIndex(MAX_CONTROLLERS, 1_000_000);
    private final MultiblockDirtyQueue dirty = new MultiblockDirtyQueue(MAX_CONTROLLERS, 32, 8_192);
    private final Map<BlockPos, ClassicControllerBlockEntity> controllers = new LinkedHashMap<>();
    private final LinkedHashSet<BlockPos> retry = new LinkedHashSet<>();
    private final LinkedHashSet<BlockPos> processes = new LinkedHashSet<>();
    private final ClassicRecipeSearch<BlockPos, ClassicMachineRecipe> search = new ClassicRecipeSearch<>();
    private final Map<ResourceLocation, List<ClassicMachineRecipe>> catalogs = new HashMap<>();
    private final Map<ClassicMachineRecipe, Optional<ProcessDefinition>> resolved = new HashMap<>();
    private long patternGeneration = -1;
    private boolean closed;
    private io.github.sunthemoon.advancedrocketrycommunity.api.environment.EnvironmentQueries environments;
    void environments(io.github.sunthemoon.advancedrocketrycommunity.api.environment.EnvironmentQueries value) { environments = value; }
    boolean gravityAllows(BlockPos position) {
        double maximum = CommonConfig.crystallizerMaximumGravity();
        if (maximum == 0) { return true; }
        return environments != null && environments.at(level.dimension(), position)
                .map(environment -> environment.gravityMultiplier() <= maximum).orElse(false);
    }
    private boolean sawmillEnabled = CommonConfig.sawmillVanillaWood();

    ClassicMachineManager(ServerLevel level, MultiblockPatternCatalogManager patterns) { this.level = level; this.patterns = patterns; }
    public void observe(ClassicControllerBlockEntity controller) {
        if (closed || controller.getLevel() != level) { return; }
        BlockPos position = controller.getBlockPos();
        if (!controllers.containsKey(position) && controllers.size() == MAX_CONTROLLERS) { controller.catalogUnavailable(); return; }
        controllers.put(position, controller);
        if (index(controller)) { enqueue(controller); wake(position); }
    }
    public void forget(ClassicControllerBlockEntity controller) {
        if (!controllers.remove(controller.getBlockPos(), controller)) { return; }
        var key = key(controller.getBlockPos()); footprints.remove(key);
        search.cancel(key.position()); retry.remove(key.position()); processes.remove(key.position());
    }
    public void changed(BlockPos position) {
        if (closed) { return; }
        for (var key : footprints.controllersAt(level.dimension(), position)) {
            var controller = controllers.get(key.position()); if (controller != null) { enqueue(controller); }
        }
    }
    public void onChunkUnloading(int x, int z) {
        for (var key : footprints.controllersInChunk(level.dimension(), x, z)) {
            var controller = controllers.get(key.position());
            if (controller != null) { controller.chunkUnavailable(new ChunkPos(x, z)); search.cancel(key.position()); }
        }
    }
    public void onChunkChanged(int x, int z) {
        for (var key : footprints.controllersInChunk(level.dimension(), x, z)) {
            var controller = controllers.get(key.position());
            if (controller != null) { controller.chunkAvailable(new ChunkPos(x, z)); enqueue(controller); }
        }
    }
    public void wake(BlockPos position) { if (!closed && controllers.containsKey(position)) { processes.add(position.immutable()); } }
    void resourceChanged(ClassicControllerBlockEntity controller) { search.cancel(controller.getBlockPos()); wake(controller.getBlockPos()); }
    public void reloadRecipes() {
        search.clear(); catalogs.clear(); resolved.clear();
        for (var controller : controllers.values()) { enqueue(controller); }
    }
    private void enqueue(ClassicControllerBlockEntity controller) {
        controller.invalidate(); search.cancel(controller.getBlockPos());
        int cells = pattern(controller).map(pattern -> pattern.size().volume()).orElse(4_096);
        if (dirty.enqueue(key(controller.getBlockPos()), cells) == DirtyEnqueueResult.QUEUE_FULL) { retry.add(controller.getBlockPos()); }
    }
    private Optional<MultiblockPatternDefinition> pattern(ClassicControllerBlockEntity controller) {
        return patterns.current().flatMap(catalog -> catalog.get(controller.definition().pattern().toString()))
                .filter(pattern -> !controller.definition().research() || researchPatternMatches(controller, pattern))
                .filter(pattern -> !pattern.allowMirrorLocalX() && pattern.cells().size() <= 4_096
                        && pattern.allowedRotations().equals(java.util.EnumSet.allOf(
                                io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation.class)));
    }
    private static boolean researchPatternMatches(ClassicControllerBlockEntity controller, MultiblockPatternDefinition pattern) {
        var expected = io.github.sunthemoon.advancedrocketrycommunity.machine.research.ResearchPatterns.definition(
                controller.definition().equals(ClassicMachineDefinition.ASTROBODY_DATA_PROCESSOR));
        return pattern.size().equals(expected.size()) && pattern.controllerAnchor().equals(expected.controllerAnchor())
                && pattern.cells().equals(expected.cells());
    }
    private boolean index(ClassicControllerBlockEntity controller) {
        var cells = pattern(controller).map(controller::footprint).orElseGet(() -> {
            var positions = new java.util.HashSet<>(controller.checkpoint().claimedParts());
            positions.add(controller.getBlockPos()); return java.util.Set.copyOf(positions);
        });
        if (!footprints.replace(key(controller.getBlockPos()), cells)) { controller.invalidate(); controller.catalogUnavailable(); return false; }
        return true;
    }
    public void tick() {
        if (closed || !level.getServer().isSameThread()) { return; }
        boolean enabled = CommonConfig.sawmillVanillaWood();
        if (enabled != sawmillEnabled) { sawmillEnabled = enabled; reloadRecipes(); }
        long generation = patterns.status().generation();
        if (generation != patternGeneration) {
            patternGeneration = generation;
            for (var controller : controllers.values()) { if (index(controller)) { enqueue(controller); } }
        }
        var retries = retry.iterator(); int attempts = 32;
        while (retries.hasNext() && attempts-- > 0) {
            var position = retries.next(); retries.remove(); var controller = controllers.get(position);
            if (controller != null) {
                if (dirty.enqueue(key(position), pattern(controller).map(pattern -> pattern.size().volume()).orElse(4_096))
                        == DirtyEnqueueResult.QUEUE_FULL) { retry.add(position); break; }
            }
        }
        dirty.tick(key -> {
            var controller = controllers.get(key.position());
            if (controller == null || !controller.physical()) { return; }
            var pattern = pattern(controller);
            if (pattern.isEmpty()) { controller.catalogUnavailable(); return; }
            controller.revalidate(level, pattern.orElseThrow()); index(controller); wake(key.position());
        });
        var queued = processes.iterator(); var again = new ArrayList<BlockPos>(); int budget = 256;
        while (queued.hasNext() && budget-- > 0) {
            BlockPos position = queued.next(); queued.remove(); var controller = controllers.get(position);
            if (controller == null || !controller.physical()) { continue; }
            if (controller.definition().research()) { continue; }
            List<ClassicMachineRecipe> catalog = catalog(controller);
            if (catalog == null) { controller.catalogUnavailable(); continue; }
            if (controller.needsLookup() && !search.pending(position)) {
                if (search.enqueue(position, catalog)) { controller.lookupStarted(); }
                else { controller.catalogUnavailable(); }
            }
            var job = controller.checkpoint().job();
            if (job.isPresent()) {
                var recipe = catalog.stream().filter(value -> value.getId().equals(job.orElseThrow().recipe())).findFirst().orElse(null);
                controller.tickProcess(recipe, recipe == null ? null : resolve(recipe));
                again.add(position);
            }
            if (controller.needsLookup()) { again.add(position); }
        }
        processes.addAll(again);
        search.tick((position, recipe) -> {
            var controller = controllers.get(position); var definition = resolve(recipe);
            return controller != null && definition != null && controller.candidateMatches(recipe, definition);
        }, (position, recipe) -> {
            var controller = controllers.get(position);
            if (controller != null) { controller.lookupCompleted(recipe); wake(position); }
        });
    }
    private List<ClassicMachineRecipe> catalog(ClassicControllerBlockEntity controller) {
        var kind = controller.definition().kind();
        if (!catalogs.containsKey(kind)) {
            var recipes = level.getRecipeManager().getAllRecipesFor(controller.definition().recipes().get());
            if (recipes.size() > ClassicRecipeSearch.MAX_RECIPES) {
                AdvancedRocketryCommunity.LOGGER.error("Classic recipe catalog {} disabled: {} recipes exceed {}", kind,
                        recipes.size(), ClassicRecipeSearch.MAX_RECIPES);
            }
            catalogs.put(kind, recipes.size() <= ClassicRecipeSearch.MAX_RECIPES
                    ? recipes.stream().sorted(Comparator.comparing(recipe -> recipe.getId().toString())).toList() : null);
        }
        return catalogs.get(kind);
    }
    private ProcessDefinition resolve(ClassicMachineRecipe recipe) {
        return resolved.computeIfAbsent(recipe, value -> {
            try { return Optional.of(value.definition()); }
            catch (IllegalArgumentException invalidTags) {
                // Resolution is cached once per recipe per reload; diagnostic text is independently bounded.
                String message = String.valueOf(invalidTags.getMessage());
                AdvancedRocketryCommunity.LOGGER.error("Classic recipe {} disabled: {}", value.getId(),
                        message.substring(0, Math.min(message.length(), 160)));
                return Optional.empty();
            }
        }).orElse(null);
    }
    public void close() {
        environments = null;
        closed = true; controllers.values().forEach(ClassicControllerBlockEntity::invalidate);
        controllers.clear(); footprints.clear(); dirty.clear(); retry.clear(); processes.clear(); search.clear(); catalogs.clear(); resolved.clear();
    }
    private MultiblockControllerKey key(BlockPos position) { return new MultiblockControllerKey(level.dimension(), position); }
}
