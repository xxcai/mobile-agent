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
5. `step-05-tool-executor-spec.md`
   升级 `ToolExecutor` 协议，让工具定义能提供稳定的结构化特征
6. `step-06-legacy-channel-schema-aggregation.md`
   让 `call_android_tool` 基于已注册工具特征自动聚合 schema 与提示
7. `step-07-model-guidance-validation.md`
   用最小验证面检查模型选通道、选工具和组织参数的效果

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

## 第 5 步后的设计补充

为了降低后续新增工具时的维护成本，第 5 步开始不再依赖手写长提示词来指导模型，而是采用“稳定通道边界 + 工具自描述特征”的方案。

### 通道层

通道层只保留稳定边界语义：

- `call_android_tool`
  宿主 App 业务工具通道，协议固定为 `function + args`
- `android_gesture_tool`
  屏幕坐标手势通道，协议固定为 `action + 坐标参数`

通道层不应继续堆叠大量具体工具清单或排他性规则，避免新增工具后反复修改顶层提示。

### 工具层

从第 5 步开始，`call_android_tool` 的提示信息主要来自已注册工具自身，而不是只靠 `LegacyAndroidToolChannel` 手写拼接。

当前 `ToolExecutor` 已有：

- `getName()`
- `getDescription()`
- `getArgsDescription()`
- `getArgsSchema()`

在 demo 阶段，允许直接升级 `ToolExecutor`，把模型选择工具所需的信息变成一组更明确的结构化定义，而不是继续增加兼容接口。

目标方向：

- 工具摘要：说明工具解决什么问题
- 意图示例：说明哪些用户表达会命中这个工具
- 参数 schema：说明参数字段和必填项
- 参数示例：给出最小可用请求样例

### 聚合层

`LegacyAndroidToolChannel` 不再承担“手写提示词中心”的角色，而是负责：

- 保留稳定的通道边界描述
- 聚合已注册工具的结构化定义
- 生成对模型更友好的 function description 与参数说明

这样后续新增业务工具时：

- 不需要修改 `call_android_tool` 顶层提示词
- 只需要在新工具上补充自己的定义
- channel 聚合逻辑自动生效
