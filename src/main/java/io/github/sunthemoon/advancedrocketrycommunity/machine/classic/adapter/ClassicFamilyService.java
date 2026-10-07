package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Level-owned bounded lifecycle/audit state; construction performs no native queries. */
final class ClassicFamilyService implements AutoCloseable {
    private final ServerLevel level;
    private final MultiblockPatternCatalogManager patterns;
    private final ClassicLoadedWorld world;
    private final ClassicAccessCoordinator access;
    private final ClassicLathePatternPolicy patternPolicy = new ClassicLathePatternPolicy();
    private final MultiblockDirtyQueue dirty = new MultiblockDirtyQueue(16_384, 32, 8_192);
    private final MultiblockFootprintIndex footprints = new MultiblockFootprintIndex(16_384, 1_000_000);
    private final Map<MultiblockControllerKey, ClassicControllerBlockEntity> controllers = new HashMap<>();
    private ClassicServiceState state = ClassicServiceState.RUNNING;
    private Object epoch = new Object();
    private Object recipeEpoch = new Object();

    ClassicFamilyService(ServerLevel level, MultiblockPatternCatalogManager patterns) {
        this.level = Objects.requireNonNull(level, "level"); this.patterns = Objects.requireNonNull(patterns, "patterns");
        this.world = new ClassicLoadedWorld(level); this.access = new ClassicAccessCoordinator(this);
    }

    ServerLevel level() { return level; }
    ClassicServiceState state() { return state; }
    Object epoch() { return epoch; }
    Object recipeEpoch() { return recipeEpoch; }
    ClassicLoadedWorld world() { return world; }
    ClassicAccessCoordinator access() { return access; }
    MultiblockPatternCatalog catalog() { return patterns.current().orElse(null); }
    long catalogGeneration() { return patterns.status().generation(); }
    boolean running() {
        return state == ClassicServiceState.RUNNING && level.getServer().isSameThread()
                && ClassicLevelServices.find(level).orElse(null) == this;
    }

    void attach(ClassicControllerBlockEntity owner) {
        if (!running() || owner.getLevel() != level || world.controller(owner.getBlockPos()).orElse(null) != owner) { return; }
        var key = key(owner); ClassicControllerBlockEntity previous = controllers.get(key);
        if (previous != null && previous != owner) { previous.retireAccess(); footprints.remove(key); }
        if (previous == null && controllers.size() >= 16_384) { owner.markDirty(); return; }
        controllers.put(key, owner); enqueue(owner);
    }

    boolean contains(ClassicControllerBlockEntity owner) { return controllers.get(key(owner)) == owner; }

    void detach(ClassicControllerBlockEntity owner) {
        if (owner.getLevel() != level) { return; }
        owner.retireAccess();
        if (state == ClassicServiceState.CLOSED) { return; }
        requireThread(level); var key = key(owner);
        if (controllers.remove(key, owner)) { footprints.remove(key); }
    }

    void blockChanged(ServerLevel actual, BlockPos position) {
        requireThread(actual);
        for (var key : footprints.controllersAt(level.dimension(), position)) { invalidate(key); }
        var owner = world.controller(position); owner.ifPresent(this::attach);
    }

    void chunkUnavailable(ServerLevel actual, ChunkPos position) {
        requireThread(actual);
        for (var key : footprints.controllersInChunk(level.dimension(), position.x, position.z)) { invalidate(key); }
    }

    void tagsOrRecipesReloaded(ServerLevel actual) {
        requireThread(actual); recipeEpoch = new Object(); invalidateAll();
    }

    void patternsReloaded(ServerLevel actual) { requireThread(actual); invalidateAll(); }

    private void invalidateAll() {
        epoch = new Object();
        for (ClassicControllerBlockEntity owner : controllers.values()) { owner.markDirty(); enqueue(owner); }
    }

    private void invalidate(MultiblockControllerKey key) {
        ClassicControllerBlockEntity owner = controllers.get(key);
        if (owner != null) { owner.markDirty(); enqueue(owner); }
    }

    private void enqueue(ClassicControllerBlockEntity owner) {
        owner.markDirty();
        int cells = selected(owner).map(value -> value.size().volume()).orElse(4_096);
        dirty.enqueue(key(owner), cells); // saturation leaves the owner dirty and inaccessible
    }

    void serverTick() {
        if (!running()) { return; }
        dirty.tick(key -> {
            ClassicControllerBlockEntity owner = controllers.get(key);
            if (owner == null || world.controller(key.position()).orElse(null) != owner) { return; }
            owner.prepareLoaded(this);
            if (owner.ownerState().available()) { auditRetained(owner); }
        });
    }

    /** Restores only access witnesses of the exact retained frame; never forms or rebinds a machine. */
    private void auditRetained(ClassicControllerBlockEntity owner) {
        ClassicControllerFrame frame = owner.frame();
        var definition = selected(owner);
        if (!owner.ownerState().available() || frame == null || definition.isEmpty()
                || frame.machine().formationState() != MultiblockFormationState.FORMED) { return; }
        var targets = new ArrayList<ClassicHatchBlockEntity>();
        for (ClassicAssignment assignment : frame.machine().assignments()) {
            var hatch = world.hatch(assignment.position());
            if (hatch.isEmpty()) { return; }
            hatch.orElseThrow().prepareLoaded(this);
            if (!hatch.orElseThrow().ownerState().available() || hatch.orElseThrow().view() == null) { return; }
            targets.add(hatch.orElseThrow());
        }
        if (owner.frame() != frame) { return; }
        try (GuardTicket ticket = GuardTicket.acquireAudit(this, owner, targets).orElse(null)) {
            if (ticket == null) { return; }
            if (!ClassicSaveProtection.retainedOwnerMatches(this, owner, ticket)) { return; }
            ticket.requireValid();
            for (ClassicHatchBlockEntity hatch : targets) {
                if (!ClassicSaveProtection.retainedOwnerMatches(this, hatch, ticket)) { return; }
                ticket.requireValid();
            }
            MultiblockPatternDefinition pattern = definition.orElseThrow();
            Set<BlockPos> cells = cells(owner, pattern); Set<BlockPos> observedHatches = new HashSet<>();
            for (var entry : pattern.cells().entrySet()) {
                BlockPos position = position(owner, pattern, entry.getKey());
                PatternObservation observed = world.observe(new PatternPosition(position.getX(), position.getY(), position.getZ()));
                ticket.requireValid();
                if (!observed.loaded() || !entry.getValue().matches(observed.block().orElseThrow())) { return; }
                PatternMatcher matcher = entry.getValue() instanceof PatternMatcher.OptionalCell optional
                        ? optional.matcher() : entry.getValue();
                var actual = ClassicHatchKind.fromBlockId(net.minecraft.resources.ResourceLocation.tryParse(observed.block().orElseThrow().blockId()));
                if (actual.isPresent()) {
                    if (!(matcher instanceof PatternMatcher.Port port) || !port.channel().equals(actual.orElseThrow().patternRole())) { return; }
                    observedHatches.add(position);
                }
            }
            if (!observedHatches.equals(new HashSet<>(frame.machine().assignments().stream().map(ClassicAssignment::position).toList()))) { return; }
            for (ClassicHatchBlockEntity hatch : targets) { if (!owner.bindingMatches(hatch)) { return; } }
            ticket.requireValid();
            if (!owner.ownerState().available() || targets.stream().anyMatch(hatch -> !hatch.ownerState().available())) { return; }
            if (!footprints.replace(key(owner), cells)) { return; }
            owner.admitRetained(frame, catalog(), catalogGeneration(), chunkPositions(owner), ticket);
        }
    }

    java.util.Optional<MultiblockPatternDefinition> selected(ClassicControllerBlockEntity owner) {
        if (!owner.patternId().toString().equals("advancedrocketrycommunity:lathe")
                || !owner.machineKind().equals(owner.patternId())) { return java.util.Optional.empty(); }
        return patternPolicy.select(patterns).map(ClassicPatternSelection::definition);
    }

    List<BlockPos> chunkPositions(ClassicControllerBlockEntity owner) {
        var selected = selected(owner);
        if (selected.isEmpty() || owner.frame() == null) { return List.of(owner.getBlockPos()); }
        Map<ChunkPos, BlockPos> chunks = new HashMap<>();
        for (BlockPos position : cells(owner, selected.orElseThrow())) {
            chunks.putIfAbsent(new ChunkPos(position), position);
            ClassicValueChecks.require(chunks.size() <= 4, "Family footprint chunk bound");
        }
        return List.copyOf(chunks.values());
    }

    private Set<BlockPos> cells(ClassicControllerBlockEntity owner, MultiblockPatternDefinition pattern) {
        var result = new HashSet<BlockPos>();
        for (PatternPosition local : pattern.cells().keySet()) {
            BlockPos position = position(owner, pattern, local);
            ClassicValueChecks.require(world.usable(position), "Family footprint world bounds"); result.add(position);
        }
        return Set.copyOf(result);
    }

    private static BlockPos position(ClassicControllerBlockEntity owner, MultiblockPatternDefinition pattern, PatternPosition local) {
        var origin = owner.getBlockPos();
        var rotation = owner.frame().machine().rotation();
        PatternPosition position = new PatternTransform(rotation, false).localToWorld(local, pattern.controllerAnchor(),
                new PatternPosition(origin.getX(), origin.getY(), origin.getZ()));
        return new BlockPos(position.x(), position.y(), position.z());
    }

    private MultiblockControllerKey key(ClassicControllerBlockEntity owner) { return new MultiblockControllerKey(level.dimension(), owner.getBlockPos()); }
    private void requireThread(ServerLevel actual) {
        if (actual != level || !level.getServer().isSameThread() || state == ClassicServiceState.CLOSED) {
            throw new IllegalStateException("Foreign or closed family service lifecycle");
        }
    }

    void beginQuiesce() {
        requireThread(level); state = ClassicServiceState.QUIESCING; epoch = new Object();
        for (ClassicControllerBlockEntity owner : controllers.values()) { owner.retireAccess(); }
    }

    @Override public void close() {
        if (state == ClassicServiceState.CLOSED) { return; }
        requireThread(level); state = ClassicServiceState.CLOSED; epoch = new Object(); recipeEpoch = new Object();
        for (ClassicControllerBlockEntity owner : controllers.values()) { owner.retireAccess(); }
        controllers.clear(); dirty.clear(); footprints.clear();
    }
}
