# Step 11 - Real NativeRouteBridge Integration

## 目标

- 将当前 mock `NativeRouteBridge` 替换为宿主真实模块声明收集实现。

## 范围

- 对接宿主现有 native route 声明收集来源。
- 适配到 `NativeRouteBridge` 接口。
- 保持 resolver contract 不变。
- 做最小回归验证。

## 不修改的内容

- 不替换 `MiniAppRouteBridge`。
- 不改 `RouteHint` / `RouteResolution` / `RouteTarget` contract。
- 不进入文档固化。

## 要做的事情

1. 确认真实 native route 声明来源。
2. 实现真实 `NativeRouteBridge` 适配。
3. 在 App 层替换 mock native bridge。
4. 回归验证 uri/nativeModule/keywords native 路径。
5. 记录真实数据源边界和缺失能力。

## 测试方法

- 调试入口回归
- route 单测回归
- 宿主页真实联调

## 验收标准

- `NativeRouteBridge` 已接真实数据源。
- 不影响现有 route tool contract。
- route 调试入口和最小测试继续通过。
