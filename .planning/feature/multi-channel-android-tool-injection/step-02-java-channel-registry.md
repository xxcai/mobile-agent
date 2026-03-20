# Step 02：Java 侧多通道注册与分发骨架

状态：待实施
前置：Step 01 完成并确认

## 目标

将 Java 侧从“单工具表 + 单兼容通道”升级为“多通道注册与分发骨架”，同时保留旧兼容通道。

## 本步骤要做的事

- 引入 channel executor 抽象
- 将 `call_android_tool` 收敛为旧兼容 channel
- `AndroidToolManager` 改为：
  - 维护多个 channel
  - 聚合多个外层 schema
  - 按 channelName 分发回调

## 本步骤不做的事

- 不接入 `android_gesture_tool`
- 不引入手势真实实现
- 不修改旧 app Tool 的行为

## 推荐结构

建议新增抽象：

- `AndroidToolChannelExecutor`
  - `getChannelName()`
  - `buildSchema()`
  - `execute(JSONObject params)`

建议保留一个兼容实现：

- `LegacyAndroidToolChannel`
  - 承接 `call_android_tool`
  - 继续解析 `function + args`
  - 继续使用现有 `ToolExecutor` map

## 需要修改的文件

- `agent-android/src/main/java/com/hh/agent/android/AndroidToolManager.java`
- 新增若干 channel 相关类

## 测试方式

- 校验 schema 聚合后仍包含 `call_android_tool`
- 校验 `call_android_tool` 行为不变
- 校验 callback 已具备按 channelName 分发的能力

## 验收标准

- 编译通过
- `call_android_tool` 仍能完整工作
- Java 结构已具备新增第二通道的能力
