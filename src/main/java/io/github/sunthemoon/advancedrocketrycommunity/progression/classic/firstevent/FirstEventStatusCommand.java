package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * W44 section 3.3: the read-only {@code /arce progression status} command (permission 2). Per kind it shows the tagged
 * source state, the receipt proof, the hold, the retry deadline, the failure count of the current streak and the
 * deferred and participant counts; per root its presence. It reads only and changes nothing.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class FirstEventStatusCommand {
    private FirstEventStatusCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce")
                .then(Commands.literal("progression")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))));
    }

    private static int status(CommandSourceStack source) {
        List<String> lines = ClassicFirstEventDelivery.installed(source.getServer())
                .map(ClassicFirstEventDelivery::status)
                .orElse(List.of("progression service=not_started"));
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return lines.size();
    }
}
