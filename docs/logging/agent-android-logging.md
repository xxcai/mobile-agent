# agent-android 日志规范

本文档说明 `agent-android` 的默认日志格式、分级规则，以及哪些日志应该保留，哪些日志不应继续扩散。

## 默认过滤方式

默认实现 `DefaultAgentLogger` 统一使用 Android tag：

- `AgentAndroid`

排查问题时，建议先按这个 tag 聚合全部 `agent-android` 日志。

## 日志格式

统一格式：

```text
[Scope][event_name] detail_key=value ...
```

示例：

```text
[AgentInitializer][initialize_start] tool_count=4
[MainPresenter][stream_start] session_key=native:default input_length=12
[WorkspaceManager][workspace_ready] mode=created path=/storage/emulated/0/...
```

约定：

- `Scope` 使用类或模块名，如 `AgentInitializer`、`MainPresenter`
- `event_name` 使用稳定的 snake_case，避免写成长句
- detail 使用 snake_case 的 `key=value` 形式，便于检索和比对
- 不打印用户消息正文、流式文本增量、完整对象 `toString()`

推荐事件生命周期命名：

- `*_start`
- `*_complete`
- `*_failed`
- `*_skipped`
- `*_registered`
- `*_unregistered`

## 日志分级

### `error`

用于：

- 初始化失败
- native 加载失败
- workspace 复制失败
- 流式请求错误
- 悬浮球显示/隐藏异常

### `warn`

用于：

- 可恢复但非预期的状态
- 非法或未知 finish reason
- 反注册找不到对象等降级路径

### `info`

用于：

- 初始化开始 / 完成
- 工具和通道注册
- workspace 使用现有目录或首次创建成功
- 流式请求开始 / 结束
- tool use / tool result 等关键业务阶段

### `debug`

用于：

- 少量调试辅助信息
- 生命周期计数
- 逐文件复制等低层细节

不应用于：

- 原始消息正文
- 文本增量内容
- 高频 UI 渲染过程

## 当前保留的关键日志

- `AgentInitializer`
  初始化开始、配置加载结果、workspace 注入、native 初始化成功/失败、初始化完成、悬浮球初始化
- `AndroidToolManager`
  通道注册、工具注册/反注册、tools schema 生成失败
- `WorkspaceManager`
  workspace 复用/创建、技能目录复制、复制失败
- `MainPresenter`
  加载历史开始/成功/失败、流式开始、tool use、tool result、message end、stream error
- `FloatingBallManager`
  悬浮球显示/隐藏失败

## 当前明确不保留的高噪音日志

- `AgentFragment` 中打印消息内容
- `MainPresenter` 中打印文本增量和解析结果全文
- `MessageAdapter` 中打印渲染正文
- 任意 `message.toString()` 全量对象日志

## 宿主自定义 logger

如果宿主需要接入自己的日志框架，可实现 `AgentLogger` 并调用：

```java
AgentInitializer.setLogger(customLogger);
```

建议宿主自定义实现继续保留：

- 固定可过滤前缀
- `[scope][event]` 结构
- `info` / `debug` / `warn` / `error` 的语义一致性

库内调用约束：

- 只使用 `AgentLogs.debug/info/warn/error(scope, event, detail)`
- 不再使用 `AgentLogs.d/i/w/e(tag, message)` 这类非结构化门面

## 当前事件清单

- `AgentInitializer`
  `initialize_start`
  `config_loaded`
  `config_load_failed`
  `workspace_path_injected`
  `workspace_initialize_failed`
  `native_initialize_complete`
  `native_initialize_failed`
  `initialize_complete`
  `floating_ball_initialize_start`
  `floating_ball_initialize_complete`
- `AndroidToolManager`
  `initialize_start`
  `callback_registered`
  `tools_json_generated`
  `tool_call_start`
  `tool_call_complete`
  `tool_call_failed`
  `tool_call_invalid_args`
  `tool_channel_unsupported`
  `tool_registered`
  `tool_unregistered`
  `tool_unregister_skipped`
  `channel_registered`
  `generate_tools_json_failed`
- `WorkspaceManager`
  `workspace_prepare`
  `workspace_ready`
  `workspace_prepare_failed`
  `workspace_dir_create_failed`
  `workspace_copy_failed`
  `builtin_skill_copy`
  `builtin_skill_skip`
  `asset_copied`
- `MainPresenter`
  `history_load_start`
  `history_load_success`
  `history_load_failed`
  `stream_start`
  `think_parse_incomplete`
  `think_parse_complete`
  `think_parse_error_finish`
  `tool_use`
  `tool_result`
  `stream_finish`
  `stream_finish_unexpected`
  `stream_error`
  `stream_cancel_requested`
- `AgentFragment`
  `stream_finish_reason_error`
  `stream_finish_reason_unknown`
- `VoiceRecognizerHolder`
  `recognizer_set`
- `FloatingBallLifecycle`
  `activity_started`
  `activity_stopped`
- `FloatingBallManager`
  `initialize_complete`
  `overlay_permission_missing`
  `show_complete`
  `hide_complete`
  `show_failed`
  `hide_failed`

常用字段：

- `tool_count`
- `channel_count`
- `tool_name`
- `channel_name`
- `channel`
- `session_key`
- `finish_reason`
- `error_code`
- `result_success`
- `input_length`
- `result_length`
- `accumulated_length`
- `think_length`
- `hidden_activity_count`
- `foreground_count`
- `content_length`
- `tools_json_length`

## 常用排查组合

### 初始化失败

先看：

- `AgentInitializer / initialize_start`
- `AgentInitializer / config_load_failed`
- `WorkspaceManager / workspace_prepare_failed`
- `AgentInitializer / native_initialize_failed`

建议检索：

```text
tag:AgentAndroid [AgentInitializer]
tag:AgentAndroid [WorkspaceManager]
```

### 工具调用失败

先看：

- `AndroidToolManager / tool_call_start`
- `AndroidToolManager / tool_channel_unsupported`
- `AndroidToolManager / tool_call_invalid_args`
- `AndroidToolManager / tool_call_failed`
- `AndroidToolManager / tool_call_complete`

建议检索：

```text
tag:AgentAndroid [AndroidToolManager][tool_call_
tag:AgentAndroid channel=<channel_name>
```

### 流式响应异常

先看：

- `MainPresenter / stream_start`
- `MainPresenter / stream_error`
- `MainPresenter / stream_finish_unexpected`
- `AgentFragment / stream_finish_reason_error`
- `AgentFragment / stream_finish_reason_unknown`

建议检索：

```text
tag:AgentAndroid [MainPresenter][stream_
tag:AgentAndroid finish_reason=
tag:AgentAndroid error_code=
```

### think 块解析异常

先看：

- `MainPresenter / think_parse_incomplete`
- `MainPresenter / think_parse_error_finish`
- `MainPresenter / stream_finish_unexpected`
- `AgentFragment / stream_finish_reason_error`

判断方法：

- 出现 `think_parse_incomplete`，说明收到了 `<think>` 但还没匹配到 `</think>`
- 如果随后正常出现 `think_parse_complete` 或 `stream_finish`，通常只是流式中间态，不一定是问题
- 如果最后出现 `think_parse_error_finish`、`stream_finish_unexpected` 或 `finish_reason=parse_error`，再判定为异常链路

建议检索：

```text
tag:AgentAndroid [MainPresenter][think_parse_
tag:AgentAndroid finish_reason=parse_error
tag:AgentAndroid session_key=<session_key>
```

## 直接可用的查询命令

### 实时看全部 agent-android 日志

```bash
adb logcat -v time AgentAndroid:D '*:S'
```

### 初始化失败

实时过滤：

```bash
adb logcat -v time AgentAndroid:D '*:S' | rg '\[AgentInitializer\]|\[WorkspaceManager\]|native_initialize_failed|config_load_failed|workspace_prepare_failed'
```

离线过滤：

```bash
rg -n '\[AgentInitializer\]|\[WorkspaceManager\]|native_initialize_failed|config_load_failed|workspace_prepare_failed' agent-android.log
```

### 工具调用失败

实时过滤：

```bash
adb logcat -v time AgentAndroid:D '*:S' | rg '\[AndroidToolManager\]\[(tool_call_start|tool_channel_unsupported|tool_call_invalid_args|tool_call_failed|tool_call_complete)\]'
```

按通道过滤：

```bash
adb logcat -v time AgentAndroid:D '*:S' | rg 'channel=run_shortcut'
```

离线过滤：

```bash
rg -n '\[AndroidToolManager\]\[(tool_call_start|tool_channel_unsupported|tool_call_invalid_args|tool_call_failed|tool_call_complete)\]' agent-android.log
```

### 流式响应异常

实时过滤：

```bash
adb logcat -v time AgentAndroid:D '*:S' | rg '\[(MainPresenter|AgentFragment)\]\[(stream_start|stream_finish|stream_finish_unexpected|stream_error|stream_finish_reason_error|stream_finish_reason_unknown)\]'
```

按错误字段过滤：

```bash
adb logcat -v time AgentAndroid:D '*:S' | rg 'finish_reason=|error_code='
```

离线过滤：

```bash
rg -n '\[(MainPresenter|AgentFragment)\]\[(stream_start|stream_finish|stream_finish_unexpected|stream_error|stream_finish_reason_error|stream_finish_reason_unknown)\]' agent-android.log
```

### think 块解析异常

实时过滤：

```bash
adb logcat -v time AgentAndroid:D '*:S' | rg '\[MainPresenter\]\[(think_parse_incomplete|think_parse_complete|think_parse_error_finish|stream_finish_unexpected)\]|finish_reason=parse_error'
```

按会话过滤：

```bash
adb logcat -v time AgentAndroid:D '*:S' | rg 'session_key=native:default'
```

离线过滤：

```bash
rg -n '\[MainPresenter\]\[(think_parse_incomplete|think_parse_complete|think_parse_error_finish|stream_finish_unexpected)\]|finish_reason=parse_error' agent-android.log
```

### 悬浮球权限与显示问题

实时过滤：

```bash
adb logcat -v time AgentAndroid:D '*:S' | rg '\[(AgentInitializer|FloatingBallManager)\]\[(overlay_permission_missing|initialize_complete|show_complete|hide_complete|show_failed|hide_failed)\]'
```

离线过滤：

```bash
rg -n '\[(AgentInitializer|FloatingBallManager)\]\[(overlay_permission_missing|initialize_complete|show_complete|hide_complete|show_failed|hide_failed)\]' agent-android.log
```

## 建议排查顺序

### 初始化相关

1. 看 `initialize_start`
2. 看 `config_loaded` / `config_load_failed`
3. 看 `workspace_ready` / `workspace_prepare_failed`
4. 看 `native_initialize_complete` / `native_initialize_failed`

### 工具调用相关

1. 看 `tool_call_start`
2. 看是否进入 `tool_channel_unsupported` 或 `tool_call_invalid_args`
3. 看 `tool_call_complete` 的 `result_success`
4. 失败时看 `tool_call_failed`

### think 解析相关

1. 看是否出现 `think_parse_incomplete`
2. 看后续是否有 `think_parse_complete`
3. 如果最终出现 `finish_reason=parse_error`、`think_parse_error_finish` 或 `stream_finish_unexpected`，再判定为异常
