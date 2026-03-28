package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.android.viewcontext.RuntimeViewContextSourceResolver;
import com.hh.agent.android.viewcontext.ViewContextSourceSelection;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * View-context channel used to validate perception-channel routing.
 * Native XML is produced from the in-process host view tree; DOM / screenshot remain mock.
 */
public class ViewContextToolChannel implements AndroidToolChannelExecutor {

    public static final String CHANNEL_NAME = "android_view_context_tool";

    static final String SOURCE_NATIVE_XML = "native_xml";
    static final String SOURCE_WEB_DOM = "web_dom";
    static final String SOURCE_SCREEN_SNAPSHOT = "screen_snapshot";
    static final String SOURCE_ALL = "all";

    static final String MOCK_WEB_DOM =
            "<html><body><div id=\"mock-root\"><button data-action=\"open-contact\">张三</button></div></body></html>";

    static final String MOCK_SCREEN_SNAPSHOT = "mock://screen/current/native-xml-validation";
    private final Map<String, ViewContextSourceHandler> sourceHandlers = createSourceHandlers();
    private final RuntimeViewContextSourceResolver sourceResolver;

    public ViewContextToolChannel() {
        this(RuntimeViewContextSourceResolver.createDefault());
    }

    ViewContextToolChannel(RuntimeViewContextSourceResolver sourceResolver) {
        this.sourceResolver = sourceResolver;
    }

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public JSONObject buildToolDefinition() throws Exception {
        ToolSchemaBuilder.FunctionToolBuilder builder = ToolSchemaBuilder.function(
                        CHANNEL_NAME,
                        "获取当前界面的视图上下文，用于在业务工具不能直接完成目标时先“看清界面”。"
                                + "视图来源由 runtime 根据当前 Activity 和页面结构自动选择；当前支持 "
                                + SOURCE_NATIVE_XML + " 与 " + SOURCE_WEB_DOM + "。")
                .property("targetHint", ToolSchemaBuilder.string()
                        .description("用户当前想操作的目标提示，可选。用于帮助后续视图定位，例如“第二个卡片”或“发送按钮”。"), false);
        return builder.build();
    }

    @Override
    public ToolResult execute(JSONObject params) {
        try {
            String targetHint = normalizeOptionalText(params.optString("targetHint", null));
            ViewContextSourceSelection selection = sourceResolver.resolve();
            String source = selection.getSource();
            if (source == null || source.trim().isEmpty()) {
                return ToolResult.error("execution_failed",
                                "Runtime did not resolve a view-context source")
                        .with("selectionStatus", selection.getStatus().name());
            }
            ViewContextSourceHandler sourceHandler = sourceHandlers.get(source);
            if (sourceHandler == null) {
                return ToolResult.error("execution_failed",
                                "Resolved unsupported source '" + source + "' for " + CHANNEL_NAME)
                        .with("selectionStatus", selection.getStatus().name());
            }
            ToolResult result = sourceHandler.execute(params, targetHint);
            return result
                    .with("selectionStatus", selection.getStatus().name())
                    .with("matchedActivityClassName", selection.getMatchedActivityClassName());
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

    private Map<String, ViewContextSourceHandler> createSourceHandlers() {
        LinkedHashMap<String, ViewContextSourceHandler> handlers = new LinkedHashMap<>();
        register(handlers, new NativeXmlViewContextSourceHandler());
        register(handlers, new WebDomViewContextSourceHandler());
        return handlers;
    }

    private void register(Map<String, ViewContextSourceHandler> handlers,
                          ViewContextSourceHandler handler) {
        String sourceName = handler.getSourceName();
        if (handlers.containsKey(sourceName)) {
            throw new IllegalStateException("Duplicate view-context source handler: " + sourceName);
        }
        handlers.put(sourceName, handler);
    }

}
