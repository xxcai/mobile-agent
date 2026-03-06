# Mobile Agent - 手机上的 AI Agent

## What This Is

在 Android 手机上运行的 AI Agent，通过文字聊天（后续支持语音）作为交互界面，能调用手机应用提供的各种能力（打卡、发消息等）来自动化工作流。

## Core Value

让用户通过自然对话，指挥手机自动完成日常任务。

## Requirements

### Validated

- ✓ Android 聊天界面 (Java) — 现有代码库
- ✓ MVP 架构模式 — 现有代码库
- ✓ OkHttp HTTP 客户端 — 现有代码库
- ✓ C++ NDK 集成基础 — v1.0
- ✓ C++ Agent 引擎 — v1.0
- ✓ JNI 通信 — v1.0
- ✓ API Key 配置 — v1.1
- ✓ 清理 agent 模块 — v1.2
- ✓ 预置 workspace — v1.3 (shipped 2026-03-04)
- ✓ Android Tools 通道 — v1.4 (shipped 2026-03-05)
- ✓ LLM → Android 调用管道 — v1.5 (shipped 2026-03-05)
- ✓ 自定义 Skills 验证 — v1.6 (shipped 2026-03-06)

### Active

- [ ] v1.7: (待规划)

## Current Milestone: v1.7 待规划
- ✓ 通用的 LLM → Android 调用管道（JSON 结构化参数）
- ✓ 内置工具清单：show_toast, display_notification, read_clipboard, take_screenshot
- ✓ C++ tool_registry 从 tools.json 加载
- ✓ AndroidToolManager 注册表实现
- ✓ 完全自主调用模式

## Context

**现有代码库:**
- `mobile-agent` - Android 应用
- `cxxplatform` - Windows C++ Agent 原型
- `agent` - Android C++ Agent 模块

**cxxplatform workspace 内容:**
- `USER.md` - 用户信息占位符
- `SOUL.md` - Agent 身份定义
- `skills/` - 技能配置

## Constraints

- **技术栈**: C++ (原生) + Java (Android UI) — 必须
- **兼容性**: minSdk 24 (Android 7.0)
- **NDK**: NDK 26.3.11579264
- **构建**: Gradle 8.12.1, AGP 8.3.2

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 本地运行 | Agent 运行在手机本地，保护隐私 | ✓ |
| 保持 UI 不变 | 减少回归风险 | ✓ |
| JNI 通信 | 标准 Java/C++ 互操作方式 | ✓ |
| local.properties | 管理本地 apiKey 配置 | ✓ |
| workspace 预置 | 让 Agent 有初始身份和能力 | ✓ v1.3 shipped |
| Android Tools 通道 | 统一的 JNI 回调 + 工具注册机制 | ✓ v1.4 shipped |
| 通用 Android 调用管道 | LLM 通过 JSON 参数调用 Android 功能 | ✓ v1.5 shipped |
| 完全自主调用 | LLM 直接执行，无需用户确认 | ✓ v1.5 shipped |
| 自定义 Skills | Agent 通过 Skill 调用 Android Tools | ✓ v1.6 shipped |

---
*Last updated: 2026-03-06 — updated vision to "手机上的 AI Agent"*
