package io.github.sunthemoon.advancedrocketrycommunity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualClientCache;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Hash-keyed structure cache; packet decoding and BlockState restoration never repeat per frame. */
public final class RocketEntityRenderer extends EntityRenderer<RocketEntity> {
    private static final int MAX_RENDER_CACHES = 256;
    private final LinkedHashMap<UUID, CachedRocket> caches = new LinkedHashMap<>(16, 0.75F, true);

    public RocketEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.75F;
    }

    @Override
    public void render(
            RocketEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        CachedRocket cached = cached(entity.getUUID());
        if (cached == null) {
            renderPendingBoundary(poseStack, buffers);
        } else {
            for (CachedBlock block : cached.blocks()) {
                poseStack.pushPose();
                poseStack.translate(
                        block.position().x() - 0.5D,
                        block.position().y(),
                        block.position().z() - 0.5D
                );
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                        block.state(),
                        poseStack,
                        buffers,
                        packedLight,
                        OverlayTexture.NO_OVERLAY
                );
                poseStack.popPose();
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public boolean shouldRender(
            RocketEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        CachedRocket cached = cached(entity.getUUID());
        if (cached == null) {
            return super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ);
        }
        RocketBounds bounds = cached.bounds();
        AABB worldBounds = new AABB(
                entity.getX() + bounds.minimum().x() - 0.5D,
                entity.getY() + bounds.minimum().y(),
                entity.getZ() + bounds.minimum().z() - 0.5D,
                entity.getX() + bounds.maximum().x() + 0.5D,
                entity.getY() + bounds.maximum().y() + 1.0D,
                entity.getZ() + bounds.maximum().z() + 0.5D
        );
        return frustum.isVisible(worldBounds);
    }

    @Override
    public ResourceLocation getTextureLocation(RocketEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    private CachedRocket cached(UUID entityId) {
        RocketVisualSnapshot visual = RocketVisualClientCache.snapshot(entityId).orElse(null);
        if (visual == null) {
            return null;
        }
        CachedRocket existing = caches.get(entityId);
        if (existing != null && existing.contentHash().equals(visual.structureContentHash())) {
            return existing;
        }
        ArrayList<CachedBlock> restored = new ArrayList<>(visual.blocks().size());
        for (RocketVisualBlock block : visual.blocks()) {
            BlockState state = RocketBlockStateAdapter.restore(block.state()).orElse(null);
            if (state == null) {
                caches.remove(entityId);
                return null;
            }
            restored.add(new CachedBlock(block.position(), state));
        }
        CachedRocket rebuilt = new CachedRocket(
                visual.structureContentHash(),
                List.copyOf(restored),
                visual.bounds()
        );
        caches.put(entityId, rebuilt);
        while (caches.size() > MAX_RENDER_CACHES) {
            UUID oldest = caches.keySet().iterator().next();
            caches.remove(oldest);
        }
        return rebuilt;
    }

    private static void renderPendingBoundary(PoseStack poseStack, MultiBufferSource buffers) {
        poseStack.pushPose();
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(
                poseStack,
                lines,
                new AABB(0.0D, 0.0D, 0.0D, 1.0D, 4.0D, 1.0D),
                0.18F,
                0.85F,
                1.0F,
                1.0F
        );
        poseStack.popPose();
    }

    private record CachedBlock(
            io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition position,
            BlockState state
    ) {
    }

    private record CachedRocket(String contentHash, List<CachedBlock> blocks, RocketBounds bounds) {
    }
}
