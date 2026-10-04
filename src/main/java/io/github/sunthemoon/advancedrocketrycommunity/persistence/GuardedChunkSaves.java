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
