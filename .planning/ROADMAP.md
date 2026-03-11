# Roadmap: 手机上的 AI Agent

## Milestones

- 🚧 **v2.5 容器Activity模块** — Phases 4 to 7 (in progress)
- ✅ **v2.4 Agent 性能分析** — Phases 1 to 3 (shipped 2026-03-11)
- ✅ **v2.3 语音转文字** — Phases 1 to 3 (shipped 2026-03-11)
- ✅ **v2.2 App 层动态注入 Android 工具** — Phases 1 to 3 (shipped 2026-03-10)
- ✅ **v2.1 架构重构** — Phases 1 to 5 (shipped 2026-03-09)
- ✅ **v2.0 接入真实项目** — Phases v20-01 to v20-04 (shipped 2026-03-09)
- ✅ **v1.6 自定义 Skills 验证** — Phases v16-01 to v16-02 (shipped 2026-03-06)
- ✅ **v1.5 LLM → Android 调用管道** — Phases v15-01 to v15-03 (shipped 2026-03-05)
- ✅ **v1.4 Android Tools 通道** — Phases 1-4 (shipped 2026-03-05)

---

## v2.5 容器Activity模块 (In Progress)

**Goal:** 实现悬浮球入口和容器Activity，支持数据持久化，独立于现有项目验证

### Phases

| Phase | Name | Description | Requirements |
|-------|------|-------------|--------------|
| 4 | 悬浮球入口 | 悬浮球显示/隐藏、拖拽定位 | FLOAT-01~FLOAT-04 |
| 5 | 容器Activity | 展开/收起、Task栈管理 | CONTAINER-01~CONTAINER-05 |
| 6 | 数据持久化 | 状态保存与恢复 | PERSIST-01~PERSIST-02 |
| 7 | 独立验证 | 独立Module、编译测试 | INDEP-01~INDEP-02 |

### Success Criteria

#### Phase 4: 悬浮球入口
1. 悬浮球能显示在屏幕边缘
2. 应用在前台时悬浮球可见
3. 切换到其他App时悬浮球隐藏
4. 切回本App时悬浮球恢复显示

#### Phase 5: 容器Activity
1. 点击悬浮球展开全屏Activity
2. Activity关闭后悬浮球恢复
3. 悬浮球支持拖拽
4. Activity正确插入Task栈

#### Phase 6: 数据持久化
1. 关闭Activity时数据自动保存
2. 重新打开时数据正确恢复

#### Phase 7: 独立验证
1. 新Module可独立编译
2. 不依赖现有agent模块

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 4 | - | Pending | - |
| 5 | - | Pending | - |
| 6 | - | Pending | - |
| 7 | - | Pending | - |

### Plans

(None yet)

---

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
