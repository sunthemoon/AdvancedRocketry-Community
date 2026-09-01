package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import net.minecraft.server.level.ServerPlayer;

public interface StationOperationService {
    default void onInstalled() {
    }

    StationCreationResult createForPlayer(ServerPlayer player);
}

