package com.hh.agent;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    public static final String EXTRA_ENABLE_DEBUG_CONTROLS = "enable_debug_controls";
    public static final String EXTRA_PAGE_TEMPLATE_ASSET = "page_template_asset";
    private static final String TEMPLATE_DEFAULT = "business_page.html";
    private static final String TEMPLATE_DELAYED = "business_page_delayed.html";
    private static final String TEMPLATE_FORM = "business_page_form.html";

    private WebView webView;
    private TextView probeResultView;
    private TextView debugStateView;
    private LinearLayout debugPanel;
    private String targetHint;
    private boolean debugControlsEnabled;
    private String currentTemplateAsset = TEMPLATE_DEFAULT;
    private String latestSnapshotId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_web);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String htmlContent = getIntent().getStringExtra(EXTRA_HTML_CONTENT);
        currentTemplateAsset = normalizeTemplateAsset(getIntent().getStringExtra(EXTRA_PAGE_TEMPLATE_ASSET));
        if (title == null) {
            title = "业务页面";
        }
        if (htmlContent == null) {
            htmlContent = "<p>页面内容为空。</p>";
        }
        targetHint = getIntent().getStringExtra(EXTRA_PROBE_TARGET_HINT);
        if (targetHint == null || targetHint.trim().isEmpty()) {
            targetHint = "业务页面";
        }
        debugControlsEnabled = getIntent().getBooleanExtra(EXTRA_ENABLE_DEBUG_CONTROLS, false)
                || getIntent().getBooleanExtra(EXTRA_AUTO_RUN_VIEW_CONTEXT_PROBE, false);

        ImageView backButton = findViewById(R.id.businessWebBackButton);
        TextView titleView = findViewById(R.id.businessWebTitleView);
        webView = findViewById(R.id.businessWebView);
        probeResultView = findViewById(R.id.businessWebProbeResultView);
        debugStateView = findViewById(R.id.businessWebDebugStateView);
        debugPanel = findViewById(R.id.businessWebDebugPanel);
        Button runViewContextButton = findViewById(R.id.businessWebRunViewContextButton);
        Button runWebActionButton = findViewById(R.id.businessWebRunWebActionButton);
        Button loadStaticTemplateButton = findViewById(R.id.businessWebLoadStaticTemplateButton);
        Button loadDelayedTemplateButton = findViewById(R.id.businessWebLoadDelayedTemplateButton);
        Button loadFormTemplateButton = findViewById(R.id.businessWebLoadFormTemplateButton);

        backButton.setOnClickListener(v -> finish());
        titleView.setText(title);
        configureWebView(webView, title, htmlContent);
        setupDebugControls(title, htmlContent, runViewContextButton, runWebActionButton,
                loadStaticTemplateButton, loadDelayedTemplateButton, loadFormTemplateButton);
        maybeRunViewContextProbe(webView, probeResultView);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView webView, String title, String htmlContent) {
        boolean enableJavascript = debugControlsEnabled;
        webView.getSettings().setJavaScriptEnabled(enableJavascript);
        webView.getSettings().setDomStorageEnabled(enableJavascript);
        webView.loadDataWithBaseURL("file:///android_asset/",
                buildHtmlForTemplate(title, htmlContent, currentTemplateAsset),
                "text/html", "UTF-8", null);
        updateDebugState("page_loaded");
    }

    private String buildHtmlForTemplate(String title, String htmlContent, String templateAsset) {
        if (TEMPLATE_DEFAULT.equals(templateAsset)) {
            return loadAssetHtml(TEMPLATE_DEFAULT)
                    .replace("__TITLE__", escapeHtml(title))
                    .replace("__CONTENT__", htmlContent)
                    .replace("__STAMP__", escapeHtml("Mock local HTML"));
        }
        return loadAssetHtml(templateAsset);
    }

    private String loadAssetHtml(String assetName) {
        try (InputStream inputStream = getAssets().open(assetName);
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
        probeResultView.setVisibility(TextView.VISIBLE);
        probeResultView.setText("running probe...");
        webView.postDelayed(() -> runViewContextProbe(targetHint, probeResultView), 500L);
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
                latestSnapshotId = result.optString("snapshotId", null);
                String actualSource = result.optString("source", "<none>");
                String actualDomain = result.optString("interactionDomain", "<none>");
                String actualActivity = result.optString("activityClassName", "<none>");
                boolean pass = result.optBoolean("success", false)
                        && "web_dom".equals(actualSource)
                        && "web".equals(actualDomain)
                        && BusinessWebActivity.class.getName().equals(actualActivity);
                report = "# Business WebView Context Probe\n"
                        + "test_item=view_context_runtime_web_dom\n"
                        + "input.target_hint=" + targetHint + "\n"
                        + "input.template=" + currentTemplateAsset + "\n"
                        + "expected.source=web_dom\n"
                        + "expected.interactionDomain=web\n"
                        + "expected.activityClassName=" + BusinessWebActivity.class.getName() + "\n"
                        + "actual.success=" + result.optBoolean("success", false) + "\n"
                        + "actual.source=" + actualSource + "\n"
                        + "actual.interactionDomain=" + actualDomain + "\n"
                        + "actual.selectionStatus=" + result.optString("selectionStatus", "<none>") + "\n"
                        + "actual.activityClassName=" + actualActivity + "\n"
                        + "actual.observationMode=" + result.optString("observationMode", "<none>") + "\n"
                        + "actual.snapshotId=" + result.optString("snapshotId", "<none>") + "\n"
                        + "actual.pageUrl=" + result.optString("pageUrl", "<none>") + "\n"
                        + "actual.pageTitle=" + result.optString("pageTitle", "<none>") + "\n"
                        + "actual.webViewCandidateCount=" + result.optString("webViewCandidateCount", "<none>") + "\n"
                        + "actual.webViewSelectionReason=" + result.optString("webViewSelectionReason", "<none>") + "\n"
                        + "result=" + (pass ? "PASS" : "FAIL");
            } catch (Exception e) {
                report = "# Business WebView Context Probe\n"
                        + "test_item=view_context_runtime_web_dom\n"
                        + "input.target_hint=" + targetHint + "\n"
                        + "input.template=" + currentTemplateAsset + "\n"
                        + "expected.source=web_dom\n"
                        + "actual.success=false\n"
                        + "actual.error=" + e.getMessage() + "\n"
                        + "result=FAIL";
            }
            String finalReport = report;
            runOnUiThread(() -> {
                probeResultView.setText(finalReport);
                updateDebugState("view_context_probe_finished");
            });
        });
        worker.start();
    }

    private void runWebActionProbe() {
        if (TextUtils.isEmpty(latestSnapshotId)) {
            probeResultView.setText("# Business Web Action Probe\n"
                    + "test_item=web_action_mock_click\n"
                    + "input.snapshotId=<none>\n"
                    + "input.selector=#debug-submit\n"
                    + "expected.channel=android_web_action_tool\n"
                    + "actual.success=false\n"
                    + "actual.error=missing_web_snapshot_id\n"
                    + "actual.message=Run View Context first\n"
                    + "result=FAIL");
            return;
        }
        Thread worker = new Thread(() -> {
            String report;
            try {
                AndroidToolManager manager = new AndroidToolManager(this);
                JSONObject args = new JSONObject()
                        .put("action", "click")
                        .put("selector", "#debug-submit")
                        .put("observation", new JSONObject()
                                .put("snapshotId", latestSnapshotId)
                                .put("targetDescriptor", "debug submit button"));
                String resultJson = manager.callTool("android_web_action_tool", args.toString());
                JSONObject result = new JSONObject(resultJson);
                boolean pass = result.optBoolean("success", false)
                        && "android_web_action_tool".equals(result.optString("channel", "<none>"))
                        && "web".equals(result.optString("domain", "<none>"))
                        && "click".equals(result.optString("action", "<none>"));
                report = "# Business Web Action Probe\n"
                        + "test_item=web_action_mock_click\n"
                        + "input.snapshotId=" + latestSnapshotId + "\n"
                        + "input.selector=#debug-submit\n"
                        + "expected.channel=android_web_action_tool\n"
                        + "expected.domain=web\n"
                        + "expected.action=click\n"
                        + "actual.success=" + result.optBoolean("success", false) + "\n"
                        + "actual.channel=" + result.optString("channel", "<none>") + "\n"
                        + "actual.domain=" + result.optString("domain", "<none>") + "\n"
                        + "actual.action=" + result.optString("action", "<none>") + "\n"
                        + "actual.selector=" + result.optString("selector", "<none>") + "\n"
                        + "actual.mock=" + result.optString("mock", "<none>") + "\n"
                        + "actual.message=" + result.optString("message", "<none>") + "\n"
                        + "result=" + (pass ? "PASS" : "FAIL");
            } catch (Exception e) {
                report = "# Business Web Action Probe\n"
                        + "test_item=web_action_mock_click\n"
                        + "input.snapshotId=" + latestSnapshotId + "\n"
                        + "input.selector=#debug-submit\n"
                        + "expected.channel=android_web_action_tool\n"
                        + "actual.success=false\n"
                        + "actual.error=" + e.getMessage() + "\n"
                        + "result=FAIL";
            }
            String finalReport = report;
            runOnUiThread(() -> {
                probeResultView.setText(finalReport);
                updateDebugState("web_action_probe_finished");
            });
        });
        worker.start();
    }

    private void setupDebugControls(String title,
                                    String htmlContent,
                                    Button runViewContextButton,
                                    Button runWebActionButton,
                                    Button loadStaticTemplateButton,
                                    Button loadDelayedTemplateButton,
                                    Button loadFormTemplateButton) {
        debugPanel.setVisibility(debugControlsEnabled ? TextView.VISIBLE : TextView.GONE);
        probeResultView.setText("debug panel ready");
        runViewContextButton.setOnClickListener(v -> runViewContextProbe(targetHint, probeResultView));
        runWebActionButton.setOnClickListener(v -> runWebActionProbe());
        loadStaticTemplateButton.setOnClickListener(v -> reloadTemplate(title, htmlContent, TEMPLATE_DEFAULT));
        loadDelayedTemplateButton.setOnClickListener(v -> reloadTemplate(title, htmlContent, TEMPLATE_DELAYED));
        loadFormTemplateButton.setOnClickListener(v -> reloadTemplate(title, htmlContent, TEMPLATE_FORM));
    }

    private void reloadTemplate(String title, String htmlContent, String templateAsset) {
        currentTemplateAsset = templateAsset;
        latestSnapshotId = null;
        configureWebView(webView, title, htmlContent);
        probeResultView.setText("template=" + templateAsset + "\nnext_step=Run View Context");
    }

    private void updateDebugState(String event) {
        if (!debugControlsEnabled) {
            return;
        }
        debugStateView.setText("event=" + event
                + "\ntemplate=" + currentTemplateAsset
                + "\njsEnabled=" + webView.getSettings().getJavaScriptEnabled()
                + "\nurl=" + safeText(webView.getUrl())
                + "\ntitle=" + safeText(webView.getTitle())
                + "\ntargetHint=" + safeText(targetHint)
                + "\nlatestSnapshotId=" + safeText(latestSnapshotId));
    }

    private String normalizeTemplateAsset(String templateAsset) {
        if (TEMPLATE_DELAYED.equals(templateAsset) || TEMPLATE_FORM.equals(templateAsset)) {
            return templateAsset;
        }
        return TEMPLATE_DEFAULT;
    }

    private String safeText(String value) {
        return value == null || value.trim().isEmpty() ? "<none>" : value;
    }
}
