package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraft.server.MinecraftServer;

/** Sends one bounded display snapshot on join and successful data-pack sync. */
public final class CelestialSnapshotSynchronizer {
    private final CelestialCatalogManager catalogs;
    private final CelestialNetwork network;

    public CelestialSnapshotSynchronizer(CelestialCatalogManager catalogs, CelestialNetwork network) {
        this.catalogs = catalogs;
        this.network = network;
    }

    public void onDatapackSync(OnDatapackSyncEvent event) {
        CelestialCatalog catalog = catalogs.current().orElse(null);
        if (catalog == null) {
            AdvancedRocketryCommunity.LOGGER.error("Skipped celestial snapshot sync because no valid catalog is active");
            return;
        }

        DataResult<CelestialSnapshotPacket> encoded = CelestialSnapshotPacket.fromCatalog(
                catalog,
                catalogs.status().generation()
        );
        if (encoded.error().isPresent()) {
            AdvancedRocketryCommunity.LOGGER.error(
                    "Skipped celestial snapshot sync: {}",
                    encoded.error().orElseThrow().message()
            );
            return;
        }

        CelestialSnapshotPacket packet = encoded.result().orElseThrow();
        event.getPlayers().forEach(player -> network.send(player, packet));
        AdvancedRocketryCommunity.LOGGER.info(
                "Sent {}-byte celestial snapshot generation {} to {} player(s)",
                packet.payload().length,
                packet.catalogGeneration(),
                event.getPlayers().size()
        );
    }

    /** Re-sends the bounded snapshot after server-side discovery changes. */
    public void sendAll(MinecraftServer server) {
        CelestialCatalog catalog = catalogs.current().orElse(null);
        if (catalog == null) {
            return;
        }
        DataResult<CelestialSnapshotPacket> encoded = CelestialSnapshotPacket.fromCatalog(
                catalog,
                catalogs.status().generation()
        );
        encoded.result().ifPresent(packet -> server.getPlayerList().getPlayers()
                .forEach(player -> network.send(player, packet)));
    }
}
