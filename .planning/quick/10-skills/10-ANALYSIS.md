# Quick Task 10: 分析如何把skills也通过上层注入

**日期:** 2026-03-10

---

## 1. 当前项目中Skills的定义和注册方式

**文件位置**: `cxxplatform/include/icraw/core/skill_loader.hpp`

Skills通过`SkillLoader`从文件系统加载:
- 从`workspace/skills`目录下的每个子目录读取`SKILL.md`文件
- 支持YAML frontmatter定义元数据：description, emoji, always, requiredBins, requiredEnvs, anyBins, os
- 主要方法:
  - `load_skills()` - 从多个目录加载skills
  - `load_skills_from_directory()` - 从单个目录加载
  - `get_skill_context()` - 获取skill内容用于LLM上下文
  - `build_skills_summary()` - 构建skills摘要

---

## 2. Tool的注入机制

**Android层** (`AndroidToolManager.java`):
- `registerTool(ToolExecutor executor)` - 动态注册工具
- `generateToolsJson()` - 动态生成tools.json
- `NativeMobileAgentApi.setToolsJson()` - 传递到Native层

**Native层** (`ToolRegistry`):
- `register_builtin_tools()` - 注册内置工具
- `get_tool_schemas()` - 获取工具schema用于LLM
- `execute_tool()` - 执行工具

---

## 3. Skill动态注入需要做的改动

如果要支持Skill的动态注入（类似Tool注入），需要:

1. **定义SkillMetadata的JSON格式** - 类似tools.json的格式

2. **Android层添加Skill管理器**:
   - 创建SkillManager类（类似AndroidToolManager）
   - 实现`registerSkill()`方法
   - 生成skills.json并传递到Native层

3. **Native层添加Skill注册接口**:
   - 在SkillLoader中添加动态注册方法
   - 支持从JSON加载SkillMetadata

4. **更新PromptBuilder** - 支持注入的skills

---

## 4. 关键问题

- Skills的运行时加载比Tools更复杂，因为Skills包含`execution_context`脚本，需要在Native层执行
- 需要考虑Skill的版本管理和冲突处理
- Skill的验证机制需要设计（类似Tool Verification）

---

## 5. 建议的实现路径

1. 先在Android层创建SkillManager
2. 定义skills.json格式（参考tools.json）
3. 在Native层SkillLoader添加registerSkill方法
4. 集成到PromptBuilder的上下文构建中
