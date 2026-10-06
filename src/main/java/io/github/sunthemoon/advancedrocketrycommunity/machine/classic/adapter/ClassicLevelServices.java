package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import java.util.Optional;
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

/** Private, nonserializing Level lifetime. Lookup never creates a service. */
final class ClassicLevelServices {
    private static final Capability<Entry> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() { });

    static void register(RegisterCapabilitiesEvent event) { event.register(Entry.class); }

    static void attach(AttachCapabilitiesEvent<Level> event, MultiblockPatternCatalogManager patterns) {
        if (event.getObject() instanceof ServerLevel level) {
            Provider provider = new Provider(new Entry(level, patterns));
            event.addCapability(ModIdentity.id("classic_level_services"), provider);
            event.addListener(provider::invalidate);
        }
    }

    static Optional<ClassicFamilyService> find(ServerLevel level) {
        return entry(level).map(value -> value.service);
    }

    private static Optional<Entry> entry(ServerLevel level) {
        if (!level.getServer().isSameThread()) { return Optional.empty(); }
        Entry found = level.getCapability(CAPABILITY).orElse(null);
        // Capability resolution is a callback; validate the result after it returns.
        if (!level.getServer().isSameThread() || found == null || found.closed || found.level != level
                || found.service.level() != level || found.service.state() == ClassicServiceState.CLOSED) {
            return Optional.empty();
        }
        return Optional.of(found);
    }

    static void tick(ServerLevel level) {
        Entry found = entry(level).orElse(null);
        if (found == null || found.service.state() != ClassicServiceState.RUNNING) { return; }
        long generation = found.service.catalogGeneration();
        if (found.catalogGeneration != generation) {
            found.service.patternsReloaded(level);
            found.catalogGeneration = generation;
        }
        found.service.serverTick();
    }

    static void beginQuiesce(ServerLevel level) {
        find(level).filter(service -> service.state() == ClassicServiceState.RUNNING)
                .ifPresent(ClassicFamilyService::beginQuiesce);
    }

    static void close(ServerLevel level) { entry(level).ifPresent(Entry::close); }

    private static final class Entry {
        private final ServerLevel level;
        private final ClassicFamilyService service;
        private long catalogGeneration = Long.MIN_VALUE;
        private boolean closed;

        private Entry(ServerLevel level, MultiblockPatternCatalogManager patterns) {
            this.level = level;
            this.service = new ClassicFamilyService(level, patterns);
        }

        private void close() {
            if (closed) { return; }
            closed = true;
            service.close();
        }
    }

    private static final class Provider implements ICapabilityProvider {
        private final Entry entry;
        private final LazyOptional<Entry> capability;

        private Provider(Entry entry) {
            this.entry = entry;
            capability = LazyOptional.of(() -> entry);
        }

        private void invalidate() {
            try { entry.close(); }
            finally { capability.invalidate(); }
        }

        @Override public <T> LazyOptional<T> getCapability(Capability<T> requested, Direction side) {
            return CAPABILITY.orEmpty(requested, capability);
        }
    }

    private ClassicLevelServices() { }
}
