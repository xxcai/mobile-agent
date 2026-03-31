---
name: route_navigator
description: 页面跳转助手。帮助 Agent 解析用户想去的页面、原生模块或小程序入口，并在目标明确后打开对应页面。当用户要求打开某个页面、进入某个入口、跳到某个模块、小程序或业务页面时使用。
emoji: "🧭"
always: false
---

# 页面跳转助手

**CRITICAL — 第一步 MUST 先用 `read_file` 读取并遵循 `skills/agent_shared/SKILL.md`。**
**CRITICAL — 在读完 shared 之前，不要调用 `run_shortcut("route_navigator")` 或 `describe_shortcut("route_navigator")`。**
**CRITICAL — 在完成目标判断前，不要直接调用 `resolve_route` 或 `open_resolved_route`。**

当用户的目标是“进入某个页面或业务入口”时，使用此 skill。

## 使用原则

- 优先使用 `run_shortcut`
- 当前 skill 推荐的 shortcut 只有两个：
  - `resolve_route`
  - `open_resolved_route`
- 先解析，再打开；不要跳过解析步骤直接猜测目标 URI
- 当解析结果是多个候选项时，必须让用户确认目标
- 当解析线索不足时，必须先追问，不要直接尝试打开
- 不要为了完成页面跳转而绕去 `android_gesture_tool` 模拟点击页面入口

## 触发条件

以下表达通常应触发本 skill：

- “打开报销入口”
- “进入创建群聊页面”
- “打开费控小程序”
- “跳到审批详情”
- “去云文档”
- “打开通讯录”
- “进入 IM 建群页面”

以下情况通常不应使用本 skill：

- 用户已经在目标页面，只是要求点击、滚动或输入
- 用户要求总结页面内容，而不是打开页面
- 用户要求发送消息、搜索联系人、总结动态等非跳转任务

## 推荐 Shortcut

### `resolve_route`

适用场景：

- 用户表达的是“想去哪里”，但还没有明确 target
- 需要根据 URI、原生模块、小程序名称或关键词解析目标
- 需要确认是 native 页面还是 miniapp

调用格式：

```json
{
  "shortcut": "resolve_route",
  "args": {
    "miniAppName": "报销",
    "keywords_csv": "报销,费用报销"
  }
}
```

### `open_resolved_route`

适用场景：

- 已经拿到明确的 `targetType`、`uri`、`title`
- 已完成候选确认
- 可以正式执行页面打开

调用格式：

```json
{
  "shortcut": "open_resolved_route",
  "args": {
    "targetType": "miniapp",
    "uri": "h5://1001001",
    "title": "费控报销"
  }
}
```

## 工作流程

### 步骤 1：提取跳转线索

先从用户请求中提取可能的跳转线索，例如：

- 精确 URI
- 原生模块名
- 小程序名称
- 页面关键字

如果用户请求里完全没有足够线索，不要直接调用 `open_resolved_route`，先追问要打开哪个页面或入口。

### 步骤 2：解析目标

先调用 `run_shortcut`，使用 `resolve_route`。

```json
{
  "shortcut": "resolve_route",
  "args": {
    "targetTypeHint": "可选",
    "uri": "可选",
    "nativeModule": "可选",
    "miniAppName": "可选",
    "keywords_csv": "可选，多个关键词用英文逗号分隔"
  }
}
```

### 步骤 3：处理解析结果

#### `resolved`

如果解析结果是 `resolved`：

- 直接进入打开步骤
- 使用返回的 `targetType`、`uri`、`title`

#### `candidates`

如果解析结果是 `candidates`：

- 列出候选目标
- 让用户明确选择
- 在用户确认之前，不要调用 `open_resolved_route`

推荐表达：

```text
我找到了 2 个可能的目标：
1. 费控报销（小程序）
2. 差旅报销（原生页面）

请问您要打开哪一个？
```

#### `insufficient_hint`

如果解析结果是 `insufficient_hint`：

- 说明当前线索不足
- 追问更具体的页面名、模块名或小程序名
- 不要继续调用 `open_resolved_route`

推荐表达：

```text
我还无法确定您要打开哪个入口。请再告诉我是哪个页面、模块或小程序。
```

#### `not_found`

如果解析结果是 `not_found`：

- 告诉用户当前未找到匹配目标
- 建议用户提供更准确名称
- 不要继续调用 `open_resolved_route`

### 步骤 4：打开目标

当且仅当以下条件同时满足时，才能调用 `open_resolved_route`：

- 已经有明确的 `targetType`
- 已经有明确的 `uri`
- 已经有明确的 `title`

调用：

```json
{
  "shortcut": "open_resolved_route",
  "args": {
    "targetType": "已解析的目标类型",
    "uri": "已解析的目标 URI",
    "title": "已解析的目标标题"
  }
}
```

## 结果处理

### 成功

如果打开成功：

- 简洁确认结果
- 不要重复返回过多内部字段

推荐表达：

```text
已为您打开费控报销。
```

### 失败

如果打开失败：

- 优先解释用户可理解的失败原因
- 不要假装已经打开成功
- 给出下一步建议

## 错误处理

### `invalid_target`

含义：

- 解析结果不完整，或打开参数不合法

处理：

- 重新检查 `resolve_route` 的输出
- 必要时重新解析，不要直接盲目重试

### `execution_failed`

含义：

- 解析或打开过程中出现执行异常

处理：

- 给出简洁失败说明
- 建议用户稍后重试或提供更明确线索

## 示例

### 示例 1：单一目标，直接打开

用户：

```text
打开费控报销
```

Agent：

1. 调用 `resolve_route`
2. 得到 `resolved`，target 为 `费控报销`
3. 调用 `open_resolved_route`
4. 返回：

```text
已为您打开费控报销。
```

### 示例 2：多个候选，先确认

用户：

```text
打开报销入口
```

Agent：

1. 调用 `resolve_route`
2. 得到 `candidates`
3. 返回：

```text
我找到了 2 个可能的目标：
1. 费控报销（小程序）
2. 差旅报销（原生页面）

请问您要打开哪一个？
```

### 示例 3：线索不足，先追问

用户：

```text
帮我打开那个入口
```

Agent：

1. 不直接打开
2. 追问：

```text
请问您想打开哪个页面、模块或小程序？
```

## 禁止事项

- 不要跳过 `resolve_route` 直接猜测 URI
- 不要在多个候选目标之间自行选择
- 不要在线索不足时直接打开页面
- 不要把打开失败描述成成功
- 不要为了进入页面绕去 `android_gesture_tool` 模拟点击页面入口
