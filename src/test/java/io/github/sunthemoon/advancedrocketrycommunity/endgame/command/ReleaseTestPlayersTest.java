package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class ReleaseTestPlayersTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void onlyExactRegisteredPlayerAndConnectionPairsAreOwned() {
        var first = new Object(); var second = new Object(); var stranger = new Object();
        var firstConnection = new Object(); var secondConnection = new Object();
        var firstChannel = new EmbeddedChannel(); var secondChannel = new EmbeddedChannel();
        try {
            var owned = new IdentityHashMap<Object, ReleaseTestPlayers.OwnedChannel>();
            owned.put(first, new ReleaseTestPlayers.OwnedChannel(firstConnection, firstChannel));
            owned.put(second, new ReleaseTestPlayers.OwnedChannel(secondConnection, secondChannel));
            assertTrue(ReleaseTestPlayers.owned(owned, first, firstConnection));
            assertTrue(ReleaseTestPlayers.owned(owned, second, secondConnection));
            assertFalse(List.of(first, second, stranger).stream().allMatch(player ->
                    ReleaseTestPlayers.owned(owned, player, player == first ? firstConnection : secondConnection)));
            assertFalse(ReleaseTestPlayers.owned(owned, first, secondConnection));
            assertFalse(ReleaseTestPlayers.owned(null, first, firstConnection), "Another server owns no such connection");
        } finally { firstChannel.finishAndReleaseAll(); secondChannel.finishAndReleaseAll(); }
    }

    @Test void departedPlayerDoesNotAuthorizeAReplacementLoginEvenWithTheSameIdentityValue() {
        record Player(String id) { }
        var left = new Player("same-uuid"); var joined = new Player("same-uuid"); var connection = new Object();
        var channel = new EmbeddedChannel();
        try {
            Map<Object, ReleaseTestPlayers.OwnedChannel> owned = new IdentityHashMap<>();
            owned.put(left, new ReleaseTestPlayers.OwnedChannel(connection, channel));
            assertTrue(ReleaseTestPlayers.owned(owned, left, connection));
            assertFalse(ReleaseTestPlayers.owned(owned, joined, connection));
            assertFalse(ReleaseTestPlayers.owned(owned, left, new Object()));
            channel.close();
            assertFalse(ReleaseTestPlayers.owned(owned, left, connection));
        } finally { channel.finishAndReleaseAll(); }
    }
}
