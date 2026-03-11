# Agent 性能分析指导

本文档指导如何在 Android 设备上收集和分析 agent-core C++ 层的性能日志。

## 日志标签

Phase 2 添加了以下耗时日志标签：

| 标签 | 说明 | 示例 |
|------|------|------|
| `[HTTP]` | HTTP 请求耗时 | `[HTTP] POST /chat/completions - 150ms` |
| `[LLM]` | LLM 调用耗时 | `[LLM] chat_completion_stream - 2000ms` |
| `[MCP]` | MCP 方法调用耗时 | `[MCP] call_tool get_android_ui - 500ms` |
| `[TOOL]` | 工具执行耗时 | `[TOOL] execute click - 100ms` |
| `[LOOP]` | Agent 循环迭代耗时 | `[LOOP] Iteration 1 - 3500ms` |

## 收集日志

### 1. 连接设备

```bash
# 检查设备连接
adb devices

# 如果需要安装应用
adb install app-debug.apk
```

### 2. 启动日志捕获

```bash
# 方式一：实时过滤耗时日志
adb logcat -s icraw | grep -E "\[.*\].*ms"

# 方式二：保存完整日志到文件
adb logcat -s icraw | grep -E "\[.*\].*ms" > timing_logs.txt

# 方式三：捕获所有日志（包含耗时）
adb logcat -s icraw > full_logs.txt
```

### 3. 执行测试任务

在 Android App 中发送一个典型的用户任务，例如：
- "帮我打开微信"
- "打开设置"
- "查看天气"

### 4. 等待任务完成

观察日志输出，记录完整的耗时数据。

## 分析日志

### 热点识别维度

1. **HTTP 请求**
   - 哪类请求最耗时？
   - 是否可以缓存或合并请求？

2. **LLM 调用**
   - 流式响应总耗时
   - 首 token 耗时（TTFT）
   - 每个 token 耗时（TPFT）

3. **MCP 调用**
   - 哪些 MCP 方法调用频繁？
   - 哪些方法耗时最长？
   - 是否可以预加载或缓存？

4. **TOOL 执行**
   - 哪些工具执行最耗时？
   - 工具执行是否可以并行？

5. **LOOP 迭代**
   - 单次迭代平均耗时
   - 迭代次数
   - 是否可以减少迭代？

### 分析示例

```bash
# 统计各模块耗时
grep -o "\[HTTP\].*ms" timing_logs.txt | sed 's/.*\[HTTP\] //; s/ - //; s/ms//' | awk '{sum+=$1; count++} END {print "HTTP Avg:", sum/count "ms, Total:", sum "ms, Count:", count}'

# Top 10 耗时操作
grep -E "\[.*\].*ms" timing_logs.txt | sort -t'-' -k2 -rn | head -10
```

## 性能优化方向

基于日志分析，可以考虑以下优化：

1. **HTTP 层**
   - 连接复用（HTTP Keep-Alive）
   - 请求合并
   - CDN 加速

2. **LLM 层**
   - 缓存常见响应
   - 减少 prompt 长度
   - 使用更快的模型

3. **MCP 层**
   - 预加载常用工具
   - 减少 MCP 调用次数
   - 本地化部分工具

4. **TOOL 层**
   - 工具执行并行化
   - 简化工具逻辑
   - 异步执行

5. **LOOP 层**
   - 减少不必要的迭代
   - 优化决策逻辑
   - 提前终止条件

## 相关文件

- `agent-core/src/main/cpp/src/core/curl_http_client.cpp` - HTTP 耗时日志
- `agent-core/src/main/cpp/src/core/llm_provider.cpp` - LLM 耗时日志
- `agent-core/src/main/cpp/src/core/mcp_client.cpp` - MCP 耗时日志
- `agent-core/src/main/cpp/src/tools/tool_registry.cpp` - TOOL 耗时日志
- `agent-core/src/main/cpp/src/core/agent_loop.cpp` - LOOP 耗时日志
