# Mobile Agent - C++ 移植版

## What This Is

将 cxxplatform (Windows/C++ Agent 原型) 移植到 Android 平台，使用 C++ 技术栈实现本地 Agent 能力，替代现有的 Nanobot HTTP 后端服务。

## Core Value

在 Android 设备上运行本地 AI Agent，提供实时对话和设备控制能力，无需依赖远程服务器。

## Requirements

### Validated

- ✓ Android 聊天界面 (Java) — 现有代码库
- ✓ MVP 架构模式 — 现有代码库
- ✓ OkHttp HTTP 客户端 — 现有代码库
- ✓ C++ NDK 集成基础 — 现有代码库

### Active

- [ ] API Key 配置 - 通过配置文件为 Agent 提供 API Key

## Current Milestone: v1.1 API Key 配置

**Goal:** 提供配置文件方式设置 LLM API Key

### Out of Scope

- Windows 平台支持 — cxxplatform 原生
- iOS 移植 — 独立工作
- 远程服务器部署 — 本地运行优先

## Context

**现有代码库:**
- `mobile-agent` - Android 应用，通过 HTTP 连接 Nanobot
- `cxxplatform` - Windows C++ Agent 原型 (待移植)
- 当前依赖外部 Nanobot 服务 (localhost:18791)

**移植目标:**
- 将 cxxplatform 的 C++ Agent 核心移植到 Android
- 通过 JNI 与现有 Java Android UI 通信
- 保持向后兼容，可选择使用本地 Agent 或远程服务

## Constraints

- **技术栈**: C++ (原生) + Java (Android UI) — 必须
- **兼容性**: minSdk 24 (Android 7.0) — 现有项目约束
- **NDK**: NDK 26.3.11579264 — 现有项目配置
- **构建**: Gradle 8.12.1, AGP 8.3.2 — 现有项目配置

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 本地运行优先 | 减少网络延迟，保护隐私 | ✓ 已实现 Mock 模式 |
| 保持 UI 不变 | 减少回归风险 | ✓ 已完成 |
| JNI 通信 | 标准 Java/C++ 互操作方式 | ✓ 已完成 |
| 配置文件方式 | 简单直接，无需额外 UI | — Pending |

---
*Last updated: 2026-03-03 after v1.0 milestone*
