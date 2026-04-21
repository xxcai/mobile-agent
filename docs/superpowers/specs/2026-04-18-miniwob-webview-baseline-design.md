# MiniWoB H5 Benchmark In-App Agent Design

## Goal

在 demo app 内落地一条可复现、可观察、可由 agent 触发的 MiniWoB H5 benchmark 链路：

1. 用户从“业务”页“最近使用”进入 `H5BenchmarkActivity`
2. `H5BenchmarkActivity` 中通过悬浮球对话发送 **H5基准测试**
3. agent 通过 skill + shortcut 触发 benchmark
4. app 内原生 benchmark runtime 执行 baseline-20
5. 页面展示结果，对话框可见任务上下文、LLM 文本/思考、tool use、tool result

## Implemented UX

当前实现的用户路径为：

1. 打开 app，首页可见悬浮球
2. 切换到“业务”页，在“最近使用”区域点击 **H5基准测试**
3. 进入 `H5BenchmarkActivity`
4. 用户可以：
   - 点击页面内 **开始基准测试预览** 按钮触发 benchmark host
   - 或通过悬浮球对话发送 **H5基准测试**，由 `h5_benchmark_runner` skill 触发 `start_h5_benchmark`
5. `H5BenchmarkActivity` 展示：
   - 当前 suite
   - run selector
   - summary json
   - model comparison
   - category comparison
   - task diff
6. benchmark 执行过程中，当前任务提示、LLM 文本/思考、tool use、tool result 会镜像到 `native:container` 对话

## Scope

### In scope

- 业务首页入口迁移到 `BusinessHomeFragment` “最近使用”
- `H5BenchmarkActivity` 页面宿主化
- `StartH5BenchmarkShortcut` 原生桥接
- `workspace/skills/h5_benchmark_runner/` 下的 skill、manifest、MiniWoB 页面资源统一维护
- `BusinessWebActivity` benchmark mode
- `MiniWoBTaskRegistry` / `MiniWoBSuiteRunner` / `MiniWoBBenchmarkRunner` / `ProductionMiniWoBRunOrchestrator` 执行链
- `ExternalStreamMirror` 对话镜像

### Out of scope

- 自动从任意页面导航到 `H5BenchmarkActivity`
- 将 benchmark runtime 全量改造成纯 prompt 逻辑
- 对话框内逐题展示完整 benchmark 汇总报表
- 引入额外的外部 benchmark 裁判服务

## Architecture

### 1. Entry UI

入口由 `BusinessHomeFragment` 承载：

- 首页顶部旧按钮已移除
- “业务”页“最近使用”区域新增 **H5基准测试** 入口
- 点击后直接打开 `H5BenchmarkActivity`

### 2. Benchmark Activity Host

`H5BenchmarkActivity` 负责承载 benchmark 页状态与结果展示：

- 通过 `H5BenchmarkManifestRepository` 读取 `baseline-20.json`
- 通过 `H5BenchmarkHost` 维护运行状态：
  - `IDLE`
  - `STARTING`
  - `RUNNING`
  - `COMPLETED`
  - `FAILED`
- 通过 `MiniWoBRunOrchestrator.Provider` 和 `ExecutorProvider` 启动 benchmark
- 无 orchestrator 时退化为打开首个 task 页进行预览

### 3. Shortcut and Skill Bridge

benchmark agent 触发链路为：

1. `workspace/skills/h5_benchmark_runner/SKILL.md` 命中用户表达
2. skill 调用 `start_h5_benchmark`
3. `StartH5BenchmarkShortcut` 校验当前页是否为 `H5BenchmarkActivity`
4. shortcut 调用 `activity.getBenchmarkHost().start()`
5. host 进入 `STARTING/RUNNING`

`StartH5BenchmarkShortcut` 当前返回的结构化结果为：

- `started`
- `wrong_page`
- `already_running`
- `execution_failed`

### 4. Runtime Execution

benchmark 执行链路保持在 app 内：

1. `App` 组装 `ProductionMiniWoBRunOrchestrator`
2. `MiniWoBSuiteRunner` 通过 `MiniWoBTaskRegistry` 读取 suite
3. `MiniWoBBenchmarkRunner` 驱动单题执行
4. `MiniWoBInProcessPageController` 打开 `BusinessWebActivity`
5. `BusinessWebActivity` 以 benchmark mode 加载 `file:///android_asset/...`
6. `MiniWoBJsProbe` 负责 episode 启动与状态读取

### 5. Conversation Mirroring

benchmark 对话可见性通过以下链路实现：

1. `App` 创建 `MiniWoBAgentRunDriver(StreamMirrorFactory)`
2. `StreamMirrorFactory` 将每个 task 绑定到 `MainPresenter.getInstance(ContainerActivity.SESSION_KEY)`
3. `MainPresenter.createExternalStreamMirror(...)` 创建外部流式镜像 listener
4. `ExternalStreamMirror` 将用户任务提示、reasoning、tool call、tool result、文本输出同步到容器会话

## Shared Assets and Manifest

统一目录：

- `app/src/main/assets/workspace/skills/h5_benchmark_runner/`
  - `SKILL.md`
  - `baseline-20.json`
  - `miniwob/`
  - `core/`
  - `common/`

当前 `baseline-20.json` 实际字段为：

- `suiteId`
- `suiteName`
- `assetBasePath`
- `tasks`

其中每个 task 包含：

- `taskId`
- `assetPath`
- `instruction`
- `category`
- `seed`
- `maxSteps`
- `timeoutMs`

`H5BenchmarkManifestRepository` 会将 `assetBasePath + assetPath` 规范化为最终 asset 路径。

## Execution Flow

### Agent-triggered start

1. 用户在 `H5BenchmarkActivity` 页面发送 **H5基准测试**
2. `h5_benchmark_runner` skill 命中
3. skill 调用 `start_h5_benchmark`
4. `StartH5BenchmarkShortcut` 校验当前前台页与 host 状态
5. `H5BenchmarkHost.start()` 启动 benchmark flow
6. 页面进入 `STARTING/RUNNING`

### Runtime loop

1. 读取 `baseline-20.json`
2. 打开 task 对应 MiniWoB 页面
3. Agent 在当前 task 页面执行
4. `MiniWoBBenchmarkRunner` 聚合单题结果
5. `MiniWoBScoreAggregator` 生成 suite summary
6. `H5BenchmarkActivity` 渲染结果

### Conversation loop

1. 为每个 task 生成“开始 H5 基准测试任务”用户提示
2. 将 task 内 reasoning / text / tool use / tool result 镜像到 `native:container`
3. 容器对话中可直接观察 benchmark 执行过程

## Error Handling

### Shortcut layer

- `wrong_page`: 当前前台页不是 `H5BenchmarkActivity`
- `already_running`: host 当前处于 `STARTING` 或 `RUNNING`
- `execution_failed`: 当前页存在但 host 不可用

### Activity layer

- manifest 读取失败时，`H5BenchmarkActivity` 显示 failed 状态并回退为空 manifest
- benchmark 运行失败时，页面 summary 区展示 failed 状态和错误消息

### Runtime layer

- `benchmark_activity_not_ready`
- `benchmark_page_not_ready`
- `start_episode_failed`
- `clear_storage_failed`
- `agent_run_timeout`

这些错误由现有 runtime 继续上抛，并由 activity / shortcut / 对话层做用户可见反馈。

## Verification

当前实现至少经过以下验证：

- `:agent-android:testDebugUnitTest --tests 'com.hh.agent.android.presenter.ExternalStreamMirrorTest'`
- `:app:testDebugUnitTest --tests 'com.hh.agent.BusinessWebActivityBenchmarkModeTest' --tests 'com.hh.agent.H5BenchmarkActivityTest' --tests 'com.hh.agent.h5bench.H5BenchmarkHostTest' --tests 'com.hh.agent.h5bench.H5BenchmarkManifestRepositoryTest' --tests 'com.hh.agent.h5bench.H5BenchmarkSkillAssetTest' --tests 'com.hh.agent.h5bench.MiniWoBAgentRunDriverTest' --tests 'com.hh.agent.h5bench.MiniWoBTaskRegistryTest' --tests 'com.hh.agent.h5bench.MiniWoBBenchmarkRunnerTest' --tests 'com.hh.agent.h5bench.MiniWoBSuiteRunnerTest' --tests 'com.hh.agent.h5bench.MiniWoBInProcessPageControllerTest' --tests 'com.hh.agent.shortcut.StartH5BenchmarkShortcutTest'`
- `:app:assembleDebug`
- 真机安装与运行验证（`ALN_AL00`）
