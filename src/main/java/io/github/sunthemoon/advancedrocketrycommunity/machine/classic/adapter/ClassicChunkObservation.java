package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.ChunkDataEvent;

/** Actual chunk-owned raw expectations and qualified identities, never a checkpoint factory. */
final class ClassicChunkObservation implements AutoCloseable {
    private static final Capability<ClassicChunkObservation> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() { });
    private final LevelChunk chunk;
    private final ServerLevel level;
    private final int chunkX;
    private final int chunkZ;
    private final int minimumY;
    private final int height;
    private final long capacity;
    private final Map<ClassicChunkRecords.Position, RetainedOwner> retained = new HashMap<>();
    private volatile Capture capture;
    private volatile boolean closed;

    private ClassicChunkObservation(LevelChunk chunk, ServerLevel level) {
        this.chunk = chunk; this.level = level;
        chunkX = chunk.getPos().x; chunkZ = chunk.getPos().z;
        minimumY = chunk.getMinBuildHeight(); height = chunk.getHeight();
        capacity = Math.multiplyExact(256L, height);
        if (height <= 0 || capacity > Integer.MAX_VALUE || (long) minimumY + height > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid immutable chunk build bounds");
        }
    }

    static void register(RegisterCapabilitiesEvent event) { event.register(ClassicChunkObservation.class); }

    static void attach(AttachCapabilitiesEvent<LevelChunk> event) {
        LevelChunk chunk = event.getObject();
        if (chunk.getLevel() instanceof ServerLevel level) {
            Provider provider = new Provider(new ClassicChunkObservation(chunk, level));
            event.addCapability(ModIdentity.id("classic_chunk_observation"), provider);
            event.addListener(provider::invalidate);
        }
    }

    private static ClassicChunkObservation find(LevelChunk chunk) {
        ClassicChunkObservation found = chunk.getCapability(CAPABILITY).orElse(null);
        return found != null && found.chunk == chunk && !found.closed ? found : null;
    }

    /** Async handoff reads only the event's native metadata and constructor-captured scalar bounds. */
    synchronized void capture(ChunkDataEvent.Load event) {
        if (closed) { throw new IllegalStateException("Closed classic chunk observation"); }
        ClassicChunkRecords.Scan scan = event.getChunk() == chunk
                && event.getStatus() == ChunkStatus.ChunkType.LEVELCHUNK && capture == null
                ? ClassicChunkRecords.read(event.getData(), chunkX, chunkZ, minimumY, height)
                : new ClassicChunkRecords.Scan(true, Map.of());
        capture = new Capture(scan);
        // No Level, map, owner, service or denial-provider lookup occurs on this thread.
    }

    static void captureEvent(ChunkDataEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            ClassicChunkObservation found = find(chunk);
            if (found == null) { throw new IllegalStateException("Classic raw observation provider unavailable"); }
            found.capture(event);
        }
        // Proto family promotion has no admission route here.
    }

    static boolean ownerObservationMatches(ServerLevel level, LevelChunk chunk, BlockEntity owner) {
        if (!installed(level, chunk, owner)) { return false; }
        ClassicChunkObservation found = find(chunk);
        return found != null && found.level == level && installed(level, chunk, owner) && found.rawMatches(owner);
    }

    private synchronized boolean rawMatches(BlockEntity owner) {
        Capture selected = capture;
        return !closed && selected != null && !selected.scan().refused()
                && legal(owner.getBlockPos()) && exactTypeAndKind(owner)
                && typeId(owner).equals(selected.scan().records().get(position(owner.getBlockPos())));
    }

    static boolean recordValidatedLoad(ClassicFamilyService service, ClassicControllerBlockEntity owner,
                                       GuardTicket ticket) {
        return qualifyRecord(service, owner, ticket);
    }

    static boolean recordValidatedLoad(ClassicFamilyService service, ClassicHatchBlockEntity owner,
                                       GuardTicket ticket) {
        return qualifyRecord(service, owner, ticket);
    }

    private static boolean qualifyRecord(ClassicFamilyService service, BlockEntity owner, GuardTicket ticket) {
        if (!running(service, owner) || !ready(service, owner, ticket)) { return false; }
        LevelChunk chunk = service.world().fullChunk(owner.getBlockPos()).orElse(null);
        if (chunk == null || !ready(service, owner, ticket)) { return false; }
        ClassicChunkObservation found = find(chunk);
        if (found == null || found.level != service.level() || !ready(service, owner, ticket)
                || !installed(service.level(), chunk, owner) || !found.rawMatches(owner)
                || !ready(service, owner, ticket) || !running(service, owner)) { return false; }
        // Every provider lookup precedes this private, callback-free insertion.
        return found.recordValidatedLoad(service, chunk, owner, ticket);
    }

    private synchronized boolean recordValidatedLoad(ClassicFamilyService service, LevelChunk actualChunk,
                                                      BlockEntity owner, GuardTicket ticket) {
        if (actualChunk != chunk || service.level() != level || service.state() != ClassicServiceState.RUNNING
                || !level.getServer().isSameThread() || !rawMatches(owner) || !localReady(service, owner, ticket)
                || !installed(level, chunk, owner) || retained.size() > capacity) { return false; }
        ClassicChunkRecords.Position key = position(owner.getBlockPos());
        RetainedOwner previous = retained.get(key);
        if (previous != null) {
            return previous.owner() == owner && previous.service() == service && previous.chunk() == chunk
                    && previous.type().equals(typeId(owner)) && localReady(service, owner, ticket);
        }
        if (retained.size() >= capacity) { return false; }
        RetainedOwner result = new RetainedOwner(owner, service, chunk, typeId(owner));
        if (!localReady(service, owner, ticket) || !rawMatches(owner)) { return false; }
        retained.put(key, result);
        return true;
    }

    static boolean retainedOwnerMatches(ClassicFamilyService service, ClassicControllerBlockEntity owner,
                                        GuardTicket ticket) {
        return retainedMatches(service, owner, ticket);
    }

    static boolean retainedOwnerMatches(ClassicFamilyService service, ClassicHatchBlockEntity owner,
                                        GuardTicket ticket) {
        return retainedMatches(service, owner, ticket);
    }

    private static boolean retainedMatches(ClassicFamilyService service, BlockEntity owner, GuardTicket ticket) {
        if (!running(service, owner) || ticket == null || ticket.purpose() != ClassicTicketPurpose.LOAD
                || !ticket.owns(state(owner)) || !ticket.witnessesStillValid()) {
            return false;
        }
        LevelChunk chunk = service.world().fullChunk(owner.getBlockPos()).orElse(null);
        if (chunk == null || !ticket.witnessesStillValid()) { return false; }
        ClassicChunkObservation found = find(chunk);
        if (found == null || found.level != service.level() || !ticket.witnessesStillValid()
                || !running(service, owner) || !ticket.witnessesStillValid()
                || !installed(service.level(), chunk, owner)) { return false; }
        synchronized (found) {
            RetainedOwner retained = found.retained.get(position(owner.getBlockPos()));
            return found.rawMatches(owner) && retained != null && retained.owner() == owner
                    && retained.chunk() == chunk && retained.service() == service && retained.type().equals(typeId(owner));
        }
    }

    /** Fresh LIFECYCLE selection only; neither a LOAD substitute nor a removal outcome. */
    static EmptyRemovalSelection selectEmptyHatchRemoval(ClassicFamilyService service, ClassicHatchBlockEntity owner,
                                                         GuardTicket ticket) {
        if (ticket == null || !ticket.verifyEmptyRemovalAcquisition(service, owner)) { return null; }
        LevelChunk chunk = service.world().fullChunk(owner.getBlockPos()).orElse(null);
        if (chunk == null || !ticket.verifyEmptyRemovalAcquisition(service, owner)) { return null; }
        ClassicChunkObservation found = find(chunk);
        if (found == null || found.level != service.level() || !ticket.verifyEmptyRemovalAcquisition(service, owner)) {
            return null;
        }
        // The last provider lookup is followed only by current local/nonloading identity checks.
        if (find(chunk) != found || !ticket.emptyRemovalAfterCallbackCurrent(service, owner, chunk)) { return null; }
        synchronized (found) {
            Capture selected = found.capture;
            RetainedOwner retained = found.retained.get(position(owner.getBlockPos()));
            Object heldWitness = ticket.emptyRemovalWitnessIdentity(service, owner, chunk);
            if (heldWitness == null || !found.rawMatches(owner) || found.retained.size() > found.capacity
                    || selected == null || found.capture != selected || retained == null || retained.owner() != owner
                    || retained.service() != service || retained.chunk() != chunk || !retained.type().equals(typeId(owner))) {
                return null;
            }
            return new EmptyRemovalSelection(found, ticket, service, owner, chunk, selected, retained, heldWitness);
        }
    }

    /** No full-ticket call here: that would recursively reenter joined validation. */
    static boolean emptyRemovalSelectionMatches(EmptyRemovalSelection selected, GuardTicket ticket) {
        if (selected == null || selected.ticket != ticket || find(selected.chunk) != selected.observation
                || !ticket.emptyRemovalAfterCallbackCurrent(selected.service, selected.owner, selected.chunk)) {
            return false;
        }
        return selected.locallyCurrent(ticket);
    }

    /** Exact private identities only; no NBT storage or native success flag. */
    static final class EmptyRemovalSelection {
        private final ClassicChunkObservation observation;
        private final GuardTicket ticket;
        private final ClassicFamilyService service;
        private final ClassicHatchBlockEntity owner;
        private final LevelChunk chunk;
        private final Capture capture;
        private final RetainedOwner retained;
        private final Object heldWitness;

        private EmptyRemovalSelection(ClassicChunkObservation observation, GuardTicket ticket, ClassicFamilyService service,
                ClassicHatchBlockEntity owner, LevelChunk chunk, Capture capture, RetainedOwner retained, Object heldWitness) {
            this.observation = observation; this.ticket = ticket; this.service = service; this.owner = owner;
            this.chunk = chunk; this.capture = capture; this.retained = retained; this.heldWitness = heldWitness;
        }

        boolean belongsTo(GuardTicket expectedTicket, ClassicFamilyService expectedService, ClassicHatchBlockEntity expectedOwner,
                          LevelChunk expectedChunk, Object expectedWitness) {
            return ticket == expectedTicket && service == expectedService && owner == expectedOwner
                    && chunk == expectedChunk && heldWitness == expectedWitness;
        }

        /** Only local identity/type checks run under this lock, never provider calls or NBT getters. */
        boolean locallyCurrent(GuardTicket expectedTicket) {
            synchronized (observation) {
                return ticket == expectedTicket && observation.chunk == chunk && observation.level == service.level()
                        && !observation.closed && observation.capture == capture && observation.retained.size() <= observation.capacity
                        && observation.retained.get(position(owner.getBlockPos())) == retained && observation.rawMatches(owner)
                        && retained.owner() == owner && retained.service() == service && retained.chunk() == chunk
                        && retained.type().equals(typeId(owner))
                        && ticket.emptyRemovalWitnessIdentity(service, owner, chunk) == heldWitness;
            }
        }
    }

    private static boolean running(ClassicFamilyService service, BlockEntity owner) {
        if (service == null || owner == null || owner.getLevel() != service.level()
                || !service.level().getServer().isSameThread() || service.state() != ClassicServiceState.RUNNING) {
            return false;
        }
        return ClassicLevelServices.find(service.level()).orElse(null) == service
                && owner.getLevel() == service.level() && service.state() == ClassicServiceState.RUNNING;
    }

    private static ClassicOwnerState state(BlockEntity owner) {
        return owner instanceof ClassicControllerBlockEntity controller ? controller.ownerState()
                : ((ClassicHatchBlockEntity) owner).ownerState();
    }

    private static boolean ready(ClassicFamilyService service, BlockEntity owner, GuardTicket ticket) {
        return ticket != null && (owner instanceof ClassicControllerBlockEntity controller
                ? controller.loadJoinReady(service, ticket)
                : owner instanceof ClassicHatchBlockEntity hatch && hatch.loadJoinReady(service, ticket));
    }

    private static boolean localReady(ClassicFamilyService service, BlockEntity owner, GuardTicket ticket) {
        return ticket != null && (owner instanceof ClassicControllerBlockEntity controller
                ? controller.loadJoinStillCurrent(service, ticket)
                : owner instanceof ClassicHatchBlockEntity hatch && hatch.loadJoinStillCurrent(service, ticket));
    }

    private static boolean installed(ServerLevel level, LevelChunk chunk, BlockEntity owner) {
        if (owner == null || !level.getServer().isSameThread() || owner.getLevel() != level || owner.isRemoved()
                || chunk.getLevel() != level || chunk.getStatus() != ChunkStatus.FULL
                || level.isOutsideBuildHeight(owner.getBlockPos()) || !exactTypeAndKind(owner)) { return false; }
        BlockPos position = owner.getBlockPos();
        return position.getX() >= -30_000_000 && position.getX() < 30_000_000
                && position.getZ() >= -30_000_000 && position.getZ() < 30_000_000
                && level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == chunk
                && chunk.getBlockEntities().get(position) == owner
                && chunk.getBlockState(position) == owner.getBlockState();
    }

    private static boolean exactTypeAndKind(BlockEntity owner) {
        return owner instanceof ClassicHatchBlockEntity hatch
                ? typeId(owner).equals("advancedrocketrycommunity:classic_hatch") && hatch.actualKindMatches()
                : owner instanceof ClassicControllerBlockEntity controller
                && typeId(owner).equals("advancedrocketrycommunity:lathe")
                && controller.machineKind().toString().equals("advancedrocketrycommunity:lathe")
                && controller.actualKindMatches();
    }

    private static String typeId(BlockEntity owner) { return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType()).toString(); }
    private static ClassicChunkRecords.Position position(BlockPos value) {
        return new ClassicChunkRecords.Position(value.getX(), value.getY(), value.getZ());
    }
    private boolean legal(BlockPos value) {
        return (value.getX() >> 4) == chunkX && (value.getZ() >> 4) == chunkZ
                && value.getY() >= minimumY && (long) value.getY() < (long) minimumY + height;
    }

    void inspectOutgoing(ChunkDataEvent.Save event) {
        if (event.getLevel() != level || ClassicSaveProtection.actualChunk(event.getChunk()) != chunk
                || !level.getServer().isSameThread()) { refuse(event); return; }
        boolean accepted;
        try {
            ClassicFamilyService service = ClassicLevelServices.find(level).orElse(null);
            accepted = service != null && service.state() != ClassicServiceState.CLOSED
                    && matchesOutgoing(event.getData(), service);
        }
        catch (RuntimeException failure) { accepted = false; }
        if (!accepted) { refuse(event); }
    }

    private synchronized boolean matchesOutgoing(CompoundTag parent, ClassicFamilyService service) {
        Capture selected = capture;
        if (closed || selected == null || selected.scan().refused() || service.level() != level
                || service.state() == ClassicServiceState.CLOSED || retained.size() > capacity) { return false; }
        ClassicChunkRecords.Scan outgoing = ClassicChunkRecords.read(parent, chunkX, chunkZ, minimumY, height);
        if (outgoing.refused() || !outgoing.records().equals(selected.scan().records())
                || retained.size() != selected.scan().records().size()) { return false; }
        ListTag entries = (ListTag) parent.get("block_entities");
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = (CompoundTag) entries.get(index);
            ClassicChunkRecords.Position key = new ClassicChunkRecords.Position(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            if (!outgoing.records().containsKey(key)) { continue; }
            RetainedOwner record = retained.get(key);
            if (record == null || record.service() != service || record.chunk() != chunk
                    || record.owner().getLevel() != level || !record.type().equals(outgoing.records().get(key))) { return false; }
            boolean matches = record.owner() instanceof ClassicControllerBlockEntity controller
                    ? controller.matchesOutgoingCheckpoint(service, entry)
                    : record.owner() instanceof ClassicHatchBlockEntity hatch && hatch.matchesOutgoingCheckpoint(service, entry);
            if (!matches || capture != selected || closed || service.state() == ClassicServiceState.CLOSED) { return false; }
        }
        // No live map, old lifetime or FULL lookup is required after ordinary unload.
        return capture == selected && !closed && service.level() == level && service.state() != ClassicServiceState.CLOSED;
    }

    static void inspectEvent(ChunkDataEvent.Save event, LevelChunk chunk) {
        ClassicChunkObservation found;
        try { found = find(chunk); }
        catch (RuntimeException failure) { found = null; }
        if (found == null) { refuse(event); }
        else { found.inspectOutgoing(event); }
    }

    static void retireChunk(ServerLevel level, LevelChunk chunk) {
        if (!level.getServer().isSameThread() || chunk.getLevel() != level) {
            throw new IllegalStateException("Foreign classic chunk retirement");
        }
        ClassicChunkObservation found = find(chunk);
        if (found == null) { throw new IllegalStateException("Classic retirement observation unavailable"); }
        synchronized (found) {
            if (found.closed || found.level != level || found.retained.size() > found.capacity) {
                throw new IllegalStateException("Classic retirement observation invalid");
            }
            for (RetainedOwner record : found.retained.values()) {
                if (record.owner() instanceof ClassicControllerBlockEntity controller) { controller.retireAccess(); }
                else { ((ClassicHatchBlockEntity) record.owner()).retireAccess(); }
            }
            // Keep expectations and exact references until the final save / terminal close.
        }
    }

    private static void refuse(ChunkDataEvent.Save event) { GuardedChunkSaves.refuse(event, "classic_controller_raw_refused"); }

    @Override public synchronized void close() { closed = true; capture = null; retained.clear(); }
    private record Capture(ClassicChunkRecords.Scan scan) { }
    private record RetainedOwner(BlockEntity owner, ClassicFamilyService service, LevelChunk chunk, String type) { }

    private static final class Provider implements ICapabilityProvider {
        private final ClassicChunkObservation observation;
        private final LazyOptional<ClassicChunkObservation> capability;
        private Provider(ClassicChunkObservation observation) {
            this.observation = observation; capability = LazyOptional.of(() -> observation);
        }
        private void invalidate() { try { observation.close(); } finally { capability.invalidate(); } }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> requested, Direction side) {
            return CAPABILITY.orEmpty(requested, capability);
        }
    }
}
