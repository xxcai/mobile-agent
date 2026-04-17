package com.hh.agent.android.web;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class WebDomScriptFactoryTest {

    @Test
    public void buildSnapshotScript_containsTreeMetadataAndLimits() {
        String script = WebDomScriptFactory.buildSnapshotScript();

        assertTrue(script.contains("data-agent-ref"));
        assertTrue(script.contains("json_tree"));
        assertTrue(script.contains("truncated"));
        assertTrue(script.contains("nodeCount"));
        assertTrue(script.contains("maxDepthReached"));
        assertTrue(script.contains("24000"));
        assertTrue(script.contains("200"));
        assertTrue(script.contains("6"));
    }

    @Test
    public void buildClickScript_supportsRefAndSelector() {
        String script = WebDomScriptFactory.buildClickScript("node-1", "#submit");

        assertTrue(script.contains("data-agent-ref"));
        assertTrue(script.contains("querySelector"));
        assertTrue(script.contains("node-1"));
        assertTrue(script.contains("#submit"));
        assertTrue(script.contains("normalizedX"));
        assertTrue(script.contains("bounds"));
    }

    @Test
    public void buildInputScript_dispatchesInputAndChange() {
        String script = WebDomScriptFactory.buildInputScript("node-2", "#name", "hello");

        assertTrue(script.contains("data-agent-ref"));
        assertTrue(script.contains("dispatchEvent(new Event('input'"));
        assertTrue(script.contains("dispatchEvent(new Event('change'"));
        assertTrue(script.contains("hello"));
    }

    @Test
    public void buildScrollToBottomScript_targetsWindowAndDocument() {
        String script = WebDomScriptFactory.buildScrollToBottomScript();

        assertTrue(script.contains("window.scrollTo"));
        assertTrue(script.contains("document.documentElement"));
        assertTrue(script.contains("document.body"));
    }
}
