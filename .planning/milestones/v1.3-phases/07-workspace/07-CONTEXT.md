# Phase 7: 预置 workspace - Context

**Gathered:** 2026-03-04
**Status:** Ready for planning

<domain>
## Phase Boundary

将 cxxplatform/workspace 目录下的内容预置到 App 中，包括 SOUL.md、USER.md 和 skills/。
- 如果用户的`sdcard/Android/data/package/files/`目录下没有`.icraw/workspace`目录，就从asset中获取预置的，复制到这里。
- 如过用户的`sdcard/Android/data/package/files/`目录下有`.icraw/workspace`，就用用户自己的
</domain>

<decisions>
## Implementation Decisions

### 预置文件位置
- assets/workspace/ 目录存放预置文件

### 用户文件位置
- sdcard/Android/data/package/files/.icraw/workspace

### 加载时机
- 初始化的时候，在Java层完成这个检查和复制的过程

### 平台兼容性
- agent模块里面的cpp代码，要屏蔽平台的差异，检查读取workspace的逻辑，是否同时兼容Android和Windows。

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
