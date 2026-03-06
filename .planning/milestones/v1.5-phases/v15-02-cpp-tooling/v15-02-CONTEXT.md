# Phase 2: C++ 端工具改造 - Context

**Gathered:** 2026-03-05
**Status:** Ready for planning

<domain>
## Phase Boundary

修改 C++ Agent，让 LLM 只看到 `call_android_tool` 通用管道，替换现有的内置工具暴露方式。

核心原则 (来自 v1.5 架构设计):
- 单一工具原则: 暴露给 LLM 的只有一个工具 `call_android_tool`
- 功能枚举: 通过 enum 限制 LLM 可选的功能名称
- 注册表模式: Android 端通过注册表映射 function 名称到具体 Executor

</domain>

<decisions>
## Implementation Decisions

### 工具注册方式
- **只用通用管道**: ToolRegistry 只注册 `call_android_tool`，LLM 只能通过它调用 Android 功能
- 原因: PIPE-02 要求 "只暴露 call_android_tool 给 LLM"

### Schema 来源
- **Java 传递**: tools.json 在 Android assets 中，由 Java 层读取后通过 JNI 传递给 C++
- 原因: 现有架构已使用 JNI 回调机制，保持一致性

### 调用模式
- **完全自主调用**: LLM 直接调用 Android 功能，无需用户确认
- 原因: MODE-01 要求 "完全自主调用（无用户确认）"

### 旧工具处理
- **只移除 show_toast**: 保留 read_file, write_file 等内置工具，只移除 show_toast（因为已通过 call_android_tool 暴露）
- 原因: 保持文件系统等工具可用，只移除冗余的 show_toast

</decisions>

<specifics>
## Specific Ideas

**C++ 代码修改点:**
1. `tool_registry.cpp` - 修改 `register_builtin_tools()` 移除 show_toast 工具注册（保留其他内置工具）
2. `native_agent.cpp` - 保持现有的 JNI 入口不变
3. 新增 Java → C++ 传递 tools schema 的 JNI 方法

**参数传递:**
```cpp
// call_android_tool 工具 schema
{
  "name": "call_android_tool",
  "description": "调用 Android 设备功能...",
  "parameters": {
    "type": "object",
    "properties": {
      "function": {
        "type": "string",
        "enum": ["display_notification", "show_toast", "read_clipboard", "take_screenshot"]
      },
      "args": {"type": "object"}
    },
    "required": ["function", "args"]
  }
}
```

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AndroidTools::call_tool(tool_name, args)` - 已在 android_tools.cpp 中实现
- JNI 回调机制 - 已在 native_agent.cpp 中实现
- ToolRegistry 框架 - 现有工具注册模式可复用

### Established Patterns
- JNI 双向通信: Java → C++ (传递 schema), C++ → Java (调用 Android 功能)
- ToolRegistry::register_builtin_tools() - 内置工具注册模式

### Integration Points
- `tool_registry.cpp` - 修改内置工具注册逻辑
- `native_agent.cpp` - 新增 JNI 方法接收 tools schema
- Java NativeAgent 类 - 调用新的 JNI 方法传递 schema

</code_context>

<deferred>
## Deferred Ideas

- 动态工具注册 (后续扩展)
- 用户确认调用模式 (MODE-02)
- 更多 Android 功能扩展

</deferred>

---

*Phase: v15-02-cpp-tooling*
*Context gathered: 2026-03-05*
