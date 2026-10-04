package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

/** Three exact gas/item bindings; no tags or arbitrary fluid payload conversion. */
public record GasCanisterCatalog(Item empty, List<Entry> gases) {
    public GasCanisterCatalog {
        Objects.requireNonNull(empty, "empty");
        gases = List.copyOf(gases);
        if (gases.isEmpty() || gases.size() > 3) {
            throw new IllegalArgumentException("A gas canister catalog needs one to three gases");
        }
        for (int index = 0; index < gases.size(); index++) {
            Entry entry = gases.get(index);
            if (entry.item() == empty) { throw new IllegalArgumentException("Filled item equals empty item"); }
            for (int previous = 0; previous < index; previous++) {
                Entry other = gases.get(previous);
                if (entry.item() == other.item() || entry.fluid() == other.fluid()) {
                    throw new IllegalArgumentException("Duplicate canister catalog binding");
                }
            }
        }
    }

    public static GasCanisterCatalog registered() {
        return new GasCanisterCatalog(ModItems.EMPTY_CANISTER.get(), List.of(
                new Entry(ModItems.OXYGEN_CANISTER.get(), ClassicFluids.OXYGEN.get()),
                new Entry(ModItems.HYDROGEN_CANISTER.get(), ClassicFluids.HYDROGEN.get()),
                new Entry(ClassicFluids.NITROGEN_CANISTER.get(), ClassicFluids.NITROGEN.get())));
    }

    public Entry forItem(Item item) {
        for (Entry gas : gases) { if (gas.item() == item) { return gas; } }
        return null;
    }

    public Entry forFluid(Fluid fluid) {
        for (Entry gas : gases) { if (gas.fluid() == fluid) { return gas; } }
        return null;
    }

    public record Entry(Item item, Fluid fluid) {
        public Entry {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(fluid, "fluid");
        }
    }
}
