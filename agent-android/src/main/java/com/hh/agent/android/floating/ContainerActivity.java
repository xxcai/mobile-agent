package com.hh.agent.android.floating;

import com.hh.agent.android.AgentFragment;
import com.hh.agent.android.R;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

/**
 * 容器Activity - 悬浮球点击后展开的半透明Activity
 * 使用SingleTop模式管理Task栈
 */
public class ContainerActivity extends AppCompatActivity {

    private static final long ENTER_ANIMATION_DURATION_MS = 220L;
    private static final long EXIT_ANIMATION_DURATION_MS = 180L;

    private FrameLayout mRootLayout;
    private View mScrimView;
    private LinearLayout mCardLayout;
    private FloatingBallManager mFloatingBallManager;
    private boolean mIsClosing;

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
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.FILL;

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
        mRootLayout = new FrameLayout(this);
        mRootLayout.setBackgroundColor(0x00000000);

        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        mRootLayout.setLayoutParams(rootParams);

        mScrimView = new View(this);
        mScrimView.setBackgroundColor(0x66000000);
        mScrimView.setAlpha(0f);
        mScrimView.setOnClickListener(v -> finish());
        mRootLayout.addView(mScrimView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        mCardLayout = new LinearLayout(this);
        mCardLayout.setOrientation(LinearLayout.VERTICAL);
        mCardLayout.setBackgroundColor(0xFFFFFFFF);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (int) (screenHeight * 0.75f),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        cardParams.setMargins(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        mCardLayout.setLayoutParams(cardParams);

        // 设置圆角 outline（使窗口呈现圆角）
        mCardLayout.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(24));
            }
        });
        mCardLayout.setClipToOutline(true);

        // 创建内容区域容器 (填充剩余空间)
        FrameLayout contentContainer = new FrameLayout(this);
        contentContainer.setId(View.generateViewId()); // 生成唯一ID供Fragment使用
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        mCardLayout.addView(contentContainer, contentParams);
        mRootLayout.addView(mCardLayout);

        // 设置容器内部点击不关闭（移除原来的OnClickListener）
        // 外部点击通过 FLAG_WATCH_OUTSIDE_TOUCH 机制处理

        setContentView(mRootLayout);
        setupInsetsHandling();

        // 加载 AgentFragment 到内容区域
        loadAgentFragment(contentContainer.getId());

        startEnterAnimation();
    }

    private void setupInsetsHandling() {
        ViewCompat.setOnApplyWindowInsetsListener(mRootLayout, (view, windowInsets) -> {
            Insets systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            boolean imeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime());

            FrameLayout.LayoutParams cardParams = (FrameLayout.LayoutParams) mCardLayout.getLayoutParams();
            int horizontalMargin = dpToPx(16);
            int topMargin = dpToPx(16) + systemBarsInsets.top;
            int baseBottomMargin = dpToPx(16) + systemBarsInsets.bottom;
            int imeLift = imeVisible ? Math.max(0, imeInsets.bottom - systemBarsInsets.bottom) : 0;

            // Keep the card above the IME by moving its bottom margin up by the keyboard overlap.
            // This avoids relying on window resize/pan for the translucent activity.
            int bottomMargin = baseBottomMargin + imeLift;

            // Compute the normal 75% card height from the available viewport without the IME.
            // When the keyboard is visible, cap the card height so the top edge stays on-screen
            // while the bottom edge remains anchored above the keyboard.
            int availableHeight = view.getHeight() - topMargin - baseBottomMargin;
            if (availableHeight > 0) {
                int baseCardHeight = Math.round(availableHeight * 0.75f);
                int maxCardHeightWhenImeVisible = Math.max(0, availableHeight - imeLift);
                cardParams.height = imeVisible
                        ? Math.min(baseCardHeight, maxCardHeightWhenImeVisible)
                        : baseCardHeight;
            }
            cardParams.setMargins(horizontalMargin, topMargin, horizontalMargin, bottomMargin);
            mCardLayout.setLayoutParams(cardParams);
            return windowInsets;
        });

        ViewCompat.requestApplyInsets(mRootLayout);
    }

    private void startEnterAnimation() {
        mRootLayout.post(() -> {
            if (mCardLayout == null || mScrimView == null) {
                return;
            }
            mCardLayout.setTranslationY(mCardLayout.getHeight() + dpToPx(24));
            mCardLayout.animate()
                    .translationY(0f)
                    .setDuration(ENTER_ANIMATION_DURATION_MS)
                    .start();
            mScrimView.animate()
                    .alpha(1f)
                    .setDuration(ENTER_ANIMATION_DURATION_MS)
                    .start();
        });
    }

    /**
     * 加载 AgentFragment
     */
    private void loadAgentFragment(int containerId) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        // 创建并加载 AgentFragment
        AgentFragment agentFragment = new AgentFragment();
        transaction.replace(containerId, agentFragment);
        transaction.commit();
    }

    @Override
    public void finish() {
        if (mIsClosing) {
            return;
        }
        mIsClosing = true;

        // 显示悬浮球
        if (mFloatingBallManager != null) {
            mFloatingBallManager.show();
        }

        if (mCardLayout == null || mScrimView == null) {
            super.finish();
            overridePendingTransition(0, 0);
            return;
        }

        mScrimView.animate()
                .alpha(0f)
                .setDuration(EXIT_ANIMATION_DURATION_MS)
                .start();
        mCardLayout.animate()
                .translationY(mCardLayout.getHeight() + dpToPx(24))
                .setDuration(EXIT_ANIMATION_DURATION_MS)
                .withEndAction(() -> {
                    super.finish();
                    overridePendingTransition(0, 0);
                })
                .start();
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
