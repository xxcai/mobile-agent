# Android Skill 扩展指南

本文档介绍如何在 Mobile Agent 中添加 Skill。

## 什么是 Skill

Skill 是 Agent 的能力扩展，定义了复杂工作流程的执行方式。一个 Skill 包含：
- 触发条件：什么情况下使用这个 Skill
- 工作流程：执行步骤的详细描述
- 决策规则：不同情况下的处理方式
- 示例对话：帮助 LLM 理解如何执行

## 添加 Skill

### 步骤 1: 创建 Skill 目录

在 `app/src/main/assets/workspace/skills/` 下创建新目录：

```
app/src/main/assets/workspace/skills/
└── my_skill/
    └── SKILL.md
```

### 步骤 2: 编写 SKILL.md

每个 Skill 目录需要包含一个 `SKILL.md` 文件：

```yaml
---
description: Skill 描述
emoji: "💬"
always: false
---

# Skill 名称

当用户请求xxx时，使用此 Skill 完成工作流程。

## 触发条件

- 用户要求"xxx"
- 用户要求"xxx"

## 工作流程

### 步骤 1: xxx

使用 `tool_name` 工具执行操作。

**输入参数：**
```json
{
  "param": "参数值"
}
```

### 步骤 2: 处理结果

根据结果进行相应处理。

### 步骤 3: 返回结果

向用户确认操作完成。

## 决策规则

- 情况A：处理方式
- 情况B：处理方式

## 示例对话

**用户：** "xxx"

**Agent：**
1. 执行第一步
2. 处理结果
3. 返回结果
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| description | string | Skill 的简短描述 |
| emoji | string | Skill 的图标（可选） |
| always | boolean | 是否总是启用（默认 false） |

### 工作流程字段

| 字段 | 说明 |
|------|------|
| 步骤 N | 步骤标题，使用 `tool_name` 调用工具 |
| 输入参数 | JSON 格式的输入参数 |
| 输出示例 | 工具调用返回的示例结果 |

## Skill 示例

参考 `app/src/main/assets/workspace/skills/im_sender/SKILL.md`，这是一个完整的 Skill 示例：

```yaml
---
description: 发送即时消息助手，帮助用户搜索联系人并发送IM消息
emoji: "💬"
always: false
---

# IM 消息发送助手

当用户请求发送即时消息时，使用此 Skill 完成以下工作流程。

## 触发条件

- 用户要求"发消息给xxx"
- 用户要求"发送IM消息"
- 用户要求"给xxx发一条消息"

## 工作流程

### 步骤 1: 搜索联系人

使用 `search_contacts` 工具搜索联系人。

**输入参数：**
```json
{
  "query": "联系人姓名"
}
```

### 步骤 2: 处理搜索结果

根据搜索结果数量采取不同策略：
- **多个匹配项**：同时返回所有结果，让用户选择
- **单个匹配项**：直接使用该联系人
- **无匹配项**：告知用户未找到联系人

### 步骤 3: 发送消息

使用 `send_im_message` 工具发送消息。

### 步骤 4: 返回结果

向用户确认消息发送成功。

## 决策规则

- 多个匹配项：同时返回所有结果，让用户选择
- 单个匹配项：直接发送，不询问
```

## 自动加载

放置在 `workspace/skills/` 目录下的 Skill 会自动被 Agent 加载，无需额外配置。

Agent 启动时会扫描所有 Skill 目录，解析 SKILL.md 文件，并根据触发条件决定何时使用。

## 与 Tool 的关系

- **Tool**：提供基础能力（如发送消息、搜索联系人）
- **Skill**：组合 Tool 实现复杂工作流程（如完整的发消息流程）

Skill 调用 Tool，Tool 执行具体操作。
