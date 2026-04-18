package com.hh.agent.h5bench;

import android.content.Context;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.util.List;

public class MiniWoBTaskRegistry {
    private final Context context;

    public MiniWoBTaskRegistry(Context context) {
        this.context = context;
    }

    public List<MiniWoBTaskDefinition> loadSuite(String assetPath) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(context.getAssets().open(assetPath))) {
            H5BenchmarkManifest manifest = new Gson().fromJson(reader, H5BenchmarkManifest.class);
            return manifest.getTasks();
        }
    }
}
