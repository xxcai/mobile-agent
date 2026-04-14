package com.hh.agent.benchmark;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.hh.agent.android.debug.BenchmarkTaskContext;
import com.hh.agent.android.debug.BenchmarkTaskContextHolder;
import com.hh.agent.android.floating.ContainerActivity;
import com.hh.agent.android.presenter.MainPresenter;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class BenchmarkTaskManager {

    static final String METHOD_RUN_TASK = "run_task";
    private static final String TASKS_DIR = ".icraw/tasks";
    private static final long SEND_DELAY_MS = 1000L;
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();

    private String currentRunId;
    private Runnable monitorRunnable;

    BenchmarkTaskManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    Bundle runTask(Bundle extras) {
        String runId = trimToEmpty(extras != null ? extras.getString("run_id") : null);
        String taskId = trimToEmpty(extras != null ? extras.getString("task_id") : null);
        String prompt = decodePrompt(extras != null ? extras.getString("prompt_base64") : null);

        if (runId.isEmpty() || prompt.isEmpty()) {
            return buildResponse(false, runId, STATUS_FAILED, "run_id and prompt are required");
        }

        synchronized (lock) {
            if (currentRunId != null && !currentRunId.isEmpty()) {
                JSONObject currentMeta = readMeta(currentRunId);
                String currentStatus = currentMeta.optString("status", STATUS_RUNNING);
                if (STATUS_COMPLETED.equals(currentStatus) || STATUS_FAILED.equals(currentStatus)) {
                    clearCurrentRun(currentRunId);
                }
            }
            if (currentRunId != null && !currentRunId.isEmpty()) {
                return buildResponse(false, runId, "busy", "another task is in progress");
            }
            currentRunId = runId;
        }

        try {
            createInitialMeta(runId, taskId, prompt);
            launchContainer();
            scheduleSend(runId, taskId, prompt);
            startCompletionMonitor(runId);
            return buildResponse(true, runId, STATUS_RUNNING, "");
        } catch (Exception exception) {
            updateMeta(runId, STATUS_FAILED, exception.getMessage());
            clearCurrentRun(runId);
            return buildResponse(false, runId, STATUS_FAILED, exception.getMessage());
        }
    }

    private void launchContainer() {
        mainHandler.post(() -> {
            Intent intent = new Intent(appContext, ContainerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(intent);
        });
    }

    private void scheduleSend(String runId, String taskId, String prompt) {
        mainHandler.postDelayed(() -> {
            try {
                BenchmarkTaskContextHolder.setCurrent(new BenchmarkTaskContext(runId, taskId));
                MainPresenter.getInstance(ContainerActivity.SESSION_KEY).sendMessage(prompt);
            } catch (Exception exception) {
                updateMeta(runId, STATUS_FAILED, exception.getMessage());
                clearCurrentRun(runId);
            } finally {
                BenchmarkTaskContextHolder.clearCurrent();
            }
        }, SEND_DELAY_MS);
    }

    private void startCompletionMonitor(String runId) {
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isCurrentRun(runId)) {
                    return;
                }
                JSONObject meta = readMeta(runId);
                String status = meta.optString("status", STATUS_RUNNING);
                if (STATUS_COMPLETED.equals(status) || STATUS_FAILED.equals(status)) {
                    clearCurrentRun(runId);
                    return;
                }
                mainHandler.postDelayed(this, 1000L);
            }
        };
        mainHandler.postDelayed(monitorRunnable, 1000L);
    }

    private boolean isCurrentRun(String runId) {
        synchronized (lock) {
            return runId.equals(currentRunId);
        }
    }

    private void clearCurrentRun(String runId) {
        synchronized (lock) {
            if (!runId.equals(currentRunId)) {
                return;
            }
            currentRunId = null;
        }
        if (monitorRunnable != null) {
            mainHandler.removeCallbacks(monitorRunnable);
            monitorRunnable = null;
        }
    }

    private void createInitialMeta(String runId, String taskId, String prompt) throws Exception {
        File taskDir = requireTaskDir(runId);
        JSONObject meta = new JSONObject()
                .put("runId", runId)
                .put("taskId", taskId)
                .put("sessionKey", ContainerActivity.SESSION_KEY)
                .put("userInput", prompt)
                .put("createdAt", nowIso())
                .put("endedAt", "")
                .put("status", STATUS_RUNNING)
                .put("errorMessage", "");
        writeWholeFile(new File(taskDir, "meta.json"), meta.toString(2));
        writeWholeFile(new File(taskDir, "events.jsonl"), "");
        writeWholeFile(new File(taskDir, "response.txt"), "");
    }

    private void updateMeta(String runId, String status, String errorMessage) {
        try {
            File metaFile = new File(requireTaskDir(runId), "meta.json");
            JSONObject meta = readJson(metaFile);
            meta.put("status", status);
            if (meta.optString("endedAt", "").isEmpty()) {
                meta.put("endedAt", nowIso());
            }
            meta.put("errorMessage", errorMessage == null ? "" : errorMessage);
            writeWholeFile(metaFile, meta.toString(2));
        } catch (Exception ignored) {
            // Best-effort benchmark metadata update.
        }
    }

    private JSONObject readMeta(String runId) {
        try {
            return readJson(new File(requireTaskDir(runId), "meta.json"));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private File requireTaskDir(String runId) {
        File filesDir = appContext.getExternalFilesDir(null);
        if (filesDir == null) {
            filesDir = appContext.getFilesDir();
        }
        if (filesDir == null) {
            throw new IllegalStateException("filesDir unavailable");
        }
        File rootDir = new File(filesDir, TASKS_DIR);
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            throw new IllegalStateException("failed to create tasks root");
        }
        File taskDir = new File(rootDir, runId);
        if (!taskDir.exists() && !taskDir.mkdirs()) {
            throw new IllegalStateException("failed to create task dir");
        }
        return taskDir;
    }

    private static JSONObject readJson(File file) throws Exception {
        if (!file.exists()) {
            return new JSONObject();
        }
        byte[] bytes = readWholeFile(file);
        String raw = new String(bytes, StandardCharsets.UTF_8);
        return raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
    }

    private static void writeWholeFile(File file, String content) throws Exception {
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static byte[] readWholeFile(File file) throws Exception {
        try (java.io.FileInputStream inputStream = new java.io.FileInputStream(file)) {
            return inputStream.readAllBytes();
        }
    }

    private static Bundle buildResponse(boolean accepted, String runId, String status, String message) {
        Bundle bundle = new Bundle();
        bundle.putBoolean("accepted", accepted);
        bundle.putString("run_id", runId == null ? "" : runId);
        bundle.putString("status", status == null ? "" : status);
        bundle.putString("message", message == null ? "" : message);
        return bundle;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String decodePrompt(String promptBase64) {
        String normalized = trimToEmpty(promptBase64);
        if (normalized.isEmpty()) {
            return "";
        }
        try {
            byte[] decoded = android.util.Base64.decode(normalized, android.util.Base64.DEFAULT);
            return new String(decoded, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String nowIso() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date());
    }
}
