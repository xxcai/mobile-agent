package com.hh.agent.android.channel;

import com.hh.agent.android.viewcontext.ViewContextSnapshotProvider;
import com.hh.agent.android.toolschema.ToolSchemaBuilder;
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
    private final Map<String, ViewContextSourceDefinition> sourceDefinitions = createSourceDefinitions();

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public JSONObject buildToolDefinition() throws Exception {
        ToolSchemaBuilder.FunctionToolBuilder builder = ToolSchemaBuilder.function(
                        CHANNEL_NAME,
                        "获取当前界面的视图上下文，用于在业务工具不能直接完成目标时先“看清界面”。这个通道负责感知，不负责点击或滑动。"
                                + buildSourceDescription() + "。")
                .property("source", ToolSchemaBuilder.string()
                        .description("要获取的视图上下文来源。前 3 个值表示单一来源；all 表示聚合模式，用于一次返回多个上下文来源。" + buildSourceDescription() + "。")
                        .enumValues(getSourceNames())
                        .defaultValue(SOURCE_NATIVE_XML), true)
                .property("targetHint", ToolSchemaBuilder.string()
                        .description("用户当前想操作的目标提示，可选。用于帮助后续视图定位，例如“第二个卡片”或“发送按钮”。"), false);
        for (ViewContextSourceDefinition definition : sourceDefinitions.values()) {
            definition.contributeProperties(builder);
        }
        return builder.build();
    }

    @Override
    public ToolResult execute(JSONObject params) {
        try {
            String source = params.optString("source", SOURCE_NATIVE_XML).trim();
            if (source.isEmpty()) {
                source = SOURCE_NATIVE_XML;
            }

            String targetHint = normalizeOptionalText(params.optString("targetHint", null));
            ViewContextSourceDefinition sourceDefinition = sourceDefinitions.get(source);
            if (sourceDefinition == null) {
                return ToolResult.error("invalid_args",
                                "Unsupported source '" + source + "' for " + CHANNEL_NAME)
                        .with("allowedSources",
                                SOURCE_NATIVE_XML + "," + SOURCE_WEB_DOM + ","
                                        + SOURCE_SCREEN_SNAPSHOT + "," + SOURCE_ALL);
            }
            return sourceDefinition.execute(params, targetHint);
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

    private Map<String, ViewContextSourceDefinition> createSourceDefinitions() {
        LinkedHashMap<String, ViewContextSourceDefinition> definitions = new LinkedHashMap<>();
        register(definitions, new NativeXmlViewContextSourceDefinition());
        register(definitions, new WebDomViewContextSourceDefinition());
        register(definitions, new ScreenSnapshotViewContextSourceDefinition());
        register(definitions, new AllViewContextSourceDefinition());
        return definitions;
    }

    private void register(Map<String, ViewContextSourceDefinition> definitions,
                          ViewContextSourceDefinition definition) {
        String sourceName = definition.getSourceName();
        if (definitions.containsKey(sourceName)) {
            throw new IllegalStateException("Duplicate view-context source definition: " + sourceName);
        }
        definitions.put(sourceName, definition);
    }

    private String buildSourceDescription() {
        StringBuilder description = new StringBuilder();
        boolean first = true;
        for (ViewContextSourceDefinition definition : sourceDefinitions.values()) {
            if (!first) {
                description.append("；");
            }
            description.append(definition.getSourceDescription());
            first = false;
        }
        return description.toString();
    }

    private String[] getSourceNames() {
        return sourceDefinitions.keySet().toArray(new String[0]);
    }
}
