# Roadmap: 手机上的 AI Agent

## Milestones

- 🚧 **v2.3 语音转文字** — Phases 1 to 3 (planning)
- ✅ **v2.2 App 层动态注入 Android 工具** — Phases 1 to 3 (shipped 2026-03-10)
- ✅ **v2.1 架构重构** — Phases 1 to 5 (shipped 2026-03-09)
- ✅ **v2.0 接入真实项目** — Phases v20-01 to v20-04 (shipped 2026-03-09)
- ✅ **v1.6 自定义 Skills 验证** — Phases v16-01 to v16-02 (shipped 2026-03-06)
- ✅ **v1.5 LLM → Android 调用管道** — Phases v15-01 to v15-03 (shipped 2026-03-05)
- ✅ **v1.4 Android Tools 通道** — Phases 1-4 (shipped 2026-03-05)

---

## v2.3 (Current)

**Goal:** 在聊天界面添加语音输入能力，实现按压说话、实时语音转文字功能

### Phases

- [x] **Phase 1: 语音按钮 UI** - 在输入框右侧添加语音按钮 (Plan 1 completed)
- [x] **Phase 2: 语音交互逻辑** - 按压录音、实时转写、松手结束 (completed 2026-03-11)
- [ ] **Phase 3: 语音能力接入** - 接口定义与注入

---

### Phase 1: 语音按钮 UI

**Goal:** 在聊天界面添加语音按钮，用户可以看到并控制语音功能

**Depends on:** Nothing (first phase)

**Requirements:** VT-01, VT-02, VT-03

**Success Criteria** (what must be TRUE):

1. 用户可以在输入框右侧看到语音按钮（当语音功能开启时）
2. 按钮顺序为 etMessage → btnVoice → btnSend，符合视觉布局
3. 按钮在 app 关闭语音功能时默认隐藏，开启时显示

**Plans:** 1/1 completed (Plan 1: 语音按钮 UI)

---

### Phase 2: 语音交互逻辑

**Goal:** 用户可以通过按压按钮进行语音输入，系统实时返回转写结果

**Depends on:** Phase 1

**Requirements:** VT-04, VT-05, VT-06

**Success Criteria** (what must be TRUE):

1. 用户按压语音按钮时，界面显示录音动画提示，语音识别开始
2. 用户讲话过程中，语音转文字工具实时返回识别结果（完整文本），实时更新到输入框
3. 用户松手时，录音动画结束，语音识别停止

**Plans:** 1/1 plans complete

Plans:
- [ ] 02-PLAN.md — 按压说话模式、录音动画、IVoiceRecognizer 接口、Mock 实现

---

### Phase 3: 语音能力接入

**Goal:** agent-android 提供语音转文字接口，上层 app 可注入具体实现

**Depends on:** Phase 2

**Requirements:** VT-07, VT-08

**Success Criteria** (what must be TRUE):

1. agent-android 提供 IVoiceToText 接口定义，包含语音识别能力
2. 上层 app 模块可以通过接口注入语音转文字能力实现
3. 注入的实现能够正确回调识别结果到 UI 层

**Plans:** TBD

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1 - 语音按钮 UI | 1/1 | Completed | 2026-03-11 |
| 2 - 语音交互逻辑 | 1/1 | Complete   | 2026-03-11 |
| 3 - 语音能力接入 | 0/1 | Not started | - |

---

*Roadmap updated: 2026-03-11*
