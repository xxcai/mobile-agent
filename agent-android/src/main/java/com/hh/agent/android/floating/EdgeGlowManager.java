package com.hh.agent.android.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import com.hh.agent.android.glow.EdgeGlowView;

/**
 * 边缘光晕悬浮窗管理器。
 * 将 EdgeGlowView 作为全屏透明悬浮窗叠加在屏幕上。
 * 生命周期与悬浮球转圈动画一致：Agent 工作时显示，完成后消失。
 */
public class EdgeGlowManager {

    private static final String TAG = "EdgeGlowManager";
    private static EdgeGlowManager sInstance;

    private final Context mContext;
    private final Handler mMainHandler;
    private final WindowManager mWindowManager;

    private EdgeGlowView mEdgeGlowView;
    private WindowManager.LayoutParams mLayoutParams;
    private boolean mIsShowing = false;
    private boolean mIsActive = false;

    private EdgeGlowManager(Context context) {
        mContext = context.getApplicationContext();
        mMainHandler = new Handler(Looper.getMainLooper());
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public static EdgeGlowManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (EdgeGlowManager.class) {
                if (sInstance == null) {
                    sInstance = new EdgeGlowManager(context);
                }
            }
        }
        return sInstance;
    }

    /** 创建悬浮窗 View 和 LayoutParams（不立即添加） */
    private void ensureView() {
        if (mEdgeGlowView != null) return;

        mEdgeGlowView = new EdgeGlowView(mContext);

        // 使用 getRealSize() 获取包含状态栏/导航栏的完整屏幕尺寸。
        // MATCH_PARENT 和 displayMetrics.heightPixels 只返回应用可用区域，
        // 不包含系统栏，导致光晕无法贴到屏幕物理边缘。
        android.graphics.Point realSize = new android.graphics.Point();
        mWindowManager.getDefaultDisplay().getRealSize(realSize);
        int screenWidth = realSize.x;
        int screenHeight = realSize.y;

        mLayoutParams = new WindowManager.LayoutParams(
                screenWidth,
                screenHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        mLayoutParams.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
    }

    /** 激活或关闭边缘光晕 */
    public void setActive(boolean active) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMainHandler.post(() -> setActiveInternal(active));
            return;
        }
        setActiveInternal(active);
    }

    private void setActiveInternal(boolean active) {
        if (mIsActive == active) return;
        mIsActive = active;

        if (active) {
            show();
        } else {
            // 关闭动画由 EdgeGlowView.setActive(false) 驱动
            // 动画结束后在回调中移除 window
            if (mEdgeGlowView != null) {
                mEdgeGlowView.setActive(false);
            }
        }
    }

    /** 显示悬浮窗 */
    private void show() {
        ensureView();

        if (mIsShowing) {
            // 已经在显示，只激活动画
            if (mEdgeGlowView != null) {
                mEdgeGlowView.setActive(true);
            }
            return;
        }

        try {
            mWindowManager.addView(mEdgeGlowView, mLayoutParams);
            mIsShowing = true;
            // 激活光晕动画
            mEdgeGlowView.setActive(true);
            // 监听消失动画完成，自动移除 window
            mEdgeGlowView.setOnDisappearListener(this::removeView);
        } catch (Exception e) {
            mIsShowing = false;
        }
    }

    /** 移除悬浮窗 */
    private void removeView() {
        if (mIsShowing && mEdgeGlowView != null) {
            try {
                mWindowManager.removeView(mEdgeGlowView);
            } catch (Exception ignored) {
            }
            mIsShowing = false;
            // 重置 View 以便下次复用
            mEdgeGlowView = null;
        }
    }

    /** App 前后台切换：到后台隐藏，回前台恢复 */
    public void updateVisibility(boolean appInForeground) {
        if (!appInForeground) {
            // 到后台：直接移除
            if (mIsShowing) {
                removeView();
            }
        } else if (mIsActive) {
            // 回前台且仍在工作中：重新显示
            show();
        }
    }
}
