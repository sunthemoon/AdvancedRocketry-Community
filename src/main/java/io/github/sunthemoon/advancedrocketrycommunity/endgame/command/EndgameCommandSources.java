package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

/** Player commands of the endgame act only for the player who typed them. */
public final class EndgameCommandSources {
    private EndgameCommandSources() {
    }

    /**
     * The player's own, non-silent source: not {@code /execute as}, a function, a command block or a FakePlayer, and
     * the player is still the connected one.
     */
    public static Optional<ServerPlayer> ownPlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && source.source == player && source.withSuppressedOutput() != source && player.getServer() != null
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
            return Optional.of(player);
        }
        return Optional.empty();
    }
}
