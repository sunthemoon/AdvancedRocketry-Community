package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;

/** Client texture extension uses resource IDs only; no client Minecraft classes. */
public final class ClassicFluidType extends FluidType {
    public ClassicFluidType(ClassicFluidDefinition definition) {
        super(properties(definition));
    }

    private static Properties properties(ClassicFluidDefinition definition) {
        Properties properties = Properties.create()
                .descriptionId("fluid_type." + ModIdentity.MOD_ID + "." + definition.id())
                .density(definition.density()).viscosity(definition.viscosity())
                .temperature(definition.temperature()).lightLevel(definition.light())
                .canConvertToSource(false).canHydrate(false).canExtinguish(false);
        if (!definition.gas()) {
            boolean hot = definition == ClassicFluidDefinition.ENRICHED_LAVA;
            properties.sound(SoundActions.BUCKET_FILL,
                    hot ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY,
                            hot ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY);
            if (hot) {
                properties.canSwim(false).canDrown(false).motionScale(0.007D)
                        .pathType(BlockPathTypes.LAVA).adjacentPathType(BlockPathTypes.DANGER_FIRE);
            }
        }
        return properties;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        // FluidType invokes this during its superclass constructor, before instance fields.
        // The definition is recovered lazily after the type has its stable registry identity.
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return texture("_still");
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return texture("_flow");
            }

            @Override
            public int getTintColor() {
                return definition().tint();
            }

            private ResourceLocation texture(String suffix) {
                return ModIdentity.id("block/fluid/" + definition().id() + suffix);
            }

            private ClassicFluidDefinition definition() {
                String description = ClassicFluidType.this.getDescriptionId();
                for (ClassicFluidDefinition candidate : ClassicFluidDefinition.values()) {
                    if (description.endsWith("." + candidate.id())) { return candidate; }
                }
                throw new IllegalStateException("Unknown classic fluid type: " + description);
            }
        });
    }
}
