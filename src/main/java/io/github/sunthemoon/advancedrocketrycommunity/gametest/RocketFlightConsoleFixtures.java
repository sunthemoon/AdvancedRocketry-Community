package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * W182 (finding W179-F3): the server serves a rocket flight intent only to a connected, living, non-spectator player
 * that is not a fake player and has that rocket's flight menu open and valid, because the flight screen is the only
 * client that sends one. A GameTest or native probe that drives a flight through that C2S entry therefore uses a
 * connected player and opens the flight menu through the server's own path first, as a client does.
 */
public final class RocketFlightConsoleFixtures {
    private RocketFlightConsoleFixtures() {
    }

    /**
     * A client's server player for a fixture: a game connection over an embedded channel, but kept out of the player
     * list and the Level like the {@code FakePlayer} it replaces, so no login, logout, chunk ticket or player tick
     * follows from it. A fixture that needs a joined player uses {@code placeNewPlayer} instead.
     */
    public static ServerPlayer standIn(ServerLevel level, GameProfile profile) {
        return connect(new ServerPlayer(level.getServer(), level, profile));
    }

    /** Gives a fixture's own unjoined {@link ServerPlayer}, for example one that counts teleports, that connection. */
    public static <P extends ServerPlayer> P connect(P player) {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(player.getServer(), connection, player);
        return player;
    }

    /**
     * Opens {@code rocket}'s flight menu for {@code player} as the player's use of the rocket does, unless that menu
     * is open and valid already. Returns whether it is open and valid afterwards; the server refuses the opening, for
     * example, out of reach.
     */
    public static boolean open(ServerPlayer player, RocketEntity rocket) {
        if (!valid(player, rocket)) {
            RocketRuntime.openFlightMenu(player, rocket);
        }
        return valid(player, rocket);
    }

    /**
     * Runs {@code intent} with {@code rocket}'s flight menu open, as the flight screen's button sends it, then closes
     * the menu, as the player closing the screen does, so no open menu stays behind on a joined, ticking player.
     * Returns whether the menu was open; when it was not, the server refuses the intent.
     */
    public static boolean send(ServerPlayer player, RocketEntity rocket, Runnable intent) {
        boolean opened = open(player, rocket);
        intent.run();
        player.closeContainer();
        return opened;
    }

    private static boolean valid(ServerPlayer player, RocketEntity rocket) {
        return player.containerMenu instanceof RocketFlightMenu menu && menu.rocketEntityId() == rocket.getId()
                && menu.stillValid(player);
    }
}
