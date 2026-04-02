---
name: route_navigator
description: 页面跳转助手。帮助 Agent 解析用户想去的页面、原生模块或 we码（WeCode / 微码）入口，并在目标明确后打开对应页面。当用户要求打开某个页面、进入某个入口、跳到某个模块、we码或业务页面时使用。
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
- 当用户明确说了 `we码`、`WeCode`、`微码` 时，调用 `resolve_route` 必须显式传 `targetTypeHint: "wecode"`，不要只传泛关键词
- 当用户明确说了“原生页面”或明确排除 we码 时，调用 `resolve_route` 必须显式传 `targetTypeHint: "native"`
- 先解析，再打开；不要跳过解析步骤直接猜测目标 URI
- 当 `resolve_route` 返回 `candidates` 时，先尝试做受控判定：只有存在显式区分词且能唯一映射到一个候选，才允许直接代选
- 如果上一轮刚给用户列出 route `candidates`，而用户这一轮回复“第一个 / 第二个 / 前者 / 后者 / login 那个 / settings 那个”这类确认语句，当前回合仍然继续使用本 skill
- 如果上一轮刚做过围绕候选差异的定向追问，而用户这一轮只回复差异词，例如“登录 / 账号安全 / 设置 / IM / 报销”，当前回合也继续使用本 skill
- 候选一旦被用户明确确认，直接用该候选的 `targetType + uri + title` 调用 `open_resolved_route`
- 如果 `candidates` 无法安全代选，必须给出围绕候选差异的定向追问，不要只把完整候选列表机械抛回给用户
- 当页面打开还带有 route 参数时，先查看 `open_resolved_route` 的定义，再按其 schema 组装参数
- route 参数一律放在 `routeArgs` 里，不要把 `source`、`payload`、`page`、`module` 这类页面参数直接放到 `open_resolved_route` 顶层
- 当 manifest 声明某个参数需要编码时，必须在对应 `routeArgs.<name>` 中显式提供 `encoded: true|false`
- 当解析结果是多个候选项时：
  - 若用户原话存在显式区分词，且能唯一映射到一个候选，可直接代选
  - 否则必须追问用户确认
- 当解析线索不足时，必须先追问，不要直接尝试打开
- 不要为了完成页面跳转而绕去 `android_gesture_tool` 模拟点击页面入口

## 触发条件

以下表达通常应触发本 skill：

- “打开报销入口”
- “进入创建群聊页面”
- “打开费控 we码”
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
- 在上一轮刚做过候选差异追问后，用户回复：
  - “登录”
  - “账号安全”
  - “设置”
  - “IM”
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
- 需要确认是 native 页面还是 we码

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
- 如页面还需要 route 参数，按 `routeArgs` 传入

调用格式：

```json
{
  "shortcut": "open_resolved_route",
  "args": {
    "targetType": "wecode",
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
- we码名称
- 页面关键字

提取后先判断用户是否已经给了明确类型偏好：

- 明确提到 `we码`、`WeCode`、`微码`：
  - 传 `targetTypeHint: "wecode"`
- 明确提到“原生页面”“原生模块”：
  - 传 `targetTypeHint: "native"`
- 没有明确类型偏好：
  - `targetTypeHint` 可不传

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
    "weCodeName": "可选",
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

- 先检查用户原话里是否有显式区分词
- 只有当该区分词能唯一映射到一个候选时，才允许直接代选
- 若不存在显式区分词，或仍无法唯一映射，必须追问用户
- 在无法安全代选之前，不要调用 `open_resolved_route`
- 不要重新发明新的 shortcut 名，也不要脱离当前候选上下文重新猜测

显式区分词当前只允许来自以下几类：

- `module` 词，例如：`登录`、`账号安全`、`审批`
- 业务域词，例如：`IM`、`报销`、`通讯录`
- 入口来源词，例如：`首页入口`、`消息入口`
- 页面限定短语，例如：`登录里的`、`设置里的`、`账号安全那个`

以下词默认不构成可直接代选依据：

- 泛词，例如：`详情`、`设置`、`记录`、`密码`
- 同时与多个候选都匹配的模糊词
- 任何无法唯一映射到单个候选的词

用户确认后的处理：

- 如果用户明确说“第一个 / 第二个 / 前者 / 后者 / login 那个 / settings 那个”
- 直接把用户选择映射到上一轮候选列表中的一个目标
- 然后立刻调用 `open_resolved_route`
- 不要再调用其他不存在的 shortcut
- 不需要重新做一次无关的 route 解析，除非候选上下文已经丢失

追问后差异词确认的处理：

- 如果上一轮已经明确围绕候选差异追问，例如：
  - `登录里的修改密码，还是账号安全里的修改密码？`
- 而用户这一轮只回复差异词，例如：
  - `登录`
  - `账号安全`
  - `设置`
- 直接把该差异词映射到上一轮候选中的一个目标
- 然后立刻调用 `open_resolved_route`
- 不要再试探任何新的 shortcut 名，例如 `changePassword`

自动代选的处理：

- 如果用户原话已经明确说出区分词，例如：
  - `登录里的修改密码页面`
  - `账号安全那个修改密码页面`
- 并且该线索只对应一个 candidate
- 直接选择该 candidate，并调用 `open_resolved_route`

定向追问的处理：

- 如果候选之间的差异是 `module`，优先围绕 `module` 追问
- 如果候选之间的差异是业务域，优先围绕业务域追问
- 追问应直接指出区分维度，不要只说“请问您要哪一个”
- 追问应先压缩候选，只保留真正需要用户判断的差异，不要机械列出全部候选
- 如果同一 module 下还存在功能差异，先问更高优先级差异；必要时下一轮再继续细分

定向追问的优先级：

1. 先问 `module` / 业务域差异
2. 再问页面功能差异
3. 最后才列具体候选名称

推荐表达：

```text
我找到了 2 个可能的目标：
1. 费控报销（we码）
2. 差旅报销（原生页面）

请问您要打开哪一个？
```

更推荐的定向追问表达：

```text
我找到了两个相关页面：一个是登录里的修改密码，另一个是账号安全里的修改密码。您要打开哪一个？
```

更不推荐的表达：

```text
我找到了 3 个相关页面：
1. ...
2. ...
3. ...

请问您要打开哪一个？
```

#### `insufficient_hint`

如果解析结果是 `insufficient_hint`：

- 说明当前线索不足
- 追问更具体的页面名、模块名或 we码名
- 不要继续调用 `open_resolved_route`

推荐表达：

```text
我还无法确定您要打开哪个入口。请再告诉我是哪个页面、模块或 we码。
```

#### `not_found`

如果解析结果是 `not_found`：

- 告诉用户当前未找到匹配目标
- 不要自动 rewrite、不要自动再次调用 `resolve_route`
- 一旦本回合已经收到 `not_found`，本回合后续禁止再次调用 `resolve_route`
- 不要为了“再试一次”擅自把原词拆开、扩词、换词或改写成另一组 `keywords_csv`
- 不要替用户决定最可能的目标
- 根据当前失败上下文，给出更有针对性的补充建议
- 建议内容只允许围绕这些方向：
  - 补 `module` / 业务域词
  - 补页面功能词
  - 补常见别名或更口语化叫法
- 不要继续调用 `open_resolved_route`

建议内容的优先级：

1. 优先建议补 `module` / 业务域词
2. 其次建议补页面功能词
3. 最后才建议换一个更常见的页面叫法

收到 `not_found` 后，本回合允许的动作只有两类：

1. 直接向用户说明未找到
2. 给出补充线索建议

收到 `not_found` 后，本回合禁止的动作包括：

- 再次调用 `resolve_route`
- 改写 `keywords_csv` 后重试
- 改为猜测 `open_resolved_route`
- 把未命中的词拆成多个更短关键词再试

推荐表达：

```text
我暂时没找到对应页面。请再告诉我更具体一点的信息，例如：
- 是哪个模块里的页面，比如登录、账号安全、审批、IM
- 这个页面是做什么的，比如修改密码、重置密码、范围选择
- 或者你常用的页面叫法
```

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
1. 费控报销（we码）
2. 差旅报销（原生页面）

请问您要打开哪一个？
```

### 示例 5：用户原话已足够区分，直接代选

用户：

```text
打开登录里的修改密码页面
```

Agent：

1. 调用 `resolve_route`
2. 得到 `candidates`，包括：
   - `ui://myapp.login/changePassword`
   - `ui://myapp.settings/changePassword`
3. 用户原话里包含显式区分词 `登录里的`
4. 该线索唯一映射到 login candidate
5. 直接调用：

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

6. 返回：

```text
已为您打开登录里的修改密码页面。
```

### 示例 6：无法安全代选，定向追问

用户：

```text
打开修改密码页面
```

Agent：

1. 调用 `resolve_route`
2. 得到 `candidates`，包括：
   - `ui://myapp.login/changePassword`
   - `ui://myapp.settings/changePassword`
3. 用户原话里没有能唯一映射到单个候选的区分词
4. 不直接打开，改为定向追问：

```text
我找到了两个相关页面：一个是登录里的修改密码，另一个是账号安全里的修改密码。您要打开哪一个？
```

### 示例 7：用户用差异词确认后直接打开

上一轮 Agent 已追问：

```text
我找到了两个相关页面：一个是登录里的修改密码，另一个是账号安全里的修改密码。您要打开哪一个？
```

用户：

```text
账号安全
```

Agent：

1. 把“账号安全”映射到上一轮 settings candidate：
   - `targetType = native`
   - `uri = ui://myapp.settings/changePassword`
   - `title = changePassword`
2. 直接调用：

```json
{
  "shortcut": "open_resolved_route",
  "args": {
    "targetType": "native",
    "uri": "ui://myapp.settings/changePassword",
    "title": "changePassword"
  }
}
```

3. 返回：

```text
已为您打开账号安全里的修改密码页面。
```

### 示例 8：用户确认候选后直接打开

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

### 示例 9：线索不足，先追问

用户：

```text
帮我打开那个入口
```

Agent：

1. 不直接打开
2. 追问：

```text
请问您想打开哪个页面、模块或 we码？
```

### 示例 10：`not_found` 后给补充建议

用户：

```text
打开绩效星图页面
```

Agent：

1. 调用 `resolve_route`
2. 得到 `not_found`
3. 不自动 rewrite，不自动重试
4. 返回更有针对性的建议：

```text
我暂时没找到对应页面。请再告诉我更具体一点的信息，例如：
- 是哪个模块里的页面，比如绩效管理、工作台、数据中心
- 这个页面是做什么的，比如绩效看板、绩效分布、员工画像
- 或者你常用的页面叫法
```

### 示例 11：`not_found` 后提示补功能词而不是替用户决定

用户：

```text
打开那个密码页面
```

Agent：

1. 调用 `resolve_route`
2. 得到 `not_found`
3. 不直接猜“修改密码”或“重置密码”
4. 返回：

```text
我暂时没找到对应页面。请补充一下是哪个模块里的密码页面，或者它是“修改密码”“重置密码”“找回密码”中的哪一种。
```

## 禁止事项

- 不要跳过 `resolve_route` 直接猜测 URI
- 不要在多个候选目标之间基于模糊词盲选
- 不要在线索不足时直接打开页面
- 不要在候选确认回合发明不存在的 shortcut 名，例如 `open_schema`
- 不要在追问后的确认回合试探 `changePassword` 这类并不存在的 shortcut 名
- 不要在 `candidates` 场景下跳出候选集自由猜新的 URI
- 不要把机械复述全部候选列表当成“定向追问”
- 不要在 `not_found` 后自动 rewrite 并再次调用 `resolve_route`
- 不要在 `not_found` 后把用户原词拆成多个关键词再再次调用 `resolve_route`
- 不要在 `not_found` 后替用户决定“你应该是想去某某页面”
- 不要把 route 参数直接塞到 `open_resolved_route.args` 顶层
- 不要在缺少 `encoded` 时假装已经满足了需要编码的参数契约
- 不要把打开失败描述成成功
- 不要为了进入页面绕去 `android_gesture_tool` 模拟点击页面入口
