# Phase 7: 预置 workspace - Context

**Gathered:** 2026-03-04
**Status:** Ready for planning

<domain>
## Phase Boundary

将 cxxplatform/workspace 目录下的内容预置到 App 中，包括 SOUL.md、USER.md 和 skills/。
</domain>

<decisions>
## Implementation Decisions

### 文件位置
- assets/workspace/ 目录存放预置文件
- C++ Agent 从 assets 目录加载

### 加载时机
- JNI 初始化时从 Java 层传入 assets 路径
- C++ 端读取并解析为字符串供 Agent 使用

### Claude's Discretion
- 具体文件解析格式
- 错误处理策略
</decisions>

<specifics>
## Specific Ideas

从 cxxplatform/workspace 复制:
- USER.md
- SOUL.md
- skills/ 目录

</specifics>

<code_context>
## Existing Code Insights

### Integration Points
- native_agent.cpp: JNI 入口
- IcrawConfig: 配置结构
</code_context>

---

*Phase: 07-workspace*
*Context gathered: 2026-03-04*
