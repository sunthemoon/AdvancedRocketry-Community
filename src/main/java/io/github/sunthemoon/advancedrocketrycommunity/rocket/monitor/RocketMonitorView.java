package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketFuelBank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.registries.ForgeRegistries;

/** Kind-4 scalar projection only. Native resource tags and world positions never enter the packet. */
public final class RocketMonitorView {
    private RocketMonitorView() { }
    public static List<Integer> values(RocketMonitorBlockEntity monitor, RocketMonitorQuote quote, int selected, int count) {
        var resolved = RocketMonitorBinding.resolve(monitor, false);
        var flight = resolved.rocket() == null ? null : resolved.rocket().flightData().orElse(null);
        var root = monitor.root();
        var values = new ArrayList<Integer>(21);
        var refusal = quote != null ? quote.code() : monitor.lastFlight();
        values.add(resolved.status().ordinal()); values.add(flight == null ? -1 : flight.state().networkId());
        values.add(monitor.height()); values.add(monitor.velocity()); values.add(selected); values.add(count);
        values.add(root == null ? 0 : root.mode()); values.add(root != null && root.armed() ? 1 : 0);
        values.add(refusal == null ? -1 : refusal.ordinal());
        values.addAll(fuelValues(flight == null ? null : flight.fuel(), quote));
        return List.copyOf(values);
    }
    static List<Integer> fuelValues(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState fuel,
            RocketMonitorQuote quote) {
        var values = new ArrayList<Integer>(12);
        var nativeFuel = fuel == null ? null : fuel.propulsion().orElse(null);
        int banks = fuel == null ? 0 : nativeFuel != null && nativeFuel.descriptor().oxidizerCapacity() > 0 ? 2 : 1;
        values.add(banks);
        var required = quote == null ? io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketRequiredCosts.UNKNOWN : quote.requiredCosts();
        bank(values, banks == 0 ? -1 : nativeFuel == null ? 0 : role(nativeFuel.descriptor().primaryRole()),
                fuel == null ? 0 : fuel.amount(), fuel == null ? 0 : fuel.capacity(), required.primary(),
                nativeFuel == null ? null : nativeFuel.primary());
        bank(values, banks < 2 ? -1 : 3, banks < 2 ? 0 : nativeFuel.oxidizer().amount(),
                banks < 2 ? 0 : nativeFuel.descriptor().oxidizerCapacity(), required.oxidizer(),
                banks < 2 ? null : nativeFuel.oxidizer());
        values.add(Math.toIntExact(required.primary())); return List.copyOf(values);
    }
    public static net.minecraft.network.chat.Component costText(int required) {
        return required < 0 ? net.minecraft.network.chat.Component.translatable("screen.advancedrocketrycommunity.monitor.flight.-1")
                : net.minecraft.network.chat.Component.literal(Integer.toString(required));
    }
    private static int role(RocketPropulsion.Role role) {
        return switch (role) { case MONO -> 1; case BIPROPELLANT_FUEL -> 2; case OXIDIZER -> 3; case WORKING_FLUID -> 4; };
    }
    private static void bank(List<Integer> values, int role, long amount, long capacity, long required, RocketFuelBank bank) {
        int fluid = -1;
        if (bank != null && bank.fluidId() != null) {
            var registered = ForgeRegistries.FLUIDS.getValue(bank.fluidId());
            if (registered != null) { fluid = BuiltInRegistries.FLUID.getId(registered); }
        }
        values.add(role); values.add(Math.toIntExact(amount)); values.add(Math.toIntExact(capacity));
        values.add(Math.toIntExact(required)); values.add(fluid);
    }
}
