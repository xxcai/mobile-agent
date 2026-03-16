# Mobile Agent v2.7 - 流式输出

**Milestone:** v2.7
**Goal:** 将 MobileAgent 的同步 chat 接口改为流式输出，实现 AI 响应实时展示到 UI，提升用户等待体验。

## Phases

- [x] **Phase 1: JNI 底层桥接** - C++ 到 Java 事件通道打通
- [x] **Phase 2: Java API 层流式接口** - 业务抽象层
- [ ] **Phase 3: UI 流式交互** - 消息增量更新
- [ ] **Phase 4: 异常处理与取消** - 网络错误、取消功能

## Phase Details

### Phase 1: JNI 底层桥接

**Goal:** C++ 层流式事件通过 JNI 回调到 Java 层

**Depends on:** Nothing (first phase)

**Requirements:** STREAM-01, STREAM-02

**Success Criteria** (what must be TRUE):
  1. C++ 层 chat_stream() 正确发送 text_delta、message_end、tool_use 等事件
  2. JNI 层 nativeSendMessageStream() 方法可调用，返回事件到 Java 层
  3. Java 层 AgentEventListener 接口接收事件回调
  4. JNI 线程安全正确处理（AttachCurrentThread + DetachCurrentThread）

**Plans:** 1 plan
- [x] v2.7-01-01-PLAN.md — JNI 底层桥接（AgentEventListener + nativeSendMessageStream）

---

### Phase 2: Java API 层流式接口

**Goal:** 提供干净的流式 API 接口给 Presenter 层

**Depends on:** Phase 1

**Requirements:** STREAM-03

**Success Criteria** (what must be TRUE):
  1. MobileAgentApi 新增 sendMessageStream(String, AgentEventListener) 接口
  2. NativeMobileAgentApi 实现流式接口，内部调用 JNI 方法
  3. API 层正确管理 AgentEventListener 生命周期

**Plans:** 1 plan
- [x] v2.7-02-01-PLAN.md — Java API 层流式接口

---

### Phase 3: UI 流式交互

**Goal:** 用户点击发送后实时看到 AI 增量响应

**Depends on:** Phase 2

**Requirements:** UI-01, UI-02, UI-03, UI-04, UI-05, UI-06

**Success Criteria** (what must be TRUE):
  1. 点击发送按钮触发 sendMessageStream() 而非 sendMessage()
  2. 流式响应过程中发送按钮被禁用
  3. 流式响应过程中显示打字机动效
  4. AI 返回的文本片段实时增量展示在消息列表
  5. 工具调用消息动态构建和展示
  6. 消息状态正确切换：发送中 → 接收中 → 完成

**Plans:** 1 plan
- [ ] v2.7-03-01-PLAN.md — 流式文本增量更新（UI-03, UI-04）

---

### Phase 4: 异常处理与取消

**Goal:** 用户可取消流式响应，系统正确处理各类错误

**Depends on:** Phase 3

**Requirements:** ERROR-01, ERROR-02, ERROR-03, ERROR-04

**Success Criteria** (what must be TRUE):
  1. 网络错误时展示错误提示并提供重试按钮
  2. API 错误时通过 message_end 的 finish_reason 判断并展示错误
  3. 用户可主动取消流式请求
  4. 取消后正确释放资源、UI 状态重置

**Plans:** TBD

---

## Progress Table

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. JNI 底层桥接 | 1/1 | ✓ Complete | 2026-03-16 |
| 2. Java API 层流式接口 | 1/1 | ✓ Complete | 2026-03-16 |
| 3. UI 流式交互 | 0/1 | In Progress | - |
| 4. 异常处理与取消 | 0/1 | Not started | - |

---

## Coverage Map

| Phase | Requirements | Count |
|-------|--------------|-------|
| 1 - JNI 底层桥接 | STREAM-01, STREAM-02 | 2 |
| 2 - Java API 层 | STREAM-03 | 1 |
| 3 - UI 流式交互 | UI-01, UI-02, UI-03, UI-04, UI-05, UI-06 | 6 |
| 4 - 异常处理与取消 | ERROR-01, ERROR-02, ERROR-03, ERROR-04 | 4 |

**Total:** 13/13 requirements mapped ✓
