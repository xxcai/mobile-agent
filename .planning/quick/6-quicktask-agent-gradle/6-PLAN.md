---
phase: quick
plan: 6
type: execute
wave: 1
depends_on: []
files_modified:
  - "build.gradle"
  - "agent/build.gradle"
autonomous: true
requirements: []
must_haves:
  truths:
    - "Agent 模块不再需要显式引用 config-template.gradle"
    - "config-template.gradle 通过 root build.gradle 自动应用到所有子项目"
  artifacts:
    - path: "build.gradle"
      provides: "自动应用 config-template.gradle 到所有子项目"
    - path: "agent/build.gradle"
      provides: "移除显式 apply 语句后的简洁配置"
  key_links:
    - from: "build.gradle"
      to: "config-template.gradle"
      via: "subprojects afterEvaluate block"
      pattern: "apply from.*config-template"
---

<objective>
移除 agent/build.gradle 中对 config-template.gradle 的显式引用，改由根目录 build.gradle 自动应用到所有子项目

Purpose: config-template.gradle 已支持自动检测 library/application 变体，无需在每个模块单独应用
Output: 简化 Gradle 配置，消除重复的 apply 语句
</objective>

<context>
@config-template.gradle (已支持 libraryVariants 和 applicationVariants 自动检测)
@agent/build.gradle (当前包含 apply from 语句)
@app/build.gradle (当前也包含 apply from 语句)
</context>

<tasks>

<task type="auto">
  <name>Task 1: Remove config-template.gradle apply from agent/build.gradle</name>
  <files>agent/build.gradle</files>
  <action>
删除 agent/build.gradle 末尾的:
```
// Apply external config-template.gradle
apply from: rootProject.file('config-template.gradle')
```
保留其他配置不变。
  </action>
  <verify>
    <automated>grep -q "config-template" /Users/caixiao/Workspace/projects/mobile-agent/agent/build.gradle || echo "removed"</automated>
  </verify>
  <done>agent/build.gradle 不再包含 config-template.gradle 的 apply 语句</done>
</task>

<task type="auto">
  <name>Task 2: Add config-template.gradle to root build.gradle</name>
  <files>build.gradle</files>
  <action>
在根 build.gradle 末尾添加 subprojects 块来自动应用 config-template.gradle:

```groovy
subprojects {
    afterEvaluate {
        if (project.hasProperty('android')) {
            apply from: rootProject.file('config-template.gradle')
        }
    }
}
```

或者更简单地，在根 build.gradle 末尾直接添加:
```groovy
apply from: 'config-template.gradle'
```
  </action>
  <verify>
    <automated>./gradlew :agent:assembleDebug --dry-run 2>&1 | grep -q "copyConfigTemplate" && echo "task exists"</automated>
  </verify>
  <done>config-template.gradle 任务在 root build.gradle 中配置，自动应用到所有子项目</done>
</task>

<task type="auto">
  <name>Task 3: Verify build succeeds</name>
  <files></files>
  <action>
运行 ./gradlew :agent:assembleDebug :app:assembleDebug 验证构建成功
  </action>
  <verify>
    <automated>./gradlew :agent:assembleDebug :app:assembleDebug 2>&1 | tail -20</automated>
  </verify>
  <done>Agent 和 App 模块都能成功构建，config.json 正确复制到 assets 目录</done>
</task>

</tasks>

<verification>
- agent/build.gradle 不再包含 config-template.gradle 引用
- root build.gradle 包含 config-template.gradle 的自动应用
- ./gradlew :agent:assembleDebug 成功
- ./gradlew :app:assembleDebug 成功
</verification>

<success_criteria>
- agent/build.gradle 简化，移除了显式 apply
- config-template.gradle 通过 root build.gradle 自动应用到所有模块
- 构建验证通过
</success_criteria>

<output>
After completion, create .planning/quick/6-quicktask-agent-gradle/6-SUMMARY.md
</output>
