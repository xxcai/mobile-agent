---
phase: quick-03
plan: 01
type: execute
wave: 1
depends_on: []
files_modified: []
autonomous: true
requirements: []
---

<objective>
整理 .planning 目录结构，清理不一致的目录命名和缺失的文件
</objective>

<context>
当前 .planning/quick 目录状态:
- quick/1-soul-md-user-md/: 只有 1-SUMMARY.md，缺少 PLAN 文件
- quick/2-agent-gradle/: 有 2-PLAN.md 和 2-SUMMARY.md
- quick/3-planning/: 空目录，等待创建 PLAN
- STATE.md 记录了 #1 和 #2 两个 quick tasks

问题:
1. quick/1-soul-md-user-md 目录缺少对应的 PLAN 文件
2. 目录命名不一致 (1-soul-md-user-md vs 2-agent-gradle)
</context>

<tasks>

<task type="auto">
  <name>Task 1: 分析并整理 quick 目录结构</name>
  <files>.planning/quick/</files>
  <action>
检查并记录当前 quick 目录结构状态:
1. 列出所有 quick 子目录
2. 检查每个目录是否包含 PLAN.md 和 SUMMARY.md
3. 对比 STATE.md 中记录的 quick tasks

如果 1-soul-md-user-md 确实缺少 PLAN.md:
- 根据现有 SUMMARY.md 内容推断原始任务
- 创建对应的 1-PLAN.md 文件
- 如果该任务已完成，直接创建 SUMMARY 风格的 PLAN 标记为 done
</action>
  <verify>
ls -la .planning/quick/*/
  </verify>
  <done>quick 目录结构清晰，每个 task 都有对应的 PLAN.md 文件</done>
</task>

<task type="auto">
  <name>Task 2: 更新 STATE.md Quick Tasks 表格</name>
  <files>.planning/STATE.md</files>
  <action>
根据实际目录结构更新 STATE.md 中的 Quick Tasks 表格:
- 确保目录名与表格中的 Directory 列一致
- 补充缺失的 commit 信息 (如果已知)
- 验证所有已完成的 quick tasks 都有对应的 PLAN + SUMMARY 文件对
  </action>
  <verify>
grep -A 5 "Quick Tasks Completed" .planning/STATE.md
  </verify>
  <done>STATE.md Quick Tasks 表格准确反映实际目录状态</done>
</task>

</tasks>

<verification>
检查 .planning/quick/ 下每个目录:
- [ ] 每个已完成 task 都有 PLAN.md
- [ ] 每个已完成 task 都有 SUMMARY.md
- [ ] 目录命名符合规范 (序号-描述)
- [ ] STATE.md 与实际文件一致
</verification>

<success_criteria>
.planning/quick/ 目录结构清晰，所有已完成的任务都有完整的 PLAN.md 和 SUMMARY.md 文件对
</success_criteria>

<output>
After completion, create `.planning/quick/3-planning/3-01-SUMMARY.md`
</output>
