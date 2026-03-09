# Phase 3: 代码下沉 - Context

**Gathered:** 2026-03-09
**Status:** Completed (code already moved)

<domain>
## Phase Boundary

将 AndroidToolManager、WorkspaceManager、Tools 从 app 模块下沉到 agent-android 模块，实现三层架构。

</domain>

<decisions>
## Implementation Decisions

### 已完成的代码迁移
- AndroidToolManager → agent-android/src/.../AndroidToolManager.java
- WorkspaceManager → agent-android/src/.../WorkspaceManager.java
- Tools (ShowToastTool, DisplayNotificationTool, etc.) → agent-android/src/.../tool/

### 模块结构
- agent-core: 纯 Java 核心库 (NativeAgent, ToolExecutor, MobileAgentApi)
- agent-android: Android 适配层 (AndroidToolManager, Tools, UI)
- app: 简化壳 (仅 LauncherActivity)

### 构建验证
- APK 编译成功: `./gradlew assembleDebug` → BUILD SUCCESSFUL

</decisions>

<specifics>
## Specific Ideas

代码下沉在 Phase 1 和 Phase 2 期间已完成。Phase 3 主要验证工作正常。

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- AndroidToolManager: 已迁移到 agent-android
- WorkspaceManager: 已迁移到 agent-android
- ShowToastTool, DisplayNotificationTool, ReadClipboardTool, TakeScreenshotTool, SearchContactsTool, SendImMessageTool: 全部在 agent-android/tool/

### Module Dependencies
- app/build.gradle 依赖 agent-core 和 agent-android

</code_context>

<deferred>
## Deferred Ideas

- Phase 4: 启动流程梳理 (内存泄漏、主线程阻塞)
- Phase 5: 接入文档

</deferred>

---

*Phase: 03-code-sinking*
*Context gathered: 2026-03-09*
