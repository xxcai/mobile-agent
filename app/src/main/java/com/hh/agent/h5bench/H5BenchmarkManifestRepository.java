package com.hh.agent.h5bench;

import android.content.Context;

import com.google.gson.Gson;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class H5BenchmarkManifestRepository {
    public static final String BASELINE_20_ASSET_PATH = "workspace/skills/h5_benchmark_runner/baseline-20.json";

    private final Context context;
    private final Gson gson = new Gson();

    public H5BenchmarkManifestRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public H5BenchmarkManifest loadBaseline20() throws Exception {
        return load(BASELINE_20_ASSET_PATH);
    }

    public H5BenchmarkManifest load(String assetPath) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                context.getAssets().open(assetPath), StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, H5BenchmarkManifest.class);
        }
    }
}
