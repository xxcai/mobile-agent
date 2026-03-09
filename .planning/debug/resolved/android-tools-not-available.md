---
status: resolved
trigger: "android-tools-not-available"
created: 2026-03-09T00:00:00Z
updated: 2026-03-09T00:00:00Z
---

## Current Focus
hypothesis: "Root cause found: AndroidToolManager is never instantiated/initialized, so callback is never registered"
test: "Add AndroidToolManager initialization in MainPresenter"
expecting: "Callback will be registered, android_tools_not_available error should disappear"
next_action: "User verification needed - test Android tools functionality"

## Symptoms
expected: Agent 能正常调用 Android Tools（Toast、联系人、截图等）
actual: Agent 提示 "android_tools_not_available"，工具调用失败
errors: ["android_tools_not_available"]
reproduction: 用户尝试让 Agent 执行 Android 操作（如发消息、查联系人）
started: 出现在 v20-03 代码迁移之后

## Eliminated

## Evidence
- timestamp: 2026-03-09
  checked: agent/src/main/cpp/android_tools.cpp
  found: Error is thrown at line 12-17 when callback_ is null
  implication: The callback is never registered in the C++ layer

- timestamp: 2026-03-09
  checked: agent/src/main/cpp/native_agent.cpp
  found: register_callback is called at line 329, but only if g_callback_object is set
  implication: The Java side must call NativeAgent.nativeRegisterAndroidToolCallback()

- timestamp: 2026-03-09
  checked: NativeMobileAgentApi.java
  found: setToolCallback() method exists at line 43, which calls NativeAgent.registerAndroidToolCallback(callback)
  implication: AndroidToolManager should call setToolCallback(this) to register

- timestamp: 2026-03-09
  checked: app/src/main/java/com/hh/agent/AndroidToolManager.java
  found: initialize() method at line 40-58 registers callback via NativeMobileAgentApi.getInstance().setToolCallback(this)
  implication: AndroidToolManager.initialize() must be called

- timestamp: 2026-03-09
  checked: app/src/main/java/com/hh/agent/presenter/MainPresenter.java (BEFORE FIX)
  found: createApi() method at line 49-61 initializes NativeMobileAgentApiAdapter but does NOT create or initialize AndroidToolManager
  implication: This is the root cause - AndroidToolManager is never instantiated

## Resolution
root_cause: AndroidToolManager was moved to app module during v20-03 migration, but the initialization code was not added to MainPresenter. The callback is never registered because AndroidToolManager.initialize() is never called.
fix: Added AndroidToolManager initialization in MainPresenter.createApi() after adapter.initialize()
verification: User confirmed the fix works - android_tools_not_available error is resolved
files_changed: [app/src/main/java/com/hh/agent/presenter/MainPresenter.java]
human_verified: true
