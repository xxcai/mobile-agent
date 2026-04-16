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
