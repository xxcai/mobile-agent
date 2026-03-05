---
gsd_state_version: 1.0
milestone: v1.5
milestone_name: Roadmap
status: unknown
last_updated: "2026-03-05T08:57:16.651Z"
progress:
  total_phases: 9
  completed_phases: 7
  total_plans: 11
  completed_plans: 9
---

# STATE: Mobile Agent - C++ 移植版

**Last Updated:** 2026-03-05

---

## Project Reference

**Core Value:** 在 Android 设备上运行本地 AI Agent，提供实时对话和设备控制能力，无需依赖远程服务器。

**Current Focus:** v1.5 LLM → Android 调用管道

---

## Current Position

| Field | Value |
|-------|-------|
| Milestone | v1.5 LLM → Android 调用管道 |
| Phase | Not started |
| Status | Ready for Phase 1 discussion |
| Last activity: | 2026-03-05 — v1.4 shipped, v1.5 ready |

---

## Performance Metrics

| Metric | Value |
|--------|-------|
| v1.4 Phases | 4 (shipped) |
| v1.5 Phases | 5 (in progress) |
| v1.4 Requirements | 4 ✓ |
| v1.5 Requirements | 5 |

---

## v1.4 Requirements (SHIPPED 2026-03-05)

- **TOOL-01**: C++ 提供 call_android_tool(tool_name, args) 同步调用接口 ✓
- **TOOL-02**: Java 层注册和执行 Android Tools 的机制 ✓
- **TOOL-03**: 可配置的 tools.json 定义可用工具列表 ✓
- **TOOL-04**: show_toast Tool 实现 ✓

---

## v1.5 Requirements

- **PIPE-01**: 通用的 LLM → Android 调用管道（JSON 结构化参数）
- **PIPE-02**: 内置工具框架（支持扩展注册）
- **PIPE-03**: Skills 编排多步骤工作流
- **PIPE-04**: 完全自主调用模式

---

## Session Continuity

### Recent Changes

- 2026-03-05: v1.4 shipped - Android Tools 通道
- 2026-03-05: v1.5 started - LLM → Android 调用管道
- 2026-03-04: v1.3 shipped - 预置 workspace

### Blockers

None

### Todos

None

---

## Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 1 | 清理 lib 中未使用的 gradle 依赖 | 2026-03-05 | 020f92a | [1-lib-gradle](./quick/1-lib-gradle/) |
| 2 | 清理 agent 中用不到的 gradle 依赖 | 2026-03-05 | | [2-agent-gradle](./quick/2-agent-gradle/) |

---

## v1.4 进度

| Phase | Name | Status |
|-------|------|--------|
| 1 | JNI 回调通道 | ✓ Complete |
| 2 | Java Tools 注册机制 | ✓ Complete |
| 3 | show_toast Tool | ✓ Complete |
| 4 | 修复config.json安全问题 | ✓ Complete |

---

## v1.5 进度

| Phase | Name | Status |
|-------|------|--------|
| 1 | tools.json 迁移到 inputSchema | ● In discussion |
| 2 | 通用 call_android_tool | ○ Not started |
| 3 | 验证工具框架 | ○ Not started |
| 4 | Skills 加载机制 | ○ Not started |
| 5 | Skill 编排示例 | ○ Not started |

---

## Roadmap Evolution

- v1.4 shipped: Android Tools 通道 (4 phases)
- v1.5 started: LLM → Android 调用管道 (5 phases)
- Phase 编号调整: 每个 milestone 独立编号 (v1.4 Phase 1-4, v1.5 Phase 1-5)

---

*State managed by GSD workflow*
