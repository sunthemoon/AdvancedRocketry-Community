package io.github.sunthemoon.advancedrocketrycommunity.material.worldgen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Material;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Overworld ores of ADR-063 section 4: tin (10 veins of 6), rutile (6 of 6), aluminum and dilithium (1 of 16
 * each), uniform between y -16 and 64, with the deepslate variant wherever a vein replaces deepslate. Iridium is not
 * placed in the Overworld. DataGen writes the features from this table; GameTests check the loaded data against it.
 */
public final class OverworldOres {
    public static final int MIN_Y = -16;
    public static final int MAX_Y = 64;
    public static final List<Vein> VEINS = List.of(
            new Vein(Material.TIN, 10, 6),
            new Vein(Material.TITANIUM, 6, 6),
            new Vein(Material.ALUMINUM, 1, 16),
            new Vein(Material.DILITHIUM, 1, 16));
    public static final ResourceKey<BiomeModifier> BIOME_MODIFIER = ResourceKey.create(
            ForgeRegistries.Keys.BIOME_MODIFIERS, ModIdentity.id("overworld_ores"));

    private OverworldOres() {
    }

    /** One Overworld ore: the material, veins per chunk and blocks per vein. */
    public record Vein(Material material, int count, int size) {
        public String featureName() {
            return "overworld_" + material.oreName().orElseThrow() + "_ore";
        }

        public ResourceKey<ConfiguredFeature<?, ?>> configured() {
            return ResourceKey.create(Registries.CONFIGURED_FEATURE, ModIdentity.id(featureName()));
        }

        public ResourceKey<PlacedFeature> placed() {
            return ResourceKey.create(Registries.PLACED_FEATURE, ModIdentity.id(featureName()));
        }
    }
}
