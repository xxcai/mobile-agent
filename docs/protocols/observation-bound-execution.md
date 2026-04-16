# Observation-Bound Execution 协议说明

本文档说明当前 Android Agent 在页面元素类任务上的协议设计。

目标不是再解释一遍“有几个字段”，而是明确下面这件事：

- Agent 先怎么看到页面
- 看到页面后，怎么把“我要点这个元素”表达给执行层
- 为什么这比直接猜一组 `(x, y)` 更稳

当前文档以仓库里的 mock 聊天页面为例：

- 聊天列表页：`MainActivity`
- 聊天详情页：`ChatDetailActivity`

## 一句话理解

不要让 `android_gesture_tool` 自己猜“该点哪里”。

要先用 `android_view_context_tool` 拿到当前页面快照，再明确告诉 `android_gesture_tool`：

- 我引用的是哪一次页面观察
- 我要点的是观察结果里的哪个节点
- 这个节点当时的 bounds 是什么

也就是：

```text
先看清 -> 再引用 -> 再执行
```

## 为什么不能只靠坐标

如果模型只输出：

```json
{"action":"tap","x":120,"y":360}
```

那执行层只知道“点这个位置”，并不知道：

- 这个坐标是从哪次页面观察里来的
- 这个坐标本来想点的是“张三”还是“发送”
- 页面如果已经变化，这个坐标还是否有效

这就是“自由坐标执行”。

它的问题不是不能工作，而是：

- 很难调试
- 很难做运行时校验
- 很难在页面变化时判断风险

## 当前协议分成两层

### 第 1 层：`android_view_context_tool`

这个通道负责“看页面”，当前优先返回规范化 observation：

- `pageSummary`
- `screenElements`
- `uiTree`
- `quality`
- `raw`

同时继续保留 legacy/source-specific 字段：

- `nativeViewXml`
- `snapshotId`
- `snapshotCreatedAtEpochMs`
- `snapshotScope`
- `snapshotCurrentTurnOnly`
- `activityClassName`
- `targetHint`

其中最关键的是 `snapshotId`。

它表示：

> “这是这一次页面观察的标识。”

后面的执行如果要引用这次观察，就必须把这个 `snapshotId` 带上。

### 第 2 层：`android_gesture_tool`

这个通道负责“执行动作”。

当前 `android_gesture_tool` 以 `observation` 作为页面元素动作的主要引用信息：

```json
{
  "action": "tap",
  "x": 120,
  "y": 360,
  "observation": {
    "snapshotId": "obs_xxx",
    "targetNodeIndex": 18,
    "targetDescriptor": "张三",
    "referencedBounds": "[42,438][1038,564]"
  }
}
```

对 `tap` 来说，`x/y` 仍可作为受控 fallback，但推荐主路径始终依赖 `observation`。
对 `swipe` 来说，正式协议已经不再要求模型直接生成 `startX / startY / endX / endY`，而是由 runtime 接收高层滚动意图后自行计算。

## 4 个 observation 字段分别是什么意思

### `snapshotId`

表示：

> “这次点击，引用的是哪一次 observation。”

没有它，执行层就无法确认“这次点击”和“那次页面观察”是不是同一件事。

### `targetDescriptor`

表示：

> “人类可读的目标描述。”

例如：

- `张三`
- `发送`
- `项目同步群`

它的主要价值是：

- 便于调试
- 便于日志排查
- 便于模型和人理解当前动作的目标语义

### `targetNodeIndex`

表示：

> “目标节点在那份 observation 里的索引。”

注意：

- 它不是全局稳定 id
- 它只在当前那份 `nativeViewXml` 里有意义

你可以把它理解成：

> “在这张页面快照里，我说的是第 18 个节点，不是别的节点。”

### `referencedBounds`

表示：

> “这个目标节点在 observation 里对应的 bounds。”

例如：

```text
[42,438][1038,564]
```

这是当前从页面观察走向手势执行的几何桥梁。

当前 runtime 的默认做法是：

1. 解析 `referencedBounds`
2. 取中心点
3. 再执行点击

## 用聊天列表页举例

场景：用户想点开“张三”的会话。

### 第一步：先看页面

`android_view_context_tool` 返回：

```json
{
  "success": true,
  "activityClassName": "com.hh.agent.MainActivity",
  "snapshotId": "obs_abc123",
  "nativeViewXml": "<hierarchy>...</hierarchy>"
}
```

此时执行层知道：

- 当前页面是聊天列表页
- 这次观察的标识是 `obs_abc123`

### 第二步：从 observation 里锁定目标节点

当前推荐先从 canonical `screenElements` 中匹配文本为 `张三` 的候选，并提取：

- `targetNodeIndex`
- `referencedBounds`

如果 canonical 字段缺失或不足，再回退读取 legacy `hybridObservation` / `nativeViewXml`。

例如：

```json
{
  "snapshotId": "obs_abc123",
  "targetNodeIndex": 18,
  "targetDescriptor": "张三",
  "referencedBounds": "[42,438][1038,564]"
}
```

### 第三步：再调用 gesture

```json
{
  "action": "tap",
  "x": 200,
  "y": 420,
  "observation": {
    "snapshotId": "obs_abc123",
    "targetNodeIndex": 18,
    "targetDescriptor": "张三",
    "referencedBounds": "[42,438][1038,564]"
  }
}
```

这里最重要的不是 `x=200,y=420`，而是：

- 我引用的是 `obs_abc123`
- 我点的是这个快照里的第 18 个节点
- 这个节点的 bounds 是 `[42,438][1038,564]`

这样日志和运行时都能看懂“这次点击到底在点什么”。

## 用聊天详情页举例

场景：用户进入 `ChatDetailActivity` 后，想点底部“发送”按钮。

同样是三步：

1. `android_view_context_tool` 获取详情页 observation
2. 从 `nativeViewXml` 匹配文本为 `发送` 的节点
3. `android_gesture_tool` 带着 `snapshotId + targetNodeIndex + referencedBounds` 执行 tap

这时日志能明确表达：

- 当前页面是 `com.hh.agent.ChatDetailActivity`
- 当前点击目标是 `发送`
- 当前引用的是本回合详情页 observation，而不是上一页的老坐标

## 这套协议的 3 个强度等级

### 等级 1：弱引用

只带：

- `snapshotId`
- `targetDescriptor`

这表示：

> “我知道我引用的是哪次观察，也知道大概要点谁。”

### 等级 2：中引用

再加：

- `targetNodeIndex`

这表示：

> “我知道这次观察里的具体节点是谁。”

### 等级 3：强引用

再加：

- `referencedBounds`

这表示：

> “我已经拿到了这个节点在当次 observation 里的几何位置，可以据此执行。”

当前 mock 聊天页面的 probe，已经能跑到等级 3。

## 当前已经做到什么

- `android_view_context_tool` 已返回真实宿主页面 `nativeViewXml`
- observation 结果已带 `snapshotId`
- `android_gesture_tool` 已支持 `observation` 参数对象
- mock 聊天列表页和详情页都已能从真实 observation 中提取：
  - `targetNodeIndex`
  - `referencedBounds`
- `tap` 已能基于 observation 做真实 in-process 执行
- `swipe` 已切成高层滚动意图协议，由 runtime 计算滚动参数
- 当前测试页和 mock 页面都能验证：
  - `snapshotId` 在 view context 和 gesture 之间保持一致
  - observation 可以驱动真实点击和滚动

## 当前还没做到什么

- 默认 runtime 仍未完全切到统一的真实事件注入
- `tap` 仍主要依赖 bounds 命中后 `performClick / performItemClick`
- `swipe` 仍主要依赖容器识别后的滚动实现
- `long_press / double_tap` 还没有进入正式 gesture 协议
- LLM 自动调用链路还没有系统化验收完整的事件注入主路径

所以当前状态可以概括成：

> 协议已经 observation-bound，执行主链也已真实可用，但 runtime 还处在“语义调用 -> 事件注入”迁移中。

## 推荐的后续实现顺序

### 1. 让默认 runtime 切到 Activity 事件注入

最小实现建议：

- 当 `action=tap` 且存在 `observation.referencedBounds` 时
- 优先从 `referencedBounds` 计算点击中心点
- 生成 `ACTION_DOWN / ACTION_UP`
- 通过当前前台 Activity 的 `dispatchTouchEvent` 注入

对于 `swipe`：

- 继续接收 `direction / scope / amount`
- 由 runtime 生成 `DOWN / MOVE* / UP`
- 同样通过 Activity 的 `dispatchTouchEvent` 注入

### 2. 再扩到更多真实手势类型

- 把 `long_press / double_tap` 纳入统一事件序列构造层
- 让调试 demo 与正式 runtime 尽量共用底层注入逻辑

### 3. 保持 runtime gating

也就是：

- 页面元素类任务如果没有当前回合 observation
- 继续拒绝直接 gesture 或只允许受控 fallback

### 4. 最后再把这套规则完全前推到模型链路

也就是让模型默认走：

```text
先 view context -> 再 gesture(observation-bound)
```

而不是直接猜坐标。
