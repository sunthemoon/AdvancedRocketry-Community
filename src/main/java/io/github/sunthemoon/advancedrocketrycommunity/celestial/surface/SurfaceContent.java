package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface;

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
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Moon, Mars and Venus surface blocks of ADR-063 section 5 (C15b), with the legacy properties: the three turfs
 * are soft ground (hardness 0.5, shovel, sand sound) that does not fall; the geode shell is hard rock (hardness 6,
 * blast resistance 2,000) that needs a pickaxe; the charcoal log is a log that does not burn.
 */
public final class SurfaceContent {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            AdvancedRocketryCommunity.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            AdvancedRocketryCommunity.MOD_ID);

    public static final RegistryObject<Block> MOON_TURF = block("moon_turf", () -> turf(MapColor.SNOW));
    public static final RegistryObject<Block> DARK_MOON_TURF = block("dark_moon_turf", () -> turf(MapColor.CLAY));
    public static final RegistryObject<Block> FERRIC_SAND = block("ferric_sand", () -> turf(MapColor.NETHER));
    public static final RegistryObject<Block> CHARCOAL_LOG = block("charcoal_log", CharcoalLogBlock::new);
    public static final RegistryObject<Block> GEODE_SHELL = block("geode_shell",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
                    .strength(6.0F, 2_000.0F).sound(SoundType.STONE)));

    private SurfaceContent() {
    }

    private static RegistryObject<Block> block(String id, Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(id, factory);
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private static Block turf(MapColor colour) {
        return new Block(BlockBehaviour.Properties.of().mapColor(colour).strength(0.5F).sound(SoundType.SAND));
    }

    /** The registered surface blocks, in creative-tab order. */
    public static List<RegistryObject<Block>> blocks() {
        return List.of(MOON_TURF, DARK_MOON_TURF, FERRIC_SAND, CHARCOAL_LOG, GEODE_SHELL);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(SurfaceContent::creativeTab);
    }

    /** Natural blocks belong in the vanilla Natural Blocks tab. */
    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            blocks().forEach(block -> event.accept(block.get()));
        }
    }

    /** A charred log: an axis-aligned log that fire does not spread to or destroy (legacy flammability 0). */
    private static final class CharcoalLogBlock extends RotatedPillarBlock {
        private CharcoalLogBlock() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASS)
                    .strength(2.0F).sound(SoundType.WOOD));
        }

        @Override
        public boolean isFlammable(BlockState state, BlockGetter level, BlockPos position, Direction face) {
            return false;
        }

        @Override
        public int getFlammability(BlockState state, BlockGetter level, BlockPos position, Direction face) {
            return 0;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos position, Direction face) {
            return 0;
        }
    }
}
