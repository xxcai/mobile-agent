---
name: agent_shared
description: Agent 共享规则。提供所有业务 skill 共用的执行规程，包括 skill 与 shortcut 的区别、读取 skill 的顺序、路径规则和完成态约束。
always: true
---

# Agent Shared Rules

**CRITICAL — skill 名不是 shortcut 名；不要对 skill 名调用 `run_shortcut` 或 `describe_shortcut`。**
**CRITICAL — 当前 profile 不允许任何 shortcut；执行统一走视觉链路。**
**CRITICAL — 没有当前回合执行成功，不要宣称任务已经完成。**

## 默认执行顺序

1. 先使用 `android_view_context_tool` 观察当前界面
2. 原生界面动作使用 `android_gesture_tool`
3. Web 界面动作使用 `android_web_action_tool`
