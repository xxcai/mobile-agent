# Prompt 构建与工具展示

本文档说明当前 Android Agent 的 system prompt 如何组装，以及顶层 prompt、skills summary 和 tool schema 各自承担什么职责。

目标不是追求“文案更多”，而是减少重复规则和多真相源，让模型更快看懂：

- 当前有哪些全局规则
- skills 应该如何发现和读取
- 哪些能力是可执行工具，哪些只是 workflow 指引
- Memory section 当前真实注入的是什么

## Prompt 组装顺序

当前 `PromptBuilder` 会按下面顺序组装 system prompt：

```text
1. SOUL.md
2. USER.md
3. AGENTS.md
4. TOOLS.md
5. skills/ 中的 always skill 与 skill summary
6. Session memory summary
7. Available Tools
8. Runtime Information
```

对应代码见：

- `agent-core/src/main/cpp/src/core/prompt_builder.cpp`
- `agent-core/src/main/assets/workspace/SOUL.md`
- `agent-core/src/main/assets/workspace/AGENTS.md`
- `agent-core/src/main/assets/workspace/TOOLS.md`

## 顶层分层

当前 prompt 同时依赖三层信息，但三层职责已经收敛：

- `SOUL.md`
  - 只负责身份、价值观、表达风格和“skills 不是 callable function / tools 才是 callable function”的基础认知
- `AGENTS.md`
  - 负责行为原则、路由原则、turn scope、memory 使用边界和高层错误处理
- `TOOLS.md`
  - 负责工具侧约束、失败语义、view/gesture 执行协议

在这之上，prompt 还依赖两类运行时拼接内容：

- `skills summary`
  - 负责告诉模型有哪些 workflow skill，以及命中 skill 时先读 `SKILL.md`
- tool schema
  - 负责告诉模型有哪些可执行通道，以及每个通道的参数协议

这次治理后的关键边界是：

- skills summary 主导“何时读 skill”和“参数不清时先 `describe_shortcut`”
- `agent_shared` 常驻 skill 保留“skill 不是 shortcut”的共享规程
- `run_shortcut` / `describe_shortcut` schema 只描述自身功能，不再重复大段治理文案

## 当前工具展示结构

每个工具现在按下面结构出现在 prompt 中：

```text
## <tool_name>
Route role: <tool description>

Input shape:
Required first: ...
- <field>: <type> - <description>
  Choose from: ...
  Default value: ...
  Nested shape:
  - <nested_field>: <type> - <description>
```

这里有几个关键点：

- `Route role`
  - 先让模型知道这个工具是干什么的，不是立刻陷入参数细节
- `Required first`
  - 强调调用前的最小前置条件
- `Choose from`
  - 对枚举参数，直接把候选项展开，减少模型漏看 enum 的概率
- `Nested shape`
  - 对 object 参数继续展开，避免模型误以为 object 没有内部结构

## 当前 Skills 展示

`PromptBuilder` 现在会先拼 always skills，再拼 on-demand skills summary：

- `# Active Skills`
  - 渲染 `always: true` 的 skill 全文，例如 `agent_shared`
- `# Available Skills`
  - 渲染 skills summary，作为 on-demand 发现层

当前 `# Available Skills` 的摘要形式接近：

```text
The following skills extend your capabilities. To use a skill, read its SKILL.md file using read_file tool. When a request clearly matches a skill, read that skill before calling any same-named shortcut. Use workspace-relative paths like skills/<skill_name>/SKILL.md, not absolute paths. Shortcuts are executable actions: inspect them with describe_shortcut("<shortcut_name>") and execute them with run_shortcut.

<skills>
When a request matches a skill, read its SKILL.md first.
Skill names are not shortcut names. Never pass them to run_shortcut or describe_shortcut.
If a shortcut's parameters are unclear, call describe_shortcut before run_shortcut.

- **agent_shared**: Agent 共享规则。提供所有业务 skill 共用的执行规程，包括 skill 与 shortcut 的区别、读取 skill 和 reference 的顺序、路径规则、完成态判断和通用 fallback 约束。
- **route_navigator**: 页面跳转助手。帮助 Agent 解析用户想去的页面、原生模块或 we码（WeCode / 微码）入口，并在目标明确后打开对应页面。
</skills>
```

Android 平台下，skills summary 不再输出 skill availability / requires 尾注，因为当前 builtin skills 不依赖这类环境约束。非 Android 平台仍保留原有约束判定与输出。

## 一个更直观的例子

以 `run_shortcut` 为例，当前 prompt 中它更接近下面这种阅读体验：

```text
## run_shortcut
Route role: 运行已注册的 shortcut 原子动作。协议固定为 {"shortcut":"名称","args":{...}}。

Input shape:
- Required first: shortcut, args
- shortcut: string (required) - 要运行的 shortcut 名称。
- args: object (required) - 传给 shortcut 的 JSON 参数对象，字段结构由目标 shortcut 定义决定。
```

而 `android_gesture_tool` 会更像：

```text
## android_gesture_tool
Route role: 执行当前宿主页面内的 Android UI 手势。适合在先看页面后，完成点击和滚动等动作。不要用这个通道搜索联系人、发送消息、读取剪贴板或调用宿主 App 的业务工具。

Input shape:
Required first: action
- action: string (required) - 手势动作类型
  Choose from: tap, swipe
- x: integer - 点击目标的 X 坐标
- y: integer - 点击目标的 Y 坐标
- observation: object - 当前回合页面快照引用信息，页面元素任务优先填写
- direction: string - 滚动方向，仅 action=swipe 时使用
- scope: string - 滚动作用域，仅 action=swipe 时使用
- amount: string - 滚动幅度，仅 action=swipe 时使用
- duration: integer - 滚动后的稳定等待时间
```

## Memory Section

当前 `Memory` section 只注入 active session 的最新 summary，不再声称固定加载某个 `MEMORY.md` 文件。

对应代码路径：

- `PromptBuilder::build_full()`
- `PromptBuilder::build_memory_section(session_id)`

当前行为可以概括为：

```text
session_id -> get_latest_summary(session_id) -> # Memory / ## Session Memory
```

如果当前 session 没有 summary，这个 section 可以为空。

## 这和“降级到视觉”有什么关系

这一层做的是“调用前判断”，不是“调用后裁定”。

它的价值在于：

- 当用户意图明显命中某个 workflow skill 时，模型更容易先读 skill，再决定 shortcut
- 当用户意图明显落在 `run_shortcut.shortcut` 的候选能力里时，模型更容易先走业务通道
- 当用户意图明显超出这些业务能力，且更像页面元素选择、坐标点击、列表滑动时，模型更容易意识到这不是现有业务工具能直接完成的目标
- 这样可以减少“先乱调一个业务工具再说”的情况

但这还不是最终裁定。最终是否允许从业务降级到视觉，仍应以结构化工具结果为准。

在引入 `android_view_context_tool` 之后，推荐的顺序会更清晰：

- 业务工具：适合可直接表达的 app 级动作
- 视图感知通道：适合先理解当前界面结构的任务
- 手势通道：适合在目标已明确后执行点击或滑动

对于页面元素类任务，当前推荐进一步理解成：

```text
android_view_context_tool -> android_gesture_tool(observation-bound)
```

也就是：

1. 先拿当前页面 observation
2. 再把 `snapshotId`、目标节点索引、目标 bounds 等引用信息带进 gesture
3. 最后再执行 tap / swipe

例如在 mock 聊天页里：

- `点击张三`
  - 先看聊天列表页
  - 再引用 observation 里的 `张三` 节点
  - 再进入 gesture

- `点击发送`
  - 先看聊天详情页
  - 再引用 observation 里的 `发送` 节点
  - 再进入 gesture

这样做的目的不是“让参数变复杂”，而是让执行层知道：

- 这次动作引用的是哪一次页面观察
- 点的是观察结果里的哪个节点
- 这个节点当时的 bounds 是什么

更完整的协议说明见：

- [Observation-Bound Execution 协议说明](../protocols/observation-bound-execution.md)

## 本轮已解决

- 去除了 `PromptBuilder::build_full()` 的重复实现。
- Memory section 改为 session-aware 注入，不再硬编码 `"default"`。
- `SOUL.md` / `AGENTS.md` / `TOOLS.md` 的职责重新分层。
- skills summary 与 `run_shortcut` / `describe_shortcut` 的重复引导语已收敛。
- Android 端 skills summary 已去掉对当前 builtin skills 无增量的 availability 尾注。

## 当前真实边界

当前这套改动主要提升“看懂 prompt 与 skill / tool 边界的能力”，还没有解决下面这些问题：

- 业务工具尚未统一返回 `business_capability_not_supported`
- 业务工具尚未统一返回 `business_target_not_accessible`
- 视觉链路虽然已具备真实 in-process 执行，但默认 runtime 仍处于从语义调用向事件注入迁移的过程中

所以这一轮 prompt 治理的定位是：

- 提高调用前路由判断质量
- 降低模型误选业务工具的概率
- 为后续结构化失败协议和视觉链路打基础
