# Phase 2: 语音交互逻辑 - Context

**Gathered:** 2026-03-11
**Status:** Ready for planning

<domain>
## Phase Boundary

用户按压语音按钮进行语音输入，系统实时返回转写结果并更新到输入框。松手结束录音。

**来自 Phase 1 的决策:**
- 按钮 (btnVoice) 已添加在 etMessage 和 btnSend 之间
- 通过 setVoiceButtonVisible() 控制按钮显示/隐藏

</domain>

<decisions>
## Implementation Decisions

### 录音交互模式
- **按压说话 (Press-to-talk)**: 按住按钮开始录音，松手结束
- 符合用户直觉，节省电量

### 录音动画效果
- **图标切换**: 录音时切换为波形图标，停止后恢复麦克风图标
- 创建 `ic_mic_recording.xml` drawable
- 使用 `setImageResource()` 切换图标

### 语音识别实现
- **接口抽象**: 通过 IVoiceRecognizer 接口接入
- **接口定义**:
  ```java
  public interface IVoiceRecognizer {
      interface Callback {
          void onSuccess(String text);
          void onFail(String error);
      }
      void start(Callback callback);
      void stop();
  }
  ```
- **App 层 Mock**: MockVoiceRecognizer 实现类
  - 模拟多次更新: 字符串数组 ["你", "你好", "你好，", "你好，今天", "你好，今天天气很好"]
  - 定时触发 onSuccess 最终返回
  - UI 层通过独立机制处理实时更新显示

### 转写更新策略
- **实时更新到输入框**: 识别到的文字实时显示在 etMessage 中
- 用户可以直接编辑后发送

</decisions>

<specifics>
## Specific Ideas

- 录音状态通过图标动画体现
- 转写结果直接写入输入框，用户可直接修改

</specifics>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- btnVoice 字段已存在于 AgentActivity.java
- setVoiceButtonVisible() 方法已实现
- inputContainer (LinearLayout): 按钮容器

### Integration Points
- AgentActivity.java: 添加 OnTouchListener 监听按钮按压
- IVoiceRecognizer 接口: 由 app 层注入实现
- etMessage: EditText，转写结果写入目标

### 需要新增
- IVoiceRecognizer 接口定义 (agent-android)
- MockVoiceRecognizer 实现 (app 层)
- 录音图标切换 (ic_mic → ic_mic_recording)
- UI 层实时更新机制

</codebase_context>

<deferred>
## Deferred Ideas

- 语音能力接口注入 (Phase 3)
- 自定义第三方语音识别 (未来)

</deferred>

---

*Phase: 02-voice-interaction*
*Context gathered: 2026-03-11*
