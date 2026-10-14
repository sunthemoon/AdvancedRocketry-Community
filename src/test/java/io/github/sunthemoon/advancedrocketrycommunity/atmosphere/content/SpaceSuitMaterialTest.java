package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpaceSuitMaterialTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyGameplayPropertyMatchesTheFormerIronMaterial() {
        SpaceSuitMaterial material = SpaceSuitMaterial.INSTANCE;
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            assertEquals(ArmorMaterials.IRON.getDurabilityForType(type), material.getDurabilityForType(type), type.name());
            assertEquals(ArmorMaterials.IRON.getDefenseForType(type), material.getDefenseForType(type), type.name());
        }
        assertEquals(ArmorMaterials.IRON.getEnchantmentValue(), material.getEnchantmentValue());
        assertEquals(ArmorMaterials.IRON.getToughness(), material.getToughness());
        assertEquals(ArmorMaterials.IRON.getKnockbackResistance(), material.getKnockbackResistance());
        assertSame(ArmorMaterials.IRON.getEquipSound(), material.getEquipSound());
        assertEquals(1, material.getRepairIngredient().getItems().length);
        assertTrue(material.getRepairIngredient().test(new ItemStack(Items.IRON_INGOT)));
        assertEquals("advancedrocketrycommunity:space_suit", material.getName());
    }

    @Test void suitAdapterPreservesItsPropertiesAndBothTextureLayersExist() {
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            SpaceSuitArmorItem item = new SpaceSuitArmorItem(SpaceSuitMaterial.INSTANCE, type, new Item.Properties());
            assertInstanceOf(ArmorItem.class, item);
            assertSame(SpaceSuitMaterial.INSTANCE, item.getMaterial());
            assertEquals(type, item.getType());
            assertEquals(1, item.getMaxStackSize());
            assertEquals(ArmorMaterials.IRON.getDurabilityForType(type), item.getMaxDamage());
            int layer = type == ArmorItem.Type.LEGGINGS ? 2 : 1;
            String location = "advancedrocketrycommunity:textures/models/armor/space_suit_layer_" + layer + ".png";
            // JUnit has no delegate for this unregistered fixture; GameTest checks real registered stacks.
            ItemStack stack = ItemStack.EMPTY;
            assertEquals(location, item.getArmorTexture(stack, null, item.getEquipmentSlot(), null));
            assertEquals(location, item.getArmorTexture(stack, null, item.getEquipmentSlot(), "overlay"));
            assertTrue(Files.isRegularFile(Path.of("src/generated/v1.8/resources/assets/"
                    + location.replace(':', '/'))));
        }
    }
}
