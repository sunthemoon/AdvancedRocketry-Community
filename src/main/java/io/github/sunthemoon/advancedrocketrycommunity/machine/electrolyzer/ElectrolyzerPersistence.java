package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.Set;
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
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            SCHEMA_KEY,
            INVENTORY_KEY,
            FLUID_KEY,
            ENERGY_KEY,
            PROGRESS_KEY
    );

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
        if (!parent.contains(DATA_KEY)) {
            return DecodeResult.empty();
        }
        Tag raw = parent.get(DATA_KEY);
        if (!(raw instanceof CompoundTag machine)) {
            return DecodeResult.blockedInvalid(raw);
        }
        if (machine.sizeInBytes() > MAX_MACHINE_NBT_BYTES) {
            return DecodeResult.blockedInvalid(machine);
        }
        if (!machine.contains(SCHEMA_KEY, Tag.TAG_INT)) {
            return DecodeResult.blockedInvalid(machine);
        }
        int schema = machine.getInt(SCHEMA_KEY);
        if (schema > ElectrolyzerRecipeSpec.CURRENT_SCHEMA_VERSION) {
            return DecodeResult.future(machine.copy());
        }
        if (schema != ElectrolyzerRecipeSpec.CURRENT_SCHEMA_VERSION) {
            return DecodeResult.blockedInvalid(machine);
        }

        Set<String> actualFields = machine.getAllKeys();
        boolean validFields = actualFields.equals(REQUIRED_FIELDS)
                || (actualFields.size() == REQUIRED_FIELDS.size() + 1
                && actualFields.containsAll(REQUIRED_FIELDS)
                && actualFields.contains(ACTIVE_RECIPE_KEY));
        boolean validTypes = validFields
                && machine.contains(INVENTORY_KEY, Tag.TAG_COMPOUND)
                && machine.contains(FLUID_KEY, Tag.TAG_COMPOUND)
                && machine.contains(ENERGY_KEY, Tag.TAG_INT)
                && machine.contains(PROGRESS_KEY, Tag.TAG_INT)
                && (!machine.contains(ACTIVE_RECIPE_KEY)
                || machine.contains(ACTIVE_RECIPE_KEY, Tag.TAG_STRING));
        InventoryResult inventory = decodeInventory(machine.getCompound(INVENTORY_KEY));
        FluidResult fluid = decodeFluid(machine.getCompound(FLUID_KEY));
        boolean valid = validTypes && inventory.valid() && fluid.valid();

        int energy = machine.getInt(ENERGY_KEY);
        if (energy < 0 || energy > ElectrolyzerBlockEntity.ENERGY_CAPACITY) {
            energy = 0;
            valid = false;
        }

        int progress = machine.getInt(PROGRESS_KEY);
        if (progress < 0 || progress > ElectrolyzerRecipeSpec.MAX_PROCESSING_TICKS) {
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
        boolean inactiveRecipe = progress == 0 && activeRecipe != null;
        if (missingActiveRecipe || inactiveRecipe) {
            progress = 0;
            activeRecipe = null;
            valid = false;
        }

        return new DecodeResult(
                true,
                false,
                !valid,
                !valid,
                valid ? null : machine.copy(),
                inventory.stacks(),
                fluid.stack(),
                energy,
                progress,
                activeRecipe
        );
    }

    private static InventoryResult decodeInventory(CompoundTag tag) {
        ItemStack[] stacks = emptyStacks();
        if (!tag.getAllKeys().equals(Set.of("Size", "Items"))
                || !tag.contains("Size", Tag.TAG_INT)
                || tag.getInt("Size") != ElectrolyzerBlockEntity.SLOT_COUNT
                || !(tag.get("Items") instanceof ListTag items)
                || items.size() > ElectrolyzerBlockEntity.SLOT_COUNT
                || (!items.isEmpty() && items.getElementType() != Tag.TAG_COMPOUND)) {
            return new InventoryResult(false, stacks);
        }

        boolean valid = true;
        boolean[] occupied = new boolean[ElectrolyzerBlockEntity.SLOT_COUNT];
        for (int index = 0; index < items.size(); index++) {
            CompoundTag itemTag = items.getCompound(index);
            if (!itemTag.contains("Slot", Tag.TAG_INT)) {
                valid = false;
                continue;
            }
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
        if (tag.isEmpty()) {
            return new FluidResult(true, FluidStack.EMPTY);
        }
        if (!tag.getAllKeys().equals(Set.of("FluidName", "Amount"))
                || !tag.contains("FluidName", Tag.TAG_STRING)
                || !tag.contains("Amount", Tag.TAG_INT)) {
            return new FluidResult(false, FluidStack.EMPTY);
        }
        FluidStack loaded = FluidStack.loadFluidStackFromNBT(tag);
        if (loaded.isEmpty()) {
            boolean canonicalEmpty = "minecraft:empty".equals(tag.getString("FluidName"))
                    && tag.getInt("Amount") == 0;
            return new FluidResult(canonicalEmpty, FluidStack.EMPTY);
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
            boolean present,
            boolean future,
            boolean invalid,
            boolean blockingInvalid,
            @Nullable Tag preservedData,
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

        private static DecodeResult blockedInvalid(Tag data) {
            return new DecodeResult(
                    true,
                    false,
                    true,
                    true,
                    data == null ? null : data.copy(),
                    emptyStacks(),
                    FluidStack.EMPTY,
                    0,
                    0,
                    null
            );
        }

        @Nullable
        @Override
        public Tag preservedData() {
            return preservedData == null ? null : preservedData.copy();
        }
    }

    private record InventoryResult(boolean valid, ItemStack[] stacks) {
    }

    private record FluidResult(boolean valid, FluidStack stack) {
    }
}
