# Roadmap: 手机上的 AI Agent

## Milestones

- ✅ **v2.3 语音转文字** — Phases 1 to 3 (shipped 2026-03-11)
- ✅ **v2.2 App 层动态注入 Android 工具** — Phases 1 to 3 (shipped 2026-03-10)
- ✅ **v2.1 架构重构** — Phases 1 to 5 (shipped 2026-03-09)
- ✅ **v2.0 接入真实项目** — Phases v20-01 to v20-04 (shipped 2026-03-09)
- ✅ **v1.6 自定义 Skills 验证** — Phases v16-01 to v16-02 (shipped 2026-03-06)
- ✅ **v1.5 LLM → Android 调用管道** — Phases v15-01 to v15-03 (shipped 2026-03-05)
- ✅ **v1.4 Android Tools 通道** — Phases 1-4 (shipped 2026-03-05)

## v2.4 Agent 性能分析 (Complete)

**Goal:** 分析 agent-core C++ 层日志和性能，识别耗时点并优化

### Phases

| Phase | Name | Description |
|-------|------|-------------|
| 1 | 统一日志格式 | 改进日志宏，添加脱敏处理 |
| 2 | 补充关键路径日志 | HTTP/LLM/MCP/TOOL/LOOP 耗时日志 |
| 3 | 性能分析与优化 | ScopedTimer，识别热点 |

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1 | - | Complete | 2026-03-11 |
| 2 | 1/1 | Complete | 2026-03-11 |
| 3 | 1/1 | Complete | 2026-03-11 |

### Plans

- [x] 03-performance-analysis/03-PLAN.md — 手动日志分析 + 性能热点分析

*Roadmap updated: 2026-03-11*
