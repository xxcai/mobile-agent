# Phase 4: 修复config.json安全问题 - Context

**Gathered:** 2026-03-05
**Status:** Ready for planning

<domain>
## Phase Boundary

修复 config.json 文件的安全问题：通过模板 + Gradle 构建时复制的方式，避免敏感 API Key 进入代码仓库。

</domain>

<decisions>
## Implementation Decisions

### 方案选择
- 使用模板文件 + Gradle 构建时复制
- 模板文件包含实际 API Key，但不被 git 跟踪

### 实施步骤
1. 将 config.json 项目根目录下，作为模板
2. 在 .gitignore 中添加忽略规则（模板文件），清理之前多余的规则
3. 在 Gradle 中添加构建任务：
   - 复制模板到 app/build/assets/
   - 确保模板在编译时被复制到输出目录
4. 删除原有的 config.json 和 config.json.bak

### 模板内容
- 与现有 config.json 相同，包含实际 API Key
- 文件名：config.json.template
- 不被 git 跟踪

</decisions>

<specifics>
## Specific Ideas

- 模板文件存放在项目根目录：config.json.template
- Gradle task 在 processDebugResources 之前执行复制

</specifics>

<deferred>
## Deferred Ideas

None

</deferred>

---

*Phase: 04-config-json*
*Context gathered: 2026-03-05*
