# Phase 2: 重命名模块 - Context

**Gathered:** 2026-03-09
**Status:** Ready for planning

<domain>
## Phase Boundary

将 agent 模块重命名为 agent-core，app 模块简化为仅包含 Activity 和简单绑定（壳）。

</domain>

<decisions>
## Implementation Decisions

### 包名策略
- 保持现有包名 `com.hh.agent.library` 不变，仅重命名模块目录
- 避免大规模包名迁移带来的风险

### 模块依赖关系
- app → agent-android → agent-core
- app 直接依赖 agent-android（通过 agent-android 间接依赖 agent-core）

### 代码划分
- agent-core: 纯 Java 核心逻辑，无 Android 依赖
- agent-android: Android 适配层 + Activity/UI（Phase 1 已创建）
- app: 仅保留入口和简单绑定，跳转到 agent-android 的 Activity

### 文件路径
- 目录重命名：agent → agent-core
- 保持原有文件结构不变

</decisions>

<specifics>
## Specific Ideas

- **Activity 复用**: app 模块的 Activity 和 UI 相关代码移入 agent-android，其他 Android app 只需依赖 agent-android 并跳转到 `AgentActivity` 即可接入

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-rename-modules*
*Context gathered: 2026-03-09*
