package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicResources;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Native lifetime adapter. Structural values are never accepted through a public setter. */
public abstract class ClassicControllerBlockEntity extends BlockEntity {
    private final ResourceLocation machineKind;
    private final ClassicOwnerState storage = new ClassicOwnerState(this);
    private ClassicControllerFrame frame;
    private CompoundTag encoded;
    private ClassicControllerDecode rejected;
    private ClassicRefusal diagnostic = new ClassicRefusal("unsupported_data", "");
    private boolean dirty = true;
    private MultiblockPatternCatalog admittedCatalog;
    private long admittedGeneration;
    private List<BlockPos> admittedChunks = List.of();
    private ClassicFamilyService attached;
    private boolean preparingLoad;
    private LoadJoinCandidate loadJoinCandidate;
    private ClassicFamilyService joinedService;
    private Object joinedLifetime;
    private Object joinedStorageEpoch;

    private record LoadJoinCandidate(ClassicFamilyService service, Object serviceEpoch, Object recipeEpoch,
            MultiblockPatternCatalog catalog, long generation, Object lifetime, Object storageEpoch,
            ClassicPendingLoad pending, ClassicControllerFrame frame, CompoundTag encoded,
            ClassicControllerDecode rejected) { }

    protected ClassicControllerBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state,
                                           ResourceLocation actualMachineKind) {
        super(type, position, state); ClassicValueChecks.id(actualMachineKind);
        this.machineKind = actualMachineKind;
    }

    protected abstract ResourceLocation patternId();
    protected abstract List<ClassicValidatedRecipe> boundedRecipes();
    final ResourceLocation machineKind() { return machineKind; }
    final ClassicOwnerState ownerState() { return storage; }
    final ClassicControllerFrame frame() { return frame; }
    final boolean actualKindMatches() { return machineKind.equals(BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock())); }
    final List<BlockPos> admittedChunkPositions() { return admittedChunks; }

    final void installCut(ClassicControllerFrame expected, ClassicControllerFrame replacement,
                          CompoundTag output, GuardTicket ticket) {
        ticket.requireValid();
        if (!ticket.acceptsPublication(this, expected, replacement, output)
                || ticket.controller() != this || frame != expected || !ClassicFrameCodec.preflightController(output)) {
            throw new IllegalStateException("Controller cut publication changed");
        }
        // All native work finished before this callback-free, held server-thread publication.
        frame = replacement; encoded = output; storage.retireFacades();
    }

    final void retireAccess() {
        clearLoadJoin(); storage.retire(); dirty = true; admittedCatalog = null; admittedChunks = List.of();
    }
    final void markDirty() { retireAccess(); }
    private void clearAdmission() { storage.retireFacades(); dirty = true; admittedCatalog = null; admittedChunks = List.of(); }

    final boolean bindingMatches(ClassicHatchBlockEntity hatch) {
        if (frame == null || hatch.view() == null || hatch.view().binding().isEmpty()) { return false; }
        var binding = hatch.view().binding().orElseThrow(); var machine = frame.machine();
        if (getLevel() != hatch.getLevel() || binding.controllerLevel() != getLevel().dimension()
                || !binding.controllerPosition().equals(getBlockPos()) || !binding.machineInstanceId().equals(machine.machineId())
                || binding.generation() != machine.generation()) { return false; }
        return machine.assignments().stream().anyMatch(value -> value.position().equals(hatch.getBlockPos())
                && value.kind() == hatch.kind() && value.bankKey().equals(hatch.view().bankKey()));
    }

    final boolean admits(ClassicTicketPurpose purpose, List<ClassicHatchBlockEntity> targets) {
        if (purpose == ClassicTicketPurpose.LOAD) { return true; }
        if (frame == null || !(getLevel() instanceof ServerLevel level)) { return false; }
        if (!frame.machine().ownerLevel().equals(level.dimension().location())
                || !frame.machine().ownerPosition().equals(getBlockPos()) || !frame.machine().machineKind().equals(machineKind)) { return false; }
        ClassicFamilyService service = ClassicLevelServices.find(level).orElse(null);
        if (purpose == ClassicTicketPurpose.LIFECYCLE) { return service != null && service.selected(this).isPresent(); }
        if (dirty || admittedCatalog == null) { return false; }
        if (service == null || service.catalog() != admittedCatalog || service.catalogGeneration() != admittedGeneration) { return false; }
        if (purpose == ClassicTicketPurpose.CAPABILITY || purpose == ClassicTicketPurpose.COMPLETION
                || purpose == ClassicTicketPurpose.RECOVERY) {
            if (frame.machine().formationState() != MultiblockFormationState.FORMED) { return false; }
            if (purpose == ClassicTicketPurpose.CAPABILITY
                    && !ClassicCheckpointChecks.ordinaryCapabilityAllowed(frame.machine(), frame.journal())) { return false; }
            if (targets.size() != frame.machine().assignments().size()) { return false; }
            var observed = new java.util.ArrayList<ClassicAssignment>();
            for (var hatch : targets) {
                if (!bindingMatches(hatch)) { return false; }
                observed.add(new ClassicAssignment(hatch.getBlockPos(), hatch.kind(), hatch.view().bankKey()));
            }
            if (!ClassicCheckpointChecks.completeAssignments(frame.machine().assignments(), observed)) { return false; }
        }
        return true;
    }

    final void admitRetained(ClassicControllerFrame expected, MultiblockPatternCatalog catalog, long generation,
                             List<BlockPos> chunks, GuardTicket ticket) {
        ticket.requireValid();
        ClassicFamilyService service = joinedService;
        if (frame != expected || ticket.controller() != this || chunks.isEmpty() || chunks.size() > 4
                || !storage.available() || !hasCurrentLoadJoin(service)
                || !ClassicSaveProtection.retainedOwnerMatches(service, this, ticket)) {
            throw new IllegalStateException("Retained admission changed");
        }
        ticket.requireValid();
        if (!storage.available() || !hasCurrentLoadJoin(service) || frame != expected) {
            throw new IllegalStateException("Retained LOAD join changed");
        }
        ClassicValueChecks.require(expected.machine().ownerLevel().equals(ticket.level().dimension().location())
                && expected.machine().ownerPosition().equals(getBlockPos()) && expected.machine().machineKind().equals(machineKind),
                "Retained actual owner anchor");
        admittedCatalog = Objects.requireNonNull(catalog, "catalog"); admittedGeneration = generation;
        admittedChunks = List.copyOf(chunks); dirty = false;
    }

    final ClassicValidatedRecipe matchingRecipe(ResourceLocation id, String signature, GuardTicket ticket) {
        ticket.requireValid(); List<ClassicValidatedRecipe> recipes = Objects.requireNonNull(boundedRecipes(), "recipes");
        ticket.requireValid(); ClassicValueChecks.require(recipes.size() <= 1_024, "Recipe catalog bound");
        ClassicValidatedRecipe found = null; var ids = new HashSet<ResourceLocation>();
        for (var recipe : recipes) {
            ClassicValueChecks.require(recipe != null && ids.add(recipe.id()), "Recipe catalog identity");
            if (recipe.id().equals(id)) { found = recipe; }
        }
        ticket.requireValid();
        if (found == null || !found.jsonSignature().equals(signature)) {
            diagnostic = new ClassicRefusal(found == null ? "recipe_missing" : "recipe_changed", id.toString());
            throw new IllegalArgumentException("Retained recipe identity unavailable");
        }
        return found; // replay validates recorded rows; it does not resolve today's tags
    }

    final void prepareLoaded(ClassicFamilyService service) {
        if (preparingLoad) { return; }
        preparingLoad = true;
        boolean attempted = false, joined = false;
        try {
            if (getLevel() != service.level() || service.world().controller(getBlockPos()).orElse(null) != this) { return; }
            if (storage.available() && !hasCurrentLoadJoin(service)) { retireAccess(); }
            if (!stageRevalidation(service)) { return; }
            try (GuardTicket ticket = service.access().enterLoad(this).orElse(null)) {
                if (ticket == null) { return; }
                var pending = storage.pending().orElse(null);
                if (pending == null && (frame != null || storage.loaded())) { return; }
                attempted = true; clearLoadJoin(); storage.withholdGameplay();
                Object guard = ticket.guardFor(storage), lifetime = storage.lifetime(), epoch = storage.storageEpoch();
                if (pending != null) {
                    if (!pending.ownedBy(storage)) { throw new IllegalStateException("Foreign pending controller load"); }
                    if (pending.requiresSaveRefusal()) { observeDenial(); return; }
                    ClassicControllerDecode result = ClassicFrameCodec.decodeController(pending.roots().forDecode(ticket), machineKind,
                            service.level().dimension().location(), getBlockPos(), ticket);
                    ticket.requireValid();
                    if (storage.pending().orElse(null) != pending || frame != null || encoded != null) {
                        throw new IllegalStateException("Controller pending selection changed");
                    }
                    if (result.supported().isEmpty()) {
                        rejected = result;
                        if (result.requiresSaveRefusal()) { observeDenial(); return; }
                    } else {
                        var candidate = result.supported().orElseThrow();
                        CompoundTag output = ClassicFrameCodec.encodeController(candidate, ticket);
                        ticket.requireValid(); storage.decoded(pending, ticket);
                        frame = candidate; encoded = output; rejected = null;
                        diagnostic = candidate.machine().refusal();
                    }
                } else {
                    // The real LOAD predicate must supply the Root-owned known-placement witness.
                    UUID id = UUID.randomUUID();
                    ClassicMachineState machine = new ClassicMachineState(machineKind, id, service.level().dimension().location(),
                            getBlockPos(), 0, PatternRotation.ZERO, MultiblockFormationState.UNFORMED, List.of(), 0,
                            new ClassicRefusal("not_formed", ""), new ClassicProcessFrame.Idle(), Optional.empty(), Optional.empty(), Optional.empty());
                    var candidate = new ClassicControllerFrame(machine, ClassicResources.empty(id), Optional.empty(),
                            new ClassicSignatureMarker(Optional.empty()));
                    CompoundTag output = ClassicFrameCodec.encodeController(candidate, ticket);
                    ticket.requireValid(); frame = candidate; encoded = output; rejected = null;
                    diagnostic = candidate.machine().refusal();
                }
                if (!storage.heldBy(guard) || storage.lifetime() != lifetime || storage.storageEpoch() != epoch) {
                    throw new IllegalStateException("Controller private LOAD publication changed");
                }
                loadJoinCandidate = new LoadJoinCandidate(service, service.epoch(), service.recipeEpoch(), service.catalog(),
                        service.catalogGeneration(), lifetime, epoch, storage.pending().orElse(null), frame, encoded, rejected);
            }
            // The first ticket pins the old frame. It is closed, never refreshed or continued.
            joined = recordCompletedLoad(service);
        } finally {
            if (attempted && !joined) { retireAccess(); }
            loadJoinCandidate = null; preparingLoad = false;
        }
    }

    private boolean recordCompletedLoad(ClassicFamilyService service) {
        if (loadJoinCandidate == null) { return false; }
        try (GuardTicket ticket = service.access().enterLoad(this).orElse(null)) {
            if (ticket == null || !loadJoinReady(service, ticket)
                    || !ClassicSaveProtection.recordValidatedLoad(service, this, ticket)) { return false; }
            ticket.requireValid();
            if (!loadJoinStillCurrent(service, ticket)) { return false; }
            joinedService = service; joinedLifetime = storage.lifetime(); joinedStorageEpoch = storage.storageEpoch();
            if (loadJoinCandidate.pending() == null) { storage.installed(); }
            ticket.requireValid();
            return loadJoinStillCurrent(service, ticket) && hasCurrentLoadJoin(service)
                    && (loadJoinCandidate.pending() != null || storage.available());
        }
    }

    final boolean loadJoinReady(ClassicFamilyService expectedService, GuardTicket ticket) {
        return ticket != null && ticket.isSingleOwnerLoad(expectedService, storage)
                && loadJoinStillCurrent(expectedService, ticket);
    }

    /** Callback-free local tail; the caller must first perform full fresh-ticket validation. */
    final boolean loadJoinStillCurrent(ClassicFamilyService expectedService, GuardTicket ticket) {
        LoadJoinCandidate candidate = loadJoinCandidate;
        if (!preparingLoad || candidate == null || expectedService == null || candidate.service() != expectedService
                || expectedService.state() != ClassicServiceState.RUNNING || getLevel() != expectedService.level()
                || !expectedService.level().getServer().isSameThread() || expectedService.epoch() != candidate.serviceEpoch()
                || expectedService.recipeEpoch() != candidate.recipeEpoch() || expectedService.catalog() != candidate.catalog()
                || expectedService.catalogGeneration() != candidate.generation() || storage.lifetime() != candidate.lifetime()
                || storage.storageEpoch() != candidate.storageEpoch() || storage.pending().orElse(null) != candidate.pending()
                || frame != candidate.frame() || encoded != candidate.encoded() || rejected != candidate.rejected()
                || ticket == null || !ticket.singleOwnerLoadStillHeld(expectedService, storage)) { return false; }
        if (candidate.pending() != null) {
            return candidate.pending().ownedBy(storage) && !candidate.pending().requiresSaveRefusal()
                    && rejected != null && rejected.supported().isEmpty() && !rejected.requiresSaveRefusal()
                    && frame == null && encoded == null && !storage.available();
        }
        return frame != null && encoded != null && rejected == null && ClassicFrameCodec.preflightController(encoded);
    }

    private boolean hasCurrentLoadJoin(ClassicFamilyService service) {
        return service != null && joinedService == service && joinedLifetime == storage.lifetime()
                && joinedStorageEpoch == storage.storageEpoch();
    }

    private void clearLoadJoin() { joinedService = null; joinedLifetime = null; joinedStorageEpoch = null; }

    final boolean matchesOutgoingCheckpoint(ClassicFamilyService expectedService, CompoundTag outgoing) {
        if (expectedService == null || outgoing == null || !(getLevel() instanceof ServerLevel level)
                || level != expectedService.level() || !level.getServer().isSameThread()
                || expectedService.state() == ClassicServiceState.CLOSED) { return false; }
        Object lifetime = storage.lifetime(), epoch = storage.storageEpoch();
        ClassicPendingLoad pending = storage.pending().orElse(null);
        ClassicControllerFrame selectedFrame = frame; CompoundTag selectedEncoded = encoded;
        ClassicControllerDecode selectedRejected = rejected;
        try (ClassicRawPermit permit = ClassicRawPermit.emission(storage)) {
            permit.requireCurrent();
            boolean matches = pending != null
                    ? pending.ownedBy(storage) && !pending.requiresSaveRefusal()
                        && pending.roots().matchesRetained(outgoing, pending.planRequiresJournal(), permit)
                    : selectedRejected == null && selectedFrame != null && selectedEncoded != null
                        && ClassicFrameCodec.preflightController(selectedEncoded)
                        && ClassicRootBundle.matchesEncoded(selectedEncoded, ClassicRootBundle.OwnerType.CONTROLLER, outgoing, permit);
            permit.requireCurrent();
            return matches && storage.lifetime() == lifetime && storage.storageEpoch() == epoch
                    && storage.pending().orElse(null) == pending && frame == selectedFrame && encoded == selectedEncoded
                    && rejected == selectedRejected && getLevel() == level && expectedService.level() == level
                    && level.getServer().isSameThread() && expectedService.state() != ClassicServiceState.CLOSED;
        }
    }

    /** Dirty retained values return to raw ownership before a fresh full LOAD admission. */
    private boolean stageRevalidation(ClassicFamilyService service) {
        if (frame == null || !dirty || storage.pending().isPresent()) { return true; }
        try (GuardTicket ticket = service.access().enterLoad(this).orElse(null)) {
            if (ticket == null) { return false; }
            try (ClassicRawPermit permit = ClassicRawPermit.underLoad(storage, ticket)) {
                ClassicPendingLoad incoming = ClassicPendingLoad.capture(Objects.requireNonNull(encoded, "encoded"),
                        ClassicRootBundle.OwnerType.CONTROLLER, permit);
                storage.captured(incoming, permit);
                try { ticket.requireValid(); }
                finally {
                    // No native callback follows this whole raw handoff. The next LOAD takes fresh witnesses.
                    frame = null; encoded = null; rejected = null; retireAccess();
                }
            }
        }
        return true;
    }

    @Override public final void load(CompoundTag parent) {
        ServerLevel actual = getLevel() instanceof ServerLevel level ? level : null;
        if (actual != null && !actual.getServer().isSameThread()) { throw new IllegalStateException("Off-thread installed load"); }
        boolean installed = actual != null && new ClassicLoadedWorld(actual).containsOwner(this);
        ClassicFamilyService service = actual == null ? null : ClassicLevelServices.find(actual).orElse(null);
        try (GuardTicket ticket = installed && service != null ? service.access().enterLoad(this).orElse(null) : null;
             ClassicRawPermit permit = ticket != null ? ClassicRawPermit.underLoad(storage, ticket)
                     : ClassicRawPermit.acquire(storage, ClassicRawPurpose.CAPTURE).orElse(null)) {
            if (permit == null) { throw new IllegalStateException("Reentrant controller load"); }
            ClassicPendingLoad incoming = ClassicPendingLoad.capture(parent, ClassicRootBundle.OwnerType.CONTROLLER, permit);
            storage.captured(incoming, permit);
            try {
                clearAdmission();
                if (incoming.requiresSaveRefusal() && actual != null) { observeDenial(); }
                if (actual != null && new ClassicLoadedWorld(actual).containsOwner(this) && ticket == null) { return; }
                // No family native decode on an unattached object. The lock covers inherited/provider work.
                if (ticket != null) { ticket.requireValid(); }
                super.load(parent);
                permit.requireCurrent(); if (ticket != null) { ticket.requireValid(); }
            } finally {
                // The retained raw handoff and retirement finish before the storage lease is released.
                frame = null; encoded = null; rejected = null; retireAccess();
            }
        }
    }

    @Override protected final void saveAdditional(CompoundTag parent) {
        // A raw lease does not authorize worker-thread or detached world serialization.
        if (!(getLevel() instanceof ServerLevel actual) || !actual.getServer().isSameThread()) {
            throw new IllegalStateException("Controller save has no server-thread Level");
        }
        boolean refused = false;
        try (ClassicRawPermit permit = ClassicRawPermit.emission(storage)) {
            permit.requireCurrent(); super.saveAdditional(parent); permit.requireCurrent();
            if (getLevel() != actual) { throw new IllegalStateException("Controller save Level changed"); }
            var pending = storage.pending().orElse(null);
            if (pending != null) {
                if (pending.requiresSaveRefusal()) { refused = true; }
                else { pending.emit(parent, permit); }
            } else if (rejected != null) {
                if (rejected.requiresSaveRefusal()) { refused = true; }
                else { rejected.emitRetainedRoots(parent, permit); }
            } else if (encoded != null && ClassicFrameCodec.preflightController(encoded)) {
                CompoundTag output = encoded.copy(); ClassicRootBundle.KEYS.forEach(parent::remove);
                output.getAllKeys().forEach(key -> parent.put(key, output.get(key))); permit.requireCurrent();
            } else { refused = true; }
        } catch (RuntimeException failure) {
            // The whole-chunk consumer refuses this output; neither raw nor typed checkpoints change.
            refused = true;
        }
        // Failure of the real denial carrier escapes; it is never consumed or retried.
        if (refused) { GuardedChunkSaves.recordObservedDenial(actual, new ChunkPos(getBlockPos()), "classic_controller_raw_refused"); }
    }

    private void observeDenial() {
        if (!(getLevel() instanceof ServerLevel level)) { throw new IllegalStateException("Controller raw save has no server guard"); }
        GuardedChunkSaves.recordObservedDenial(level, new ChunkPos(getBlockPos()), "classic_controller_raw_refused");
    }

    private void detachService() {
        ClassicFamilyService previous = attached; attached = null;
        if (previous != null) { previous.detach(this); }
    }

    @Override public final void setLevel(Level level) {
        retireAccess();
        try { detachService(); }
        finally { try { super.setLevel(level); } finally { retireAccess(); } }
    }
    @Override public final void onLoad() {
        retireAccess();
        try { detachService(); } finally { super.onLoad(); }
        if (getLevel() instanceof ServerLevel level) {
            ClassicFamilyService service = ClassicLevelServices.find(level).orElse(null);
            if (service != null) { service.attach(this); attached = service; }
        }
    }
    @Override public final void onChunkUnloaded() {
        retireAccess();
        try { detachService(); } finally { super.onChunkUnloaded(); }
    }
    @Override public final void invalidateCaps() { retireAccess(); super.invalidateCaps(); }
    @Override public final void reviveCaps() { retireAccess(); super.reviveCaps(); }
    @Override public final void setRemoved() {
        retireAccess();
        try { detachService(); } finally { super.setRemoved(); }
    }
    protected final void serverTick(ServerLevel level) {
        if (getLevel() != level || !level.getServer().isSameThread()) { return; }
        ClassicFamilyService service = ClassicLevelServices.find(level).orElse(null);
        if (service != null && (attached != service || !service.contains(this))) {
            if (attached != service) { detachService(); }
            service.attach(this); attached = service;
        }
    }

    public final boolean blocksOrdinaryRemoval() {
        return storage.busy() || storage.pending().isPresent() || rejected != null || frame == null
                || frame.machine().nativePlan().isPresent() || frame.machine().dropQuarantine().isPresent()
                || !frame.resources().banks().isEmpty();
    }

    public final ClassicStatusView statusView() {
        if (frame == null) { return new ClassicStatusView(MultiblockFormationState.UNSUPPORTED_DATA,
                ProcessMachineState.UNSUPPORTED_DATA, 0, 0, 0, diagnostic); }
        var process = frame.machine().process();
        int progress = process instanceof ClassicProcessFrame.Work work ? work.progressTicks() : 0;
        int duration = process instanceof ClassicProcessFrame.Work work ? work.durationTicks() : 0;
        ProcessMachineState state = process instanceof ClassicProcessFrame.Work work ? work.state() : ProcessMachineState.IDLE;
        int energy = 0;
        if (getLevel() instanceof ServerLevel level && level.getServer().isSameThread()) {
            var service = ClassicLevelServices.find(level).orElse(null);
            if (service != null) {
                var targets = new java.util.ArrayList<ClassicHatchBlockEntity>();
                for (var assignment : frame.machine().assignments()) {
                    var hatch = service.world().hatch(assignment.position()).orElse(null);
                    if (hatch == null) { targets.clear(); break; }
                    targets.add(hatch);
                }
                try (GuardTicket ticket = targets.size() == frame.machine().assignments().size()
                        ? GuardTicket.acquire(service, this, targets, ClassicTicketPurpose.CAPABILITY).orElse(null) : null) {
                    if (ticket != null) {
                        for (var assignment : frame.machine().assignments()) {
                            if (assignment.kind() == ClassicHatchKind.POWER_INPUT) {
                                var hatch = service.world().hatch(assignment.position()).orElseThrow();
                                ticket.requireValid(); energy = Math.addExact(energy, hatch.view().energy().orElseThrow());
                            }
                        }
                        ticket.requireValid();
                    }
                }
            }
        }
        return new ClassicStatusView(frame.machine().formationState(), state, progress, duration, energy,
                diagnostic.code().equals("recipe_missing") || diagnostic.code().equals("recipe_changed") ? diagnostic : frame.machine().refusal());
    }
}
