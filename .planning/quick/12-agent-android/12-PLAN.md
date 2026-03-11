---
phase: 12-agent-android
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - agent-android/src/main/AndroidManifest.xml
  - agent-android/src/main/java/com/hh/agent/android/AgentActivity.java
autonomous: true
requirements:
  - VT-09
---

<objective>
实现点击语音按钮时的录音权限处理

Purpose: 点击语音按钮时，需要检查并请求 RECORD_AUDIO 权限，确保录音功能正常工作

Output: 录音权限正确声明和动态申请
</objective>

<context>
@/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/AndroidManifest.xml
@/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/AgentActivity.java

项目使用 Android View 系统，当前语音按钮在 setupVoiceButtonListener 中直接调用 recognizer.start()，缺少权限检查
</context>

<tasks>

<task type="auto">
  <name>Task 1: 添加 RECORD_AUDIO 权限声明</name>
  <files>agent-android/src/main/AndroidManifest.xml</files>
  <action>
在 AndroidManifest.xml 的 uses-permission 区域添加:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```
  </action>
  <verify>grep -n "RECORD_AUDIO" agent-android/src/main/AndroidManifest.xml</verify>
  <done>权限已声明到 AndroidManifest.xml</done>
</task>

<task type="auto">
  <name>Task 2: 添加录音权限动态申请逻辑</name>
  <files>agent-android/src/main/java/com/hh/agent/android/AgentActivity.java</files>
  <action>
在 AgentActivity 中:
1. 添加常量: private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
2. 添加成员变量: private boolean permissionGranted = false;
3. 在 onCreate 中调用 checkAndRequestAudioPermission()
4. 添加 checkAndRequestAudioPermission() 方法检查权限，如未授权则调用 ActivityCompat.requestPermissions
5. 重写 onRequestPermissionsResult() 处理权限结果，设置 permissionGranted
6. 修改 setupVoiceButtonListener()，在 start 之前检查 permissionGranted，如未授权则请求权限并 return
  </action>
  <verify>
grep -n "REQUEST_RECORD_AUDIO_PERMISSION\|onRequestPermissionsResult\|permissionGranted" agent-android/src/main/java/com/hh/agent/android/AgentActivity.java | head -20
  </verify>
  <done>点击语音按钮时会检查并动态申请 RECORD_AUDIO 权限</done>
</task>

</tasks>

<verification>
[ ] AndroidManifest.xml 包含 RECORD_AUDIO 权限声明
[ ] AgentActivity 包含权限检查和请求逻辑
[ ] 未授权时点击按钮会弹出权限申请对话框
</verification>

<success_criteria>
用户点击语音按钮时，如果未授予 RECORD_AUDIO 权限，会弹出系统权限申请对话框；授权后语音识别功能正常工作
</success_criteria>

<output>
After completion, create .planning/quick/12-agent-android/12-01-SUMMARY.md
</output>
