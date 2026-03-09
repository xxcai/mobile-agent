# Roadmap: 手机上的 AI Agent

## Milestones

- ✅ **v2.1 架构重构** — Phases 1 to 5 (shipped 2026-03-09)
- ✅ **v2.0 接入真实项目** — Phases v20-01 to v20-04 (shipped 2026-03-09)
- ✅ **v1.6 自定义 Skills 验证** — Phases v16-01 to v16-02 (shipped 2026-03-06)
- ✅ **v1.5 LLM → Android 调用管道** — Phases v15-01 to v15-03 (shipped 2026-03-05)
- ✅ **v1.4 Android Tools 通道** — Phases 1-4 (shipped 2026-03-05)

---

## v2.1 (In Progress)

<details>
<summary>✅ v2.1 架构重构 (Phases 1 to 5) — SHIPPED 2026-03-09</summary>

**Goal:** 将 app 模块拆分为 app + agent-android，实现三层架构

**Phases:**
- [x] Phase 1: 新增 agent-android 模块 (1/1 plans)
- [x] Phase 2: 重命名模块 (agent → agent-core, app 简化为壳) (1/1 plans) (completed 2026-03-09)
- [x] Phase 3: 代码下沉 (AndroidToolManager, WorkspaceManager, Tools → agent-android) (completed 2026-03-09)
- [x] Phase 4: 启动流程梳理 (内存泄漏、主线程阻塞) (completed 2026-03-09)
- [x] Phase 5: 接入文档 (5/5 plans)

**Key Deliverables:**
- agent-android 模块（Android 适配层）
- agent-core 模块（纯 Java 核心，原 agent 重命名）
- app 简化为接入演示壳
- 启动流程优化
- 完整接入文档

**Plans:**
- [x] 01-agent-android-module/01-PLAN.md — 新增 agent-android 模块
- [x] 02-rename-modules/02-PLAN.md — 重命名模块 (agent → agent-core, app 简化为壳)
- [x] 05-api-key/05-01-PLAN.md — 扩展 ToolExecutor 接口
- [x] 05-api-key/05-02-PLAN.md — 6 个 Tool 实现新方法
- [x] 05-api-key/05-03-PLAN.md — AndroidToolManager 动态生成 tools.json
- [x] 05-api-key/05-04-PLAN.md — WorkspaceManager Skills 初始化修改
- [x] 05-api-key/05-05-PLAN.md — 接入文档（README、API）

</details>

---

## v2.0 (Shipped)

<details>
<summary>✅ v2.0 接入真实项目 (Phases v20-01 to v20-04) — SHIPPED 2026-03-09</summary>

- [x] Phase v20-01: 代码清理 (done as part of v20-02)
- [x] Phase v20-02: 重命名 (1/1 plans) — completed 2026-03-06
- [x] Phase v20-03: 代码迁移 (3/3 plans) — completed 2026-03-09
- [x] Phase v20-04: 验证 (1/1 plans) — completed 2026-03-09

**Key Deliverables:**
- Nanobot → MobileAgent 重命名完成
- 平台工具从 agent 迁移到 app 模块
- agent 模块实现纯 Java AAR（无 Android 依赖）
- Agent API 改为接收 JSON 字符串
- APK 编译成功，功能验证通过

**Known Gaps (Tech Debt):**
- CLEAN-01~CLEAN-05: 代码清理未正式验证（实际已完成）

</details>

---

## v1.6 (Shipped)

<details>
<summary>✅ v1.6 自定义 Skills 验证 (Phases v16-01 to v16-02) — SHIPPED 2026-03-06</summary>

- [x] Phase v16-01: 自定义 Skills 机制 (1/1 plans) — completed 2026-03-06
- [x] Phase v16-02: Agent 调用 Tools (1/1 plans) — completed 2026-03-06

**Key Deliverables:**
- SKILL.md 格式定义和 YAML frontmatter 解析
- C++ 层 SkillLoader 加载机制
- search_contacts / send_im_message Android Tools
- im_sender 测试 Skill

</details>

---

## Progress

| Milestone | Phase Range | Status | Completed |
|-----------|-------------|--------|-----------|
| v2.1 | 1 to 5 | ✓ Complete | 2026-03-09 |
| v2.0 | v20-01 to v20-04 | ✓ Complete | 2026-03-09 |
| v1.6 | v16-01 to v16-02 | ✓ Complete | 2026-03-06 |
| v1.5 | v15-01 to v15-03 | ✓ Complete | 2026-03-05 |
| v1.4 | 1-4 | ✓ Complete | 2026-03-05 |

---

*Roadmap updated: 2026-03-09*
