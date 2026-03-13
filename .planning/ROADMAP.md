# Roadmap: 手机上的 AI Agent

## Milestones

- 🔄 **v2.6 主界面重构** — Phases 1 to 6 (in progress)
- ✅ **v2.5 容器Activity模块** — Phases 1 to 2 (shipped 2026-03-12)
- ✅ **v2.4 Agent 性能分析** — Phases 1 to 3 (shipped 2026-03-11)
- ✅ **v2.3 语音转文字** — Phases 1 to 3 (shipped 2026-03-11)
- ✅ **v2.2 App 层动态注入 Android 工具** — Phases 1 to 3 (shipped 2026-03-10)
- ✅ **v2.1 架构重构** — Phases 1 to 5 (shipped 2026-03-09)
- ✅ **v2.0 接入真实项目** — Phases v20-01 to v20-04 (shipped 2026-03-09)
- ✅ **v1.6 自定义 Skills 验证** — Phases v16-01 to v16-02 (shipped 2026-03-06)
- ✅ **v1.5 LLM → Android 调用管道** — Phases v15-01 to v15-03 (shipped 2026-03-05)
- ✅ **v1.4 Android Tools 通道** — Phases 1-4 (shipped 2026-03-05)

---

## v2.6 主界面重构

### Phase 1: 模块合并 (Complete)
**Goal:** floating-ball 模块合并到 android-agent

**Requirements:** FLOAT-02

**Success criteria:**
1. floating-ball 代码合并到 android-agent 模块
2. 功能正常工作

**Plans:**
1/1 plans complete

### Phase 2: MainActivity + 悬浮球基础 (Complete)
**Goal:** 新启动页，悬浮球浮在 MainActivity 上

**Requirements:** MAIN-01, MAIN-02, FLOAT-01

**Success criteria:**
1. 新建 MainActivity 作为启动页
2. app 启动时加载 MainActivity
3. 悬浮球在 MainActivity 上正确显示

**Plans:**
1/1 plans complete

### Phase 3: Fragment 容器化 (Complete)
**Goal:** AgentActivity 界面抽取为 Fragment，集成到 ContainerActivity

**Requirements:** FRAG-01, FRAG-02, FRAG-03

**Success criteria:**
1. AgentActivity 界面抽取为 Fragment
2. Fragment 正确加载到 ContainerActivity
3. 原有跳转逻辑保留

**Plans:**
1/1 plans complete

### Phase 4: Agent 后台运行 (Complete)
**Goal:** Agent 后台运行，支持记忆恢复

**Requirements:** AGENT-01, AGENT-02, AGENT-03, AGENT-04

**Success criteria:**
1. Agent 可以在后台持续运行
2. 重新打开 Agent 界面时恢复之前显示
3. 重新打开 Agent 界面时更新最新进度

**Plans:**
1/1 plans complete

### Phase 5: 界面优化 + 语音保留 (Complete)
**Goal:** AgentActivity 界面优化，保留语音功能

**Requirements:** UI-01, VOICE-01

**Success criteria:**
1. AgentActivity 界面优化完成（见 discuss 环节）
2. 语音功能正常工作

**Plans:**
1/1 plans complete

### Phase 6: 整合测试
**Goal:** 端到端测试所有功能

**Success criteria:**
1. MainActivity 启动正常
2. 悬浮球显示正常
3. Fragment 切换正常
4. Agent 后台运行正常
5. 界面优化生效
6. 语音功能正常

**Plans:**
- [ ] 06-01-PLAN.md — 6 个功能点手动测试验证

---

<details>
<summary>✅ v2.5 容器Activity模块 (Phases 1-2) — SHIPPED 2026-03-12</summary>

- [x] Phase 1: 悬浮球入口 (1/1 plans) — completed 2026-03-12
- [x] Phase 2: 容器Activity (2/2 plans) — completed 2026-03-12

**Key achievements:**
- 创建独立的 floating-ball Android Module
- 实现悬浮球显示/隐藏/拖拽
- 实现 ContainerActivity 半透明容器
- 修复点击外部收起和悬浮球恢复问题

**See:** `.planning/milestones/v2.5-ROADMAP.md`

</details>

---

*Roadmap updated: 2026-03-13*
