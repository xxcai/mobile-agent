package com.hh.agent.android.viewcontext;

import android.app.Activity;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.hh.agent.android.floating.ContainerActivity;
import com.hh.agent.android.floating.FloatingBallLifecycleCallbacks;
import com.hh.agent.android.log.AgentLogs;

import java.lang.ref.WeakReference;

/**
 * Collects a compact XML-like snapshot from the current in-process foreground activity.
 */
public final class InProcessViewHierarchyDumper {
    private static final String TAG = "InProcessViewHierarchyDumper";

    private static final long CONTAINER_DISMISS_TIMEOUT_MS = 900L;
    private static final long FOREGROUND_STABLE_TIMEOUT_MS = 900L;
    private static final int MAX_NODES = 300;

    private InProcessViewHierarchyDumper() {
    }

    public static DumpResult dumpCurrentHierarchy(@Nullable String targetHint) {
        Activity activity = getCurrentStableForegroundActivity();
        if (activity == null) {
            return DumpResult.error("Foreground activity did not stabilize in time");
        }
        return dumpHierarchy(activity, targetHint);
    }

    static DumpResult dumpHierarchy(@Nullable Activity activity, @Nullable String targetHint) {
        if (activity == null) {
            return DumpResult.error("Foreground activity did not stabilize in time");
        }

        final Activity stableActivity = activity;
        final WeakReference<Activity> activityRef = new WeakReference<>(stableActivity);
        final Holder holder = new Holder();
        stableActivity.runOnUiThread(() -> {
            Activity currentActivity = activityRef.get();
            if (currentActivity == null || currentActivity.isFinishing() || currentActivity.isDestroyed()) {
                holder.error = "Foreground activity was destroyed before hierarchy collection";
                synchronized (holder) {
                    holder.done = true;
                    holder.notifyAll();
                }
                return;
            }

            View decorView = currentActivity.getWindow() != null
                    ? currentActivity.getWindow().getDecorView()
                    : null;
            if (decorView == null) {
                holder.error = "Foreground activity has no decor view";
                synchronized (holder) {
                    holder.done = true;
                    holder.notifyAll();
                }
                return;
            }

            StringBuilder xml = new StringBuilder();
            DumpState state = new DumpState();
            xml.append("<hierarchy activity=\"")
                    .append(escape(currentActivity.getClass().getName()))
                    .append("\"");
            if (!TextUtils.isEmpty(targetHint)) {
                xml.append(" target-hint=\"").append(escape(targetHint)).append("\"");
            }
            xml.append(">");
            appendNode(xml, decorView, state);
            xml.append("</hierarchy>");

            AgentLogs.info(
                    TAG,
                    "dump_complete",
                    "activity=" + currentActivity.getClass().getName()
                            + " node_count=" + state.nodeCount
                            + " max_nodes=" + MAX_NODES
            );

            holder.xml = xml.toString();
            holder.activityClassName = currentActivity.getClass().getName();
            holder.dismissedContainer = currentActivity != activityRef.get();
            synchronized (holder) {
                holder.done = true;
                holder.notifyAll();
            }
        });

        synchronized (holder) {
            long deadline = SystemClock.uptimeMillis() + FOREGROUND_STABLE_TIMEOUT_MS;
            while (!holder.done && SystemClock.uptimeMillis() < deadline) {
                try {
                    holder.wait(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return DumpResult.error("Interrupted while collecting view hierarchy");
                }
            }
        }

        if (!holder.done) {
            return DumpResult.error("Timed out while collecting view hierarchy");
        }
        if (holder.error != null) {
            return DumpResult.error(holder.error);
        }
        return DumpResult.success(holder.xml, holder.activityClassName);
    }

    @Nullable
    public static Activity getCurrentStableForegroundActivity() {
        Activity activity = FloatingBallLifecycleCallbacks.getCurrentForegroundActivity();
        if (activity == null) {
            return null;
        }

        if (activity instanceof ContainerActivity) {
            dismissContainerActivity(activity);
            return FloatingBallLifecycleCallbacks.awaitNextStableForegroundActivity(
                    true,
                    CONTAINER_DISMISS_TIMEOUT_MS
            );
        }

        return FloatingBallLifecycleCallbacks.awaitNextStableForegroundActivity(
                false,
                FOREGROUND_STABLE_TIMEOUT_MS
        );
    }

    private static void dismissContainerActivity(Activity activity) {
        activity.runOnUiThread(() -> {
            if (activity instanceof ContainerActivity) {
                ((ContainerActivity) activity).finishImmediately();
            } else {
                activity.finish();
            }
        });
    }

    private static void appendNode(StringBuilder xml, View view, DumpState state) {
        if (view == null) {
            return;
        }
        if (state.nodeCount >= MAX_NODES) {
            return;
        }
        if (!isMeaningful(view)) {
            return;
        }

        int nodeIndex = state.nodeCount++;
        xml.append(buildNodeOpenTag(
                nodeIndex,
                view.getClass().getName(),
                resolveResourceId(view),
                extractText(view),
                extractBounds(view)
        ));

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (state.nodeCount >= MAX_NODES) {
                    break;
                }
                appendNode(xml, group.getChildAt(i), state);
            }
        }

        xml.append("</node>");
    }

    private static boolean isMeaningful(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return false;
        }
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        if (view.getAlpha() <= 0f) {
            return false;
        }
        ViewParent parent = view.getParent();
        return parent != null || view.getRootView() == view;
    }

    private static String extractText(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            if (!TextUtils.isEmpty(text)) {
                return text.toString();
            }
            CharSequence hint = textView.getHint();
            if (!TextUtils.isEmpty(hint)) {
                return hint.toString();
            }
        }
        CharSequence contentDescription = view.getContentDescription();
        if (!TextUtils.isEmpty(contentDescription)) {
            return contentDescription.toString();
        }
        return "";
    }

    private static void appendOptionalAttribute(StringBuilder xml, String name, @Nullable String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        xml.append(" ").append(name).append("=\"").append(escape(value)).append("\"");
    }

    static String buildNodeOpenTag(
            int nodeIndex,
            String className,
            @Nullable String resourceId,
            @Nullable String text,
            String bounds
    ) {
        StringBuilder xml = new StringBuilder();
        xml.append("<node index=\"").append(nodeIndex).append("\"");
        xml.append(" class=\"").append(escape(className)).append("\"");
        appendOptionalAttribute(xml, "resource-id", resourceId);
        appendOptionalAttribute(xml, "text", text);
        xml.append(" bounds=\"").append(escape(bounds)).append("\"");
        xml.append(">");
        return xml.toString();
    }

    private static String resolveResourceId(View view) {
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

    private static String extractBounds(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int left = location[0];
        int top = location[1];
        int right = left + view.getWidth();
        int bottom = top + view.getHeight();
        return "[" + left + "," + top + "][" + right + "," + bottom + "]";
    }

    private static String escape(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static final class DumpResult {
        public final boolean success;
        public final String xml;
        public final String activityClassName;
        public final String errorMessage;

        private DumpResult(boolean success, String xml, String activityClassName, String errorMessage) {
            this.success = success;
            this.xml = xml;
            this.activityClassName = activityClassName;
            this.errorMessage = errorMessage;
        }

        public static DumpResult success(String xml, String activityClassName) {
            return new DumpResult(true, xml, activityClassName, null);
        }

        public static DumpResult error(String errorMessage) {
            return new DumpResult(false, null, null, errorMessage);
        }
    }

    private static final class DumpState {
        int nodeCount;
    }

    private static final class Holder {
        boolean done;
        String xml;
        String activityClassName;
        String error;
        boolean dismissedContainer;
    }
}



