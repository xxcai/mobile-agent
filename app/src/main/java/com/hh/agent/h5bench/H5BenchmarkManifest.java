package com.hh.agent.h5bench;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class H5BenchmarkManifest {
    @SerializedName("suiteId")
    private String suiteId;

    @SerializedName(value = "displayName", alternate = {"suiteName"})
    private String displayName;

    @SerializedName("assetBasePath")
    private String assetBasePath;

    @SerializedName("tasks")
    private List<MiniWoBTaskDefinition> tasks;

    public static H5BenchmarkManifest empty(String suiteId, String displayName) {
        H5BenchmarkManifest manifest = new H5BenchmarkManifest();
        manifest.suiteId = suiteId;
        manifest.displayName = displayName;
        manifest.tasks = Collections.emptyList();
        return manifest;
    }

    H5BenchmarkManifest withTasks(List<MiniWoBTaskDefinition> normalizedTasks) {
        H5BenchmarkManifest manifest = new H5BenchmarkManifest();
        manifest.suiteId = suiteId;
        manifest.displayName = displayName;
        manifest.assetBasePath = assetBasePath;
        manifest.tasks = normalizedTasks == null ? Collections.emptyList() : new ArrayList<>(normalizedTasks);
        return manifest;
    }

    public String getSuiteId() {
        return suiteId;
    }

    public String getDisplayName() {
        return displayName == null || displayName.trim().isEmpty() ? suiteId : displayName;
    }

    public String getAssetBasePath() {
        return assetBasePath;
    }

    public List<MiniWoBTaskDefinition> getTasks() {
        return tasks == null ? Collections.emptyList() : Collections.unmodifiableList(tasks);
    }
}
