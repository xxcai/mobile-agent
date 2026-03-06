---
phase: quick-01
plan: 01
type: execute
wave: 1
depends_on: []
files_modified: []
autonomous: true
requirements: []
---

<objective>
检查 SOUL.md 和 USER.md 是否正确加载到 Agent 运行时
</objective>

<context>
该任务验证 SOUL.md 和 USER.md 文件是否正确从 assets 复制到用户目录，并被 C++ Agent 成功加载到系统提示中。

日志输出证明加载成功:
```
D icraw: MemoryManager: Loaded SOUL.md (1147 bytes)
D icraw: MemoryManager: Loaded USER.md (162 bytes)
D icraw: PromptBuilder: Added SOUL.md to prompt (1147 bytes)
D icraw: PromptBuilder: Added USER.md to prompt (162 bytes)
I icraw: SkillLoader: Total loaded 1 skills
```
</context>

<tasks>

<task type="auto">
  <name>Task 1: 验证 SOUL.md 和 USER.md 加载</name>
  <files>agents/icraw/src/main/cpp/</files>
  <action>
检查日志输出，确认以下加载流程:
1. Java 层 WorkspaceManager 从 assets 复制到用户目录
2. C++ MemoryManager 从 workspace_path 读取文件
3. PromptBuilder 在构建系统提示时注入内容
4. Skills 也成功加载
  </action>
  <verify>
日志包含 "MemoryManager: Loaded SOUL.md" 和 "MemoryManager: Loaded USER.md"
  </verify>
  <done>✅ SOUL.md 和 USER.md 已正确加载</done>
</task>

</tasks>

<verification>
- [x] 日志验证通过
- [x] 运行时验证通过
</verification>

<success_criteria>
SOUL.md 和 USER.md 正确加载到 Agent 运行时
</success_criteria>

<output>
This was a verification task - no additional files created.
</output>
