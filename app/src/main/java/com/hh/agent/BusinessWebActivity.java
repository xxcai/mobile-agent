package com.hh.agent;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hh.agent.android.AndroidToolManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class BusinessWebActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_HTML_CONTENT = "html_content";
    public static final String EXTRA_AUTO_RUN_VIEW_CONTEXT_PROBE = "auto_run_view_context_probe";
    public static final String EXTRA_PROBE_TARGET_HINT = "probe_target_hint";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_web);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String htmlContent = getIntent().getStringExtra(EXTRA_HTML_CONTENT);
        if (title == null) {
            title = "业务页面";
        }
        if (htmlContent == null) {
            htmlContent = "<p>页面内容为空。</p>";
        }

        ImageView backButton = findViewById(R.id.businessWebBackButton);
        TextView titleView = findViewById(R.id.businessWebTitleView);
        WebView webView = findViewById(R.id.businessWebView);
        TextView probeResultView = findViewById(R.id.businessWebProbeResultView);

        backButton.setOnClickListener(v -> finish());
        titleView.setText(title);
        configureWebView(webView, title, htmlContent);
        maybeRunViewContextProbe(webView, probeResultView);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView webView, String title, String htmlContent) {
        webView.getSettings().setJavaScriptEnabled(false);
        webView.getSettings().setDomStorageEnabled(false);
        String html = loadHtmlTemplate()
                .replace("__TITLE__", escapeHtml(title))
                .replace("__CONTENT__", htmlContent)
                .replace("__STAMP__", escapeHtml("Mock local HTML"));
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
    }

    private String loadHtmlTemplate() {
        try (InputStream inputStream = getAssets().open("business_page.html");
             InputStreamReader reader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch (IOException exception) {
            return "<html><body><h1>__TITLE__</h1><div>__CONTENT__</div></body></html>";
        }
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void maybeRunViewContextProbe(WebView webView, TextView probeResultView) {
        if (!getIntent().getBooleanExtra(EXTRA_AUTO_RUN_VIEW_CONTEXT_PROBE, false)) {
            return;
        }
        String targetHint = getIntent().getStringExtra(EXTRA_PROBE_TARGET_HINT);
        if (targetHint == null || targetHint.trim().isEmpty()) {
            targetHint = "业务页面";
        }
        final String finalTargetHint = targetHint;
        probeResultView.setVisibility(TextView.VISIBLE);
        probeResultView.setText("running probe...");
        webView.postDelayed(() -> runViewContextProbe(finalTargetHint, probeResultView), 500L);
    }

    private void runViewContextProbe(String targetHint, TextView probeResultView) {
        Thread worker = new Thread(() -> {
            String report;
            try {
                AndroidToolManager manager = new AndroidToolManager(this);
                String resultJson = manager.callTool(
                        "android_view_context_tool",
                        new JSONObject().put("targetHint", targetHint).toString());
                JSONObject result = new JSONObject(resultJson);
                report = "success=" + result.optBoolean("success", false)
                        + "\nsource=" + result.optString("source", "<none>")
                        + "\nselectionStatus=" + result.optString("selectionStatus", "<none>")
                        + "\nactivityClassName=" + result.optString("activityClassName", "<none>");
            } catch (Exception e) {
                report = "success=false\nerror=" + e.getMessage();
            }
            String finalReport = report;
            runOnUiThread(() -> probeResultView.setText(finalReport));
        });
        worker.start();
    }
}
