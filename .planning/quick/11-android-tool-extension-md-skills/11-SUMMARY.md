---
phase: quick-11
plan: "11"
subsystem: documentation
tags: [docs, android-tool, skills]
dependency_graph:
  requires: []
  provides: [android-tool-extension]
  affects: []
tech_stack:
  added: []
  patterns: [markdown-documentation]
key_files:
  created: []
  modified:
    - docs/android-tool-extension.md
decisions: []
---

# Quick Task 11: 更新 Android 工具扩展文档

**One-liner:** 更新 android-tool-extension.md 反映 v2.2 app 层注册方式，新增 Skills 接入文档

## 任务概述

- **任务类型:** 文档更新
- **完成时间:** 2026-03-10
- **提交:** f07df74

## 做了什么

1. **更新 Tool 注册部分**
   - 将创建路径从 `agent-android/src/main/java/com/hh/agent/android/tool/` 改为 `app/src/main/java/com/hh/agent/tool/`
   - 更新注册方式：不在 AndroidToolManager.initialize() 中添加，而是在 LauncherActivity 中通过 toolManager.registerTool() 注册
   - 添加了完整的 registerTool() 代码示例

2. **新增 Skills 接入章节**
   - 添加"步骤 4: 添加 Skill"章节
   - 说明 Skill 放置路径: `app/src/main/assets/workspace/skills/{skill_name}/`
   - 说明 SKILL.md 格式（参考 im_sender 示例）
   - 说明 Skill 会自动被 Agent 加载

3. **更新现有工具参考表格**
   - 工具文件位置改为 app 层路径
   - 添加了工具说明列

## 验证

- [x] 文档包含 app 层 Tool 注册示例
- [x] 文档包含 LauncherActivity.registerTool() 使用方式
- [x] 文档包含 Skills 接入文档章节

## 交付物

| 文件 | 说明 |
|------|------|
| docs/android-tool-extension.md | 更新的 Android 工具扩展和 Skills 接入文档 |

## Self-Check

- [x] 文件已更新: docs/android-tool-extension.md
- [x] 提交已创建: f07df74
