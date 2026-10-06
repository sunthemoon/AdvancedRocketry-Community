package io.github.sunthemoon.advancedrocketrycommunity.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Save refusal survives ChunkMap unload even when vanilla clears all live BlockEntities. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class GuardedChunkSaves {
    public static final Capability<ChunkSaveDenials> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() { });

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterCapabilitiesEvent event) { event.register(ChunkSaveDenials.class); }
        private Registration() { }
    }

    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<Level> event) {
        if (event.getObject() instanceof ServerLevel) {
            Provider provider = new Provider();
            event.addCapability(ModIdentity.id("guarded_chunk_saves"), provider);
            event.addListener(provider::invalidate);
        }
    }

    private static ChunkSaveDenials state(ServerLevel level) {
        return level.getCapability(CAPABILITY).orElseThrow(
                () -> new IllegalStateException("Server Level chunk-save guard is unavailable"));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeSave(ChunkDataEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var reason = state(level).reason(event.getChunk().getPos().toLong());
            if (reason.isPresent()) { fail(event, reason.orElseThrow()); }
        }
    }

    public static void refuse(ChunkDataEvent.Save event, String reason) {
        if (event.getLevel() instanceof ServerLevel level) {
            state(level).deny(event.getChunk().getPos().toLong(), reason);
        }
        fail(event, reason);
    }

    /** Preserve an observed rejection before serialization can omit the rejected object. */
    public static void recordObservedDenial(ServerLevel level, ChunkPos position, String reason) {
        state(level).deny(position.toLong(), reason);
    }

    /** Call only while a coherent operation is actually held, never for a loaded pending record. */
    public static void defer(ChunkDataEvent.Save event, String reason) {
        beforeSave(event);
        if (reason == null || reason.isBlank() || reason.length() > ChunkSaveDenials.MAX_REASON_CHARS) {
            throw new IllegalArgumentException("Invalid bounded chunk-save deferral reason");
        }
        fail(event, reason);
    }

    /** Stopped follows the writer/Level-close attempt; ordinary unload retains admission state. */
    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        RuntimeException firstFailure = null;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            try {
                state(level).close();
            } catch (RuntimeException failure) {
                if (firstFailure == null) { firstFailure = failure; }
            }
        }
        if (firstFailure != null) { throw firstFailure; }
    }

    private static void fail(ChunkDataEvent.Save event, String reason) {
        event.getChunk().setUnsaved(true);
        throw new IllegalStateException(reason);
    }

    /** Disposable GT fixtures only; a native DedicatedServer has no online guard-reset operation. */
    public static void restoreGameTestFixture(ServerLevel level, ChunkPos position) {
        if (!(level.getServer() instanceof GameTestServer)) {
            throw new IllegalStateException("Save-guard fixture cleanup requires the actual GameTestServer");
        }
        state(level).restoreFixture(position.toLong());
    }

    private static final class Provider implements ICapabilityProvider {
        private final ChunkSaveDenials state = new ChunkSaveDenials();
        private final LazyOptional<ChunkSaveDenials> capability = LazyOptional.of(() -> state);
        private void invalidate() { state.close(); capability.invalidate(); }
        @Override @NotNull public <T> LazyOptional<T> getCapability(@NotNull Capability<T> requested, @Nullable Direction side) {
            return CAPABILITY.orEmpty(requested, capability);
        }
    }

    private GuardedChunkSaves() { }
}
