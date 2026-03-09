---
phase: "01-agent-android-module"
plan: "01"
type: execute
wave: 1
depends_on: []
files_modified:
  - settings.gradle
  - app/build.gradle
autonomous: true
requirements:
  - "ARCH-01"

must_haves:
  truths:
    - "新增 agent-android 模块存在"
    - "agent-android 模块可编译"
    - "app 模块依赖 agent-android"
    - "Android 工具类迁移到 agent-android"
  artifacts:
    - path: "agent-android/build.gradle"
      provides: "Android 库模块配置"
    - path: "agent-android/src/main/AndroidManifest.xml"
      provides: "模块清单文件"
    - path: "agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java"
      provides: "Android 工具管理器"
    - path: "agent-android/src/main/java/com/hh/agent/android/tool/*.java"
      provides: "Android Tool 实现类"
  key_links:
    - from: "settings.gradle"
      to: "agent-android"
      via: "include ':agent-android'"
    - from: "app/build.gradle"
      to: "agent-android"
      via: "implementation project(':agent-android')"
---

<objective>
新增 agent-android 模块，作为 Android 适配层

Purpose: 将 Android 特定的工具类和管理器从 app 模块抽取到独立模块，为后续的三层架构（app -> agent-android -> agent-core）打下基础

Output: 新增 agent-android 模块，包含 Android 工具管理和工具实现类
</objective>

<context>
@/Users/caixiao/Workspace/projects/mobile-agent/settings.gradle
@/Users/caixiao/Workspace/projects/mobile-agent/app/build.gradle
@/Users/caixiao/Workspace/projects/mobile-agent/agent/build.gradle
</context>

<tasks>

<task type="auto">
  <name>Task 1: 创建 agent-android 模块结构</name>
  <files>agent-android/build.gradle, agent-android/src/main/AndroidManifest.xml</files>
  <action>
    1. 创建目录 agent-android/src/main/java/com/hh/agent/android/
    2. 创建 agent-android/build.gradle:
       - 使用 com.android.library 插件
       - namespace: com.hh.agent.android
       - compileSdk 34, minSdk 24, targetSdk 31
       - 依赖 agent 模块 (implementation project(':agent'))
    3. 创建 agent-android/src/main/AndroidManifest.xml:
       - package: com.hh.agent.android
       - 无特殊权限（工具通过 Context 执行）
  </action>
  <verify>
    <automated>./gradlew :agent-android:assembleDebug --dry-run 2>&1 | head -20</automated>
  </verify>
  <done>agent-android 模块目录结构创建完成，build.gradle 配置正确</done>
</task>

<task type="auto">
  <name>Task 2: 迁移 AndroidToolManager 到 agent-android</name>
  <files>agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java</files>
  <action>
    1. 从 app/src/main/java/com/hh/agent/AndroidToolManager.java 复制到 agent-android
    2. 修改包名: package com.hh.agent.android;
    3. 添加 import com.hh.agent.library.AndroidToolCallback;
    4. AndroidToolManager 需要 Context 参数，构造函数接收 android.content.Context
    5. 保留现有功能：工具注册、工具执行、工具列表获取
  </action>
  <verify>
    <automated>./gradlew :agent-android:compileDebugJavaWithJavac 2>&1 | tail -10</automated>
  </verify>
  <done>AndroidToolManager 迁移完成，可编译</done>
</task>

<task type="auto">
  <name>Task 3: 迁移 Android Tool 类到 agent-android</name>
  <files>agent-android/src/main/java/com/hh/agent/android/tool/*.java</files>
  <action>
    1. 创建目录 agent-android/src/main/java/com/hh/agent/android/tool/
    2. 迁移以下 Tool 类（修改包名为 com.hh.agent.android.tool）:
       - ShowToastTool
       - DisplayNotificationTool
       - ReadClipboardTool
       - TakeScreenshotTool
       - SearchContactsTool
       - SendImMessageTool
    3. 每个 Tool 类：
       - 修改 package 声明
       - 保留原有实现（需要 Android Context 的部分）
  </action>
  <verify>
    <automated>./gradlew :agent-android:compileDebugJavaWithJavac 2>&1 | tail -15</automated>
  </verify>
  <done>所有 Android Tool 类迁移完成，可编译</done>
</task>

<task type="auto">
  <name>Task 4: 更新项目依赖配置</name>
  <files>settings.gradle, app/build.gradle</files>
  <action>
    1. 更新 settings.gradle: 添加 include ':agent-android'
    2. 更新 app/build.gradle:
       - 移除对 agent 的直接依赖（如果有）
       - 添加 implementation project(':agent-android')
       - 保留对 agent 的 implementation 依赖（agent-android 已依赖）
    3. 验证编译: ./gradlew assembleDebug
  </action>
  <verify>
    <automated>./gradlew assembleDebug 2>&1 | tail -20</automated>
  </verify>
  <done>项目依赖配置更新完成，Debug APK 编译成功</done>
</task>

</tasks>

<verification>
- [ ] agent-android 模块目录结构正确
- [ ] agent-android/build.gradle 配置正确
- [ ] AndroidToolManager 迁移到 agent-android
- [ ] 所有 Android Tool 类迁移到 agent-android
- [ ] settings.gradle 包含 agent-android
- [ ] app/build.gradle 依赖 agent-android
- [ ] ./gradlew assembleDebug 编译成功
</verification>

<success_criteria>
agent-android 模块创建完成，app 模块可正常依赖该模块，整个项目编译通过
</success_criteria>

<output>
After completion, create `.planning/phases/01-agent-android-module/01-SUMMARY.md`
</output>
