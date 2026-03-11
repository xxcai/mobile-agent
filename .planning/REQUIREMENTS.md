# Requirements: Mobile Agent - Agent 性能分析

**Defined:** 2026-03-11
**Core Value:** 让用户通过自然对话，指挥手机自动完成日常任务。

---

# v2.4 Requirements (Agent 性能分析)

## v2.4.1 统一日志格式

**问题:** 当前日志宏每次调用都执行 `is_initialized()` 检查，参数会被求值

### Requirements

- [ ] **LOG-01**: 改进日志宏，使用 spdlog 原生宏或条件编译，消除每次调用检查
- [ ] **LOG-02**: 确保日志不打印敏感信息（API keys, 用户敏感数据）
- [ ] **LOG-03**: 统一日志前缀格式：`[模块名] 消息`

**验收标准:**
- 日志宏不再每次调用 `is_initialized()`
- 日志中不包含 API key、完整用户输入等敏感信息

---

## v2.4.2 补充关键路径日志

**问题:** 关键路径缺少耗时日志，难以定位性能瓶颈

### Requirements

#### HTTP 层日志增强
- [ ] **HTTP-01**: 添加请求开始/结束时间戳
- [ ] **HTTP-02**: 添加请求耗时统计（毫秒级）
- [ ] **HTTP-03**: 日志格式：`[HTTP] POST /chat/completions - 200 OK (1234ms)`

#### LLM 调用日志增强
- [ ] **LLM-01**: 添加流式响应总耗时
- [ ] **LLM-02**: 添加每轮迭代耗时
- [ ] **LLM-03**: 日志格式：`[LLM] Stream completed: 1234ms, tokens=567`

#### MCP 调用日志增强
- [ ] **MCP-01**: 添加 MCP 方法调用耗时
- [ ] **MCP-02**: 日志格式：`[MCP] tools/call completed: 234ms`

#### Tool 执行日志增强
- [ ] **TOOL-01**: 添加 tool 执行耗时
- [ ] **TOOL-02**: 日志格式：`[TOOL] execute_tool(name=xxx) completed: 56ms`

#### Agent Loop 日志增强
- [ ] **LOOP-01**: 添加每轮迭代耗时
- [ ] **LOOP-02**: 添加整体处理耗时

**验收标准:**
- 每个关键路径都有耗时日志
- 日志包含模块前缀，便于过滤

---

## v2.4.3 性能分析与优化

### Requirements

- [ ] **PERF-01**: 在关键路径添加 ScopedTimer（RAII 风格的耗时测量）
- [ ] **PERF-02**: 识别耗时热点函数
- [ ] **PERF-03**: 可选的 perfetto 跟踪点（生产环境可关闭）

**验收标准:**
- 能够从日志中识别出最耗时的操作

---

## v2.4.4 脱敏处理

### Requirements

- [ ] **SEC-01**: API Key 只显示前后 4 位（如 `sk-abc****xyz`）
- [ ] **SEC-02**: 用户敏感输入进行脱敏处理

**验收标准:**
- 日志中不出现完整 API key
- 用户敏感信息进行脱敏

---

## Out of Scope (v2.4)

| Feature | Reason |
|---------|--------|
| 生产环境日志级别动态调整 | 当前仅优化日志内容 |
| perfetto 完整集成 | 作为未来优化项 |

---

## Traceability (v2.4)

| Requirement | Phase | Status |
|------------|-------|--------|
| LOG-01 | Phase 1 | Pending |
| LOG-02 | Phase 1 | Pending |
| LOG-03 | Phase 1 | Pending |
| HTTP-01 | Phase 2 | Pending |
| HTTP-02 | Phase 2 | Pending |
| HTTP-03 | Phase 2 | Pending |
| LLM-01 | Phase 2 | Pending |
| LLM-02 | Phase 2 | Pending |
| LLM-03 | Phase 2 | Pending |
| MCP-01 | Phase 2 | Pending |
| MCP-02 | Phase 2 | Pending |
| TOOL-01 | Phase 2 | Pending |
| TOOL-02 | Phase 2 | Pending |
| LOOP-01 | Phase 2 | Pending |
| LOOP-02 | Phase 2 | Pending |
| PERF-01 | Phase 3 | Pending |
| PERF-02 | Phase 3 | Pending |
| PERF-03 | Phase 3 | Pending |
| SEC-01 | Phase 1 | Pending |
| SEC-02 | Phase 1 | Pending |

---

# v2.3 Requirements (语音转文字)

## v1 Requirements

### 语音按钮 UI

- [x] **VT-01**: 在输入框 (etMessage) 右侧添加语音按钮 (btnVoice)
- [x] **VT-02**: 按钮位置顺序: etMessage → btnVoice → btnSend
- [x] **VT-03**: 按钮仅在 app 开启语音功能时显示，默认隐藏

### 语音交互

- [x] **VT-04**: 按压按钮显示录音动画提示，开始语音识别
- [x] **VT-05**: 讲话过程中语音转文字工具实时返回识别结果（完整文本），实时更新到输入框
- [x] **VT-06**: 松手按钮结束语音识别，动画结束

### 语音能力接入

- [x] **VT-07**: agent-android 提供语音转文字接口定义
- [x] **VT-08**: 上层 app 模块通过接口注入语音转文字能力实现

## v2 Requirements

暂无

## Out of Scope

| Feature | Reason |
|---------|--------|
| 语音合成 (TTS) | 用户未要求，作为未来功能 |
| 语音消息发送 | 当前仅支持语音转文字输入 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| VT-01 | Phase 1 | Complete |
| VT-02 | Phase 1 | Complete |
| VT-03 | Phase 1 | Complete |
| VT-04 | Phase 2 | Complete |
| VT-05 | Phase 2 | Complete |
| VT-06 | Phase 2 | Complete |
| VT-07 | Phase 3 | Complete |
| VT-08 | Phase 3 | Complete |

**Coverage:**
- v1 requirements: 8 total
- Mapped to phases: 8
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-10*
*Last updated: 2026-03-10 after roadmap created*
