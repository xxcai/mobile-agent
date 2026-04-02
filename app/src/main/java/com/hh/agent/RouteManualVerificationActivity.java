package com.hh.agent;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolResult;
import com.hh.agent.shortcut.AppShortcutProvider;

import org.json.JSONObject;

import java.util.List;

public class RouteManualVerificationActivity extends AppCompatActivity {
    private TextView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Route Manual Verification");
        setContentView(createContentView());
    }

    private ScrollView createContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        container.setPadding(padding, padding, padding, padding);
        scrollView.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView titleView = new TextView(this);
        titleView.setText("Route 手工验证");
        titleView.setTextSize(20f);
        container.addView(titleView);

        TextView descView = new TextView(this);
        descView.setText("点击以下按钮触发 open_resolved_route。成功用例会打开 RouteNativeDemoActivity，失败用例会在本页展示错误结果。");
        descView.setTextSize(14f);
        descView.setPadding(0, dp(8), 0, dp(12));
        container.addView(descView);

        container.addView(createCaseButton(
                "1. 无参 route",
                buildNoArgCase()));

        container.addView(createCaseButton(
                "2. url encode, encoded=false",
                buildUrlNotEncodedCase()));

        container.addView(createCaseButton(
                "3. url encode, encoded=true",
                buildUrlEncodedCase()));

        container.addView(createCaseButton(
                "4. base64 encode, encoded=false",
                buildBase64NotEncodedCase()));

        container.addView(createCaseButton(
                "5. 缺少 encoded",
                buildMissingEncodedCase()));

        resultView = new TextView(this);
        resultView.setText("结果会显示在这里。");
        resultView.setTextSize(13f);
        resultView.setPadding(0, dp(16), 0, 0);
        container.addView(resultView);

        return scrollView;
    }

    private Button createCaseButton(String label, JSONObject args) {
        Button button = new Button(this);
        button.setText(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        button.setLayoutParams(params);
        button.setOnClickListener(v -> runOpenResolvedRoute(args));
        return button;
    }

    private void runOpenResolvedRoute(JSONObject args) {
        try {
            ShortcutExecutor shortcut = requireOpenResolvedRouteShortcut();
            ToolResult result = shortcut.execute(new JSONObject(args.toString()));
            resultView.setText(result.toJsonString());
        } catch (Exception exception) {
            resultView.setText("execution_failed: " + exception.getMessage());
        }
    }

    private ShortcutExecutor requireOpenResolvedRouteShortcut() {
        List<ShortcutExecutor> shortcuts = AppShortcutProvider.createShortcuts(this);
        for (ShortcutExecutor shortcut : shortcuts) {
            if ("open_resolved_route".equals(shortcut.getDefinition().getName())) {
                return shortcut;
            }
        }
        throw new IllegalStateException("open_resolved_route shortcut not found");
    }

    private JSONObject buildNoArgCase() {
        return object(
                "targetType", "native",
                "uri", "ui://myapp.search/selectActivity",
                "title", "selectActivity");
    }

    private JSONObject buildUrlNotEncodedCase() {
        return object(
                "targetType", "native",
                "uri", "ui://myapp.im/createGroup",
                "title", "createGroup",
                "routeArgs", object(
                        "source", object(
                                "value", "agent card",
                                "encoded", false)));
    }

    private JSONObject buildUrlEncodedCase() {
        return object(
                "targetType", "native",
                "uri", "ui://myapp.im/createGroup",
                "title", "createGroup",
                "routeArgs", object(
                        "source", object(
                                "value", "agent+card",
                                "encoded", true)));
    }

    private JSONObject buildBase64NotEncodedCase() {
        return object(
                "targetType", "native",
                "uri", "ui://myapp.expense/records",
                "title", "records",
                "routeArgs", object(
                        "payload", object(
                                "value", "{\"tab\":\"message\"}",
                                "encoded", false)));
    }

    private JSONObject buildMissingEncodedCase() {
        return object(
                "targetType", "native",
                "uri", "ui://myapp.expense/records",
                "title", "records",
                "routeArgs", object(
                        "payload", object(
                                "value", "{\"tab\":\"message\"}")));
    }

    private JSONObject object(Object... pairs) {
        try {
            JSONObject json = new JSONObject();
            for (int i = 0; i < pairs.length; i += 2) {
                json.put(String.valueOf(pairs[i]), pairs[i + 1]);
            }
            return json;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build manual verification args", exception);
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
