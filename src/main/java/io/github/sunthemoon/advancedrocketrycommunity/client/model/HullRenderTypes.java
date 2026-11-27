package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Entity cutout with a depth offset so a formed hull wins over coplanar structure faces. */
final class HullRenderTypes extends RenderType {
    private static final Function<ResourceLocation, RenderType> CUTOUT = Util.memoize(texture -> create(
            "arce_hull_cutout", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setLayeringState(POLYGON_OFFSET_LAYERING)
                    .createCompositeState(true)));

    static RenderType cutout(ResourceLocation texture) { return CUTOUT.apply(texture); }

    private HullRenderTypes() {
        super("arce_hull_cutout", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
                () -> { }, () -> { });
    }
}
