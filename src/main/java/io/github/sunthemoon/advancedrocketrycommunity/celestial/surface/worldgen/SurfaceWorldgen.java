package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import com.mojang.serialization.Codec;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registration of the C15b world generators (ADR-063 section 5): structure and piece types, a feature, a source. */
public final class SurfaceWorldgen {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(
            Registries.STRUCTURE_TYPE, AdvancedRocketryCommunity.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(
            Registries.STRUCTURE_PIECE, AdvancedRocketryCommunity.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES,
            AdvancedRocketryCommunity.MOD_ID);
    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(
            Registries.BIOME_SOURCE, AdvancedRocketryCommunity.MOD_ID);

    public static final RegistryObject<StructureType<CraterStructure>> CRATER = STRUCTURE_TYPES.register("crater",
            () -> () -> CraterStructure.CODEC);
    public static final RegistryObject<StructureType<VolcanoStructure>> VOLCANO = STRUCTURE_TYPES.register("volcano",
            () -> () -> VolcanoStructure.CODEC);
    public static final RegistryObject<StructureType<GeodeStructure>> GEODE = STRUCTURE_TYPES.register("geode",
            () -> () -> GeodeStructure.CODEC);

    public static final RegistryObject<StructurePieceType> CRATER_PIECE = STRUCTURE_PIECES.register("crater",
            () -> (StructurePieceType.ContextlessType) CraterPiece::new);
    public static final RegistryObject<StructurePieceType> VOLCANO_PIECE = STRUCTURE_PIECES.register("volcano",
            () -> (StructurePieceType.ContextlessType) VolcanoPiece::new);
    public static final RegistryObject<StructurePieceType> GEODE_PIECE = STRUCTURE_PIECES.register("geode",
            () -> (StructurePieceType.ContextlessType) GeodePiece::new);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> CHARRED_TREE = FEATURES.register(
            "charred_tree", () -> new CharredTreeFeature(NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<Codec<? extends BiomeSource>> PATCHES = BIOME_SOURCES.register("patches",
            () -> PatchBiomeSource.CODEC);

    /** The ores a geode's clusters are drawn from (ADR-063 section 5): a data-pack block tag. */
    public static final TagKey<Block> GEODE_ORES = TagKey.create(Registries.BLOCK, ModIdentity.id("geode_ores"));

    private SurfaceWorldgen() {
    }

    public static void register(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        FEATURES.register(modBus);
        BIOME_SOURCES.register(modBus);
    }
}
