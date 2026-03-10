# Phase 1: Tool 注册接口 - Context

**Gathered:** 2026-03-10
**Status:** Ready for planning

<domain>
## Phase Boundary

App 层可以通过接口注册自定义 Tool 到 AndroidToolManager，支持运行时动态添加。

</domain>

<decisions>
## Implementation Decisions

### 接口签名
- 只传 ToolExecutor 参数，从接口方法 getName() 和 getDescription() 获取元信息
- 简化调用方，不需要显式传入名称和描述

### 内置 Tool 注册
- 6 个内置 Tool（show_toast, display_notification, read_clipboard, take_screenshot, search_contacts, send_im_message）改为在 app 层通过 registerTool() 统一注册
- 从 AndroidToolManager.initialize() 中移除硬编码的内置 Tool 注册逻辑
- 需要在 app 层添加内置 Tool 的实例化和注册代码

### 重复注册处理
- 同名 Tool 重复注册时抛出异常，拒绝覆盖
- 保护已有 Tool 不被意外替换

### Claude's Discretion
- Tool 存储使用 Map 或其他数据结构的具体选择
- 异常类型和错误消息的具体措辞

</decisions>

<specifics>
## Specific Ideas

- 暂时保留 generateToolsJson() 在 AndroidToolManager 中，用于生成工具描述 JSON

</specifics>

#codebase
## Existing Code Insights

### Reusable Assets
- ToolExecutor 接口：agent-core/src/main/java/com/hh/agent/library/ToolExecutor.java
- getName(), getDescription(), getArgsDescription(), getArgsSchema() 方法已定义

### Established Patterns
- AndroidToolManager 使用单例模式
- NativeMobileAgentApi.setToolsJson() 用于推送 tools.json 到 native 层

### Integration Points
- AndroidToolManager.registerTool() 是核心入口
- 注册后需要调用 generateToolsJson() + NativeMobileAgentApi.setToolsJson() 推送给 Agent

</codebase>

<deferred>
## Deferred Ideas

- Phase 2 会实现批量注册接口 registerTools(Map)
- Phase 3 会实现 Tool 注销接口

</deferred>

---

*Phase: 01-tool-register*
*Context gathered: 2026-03-10*
