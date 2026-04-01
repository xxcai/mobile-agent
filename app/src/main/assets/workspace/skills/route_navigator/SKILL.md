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
- 如果上一轮刚给用户列出 route `candidates`，而用户这一轮回复“第一个 / 第二个 / 前者 / 后者 / login 那个 / settings 那个”这类确认语句，当前回合仍然继续使用本 skill
- 候选一旦被用户明确确认，直接用该候选的 `targetType + uri + title` 调用 `open_resolved_route`
- 当页面打开还带有 route 参数时，先查看 `open_resolved_route` 的定义，再按其 schema 组装参数
- route 参数一律放在 `routeArgs` 里，不要把 `source`、`payload`、`page`、`module` 这类页面参数直接放到 `open_resolved_route` 顶层
- 当 manifest 声明某个参数需要编码时，必须在对应 `routeArgs.<name>` 中显式提供 `encoded: true|false`
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
- 在上一轮刚列出 route 候选后，用户回复：
  - “第一个”
  - “第二个”
  - “前者”
  - “后者”
  - “login 那个”
  - “账号安全那个”

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
- 如页面还需要 route 参数，按 `routeArgs` 传入

调用格式：

```json
{
  "shortcut": "open_resolved_route",
  "args": {
    "targetType": "miniapp",
    "uri": "h5://1001001",
    "title": "费控报销",
    "routeArgs": {
      "someParam": {
        "value": "raw value",
        "encoded": false
      }
    }
  }
}
```

参数约束：

- `open_resolved_route` 顶层只放目标信息：
  - `targetType`
  - `uri`
  - `title`
  - 可选 `routeArgs`
- 如果没有 route 参数，不要伪造 `routeArgs`
- 如果有 route 参数：
  - 每个参数名都作为 `routeArgs` 的 key
  - 每项格式固定为 `{ "value": "...", "encoded": true|false }`
- 如果参数值是你刚根据用户自然语言整理出的原始值，通常传 `encoded: false`
- 如果用户明确给的是已经编码后的值，或你明确知道该值已经完成所需编码，才传 `encoded: true`
- 不确定 shortcut 细节时，先调用 `describe_shortcut("open_resolved_route")`，不要猜参数结构

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
- 用户确认之后，不要重新发明新的 shortcut 名，也不要脱离当前候选上下文重新猜测

用户确认后的处理：

- 如果用户明确说“第一个 / 第二个 / 前者 / 后者 / login 那个 / settings 那个”
- 直接把用户选择映射到上一轮候选列表中的一个目标
- 然后立刻调用 `open_resolved_route`
- 不要再调用其他不存在的 shortcut
- 不需要重新做一次无关的 route 解析，除非候选上下文已经丢失

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

如果页面还需要 route 参数：

- 先根据用户输入整理出原始参数值
- 再放进 `routeArgs`
- 不要把 route 参数直接写到顶层 `args`
- 若该参数需要编码：
  - 原始值传 `encoded: false`
  - 已编码值传 `encoded: true`

调用：

```json
{
  "shortcut": "open_resolved_route",
  "args": {
    "targetType": "已解析的目标类型",
    "uri": "已解析的目标 URI",
    "title": "已解析的目标标题",
    "routeArgs": {
      "参数名": {
        "value": "参数值",
        "encoded": false
      }
    }
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
- 重新检查 `open_resolved_route` 是否误把 route 参数放到了顶层
- 重新检查需要编码的参数是否放在 `routeArgs` 中，并显式提供了 `encoded`
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

### 示例 2：带 route 参数的 native 页面

用户：

```text
打开创建群聊页面，source 用 agent card
```

Agent：

1. 调用 `resolve_route`
2. 得到 `resolved`，target 为 `ui://myapp.im/createGroup`
3. 因为页面带 route 参数，先确认 `open_resolved_route` 的参数格式
4. 调用：

```json
{
  "shortcut": "open_resolved_route",
  "args": {
    "targetType": "native",
    "uri": "ui://myapp.im/createGroup",
    "title": "createGroup",
    "routeArgs": {
      "source": {
        "value": "agent card",
        "encoded": false
      }
    }
  }
}
```

5. 返回：

```text
已为您打开创建群聊页面。
```

### 示例 3：需要编码的结构化参数

用户：

```text
打开报销记录页面，payload 传 {"tab":"message"}
```

Agent：

1. 调用 `resolve_route`
2. 得到 `resolved`，target 为 `ui://myapp.expense/records`
3. 把用户给出的结构化负载整理成参数原始值
4. 调用：

```json
{
  "shortcut": "open_resolved_route",
  "args": {
    "targetType": "native",
    "uri": "ui://myapp.expense/records",
    "title": "records",
    "routeArgs": {
      "payload": {
        "value": "{\"tab\":\"message\"}",
        "encoded": false
      }
    }
  }
}
```

### 示例 4：多个候选，先确认

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

### 示例 5：用户确认候选后直接打开

上一轮 Agent 已返回：

```text
我找到了 2 个可能的目标：
1. changePassword（来自 login 模块）
2. changePassword（来自 settings 模块）

请问您要打开哪一个？
```

用户：

```text
第一个
```

Agent：

1. 把“第一个”映射到上一轮第 1 个候选：
   - `targetType = native`
   - `uri = ui://myapp.login/changePassword`
   - `title = changePassword`
2. 直接调用：

```json
{
  "shortcut": "open_resolved_route",
  "args": {
    "targetType": "native",
    "uri": "ui://myapp.login/changePassword",
    "title": "changePassword"
  }
}
```

3. 返回：

```text
已为您打开修改密码页面。
```

### 示例 6：线索不足，先追问

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
- 不要在候选确认回合发明不存在的 shortcut 名，例如 `open_schema`
- 不要把 route 参数直接塞到 `open_resolved_route.args` 顶层
- 不要在缺少 `encoded` 时假装已经满足了需要编码的参数契约
- 不要把打开失败描述成成功
- 不要为了进入页面绕去 `android_gesture_tool` 模拟点击页面入口
