# Roadmap: 手机上的 AI Agent

## Milestones

- ⏳ **v2.8 历史消息加载** — Phases 1 to 3 (planning)
- ✅ **v2.7 流式输出** — Phases 1 to 4 (shipped 2026-03-16)
- ✅ **v2.6 主界面重构** — Phases 1 to 6 (shipped 2026-03-13)
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

<details>
<summary>⏳ v2.8 历史消息加载 (Phases 1-3) — In Progress</summary>

- [x] Phase 1: 数据库加载层 (plans: 1/1) — completed 2026-03-17
  - [x] v2.8-01-01-PLAN.md — 实现 C++ SQLite JNI 桥接 (COMPLETED)
- [x] Phase 2: UI 展示层 (plans: 1/1) — completed 2026-03-17
  - [x] v2.8-02-01-PLAN.md — 验证 UI 展示层实现 (HIST-04, HIST-05) (COMPLETED)
- [ ] Phase 3: 分页加载 (plans: TBD)

**Requirements:**
- Phase 1: HIST-01, HIST-02, HIST-03
- Phase 2: HIST-04, HIST-05
- Phase 3: HIST-06

</details>

<details>
<summary>✅ v2.7 流式输出 (Phases 1-4) — SHIPPED 2026-03-16</summary>

- [x] Phase 1: JNI 底层桥接 (1/1 plans) — completed 2026-03-16
- [x] Phase 2: Java API 层 (1/1 plans) — completed 2026-03-16
- [x] Phase 3: UI 流式交互 (1/1 plans) — completed 2026-03-16
- [x] Phase 4: 异常处理与取消 (2/2 plans) — completed 2026-03-16

**See:** `.planning/milestones/v2.7-ROADMAP.md`

</details>

<details>
<summary>✅ v2.6 主界面重构 (Phases 1-6) — SHIPPED 2026-03-13</summary>

- [x] Phase 1: 模块合并 (1/1 plans) — completed 2026-03-12
- [x] Phase 2: MainActivity + 悬浮球基础 (1/1 plans) — completed 2026-03-12
- [x] Phase 3: Fragment 容器化 (1/1 plans) — completed 2026-03-12
- [x] Phase 4: Agent 后台运行 (1/1 plans) — completed 2026-03-12
- [x] Phase 5: 界面优化 + 语音保留 (1/1 plans) — completed 2026-03-13
- [x] Phase 6: 整合测试 (1/1 plans) — completed 2026-03-13

**Key achievements:**
- floating-ball 模块合并到 android-agent
- MainActivity 启动页 + 悬浮球控制
- Fragment 容器化（AgentFragment + ContainerActivity）
- 会话持久化（SharedPreferences + Gson）
- 界面优化（圆角、不透明背景、底部输入框修复）
- 6 项功能整合测试通过

**See:** `.planning/milestones/v2.6-ROADMAP.md`

</details>

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

*Roadmap updated: 2026-03-17*
