# Roadmap: 手机上的 AI Agent

## Milestones

- 🚧 **v2.0 接入真实项目** — Phases v20-01 to v20-04 (in progress)
- ✅ **v1.6 自定义 Skills 验证** — Phases v16-01 to v16-02 (shipped 2026-03-06)
- ✅ **v1.5 LLM → Android 调用管道** — Phases v15-01 to v15-03 (shipped 2026-03-05)
- ✅ **v1.4 Android Tools 通道** — Phases 1-4 (shipped 2026-03-05)

---

## v2.0 (In Progress)

**Goal:** 重构代码架构，清理旧代码，统一命名

### Phase v20-01: 代码清理

**Goal:** 删除 Vue 相关代码，清理 lib 模块中的旧 HTTP 连接代码

**Requirements:**
- CLEAN-01: 删除 vue/ 目录
- CLEAN-02: 清理 HttpNanobotApi
- CLEAN-03: 清理 MockNanobotApi
- CLEAN-04: 清理 NanobotConfig
- CLEAN-05: 清理相关测试代码

**Success Criteria:**
1. vue/ 目录已删除
2. lib 模块中无 Nanobot 相关 HTTP 代码
3. 相关测试文件已清理

**Plans:**
- [x] v20-01-01-PLAN.md — 删除 Vue 相关代码，清理 HTTP 连接

---

### Phase v20-02: 重命名

**Goal:** 将所有 Nanobot 相关名称统一重命名为 MobileAgent

**Requirements:**
- RENAME-01: NanobotApi → MobileAgentApi
- RENAME-02: NativeNanobotApi → NativeMobileAgentApi
- RENAME-03: NativeNanobotApiAdapter → NativeMobileAgentApiAdapter
- RENAME-04: MainPresenter 中 nanobot 相关方法重命名
- RENAME-05: MainActivity 中 nanobot 相关引用更新
- RENAME-06: C++ 层 nanobot 相关命名更新

**Success Criteria:**
1. Java 层所有 Nanobot 相关类已重命名
2. C++ 层相关命名已更新
3. 项目可编译通过

**Plans:**
1/1 plans complete

---

### Phase v20-03: 代码迁移

**Goal:** 将平台相关逻辑从 agent 模块上移到 app 模块

**Requirements:**
- MIGRATE-01: 分析 agent 模块代码，识别可上移到 app 的平台逻辑
- MIGRATE-02: 保留 Android 管道能力在 agent（AAR 需提供的能力）
- MIGRATE-03: 将平台相关逻辑从 agent 移至 app 模块

**Success Criteria:**
1. agent 模块只保留 C++ 核心 + Android 管道 + JNI 适配
2. 平台相关逻辑已在 app 模块实现
3. 编译通过

**Plans:**
- [ ] v20-03-01-PLAN.md — 将 Android Tools 和平台相关逻辑从 agent 移至 app

---

### Phase v20-04: 验证

**Goal:** 确保重构后项目正常工作

**Requirements:**
- MIGRATE-04: 确保重构后 build 正常
- VERIFY-02: 项目 assembleDebug 成功
- VERIFY-03: 重命名后无编译错误
- VERIFY-04: 原有功能（聊天、Tool 调用）正常工作

**Success Criteria:**
1. assembleDebug 成功
2. 无编译错误和警告
3. APK 可以正常安装运行

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
| v2.0 | v20-01 to v20-04 | ◆ In Progress | — |
| v1.6 | v16-01 to v16-02 | ✓ Complete | 2026-03-06 |
| v1.5 | v15-01 to v15-03 | ✓ Complete | 2026-03-05 |
| v1.4 | 1-4 | ✓ Complete | 2026-03-05 |

---

*Roadmap updated: 2026-03-09*
