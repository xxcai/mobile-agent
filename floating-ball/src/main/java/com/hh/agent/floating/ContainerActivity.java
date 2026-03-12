package com.hh.agent.floating;

import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

    private LinearLayout mRootLayout;
    private FloatingBallManager mFloatingBallManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 初始化悬浮球管理器并隐藏悬浮球
        mFloatingBallManager = FloatingBallManager.getInstance(this);
        mFloatingBallManager.hide();

        // 设置Window为半透明
        setupWindow();

        // 创建布局
        setupLayout();

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
        // 窗口半透明已在主题中通过windowIsTranslucent设置
        // 此处不再需要FLAG_TRANSLUCENT_STATUS，避免与底部定位窗口冲突

        // 设置窗口高度为屏幕60%
        WindowManager.LayoutParams params = getWindow().getAttributes();
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        params.height = (int) (screenHeight * 0.6);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

        // 设置标志位监听窗口外部触摸事件
        // FLAG_NOT_TOUCH_MODAL: 让触摸事件传递给下层窗口
        // FLAG_WATCH_OUTSIDE_TOUCH: 监听窗口外部的触摸事件
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        getWindow().setAttributes(params);
    }

    /**
     * 创建布局
     */
    private void setupLayout() {
        // 创建根布局 - 使用LinearLayout简化垂直布局
        mRootLayout = new LinearLayout(this);
        mRootLayout.setOrientation(LinearLayout.VERTICAL);
        mRootLayout.setBackgroundColor(0xE6FFFFFF); // 90%不透明的白色背景

        // 获取屏幕高度
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int containerHeight = (int) (screenHeight * 0.6); // 占屏幕60%高度

        // 设置布局参数
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                containerHeight
        );
        rootParams.gravity = Gravity.BOTTOM;
        mRootLayout.setLayoutParams(rootParams);

        // 创建标题栏 (固定高度48dp)
        View titleBar = createTitleBar();
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48)
        );
        mRootLayout.addView(titleBar, titleParams);

        // 创建内容区域 (填充剩余空间)
        View contentArea = createContentArea();
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        mRootLayout.addView(contentArea, contentParams);

        // 设置容器内部点击不关闭（移除原来的OnClickListener）
        // 外部点击通过 FLAG_WATCH_OUTSIDE_TOUCH 机制处理

        setContentView(mRootLayout);

        // 设置进入和退出动画
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_bottom);
    }

    /**
     * 创建标题栏
     */
    private View createTitleBar() {
        // 使用FrameLayout作为标题栏容器，可以方便地定位子元素
        FrameLayout titleBar = new FrameLayout(this);
        titleBar.setBackgroundColor(0xE6FFFFFF); // 90%不透明的白色背景

        // 标题文字 - 居中显示
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

        // 关闭按钮 - 靠右显示
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
                dpToPx(1)
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

        // 使用MATCH_PARENT让内容区域填充LinearLayout中的剩余空间
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        contentText.setLayoutParams(params);

        return contentText;
    }

    @Override
    public void finish() {
        // 显示悬浮球
        if (mFloatingBallManager != null) {
            mFloatingBallManager.show();
        }
        super.finish();
        // 设置退出动画
        overridePendingTransition(0, R.anim.slide_out_bottom);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 处理窗口外部触摸事件
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            // 点击窗口外部（Activity Window 60% 区域之外的屏幕部分）关闭Activity
            finish();
            return true;
        }
        return super.onTouchEvent(event);
    }
}
