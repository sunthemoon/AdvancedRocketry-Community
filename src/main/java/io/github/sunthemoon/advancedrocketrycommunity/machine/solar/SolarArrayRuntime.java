package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Level-owned runtime; no static world state, chunk tickets or query-side creation. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class SolarArrayRuntime {
    private static final Capability<SolarArrayManager> MANAGER = CapabilityManager.get(new CapabilityToken<>() { });
    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterCapabilitiesEvent event) { event.register(SolarArrayManager.class); }
        private Registration() { }
    }
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<Level> event) {
        if (event.getObject() instanceof ServerLevel level) {
            var manager = new SolarArrayManager(level);
            var cap = LazyOptional.of(() -> manager);
            event.addCapability(ModIdentity.id("solar_array_runtime"), new ICapabilityProvider() {
                @Override public <T> LazyOptional<T> getCapability(Capability<T> requested, Direction side) { return MANAGER.orEmpty(requested, cap); }
            });
            event.addListener(() -> { manager.close(); cap.invalidate(); });
        }
    }
    public static SolarArrayManager manager(ServerLevel level) {
        return level.getCapability(MANAGER).orElseThrow(() -> new IllegalStateException("Solar array Level runtime unavailable"));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { event.getServer().getAllLevels().forEach(level -> manager(level).tick()); }
    }
    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) { return; }
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            if (multi.getReplacedBlockSnapshots().size() > 4_096) { manager(level).reload(); return; }
            multi.getReplacedBlockSnapshots().forEach(snapshot -> manager(level).changed(snapshot.getPos()));
        } else { manager(level).changed(event.getPos()); }
    }
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) { manager(level).changed(event.getPos()); }
    }
    @SubscribeEvent public static void neighbors(BlockEvent.NeighborNotifyEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            manager(level).changed(event.getPos());
            event.getNotifiedSides().forEach(side -> manager(level).changed(event.getPos().relative(side)));
        }
    }
    @SubscribeEvent public static void exploded(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) { event.getAffectedBlocks().forEach(manager(level)::changed); }
    }
    @SubscribeEvent public static void loaded(ChunkEvent.Load event) { chunk(event, false); }
    @SubscribeEvent public static void unloaded(ChunkEvent.Unload event) { chunk(event, true); }
    private static void chunk(ChunkEvent event, boolean unloading) {
        if (event.getLevel() instanceof ServerLevel level) {
            var pos = event.getChunk().getPos(); manager(level).chunkChanged(pos.x, pos.z, unloading);
        }
    }
    @SubscribeEvent public static void synced(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) { event.getPlayerList().getServer().getAllLevels().forEach(level -> manager(level).reload()); }
    }
    @SubscribeEvent public static void closed(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) { manager(level).close(); }
    }
    private SolarArrayRuntime() { }
}
