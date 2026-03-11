# Phase 1: 语音按钮 UI - Context

**Gathered:** 2026-03-10
**Status:** Ready for planning

<domain>
## Phase Boundary

在聊天界面 (activity_main.xml) 的输入区域添加语音按钮 (btnVoice)。按钮位于输入框 (etMessage) 和发送按钮 (btnSend) 之间。按钮仅在 app 开启语音功能时显示，默认隐藏。

</domain>

<decisions>
## Implementation Decisions

### 按钮实现方式
- 在 activity_main.xml 的 inputContainer 中添加 ImageButton (btnVoice)
- 位置: etMessage → btnVoice → btnSend
- 按钮使用麦克风图标

### 可见性控制
- 按钮默认隐藏 (android:visibility="gone")
- 通过 setVisibility() 控制显示/隐藏
- 语音功能开关由上层 app 通过接口控制

### 按钮属性
- 尺寸: 48dp x 48dp (与 btnSend 一致)
- 样式: 使用现有的 bg_send_button 背景或类似样式
- 图标: 麦克风图标

### 状态显示
- Phase 1 仅添加按钮，录音状态动画在 Phase 2 实现

### Claude's Discretion
- 具体的图标资源选择
- 按钮颜色和点击反馈
- 布局间距微调

</decisions>

<specifics>
## Specific Ideas

- 按钮位置严格按照: etMessage → btnVoice → btnSend 顺序
- 按钮仅在语音功能开启时显示

</specifics>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- activity_main.xml: 现有输入区域布局
- bg_send_button: 现有按钮背景 drawable
- btnSend: 现有发送按钮 (48dp x 48dp)

### Established Patterns
- 使用 LinearLayout (horizontal) 排列按钮
- 使用 setVisibility() 控制组件显示

### Integration Points
- inputContainer (LinearLayout): 添加新按钮的位置
- MainActivity/MainPresenter: 控制按钮可见性

</codebase_context>

<deferred>
## Deferred Ideas

- 录音动画效果 (Phase 2)
- 实时语音转文字 (Phase 2)
- 语音能力接口注入 (Phase 3)

</deferred>

---

*Phase: 01-voice-button*
*Context gathered: 2026-03-10*
