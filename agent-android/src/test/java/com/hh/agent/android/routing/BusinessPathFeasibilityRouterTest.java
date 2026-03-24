package com.hh.agent.android.routing;

import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.core.tool.ToolResult;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BusinessPathFeasibilityRouterTest {

    @Test
    public void route_explicitBusinessEntity_prefersBusinessTool() {
        Map<String, ToolExecutor> tools = createTools();
        String rawResponse = "{\"business_path_feasible\":true,"
                + "\"entry_function\":\"search_contacts\","
                + "\"fallback_mode\":\"ui_fallback_on_structured_failure\","
                + "\"reason\":\"llm_router_selected_business_path\"}";

        BusinessPathFeasibilityDecision parsed = FeasibilityRouterResponseParser.parse(rawResponse, tools);
        assertNotNull(parsed);
        assertEquals("search_contacts", parsed.getEntryFunction());

        BusinessPathFeasibilityDecision decision = BusinessPathFeasibilityRouter.route(
                "给张三发送一条测试消息",
                tools,
                new BusinessPathFeasibilityRouter.RouterClient() {
                    @Override
                    public String runStateless(String systemPrompt, String message) {
                        return rawResponse;
                    }
                });

        assertTrue(decision.isBusinessPathFeasible());
        assertEquals("search_contacts", decision.getEntryFunction());
        assertEquals(FeasibilityFallbackMode.UI_FALLBACK_ON_STRUCTURED_FAILURE,
                decision.getFallbackMode());
    }

    @Test
    public void route_pageElementTask_prefersDirectUiPath() {
        BusinessPathFeasibilityDecision decision = BusinessPathFeasibilityRouter.route(
                "点击发送按钮",
                createTools(),
                (systemPrompt, message) -> "{\"business_path_feasible\":false,"
                        + "\"entry_function\":\"none\","
                        + "\"fallback_mode\":\"direct_ui_path\","
                        + "\"reason\":\"llm_router_selected_direct_ui_path\"}");

        assertFalse(decision.isBusinessPathFeasible());
        assertEquals("none", decision.getEntryFunction());
        assertEquals(FeasibilityFallbackMode.DIRECT_UI_PATH, decision.getFallbackMode());
    }

    @Test
    public void route_ambiguousCurrentChat_defaultsToDirectUiPath() {
        BusinessPathFeasibilityDecision decision = BusinessPathFeasibilityRouter.route(
                "给当前聊天发送一条测试消息",
                createTools(),
                (systemPrompt, message) -> "");

        assertFalse(decision.isBusinessPathFeasible());
        assertEquals("none", decision.getEntryFunction());
        assertEquals(FeasibilityFallbackMode.DIRECT_UI_PATH, decision.getFallbackMode());
    }

    @Test
    public void route_invalidLlmOutput_fallsBackToDeterministicDecision() {
        BusinessPathFeasibilityDecision decision = BusinessPathFeasibilityRouter.route(
                "查找张三",
                createTools(),
                (systemPrompt, message) -> "{\"business_path_feasible\":true,"
                        + "\"entry_function\":\"unknown_tool\","
                        + "\"fallback_mode\":\"ui_fallback_on_structured_failure\","
                        + "\"reason\":\"bad_output\"}");

        assertTrue(decision.isBusinessPathFeasible());
        assertEquals("search_contacts", decision.getEntryFunction());
        assertEquals(FeasibilityFallbackMode.NO_UI_FALLBACK, decision.getFallbackMode());
    }

    @Test
    public void augmentAndStrip_restoresOriginalUserRequest() {
        BusinessPathFeasibilityDecision decision = BusinessPathFeasibilityDecision.businessFirst(
                "search_contacts",
                FeasibilityFallbackMode.UI_FALLBACK_ON_STRUCTURED_FAILURE,
                "entry_function_requires_contact_lookup_first");

        String original = "给张三发送一条测试消息";
        String augmented = RoutingPromptAugmenter.augment(original, decision);

        assertTrue(augmented.contains("business_path_feasible=true"));
        assertEquals(original, RoutingPromptAugmenter.strip(augmented));
    }

    private static Map<String, ToolExecutor> createTools() {
        Map<String, ToolExecutor> tools = new HashMap<>();
        tools.put("send_im_message", new FakeToolExecutor("send_im_message",
                ToolDefinition.builder("发送消息", "向指定联系人或会话发送文本消息")
                        .intentExamples("给李四发消息说明天开会", "告诉张三下午三点来会议室")
                        .stringParam("contact_id", "联系人ID", true, "001")
                        .stringParam("message", "消息内容", true, "测试消息")
                        .build()));
        tools.put("search_contacts", new FakeToolExecutor("search_contacts",
                ToolDefinition.builder("搜索联系人", "按姓名搜索联系人")
                        .intentExamples("查找张三", "搜索联系人李四")
                        .stringParam("query", "联系人查询词", true, "张三")
                        .build()));
        tools.put("read_clipboard", new FakeToolExecutor("read_clipboard",
                ToolDefinition.builder("读取剪贴板", "读取当前复制的内容")
                        .intentExamples("看看剪贴板里是什么")
                        .build()));
        tools.put("display_notification", new FakeToolExecutor("display_notification",
                ToolDefinition.builder("显示通知", "显示一条通知提醒")
                        .intentExamples("弹一个通知提醒我开会")
                        .stringParam("title", "通知标题", true, "待办")
                        .stringParam("content", "通知内容", true, "下午三点开会")
                        .build()));
        return tools;
    }

    private static final class FakeToolExecutor implements ToolExecutor {
        private final String name;
        private final ToolDefinition definition;

        private FakeToolExecutor(String name, ToolDefinition definition) {
            this.name = name;
            this.definition = definition;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ToolDefinition getDefinition() {
            return definition;
        }

        @Override
        public ToolResult execute(JSONObject args) {
            return ToolResult.success();
        }
    }
}
