package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SpaceBreathingApplication.Refusal;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ADR-066 §5.1 eligibility. Plain JUnit has no mod registry, so the registered vanilla Protection enchantment
 * (also category ARMOR) stands in for applying; the real class is checked for its own restrictions.
 */
class SpaceBreathingApplicationTest {
    private static final Predicate<Item> NONE = item -> false;
    private static Enchantment applied;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); applied = Enchantments.ALL_DAMAGE_PROTECTION; }

    private static Optional<Refusal> refusal(ItemStack stack, EquipmentSlot slot) {
        return SpaceBreathingApplication.refusal(stack, slot, applied, NONE);
    }

    @Test void eligibleArmorKeepsDamageNameEnchantmentsAndUnknownData() {
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        chest.setDamageValue(37); chest.setHoverName(Component.literal("Field plate"));
        chest.enchant(Enchantments.UNBREAKING, 2);
        chest.getOrCreateTag().putString("other_mod", "kept");
        CompoundTag oxygen = new CompoundTag(); oxygen.putInt("schema_version", 1); oxygen.putInt("oxygen_units", 120);
        chest.getOrCreateTag().put(EnchantedSuitOxygen.KEY, oxygen);
        CompoundTag before = chest.serializeNBT();

        ItemStack result = SpaceBreathingApplication.apply(chest, EquipmentSlot.CHEST, applied, NONE);

        assertNotNull(result);
        assertEquals(before, chest.serializeNBT(), "The input stack must stay unchanged");
        assertEquals(1, EnchantmentHelper.getItemEnchantmentLevel(applied, result));
        assertEquals(2, EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, result));
        assertEquals(37, result.getDamageValue());
        assertEquals("Field plate", result.getHoverName().getString());
        assertEquals("kept", result.getTag().getString("other_mod"));
        assertEquals(120, EnchantedSuitOxygen.read(result.getTag()), "A valid oxygen root is not rewritten");
        // Removing the one added enchantment entry restores the exact original stack.
        CompoundTag stripped = result.serializeNBT();
        var list = stripped.getCompound("tag").getList("Enchantments", 10);
        assertEquals(2, list.size());
        list.remove(1);
        assertEquals(before, stripped);
    }

    @Test void everyNativeArmorSlotIsEligibleOnlyInItsOwnRecipe() {
        var pieces = new Object[][]{{Items.LEATHER_HELMET, EquipmentSlot.HEAD}, {Items.TURTLE_HELMET, EquipmentSlot.HEAD},
                {Items.DIAMOND_CHESTPLATE, EquipmentSlot.CHEST}, {Items.GOLDEN_LEGGINGS, EquipmentSlot.LEGS},
                {Items.NETHERITE_BOOTS, EquipmentSlot.FEET}};
        for (Object[] piece : pieces) {
            ItemStack stack = new ItemStack((Item) piece[0]);
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                assertEquals(slot == piece[1] ? Optional.empty() : Optional.of(Refusal.WRONG_SLOT), refusal(stack, slot), piece[0] + " " + slot);
            }
        }
    }

    @Test void ineligibleStacksAreRefusedWithAReasonAndLeftUnchanged() {
        assertEquals(Optional.of(Refusal.UNSUPPORTED_STACK), refusal(ItemStack.EMPTY, EquipmentSlot.CHEST));
        assertEquals(Optional.of(Refusal.NOT_NATIVE_ARMOR), refusal(new ItemStack(Items.ELYTRA), EquipmentSlot.CHEST));
        assertEquals(Optional.of(Refusal.UNSUPPORTED_STACK), refusal(new ItemStack(Items.CARVED_PUMPKIN), EquipmentSlot.HEAD),
                "A stackable wearable is refused before any armor check");
        assertEquals(Optional.of(Refusal.NOT_NATIVE_ARMOR), refusal(new ItemStack(Items.SADDLE), EquipmentSlot.HEAD),
                "An unstackable non-armor Item is refused");
        assertEquals(Optional.of(Refusal.PROVIDER_CLAIMED), SpaceBreathingApplication.refusal(new ItemStack(Items.IRON_HELMET),
                EquipmentSlot.HEAD, applied, item -> item == Items.IRON_HELMET));

        ItemStack sealed = new ItemStack(Items.IRON_BOOTS); sealed.enchant(applied, 1);
        CompoundTag sealedBefore = sealed.serializeNBT();
        assertEquals(Optional.of(Refusal.ALREADY_SEALED), refusal(sealed, EquipmentSlot.FEET));
        assertNull(SpaceBreathingApplication.apply(sealed, EquipmentSlot.FEET, applied, NONE));
        assertEquals(sealedBefore, sealed.serializeNBT());
        ItemStack higher = new ItemStack(Items.IRON_BOOTS); higher.enchant(applied, 3);
        assertEquals(Optional.of(Refusal.ALREADY_SEALED), refusal(higher, EquipmentSlot.FEET));

        ItemStack conflicting = new ItemStack(Items.IRON_LEGGINGS); conflicting.enchant(Enchantments.FIRE_PROTECTION, 1);
        assertEquals(Optional.of(Refusal.INCOMPATIBLE_ENCHANTMENT), refusal(conflicting, EquipmentSlot.LEGS));

        ItemStack corruptOxygen = new ItemStack(Items.IRON_CHESTPLATE);
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 2); root.putInt("oxygen_units", 10);
        corruptOxygen.getOrCreateTag().put(EnchantedSuitOxygen.KEY, root);
        CompoundTag corruptBefore = corruptOxygen.serializeNBT();
        assertEquals(Optional.of(Refusal.UNSUPPORTED_OXYGEN_DATA), refusal(corruptOxygen, EquipmentSlot.CHEST));
        assertEquals(corruptBefore, corruptOxygen.serializeNBT());

        ItemStack oversized = new ItemStack(Items.IRON_HELMET);
        oversized.getOrCreateTag().putString("padding", "x".repeat(20_000));
        assertEquals(Optional.of(Refusal.UNSUPPORTED_STACK), refusal(oversized, EquipmentSlot.HEAD));
    }

    @Test void theRealEnchantmentRefusesTablesBooksTradesAndDiscoveryButFitsArmorCategory() {
        var enchantment = new SpaceBreathingEnchantment();
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        assertTrue(enchantment.isTreasureOnly());
        assertFalse(enchantment.isTradeable());
        assertFalse(enchantment.isDiscoverable());
        assertFalse(enchantment.isAllowedOnBooks());
        assertFalse(enchantment.canApplyAtEnchantingTable(chest));
        // Forge routes canEnchant (anvils) to the table check, so only the chemical recipe applies it.
        assertFalse(enchantment.canEnchant(chest));
        assertTrue(enchantment.category.canEnchant(Items.IRON_CHESTPLATE));
        assertEquals(1, enchantment.getMaxLevel());
    }

    @Test void aConsumedTankMustHoldASupportedEmptyRoot() {
        ItemStack fresh = new ItemStack(Items.SADDLE);
        assertTrue(SpaceBreathingApplication.gasFree(fresh, 8_000), "A tank without a root is empty");
        ItemStack empty = new ItemStack(Items.SADDLE);
        empty.getOrCreateTag().put(TankData.KEY, new TankData(TankData.Gas.EMPTY, 0, 8_000).encode());
        assertTrue(SpaceBreathingApplication.gasFree(empty, 8_000));
        ItemStack oxygen = new ItemStack(Items.SADDLE);
        oxygen.getOrCreateTag().put(TankData.KEY, new TankData(TankData.Gas.OXYGEN, 1, 8_000).encode());
        assertFalse(SpaceBreathingApplication.gasFree(oxygen, 8_000), "A recipe never destroys stored gas");
        ItemStack drained = new ItemStack(Items.SADDLE);
        drained.getOrCreateTag().put(TankData.KEY, new TankData(TankData.Gas.HYDROGEN, 0, 8_000).encode());
        assertTrue(SpaceBreathingApplication.gasFree(drained, 8_000), "A drained tank keeps its selection but holds no gas");
        ItemStack corrupt = new ItemStack(Items.SADDLE);
        corrupt.getOrCreateTag().putString(TankData.KEY, "empty");
        assertFalse(SpaceBreathingApplication.gasFree(corrupt, 8_000), "An unsupported root is preserved");
    }
}
