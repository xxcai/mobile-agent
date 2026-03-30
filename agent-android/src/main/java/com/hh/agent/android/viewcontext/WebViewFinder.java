package com.hh.agent.android.viewcontext;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the primary visible WebView from the current foreground Activity.
 */
public class WebViewFinder {

    @Nullable
    public WebViewFindResult findPrimaryWebView(@Nullable Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return null;
        }
        View decorView = activity.getWindow().getDecorView();
        if (decorView == null) {
            return null;
        }

        List<WebViewCandidate> candidates = new ArrayList<>();
        collectCandidates(decorView, candidates);
        if (candidates.isEmpty()) {
            return null;
        }

        WebViewCandidate best = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            WebViewCandidate candidate = candidates.get(i);
            if (candidate.visibleArea > best.visibleArea) {
                best = candidate;
            }
        }
        return new WebViewFindResult(best.webView, candidates.size(), "largest_visible_area");
    }

    private void collectCandidates(@Nullable View view, List<WebViewCandidate> out) {
        if (view == null || !isVisibleCandidate(view)) {
            return;
        }
        if (view instanceof WebView) {
            out.add(new WebViewCandidate((WebView) view, computeVisibleArea(view)));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectCandidates(group.getChildAt(i), out);
            }
        }
    }

    private boolean isVisibleCandidate(View view) {
        return view.getVisibility() == View.VISIBLE
                && view.getAlpha() > 0f
                && view.isAttachedToWindow()
                && view.getWidth() > 0
                && view.getHeight() > 0;
    }

    private long computeVisibleArea(View view) {
        return (long) view.getWidth() * (long) view.getHeight();
    }

    private static final class WebViewCandidate {
        private final WebView webView;
        private final long visibleArea;

        private WebViewCandidate(WebView webView, long visibleArea) {
            this.webView = webView;
            this.visibleArea = visibleArea;
        }
    }

    public static final class WebViewFindResult {
        public final WebView webView;
        public final int candidateCount;
        public final String selectionReason;

        public WebViewFindResult(WebView webView, int candidateCount, String selectionReason) {
            this.webView = webView;
            this.candidateCount = candidateCount;
            this.selectionReason = selectionReason;
        }
    }
}
