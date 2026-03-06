# Phase v16-01: 自定义 Skills 机制 - Context

**Gathered:** 2026-03-06
**Status:** Ready for planning

<domain>
## Phase Boundary

实现自定义 Skills 的定义和加载机制，包括：
- Skill 配置文件格式定义
- C++ 层加载 Skills 的机制
- Skills 之间的依赖关系处理

**Scope note:** 这个 phase 关注 Skills 本身的定义和加载，不涉及 Agent 如何调用 Skills（那是 Phase v16-02）

</domain>

<decisions>
## Implementation Decisions

### 现有实现复用
- **SkillLoader**: 现有代码 `agent/src/main/cpp/src/core/skill_loader.cpp` 已完整实现
- **配置格式**: 继续使用现有的 YAML frontmatter + Markdown 格式（SKILL.md）
  - 不需要改为纯 JSON/YAML
- **加载路径**: workspace/skills/ 目录 + extra_dirs 配置

### Skill 定义字段 (已有实现)
- `description`: Skill 描述
- `emoji`: 图标
- `requiredBins`: 需要的二进制依赖
- `requiredEnvs`: 需要的环境变量
- `anyBins`: 任一可用的二进制
- `os`: OS 限制
- `always`: 是否总是加载

### 依赖处理 (需要实现)
- Skills 之间可能存在依赖关系
- 需要在配置中声明依赖
- 加载时需要按依赖顺序处理

### Claude's Discretion
- 具体依赖关系的数据结构设计
- 循环依赖的检测和处理策略
- 依赖解析的技术实现细节

</decisions>

<specifics>
## Specific Ideas

**现有 Skill 示例:** `cxxplatform/workspace/skills/chinese_writer/SKILL.md`
- 使用 YAML frontmatter 定义元数据
- Markdown 正文定义 Skill 行为

**加载机制:** `agent/src/main/cpp/src/core/skill_loader.cpp`
- 从 workspace/skills/ 目录加载
- 解析 YAML frontmatter
- OS/环境/二进制检查

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `agent/src/main/cpp/src/core/skill_loader.cpp` — 完整的 Skill 加载实现
- `agent/src/main/cpp/include/icraw/core/skill_loader.hpp` — SkillLoader 头文件
- `cxxplatform/workspace/skills/chinese_writer/SKILL.md` — 示例 Skill 文件

### Established Patterns
- YAML frontmatter + Markdown 正文格式
- Skills 按目录组织，每个 Skill 一个子目录
- SKILL.md 文件名约定

### Integration Points
- `config.cpp` — SkillsConfig 配置
- `mobile_agent.cpp` — Agent 初始化时加载 Skills
- `prompt_builder.cpp` — 将 Skills 注入 prompt

</code_context>

<deferred>
## Deferred Ideas

- Agent 如何根据用户意图选择合适的 Skill — Phase v16-02
- Skill 如何触发 Android Tool 调用 — Phase v16-02
- 多步骤 Skill 工作流 — Phase v16-02

</deferred>

---

*Phase: v16-01-skills*
*Context gathered: 2026-03-06*
