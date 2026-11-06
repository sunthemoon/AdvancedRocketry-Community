package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Level-owned, load-only monitor hints; rebuilt on entity joins, never serialized or used to load a chunk. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class RocketMonitorLoadedRockets {
    private static final Capability<LoadedRocketIdentities> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() { });
    private RocketMonitorLoadedRockets() { }

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() { }
        @SubscribeEvent public static void register(RegisterCapabilitiesEvent event) { event.register(LoadedRocketIdentities.class); }
    }

    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<Level> event) {
        if (event.getObject() instanceof ServerLevel) {
            var provider = new Provider();
            event.addCapability(ModIdentity.id("rocket_monitor_loaded_rockets"), provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()
                && event.getEntity() instanceof RocketEntity rocket) {
            rocket.assemblyTransactionId().ifPresent(logical -> level.getCapability(CAPABILITY)
                    .ifPresent(index -> index.joined(rocket.getUUID(), logical)));
        }
    }

    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()
                && event.getEntity() instanceof RocketEntity rocket) {
            level.getCapability(CAPABILITY).ifPresent(index -> index.left(rocket.getUUID()));
        }
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) { level.getCapability(CAPABILITY).ifPresent(LoadedRocketIdentities::clear); }
    }

    public static RocketEntity unique(ServerLevel level, UUID logical) {
        if (!level.getServer().isSameThread()) { return null; }
        var physical = level.getCapability(CAPABILITY).resolve().map(index -> index.unique(logical)).orElse(null);
        return physical != null && level.getEntity(physical) instanceof RocketEntity rocket ? rocket : null;
    }

    public static boolean ambiguous(ServerLevel level, UUID logical) {
        return !level.getServer().isSameThread()
                || level.getCapability(CAPABILITY).resolve().map(index -> index.ambiguous(logical)).orElse(false);
    }

    private static final class Provider implements ICapabilityProvider {
        private final LoadedRocketIdentities state = new LoadedRocketIdentities();
        private final LazyOptional<LoadedRocketIdentities> capability = LazyOptional.of(() -> state);
        private void invalidate() { state.clear(); capability.invalidate(); }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> requested, net.minecraft.core.Direction side) {
            return CAPABILITY.orEmpty(requested, capability);
        }
    }
}
