package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialClientCache;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextCache;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.AmbientController;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkySelection;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkySelection.Selection;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;

/** Physical-client lifetime owner. No server world, chunk or persistence access. */
public final class PlanetarySkyClient {
    private static final SkySelection SELECTION = new SkySelection();
    private static final PlanetarySkyRenderer RENDERER = new PlanetarySkyRenderer();
    private static final AmbientController AMBIENCE = new AmbientController();
    private static final PlanetaryAmbience PLAYBACK = new PlanetaryAmbience();
    public static final SkyProfileReloadListener RESOURCES = new SkyProfileReloadListener(
            PlanetarySkyClient::resourceReloaded, message -> AdvancedRocketryCommunity.LOGGER.warn("{}", message));

    private PlanetarySkyClient() { }

    public static Selection selection(ClientLevel level) {
        if (level == null || !(level.effects() instanceof PlanetaryDimensionEffects)) {
            SELECTION.clear();
            return null;
        }
        // ADR-047: the station context applies only in the Space Level.
        var orbit = level.dimension().equals(CelestialIds.SPACE_LEVEL)
                ? StationSkyContextCache.orbitBody() : java.util.Optional.<net.minecraft.resources.ResourceLocation>empty();
        return SELECTION.resolve(level.dimension().location(), CelestialClientCache.snapshot().orElse(null),
                RESOURCES.profiles(), orbit);
    }

    static boolean render(Selection selection, PoseStack poses, Matrix4f projection, float angle) {
        return RENDERER.render(selection, poses, projection, angle);
    }

    public static boolean clearView(Camera camera) {
        return camera.isInitialized() && camera.getFluidInCamera() == FogType.NONE
                && !(camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS)));
    }

    public static void tick() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        var level = minecraft.level;
        var selected = selection(level);
        var camera = minecraft.gameRenderer.getMainCamera();
        boolean exposed = level != null && minecraft.player != null && selected != null
                && camera.getEntity() != null && camera.getEntity().level() == level && clearView(camera)
                && level.hasChunkAt(camera.getBlockPosition()) && level.canSeeSky(camera.getBlockPosition());
        AMBIENCE.tick(level, selected, exposed, PLAYBACK);
    }

    public static void logout() {
        StationSkyContextCache.clear();
        cleanup(false);
    }

    /** A new Level (dimension change or respawn): the server re-sends the station sky context. */
    public static void levelChanged() {
        StationSkyContextCache.clear();
    }

    private static void resourceReloaded() {
        cleanup(true);
    }

    private static void cleanup(boolean reload) {
        Minecraft.getInstance().execute(() -> {
            RenderSystem.assertOnRenderThread();
            AMBIENCE.reset(PLAYBACK);
            SELECTION.clear();
            if (reload) { RENDERER.resourceReloaded(); } else { RENDERER.close(); }
        });
    }

}
