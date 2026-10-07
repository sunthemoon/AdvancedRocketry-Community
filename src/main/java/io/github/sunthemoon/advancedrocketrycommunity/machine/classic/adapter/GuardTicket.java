package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** Non-transferable authority derived from actual installed owners, not structural values. */
final class GuardTicket implements AutoCloseable {
    private final ClassicFamilyService service;
    private final ServerLevel level;
    private final ClassicTicketPurpose purpose;
    private final List<Witness> witnesses;
    private final List<LevelChunk> chunks;
    private final Object serviceEpoch;
    private final Object recipeEpoch;
    private final MultiblockPatternCatalog catalog;
    private final long catalogGeneration;
    private final java.util.Map<ClassicOwnerState, Object> expectedValues = new java.util.IdentityHashMap<>();
    private ClassicNativePlan sealedPlan;
    private Publication publication;
    private boolean closed;
    private boolean invalid;

    private GuardTicket(ClassicFamilyService service, ClassicTicketPurpose purpose,
                        List<Witness> witnesses, List<LevelChunk> chunks) {
        this.service = service; this.level = service.level(); this.purpose = purpose;
        this.witnesses = List.copyOf(witnesses); this.chunks = List.copyOf(chunks);
        this.serviceEpoch = service.epoch(); this.recipeEpoch = service.recipeEpoch();
        this.catalog = service.catalog(); this.catalogGeneration = service.catalogGeneration();
        for (Witness witness : witnesses) { expectedValues.put(witness.state(), witness.value()); }
        if (purpose == ClassicTicketPurpose.RECOVERY) {
            for (Witness witness : witnesses) {
                if (witness.value() instanceof ClassicControllerFrame frame) { sealedPlan = frame.machine().nativePlan().orElse(null); }
            }
        }
    }

    static Optional<GuardTicket> acquire(ClassicFamilyService service, ClassicControllerBlockEntity controller,
            List<ClassicHatchBlockEntity> hatches, ClassicTicketPurpose purpose) {
        return acquire(service, controller, hatches, purpose, false);
    }

    static Optional<GuardTicket> acquireAudit(ClassicFamilyService service, ClassicControllerBlockEntity controller,
                                             List<ClassicHatchBlockEntity> hatches) {
        return acquire(service, controller, hatches, ClassicTicketPurpose.LOAD, true);
    }

    private static Optional<GuardTicket> acquire(ClassicFamilyService service, ClassicControllerBlockEntity controller,
            List<ClassicHatchBlockEntity> hatches, ClassicTicketPurpose purpose, boolean audit) {
        if (purpose == null || !service.running() || hatches.size() > 64 || (controller == null && hatches.size() != 1)) {
            return Optional.empty();
        }
        ClassicLoadedWorld world = service.world();
        List<ClassicOwnerState> states = new ArrayList<>();
        if (controller != null) { states.add(controller.ownerState()); }
        for (ClassicHatchBlockEntity hatch : hatches) { states.add(hatch.ownerState()); }
        var unique = new HashSet<BlockEntity>();
        for (ClassicOwnerState state : states) {
            BlockEntity owner = state.owner();
            if (!unique.add(owner) || !installed(world, owner) || state.busy()) { return Optional.empty(); }
            if (purpose != ClassicTicketPurpose.LOAD && (!supported(owner) || state.pending().isPresent()
                    || (purpose != ClassicTicketPurpose.LIFECYCLE && !state.available()))) {
                return Optional.empty();
            }
        }
        if (purpose != ClassicTicketPurpose.LOAD && controller == null) { return Optional.empty(); }
        if (controller != null && !controller.admits(purpose, hatches)) { return Optional.empty(); }
        List<LevelChunk> chunks = new ArrayList<>();
        if (purpose == ClassicTicketPurpose.LOAD || purpose == ClassicTicketPurpose.LIFECYCLE) {
            if (controller != null && (audit || purpose == ClassicTicketPurpose.LIFECYCLE)) {
                for (var position : service.chunkPositions(controller)) {
                    LevelChunk chunk = world.fullChunk(position).orElse(null);
                    if (chunk == null) { return Optional.empty(); }
                    if (!chunks.contains(chunk)) { chunks.add(chunk); }
                }
            }
            for (ClassicOwnerState state : states) {
                LevelChunk chunk = world.fullChunk(state.owner().getBlockPos()).orElse(null);
                if (chunk == null) { return Optional.empty(); }
                if (!chunks.contains(chunk)) { chunks.add(chunk); }
            }
            if (chunks.size() > 4) { return Optional.empty(); }
        } else {
            for (var position : controller.admittedChunkPositions()) {
                LevelChunk chunk = world.fullChunk(position).orElse(null);
                if (chunk == null) { return Optional.empty(); }
                if (!chunks.contains(chunk)) { chunks.add(chunk); }
            }
            if (chunks.isEmpty() || chunks.size() > 4) { return Optional.empty(); }
            for (ClassicOwnerState state : states) {
                if (!chunks.contains(world.fullChunk(state.owner().getBlockPos()).orElse(null))) { return Optional.empty(); }
            }
        }
        states.sort(Comparator.comparingInt((ClassicOwnerState state) -> state.owner().getBlockPos().getY())
                .thenComparingInt(state -> state.owner().getBlockPos().getZ())
                .thenComparingInt(state -> state.owner().getBlockPos().getX()));
        List<Witness> acquired = new ArrayList<>();
        try {
            for (ClassicOwnerState state : states) {
                Object guard = state.acquire();
                if (guard == null) { release(acquired); return Optional.empty(); }
                acquired.add(new Witness(state, state.lifetime(), guard, value(state.owner())));
            }
            GuardTicket ticket = new GuardTicket(service, purpose, acquired, chunks);
            if (!ticket.witnessesStillValid()) { ticket.close(); return Optional.empty(); }
            return Optional.of(ticket);
        } catch (RuntimeException | Error failure) {
            release(acquired); throw failure;
        }
    }

    private static boolean installed(ClassicLoadedWorld world, BlockEntity owner) {
        if (owner instanceof ClassicControllerBlockEntity controller) {
            return world.controller(owner.getBlockPos()).orElse(null) == controller && controller.actualKindMatches();
        }
        if (owner instanceof ClassicHatchBlockEntity hatch) {
            return world.hatch(owner.getBlockPos()).orElse(null) == hatch && hatch.actualKindMatches();
        }
        return false;
    }

    private static boolean supported(BlockEntity owner) {
        return owner instanceof ClassicControllerBlockEntity controller ? controller.frame() != null
                : ((ClassicHatchBlockEntity) owner).view() != null;
    }

    private static Object value(BlockEntity owner) {
        return owner instanceof ClassicControllerBlockEntity controller ? controller.frame()
                : ((ClassicHatchBlockEntity) owner).checkpoint();
    }

    boolean witnessesStillValid() {
        if (closed || invalid) { return false; }
        try {
            if (!service.running() || service.level() != level || service.epoch() != serviceEpoch
                    || service.recipeEpoch() != recipeEpoch || service.catalog() != catalog
                    || service.catalogGeneration() != catalogGeneration || !level.getServer().isSameThread()) {
                invalid = true; return false;
            }
            for (LevelChunk chunk : chunks) {
                var position = chunk.getPos().getWorldPosition();
                // Build-height independent chunk key: use a legal Y for this actual Level.
                position = new net.minecraft.core.BlockPos(position.getX(), level.getMinBuildHeight(), position.getZ());
                if (service.world().fullChunk(position).orElse(null) != chunk) { invalid = true; return false; }
            }
            for (Witness witness : witnesses) {
                if (!local(witness) || !installed(service.world(), witness.state().owner()) || !local(witness)) {
                    invalid = true; return false;
                }
            }
            if (purpose != ClassicTicketPurpose.LOAD && purpose != ClassicTicketPurpose.LIFECYCLE) {
                ClassicControllerBlockEntity controller = null;
                var hatches = new ArrayList<ClassicHatchBlockEntity>();
                for (Witness witness : witnesses) {
                    if (witness.state().owner() instanceof ClassicControllerBlockEntity owner) { controller = owner; }
                    else { hatches.add((ClassicHatchBlockEntity) witness.state().owner()); }
                }
                if (controller == null || !controller.admits(purpose, hatches)) { invalid = true; return false; }
            }
            // Provider callbacks may have retired an earlier participant/service during the loop.
            if (service.epoch() != serviceEpoch || service.recipeEpoch() != recipeEpoch || !service.running()
                    || service.catalog() != catalog || service.catalogGeneration() != catalogGeneration) {
                invalid = true; return false;
            }
            // The final map pass has no observer/provider callback; it catches changes to earlier owners.
            for (Witness witness : witnesses) {
                BlockEntity owner = witness.state().owner();
                boolean kindMatches = owner instanceof ClassicControllerBlockEntity controller
                        ? controller.actualKindMatches() : ((ClassicHatchBlockEntity) owner).actualKindMatches();
                if (!local(witness) || !service.world().containsOwner(owner) || !kindMatches) { invalid = true; return false; }
            }
            for (LevelChunk chunk : chunks) {
                var key = chunk.getPos().getWorldPosition();
                var position = new net.minecraft.core.BlockPos(key.getX(), level.getMinBuildHeight(), key.getZ());
                if (service.world().fullChunk(position).orElse(null) != chunk) { invalid = true; return false; }
            }
            return true;
        } catch (RuntimeException failure) { invalid = true; return false; }
    }

    private boolean local(Witness witness) {
        return witness.state().heldBy(witness.guard()) && witness.state().lifetime() == witness.lifetime()
                && witness.state().owner().getLevel() == level && !witness.state().owner().isRemoved()
                && value(witness.state().owner()) == expectedValues.get(witness.state())
                && (purpose == ClassicTicketPurpose.LOAD || purpose == ClassicTicketPurpose.LIFECYCLE || witness.state().available());
    }

    void requireValid() {
        if (!witnessesStillValid()) { throw new IllegalStateException("Classic operation witnesses unavailable"); }
    }

    boolean isSingleOwnerLoad(ClassicFamilyService expectedService, ClassicOwnerState expectedOwner) {
        requireValid();
        return singleOwnerLoadStillHeld(expectedService, expectedOwner);
    }

    /** Local tail only, after full validation; this never refreshes a captured value. */
    boolean singleOwnerLoadStillHeld(ClassicFamilyService expectedService, ClassicOwnerState expectedOwner) {
        return !closed && !invalid && service == expectedService && expectedService != null
                && purpose == ClassicTicketPurpose.LOAD && witnesses.size() == 1 && chunks.size() == 1
                && witnesses.get(0).state() == expectedOwner && local(witnesses.get(0));
    }

    Object recipeEpoch() { requireValid(); return recipeEpoch; }
    ClassicTicketPurpose purpose() { return purpose; }
    ServerLevel level() { requireValid(); return level; }
    boolean owns(ClassicOwnerState state) { return witnesses.stream().anyMatch(value -> value.state() == state); }
    Object guardFor(ClassicOwnerState state) {
        requireValid(); return witnesses.stream().filter(value -> value.state() == state).findFirst().orElseThrow().guard();
    }

    ClassicControllerBlockEntity controller() {
        requireValid();
        return witnesses.stream().map(value -> value.state().owner()).filter(ClassicControllerBlockEntity.class::isInstance)
                .map(ClassicControllerBlockEntity.class::cast).findFirst().orElseThrow();
    }

    ClassicHatchBlockEntity hatch() {
        requireValid();
        if (witnesses.size() != 1 || !(witnesses.get(0).state().owner() instanceof ClassicHatchBlockEntity hatch)) {
            throw new IllegalStateException("Not a single-hatch load ticket");
        }
        return hatch;
    }

    /** Only coherent completion/recovery cuts can privately advance this held ticket. */
    void publishCut(ClassicControllerFrame expected, ClassicControllerFrame replacement) {
        try {
            requireValid();
            if (purpose != ClassicTicketPurpose.COMPLETION && purpose != ClassicTicketPurpose.RECOVERY) {
                throw new IllegalStateException("Not a transaction continuation");
            }
            ClassicControllerBlockEntity owner = controller();
            if (owner.frame() != expected) { throw new IllegalStateException("Unexpected controller cut"); }
            ClassicCheckpointChecks.transition(expected, replacement, sealedPlan, this);
            var output = ClassicFrameCodec.encodeController(replacement, this);
            requireValid();
            publication = new Publication(owner, expected, replacement, output);
            owner.installCut(expected, replacement, output, this);
            expectedValues.put(owner.ownerState(), replacement);
            if (sealedPlan == null) { sealedPlan = replacement.machine().nativePlan().orElse(null); }
            requireValid();
        } catch (RuntimeException | Error failure) {
            invalid = true;
            try { close(); }
            catch (RuntimeException | Error disposal) { failure.addSuppressed(disposal); }
            throw failure;
        } finally { publication = null; }
    }

    boolean acceptsPublication(ClassicControllerBlockEntity owner, ClassicControllerFrame expected,
                               ClassicControllerFrame replacement, net.minecraft.nbt.CompoundTag output) {
        requireValid();
        return publication != null && publication.owner() == owner && publication.before() == expected
                && publication.after() == replacement && publication.output() == output;
    }

    private static void release(List<Witness> values) {
        for (int index = values.size() - 1; index >= 0; index--) {
            Witness value = values.get(index); value.state().release(value.guard());
        }
    }

    @Override public void close() {
        if (!closed) { closed = true; release(witnesses); }
    }

    private record Witness(ClassicOwnerState state, Object lifetime, Object guard, Object value) { }
    private record Publication(ClassicControllerBlockEntity owner, ClassicControllerFrame before,
                               ClassicControllerFrame after, net.minecraft.nbt.CompoundTag output) { }
}
