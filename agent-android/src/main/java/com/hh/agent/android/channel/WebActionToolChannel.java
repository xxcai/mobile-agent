package com.hh.agent.android.channel;

import com.hh.agent.android.toolschema.ToolSchemaBuilder;
import com.hh.agent.android.viewcontext.ViewObservationSnapshot;
import com.hh.agent.android.viewcontext.ViewObservationSnapshotRegistry;
import com.hh.agent.android.webaction.RealWebActionExecutor;
import com.hh.agent.android.webaction.WebActionExecutor;
import com.hh.agent.android.webaction.WebActionRequest;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;

/**
 * Web action channel kept separate from native gesture injection.
 */
public class WebActionToolChannel implements AndroidToolChannelExecutor {

    public static final String CHANNEL_NAME = "android_web_action_tool";

    private final WebActionExecutor executor;

    public WebActionToolChannel() {
        this(null);
    }

    WebActionToolChannel(WebActionExecutor executor) {
        this.executor = executor;
    }

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public JSONObject buildToolDefinition() throws Exception {
        return ToolSchemaBuilder.function(
                        CHANNEL_NAME,
                        "执行当前宿主页面内的 Web DOM 动作。该通道只消费 web observation，适用于 click、input、eval_js、scroll_to_bottom 等基于 DOM 的操作；不要把它用于原生触摸注入。")
                .property("action", ToolSchemaBuilder.string()
                        .description("Web 动作类型。")
                        .enumValues("click", "input", "eval_js", "scroll_to_bottom"), true)
                .property("ref", ToolSchemaBuilder.string()
                        .description("由 web_dom observation 提供的同回合节点引用。"), false)
                .property("selector", ToolSchemaBuilder.string()
                        .description("目标 DOM 元素的 CSS selector。"), false)
                .property("text", ToolSchemaBuilder.string()
                        .description("当 action=input 时写入的文本。"), false)
                .property("script", ToolSchemaBuilder.string()
                        .description("当 action=eval_js 时执行的 JavaScript 代码。"), false)
                .property("observation", ToolSchemaBuilder.object()
                        .description("基于 web observation 执行时的引用信息。")
                        .property("snapshotId", ToolSchemaBuilder.string()
                                .description("当前回合 web_dom observation 的 snapshot 标识。"), false)
                        .property("targetDescriptor", ToolSchemaBuilder.string()
                                .description("目标元素的人类可读描述，例如提交按钮。"), false), false)
                .build();
    }

    @Override
    public ToolResult execute(JSONObject params) {
        try {
            WebActionRequest request = WebActionRequest.fromJson(params);
            ToolResult validationResult = validate(request);
            if (validationResult != null) {
                return validationResult;
            }
            return getExecutor().execute(request);
        } catch (Exception e) {
            return ToolResult.error("execution_failed", e.getMessage())
                    .with("channel", CHANNEL_NAME)
                    .with("domain", "web");
        }
    }

    private ToolResult validate(WebActionRequest request) {
        if (request.action.isEmpty()) {
            return ToolResult.error("invalid_args", CHANNEL_NAME + " requires a non-empty 'action' field")
                    .with("channel", CHANNEL_NAME)
                    .with("domain", "web");
        }
        if (!"click".equals(request.action)
                && !"input".equals(request.action)
                && !"eval_js".equals(request.action)
                && !"scroll_to_bottom".equals(request.action)) {
            return ToolResult.error("invalid_args", "Unsupported web action '" + request.action + "'")
                    .with("channel", CHANNEL_NAME)
                    .with("domain", "web");
        }
        if (request.observation == null) {
            return ToolResult.error("missing_web_observation",
                            "web action requires current-turn web observation evidence before execution")
                    .with("channel", CHANNEL_NAME)
                    .with("domain", "web")
                    .with("suggestedNextTool", ViewContextToolChannel.CHANNEL_NAME)
                    .with("messageForModel",
                            "Call android_view_context_tool first, then retry the web action with observation.snapshotId.");
        }
        String snapshotId = request.observation.optString("snapshotId", "").trim();
        if (snapshotId.isEmpty()) {
            return ToolResult.error("missing_web_snapshot_id",
                            "web action observation must include a non-empty snapshotId")
                    .with("channel", CHANNEL_NAME)
                    .with("domain", "web")
                    .with("suggestedNextTool", ViewContextToolChannel.CHANNEL_NAME)
                    .with("messageForModel",
                            "Call android_view_context_tool first, then retry the web action with observation.snapshotId.");
        }
        ToolResult snapshotValidation = validateSnapshotExists(snapshotId);
        if (snapshotValidation != null) {
            return snapshotValidation;
        }
        if (("click".equals(request.action) || "input".equals(request.action))
                && isEmpty(request.ref)
                && isEmpty(request.selector)) {
            return ToolResult.error("invalid_args",
                            request.action + " requires a non-empty 'ref' or 'selector'")
                    .with("channel", CHANNEL_NAME)
                    .with("domain", "web");
        }
        if ("input".equals(request.action) && (request.text == null || request.text.isEmpty())) {
            return ToolResult.error("invalid_args", "input requires a non-empty 'text'")
                    .with("channel", CHANNEL_NAME)
                    .with("domain", "web");
        }
        if ("eval_js".equals(request.action) && isEmpty(request.script)) {
            return ToolResult.error("invalid_args", "eval_js requires a non-empty 'script'")
                    .with("channel", CHANNEL_NAME)
                    .with("domain", "web");
        }
        return null;
    }

    private ToolResult validateSnapshotExists(String snapshotId) {
        ViewObservationSnapshot snapshot = ViewObservationSnapshotRegistry.findSnapshotById(snapshotId);
        if (snapshot == null) {
            return ToolResult.error("invalid_web_snapshot_id",
                            "snapshotId '" + snapshotId + "' not found in registry")
                    .with("channel", CHANNEL_NAME)
                    .with("domain", "web")
                    .with("suggestedNextTool", ViewContextToolChannel.CHANNEL_NAME)
                    .with("messageForModel",
                            "The snapshotId is expired or invalid. Call android_view_context_tool to get a fresh observation, then retry with the new snapshotId.");
        }
        return null;
    }

    private boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private WebActionExecutor getExecutor() {
        return executor != null ? executor : RealWebActionExecutor.createDefault();
    }
}
