package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankContainerExchange;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketFuelBank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/** One-unit bucket/canister exchange, reusing the audited ADR-064 inventory planner. */
public final class RocketPropulsionInteraction {
    private RocketPropulsionInteraction() { }
    public static InteractionResult exchange(ServerPlayer player, RocketEntity rocket, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || !held.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()) { return InteractionResult.PASS; }
        if (player instanceof FakePlayer || player.isSpectator() || !player.isAlive() || !rocket.operational()
                || !rocket.isAlive() || player.level() != rocket.level() || !player.serverLevel().getServer().isSameThread()
                || !player.serverLevel().hasChunkAt(rocket.blockPosition()) || player.distanceToSqr(rocket) > 36
                || !rocket.ownerId().filter(player.getUUID()::equals).isPresent()
                || !player.serverLevel().mayInteract(player, rocket.blockPosition())) { return InteractionResult.FAIL; }
        var flight = rocket.flightData().orElseThrow(); var fuel = flight.fuel();
        if (!io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.resourceMutationAllowed(rocket)) {
            player.displayClientMessage(Component.translatable("message.advancedrocketrycommunity.propulsion.recovery_pending"), true);
            return InteractionResult.FAIL;
        }
        var nativeFuel = fuel.propulsion().orElse(null);
        if (nativeFuel == null || !nativeFuel.descriptor().settings().canBeFueledByHand()
                || nativeFuel.descriptor().mode() == RocketPropulsion.Mode.ABSTRACT
                || !flight.state().acceptsFuel()) { return refused(player); }
        var inventory = player.getInventory(); var storage = new ArrayList<ItemStack>(); var slots = new ArrayList<Integer>();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (hand != InteractionHand.MAIN_HAND || slot != inventory.selected) {
                storage.add(inventory.getItem(slot)); slots.add(slot);
            }
        }
        // Try primary then oxidizer. The detached exchange planner rejects branch mixing and metadata loss.
        for (boolean oxidizer : new boolean[] {false, true}) {
            if (oxidizer && nativeFuel.descriptor().mode() != RocketPropulsion.Mode.BIPROPELLANT
                    || !oxidizer && nativeFuel.primary().branch() == RocketFuelBank.Branch.ABSTRACT) { continue; }
            var bank = oxidizer ? nativeFuel.oxidizer() : nativeFuel.primary();
            long capacity = oxidizer ? nativeFuel.descriptor().oxidizerCapacity() : fuel.capacity();
            FluidStack existing = asFluid(bank);
            if (bank.branch() == RocketFuelBank.Branch.FLUID && existing.isEmpty()) { return refused(player); }
            var prepared = TankContainerExchange.plan(held, storage, existing, Math.toIntExact(capacity));
            if (prepared.isEmpty()) { continue; }
            FluidStack after = prepared.get().fluid();
            var role = oxidizer ? RocketPropulsion.Role.OXIDIZER : nativeFuel.descriptor().primaryRole();
            if (!catalogAllowsExchange(nativeFuel.descriptor(), role,
                    after.isEmpty() ? bank.fluidId() : ForgeRegistries.FLUIDS.getKey(after.getFluid()),
                    existing.getAmount(), after.getAmount(), RocketFluidFuelRuntime.capture())) { continue; }
            // Creative uses the same exact container debit/exchange convention as survival; no duplicate remainder.
            var next = fuel.withFluidBank(oxidizer, after.isEmpty()
                    ? (bank.fluidId() == null ? io.github.sunthemoon.advancedrocketrycommunity.ModIdentity.id("rocket_fuel") : bank.fluidId())
                    : ForgeRegistries.FLUIDS.getKey(after.getFluid()), after.getTag(), after.getAmount());
            var slotPlan = prepared.get().slots();
            rocket.updateFlightData(flight.withFuel(next, rocket.level().getGameTime()));
            player.setItemInHand(hand, slotPlan.held());
            if (slotPlan.destinationSlot() >= 0) { inventory.setItem(slots.get(slotPlan.destinationSlot()), slotPlan.destination()); }
            inventory.setChanged();
            player.displayClientMessage(Component.translatable("message.advancedrocketrycommunity.propulsion.contents", next.summary()), true);
            return InteractionResult.CONSUME;
        }
        return refused(player);
    }

    static boolean catalogAllowsExchange(RocketPropulsion descriptor, RocketPropulsion.Role role,
            net.minecraft.resources.ResourceLocation fluid, long before, long after, RocketFluidFuelCatalog catalog) {
        // The detached planner has already checked registered identity and lossless container support.
        // A catalog authorizes incoming propulsion, not retrieval of an existing physical Fluid.
        return after < before || after > before && descriptor.settings().allows(role, fluid)
                && catalog.find(role, fluid) != null;
    }
    private static FluidStack asFluid(RocketFuelBank bank) {
        if (bank.branch() != RocketFuelBank.Branch.FLUID || !ForgeRegistries.FLUIDS.containsKey(bank.fluidId())) { return FluidStack.EMPTY; }
        var fluid = new FluidStack(ForgeRegistries.FLUIDS.getValue(bank.fluidId()), Math.toIntExact(bank.amount()), bank.fluidTag());
        return TankSave.safe(fluid) ? fluid : FluidStack.EMPTY;
    }
    private static InteractionResult refused(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.advancedrocketrycommunity.propulsion.exchange_refused"), true);
        return InteractionResult.FAIL;
    }
}
