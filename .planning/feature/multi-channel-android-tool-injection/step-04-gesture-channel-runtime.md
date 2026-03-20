# Step 04：引入 `android_gesture_tool` 运行时框架

状态：待实施
前置：Step 03 完成并确认

## 目标

让 `android_gesture_tool` 从“内联 mock 逻辑”升级为“可替换运行时执行器”架构。

本步骤仍然全部使用 mock，不做真实点击或真实滑动。

## 本步骤要做的事

- 引入 gesture executor 抽象
- 引入默认 mock executor
- 为未来真实运行时预留注册/替换入口
- 让 `GestureToolChannel` 改为委托给 executor 执行
- 保留当前 `tap` / `swipe` 协议和参数校验

## 本步骤不做的事

- 不做真实点击
- 不做真实滑动
- 不做 long press
- 不做 double tap
- 不做 pinch
- 不新增更多通道

## 测试方式

- 验证 `android_gesture_tool` 仍可返回 mock 成功结果
- 验证 `tap` / `swipe` 参数校验不回退
- 验证 channel 已通过执行器抽象输出结果，而不是继续把 mock 逻辑写死在 channel 内

## 验收标准

- 编译通过
- `tap` 返回 mock 成功结果
- `swipe` 返回 mock 成功结果
- 非法参数返回明确错误
- 代码结构中已存在未来可接真实运行时的 executor 扩展点
