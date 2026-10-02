package io.github.sunthemoon.advancedrocketrycommunity.material;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Kind;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Material;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Product;
import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressBlock;
import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.material.worldgen.SwitchPlacement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Forge registration of the classic material set, its ores and the small plate press (ADR-063 sections 1–4). */
public final class MaterialContent {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            AdvancedRocketryCommunity.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            AdvancedRocketryCommunity.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(
            ForgeRegistries.RECIPE_TYPES, AdvancedRocketryCommunity.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(
            ForgeRegistries.RECIPE_SERIALIZERS, AdvancedRocketryCommunity.MOD_ID);
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS = DeferredRegister.create(
            Registries.PLACEMENT_MODIFIER_TYPE, AdvancedRocketryCommunity.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, AdvancedRocketryCommunity.MOD_ID);

    private static final Map<String, RegistryObject<Block>> BLOCK_ENTRIES = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<Item>> ITEM_ENTRIES = new LinkedHashMap<>();

    static {
        for (Entry entry : MaterialCatalog.entries()) {
            if (entry.isBlock()) {
                RegistryObject<Block> block = BLOCKS.register(entry.id(), () -> createBlock(entry));
                BLOCK_ENTRIES.put(entry.id(), block);
                ITEM_ENTRIES.put(entry.id(), ITEMS.register(entry.id(),
                        () -> new BlockItem(block.get(), new Item.Properties())));
            } else {
                ITEM_ENTRIES.put(entry.id(), ITEMS.register(entry.id(), () -> new Item(new Item.Properties())));
            }
        }
    }

    public static final RegistryObject<Block> SMALL_PLATE_PRESS = BLOCKS.register("small_plate_press",
            () -> new SmallPlatePressBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(4.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
    public static final RegistryObject<Item> SMALL_PLATE_PRESS_ITEM = ITEMS.register("small_plate_press",
            () -> new BlockItem(SMALL_PLATE_PRESS.get(), new Item.Properties()));

    public static final RegistryObject<RecipeType<SmallPlatePressRecipe>> SMALL_PLATE_PRESS_TYPE =
            RECIPE_TYPES.register("small_plate_press", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return AdvancedRocketryCommunity.MOD_ID + ":small_plate_press";
                }
            });
    public static final RegistryObject<RecipeSerializer<SmallPlatePressRecipe>> SMALL_PLATE_PRESS_SERIALIZER =
            RECIPE_SERIALIZERS.register("small_plate_press", SmallPlatePressRecipe.Serializer::new);

    public static final RegistryObject<PlacementModifierType<SwitchPlacement>> SWITCH_PLACEMENT =
            PLACEMENT_MODIFIERS.register("server_switch", () -> () -> SwitchPlacement.CODEC);

    public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_TABS.register("materials",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.advancedrocketrycommunity.materials"))
                    .icon(() -> new ItemStack(item("titanium_ingot")))
                    .displayItems((parameters, output) -> {
                        ITEM_ENTRIES.values().forEach(entry -> output.accept(entry.get()));
                        output.accept(SMALL_PLATE_PRESS_ITEM.get());
                    })
                    .build());

    private MaterialContent() {
    }

    private static Block createBlock(Entry entry) {
        Material material = entry.material();
        if (entry.kind() == Kind.STONE_ORE || entry.kind() == Kind.DEEPSLATE_ORE) {
            boolean deepslate = entry.kind() == Kind.DEEPSLATE_ORE;
            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                    .mapColor(deepslate ? MapColor.DEEPSLATE : MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(deepslate ? 4.5F : 3.0F, 3.0F)
                    .sound(deepslate ? SoundType.DEEPSLATE : SoundType.STONE);
            // No ore gives mining experience: each drops itself or a raw item, and its smelting recipe gives the
            // experience. Dilithium ore drops itself, so experience on breaking would repeat forever (C15aR1-M1).
            return new Block(properties);
        }
        boolean coil = entry.product().filter(product -> product == Product.COIL).isPresent();
        return new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops().strength(coil ? 3.0F : 5.0F, 6.0F).sound(SoundType.METAL));
    }

    /** A registered material block by registry path. */
    public static Block block(String id) {
        RegistryObject<Block> block = BLOCK_ENTRIES.get(id);
        if (block == null) {
            throw new IllegalArgumentException("Unknown material block " + id);
        }
        return block.get();
    }

    /** A registered material item (or block item) by registry path. */
    public static Item item(String id) {
        RegistryObject<Item> item = ITEM_ENTRIES.get(id);
        if (item == null) {
            throw new IllegalArgumentException("Unknown material item " + id);
        }
        return item.get();
    }

    public static Optional<Item> product(Material material, Product product) {
        return material.productId(product).map(MaterialContent::item);
    }

    /** Registry paths of every material block, in registration order. */
    public static Map<String, RegistryObject<Block>> blocks() {
        return Collections.unmodifiableMap(BLOCK_ENTRIES);
    }

    /** Registry paths of every material item, in registration order. */
    public static Map<String, RegistryObject<Item>> items() {
        return Collections.unmodifiableMap(ITEM_ENTRIES);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        PLACEMENT_MODIFIERS.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
}
