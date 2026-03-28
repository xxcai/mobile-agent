package com.hh.agent.android.viewcontext;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WebViewAreaFallbackSourceResolverTest {

    @Test
    public void resolveSourceFromAreas_returnsNativeWhenNoWebViewArea() {
        assertEquals("native_xml",
                WebViewAreaFallbackSourceResolver.resolveSourceFromAreas(100L, 0L));
    }

    @Test
    public void resolveSourceFromAreas_returnsNativeAtThreshold() {
        assertEquals("native_xml",
                WebViewAreaFallbackSourceResolver.resolveSourceFromAreas(100L, 50L));
    }

    @Test
    public void resolveSourceFromAreas_returnsWebDomAboveThreshold() {
        assertEquals("web_dom",
                WebViewAreaFallbackSourceResolver.resolveSourceFromAreas(100L, 51L));
    }

    @Test
    public void clampVisibleWebViewArea_capsAreaToRootArea() {
        assertEquals(100L,
                WebViewAreaFallbackSourceResolver.clampVisibleWebViewArea(150L, 100L));
    }

    @Test
    public void clampVisibleWebViewArea_returnsZeroWhenRootAreaMissing() {
        assertEquals(0L,
                WebViewAreaFallbackSourceResolver.clampVisibleWebViewArea(50L, 0L));
    }
}
