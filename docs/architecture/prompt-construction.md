# Prompt 构建与工具展示

本文档说明当前 Android Agent 的 system prompt 是如何拼装的，以及为什么要把工具信息渲染成更接近“路由卡片”的样式。

目标不是追求好看，而是让模型更快看懂：

- 当前有哪些全局规则
- 当前有哪些业务工具和 UI 工具
- 这次用户意图更像业务调用，还是更像视觉 / UI 操作

## Prompt 组装顺序

当前 `PromptBuilder` 会按下面顺序组装 system prompt：

```text
1. SOUL.md
2. USER.md
3. AGENTS.md
4. TOOLS.md
5. skills/ 中的 always skill 与 skill summary
6. Memory summary
7. Available Tools
8. Runtime Information
```

对应代码见：

- `agent-core/src/main/cpp/src/core/prompt_builder.cpp`
- `agent-core/src/main/assets/workspace/SOUL.md`
- `agent-core/src/main/assets/workspace/AGENTS.md`
- `agent-core/src/main/assets/workspace/TOOLS.md`

## 为什么要把工具展示做得更形象

如果工具展示只剩下“工具名 + 参数列表”，模型虽然能调用工具，但不容易在调用前快速判断：

- 这是业务工具还是 UI 工具
- 该工具解决的是哪类目标
- 哪些参数必须先有
- 哪些字段存在明确枚举范围

这会直接影响意图路由：

- 容易硬选一个不匹配的业务工具
- 容易忽略 `run_shortcut.shortcut` 里真正可选的业务能力
- 容易在边界场景下过早或过晚地考虑视觉链路

因此当前展示改成了更接近“路由卡片”的样式。

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

## 一个更直观的例子

以 `run_shortcut` 为例，当前 prompt 中它更接近下面这种阅读体验：

```text
## run_shortcut
Route role: 运行宿主 App 已注册的业务 shortcut。适用于联系人、消息等业务能力。不要用这个通道做屏幕坐标点击或滑动，这类手势应使用 android_gesture_tool。

Input shape:
- Required first: shortcut, args
- shortcut: string (required) - 要调用的业务 shortcut 名称，只能从 enum 列表中选择。优先依据 skill 指导选择
  Choose from: search_contacts, send_im_message
- args: object (required) - args 的字段结构由所选 shortcut 决定
  Nested shape is defined by the selected shortcut or described above.
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

## 这和“降级到视觉”有什么关系

这一层做的是“调用前判断”，不是“调用后裁定”。

它的价值在于：

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

## 真实边界

当前这套改动只提升“看懂 prompt 的能力”，还没有解决下面这些问题：

- 业务工具尚未统一返回 `business_capability_not_supported`
- 业务工具尚未统一返回 `business_target_not_accessible`
- 视觉链路虽然已具备真实 in-process 执行，但默认 runtime 仍处于从语义调用向事件注入迁移的过程中

所以这份展示优化的定位是：

- 提高调用前路由判断质量
- 降低模型误选业务工具的概率
- 为后续结构化失败协议和视觉链路打基础
