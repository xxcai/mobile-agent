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
    private static final String DEBUG_DIR = ".icraw/debug-transcripts";
    private static final int INPUT_LABEL_MAX = 40;
    private static final String EVENT_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
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
        File rootDir = getRootDirectory();
        if (rootDir == null) {
            return NO_OP_TRANSCRIPT;
        }
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            AgentLogs.warn(TAG, "debug_dir_create_failed", "path=" + rootDir.getAbsolutePath());
            return NO_OP_TRANSCRIPT;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        String dirName = timestamp + "__" + sanitizeInputLabel(userInput);
        File turnDir = new File(rootDir, dirName);
        if (!turnDir.mkdirs()) {
            AgentLogs.warn(TAG, "turn_dir_create_failed", "path=" + turnDir.getAbsolutePath());
            return NO_OP_TRANSCRIPT;
        }

        File metaFile = new File(turnDir, "meta.json");
        File eventsFile = new File(turnDir, "events.jsonl");
        File responseFile = new File(turnDir, "response.txt");

        try {
            JSONObject meta = new JSONObject()
                    .put("createdAt", timestamp)
                    .put("sessionKey", sessionKey == null ? "" : sessionKey)
                    .put("userInput", userInput == null ? "" : userInput)
                    .put("directory", turnDir.getAbsolutePath());
            writeWholeFile(metaFile, meta.toString(2));
            writeWholeFile(responseFile, "");
        } catch (Exception e) {
            AgentLogs.warn(TAG, "meta_write_failed", "message=" + e.getMessage());
            return NO_OP_TRANSCRIPT;
        }

        AgentLogs.info(TAG, "turn_transcript_created", "path=" + turnDir.getAbsolutePath());
        return new FileSessionTranscript(turnDir, eventsFile, responseFile);
    }

    private File getRootDirectory() {
        File filesDir = appContext.getExternalFilesDir(null);
        if (filesDir == null) {
            filesDir = appContext.getFilesDir();
        }
        if (filesDir == null) {
            return null;
        }
        return new File(filesDir, DEBUG_DIR);
    }

    private static String sanitizeInputLabel(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return "empty-input";
        }
        String normalized = userInput.trim().replaceAll("\\s+", "_");
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}_-]", "_");
        if (normalized.length() > INPUT_LABEL_MAX) {
            normalized = normalized.substring(0, INPUT_LABEL_MAX);
        }
        while (normalized.contains("__")) {
            normalized = normalized.replace("__", "_");
        }
        return normalized.isEmpty() ? "input" : normalized;
    }

    private static void writeWholeFile(File file, String content) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    public interface SessionTranscript {
        void onTextDelta(String text);
        void onToolUse(String id, String name, String argumentsJson);
        void onToolResult(String id, String result);
        void onMessageEnd(String finishReason);
        void onError(String errorCode, String errorMessage);
    }

    private static final class FileSessionTranscript implements SessionTranscript {
        private final File turnDir;
        private final File eventsFile;
        private final File responseFile;
        private final StringBuilder responseBuilder = new StringBuilder();
        private final SimpleDateFormat eventTimeFormat =
                new SimpleDateFormat(EVENT_TIME_PATTERN, Locale.US);

        private FileSessionTranscript(File turnDir, File eventsFile, File responseFile) {
            this.turnDir = turnDir;
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
        }

        @Override
        public synchronized void onError(String errorCode, String errorMessage) {
            appendEvent("error", buildPayload(
                    "errorCode", nullToEmpty(errorCode),
                    "errorMessage", nullToEmpty(errorMessage)));
            flushResponse();
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
