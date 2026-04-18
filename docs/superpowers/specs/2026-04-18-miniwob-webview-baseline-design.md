# MiniWoB H5 Benchmark Agent Entry Design

## Goal

把 H5 benchmark 调整为一条统一、可维护的 agent 触发链路：

1. 启动 app 后首页能看到悬浮球，点击后进入 agent 对话框。
2. H5 benchmark 入口从首页顶部栏移除，改为放在“业务”页“最近使用”区域。
3. 用户进入 `H5BenchmarkActivity` 后，通过悬浮球对话发送“`H5基准测试`”触发 benchmark。
4. benchmark 触发必须走 agent 对话模式，对话框中要能看到 LLM 的处理信息。
5. skill、baseline-20 配置、以及 benchmark 页面资源统一维护在同一个 skill 目录，并从该目录加载。
6. 改造过程中识别并清理被新链路替代的冗余代码与重复配置。

## Scope

### In scope

- 修复 `MainActivity` / `H5BenchmarkActivity` 内的悬浮球可见性
- 将 H5 benchmark 入口迁移到“业务”页“最近使用”
- 为 H5 benchmark 增加 skill 驱动的启动方式
- 为 benchmark 增加原生 host capability / shortcut 桥接层
- 将 baseline-20 manifest 与 MiniWoB 页面资源迁入 skill 目录
- 统一 `H5BenchmarkActivity`、skill、host capability 的 suite 数据源
- 让对话框显示 benchmark 启动过程和结束摘要
- 清理旧入口、旧路径、重复 suite 常量和无效胶水代码

### Out of scope

- 不把整个 benchmark runtime 改成纯 prompt 驱动实现
- 不把 benchmark 扩展成全局任意页面都可自动启动
- 不引入新的外部 benchmark server 或浏览器裁判后端
- 不在对话框中实时逐题刷出所有 MiniWoB 子任务日志

## Final UX

目标用户流程固定为：

1. 打开 app。
2. 首页可见悬浮球；点击悬浮球后才出现 agent 对话框。
3. 切到“业务”页，在“最近使用”里点击 **H5基准测试**。
4. 进入 `H5BenchmarkActivity`。
5. 点击悬浮球并在对话框里发送 **H5基准测试**。
6. agent 对话框显示 LLM 正在理解并启动 benchmark 的处理信息。
7. benchmark 开始执行。
8. `H5BenchmarkActivity` 显示详细结果；对话框补充一条简洁结果摘要。

## Core Decisions

### 1. 入口只保留一条

- 移除 `MainActivity` 顶部栏中的 `openH5BenchmarkButton`
- 在“业务”页“最近使用”区域新增 **H5基准测试** 图标入口
- 点击该入口后直接进入 `H5BenchmarkActivity`

这样避免首页顶部入口与业务入口并存，减少重复路径。

### 2. benchmark 启动改为 agent 对话驱动

`H5BenchmarkActivity` 页面内不再依赖“本地静默识别一句话后直接开跑”的方案。

用户消息“`H5基准测试`”必须走：

1. agent 收到消息
2. benchmark skill 触发
3. skill 在对话中输出处理信息
4. skill 调用 benchmark host capability / shortcut
5. host capability 启动现有 benchmark 执行链路

这样可以保留对话框内的 LLM 处理信息，并与现有 agent 模式一致。

### 3. skill 负责编排，代码负责执行

不把整个 `startBenchmarkFlow()` 改成纯 skill 执行逻辑。

采用分层设计：

- **skill 负责**
  - 触发词识别
  - 当前页判断
  - suite 选择
  - 用户可见的处理信息
  - 启动前后状态说明
  - 结果摘要组织

- **代码负责**
  - benchmark runtime 启动
  - Activity / Executor / 生命周期绑定
  - `BusinessWebActivity` 打开与关闭
  - `MiniWoBRunOrchestrator`、`MiniWoBSuiteRunner`、`MiniWoBBenchmarkRunner` 等原生执行链路

`startBenchmarkFlow()` 应抽成一个可复用的 host capability，而不是继续只做按钮私有实现。

### 4. baseline-20 只保留一个数据源

不能在多个地方重复维护：

- `H5BenchmarkActivity.SUITE_ID`
- skill 内硬编码 suite 名
- native runner 里单独写 asset path

统一改成一份共享 manifest，作为唯一 suite 数据源。

### 5. skill、baseline-20、页面资源同目录

benchmark 资源统一收敛到原 skills 目录中，不再从 `assets/web/h5bench/` 加载。

固定目录：

- `app/src/main/assets/workspace/skills/h5_benchmark_runner/`
  - `SKILL.md`
  - `baseline-20.json`
  - `miniwob/...`
  - `core/...`
  - `common/...`
  - `references/`

页面加载路径改为：

- `file:///android_asset/workspace/skills/h5_benchmark_runner/miniwob/<task>.html`

`baseline-20.json` 中对页面资源的引用也统一指向同目录下的资源。

## Components

### A. Entry UI

负责 benchmark 的显式入口调整。

涉及：

- `MainActivity`
- `activity_main.xml`
- `BusinessHomeFragment`
- `fragment_business_home.xml`

职责：

- 删除首页顶部 benchmark 按钮
- 在“最近使用”里增加 H5 benchmark 图标
- 点击后导航到 `H5BenchmarkActivity`

### B. Floating Ball Visibility

负责恢复首页与 benchmark 页的悬浮球显示。

涉及：

- `App`
- 悬浮球初始化配置
- 当前前台页面可见性判定逻辑

职责：

- 保证 `MainActivity` 可见悬浮球
- 保证 `H5BenchmarkActivity` 可见悬浮球
- 不改对话框基础交互模型

### C. Benchmark Skill

新增 benchmark 专用 skill：

- `assets/workspace/skills/h5_benchmark_runner/SKILL.md`

触发条件：

- 当前页面为 `H5BenchmarkActivity`
- 用户表达为“h5基准测试”“开始 h5 基准测试”“跑基准测试”等同义意图

职责：

- 读取当前页面，确认当前确实在 benchmark 页面
- 说明即将启动 baseline-20 benchmark
- 调用 benchmark shortcut / host capability
- 在完成后输出简洁结果摘要

### D. Benchmark Host Capability

新增一个原生 shortcut / bridge：

- `start_h5_benchmark`

职责：

- 校验当前前台是否为 `H5BenchmarkActivity`
- 判断当前是否已有 benchmark 在运行
- 调用 activity 暴露的 benchmark host capability
- 返回结构化执行结果：
  - `started`
  - `already_running`
  - `wrong_page`
  - `failed`

该 capability 是按钮入口和 skill 入口共享的唯一执行通道。

### E. Benchmark Activity Host

`H5BenchmarkActivity` 从“直接拥有按钮私有逻辑”调整为“暴露 benchmark host capability 的页面宿主”。

职责：

- 读取共享 `baseline-20.json`
- 展示当前 suite 信息
- 启动 benchmark
- 维护当前运行状态
- 渲染结果 summary / model comparison / category comparison / task diff
- 在 benchmark 完成后产出一个适合对话框回显的摘要对象

## Data Flow

### 1. 启动 benchmark

1. 用户在 `H5BenchmarkActivity` 页面发送“`H5基准测试`”
2. benchmark skill 被触发
3. skill 读取当前页面并确认处于 benchmark 页
4. skill 输出处理信息
5. skill 调用 `start_h5_benchmark`
6. host capability 调 activity 的 benchmark host
7. benchmark runtime 启动

### 2. 执行 benchmark

执行链路继续复用现有原生链路：

1. 读取 `baseline-20.json`
2. 根据 manifest 加载任务页
3. 打开 `BusinessWebActivity`
4. WebView 加载 `workspace/skills/h5_benchmark_runner/...` 下的任务页面
5. `MiniWoBRunOrchestrator` 执行整轮 baseline
6. `H5BenchmarkActivity` 收到并展示结果

### 3. 回传结果

1. `H5BenchmarkActivity` 得到 suite summary
2. 生成简洁结果摘要对象
3. skill / host capability 将摘要同步给对话框
4. 对话框显示结果结论

## Shared Manifest

`baseline-20.json` 是唯一 suite 数据源，至少包含：

- `suiteId`
- `displayName`
- `entryUtterances`
- `assetBasePath`
- `taskListAssetPath`
- `defaultModelLabel`
- `maxSteps`
- `timeoutMs`
- `resultSummaryFields`

说明：

- `entryUtterances` 供 skill 侧理解触发词
- `taskListAssetPath` 指向当前 suite 的任务列表
- `assetBasePath` 指向同目录 benchmark 页面资源根路径
- `resultSummaryFields` 决定对话框摘要默认展示哪些字段

## State Model

`H5BenchmarkActivity` 需要维护显式运行状态：

- `idle`
- `starting`
- `running`
- `completed`
- `failed`

状态规则：

- `starting` / `running` 时拒绝重复启动
- `failed` 必须带可展示错误信息
- `completed` 保留最近一次结果，支持页面与对话框同时回显

## Error Handling

### Wrong page

如果用户不在 `H5BenchmarkActivity` 页面：

- skill 明确提示当前页不对
- 不自动偷偷导航
- 不启动 benchmark

### Already running

如果当前已经有 benchmark 在执行：

- skill 明确提示“测试已在运行中”
- 不开启第二轮并发 run

### Start failure

如果原生启动失败，包括：

- `benchmark_activity_not_ready`
- `benchmark_page_not_ready`
- `ui_action_timeout`

则：

- 对话框展示可理解错误
- 页面状态进入 `failed`

### Runtime / provider failure

如果 benchmark 执行中出现模型、网络或超时错误：

- 页面结果区展示错误摘要
- 对话框追加失败说明
- 不把失败伪装成“处理中”

## Redundancy Cleanup

实现时必须顺带清理以下冗余：

1. 删除首页顶部旧 benchmark 按钮和相关绑定代码
2. 删除或重构仅供旧按钮调用的私有启动逻辑
3. 去除散落在 activity、skill、runner 中的重复 suite 常量
4. 删除不再使用的旧 asset 路径与旧入口文案
5. 若存在仅为旧 `assets/web/h5bench/` 路径服务的胶水代码，迁移后应一并清理

清理原则：

- 只清理被新设计替代的代码
- 不做与本需求无关的重构
- 保持 benchmark 运行链路可验证

## Verification

### UI verification

- 启动 app 后首页可见悬浮球
- 点击悬浮球后出现 agent 对话框
- 首页顶部不再出现旧 benchmark 按钮
- “业务”页“最近使用”里可见 H5基准测试入口

### Agent verification

- 在 `H5BenchmarkActivity` 中发送“H5基准测试”会触发 benchmark skill
- 对话框能看到 LLM 处理信息
- 当前页不对时 skill 会拒绝启动并提示原因

### Runtime verification

- 页面从 `workspace/skills/h5_benchmark_runner/` 目录加载
- baseline-20 manifest 被 `H5BenchmarkActivity`、skill、host capability 共用
- benchmark 能真正开始执行
- 页面能显示结果
- 对话框能收到简洁结果摘要

## Implementation Notes

- 现有 benchmark runtime 尽量复用，不重新设计执行器
- `H5BenchmarkActivity` 的启动能力应对按钮和 skill 统一暴露
- 若未来扩展更多 suite，应继续在同一 skill 目录下以 manifest 扩展，而不是再分散回多个目录
