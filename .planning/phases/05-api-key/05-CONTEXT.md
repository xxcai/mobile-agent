# Phase 5: API Key 配置 - Context

**Gathered:** 2026-03-03
**Status:** Ready for planning

<domain>
## Phase Boundary

通过配置文件为 Agent 提供 LLM API Key，支持 JSON 格式配置文件。

</domain>

<decisions>
## Implementation Decisions

### 配置方式
- 使用 JSON 配置文件
- 配置路径通过 Java 层传入 C++ Agent
- 默认配置文件路径: `/data/data/com.hh.agent/files/config.json`

### 配置内容
- apiKey: LLM API Key
- baseUrl: LLM API 端点 (如 https://api.openai.com/v1)

### Claude's Discretion
- 配置文件不存在时的错误处理方式
- 配置读取失败时的降级策略

</decisions>

<specifics>
## Specific Ideas

- 用户选择配置文件方式配置 API Key

</specifics>

<deferred>
## Deferred Ideas

None

</deferred>

---

*Phase: 05-api-key*
*Context gathered: 2026-03-03*
