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

    private final Context context;
    private final FloatBuffer vertexBuffer;

    private GlResource glResource;
    private int aPositionLocation;
    private int uTimeLocation;
    private int uResolutionLocation;

    private long startTime;
    private long pausedTime;
    private long pauseStartMs;
    private boolean paused;

    private int width;
    private int height;

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
        // Compile shaders
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

        // Cache attribute and uniform locations
        aPositionLocation = GLES30.glGetAttribLocation(program, "aPosition");
        uTimeLocation = GLES30.glGetUniformLocation(program, "uTime");
        uResolutionLocation = GLES30.glGetUniformLocation(program, "uResolution");

        startTime = System.currentTimeMillis();
        pausedTime = 0;

        GLES30.glClearColor(0f, 0f, 0f, 0f);
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

        // Set uniforms
        GLES30.glUniform1f(uTimeLocation, (float) elapsed);
        GLES30.glUniform2f(uResolutionLocation, (float) width, (float) height);

        // Draw full-screen quad
        GLES30.glEnableVertexAttribArray(aPositionLocation);
        GLES30.glVertexAttribPointer(aPositionLocation, 2, GLES30.GL_FLOAT, false, STRIDE, vertexBuffer);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glDisableVertexAttribArray(aPositionLocation);
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
