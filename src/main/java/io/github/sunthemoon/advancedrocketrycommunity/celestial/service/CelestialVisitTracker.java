package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Records the first server-authoritative visit to catalog-backed Levels. */
public final class CelestialVisitTracker {
    private final CelestialCatalogManager catalogs;

    public CelestialVisitTracker(CelestialCatalogManager catalogs) {
        this.catalogs = catalogs;
    }

    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        record(event.getEntity());
    }

    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        record(event.getEntity());
    }

    private void record(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        catalogs.current()
                .flatMap(catalog -> catalog.forLevel(serverPlayer.serverLevel().dimension()))
                .ifPresent(definition -> {
                    CelestialSavedData data = CelestialSavedData.get(serverPlayer.server);
                    CelestialSavedData.MutationResult result = data.recordVisit(
                            definition.id(),
                            serverPlayer.server.overworld().getGameTime()
                    );
                    if (result == CelestialSavedData.MutationResult.UNSUPPORTED_SCHEMA) {
                        AdvancedRocketryCommunity.LOGGER.error(
                                "Refused celestial visit mutation for unsupported SavedData schema {}",
                                data.schemaVersion()
                        );
                    }
                });
    }
}
