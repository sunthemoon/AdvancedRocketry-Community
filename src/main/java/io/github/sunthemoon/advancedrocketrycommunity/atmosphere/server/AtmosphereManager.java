package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Server-owned lifecycle boundary; no state survives {@link #clear()}. */
public final class AtmosphereManager {
    private final CelestialEnvironmentService environments;
    private final Map<ResourceKey<Level>, AtmosphereLevelService> levels = new HashMap<>();

    public AtmosphereManager(CelestialEnvironmentService environments) {
        this.environments = Objects.requireNonNull(environments, "environments");
    }

    public void observeVent(ServerLevel level, OxygenVentBlockEntity vent) {
        service(level).observeVent(vent);
    }

    public void removeVent(ServerLevel level, BlockPos position) {
        AtmosphereLevelService service = levels.get(level.dimension());
        if (service != null) {
            service.removeVent(position);
        }
    }

    public void markDirty(ServerLevel level, BlockPos position) {
        service(level).markDirty(position);
    }

    public void onChunkUnload(ServerLevel level, int chunkX, int chunkZ) {
        AtmosphereLevelService service = levels.get(level.dimension());
        if (service != null) {
            service.onChunkUnload(chunkX, chunkZ);
        }
    }

    public void onChunkLoad(ServerLevel level, int chunkX, int chunkZ) {
        AtmosphereLevelService service = levels.get(level.dimension());
        if (service != null) {
            service.onChunkLoad(chunkX, chunkZ);
        }
    }

    public BreathabilityState breathabilityAt(ServerLevel level, BlockPos position) {
        return service(level).breathabilityAt(position);
    }

    public boolean baseAtmosphereBreathable(ServerLevel level) {
        return service(level).baseAtmosphereBreathable();
    }

    public Optional<AtmosphereLevelMetrics> metrics(ResourceKey<Level> levelKey) {
        return Optional.ofNullable(levels.get(levelKey)).map(AtmosphereLevelService::metrics);
    }

    public void tick(MinecraftServer server) {
        Set<ResourceKey<Level>> loaded = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            loaded.add(level.dimension());
            service(level).tick();
        }
        levels.entrySet().removeIf(entry -> {
            if (loaded.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().clear();
            return true;
        });
    }

    public void clear() {
        levels.values().forEach(AtmosphereLevelService::clear);
        levels.clear();
    }

    private AtmosphereLevelService service(ServerLevel level) {
        EnvironmentState state = resolveEnvironment(level);
        AtmosphereLevelService service = levels.computeIfAbsent(
                level.dimension(),
                ignored -> new AtmosphereLevelService(
                        level,
                        state.breathable(),
                        state.vacuum(),
                        CommonConfig.MAX_ATMOSPHERE_VOLUME.get(),
                        CommonConfig.MAX_ATMOSPHERE_INSPECTIONS_PER_TICK.get()
                )
        );
        service.updateEnvironment(state.breathable(), state.vacuum());
        return service;
    }

    private EnvironmentState resolveEnvironment(ServerLevel level) {
        return environments.forLevel(level.dimension())
                .map(profile -> new EnvironmentState(profile.breathable(), profile.vacuum()))
                .orElseGet(() -> new EnvironmentState(
                        level.dimension() == Level.OVERWORLD,
                        level.dimension() != Level.OVERWORLD
                ));
    }

    private record EnvironmentState(boolean breathable, boolean vacuum) {
    }
}
