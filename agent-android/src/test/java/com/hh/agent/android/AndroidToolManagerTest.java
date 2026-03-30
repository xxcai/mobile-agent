package com.hh.agent.android;

import com.hh.agent.android.channel.GestureToolChannel;
import com.hh.agent.android.channel.ShortcutRuntimeChannel;
import com.hh.agent.android.channel.ViewContextToolChannel;
import com.hh.agent.android.log.AgentLogger;
import com.hh.agent.android.log.AgentLogs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

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
        assertTrue(channels.containsKey(GestureToolChannel.CHANNEL_NAME));
        assertTrue(channels.containsKey(ViewContextToolChannel.CHANNEL_NAME));
        assertFalse(channels.containsKey("call_android_tool"));
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
