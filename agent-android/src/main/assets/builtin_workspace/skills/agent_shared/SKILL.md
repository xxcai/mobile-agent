---
name: agent_shared
description: Agent 共享规则。提供所有业务 skill 共用的执行规程，包括 skill 与 shortcut 的区别、读取 skill 和 reference 的顺序、路径规则、完成态判断和通用 fallback 约束。
always: true
---

# Agent Shared Rules

本 skill 作为常驻共享规则，提供所有业务 skill 共用的执行规程。

**CRITICAL — 命中某个业务 skill 时，MUST 先读取该 skill，再决定是否调用任何业务 shortcut。**
**CRITICAL — skill 名不是 shortcut 名；不要对 skill 名调用 `run_shortcut` 或 `describe_shortcut`。**
**CRITICAL — 读取 skill 与 reference 时，一律使用 `skills/...` 相对路径，不要使用绝对路径。**
**CRITICAL — 没有当前回合执行成功，不要宣称任务已经完成。**
**CRITICAL — 当本轮输入明显是在选择上一轮候选项时，优先使用 `resolve_candidate_selection`；不要在已有候选状态时直接重跑原始搜索类 shortcut。**
**CRITICAL — 当你判断“当前应使用某个 skill”时，下一步动作是 `read_file("skills/<skill_name>/SKILL.md")`，不是 `run_shortcut("<skill_name>")` 或 `describe_shortcut("<skill_name>")`。**

## 默认执行顺序

当请求明显命中某个业务域时，默认按以下顺序执行：

1. 先读取对应 `SKILL.md`
2. 按该 skill 的要求读取必要 reference
3. 需要 shortcut 细节时再调用 `describe_shortcut`
4. 只有目标和参数都明确时，才调用 `run_shortcut`

如果当前还没有完成以上步骤：

- 不要先试探同名 shortcut
- 不要先输出过程性说明
- 不要先退回视觉链路

## 对象类型

- Skill 是工作规程，用 `read_file("skills/<skill_name>/SKILL.md")` 读取
- Reference 是细节说明，用 `read_file("skills/<skill_name>/references/<file>.md")` 读取
- Shortcut 是可执行原子动作，用 `describe_shortcut("<shortcut_name>")` 查看定义，用 `run_shortcut(...)` 执行

## 完成态规则

- 没有当前回合执行成功，不要宣称任务已经完成
- 对发送、打开、写入、提交这类动作，必须以当前回合 shortcut 成功返回作为完成证据
- 不要用自然语言推断代替执行结果

## 通用 Fallback 规则

- 不要因为一次参数错误、权限错误或临时失败，就直接退回视觉链路
- 只有当当前 skill 或 shortcut 明确要求，或结构化结果明确允许时，才切到 `android_view_context_tool` / `android_gesture_tool`
- 不要把 UI 观察结果替代业务 shortcut 的结构化结果

## 禁止事项

- 不要把 skill 名当成 shortcut 名
- 不要对 `route_navigator`、`agent_shared` 这类 skill 名调用 `run_shortcut` 或 `describe_shortcut`
- 不要在未读 skill 的情况下直接试探业务 shortcut
- 不要猜 skill 或 reference 的相对路径
- 不要在没有执行证据时向用户宣称“已完成”
