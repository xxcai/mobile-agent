# agent-core 日志规范

本文档说明 `agent-core` 当前 Java / C++ 两层的日志机制、默认行为、分级规则，以及当前阶段保留哪些日志、压缩哪些日志。

## 当前状态

### Java 层

`agent-core` Java 层已接入共享 `AgentLogger` / `AgentLogs` 机制。

当前特点：

- 支持宿主注入自定义 logger
- 与 `agent-android` Java 层共用同一套 logger 协议
- 默认使用 Android Log 输出

### C++ 层

`agent-core` C++ 层已完成统一日志门面和平台隔离。

当前特点：

- 统一使用 `ICRAW_LOG_*` 宏
- Android 默认输出到 native log
- 非 Android 走 native backend
- Android 场景下已支持复用上层 Java 注入 logger
- native logger level 已支持从初始化配置读取

结论：

- Java 层：可注入
- C++ 层：Android 场景可通过 Java logger bridge 注入；非 Android 仍使用 native backend

## 默认过滤方式

### Java 层

默认 tag：

- `AgentCore`

### C++ 层

Android 默认 native tag：

- `icraw`

排查时建议先按层区分：

- Java：`tag:AgentCore`
- C++：`tag:icraw`

## 日志格式

当前目标格式：

```text
[Scope][event_name] detail_key=value ...
```

Java / JNI / runtime / LLM / HTTP / MCP 关键链路已开始向该格式收口。

示例：

```text
[NativeMobileAgentApi][history_query_complete] session_key=native:default message_count=12
[NativeAgentJni][stream_start] input_length=38
[AndroidTools][tool_call_complete] tool_name=call_android_tool result_length=128
[MobileAgent][chat_stream_complete] history_count=15
[AgentLoop][tool_call_execute_complete] tool_id=toolu_xxx result_success=true bytes_written=128
[LlmProvider][chat_request_complete] mode=non_stream duration_ms=840 response_length=512
[HttpClient][request_complete] method=POST url=https://... status_code=200 duration_ms=420 response_length=938
[McpClient][request_start] method=tools/list request_id=3
```

约定：

- `Scope` 使用稳定模块名，如 `NativeAgentJni`、`AgentLoop`
- `event_name` 使用稳定 snake_case
- detail 使用 `key=value`
- `info / warn / error` 只打印摘要，不打印敏感正文

## 日志分级

### `error`

用于：

- 初始化失败
- JNI / native 失败
- tool 调用失败
- stream 失败
- memory / compaction / flush 失败
- LLM / HTTP / MCP 请求失败

### `warn`

用于：

- 可恢复但非预期状态
- 空 listener / 未初始化 / 空 schema
- 非法参数 / 不完整 tool call / 缺失 save_memory tool
- HTTP 非 2xx / 业务降级路径

### `info`

用于：

- 初始化开始 / 完成
- stream 开始 / 结束
- tool 注册 / tool 执行开始 / 完成
- history 查询开始 / 完成
- memory consolidation / flush / compaction 关键阶段
- HTTP / MCP 请求开始 / 完成
- LLM 请求开始 / 完成

### `debug`

用于：

- demo 阶段调试信息
- 用户输入 / 模型输出
- 流式增量
- tool args / tool result
- request body / response body
- SSE event / MCP body
- 低层解析和状态细节

## 敏感信息规则

当前为 demo 阶段，`debug` 允许打印敏感信息。

允许在 `debug` 打印：

- 用户输入
- 模型输出
- 流式文本增量
- tool args
- tool result
- request body
- response body
- SSE event
- MCP request / response body

但：

- `info / warn / error` 一律不打印敏感正文
- `debug` 也优先打印摘要或截断 preview

当前 C++ 层统一使用：

- `icraw::log_utils::truncate_for_debug(...)`

来做调试输出截断。

## 当前保留的关键日志

### Java / JNI Bridge

- `NativeMobileAgentApi`
  `initialize_start`、`initialize_complete`、`initialize_failed`、`tools_schema_set`、`history_query_start`、`history_query_complete`
- `NativeAgentJni`
  `jni_load_complete`、`native_initialize_start`、`native_initialize_complete`、`send_message_start`、`send_message_complete`、`stream_start`、`stream_complete`、`history_query_start`、`history_query_complete`
- `AndroidTools`
  `callback_registered`、`callback_missing`、`tool_call_start`、`tool_call_complete`、`tool_call_failed`

### Core Runtime

- `MobileAgent`
  `initialize_start`、`initialize_complete`、`history_load_start`、`history_load_complete`、`chat_stream_start`、`chat_stream_complete`
- `AgentLoop`
  `loop_start`、`iteration_complete`、`stream_start`、`stream_complete`、`tool_call_execute_start`、`tool_call_execute_complete`、`tool_call_execute_failed`
  `memory_consolidation_start`、`memory_consolidation_complete`、`memory_consolidation_failed`
  `memory_flush_start`、`memory_flush_complete`、`memory_flush_failed`
  `compaction_start`、`compaction_complete`、`compaction_failed`
- `ToolRegistry`
  `tool_registered`、`tool_execute_complete`

### LLM / HTTP / MCP

- `LlmProvider`
  `parser_selected`、`chat_request_start`、`chat_request_complete`、`chat_request_failed`
  `chat_stream_start`、`chat_stream_complete`、`chat_stream_failed`
- `HttpClient`
  `request_start`、`request_complete`、`request_failed`
  `stream_request_start`、`stream_request_complete`、`stream_request_failed`
- `McpClient`
  `initialize_start`、`initialize_complete`、`initialize_failed`
  `request_start`、`request_failed`
  `list_tools_complete`、`call_tool_complete`

## 当前明确收紧的高噪音日志

以下内容当前仅允许出现在 `debug`：

- `nativeSendMessage` 的用户输入和模型输出
- `nativeSendMessageStream` 的 `text_delta`
- `AndroidTools` 的 tool args / result
- `AgentLoop` 的 tool arguments、tool result 摘要、memory 内容摘要
- `LlmProvider` 的 request body、SSE event、response body
- `HttpClient` 的 request / response body
- `McpClient` 的 request / response body

以下内容当前不再在高层打印整块正文：

- assistant message 全内容
- memory history / memory update 全内容
- tool args 全量字符串
- tool result 全量字符串

## 宿主自定义 logger

### Java 层

宿主可实现 `AgentLogger` 并注入：

```java
NativeAgent.setLogger(customLogger);
```

如果通过 `agent-android` 初始化，也可继续使用：

```java
AgentInitializer.setLogger(customLogger);
```

### C++ 层

当前 C++ 层在 Android 场景下已接入 Java logger bridge。

当前行为：

- Android：
  - 若已调用 `NativeAgent.setLogger(customLogger)`，C++ 日志通过 JNI bridge 复用该 Java logger 输出
  - 若通过 `agent-android` 初始化，`AgentInitializer` 会把当前生效 logger 继续透传给 `agent-core`
  - 若未注入自定义 logger，则回退到默认 native log backend
- 非 Android：native backend

常见 Android 注入路径：

```java
AgentInitializer.setLogger(customLogger);
```

`agent-android` 未显式注入时，也会把当前默认 logger 透传给 `agent-core`。

`agent-core` 独立使用时，也可直接调用：

```java
NativeAgent.setLogger(customLogger);
```

### native logger level

Android JNI 初始化时，native logger 当前会优先读取初始化配置中的：

```json
{
  "logging": {
    "level": "debug"
  }
}
```

当前行为：

- 若 `configJson.logging.level` 存在，则 native 按该 level 过滤
- 若未配置，则保持兼容默认值 `debug`
- 切换 Java logger bridge 时只切 backend，不会重置当前 level

## 当前调用约束

### Java 层

- 使用共享 `AgentLogs`
- 关键链路优先使用 `[Scope][event] detail` 风格

### C++ 层

- 只使用 `ICRAW_LOG_TRACE/DEBUG/INFO/WARN/ERROR`
- 不再直接使用底层 logger 实现

## 常用排查组合

### 初始化失败

先看：

- `NativeMobileAgentApi / initialize_start`
- `NativeAgentJni / native_initialize_start`
- `NativeAgentJni / config_parse_failed`
- `NativeAgentJni / native_initialize_failed`
- `MobileAgent / initialize_start`

建议检索：

```text
tag:AgentCore [NativeMobileAgentApi]
tag:icraw [NativeAgentJni]
tag:icraw [MobileAgent]
```

### 工具调用失败

先看：

- `AndroidTools / tool_call_start`
- `AndroidTools / tool_call_failed`
- `AgentLoop / tool_call_execute_start`
- `AgentLoop / tool_call_execute_failed`
- `ToolRegistry / tool_execute_complete`

建议检索：

```text
tag:icraw [AndroidTools]
tag:icraw [AgentLoop][tool_call_
tag:icraw tool_name=
```

### 流式响应异常

先看：

- `NativeAgentJni / stream_start`
- `NativeAgentJni / stream_failed`
- `NativeAgentJni / stream_complete`
- `AgentLoop / stream_start`
- `AgentLoop / stream_complete`
- `LlmProvider / chat_stream_start`
- `LlmProvider / chat_stream_failed`
- `HttpClient / stream_request_failed`

建议检索：

```text
tag:icraw [NativeAgentJni][stream_
tag:icraw [AgentLoop][stream_
tag:icraw [LlmProvider][chat_stream_
tag:icraw [HttpClient][stream_request_
```

### LLM / HTTP / MCP 问题

先看：

- `LlmProvider / chat_request_start`
- `LlmProvider / chat_request_failed`
- `HttpClient / request_failed`
- `McpClient / request_failed`

建议检索：

```text
tag:icraw [LlmProvider]
tag:icraw [HttpClient]
tag:icraw [McpClient]
```

## 当前能力边界

当前文档描述的是当前已落地能力。

当前边界如下：

- Java 层已支持宿主注入 `AgentLogger`
- `agent-core` 与 `agent-android` Java 层共用同一套 logger 协议
- Android 场景下，C++ 层日志已支持跟随 Java logger bridge 切换
- `agent-android` 初始化链路会把当前生效 logger 继续透传给 `agent-core`
- C++ 层已完成统一日志门面和日志治理
- native logger level 已支持从初始化配置读取

## 当前已知限制

- Java 与 C++ 仍使用不同默认 tag：
  - Java：`AgentCore`
  - C++：`icraw`
- 非 Android 场景仍使用 native backend，不走 Java logger bridge
- Java / C++ / `agent-android` 的 tag 规则尚未统一为单一 tag
- 更细的运行时日志开关策略尚未在本文档覆盖

## 当前验证

当前已通过的最小编译验证包括：

- `./gradlew :agent-core:compileDebugJavaWithJavac`
- `./gradlew :agent-android:compileDebugJavaWithJavac`
- `./gradlew :agent-core:externalNativeBuildDebug`
