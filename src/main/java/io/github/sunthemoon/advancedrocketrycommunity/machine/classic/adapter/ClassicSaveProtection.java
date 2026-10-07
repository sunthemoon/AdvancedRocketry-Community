package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Native observation/save event bridge. Registration waits for complete physical integration. */
public final class ClassicSaveProtection {
    @SubscribeEvent public static void attachChunk(AttachCapabilitiesEvent<LevelChunk> event) {
        ClassicChunkObservation.attach(event);
    }

    @SubscribeEvent public static void chunkLoad(ChunkDataEvent.Load event) { ClassicChunkObservation.captureEvent(event); }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void chunkSave(ChunkDataEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level)) { return; }
        if (!level.getServer().isSameThread()) {
            event.getChunk().setUnsaved(true);
            throw new IllegalStateException("Off-thread classic native save");
        }
        LevelChunk chunk = actualChunk(event.getChunk());
        if (chunk == null) {
            var records = ClassicChunkRecords.read(event.getData(), event.getChunk().getPos().x,
                    event.getChunk().getPos().z, event.getChunk().getMinBuildHeight(), event.getChunk().getHeight());
            if (records.refused() || !records.records().isEmpty()) { refuse(event); }
            return; // No Proto-to-FULL owner promotion or observation transfer.
        }
        if (chunk.getLevel() != level) { refuse(event); return; }
        // Inspection converts only its own lookup/comparison failures to denial;
        // an exception from the denial carrier itself escapes without retry.
        ClassicChunkObservation.inspectEvent(event, chunk);
    }

    static LevelChunk actualChunk(ChunkAccess chunk) {
        if (chunk instanceof LevelChunk actual) { return actual; }
        if (chunk instanceof ImposterProtoChunk imposter) { return imposter.getWrapped(); }
        return null;
    }

    static boolean ownerObservationMatches(ServerLevel level, LevelChunk chunk, BlockEntity owner) {
        return ClassicChunkObservation.ownerObservationMatches(level, chunk, owner);
    }

    static boolean recordValidatedLoad(ClassicFamilyService service, ClassicControllerBlockEntity owner, GuardTicket ticket) {
        return ClassicChunkObservation.recordValidatedLoad(service, owner, ticket);
    }
    static boolean recordValidatedLoad(ClassicFamilyService service, ClassicHatchBlockEntity owner, GuardTicket ticket) {
        return ClassicChunkObservation.recordValidatedLoad(service, owner, ticket);
    }
    static boolean retainedOwnerMatches(ClassicFamilyService service, ClassicControllerBlockEntity owner, GuardTicket ticket) {
        return ClassicChunkObservation.retainedOwnerMatches(service, owner, ticket);
    }
    static boolean retainedOwnerMatches(ClassicFamilyService service, ClassicHatchBlockEntity owner, GuardTicket ticket) {
        return ClassicChunkObservation.retainedOwnerMatches(service, owner, ticket);
    }

    @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) { return; }
        LevelChunk chunk = actualChunk(event.getChunk());
        if (chunk == null || chunk.getLevel() != level) { throw new IllegalStateException("Foreign classic chunk unload"); }
        ClassicChunkObservation.retireChunk(level, chunk);
        ClassicLevelServices.find(level).ifPresent(service -> service.chunkUnavailable(level, chunk.getPos()));
    }

    @SubscribeEvent public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) { ClassicLevelServices.beginQuiesce(level); }
    }

    @SubscribeEvent public static void serverStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) { ClassicLevelServices.beginQuiesce(level); }
    }

    @SubscribeEvent public static void serverStopped(ServerStoppedEvent event) {
        RuntimeException firstFailure = null;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            try { ClassicLevelServices.close(level); }
            catch (RuntimeException failure) { if (firstFailure == null) { firstFailure = failure; } }
        }
        if (firstFailure != null) { throw firstFailure; }
    }

    public static final class Registration {
        public static void register(RegisterCapabilitiesEvent event) { ClassicChunkObservation.register(event); }
        private Registration() { }
    }

    private static void refuse(ChunkDataEvent.Save event) { GuardedChunkSaves.refuse(event, "classic_controller_raw_refused"); }
    private ClassicSaveProtection() { }
}
