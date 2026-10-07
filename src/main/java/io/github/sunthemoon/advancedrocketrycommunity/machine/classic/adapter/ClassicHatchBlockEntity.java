package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

/** Physical five-kind owner. Native resource facades are a separate guarded consumer. */
public final class ClassicHatchBlockEntity extends BlockEntity {
    private final ClassicHatchKind kind;
    private final ClassicOwnerState storage = new ClassicOwnerState(this);
    private ClassicHatchCheckpoint checkpoint;
    private ClassicHatchDecode rejected;
    private CompoundTag encoded;
    private boolean preparingLoad;
    private LoadJoinCandidate loadJoinCandidate;
    private ClassicFamilyService joinedService;
    private Object joinedLifetime;
    private Object joinedStorageEpoch;

    private record LoadJoinCandidate(ClassicFamilyService service, Object serviceEpoch, Object recipeEpoch,
            MultiblockPatternCatalog catalog, long generation, Object lifetime, Object storageEpoch,
            ClassicPendingLoad pending, ClassicHatchCheckpoint checkpoint, CompoundTag encoded,
            ClassicHatchDecode rejected) { }

    public ClassicHatchBlockEntity(BlockEntityType<ClassicHatchBlockEntity> type, BlockPos position, BlockState state) {
        super(Objects.requireNonNull(type, "type"), position, state);
        kind = ClassicHatchKind.fromBlockId(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                .orElseThrow(() -> new IllegalArgumentException("Not a physical classic hatch block"));
    }

    ClassicOwnerState ownerState() { return storage; }
    ClassicHatchKind kind() { return kind; }
    ClassicHatchCheckpoint checkpoint() { return checkpoint; }
    ClassicHatchState view() { return checkpoint == null ? null : checkpoint.view(); }
    boolean actualKindMatches() { return kind.blockId().equals(BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock())); }
    void retireAccess() { clearLoadJoin(); storage.retire(); }

    void prepareLoaded(ClassicFamilyService service) {
        if (preparingLoad) { return; }
        preparingLoad = true;
        boolean attempted = false, joined = false;
        try {
            if (getLevel() != service.level() || service.world().hatch(getBlockPos()).orElse(null) != this) { return; }
            if (storage.available() && !hasCurrentLoadJoin(service)) { retireAccess(); }
            if (!stageRevalidation(service)) { return; }
            try (GuardTicket ticket = service.access().enterLoad(this).orElse(null)) {
                if (ticket == null) { return; }
                var pending = storage.pending().orElse(null);
                if (pending == null && (checkpoint != null || storage.loaded())) { return; }
                attempted = true; clearLoadJoin(); storage.withholdGameplay();
                Object guard = ticket.guardFor(storage), lifetime = storage.lifetime(), epoch = storage.storageEpoch();
                if (pending != null) {
                    if (!pending.ownedBy(storage)) { throw new IllegalStateException("Foreign pending hatch load"); }
                    if (pending.requiresSaveRefusal()) { observeDenial(); return; }
                    var result = ClassicFrameCodec.decodeHatch(pending.roots().forDecode(ticket), kind, getBlockPos(), ticket);
                    ticket.requireValid();
                    if (storage.pending().orElse(null) != pending || checkpoint != null || encoded != null) {
                        throw new IllegalStateException("Hatch pending selection changed");
                    }
                    if (result.supported().isEmpty()) {
                        rejected = result;
                        if (result.requiresSaveRefusal()) { observeDenial(); return; }
                    } else {
                        var candidate = new ClassicHatchCheckpoint(result.supported().orElseThrow(), Optional.empty());
                        CompoundTag output = ClassicFrameCodec.encodeHatchCheckpoint(candidate, ticket);
                        ticket.requireValid(); storage.decoded(pending, ticket);
                        checkpoint = candidate; encoded = output; rejected = null;
                    }
                } else {
                    // Known placement belongs to the real Root-owned LOAD predicate, not this branch.
                    var candidate = new ClassicHatchCheckpoint(new ClassicHatchState(kind, Optional.empty(), Optional.empty(),
                            kind == ClassicHatchKind.POWER_INPUT ? OptionalInt.of(0) : OptionalInt.empty()), Optional.empty());
                    CompoundTag output = ClassicFrameCodec.encodeHatchCheckpoint(candidate, ticket);
                    ticket.requireValid(); checkpoint = candidate; encoded = output; rejected = null;
                }
                if (!storage.heldBy(guard) || storage.lifetime() != lifetime || storage.storageEpoch() != epoch) {
                    throw new IllegalStateException("Hatch private LOAD publication changed");
                }
                loadJoinCandidate = new LoadJoinCandidate(service, service.epoch(), service.recipeEpoch(), service.catalog(),
                        service.catalogGeneration(), lifetime, epoch, storage.pending().orElse(null), checkpoint, encoded, rejected);
            }
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

    boolean loadJoinReady(ClassicFamilyService expectedService, GuardTicket ticket) {
        return ticket != null && ticket.isSingleOwnerLoad(expectedService, storage)
                && loadJoinStillCurrent(expectedService, ticket);
    }

    /** Local publication tail only; it does not perform or replace full ticket validation. */
    boolean loadJoinStillCurrent(ClassicFamilyService expectedService, GuardTicket ticket) {
        LoadJoinCandidate candidate = loadJoinCandidate;
        if (!preparingLoad || candidate == null || expectedService == null || candidate.service() != expectedService
                || expectedService.state() != ClassicServiceState.RUNNING || getLevel() != expectedService.level()
                || !expectedService.level().getServer().isSameThread() || expectedService.epoch() != candidate.serviceEpoch()
                || expectedService.recipeEpoch() != candidate.recipeEpoch() || expectedService.catalog() != candidate.catalog()
                || expectedService.catalogGeneration() != candidate.generation() || storage.lifetime() != candidate.lifetime()
                || storage.storageEpoch() != candidate.storageEpoch() || storage.pending().orElse(null) != candidate.pending()
                || checkpoint != candidate.checkpoint() || encoded != candidate.encoded() || rejected != candidate.rejected()
                || ticket == null || !ticket.singleOwnerLoadStillHeld(expectedService, storage)) { return false; }
        if (candidate.pending() != null) {
            return candidate.pending().ownedBy(storage) && !candidate.pending().requiresSaveRefusal()
                    && rejected != null && rejected.supported().isEmpty() && !rejected.requiresSaveRefusal()
                    && checkpoint == null && encoded == null && !storage.available();
        }
        return checkpoint != null && encoded != null && rejected == null && ClassicFrameCodec.preflightHatch(encoded);
    }

    private boolean hasCurrentLoadJoin(ClassicFamilyService service) {
        return service != null && joinedService == service && joinedLifetime == storage.lifetime()
                && joinedStorageEpoch == storage.storageEpoch();
    }

    private void clearLoadJoin() { joinedService = null; joinedLifetime = null; joinedStorageEpoch = null; }

    boolean matchesOutgoingCheckpoint(ClassicFamilyService expectedService, CompoundTag outgoing) {
        if (expectedService == null || outgoing == null || !(getLevel() instanceof ServerLevel level)
                || level != expectedService.level() || !level.getServer().isSameThread()
                || expectedService.state() == ClassicServiceState.CLOSED) { return false; }
        Object lifetime = storage.lifetime(), epoch = storage.storageEpoch();
        ClassicPendingLoad pending = storage.pending().orElse(null);
        ClassicHatchCheckpoint selectedCheckpoint = checkpoint; CompoundTag selectedEncoded = encoded;
        ClassicHatchDecode selectedRejected = rejected;
        try (ClassicRawPermit permit = ClassicRawPermit.emission(storage)) {
            permit.requireCurrent();
            boolean matches = pending != null
                    ? pending.ownedBy(storage) && !pending.requiresSaveRefusal()
                        && pending.roots().matchesRetained(outgoing, pending.planRequiresJournal(), permit)
                    : selectedRejected == null && selectedCheckpoint != null && selectedEncoded != null
                        && ClassicFrameCodec.preflightHatch(selectedEncoded)
                        && ClassicRootBundle.matchesEncoded(selectedEncoded, ClassicRootBundle.OwnerType.HATCH, outgoing, permit);
            permit.requireCurrent();
            return matches && storage.lifetime() == lifetime && storage.storageEpoch() == epoch
                    && storage.pending().orElse(null) == pending && checkpoint == selectedCheckpoint && encoded == selectedEncoded
                    && rejected == selectedRejected && getLevel() == level && expectedService.level() == level
                    && level.getServer().isSameThread() && expectedService.state() != ClassicServiceState.CLOSED;
        }
    }

    /** Retained retirement requires a new full LOAD, not revival of an old gameplay witness. */
    private boolean stageRevalidation(ClassicFamilyService service) {
        if (checkpoint == null || storage.available() || storage.pending().isPresent()) { return true; }
        try (GuardTicket ticket = service.access().enterLoad(this).orElse(null)) {
            if (ticket == null) { return false; }
            try (ClassicRawPermit permit = ClassicRawPermit.underLoad(storage, ticket)) {
                CompoundTag parent = new CompoundTag();
                parent.put(ClassicRootBundle.HATCH, Objects.requireNonNull(encoded, "encoded"));
                ClassicPendingLoad incoming = ClassicPendingLoad.capture(parent, ClassicRootBundle.OwnerType.HATCH, permit);
                storage.captured(incoming, permit);
                try { ticket.requireValid(); }
                finally {
                    checkpoint = null; encoded = null; rejected = null; retireAccess();
                }
            }
        }
        return true;
    }

    @Override public void load(CompoundTag parent) {
        ServerLevel actual = getLevel() instanceof ServerLevel level ? level : null;
        if (actual != null && !actual.getServer().isSameThread()) { throw new IllegalStateException("Off-thread installed load"); }
        boolean installed = actual != null && new ClassicLoadedWorld(actual).containsOwner(this);
        ClassicFamilyService service = actual == null ? null : ClassicLevelServices.find(actual).orElse(null);
        try (GuardTicket ticket = installed && service != null ? service.access().enterLoad(this).orElse(null) : null;
             ClassicRawPermit permit = ticket != null ? ClassicRawPermit.underLoad(storage, ticket)
                     : ClassicRawPermit.acquire(storage, ClassicRawPurpose.CAPTURE).orElse(null)) {
            if (permit == null) { throw new IllegalStateException("Reentrant hatch load"); }
            ClassicPendingLoad incoming = ClassicPendingLoad.capture(parent, ClassicRootBundle.OwnerType.HATCH, permit);
            storage.captured(incoming, permit);
            try {
                if (incoming.requiresSaveRefusal() && actual != null) { observeDenial(); }
                if (actual != null && new ClassicLoadedWorld(actual).containsOwner(this) && ticket == null) { return; }
                if (ticket != null) { ticket.requireValid(); }
                super.load(parent);
                permit.requireCurrent(); if (ticket != null) { ticket.requireValid(); }
            } finally {
                // Retain the whole raw checkpoint even if inherited/provider work fails.
                checkpoint = null; encoded = null; rejected = null; retireAccess();
            }
        }
    }

    @Override protected void saveAdditional(CompoundTag parent) {
        if (!(getLevel() instanceof ServerLevel actual) || !actual.getServer().isSameThread()) {
            throw new IllegalStateException("Hatch save has no server-thread Level");
        }
        boolean refused = false;
        try (ClassicRawPermit permit = ClassicRawPermit.emission(storage)) {
            permit.requireCurrent(); super.saveAdditional(parent); permit.requireCurrent();
            if (getLevel() != actual) { throw new IllegalStateException("Hatch save Level changed"); }
            var pending = storage.pending().orElse(null);
            if (pending != null) {
                if (pending.requiresSaveRefusal()) { refused = true; }
                else { pending.emit(parent, permit); }
            } else if (rejected != null) {
                if (rejected.requiresSaveRefusal()) { refused = true; }
                else { rejected.emitRetainedRoots(parent, permit); }
            } else if (encoded != null && ClassicFrameCodec.preflightHatch(encoded)) {
                CompoundTag output = encoded.copy(); ClassicRootBundle.KEYS.forEach(parent::remove);
                parent.put(ClassicRootBundle.HATCH, output); permit.requireCurrent();
            } else { refused = true; }
        } catch (RuntimeException failure) {
            // This is a denial, not successful serialization or a new writable guard.
            refused = true;
        }
        if (refused) { GuardedChunkSaves.recordObservedDenial(actual, new ChunkPos(getBlockPos()), "classic_hatch_raw_refused"); }
    }

    private void observeDenial() {
        if (!(getLevel() instanceof ServerLevel level)) { throw new IllegalStateException("Hatch raw save has no server guard"); }
        GuardedChunkSaves.recordObservedDenial(level, new ChunkPos(getBlockPos()), "classic_hatch_raw_refused");
    }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        // No unguarded native superclass capability can stand in for a family facade.
        if (capability == ForgeCapabilities.ITEM_HANDLER || capability == ForgeCapabilities.FLUID_HANDLER
                || capability == ForgeCapabilities.ENERGY) { return LazyOptional.empty(); }
        return super.getCapability(capability, side);
    }

    @Override public void setLevel(Level level) {
        retireAccess(); try { super.setLevel(level); } finally { retireAccess(); }
    }
    @Override public void onLoad() {
        retireAccess(); super.onLoad();
        if (getLevel() instanceof ServerLevel level) {
            var service = ClassicLevelServices.find(level).orElse(null);
            if (service != null) { prepareLoaded(service); }
        }
    }
    @Override public void onChunkUnloaded() { retireAccess(); super.onChunkUnloaded(); }
    @Override public void invalidateCaps() { retireAccess(); super.invalidateCaps(); }
    @Override public void reviveCaps() { retireAccess(); super.reviveCaps(); }
    @Override public void setRemoved() { retireAccess(); super.setRemoved(); }

    public boolean blocksOrdinaryRemoval() {
        return storage.busy() || storage.pending().isPresent() || rejected != null || checkpoint == null
                || checkpoint.handoff().isPresent() || checkpoint.view().binding().isPresent()
                || checkpoint.view().energy().orElse(0) > 0;
    }
}
