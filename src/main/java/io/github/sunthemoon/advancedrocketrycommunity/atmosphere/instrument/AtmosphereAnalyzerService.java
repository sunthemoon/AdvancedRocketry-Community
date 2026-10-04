package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContext;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.WorldLocation;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;

/** Host-only read handle. Closing releases the binding permanently; a handle never changes host. */
public final class AtmosphereAnalyzerService implements AutoCloseable {
    private volatile Binding binding;

    public AtmosphereAnalyzerService(MinecraftServer server, CelestialCatalogManager catalogs,
                                     AtmosphereManager atmospheres, BodyContextResolver contexts) {
        binding = new Binding(Objects.requireNonNull(server, "server"), Objects.requireNonNull(catalogs, "catalogs"),
                Objects.requireNonNull(atmospheres, "atmospheres"), Objects.requireNonNull(contexts, "contexts"));
    }

    public AnalyzerReading read(ServerPlayer player) {
        Binding active = binding;
        // The thread test precedes every player, Level, catalog and atmosphere query.
        if (active == null || !active.server().isSameThread() || !admitted(active.server(), player)) {
            return AnalyzerReading.unavailable();
        }
        ServerLevel level = player.serverLevel();
        double x = player.getX(), y = player.getEyeY(), z = player.getZ();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || x < -30_000_000D || x >= 30_000_000D || z < -30_000_000D || z >= 30_000_000D
                || y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return AnalyzerReading.unavailable();
        }
        BlockPos eye = BlockPos.containing(x, y, z);
        if (!Level.isInSpawnableBounds(eye) || !level.getWorldBorder().isWithinBounds(eye)
                || level.getChunkSource().getChunkNow(eye.getX() >> 4, eye.getZ() >> 4) == null) {
            return AnalyzerReading.unavailable();
        }
        CelestialCatalog catalog = active.catalogs().current().orElse(null);
        if (catalog == null) { return AnalyzerReading.unavailable(); }
        BodyContext context = active.contexts().resolve(new WorldLocation(level.dimension(), eye), catalog).orElse(null);
        if (context == null) { return AnalyzerReading.unavailable(); }
        AmbientIdentity ambient = project(catalog, context, level.dimension()).orElse(null);
        if (ambient == null) { return AnalyzerReading.unavailable(); }
        BreathabilityState state = active.atmospheres().breathabilityAt(level, eye);
        boolean supplied = state == BreathabilityState.BREATHABLE && active.atmospheres().controlledAt(level, eye);
        AnalyzerReading reading = compose(context, ambient, state, supplied);
        if (binding != active || active.catalogs().current().orElse(null) != catalog) {
            return AnalyzerReading.unavailable();
        }
        return reading;
    }

    static boolean admitted(MinecraftServer server, ServerPlayer player) {
        return player != null && !(player instanceof FakePlayer) && !player.isRemoved() && !player.hasDisconnected()
                && player.isAlive() && !player.isSpectator() && player.connection != null
                && player.getServer() == server && server.getPlayerList().getPlayer(player.getUUID()) == player
                && server.getLevel(player.serverLevel().dimension()) == player.serverLevel();
    }

    /** Pure projection of a captured catalog; room supply never substitutes an orbit body's atmosphere. */
    static Optional<AmbientIdentity> project(CelestialCatalog catalog, BodyContext context, ResourceKey<Level> level) {
        var candidates = catalog.candidatesForLevel(level);
        if (candidates.size() != 1 || catalog.get(context.bodyId()).isEmpty()) { return Optional.empty(); }
        var body = candidates.get(0);
        if (level.equals(CelestialIds.SPACE_LEVEL)) {
            if (!body.id().equals(CelestialIds.SPACE_ID) || context.locus() != BodyContext.Locus.ORBIT) {
                return Optional.empty();
            }
        } else if (context.locus() != BodyContext.Locus.SURFACE || !context.bodyId().equals(body.id())) {
            return Optional.empty();
        }
        var profile = body.atmosphere();
        return Optional.of(new AmbientIdentity(body.id().toString(),
                new AnalyzerReading.Ambient(profile.pressure(), profile.temperatureKelvin())));
    }

    static AnalyzerReading compose(BodyContext context, AmbientIdentity ambient, BreathabilityState state, boolean supplied) {
        AnalyzerReading.State effective = switch (state) {
            case BREATHABLE -> AnalyzerReading.State.BREATHABLE;
            case VACUUM -> AnalyzerReading.State.NON_BREATHABLE;
            case PENDING -> AnalyzerReading.State.PENDING;
        };
        return new AnalyzerReading(effective, Optional.of(context.bodyId().toString()),
                Optional.of(context.locus() == BodyContext.Locus.ORBIT ? AnalyzerReading.Locus.ORBIT : AnalyzerReading.Locus.SURFACE),
                Optional.of(ambient.bodyId()), Optional.of(ambient.values()), supplied && effective == AnalyzerReading.State.BREATHABLE);
    }

    boolean owns(MinecraftServer server) { Binding active = binding; return active != null && active.server() == server; }
    @Override public void close() { binding = null; }

    record AmbientIdentity(String bodyId, AnalyzerReading.Ambient values) { }
    private record Binding(MinecraftServer server, CelestialCatalogManager catalogs,
                           AtmosphereManager atmospheres, BodyContextResolver contexts) { }
}
