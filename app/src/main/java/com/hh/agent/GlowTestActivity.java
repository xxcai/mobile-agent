package com.hh.agent;

import android.app.Activity;
import android.os.Bundle;

import com.hh.agent.android.glow.EdgeGlowView;

/**
 * 临时测试 Activity，验证 EdgeGlowView 渲染 pipeline。
 * 启动后应看到半透明渐变色覆盖全屏，颜色随时间变化。
 */
public class GlowTestActivity extends Activity {

    private EdgeGlowView edgeGlowView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_glow_test);
        edgeGlowView = findViewById(R.id.edgeGlowView);
        edgeGlowView.setContinuousRender(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
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
}
