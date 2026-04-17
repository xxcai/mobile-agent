package com.hh.agent.android.viewcontext;

import android.app.Activity;

import androidx.annotation.Nullable;

import com.hh.agent.core.tool.ToolResult;

/**
 * Provides screenshot-backed view context when a visual analyzer has been injected by the app.
 */
public final class ScreenSnapshotObservationProvider {

    private static final String SOURCE_SCREEN_SNAPSHOT = "screen_snapshot";

    private final StableForegroundActivityProvider stableForegroundActivityProvider;
    @Nullable
    private final ScreenSnapshotAnalyzer analyzer;

    public ScreenSnapshotObservationProvider(StableForegroundActivityProvider stableForegroundActivityProvider,
                                             @Nullable ScreenSnapshotAnalyzer analyzer) {
        if (stableForegroundActivityProvider == null) {
            throw new IllegalArgumentException("stableForegroundActivityProvider cannot be null");
        }
        this.stableForegroundActivityProvider = stableForegroundActivityProvider;
        this.analyzer = analyzer;
    }

    public ToolResult getCurrentSnapshot(@Nullable String targetHint) {
        return getCurrentSnapshot(targetHint, false, ObservationDetailMode.DISCOVERY);
    }

    public ToolResult getCurrentSnapshot(@Nullable String targetHint, boolean includeRawFallback) {
        return getCurrentSnapshot(targetHint, includeRawFallback, ObservationDetailMode.DISCOVERY);
    }

    public ToolResult getCurrentSnapshot(@Nullable String targetHint,
                                         boolean includeRawFallback,
                                         ObservationDetailMode detailMode) {
        if (analyzer == null) {
            return ToolResult.error(
                    "view_context_unavailable",
                    "No screen snapshot analyzer has been registered"
            );
        }

        Activity activity = stableForegroundActivityProvider.getStableForegroundActivity();
        if (activity == null) {
            return ToolResult.error(
                    "view_context_unavailable",
                    "No stable foreground activity available for screen snapshot"
            );
        }

        try {
            ScreenSnapshotAnalysis analysis = analyzer.analyze(activity, targetHint);
            String activityClassName = normalizeActivityClassName(analysis.activityClassName, activity);
            String nativeViewXml = tryCollectNativeViewXml(activity, targetHint);
            return ViewContextSnapshotProvider.buildObservationToolResult(
                    SOURCE_SCREEN_SNAPSHOT,
                    targetHint,
                    activityClassName,
                    analysis.observationMode,
                    nativeViewXml,
                    analysis,
                    includeRawFallback,
                    detailMode
            );
        } catch (Exception e) {
            return ToolResult.error("view_context_unavailable", safeMessage(e));
        }
    }

    public static ScreenSnapshotObservationProvider createDefault() {
        return new ScreenSnapshotObservationProvider(
                new StableForegroundActivityProvider() {
                    @Override
                    public Activity getStableForegroundActivity() {
                        return InProcessViewHierarchyDumper.getCurrentStableForegroundActivity();
                    }
                },
                ScreenSnapshotAnalyzerHolder.getInstance().getAnalyzer()
        );
    }

    @Nullable
    private static String tryCollectNativeViewXml(Activity activity, @Nullable String targetHint) {
        try {
            InProcessViewHierarchyDumper.DumpResult dumpResult =
                    InProcessViewHierarchyDumper.dumpHierarchy(activity, targetHint);
            return dumpResult.success ? dumpResult.xml : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeActivityClassName(@Nullable String activityClassName, Activity activity) {
        if (activityClassName == null || activityClassName.trim().isEmpty()) {
            return activity.getClass().getName();
        }
        return activityClassName;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
