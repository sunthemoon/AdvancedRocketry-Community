package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModDamageTypes;

/** Applies finite suit oxygen and damage only on the logical server. */
public final class PlayerLifeSupportService {
    public static final int HEARTBEAT_TICKS = 100;

    private final AtmosphereManager atmosphere;
    private final SnapshotSink snapshotSink;
    private final Map<UUID, PlayerState> players = new HashMap<>();

    public PlayerLifeSupportService(AtmosphereManager atmosphere, SnapshotSink snapshotSink) {
        this.atmosphere = Objects.requireNonNull(atmosphere, "atmosphere");
        this.snapshotSink = Objects.requireNonNull(snapshotSink, "snapshotSink");
    }

    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tickPlayer(player);
        }
    }

    public PlayerLifeSupportSnapshot tickPlayer(ServerPlayer player) {
        PlayerState state = players.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
        int suitPieces = SpaceSuitArmorItem.countEquippedPieces(player);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        SpaceSuitOxygen.ReadResult oxygenData = SpaceSuitOxygen.read(chest);
        int oxygen = oxygenData.status() == SpaceSuitOxygen.DataStatus.VALID
                ? oxygenData.oxygenUnits()
                : 0;
        BlockPos eyePosition = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        BreathabilityState breathability = atmosphere.breathabilityAt(
                player.serverLevel(),
                eyePosition
        );

        PlayerLifeSupportSnapshot snapshot;
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
            state.vacuumPhase = decision.vacuumPhase();
            if (decision.oxygenUnits() != oxygen
                    && oxygenData.status() == SpaceSuitOxygen.DataStatus.VALID
                    && !SpaceSuitOxygen.set(chest, decision.oxygenUnits())) {
                throw new IllegalStateException("Validated server suit oxygen could not be updated");
            }
            if (decision.damage() > 0.0F) {
                player.hurt(ModDamageTypes.vacuum(player.serverLevel()), decision.damage());
            }
            snapshot = new PlayerLifeSupportSnapshot(
                    decision.status(),
                    breathability,
                    suitPieces,
                    decision.oxygenUnits()
            );
        }
        synchronize(player, state, snapshot);
        return snapshot;
    }

    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        players.remove(event.getEntity().getUUID());
    }

    public Optional<PlayerLifeSupportSnapshot> snapshot(UUID playerId) {
        PlayerState state = players.get(playerId);
        return state == null ? Optional.empty() : Optional.ofNullable(state.lastSnapshot);
    }

    public void clear() {
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
