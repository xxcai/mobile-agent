package com.hh.agent.h5bench;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MiniWoBTaskRegistry {
    private final Context context;

    public MiniWoBTaskRegistry(Context context) {
        this.context = context;
    }

    public List<MiniWoBTaskDefinition> loadSuite(String assetPath) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(context.getAssets().open(assetPath))) {
            Gson gson = new Gson();
            SuiteManifest manifest = gson.fromJson(reader, SuiteManifest.class);
            validateManifest(manifest, assetPath);
            List<MiniWoBTaskDefinition> tasks = new ArrayList<>();
            for (MiniWoBTaskDefinition task : manifest.tasks) {
                tasks.add(new MiniWoBTaskDefinition(
                        task.getTaskId(),
                        normalizeAssetPath(manifest.assetBasePath, task.getAssetPath()),
                        task.getInstruction(),
                        task.getCategory(),
                        task.getSeed(),
                        task.getMaxSteps(),
                        task.getTimeoutMs()));
            }
            return tasks;
        }
    }

    static String normalizeAssetPath(String assetBasePath, String assetPath) {
        String normalizedBasePath = trimLeadingSlash(requireNonBlank(assetBasePath, "Manifest missing required 'assetBasePath' field"));
        String normalizedAssetPath = trimLeadingSlash(requireNonBlank(assetPath, "Task missing required 'assetPath' field"));
        if (normalizedAssetPath.startsWith(normalizedBasePath + "/")) {
            return normalizedAssetPath;
        }
        return normalizedBasePath + "/" + normalizedAssetPath;
    }

    private static void validateManifest(SuiteManifest manifest, String assetPath) {
        if (manifest == null) {
            throw new IllegalArgumentException("Failed to parse suite manifest: " + assetPath);
        }
        requireNonBlank(manifest.assetBasePath, "Manifest missing required 'assetBasePath' field: " + assetPath);
        if (manifest.tasks == null) {
            throw new IllegalArgumentException("Manifest missing required 'tasks' field: " + assetPath);
        }
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String trimLeadingSlash(String value) {
        if (value.startsWith("/")) {
            return value.substring(1);
        }
        return value;
    }

    private static final class SuiteManifest {
        @SerializedName("assetBasePath")
        private String assetBasePath;

        @SerializedName("tasks")
        private List<MiniWoBTaskDefinition> tasks;
    }
}
