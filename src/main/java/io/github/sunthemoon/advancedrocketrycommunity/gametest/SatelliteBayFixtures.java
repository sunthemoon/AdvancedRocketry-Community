package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Shared W27b fixtures: owned packages, schema-1 roots, bays and their drops. */
final class SatelliteBayFixtures {
    static final String BATCH = "satellite_bay";
    static final String TEMPLATE = "rocket_test";

    private SatelliteBayFixtures() {
    }

    /** A data package (ADR-049 section 6: data identities live only in the data package item). */
    static ItemStack dataPackage(UUID owner) {
        ItemStack stack = new ItemStack(ModItems.DATA_SATELLITE_PACKAGE.get());
        SatelliteItemData.write(stack, new SatelliteIdentity(UUID.randomUUID(), owner, ModIdentity.id("w27b_data")));
        return stack;
    }

    /** A non-data package in the generic package item. */
    static ItemStack solarPackage(UUID owner) {
        ItemStack stack = new ItemStack(ModItems.SATELLITE_PACKAGE.get());
        SatelliteItemData.write(stack, new SatelliteIdentity(UUID.randomUUID(), owner, ModIdentity.id("w27b_solar"),
                SatelliteKind.SOLAR, List.of(ModIdentity.id("satellite_solar_module"))));
        return stack;
    }

    static CompoundTag root(UUID owner, @Nullable ItemStack pkg) {
        return SatelliteBayRoot.encode(new SatelliteBayRoot.Contents(owner,
                pkg == null ? null : pkg.copyWithCount(1).serializeNBT(), null));
    }

    static CompoundTag consumedRoot(UUID owner) {
        return SatelliteBayRoot.encode(new SatelliteBayRoot.Contents(owner, null, UUID.randomUUID()));
    }

    /** The outer block-entity tag that carries {@code root}, as a chunk or {@code /data} would supply it. */
    static CompoundTag outer(Tag root) {
        CompoundTag outer = new CompoundTag();
        outer.put(SatelliteBayRoot.KEY, root);
        return outer;
    }

    static SatelliteBayBlockEntity place(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, ModBlocks.ROCKET_SATELLITE_BAY.get());
        return (SatelliteBayBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }

    /** A bay at {@code relative} that holds {@code root}, loaded as an operator's NBT would load it. */
    static SatelliteBayBlockEntity place(GameTestHelper helper, BlockPos relative, Tag root) {
        SatelliteBayBlockEntity bay = place(helper, relative);
        bay.load(outer(root));
        return bay;
    }

    /** Removes a bay without its removal callbacks, so fixture cleanup emits nothing. */
    static void clear(ServerLevel level, BlockPos absolute) {
        level.removeBlockEntity(absolute);
        level.setBlock(absolute, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    static List<ItemEntity> drops(ServerLevel level, BlockPos absolute) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0D));
    }

    static void discardDrops(ServerLevel level, BlockPos absolute) {
        drops(level, absolute).forEach(ItemEntity::discard);
    }

    /** The sorted-key canonical signature of a compound: equal exactly when its content is byte-equal. */
    static String signature(Tag root) {
        return RocketNativeResources.signature((CompoundTag) root);
    }

    /** The bay's saved root through its native {@code saveWithoutMetadata} path. */
    static Tag saved(SatelliteBayBlockEntity bay) {
        return bay.saveWithoutMetadata().get(SatelliteBayRoot.KEY);
    }
}
