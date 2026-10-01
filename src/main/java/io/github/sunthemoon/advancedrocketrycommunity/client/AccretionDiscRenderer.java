package io.github.sunthemoon.advancedrocketrycommunity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * ADR-057 section 6: one bounded accretion-disc effect around a generating black-hole generator, from its update
 * tag only: a flat ring of 48 quads (192 vertices, under the 512-vertex bound) slowly turning above the structure's
 * centre. Drawn from code; no texture is used or copied.
 */
public final class AccretionDiscRenderer implements BlockEntityRenderer<BlackHoleGeneratorBlockEntity> {
    private static final int SEGMENTS = 48;
    private static final float INNER = 1.2F;
    private static final float OUTER = 2.6F;

    public AccretionDiscRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BlackHoleGeneratorBlockEntity generator, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (!generator.generatingForRender() || generator.getLevel() == null) {
            return;
        }
        Direction back = generator.getBlockState().getValue(BlackHoleGeneratorBlock.FACING).getOpposite();
        float time = (generator.getLevel().getGameTime() % 3600L + partialTick) * 2.0F;
        pose.pushPose();
        pose.translate(0.5D + back.getStepX(), 2.1D, 0.5D + back.getStepZ());
        pose.mulPose(Axis.YP.rotationDegrees(time));
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / SEGMENTS;
            double a1 = Math.PI * 2.0D * (i + 1) / SEGMENTS;
            int alpha = 70 + (i % 6) * 12;
            quad(consumer, matrix, a0, a1, alpha);
        }
        pose.popPose();
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, double a0, double a1, int alpha) {
        float cos0 = (float) Math.cos(a0);
        float sin0 = (float) Math.sin(a0);
        float cos1 = (float) Math.cos(a1);
        float sin1 = (float) Math.sin(a1);
        consumer.vertex(matrix, INNER * cos0, 0.0F, INNER * sin0).color(255, 210, 160, alpha).endVertex();
        consumer.vertex(matrix, OUTER * cos0, 0.0F, OUTER * sin0).color(255, 120, 40, 0).endVertex();
        consumer.vertex(matrix, OUTER * cos1, 0.0F, OUTER * sin1).color(255, 120, 40, 0).endVertex();
        consumer.vertex(matrix, INNER * cos1, 0.0F, INNER * sin1).color(255, 210, 160, alpha).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(BlackHoleGeneratorBlockEntity generator) {
        return true;
    }
}
