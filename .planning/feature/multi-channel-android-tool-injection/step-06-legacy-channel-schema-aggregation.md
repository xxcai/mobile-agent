# Step 06：让 `call_android_tool` 基于工具定义自动聚合 schema

状态：待实施
前置：Step 05 完成并确认

## 目标

让 `LegacyAndroidToolChannel` 从已注册工具的结构化定义中自动生成更适合模型的 schema 和提示，而不是继续依赖手写长 description。

## 本步骤要做的事

- 保留 `call_android_tool` 的稳定通道边界描述
- 基于每个工具的 `ToolDefinition` 自动生成工具摘要
- 在通道 schema 中体现：
  - 可用工具名
  - 每个工具的用途摘要
  - 用户意图示例
  - 参数 schema / 参数样例
- 收敛当前过度依赖“互斥限制词”的文案

## 设计要点

### 通道层只保留稳定语义

`call_android_tool` 顶层 description 只说明：

- 它是宿主 App 业务工具通道
- 它使用 `function + args`
- 它不负责屏幕坐标手势

### 工具层承担具体提示

工具自己的定义承担：

- “什么时候应该用我”
- “我需要哪些参数”
- “最小可用请求长什么样”

### 生成策略

优先考虑以下输出形态：

- `function.enum`
  继续约束工具名
- `function.description`
  补充每个工具的摘要列表
- `args.description`
  提示 `args` 的结构依赖于所选工具
- 如果实现成本可控，可在 description 中加入每个工具的最小参数样例

这一步不要求把 `call_android_tool` 改造成复杂的 `oneOf` schema，只要求显著提升工具可辨识度和参数可组织性。

## 本步骤不做的事

- 不改 native 链路
- 不改多通道分发结构
- 不改 `android_gesture_tool` 运行时
- 不做真实点击/滑动

## 测试方式

- 通过 `MainActivity` 输出 `call_android_tool` 的最终 description
- 检查 schema 中是否包含工具摘要和参数提示
- 回归现有 `search_contacts` / `send_im_message` 调用

## 验收标准

- 新增业务工具时，不需要修改 `call_android_tool` 顶层 description
- `call_android_tool` 的 schema 能直接看出核心工具用途
- 现有业务工具调用不回退
- 工程编译通过
