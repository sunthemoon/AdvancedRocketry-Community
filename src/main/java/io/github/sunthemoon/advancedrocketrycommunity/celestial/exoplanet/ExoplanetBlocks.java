package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The C15c blocks of ADR-063 section 6 with revision 6: the lightwood set (log, leaves, sapling, planks), six crystal
 * blocks and the electric mushroom, with their block items in the vanilla Natural Blocks tab.
 */
public final class ExoplanetBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            AdvancedRocketryCommunity.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            AdvancedRocketryCommunity.MOD_ID);

    /** The six legacy crystal colours, in legacy order (amethyst, sapphire, emerald, ruby, citrine, wulfenite). */
    public enum Crystal {
        VIOLET(0xB23FFF, MapColor.COLOR_PURPLE),
        BLUE(0x3333FF, MapColor.COLOR_BLUE),
        GREEN(0x00FF00, MapColor.COLOR_GREEN),
        RED(0xFF3434, MapColor.COLOR_RED),
        YELLOW(0xFFFF34, MapColor.COLOR_YELLOW),
        ORANGE(0xFF9400, MapColor.COLOR_ORANGE);

        private final int tint;
        private final MapColor mapColor;

        Crystal(int tint, MapColor mapColor) {
            this.tint = tint;
            this.mapColor = mapColor;
        }

        public int tint() {
            return tint;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT) + "_crystal_block";
        }
    }

    public static final RegistryObject<Block> LIGHTWOOD_LOG = block("lightwood_log", LightwoodLog::new);
    public static final RegistryObject<Block> LIGHTWOOD_LEAVES = block("lightwood_leaves", LightwoodLeaves::new);
    public static final RegistryObject<Block> LIGHTWOOD_SAPLING = block("lightwood_sapling",
            () -> new SaplingBlock(new LightwoodTreeGrower(), BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)));
    public static final RegistryObject<Block> LIGHTWOOD_PLANKS = block("lightwood_planks", LightwoodPlanks::new);
    public static final List<RegistryObject<Block>> CRYSTALS = java.util.Arrays.stream(Crystal.values())
            .map(crystal -> block(crystal.id(), () -> crystalBlock(crystal))).toList();
    public static final RegistryObject<Block> ELECTRIC_MUSHROOM = block("electric_mushroom",
            () -> new ElectricMushroomBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .noCollission().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)));

    private ExoplanetBlocks() {
    }

    private static RegistryObject<Block> block(String id, Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(id, factory);
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    /** Glass-like: hardness 2, glass sound, translucent, drops itself; no tool is needed (legacy). */
    private static Block crystalBlock(Crystal crystal) {
        return new GlassBlock(BlockBehaviour.Properties.of().mapColor(crystal.mapColor).strength(2.0F)
                .sound(SoundType.GLASS).noOcclusion().isValidSpawn((state, level, position, type) -> false)
                .isRedstoneConductor((state, level, position) -> false)
                .isSuffocating((state, level, position) -> false).isViewBlocking((state, level, position) -> false));
    }

    public static RegistryObject<Block> crystal(Crystal crystal) {
        return CRYSTALS.get(crystal.ordinal());
    }

    /** The registered blocks, in creative-tab order. */
    public static List<RegistryObject<Block>> blocks() {
        List<RegistryObject<Block>> blocks = new java.util.ArrayList<>(List.of(LIGHTWOOD_LOG, LIGHTWOOD_LEAVES,
                LIGHTWOOD_SAPLING, LIGHTWOOD_PLANKS));
        blocks.addAll(CRYSTALS);
        blocks.add(ELECTRIC_MUSHROOM);
        return List.copyOf(blocks);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(ExoplanetBlocks::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            blocks().forEach(block -> event.accept(block.get()));
        }
    }

    /** A log that burns like vanilla logs (hardness 3, legacy). */
    private static final class LightwoodLog extends RotatedPillarBlock {
        private LightwoodLog() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.BASS)
                    .strength(3.0F).sound(SoundType.WOOD).ignitedByLava());
        }

        @Override
        public boolean isFlammable(BlockState state, BlockGetter level, BlockPos position, Direction face) {
            return true;
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos position, Direction face) {
            return 5;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos position, Direction face) {
            return 5;
        }
    }

    /** Glowing leaves: light 8 and the legacy fire values (50, 50); they decay like vanilla leaves. */
    private static final class LightwoodLeaves extends LeavesBlock {
        private LightwoodLeaves() {
            super(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES).mapColor(MapColor.COLOR_CYAN)
                    .lightLevel(state -> 8));
        }

        @Override
        public boolean isFlammable(BlockState state, BlockGetter level, BlockPos position, Direction face) {
            return true;
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos position, Direction face) {
            return 50;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos position, Direction face) {
            return 50;
        }
    }

    /** Planks with light 4 (legacy), hardness 3, burning like vanilla planks. */
    private static final class LightwoodPlanks extends Block {
        private LightwoodPlanks() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.LAPIS).instrument(NoteBlockInstrument.BASS)
                    .strength(3.0F).sound(SoundType.WOOD).lightLevel(state -> 4).ignitedByLava());
        }

        @Override
        public boolean isFlammable(BlockState state, BlockGetter level, BlockPos position, Direction face) {
            return true;
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos position, Direction face) {
            return 5;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos position, Direction face) {
            return 20;
        }
    }
}
