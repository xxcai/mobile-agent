---
phase: 03-voice-integration
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - agent-android/src/main/java/com/hh/agent/android/voice/VoiceRecognizerHolder.java
  - agent-android/src/main/java/com/hh/agent/android/AgentActivity.java
  - app/src/main/java/com/hh/agent/app/voice/MockVoiceRecognizer.java
autonomous: true
requirements:
  - VT-07
  - VT-08
must_haves:
  truths:
    - "agent-android 提供 IVoiceRecognizer 接口定义"
    - "上层 app 可以通过 VoiceRecognizerHolder 注入自定义实现"
    - "注入的实现能正确回调识别结果到 UI 层"
  artifacts:
    - path: "agent-android/src/main/java/com/hh/agent/android/voice/VoiceRecognizerHolder.java"
      provides: "单例管理语音识别器实例，支持 Setter 注入"
      min_lines: 30
    - path: "agent-android/src/main/java/com/hh/agent/android/AgentActivity.java"
      provides: "通过单例获取语音识别器"
      pattern: "VoiceRecognizerHolder.getInstance"
    - path: "app/src/main/java/com/hh/agent/app/voice/MockVoiceRecognizer.java"
      provides: "Mock 实现，app 模块用于开发测试"
    - path: "agent-android/src/main/java/com/hh/agent/android/voice/README.md"
      provides: "app 层注入文档"
  key_links:
    - from: "app 层代码"
      to: "VoiceRecognizerHolder"
      via: "setRecognizer() 方法"
      pattern: "VoiceRecognizerHolder.getInstance().setRecognizer"
    - from: "AgentActivity"
      to: "VoiceRecognizerHolder"
      via: "getRecognizer() 方法"
      pattern: "VoiceRecognizerHolder.getInstance().getRecognizer"
---

<objective>
创建 VoiceRecognizerHolder 单例类，使上层 app 可通过接口注入语音识别实现

Purpose: 实现依赖注入，使 app 层可以替换默认的 MockVoiceRecognizer 为真实语音识别 SDK
Output: VoiceRecognizerHolder 单例类 + AgentActivity 集成 + 注入文档
</objective>

<context>
@agent-android/src/main/java/com/hh/agent/android/voice/IVoiceRecognizer.java
@agent-android/src/main/java/com/hh/agent/android/AgentActivity.java (lines 35, 65)

Phase 2 已创建 MockVoiceRecognizer 在 agent-android 模块，需要移动到 app 模块

# Interface from existing code:
```java
public interface IVoiceRecognizer {
    interface Callback {
        void onSuccess(String text);
        void onFail(String error);
    }
    void start(Callback callback);
    void stop();
    boolean isRecognizing();
}
```
</context>

<tasks>

<task type="auto">
  <name>Task 1: 移动 MockVoiceRecognizer 到 app 模块</name>
  <files>agent-android/src/main/java/com/hh/agent/android/voice/MockVoiceRecognizer.java
app/src/main/java/com/hh/agent/app/voice/MockVoiceRecognizer.java</files>
  <action>
1. 从 agent-android/src/main/java/com/hh/agent/android/voice/MockVoiceRecognizer.java 读取代码
2. 移动到 app/src/main/java/com/hh/agent/app/voice/MockVoiceRecognizer.java
3. 更新 package 声明: package com.hh.agent.app.voice;
4. 删除 agent-android 中的原文件

确保在 app 模块的 build.gradle 中添加必要依赖（如 Handler）
  </action>
  <verify>
<automated>ls -la app/src/main/java/com/hh/agent/app/voice/MockVoiceRecognizer.java</automated>
  </verify>
  <done>MockVoiceRecognizer 已移动到 app 模块</done>
</task>

<task type="auto">
  <name>Task 2: 创建 VoiceRecognizerHolder 单例类</name>
  <files>agent-android/src/main/java/com/hh/agent/android/voice/VoiceRecognizerHolder.java</files>
  <action>
在 voice 包下创建 VoiceRecognizerHolder 单例类：
- 私有构造函数，单例模式
- private IVoiceRecognizer recognizer 字段
- getInstance() 返回单例
- setRecognizer(IVoiceRecognizer r) 设置识别器实现
- getRecognizer() 返回识别器，如未设置则返回 null（由 app 层主动注入）

确保线程安全（双检锁或 Bill Pugh Singleton）
  </action>
  <verify>
<automated>ls -la agent-android/src/main/java/com/hh/agent/android/voice/VoiceRecognizerHolder.java</automated>
  </verify>
  <done>VoiceRecognizerHolder 类已创建，支持单例获取和 Setter 注入</done>
</task>

<task type="auto">
  <name>Task 3: 修改 AgentActivity 从单例获取语音识别器</name>
  <files>agent-android/src/main/java/com/hh/agent/android/AgentActivity.java</files>
  <action>
修改 AgentActivity.java：
1. 移除 voiceRecognizer 字段声明（如 line 35 的 `private IVoiceRecognizer voiceRecognizer;`）
2. 移除 `voiceRecognizer = new MockVoiceRecognizer();` 初始化（如 line 65）
3. 将所有 `voiceRecognizer.xxx()` 调用改为 `VoiceRecognizerHolder.getInstance().getRecognizer().xxx()`

保持现有的语音交互逻辑不变（btnVoice onTouchEvent, start/stop 调用）
  </action>
  <verify>
<automated>grep -n "VoiceRecognizerHolder" agent-android/src/main/java/com/hh/agent/android/AgentActivity.java</automated>
  </verify>
  <done>AgentActivity 通过单例获取语音识别器，无直接实例化</done>
</task>

<task type="auto">
  <name>Task 4: 添加 app 层注入文档</name>
  <files>agent-android/src/main/java/com/hh/agent/android/voice/README.md</files>
  <action>
在 voice 包下创建 README.md 说明文档：
- 描述 VoiceRecognizerHolder 的用途
- 提供 app 层注入自定义语音识别实现的示例代码
- 说明 MockVoiceRecognizer 在 app 模块，用于开发测试

示例代码：
```java
// 在 Application 或初始化时注入
VoiceRecognizerHolder.getInstance().setRecognizer(new YourVoiceRecognizer());
```
  </action>
  <verify>
<automated>ls -la agent-android/src/main/java/com/hh/agent/android/voice/README.md</automated>
  </verify>
  <done>voice 包下有 README.md 说明如何注入自定义实现</done>
</task>

</tasks>

<verification>
- app 模块包含 MockVoiceRecognizer.java
- agent-android 模块不包含 MockVoiceRecognizer.java
- VoiceRecognizerHolder.java 存在且包含 getInstance, setRecognizer, getRecognizer 方法
- AgentActivity.java 不包含 `new MockVoiceRecognizer()` 调用
- AgentActivity.java 包含 `VoiceRecognizerHolder.getInstance()` 调用
- voice 包下有 README.md 说明注入方式
</verification>

<success_criteria>
1. agent-android 提供 IVoiceRecognizer 接口定义 - 已在 Phase 2 完成
2. 上层 app 模块可以通过接口注入语音转文字能力实现 - 通过 VoiceRecognizerHolder.setRecognizer()
3. 注入的实现能够正确回调识别结果到 UI 层 - 保持原有 Callback 机制不变
</success_criteria>

<output>
After completion, create `.planning/phases/03-voice-integration/{phase}-{plan}-SUMMARY.md`
</output>
