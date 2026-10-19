package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankContainerExchange;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import java.util.ArrayList;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Ordinary local native storage, outside cargo escrow; one lock and a detached whole-container plan. */
final class RocketCargoLocalTank {
    private RocketCargoLocalTank() { }

    @Nullable static LoaderTank intake(LoaderTank tank, FluidStack offered) {
        if (offered == null || offered.isEmpty()) { return null; }
        try {
            var id = ForgeRegistries.FLUIDS.getKey(offered.getFluid());
            if (id == null) { return null; }
            // The immutable model bounds the tag before copying it. Offered volume need not fit the tank.
            LoaderTank unit = LoaderTank.of(id, offered.getTag(), 1);
            if (!unit.escrowCarriable() || !tank.isEmpty()
                    && (!tank.fluid().equals(unit.fluid()) || !Objects.equals(tank.tag(), unit.tag()))) { return null; }
            return unit;
        } catch (IllegalArgumentException refused) { return null; }
    }

    static boolean exchange(RocketFluidCargoBlockEntity device, Player player, InteractionHand hand) {
        if (!device.admitted(player) || !device.beginLocal()) { return false; }
        try {
            var inventory = player.getInventory();
            ItemStack held = player.getItemInHand(hand);
            var storage = new ArrayList<ItemStack>();
            var slots = new ArrayList<Integer>();
            for (int slot = 0; slot < inventory.items.size(); slot++) {
                if (hand != InteractionHand.MAIN_HAND || slot != inventory.selected) {
                    storage.add(inventory.getItem(slot)); slots.add(slot);
                }
            }
            LoaderTank before = device.tank();
            var plan = TankContainerExchange.plan(held, storage, before.fluidStack(), LoaderTank.CAPACITY);
            if (plan.isEmpty()) { return false; }
            LoaderTank after;
            try { after = LoaderTank.of(plan.get().fluid()); }
            catch (IllegalArgumentException refused) { return false; }
            if (after.amount() > before.amount() && !after.escrowCarriable()) { return false; }
            var prepared = plan.get().slots();
            int destination = prepared.destinationSlot() < 0 ? -1 : slots.get(prepared.destinationSlot());
            // Only inspected bucket/canister implementations execute during plan(), on detached one-unit copies.
            if (player.getItemInHand(hand) != held) { return false; }
            device.writeTank(after);
            player.setItemInHand(hand, prepared.held());
            if (destination >= 0) { inventory.setItem(destination, prepared.destination()); }
            inventory.setChanged();
            return true;
        } finally { device.endLocal(); }
    }
}
