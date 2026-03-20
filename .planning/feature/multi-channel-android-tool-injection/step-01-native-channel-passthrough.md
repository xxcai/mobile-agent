# Step 01：native 通道透传与旧兼容保留

状态：待实施
范围：最小改动

## 目标

修复 native 动态工具执行写死 `call_android_tool` 的问题。

完成后系统应具备：

- dynamic tool execution 不再依赖 `params.function` / `params.args`
- native 能把“外层通道名 + 原始参数 JSON”透传到 Java
- `call_android_tool` 仍能通过 Java 兼容逻辑正常执行

本步骤不引入新通道能力，只修协议链路。

## 问题定位

当前问题点：

- `agent-core/src/main/cpp/src/tools/tool_registry.cpp`
  动态工具执行时硬编码读取：
  - `params.function`
  - `params.args`
- `agent-core/src/main/java/com/hh/agent/core/AndroidToolCallback.java`
  当前 callback 语义只适合“调用某个内层工具”
- `agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java`
  目前只知道如何处理 `call_android_tool`

## 设计决议

### callback 契约

保留方法名不变，但更新语义：

```java
String callTool(String channelName, String paramsJson);
```

说明：

- `channelName` 是外层动态工具名，例如 `call_android_tool`
- `paramsJson` 是该外层工具收到的原始参数 JSON

### native 契约

动态工具执行时：

- 不再读取 `params.function`
- 不再读取 `params.args`
- 直接调用：

```text
g_android_tools.call_tool(tool_schema.name, params)
```

其中：

- `tool_schema.name` 就是外层通道名
- `params` 原样透传

### Java 兼容策略

Java 侧 `AndroidToolManager.callTool(channelName, paramsJson)` 仅处理两类情况：

#### 情况 1：`channelName == "call_android_tool"`

兼容旧协议：

- 解析 `paramsJson.function`
- 解析 `paramsJson.args`
- 再路由到现有 `tools` map 中的 `ToolExecutor`

#### 情况 2：其他 channel

统一返回：

```json
{
  "success": false,
  "error": "unsupported_tool_channel",
  "message": "Tool channel 'xxx' is not supported"
}
```

## 需要修改的文件

- `agent-core/src/main/java/com/hh/agent/core/AndroidToolCallback.java`
- `agent-core/src/main/cpp/native_agent.cpp`
- `agent-core/src/main/cpp/android_tools.cpp`
- `agent-core/src/main/cpp/src/tools/tool_registry.cpp`
- `agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java`

## 不修改的内容

- 不新增 `android_gesture_tool`
- 不新增 channel registry 抽象
- 不改 `AgentInitializer`
- 不改 app 中已有 `ToolExecutor`
- 不改 Skill 文件
- 不做 README/docs 更新

## 测试方式

### 用例 1：旧通道正常调用

输入：

- 一个正常的 `call_android_tool`
- `function=search_contacts`

期望：

- 成功执行
- 结果与当前行为一致

### 用例 2：旧通道缺少 function

输入：

- `call_android_tool`
- 缺少 `function`

期望：

- 返回明确错误
- 不崩溃

### 用例 3：旧通道 function 不存在

输入：

- `call_android_tool`
- `function=not_exists`

期望：

- 返回 `tool_not_found`

### 用例 4：未知新通道

输入：

- 一个非 `call_android_tool` 的外层动态工具名

期望：

- 返回 `unsupported_tool_channel`
- 不能误走旧通道解析逻辑

## 验收标准

- 编译通过
- `call_android_tool` 现有功能不回退
- native 不再写死 `function/args`
- 未实现的新通道会得到明确错误，而不是被错误按旧协议解析

## 完成后才可进入下一步

只有在本步骤完成并获得确认后，才进入：

- `step-02-java-channel-registry.md`
