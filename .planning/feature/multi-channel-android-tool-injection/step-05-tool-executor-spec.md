# Step 05：升级 `ToolExecutor` 工具定义协议

状态：待实施
前置：Step 04 完成并确认

## 目标

把当前偏字符串化的工具定义，升级成更适合模型使用的结构化工具定义。

这一步的重点不是继续手写 `call_android_tool` 的提示词，而是先让每个业务工具自己表达：

- 自己做什么
- 哪些用户意图会命中自己
- 参数应如何组织
- 最小可用调用样例是什么

## 当前问题

当前 `ToolExecutor` 虽然已有：

- `getName()`
- `getDescription()`
- `getArgsDescription()`
- `getArgsSchema()`

但这些信息仍然存在几个问题：

1. `getDescription()` 和 `getArgsDescription()` 偏面向人读，缺少稳定结构
2. 缺少“用户意图示例”这一类直接帮助模型选工具的信息
3. 缺少“最小可用参数样例”这一类直接帮助模型组参数的信息
4. `LegacyAndroidToolChannel` 只能把这些字符串拼成一段长 description，难以持续扩展

## 本步骤要做的事

- 直接升级 `ToolExecutor` 协议，而不是额外引入兼容接口
- 为业务工具定义一个统一的结构化说明对象
- 明确最小必需字段，避免过度设计
- 同步更新当前 demo 工具实现到新协议

## 建议的协议方向

可以采用以下形态之一：

```java
public interface ToolExecutor {
    String getName();
    ToolDefinition getDefinition();
    String execute(JSONObject args);
}
```

其中 `ToolDefinition` 至少应包含：

- `summary`
  工具做什么
- `intentExamples`
  哪些用户表达会触发这个工具
- `argsSchema`
  参数 schema
- `argsExample`
  最小可用参数示例

如果实现时希望保留部分旧字段，也可以在 `ToolDefinition` 中兼容：

- `description`
- `argsDescription`

但这一步的目标是收敛到统一结构，而不是继续扩展更多零散 getter。

## 本步骤不做的事

- 不修改 `call_android_tool` 的 schema 聚合策略
- 不修改 `android_gesture_tool`
- 不做真实手势运行时实现
- 不更新 workspace skill 文案

## 测试方式

- 编译 `agent-core`、`agent-android`、`app`
- 检查当前 demo 工具都已实现新的 `ToolExecutor` 协议
- 确认 `AndroidToolManager`、`LegacyAndroidToolChannel` 等引用点已能编译通过

## 验收标准

- `ToolExecutor` 协议完成升级
- 当前 demo 工具全部迁移到新协议
- 没有引入额外兼容接口层
- 工程编译通过
