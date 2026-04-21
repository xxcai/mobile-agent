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
 * 瀹瑰櫒Activity - 鎮诞鐞冪偣鍑诲悗灞曞紑鐨勫崐閫忔槑Activity
 * 浣跨敤SingleTop妯″紡绠＄悊Task鏍?
 */
public class ContainerActivity extends AppCompatActivity {

    public static final String SESSION_KEY = "native:container";
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

        // 鍒濆鍖栨偓娴悆绠＄悊鍣ㄥ苟闅愯棌鎮诞鐞?
        mFloatingBallManager = FloatingBallManager.getInstance(this);
        mFloatingBallManager.hide();

        // 璁剧疆Window涓哄崐閫忔槑
        setupWindow();

        // 鍒涘缓甯冨眬
        setupLayout();

        // 澶勭悊杩斿洖閿?
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    /**
     * 璁剧疆Window涓哄崐閫忔槑
     */
    private void setupWindow() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.FILL;

        // 璁剧疆鏍囧織浣嶇洃鍚獥鍙ｅ閮ㄨЕ鎽镐簨浠?
        // FLAG_NOT_TOUCH_MODAL: 璁╄Е鎽镐簨浠朵紶閫掔粰涓嬪眰绐楀彛
        // FLAG_WATCH_OUTSIDE_TOUCH: 鐩戝惉绐楀彛澶栭儴鐨勮Е鎽镐簨浠?
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        getWindow().setAttributes(params);
    }

    /**
     * 鍒涘缓甯冨眬
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

        // 璁剧疆鍦嗚 outline锛堜娇绐楀彛鍛堢幇鍦嗚锛?
        mCardLayout.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(24));
            }
        });
        mCardLayout.setClipToOutline(true);

        // 鍒涘缓鍐呭鍖哄煙瀹瑰櫒 (濉厖鍓╀綑绌洪棿)
        FrameLayout contentContainer = new FrameLayout(this);
        contentContainer.setId(View.generateViewId()); // 鐢熸垚鍞竴ID渚汧ragment浣跨敤
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        mCardLayout.addView(contentContainer, contentParams);
        mRootLayout.addView(mCardLayout);

        // 璁剧疆瀹瑰櫒鍐呴儴鐐瑰嚮涓嶅叧闂紙绉婚櫎鍘熸潵鐨凮nClickListener锛?
        // 澶栭儴鐐瑰嚮閫氳繃 FLAG_WATCH_OUTSIDE_TOUCH 鏈哄埗澶勭悊

        setContentView(mRootLayout);
        setupInsetsHandling();

        // 鍔犺浇 AgentFragment 鍒板唴瀹瑰尯鍩?
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
     * 鍔犺浇 AgentFragment
     */
    private void loadAgentFragment(int containerId) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        // 鍒涘缓骞跺姞杞?AgentFragment
        AgentFragment agentFragment = AgentFragment.newInstance(SESSION_KEY);
        transaction.replace(containerId, agentFragment);
        transaction.commit();
    }

    public void finishImmediately() {
        if (mIsClosing) {
            return;
        }
        mIsClosing = true;
        if (mScrimView != null) {
            mScrimView.animate().cancel();
        }
        if (mCardLayout != null) {
            mCardLayout.animate().cancel();
        }
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void finish() {
        if (mIsClosing) {
            return;
        }
        mIsClosing = true;

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
        // 澶勭悊绐楀彛澶栭儴瑙︽懜浜嬩欢
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            // 鐐瑰嚮绐楀彛澶栭儴锛圓ctivity Window 60% 鍖哄煙涔嬪鐨勫睆骞曢儴鍒嗭級鍏抽棴Activity
            finish();
            return true;
        }
        return super.onTouchEvent(event);
    }
}



