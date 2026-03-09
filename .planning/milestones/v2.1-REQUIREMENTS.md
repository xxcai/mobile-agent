# Requirements: Mobile Agent

**Defined:** 2026-03-09
**Core Value:** 让用户通过自然对话，指挥手机自动完成日常任务。

## v1 Requirements

架构重构 - 三层模块化

### 模块结构

- [x] **ARCH-01**: 新增 agent-android 模块（Android 适配层）
- [x] **ARCH-02**: agent 模块重命名为 agent-core
- [x] **ARCH-03**: app 模块重构为仅包含 Activity 和简单绑定（壳）

### 代码下沉

- [x] **ARCH-04**: AndroidToolManager 下沉到 agent-android
- [x] **ARCH-05**: WorkspaceManager 下沉到 agent-android
- [x] **ARCH-06**: 所有 Android Tools 下沉到 agent-android (ShowToast, DisplayNotification, ReadClipboard, TakeScreenshot, SearchContacts, SendImMessage)
- [x] **ARCH-07**: NativeMobileAgentApiAdapter 下沉到 agent-android（转为接口）

### 启动流程

- [x] **ARCH-08**: 梳理应用启动流程
- [x] **ARCH-09**: 检查并修复 Context 内存泄漏
- [x] **ARCH-10**: 检查并修复主线程阻塞问题

### 接入文档

- [x] **ARCH-11**: README 文档（模块说明、依赖关系、快速开始）
- [x] **ARCH-12**: 接入示例（标准项目结构、config.json.template 示例）
- [x] **ARCH-13**: API 说明文档

---

## v2 Requirements

暂无

---

## Out of Scope

| Feature | Reason |
|---------|--------|
| UI 大改 | app 层作为演示壳，保持现有 UI 不变 |
| 新功能 | 专注于架构重构 |

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| ARCH-01 | Phase 1 | Complete |
| ARCH-02 | Phase 2 | Complete |
| ARCH-03 | Phase 2 | Complete |
| ARCH-04 | Phase 3 | Complete |
| ARCH-05 | Phase 3 | Complete |
| ARCH-06 | Phase 3 | Complete |
| ARCH-07 | Phase 3 | Complete |
| ARCH-08 | Phase 4 | Complete |
| ARCH-09 | Phase 4 | Complete |
| ARCH-10 | Phase 4 | Complete |
| ARCH-11 | Phase 5 | Complete |
| ARCH-12 | Phase 5 | Complete |
| ARCH-13 | Phase 5 | Complete |

**Coverage:**
- v1 requirements: 13 total
- Mapped to phases: 13
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-09*
*Last updated: 2026-03-09 after v2.1 milestone started*
