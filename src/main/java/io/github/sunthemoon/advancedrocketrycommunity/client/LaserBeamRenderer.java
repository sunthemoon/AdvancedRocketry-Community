package io.github.sunthemoon.advancedrocketrycommunity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserBeam;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ADR-055 section 5 visuals from the block entities' update tags only: one 3-wide beam from at most 384 blocks above
 * an active laser target down to its current layer, and a short emitter glow under an active drill controller. The
 * beam texture is the vanilla beacon beam, referenced at runtime and not copied.
 */
public final class LaserBeamRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final float[] COLOR = {0.45F, 0.85F, 1.0F};

    private LaserBeamRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static LaserBeamRenderer<LaserTargetBlockEntity> forTarget(BlockEntityRendererProvider.Context context) {
        return new LaserBeamRenderer<>(context);
    }

    public static LaserBeamRenderer<OrbitalLaserDrillBlockEntity> forDrill(BlockEntityRendererProvider.Context context) {
        return new LaserBeamRenderer<>(context);
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        if (blockEntity.getLevel() == null) {
            return;
        }
        long time = blockEntity.getLevel().getGameTime();
        if (blockEntity instanceof LaserTargetBlockEntity target && target.activeForRender()) {
            int markerY = target.getBlockPos().getY();
            int bottom = target.depthForRender() + 1 - markerY;
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, time, bottom,
                    LaserBeam.MAX_HEIGHT - bottom, COLOR, 1.3F, 1.5F);
        } else if (blockEntity instanceof OrbitalLaserDrillBlockEntity drill && drill.activeForRender()) {
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, time,
                    (int) -(1 + LaserBeam.EMITTER_LENGTH), (int) LaserBeam.EMITTER_LENGTH, COLOR, 0.15F, 0.25F);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRender(T blockEntity, Vec3 camera) {
        return Vec3.atCenterOf(blockEntity.getBlockPos()).multiply(1.0D, 0.0D, 1.0D)
                .closerThan(camera.multiply(1.0D, 0.0D, 1.0D), getViewDistance());
    }
}
