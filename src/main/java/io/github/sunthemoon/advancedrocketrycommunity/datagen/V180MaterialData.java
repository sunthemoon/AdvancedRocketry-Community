package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.SurfaceWorldgen;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Kind;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Material;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Product;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialTags;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

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
            dropSelf(ModBlocks.COMBUSTION_GENERATOR.get());
            // C15b surface blocks drop themselves (ADR-063 section 5), except the charcoal log, which drops one
            // charcoal as the legacy log did: itself with Silk Touch, and Fortune adds up to its level.
            for (RegistryObject<Block> block : SurfaceContent.blocks()) {
                if (block != SurfaceContent.CHARCOAL_LOG) {
                    dropSelf(block.get());
                }
            }
            Block log = SurfaceContent.CHARCOAL_LOG.get();
            add(log, createSilkTouchDispatchTable(log, applyExplosionDecay(log, LootItem.lootTableItem(
                    net.minecraft.world.item.Items.CHARCOAL)
                    .apply(ApplyBonusCount.addUniformBonusCount(Enchantments.BLOCK_FORTUNE)))));
            // C15c blocks (ADR-063 section 6) drop themselves, except the lightwood leaves: a sapling one time in a
            // hundred and nothing else (legacy), or the leaves themselves with shears or Silk Touch.
            for (RegistryObject<Block> block : ExoplanetBlocks.blocks()) {
                if (block != ExoplanetBlocks.LIGHTWOOD_LEAVES) {
                    dropSelf(block.get());
                }
            }
            Block leaves = ExoplanetBlocks.LIGHTWOOD_LEAVES.get();
            add(leaves, createSilkTouchOrShearsDispatchTable(leaves, applyExplosionCondition(leaves,
                    LootItem.lootTableItem(ExoplanetBlocks.LIGHTWOOD_SAPLING.get()))
                    .when(LootItemRandomChanceCondition.randomChance(0.01F))));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            List<Block> blocks = new ArrayList<>();
            MaterialContent.blocks().values().forEach(block -> blocks.add(block.get()));
            blocks.add(MaterialContent.SMALL_PLATE_PRESS.get());
            blocks.add(ModBlocks.COMBUSTION_GENERATOR.get());
            SurfaceContent.blocks().forEach(block -> blocks.add(block.get()));
            ExoplanetBlocks.blocks().forEach(block -> blocks.add(block.get()));
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
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.COMBUSTION_GENERATOR.get());
            tag(BlockTags.NEEDS_STONE_TOOL).add(ModBlocks.COMBUSTION_GENERATOR.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.PRESSURIZED_TANK.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.STATION_LIGHT.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.AIRLOCK_DOOR.get());
            tag(BlockTags.DOORS).add(ModBlocks.AIRLOCK_DOOR.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.SOLAR_GENERATOR.get(), ModBlocks.SOLAR_PANEL.get());
            tag(BlockTags.NEEDS_STONE_TOOL).add(ModBlocks.SOLAR_GENERATOR.get(), ModBlocks.SOLAR_PANEL.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpContent.BLOCK.get());
            tag(BlockTags.NEEDS_STONE_TOOL).add(io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpContent.BLOCK.get());
            for (var tier : io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDefinition.values()) {
                Block motor = io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorContent.block(tier).get();
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(motor);
                tag(BlockTags.NEEDS_STONE_TOOL).add(motor);
            }
            // C15b surfaces (ADR-063 section 5): soft turfs dig with a shovel, the charcoal log with an axe, the
            // geode shell needs an iron pickaxe (legacy: the jackhammer at level 2, which comes in C18b); geode ores
            // default to the legacy list (iron, gold, copper, tin, redstone).
            tag(BlockTags.MINEABLE_WITH_SHOVEL).add(SurfaceContent.MOON_TURF.get(), SurfaceContent.DARK_MOON_TURF.get(),
                    SurfaceContent.FERRIC_SAND.get());
            tag(BlockTags.MINEABLE_WITH_AXE).add(SurfaceContent.CHARCOAL_LOG.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(SurfaceContent.GEODE_SHELL.get());
            tag(BlockTags.NEEDS_IRON_TOOL).add(SurfaceContent.GEODE_SHELL.get());
            // C15c: the lightwood set joins the vanilla wood tags, so vanilla wood recipes, tools and fuel accept it;
            // the crystal blocks dig with a pickaxe.
            tag(BlockTags.LOGS_THAT_BURN).add(ExoplanetBlocks.LIGHTWOOD_LOG.get());
            tag(BlockTags.PLANKS).add(ExoplanetBlocks.LIGHTWOOD_PLANKS.get());
            tag(BlockTags.LEAVES).add(ExoplanetBlocks.LIGHTWOOD_LEAVES.get());
            tag(BlockTags.SAPLINGS).add(ExoplanetBlocks.LIGHTWOOD_SAPLING.get());
            ExoplanetBlocks.CRYSTALS.forEach(crystal -> tag(BlockTags.MINEABLE_WITH_PICKAXE).add(crystal.get()));
            tag(SurfaceWorldgen.GEODE_ORES).add(net.minecraft.world.level.block.Blocks.IRON_ORE,
                    net.minecraft.world.level.block.Blocks.GOLD_ORE, net.minecraft.world.level.block.Blocks.COPPER_ORE,
                    MaterialContent.block("tin_ore"), net.minecraft.world.level.block.Blocks.REDSTONE_ORE);
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
                        if (entry.kind() == Kind.STONE_ORE) {
                            // Once per material: every ore has a stone variant (C15aR1-I1).
                            tag(Tags.Blocks.ORES).addTag(blockTag(ore));
                        }
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
            copy(BlockTags.DOORS, ItemTags.DOORS);
            copy(BlockTags.LOGS_THAT_BURN, ItemTags.LOGS_THAT_BURN);
            copy(BlockTags.PLANKS, ItemTags.PLANKS);
            copy(BlockTags.LEAVES, ItemTags.LEAVES);
            copy(BlockTags.SAPLINGS, ItemTags.SAPLINGS);
            TagKey<Item> thermite = itemTag(new ResourceLocation("forge", "dusts/thermite"));
            tag(thermite).add(ModItems.THERMITE.get());
            tag(Tags.Items.DUSTS).addTag(thermite);
            for (Entry entry : MaterialCatalog.entries()) {
                Item item = MaterialContent.item(entry.id());
                Material material = entry.material();
                switch (entry.kind()) {
                    case STONE_ORE, DEEPSLATE_ORE -> {
                        for (ResourceLocation ore : MaterialTags.oreTags(material)) {
                            tag(itemTag(ore)).add(item);
                            if (entry.kind() == Kind.STONE_ORE) {
                                tag(Tags.Items.ORES).addTag(itemTag(ore));
                            }
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
