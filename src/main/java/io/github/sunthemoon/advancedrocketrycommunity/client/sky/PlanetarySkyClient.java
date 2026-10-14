package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialClientCache;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextCache;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.AmbientController;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyMath;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkySelection;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkySelection.Selection;
import io.github.sunthemoon.advancedrocketrycommunity.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Physical-client lifetime owner. No server world, chunk or persistence access. */
public final class PlanetarySkyClient {
    private static final SkySelection SELECTION = new SkySelection();
    private static final PlanetarySkyRenderer RENDERER = new PlanetarySkyRenderer();
    private static final AmbientController AMBIENCE = new AmbientController();
    private static final PlanetaryAmbience PLAYBACK = new PlanetaryAmbience();
    private static final SingularitySky<Particle> SINGULARITY = new SingularitySky<>(new SingularitySky.Handles<>() {
        @Override public boolean alive(Particle particle) { return particle.isAlive(); }
        @Override public void remove(Particle particle) { particle.remove(); }
    });
    public static final SkyProfileReloadListener RESOURCES = new SkyProfileReloadListener(
            PlanetarySkyClient::resourceReloaded, message -> AdvancedRocketryCommunity.LOGGER.warn("{}", message));

    private PlanetarySkyClient() { }

    /** Presentation switches do not clear the raw selection consumed by ambience. */
    public static Selection visualSelection(ClientLevel level) {
        if (level == null || !(level.effects() instanceof PlanetaryDimensionEffects effects)
                || !effects.overridesSky()) {
            return null;
        }
        return selection(level);
    }

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

    static boolean render(Selection selection, PoseStack poses, Matrix4f projection, float angle, float flash,
                          float partialTick) {
        double visibleFlash = Minecraft.getInstance().options.hideLightningFlash().get() ? 0 : flash;
        // ADR-065: the station attitude and distance apply only with a station context in the Space Level.
        var level = Minecraft.getInstance().level;
        var context = level != null && level.dimension().equals(CelestialIds.SPACE_LEVEL)
                ? StationSkyContextCache.context() : java.util.Optional.<StationSkyContextPacket.Context>empty();
        double[] attitude = context.flatMap(ignored -> StationSkyContextCache.phaseDegrees(partialTick))
                .map(phases -> SkyMath.stationAttitude(phases[0], phases[1], phases[2])).orElse(null);
        int distance = context.map(value -> value.controls().distance()).orElse(4);
        // ADR-066 section 7.1: this only runs with the sky switch on; an unknown body keeps the existing sky.
        boolean singular = SingularitySky.resolve(context, true, selection.orbit().map(SkySelection.Orbit::bodyId))
                .isPresent();
        return RENDERER.render(selection, poses, projection, angle, visibleFlash, attitude, distance, singular);
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
        // The local time base of the ADR-065 sky extrapolation; it never feeds the server.
        StationSkyContextCache.clientTick();
        var level = minecraft.level;
        var selected = selection(level);
        var camera = minecraft.gameRenderer.getMainCamera();
        boolean exposed = level != null && minecraft.player != null && selected != null
                && camera.getEntity() != null && camera.getEntity().level() == level && clearView(camera)
                && level.hasChunkAt(camera.getBlockPosition()) && level.canSeeSky(camera.getBlockPosition());
        AMBIENCE.tick(level, selected, exposed, PLAYBACK);
        singularityTick(minecraft, level, exposed);
    }

    /**
     * ADR-066 section 7.1: the singularity follows the sky switch of the Level's effect and the server's
     * context; its few motes are an accent that effects.advancedVisuals=false or the particle setting removes.
     */
    private static void singularityTick(Minecraft minecraft, ClientLevel level, boolean exposed) {
        var visual = visualSelection(level);
        var context = level != null && level.dimension().equals(CelestialIds.SPACE_LEVEL)
                ? StationSkyContextCache.context() : java.util.Optional.<StationSkyContextPacket.Context>empty();
        var known = visual == null ? java.util.Optional.<net.minecraft.resources.ResourceLocation>empty()
                : visual.orbit().map(SkySelection.Orbit::bodyId);
        if (!SINGULARITY.tick(SingularitySky.resolve(context, visual != null, known)) || !exposed
                || !ClientConfig.advancedVisuals()) {
            return;
        }
        int requested = switch (minecraft.options.particles().get()) {
            case ALL -> 2;
            case DECREASED -> 1;
            case MINIMAL -> 0;
        };
        // The same rigid attitude the renderer applies, so the motes gather toward the drawn disc.
        double[] attitude = StationSkyContextCache.phaseDegrees(0)
                .map(phases -> SkyMath.stationAttitude(phases[0], phases[1], phases[2])).orElseGet(() -> new double[3]);
        double[] direction = SkyMath.orient(attitude, SkyMath.orbitedBodyDirection());
        Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition();
        SINGULARITY.emit(requested, () -> mote(minecraft, level.random, eye, direction));
    }

    /** A portal mote that converges on a point 6 to 14 blocks toward the singularity; nothing is moved. */
    private static Particle mote(Minecraft minecraft, RandomSource random, Vec3 eye, double[] direction) {
        double reach = 6 + random.nextDouble() * 8;
        return minecraft.particleEngine.createParticle(ParticleTypes.PORTAL, eye.x + direction[0] * reach,
                eye.y + direction[1] * reach, eye.z + direction[2] * reach,
                random.nextGaussian() * 1.5, random.nextGaussian() * 1.5, random.nextGaussian() * 1.5);
    }

    public static void logout() {
        StationSkyContextCache.clear();
        cleanup(false);
    }

    /** A new Level (dimension change or respawn): the server re-sends the station sky context. */
    public static void levelChanged() {
        StationSkyContextCache.clear();
        SINGULARITY.clear();
    }

    private static void resourceReloaded() {
        cleanup(true);
    }

    private static void cleanup(boolean reload) {
        Minecraft.getInstance().execute(() -> {
            RenderSystem.assertOnRenderThread();
            AMBIENCE.reset(PLAYBACK);
            SELECTION.clear();
            SINGULARITY.clear();
            if (reload) { RENDERER.resourceReloaded(); } else { RENDERER.close(); }
        });
    }

}
