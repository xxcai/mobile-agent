package com.hh.agent.android.channel;

import com.hh.agent.core.tool.ToolResult;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Mock view-context channel used to validate perception-channel routing.
 * Real native XML / DOM / screenshot collection will be added in a later step.
 */
public class ViewContextToolChannel implements AndroidToolChannelExecutor {

    public static final String CHANNEL_NAME = "android_view_context_tool";

    private static final String SOURCE_NATIVE_XML = "native_xml";
    private static final String SOURCE_WEB_DOM = "web_dom";
    private static final String SOURCE_SCREEN_SNAPSHOT = "screen_snapshot";
    private static final String SOURCE_ALL = "all";

    private static final String MOCK_NATIVE_XML =
            "<hierarchy rotation=\"0\">"
                    + "<node index=\"0\" text=\"\" resource-id=\"com.hh.agent:id/root\" class=\"android.widget.FrameLayout\" clickable=\"false\">"
                    + "<node index=\"0\" text=\"消息\" resource-id=\"com.hh.agent:id/tab_message\" class=\"android.widget.TextView\" clickable=\"true\" bounds=\"[0,0][240,120]\"/>"
                    + "<node index=\"1\" text=\"张三\" resource-id=\"com.hh.agent:id/contact_name\" class=\"android.widget.TextView\" clickable=\"true\" bounds=\"[24,160][300,240]\"/>"
                    + "<node index=\"2\" text=\"新消息\" resource-id=\"com.hh.agent:id/unread_badge\" class=\"android.widget.TextView\" clickable=\"false\" bounds=\"[320,170][420,220]\"/>"
                    + "<node index=\"3\" text=\"发送消息\" resource-id=\"com.hh.agent:id/send_button\" class=\"android.widget.Button\" clickable=\"true\" bounds=\"[24,640][240,720]\"/>"
                    + "</node>"
                    + "</hierarchy>";

    private static final String MOCK_WEB_DOM =
            "<html><body><div id=\"mock-root\"><button data-action=\"open-contact\">张三</button></div></body></html>";

    private static final String MOCK_SCREEN_SNAPSHOT = "mock://screen/current/native-xml-validation";

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public JSONObject buildToolDefinition() throws Exception {
        JSONObject properties = new JSONObject();

        properties.put("source", new JSONObject()
                .put("type", "string")
                .put("description", "要获取的视图上下文来源。native_xml 优先用于验证原生界面树；web_dom 和 screen_snapshot 当前为 mock。")
                .put("enum", new JSONArray()
                        .put(SOURCE_NATIVE_XML)
                        .put(SOURCE_WEB_DOM)
                        .put(SOURCE_SCREEN_SNAPSHOT)
                        .put(SOURCE_ALL))
                .put("default", SOURCE_NATIVE_XML));
        properties.put("targetHint", new JSONObject()
                .put("type", "string")
                .put("description", "用户当前想操作的目标提示，可选。用于帮助后续视图定位，例如“第二个卡片”或“发送按钮”。"));
        properties.put("includeMockWebDom", new JSONObject()
                .put("type", "boolean")
                .put("description", "当 source=all 时，是否附带 mock web DOM。")
                .put("default", false));
        properties.put("includeMockScreenshot", new JSONObject()
                .put("type", "boolean")
                .put("description", "当 source=all 时，是否附带 mock screenshot 引用。")
                .put("default", false));

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", new JSONArray().put("source"));

        JSONObject function = new JSONObject();
        function.put("name", CHANNEL_NAME);
        function.put("description", "获取当前界面的视图上下文，用于在业务工具不能直接完成目标时先“看清界面”。这个通道负责感知，不负责点击或滑动。优先使用 native_xml 验证原生界面树；web_dom 和 screen_snapshot 当前仅返回 mock。");
        function.put("parameters", parameters);

        return new JSONObject()
                .put("type", "function")
                .put("function", function);
    }

    @Override
    public ToolResult execute(JSONObject params) {
        try {
            String source = params.optString("source", SOURCE_NATIVE_XML).trim();
            if (source.isEmpty()) {
                source = SOURCE_NATIVE_XML;
            }

            boolean includeMockWebDom = params.optBoolean("includeMockWebDom", false);
            boolean includeMockScreenshot = params.optBoolean("includeMockScreenshot", false);
            String targetHint = normalizeOptionalText(params.optString("targetHint", null));

            ToolResult result = ToolResult.success()
                    .with("channel", CHANNEL_NAME)
                    .with("source", source)
                    .with("mock", true)
                    .with("targetHint", targetHint);

            switch (source) {
                case SOURCE_NATIVE_XML:
                    return result
                            .with("nativeViewXml", MOCK_NATIVE_XML)
                            .with("webDom", (String) null)
                            .with("screenSnapshot", (String) null);
                case SOURCE_WEB_DOM:
                    return result
                            .with("nativeViewXml", (String) null)
                            .with("webDom", MOCK_WEB_DOM)
                            .with("screenSnapshot", (String) null);
                case SOURCE_SCREEN_SNAPSHOT:
                    return result
                            .with("nativeViewXml", (String) null)
                            .with("webDom", (String) null)
                            .with("screenSnapshot", MOCK_SCREEN_SNAPSHOT);
                case SOURCE_ALL:
                    return result
                            .with("nativeViewXml", MOCK_NATIVE_XML)
                            .with("webDom", includeMockWebDom ? MOCK_WEB_DOM : null)
                            .with("screenSnapshot", includeMockScreenshot ? MOCK_SCREEN_SNAPSHOT : null);
                default:
                    return ToolResult.error("invalid_args",
                                    "Unsupported source '" + source + "' for " + CHANNEL_NAME)
                            .with("allowedSources",
                                    SOURCE_NATIVE_XML + "," + SOURCE_WEB_DOM + ","
                                            + SOURCE_SCREEN_SNAPSHOT + "," + SOURCE_ALL);
            }
        } catch (Exception e) {
            return ToolResult.error("execution_failed", e.getMessage());
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
