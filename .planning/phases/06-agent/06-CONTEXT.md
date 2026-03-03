# Phase 6: 清理 agent 模块 - Context

**Gathered:** 2026-03-03
**Status:** Ready for planning

<domain>
## Phase Boundary

清理 agent 模块中不需要的代码和文件，保持代码简洁。这个是基础设施/维护任务，不改变功能。

</domain>

<decisions>
## Implementation Decisions

### 清理范围
- 移除 agent 模块中未使用的源文件和头文件
- 简化 CMakeLists.txt，移除不需要的依赖和配置
- 清理调试代码、注释掉的代码、临时代码
- 代码风格检查和统一

### Claude's Discretion
- 具体的清理策略由 planner 决定
- 需要确保清理后代码仍然可以正常编译运行

</decisions>

<specifics>
## Specific Ideas

用户选择了以下清理任务：
1. 未使用文件 - 移除未使用的源文件和头文件
2. 构建配置 - 简化 CMakeLists.txt
3. 调试代码 - 清理调试代码和临时注释
4. 代码风格 - 统一代码风格

</specifics>

<code_context>
## Existing Code Insights

### 当前代码结构
- agent/src/main/cpp/ - 主要 C++ 源代码
- agent/src/main/cpp/src/ - 核心实现文件
- agent/src/main/cpp/include/ - 头文件

### 可能的未使用文件
需要检查：
- src/core/ 中的文件是否都被使用
- include/icraw/ 中的头文件是否都被引用

### 构建配置
- CMakeLists.txt - 需要简化
- 可能存在重复的依赖配置

</code_context>

<deferred>
## Deferred Ideas

None — 清理任务范围明确

</deferred>

---
*Phase: 06-agent*
*Context gathered: 2026-03-03*
