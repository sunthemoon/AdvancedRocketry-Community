package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationOrbitEnvironmentResolver;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** One matching host's read-only handle; only startup acquires SavedData. No world collection or query-side producer. */
public final class SolarExposure {
    private volatile Binding binding;

    public void start(MinecraftServer server, CelestialCatalogManager catalogs) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(catalogs, "catalogs");
        if (!server.isSameThread()) { throw new IllegalStateException("Solar startup requires the host thread"); }
        if (binding != null) { throw new IllegalStateException("Solar exposure is already started"); }
        binding = new Binding(server, catalogs, StationRegistrySavedData.get(server));
    }

    public boolean owns(MinecraftServer server) { Binding active = binding; return active != null && active.server == server; }
    public void close(MinecraftServer server) {
        Binding active = binding;
        if (active != null && active.server == server) {
            if (!server.isSameThread()) { throw new IllegalStateException("Solar close requires the host thread"); }
            binding = null;
        }
    }

    public SolarGeneration.Environment read(ServerLevel level, BlockPos position, BlockEntity owner) {
        Binding active = binding;
        // Stage one is metadata only. Same-position orphan detection necessarily belongs to stage two.
        if (active == null || !active.server.isSameThread() || level == null || position == null || owner == null
                || level.getServer() != active.server || active.server.getLevel(level.dimension()) != level
                || owner.isRemoved() || owner.getLevel() != level || !owner.getBlockPos().equals(position)
                || !level.isInWorldBounds(position) || !level.getWorldBorder().isWithinBounds(position)) {
            return SolarGeneration.Environment.unavailable();
        }
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntities().get(position) != owner) {
            return SolarGeneration.Environment.unavailable();
        }
        var snapshot = active.catalogs.snapshot();
        var catalog = snapshot.catalog();
        if (catalog == null || !snapshot.status().ready()) { return SolarGeneration.Environment.unavailable(); }
        SolarGeneration.Environment reading;
        if (level.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            var orbit = StationOrbitEnvironmentResolver.resolveAt(active.stations, Optional.of(catalog),
                    level.dimension(), position.getX(), position.getZ()).orElse(null);
            if (orbit == null) { return SolarGeneration.Environment.unavailable(); }
            int cutoff = chunk.getSkyLightSources().getLowestSourceY(position.getX() & 15, position.getZ() & 15);
            if (!SolarGeneration.validCutoff(cutoff, level.getMinBuildHeight(), level.getMaxBuildHeight())) {
                return SolarGeneration.Environment.unavailable();
            }
            reading = new SolarGeneration.Environment(true,
                    SolarGeneration.exposed(position.getY(), cutoff, level.getMinBuildHeight(), level.getMaxBuildHeight()),
                    2, orbit.orbitBodyAvailable() ? 2 : 3, orbit.solarIntensity(), 1_000);
        } else {
            if (!level.dimensionType().hasSkyLight() || (long) position.getY() + 1 >= level.getMaxBuildHeight()) {
                return SolarGeneration.Environment.unavailable();
            }
            var candidates = catalog.candidatesForLevel(level.dimension());
            if (candidates.size() != 1) { return SolarGeneration.Environment.unavailable(); }
            double attenuation;
            try { attenuation = SolarGeneration.attenuation(level.getRainLevel(1.0F), level.getThunderLevel(1.0F)); }
            catch (IllegalArgumentException refused) { return SolarGeneration.Environment.unavailable(); }
            reading = new SolarGeneration.Environment(true, level.canSeeSky(position.above()), level.isDay() ? 1 : 0,
                    1, candidates.get(0).solarIntensity() * attenuation, SolarGeneration.weatherPermille(attenuation));
        }
        var after = active.catalogs.snapshot();
        return binding == active && after.catalog() == catalog
                && after.status().generation() == snapshot.status().generation() && reading.valid()
                ? reading : SolarGeneration.Environment.unavailable();
    }

    private record Binding(MinecraftServer server, CelestialCatalogManager catalogs, StationRegistrySavedData stations) { }
}
