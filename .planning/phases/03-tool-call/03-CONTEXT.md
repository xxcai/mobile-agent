# Phase 3: 动态 Tool 调用与验证 - Context

**Gathered:** 2026-03-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Agent 能够调用通过 App 层注册的 Tool，完成示例验证。

</domain>

<decisions>
## Implementation Decisions

### 迁移方式
- SearchContactsTool.java 和 SendImMessageTool.java 移动到 app 层
- 从 agent-android/src/main/java/com/hh/agent/android/tool/ 移动到 app/src/main/java/com/hh/agent/tool/

### 原位置处理
- 迁移后从 agent-android 中删除源文件
- 不保留 deprecated 副本

### 验证方式
- 需要运行时验证 Tool 能被 Agent 正常调用并返回结果
- 不仅依赖编译通过

### 注册位置
- 在 LauncherActivity 中注册这两个 Tool
- 复用现有的 registerTool() 接口

### Claude's Discretion
- 具体的包名选择
- 验证测试的方式

</decisions>

<specifics>
## Specific Ideas

- 将 SearchContactsTool 和 SendImMessageTool 作为验证对象，证明 App 层注册的 Tool 可以被 Agent 正常调用

</specifics>

#codebase
## Existing Code Insights

### Reusable Assets
- AndroidToolManager.registerTool() - Phase 1 已实现
- LauncherActivity 中已有 6 个内置 Tool 的注册代码

### Integration Points
- Tool 迁移后需要在 LauncherActivity 中添加注册代码
- 确保 Agent 能通过 call_android_tool 调用这些 Tool

</codebase>

<deferred>
## Deferred Ideas

- 无

</deferred>

---

*Phase: 03-tool-call*
*Context gathered: 2026-03-10*
