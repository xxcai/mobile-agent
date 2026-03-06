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
- ✓ 自定义 Skills 机制 — v1.6 (shipped 2026-03-06)
- ✓ Agent 调用 Tools — v1.6 (shipped 2026-03-06)

### Active

- [ ] v2.0: 接入真实项目 - AAR 打包 + 重构 (本里程碑)

## Current Milestone: v1.6 自定义 Skills 验证

**Status:** ✓ SHIPPED 2026-03-06

**Delivered:**
- SKILL.md 格式定义和 YAML frontmatter 解析
- C++ 层 SkillLoader 加载机制
- search_contacts / send_im_message Android Tools
- im_sender 测试 Skill 通过 UAT (6/6)

## Current Milestone: v2.0 接入真实项目

**Goal:** 重构代码架构，打包 AAR，清理旧代码，统一命名

**Target features:**
- 打包 agent 模块为 AAR（C++ 核心 + Android 管道 + JNI 适配）
- 打包 app 模块为 AAR（Android 平台逻辑）
- 清理 Vue 相关代码
- 清理 nanobot 连接相关代码，统一重命名
- 平台相关逻辑从 agent 上移到 app 模块

---

## v1.6 自定义 Skills 验证 (Shipped: 2026-03-06)

## Context

**现有代码库:**
- `mobile-agent` - Android 应用（含 C++ Agent 引擎）
- `cxxplatform` - Windows C++ Agent 原型（参考）
- `agent` - Android C++ Agent 模块

**架构演进:**
- ~~旧架构：App 通过 HTTP 连接 PC 上的 Nanobot 服务（需要 adb reverse）~~
- **新架构：Agent 直接运行在手机本地**，不依赖 PC

**Agent 运行时:**
- LLM 调用在手机本地执行
- 不需要网络代理或 adb 端口转发
- 保护隐私，离线可用（需要 LLM API key）

## Constraints

- **版本规则**: v1.6 之后使用整数版本号 (v2.0, v3.0)，不使用小数点 (v1.7, v1.8)
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
| Skill 调用 Tool | 多步骤工具链，Tool 结果返回给 LLM | ✓ v1.6 shipped |

---
*Last updated: 2026-03-06 — v2.0 started*
