package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The one v1.8 registry provider (a DataGen run allows one per name): the Overworld ores of C15a (ADR-063 section 4)
 * and the Moon, Mars and Venus data of C15b (section 5). Each registry runs both batches' bootstrap steps.
 */
public final class V180Worldgen {
    private V180Worldgen() {
    }

    public static DatapackBuiltinEntriesProvider provider(PackOutput output,
                                                          CompletableFuture<HolderLookup.Provider> registries) {
        return new DatapackBuiltinEntriesProvider(output, registries, builder(), Set.of(ModIdentity.MOD_ID));
    }

    static RegistrySetBuilder builder() {
        return new RegistrySetBuilder()
                .add(Registries.NOISE, V180PlanetWorldgen::noise)
                .add(Registries.CONFIGURED_FEATURE, context -> {
                    V180MaterialWorldgen.configured(context);
                    V180PlanetWorldgen.configured(context);
                })
                .add(Registries.PLACED_FEATURE, context -> {
                    V180MaterialWorldgen.placed(context);
                    V180PlanetWorldgen.placed(context);
                })
                .add(Registries.BIOME, V180PlanetWorldgen::biomes)
                .add(Registries.STRUCTURE, V180PlanetWorldgen::structures)
                .add(Registries.STRUCTURE_SET, V180PlanetWorldgen::structureSets)
                .add(Registries.NOISE_SETTINGS, V180PlanetWorldgen::noiseSettings)
                .add(ForgeRegistries.Keys.BIOME_MODIFIERS, V180MaterialWorldgen::biomeModifiers);
    }
}
