package com.hh.agent.android.glow;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

/**
 * 全屏透明 GLSurfaceView，渲染边缘光晕效果。
 */
public class EdgeGlowView extends GLSurfaceView {

    private static final String TAG = "EdgeGlowView";

    private EdgeGlowRenderer renderer;

    public EdgeGlowView(Context context) {
        super(context);
        init(context);
    }

    public EdgeGlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // OpenGL ES 3.0
        setEGLContextClientVersion(3);

        // 8-bit RGBA with alpha for transparency
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);

        // Render above normal views
        setZOrderOnTop(true);

        // Renderer
        renderer = new EdgeGlowRenderer(context);
        setRenderer(renderer);

        // Only render when dirty (we'll switch to continuous when active)
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        // Don't intercept touch events
        setClickable(false);
        setFocusable(false);

        Log.d(TAG, "EdgeGlowView initialized");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (renderer != null) {
            renderer.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (renderer != null) {
            renderer.onResume();
        }
    }

    /**
     * 请求渲染一帧。
     */
    public void requestFrame() {
        requestRender();
    }

    /**
     * 设置持续渲染模式。
     */
    public void setContinuousRender(boolean continuous) {
        setRenderMode(continuous ? RENDERMODE_CONTINUOUSLY : RENDERMODE_WHEN_DIRTY);
    }
}
