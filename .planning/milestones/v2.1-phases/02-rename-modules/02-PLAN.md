---
phase: 02-rename-modules
plan: "02"
type: execute
wave: 1
depends_on: []
files_modified: []
autonomous: true
requirements: [ARCH-02, ARCH-03]
must_haves:
  truths:
    - "agent 模块重命名为 agent-core，包名保持不变"
    - "app 模块仅保留 LauncherActivity 跳转到 AgentActivity"
    - "三层架构正确: app → agent-android → agent-core"
  artifacts:
    - path: "agent-core/"
      provides: "纯 Java 核心模块（原 agent 重命名）"
    - path: "agent-android/src/main/java/com/hh/agent/android/AgentActivity.java"
      provides: "主界面 Activity"
    - path: "app/src/main/java/com/hh/agent/LauncherActivity.java"
      provides: "入口 Activity，仅跳转到 AgentActivity"
  key_links:
    - from: "app/LauncherActivity.java"
      to: "agent-android/AgentActivity.java"
      via: "Intent 跳转"
    - from: "app/build.gradle"
      to: "agent-android"
      via: "implementation project"
---

<objective>
将 agent 模块重命名为 agent-core，app 模块简化为仅包含 Activity 和简单绑定（壳）

Purpose: 实现三层架构：app -> agent-android -> agent-core
Output: agent-core 模块 + 简化的 app 壳
</objective>

<context>
@.planning/phases/01-agent-android-module/01-SUMMARY.md

**当前模块结构:**
- agent/ (将重命名为 agent-core)
- agent-android/ (已存在，包含 AndroidToolManager 和 6 个 Tool)
- app/ (依赖 agent 和 agent-android，包含 Activity/UI 和重复代码)

**目标模块结构:**
- agent-core/ (原 agent，重命名)
- agent-android/ (Android 适配层 + Activity/UI)
- app/ (仅入口，跳转到 AgentActivity)
</context>

<tasks>

<task type="auto">
  <name>Task 1: 重命名 agent → agent-core</name>
  <files>
    - agent/ → agent-core/ (目录重命名)
    - settings.gradle
    - agent-core/build.gradle (如有需要更新 namespace)
  </files>
  <action>
    1. 将 agent/ 目录重命名为 agent-core/
    2. 更新 settings.gradle: include ':agent' → include ':agent-core'
    3. 确保 agent-core/build.gradle 的 namespace 保持 'com.hh.agent.library'

    注意：保持现有包名 com.hh.agent.library 不变，仅重命名模块目录
  </action>
  <verify>
    <automated>./gradlew :agent-core:assembleDebug --quiet 2>&1 | head -20</automated>
  </verify>
  <done>agent-core 模块编译成功，settings.gradle 包含 ':agent-core'</done>
</task>

<task type="auto">
  <name>Task 2: 更新 app 模块依赖</name>
  <files>
    - app/build.gradle
  </files>
  <action>
    1. 从 app/build.gradle 移除对 ':agent' 的直接依赖
    2. 保留对 ':agent-android' 的依赖（agent-core 通过 agent-android 传递依赖）

    依赖变化:
    - implementation project(':agent')  (删除)
    - implementation project(':agent-android') (保留)
  </action>
  <verify>
    <automated>./gradlew :app:assembleDebug --quiet 2>&1 | head -20</automated>
  </verify>
  <done>app 模块仅依赖 agent-android，编译成功</done>
</task>

<task type="auto">
  <name>Task 3: 迁移 Activity/UI 到 agent-android</name>
  <files>
    - agent-android/src/main/java/com/hh/agent/android/AgentActivity.java
    - agent-android/src/main/java/com/hh/agent/android/contract/MainContract.java
    - agent-android/src/main/java/com/hh/agent/android/presenter/MainPresenter.java
    - agent-android/src/main/java/com/hh/agent/android/ui/MessageAdapter.java
    - agent-android/src/main/java/com/hh/agent/android/WorkspaceManager.java
    - agent-android/src/main/java/com/hh/agent/android/presenter/NativeMobileAgentApiAdapter.java
    - agent-android/src/main/res/layout/*.xml
    - agent-android/src/main/res/drawable/*.xml
    - agent-android/src/main/res/values/*.xml
    - agent-android/src/main/AndroidManifest.xml
  </files>
  <action>
    1. 将 app/src/main/java/com/hh/agent/MainActivity.java 迁移到 agent-android，改名为 AgentActivity.java
    2. 将 app/src/main/java/com/hh/agent/contract/MainContract.java 迁移到 agent-android
    3. 将 app/src/main/java/com/hh/agent/presenter/MainPresenter.java 迁移到 agent-android
    4. 将 app/src/main/java/com/hh/agent/ui/MessageAdapter.java 迁移到 agent-android
    5. 将 app/src/main/java/com/hh/agent/WorkspaceManager.java 迁移到 agent-android
    6. 将 app/src/main/java/com/hh/agent/presenter/NativeMobileAgentApiAdapter.java 迁移到 agent-android
    7. 迁移相关资源文件 (layout, drawable, values) 到 agent-android
    8. 更新 AndroidManifest.xml 添加 AgentActivity
    9. 更新所有 import 语句的包名

    包名映射:
    - com.hh.agent.contract → com.hh.agent.android.contract
    - com.hh.agent.presenter → com.hh.agent.android.presenter
    - com.hh.agent.ui → com.hh.agent.android.ui
    - com.hh.agent.library.model → 保持不变 (在 agent-core 中)
  </action>
  <verify>
    <automated>./gradlew :agent-android:assembleDebug --quiet 2>&1 | head -20</automated>
  </verify>
  <done>AgentActivity 及相关类在 agent-android 中编译成功</done>
</task>

<task type="auto">
  <name>Task 4: 简化 app 模块为壳</name>
  <files>
    - app/src/main/java/com/hh/agent/LauncherActivity.java
    - app/src/main/java/com/hh/agent/AndroidToolManager.java
    - app/src/main/java/com/hh/agent/tools/*.java
  </files>
  <action>
    1. 修改 LauncherActivity.java，改为跳转到 agent-android 的 AgentActivity
    2. 删除 app 中的重复代码:
       - 删除 app/src/main/java/com/hh/agent/AndroidToolManager.java (已迁移到 agent-android)
       - 删除 app/src/main/java/com/hh/agent/tools/ 目录下所有文件 (已迁移到 agent-android)
    3. 保留 app 的 AndroidManifest.xml，确保 LauncherActivity 为入口
    4. 确保 app 不再直接依赖 agent-core (通过 agent-android 间接依赖)

    LauncherActivity 简化逻辑:
    ```java
    Intent intent = new Intent(this, com.hh.agent.android.AgentActivity.class);
    startActivity(intent);
    finish();
    ```
  </action>
  <verify>
    <automated>./gradlew :app:assembleDebug --quiet 2>&1 | head -20</automated>
  </verify>
  <done>app 模块仅包含 LauncherActivity 跳转逻辑，编译成功</done>
</task>

</tasks>

<verification>
- [ ] agent-core 模块编译通过
- [ ] agent-android 包含 AgentActivity 和所有 UI 类
- [ ] app 仅依赖 agent-android，编译通过
- [ ] LauncherActivity 正确跳转到 AgentActivity
- [ ] 三层架构正确: app → agent-android → agent-core
</verification>

<success_criteria>
1. agent-core 模块可独立编译 (纯 Java，无 Android 依赖)
2. agent-android 包含完整 Activity/UI 和 AndroidToolManager
3. app 简化为壳，仅包含 LauncherActivity 跳转到 AgentActivity
4. 整体编译成功: ./gradlew assembleDebug
</success_criteria>

<output>
After completion, create `.planning/phases/02-rename-modules/02-SUMMARY.md`
</output>
