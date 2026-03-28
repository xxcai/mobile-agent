package com.hh.agent.android.viewcontext;

import android.app.Activity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RuntimeViewContextSourceResolverTest {

    @Test
    public void resolve_prefersPolicySelectionOverFallback() {
        RuntimeViewContextSourceResolver resolver = new RuntimeViewContextSourceResolver(
                new ViewContextSourceSelector(new ActivityViewContextSourcePolicy() {
                    @Override
                    public java.util.Map<String, String> getActivitySourceMap() {
                        java.util.HashMap<String, String> map = new java.util.HashMap<>();
                        map.put(TestActivity.class.getName(), "web_dom");
                        return map;
                    }
                }),
                new WebViewAreaFallbackSourceResolver() {
                    @Override
                    public ViewContextSourceSelection resolve(Activity activity) {
                        return ViewContextSourceSelection.fallbackResolved("native_xml");
                    }
                },
                new StableForegroundActivityProvider() {
                    @Override
                    public Activity getStableForegroundActivity() {
                        return new TestActivity();
                    }
                }
        );

        ViewContextSourceSelection selection = resolver.resolve();

        assertEquals(ViewContextSourceSelection.Status.POLICY_MATCHED, selection.getStatus());
        assertEquals("web_dom", selection.getSource());
    }

    @Test
    public void resolve_usesFallbackWhenPolicyMisses() {
        RuntimeViewContextSourceResolver resolver = new RuntimeViewContextSourceResolver(
                new ViewContextSourceSelector(ActivityViewContextSourcePolicy.EMPTY),
                new WebViewAreaFallbackSourceResolver() {
                    @Override
                    public ViewContextSourceSelection resolve(Activity activity) {
                        return ViewContextSourceSelection.fallbackResolved("native_xml");
                    }
                },
                new StableForegroundActivityProvider() {
                    @Override
                    public Activity getStableForegroundActivity() {
                        return new TestActivity();
                    }
                }
        );

        ViewContextSourceSelection selection = resolver.resolve();

        assertEquals(ViewContextSourceSelection.Status.FALLBACK_RESOLVED, selection.getStatus());
        assertEquals("native_xml", selection.getSource());
    }

    public static class TestActivity extends Activity {
    }
}
