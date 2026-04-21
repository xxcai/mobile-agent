package com.hh.agent.android.gesture;

import android.app.Activity;
import android.graphics.Rect;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.TextView;
import android.widget.ScrollView;

import androidx.recyclerview.widget.RecyclerView;

import com.hh.agent.android.floating.ContainerActivity;
import com.hh.agent.android.floating.FloatingBallLifecycleCallbacks;
import com.hh.agent.android.viewcontext.ViewObservationSnapshot;
import com.hh.agent.android.viewcontext.ViewObservationSnapshotRegistry;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real in-process gesture executor for current-host mock pages.
 * Supports tap and standard container scroll only.
 */
public class InProcessGestureExecutor implements AndroidGestureExecutor {

    private static final long UI_WAIT_TIMEOUT_MS = 1500L;
    private static final long TAP_UP_DELAY_MS = 24L;
    private static final int SWIPE_MOVE_STEP_COUNT = 10;
    private static final int MIN_SWIPE_GESTURE_DURATION_MS = 240;
    private static final int MAX_SWIPE_GESTURE_DURATION_MS = 720;
    private static final Pattern BOUNDS_PATTERN =
            Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

    @Override
    public GestureExecutionResult tap(JSONObject params) {
        try {
            Activity activity = resolveStableActivity();
            if (activity == null) {
                return GestureExecutionResult.error("tap",
                        "foreground_activity_unavailable",
                        "No stable foreground activity available for in-process tap");
            }

            ViewObservationSnapshot snapshot = validateSnapshot(params.optJSONObject("observation"));
            if (snapshot == null) {
                return GestureExecutionResult.error("tap",
                        "stale_view_context_snapshot",
                        "Tap requires the latest current-turn observation snapshot");
            }
            if (!activity.getClass().getName().equals(snapshot.activityClassName)) {
                return GestureExecutionResult.error("tap",
                        "foreground_activity_changed",
                        "Foreground activity changed since the observation snapshot was created");
            }

            JSONObject payload = new JSONObject(params.toString());
            Point targetPoint = resolveTapPoint(payload);
            ExecutionResult result = runOnUiThread(activity, () -> performTap(activity, targetPoint));
            if (!result.success) {
                return GestureExecutionResult.error("tap", result.error, result.message);
            }

            payload.put("resolvedTapX", targetPoint.x);
            payload.put("resolvedTapY", targetPoint.y);
            payload.put("tapPointSource", targetPoint.source);
            payload.put("injectedTapX", result.injectedStartX);
            payload.put("injectedTapY", result.injectedStartY);
            payload.put("executionMode", result.executionMode);
            payload.put("resolvedViewClass", result.viewClassName);
            payload.put("resolvedViewId", result.viewId);
            payload.put("resolvedViewText", result.viewText);
            payload.put("resolvedViewSemanticContext", result.viewSemanticContext);
            payload.put("dispatchPath", result.dispatchPath);
            payload.put("dispatchEventCount", result.dispatchEventCount);
            payload.put("dispatchHandledCount", result.dispatchHandledCount);
            payload.put("dispatchAnyHandled", result.dispatchAnyHandled);
            payload.put("dispatchAllHandled", result.dispatchAllHandled);
            payload.put("dispatchTrace", result.dispatchTrace);

            JSONObject observation = payload.optJSONObject("observation");
            String targetDescriptor = observation != null
                    ? observation.optString("targetDescriptor", "").trim()
                    : "";
            if (shouldRejectFallbackTap(targetPoint.source, targetDescriptor, result.viewSemanticContext)) {
                return GestureExecutionResult.error("tap",
                        "tap_target_mismatch",
                        "Tap landed on a view that does not match the intended target descriptor");
            }
            return GestureExecutionResult.success("tap", false, payload);
        } catch (Exception e) {
            return GestureExecutionResult.error("tap", "execution_failed", e.getMessage());
        }
    }

    @Override
    public GestureExecutionResult swipe(JSONObject params) {
        try {
            Activity activity = resolveStableActivity();
            if (activity == null) {
                return GestureExecutionResult.error("swipe",
                        "foreground_activity_unavailable",
                        "No stable foreground activity available for in-process scroll");
            }

            JSONObject payload = new JSONObject(params.toString());
            String direction = payload.optString("direction", "").trim();
            String amount = payload.optString("amount", "medium").trim();
            int durationMs = payload.optInt("duration", 400);
            JSONObject observation = payload.optJSONObject("observation");
            Rect referencedBounds = parseBounds(observation != null
                    ? observation.optString("referencedBounds", "")
                    : "");
            if (referencedBounds == null) {
                return GestureExecutionResult.error("swipe",
                        "missing_scroll_container_bounds",
                        "Swipe requires observation.referencedBounds for the target scroll container");
            }

            ExecutionResult result = runOnUiThread(activity,
                    () -> performScroll(activity, referencedBounds, direction, amount, durationMs));
            if (!result.success) {
                return GestureExecutionResult.error("swipe", result.error, result.message);
            }

            if (durationMs > 0) {
                SystemClock.sleep(Math.min(durationMs, 500));
            }

            payload.put("executionMode", result.executionMode);
            payload.put("resolvedContainerClass", result.viewClassName);
            payload.put("resolvedContainerId", result.viewId);
            payload.put("resolvedStartX", result.resolvedStartX);
            payload.put("resolvedStartY", result.resolvedStartY);
            payload.put("resolvedEndX", result.resolvedEndX);
            payload.put("resolvedEndY", result.resolvedEndY);
            payload.put("injectedStartX", result.injectedStartX);
            payload.put("injectedStartY", result.injectedStartY);
            payload.put("injectedEndX", result.injectedEndX);
            payload.put("injectedEndY", result.injectedEndY);
            payload.put("gestureDurationMs", result.gestureDurationMs);
            payload.put("scrollAmountPx", result.scrollAmountPx);
            payload.put("scrollOffsetBefore", result.scrollOffsetBefore);
            payload.put("scrollOffsetAfter", result.scrollOffsetAfter);
            payload.put("scrollOffsetDelta", result.scrollOffsetDelta);
            payload.put("dispatchPath", result.dispatchPath);
            payload.put("dispatchEventCount", result.dispatchEventCount);
            payload.put("dispatchHandledCount", result.dispatchHandledCount);
            payload.put("dispatchAnyHandled", result.dispatchAnyHandled);
            payload.put("dispatchAllHandled", result.dispatchAllHandled);
            payload.put("dispatchTrace", result.dispatchTrace);
            return GestureExecutionResult.success("swipe", false, payload);
        } catch (Exception e) {
            return GestureExecutionResult.error("swipe", "execution_failed", e.getMessage());
        }
    }

    private Activity resolveStableActivity() {
        Activity current = FloatingBallLifecycleCallbacks.getCurrentForegroundActivity();
        if (current instanceof ContainerActivity) {
            return FloatingBallLifecycleCallbacks.awaitNextStableForegroundActivity(true, UI_WAIT_TIMEOUT_MS);
        }
        Activity stable = FloatingBallLifecycleCallbacks.awaitNextStableForegroundActivity(false, UI_WAIT_TIMEOUT_MS);
        return stable != null ? stable : current;
    }

    private ViewObservationSnapshot validateSnapshot(JSONObject observation) {
        if (observation == null) {
            return null;
        }
        String snapshotId = observation.optString("snapshotId", "").trim();
        if (snapshotId.isEmpty()) {
            return null;
        }
        ViewObservationSnapshot latest = ViewObservationSnapshotRegistry.getLatestSnapshot();
        if (latest == null) {
            return null;
        }
        return snapshotId.equals(latest.snapshotId) ? latest : null;
    }

    private Point resolveTapPoint(JSONObject payload) {
        JSONObject observation = payload.optJSONObject("observation");
        String referencedBounds = observation != null
                ? observation.optString("referencedBounds", "")
                : "";
        Rect rect = parseBounds(referencedBounds);
        if (rect != null) {
            return new Point(rect.centerX(), rect.centerY(), "observation_bounds");
        }
        return new Point(payload.optInt("x"), payload.optInt("y"), "fallback_coordinates");
    }

    private ExecutionResult performTap(Activity activity, Point point) {
        View root = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (root == null) {
            return ExecutionResult.error("tap_target_not_found", "Foreground activity has no decor view");
        }

        List<View> views = new ArrayList<>();
        collectViews(root, views);
        View target = findBestViewAtPoint(views, point.x, point.y);
        if (target == null) {
            return ExecutionResult.error("tap_target_not_found",
                    "No visible view matched the requested tap point");
        }

        WindowPoint injectedPoint = toWindowPoint(activity, point.x, point.y);
        DispatchSummary dispatch = dispatchTapSequence(activity, injectedPoint.x, injectedPoint.y);
        if (!dispatch.anyHandled) {
            return ExecutionResult.error("tap_dispatch_not_handled",
                    "Tap event sequence was not handled by the current activity");
        }

        return ExecutionResult.successTap("activity_dispatch_touch_event", target, dispatch, injectedPoint);
    }

    private boolean shouldRejectFallbackTap(String tapPointSource,
                                            String targetDescriptor,
                                            String resolvedViewSemanticContext) {
        if (!"fallback_coordinates".equals(tapPointSource)) {
            return false;
        }
        if (targetDescriptor == null || targetDescriptor.trim().isEmpty()) {
            return false;
        }
        return !semanticTargetMatches(targetDescriptor, resolvedViewSemanticContext);
    }

    private boolean semanticTargetMatches(String targetDescriptor, String resolvedViewSemanticContext) {
        List<String> targetTokens = extractSemanticTokens(targetDescriptor);
        if (targetTokens.isEmpty()) {
            return true;
        }
        String normalizedContext = normalizeSemanticText(resolvedViewSemanticContext);
        if (normalizedContext.isEmpty()) {
            return false;
        }

        int matched = 0;
        int strongMatched = 0;
        for (String token : targetTokens) {
            if (!normalizedContext.contains(token)) {
                continue;
            }
            matched++;
            if (token.length() >= 3 || containsNonAscii(token)) {
                strongMatched++;
            }
        }
        if (strongMatched > 0) {
            return true;
        }
        return matched >= Math.min(2, targetTokens.size());
    }

    private List<String> extractSemanticTokens(String text) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        String normalized = normalizeSemanticText(text);
        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = normalized.split("\\s+");
        for (String part : parts) {
            if (part.length() < 2 && !containsNonAscii(part)) {
                continue;
            }
            tokens.add(part);
        }
        return new ArrayList<>(tokens);
    }

    private String normalizeSemanticText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch > 127) {
                normalized.append(ch);
            } else {
                normalized.append(' ');
            }
        }
        return normalized.toString().replaceAll("\\s+", " ").trim();
    }

    private boolean containsNonAscii(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) {
                return true;
            }
        }
        return false;
    }

    private ExecutionResult performScroll(Activity activity,
                                          Rect referencedBounds,
                                          String direction,
                                          String amount,
                                          int requestedDurationMs) {
        View root = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (root == null) {
            return ExecutionResult.error("scroll_target_not_found", "Foreground activity has no decor view");
        }

        List<View> views = new ArrayList<>();
        collectViews(root, views);
        View container = findScrollableContainer(views, referencedBounds);
        if (container == null) {
            return ExecutionResult.error("scroll_target_not_found",
                    "No supported scroll container matched the requested observation bounds");
        }

        ScrollPlan plan = buildScrollPlan(container, direction, amount);
        if (plan == null) {
            return ExecutionResult.error("invalid_scroll_intent",
                    "Unsupported swipe direction or amount");
        }

        WindowPoint injectedStart = toWindowPoint(activity, plan.startX, plan.startY);
        WindowPoint injectedEnd = toWindowPoint(activity, plan.endX, plan.endY);
        int gestureDurationMs = clamp(requestedDurationMs,
                MIN_SWIPE_GESTURE_DURATION_MS,
                MAX_SWIPE_GESTURE_DURATION_MS);
        int scrollOffsetBefore = computeScrollOffset(container);
        DispatchSummary dispatch = dispatchSwipeSequence(activity, injectedStart, injectedEnd, gestureDurationMs);
        int scrollOffsetAfter = computeScrollOffset(container);
        int scrollOffsetDelta = scrollOffsetAfter - scrollOffsetBefore;
        if (!dispatch.anyHandled && scrollOffsetDelta == 0) {
            return ExecutionResult.error("scroll_dispatch_not_handled",
                    "Swipe event sequence was not handled by the current activity");
        }

        return ExecutionResult.successScroll("activity_dispatch_touch_event", container, plan, dispatch,
                injectedStart, injectedEnd, gestureDurationMs,
                scrollOffsetBefore, scrollOffsetAfter, scrollOffsetDelta);
    }

    private void collectViews(View view, List<View> out) {
        if (view == null || view.getVisibility() != View.VISIBLE
                || !view.isAttachedToWindow() || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return;
        }
        out.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectViews(group.getChildAt(i), out);
            }
        }
    }

    private View findBestViewAtPoint(List<View> views, int x, int y) {
        View best = null;
        int bestArea = Integer.MAX_VALUE;
        Rect rect = new Rect();
        for (View view : views) {
            view.getGlobalVisibleRect(rect);
            if (!rect.contains(x, y)) {
                continue;
            }
            int area = Math.max(1, rect.width() * rect.height());
            if (area < bestArea) {
                best = view;
                bestArea = area;
            }
        }
        return best;
    }

    private View findScrollableContainer(List<View> views, Rect referencedBounds) {
        View best = null;
        float bestScore = -1f;
        Rect rect = new Rect();
        for (View view : views) {
            if (!(view instanceof AbsListView)
                    && !(view instanceof ScrollView)
                    && !(view instanceof RecyclerView)) {
                continue;
            }
            view.getGlobalVisibleRect(rect);
            if (rect.height() <= 0 || rect.width() <= 0) {
                continue;
            }
            float score = computeContainerMatchScore(rect, referencedBounds);
            if (score > bestScore) {
                best = view;
                bestScore = score;
            }
        }
        if (best != null && bestScore > 0f) {
            return best;
        }
        return null;
    }

    private ScrollPlan buildScrollPlan(View container, String direction, String amount) {
        Rect rect = new Rect();
        container.getGlobalVisibleRect(rect);
        if (rect.height() <= 0 || rect.width() <= 0) {
            return null;
        }

        float amountRatio;
        switch (amount) {
            case "small":
                amountRatio = 0.25f;
                break;
            case "medium":
                amountRatio = 0.40f;
                break;
            case "large":
                amountRatio = 0.60f;
                break;
            case "one_screen":
                amountRatio = 0.75f;
                break;
            default:
                return null;
        }

        int centerX = rect.centerX();
        int height = rect.height();
        int margin = Math.max(24, Math.round(height * 0.14f));
        int upperY = rect.top + margin;
        int lowerY = rect.bottom - margin;
        if (lowerY <= upperY) {
            return null;
        }

        int scrollAmountPx = Math.max(1, Math.round(height * amountRatio));
        if ("down".equals(direction)) {
            int startY = lowerY;
            int endY = Math.max(upperY, startY - scrollAmountPx);
            return new ScrollPlan(centerX, startY, centerX, endY, startY - endY);
        }
        if ("up".equals(direction)) {
            int startY = upperY;
            int endY = Math.min(lowerY, startY + scrollAmountPx);
            return new ScrollPlan(centerX, startY, centerX, endY, startY - endY);
        }
        return null;
    }

    private float computeContainerMatchScore(Rect candidate, Rect referencedBounds) {
        Rect intersection = new Rect(candidate);
        if (!intersection.intersect(referencedBounds)) {
            return 0f;
        }
        float intersectionArea = Math.max(1, intersection.width() * intersection.height());
        float candidateArea = Math.max(1, candidate.width() * candidate.height());
        float boundsArea = Math.max(1, referencedBounds.width() * referencedBounds.height());
        float unionArea = candidateArea + boundsArea - intersectionArea;
        float iou = intersectionArea / Math.max(1f, unionArea);
        boolean containsCenter = candidate.contains(referencedBounds.centerX(), referencedBounds.centerY());
        return containsCenter ? iou + 1f : iou;
    }

    private Rect parseBounds(String referencedBounds) {
        if (referencedBounds == null || referencedBounds.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = BOUNDS_PATTERN.matcher(referencedBounds.trim());
        if (!matcher.matches()) {
            return null;
        }
        int left = Integer.parseInt(matcher.group(1));
        int top = Integer.parseInt(matcher.group(2));
        int right = Integer.parseInt(matcher.group(3));
        int bottom = Integer.parseInt(matcher.group(4));
        return new Rect(left, top, right, bottom);
    }

    private ExecutionResult runOnUiThread(Activity activity, UiAction action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ExecutionResult[] holder = new ExecutionResult[1];
        Exception[] errorHolder = new Exception[1];

        activity.runOnUiThread(() -> {
            try {
                holder[0] = action.run();
            } catch (Exception e) {
                errorHolder[0] = e;
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(UI_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            return ExecutionResult.error("ui_thread_timeout", "Timed out waiting for UI execution");
        }
        if (errorHolder[0] != null) {
            throw errorHolder[0];
        }
        return holder[0] != null
                ? holder[0]
                : ExecutionResult.error("execution_failed", "UI execution returned no result");
    }

    private interface UiAction {
        ExecutionResult run() throws Exception;
    }

    private DispatchSummary dispatchTapSequence(Activity activity, int x, int y) {
        long downTime = SystemClock.uptimeMillis();
        List<DispatchEvent> events = new ArrayList<>();
        events.add(new DispatchEvent(MotionEvent.ACTION_DOWN, downTime, x, y));
        events.add(new DispatchEvent(MotionEvent.ACTION_UP, downTime + TAP_UP_DELAY_MS, x, y));
        return dispatchEvents(activity, events, TAP_UP_DELAY_MS);
    }

    private DispatchSummary dispatchSwipeSequence(Activity activity,
                                                  WindowPoint start,
                                                  WindowPoint end,
                                                  int gestureDurationMs) {
        long downTime = SystemClock.uptimeMillis();
        List<DispatchEvent> events = new ArrayList<>();
        events.add(new DispatchEvent(MotionEvent.ACTION_DOWN, downTime, start.x, start.y));
        int totalSteps = SWIPE_MOVE_STEP_COUNT + 1;
        long stepDelayMs = Math.max(16L, Math.round(gestureDurationMs / (float) totalSteps));
        for (int stepIndex = 1; stepIndex < totalSteps; stepIndex++) {
            float fraction = stepIndex / (float) totalSteps;
            int moveX = Math.round(start.x + ((end.x - start.x) * fraction));
            int moveY = Math.round(start.y + ((end.y - start.y) * fraction));
            long eventTime = downTime + (stepIndex * stepDelayMs);
            events.add(new DispatchEvent(MotionEvent.ACTION_MOVE, eventTime, moveX, moveY));
        }
        long upTime = downTime + (totalSteps * stepDelayMs);
        events.add(new DispatchEvent(MotionEvent.ACTION_UP, upTime, end.x, end.y));
        return dispatchEvents(activity, events, stepDelayMs);
    }

    private DispatchSummary dispatchEvents(Activity activity, List<DispatchEvent> events, long interEventDelayMs) {
        List<String> trace = new ArrayList<>();
        int handledCount = 0;
        boolean allHandled = true;
        for (DispatchEvent eventDef : events) {
            MotionEvent event = MotionEvent.obtain(
                    events.get(0).eventTime,
                    eventDef.eventTime,
                    eventDef.action,
                    eventDef.x,
                    eventDef.y,
                    0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            boolean handled;
            try {
                handled = activity.dispatchTouchEvent(event);
            } finally {
                event.recycle();
            }
            if (handled) {
                handledCount++;
            } else {
                allHandled = false;
            }
            trace.add(actionToString(eventDef.action)
                    + "@(" + eventDef.x + "," + eventDef.y + ")"
                    + ":handled=" + handled);
            if (eventDef.action != MotionEvent.ACTION_UP) {
                SystemClock.sleep(interEventDelayMs);
            }
        }
        return new DispatchSummary(
                "activity_dispatch_touch_event",
                events.size(),
                handledCount,
                handledCount > 0,
                allHandled,
                String.join(" | ", trace));
    }

    private WindowPoint toWindowPoint(Activity activity, int screenX, int screenY) {
        View decorView = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decorView == null) {
            return new WindowPoint(screenX, screenY);
        }
        int[] decorOnScreen = new int[2];
        decorView.getLocationOnScreen(decorOnScreen);
        return new WindowPoint(screenX - decorOnScreen[0], screenY - decorOnScreen[1]);
    }

    private int computeScrollOffset(View container) {
        if (container instanceof ScrollView) {
            return ((ScrollView) container).getScrollY();
        }
        if (container instanceof RecyclerView) {
            return ((RecyclerView) container).computeVerticalScrollOffset();
        }
        if (container instanceof AbsListView) {
            AbsListView listView = (AbsListView) container;
            View firstChild = listView.getChildAt(0);
            if (firstChild == null) {
                return 0;
            }
            int firstChildHeight = Math.max(1, firstChild.getHeight());
            return (listView.getFirstVisiblePosition() * firstChildHeight) - firstChild.getTop();
        }
        return 0;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String actionToString(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return "DOWN";
            case MotionEvent.ACTION_MOVE:
                return "MOVE";
            case MotionEvent.ACTION_UP:
                return "UP";
            default:
                return String.valueOf(action);
        }
    }

    private static final class Point {
        private final int x;
        private final int y;
        private final String source;

        private Point(int x, int y, String source) {
            this.x = x;
            this.y = y;
            this.source = source;
        }
    }

    private static final class WindowPoint {
        private final int x;
        private final int y;

        private WindowPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class DispatchEvent {
        private final int action;
        private final long eventTime;
        private final int x;
        private final int y;

        private DispatchEvent(int action, long eventTime, int x, int y) {
            this.action = action;
            this.eventTime = eventTime;
            this.x = x;
            this.y = y;
        }
    }

    private static final class DispatchSummary {
        private final String dispatchPath;
        private final int eventCount;
        private final int handledCount;
        private final boolean anyHandled;
        private final boolean allHandled;
        private final String trace;

        private DispatchSummary(String dispatchPath,
                                int eventCount,
                                int handledCount,
                                boolean anyHandled,
                                boolean allHandled,
                                String trace) {
            this.dispatchPath = dispatchPath;
            this.eventCount = eventCount;
            this.handledCount = handledCount;
            this.anyHandled = anyHandled;
            this.allHandled = allHandled;
            this.trace = trace;
        }
    }

    private static final class ExecutionResult {
        private final boolean success;
        private final String error;
        private final String message;
        private final String executionMode;
        private final String viewClassName;
        private final String viewId;
        private final String viewText;
        private final String viewSemanticContext;
        private final int resolvedStartX;
        private final int resolvedStartY;
        private final int resolvedEndX;
        private final int resolvedEndY;
        private final int injectedStartX;
        private final int injectedStartY;
        private final int injectedEndX;
        private final int injectedEndY;
        private final int gestureDurationMs;
        private final int scrollAmountPx;
        private final int scrollOffsetBefore;
        private final int scrollOffsetAfter;
        private final int scrollOffsetDelta;
        private final String dispatchPath;
        private final int dispatchEventCount;
        private final int dispatchHandledCount;
        private final boolean dispatchAnyHandled;
        private final boolean dispatchAllHandled;
        private final String dispatchTrace;

        private ExecutionResult(boolean success,
                                String error,
                                String message,
                                String executionMode,
                                String viewClassName,
                                String viewId,
                                String viewText,
                                String viewSemanticContext,
                                int resolvedStartX,
                                int resolvedStartY,
                                int resolvedEndX,
                                int resolvedEndY,
                                int injectedStartX,
                                int injectedStartY,
                                int injectedEndX,
                                int injectedEndY,
                                int gestureDurationMs,
                                int scrollAmountPx,
                                int scrollOffsetBefore,
                                int scrollOffsetAfter,
                                int scrollOffsetDelta,
                                String dispatchPath,
                                int dispatchEventCount,
                                int dispatchHandledCount,
                                boolean dispatchAnyHandled,
                                boolean dispatchAllHandled,
                                String dispatchTrace) {
            this.success = success;
            this.error = error;
            this.message = message;
            this.executionMode = executionMode;
            this.viewClassName = viewClassName;
            this.viewId = viewId;
            this.viewText = viewText;
            this.viewSemanticContext = viewSemanticContext;
            this.resolvedStartX = resolvedStartX;
            this.resolvedStartY = resolvedStartY;
            this.resolvedEndX = resolvedEndX;
            this.resolvedEndY = resolvedEndY;
            this.injectedStartX = injectedStartX;
            this.injectedStartY = injectedStartY;
            this.injectedEndX = injectedEndX;
            this.injectedEndY = injectedEndY;
            this.gestureDurationMs = gestureDurationMs;
            this.scrollAmountPx = scrollAmountPx;
            this.scrollOffsetBefore = scrollOffsetBefore;
            this.scrollOffsetAfter = scrollOffsetAfter;
            this.scrollOffsetDelta = scrollOffsetDelta;
            this.dispatchPath = dispatchPath;
            this.dispatchEventCount = dispatchEventCount;
            this.dispatchHandledCount = dispatchHandledCount;
            this.dispatchAnyHandled = dispatchAnyHandled;
            this.dispatchAllHandled = dispatchAllHandled;
            this.dispatchTrace = dispatchTrace;
        }

        private static ExecutionResult successTap(String executionMode,
                                                  View view,
                                                  DispatchSummary dispatch,
                                                  WindowPoint injectedPoint) {
            return new ExecutionResult(
                    true,
                    null,
                    null,
                    executionMode,
                    view.getClass().getName(),
                    resolveViewId(view),
                    resolveViewText(view),
                    resolveViewSemanticContext(view),
                    0,
                    0,
                    0,
                    0,
                    injectedPoint.x,
                    injectedPoint.y,
                    injectedPoint.x,
                    injectedPoint.y,
                    0,
                    0,
                    0,
                    0,
                    0,
                    dispatch.dispatchPath,
                    dispatch.eventCount,
                    dispatch.handledCount,
                    dispatch.anyHandled,
                    dispatch.allHandled,
                    dispatch.trace
            );
        }

        private static ExecutionResult successScroll(String executionMode,
                                                     View view,
                                                     ScrollPlan plan,
                                                     DispatchSummary dispatch,
                                                     WindowPoint injectedStart,
                                                     WindowPoint injectedEnd,
                                                     int gestureDurationMs,
                                                     int scrollOffsetBefore,
                                                     int scrollOffsetAfter,
                                                     int scrollOffsetDelta) {
            return new ExecutionResult(
                    true,
                    null,
                    null,
                    executionMode,
                    view.getClass().getName(),
                    resolveViewId(view),
                    resolveViewText(view),
                    resolveViewSemanticContext(view),
                    plan.startX,
                    plan.startY,
                    plan.endX,
                    plan.endY,
                    injectedStart.x,
                    injectedStart.y,
                    injectedEnd.x,
                    injectedEnd.y,
                    gestureDurationMs,
                    plan.scrollAmountPx,
                    scrollOffsetBefore,
                    scrollOffsetAfter,
                    scrollOffsetDelta,
                    dispatch.dispatchPath,
                    dispatch.eventCount,
                    dispatch.handledCount,
                    dispatch.anyHandled,
                    dispatch.allHandled,
                    dispatch.trace
            );
        }

        private static ExecutionResult error(String error, String message) {
            return new ExecutionResult(false, error, message, null, null, null, null, null,
                    0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0,
                    null, 0, 0, false, false, null);
        }

        private static String resolveViewId(View view) {
            int id = view.getId();
            if (id == View.NO_ID) {
                return "";
            }
            try {
                return view.getResources().getResourceName(id);
            } catch (Exception ignored) {
                return String.valueOf(id);
            }
        }

        private static String resolveViewText(View view) {
            if (view instanceof TextView) {
                CharSequence text = ((TextView) view).getText();
                return text != null ? text.toString() : "";
            }
            CharSequence contentDescription = view.getContentDescription();
            return contentDescription != null ? contentDescription.toString() : "";
        }

        private static String resolveViewSemanticContext(View view) {
            List<String> parts = new ArrayList<>();
            View current = view;
            int depth = 0;
            while (current != null && depth < 4) {
                parts.add(current.getClass().getName());
                String viewId = resolveViewId(current);
                if (!viewId.isEmpty()) {
                    parts.add(viewId);
                }
                String viewText = resolveViewText(current);
                if (!viewText.isEmpty()) {
                    parts.add(viewText);
                }
                ViewParent parent = current.getParent();
                current = parent instanceof View ? (View) parent : null;
                depth++;
            }
            return String.join(" ", parts);
        }
    }

    private static final class ScrollPlan {
        private final int startX;
        private final int startY;
        private final int endX;
        private final int endY;
        private final int scrollAmountPx;

        private ScrollPlan(int startX, int startY, int endX, int endY, int scrollAmountPx) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.scrollAmountPx = scrollAmountPx;
        }
    }
}




