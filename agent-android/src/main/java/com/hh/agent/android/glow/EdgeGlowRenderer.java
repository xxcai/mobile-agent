package com.hh.agent.android.glow;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.hh.agent.android.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * EdgeGlow 的 GL 渲染器。
 * 编译 shader、管理 uniform、绘制全屏 quad。
 */
class EdgeGlowRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "EdgeGlowRenderer";

    // Full-screen quad vertices (TRIANGLE_STRIP): [-1,-1] [1,-1] [-1,1] [1,1]
    private static final float[] QUAD_VERTICES = {
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f,
    };
    private static final int FLOAT_SIZE = 4;
    private static final int STRIDE = 2 * FLOAT_SIZE;

    /** Default corner radius (px) when system value is unavailable. */
    static final float DEFAULT_CORNER_RADIUS = 80f;
    /** Default blur radius (px) for edge glow spread. */
    static final float DEFAULT_BLUR_RADIUS = 25f;
    /** Default inner padding (px). */
    static final float DEFAULT_PADDING = 16f * 3f; // ~16dp at 3x density

    private final Context context;
    private final FloatBuffer vertexBuffer;

    private GlResource glResource;
    private int aPositionLocation;
    private int uTimeLocation;
    private int uResolutionLocation;
    private int uCornerRadiusLocation;
    private int uBlurRadiusLocation;
    private int uPaddingLocation;
    private int uAlphaLocation;

    private long startTime;
    private long pausedTime;
    private long pauseStartMs;
    private boolean paused;

    private int width;
    private int height;

    // Configurable parameters
    private float cornerRadius = DEFAULT_CORNER_RADIUS;
    private float blurRadius = DEFAULT_BLUR_RADIUS;
    private float padding = DEFAULT_PADDING;
    private volatile float alpha = 0f;

    EdgeGlowRenderer(Context context) {
        this.context = context;
        vertexBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.length * FLOAT_SIZE)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(QUAD_VERTICES);
        vertexBuffer.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        String vertexSource = GlUtils.loadShaderFromRaw(context, R.raw.edge_glow_vertex);
        String fragmentSource = GlUtils.loadShaderFromRaw(context, R.raw.edge_glow_fragment);

        int vertexShader = GlUtils.compileShader(GLES30.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = GlUtils.compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);

        if (vertexShader == 0 || fragmentShader == 0) {
            Log.e(TAG, "Shader compilation failed");
            return;
        }

        int program = GlUtils.linkProgram(vertexShader, fragmentShader);
        if (program == 0) {
            Log.e(TAG, "Program linking failed");
            return;
        }

        glResource = new GlResource(program, vertexShader, fragmentShader);

        aPositionLocation = GLES30.glGetAttribLocation(program, "aPosition");
        uTimeLocation = GLES30.glGetUniformLocation(program, "uTime");
        uResolutionLocation = GLES30.glGetUniformLocation(program, "uResolution");
        uCornerRadiusLocation = GLES30.glGetUniformLocation(program, "uCornerRadius");
        uBlurRadiusLocation = GLES30.glGetUniformLocation(program, "uBlurRadius");
        uPaddingLocation = GLES30.glGetUniformLocation(program, "uPadding");
        uAlphaLocation = GLES30.glGetUniformLocation(program, "uAlpha");

        startTime = System.currentTimeMillis();
        pausedTime = 0;

        GLES30.glClearColor(0f, 0f, 0f, 0f);

        // Enable premultiplied alpha blending
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        Log.d(TAG, "onSurfaceCreated done, program=" + program);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        this.width = width;
        this.height = height;
        GLES30.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        if (glResource == null) return;

        long now = System.currentTimeMillis();
        long elapsed = paused ? pausedTime : (now - startTime - pausedTime);

        GLES30.glUseProgram(glResource.getProgramId());

        GLES30.glUniform1f(uTimeLocation, (float) elapsed);
        GLES30.glUniform2f(uResolutionLocation, (float) width, (float) height);
        GLES30.glUniform1f(uCornerRadiusLocation, cornerRadius);
        GLES30.glUniform1f(uBlurRadiusLocation, blurRadius);
        GLES30.glUniform1f(uPaddingLocation, padding);
        GLES30.glUniform1f(uAlphaLocation, alpha);

        GLES30.glEnableVertexAttribArray(aPositionLocation);
        GLES30.glVertexAttribPointer(aPositionLocation, 2, GLES30.GL_FLOAT, false, STRIDE, vertexBuffer);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glDisableVertexAttribArray(aPositionLocation);
    }

    void setCornerRadius(float radius) {
        this.cornerRadius = radius;
    }

    void setBlurRadius(float radius) {
        this.blurRadius = radius;
    }

    void setPadding(float padding) {
        this.padding = padding;
    }

    void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    float getAlpha() {
        return alpha;
    }

    void onPause() {
        if (!paused) {
            paused = true;
            pauseStartMs = System.currentTimeMillis();
        }
    }

    void onResume() {
        if (paused) {
            paused = false;
            pausedTime += System.currentTimeMillis() - pauseStartMs;
        }
    }
}
