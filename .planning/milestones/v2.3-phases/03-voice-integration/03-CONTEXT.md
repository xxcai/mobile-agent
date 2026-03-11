# Phase 3: 语音能力接入 - Context

**Gathered:** 2026-03-11
**Status:** Ready for planning

<domain>
## Phase Boundary

agent-android 提供 IVoiceRecognizer 接口定义，上层 app 通过 Setter 方式注入具体实现。

**来自 Phase 2 的决策:**
- IVoiceRecognizer 接口已在 agent-android 中定义
- MockVoiceRecognizer 实现已完成

</domain>

<decisions>
## Implementation Decisions

### 接口位置
- **IVoiceRecognizer 接口**: 保持在 agent-android 模块中
- 包路径: `com.hh.agent.android.voice.IVoiceRecognizer`

### 注入方式
- **单例管理**: 创建 VoiceRecognizerHolder 单例类管理语音识别器实例
- **Setter 注入**: app 层通过 `VoiceRecognizerHolder.getInstance().setRecognizer()` 注入实现
- **获取方式**: AgentActivity 通过 `VoiceRecognizerHolder.getInstance().getRecognizer()` 获取
- 默认实现: agent-android 保留 MockVoiceRecognizer 用于开发测试

### 接口名称
- 保持 `IVoiceRecognizer` 命名（与 Phase 2 一致）

### Mock 位置
- MockVoiceRecognizer 保留在 agent-android 模块
- 用于开发测试，生产环境由 app 注入真实实现

</decisions>

<specifics>
## Specific Ideas

- 接口定义与实现分离
- 使用单例模式管理语音识别器
- VoiceRecognizerHolder 单例设计:
  ```java
  public class VoiceRecognizerHolder {
      private static VoiceRecognizerHolder instance;
      private IVoiceRecognizer recognizer;

      public static VoiceRecognizerHolder getInstance() { ... }
      public void setRecognizer(IVoiceRecognizer r) { ... }
      public IVoiceRecognizer getRecognizer() { ... }
  }
  ```

</specifics>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- IVoiceRecognizer 接口: 已存在于 agent-android
- MockVoiceRecognizer: 已实现
- AgentActivity: 已有 voiceRecognizer 字段

### Integration Points
- VoiceRecognizerHolder: 单例类管理语音识别器
- AgentActivity 通过单例获取 voiceRecognizer
- app 层通过单例注入实现

### 需要新增
- VoiceRecognizerHolder 单例类
- AgentActivity 中移除直接字段，改为从单例获取
- 文档说明 app 如何注入

</codebase_context>

<deferred>
## Deferred Ideas

- 真实语音识别 SDK 接入 (未来)
- 离线语音识别支持 (未来)

</deferred>

---

*Phase: 03-voice-integration*
*Context gathered: 2026-03-11*
