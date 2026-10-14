package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraftforge.client.model.renderable.CompositeRenderable;

/**
 * Draws a catalogued machine model over its formed structure (ADR-066 section 7.1). It draws only while the server's
 * formed flag is set and the model is loaded; otherwise the controller keeps its JSON block model. It hides no block
 * and changes no collision or pattern.
 */
public final class ClassicObjModelRenderer implements BlockEntityRenderer<ClassicControllerBlockEntity> {
    private final ObjModelFixture fixture;
    private final ObjModelCache<CompositeRenderable> cache;

    public ClassicObjModelRenderer(ObjModelFixture fixture, ObjModelCache<CompositeRenderable> cache) {
        if (!ObjModelFixture.ENTITY_CUTOUT.equals(fixture.renderType())) {
            throw new IllegalArgumentException("Unsupported model render type " + fixture.renderType());
        }
        this.fixture = fixture;
        this.cache = cache;
    }

    @Override
    public void render(ClassicControllerBlockEntity controller, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        var level = controller.getLevel();
        var state = controller.getBlockState();
        var entry = cache.get(fixture.id()).orElse(null);
        if (level == null || entry == null || !controller.modelFormed() || !state.hasProperty(ClassicControllerBlock.FACING)) { return; }
        // The pattern keeps the cell above the controller empty; the controller block itself is unlit.
        int light = LevelRenderer.getLightColor(level, controller.getBlockPos().above(ObjModelFixture.LIGHT_CELL_ABOVE));
        pose.pushPose();
        place(pose, fixture, state.getValue(ClassicControllerBlock.FACING));
        entry.model().render(pose, buffers, texture -> RenderType.entityCutout(entry.texture(texture)), light,
                OverlayTexture.NO_OVERLAY, partialTick, CompositeRenderable.Transforms.EMPTY);
        pose.popPose();
    }

    /** Places the mirrored model on the controller: the rotation of its pattern transform, then offset and scale. */
    public static void place(PoseStack pose, ObjModelFixture fixture, Direction facing) {
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(ObjModelFixture.yRotationDegrees(facing)));
        pose.translate(-0.5, 0, -0.5);
        pose.translate(fixture.offsetX(), fixture.offsetY(), fixture.offsetZ());
        pose.scale((float) fixture.scale(), (float) fixture.scale(), (float) fixture.scale());
    }
}
