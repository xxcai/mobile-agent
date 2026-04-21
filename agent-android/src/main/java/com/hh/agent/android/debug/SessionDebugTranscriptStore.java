package com.hh.agent.android.debug;

import android.content.Context;

import com.hh.agent.android.log.AgentLogs;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Writes one debug transcript directory per streamed response.
 */
public final class SessionDebugTranscriptStore {

    private static final String TAG = "SessionDebugTranscript";
    private static final String TASKS_DIR = ".icraw/tasks";
    private static final String EVENT_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final String META_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    private static final SessionTranscript NO_OP_TRANSCRIPT = new NoOpSessionTranscript();

    private static SessionDebugTranscriptStore instance;

    private final Context appContext;

    private SessionDebugTranscriptStore(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized void initialize(Context context) {
        if (context == null) {
            return;
        }
        instance = new SessionDebugTranscriptStore(context);
    }

    public static synchronized SessionDebugTranscriptStore getInstance() {
        return instance;
    }

    public SessionTranscript startTurn(String sessionKey, String userInput) {
        BenchmarkTaskContext taskContext = BenchmarkTaskContextHolder.consumeCurrent();
        File rootDir = getRootDirectory();
        if (rootDir == null) {
            return NO_OP_TRANSCRIPT;
        }
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            AgentLogs.warn(TAG, "debug_dir_create_failed", "path=" + rootDir.getAbsolutePath());
            return NO_OP_TRANSCRIPT;
        }

        String dirName = taskContext != null && !taskContext.getRunId().isEmpty()
                ? TaskRunIds.sanitizeRunId(taskContext.getRunId())
                : TaskRunIds.createSafeRunId(userInput);
        File turnDir = new File(rootDir, dirName);
        if (!turnDir.exists() && !turnDir.mkdirs()) {
            AgentLogs.warn(TAG, "turn_dir_create_failed", "path=" + turnDir.getAbsolutePath());
            return NO_OP_TRANSCRIPT;
        }

        File metaFile = new File(turnDir, "meta.json");
        File eventsFile = new File(turnDir, "events.jsonl");
        File responseFile = new File(turnDir, "response.txt");

        try {
            JSONObject meta = mergeMeta(readWholeJson(metaFile), sessionKey, userInput, taskContext);
            writeWholeFile(metaFile, meta.toString(2));
            writeWholeFile(responseFile, "");
        } catch (Exception e) {
            AgentLogs.warn(TAG, "meta_write_failed", "message=" + e.getMessage());
            return NO_OP_TRANSCRIPT;
        }

        AgentLogs.info(TAG, "turn_transcript_created", "path=" + turnDir.getAbsolutePath());
        return new FileSessionTranscript(turnDir, metaFile, eventsFile, responseFile);
    }

    private File getRootDirectory() {
        File filesDir = appContext.getExternalFilesDir(null);
        if (filesDir == null) {
            filesDir = appContext.getFilesDir();
        }
        if (filesDir == null) {
            return null;
        }
        return new File(filesDir, TASKS_DIR);
    }

    private static void writeWholeFile(File file, String content) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static JSONObject readWholeJson(File file) {
        if (file == null || !file.exists()) {
            return new JSONObject();
        }
        try {
            byte[] bytes;
            try (java.io.FileInputStream inputStream = new java.io.FileInputStream(file)) {
                bytes = inputStream.readAllBytes();
            }
            String raw = new String(bytes, StandardCharsets.UTF_8);
            return raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static JSONObject mergeMeta(JSONObject existing,
                                        String sessionKey,
                                        String userInput,
                                        BenchmarkTaskContext taskContext) throws Exception {
        JSONObject meta = existing == null ? new JSONObject() : existing;
        if (!meta.has("runId")) {
            meta.put("runId", taskContext != null ? taskContext.getRunId() : "");
        }
        if (!meta.has("taskId")) {
            meta.put("taskId", taskContext != null ? taskContext.getTaskId() : "");
        }
        meta.put("displayName", TaskRunIds.buildDisplayName(userInput));
        meta.put("sessionKey", sessionKey == null ? "" : sessionKey);
        meta.put("userInput", userInput == null ? "" : userInput);
        if (meta.optString("createdAt", "").isEmpty()) {
            meta.put("createdAt", nowIso());
        }
        if (!meta.has("endedAt")) {
            meta.put("endedAt", "");
        }
        if (meta.optString("status", "").isEmpty()) {
            meta.put("status", "running");
        }
        if (!meta.has("errorMessage")) {
            meta.put("errorMessage", "");
        }
        return meta;
    }

    private static String nowIso() {
        return new SimpleDateFormat(META_TIME_PATTERN, Locale.US).format(new Date());
    }

    public interface SessionTranscript {
        void onTextDelta(String text);
        void onReasoningDelta(String text);
        void onToolUse(String id, String name, String argumentsJson);
        void onToolResult(String id, String result);
        void onMessageEnd(String finishReason);
        void onError(String errorCode, String errorMessage);
    }

    private static final class FileSessionTranscript implements SessionTranscript {
        private final File turnDir;
        private final File metaFile;
        private final File eventsFile;
        private final File responseFile;
        private final StringBuilder responseBuilder = new StringBuilder();
        private final SimpleDateFormat eventTimeFormat =
                new SimpleDateFormat(EVENT_TIME_PATTERN, Locale.US);

        private FileSessionTranscript(File turnDir, File metaFile, File eventsFile, File responseFile) {
            this.turnDir = turnDir;
            this.metaFile = metaFile;
            this.eventsFile = eventsFile;
            this.responseFile = responseFile;
        }

        @Override
        public synchronized void onTextDelta(String text) {
            responseBuilder.append(text == null ? "" : text);
            appendEvent("text_delta", buildPayload(
                    "length", text == null ? 0 : text.length(),
                    "text", text == null ? "" : text));
            flushResponse();
        }

        @Override
        public synchronized void onReasoningDelta(String text) {
            appendEvent("reasoning_delta", buildPayload(
                    "length", text == null ? 0 : text.length(),
                    "text", text == null ? "" : text));
        }

        @Override
        public synchronized void onToolUse(String id, String name, String argumentsJson) {
            appendEvent("tool_use", buildPayload(
                    "id", nullToEmpty(id),
                    "name", nullToEmpty(name),
                    "arguments", nullToEmpty(argumentsJson)));
        }

        @Override
        public synchronized void onToolResult(String id, String result) {
            appendEvent("tool_result", buildPayload(
                    "id", nullToEmpty(id),
                    "result", nullToEmpty(result),
                    "length", result == null ? 0 : result.length()));
        }

        @Override
        public synchronized void onMessageEnd(String finishReason) {
            appendEvent("message_end", buildPayload(
                    "finishReason", nullToEmpty(finishReason)));
            flushResponse();
            if (!"tool_calls".equals(finishReason)) {
                markFinished("stop".equals(finishReason) ? "completed" : "failed",
                        "stop".equals(finishReason) ? "" : nullToEmpty(finishReason));
            }
        }

        @Override
        public synchronized void onError(String errorCode, String errorMessage) {
            appendEvent("error", buildPayload(
                    "errorCode", nullToEmpty(errorCode),
                    "errorMessage", nullToEmpty(errorMessage)));
            flushResponse();
            markFinished("failed", nullToEmpty(errorMessage));
        }

        private void appendEvent(String type, JSONObject payload) {
            try (FileWriter writer = new FileWriter(eventsFile, true)) {
                String eventTime = eventTimeFormat.format(new Date());
                JSONObject line = new JSONObject()
                        .put("time", eventTime)
                        .put("type", type)
                        .put("payload", payload);
                writer.write(line.toString());
                writer.write('\n');
            } catch (Exception e) {
                AgentLogs.warn(TAG, "event_append_failed",
                        "path=" + turnDir.getAbsolutePath() + " message=" + e.getMessage());
            }
        }

        private void flushResponse() {
            try {
                writeWholeFile(responseFile, responseBuilder.toString());
            } catch (IOException e) {
                AgentLogs.warn(TAG, "response_write_failed",
                        "path=" + turnDir.getAbsolutePath() + " message=" + e.getMessage());
            }
        }

        private void markFinished(String status, String errorMessage) {
            try {
                JSONObject meta = readWholeJson(metaFile);
                if (!"running".equals(meta.optString("status", "running"))) {
                    return;
                }
                meta.put("status", status);
                meta.put("endedAt", nowIso());
                meta.put("errorMessage", errorMessage == null ? "" : errorMessage);
                writeWholeFile(metaFile, meta.toString(2));
            } catch (Exception e) {
                AgentLogs.warn(TAG, "meta_finalize_failed",
                        "path=" + turnDir.getAbsolutePath() + " message=" + e.getMessage());
            }
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }

        private static JSONObject buildPayload(Object... pairs) {
            JSONObject payload = new JSONObject();
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                try {
                    payload.put(String.valueOf(pairs[i]), pairs[i + 1]);
                } catch (Exception ignored) {
                    // Payload is best-effort debug output; skip invalid fields.
                }
            }
            return payload;
        }
    }

    private static final class NoOpSessionTranscript implements SessionTranscript {
        @Override
        public void onTextDelta(String text) {
        }

        @Override
        public void onReasoningDelta(String text) {
        }

        @Override
        public void onToolUse(String id, String name, String argumentsJson) {
        }

        @Override
        public void onToolResult(String id, String result) {
        }

        @Override
        public void onMessageEnd(String finishReason) {
        }

        @Override
        public void onError(String errorCode, String errorMessage) {
        }
    }
}
