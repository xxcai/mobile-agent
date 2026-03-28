package com.hh.agent;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class RouteNativeDemoActivity extends AppCompatActivity {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URI = "uri";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String uri = getIntent().getStringExtra(EXTRA_URI);
        if (title == null) {
            title = "Native Route Demo";
        }
        if (uri == null) {
            uri = "<none>";
        }
        setTitle(title);
        setContentView(createContentView(title, uri));
    }

    private LinearLayout createContentView(String title, String uri) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        int padding = dp(24);
        container.setPadding(padding, padding, padding, padding);

        TextView titleView = new TextView(this);
        titleView.setText("Native Route Opened: " + title);
        titleView.setTextSize(18f);
        container.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView uriView = new TextView(this);
        uriView.setText("uri=" + uri);
        uriView.setTextSize(14f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        container.addView(uriView, params);
        return container;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
