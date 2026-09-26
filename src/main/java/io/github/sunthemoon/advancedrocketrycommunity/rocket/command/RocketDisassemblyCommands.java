package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.server.level.ServerPlayer;

/** Player consent lives outside the operator-only diagnostic command subtree. */
public final class RocketDisassemblyCommands {
    private RocketDisassemblyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, RocketManager rockets) {
        dispatcher.register(Commands.literal("arce").then(Commands.literal("rocket-disassembly")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("confirm")
                        .then(Commands.argument("confirmation", UuidArgument.uuid())
                                .executes(context -> rockets.confirmDisassembly(
                                        context.getSource().getPlayerOrException(),
                                        UuidArgument.getUuid(context, "confirmation")) ? 1 : 0)))));
    }
}
