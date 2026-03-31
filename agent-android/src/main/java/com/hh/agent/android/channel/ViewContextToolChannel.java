package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.android.viewcontext.RuntimeViewContextSourceResolver;
import com.hh.agent.android.viewcontext.ViewContextSourceSelection;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * View-context channel used to route the current page observation to the right perception source.
 */
public class ViewContextToolChannel implements AndroidToolChannelExecutor {

    public static final String CHANNEL_NAME = "android_view_context_tool";

    static final String SOURCE_NATIVE_XML = "native_xml";
    static final String SOURCE_WEB_DOM = "web_dom";
    static final String SOURCE_SCREEN_SNAPSHOT = "screen_snapshot";
    static final String SOURCE_ALL = "all";

    static final String MOCK_WEB_DOM =
            "<html><body><div id=\"mock-root\"><button data-action=\"open-contact\">寮犱笁</button></div></body></html>";

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
                        "Get the current page context for downstream reasoning when direct business tools cannot finish the task. Prefer hybridObservation.summary for page understanding, hybridObservation.actionableNodes for target selection, and hybridObservation.conflicts for confidence checks. Use nativeViewXml, screenVisionCompact, or webDom only as raw fallback evidence.")
                .property("targetHint", ToolSchemaBuilder.string()
                        .description("Optional hint about the target the user wants to act on, such as send button or second card."), false);
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
        register(handlers, new ScreenSnapshotViewContextSourceHandler());
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
