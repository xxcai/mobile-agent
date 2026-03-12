package com.hh.agent.floating;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 容器Activity - 悬浮球点击后展开的半透明Activity
 * 使用SingleTop模式管理Task栈
 */
public class ContainerActivity extends AppCompatActivity {

    private static final String TAG = "ContainerActivity";
    public static final String ACTION_SHOW_FLOATING_BALL = "com.hh.agent.action.SHOW_FLOATING_BALL";

    private FrameLayout mRootLayout;
    private BroadcastReceiver mFloatingBallReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置Window为半透明
        setupWindow();

        // 创建布局
        setupLayout();

        // 注册广播接收器
        registerFloatingBallReceiver();

        // 处理返回键
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    /**
     * 设置Window为半透明
     */
    private void setupWindow() {
        // 设置半透明背景
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            );
        }

        // 设置背景半透明
        getWindow().setGravity(Gravity.BOTTOM);
    }

    /**
     * 创建布局
     */
    private void setupLayout() {
        // 创建根布局
        mRootLayout = new FrameLayout(this);
        mRootLayout.setBackgroundColor(0xE6FFFFFF); // 90%不透明的白色背景

        // 获取屏幕高度
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int containerHeight = (int) (screenHeight * 0.6); // 占屏幕60%高度

        // 设置布局参数
        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                containerHeight
        );
        rootParams.gravity = Gravity.BOTTOM;
        mRootLayout.setLayoutParams(rootParams);

        // 创建标题栏
        View titleBar = createTitleBar();
        mRootLayout.addView(titleBar);

        // 创建内容区域
        View contentArea = createContentArea();
        mRootLayout.addView(contentArea);

        // 设置外部点击关闭
        mRootLayout.setClickable(true);
        mRootLayout.setFocusable(true);
        mRootLayout.setOnClickListener(v -> {
            // 点击外部区域关闭Activity
            finish();
        });

        setContentView(mRootLayout);

        // 设置进入和退出动画
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_bottom);
    }

    /**
     * 创建标题栏
     */
    private View createTitleBar() {
        FrameLayout titleBar = new FrameLayout(this);
        int titleBarHeight = dpToPx(48);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                titleBarHeight
        );
        params.gravity = Gravity.TOP;
        titleBar.setLayoutParams(params);

        // 标题文字
        TextView titleText = new TextView(this);
        titleText.setText("容器");
        titleText.setTextSize(18);
        titleText.setTextColor(0xFF333333);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.gravity = Gravity.CENTER;
        titleText.setLayoutParams(titleParams);
        titleBar.addView(titleText);

        // 关闭按钮
        ImageButton closeButton = new ImageButton(this);
        closeButton.setBackgroundResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                dpToPx(36),
                dpToPx(36)
        );
        closeParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        closeParams.rightMargin = dpToPx(12);
        closeButton.setLayoutParams(closeParams);
        closeButton.setOnClickListener(v -> finish());
        titleBar.addView(closeButton);

        // 底部分割线
        View divider = new View(this);
        divider.setBackgroundColor(0xFFDDDDDD);
        FrameLayout.LayoutParams dividerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                1
        );
        dividerParams.gravity = Gravity.BOTTOM;
        divider.setLayoutParams(dividerParams);
        titleBar.addView(divider);

        return titleBar;
    }

    /**
     * 创建内容区域
     */
    private View createContentArea() {
        TextView contentText = new TextView(this);
        contentText.setText("悬浮球容器\n\n点击返回键、关闭按钮或外部区域可收起");
        contentText.setTextSize(16);
        contentText.setTextColor(0xFF666666);
        contentText.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        params.topMargin = dpToPx(48);
        contentText.setLayoutParams(params);

        return contentText;
    }

    /**
     * 注册悬浮球显示广播接收器
     */
    private void registerFloatingBallReceiver() {
        mFloatingBallReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_SHOW_FLOATING_BALL.equals(intent.getAction())) {
                    // 悬浮球已恢复显示
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_SHOW_FLOATING_BALL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mFloatingBallReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mFloatingBallReceiver, filter);
        }
    }

    /**
     * 发送广播通知悬浮球恢复显示
     */
    private void sendShowFloatingBallBroadcast() {
        Intent intent = new Intent(ACTION_SHOW_FLOATING_BALL);
        sendBroadcast(intent);
    }

    @Override
    public void finish() {
        super.finish();
        // 设置退出动画
        overridePendingTransition(0, R.anim.slide_out_bottom);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 通知悬浮球恢复显示
        sendShowFloatingBallBroadcast();

        // 注销广播接收器
        if (mFloatingBallReceiver != null) {
            unregisterReceiver(mFloatingBallReceiver);
            mFloatingBallReceiver = null;
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
