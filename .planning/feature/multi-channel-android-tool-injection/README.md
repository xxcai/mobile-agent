# 多管道 Android Tool 注入

日期：2026-03-20
范围：`agent-core`、`agent-android`
状态：设计中，未开始实现

## 目标

在保留旧兼容通道 `call_android_tool` 的前提下，引入多管道 Android Tool 注入能力。

第一阶段目标：

- 修复 native 动态工具执行写死 `call_android_tool` 协议的问题
- 为多外层通道建立统一透传链路
- 保留 `call_android_tool` 的旧协议兼容
- 为后续引入 `android_gesture_tool` 做好结构准备

## 背景

当前系统只支持单一外层工具通道：

- `call_android_tool`

当前协议形态：

```json
{
  "function": "search_contacts",
  "args": {
    "query": "张三"
  }
}
```

当前关键问题：

1. `AndroidToolManager` 只会生成一个外层 function：`call_android_tool`
2. native 动态工具执行逻辑把所有动态工具都按 `call_android_tool` 的参数结构解析
3. Java callback 只能表达“调用某个内层工具”，不能表达“调用某个外层通道”

## 设计原则

### 原则 1：按能力域拆分外层通道

外层 function 应按能力域拆分，而不是把所有能力都塞进 `call_android_tool`。

示例：

- `call_android_tool`
- `android_gesture_tool`
- 后续可扩展：
  - `android_ui_tool`
  - `android_device_tool`

### 原则 2：native 不解释通道内部协议

native 只负责：

- 注册外层动态工具名
- 在执行时透传：
  - `channelName`
  - `paramsJson`

native 不负责解释：

- `function`
- `args`
- `action`
- 其他某个通道的私有字段

### 原则 3：每个通道拥有独立参数协议

旧兼容通道保留：

```json
{
  "function": "search_contacts",
  "args": {
    "query": "张三"
  }
}
```

新手势通道建议采用：

```json
{
  "action": "tap",
  "x": 120,
  "y": 340
}
```

以及：

```json
{
  "action": "swipe",
  "startX": 100,
  "startY": 500,
  "endX": 400,
  "endY": 500,
  "duration": 300
}
```

### 原则 4：让模型更容易选对通道

为了让模型正确选择通道、动作和参数，每个外层 function 的 schema 必须明确表达：

- 这个通道做什么
- 支持哪些动作
- 每个动作需要哪些参数
- 不适合用来做什么

其中：

- `call_android_tool` 适合 app 级功能，例如联系人、通知、剪贴板
- `android_gesture_tool` 适合坐标级手势，例如点击、滑动

## 目标架构

### LLM 调用链路

#### 旧兼容通道

```text
LLM
  ↓
call_android_tool({...})
  ↓
ToolRegistry
  ↓ 透传 channelName=call_android_tool, paramsJson
JNI callback
  ↓
AndroidToolManager.callTool(channelName, paramsJson)
  ↓
Legacy channel 解析 function + args
  ↓
具体 ToolExecutor
```

#### 新通道

```text
LLM
  ↓
android_gesture_tool({...})
  ↓
ToolRegistry
  ↓ 透传 channelName=android_gesture_tool, paramsJson
JNI callback
  ↓
AndroidToolManager.callTool(channelName, paramsJson)
  ↓
Gesture channel 解析 action + 参数
  ↓
具体手势执行器
```

## 执行顺序

必须按步骤推进，每一步完成并经确认后才进入下一步。

1. `step-01-native-channel-passthrough.md`
   修复 native 写死 `call_android_tool` 的问题，建立 `channelName + paramsJson` 透传链路
2. `step-02-java-channel-registry.md`
   引入 Java 侧多通道注册和分发骨架，保留旧兼容通道
3. `step-03-gesture-channel-schema.md`
   新增 `android_gesture_tool` schema 和 mock 执行
4. `step-04-gesture-channel-runtime.md`
   引入手势运行时框架，但默认执行器仍为 mock
5. `step-05-gesture-channel-runtime.md`
   接入 `tap` / `swipe` 真实实现
6. `step-06-model-guidance.md`
   优化 schema 文案和模型提示策略，提升通道选择准确率

## 与 droidrun-portal 的对应关系

参考项目：

- `/Users/caixiao/Workspace/projects/droidrun-portal`

可借鉴分层：

- `ActionDispatcher`
  负责按 action 路由
- `GestureController`
  负责执行 `tap`、`swipe`、`global action`

映射到当前项目：

- `android_gesture_tool` 对应一个新的外层 channel
- channel 内部再按 `action` 分发

## 执行规则

- 未经确认，不进入下一步
- 未经确认，不做提交
- 每一步只做该步必要修改
- 每一步都必须可测试并有明确验收标准
