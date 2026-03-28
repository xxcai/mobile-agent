# Android Route Tooling

## 目标

为 agent 提供基于宿主路由框架的稳定导航能力，覆盖两类目标：

- 原生 Android 页面
- 宿主内 WebView 承载的类小程序页面

本轮重点是把“从用户意图到页面跳转完成”的链路做成稳定 contract，用结构化 business tool 承载解析和执行，用固定 skill 承载业务目标知识，而不是让模型直接猜 URI、分别查询两套路由源，或提前退回 UI 点击。

## 关键决策

- 路由能力优先作为 `call_android_tool` 下的 Android business tool，不单独新建 channel。
- tool 负责 route target 解析、跳转执行和失败协议；skill 负责业务目标知识、使用时机和参数说明。
- 默认不直接暴露任意 URI 给模型去拼接或猜测，但允许 skill 在“已知精确 URI”的场景下直接提供 URI 作为 route hint。
- skill 到 tool 的主协议以 `RouteHint` 为中心，而不是假设总能给出稳定 `bizKey`。
- Java 侧负责 `RouteHint -> RouteResolution -> openUri(...)` 的统一解析链路、白名单和失败协议，不把原生路由表查询和小程序查询分别暴露给模型。
- 宿主内原生页和小程序页在 tool 结果里统一抽象为 `RouteTarget`，只在 `targetType` 和 `source` 上区分。
- 与现有 `android_view_context_tool` / `android_gesture_tool` 保持并存，不在本轮替代 UI 路径。
- 如果当前前台是 `ContainerActivity`，route opener 在执行跳转前必须先收起容器页，并等待真实宿主前台 Activity 稳定后再调用 `openUri(...)`。
- 本轮“跳转成功”的定义是：tool 完成解析并调用宿主跳转能力得到成功结果；不要求本轮完成视觉校验或表单操作。

## Route Hint 场景

- 已知精确 URI
  - 例如 skill 已明确知道 `ui://myapp.search/selectActivity`
  - tool 只需要做白名单校验和跳转执行
- 已知原生模块，但不知道具体页面
  - 例如已知模块是 `myapp.search`，只知道关键词
  - tool 在宿主收集到的原生声明中按模块内匹配
- 已知小程序名字
  - tool 通过小程序查询能力获取最匹配结果
- 只知道模糊名称，且不确定原生还是小程序
  - tool 同时做 native / miniapp 解析，并返回唯一结果、候选或未命中

## 步骤顺序

1. 梳理当前双栈路由能力边界，收敛统一 `RouteTarget` contract（已完成）
2. 设计基于 `RouteHint / RouteResolution` 的 route resolver / opener business tool schema、错误协议和返回形态（已完成）
3. 设计 Java 侧数据源边界，明确 native registry、小程序查询和 `openUri(...)` 的装配方式（已完成）
4. 设计 route skill 的内容结构、RouteHint 表达方式和与 tool 的协作方式（已完成）
5. 明确最小验证面、测试入口和文档同步边界，再决定是否进入实现阶段（已完成）
6. 建立 route runtime 数据结构与 resolver 骨架（已完成）
7. 接入 mock bridge 与 `resolve_route` tool（已完成）
8. 建立 opener 骨架与前台环境准备能力（已完成）
9. 接入真实 `openUri(...)` 与 `open_resolved_route` tool（已完成）
10. 完成调试入口、最小测试与主链路联调（已完成）
11. 替换 `NativeRouteBridge` 为真实实现（待开始）
12. 替换 `MiniAppRouteBridge` 为真实实现（待开始）
13. 评估并固化正式文档与 skill 内容（待开始）

## 阶段收口快照

- 当前已完成内容
  - 已确认路由能力更适合放在 Android business tool 层，而不是新的 tool channel。
  - 已确认 URI/route 知识应由固定 skill 提供，不应直接散落在通用 prompt 中。
  - 已确认宿主存在原生 Activity 与 WebView 承载页两类目标，本 feature 需要统一抽象两者，而不是让模型分别处理。
  - 已确认主协议从单一 `bizKey` 调整为 `RouteHint -> RouteResolution -> openUri(...)`。
  - 已确认跳转前如果前台是 `ContainerActivity`，必须先收起容器页并等待宿主前台稳定。
  - 已完成 `resolve_route` / `open_resolved_route` 的 contract 草案，明确了输入结构、输出状态、字段优先级、冲突处理、URI 校验口子和 opener 固定时序。
  - 已完成 Java runtime 分层和装配边界设计，明确 App 侧只注入 `NativeRouteBridge`、`MiniAppRouteBridge`、`HostRouteInvoker`，其余能力尽量收进 `agent-android`。
  - 已完成 skill 侧 RouteHint 治理边界设计，明确 skill 只提供线索、不做 route 判定，并明确了 route 信息优先顺序和触发 `resolve_route` 的规则。
  - 已完成验证面和验证入口设计，明确 resolver / opener 为当前最小验证范围，native / miniapp bridge 允许 mock，`openUri(...)` 优先接真实能力。
  - 已完成 Step 06 编码落地，建立 route runtime 基础模型、resolver 骨架和最小单测。
  - 已完成 Step 07 编码落地，接入 mock bridge、`resolve_route` tool、App 层最小组装和 resolver 调试入口。
  - 已完成 Step 08 编码落地，建立 opener 骨架、前台环境准备能力和局部错误翻译路径。
  - 已完成 Step 09 编码落地，接入 demo 版真实 route invoker、`open_resolved_route` tool 和 route 打开调试入口。
  - 已完成 Step 10 验证收口，自动化验证和手工验收均通过。
- 当前未完成内容
  - 当前 feature 的 demo 闭环已完成，尚未开始将 mock `NativeRouteBridge` / `MiniAppRouteBridge` 替换为真实实现。
  - 尚未完成 Step 11-13 的真实接入与文档固化。
- 当前真实能力边界
  - 目前 agent 只能通过已有 business tool 或 UI tool 完成跳转/点击，没有结构化 route tool。
  - 当前 skill 体系可以承载领域知识，但不具备“skill 调 skill”的运行时机制。
  - 现有主链路已区分 `call_android_tool`、`android_view_context_tool`、`android_gesture_tool`，适合把 route 继续放在 business tool 层。
- 建议下一步优先级
  - 当前 feature 已阶段性收口。
  - 如果继续推进，建议优先替换 `NativeRouteBridge` 为真实实现，再替换 `MiniAppRouteBridge`。
  - bridge 真实接入稳定后，再判断是否需要补 `docs/` 和正式 skill 内容。

## 后续步骤

### Step 11 - Real NativeRouteBridge Integration

目标：

- 将当前 mock `NativeRouteBridge` 替换为宿主真实模块声明收集实现。

要做的事情：

1. 对接宿主现有的 native route 声明收集来源。
2. 将声明结果适配到 `NativeRouteBridge` 接口。
3. 保持 `RouteResolver` contract 不变。
4. 回归验证：
   - `uri` 直达
   - `nativeModule`
   - `keywords` native 命中
5. 替换后记录兼容边界和缺失能力。

验收标准：

- `NativeRouteBridge` 已接真实数据源。
- 不影响现有 `resolve_route` / `open_resolved_route` contract。
- 现有 route 调试入口和最小测试继续通过。

### Step 12 - Real MiniAppRouteBridge Integration

目标：

- 将当前 mock `MiniAppRouteBridge` 替换为真实小程序查询实现。

要做的事情：

1. 对接宿主可用的小程序查询能力。
2. 适配到单 query `MiniAppRouteBridge.search(query)`。
3. 保持 query 选择规则不变：
   - 优先 `miniAppName`
   - 其次第一个 keyword
4. 回归验证：
   - `miniAppName`
   - `keywords` miniapp 命中
   - native / miniapp 双候选
5. 记录查询返回质量和候选稳定性问题。

验收标准：

- `MiniAppRouteBridge` 已接真实查询源。
- resolver 在 miniapp 路径上的 contract 保持稳定。
- 调试入口可复现真实 miniapp 候选和命中结果。

### Step 13 - Docs And Skill Solidification

目标：

- 在真实 bridge 接入稳定后，固化正式文档与 skill 内容。

要做的事情：

1. 评估是否需要同步 `docs/`。
2. 补 route tool 的正式接入说明。
3. 补 skill 编写规范和 route hint 约束。
4. 标记哪些 demo 实现仍只是验证用，不应当作正式宿主能力。
5. 判断当前 feature 是否完全收口，或是否拆出后续独立 feature。

验收标准：

- 文档与当前代码状态一致。
- skill route 约束可被后续业务 skill 直接复用。
- demo 与正式能力边界已明确。

## 实现事项总览

1. 实现 `resolve_route` tool 的代码骨架。
2. 实现 `RouteHint`、`RouteResolution`、`RouteTarget` 的 Java 数据结构和 JSON 映射。
3. 在 `agent-android` 内建立 `RouteResolver`、`RouteScorer`、`UriAccessPolicy`、`AndroidRouteRuntime`。
4. 定义并接入 `NativeRouteBridge`、`MiniAppRouteBridge`、`HostRouteInvoker` 抽象。
5. 先提供 mock 的 `NativeRouteBridge` 和 `MiniAppRouteBridge` 实现，打通 resolver 链路。
6. 在 App 层集中组装 route runtime，并注册 `resolve_route` 对应的 tool executor 到 `AndroidToolManager`。
7. 增加 resolver 的调试入口，验证 `uri`、`nativeModule`、`miniAppName`、`keywords` 四类输入。
8. 增加 resolver 的最小测试，覆盖 `resolved`、`candidates`、`not_found`、`insufficient_hint`。
9. 实现 `open_resolved_route` 的代码骨架。
10. 在 `agent-android` 内建立 `RouteOpener` 和 `ForegroundHostController`，封装收起 `ContainerActivity` 与等待宿主前台稳定。
11. 在 App 层接入真实 `HostRouteInvoker`，调用宿主 `openUri(...)`。
12. 注册 `open_resolved_route` 对应的 tool executor 到 `AndroidToolManager`。
13. 增加 opener 的调试入口，验证宿主页直接跳转、容器页收起后跳转、前台未稳定失败和 `openUri(...)` 异常失败。
14. 做一次主链路联调，验证 skill -> `resolve_route` -> `open_resolved_route` 的真实调用路径。
15. 再逐步把 mock 的 native / miniapp bridge 替换成真实实现。
16. 最后再决定是否补正式文档和 skill 内容更新。

## 编码步骤

### Step 06 - Route Runtime Models And Resolver Skeleton

目标：

- 建立 route runtime 的核心数据结构和 resolver 骨架，但不接入真实宿主能力。

要做的事情：

1. 实现 `RouteHint`、`RouteResolution`、`RouteTarget` 的 Java 数据结构。
2. 实现这些数据结构与 tool JSON 请求/响应之间的映射。
3. 在 `agent-android` 内建立：
   - `RouteResolver`
   - `RouteScorer`
   - `UriAccessPolicy`
   - `AndroidRouteRuntime`
4. 在 `RouteResolver` 中搭好固定执行路径：
   - hint 归一化
   - 可检索判断
   - uri 直达分支
   - native / miniapp / keywords 分支占位
   - 候选收敛占位
5. 为后续 bridge 注入预留构造参数和接口依赖。

验收标准：

- route runtime 的核心模型和 resolver 骨架可编译。
- `AndroidRouteRuntime.resolve(...)` 可接受输入并返回基础结构结果。
- 不接入真实 bridge 也能完成空实现下的基本调用链。

### Step 07 - Mock Bridges And resolve_route Tool

目标：

- 用 mock bridge 打通 `resolve_route` 完整链路，并注册到 Android tool 系统。

要做的事情：

1. 定义并接入：
   - `NativeRouteBridge`
   - `MiniAppRouteBridge`
   - `HostRouteInvoker` 抽象
2. 提供 mock 的 native / miniapp bridge 实现。
3. 在 App 层集中组装 `AndroidRouteRuntime`。
4. 实现 `resolve_route` 对应的 tool executor。
5. 注册 `resolve_route` 到 `AndroidToolManager`。
6. 增加 resolver 调试入口，支持构造四类 `RouteHint` 输入。
7. 增加 resolver 最小测试，覆盖：
   - `resolved`
   - `candidates`
   - `not_found`
   - `insufficient_hint`

验收标准：

- `resolve_route` 可通过 mock bridge 返回结构化结果。
- 调试入口可验证 uri/nativeModule/miniAppName/keywords 四类输入。
- 最小测试可证明 resolver 状态收敛正确。

### Step 08 - Opener Skeleton And Foreground Preparation

目标：

- 建立 `RouteOpener` 和 `ForegroundHostController`，打通跳转前环境准备能力。

要做的事情：

1. 在 `agent-android` 内实现 `RouteOpener` 骨架。
2. 实现 `ForegroundHostController`，封装：
   - 当前前台判断
   - `ContainerActivity` 收起
   - 宿主前台稳定等待
3. 将现有容器页收起与稳定等待能力归口到 controller。
4. 定义 opener 失败翻译逻辑：
   - `invalid_target`
   - `container_not_dismissed`
   - `host_activity_not_stable`
   - `open_uri_failed`
5. 先用 mock / fake `HostRouteInvoker` 验证 opener 内部流程。

验收标准：

- `RouteOpener` 可独立完成 target 校验和前台准备。
- `ForegroundHostController` 可复用现有容器页处理能力。
- opener 能在不接真实 `openUri(...)` 前提下完成内部流程验证。

### Step 09 - Real openUri Integration And open_resolved_route Tool

目标：

- 接入真实宿主 `openUri(...)`，打通 `open_resolved_route` 链路。

要做的事情：

1. 在 App 层实现真实 `HostRouteInvoker`。
2. 将 `HostRouteInvoker` 注入 `AndroidRouteRuntime`。
3. 实现 `open_resolved_route` 对应的 tool executor。
4. 注册 `open_resolved_route` 到 `AndroidToolManager`。
5. 增加 opener 调试入口，验证：
   - 宿主页直接跳转
   - 容器页收起后跳转
   - 前台未稳定失败
   - `openUri(...)` 异常失败

验收标准：

- `open_resolved_route` 能通过真实 `openUri(...)` 完成跳转调用。
- 容器页前置时序生效。
- 调试入口能观察 success / failure 和错误码分支。

### Step 10 - Validation, Integration, And Runtime Replacement

目标：

- 完成 route tool 的最小联调验证，并准备后续从 mock 过渡到真实 bridge。

要做的事情：

1. 做一次主链路联调，验证：
   - skill
   - `resolve_route`
   - `open_resolved_route`
   的真实调用路径。
2. 补齐最小测试和手工验证记录。
3. 确认调试入口和宿主页真实入口都可稳定复现。
4. 评估并开始逐步替换：
   - mock `NativeRouteBridge`
   - mock `MiniAppRouteBridge`
   为真实实现。
5. 判断是否需要新增正式文档步骤。

验收标准：

- route 主链路能在真实会话中走通到跳转动作。
- mock / real 替换边界保持稳定，不影响 runtime contract。
- 可以据此决定是否进入下一阶段或开始文档固化。
