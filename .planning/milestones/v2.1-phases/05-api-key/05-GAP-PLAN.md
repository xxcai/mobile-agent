# Phase 5 Gap Closure Plan

## Issues to Fix

### Issue 1 & 2: WorkspaceManager 硬编码 BUILT_IN_SKILLS
- **Truth**: 动态读取 assets/skills/ 目录，而不是硬编码
- **Test**: 6, 7
- **Fix**: 修改 WorkspaceManager 使用 assetManager.list("workspace/skills") 获取技能列表

### Issue 3: README.md 项目结构不准确
- **Truth**: README.md 准确反映实际项目结构
- **Test**: 8
- **Fix**: 更新 README.md 项目结构部分

### Issue 4: android-tool-extension.md 过于复杂
- **Truth**: 文档专注于告诉上层如何扩展 Android 工具
- **Test**: 9
- **Fix**: 简化文档，聚焦于扩展教程

---

## Tasks

### Task 1: 修复 WorkspaceManager 动态读取 skills
```java
// 替换硬编码的 BUILT_IN_SKILLS
String[] skillNames = context.getAssets().list("workspace/skills");
for (String skillName : skillNames) {
    File targetSkillDir = new File(skillsDir, skillName);
    if (!targetSkillDir.exists()) {
        copyAssetDirectory("workspace/skills/" + skillName, targetSkillDir);
    }
}
```

### Task 2: 更新 README.md 项目结构
- 读取实际项目结构
- 更新 README.md

### Task 3: 简化 android-tool-extension.md
- 重写为简洁的扩展指南
- 只包含：创建 Tool 类 → 注册到 AndroidToolManager → 实现 ToolExecutor 接口

---

## Verification
- [ ] 代码编译通过
- [ ] README.md 反映真实结构
- [ ] android-tool-extension.md 简洁明了
