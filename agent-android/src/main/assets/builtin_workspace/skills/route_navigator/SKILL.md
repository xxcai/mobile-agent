---
name: route_navigator
description: 页面跳转助手。帮助 Agent 解析用户想去的页面、原生模块或 we码（WeCode / 微码）入口，并在目标明确后打开对应页面。当用户要求打开某个页面、进入某个入口、跳到某个模块、we码或业务页面时使用。
always: false
---

# Route Navigator

**CRITICAL — 在完成目标判断前，不要直接调用 `resolve_route` 或 `open_resolved_route`。**
**CRITICAL — `route_navigator` 是 skill，不是 shortcut；本 skill 只允许使用 `resolve_route`、`open_resolved_route`、`resolve_candidate_selection` 这 3 个 shortcut。**

当用户的目标是“进入某个页面或业务入口”时，使用此 skill。

## 使用原则

- 当前 skill 推荐的 shortcut：
  - `resolve_route`
  - `open_resolved_route`
  - `resolve_candidate_selection`
- 先解析，再打开；不要跳过解析步骤直接猜测目标 URI
- 当用户明确说了 `we码`、`WeCode`、`微码` 时，调用 `resolve_route` 必须显式传 `targetTypeHint: "wecode"`
- 当用户明确说了“原生页面”或明确排除 we码 时，调用 `resolve_route` 必须显式传 `targetTypeHint: "native"`
- 候选确认回合仍然继续使用本 skill，不要把确认回合改写成新的搜索回合
- 当用户要打开一个未知页面时，第一步仍然是 `resolve_route`，不要先猜页面名去 `describe_shortcut`，也不要先用 `grep_files` 搜索页面词
- 当页面打开还带有 route 参数时，统一放在 `routeArgs` 中，不要放在 `open_resolved_route` 顶层
- 当 manifest 声明某个参数需要编码时，必须在对应 `routeArgs.<name>` 中显式提供 `encoded: true|false`
- 不要为了完成页面跳转而绕去 `android_gesture_tool` 模拟点击页面入口

## 触发条件

以下表达通常应触发本 skill：

- “打开报销入口”
- “进入创建群聊页面”
- “打开费控 we码”
- “打开原生页面里的修改密码”
- 上一轮刚列出 route 候选后，用户回复：
  - “第一个”
  - “第二个”
  - “前者”
  - “后者”
  - “login 那个”
  - “账号安全那个”
- 上一轮刚做过候选差异追问后，用户回复：
  - “登录”
  - “账号安全”
  - “报销”

以下情况通常不应使用本 skill：

- 用户已经在目标页面，只是要求点击、滚动或输入
- 用户要求总结页面内容，而不是打开页面
- 用户要求发送消息、搜索联系人、总结动态等非跳转任务

## 推荐 Shortcut

### `resolve_route`

适用场景：

- 用户表达的是“想去哪里”，但还没有明确 target
- 需要根据 URI、原生模块、we码名称或关键词解析目标

调用格式：

```json
{
  "shortcut": "resolve_route",
  "args": {
    "targetTypeHint": "wecode",
    "weCodeName": "报销",
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
    "targetType": "wecode",
    "uri": "h5://1001001",
    "title": "费控报销"
  }
}
```

### `resolve_candidate_selection`

适用场景：

- 上一轮刚返回 route `candidates`
- 当前用户在确认上一轮候选，例如：
  - `第一个`
  - `前者`
  - `login 那个`
  - `账号安全`

调用格式：

```json
{
  "shortcut": "resolve_candidate_selection",
  "args": {
    "selectionText": "第一个",
    "domain": "route"
  }
}
```

## 工作流程

### 步骤 1：提取跳转线索

先从用户请求中提取跳转线索，例如：

- 精确 URI
- 原生模块名
- we码名称
- 页面关键字

提取后先判断用户是否给了明确类型偏好：

- 明确提到 `we码`、`WeCode`、`微码`：
  - 传 `targetTypeHint: "wecode"`
- 明确提到“原生页面”“原生模块”：
  - 传 `targetTypeHint: "native"`
- 没有明确类型偏好：
  - `targetTypeHint` 可不传

如果用户请求里完全没有足够线索，不要直接调用 `open_resolved_route`，先追问要打开哪个页面或入口。

### 步骤 2：解析目标

先调用 `resolve_route`。

### 步骤 3：处理解析结果

#### `resolved`

- 直接使用返回的 `targetType`、`uri`、`title`
- 进入打开步骤

#### `candidates`

- 如果用户原话已存在能唯一映射到单个候选的区分词，可直接代选
- 否则必须追问用户确认
- 在无法安全代选之前，不要调用 `open_resolved_route`
- 不要发明新的 shortcut，也不要脱离当前候选上下文重新猜测目标

用户确认后的处理：

- 如果用户明确说“第一个 / 第二个 / 前者 / 后者 / login 那个 / settings 那个 / 登录 / 账号安全”
- 先调用 `resolve_candidate_selection`
- 如果成功：
  - 直接使用返回 `payload` 中的 `targetType`、`uri`、`title`
  - 然后立刻调用 `open_resolved_route`
- 如果失败且错误明确提示参数格式不对：
  - 先按返回的 `expectedArgs` / `argsExample` 修正参数后重试一次
- 如果失败且不是参数格式问题：
  - 只允许解释当前候选状态不可用，或在用户明确改变目标后重新走新的 route 解析
- 不要在 `resolve_candidate_selection` 失败后自行猜测目标 `uri`
- 不要把候选确认语句解释成 skill 名、页面名或模块名 shortcut
- 不要把候选确认回合直接改写成新的 `resolve_route` 回合，除非用户明确改变了目标

#### `insufficient_hint`

- 说明当前线索不足
- 追问更具体的页面名、模块名或 we码名
- 不要继续调用 `open_resolved_route`

#### `not_found`

- 告诉用户当前未找到匹配目标
- 不要自动 rewrite，不要自动再次调用 `resolve_route`
- 根据当前失败上下文，建议用户补充模块词、功能词或常见别名
- 不要继续调用 `open_resolved_route`
- 不要在 `not_found` 后改为 `describe_shortcut(猜测的页面名)`
- 不要在 `not_found` 后改为 `grep_files` 搜索页面词
- 不要在 `not_found` 后切到 `android_view_context_tool` 试图“找页面”

### 步骤 4：打开目标

当且仅当以下条件同时满足时，才能调用 `open_resolved_route`：

- 已经有明确的 `targetType`
- 已经有明确的 `uri`
- 已经有明确的 `title`

如果页面还需要 route 参数：

- 先根据用户输入整理出原始参数值
- 再放进 `routeArgs`
- 不要把 route 参数直接写到顶层 `args`

## 错误处理

### `invalid_target`

- 重新检查 `resolve_route` 的输出
- 重新检查 `open_resolved_route` 是否误把 route 参数放到了顶层
- 重新检查需要编码的参数是否放在 `routeArgs` 中，并显式提供了 `encoded`

### `execution_failed`

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
2. 得到 `resolved`
3. 调用 `open_resolved_route`

### 示例 2：多个候选，先追问

用户：

```text
打开修改密码页面
```

Agent：

1. 调用 `resolve_route`
2. 得到多个候选
3. 追问用户要“登录里的”还是“账号安全里的”

### 示例 3：候选确认后直接打开

上一轮 Agent 已返回多个候选。

用户：

```text
第一个
```

Agent：

1. 调用 `resolve_candidate_selection`
2. 成功后直接调用 `open_resolved_route`

### 示例 4：带 route 参数的打开

用户：

```text
打开创建群聊页面，source 用 agent card
```

Agent：

1. 调用 `resolve_route`
2. 得到 `resolved`
3. 调用 `open_resolved_route`，并把参数放入 `routeArgs`

## 禁止事项

- 不要跳过 `resolve_route` 直接猜测 URI
- 不要在多个候选目标之间基于模糊词盲选
- 不要在线索不足时直接打开页面
- 不要发明未定义的 shortcut
- 不要把 `route_navigator`、`changePassword`、`createGroup` 这类 skill 名或页面名当成 shortcut 去试探
- 不要在 `candidates` 场景下脱离候选上下文自由猜新的 URI
- 不要在 `not_found` 后自动 rewrite 并再次调用 `resolve_route`
- 不要在 `not_found` 后改为 `describe_shortcut`、`grep_files` 或 `android_view_context_tool` 兜底找页面
- 不要把 route 参数直接塞到 `open_resolved_route.args` 顶层
- 不要在缺少 `encoded` 时假装已经满足参数契约
- 不要把打开失败描述成成功
