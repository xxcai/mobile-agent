package com.hh.agent.android;

import com.hh.agent.android.log.AgentLogger;
import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolDefinition;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.core.tool.ToolResult;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ToolExecutorShortcutAdapterIntegrationTest {

    @Before
    public void setUp() {
        AgentLogs.setLogger(new NoOpAgentLogger());
    }

    @After
    public void tearDown() {
        AgentLogs.resetLogger();
    }

    @Test
    public void registerToolAlsoRegistersShortcutRuntimeEntry() throws Exception {
        AndroidToolManager manager = new AndroidToolManager(null);
        ToolExecutor tool = createSendMessageTool();

        manager.registerTool(tool);

        Map<String, ShortcutExecutor> shortcuts = manager.getRegisteredShortcuts();
        assertTrue(shortcuts.containsKey("send_im_message"));

        String raw = manager.callTool("run_shortcut", new JSONObject()
                .put("shortcut", "send_im_message")
                .put("args", new JSONObject()
                        .put("contact_id", "003")
                        .put("message", "test"))
                .toString());
        JSONObject result = new JSONObject(raw);
        assertTrue(result.getBoolean("success"));
        assertEquals("send_im_message", result.getString("shortcut"));
        assertEquals("003", result.getString("contact_id"));
    }

    @Test
    public void registerToolsBatchAlsoRegistersShortcutRuntimeEntries() {
        AndroidToolManager manager = new AndroidToolManager(null);
        ToolExecutor tool = createSendMessageTool();

        manager.registerTools(Collections.singletonMap(tool.getName(), tool));

        assertTrue(manager.getRegisteredShortcuts().containsKey("send_im_message"));
    }

    private static ToolExecutor createSendMessageTool() {
        return new ToolExecutor() {
            @Override
            public String getName() {
                return "send_im_message";
            }

            @Override
            public ToolDefinition getDefinition() {
                return ToolDefinition.builder("发送消息", "向指定联系人发送文本消息")
                        .stringParam("contact_id", "联系人ID", true, "003")
                        .stringParam("message", "消息内容", true, "test")
                        .build();
            }

            @Override
            public ToolResult execute(JSONObject args) {
                return ToolResult.success()
                        .with("contact_id", args.optString("contact_id", ""))
                        .with("message", args.optString("message", ""));
            }
        };
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
}
