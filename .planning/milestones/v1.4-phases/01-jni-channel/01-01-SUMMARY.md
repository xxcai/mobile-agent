# Summary: JNI 回调通道

**Phase:** 01-jni-channel
**Plan:** 01-01
**Completed:** 2026-03-04

## What was built

创建了 C++ Agent 调用 Android 平台功能的统一通道。

## Files Created

- `agent/src/main/cpp/include/icraw/android_tools.hpp` - C++ AndroidToolCallback 接口定义
- `agent/src/main/cpp/android_tools.cpp` - AndroidTools 管理器实现
- `agent/src/main/java/com/hh/agent/library/AndroidToolCallback.java` - Java 回调接口

## Files Modified

- `agent/src/main/cpp/native_agent.cpp` - 添加 JNI 回调注册和调用方法
- `agent/src/main/java/com/hh/agent/library/NativeAgent.java` - 添加回调注册方法
- `agent/src/main/cpp/CMakeLists.txt` - 添加新源文件

## Key Changes

1. **C++ 侧**:
   - 定义 `AndroidToolCallback` 抽象类
   - 实现 `AndroidTools` 管理器
   - 添加 JNI 方法 `nativeRegisterAndroidToolCallback` 和 `nativeCallAndroidTool`

2. **Java 侧**:
   - 定义 `AndroidToolCallback` 接口
   - `NativeAgent` 添加回调注册方法

## Usage

```java
// Java 层注册回调
NativeAgent.registerAndroidToolCallback(new AndroidToolCallback() {
    @Override
    public String callTool(String toolName, String argsJson) {
        // 实现 tool 调用逻辑
        return "{\"success\": true, \"result\": \"done\"}";
    }
});

// C++ 侧调用
std::string result = g_android_tools.call_tool("show_toast", {{"message", "Hello"}});
```

## Verification

- [x] `call_android_tool` 接口可用
- [x] 同步返回 JSON 结果
- [x] JNI 层正确传递参数

## Notes

- Phase 2 需要实现 Java 层的 ToolExecutor 来执行具体的 Android 功能
- Phase 3 需要添加 show_toast tool 实现
