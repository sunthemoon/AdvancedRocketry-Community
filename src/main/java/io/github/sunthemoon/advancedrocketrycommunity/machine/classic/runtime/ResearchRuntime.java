package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.environment.ServerEnvironmentReadyEvent;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server work state belongs to the overworld capability, with explicit Level/server teardown. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class ResearchRuntime {
    private static final Capability<ResearchService> SERVICE = CapabilityManager.get(new CapabilityToken<>() { });
    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ResearchCapabilityRegistration {
        @SubscribeEvent public static void registerResearchCapability(RegisterCapabilitiesEvent event) { event.register(ResearchService.class); }
        private ResearchCapabilityRegistration() { }
    }
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<Level> event) {
        if (event.getObject() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)) {
            var service = new ResearchService(level); var optional = LazyOptional.of(() -> service);
            event.addCapability(ModIdentity.id("ground_research"), new ICapabilityProvider() {
                @Override public <T> LazyOptional<T> getCapability(Capability<T> requested, Direction side) { return SERVICE.orEmpty(requested, optional); }
            });
            event.addListener(() -> { service.close(); optional.invalidate(); });
        }
    }
    public static ResearchService service(ServerLevel level) {
        return level.getServer().overworld().getCapability(SERVICE).orElseThrow(() -> new IllegalStateException("Research runtime unavailable"));
    }
    @SubscribeEvent public static void environments(ServerEnvironmentReadyEvent event) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) { service(server.overworld()).environments(event.queries()); }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { service(event.getServer().overworld()).tick(); }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.getServer().overworld() != null) { service(level).unload(level); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { service(event.getServer().overworld()).close(); }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) { service(level).logout(event.getEntity().getUUID()); }
    }
    @SubscribeEvent public static void save(ChunkDataEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel)) { return; }
        var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        for (int index = 0; index < entities.size(); index++) {
            var entity = entities.getCompound(index); String id = entity.getString("id");
            String root = id.equals(ModIdentity.id("observatory").toString()) ? ResearchControllerBlockEntity.OBSERVATORY_ROOT
                    : id.equals(ModIdentity.id("astrobody_data_processor").toString()) ? ResearchControllerBlockEntity.PROCESSOR_ROOT : null;
            if (root != null && entity.contains(root) && !ResearchIdentity.bounded(entity.get(root))) {
                GuardedChunkSaves.refuse(event, "Research device root requires backup and offline repair");
            }
        }
    }
    private ResearchRuntime() { }
}
