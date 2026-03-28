# Step 13 - Docs And Skill Solidification

## 目标

- 在真实 bridge 接入稳定后，固化正式文档与 skill 内容。

## 范围

- 评估并同步 `docs/`。
- 固化 route tool 接入说明。
- 固化 skill route hint 编写规范。
- 明确 demo 与正式能力边界。

## 不修改的内容

- 不新增新的 route runtime 机制。
- 不引入跳转后视觉操作闭环。

## 要做的事情

1. 评估是否需要同步正式文档。
2. 补 route tool 的正式接入说明。
3. 补 skill 编写规范和 route hint 约束。
4. 标记 demo 与正式能力边界。
5. 判断 feature 是否完全收口，或拆出后续独立 feature。

## 测试方法

- 文档校对
- 代码与文档一致性检查
- skill 示例 walkthrough

## 验收标准

- 文档与当前代码状态一致。
- skill route 约束可被后续业务 skill 直接复用。
- demo 与正式能力边界已明确。
