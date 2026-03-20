# Step 06：优化模型通道选择与参数构造

状态：待实施
前置：Step 05 完成并确认

## 目标

提升模型选择正确通道、正确动作和正确参数的概率。

## 重点问题

需要让模型明确区分：

- `call_android_tool`
  适合 app 级功能
- `android_gesture_tool`
  适合坐标级点击、滑动等手势

## 本步骤要做的事

- 优化各通道 description
- 明确动作和参数说明
- 必要时补充 workspace / skill 中的示例

## 本步骤不做的事

- 不再扩大运行时能力范围
- 不新增新的通道类型

## 测试方式

- 构造多组用户意图
- 观察模型是否优先选择正确通道
- 检查参数组织是否符合 schema

## 验收标准

- schema 文案清晰
- 至少一组点击/滑动场景能优先命中 `android_gesture_tool`
- 不影响旧 Skill 继续使用 `call_android_tool`
