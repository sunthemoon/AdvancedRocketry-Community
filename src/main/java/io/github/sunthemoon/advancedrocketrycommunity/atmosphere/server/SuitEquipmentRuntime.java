package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.OxygenTransferResult;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentService;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Item-use bridge to the mod-owned service; session state clears through server-stop handling. */
public final class SuitEquipmentRuntime {
    /** Read-only landing check; no debit or equipment mutation grants this result. */
    public static boolean hasRejoinAir(net.minecraft.server.level.ServerPlayer player) {
        SuitEquipmentService current = service;
        return current != null && player.getServer().isSameThread() && current.countPieces(player) == 4
                && current.readOxygen(player).oxygenUnits() > 0;
    }
    private static volatile SuitEquipmentService service;

    private SuitEquipmentRuntime() { }

    public static void install(SuitEquipmentService installed) {
        service = Objects.requireNonNull(installed, "installed");
    }

    public static boolean isChest(ItemStack stack) {
        SuitEquipmentService current = service;
        return current != null && current.isChest(stack);
    }

    /** Fails closed: without the installed catalog, every item counts as provider-owned. */
    public static boolean claimed(Item item) {
        SuitEquipmentService current = service;
        return current == null || current.claims(item);
    }

    public static OxygenTransferResult fillOneCanister(ServerPlayer player, InteractionHand hand) {
        SuitEquipmentService current = service;
        return current == null ? new OxygenTransferResult(false, 0, 0) : current.fillOneCanister(player, hand);
    }

    public static boolean fillTankCanister(ServerPlayer player, InteractionHand hand) {
        SuitEquipmentService current = service;
        return current != null && current.fillTankCanister(player, hand);
    }
}
