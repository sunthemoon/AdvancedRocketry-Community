package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.OxygenTransferResult;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentService;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Item-use bridge to the mod-owned service; session state clears through server-stop handling. */
public final class SuitEquipmentRuntime {
    private static volatile SuitEquipmentService service;

    private SuitEquipmentRuntime() { }

    public static void install(SuitEquipmentService installed) {
        service = Objects.requireNonNull(installed, "installed");
    }

    public static boolean isChest(ItemStack stack) {
        SuitEquipmentService current = service;
        return current != null && current.isChest(stack);
    }

    public static OxygenTransferResult fillOneCanister(ServerPlayer player, InteractionHand hand) {
        SuitEquipmentService current = service;
        return current == null ? new OxygenTransferResult(false, 0, 0) : current.fillOneCanister(player, hand);
    }
}
