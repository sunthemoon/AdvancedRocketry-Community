package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Sends visual data only after Forge confirms that a player tracks the entity. */
public final class RocketVisualSynchronizer {
    private final RocketVisualNetwork network;

    public RocketVisualSynchronizer(RocketVisualNetwork network) {
        this.network = Objects.requireNonNull(network, "network");
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
