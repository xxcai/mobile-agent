---
phase: v20-03-code-migration
plan: 01
subsystem: architecture
tags: [android, refactor, mvp, tools]

# Dependency graph
requires:
  - phase: v20-02 (重命名)
    provides: 代码清理完成，目录结构规范化
provides:
  - agent 模块重构为纯 Java AAR，无 Android 依赖
  - app 模块包含所有 Android 平台相关代码
  - 工具管理器和工具实现已迁移
affects:
  - v20-04 (验证)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Agent/Platform 分离: agent 模块无 Android 依赖，app 模块负责平台集成"
    - "Callback 接口模式: AndroidToolCallback 接口定义在 agent，具体实现注册到 app"

key-files:
  created:
    - app/src/main/java/com/hh/agent/tools/ShowToastTool.java
    - app/src/main/java/com/hh/agent/tools/DisplayNotificationTool.java
    - app/src/main/java/com/hh/agent/tools/ReadClipboardTool.java
    - app/src/main/java/com/hh/agent/tools/TakeScreenshotTool.java
    - app/src/main/java/com/hh/agent/tools/SearchContactsTool.java
    - app/src/main/java/com/hh/agent/tools/SendImMessageTool.java
    - app/src/main/java/com/hh/agent/AndroidToolManager.java
    - app/src/main/java/com/hh/agent/WorkspaceManager.java
  modified:
    - agent/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java
    - app/src/main/java/com/hh/agent/presenter/NativeMobileAgentApiAdapter.java

key-decisions:
  - "使用 callback 接口而非直接依赖: NativeMobileAgentApi 使用 AndroidToolCallback 接口而非直接引用 AndroidToolManager"
  - "App 主动注册: AndroidToolManager.initialize() 调用 setToolCallback() 注册到 NativeMobileAgentApi"

patterns-established:
  - "纯 Java Agent: agent 模块不包含任何 android.* 导入，可打包为 AAR"
  - "平台代码在 app: 所有 Android 平台相关代码（Tools, AndroidToolManager, WorkspaceManager）都在 app 模块"

requirements-completed: [MIGRATE-01, MIGRATE-02, MIGRATE-03]

# Metrics
duration: 4min
completed: 2026-03-09
---

# Phase v20-03 Plan 01: 代码迁移 Summary

**将平台相关工具和管理器从 agent 模块迁移到 app 模块，实现架构解耦**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-09T01:40:05Z
- **Completed:** 2026-03-09T01:44:00Z
- **Tasks:** 3 (all completed)
- **Files modified:** 10

## Accomplishments
- 将 6 个 Tool 实现从 agent 迁移到 app/tools/ 包
- 将 AndroidToolManager 和 WorkspaceManager 迁移到 app 模块
- 更新 NativeMobileAgentApi 使用回调接口模式（而非直接依赖具体类）
- 编译通过，agent 模块现在不含 Android 依赖

## Task Commits

Each task was committed atomically:

1. **Migration task (all 3 tasks combined)** - `5c68c31` (refactor)

**Plan metadata:** `5c68c31` (refactor: migrate Android tools from agent to app module)

## Files Created/Modified
- `app/src/main/java/com/hh/agent/tools/` - 6 tool implementations (ShowToastTool, DisplayNotificationTool, ReadClipboardTool, TakeScreenshotTool, SearchContactsTool, SendImMessageTool)
- `app/src/main/java/com/hh/agent/AndroidToolManager.java` - Tool 管理器
- `app/src/main/java/com/hh/agent/WorkspaceManager.java` - 工作空间管理器
- `agent/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java` - 更新使用 callback 接口
- `app/src/main/java/com/hh/agent/presenter/NativeMobileAgentApiAdapter.java` - 更新 import

## Decisions Made
- 使用 callback 接口而非直接依赖: NativeMobileMobileApi 使用 AndroidToolCallback 接口而非直接引用 AndroidToolManager
- App 主动注册: AndroidToolManager.initialize() 调用 setToolCallback() 注册到 NativeMobileAgentApi

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] NativeMobileAgentApi 直接引用 AndroidToolManager 导致编译失败**
- **Found during:** Task 3 (编译验证)
- **Issue:** agent 模块尝试 import com.hh.agent.AndroidToolManager，但 agent 无法访问 app 模块类
- **Fix:** 将 NativeMobileAgentApi 中的 AndroidToolManager 引用改为 AndroidToolCallback 接口，并添加 setToolCallback() 方法
- **Files modified:** agent/src/main/java/com/hh/agent/library/api/NativeMobileAgentApi.java
- **Verification:** 编译通过，./gradlew assembleDebug 成功
- **Committed in:** 5c68c31

---

**Total deviations:** 1 auto-fixed (blocking issue during compilation)
**Impact on plan:** 修复是架构改进，使 agent 和 app 完全解耦，符合目标

## Issues Encountered
- NativeMobileAgentApi 自动创建 AndroidToolManager 的设计问题 - 通过重构为 callback 模式解决

## Next Phase Readiness
- agent 模块现在可打包为纯 Java AAR
- app 模块包含所有 Android 平台代码
- 准备进行 v20-04 验证测试

---
*Phase: v20-03-code-migration*
*Completed: 2026-03-09*
