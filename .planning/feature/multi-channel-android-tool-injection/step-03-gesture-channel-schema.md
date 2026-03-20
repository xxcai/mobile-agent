# Step 03：引入 `android_gesture_tool` schema 与 mock 执行

状态：待实施
前置：Step 02 完成并确认

## 目标

正式暴露第二个外层工具通道：`android_gesture_tool`，但先只实现协议和 mock 执行，不接真实手势。

## 第一版协议

### tap

```json
{
  "action": "tap",
  "x": 120,
  "y": 340
}
```

### swipe

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

## 本步骤要做的事

- 新增 `GestureToolChannel`
- 在 schema 中加入 `android_gesture_tool`
- 加入参数校验
- 执行时返回 mock / `not_implemented`

## 本步骤不做的事

- 不执行真实点击
- 不执行真实滑动

## 测试方式

- 检查 schema 中是否出现 `android_gesture_tool`
- 手工构造 `tap` / `swipe` 调用
- 验证参数能被正确校验
- 验证执行结果不会误路由到旧通道

## 验收标准

- 编译通过
- 第二通道已暴露
- 参数结构正确
- mock 执行结果明确可解释
