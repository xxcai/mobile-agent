package com.hh.agent.android.glow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.RoundedCorner;
import android.view.animation.PathInterpolator;

/**
 * 全屏透明 GLSurfaceView，渲染边缘光晕效果。
 *
 * 使用方式：
 * - 添加到布局中（叠加在内容上方）
 * - 调用 setActive(true) 激活光晕，setActive(false) 隐藏
 * - 支持快速连续切换，动画会从中途接续
 */
public class EdgeGlowView extends GLSurfaceView {

    private static final String TAG = "EdgeGlowView";

    /** 出现/消失动画时长（毫秒） */
    private static final long ANIM_DURATION_MS = 600L;

    private EdgeGlowRenderer renderer;
    private ValueAnimator currentAnimator;
    private boolean active;
    private Runnable onDisappearListener;

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
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);

        renderer = new EdgeGlowRenderer(context);
        setRenderer(renderer);
        // 默认按需渲染，setActive 时切换为连续渲染
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        setClickable(false);
        setFocusable(false);

        // 从系统 API 读取屏幕圆角
        applyScreenCornerRadius(context);

        Log.d(TAG, "EdgeGlowView 初始化完成");
    }

    /**
     * 从 RoundedCorner API 读取设备屏幕圆角半径。
     * API 不可用时回退到默认值。
     */
    private void applyScreenCornerRadius(Context context) {
        float cornerRadius = EdgeGlowRenderer.DEFAULT_CORNER_RADIUS;

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
        // 不设内缩，光晕直接贴边
        renderer.setPadding(0f);
        Log.d(TAG, "cornerRadius=" + cornerRadius);
    }

    /** 动态设置圆角半径 */
    public void setCornerRadius(float radius) {
        if (renderer != null) {
            renderer.setCornerRadius(radius);
        }
    }

    /** 动态设置光晕扩散宽度 */
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

    /** 请求渲染一帧（仅在 RENDERMODE_WHEN_DIRTY 时有效） */
    public void requestFrame() {
        requestRender();
    }

    /**
     * 激活或关闭边缘光晕。
     *
     * true  → 出现动画：alpha 0→1，600ms，ease-out
     * false → 消失动画：alpha 1→0，600ms，ease-in-out
     *
     * 支持快速连续切换：会取消上一个动画，从当前 alpha 接续。
     */
    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;

        // 联动粒子发射
        if (renderer != null) {
            renderer.setEmitting(active);
        }

        // 取消正在进行的动画
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }

        float startAlpha;
        float endAlpha;

        // 从当前 alpha 接续，避免跳变
        if (renderer != null) {
            startAlpha = renderer.getAlpha();
            if (startAlpha <= 0f && !active) return;  // 已隐藏
            if (startAlpha >= 1f && active) return;    // 已显示
            endAlpha = active ? 1f : 0f;
        } else {
            startAlpha = active ? 0f : 1f;
            endAlpha = active ? 1f : 0f;
        }

        // 动画期间切换为连续渲染（呼吸波需要时间驱动）
        setRenderMode(RENDERMODE_CONTINUOUSLY);

        ValueAnimator animator = ValueAnimator.ofFloat(startAlpha, endAlpha);
        animator.setDuration(ANIM_DURATION_MS);
        if (active) {
            // 出现：ease-out
            animator.setInterpolator(new PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f));
        } else {
            // 消失：ease-in-out
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
                    // 消失完成后切回按需渲染，节省 GPU
                    setRenderMode(RENDERMODE_WHEN_DIRTY);
                    // 通知外部（EdgeGlowManager）移除悬浮窗
                    if (onDisappearListener != null) {
                        onDisappearListener.run();
                    }
                }
            }
        });

        currentAnimator = animator;
        animator.start();
    }

    public boolean isActive() {
        return active;
    }

    /** 设置消失动画完成后的回调（由 EdgeGlowManager 使用） */
    public void setOnDisappearListener(Runnable listener) {
        this.onDisappearListener = listener;
    }
}
