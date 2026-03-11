---
phase: 12-agent-android
plan: 01
subsystem: voice
tags: [android, permission, audio]
key-files:
  created: []
  modified:
    - agent-android/src/main/AndroidManifest.xml
    - agent-android/src/main/java/com/hh/agent/android/AgentActivity.java
---

# Quick Task 12-01: 录音权限处理 Summary

## Overview

实现点击语音按钮时的录音权限处理，确保 RECORD_AUDIO 权限正确声明和动态申请。

## Changes

### 1. AndroidManifest.xml
- 添加 `android.permission.RECORD_AUDIO` 权限声明

### 2. AgentActivity.java
- 添加 `REQUEST_RECORD_AUDIO_PERMISSION = 200` 常量
- 添加 `permissionGranted` 成员变量
- 添加 `checkAndRequestAudioPermission()` 方法检查权限，如未授权则动态请求
- 添加 `onRequestPermissionsResult()` 回调处理权限结果
- 修改 `setupVoiceButtonListener()` 在开始录音前检查权限

## Verification

- [x] AndroidManifest.xml 包含 RECORD_AUDIO 权限声明
- [x] AgentActivity 包含权限检查和请求逻辑
- [x] 未授权时点击按钮会弹出权限申请对话框

## Commits

- `65128bb`: feat(12-agent-android): add RECORD_AUDIO permission declaration
- `bc90b91`: feat(12-agent-android): add audio permission request logic

## Success Criteria Met

用户点击语音按钮时，如果未授予 RECORD_AUDIO 权限，会弹出系统权限申请对话框；授权后语音识别功能正常工作。
