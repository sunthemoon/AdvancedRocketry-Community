package io.github.sunthemoon.advancedrocketrycommunity.classiccomponent;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Four inert crafting/structure blocks; no entity, energy, tier-speed or saved-state adapter. */
public final class MotorContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            ModIdentity.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            ModIdentity.MOD_ID);
    private static final Map<MotorDefinition, RegistryObject<Block>> BLOCK_ENTRIES;
    private static final Map<MotorDefinition, RegistryObject<Item>> ITEM_ENTRIES;
    public static final TagKey<Block> BLOCK_TAG = TagKey.create(Registries.BLOCK, ModIdentity.id("motors"));
    public static final TagKey<Item> ITEM_TAG = TagKey.create(Registries.ITEM, ModIdentity.id("motors"));

    static {
        Map<MotorDefinition, RegistryObject<Block>> blocks = new EnumMap<>(MotorDefinition.class);
        Map<MotorDefinition, RegistryObject<Item>> items = new EnumMap<>(MotorDefinition.class);
        for (MotorDefinition definition : MotorDefinition.values()) {
            RegistryObject<Block> block = BLOCKS.register(definition.id(), () -> new Block(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops()
                            .strength(5.0F, 6.0F).sound(SoundType.METAL)));
            blocks.put(definition, block);
            items.put(definition, ITEMS.register(definition.id(), () -> new BlockItem(block.get(), new Item.Properties())));
        }
        BLOCK_ENTRIES = Map.copyOf(blocks);
        ITEM_ENTRIES = Map.copyOf(items);
    }

    private MotorContent() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    public static RegistryObject<Block> block(MotorDefinition definition) { return BLOCK_ENTRIES.get(definition); }
    public static RegistryObject<Item> item(MotorDefinition definition) { return ITEM_ENTRIES.get(definition); }
    public static ResourceLocation id(MotorDefinition definition) { return ModIdentity.id(definition.id()); }
}
