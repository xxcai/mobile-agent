# Roadmap: 手机上的 AI Agent

## Milestones

- ✅ **v2.2 App 层动态注入 Android 工具** — Phases 1 to 3 (in progress)
- ✅ **v2.1 架构重构** — Phases 1 to 5 (shipped 2026-03-09)
- ✅ **v2.0 接入真实项目** — Phases v20-01 to v20-04 (shipped 2026-03-09)
- ✅ **v1.6 自定义 Skills 验证** — Phases v16-01 to v16-02 (shipped 2026-03-06)
- ✅ **v1.5 LLM → Android 调用管道** — Phases v15-01 to v15-03 (shipped 2026-03-05)
- ✅ **v1.4 Android Tools 通道** — Phases 1-4 (shipped 2026-03-05)

---

## v2.2 (In Progress)

<details>
<summary>🚧 v2.2 App 层动态注入 Android 工具 (Phases 1 to 3) — IN PROGRESS</summary>

**Goal:** 实现 app 层可动态注册自定义 Tool 到 AndroidToolManager，使 app 层能够扩展 Android 能力。

### Phase 1: Tool 注册接口

**Goal**: App 层可以通过接口注册自定义 Tool 到 AndroidToolManager，支持运行时动态添加

**Depends on**: Nothing (first phase)

**Requirements**: INJT-01, INJT-02, INJT-03

**Success Criteria** (what must be TRUE):
  1. App 层可以通过 registerTool(name, description, executor) 接口注册自定义 Tool
  2. 注册的 Tool 可以在应用运行期间动态添加，无需重启
  3. Tool 注册时需要提供名称、描述和执行器，三者缺一不可

**Plans**: 1 plan

- [x] 01-tool-register/01-PLAN.md — 添加 registerTool() 接口，内置 Tool 迁移到 app 层

---

### Phase 2: Tool 生命周期管理

**Goal**: 支持 Tool 的查询、注销，并确保变更能主动推送给 Agent（LLM）

**Depends on**: Phase 1

**Requirements**: INJT-04, INJT-05, INJT-06

**Design Decisions**:
- 批量操作优化：提供 `registerTools(Map)` 和 `unregisterTools(List)` 接口，一次性注册/注销多个 Tool 后只刷新一次 tools.json
- 主动推送机制：Tool 变更后立即调用 `generateToolsJson()` + `NativeMobileAgentApi.setToolsJson()` 推送到 native 层，使 LLM 感知变更
- 静态声明：仅使用动态生成，assets/tools.json 不再需要

**Success Criteria** (what must be TRUE):
  1. App 层可以查询已注册的所有 Tool 列表（getToolNames）
  2. App 层可以注销已注册的 Tool（支持内置和自定义 Tool）
  3. Tool 变更后主动推送给 Agent，LLM 能感知到新增/移除的 Tool
  4. 提供批量操作接口，避免频繁刷新

**Plans**: 1 plan

- [ ] 02-tool-lifecycle/02-PLAN.md — 添加查询、注销和批量操作接口

---

### Phase 3: 动态 Tool 调用与验证

**Goal**: Agent 能够调用通过 App 层注册的 Tool，完成示例验证

**Depends on**: Phase 2

**Requirements**: INJT-07, INJT-08, INJT-09, INJT-10, INJT-11

**Design Decisions**:
- 验证对象选择：将 SearchContactsTool 和 SendImMessageTool 从 agent-android 迁移到 app 层，作为验证内置 Tool 迁移到 app 层的示例

**Success Criteria** (what must be TRUE):
  1. Agent 可以调用通过 App 层注册的 Tool（通过 Tool 名称）
  2. Tool 执行结果可以返回给 Agent（LLM）
  3. 自定义 Tool 与内置 Tool 使用相同的调用通道（无差异）
  4. SearchContactsTool 和 SendImMessageTool 从 agent-android 迁移到 app 层注册
  5. 验证 SearchContactsTool 和 SendImMessageTool 可以被 Agent 正常调用并返回结果

**Plans: 1 plan

- [x] 03-tool-call/03-01-PLAN.md — 迁移 SearchContactsTool 和 SendImMessageTool 到 app 层 (编译通过，等待运行时验证)

</details>

---

## v2.1 (Shipped)

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
1/1 plans complete
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
| v2.2 | 1 to 3 | 1/1 | Complete    | 2026-03-10 | v2.1 | 1 to 5 | ✓ Complete | 2026-03-09 |
| v2.0 | v20-01 to v20-04 | ✓ Complete | 2026-03-09 |
| v1.6 | v16-01 to v16-02 | ✓ Complete | 2026-03-06 |
| v1.5 | v15-01 to v15-03 | ✓ Complete | 2026-03-05 |
| v1.4 | 1-4 | ✓ Complete | 2026-03-05 |

---

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Tool 注册接口 | 1/1 | Complete | 2026-03-10 |
| 2. Tool 生命周期管理 | 1/1 | Complete | 2026-03-10 |
| 3. 动态 Tool 调用与验证 | 1/1 | Complete    | 2026-03-10 |

---

*Roadmap updated: 2026-03-10*
