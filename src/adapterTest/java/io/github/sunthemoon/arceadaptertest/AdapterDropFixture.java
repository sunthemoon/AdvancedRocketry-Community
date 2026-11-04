package io.github.sunthemoon.arceadaptertest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

/** Unfiltered item observations for the adapter fixtures' original assertion regions. */
final class AdapterDropFixture {
    static void assertNoDrops(GameTestHelper helper, AABB region, String message) {
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, region);
        helper.assertTrue(drops.isEmpty(), message + ": " + drops.stream()
                .map(item -> item.getItem() + " type=" + ForgeRegistries.ITEMS.getKey(item.getItem().getItem())
                        + " count=" + item.getItem().getCount() + " at " + item.position() + " age=" + item.getAge())
                .toList());
    }

    private AdapterDropFixture() { }
}
