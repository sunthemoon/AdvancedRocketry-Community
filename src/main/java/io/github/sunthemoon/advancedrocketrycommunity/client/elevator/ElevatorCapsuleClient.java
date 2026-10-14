package io.github.sunthemoon.advancedrocketrycommunity.client.elevator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorCapsuleCodec.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorEndpointBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRules;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** ADR-066 elevator_capsule: stationary countdown cage, no entity, texture, movement or server action. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, value = Dist.CLIENT)
public final class ElevatorCapsuleClient {
    public static final String EFFECT_ID = "elevator_capsule";
    private static final double RANGE_SQUARED = 64.0D * 64.0D;
    private static final int SIDES = 12;
    private static final ElevatorCapsuleClientState STATE = new ElevatorCapsuleClientState();
    private static WeakReference<ClientLevel> level = new WeakReference<>(null);

    private ElevatorCapsuleClient() {
    }

    /** Called by the existing endpoint renderer; selection stores only primitives and immutable summaries. */
    public static void offer(ElevatorEndpointBlockEntity endpoint) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel current = minecraft.level;
        if (current == null || endpoint.getLevel() != current || endpoint.isRemoved()) {
            return;
        }
        useLevel(current);
        double distance = Vec3.atCenterOf(endpoint.getBlockPos())
                .distanceToSqr(minecraft.gameRenderer.getMainCamera().getPosition());
        if (distance > RANGE_SQUARED) {
            return;
        }
        List<Entry> rides = new ArrayList<>();
        for (Entry entry : endpoint.capsulesForRender()) {
            if (player(current, endpoint, entry.entityId()) != null) {
                rides.add(entry);
            } else {
                endpoint.discardCapsule(entry.entityId());
            }
        }
        if (!rides.isEmpty() && minecraft.levelRenderer.getFrustum()
                .isVisible(new AABB(endpoint.getBlockPos()).inflate(1.5D).expandTowards(0, 2, 0))) {
            STATE.offer(new ElevatorCapsuleClientState.Candidate(endpoint.getBlockPos().asLong(), distance, rides));
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            STATE.clear();
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel current = minecraft.level;
        if (current == null || current != level.get()) {
            clear();
            return;
        }
        var buffers = minecraft.renderBuffers().bufferSource();
        try {
            for (var effect : STATE.effects()) {
                BlockPos position = BlockPos.of(effect.endpoint());
                // CHECK does not create a missing block entity; false never loads a chunk.
                LevelChunk chunk = current.getChunkSource().getChunk(position.getX() >> 4, position.getZ() >> 4, false);
                if (chunk == null || !(chunk.getBlockEntity(position, LevelChunk.EntityCreationType.CHECK)
                        instanceof ElevatorEndpointBlockEntity endpoint) || endpoint.isRemoved()) {
                    continue;
                }
                Entry live = endpoint.capsulesForRender().stream()
                        .filter(entry -> entry.entityId() == effect.ride().entityId()).findFirst().orElse(null);
                Player rider = live == null ? null : player(current, endpoint, live.entityId());
                if (rider == null || !event.getFrustum().isVisible(rider.getBoundingBox().inflate(0.5D))) {
                    continue;
                }
                draw(event.getPoseStack(), buffers.getBuffer(RenderType.lines()), rider, live.remainingTicks(),
                        event.getPartialTick(), event.getCamera().getPosition());
            }
            buffers.endBatch(RenderType.lines());
        } finally {
            STATE.clear();
        }
    }

    private static Player player(ClientLevel current, ElevatorEndpointBlockEntity endpoint, int id) {
        Entity entity = current.getEntity(id);
        return entity instanceof Player rider && rider.isAlive() && !rider.isRemoved() && endpoint.onPlatform(rider)
                ? rider : null;
    }

    /** Two fixed rings and four uprights (56 vertices per rider). Only brightness changes with the countdown. */
    private static void draw(PoseStack pose, VertexConsumer vertices, Player rider, int remaining, float partialTick,
                             Vec3 camera) {
        pose.pushPose();
        pose.translate(Mth.lerp(partialTick, rider.xo, rider.getX()) - camera.x,
                Mth.lerp(partialTick, rider.yo, rider.getY()) - camera.y,
                Mth.lerp(partialTick, rider.zo, rider.getZ()) - camera.z);
        float progress = 1.0F - remaining / (float) ElevatorRules.RIDE_COUNTDOWN_TICKS;
        float red = 0.25F + 0.7F * progress;
        double top = rider.getBbHeight() + 0.25D;
        for (int side = 0; side < SIDES; side++) {
            double a = side * Math.PI * 2 / SIDES;
            double b = (side + 1) * Math.PI * 2 / SIDES;
            double x1 = Math.cos(a) * 0.65D;
            double z1 = Math.sin(a) * 0.65D;
            double x2 = Math.cos(b) * 0.65D;
            double z2 = Math.sin(b) * 0.65D;
            line(pose, vertices, x1, -0.05D, z1, x2, -0.05D, z2, red);
            line(pose, vertices, x1, top, z1, x2, top, z2, red);
            if (side % 3 == 0) {
                line(pose, vertices, x1, -0.05D, z1, x1, top, z1, red);
            }
        }
        pose.popPose();
    }

    private static void line(PoseStack pose, VertexConsumer vertices, double x1, double y1, double z1,
                             double x2, double y2, double z2, float red) {
        double length = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
        float nx = (float) ((x2 - x1) / length);
        float ny = (float) ((y2 - y1) / length);
        float nz = (float) ((z2 - z1) / length);
        vertices.vertex(pose.last().pose(), (float) x1, (float) y1, (float) z1).color(red, 0.9F, 1.0F, 0.8F)
                .normal(pose.last().normal(), nx, ny, nz).endVertex();
        vertices.vertex(pose.last().pose(), (float) x2, (float) y2, (float) z2).color(red, 0.9F, 1.0F, 0.8F)
                .normal(pose.last().normal(), nx, ny, nz).endVertex();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            useLevel(Minecraft.getInstance().level);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void clone(ClientPlayerNetworkEvent.Clone event) {
        clear();
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clear();
        }
    }

    private static void useLevel(ClientLevel current) {
        if (current != level.get()) {
            clear();
            level = new WeakReference<>(current);
        }
    }

    private static void clear() {
        STATE.clear();
        level = new WeakReference<>(null);
    }
}
