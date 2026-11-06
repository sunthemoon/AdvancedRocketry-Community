package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketSeatRelease;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

/** One loaded rocket and one assigned cached name/UUID; never resolves remote profiles or activates chunks. */
public final class RocketSeatCommands {
    private RocketSeatCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arce").then(Commands.literal("rocket")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("release-seat").then(Commands.argument("rocket", EntityArgument.entity())
                        .then(Commands.argument("player", StringArgumentType.word()).executes(RocketSeatCommands::release))))));
    }

    private static int release(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        // Functions can replay a parsed command under a different source. Recheck before resolving arguments.
        if (!source.hasPermission(2)) { return refuse(source, "permission level 2 required"); }
        if (!(EntityArgument.getEntity(context, "rocket") instanceof RocketEntity rocket)
                || rocket.level() != source.getLevel() || !rocket.isAlive() || !rocket.operational()
                || !source.getLevel().hasChunkAt(rocket.blockPosition())) {
            return refuse(source, "target is not a loaded operational rocket in this level");
        }
        String name = StringArgumentType.getString(context, "player");
        UUID player = resolve(rocket, name);
        if (player == null) { return refuse(source, "use one assigned cached player name or UUID"); }
        var result = RocketSeatRelease.operator(rocket, player);
        if (!result.success()) { return refuse(source, result.code().name()); }
        source.sendSuccess(() -> Component.literal("Released rocket seat: rocket=" + rocket.getUUID() + " player=" + player + "; dismounts the target if riding this rocket"), false);
        return 1;
    }

    private static UUID resolve(RocketEntity rocket, String name) {
        if (name.length() == 36) {
            try { return UUID.fromString(name); }
            catch (IllegalArgumentException ignored) { return null; }
        }
        if (!name.matches("[A-Za-z0-9_]{1,16}")) { return null; }
        var server = rocket.getServer();
        UUID match = null;
        // A UUID cache lookup is local. GameProfileCache.get(name) may perform remote authentication.
        for (var seat : rocket.flightData().orElseThrow().passengers().assignments()) {
            var online = server.getPlayerList().getPlayer(seat.passengerId());
            var profile = online != null ? online.getGameProfile()
                    : server.getProfileCache() == null ? null : server.getProfileCache().get(seat.passengerId()).orElse(null);
            if (profile != null && name.equalsIgnoreCase(profile.getName())) {
                if (match != null) { return null; }
                match = seat.passengerId();
            }
        }
        return match;
    }

    private static int refuse(CommandSourceStack source, String reason) {
        source.sendFailure(Component.literal("Seat release refused: " + reason));
        return 0;
    }
}
