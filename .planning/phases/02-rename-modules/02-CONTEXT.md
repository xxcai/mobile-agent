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
- agent-android: Android 适配层（Phase 1 已创建）
- app: 仅保留 Activity 和简单绑定

### 文件路径
- 目录重命名：agent → agent-core
- 保持原有文件结构不变

</decisions>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-rename-modules*
*Context gathered: 2026-03-09*
