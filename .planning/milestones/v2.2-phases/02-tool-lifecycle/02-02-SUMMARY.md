---
phase: 02-tool-lifecycle
plan: 02
subsystem: agent-android
tags:
  - tool-lifecycle
  - AndroidToolManager
dependency_graph:
  requires:
    - INJT-04
    - INJT-05
    - INJT-06
  provides:
    - getRegisteredTools()
    - unregisterTool(String)
    - registerTools(HashMap)
    - unregisterTools(ArrayList)
  affects:
    - NativeMobileAgentApi (via setToolsJson)
tech_stack:
  added:
    - ArrayList import
  patterns:
    - 原子性批量操作 (validation before mutation)
    - 主动推送机制 (tools.json push after change)
key_files:
  created: []
  modified:
    - agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java
decisions:
  - "使用 HashMap 和 ArrayList 作为批量操作的参数类型，确保 Android 兼容性"
  - "批量操作采用先验证后执行的原子性模式，验证失败时抛出异常不执行变更"
  - "所有变更方法都在操作成功后触发一次 tools.json 推送"
---

# Phase 2 Plan 2: Tool 生命周期管理实现 Summary

**One-liner:** 实现 Tool 查询、单个/批量注销接口，所有变更后主动推送 tools.json 到 native 层

## 完成概述

在 AndroidToolManager 中添加了完整的 Tool 生命周期管理能力，支持查询、单个注销、批量注册和批量注销，所有变更都会主动推送到 native 层。

## 任务完成情况

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | 添加查询和单个注销接口 | ba63f5d | AndroidToolManager.java |
| 2 | 添加批量操作接口（原子性） | ba63f5d | AndroidToolManager.java |
| 3 | 验证 Tool 生命周期完整性 | ba63f5d | AndroidToolManager.java |

## 需求覆盖

- **INJT-04**: getRegisteredTools() 可查询已注册 Tool 列表
- **INJT-05**: unregisterTool() 可注销已注册 Tool
- **INJT-06**: 任何 Tool 变更后主动推送 tools.json 到 native 层

## 实现细节

### 新增方法

1. **getRegisteredTools()** - 返回 Map<String, ToolExecutor>
   - 返回内部 tools Map 的副本，防止外部修改
   - 返回空 Map 而不是 null

2. **unregisterTool(String toolName)** - 返回 boolean
   - 当 Tool 不存在时返回 false，不抛异常
   - 移除后调用 generateToolsJson() + setToolsJson() 推送到 native 层

3. **registerTools(HashMap<String, ToolExecutor> toolsToRegister)** - 返回 boolean
   - 参数检查：Map 不能为 null，内部元素不能为 null
   - 原子性：先验证所有 Tool 名称合法且无重复，再执行批量添加
   - 验证失败则抛出 IllegalArgumentException，不执行任何变更

4. **unregisterTools(ArrayList<String> toolNames)** - 返回 boolean
   - 参数检查：List 不能为 null
   - 原子性：先验证所有 Tool 名称存在，再执行批量删除
   - 验证失败则抛出 IllegalArgumentException，不执行任何变更

### 推送机制验证

所有 4 个注册/注销方法都会触发 tools.json 推送：
- registerTool() - 已有推送逻辑
- unregisterTool() - Task 1 添加推送逻辑
- registerTools() - Task 2 添加推送逻辑
- unregisterTools() - Task 2 添加推送逻辑

## 验证结果

- 编译通过: `./gradlew :agent-android:assembleDebug` 成功
- 方法签名符合设计决策
- 原子性保证：批量操作失败时全部回滚
- 推送机制：变更后立即推送到 native 层

## Deviations from Plan

None - plan executed exactly as written.

## Self-Check

- [x] AndroidToolManager.java modified
- [x] Commit ba63f5d created
- [x] All 4 new methods implemented
- [x] Build passes
