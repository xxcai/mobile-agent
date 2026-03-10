# Phase 2: Tool 生命周期管理 - Context

**Gathered:** 2026-03-10
**Status:** Ready for planning

<domain>
## Phase Boundary

支持 Tool 的查询、注销，并确保变更能主动推送给 Agent（LLM）。

</domain>

<decisions>
## Implementation Decisions

### 查询接口返回格式
- getRegisteredTools() 返回 Map<String, ToolExecutor>
- 调用方可以直接使用 ToolExecutor 对象，无需再次查询

### 注销不存在 Tool 的处理
- unregisterTool(toolName) 当 Tool 不存在时返回 false，不抛异常
- 简化调用方处理逻辑

### 批量操作失败处理
- registerTools() 和 unregisterTools() 采用原子性操作
- 遇到任何一个失败则全部回滚，不执行任何变更
- 保证数据一致性

### Claude's Discretion
- 具体方法命名（getRegisteredTools vs getToolMap）
- 异常消息的具体措辞

</decisions>

<specifics>
## Specific Ideas

- 使用 Phase 1 中实现的 registerTool() 作为基础
- 批量操作内部调用单个操作，但有回滚逻辑

</specifics>

#codebase
## Existing Code Insights

### Reusable Assets
- AndroidToolManager.registerTool() - Phase 1 已实现
- NativeMobileAgentApi.setToolsJson() - 用于推送更新

### Integration Points
- registerTools(Map<String, ToolExecutor>) - 批量注册入口
- unregisterTools(List<String>) - 批量注销入口

</codebase>

<deferred>
## Deferred Ideas

- Phase 3 会实现 Tool 调用和验证

</deferred>

---

*Phase: 02-tool-lifecycle*
*Context gathered: 2026-03-10*
