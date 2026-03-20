package com.hh.agent;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 用于验证自定义隐藏列表生效。
 */
public class FloatingBallHiddenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Floating Ball Hidden");
        setContentView(createContentView(
                "测试路径：进入本页后悬浮球应隐藏。\n" +
                        "本页通过 FloatingBallVisibilityConfig 加入了隐藏列表。"
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
