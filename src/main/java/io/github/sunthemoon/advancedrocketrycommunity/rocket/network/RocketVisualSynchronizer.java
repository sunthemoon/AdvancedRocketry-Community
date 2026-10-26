package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Sends visual data only after Forge confirms that a player tracks the entity. */
public final class RocketVisualSynchronizer {
    private final RocketVisualNetwork network;

    public RocketVisualSynchronizer(RocketVisualNetwork network) {
        this.network = Objects.requireNonNull(network, "network");
        MinecraftForge.EVENT_BUS.addListener(this::onSnapshotChanged);
    }

    /** Called only after the authoritative snapshot and its resource wrapper have passed validation. */
    public static void snapshotChanged(RocketEntity rocket, RocketStructureSnapshot before) {
        if (!rocket.level().isClientSide && rocket.operational()) {
            publishIfChanged(before, rocket.snapshot().orElseThrow(),
                    () -> MinecraftForge.EVENT_BUS.post(new SnapshotChanged(rocket)));
        }
    }

    static void publishIfChanged(RocketStructureSnapshot before, RocketStructureSnapshot after, Runnable publish) {
        if (!before.contentHash().equals(after.contentHash())) { publish.run(); }
    }

    private void onSnapshotChanged(SnapshotChanged event) {
        var rocket = (RocketEntity) event.getEntity();
        try {
            network.sendTracking(rocket);
        } catch (RuntimeException exception) {
            // Display failure cannot roll back a committed cargo delta or its escrow marker.
            AdvancedRocketryCommunity.LOGGER.warn(
                    "Skipped bounded visual sync for RocketEntity {}: {}", rocket.getUUID(), exception.getMessage());
        }
    }

    /** Server-local notification, not a packet or a persisted change record. */
    public static final class SnapshotChanged extends EntityEvent {
        private SnapshotChanged(RocketEntity rocket) { super(rocket); }
    }

    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof RocketEntity rocket)) {
            return;
        }
        try {
            int chunks = network.send(player, rocket);
            AdvancedRocketryCommunity.LOGGER.debug(
                    "Sent {} visual chunk(s) for RocketEntity {} to tracking player {}",
                    chunks,
                    rocket.getUUID(),
                    player.getGameProfile().getName()
            );
        } catch (RuntimeException exception) {
            AdvancedRocketryCommunity.LOGGER.warn(
                    "Skipped bounded visual sync for RocketEntity {}: {}",
                    rocket.getUUID(),
                    exception.getMessage()
            );
        }
    }
}
