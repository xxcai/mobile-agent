package com.hh.agent.android;

import com.hh.agent.android.channel.DescribeShortcutChannel;
import com.hh.agent.android.channel.GestureToolChannel;
import com.hh.agent.android.channel.ShortcutRuntimeChannel;
import com.hh.agent.android.channel.ViewContextToolChannel;
import com.hh.agent.android.log.AgentLogger;
import com.hh.agent.android.log.AgentLogs;
import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.json.JSONObject;

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
        AndroidToolManager manager = new AndroidToolManager(null);

        Map<String, ?> channels = manager.getRegisteredChannels();

        assertTrue(channels.containsKey(ShortcutRuntimeChannel.CHANNEL_NAME));
        assertTrue(channels.containsKey(DescribeShortcutChannel.CHANNEL_NAME));
        assertTrue(channels.containsKey(GestureToolChannel.CHANNEL_NAME));
        assertTrue(channels.containsKey(ViewContextToolChannel.CHANNEL_NAME));
        assertFalse(channels.containsKey("call_android_tool"));
    }

    @Test
    public void registerShortcutsExposesInjectedShortcutThroughRunShortcut() throws Exception {
        AndroidToolManager manager = new AndroidToolManager(null);
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
