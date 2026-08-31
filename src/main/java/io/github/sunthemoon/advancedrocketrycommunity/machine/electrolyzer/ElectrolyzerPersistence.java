package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

final class ElectrolyzerPersistence {
    static final String DATA_KEY = "arce_machine";

    private static final String SCHEMA_KEY = "schema_version";
    private static final String INVENTORY_KEY = "inventory";
    private static final String FLUID_KEY = "fluid";
    private static final String ENERGY_KEY = "energy";
    private static final String PROGRESS_KEY = "progress";
    private static final String ACTIVE_RECIPE_KEY = "active_recipe";
    private static final int MAX_MACHINE_NBT_BYTES = 65_536;
    private static final int MAX_RECIPE_ID_LENGTH = 128;

    private ElectrolyzerPersistence() {
    }

    static CompoundTag encode(
            ElectrolyzerInventory inventory,
            ElectrolyzerFluidTank waterTank,
            ElectrolyzerEnergyStorage energyStorage,
            int progress,
            @Nullable ResourceLocation activeRecipeId
    ) {
        CompoundTag machine = new CompoundTag();
        machine.putInt(SCHEMA_KEY, ElectrolyzerRecipeSpec.CURRENT_SCHEMA_VERSION);
        machine.put(INVENTORY_KEY, inventory.serializeNBT());
        machine.put(FLUID_KEY, waterTank.writeToNBT(new CompoundTag()));
        machine.putInt(ENERGY_KEY, energyStorage.getEnergyStored());
        machine.putInt(PROGRESS_KEY, progress);
        if (activeRecipeId != null) {
            machine.putString(ACTIVE_RECIPE_KEY, activeRecipeId.toString());
        }
        if (machine.sizeInBytes() > MAX_MACHINE_NBT_BYTES) {
            throw new IllegalStateException("Electrolyzer persisted data exceeded its 64 KiB bound");
        }
        return machine;
    }

    static DecodeResult decode(CompoundTag parent) {
        if (!parent.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            return DecodeResult.empty();
        }

        CompoundTag machine = parent.getCompound(DATA_KEY);
        if (machine.sizeInBytes() > MAX_MACHINE_NBT_BYTES) {
            return DecodeResult.blockedInvalid();
        }
        int schema = machine.contains(SCHEMA_KEY, Tag.TAG_INT) ? machine.getInt(SCHEMA_KEY) : 0;
        if (schema > ElectrolyzerRecipeSpec.CURRENT_SCHEMA_VERSION) {
            return DecodeResult.future(machine.copy());
        }
        if (schema != ElectrolyzerRecipeSpec.CURRENT_SCHEMA_VERSION) {
            return DecodeResult.blockedInvalid();
        }

        InventoryResult inventory = decodeInventory(machine.getCompound(INVENTORY_KEY));
        FluidResult fluid = decodeFluid(machine.getCompound(FLUID_KEY));
        boolean valid = inventory.valid() && fluid.valid();

        int energy = machine.getInt(ENERGY_KEY);
        if (energy < 0 || energy > ElectrolyzerBlockEntity.ENERGY_CAPACITY) {
            energy = 0;
            valid = false;
        }

        int progress = machine.getInt(PROGRESS_KEY);
        if (progress < 0 || progress >= ElectrolyzerRecipeSpec.MAX_PROCESSING_TICKS) {
            progress = 0;
            valid = false;
        }

        ResourceLocation activeRecipe = null;
        String recipeText = machine.getString(ACTIVE_RECIPE_KEY);
        if (!recipeText.isEmpty()) {
            if (recipeText.length() > MAX_RECIPE_ID_LENGTH) {
                valid = false;
            } else {
                activeRecipe = ResourceLocation.tryParse(recipeText);
                valid &= activeRecipe != null;
            }
        }
        boolean missingActiveRecipe = progress > 0 && activeRecipe == null;
        if (missingActiveRecipe) {
            progress = 0;
            valid = false;
        }

        return new DecodeResult(
                false,
                !valid,
                !valid && !missingActiveRecipe,
                null,
                inventory.stacks(),
                fluid.stack(),
                energy,
                progress,
                activeRecipe
        );
    }

    private static InventoryResult decodeInventory(CompoundTag tag) {
        ItemStack[] stacks = emptyStacks();
        if (!tag.contains("Size", Tag.TAG_INT)
                || tag.getInt("Size") != ElectrolyzerBlockEntity.SLOT_COUNT) {
            return new InventoryResult(false, stacks);
        }

        boolean valid = true;
        boolean[] occupied = new boolean[ElectrolyzerBlockEntity.SLOT_COUNT];
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int index = 0; index < items.size(); index++) {
            CompoundTag itemTag = items.getCompound(index);
            int slot = itemTag.getInt("Slot");
            if (slot < 0 || slot >= ElectrolyzerBlockEntity.SLOT_COUNT || occupied[slot]) {
                valid = false;
                continue;
            }
            occupied[slot] = true;
            ItemStack stack = ItemStack.of(itemTag);
            if (!isPersistedStackValid(slot, stack)) {
                valid = false;
                continue;
            }
            stacks[slot] = stack;
        }
        return new InventoryResult(valid, stacks);
    }

    private static FluidResult decodeFluid(CompoundTag tag) {
        FluidStack loaded = FluidStack.loadFluidStackFromNBT(tag);
        if (loaded.isEmpty()) {
            return new FluidResult(true, FluidStack.EMPTY);
        }
        if (loaded.getFluid() != Fluids.WATER
                || loaded.getAmount() < 0
                || loaded.getAmount() > ElectrolyzerBlockEntity.WATER_CAPACITY
                || loaded.hasTag()) {
            return new FluidResult(false, FluidStack.EMPTY);
        }
        return new FluidResult(true, new FluidStack(Fluids.WATER, loaded.getAmount()));
    }

    private static boolean isPersistedStackValid(int slot, ItemStack stack) {
        if (stack.isEmpty()
                || stack.getCount() < 1
                || stack.getCount() > stack.getMaxStackSize()
                || stack.hasTag()) {
            return false;
        }
        return switch (slot) {
            case ElectrolyzerBlockEntity.SLOT_INPUT -> stack.is(ModItems.EMPTY_CANISTER.get());
            case ElectrolyzerBlockEntity.SLOT_CHARGE -> stack.is(Items.REDSTONE);
            case ElectrolyzerBlockEntity.SLOT_HYDROGEN -> stack.is(ModItems.HYDROGEN_CANISTER.get());
            case ElectrolyzerBlockEntity.SLOT_OXYGEN -> stack.is(ModItems.OXYGEN_CANISTER.get());
            default -> false;
        };
    }

    private static ItemStack[] emptyStacks() {
        ItemStack[] stacks = new ItemStack[ElectrolyzerBlockEntity.SLOT_COUNT];
        java.util.Arrays.fill(stacks, ItemStack.EMPTY);
        return stacks;
    }

    record DecodeResult(
            boolean future,
            boolean invalid,
            boolean blockingInvalid,
            @Nullable CompoundTag preservedFutureData,
            ItemStack[] inventory,
            FluidStack water,
            int energy,
            int progress,
            @Nullable ResourceLocation activeRecipeId
    ) {
        private static DecodeResult empty() {
            return new DecodeResult(
                    false,
                    false,
                    false,
                    null,
                    emptyStacks(),
                    FluidStack.EMPTY,
                    0,
                    0,
                    null
            );
        }

        private static DecodeResult future(CompoundTag data) {
            return new DecodeResult(
                    true,
                    false,
                    false,
                    data,
                    emptyStacks(),
                    FluidStack.EMPTY,
                    0,
                    0,
                    null
            );
        }

        private static DecodeResult blockedInvalid() {
            return new DecodeResult(
                    false,
                    true,
                    true,
                    null,
                    emptyStacks(),
                    FluidStack.EMPTY,
                    0,
                    0,
                    null
            );
        }
    }

    private record InventoryResult(boolean valid, ItemStack[] stacks) {
    }

    private record FluidResult(boolean valid, FluidStack stack) {
    }
}
