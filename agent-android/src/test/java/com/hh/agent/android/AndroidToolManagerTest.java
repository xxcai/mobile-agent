package com.hh.agent.android;

import com.hh.agent.android.channel.DescribeShortcutChannel;
import com.hh.agent.android.channel.GestureToolChannel;
import com.hh.agent.android.channel.AndroidToolChannelExecutor;
import com.hh.agent.android.channel.ShortcutRuntimeChannel;
import com.hh.agent.android.channel.ViewContextToolChannel;
import com.hh.agent.android.channel.WebActionToolChannel;
import com.hh.agent.android.log.AgentLogger;
import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.shortcut.ShortcutRuntime;
import com.hh.agent.core.tool.ToolResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AndroidToolManagerTest {

    @Before
    public void setUp() {
        AgentLogs.setLogger(new NoOpAgentLogger());
    }

    @After
    public void tearDown() {
        AgentLogs.resetLogger();
    }

    @Test
    public void defaultChannelsExposeShortcutRuntimeAndExcludeLegacyChannel() {
        ShortcutRuntime shortcutRuntime = new ShortcutRuntime();
        AndroidToolManager manager = new AndroidToolManager(null, shortcutRuntime, createTestChannels(shortcutRuntime));

        Map<String, ?> channels = manager.getRegisteredChannels();

        assertTrue(channels.containsKey(ShortcutRuntimeChannel.CHANNEL_NAME));
        assertTrue(channels.containsKey(DescribeShortcutChannel.CHANNEL_NAME));
        assertTrue(channels.containsKey(GestureToolChannel.CHANNEL_NAME));
        assertTrue(channels.containsKey(WebActionToolChannel.CHANNEL_NAME));
        assertTrue(channels.containsKey(ViewContextToolChannel.CHANNEL_NAME));
        assertFalse(channels.containsKey("call_android_tool"));
    }

    @Test
    public void registerShortcutsExposesInjectedShortcutThroughRunShortcut() throws Exception {
        ShortcutRuntime shortcutRuntime = new ShortcutRuntime();
        AndroidToolManager manager = new AndroidToolManager(null, shortcutRuntime, createTestChannels(shortcutRuntime));
        manager.registerShortcut(new ShortcutExecutor() {
            @Override
            public ShortcutDefinition getDefinition() {
                return ShortcutDefinition.builder("search_contacts", "查找联系人", "按姓名或关键字搜索联系人")
                        .stringParam("query", "联系人姓名或搜索关键字", true, "张三")
                        .build();
            }

            @Override
            public ToolResult execute(JSONObject args) {
                return ToolResult.success().with("query", args.optString("query", ""));
            }
        });

        String raw = manager.callTool("run_shortcut", new JSONObject()
                .put("shortcut", "search_contacts")
                .put("args", new JSONObject().put("query", "张三"))
                .toString());
        JSONObject result = new JSONObject(raw);
        assertTrue(result.getBoolean("success"));
        assertEquals("search_contacts", result.getString("shortcut"));
        assertEquals("张三", result.getString("query"));
    }

    @Test
    public void visualOnlyProfileFiltersShortcutAccess() throws Exception {
        AndroidToolManager manager = new AndroidToolManager(
                null,
                new ToolProfilePolicy(AgentRuntimeProfiles.VISUAL_ONLY));
        manager.registerShortcut(new ShortcutExecutor() {
            @Override
            public ShortcutDefinition getDefinition() {
                return ShortcutDefinition.builder("send_im_message", "发送消息", "向指定联系人发送文本消息")
                        .stringParam("message", "消息内容", true, "test")
                        .build();
            }

            @Override
            public ToolResult execute(JSONObject args) {
                return ToolResult.success();
            }
        });

        JSONObject blocked = new JSONObject(manager.callTool("run_shortcut", new JSONObject()
                .put("shortcut", "send_im_message")
                .put("args", new JSONObject().put("message", "hi"))
                .toString()));

        assertFalse(blocked.getBoolean("success"));
        assertEquals("unsupported_tool_channel", blocked.getString("error"));
        assertEquals("run_shortcut", blocked.getString("channel"));
        assertEquals(AgentRuntimeProfiles.VISUAL_ONLY, blocked.getString("profile"));
    }

    @Test
    public void visualOnlyProfileRemovesShortcutToolsFromSchema() throws Exception {
        AndroidToolManager manager = new AndroidToolManager(
                null,
                new ToolProfilePolicy(AgentRuntimeProfiles.VISUAL_ONLY));

        JSONObject toolsJson = new JSONObject(manager.generateToolsJsonString());
        org.json.JSONArray tools = toolsJson.getJSONArray("tools");

        assertEquals(3, tools.length());
        for (int index = 0; index < tools.length(); index++) {
            String toolName = tools.getJSONObject(index).getJSONObject("function").getString("name");
            assertFalse(ShortcutRuntimeChannel.CHANNEL_NAME.equals(toolName));
            assertFalse(DescribeShortcutChannel.CHANNEL_NAME.equals(toolName));
        }
    }

    @Test
    public void resolveCandidateSelectionUsesSessionScopedLatestCandidates() throws Exception {
        AndroidToolManager manager = new AndroidToolManager(null);
        manager.registerShortcut(new ShortcutExecutor() {
            @Override
            public ShortcutDefinition getDefinition() {
                return ShortcutDefinition.builder("search_contacts", "查找联系人", "按姓名搜索")
                        .stringParam("query", "query", true, "张三")
                        .build();
            }

            @Override
            public ToolResult execute(JSONObject args) {
                return ToolResult.success().withJson("candidateSelection", buildCandidateSelectionJson());
            }
        });

        manager.callTool("run_shortcut", new JSONObject()
                .put("shortcut", "search_contacts")
                .put("args", new JSONObject().put("query", "张三"))
                .toString(), "native:container");

        String raw = manager.callTool("run_shortcut", new JSONObject()
                .put("shortcut", "resolve_candidate_selection")
                .put("args", new JSONObject()
                        .put("selectionText", "技术部那个")
                        .put("domain", "contact"))
                .toString(), "native:container");

        JSONObject result = new JSONObject(raw);
        assertTrue(result.getBoolean("success"));
        assertEquals("001", result.getJSONObject("payload").getString("contact_id"));
    }

    @Test
    public void resolveCandidateSelectionSupportsSelectionIndexAlias() throws Exception {
        AndroidToolManager manager = new AndroidToolManager(null);
        manager.registerShortcut(new ShortcutExecutor() {
            @Override
            public ShortcutDefinition getDefinition() {
                return ShortcutDefinition.builder("search_contacts", "查找联系人", "按姓名搜索")
                        .stringParam("query", "query", true, "张三")
                        .build();
            }

            @Override
            public ToolResult execute(JSONObject args) {
                return ToolResult.success().withJson("candidateSelection", buildCandidateSelectionJson());
            }
        });

        manager.callTool("run_shortcut", new JSONObject()
                .put("shortcut", "search_contacts")
                .put("args", new JSONObject().put("query", "张三"))
                .toString(), "native:container");

        String raw = manager.callTool("run_shortcut", new JSONObject()
                .put("shortcut", "resolve_candidate_selection")
                .put("args", new JSONObject()
                        .put("selection_index", 1)
                        .put("domain", "contact"))
                .toString(), "native:container");

        JSONObject result = new JSONObject(raw);
        assertTrue(result.getBoolean("success"));
        assertEquals("001", result.getJSONObject("payload").getString("contact_id"));
    }

    @Test
    public void resolveCandidateSelectionRestoresRoutePayloadForSession() throws Exception {
        AndroidToolManager manager = new AndroidToolManager(null);
        manager.registerShortcut(new ShortcutExecutor() {
            @Override
            public ShortcutDefinition getDefinition() {
                return ShortcutDefinition.builder("resolve_route", "解析页面跳转", "返回多个 route 候选")
                        .stringParam("query", "query", true, "密码")
                        .build();
            }

            @Override
            public ToolResult execute(JSONObject args) {
                return ToolResult.success().withJson("candidateSelection", buildRouteCandidateSelectionJson());
            }
        });

        manager.callTool("run_shortcut", new JSONObject()
                .put("shortcut", "resolve_route")
                .put("args", new JSONObject().put("query", "密码"))
                .toString(), "native:container");

        String raw = manager.callTool("run_shortcut", new JSONObject()
                .put("shortcut", "resolve_candidate_selection")
                .put("args", new JSONObject()
                        .put("selectionText", "login 那个")
                        .put("domain", "route"))
                .toString(), "native:container");

        JSONObject result = new JSONObject(raw);
        assertTrue(result.getBoolean("success"));
        assertEquals("native", result.getJSONObject("payload").getString("targetType"));
        assertEquals("ui://myapp.login/resetPassword",
                result.getJSONObject("payload").getString("uri"));
        assertEquals("登录页找回密码页面",
                result.getJSONObject("payload").getString("title"));
    }

    private static Collection<AndroidToolChannelExecutor> createTestChannels(ShortcutRuntime shortcutRuntime) {
        return Arrays.asList(
                new ShortcutRuntimeChannel(shortcutRuntime),
                new DescribeShortcutChannel(shortcutRuntime),
                new NamedNoOpChannel(GestureToolChannel.CHANNEL_NAME),
                new NamedNoOpChannel(WebActionToolChannel.CHANNEL_NAME),
                new NamedNoOpChannel(ViewContextToolChannel.CHANNEL_NAME)
        );
    }

    private static final class NoOpAgentLogger implements AgentLogger {
        @Override
        public void d(String tag, String message) {
        }

        @Override
        public void i(String tag, String message) {
        }

        @Override
        public void w(String tag, String message) {
        }

        @Override
        public void e(String tag, String message) {
        }

        @Override
        public void e(String tag, String message, Throwable throwable) {
        }
    }

    private static String buildCandidateSelectionJson() {
        try {
            return new JSONObject()
                    .put("domain", "contact")
                    .put("items", new org.json.JSONArray()
                            .put(new JSONObject()
                                    .put("index", 1)
                                    .put("label", "张三（技术部）")
                                    .put("aliases", new org.json.JSONArray().put("技术部那个"))
                                    .put("payload", new JSONObject().put("contact_id", "001")))
                            .put(new JSONObject()
                                    .put("index", 2)
                                    .put("label", "张三（市场部）")
                                    .put("payload", new JSONObject().put("contact_id", "002"))))
                    .toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build test candidate selection", exception);
        }
    }

    private static String buildRouteCandidateSelectionJson() {
        try {
            return new JSONObject()
                    .put("domain", "route")
                    .put("items", new org.json.JSONArray()
                            .put(new JSONObject()
                                    .put("index", 1)
                                    .put("label", "登录页找回密码页面")
                                    .put("stableKey", "native:ui://myapp.login/resetPassword")
                                    .put("aliases", new org.json.JSONArray()
                                            .put("login 那个")
                                            .put("找回密码")
                                            .put("登录页"))
                                    .put("payload", new JSONObject()
                                            .put("targetType", "native")
                                            .put("uri", "ui://myapp.login/resetPassword")
                                            .put("title", "登录页找回密码页面")))
                            .put(new JSONObject()
                                    .put("index", 2)
                                    .put("label", "账号安全修改密码页面")
                                    .put("stableKey", "native:ui://myapp.settings/changePassword")
                                    .put("aliases", new org.json.JSONArray()
                                            .put("settings 那个")
                                            .put("修改密码")
                                            .put("账号安全"))
                                    .put("payload", new JSONObject()
                                            .put("targetType", "native")
                                            .put("uri", "ui://myapp.settings/changePassword")
                                            .put("title", "账号安全修改密码页面"))))
                    .toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build route candidate selection", exception);
        }
    }

    private static final class NamedNoOpChannel implements AndroidToolChannelExecutor {
        private final String channelName;

        private NamedNoOpChannel(String channelName) {
            this.channelName = channelName;
        }

        @Override
        public String getChannelName() {
            return channelName;
        }

        @Override
        public JSONObject buildToolDefinition() {
            return new JSONObject();
        }

        @Override
        public ToolResult execute(JSONObject params) {
            return ToolResult.success();
        }
    }
}
