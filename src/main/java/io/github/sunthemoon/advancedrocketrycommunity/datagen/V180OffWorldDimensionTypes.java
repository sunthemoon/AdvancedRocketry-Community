package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfiles;
import java.util.OptionalLong;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.dimension.DimensionType;

/** The current writer of all six off-world types; only natural differs from their pre-W37B definitions. */
public final class V180OffWorldDimensionTypes {
    private V180OffWorldDimensionTypes() { }

    public static void bootstrap(BootstapContext<DimensionType> context) {
        register(context, "moon", true, true, 0.1F);
        register(context, "space", false, true, 0.0F);
        register(context, "mars", true, false, 0.0F);
        register(context, "venus", true, false, 0.0F);
        V180ExoplanetWorldgen.dimensionTypes(context);
    }

    private static void register(BootstapContext<DimensionType> context, String name, boolean skylight,
                                 boolean fixed, float ambient) {
        context.register(ResourceKey.create(Registries.DIMENSION_TYPE, ModIdentity.id(name)),
                new DimensionType(fixed ? OptionalLong.of(18000) : OptionalLong.empty(), skylight, false,
                        false, true, 1.0D, true, false, 0, 256, 256, BlockTags.INFINIBURN_OVERWORLD,
                        name.equals("space") ? SkyProfiles.SPACE_EFFECTS : SkyProfiles.SURFACE_EFFECTS,
                        ambient, new DimensionType.MonsterSettings(false, false, ConstantInt.of(0), 0)));
    }
}
