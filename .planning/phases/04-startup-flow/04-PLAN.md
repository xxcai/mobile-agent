---
phase: "04"
plan: "01"
subsystem: android-performance
tags: [android, memory-leak, thread]
dependency_graph:
  requires: []
  provides:
    - "修复 Context 内存泄漏"
    - "修复主线程阻塞"
  affects:
    - "agent-android/src/main/java/com/hh/agent/android/presenter/NativeMobileAgentApiAdapter.java"
    - "agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java"
    - "agent-android/src/main/java/com/hh/agent/android/presenter/MainPresenter.java"
    - "agent-android/src/main/java/com/hh/agent/android/AgentActivity.java"
tech_stack:
  patterns:
    - "MVP 架构"
    - "Handler 异步处理"
key_files:
  modified:
    - "NativeMobileAgentApiAdapter.java"
    - "AndroidToolManager.java"
    - "MainPresenter.java"
    - "AgentActivity.java"
decisions:
  - "使用 ApplicationContext 替代 Activity Context"
  - "在 onDestroy 中清理所有 Context 引用"
  - "初始化操作移到后台线程"
requirements_completed: []

---

# Phase 4 Plan 01: 启动流程优化

## Objective

修复 Context 内存泄漏问题和主线程阻塞问题。

## Tasks

### Task 1: 修复 NativeMobileAgentApiAdapter Context 泄漏

```java
// NativeMobileAgentApiAdapter.java

// 添加 clearContext() 方法
public void clearContext() {
    this.context = null;
}
```

**Verification:**
- [ ] clearContext() 方法已添加
- [ ] 编译通过

### Task 2: 修复 AndroidToolManager Context 泄漏

```java
// AndroidToolManager.java

// 添加 clearContext() 方法
public void clearContext() {
    this.context = null;
}
```

**Verification:**
- [ ] clearContext() 方法已添加
- [ ] 编译通过

### Task 3: 修改 MainPresenter 添加清理方法

```java
// MainPresenter.java

// 添加 destroy 方法中的清理逻辑
public void destroy() {
    executor.shutdown();
    // 新增：清理 Context 引用
    if (mobileAgentApi instanceof NativeMobileAgentApiAdapter) {
        ((NativeMobileAgentApiAdapter) mobileAgentApi).clearContext();
    }
}
```

**Verification:**
- [ ] destroy() 方法中调用 clearContext()
- [ ] 编译通过

### Task 4: 验证编译通过

```bash
./gradlew :agent-android:assembleDebug
```

**Verification:**
- [ ] 编译成功
- [ ] 无警告

## Must Haves

- [ ] NativeMobileAgentApiAdapter 不再持有 Activity Context 引用
- [ ] AndroidToolManager 不再持有 Activity Context 引用
- [ ] Activity 销毁时正确清理资源

## Summary

添加 clearContext() 方法并在 onDestroy 中调用，确保不再持有 Activity Context 引用。

---
*Plan: 04-01*
