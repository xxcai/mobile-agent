package com.hh.agent.mockim.debug;

import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hh.agent.android.AndroidToolManager;
import com.hh.agent.core.tool.ToolExecutor;
import com.hh.agent.tool.DisplayNotificationTool;
import com.hh.agent.tool.ReadClipboardTool;
import com.hh.agent.tool.SearchContactsTool;
import com.hh.agent.tool.SendImMessageTool;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared debug probe entry for MainActivity mock chat pages.
 * It keeps verification on the real chat UI instead of routing back to ToolChannelTestActivity.
 */
public final class MockChatProbeRunner {

    private static final Pattern NODE_MATCH_PATTERN = Pattern.compile(
            "<node[^>]*index=\\\"([^\\\"]+)\\\"[^>]*text=\\\"([^\\\"]*)\\\"[^>]*bounds=\\\"([^\\\"]+)\\\"[^>]*/?>");

    private MockChatProbeRunner() {
    }

    public static void runObservationBoundGestureProbe(AppCompatActivity activity,
                                                       String reportTitle,
                                                       String targetHint,
                                                       int fallbackX,
                                                       int fallbackY) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        showReportDialog(activity, reportTitle,
                "# " + reportTitle + "\n"
                        + "step_1=android_view_context_tool(native_xml)\n"
                        + "step_2=android_gesture_tool(tap + observation)\n"
                        + "target_hint=" + targetHint + "\n"
                        + "note=waiting for runtime result...");

        Thread worker = new Thread(() -> {
            String report;
            try {
                AndroidToolManager manager = buildToolManager(activity);
                String viewContextResult = manager.callTool(
                        "android_view_context_tool",
                        "{\"source\":\"native_xml\",\"targetHint\":\"" + escape(targetHint) + "\"}");
                JSONObject viewContextJson = new JSONObject(viewContextResult);
                if (!viewContextJson.optBoolean("success", false)) {
                    report = buildFailureReport(reportTitle, targetHint, viewContextResult);
                } else {
                    String snapshotId = viewContextJson.optString("snapshotId", "");
                    String nativeViewXml = viewContextJson.optString("nativeViewXml", "");
                    NodeReference nodeReference = findNodeReference(nativeViewXml, targetHint);
                    String gestureArgs = buildGestureArgs(
                            snapshotId,
                            targetHint,
                            fallbackX,
                            fallbackY,
                            nodeReference);
                    String gestureResult = manager.callTool("android_gesture_tool", gestureArgs);
                    report = buildSuccessReport(
                            reportTitle,
                            targetHint,
                            viewContextResult,
                            gestureArgs,
                            gestureResult,
                            nodeReference);
                }
            } catch (Exception e) {
                report = "# " + reportTitle + "\n"
                        + "target_hint=" + targetHint + "\n"
                        + "error=probe_failed\n"
                        + "message=" + e.getMessage();
            }

            String finalReport = report;
            mainHandler.post(() -> showReportDialog(activity, reportTitle, finalReport));
        });
        worker.start();
    }

    private static AndroidToolManager buildToolManager(AppCompatActivity activity) {
        AndroidToolManager manager = new AndroidToolManager(activity);
        Map<String, ToolExecutor> tools = new HashMap<>();
        tools.put("display_notification", new DisplayNotificationTool(activity));
        tools.put("read_clipboard", new ReadClipboardTool(activity));
        tools.put("search_contacts", new SearchContactsTool());
        tools.put("send_im_message", new SendImMessageTool());
        manager.registerTools(tools);
        manager.initialize();
        return manager;
    }

    private static String buildFailureReport(String reportTitle,
                                             String targetHint,
                                             String viewContextResult) {
        return "# " + reportTitle + "\n"
                + "target_hint=" + targetHint + "\n\n"
                + "view_context_result=" + viewContextResult;
    }

    private static String buildSuccessReport(String reportTitle,
                                             String targetHint,
                                             String viewContextResult,
                                             String gestureArgs,
                                             String gestureResult,
                                             NodeReference nodeReference) throws Exception {
        JSONObject viewContextJson = new JSONObject(viewContextResult);
        JSONObject gestureJson = new JSONObject(gestureResult);
        JSONObject paramsJson = gestureJson.optJSONObject("params");
        JSONObject observationJson = paramsJson != null
                ? paramsJson.optJSONObject("observation")
                : null;

        String snapshotId = viewContextJson.optString("snapshotId", "<none>");
        String referencedSnapshotId = observationJson != null
                ? observationJson.optString("snapshotId", "<none>")
                : "<none>";
        String nativeViewXml = viewContextJson.optString("nativeViewXml", "");

        StringBuilder report = new StringBuilder();
        report.append("# ").append(reportTitle).append('\n');
        report.append("target_hint=").append(targetHint).append("\n\n");
        report.append("activityClassName=")
                .append(viewContextJson.optString("activityClassName", "<none>"))
                .append('\n');
        report.append("observationMode=")
                .append(viewContextJson.optString("observationMode", "<none>"))
                .append('\n');
        report.append("view_context_mock=")
                .append(viewContextJson.optBoolean("mock", true))
                .append('\n');
        report.append("nativeViewXml_contains_target=")
                .append(nativeViewXml.contains(targetHint))
                .append('\n');
        report.append("matched_target_node_index=")
                .append(nodeReference != null ? nodeReference.nodeIndex : "<none>")
                .append('\n');
        report.append("matched_target_bounds=")
                .append(nodeReference != null ? nodeReference.bounds : "<none>")
                .append("\n\n");
        report.append("view_context_result=").append(viewContextResult).append("\n\n");
        report.append("snapshotId=").append(snapshotId).append('\n');
        report.append("gesture_input=").append(gestureArgs).append('\n');
        report.append("gesture_result=").append(gestureResult).append("\n\n");
        report.append("gesture_observation_snapshot_id=").append(referencedSnapshotId).append('\n');
        report.append("snapshot_match=")
                .append(snapshotId.equals(referencedSnapshotId) ? "PASS" : "FAIL")
                .append('\n');
        if (observationJson != null) {
            report.append("gesture_target_node_index=")
                    .append(observationJson.opt("targetNodeIndex"))
                    .append('\n');
            report.append("gesture_referenced_bounds=")
                    .append(observationJson.optString("referencedBounds", "<none>"))
                    .append('\n');
        }
        return report.toString();
    }

    private static String buildGestureArgs(String snapshotId,
                                           String targetHint,
                                           int fallbackX,
                                           int fallbackY,
                                           NodeReference nodeReference) {
        StringBuilder gestureArgs = new StringBuilder();
        gestureArgs.append("{\"action\":\"tap\",\"x\":").append(fallbackX)
                .append(",\"y\":").append(fallbackY)
                .append(",\"observation\":{\"snapshotId\":\"").append(escape(snapshotId)).append("\"")
                .append(",\"targetDescriptor\":\"").append(escape(targetHint)).append("\"");
        if (nodeReference != null) {
            gestureArgs.append(",\"targetNodeIndex\":").append(nodeReference.nodeIndex);
            gestureArgs.append(",\"referencedBounds\":\"").append(escape(nodeReference.bounds)).append("\"");
        }
        gestureArgs.append("}}");
        return gestureArgs.toString();
    }

    private static NodeReference findNodeReference(String nativeViewXml, String targetHint) {
        if (nativeViewXml == null || nativeViewXml.isEmpty()
                || targetHint == null || targetHint.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = NODE_MATCH_PATTERN.matcher(nativeViewXml);
        while (matcher.find()) {
            String nodeIndex = matcher.group(1);
            String nodeText = matcher.group(2);
            String bounds = matcher.group(3);
            if (targetHint.equals(nodeText)) {
                try {
                    return new NodeReference(Integer.parseInt(nodeIndex), bounds);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static void showReportDialog(AppCompatActivity activity, String title, String report) {
        TextView contentView = new TextView(activity);
        int padding = dp(activity, 16);
        contentView.setPadding(padding, padding, padding, padding);
        contentView.setTextIsSelectable(true);
        contentView.setMovementMethod(new ScrollingMovementMethod());
        contentView.setText(report);
        contentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(contentView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .show();
    }

    private static int dp(AppCompatActivity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static final class NodeReference {
        private final int nodeIndex;
        private final String bounds;

        private NodeReference(int nodeIndex, String bounds) {
            this.nodeIndex = nodeIndex;
            this.bounds = bounds;
        }
    }
}
