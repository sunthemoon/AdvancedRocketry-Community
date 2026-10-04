package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Transient Level-owned scheduling; no persisted queue, static world collection or chunk forcing. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class TankTransfers {
    public static final Capability<LevelQueue> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() { });

    public static final class LevelQueue {
        private final TankTransferQueue<PressurizedTankBlockEntity> queue = new TankTransferQueue<>();
        public int queued() { return queue.size(); }
    }

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterCapabilitiesEvent event) { event.register(LevelQueue.class); }
        private Registration() { }
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Level> event) {
        if (event.getObject() instanceof ServerLevel) {
            Provider provider = new Provider();
            event.addCapability(ModIdentity.id("tank_transfers"), provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            level.getCapability(CAPABILITY).ifPresent(state ->
                    state.queue.tick(level.getGameTime(), tank -> tank.pullFromAbove(level.getGameTime())));
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            level.getCapability(CAPABILITY).ifPresent(state -> state.queue.clear());
        }
    }

    static void offer(ServerLevel level, PressurizedTankBlockEntity tank, long dirtySince) {
        level.getCapability(CAPABILITY).ifPresent(state -> state.queue.offer(tank, level.getGameTime(), dirtySince));
    }

    static void cancel(PressurizedTankBlockEntity tank) {
        if (tank.getLevel() instanceof ServerLevel level) {
            level.getCapability(CAPABILITY).ifPresent(state -> state.queue.remove(tank));
        }
    }

    private static final class Provider implements ICapabilityProvider {
        private final LevelQueue state = new LevelQueue();
        private final LazyOptional<LevelQueue> capability = LazyOptional.of(() -> state);
        private void invalidate() { state.queue.clear(); capability.invalidate(); }
        @Override @NotNull
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> requested, @Nullable Direction side) {
            return CAPABILITY.orEmpty(requested, capability);
        }
    }

    private TankTransfers() { }
}
