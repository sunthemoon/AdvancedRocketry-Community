package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /arce bay receipt retire-rejected <satellite>} (W27A-DESIGN section 5, N5), with the permission of
 * {@code /arce rocket recover}. It answers at once and reports the exact acknowledgement when the write completes.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class BayReceiptCommands {
    private static final String MESSAGE = "message.advancedrocketrycommunity.bay_receipt.";
    private BayReceiptCommands() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("bay").then(Commands.literal("receipt")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("retire-rejected").then(Commands.argument("satellite", UuidArgument.uuid())
                        .executes(context -> retire(context.getSource(), UuidArgument.getUuid(context, "satellite"))))))));
    }

    static int retire(CommandSourceStack source, UUID satellite) {
        var server = source.getServer();
        var receipts = EscrowRuntime.of(server).bayReceipts(server);
        var checked = BayReceiptRetirement.check(receipts, satellite, BayReceiptRetirement.journals(server));
        if (checked.refusal() != null) {
            source.sendFailure(refusal(checked, satellite)); return 0;
        }
        BayReceipt receipt = checked.receipt();
        var admission = BayReceiptRetirement.retire(receipts.store(), receipt, operator(source), outcome -> {
            if (outcome == BayReceiptWriter.Outcome.COMMITTED) {
                source.sendSuccess(() -> Component.translatable(MESSAGE + "retired", receipt.op().toString(), satellite.toString()), true);
            } else {
                source.sendFailure(Component.translatable(MESSAGE + "failed", outcome.name(), receipt.op().toString()));
            }
        });
        if (!admission.queued()) {
            source.sendFailure(Component.translatable(BayReceiptRetirement.Refusal.UNAVAILABLE.translationKey())); return 0;
        }
        source.sendSuccess(() -> Component.translatable(MESSAGE + "requested", receipt.op().toString(), satellite.toString()), true);
        return 1;
    }

    static Component refusal(BayReceiptRetirement.Checked checked, UUID satellite) {
        String key = checked.refusal().translationKey();
        BayReceipt receipt = checked.receipt();
        return switch (checked.refusal()) {
            case NOT_LOADED, UNAVAILABLE -> Component.translatable(key);
            case NOT_FOUND, RELEASED, UNACKNOWLEDGED -> Component.translatable(key, satellite.toString());
            case NOT_REJECTED -> Component.translatable(key, satellite.toString(), receipt.phase().name());
            case ROCKET_PENDING, TRANSFER_RECORD, TRANSACTION_RECORD -> Component.translatable(key, receipt.rocket().toString());
        };
    }

    private static String operator(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player
                ? player.getGameProfile().getName() + "/" + player.getUUID() : source.getTextName();
    }
}
