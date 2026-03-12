package com.hh.agent.floating;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * 悬浮球单例管理器
 * 使用WindowManager添加/移除悬浮View
 */
public class FloatingBallManager {

    private static final String TAG = "FloatingBallManager";
    private static FloatingBallManager sInstance;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;
    private FloatingBallView mFloatingBallView;
    private View.OnClickListener mOnClickListener;
    private boolean mIsShowing = false;
    private int mCurrentX;
    private int mCurrentY;

    private FloatingBallManager(Context context) {
        mContext = context.getApplicationContext();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * 获取单例实例
     */
    public static FloatingBallManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (FloatingBallManager.class) {
                if (sInstance == null) {
                    sInstance = new FloatingBallManager(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * 初始化悬浮球
     */
    public void initialize() {
        if (mFloatingBallView != null) {
            return;
        }

        // 创建悬浮球View
        mFloatingBallView = new FloatingBallView(mContext);
        mFloatingBallView.setOnTouchListener(mFloatingBallView.getDragTouchListener());
        mFloatingBallView.setOnClickListener(v -> {
            if (mOnClickListener != null) {
                mOnClickListener.onClick(v);
            }
        });

        // 创建LayoutParams
        mLayoutParams = new WindowManager.LayoutParams();
        mLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        mLayoutParams.format = PixelFormat.RGBA_8888;
        mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mLayoutParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;

        // 默认位置：屏幕右侧边缘，垂直居中
        mLayoutParams.x = 0;
        mLayoutParams.y = 0;
        mCurrentX = 0;
        mCurrentY = 0;
    }

    /**
     * 显示悬浮球
     */
    public void show() {
        if (mFloatingBallView == null) {
            initialize();
        }

        if (!mIsShowing && checkOverlayPermission()) {
            try {
                mWindowManager.addView(mFloatingBallView, mLayoutParams);
                mIsShowing = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 隐藏悬浮球
     */
    public void hide() {
        if (mIsShowing && mFloatingBallView != null) {
            try {
                mWindowManager.removeView(mFloatingBallView);
                mIsShowing = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 是否正在显示
     */
    public boolean isShowing() {
        return mIsShowing;
    }

    /**
     * 设置点击监听器
     */
    public void setOnClickListener(View.OnClickListener listener) {
        mOnClickListener = listener;
    }

    /**
     * 检查悬浮窗权限
     */
    public boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(mContext);
        }
        return true;
    }

    /**
     * 请求悬浮窗权限
     */
    public void requestOverlayPermission(Activity activity) {
        if (!checkOverlayPermission()) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + mContext.getPackageName())
            );
            activity.startActivity(intent);
            Toast.makeText(mContext, "请开启悬浮窗权限", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 更新悬浮球位置（拖拽时调用）
     */
    public void updatePosition(int x, int y) {
        if (mLayoutParams != null) {
            mCurrentX = x;
            mCurrentY = y;
            mLayoutParams.x = x;
            mLayoutParams.y = y;
            if (mIsShowing) {
                mWindowManager.updateViewLayout(mFloatingBallView, mLayoutParams);
            }
        }
    }

    /**
     * 边缘吸附
     */
    public void snapToEdge() {
        if (mLayoutParams == null) {
            return;
        }

        // 获取屏幕宽度
        int screenWidth = mWindowManager.getDefaultDisplay().getWidth();
        int ballWidth = dpToPx(48);

        // 计算吸附位置
        int centerX = mCurrentX + ballWidth / 2;
        if (centerX < screenWidth / 2) {
            // 靠近左边缘，吸附到左边
            mCurrentX = 0;
        } else {
            // 靠近右边缘，吸附到右边
            mCurrentX = screenWidth - ballWidth;
        }

        mLayoutParams.x = mCurrentX;
        if (mIsShowing) {
            mWindowManager.updateViewLayout(mFloatingBallView, mLayoutParams);
        }
    }

    private int dpToPx(int dp) {
        float density = mContext.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
