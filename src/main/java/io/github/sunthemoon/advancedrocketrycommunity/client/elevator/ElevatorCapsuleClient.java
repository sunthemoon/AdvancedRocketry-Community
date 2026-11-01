package io.github.sunthemoon.advancedrocketrycommunity.client.elevator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorCapsuleCodec.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorEndpointBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRules;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ADR-066 elevator_capsule: one stationary legacy capsule per endpoint with a pending ride (Root decision R263), or
 * the per-rider line cage when its model or texture is unusable. No entity, movement or server action.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, value = Dist.CLIENT)
public final class ElevatorCapsuleClient {
    public static final String EFFECT_ID = "elevator_capsule";
    static final ResourceLocation TEXTURE = CapsuleResources.TEXTURE;
    /**
     * The capsule's yaw: upstream {@code TileSpaceElevator} (c5cd5af6) gives the capsule 270 for a north-facing
     * controller. The modern endpoints have no facing, and the K4 anchor hull is drawn as the north-facing elevator.
     */
    static final float CAPSULE_YAW_DEGREES = 270;
    static final CapsuleResources RESOURCES = new CapsuleResources(AdvancedRocketryCommunity.LOGGER::warn);
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
        AABB visible = new AABB(endpoint.getBlockPos()).inflate(1.5D).expandTowards(0, 2, 0);
        CapsuleMesh mesh = RESOURCES.mesh().orElse(null);
        if (mesh != null) {
            visible = visible.minmax(capsuleBox(mesh, endpoint.getBlockPos()));
        }
        if (!rides.isEmpty() && minecraft.levelRenderer.getFrustum().isVisible(visible)) {
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
        // Read once per resource reload; a missing or unusable model, or a missing texture, keeps the line cage.
        CapsuleMesh mesh = RESOURCES.mesh().orElse(null);
        var renderType = mesh != null ? RenderType.entityCutoutNoCull(TEXTURE) : RenderType.lines();
        Vec3 camera = event.getCamera().getPosition();
        try {
            if (mesh != null) {
                for (var capsule : STATE.capsules()) {
                    BlockPos position = BlockPos.of(capsule.endpoint());
                    ElevatorEndpointBlockEntity endpoint = endpoint(current, position);
                    int remaining = -1;
                    for (Entry ride : endpoint == null ? List.<Entry>of() : capsule.rides()) {
                        Entry live = live(endpoint, ride.entityId());
                        if (live != null && player(current, endpoint, live.entityId()) != null
                                && (remaining < 0 || live.remainingTicks() < remaining)) {
                            remaining = live.remainingTicks();
                        }
                    }
                    if (remaining < 0 || !event.getFrustum().isVisible(capsuleBox(mesh, position))) {
                        continue;
                    }
                    drawCapsule(event.getPoseStack(), buffers.getBuffer(renderType), mesh, position, camera, remaining,
                            LevelRenderer.getLightColor(current, position.above(2)));
                }
            } else {
                for (var effect : STATE.effects()) {
                    ElevatorEndpointBlockEntity endpoint = endpoint(current, BlockPos.of(effect.endpoint()));
                    Entry live = endpoint == null ? null : live(endpoint, effect.ride().entityId());
                    Player rider = live == null ? null : player(current, endpoint, live.entityId());
                    if (rider == null || !event.getFrustum().isVisible(rider.getBoundingBox().inflate(0.5D))) {
                        continue;
                    }
                    drawCage(event.getPoseStack(), buffers.getBuffer(renderType), rider, live.remainingTicks(),
                            event.getPartialTick(), camera);
                }
            }
            buffers.endBatch(renderType);
        } finally {
            STATE.clear();
        }
    }

    @Nullable
    private static ElevatorEndpointBlockEntity endpoint(ClientLevel current, BlockPos position) {
        // CHECK does not create a missing block entity; false never loads a chunk.
        LevelChunk chunk = current.getChunkSource().getChunk(position.getX() >> 4, position.getZ() >> 4, false);
        return chunk != null && chunk.getBlockEntity(position, LevelChunk.EntityCreationType.CHECK)
                instanceof ElevatorEndpointBlockEntity endpoint && !endpoint.isRemoved() ? endpoint : null;
    }

    @Nullable
    private static Entry live(ElevatorEndpointBlockEntity endpoint, int entityId) {
        return endpoint.capsulesForRender().stream().filter(entry -> entry.entityId() == entityId).findFirst().orElse(null);
    }

    private static Player player(ClientLevel current, ElevatorEndpointBlockEntity endpoint, int id) {
        Entity entity = current.getEntity(id);
        return entity instanceof Player rider && rider.isAlive() && !rider.isRemoved() && endpoint.onPlatform(rider)
                ? rider : null;
    }

    /** The countdown tint of the cage, kept on the capsule: cyan at the request, near white at departure. */
    private static float red(int remaining) {
        return 0.25F + 0.7F * (1.0F - remaining / (float) ElevatorRules.RIDE_COUNTDOWN_TICKS);
    }

    /**
     * Upstream {@code RenderElevatorCapsule.doRender} (c5cd5af6): translate to the capsule entity's position plus
     * (0, 1, 0), then rotate by its yaw about +Y. The entity position's modern counterpart is the endpoint's arrival
     * point, the platform centre where riders board and arrive.
     */
    static void place(PoseStack pose, BlockPos endpoint, Vec3 camera) {
        Vec3 capsule = ElevatorEndpointBlockEntity.arrival(endpoint);
        pose.translate(capsule.x - camera.x, capsule.y + 1 - camera.y, capsule.z - camera.z);
        pose.mulPose(Axis.YP.rotationDegrees(CAPSULE_YAW_DEGREES));
    }

    /** The world box of the placed capsule: its model box's corners turned by the same yaw. */
    static AABB capsuleBox(CapsuleMesh mesh, BlockPos endpoint) {
        var b = mesh.bounds();
        double radians = Math.toRadians(CAPSULE_YAW_DEGREES), cos = Math.cos(radians), sin = Math.sin(radians);
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double x : new double[] {b.minX(), b.maxX()}) {
            for (double z : new double[] {b.minZ(), b.maxZ()}) {
                double turnedX = cos * x + sin * z, turnedZ = -sin * x + cos * z;
                minX = Math.min(minX, turnedX); maxX = Math.max(maxX, turnedX);
                minZ = Math.min(minZ, turnedZ); maxZ = Math.max(maxZ, turnedZ);
            }
        }
        Vec3 capsule = ElevatorEndpointBlockEntity.arrival(endpoint).add(0, 1, 0);
        return new AABB(capsule.x + minX, capsule.y + b.minY(), capsule.z + minZ,
                capsule.x + maxX, capsule.y + b.maxY(), capsule.z + maxZ).inflate(1.0E-3);
    }

    /** One legacy Capsule group: four vertices per face, at most {@link CapsuleMesh#MAX_FACES} faces. */
    private static void drawCapsule(PoseStack pose, VertexConsumer vertices, CapsuleMesh mesh, BlockPos endpoint,
                                    Vec3 camera, int remaining, int light) {
        pose.pushPose();
        place(pose, endpoint, camera);
        float red = red(remaining);
        var matrix = pose.last().pose();
        var normal = pose.last().normal();
        for (var face : mesh.faces()) {
            for (var corner : face.corners()) {
                vertices.vertex(matrix, corner.x(), corner.y(), corner.z()).color(red, 0.9F, 1.0F, 1.0F)
                        .uv(corner.u(), corner.v()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                        .normal(normal, corner.nx(), corner.ny(), corner.nz()).endVertex();
            }
        }
        pose.popPose();
    }

    /** The original fallback: two rings and four uprights of lines, 56 vertices per rider. */
    private static void drawCage(PoseStack pose, VertexConsumer vertices, Player rider, int remaining, float partialTick,
                                 Vec3 camera) {
        pose.pushPose();
        pose.translate(Mth.lerp(partialTick, rider.xo, rider.getX()) - camera.x,
                Mth.lerp(partialTick, rider.yo, rider.getY()) - camera.y,
                Mth.lerp(partialTick, rider.zo, rider.getZ()) - camera.z);
        float red = red(remaining);
        double top = Mth.clamp(rider.getBbHeight() + 0.25D, 0.5D, 3.0D);
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

    /** Registers the once-per-reload capsule check on the mod bus. */
    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(RESOURCES);
        }
    }
}
