---
gsd_state_version: 1.0
phase: 01-tool-register
plan: 01
subsystem: AndroidToolManager
tags:
  - tool-registration
  - dynamic-injection
  - android
dependency_graph:
  requires:
    - agent-core:ToolExecutor
    - agent-android:AndroidToolManager
    - app:LauncherActivity
  provides:
    - AndroidToolManager.registerTool()
  affects:
    - MainPresenter (移除初始化)
key_files:
  created: []
  modified:
    - agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java
    - agent-android/src/main/java/com/hh/agent/android/presenter/MainPresenter.java
    - app/src/main/java/com/hh/agent/LauncherActivity.java
tech_stack:
  added:
    - registerTool(ToolExecutor) API
  patterns:
    - 运行时动态 Tool 注册
    - Tool 变更自动推送 Native 层
decisions:
  - |
    接口签名: 只传 ToolExecutor，从 getName() 和 getDescription() 获取元信息
  - |
    内置 Tool: 6 个内置 Tool 改为在 app 层通过 registerTool() 统一注册
  - |
    重复注册: 同名 Tool 抛出 IllegalArgumentException 拒绝
---

# Phase 1 Plan 1: Tool 注册接口 Summary

## 一句话总结

实现 AndroidToolManager.registerTool(ToolExecutor) 接口，支持 App 层运行时动态注册自定义 Tool。

## 任务完成情况

| Task | Name | Status | Commit |
|------|------|--------|--------|
| 1 | 添加 registerTool() 方法到 AndroidToolManager | Done | dbd5a09 |
| 2 | 在 app 层注册 6 个内置 Tool | Done | dbd5a09 |
| 3 | 验证 Tool 注册流程完整性 | Done | dbd5a09 |

## 核心实现

### 1. AndroidToolManager.registerTool()

```java
public void registerTool(ToolExecutor executor) {
    // 检查 null 和空名称
    // 检查重复注册，抛出 IllegalArgumentException
    // 添加到 tools Map
    // 调用 generateToolsJson() + setToolsJson() 推送到 native 层
}
```

### 2. LauncherActivity 初始化

- 添加 `getToolManager()` 静态方法
- 在 `onCreate()` 中创建 AndroidToolManager 实例并注册 6 个内置 Tool

### 3. MainPresenter 修改

- 移除 `AndroidToolManager` 初始化代码
- Tool 注册现由 app 层 LauncherActivity 管理

## 验收标准达成

- INJT-01: App 层可以通过 registerTool(ToolExecutor) 接口注册自定义 Tool
- INJT-02: Tool 注册支持运行时动态添加（注册后立即推送到 native 层）
- INJT-03: 从 ToolExecutor.getName() 和 getDescription() 获取元信息，三者不缺

## 构建验证

```
./gradlew assembleDebug
BUILD SUCCESSFUL in 6s
```

## 偏差记录

### Auto-fixed Issues

None - plan executed exactly as written.

### Auth Gates

None.

---

## Self-Check: PASSED

- Files modified exist: Yes
- Commit dbd5a09 exists: Yes
- Build successful: Yes
