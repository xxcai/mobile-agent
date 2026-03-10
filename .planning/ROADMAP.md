# Roadmap: 手机上的 AI Agent

## Milestones

- 🚧 **v2.3 [Next Milestone]** — (planning)
- ✅ **v2.2 App 层动态注入 Android 工具** — Phases 1 to 3 (shipped 2026-03-10)
- ✅ **v2.1 架构重构** — Phases 1 to 5 (shipped 2026-03-09)
- ✅ **v2.0 接入真实项目** — Phases v20-01 to v20-04 (shipped 2026-03-09)
- ✅ **v1.6 自定义 Skills 验证** — Phases v16-01 to v16-02 (shipped 2026-03-06)
- ✅ **v1.5 LLM → Android 调用管道** — Phases v15-01 to v15-03 (shipped 2026-03-05)
- ✅ **v1.4 Android Tools 通道** — Phases 1-4 (shipped 2026-03-05)

---

## v2.3 (Next)

*Planning phase - use /gsd:new-milestone to define requirements*

---

## v2.2 (Shipped)

<details>
<summary>✅ v2.2 App 层动态注入 Android 工具 (Phases 1 to 3) — SHIPPED 2026-03-10</summary>

**Goal:** 实现 app 层可动态注册自定义 Tool 到 AndroidToolManager，使 app 层能够扩展 Android 能力。

**Phases:**
- [x] Phase 1: Tool 注册接口 (1/1 plans) — completed 2026-03-10
- [x] Phase 2: Tool 生命周期管理 (1/1 plans) — completed 2026-03-10
- [x] Phase 3: 动态 Tool 调用与验证 (1/1 plans) — completed 2026-03-10

**Key Deliverables:**
- App 层 registerTool() 动态注册接口
- Tool 生命周期管理（查询、注销、批量操作、主动推送）
- SearchContactsTool 和 SendImMessageTool 迁移到 app 层
- 删除静态 assets/tools.json，改用动态生成

**See:** [.planning/milestones/v2.2-ROADMAP.md](./milestones/v2.2-ROADMAP.md)

</details>

---

## v2.1 (Shipped)

<details>
<summary>✅ v2.1 架构重构 (Phases 1 to 5) — SHIPPED 2026-03-09</summary>

**Goal:** 将 app 模块拆分为 app + agent-android，实现三层架构

**Phases:**
- [x] Phase 1: 新增 agent-android 模块 (1/1 plans)
- [x] Phase 2: 重命名模块 (1/1 plans) — completed 2026-03-09
- [x] Phase 3: 代码下沉 (completed 2026-03-09)
- [x] Phase 4: 启动流程梳理 (completed 2026-03-09)
- [x] Phase 5: 接入文档 (5/5 plans)

**Key Deliverables:**
- agent-android 模块（Android 适配层）
- agent-core 模块（纯 Java 核心）
- app 简化为接入演示壳

**See:** [.planning/milestones/v2.1-ROADMAP.md](./milestones/v2.1-ROADMAP.md)

</details>

---

## v2.0 (Shipped)

<details>
<summary>✅ v2.0 接入真实项目 (Phases v20-01 to v20-04) — SHIPPED 2026-03-09</summary>

- [x] Phase v20-01: 代码清理
- [x] Phase v20-02: 重命名 — completed 2026-03-06
- [x] Phase v20-03: 代码迁移 — completed 2026-03-09
- [x] Phase v20-04: 验证 — completed 2026-03-09

**See:** [.planning/milestones/v2.0-ROADMAP.md](./milestones/v2.0-ROADMAP.md)

</details>

---

## v1.6 (Shipped)

<details>
<summary>✅ v1.6 自定义 Skills 验证 (Phases v16-01 to v16-02) — SHIPPED 2026-03-06</summary>

- [x] Phase v16-01: 自定义 Skills 机制 — completed 2026-03-06
- [x] Phase v16-02: Agent 调用 Tools — completed 2026-03-06

**See:** [.planning/milestones/v1.6-ROADMAP.md](./milestones/v1.6-ROADMAP.md)

</details>

---

## Progress

| Milestone | Phase Range | Status | Completed |
|-----------|-------------|--------|-----------|
| v2.2 | 1 to 3 | ✓ Complete | 2026-03-10 |
| v2.1 | 1 to 5 | ✓ Complete | 2026-03-09 |
| v2.0 | v20-01 to v20-04 | ✓ Complete | 2026-03-09 |
| v1.6 | v16-01 to v16-02 | ✓ Complete | 2026-03-06 |
| v1.5 | v15-01 to v15-03 | ✓ Complete | 2026-03-05 |
| v1.4 | 1-4 | ✓ Complete | 2026-03-05 |

---

*Roadmap updated: 2026-03-10*
