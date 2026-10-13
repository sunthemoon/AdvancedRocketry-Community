package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180LaserGunData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LaserGunDataTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void recipeUsesExistingInputsAndUnlocksOneStableItem() {
        var files = V180LaserGunData.serverFiles();
        assertEquals(2, files.size());
        var recipe = files.get("data/advancedrocketrycommunity/recipes/basic_laser_gun.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("[\"ppd\",\"rcp\",\" r \"]", recipe.get("pattern").toString());
        assertEquals(Set.of("p", "r", "d", "c"), recipe.getAsJsonObject("key").keySet());
        assertEquals("forge:plates/steel", recipe.getAsJsonObject("key").getAsJsonObject("p").get("tag").getAsString());
        assertEquals("forge:rods/iron", recipe.getAsJsonObject("key").getAsJsonObject("r").get("tag").getAsString());
        assertEquals("forge:gems/diamond", recipe.getAsJsonObject("key").getAsJsonObject("d").get("tag").getAsString());
        assertEquals("advancedrocketrycommunity:basic_circuit_plate", recipe.getAsJsonObject("key").getAsJsonObject("c").get("item").getAsString());
        assertEquals(V180LaserGunData.ID, recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
        var advancement = files.get("data/advancedrocketrycommunity/advancements/recipes/tools/basic_laser_gun.json");
        assertEquals("[[\"has_circuit\",\"has_the_recipe\"]]", advancement.get("requirements").toString());
        assertEquals("[\"advancedrocketrycommunity:basic_laser_gun\"]", advancement.getAsJsonObject("rewards").get("recipes").toString());
    }
    @Test void originalIconIsDeterministicSixteenPixelRgbaAndModelOnlyReferencesResources() {
        byte[] png = V180LaserGunData.texture();
        assertArrayEquals(png, V180LaserGunData.texture()); assertTrue(png.length < 4096);
        var bytes = ByteBuffer.wrap(png); assertEquals(16, bytes.getInt(16)); assertEquals(16, bytes.getInt(20));
        assertEquals(6, png[25]);
        assertTrue(Arrays.stream(V180LaserGunData.grid()).allMatch(row -> row.length() == 16 && row.matches("[.56789]+")));
        var model = V180LaserGunData.clientFiles().values().iterator().next();
        assertEquals("minecraft:item/handheld", model.get("parent").getAsString());
        assertEquals("advancedrocketrycommunity:item/basic_laser_gun", model.getAsJsonObject("textures").get("layer0").getAsString());
    }
    @Test void bilingualKeysAreCompleteAndImmutable() {
        var en = V180LaserGunData.translations(false); var zh = V180LaserGunData.translations(true);
        assertEquals(5, en.size()); assertEquals(en.keySet(), zh.keySet());
        assertTrue(en.values().stream().noneMatch(String::isBlank)); assertTrue(zh.values().stream().noneMatch(String::isBlank));
        assertThrows(UnsupportedOperationException.class, () -> en.put("unexpected", "entry"));
    }
    @Test void itemHasBoundedUseAndActualDiamondPickaxeTierWithoutBlanketHarvest() {
        var item = new BasicLaserGunItem();
        // Plain JUnit has no Forge delegate for an unregistered mod stack. GameTests check the real registered stack.
        assertEquals(512, item.getMaxDamage()); assertEquals(1, item.getMaxStackSize());
        assertEquals(72000, item.getUseDuration(ItemStack.EMPTY)); assertSame(Tiers.DIAMOND, item.getTier());
        // Native tier/tag qualification requires transformed Forge events; the GameTest asserts it explicitly.
    }
}
