package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.CarbonCartridge;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.SuitEquipmentRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SpaceBreathingApplication;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitModuleItem;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The closed W32 special-recipe extension of the chemical reactor (ADR-064 §9.2). Item input row 0 is the subject:
 * the exact debited stack returns in the same transaction with only the named change, never a fresh output Item.
 */
public enum ClassicSpecialRecipe {
    SPACE_BREATHING_HEAD("space_breathing_head", EquipmentSlot.HEAD),
    SPACE_BREATHING_CHEST("space_breathing_chest", EquipmentSlot.CHEST),
    SPACE_BREATHING_LEGS("space_breathing_legs", EquipmentSlot.LEGS),
    SPACE_BREATHING_FEET("space_breathing_feet", EquipmentSlot.FEET),
    CARBON_CARTRIDGE_REFRESH("carbon_cartridge_refresh", null);

    private final String id;
    private final EquipmentSlot slot;

    ClassicSpecialRecipe(String id, EquipmentSlot slot) { this.id = id; this.slot = slot; }

    public String id() { return id; }
    public static Optional<ClassicSpecialRecipe> of(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }

    /** ADR-066 §2: a disabled family produces no new work; a running job pauses with its progress kept. */
    boolean enabled() { return slot == null ? CommonConfig.classicDevicesEnabled() : CommonConfig.classicEquipmentEnabled(); }

    /** Whether the batch may debit this stack: the subject needs a transform, and no consumed tank holds gas. */
    boolean admits(ItemStack stack, boolean subject) {
        if (subject) { return transform(stack) != null; }
        return !(stack.getItem() instanceof SuitModuleItem module) || module.tankBase() == 0
                || SpaceBreathingApplication.gasFree(stack, module.tankBase());
    }

    /** The detached changed subject, or null when the stack is not eligible. */
    ItemStack transform(ItemStack subject) {
        if (slot == null) { return CarbonCartridge.refresh(subject); }
        return SpaceBreathingApplication.apply(subject, slot, SuitEquipmentContent.SPACE_BREATHING.get(), SuitEquipmentRuntime::claimed);
    }

    /** Display only (JEI): the change applied to a representative stack, without any eligibility decision. */
    public ItemStack preview(ItemStack subject) {
        ItemStack result = subject.copy();
        if (slot == null) { CarbonCartridge.withUsesForDisplay(result, CarbonCartridge.MAX_USES); }
        else { result.enchant(SuitEquipmentContent.SPACE_BREATHING.get(), 1); }
        return result;
    }

    /** Display only (JEI): the subject as the recipe expects it, a spent cartridge or the plain armor piece. */
    public ItemStack previewInput(ItemStack subject) {
        ItemStack result = subject.copy();
        if (slot == null) { CarbonCartridge.withUsesForDisplay(result, 0); }
        return result;
    }
}
