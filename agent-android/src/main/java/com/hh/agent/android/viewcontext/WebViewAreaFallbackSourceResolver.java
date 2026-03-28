package com.hh.agent.android.viewcontext;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.Nullable;

/**
 * Resolves fallback source from the current page's visible WebView area ratio.
 */
public final class WebViewAreaFallbackSourceResolver {

    static final double WEB_DOM_RATIO_THRESHOLD = 0.5d;
    static final String SOURCE_NATIVE_XML = "native_xml";
    static final String SOURCE_WEB_DOM = "web_dom";

    public ViewContextSourceSelection resolve(@Nullable Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return ViewContextSourceSelection.fallbackResolved(SOURCE_NATIVE_XML);
        }
        View decorView = activity.getWindow().getDecorView();
        if (decorView == null) {
            return ViewContextSourceSelection.fallbackResolved(SOURCE_NATIVE_XML);
        }
        long rootArea = Math.max(0L, computeViewArea(decorView));
        long visibleWebViewArea = clampVisibleWebViewArea(
                accumulateVisibleWebViewArea(decorView),
                rootArea
        );
        return ViewContextSourceSelection.fallbackResolved(
                resolveSourceFromAreas(rootArea, visibleWebViewArea)
        );
    }

    static String resolveSourceFromAreas(long rootArea, long visibleWebViewArea) {
        if (rootArea <= 0L) {
            return SOURCE_NATIVE_XML;
        }
        double ratio = (double) clampVisibleWebViewArea(visibleWebViewArea, rootArea) / (double) rootArea;
        return ratio > WEB_DOM_RATIO_THRESHOLD ? SOURCE_WEB_DOM : SOURCE_NATIVE_XML;
    }

    static long clampVisibleWebViewArea(long visibleWebViewArea, long rootArea) {
        if (rootArea <= 0L || visibleWebViewArea <= 0L) {
            return 0L;
        }
        return Math.min(visibleWebViewArea, rootArea);
    }

    private long accumulateVisibleWebViewArea(@Nullable View view) {
        if (view == null || !isVisibleForAreaCount(view)) {
            return 0L;
        }

        long area = 0L;
        if (view instanceof WebView) {
            // TODO: This fallback currently sums visible WebView bounds area and caps it by root area.
            // It does not account for overlap between multiple WebViews. If this causes misrouting,
            // upgrade this logic to compute rectangle union area instead of simple accumulation.
            area += computeViewArea(view);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                area += accumulateVisibleWebViewArea(group.getChildAt(i));
            }
        }
        return area;
    }

    private boolean isVisibleForAreaCount(View view) {
        return view.getVisibility() == View.VISIBLE
                && view.getAlpha() > 0f
                && view.getWidth() > 0
                && view.getHeight() > 0;
    }

    private long computeViewArea(View view) {
        return (long) view.getWidth() * (long) view.getHeight();
    }
}
