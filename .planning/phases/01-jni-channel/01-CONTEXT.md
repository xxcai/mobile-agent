# Phase 1: JNI 回调通道 - Context

**Gathered:** 2026-03-04
**Status:** Ready for planning

<domain>
## Phase Boundary

C++ Agent 提供调用 Android 的统一接口。通过 JNI 回调机制，C++ 代码可以调用 Java 层注册的 Android Tools，同步返回 JSON 格式的执行结果。

</domain>

<decisions>
## Implementation Decisions

### 调用方式
- **回调注册**: 在 Agent 初始化时，Java 层注册回调接口 (AndroidToolCallback)
- C++ 持有回调接口的引用，通过 JNI 调用 Java 方法
- 这种方式解耦了 C++ 和 Java 的依赖，便于扩展

### 返回值格式
- **JSON 字符串**: 统一格式 `{"success": true, "result": ...}`
- result 字段可以是任意 JSON 类型
- 失败时: `{"success": false, "error": "error message"}`

### 错误处理
- 返回错误 JSON: `{"success": false, "error": "tool not found"}`
- C++ 不抛出异常，通过返回值传递错误信息
- 错误类型: tool_not_found, invalid_args, execution_failed

### 参数格式
- args 使用 JSON Object (dict)
- 示例: `{"message": "Hello", "duration": 2000}`

### 接口设计
```cpp
// C++ 侧
class AndroidToolCallback {
public:
    virtual std::string call_tool(const std::string& tool_name,
                                   const nlohmann::json& args) = 0;
};

void register_android_tool_callback(std::unique_ptr<AndroidToolCallback> callback);
std::string call_android_tool(const std::string& tool_name, const nlohmann::json& args);
```

</decisions>

<specifics>
## Specific Ideas

- 第一个 Tool: show_toast，用于测试通道是否工作
- tool_name 使用 snake_case 命名 (如 show_toast, get_device_info)

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- native_agent.cpp: 现有的 JNI 入口，已有 JNI_OnLoad 初始化
- tool_registry.cpp: C++ 现有的 tool 注册模式可作为参考
- nlohmann/json: 已作为依赖引入

### Established Patterns
- JNI 通信模式: Java → C++ (已有 nativeInitialize, nativeSendMessage)
- 同步返回: 通过 JNI return 值传递字符串

### Integration Points
- Java 侧: NativeAgent.java 或新的 AndroidToolManager.java
- C++ 侧: native_agent.cpp 或新的 android_tools.cpp
- 配置: tools.json 在 assets 目录

</code_context>

<deferred>
## Deferred Ideas

- 异步调用支持 - 后续阶段
- 动态注册 Tools - 后续阶段

</deferred>

---

*Phase: 01-jni-channel*
*Context gathered: 2026-03-04*
