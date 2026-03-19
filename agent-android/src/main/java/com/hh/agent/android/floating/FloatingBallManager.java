package com.hh.agent.android.floating;

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
     * 设置上下文（用于启动Activity）
     */
    public void setContext(Context context) {
        // 保持对ApplicationContext的引用
    }

    /**
     * 获取屏幕宽度（像素）
     */
    private int getScreenWidth() {
        return mContext.getResources().getDisplayMetrics().widthPixels;
    }

    /**
     * 获取屏幕高度（像素）
     */
    private int getScreenHeight() {
        return mContext.getResources().getDisplayMetrics().heightPixels;
    }

    /**
     * 获取悬浮球尺寸（像素）
     */
    private int getBallSize() {
        return dpToPx(48);
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

        // 点击行为由外部注入，Manager 只负责转发单击事件
        mFloatingBallView.setOnSingleTapListener(v -> {
            if (mOnClickListener != null) {
                mOnClickListener.onClick(v);
            }
        });

        // 创建LayoutParams
        mLayoutParams = new WindowManager.LayoutParams();
        mLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        mLayoutParams.format = PixelFormat.RGBA_8888;
        mLayoutParams.gravity = Gravity.TOP | Gravity.START;
        mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        mLayoutParams.width = getBallSize();
        mLayoutParams.height = getBallSize();

        // 默认位置：屏幕右侧边缘，垂直居中
        mLayoutParams.x = getScreenWidth() - getBallSize();
        mLayoutParams.y = (getScreenHeight() / 2) - (getBallSize() / 2);
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
     * 获取当前X坐标
     */
    public int getX() {
        return mLayoutParams.x;
    }

    /**
     * 获取当前Y坐标
     */
    public int getY() {
        return mLayoutParams.y;
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
     * 显示权限提示（没有权限时调用）
     */
    public void showPermissionTip() {
        Toast.makeText(mContext,
                "需要悬浮窗权限才能显示悬浮球\n点击通知去设置",
                Toast.LENGTH_LONG).show();
    }

    /**
     * 更新悬浮球位置（拖拽时调用）
     */
    public void updatePosition(int x, int y) {
        if (mLayoutParams == null) {
            return;
        }

        // 边界检查：确保不超出屏幕范围
        int ballSize = getBallSize();
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();

        // X轴边界限制
        if (x < 0) {
            x = 0;
        } else if (x > screenWidth - ballSize) {
            x = screenWidth - ballSize;
        }

        // Y轴边界限制
        if (y < 0) {
            y = 0;
        } else if (y > screenHeight - ballSize) {
            y = screenHeight - ballSize;
        }

        mLayoutParams.x = x;
        mLayoutParams.y = y;

        if (mIsShowing) {
            mWindowManager.updateViewLayout(mFloatingBallView, mLayoutParams);
        }
    }

    /**
     * 边缘吸附
     */
    public void snapToEdge() {
        if (mLayoutParams == null) {
            return;
        }

        int screenWidth = getScreenWidth();
        int ballSize = getBallSize();
        int rightEdge = screenWidth - ballSize;

        // 计算悬浮球中心X坐标
        int centerX = mLayoutParams.x + ballSize / 2;

        // 根据中心点位置吸附到最近边缘
        if (centerX < screenWidth / 2) {
            mLayoutParams.x = 0;
        } else {
            mLayoutParams.x = rightEdge;
        }

        if (mIsShowing) {
            mWindowManager.updateViewLayout(mFloatingBallView, mLayoutParams);
        }
    }

    private int dpToPx(int dp) {
        float density = mContext.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
