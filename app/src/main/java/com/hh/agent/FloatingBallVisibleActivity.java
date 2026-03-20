package com.hh.agent;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 用于验证应用内普通页面会继续显示悬浮球。
 */
public class FloatingBallVisibleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Floating Ball Visible");
        setContentView(createContentView(
                "测试路径：进入本页后悬浮球应继续显示。\n" +
                        "这是应用内普通 Activity，不在隐藏列表里。"
        ));
    }

    private LinearLayout createContentView(String text) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        int padding = dp(24);
        container.setPadding(padding, padding, padding, padding);

        TextView description = new TextView(this);
        description.setText(text);
        description.setTextSize(16f);
        container.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        return container;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
