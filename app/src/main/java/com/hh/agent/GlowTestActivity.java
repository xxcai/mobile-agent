package com.hh.agent;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.hh.agent.android.glow.EdgeGlowView;

/**
 * 临时测试 Activity，验证 EdgeGlowView 渲染 pipeline。
 */
public class GlowTestActivity extends Activity {

    private EdgeGlowView edgeGlowView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Extend window behind status bar and navigation bar
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                        | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        );
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        }

        setContentView(R.layout.activity_glow_test);
        edgeGlowView = findViewById(R.id.edgeGlowView);

        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        btnStart.setOnClickListener(v -> edgeGlowView.setActive(true));
        btnStop.setOnClickListener(v -> edgeGlowView.setActive(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (edgeGlowView != null) {
            edgeGlowView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (edgeGlowView != null) {
            edgeGlowView.onPause();
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }
}
