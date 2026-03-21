# Android Skill 扩展指南

本文档基于当前工程代码，说明如何添加 Skill。

Skill 用来描述复杂工作流程。Tool 提供单次能力调用，Skill 负责把多个步骤和决策规则组织起来交给 Agent 使用。

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

调用 `call_android_tool`，function 为 `my_tool`。

```json
{
  "function": "my_tool",
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

当前 Android 侧已经支持多通道工具，但宿主应用中注册的业务 Tool 仍然通过统一的 `call_android_tool` 包装调用。

当前通道包括：

- `call_android_tool`
  用于宿主 App 业务工具，例如联系人、消息、通知、剪贴板
- `android_gesture_tool`
  用于点击、滑动等坐标级手势

其中 `android_gesture_tool` 当前仍是 mock 运行时框架，适合验证通道选择和参数结构，不执行真实点击/滑动。

因此在 Skill 中，调用方式应写成：

```json
{
  "function": "search_contacts",
  "args": {
    "query": "张三"
  }
}
```

而不是直接写成“调用 `search_contacts` function”。

如果后续某个 Skill 需要明确驱动点击或滑动，可以单独使用 `android_gesture_tool`，例如：

```json
{
  "action": "tap",
  "x": 120,
  "y": 340
}
```

或：

```json
{
  "action": "swipe",
  "startX": 100,
  "startY": 500,
  "endX": 400,
  "endY": 500,
  "duration": 300
}
```

但在当前工程状态下，这类调用只会得到 mock 结果。

## 现有示例

当前工程中的 Skill 示例：

- `app/src/main/assets/workspace/skills/im_sender/SKILL.md`
- `agent-core/src/main/assets/workspace/skills/chinese_writer/SKILL.md`

其中 `im_sender` 更接近 Android 场景下通过 `call_android_tool` 调用宿主工具的真实写法。

## 生效方式

Skill 不是热更新注册的。通常需要：

1. 把 `SKILL.md` 放入应用 assets
2. 重新构建并安装应用
3. 重新初始化 workspace 或清理旧 workspace 后再次启动

原因是 `WorkspaceManager` 只会在 workspace 不存在时从 assets 复制初始内容；如果用户设备上已经有旧 workspace，新增 Skill 不一定自动覆盖进去。

## 调试建议

- 先确保 Skill 文件最终被打进应用 assets
- 检查 workspace 目录下是否真的存在 `skills/<name>/SKILL.md`
- 确保 Skill 中引用的工具名和 `ToolExecutor#getName()` 返回值一致
- 如果 Skill 依赖业务 Tool，优先参考 `ToolExecutor#getDefinition()` 中的描述、意图示例和参数样例来写调用
- 如果 Skill 依赖 `requiredBins` / `requiredEnvs` / `os`，确认运行环境满足要求
