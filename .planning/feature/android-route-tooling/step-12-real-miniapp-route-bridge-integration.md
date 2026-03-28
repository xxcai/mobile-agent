# Step 12 - Real MiniAppRouteBridge Integration

## 目标

- 将当前 mock `MiniAppRouteBridge` 替换为真实小程序查询实现。

## 范围

- 对接宿主可用的小程序查询能力。
- 适配到 `MiniAppRouteBridge.search(query)`。
- 保持 query 选择规则和 resolver contract 不变。
- 做最小回归验证。

## 不修改的内容

- 不改 native bridge contract。
- 不改 route opener 时序。
- 不进入文档固化。

## 要做的事情

1. 确认真正可用的小程序查询入口。
2. 实现真实 `MiniAppRouteBridge` 适配。
3. 在 App 层替换 mock miniapp bridge。
4. 回归验证 miniappName/keywords/双候选路径。
5. 记录真实查询质量和稳定性问题。

## 测试方法

- 调试入口回归
- route 单测回归
- 宿主页真实联调

## 验收标准

- `MiniAppRouteBridge` 已接真实查询源。
- resolver 在 miniapp 路径上的 contract 保持稳定。
- 调试入口可复现真实 miniapp 候选和命中结果。
