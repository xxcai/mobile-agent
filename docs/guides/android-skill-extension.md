# Android Skill 扩展指南

本文档基于当前工程代码，说明如何添加 Skill。

Skill 用来描述复杂工作流程。Tool 提供单次能力调用，Skill 负责把多个步骤和决策规则组织起来交给 Agent 使用。

当前工程里的 Skill 应明确区分两类：

- `shortcut-guided skill`
  这类 skill 的核心职责是指导 Agent 选择和串联 `run_shortcut` 下的业务原子动作，例如联系人搜索、发消息、路由解析和页面打开。
- `visual-operation skill`
  这类 skill 的核心职责是指导 Agent 使用 `android_view_context_tool` 与 `android_gesture_tool` 做页面观察、定位、点击、滑动和受控补读。

不要把所有 skill 都强行写成 shortcut-first。只有“业务原子能力明确、可稳定封装”的场景才应该优先使用 shortcut。

## Skill 的加载位置

当前工程里，Skill 会从 workspace 的 `skills/` 目录加载。对 Android 宿主来说，初始化流程是：

1. `AgentInitializer` 调用 `WorkspaceManager.initialize()`
2. `WorkspaceManager` 把应用 assets 中的 `workspace/` 内容复制到用户工作目录
3. native 层从 workspace 下的 `skills/` 目录扫描每个 Skill 子目录中的 `SKILL.md`

对于宿主应用，自定义 Skill 建议放在：

```text
app/src/main/assets/workspace/skills/<skill_name>/SKILL.md
```

说明：

- `agent-core/src/main/assets/workspace/skills/` 中可以携带库内置 Skill
- `app/src/main/assets/workspace/skills/` 中可以放宿主自己的 Skill
- 运行时实际复制的是应用最终 assets 里的 `workspace/skills/`

## 步骤 1: 创建 Skill 目录

示例：

```text
app/src/main/assets/workspace/skills/
└── my_skill/
    └── SKILL.md
```

目录名 `my_skill` 会被当作 Skill 名称。

## 步骤 2: 编写 `SKILL.md`

最小示例：

```md
---
description: 演示用技能
always: false
---

# My Skill

当用户要求执行某个固定流程时，使用这个 Skill。

## 工作流程

### 步骤 1

调用 `run_shortcut`，shortcut 为 `my_tool`。

```json
{
  "shortcut": "my_tool",
  "args": {
    "value": "demo"
  }
}
```

### 步骤 2

根据工具结果决定下一步行为。
```

## Frontmatter 字段

当前解析器支持这些字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `description` | string | Skill 简述 |
| `always` | boolean | 是否始终注入上下文 |
| `emoji` | string | 展示用图标 |
| `requiredBins` | array | 依赖的外部命令 |
| `anyBins` | array | 至少存在一个即可 |
| `requiredEnvs` | array | 依赖的环境变量 |
| `os` | array | OS 限制 |

一个更完整的示例：

```yaml
---
description: 发送即时消息助手
emoji: "💬"
always: false
requiredBins:
  - adb
requiredEnvs:
  - SOME_ENV
os:
  - android
---
```

如果不写 frontmatter，解析器也会把整个文件正文当作 Skill 内容加载。

## Skill 内容编写建议

Skill 正文没有强制格式，但建议至少包含：

- 触发条件
- 工作流程
- 决策规则
- 错误处理
- 示例对话

这会让模型更稳定地理解何时启用 Skill，以及工具该怎么调用。

## 与 Tool 的关系

当前 Android 侧已经支持多通道工具，宿主应用中注册的业务 `ShortcutExecutor` 会通过统一的 `run_shortcut` 暴露调用。

当前通道包括：

- `run_shortcut`
  用于宿主 App 业务 shortcut，例如联系人、消息等业务原子动作
- `android_gesture_tool`
  用于页面内点击、滚动等 UI 手势

其中 `android_gesture_tool` 当前已经具备真实 in-process 执行能力，但仍应优先用于“先看页面，再执行元素动作”的场景，而不是替代业务工具。

当前 app 示例实际注入的 shortcut 有：

- `search_contacts`
- `send_im_message`
- `resolve_route`
- `open_resolved_route`

因此在 Skill 中，调用方式应写成：

```json
{
  "shortcut": "search_contacts",
  "args": {
    "query": "张三"
  }
}
```

而不是使用已经移除的 `call_android_tool.function` 协议。

如果后续某个 Skill 需要明确驱动点击或滑动，可以单独使用 `android_gesture_tool`，例如：

```json
{
  "action": "tap",
  "x": 120,
  "y": 340,
  "observation": {
    "snapshotId": "obs_xxx",
    "targetDescriptor": "发送按钮",
    "referencedBounds": "[920,2100][1038,2196]"
  }
}
```

或：

```json
{
  "action": "swipe",
  "direction": "down",
  "scope": "feed",
  "amount": "medium",
  "duration": 300
}
```

## 现有示例

当前工程中的 Skill 示例：

- `app/src/main/assets/workspace/skills/contact_resolver/SKILL.md`
- `app/src/main/assets/workspace/skills/im_sender/SKILL.md`
- `app/src/main/assets/workspace/skills/route_navigator/SKILL.md`
- `app/src/main/assets/workspace/skills/moments_summary/SKILL.md`
- `app/src/main/assets/workspace/skills/cloud_space_summary/SKILL.md`

其中：

- `contact_resolver`、`im_sender` 和 `route_navigator` 属于 `shortcut-guided skill`
- `moments_summary` 和 `cloud_space_summary` 属于 `visual-operation skill`

新增 skill 时，应先判断它属于哪一类，再决定是围绕 `run_shortcut` 写规程，还是围绕 `android_view_context_tool` / `android_gesture_tool` 写规程。

## 生效方式

Skill 不是热更新注册的。通常需要：

1. 把 `SKILL.md` 放入应用 assets
2. 重新构建并安装应用
3. 重新初始化 workspace 或清理旧 workspace 后再次启动

原因是 `WorkspaceManager` 只会在 workspace 不存在时从 assets 复制初始内容；如果用户设备上已经有旧 workspace，新增 Skill 不一定自动覆盖进去。

## 调试建议

- 先确保 Skill 文件最终被打进应用 assets
- 检查 workspace 目录下是否真的存在 `skills/<name>/SKILL.md`
- 确保 Skill 中引用的 shortcut 名和 `ShortcutDefinition#getName()` 返回值一致
- 如果 Skill 依赖业务 shortcut，优先参考 `ShortcutDefinition` 中的描述、参数样例和 `requiredSkill`/`domain` 元数据来写调用
- 如果 Skill 依赖 `requiredBins` / `requiredEnvs` / `os`，确认运行环境满足要求
