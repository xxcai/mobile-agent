package com.hh.agent.h5bench;

import android.content.Context;

import com.google.gson.Gson;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class H5BenchmarkManifestRepository {
    public static final String BASELINE_20_ASSET_PATH = "workspace/skills/h5_benchmark_runner/baseline-20.json";

    private final Context context;
    private final Gson gson = new Gson();

    public H5BenchmarkManifestRepository(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        this.context = context.getApplicationContext();
    }

    public H5BenchmarkManifest loadBaseline20() throws Exception {
        return load(BASELINE_20_ASSET_PATH);
    }

    public H5BenchmarkManifest load(String assetPath) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                context.getAssets().open(assetPath), StandardCharsets.UTF_8)) {
            H5BenchmarkManifest manifest = gson.fromJson(reader, H5BenchmarkManifest.class);
            if (manifest == null) {
                throw new IllegalArgumentException("Failed to parse benchmark manifest: " + assetPath);
            }
            String assetBasePath = requireNonBlank(
                    manifest.getAssetBasePath(),
                    "Benchmark manifest missing required 'assetBasePath': " + assetPath);
            List<MiniWoBTaskDefinition> normalizedTasks = new ArrayList<>();
            for (MiniWoBTaskDefinition task : manifest.getTasks()) {
                normalizedTasks.add(new MiniWoBTaskDefinition(
                        task.getTaskId(),
                        MiniWoBTaskRegistry.normalizeAssetPath(assetBasePath, task.getAssetPath()),
                        task.getInstruction(),
                        task.getCategory(),
                        task.getSeed(),
                        task.getMaxSteps(),
                        task.getTimeoutMs()));
            }
            return manifest.withTasks(normalizedTasks);
        }
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
