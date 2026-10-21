package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The loaded cargo devices of one server Level by device UUID, fluid and item alike: transient, Level-owned and never
 * saved. The first loaded holder of a UUID keeps it; a second loaded copy (an operator's NBT copy, of either kind) is a
 * duplicate and stays disabled until the first one leaves. The index holds at most one entry per loaded device.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class RocketFluidCargoIndex {
    public static final Capability<Devices> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() { });

    public static final class Devices {
        private final Map<UUID, BlockEntity> holders = new HashMap<>();

        public int size() { return holders.size(); }
    }

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterCapabilitiesEvent event) { event.register(Devices.class); }
        private Registration() { }
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Level> event) {
        if (event.getObject() instanceof ServerLevel) {
            Provider provider = new Provider();
            event.addCapability(ModIdentity.id("rocket_fluid_cargo_devices"), provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            level.getCapability(CAPABILITY).ifPresent(state -> state.holders.clear());
        }
    }

    /** Records {@code loader} as the holder of {@code device}; false when another loaded loader already holds it. */
    static boolean claim(ServerLevel level, UUID device, BlockEntity loader) {
        Devices devices = level.getCapability(CAPABILITY).resolve().orElse(null);
        if (devices == null) {
            return false;
        }
        BlockEntity holder = devices.holders.get(device);
        if (holder == null || holder == loader || holder.isRemoved() || holder.getLevel() != level) {
            devices.holders.put(device, loader);
            return true;
        }
        return false;
    }

    /** Forgets {@code loader} wherever it holds a UUID. */
    static void release(ServerLevel level, BlockEntity loader) {
        level.getCapability(CAPABILITY).ifPresent(state -> state.holders.values().removeIf(holder -> holder == loader));
    }

    /** The loaded holder of {@code device}, for tests and diagnostics. */
    public static BlockEntity holder(ServerLevel level, UUID device) {
        return level.getCapability(CAPABILITY).resolve().map(state -> state.holders.get(device)).orElse(null);
    }

    private static final class Provider implements ICapabilityProvider {
        private final Devices state = new Devices();
        private final LazyOptional<Devices> capability = LazyOptional.of(() -> state);

        private void invalidate() {
            state.holders.clear();
            capability.invalidate();
        }

        @Override @NotNull
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> requested, @Nullable Direction side) {
            return CAPABILITY.orEmpty(requested, capability);
        }
    }

    private RocketFluidCargoIndex() { }
}
