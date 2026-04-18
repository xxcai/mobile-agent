package com.hh.agent.shortcut;

import android.app.Activity;

import com.hh.agent.H5BenchmarkActivity;
import com.hh.agent.core.shortcut.ShortcutDefinition;
import com.hh.agent.core.shortcut.ShortcutExecutor;
import com.hh.agent.core.tool.ToolResult;
import com.hh.agent.h5bench.H5BenchmarkHost;
import com.hh.agent.h5bench.H5BenchmarkRunState;

import org.json.JSONObject;

import java.util.function.Supplier;

public final class StartH5BenchmarkShortcut implements ShortcutExecutor {
    private final Supplier<Activity> foregroundActivityProvider;

    public StartH5BenchmarkShortcut(Supplier<Activity> foregroundActivityProvider) {
        if (foregroundActivityProvider == null) {
            throw new IllegalArgumentException("foregroundActivityProvider cannot be null");
        }
        this.foregroundActivityProvider = foregroundActivityProvider;
    }

    @Override
    public ShortcutDefinition getDefinition() {
        return ShortcutDefinition.builder(
                        "start_h5_benchmark",
                        "开始 H5 基准测试",
                        "在 H5BenchmarkActivity 页面启动当前基准测试运行")
                .domain("benchmark")
                .requiredSkill("h5_benchmark_runner")
                .tips("仅当当前前台页面是 H5BenchmarkActivity 时调用")
                .build();
    }

    @Override
    public ToolResult execute(JSONObject args) {
        Activity activity = foregroundActivityProvider.get();
        if (!(activity instanceof H5BenchmarkActivity)) {
            return ToolResult.error("wrong_page", "Foreground page is not H5BenchmarkActivity")
                    .with("code", "wrong_page")
                    .with("expectedPage", H5BenchmarkActivity.class.getSimpleName())
                    .with("currentPage", activity == null ? null : activity.getClass().getSimpleName());
        }

        H5BenchmarkHost host = ((H5BenchmarkActivity) activity).getBenchmarkHost();
        if (host == null) {
            return ToolResult.error("execution_failed", "Benchmark host is unavailable")
                    .with("code", "execution_failed");
        }

        H5BenchmarkRunState state = host.getState();
        if (state == H5BenchmarkRunState.STARTING || state == H5BenchmarkRunState.RUNNING) {
            return ToolResult.error("already_running", "H5 benchmark is already running")
                    .with("code", "already_running")
                    .with("state", state.name());
        }

        if (!host.start()) {
            H5BenchmarkRunState latestState = host.getState();
            return ToolResult.error("already_running", "H5 benchmark is already running")
                    .with("code", "already_running")
                    .with("state", latestState == null ? null : latestState.name());
        }

        H5BenchmarkRunState startedState = host.getState();
        return ToolResult.success()
                .with("code", "started")
                .with("state", startedState == null ? null : startedState.name());
    }
}
