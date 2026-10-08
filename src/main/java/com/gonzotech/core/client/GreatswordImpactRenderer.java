package com.gonzotech.core.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** GPU-only full-screen filters and framebuffer copies used by the greatsword impact sequence. */
final class GreatswordImpactRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String VERTEX_SHADER = """
        #version 330 core
        layout(location = 0) in vec2 a_position;
        layout(location = 1) in vec2 a_uv;
        out vec2 v_uv;

        void main() {
            v_uv = a_uv;
            gl_Position = vec4(a_position, 0.0, 1.0);
        }
        """;

    private static final String FRAGMENT_SHADER = """
        #version 330 core
        in vec2 v_uv;
        out vec4 fragColor;

        uniform sampler2D u_screen;
        uniform int u_filter;
        uniform float u_intensity;

        void main() {
            vec4 source = texture(u_screen, v_uv);
            vec3 color = source.rgb;

            if (u_filter == 1) {
                // The first two frames are a clean 30 percent exposure boost.
                color = clamp(color * 1.30, 0.0, 1.0);
            } else if (u_filter == 2) {
                // Preserve the requested boost, then invert and crush the contrast.
                color = clamp(color * 1.30, 0.0, 1.0);
                color = vec3(1.0) - color;
                color = clamp((color - 0.5) * 2.35 + 0.5, 0.0, 1.0);
            } else if (u_filter == 3) {
                // Strong saturation/contrast followed by four additive copies of the frame.
                float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
                color = mix(vec3(luminance), color, 1.75);
                color = clamp((color - 0.43) * 1.8 + 0.43, 0.0, 1.0);
                color = clamp(color * 4.0, 0.0, 1.0);
            } else if (u_filter == 4) {
                // Brightness remains +30 percent at thaw and eases back over three ticks.
                color = clamp(color * (1.0 + 0.30 * u_intensity), 0.0, 1.0);
            }

            fragColor = vec4(color, source.a);
        }
        """;

    private static final float[] FULLSCREEN_QUAD = {
        -1.0F, -1.0F, 0.0F, 0.0F,
         1.0F, -1.0F, 1.0F, 0.0F,
         1.0F,  1.0F, 1.0F, 1.0F,
        -1.0F, -1.0F, 0.0F, 0.0F,
         1.0F,  1.0F, 1.0F, 1.0F,
        -1.0F,  1.0F, 0.0F, 1.0F
    };

    private static int programId;
    private static int vertexArrayId;
    private static int vertexBufferId;
    private static int screenUniform = -1;
    private static int filterUniform = -1;
    private static int intensityUniform = -1;
    private static boolean shaderInitialized;
    private static boolean shaderFailed;

    private static TextureTarget workingFrame;
    private static TextureTarget frozenFrame;

    private GreatswordImpactRenderer() {
    }

    static boolean applyFilter(RenderTarget screen, Filter filter, float intensity) {
        if (!ensureResources(screen)) return false;
        if (!drawTextureToTarget(screen.getColorTextureId(), workingFrame, screen, Filter.COPY, 0.0F)) {
            return false;
        }
        return drawTextureToTarget(workingFrame.getColorTextureId(), screen, screen, filter, intensity);
    }

    static boolean captureFrozenFrame(RenderTarget screen) {
        return ensureResources(screen)
            && drawTextureToTarget(screen.getColorTextureId(), frozenFrame, screen, Filter.COPY, 0.0F);
    }

    static boolean drawFrozenFrame(RenderTarget screen) {
        return ensureResources(screen)
            && drawTextureToTarget(frozenFrame.getColorTextureId(), screen, screen, Filter.COPY, 0.0F);
    }

    private static boolean ensureResources(RenderTarget screen) {
        if (screen == null || screen.width <= 0 || screen.height <= 0 || !initializeShader()) {
            return false;
        }

        try {
            if (workingFrame == null) {
                workingFrame = createTarget(screen.width, screen.height);
            } else if (workingFrame.width != screen.width || workingFrame.height != screen.height) {
                workingFrame.resize(screen.width, screen.height, Minecraft.ON_OSX);
                workingFrame.setFilterMode(GL11.GL_NEAREST);
            }

            if (frozenFrame == null) {
                frozenFrame = createTarget(screen.width, screen.height);
            } else if (frozenFrame.width != screen.width || frozenFrame.height != screen.height) {
                frozenFrame.resize(screen.width, screen.height, Minecraft.ON_OSX);
                frozenFrame.setFilterMode(GL11.GL_NEAREST);
            }
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("[Gonzo Tech] Could not allocate greatsword impact-frame targets", exception);
            destroyTargets();
            return false;
        } finally {
            // Target constructors/resizes may bind their own framebuffer while reallocating.
            try {
                screen.bindWrite(true);
            } catch (RuntimeException exception) {
                LOGGER.error("[Gonzo Tech] Failed to restore the main render target after allocation", exception);
            }
        }
    }

    private static TextureTarget createTarget(int width, int height) {
        TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        target.setFilterMode(GL11.GL_NEAREST);
        return target;
    }

    private static boolean initializeShader() {
        if (shaderInitialized) return true;
        if (shaderFailed) return false;

        int vertexShader = 0;
        int fragmentShader = 0;
        try {
            vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
            fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            programId = GL20.glCreateProgram();
            if (programId == 0) throw new IllegalStateException("OpenGL did not create the impact shader program");

            GL20.glAttachShader(programId, vertexShader);
            GL20.glAttachShader(programId, fragmentShader);
            GL20.glLinkProgram(programId);
            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Impact shader link failed: " + GL20.glGetProgramInfoLog(programId));
            }

            screenUniform = GL20.glGetUniformLocation(programId, "u_screen");
            filterUniform = GL20.glGetUniformLocation(programId, "u_filter");
            intensityUniform = GL20.glGetUniformLocation(programId, "u_intensity");
            if (screenUniform < 0 || filterUniform < 0 || intensityUniform < 0) {
                throw new IllegalStateException("Impact shader is missing a required uniform");
            }

            createFullscreenQuad();
            shaderInitialized = true;
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("[Gonzo Tech] Greatsword impact shader initialization failed", exception);
            if (programId != 0) {
                GL20.glDeleteProgram(programId);
                programId = 0;
            }
            if (vertexBufferId != 0) {
                GL15.glDeleteBuffers(vertexBufferId);
                vertexBufferId = 0;
            }
            if (vertexArrayId != 0) {
                GL30.glDeleteVertexArrays(vertexArrayId);
                vertexArrayId = 0;
            }
            shaderFailed = true;
            return false;
        } finally {
            if (vertexShader != 0) GL20.glDeleteShader(vertexShader);
            if (fragmentShader != 0) GL20.glDeleteShader(fragmentShader);
        }
    }

    private static int compileShader(int shaderType, String source) {
        int shader = GL20.glCreateShader(shaderType);
        if (shader == 0) throw new IllegalStateException("OpenGL did not create shader type " + shaderType);

        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Impact shader compile failed: " + log);
        }
        return shader;
    }

    private static void createFullscreenQuad() {
        int previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

        vertexArrayId = GL30.glGenVertexArrays();
        vertexBufferId = GL15.glGenBuffers();
        if (vertexArrayId == 0 || vertexBufferId == 0) {
            throw new IllegalStateException("OpenGL could not allocate the impact-frame quad");
        }

        try {
            GL30.glBindVertexArray(vertexArrayId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBufferId);
            FloatBuffer vertices = BufferUtils.createFloatBuffer(FULLSCREEN_QUAD.length);
            vertices.put(FULLSCREEN_QUAD).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);

            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer);
            GL30.glBindVertexArray(previousVertexArray);
        }
    }

    private static boolean drawTextureToTarget(int sourceTexture, RenderTarget destination,
                                               RenderTarget restoreTarget, Filter filter, float intensity) {
        RenderSystem.assertOnRenderThread();

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        IntBuffer scissorBox = BufferUtils.createIntBuffer(4);
        if (scissorEnabled) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);

        try {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.disableScissor();

            destination.bindWrite(true);
            GL20.glUseProgram(programId);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture);
            GL20.glUniform1i(screenUniform, 0);
            GL20.glUniform1i(filterUniform, filter.shaderValue);
            GL20.glUniform1f(intensityUniform, intensity);

            GL30.glBindVertexArray(vertexArrayId);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("[Gonzo Tech] Failed to draw greatsword impact frame", exception);
            return false;
        } finally {
            GL30.glBindVertexArray(previousVertexArray);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
            GL20.glUseProgram(previousProgram);

            try {
                restoreTarget.bindWrite(true);
            } catch (RuntimeException exception) {
                LOGGER.error("[Gonzo Tech] Failed to restore the main render target after impact frame", exception);
            }

            if (depthEnabled) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(depthMask);
            if (blendEnabled) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cullEnabled) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (scissorEnabled) {
                RenderSystem.enableScissor(scissorBox.get(0), scissorBox.get(1), scissorBox.get(2), scissorBox.get(3));
            } else {
                RenderSystem.disableScissor();
            }
        }
    }

    private static void destroyTargets() {
        if (workingFrame != null) {
            workingFrame.destroyBuffers();
            workingFrame = null;
        }
        if (frozenFrame != null) {
            frozenFrame.destroyBuffers();
            frozenFrame = null;
        }
    }

    enum Filter {
        COPY(0),
        BRIGHTNESS(1),
        NEGATIVE_HARD_CONTRAST(2),
        INTENSE_IMPACT(3),
        FADE_BRIGHTNESS(4);

        private final int shaderValue;

        Filter(int shaderValue) {
            this.shaderValue = shaderValue;
        }
    }
}
