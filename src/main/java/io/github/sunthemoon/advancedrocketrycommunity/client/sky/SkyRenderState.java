package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Bounded state readback instead of assuming defaults owned by another renderer. */
final class SkyRenderState implements AutoCloseable {
    private final Access access;
    private final State previous;

    private SkyRenderState(Access access) {
        this.access = access;
        previous = access.capture();
    }

    static SkyRenderState capture(Access access) { return new SkyRenderState(access); }

    static SkyRenderState capture() {
        RenderSystem.assertOnRenderThread();
        return capture(OPEN_GL);
    }

    @Override public void close() { access.restore(previous); }

    record State(boolean blend, boolean cull, boolean depthWrite, int sourceRgb, int destinationRgb,
            int sourceAlpha, int destinationAlpha, int equationRgb, int equationAlpha,
            int vertexArray, int arrayBuffer, int program) { }

    interface Access {
        State capture();
        void restore(State state);
    }

    private static final Access OPEN_GL = new Access() {
        @Override public State capture() {
            return new State(GL11.glIsEnabled(GL11.GL_BLEND), GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
                    GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        }

        @Override public void restore(State state) {
            RenderSystem.blendFuncSeparate(state.sourceRgb(), state.destinationRgb(), state.sourceAlpha(), state.destinationAlpha());
            GL20.glBlendEquationSeparate(state.equationRgb(), state.equationAlpha());
            if (state.blend()) { RenderSystem.enableBlend(); } else { RenderSystem.disableBlend(); }
            if (state.cull()) { RenderSystem.enableCull(); } else { RenderSystem.disableCull(); }
            RenderSystem.depthMask(state.depthWrite());
            RenderSystem.glBindVertexArray(state::vertexArray);
            RenderSystem.glBindBuffer(GL15.GL_ARRAY_BUFFER, state::arrayBuffer);
            ProgramManager.glUseProgram(state.program());
        }
    };
}
