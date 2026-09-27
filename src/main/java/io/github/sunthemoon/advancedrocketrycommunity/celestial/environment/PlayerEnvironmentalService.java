package io.github.sunthemoon.advancedrocketrycommunity.celestial.environment;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModDamageTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Server-session exposure state. Called after the same player's finite oxygen transaction. */
public final class PlayerEnvironmentalService {
    public static final int WARNING_HEARTBEAT = 100;
    private final AtmosphereManager atmosphere;
    private final WarningSink warnings;
    private final Map<UUID, State> players = new HashMap<>();

    public PlayerEnvironmentalService(AtmosphereManager atmosphere, WarningSink warnings) {
        this.atmosphere = Objects.requireNonNull(atmosphere, "atmosphere");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    public EnvironmentalExposure.Decision tick(ServerPlayer player, boolean suffocationAttempt) {
        ServerLevel level = player.serverLevel();
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Environmental exposure requires the logical server thread");
        }
        EnvironmentalConditions conditions = atmosphere.surfaceExposure(level);
        State state = players.computeIfAbsent(player.getUUID(), ignored -> new State());
        if (player.getId() != state.entityId || !level.dimension().equals(state.dimension) || !conditions.equals(state.conditions)) {
            state.phase = 0;
            state.entityId = player.getId();
            state.dimension = level.dimension();
            state.conditions = conditions;
        }
        int hazards = 0;
        if (conditions.enabled() && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
            BlockPos eye = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
            hazards = EnvironmentalExposure.hazards(conditions, EnvironmentalProtection.read(player),
                    atmosphere.controlledAt(level, eye), directSunlight(level, eye));
        }
        var decision = EnvironmentalExposure.tick(hazards, state.phase);
        state.phase = decision.phase();
        if (decision.damage() > 0 && !suffocationAttempt) {
            player.hurt(ModDamageTypes.environmental(level, decision.hazards()), decision.damage());
        }
        long now = level.getGameTime();
        if (hazards != state.lastHazards || hazards != 0 && (now < state.lastWarning
                || now - state.lastWarning >= WARNING_HEARTBEAT)) {
            warnings.send(player, warning(hazards));
            state.lastWarning = now;
        }
        state.lastHazards = hazards;
        return decision;
    }

    public static boolean directSunlight(ServerLevel level, BlockPos eye) {
        return level.dimensionType().hasSkyLight() && level.isDay()
                && level.hasChunkAt(eye) && level.canSeeSky(eye);
    }

    public void remove(UUID playerId) { players.remove(playerId); }

    public void clear() { players.clear(); }

    private static Component warning(int hazards) {
        if (hazards == 0) { return Component.empty(); }
        MutableComponent names = Component.empty();
        for (int bit : new int[]{EnvironmentalExposure.COLD, EnvironmentalExposure.HEAT,
                EnvironmentalExposure.PRESSURE, EnvironmentalExposure.SOLAR}) {
            if ((hazards & bit) == 0) { continue; }
            if (!names.getSiblings().isEmpty()) { names.append(" / "); }
            names.append(Component.translatable("environment.advancedrocketrycommunity."
                    + EnvironmentalExposure.primary(bit)));
        }
        return Component.translatable("environment.advancedrocketrycommunity.exposed", names);
    }

    @FunctionalInterface
    public interface WarningSink {
        void send(ServerPlayer player, Component message);
    }

    private static final class State {
        private int entityId = Integer.MIN_VALUE;
        private ResourceKey<Level> dimension;
        private EnvironmentalConditions conditions;
        private int phase;
        private int lastHazards;
        private long lastWarning;
    }
}
