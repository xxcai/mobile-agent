package com.hh.agent.h5bench;

import java.util.Collections;
import java.util.List;

public class H5BenchmarkManifest {
    private String suiteId;
    private String displayName;
    private String taskListAssetPath;
    private List<MiniWoBTaskDefinition> tasks;

    public String getSuiteId() {
        return suiteId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTaskListAssetPath() {
        return taskListAssetPath;
    }

    public List<MiniWoBTaskDefinition> getTasks() {
        return tasks == null ? Collections.emptyList() : tasks;
    }
}
