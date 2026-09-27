package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyGeometry;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyMath;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkySelection.Selection;
import java.util.List;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL14;

/** Five fixed GPU meshes; no profile-dependent geometry, textures or per-frame uploads. */
final class PlanetarySkyRenderer implements AutoCloseable {
    private VertexBuffer sphere;
    private VertexBuffer horizon;
    private VertexBuffer stars;
    private VertexBuffer sun;
    private VertexBuffer halo;
    private boolean failed;

    boolean render(Selection selection, PoseStack poses, Matrix4f projection, float angle) {
        RenderSystem.assertOnRenderThread();
        if (failed) {
            return false;
        }
        var previousShader = RenderSystem.getShader();
        float[] color = RenderSystem.getShaderColor();
        float red = color[0], green = color[1], blue = color[2], alpha = color[3];
        SkyRenderState previousState = null;
        try {
            previousState = SkyRenderState.capture();
            if (sphere == null) {
                sphere = upload(SkyGeometry.sphere());
                horizon = upload(SkyGeometry.horizon());
                stars = upload(SkyGeometry.stars());
                sun = upload(SkyGeometry.sun(false));
                halo = upload(SkyGeometry.sun(true));
            }
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            var profile = selection.profile();
            double daylight = SkyMath.daylight(angle);
            Matrix4f view = poses.last().pose();
            tint(SkyMath.skyColor(profile, daylight), 1, 1);
            draw(sphere, view, projection);
            tint(profile.fogColor(), 0.12 + 0.88 * daylight, 1);
            draw(horizon, view, projection);
            var rotating = new Matrix4f(view).rotateY((float) (-Math.PI / 2)).rotateX(angle);
            tint(0xFFFFFF, 1, SkyMath.stars(profile, selection.pressure(), daylight));
            draw(stars, rotating, projection);
            double radius = 100 * Math.tan(Math.toRadians(SkyMath.sunRadius(profile, selection.solarIntensity())));
            rotating.scale((float) radius, 1, (float) radius);
            tint(profile.sunColor(), 1, selection.solarIntensity() > 0 ? profile.sunOpacity() : 0);
            draw(halo, rotating, projection);
            draw(sun, rotating, projection);
            return true;
        } catch (RuntimeException exception) {
            failed = true;
            releaseBuffers();
            AdvancedRocketryCommunity.LOGGER.error("Planetary sky disabled until resource reload; using dimension fallback", exception);
            return false;
        } finally {
            VertexBuffer.unbind();
            // Clear the ShaderInstance program cache before restoring an externally bound program.
            if (GameRenderer.getPositionColorShader() != null) { GameRenderer.getPositionColorShader().clear(); }
            RenderSystem.setShaderColor(red, green, blue, alpha);
            RenderSystem.setShader(() -> previousShader);
            if (previousState != null) { previousState.close(); }
        }
    }

    private static VertexBuffer upload(List<SkyGeometry.Vertex> vertices) {
        if (vertices.size() > SkyGeometry.MAX_VERTICES) {
            throw new IllegalArgumentException("Sky mesh exceeds vertex budget");
        }
        var builder = new BufferBuilder(vertices.size() * 16);
        var buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (var vertex : vertices) {
                builder.vertex(vertex.x(), vertex.y(), vertex.z()).color(1F, 1F, 1F, vertex.alpha()).endVertex();
            }
            buffer.bind();
            buffer.upload(builder.end());
            return buffer;
        } catch (RuntimeException exception) {
            buffer.close();
            throw exception;
        } finally {
            VertexBuffer.unbind();
        }
    }

    private static void tint(int rgb, double brightness, double opacity) {
        RenderSystem.setShaderColor((float) (((rgb >> 16) & 255) / 255D * brightness),
                (float) (((rgb >> 8) & 255) / 255D * brightness),
                (float) ((rgb & 255) / 255D * brightness), (float) opacity);
    }

    private static void draw(VertexBuffer buffer, Matrix4f view, Matrix4f projection) {
        buffer.bind();
        buffer.drawWithShader(view, projection, GameRenderer.getPositionColorShader());
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        releaseBuffers();
    }

    void resourceReloaded() {
        close();
        failed = false;
    }

    private void releaseBuffers() {
        if (sphere != null) { sphere.close(); sphere = null; }
        if (horizon != null) { horizon.close(); horizon = null; }
        if (stars != null) { stars.close(); stars = null; }
        if (sun != null) { sun.close(); sun = null; }
        if (halo != null) { halo.close(); halo = null; }
    }
}
