package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicResourcesCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge events and Level capabilities; no global collection of world objects. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class ClassicRuntime {
    private static final Capability<ClassicMachineManager> MANAGER = CapabilityManager.get(new CapabilityToken<>() { });
    // Only immutable datapack definitions are shared. World queues live on their Level.
    private static final MultiblockPatternCatalogManager PATTERNS = new MultiblockPatternCatalogManager();
    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterCapabilitiesEvent event) { event.register(ClassicMachineManager.class); }
        private Registration() { }
    }
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<Level> event) {
        if (event.getObject() instanceof ServerLevel server) {
            var manager = new ClassicMachineManager(server, PATTERNS);
            var capability = LazyOptional.of(() -> manager);
            event.addCapability(ModIdentity.id("classic_machine_runtime"), new ICapabilityProvider() {
                @Override public <T> LazyOptional<T> getCapability(Capability<T> requested, Direction side) { return MANAGER.orEmpty(requested, capability); }
            });
            event.addListener(() -> { manager.close(); capability.invalidate(); });
        }
    }
    public static ClassicMachineManager manager(ServerLevel level) {
        return level.getCapability(MANAGER).orElseThrow(() -> new IllegalStateException("Classic Level runtime unavailable"));
    }
    @SubscribeEvent public static void environments(io.github.sunthemoon.advancedrocketrycommunity.api.environment.ServerEnvironmentReadyEvent event) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) { server.getAllLevels().forEach(level -> manager(level).environments(event.queries())); }
    }
    static void observedUnsafeRoot(ServerLevel level, BlockPos position) {
        GuardedChunkSaves.recordObservedDenial(level, new net.minecraft.world.level.ChunkPos(position),
                "Classic machine root requires backup and offline repair");
    }
    static boolean controllerRootsBounded(CompoundTag entity) {
        return ClassicCheckpointCodec.rootsWritable(entity);
    }
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) { event.addListener(new MultiblockPatternReloadListener(PATTERNS)); }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { event.getServer().getAllLevels().forEach(level -> manager(level).tick()); }
    }
    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) { return; }
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            var snapshots = multi.getReplacedBlockSnapshots();
            if (snapshots.size() > 4_096) { manager(level).reloadRecipes(); return; }
            for (var snapshot : snapshots) { manager(level).changed(snapshot.getPos()); }
        } else { manager(level).changed(event.getPos()); }
    }
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (ClassicRemovalPolicy.blocks(level, event.getPos())) { event.setCanceled(true); }
            else if (!event.isCanceled()) { manager(level).changed(event.getPos()); }
        }
    }
    @SubscribeEvent public static void neighbors(BlockEvent.NeighborNotifyEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            manager(level).changed(event.getPos());
            for (Direction direction : event.getNotifiedSides()) { manager(level).changed(event.getPos().relative(direction)); }
        }
    }
    @SubscribeEvent public static void exploded(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(position -> ClassicRemovalPolicy.blocks(level, position));
            for (BlockPos position : event.getAffectedBlocks()) { manager(level).changed(position); }
        }
    }
    @SubscribeEvent public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var chunk = event.getChunk().getPos(); manager(level).onChunkUnloading(chunk.x, chunk.z);
        }
    }
    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var chunk = event.getChunk().getPos(); manager(level).onChunkChanged(chunk.x, chunk.z);
        }
    }
    @SubscribeEvent public static void synced(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) { event.getPlayerList().getServer().getAllLevels().forEach(level -> manager(level).reloadRecipes()); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        event.getServer().getAllLevels().forEach(level -> manager(level).close()); PATTERNS.clear();
    }
    /** Reuses the existing sticky R-021 chunk guard; it does not discard or normalize a rejected root. */
    @SubscribeEvent public static void save(ChunkDataEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel)) { return; }
        ListTag entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        for (int index = 0; index < entities.size(); index++) {
            CompoundTag entity = entities.getCompound(index);
            String id = entity.getString("id");
            boolean controller = ClassicMachineDefinition.controllerId(id);
            boolean hatch = id.equals(ModIdentity.id("classic_hatch").toString());
            if (controller && !controllerRootsBounded(entity)
                    || hatch && entity.contains(ClassicHatchBlockEntity.ROOT)
                    && !ClassicHatchBlockEntity.bounded(entity.get(ClassicHatchBlockEntity.ROOT))) {
                GuardedChunkSaves.refuse(event, "Classic machine root requires backup and offline repair");
            }
        }
    }
    private ClassicRuntime() { }
}
