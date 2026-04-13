package com.hh.agent.android.glow;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.RoundedCorner;
import android.animation.ValueAnimator;
import android.graphics.Path;
import android.view.animation.PathInterpolator;

/**
 * 全屏透明 GLSurfaceView，渲染边缘光晕效果。
 */
public class EdgeGlowView extends GLSurfaceView {

    private static final String TAG = "EdgeGlowView";

    private static final long ANIM_DURATION_MS = 600L;

    private EdgeGlowRenderer renderer;
    private ValueAnimator currentAnimator;
    private boolean active;

    public EdgeGlowView(Context context) {
        super(context);
        init(context);
    }

    public EdgeGlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);

        renderer = new EdgeGlowRenderer(context);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        setClickable(false);
        setFocusable(false);

        // Read corner radius from display, set padding accordingly
        applyScreenCornerRadius(context);

        Log.d(TAG, "EdgeGlowView initialized");
    }

    /**
     * Read the device's screen corner radius and apply it.
     * Falls back to DEFAULT_CORNER_RADIUS if unavailable.
     */
    private void applyScreenCornerRadius(Context context) {
        float cornerRadius = EdgeGlowRenderer.DEFAULT_CORNER_RADIUS;
        float density = context.getResources().getDisplayMetrics().density;

        if (android.os.Build.VERSION.SDK_INT >= 31) {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
                if (display != null) {
                    RoundedCorner rc = display.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
                    if (rc != null) {
                        cornerRadius = rc.getRadius();
                    }
                }
            }
        }

        renderer.setCornerRadius(cornerRadius);
        // No padding: glow hugs the screen edge directly
        renderer.setPadding(0f);
        Log.d(TAG, "cornerRadius=" + cornerRadius);
    }

    /**
     * Override corner radius (e.g. from XML attribute or runtime config).
     */
    public void setCornerRadius(float radius) {
        if (renderer != null) {
            renderer.setCornerRadius(radius);
        }
    }

    /**
     * Override blur radius for the edge glow spread width.
     */
    public void setBlurRadius(float radius) {
        if (renderer != null) {
            renderer.setBlurRadius(radius);
        }
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

    public void requestFrame() {
        requestRender();
    }

    public void setContinuousRender(boolean continuous) {
        setRenderMode(continuous ? RENDERMODE_CONTINUOUSLY : RENDERMODE_WHEN_DIRTY);
    }

    /**
     * Activate or deactivate the edge glow.
     * true  → appear animation (alpha 0→1, 600ms ease-out)
     * false → disappear animation (alpha 1→0, 600ms ease-in-out)
     */
    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;

        // Cancel any in-progress animation
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }

        float startAlpha = active ? 0f : 1f;
        float endAlpha = active ? 1f : 0f;

        // Use current renderer alpha as start if mid-transition
        if (renderer != null) {
            startAlpha = renderer.getAlpha();
            if (startAlpha <= 0f && !active) {
                // Already invisible, nothing to do
                return;
            }
            if (startAlpha >= 1f && active) {
                // Already fully visible, nothing to do
                return;
            }
            endAlpha = active ? 1f : 0f;
        }

        // Switch to continuous render while animating and while active
        setRenderMode(RENDERMODE_CONTINUOUSLY);

        ValueAnimator animator = ValueAnimator.ofFloat(startAlpha, endAlpha);
        animator.setDuration(ANIM_DURATION_MS);
        if (active) {
            animator.setInterpolator(new PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f));
        } else {
            animator.setInterpolator(new PathInterpolator(0.25f, 0.04f, 0.25f, 1.0f));
        }
        animator.addUpdateListener(anim -> {
            float value = (float) anim.getAnimatedValue();
            if (renderer != null) {
                renderer.setAlpha(value);
            }
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!active) {
                    // Stop continuous rendering when disappear completes
                    setRenderMode(RENDERMODE_WHEN_DIRTY);
                }
            }
        });

        currentAnimator = animator;
        animator.start();
    }

    public boolean isActive() {
        return active;
    }
}
