package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Optional R17 native fixtures use only facility-owned connections, never an adopted player. */
final class EscrowCrewedProbe {
    private EscrowCrewedProbe() { }
    static boolean enabled() { return Boolean.getBoolean("advancedrocketrycommunity.escrowCrewed"); }

    static void mount(RocketEntity rocket, String cut) {
        if (!enabled()) { return; }
        var player = reconnect(rocket);
        EscrowTags.require(player.startRiding(rocket, true) && rocket.getPassengers().equals(java.util.List.of(player)),
                "Crewed fixture did not mount its assigned passenger before F1");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CREWED_F1 cut={} passenger={} source={} mounted=true",
                cut, player.getUUID(), rocket.getUUID());
    }

    static ServerPlayer reconnect(RocketEntity rocket) {
        var server = rocket.getServer(); EscrowTestEndpoints.requireEnabled(server);
        var assignments = rocket.flightData().orElseThrow().passengers().assignments();
        EscrowTags.require(assignments.size() == 1, "Crewed fixture requires one frozen assignment");
        var id = assignments.get(0).passengerId();
        var player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            player = ReleaseTestPlayers.place(server, id, "S2Crew", (ServerLevel) rocket.level(), rocket.position());
            EscrowTags.require(player.getVehicle() == null, "Restart fixture must leave seating to the flight executor");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CREWED_RELOGIN passenger={} source={} mounted=false", id, rocket.getUUID());
        }
        EscrowTags.require(ReleaseTestPlayers.ownsAllConnectedPlayers(server) && player.connection.connection.isConnected()
                && player.level() == rocket.level(), "Crewed fixture lost its owned, online source-Level passenger");
        return player;
    }
}
