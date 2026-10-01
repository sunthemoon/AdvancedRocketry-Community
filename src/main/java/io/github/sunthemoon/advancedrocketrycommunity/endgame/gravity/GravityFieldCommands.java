package io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.EndgameCommandSources;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * ADR-058 section 5 consent: {@code /arce endgame field trust|untrust <player>} and {@code field trusted}. Only the
 * player's own, connected, non-silent command source counts, so {@code /execute as}, functions and FakePlayers can
 * never consent for a player. Each command changes only the issuing player's own list.
 */
public final class GravityFieldCommands {
    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("endgame")
                .then(Commands.literal("field")
                        .then(Commands.literal("trust").then(Commands.argument("player",
                                GameProfileArgument.gameProfile()).executes(context -> change(context, true))))
                        .then(Commands.literal("untrust").then(Commands.argument("player",
                                GameProfileArgument.gameProfile()).executes(context -> change(context, false))))
                        .then(Commands.literal("trusted").executes(this::list)))));
    }

    private int change(CommandContext<CommandSourceStack> context, boolean trust) throws CommandSyntaxException {
        Optional<ServerPlayer> player = EndgameCommandSources.ownPlayer(context.getSource());
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        if (player.isEmpty() || devices.isEmpty()) {
            return refuse(context, player.isEmpty() ? EndgameCode.NOT_A_PLAYER : EndgameCode.ROOT_UNAVAILABLE);
        }
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            throw net.minecraft.commands.arguments.EntityArgument.ERROR_NOT_SINGLE_PLAYER.create();
        }
        UUID owner = profiles.iterator().next().getId();
        EndgameCode code = trust ? devices.get().trust().trust(player.get(), owner)
                : devices.get().trust().untrust(player.get(), owner);
        if (code != EndgameCode.OK) {
            return refuse(context, code);
        }
        context.getSource().sendSuccess(() -> Component.translatable(trust
                ? "message.advancedrocketrycommunity.endgame.field.trusted"
                : "message.advancedrocketrycommunity.endgame.field.untrusted", owner.toString()), false);
        return 1;
    }

    private int list(CommandContext<CommandSourceStack> context) {
        Optional<ServerPlayer> player = EndgameCommandSources.ownPlayer(context.getSource());
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        if (player.isEmpty() || devices.isEmpty()) {
            return refuse(context, player.isEmpty() ? EndgameCode.NOT_A_PLAYER : EndgameCode.ROOT_UNAVAILABLE);
        }
        List<UUID> owners = devices.get().trust().list(player.get());
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.advancedrocketrycommunity.endgame.field.list", owners.size(), GravityTrust.MAX_TRUSTED), false);
        owners.forEach(owner -> context.getSource().sendSuccess(() -> Component.literal(owner.toString()), false));
        return owners.size();
    }

    private static int refuse(CommandContext<CommandSourceStack> context, EndgameCode code) {
        context.getSource().sendFailure(Component.translatable(code.translationKey()).append(" (" + code.name() + ")"));
        return 0;
    }
}
