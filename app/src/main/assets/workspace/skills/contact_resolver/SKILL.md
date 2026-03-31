---
name: contact_resolver
description: 联系人解析助手。帮助 Agent 根据姓名、关键词或上一轮候选选择结果，解析出稳定的联系人目标和 contact_id。当用户要给某人发消息、提醒某人、联系某人，且联系人尚未明确时使用。
emoji: "👤"
always: false
---

# 联系人解析助手

当任务需要把“某个人”解析成稳定联系人目标时，使用此 skill。

**CRITICAL — 涉及候选序号、候选描述或续轮联系人选择时，MUST 先读取 `skills/contact_resolver/references/contact-selection.md`，不要猜测 `contact_id`。**

## 使用原则

- 这是一个 shortcut-guided skill：先依据本 skill 判断联系人状态，再调用具体 shortcut
- 当前 skill 只推荐一个 shortcut：
  - `search_contacts`
- `contact_resolver` 是 skill 名，不是 shortcut 名
- 不要对 `contact_resolver` 调用 `describe_shortcut`
- 不要调用 `run_shortcut`，`shortcut` 为 `contact_resolver`
- 当联系人尚未明确时，不要直接调用 `send_im_message`
- 当上一轮已经给出候选联系人，而用户这一轮只回复序号或候选描述时，应优先承接上一轮候选结果
- 详细候选选择规则在 `skills/contact_resolver/references/contact-selection.md`

## 触发条件

以下场景通常应触发本 skill：

- “给张三发消息”
- “联系一下李四”
- “告诉王五今天加班”
- “提醒赵六看文档”
- 用户在上一轮候选列表之后只回复：
  - `1`
  - `2`
  - `第一个`
  - `技术部那个`

以下情况通常不应使用本 skill：

- 联系人已经有稳定 `contact_id`
- 当前任务不需要联系人解析
- 用户只是要求打开页面、滚动页面或总结页面内容

## 推荐 Shortcut

### `search_contacts`

适用场景：

- 已经知道联系人姓名或关键词，但还没有明确联系人 ID
- 需要确认是否存在目标联系人
- 需要处理同名联系人分支

调用格式：

```json
{
  "shortcut": "search_contacts",
  "args": {
    "query": "张三"
  }
}
```

## 工作流程

### 步骤 1：判断当前是哪一类联系人输入

#### 类型 A：首轮联系人线索

例如：

- “张三”
- “给张三发消息”
- “联系李四”

这类输入通常需要搜索联系人。

#### 类型 B：续轮候选选择

例如：

- `1`
- `2`
- `第一个`
- `第二个`
- `技术部那个`
- `市场部那个`

这类输入通常出现在上一轮已经返回多个候选联系人之后。

处理这类输入前，先读取 `skills/contact_resolver/references/contact-selection.md`。

### 步骤 2：判断是否需要搜索联系人

#### 什么时候需要搜索联系人

以下情况应调用 `search_contacts`：

- 当前会话里还没有候选联系人结果
- 当前只有联系人姓名或关键词，还没有明确联系人 ID
- 用户改变了联系人目标
- 上一轮候选结果不足以判断用户当前选择的是谁

调用：

```json
{
  "shortcut": "search_contacts",
  "args": {
    "query": "联系人姓名或关键字"
  }
}
```

#### 什么时候可以跳过搜索联系人

只有在以下条件同时满足时，才可以跳过 `search_contacts`：

- 当前会话里已经有明确的候选联系人结果
- 用户这一轮只是对上一轮候选结果做选择
- 用户没有改变联系人目标

此时：

- 不要重新搜索联系人
- 直接把当前输入解释为候选选择
- 输出明确联系人结果，供后续 skill 使用
- 不要把候选序号直接当成 `contact_id`

### 步骤 3：处理搜索结果

#### 无匹配

如果返回空结果：

- 告诉用户未找到联系人
- 建议用户确认姓名或提供更多信息
- 不要伪造联系人 ID

#### 单一匹配

如果只有一个联系人：

- 直接确认该联系人
- 记录其 `contact_id`

#### 多个匹配

如果出现多个联系人：

- 列出候选项
- 让用户明确选择
- 在用户确认之前，不要替用户猜测

推荐表达：

```text
找到 2 位联系人：
1. 张三（技术部）
2. 张三（市场部）

请问您要发送给哪一位？
```

### 步骤 4：输出联系人解析结果

当联系人已经明确时，后续流程应至少拿到这些信息：

- `contact_id`
- `name`
- 必要时带上 `department`

后续如果任务是发送消息，应把这些结果交给 `im_sender`，再由 `im_sender` 调用 `send_im_message`。

## 示例

### 示例 1：首轮搜索

用户：

```text
给张三发消息
```

Agent：

1. 调用 `search_contacts`
2. 如果只有一个张三，确认联系人并拿到 `contact_id`
3. 如果有多个张三，要求用户确认

### 示例 2：用户在下一轮只回复序号

上一轮 Agent：

```text
找到 2 位联系人：
1. 张三（技术部）
2. 张三（市场部）

请问您要发送给哪一位？
```

用户：

```text
1
```

Agent：

1. 将 `1` 解释为选择第一个候选联系人
2. 不要重新调用 `search_contacts`
3. 输出明确联系人结果，供 `im_sender` 使用

## 禁止事项

- 不要把 skill 名 `contact_resolver` 当成 shortcut 去 `run_shortcut` 或 `describe_shortcut`
- 不要因为用户只回复 `1`，就重新启动新的意图识别流程
- 不要在已经有明确候选列表时重复搜索同一个联系人
- 不要在联系人尚未明确时直接调用 `send_im_message`
