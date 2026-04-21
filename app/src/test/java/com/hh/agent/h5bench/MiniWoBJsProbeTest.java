package com.hh.agent.h5bench;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MiniWoBJsProbeTest {
    @Test
    public void startEpisodeScriptUsesMiniWoBRuntimeEntryPoint() {
        String script = MiniWoBJsProbe.buildStartEpisodeScript(101);

        assertTrue(script.contains("Math.seedrandom('101')"));
        assertTrue(script.contains("core.startEpisodeReal()"));
    }

    @Test
    public void readStatusScriptReadsMiniWoBGlobals() {
        String script = MiniWoBJsProbe.buildReadStatusScript();

        assertTrue(script.contains("WOB_DONE_GLOBAL"));
        assertTrue(script.contains("WOB_REWARD_GLOBAL"));
        assertTrue(script.contains("WOB_RAW_REWARD_GLOBAL"));
        assertTrue(script.contains("WOB_EPISODE_ID"));
    }
}
