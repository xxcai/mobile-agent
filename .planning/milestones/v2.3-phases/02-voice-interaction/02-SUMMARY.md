---
phase: 02-voice-interaction
plan: 02
subsystem: 语音交互
tags:
  - voice-recognition
  - press-to-talk
  - Android UI
dependency_graph:
  requires:
    - Phase 1: 语音按钮 UI
  provides:
    - 按压说话交互流程
    - IVoiceRecognizer 接口
    - MockVoiceRecognizer 实现
  affects:
    - AgentActivity
    - drawable resources
tech_stack:
  added:
    - IVoiceRecognizer 接口 (抽象语音识别能力)
    - MockVoiceRecognizer 实现 (Mock 测试)
    - ic_mic_recording.xml (录音状态图标)
  patterns:
    - OnTouchListener 按压监听
    - 实时转写回调
key_files:
  created:
    - agent-android/src/main/res/drawable/ic_mic_recording.xml
    - agent-android/src/main/java/com/hh/agent/android/voice/IVoiceRecognizer.java
    - agent-android/src/main/java/com/hh/agent/android/voice/MockVoiceRecognizer.java
  modified:
    - agent-android/src/main/java/com/hh/agent/android/AgentActivity.java
key_decisions:
  - 按压说话模式 (Press-to-talk)
  - 红色图标表示录音状态
  - 实时转写结果写入输入框
metrics:
  duration: ~5 分钟
  completed_date: "2026-03-11"
---

# Phase 2 Plan 2: 语音交互逻辑 - 按压说话模式

## 概述

实现按压说话模式：用户按压语音按钮开始录音并识别，松手结束。实时转写结果显示在输入框。

## 完成的任务

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | 创建录音状态图标 drawable | 1847ed9 | ic_mic_recording.xml |
| 2 | 创建 IVoiceRecognizer 接口 | 78999b9 | IVoiceRecognizer.java |
| 3 | 创建 MockVoiceRecognizer 实现 | 272ec17 | MockVoiceRecognizer.java |
| 4 | 实现按压说话逻辑 | 3169004 | AgentActivity.java |

## 实现细节

### Task 1: 录音状态图标
- 创建 `ic_mic_recording.xml` 红色麦克风图标
- 使用 `#F44336` 红色表示录音状态

### Task 2: IVoiceRecognizer 接口
- 定义 `Callback` 回调接口 (`onSuccess`/`onFail`)
- 定义 `start()`、`stop()`、`isRecognizing()` 方法
- 抽象语音识别能力，上层 app 可注入实现

### Task 3: MockVoiceRecognizer 实现
- 实现 `IVoiceRecognizer` 接口
- 模拟实时转写更新（每 300ms 一次）
- 最终返回完整文本 "你好，今天天气很好。"

### Task 4: 按压说话逻辑
- 添加 `voiceRecognizer` 和 `isRecording` 字段
- 实现 `setupVoiceButtonListener()`:
  - `ACTION_DOWN`: 开始录音，切换为红色麦克风图标
  - `ACTION_UP`/`ACTION_CANCEL`: 停止录音，恢复原图标
- 实现 `updateVoiceButtonState()` 状态切换方法
- 识别结果实时更新到 `etMessage` 输入框

## 验证结果

- 编译通过: `./gradlew :agent-android:compileDebugJavaWithJavac` BUILD SUCCESSFUL

## 符合的 Requirements

- VT-04: 按压按钮显示录音动画提示，开始语音识别
- VT-05: 讲话过程中语音转文字工具实时返回识别结果（完整文本），实时更新到输入框
- VT-06: 松手按钮结束语音识别，动画结束

## 偏差说明

无 - 计划按预期执行。

## 自检

- [x] ic_mic_recording.xml 存在于 drawable 目录
- [x] IVoiceRecognizer.java 存在于 voice 包
- [x] MockVoiceRecognizer.java 存在于 voice 包
- [x] AgentActivity.java 包含按压说话逻辑
- [x] 编译通过
