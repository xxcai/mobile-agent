# MiniWoB WebView 基线实施计划

> 本文档已根据当前代码实现刷新，作用从“差距收敛计划”切换为“实现状态与维护计划”。当前实现对应分支：`feature/h5bench-agent-baseline`。

## 目标

在 demo app 内稳定提供一条 agent 可触发、页面可观察、结果可复现的 MiniWoB H5 benchmark 执行链路。

## 当前结论

当前实现已经完成原计划中的核心阶段，主链路可概括为：

1. 用户从“业务”页“最近使用”进入 `H5BenchmarkActivity`
2. 通过页面按钮或悬浮球对话触发 benchmark
3. skill 调用 `start_h5_benchmark`
4. shortcut 校验页面并触发 `H5BenchmarkHost`
5. app 内 runtime 执行 baseline-20
6. 页面展示结果，对话框镜像任务执行信息

## 实现状态

| 项目 | 状态 | 当前实现 |
|---|---|---|
| 入口迁移 | 已完成 | `BusinessHomeFragment` “最近使用”新增 H5 benchmark 入口，首页顶部旧入口已移除 |
| benchmark 宿主页 | 已完成 | `H5BenchmarkActivity` 已具备 host/state/result UI |
| 共享资源目录 | 已完成 | `workspace/skills/h5_benchmark_runner/` 下统一维护 skill、manifest、MiniWoB 资源 |
| 共享 manifest | 已完成 | `baseline-20.json` 为唯一 suite 资源源 |
| shortcut 桥接 | 已完成 | `StartH5BenchmarkShortcut` 已注册进 `AppShortcutProvider` |
| 对话驱动启动 | 已完成 | `h5_benchmark_runner/SKILL.md` 已接入 `start_h5_benchmark` |
| 对话可见性 | 已完成 | `ExternalStreamMirror` 已把 benchmark 流式事件镜像到 `native:container` |
| 真机构建验证 | 已完成 | 已成功 `assembleDebug`、安装到 `ALN_AL00` 并完成人工验证 |
| 旧 worktree 清理 | 已完成 | `.worktrees/h5bench-baseline` 已移除 |

## 当前代码结构

### UI / Entry

- `app/src/main/java/com/hh/agent/mockbusiness/BusinessHomeFragment.java`
- `app/src/main/res/layout/fragment_business_home.xml`
- `app/src/main/java/com/hh/agent/H5BenchmarkActivity.java`
- `app/src/main/res/layout/activity_h5_benchmark.xml`

### Shortcut / Skill

- `app/src/main/java/com/hh/agent/shortcut/StartH5BenchmarkShortcut.java`
- `app/src/main/java/com/hh/agent/shortcut/AppShortcutProvider.java`
- `app/src/main/assets/workspace/skills/h5_benchmark_runner/SKILL.md`

### Runtime

- `app/src/main/java/com/hh/agent/app/App.java`
- `app/src/main/java/com/hh/agent/BusinessWebActivity.java`
- `app/src/main/java/com/hh/agent/h5bench/H5BenchmarkHost.java`
- `app/src/main/java/com/hh/agent/h5bench/H5BenchmarkManifest.java`
- `app/src/main/java/com/hh/agent/h5bench/H5BenchmarkManifestRepository.java`
- `app/src/main/java/com/hh/agent/h5bench/H5BenchmarkRunState.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBAgentRunDriver.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBBenchmarkRunner.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBSuiteRunner.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBTaskRegistry.java`
- `app/src/main/java/com/hh/agent/h5bench/ProductionMiniWoBRunOrchestrator.java`

### Conversation Mirroring

- `agent-android/src/main/java/com/hh/agent/android/presenter/MainPresenter.java`
- `agent-android/src/main/java/com/hh/agent/android/presenter/ExternalStreamMirror.java`

## 当前行为说明

### 1. 启动方式

当前同时保留两种启动方式：

- **页面按钮**：`H5BenchmarkActivity` 内的手动触发入口，直接调用 `H5BenchmarkHost.start()`
- **agent 对话**：悬浮球对话发送 **H5基准测试**，通过 skill -> shortcut -> host 启动

### 2. manifest 形态

当前 `baseline-20.json` 使用的是轻量共享 manifest，不包含早期设计草稿中的额外 metadata。当前真实字段仅为：

- `suiteId`
- `suiteName`
- `assetBasePath`
- `tasks`

### 3. 对话镜像范围

当前镜像到 `native:container` 的内容包括：

- 每题任务提示
- LLM 文本输出
- LLM reasoning
- tool use
- tool result

页面侧仍承担 benchmark 汇总结果的主要展示职责。

## 验证记录

### 关键回归

- `:agent-android:testDebugUnitTest --tests 'com.hh.agent.android.presenter.ExternalStreamMirrorTest'`
- `:app:testDebugUnitTest --tests 'com.hh.agent.h5bench.MiniWoBAgentRunDriverTest'`

### benchmark 相关回归

- `:app:testDebugUnitTest --tests 'com.hh.agent.BusinessWebActivityBenchmarkModeTest' --tests 'com.hh.agent.H5BenchmarkActivityTest' --tests 'com.hh.agent.h5bench.H5BenchmarkHostTest' --tests 'com.hh.agent.h5bench.H5BenchmarkManifestRepositoryTest' --tests 'com.hh.agent.h5bench.H5BenchmarkSkillAssetTest' --tests 'com.hh.agent.h5bench.MiniWoBTaskRegistryTest' --tests 'com.hh.agent.h5bench.MiniWoBBenchmarkRunnerTest' --tests 'com.hh.agent.h5bench.MiniWoBSuiteRunnerTest' --tests 'com.hh.agent.h5bench.MiniWoBInProcessPageControllerTest' --tests 'com.hh.agent.shortcut.StartH5BenchmarkShortcutTest'`

### 打包与真机

- `:app:assembleDebug`
- `adb -s FMR0223C27021263 install -r app/build/outputs/apk/debug/app-debug.apk`
- `adb -s FMR0223C27021263 shell pm path com.hh.agent`

## 当前维护注意项

1. `config.json.template` 仍是本地敏感配置，不应提交。
2. 远端已存在两个相关分支：
   - `origin/feature/h5bench-agent-baseline`：当前正式实现
   - `origin/feature/h5bench-baseline`：历史 worktree 分支，供追溯参考
3. `.worktrees/h5bench-baseline` 本地目录已清理，不再需要额外收敛动作。

## 后续可选项

以下不是当前实现阻塞项，只是后续可选演进方向：

1. 让 skill 结果回显中带上更结构化的 benchmark summary
2. 将 `baseline-20.json` 扩展为更丰富的 manifest metadata
3. 补充 PR/发布说明文档，方便后续版本交接
