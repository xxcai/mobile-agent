# Roadmap: 手机上的 AI Agent

## Milestones

- ⏳ **v2.10 响应卡片改版** — Phases 8-11 (planned)
- ✅ **v2.9 代码结构优化** — Phases 1-3 (shipped 2026-03-17)
- ✅ **v2.8 历史消息加载** — Phases 1-2 (shipped 2026-03-17)
- ✅ **v2.7 流式输出** — Phases 1 to 4 (shipped 2026-03-16)
- ✅ **v2.6 主界面重构** — Phases 1 to 6 (shipped 2026-03-13)
- ✅ **v2.5 容器Activity模块** — Phases 1-2 (shipped 2026-03-12)
- ✅ **v2.4 Agent 性能分析** — Phases 1-3 (shipped 2026-03-11)
- ✅ **v2.3 语音转文字** — Phases 1-3 (shipped 2026-03-11)
- ✅ **v2.2 App 层动态注入 Android 工具** — Phases 1-3 (shipped 2026-03-10)
- ✅ **v2.1 架构重构** — Phases 1-5 (shipped 2026-03-09)
- ✅ **v2.0 接入真实项目** — Phases v20-01 to v20-04 (shipped 2026-03-09)
- ✅ **v1.6 自定义 Skills 验证** — Phases v16-01 to v16-02 (shipped 2026-03-06)
- ✅ **v1.5 LLM → Android 调用管道** — Phases v15-01 to v15-03 (shipped 2026-03-05)
- ✅ **v1.4 Android Tools 通道** — Phases 1-4 (shipped 2026-03-05)

---

<details>
<summary>✅ v2.9 代码结构优化 (Phases 1-3) — SHIPPED 2026-03-17</summary>

- [x] Phase 1: 拆分 MainContract.View 接口 (P0) — 降低耦合
  - [x] v2.9-01-01-PLAN.md — 拆分 MainContract.View 接口 (VIEW-ISP-01) (COMPLETED)
- [x] Phase 2: 提取 StreamingManager (P0) — 解耦流式状态管理
  - [x] v2.9-02-01-PLAN.md — 创建 StreamingManager 并迁移代码 (STREAM-EXTRACT-01) (COMPLETED)
- [x] Phase 3: 统一线程池管理 (P1) — 资源优化
  - [x] v2.9-03-01-PLAN.md — 创建 ThreadPoolManager 并迁移代码 (THREAD-01) (COMPLETED)

**Cancelled:**
- Phase 4: SessionManager 提取 (取消)

**Deferred:**
- Phase 5: 依赖注入容器 (当前代码复杂度不足以支持 DI)
- Phase 6: 统一包结构
- Phase 7: 统一状态管理 (已清理代码，保持现状)

**Reference:** `.planning/milestones/v2.9-ROADMAP.md`

</details>

<details>
<summary>✅ v2.8 历史消息加载 (Phases 1-2) — SHIPPED 2026-03-17</summary>

- [x] Phase 1: 数据库加载层 (plans: 1/1) — completed 2026-03-17
  - [x] v2.8-01-01-PLAN.md — 实现 C++ SQLite JNI 桥接 (COMPLETED)
- [x] Phase 2: UI 展示层 (plans: 1/1) — completed 2026-03-17
  - [x] v2.8-02-01-PLAN.md — 验证 UI 展示层实现 (HIST-04, HIST-05) (COMPLETED)

**Note:** Phase 3 (分页加载/HIST-06) 已移除，添加到 todo 待后续处理

**Requirements:**
- Phase 1: HIST-01, HIST-02, HIST-03 ✅
- Phase 2: HIST-04, HIST-05 ✅
- Phase 3: HIST-06 ❌ (已移除)

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

---

<details>
<summary>⏳ v2.10 响应卡片改版 (Phases 8-11) — PLANNED</summary>

- [ ] Phase 8: 响应卡片数据结构 (P0) — 大卡片布局
  - [ ] v2.10-08-01-PLAN.md — 响应卡片统一为大卡片布局 (RESP-01)
  - [ ] v2.10-08-02-PLAN.md — 工具区按需显示 (RESP-02)
  - [ ] v2.10-08-03-PLAN.md — think区按需显示 (RESP-03)
  - [ ] v2.10-08-04-PLAN.md — 正文区 Markdown 支持 (RESP-04)

- [ ] Phase 9: 流式文本处理 (P0) — think块解析与展示
  - [ ] v2.10-09-01-PLAN.md — 解析 onTextDelta 判断 think 块 (STREAM-01)
  - [ ] v2.10-09-02-PLAN.md — think 内容增量追加 (STREAM-02)
  - [ ] v2.10-09-03-PLAN.md — 正文内容增量追加 (STREAM-03)

- [ ] Phase 10: 工具调用状态 (P0) — 工具状态展示
  - [ ] v2.10-10-01-PLAN.md — onToolUse 展示工具 (TOOL-01)
  - [ ] v2.10-10-02-PLAN.md — onToolResult 标记完成 (TOOL-02)

- [ ] Phase 11: 状态管理与测试 (P1) — 状态转换与整合
  - [ ] v2.10-11-01-PLAN.md — 状态转换逻辑 (STATE-01, STATE-02)
  - [ ] v2.10-11-02-PLAN.md — 整合测试

**Requirements:**
- Phase 8: RESP-01, RESP-02, RESP-03, RESP-04 ⏳
- Phase 9: STREAM-01, STREAM-02, STREAM-03 ⏳
- Phase 10: TOOL-01, TOOL-02 ⏳
- Phase 11: STATE-01, STATE-02 ⏳

</details>
