# Requirements: Mobile Agent - 语音转文字

**Defined:** 2026-03-10
**Core Value:** 让用户通过自然对话，指挥手机自动完成日常任务。

## v1 Requirements

### 语音按钮 UI

- [ ] **VT-01**: 在输入框 (etMessage) 右侧添加语音按钮 (btnVoice)
- [ ] **VT-02**: 按钮位置顺序: etMessage → btnVoice → btnSend
- [ ] **VT-03**: 按钮仅在 app 开启语音功能时显示，默认隐藏

### 语音交互

- [x] **VT-04**: 按压按钮显示录音动画提示，开始语音识别
- [x] **VT-05**: 讲话过程中语音转文字工具实时返回识别结果（完整文本），实时更新到输入框
- [x] **VT-06**: 松手按钮结束语音识别，动画结束

### 语音能力接入

- [ ] **VT-07**: agent-android 提供语音转文字接口定义
- [ ] **VT-08**: 上层 app 模块通过接口注入语音转文字能力实现

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
| VT-01 | Phase 1 | Pending |
| VT-02 | Phase 1 | Pending |
| VT-03 | Phase 1 | Pending |
| VT-04 | Phase 2 | Complete |
| VT-05 | Phase 2 | Complete |
| VT-06 | Phase 2 | Complete |
| VT-07 | Phase 3 | Pending |
| VT-08 | Phase 3 | Pending |

**Coverage:**
- v1 requirements: 8 total
- Mapped to phases: 8
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-10*
*Last updated: 2026-03-10 after roadmap created*
