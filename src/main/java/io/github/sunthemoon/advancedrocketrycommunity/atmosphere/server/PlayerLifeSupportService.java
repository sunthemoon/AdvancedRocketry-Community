package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.PlayerEnvironmentalService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerLifeSupportDecision;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerLifeSupportEngine;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerLifeSupportInput;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerProtectionStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModDamageTypes;

/** Applies finite suit oxygen and damage only on the logical server. */
public final class PlayerLifeSupportService {
    public static final int HEARTBEAT_TICKS = 100;

    private final AtmosphereManager atmosphere;
    private final SnapshotSink snapshotSink;
    private final SuitEquipmentService equipment;
    private final PlayerEnvironmentalService environment;
    private final Map<UUID, PlayerState> players = new HashMap<>();
    private boolean ticking;

    public PlayerLifeSupportService(AtmosphereManager atmosphere, SnapshotSink snapshotSink) {
        this(atmosphere, snapshotSink, new SuitEquipmentService(SuitEquipmentCatalog.empty()));
    }

    public PlayerLifeSupportService(AtmosphereManager atmosphere, SnapshotSink snapshotSink,
                                    SuitEquipmentService equipment) {
        this.atmosphere = Objects.requireNonNull(atmosphere, "atmosphere");
        this.snapshotSink = Objects.requireNonNull(snapshotSink, "snapshotSink");
        this.equipment = Objects.requireNonNull(equipment, "equipment");
        environment = new PlayerEnvironmentalService(atmosphere, (player, message) -> player.displayClientMessage(message, true));
    }

    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tickPlayer(player);
        }
    }

    public PlayerLifeSupportSnapshot tickPlayer(ServerPlayer player) {
        if (ticking || !player.serverLevel().getServer().isSameThread()) {
            throw new IllegalStateException("Life support requires the non-reentrant logical server thread");
        }
        ticking = true;
        try {
            return applyTick(player);
        } finally {
            ticking = false;
        }
    }

    private PlayerLifeSupportSnapshot applyTick(ServerPlayer player) {
        PlayerState state = players.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
        SuitEquipmentService.Reading oxygenData = equipment.readOxygen(player);
        int suitPieces = equipment.countPieces(player);
        int oxygen = oxygenData.oxygenUnits();
        BlockPos eyePosition = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        BreathabilityState breathability = atmosphere.breathabilityAt(
                player.serverLevel(),
                eyePosition
        );

        PlayerLifeSupportSnapshot snapshot;
        boolean suffocationAttempt = false;
        if (player.isCreative() || player.isSpectator()) {
            state.vacuumPhase = 0;
            snapshot = new PlayerLifeSupportSnapshot(
                    PlayerProtectionStatus.EXEMPT,
                    breathability,
                    suitPieces,
                    oxygen
            );
        } else {
            PlayerLifeSupportDecision decision = PlayerLifeSupportEngine.tick(
                    new PlayerLifeSupportInput(
                            atmosphere.baseAtmosphereBreathable(player.serverLevel()),
                            breathability,
                            suitPieces,
                            oxygen,
                            state.vacuumPhase
                    )
            );
            if (decision.oxygenUnits() != oxygen && !equipment.setOxygen(player, oxygenData, decision.oxygenUnits())) {
                suitPieces = equipment.countPieces(player);
                decision = PlayerLifeSupportEngine.tick(new PlayerLifeSupportInput(
                        atmosphere.baseAtmosphereBreathable(player.serverLevel()), breathability,
                        suitPieces, 0, state.vacuumPhase));
            }
            state.vacuumPhase = decision.vacuumPhase();
            if (decision.damage() > 0.0F) {
                suffocationAttempt = true;
                player.hurt(ModDamageTypes.vacuum(player.serverLevel()), decision.damage());
            }
            snapshot = new PlayerLifeSupportSnapshot(
                    decision.status(),
                    breathability,
                    suitPieces,
                    decision.oxygenUnits()
            );
        }
        environment.tick(player, suffocationAttempt);
        synchronize(player, state, snapshot);
        return snapshot;
    }

    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        players.remove(event.getEntity().getUUID());
        environment.remove(event.getEntity().getUUID());
    }

    public Optional<PlayerLifeSupportSnapshot> snapshot(UUID playerId) {
        PlayerState state = players.get(playerId);
        return state == null ? Optional.empty() : Optional.ofNullable(state.lastSnapshot);
    }

    public void clear() {
        if (ticking) {
            throw new IllegalStateException("Cannot clear active life support");
        }
        equipment.clear();
        environment.clear();
        players.clear();
    }

    private void synchronize(
            ServerPlayer player,
            PlayerState state,
            PlayerLifeSupportSnapshot snapshot
    ) {
        long gameTime = player.serverLevel().getGameTime();
        boolean heartbeat = state.lastSentGameTime == Long.MIN_VALUE
                || gameTime < state.lastSentGameTime
                || gameTime - state.lastSentGameTime >= HEARTBEAT_TICKS;
        if (!snapshot.equals(state.lastSnapshot) || heartbeat) {
            snapshotSink.send(player, snapshot);
            state.lastSnapshot = snapshot;
            state.lastSentGameTime = gameTime;
        }
    }

    @FunctionalInterface
    public interface SnapshotSink {
        void send(ServerPlayer player, PlayerLifeSupportSnapshot snapshot);
    }

    private static final class PlayerState {
        private int vacuumPhase;
        private PlayerLifeSupportSnapshot lastSnapshot;
        private long lastSentGameTime = Long.MIN_VALUE;
    }
}
