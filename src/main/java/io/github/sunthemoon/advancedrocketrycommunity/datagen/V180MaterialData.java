package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Kind;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Material;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Product;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialTags;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** v1.8 loot tables and tags of the material set and the small plate press (ADR-061 section 2, ADR-063). */
public final class V180MaterialData {
    private V180MaterialData() {
    }

    public static LootTableProvider loot(PackOutput output) {
        return new LootTableProvider(output, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(MaterialLoot::new, LootContextParamSets.BLOCK)));
    }

    private static final class MaterialLoot extends BlockLootSubProvider {
        private MaterialLoot() {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags());
        }

        @Override
        protected void generate() {
            for (Entry entry : MaterialCatalog.entries()) {
                if (!entry.isBlock()) {
                    continue;
                }
                Block block = MaterialContent.block(entry.id());
                boolean ore = entry.kind() == Kind.STONE_ORE || entry.kind() == Kind.DEEPSLATE_ORE;
                if (ore && entry.material().rawId().isPresent()) {
                    add(block, createOreDrop(block, MaterialContent.item(entry.material().rawId().orElseThrow())));
                } else {
                    // Storage blocks, coils and the dilithium ores (which drop themselves and smelt to dust).
                    dropSelf(block);
                }
            }
            dropSelf(MaterialContent.SMALL_PLATE_PRESS.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            List<Block> blocks = new ArrayList<>();
            MaterialContent.blocks().values().forEach(block -> blocks.add(block.get()));
            blocks.add(MaterialContent.SMALL_PLATE_PRESS.get());
            return blocks;
        }
    }

    /**
     * Block tags. The two vanilla tool tags are complete v1.8 copies that supersede the v1.7 copies (ADR-063 section
     * 8); {@code needs_stone_tool} is new in v1.8.
     */
    public static final class Blocks extends BlockTagsProvider {
        public Blocks(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                      ExistingFileHelper existingFiles) {
            super(output, lookupProvider, AdvancedRocketryCommunity.MOD_ID, existingFiles);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            Block[] earlier = {
                    ModBlocks.MACHINE_CASING.get(),
                    ModBlocks.SATELLITE_TERMINAL.get(),
                    ModBlocks.ROLLING_MACHINE.get(),
                    ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get(),
                    ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get(),
                    ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT.get(),
                    ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get(),
                    ModBlocks.PRECISION_ASSEMBLER.get(),
                    ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get(),
                    ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get(),
                    ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get(),
                    ModBlocks.WARP_CORE.get(),
                    ModBlocks.SATELLITE_BUILDER.get(),
                    ModBlocks.MICROWAVE_RECEIVER.get(),
                    ModBlocks.ENDGAME_CASING.get(),
                    ModBlocks.ORBITAL_LASER_DRILL.get(),
                    ModBlocks.LASER_TARGET.get(),
                    ModBlocks.GRAVITY_FIELD_CONTROLLER.get(),
                    ModBlocks.BLACK_HOLE_GENERATOR.get(),
                    ModBlocks.RAILGUN.get(),
                    ModBlocks.ELEVATOR_ANCHOR.get(),
                    ModBlocks.ELEVATOR_TERMINAL.get()
            };
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(earlier);
            tag(BlockTags.NEEDS_IRON_TOOL).add(earlier);
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(MaterialContent.SMALL_PLATE_PRESS.get());
            tag(BlockTags.NEEDS_STONE_TOOL).add(MaterialContent.SMALL_PLATE_PRESS.get());
            for (Entry entry : MaterialCatalog.entries()) {
                if (!entry.isBlock()) {
                    continue;
                }
                Block block = MaterialContent.block(entry.id());
                Material material = entry.material();
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block);
                if (entry.kind() == Kind.STONE_ORE || entry.kind() == Kind.DEEPSLATE_ORE) {
                    boolean hard = material == Material.TITANIUM || material == Material.IRIDIUM
                            || material == Material.DILITHIUM;
                    tag(hard ? BlockTags.NEEDS_IRON_TOOL : BlockTags.NEEDS_STONE_TOOL).add(block);
                    for (ResourceLocation ore : MaterialTags.oreTags(material)) {
                        tag(blockTag(ore)).add(block);
                        tag(Tags.Blocks.ORES).addTag(blockTag(ore));
                    }
                    tag(entry.kind() == Kind.STONE_ORE ? Tags.Blocks.ORES_IN_GROUND_STONE
                            : Tags.Blocks.ORES_IN_GROUND_DEEPSLATE).add(block);
                } else {
                    tag(BlockTags.NEEDS_STONE_TOOL).add(block);
                    Product product = entry.product().orElseThrow();
                    TagKey<Block> own = MaterialTags.block(material, product);
                    tag(own).add(block);
                    // The coil umbrella is the advancedrocketrycommunity:coils group (ADR-063 section 1).
                    tag(blockTag(MaterialTags.umbrellaTag(product))).addTag(own);
                }
            }
        }
    }

    /** Item tags: every product under its Forge or project tag and umbrella (the coils group is the coil umbrella). */
    public static final class Items extends ItemTagsProvider {
        public Items(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                     CompletableFuture<TagLookup<Block>> blockTags, ExistingFileHelper existingFiles) {
            super(output, lookupProvider, blockTags, AdvancedRocketryCommunity.MOD_ID, existingFiles);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            for (Entry entry : MaterialCatalog.entries()) {
                Item item = MaterialContent.item(entry.id());
                Material material = entry.material();
                switch (entry.kind()) {
                    case STONE_ORE, DEEPSLATE_ORE -> {
                        for (ResourceLocation ore : MaterialTags.oreTags(material)) {
                            tag(itemTag(ore)).add(item);
                            tag(Tags.Items.ORES).addTag(itemTag(ore));
                        }
                        tag(entry.kind() == Kind.STONE_ORE ? Tags.Items.ORES_IN_GROUND_STONE
                                : Tags.Items.ORES_IN_GROUND_DEEPSLATE).add(item);
                    }
                    case RAW -> {
                        tag(MaterialTags.rawItem(material)).add(item);
                        tag(Tags.Items.RAW_MATERIALS).addTag(MaterialTags.rawItem(material));
                    }
                    case PRODUCT -> {
                        Product product = entry.product().orElseThrow();
                        TagKey<Item> own = MaterialTags.item(material, product);
                        tag(own).add(item);
                        tag(itemTag(MaterialTags.umbrellaTag(product))).addTag(own);
                    }
                }
            }
        }
    }

    private static TagKey<Block> blockTag(ResourceLocation id) {
        return TagKey.create(Registries.BLOCK, id);
    }

    private static TagKey<Item> itemTag(ResourceLocation id) {
        return TagKey.create(Registries.ITEM, id);
    }
}
