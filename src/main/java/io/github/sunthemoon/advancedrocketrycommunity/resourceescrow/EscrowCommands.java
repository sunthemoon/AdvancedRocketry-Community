package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Operator-only diagnostics do not create authority or admit an operation. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class EscrowCommands {
    private EscrowCommands() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("escrow")
                .requires(source -> source.hasPermission(2)).then(Commands.literal("status").executes(context -> {
                    var server = context.getSource().getServer(); var data = ResourceEscrowSavedData.existing(server);
                    var usage = data == null ? new EscrowFloors.Usage(0, 0) : data.bind(server).floorUsage();
                    context.getSource().sendSuccess(() -> Component.literal("escrow admission=disabled operations="
                            + (data == null ? 0 : data.ledger().records().size()) + "/" + EscrowLimits.OPERATIONS
                            + " floors_used=" + usage.used() + " floors_reserved=" + usage.reserved() + "/" + EscrowLimits.FLOORS
                            + " tombstones=" + (data == null ? 0 : data.ledger().tombstones().size()) + "/" + EscrowLimits.TOMBSTONES), false);
                    return 1;
                }))));
    }
}
