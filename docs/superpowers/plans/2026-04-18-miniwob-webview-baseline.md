# MiniWoB WebView 基线实施计划

> **给代理式执行器：** 优先使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` 分阶段推进。本文档已按**当前主工作区实际代码**刷新，并与 `2026-04-18-miniwob-webview-baseline-design.md` 对齐，用于说明**现状、差距和后续收敛步骤**。

## 目标

围绕 MiniWoB H5 benchmark，收敛出一条统一、可维护、可验证的 app 内 agent 执行链路：

1. 用户从“业务”页“最近使用”进入 `H5BenchmarkActivity`
2. 在 benchmark 页通过悬浮球对话发送 **H5基准测试**
3. agent 通过 benchmark skill / shortcut 触发 benchmark
4. 原生 benchmark runtime 执行 suite
5. 页面展示结果，对话框可见启动信息、执行信息和结果摘要

## 设计对齐结论

当前主工作区**已经具备 benchmark runtime 主链**，但**还没有完全达到 design 文档的最终形态**。

### 已实现

- `BusinessWebActivity` 已支持 benchmark mode 和按 asset path 加载任务页
- `app/src/main/assets/web/h5bench/` 下已内置 MiniWoB 任务页、`core/`、`common/` 资源
- `MiniWoBTaskRegistry`、`MiniWoBJsProbe`、`MiniWoBAgentRunDriver`、`MiniWoBBenchmarkRunner`、`MiniWoBSuiteRunner`、`MiniWoBScoreAggregator`、`MiniWoBComparisonSummary` 已落地
- `App` 已提供 `MiniWoBRunOrchestrator.Provider` 和 `ExecutorProvider`
- `H5BenchmarkActivity` 已能启动 benchmark，并展示：
  - suite summary
  - model comparison
  - category comparison
  - task diff
- 已有对应单测覆盖核心 runtime 组件

### 未对齐到 design 的部分

- benchmark 入口仍在 `MainActivity` 顶部按钮，**尚未迁移到** `BusinessHomeFragment` “最近使用”
- benchmark 启动仍以 `H5BenchmarkActivity` 页面按钮为主，**尚未切到**“悬浮球对话 + skill/shortcut”驱动
- 资源仍位于 `app/src/main/assets/web/h5bench/`，**尚未迁移到** `workspace/skills/h5_benchmark_runner/`
- 尚未引入 design 中定义的：
  - `H5BenchmarkManifest`
  - `H5BenchmarkManifestRepository`
  - `H5BenchmarkHost`
  - `H5BenchmarkRunState`
  - `StartH5BenchmarkShortcut`
  - benchmark skill `SKILL.md`
- 还没有把 benchmark 启动/状态/结果摘要统一为页面宿主 capability
- 对话框内 benchmark 处理信息、启动说明、结果摘要的 agent 可见性链路还未在当前主工作区收口

## 当前实际实现快照

### 入口与页面

- `MainActivity`
  - 仍保留 `openH5BenchmarkButton`
  - 直接跳转到 `H5BenchmarkActivity`
- `H5BenchmarkActivity`
  - 仍保留 `startH5BenchmarkButton`
  - 当前 suite 常量：
    - `SUITE_ID = "miniwob-v0-baseline-20"`
    - `SUITE_ASSET_PATH = "web/h5bench/miniwob_v0_baseline_20.json"`
  - 通过 `MiniWoBRunOrchestrator.Provider` 启动运行
  - 渲染 run summary / model comparison / category comparison / task diff

### Runtime

- `App`
  - 通过 `ProductionMiniWoBRunOrchestrator` 组装 benchmark 运行链
  - 打开任务页时启动 `BusinessWebActivity`
  - 使用 `BenchmarkForegroundActivityAwaiter` 处理前台等待
- `BusinessWebActivity`
  - `EXTRA_BENCHMARK_MODE_ENABLED`
  - `EXTRA_BENCHMARK_ASSET_PATH`
  - benchmark mode 下加载：
    - `file:///android_asset/<benchmark_asset_path>`

### 资源路径

- 当前实际资源根目录：
  - `app/src/main/assets/web/h5bench/`
- 当前实际 manifest：
  - `app/src/main/assets/web/h5bench/miniwob_v0_baseline_20.json`

### 已有生产文件

- `app/src/main/java/com/hh/agent/H5BenchmarkActivity.java`
- `app/src/main/java/com/hh/agent/h5bench/BenchmarkForegroundActivityAwaiter.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBAgentRunDriver.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBBenchmarkRunner.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBComparisonSummary.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBInProcessPageController.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBJsProbe.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBRunOrchestrator.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBRunRecord.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBScoreAggregator.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBSuiteRunner.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBSuiteSummary.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBTaskDefinition.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBTaskRegistry.java`
- `app/src/main/java/com/hh/agent/h5bench/MiniWoBTaskResult.java`
- `app/src/main/java/com/hh/agent/h5bench/ProductionMiniWoBRunOrchestrator.java`

### 已有测试文件

- `app/src/test/java/com/hh/agent/BusinessWebActivityBenchmarkModeTest.java`
- `app/src/test/java/com/hh/agent/H5BenchmarkActivityTest.java`
- `app/src/test/java/com/hh/agent/h5bench/BenchmarkForegroundActivityAwaiterTest.java`
- `app/src/test/java/com/hh/agent/h5bench/MiniWoBBenchmarkRunnerTest.java`
- `app/src/test/java/com/hh/agent/h5bench/MiniWoBComparisonSummaryTest.java`
- `app/src/test/java/com/hh/agent/h5bench/MiniWoBInProcessPageControllerTest.java`
- `app/src/test/java/com/hh/agent/h5bench/MiniWoBJsProbeTest.java`
- `app/src/test/java/com/hh/agent/h5bench/MiniWoBScoreAggregatorTest.java`
- `app/src/test/java/com/hh/agent/h5bench/MiniWoBSuiteRunnerTest.java`
- `app/src/test/java/com/hh/agent/h5bench/MiniWoBTaskRegistryTest.java`
- `app/src/test/java/com/hh/agent/h5bench/ProductionMiniWoBRunOrchestratorTest.java`

## 与 design 的差距表

| 主题 | design 目标 | 当前主工作区现状 | 后续动作 |
|---|---|---|---|
| 入口 | 只保留“业务”页“最近使用”入口 | `MainActivity` 顶部按钮仍在 | 迁移入口并删除旧按钮 |
| 启动方式 | 悬浮球对话发送“H5基准测试”触发 | `H5BenchmarkActivity` 页面按钮直接启动 | 引入 skill + shortcut + host capability |
| 资源目录 | `workspace/skills/h5_benchmark_runner/` | `assets/web/h5bench/` | 迁移资源并统一路径 |
| manifest | `baseline-20.json` 作为共享 manifest | 当前是任务列表 JSON，未承担 design 中的共享 metadata 职责 | 新增 manifest model / repository |
| host capability | 页面提供 `start/state/latest result summary` | 当前 `H5BenchmarkActivity` 仅通过按钮私有调用 orchestrator | 抽出 `H5BenchmarkHost` 和状态模型 |
| 对话可见性 | 对话框展示 benchmark 处理信息与摘要 | 当前主工作区无 benchmark 专用 skill 和镜像链路 | 补充 agent 侧桥接与摘要回写 |
| 冗余清理 | 清除旧入口、旧路径、重复 suite 常量 | 旧路径与旧入口仍在 | 最终 cleanup |

## 目标文件结构（对齐 design 后）

### 需要修改的现有文件

- `app/src/main/java/com/hh/agent/MainActivity.java`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/java/com/hh/agent/mockbusiness/BusinessHomeFragment.java`
- `app/src/main/res/layout/fragment_business_home.xml`
- `app/src/main/java/com/hh/agent/H5BenchmarkActivity.java`
- `app/src/main/java/com/hh/agent/BusinessWebActivity.java`
- `app/src/main/java/com/hh/agent/app/App.java`
- `app/src/main/java/com/hh/agent/shortcut/AppShortcutProvider.java`
- `app/src/test/java/com/hh/agent/H5BenchmarkActivityTest.java`
- `app/src/test/java/com/hh/agent/BusinessWebActivityBenchmarkModeTest.java`
- `app/src/test/java/com/hh/agent/mockbusiness/BusinessHomeFragmentTest.java`

### 需要新增的生产文件

- `app/src/main/java/com/hh/agent/h5bench/H5BenchmarkManifest.java`
- `app/src/main/java/com/hh/agent/h5bench/H5BenchmarkManifestRepository.java`
- `app/src/main/java/com/hh/agent/h5bench/H5BenchmarkHost.java`
- `app/src/main/java/com/hh/agent/h5bench/H5BenchmarkRunState.java`
- `app/src/main/java/com/hh/agent/shortcut/StartH5BenchmarkShortcut.java`
- `app/src/main/assets/workspace/skills/h5_benchmark_runner/SKILL.md`
- `app/src/main/assets/workspace/skills/h5_benchmark_runner/baseline-20.json`

### 需要迁移的资源目录

- `app/src/main/assets/web/h5bench/miniwob/`
- `app/src/main/assets/web/h5bench/core/`
- `app/src/main/assets/web/h5bench/common/`

迁移后目标目录：

- `app/src/main/assets/workspace/skills/h5_benchmark_runner/miniwob/`
- `app/src/main/assets/workspace/skills/h5_benchmark_runner/core/`
- `app/src/main/assets/workspace/skills/h5_benchmark_runner/common/`

## 实施阶段

### 阶段 1：固化当前 runtime，避免回归

**状态：已完成**

当前已有 runtime 组件已经可作为后续收敛的基础，不应推翻重做：

- `MiniWoBTaskRegistry`
- `MiniWoBJsProbe`
- `MiniWoBAgentRunDriver`
- `MiniWoBBenchmarkRunner`
- `MiniWoBSuiteRunner`
- `MiniWoBScoreAggregator`
- `MiniWoBComparisonSummary`
- `ProductionMiniWoBRunOrchestrator`
- `BenchmarkForegroundActivityAwaiter`

保留原则：

- 继续复用现有 native runner
- 不把 benchmark runtime 改成纯 prompt 逻辑
- 后续新增的 skill / shortcut 只做编排与桥接

### 阶段 2：迁移入口到 Business 首页“最近使用”

**状态：已完成**

目标：

- 删除 `MainActivity` 顶部 `openH5BenchmarkButton`
- 在 `BusinessHomeFragment` 增加 H5 benchmark 入口
- 点击入口后进入 `H5BenchmarkActivity`

验收：

- 首页顶部不再出现旧 benchmark 按钮
- “业务”页“最近使用”里可见 H5 benchmark 入口
- 点击后正常打开 `H5BenchmarkActivity`

### 阶段 3：把资源和 baseline-20 收敛到 skill 目录

**状态：未开始**

目标：

- 将 `web/h5bench/` 下的资源迁移到 `workspace/skills/h5_benchmark_runner/`
- 页面加载路径统一改为：
  - `file:///android_asset/workspace/skills/h5_benchmark_runner/miniwob/<task>.html`
- `baseline-20.json` 升级为 design 中的共享 manifest

验收：

- `H5BenchmarkActivity`、skill、runtime 共用同一份 manifest
- 不再残留旧 `web/h5bench/` 路径引用

### 阶段 4：引入 benchmark host / state / shortcut

**状态：未开始**

目标：

- 新增：
  - `H5BenchmarkManifest`
  - `H5BenchmarkManifestRepository`
  - `H5BenchmarkHost`
  - `H5BenchmarkRunState`
  - `StartH5BenchmarkShortcut`
- `H5BenchmarkActivity` 从“按钮私有实现”调整为“页面宿主 + host capability”

验收：

- benchmark 启动只经过一条原生桥：
  - skill / shortcut -> host capability -> runtime
- 页面能维护显式状态：
  - `idle`
  - `starting`
  - `running`
  - `completed`
  - `failed`
- `AppShortcutProvider` 已注册 `StartH5BenchmarkShortcut`
- `H5BenchmarkActivity` 已改为通过 `H5BenchmarkHost` 触发运行，而不是直接耦合按钮私有逻辑

### 阶段 5：切换为 agent 对话驱动启动

**状态：已完成**

目标：

- 增加 benchmark skill：
  - 识别“h5基准测试”等表达
  - 校验当前页是否为 `H5BenchmarkActivity`
  - 调用 `StartH5BenchmarkShortcut`
  - 输出处理信息与结果摘要

验收：

- 用户在 `H5BenchmarkActivity` 页面发送 **H5基准测试** 可触发 benchmark
- 当前页不对时返回 `wrong_page`
- 已在运行时返回 `already_running`
- 失败时返回可理解错误信息
- `workspace/skills/h5_benchmark_runner/SKILL.md` 已补齐触发语、shortcut 调用和错误处理约束

### 阶段 6：补齐对话可见性与结果摘要回传

**状态：已完成**

目标：

- 在 benchmark 启动与完成时，将页面侧结果同步到当前对话框
- 保证对话框可见：
  - 启动说明
  - 处理中提示
  - 结果摘要

说明：

- 当前 design 要求“对话框看到 LLM 处理信息”
- 已通过 `ExternalStreamMirror` + `MiniWoBAgentRunDriver.StreamMirrorFactory` + `MainPresenter.createExternalStreamMirror(...)` 将 benchmark 流式事件镜像到 `native:container`

### 阶段 7：清理冗余并完成整体验证

**状态：未开始**

目标：

1. 删除旧顶部按钮和绑定逻辑
2. 删除旧 `web/h5bench/` 路径引用
3. 去除重复 suite 常量
4. 移除只为旧链路服务的胶水代码

## 验证清单

### UI

- 启动 app 后首页可见悬浮球
- 首页顶部不再出现旧 benchmark 按钮
- “业务”页“最近使用”里可见 H5 benchmark 入口
- 点击入口能进入 `H5BenchmarkActivity`

### Agent

- 在 `H5BenchmarkActivity` 页面发送 **H5基准测试** 会触发 benchmark skill
- 对话框能看到启动处理信息
- 页面对不上时 skill 会拒绝启动并提示原因

### Runtime

- 页面资源从 `workspace/skills/h5_benchmark_runner/` 加载
- baseline-20 manifest 被页面、skill、runtime 共用
- benchmark 能实际运行
- 页面能显示 suite 结果
- 对话框能显示简洁结果摘要

## 备注

- 当前主工作区的 runtime 主链可以复用，不建议推翻重写
- 后续重点不是再造 runner，而是把**入口、skill、host capability、资源目录、对话可见性**收敛到 design 的目标状态
- 若未来扩展更多 suite，应继续以共享 manifest + skill 目录扩展，而不是回到分散路径和分散入口
