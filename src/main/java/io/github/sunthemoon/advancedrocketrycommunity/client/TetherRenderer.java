package io.github.sunthemoon.advancedrocketrycommunity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorEndpointBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRules;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/**
 * ADR-059 section 9 visuals from the update tag's tether flag only: while the pair is valid, a thin tether beam rises
 * from the anchor to at most 384 blocks and hangs from the terminal down to at most 384 blocks. The beam texture is the
 * vanilla beacon beam, referenced at runtime and not copied. No capsule entity.
 */
public final class TetherRenderer<T extends ElevatorEndpointBlockEntity> implements BlockEntityRenderer<T> {
    private static final float[] COLOR = {0.85F, 0.9F, 1.0F};

    public TetherRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T endpoint, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        if (endpoint.getLevel() == null || !endpoint.tetherForRender()) {
            return;
        }
        long time = endpoint.getLevel().getGameTime();
        int length = ElevatorRules.MAX_TETHER_BLOCKS;
        if (endpoint.anchor()) {
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, time, 1,
                    length, COLOR, 0.1F, 0.15F);
        } else {
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, time,
                    -length, length, COLOR, 0.1F, 0.15F);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(T endpoint) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRender(T endpoint, Vec3 camera) {
        return Vec3.atCenterOf(endpoint.getBlockPos()).multiply(1.0D, 0.0D, 1.0D)
                .closerThan(camera.multiply(1.0D, 0.0D, 1.0D), getViewDistance());
    }
}
