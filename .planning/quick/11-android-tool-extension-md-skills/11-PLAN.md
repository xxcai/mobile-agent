---
phase: quick-11
plan: "11"
type: execute
wave: 1
depends_on: []
files_modified:
  - docs/android-tool-extension.md
autonomous: true
requirements: []
must_haves:
  truths:
    - "文档更新为 app 层注册方式"
    - "新增 skills 接入文档章节"
  artifacts:
    - path: "docs/android-tool-extension.md"
      provides: "Android工具扩展和Skills接入文档"
  key_links: []
---

<objective>
更新 android-tool-extension.md 文档，反映 v2.2 的 app 层注册方式变更，并新增 Skills 接入文档。

Purpose: 让开发者了解如何在 app 层注册 Tool 和 Skills
Output: 更新的文档文件
</objective>

<context>
@docs/android-tool-extension.md (现有文档，需要更新)
@app/src/main/java/com/hh/agent/LauncherActivity.java (Tool 注册示例)
@app/src/main/java/com/hh/agent/tool/SendImMessageTool.java (Tool 示例)
@app/src/main/assets/workspace/skills/im_sender/SKILL.md (Skill 示例)
</context>

<tasks>

<task type="auto">
  <name>Task 1: 更新文档 - Tool 注册和 Skills 接入</name>
  <files>docs/android-tool-extension.md</files>
  <action>
更新 docs/android-tool-extension.md 文档:

1. **更新 Tool 注册部分**:
   - 将创建路径从 `agent-android/src/main/java/com/hh/agent/android/tool/` 改为 `app/src/main/java/com/hh/agent/tool/`
   - 更新注册方式：不在 AndroidToolManager.initialize() 中添加，而是在 LauncherActivity 中通过 toolManager.registerTool() 注册
   - 添加 registerTool() 的代码示例

2. **新增 Skills 接入章节**:
   - 添加"步骤 4: 添加 Skill"章节
   - 说明 Skill 放置路径: `app/src/main/assets/workspace/skills/{skill_name}/`
   - 说明 SKILL.md 格式（参考 im_sender 示例）
   - 说明 Skill 会自动被 Agent 加载

3. **更新现有工具参考表格**:
   - 工具文件位置改为 app 层路径
</action>
  <verify>文件已更新，包含新的 app 层注册方式和 Skills 章节</verify>
  <done>文档反映 v2.2 的 app 层注册方式变更，并包含 Skills 接入说明</done>
</task>

</tasks>

<verification>
检查 docs/android-tool-extension.md 是否包含:
- app 层 Tool 注册示例
- LauncherActivity.registerTool() 使用方式
- Skills 接入文档章节
</verification>

<success_criteria>
文档更新完成，开发者可按照文档在 app 层注册 Tool 和 Skills
</success_criteria>

<output>
After completion, create `.planning/quick/11-android-tool-extension-md-skills/11-SUMMARY.md`
</output>
