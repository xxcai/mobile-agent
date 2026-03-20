# Step 05：接入 `android_gesture_tool` 真实运行时

状态：待实施
前置：Step 04 完成并确认

## 目标

让 `android_gesture_tool` 具备可执行的真实手势能力。

第一版只支持：

- `tap`
- `swipe`

## 参考

参考项目：

- `/Users/caixiao/Workspace/projects/droidrun-portal`

重点参考：

- `ActionDispatcher.kt`
- `GestureController.kt`

## 本步骤要做的事

- 接入项目内实际可用的 Android 手势执行能力
- 将 `tap` / `swipe` 映射到真实运行时
- 处理运行时错误与权限/可用性问题

## 本步骤不做的事

- 不做 long press
- 不做 double tap
- 不做 pinch
- 不新增更多通道

## 测试方式

- 在设备上验证 `tap`
- 在设备上验证 `swipe`
- 校验运行时不可用时的错误返回

## 验收标准

- 编译通过
- `tap` 可执行
- `swipe` 可执行
- 失败时错误信息明确
