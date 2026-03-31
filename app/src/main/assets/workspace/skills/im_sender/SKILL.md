---
name: im_sender
description: 发送即时消息助手。帮助 Agent 搜索联系人并发送 IM 消息。当用户要求发消息、通知某人、提醒某人、联系某人，或隐含意图是向某人传递信息时使用。
emoji: "💬"
always: false
---

# IM 消息发送助手

**CRITICAL — 第一步 MUST 先用 `read_file` 读取并遵循 `skills/agent_shared/SKILL.md`。**
**CRITICAL — 如果联系人尚未明确，第二步 MUST 读取 `skills/contact_resolver/SKILL.md`，先得到稳定的 `contact_id`。**
**CRITICAL — 在完成前两步之前，不要调用任何业务 shortcut。**
**CRITICAL — 调用 `send_im_message` 前，MUST 读取 `skills/im_sender/references/send-im-message.md`，不要猜测参数或成功条件。**

当用户的目标是“把一段信息传递给某个人”时，使用此 skill。

## 使用原则

- 这是一个 shortcut-guided skill：先依据本 skill 决定流程，再调用具体 shortcut
- 当前 skill 只推荐一个 shortcut：
  - `send_im_message`
- 当联系人尚未明确时，不要在本 skill 内自己处理联系人搜索；应先遵循 `contact_resolver`
- `send_im_message` 的调用约束和错误处理细节在 `skills/im_sender/references/send-im-message.md`
- 当当前证据不足以安全发送时，不要继续执行发送动作
- 不要为了完成发送而绕去 `android_gesture_tool` 模拟点击发送按钮

## 触发条件

以下表达通常应触发本 skill：

- “给张三发消息”
- “告诉李四下午开会”
- “通知王五明天提测”
- “提醒赵六看一下文档”
- “联系一下张三”
- “让李四来我办公室一趟”
- “跟王五说一声”

以下情况通常不应使用本 skill：

- 用户只是查询联系人，不要求发送消息
- 用户要打开聊天页、点击发送按钮、滚动聊天列表
- 用户要总结消息内容，而不是发送消息

## 入口分类

进入本 skill 后，先判断当前属于哪一类输入。

### 类型 A：联系人已明确的发送请求

例如：

- 当前会话里已经有稳定的 `contact_id`
- 当前用户只是在补充或确认消息内容

这类输入可以直接进入发送步骤。

### 类型 B：续轮续发请求

例如：

- “再发一条”
- “继续发给他”
- “再告诉李四一次”
- “同样发给张三”

这类输入表示新的发送动作，但可能依赖上一轮上下文中的联系人或消息目标。

## 推荐 Shortcut

### `send_im_message`

适用场景：

- 已经有明确联系人 ID
- 已经有明确消息内容
- 联系人选择分支已经结束

调用格式：

```json
{
  "shortcut": "send_im_message",
  "args": {
    "contact_id": "003",
    "message": "明天下午3点开会"
  }
}
```

## 工作流程

### 步骤 1：先确认联系人是否已经明确

如果当前还没有稳定的 `contact_id`：

- 不要直接调用 `send_im_message`
- 先读取并遵循 `skills/contact_resolver/SKILL.md`
- 先完成联系人解析，再回到本 skill

### 步骤 2：确认消息内容

如果消息内容还不明确：

- 先追问消息内容
- 不要发送空消息

推荐表达：

```text
请问您想发送什么消息？
```

### 步骤 3：发送消息

当且仅当以下条件同时满足时，才能调用 `send_im_message`：

- 联系人 ID 已明确
- 消息内容已明确

调用前：

- 先读取 `skills/im_sender/references/send-im-message.md`
- 确认当前要传的是稳定 `contact_id`，不是候选序号或自然语言选择结果

调用：

```json
{
  "shortcut": "send_im_message",
  "args": {
    "contact_id": "联系人ID",
    "message": "消息内容"
  }
}
```

### 步骤 4：只有执行成功后才能确认已发送

如果本轮还没有成功调用 `send_im_message`：

- 不要说“已发送”
- 不要说“已再次发送”
- 不要用口头确认代替实际执行

如果用户说的是“再发一条”“继续发给他”“再告诉李四一次”这类续发表达：

- 仍然要把它视为新的发送动作
- 必须重新走本 skill 的发送流程
- 不能因为上一轮已经给某个人发过消息，就直接宣称这轮也已经发出
- 如果联系人上下文不再明确，应先回到 `contact_resolver`

## 结果处理

### 成功

如果发送成功：

- 简洁确认结果
- 不要重复输出过多内部细节

推荐表达：

```text
消息已发送给李四：明天下午3点开会。
```

### 失败

如果发送失败：

- 优先解释用户可理解的失败原因
- 如果是联系人不可达、目标不可访问，不要假装已发送成功
- 提供下一步建议，而不是只回技术错误码

## 错误处理

### `missing_required_param`

含义：

- 调用参数不完整

处理：

- 补齐必要信息
- 不要直接重试同样的请求

### `business_target_not_accessible`

含义：

- 当前联系人目标不能直接发送

处理：

- 明确告诉用户当前目标不可直接触达
- 不要伪装为发送成功

### `execution_failed`

含义：

- 执行过程失败

处理：

- 给出简洁失败说明
- 建议稍后重试

## 示例

### 示例 1：单一匹配，直接发送

用户：

```text
给李四发消息说明天下午3点开会
```

Agent：

1. 若联系人尚未明确，先通过 `contact_resolver` 得到单一匹配 `李四(产品部, id=003)`
2. 调用 `send_im_message`
3. 返回：

```text
消息已发送给李四：明天下午3点开会。
```

### 示例 2：多个匹配，先通过联系人解析确认

用户：

```text
给张三发消息说明天开会
```

Agent：

1. 先进入 `contact_resolver`
2. 由 `contact_resolver` 调用 `search_contacts`
3. 得到多个张三
4. 返回：

```text
找到 2 位联系人：
1. 张三（技术部）
2. 张三（市场部）

请问您要发送给哪一位？
```

### 示例 3：消息内容缺失

用户：

```text
给李四发个消息
```

Agent：

1. 若联系人尚未明确，先通过 `contact_resolver` 得到单一匹配
2. 追问：

```text
请问您想发送什么消息？
```

### 示例 4：用户要求“再发一条”

上一轮 Agent 已成功发出一条消息。

用户：

```text
再发一条，告诉他今晚加班
```

Agent：

1. 将其视为新的发送动作
2. 不能直接沿用上一轮成功结果作为这轮完成证据
3. 如果联系人上下文仍明确，可以复用联系人并继续发送流程
4. 如果联系人上下文不明确，先回到 `contact_resolver`
5. 只有本轮 `send_im_message` 成功后，才能确认“已发送”

## 禁止事项

- 不要在消息内容缺失时发送空消息
- 不要把失败结果描述成成功
- 不要在没有执行 `send_im_message` 的情况下声称“已发送”
- 不要把“再发一条”理解成可以直接沿用上一轮结果并口头完成
- 不要为了完成发送而绕去 `android_gesture_tool` 模拟业务发送
- 不要把 skill 名 `im_sender` 当成 shortcut 去 `run_shortcut` 或 `describe_shortcut`
