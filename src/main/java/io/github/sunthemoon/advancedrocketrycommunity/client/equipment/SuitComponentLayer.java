package io.github.sunthemoon.advancedrocketrycommunity.client.equipment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ADR-066 section 7.1 worn suit components on every rendered player (the legacy {@code RenderComponents} special
 * render): a jetpack with two tanks and nozzles, loose back tanks, or a visor plate, as NEW code-defined boxes with a
 * NEW texture. Tanks are tinted by gas, the visor by type. Vanilla only renders tracked players within its own
 * distance, so culling is vanilla's. The bounded summary is recomputed at most once per wearer tick and the small
 * per-wearer cache holds only immutable summaries, is capped and clears at logout and Level change.
 */
public final class SuitComponentLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE = ModIdentity.id("textures/entity/suit_components.png");
    static final int TEXTURE_SIZE = 32;
    static final int MAX_CACHED_WEARERS = 64;
    private static final Map<Integer, Cached> CACHE = new HashMap<>();
    private static final int EMPTY_TINT = 0x8A949C;

    private record Cached(int tick, SuitComponents components) { }

    private final ModelPart frame;
    private final ModelPart leftTank;
    private final ModelPart rightTank;
    private final ModelPart leftNozzle;
    private final ModelPart rightNozzle;
    private final ModelPart backTankLeft;
    private final ModelPart backTankRight;
    private final ModelPart visor;

    public SuitComponentLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        ModelPart root = mesh().bakeRoot();
        frame = root.getChild("frame");
        leftTank = root.getChild("left_tank");
        rightTank = root.getChild("right_tank");
        leftNozzle = root.getChild("left_nozzle");
        rightNozzle = root.getChild("right_nozzle");
        backTankLeft = root.getChild("back_tank_left");
        backTankRight = root.getChild("back_tank_right");
        visor = root.getChild("visor");
    }

    /** Boxes in body and head model units; the chestplate and helmet surfaces sit one unit outside the body. */
    static LayerDefinition mesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("frame", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, 1.0F, 3.0F, 8, 10, 2),
                PartPose.ZERO);
        root.addOrReplaceChild("left_tank", CubeListBuilder.create().texOffs(0, 12)
                .addBox(-4.0F, 0.5F, 5.0F, 3, 9, 3), PartPose.ZERO);
        root.addOrReplaceChild("right_tank", CubeListBuilder.create().texOffs(0, 12)
                .addBox(1.0F, 0.5F, 5.0F, 3, 9, 3), PartPose.ZERO);
        root.addOrReplaceChild("left_nozzle", CubeListBuilder.create().texOffs(12, 12)
                .addBox(-3.5F, 9.5F, 5.5F, 2, 2, 2), PartPose.ZERO);
        root.addOrReplaceChild("right_nozzle", CubeListBuilder.create().texOffs(12, 12)
                .addBox(1.5F, 9.5F, 5.5F, 2, 2, 2), PartPose.ZERO);
        root.addOrReplaceChild("back_tank_left", CubeListBuilder.create().texOffs(20, 0)
                .addBox(-3.5F, 1.5F, 3.0F, 3, 8, 3), PartPose.ZERO);
        root.addOrReplaceChild("back_tank_right", CubeListBuilder.create().texOffs(20, 0)
                .addBox(0.5F, 1.5F, 3.0F, 3, 8, 3), PartPose.ZERO);
        root.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(0, 24)
                .addBox(-4.0F, -6.0F, -5.6F, 8, 3, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch) {
        if (player.isInvisible() || !SuitVisorClient.equipmentAuthorized()) {
            return;
        }
        SuitComponents components = components(player);
        if (components.empty() || components.attachments() > SuitComponents.MAX_ATTACHMENTS) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int overlay = OverlayTexture.NO_OVERLAY;
        if (components.jetpack() || !components.tanks().isEmpty()) {
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            if (components.jetpack()) {
                draw(frame, pose, consumer, light, overlay, 0xFFFFFF);
                draw(leftTank, pose, consumer, light, overlay, tint(components.tank(0)));
                draw(rightTank, pose, consumer, light, overlay, tint(components.tank(1)));
                draw(leftNozzle, pose, consumer, light, overlay, 0xFFFFFF);
                draw(rightNozzle, pose, consumer, light, overlay, 0xFFFFFF);
            } else {
                draw(backTankLeft, pose, consumer, light, overlay, tint(components.tank(0)));
                if (components.tanks().size() > 1) {
                    draw(backTankRight, pose, consumer, light, overlay, tint(components.tank(1)));
                }
            }
            pose.popPose();
        }
        if (components.visor() != null) {
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            draw(visor, pose, consumer, light, overlay,
                    components.visor() == ModuleKind.EARTHBRIGHT ? 0xFFC46A : 0xA8DCFF);
            pose.popPose();
        }
    }

    private static void draw(ModelPart part, PoseStack pose, VertexConsumer consumer, int light, int overlay,
            int rgb) {
        part.render(pose, consumer, light, overlay, ((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F, 1.0F);
    }

    private static int tint(TankData.Gas gas) {
        return switch (gas) {
            case OXYGEN -> 0xE6F0FF;
            case HYDROGEN -> 0x71E5EE;
            case EMPTY -> EMPTY_TINT;
        };
    }

    /** At most one recomputation per wearer tick; a full cache is emptied, so departed wearers do not linger. */
    private static SuitComponents components(AbstractClientPlayer player) {
        Cached cached = CACHE.get(player.getId());
        if (cached != null && cached.tick() == player.tickCount) {
            return cached.components();
        }
        SuitComponents components = SuitComponents.of(player);
        if (cached == null && CACHE.size() >= MAX_CACHED_WEARERS) {
            CACHE.clear();
        }
        CACHE.put(player.getId(), new Cached(player.tickCount, components));
        return components;
    }

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent
        public static void layers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) {
                    renderer.addLayer(new SuitComponentLayer(renderer));
                }
            }
        }

        private Setup() {
        }
    }

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Lifecycle {
        @SubscribeEvent
        public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            CACHE.clear();
        }

        @SubscribeEvent
        public static void levelChanged(ClientPlayerNetworkEvent.Clone event) {
            CACHE.clear();
        }

        private Lifecycle() {
        }
    }
}
