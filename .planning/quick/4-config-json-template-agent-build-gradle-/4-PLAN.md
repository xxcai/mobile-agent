---
phase: quick
plan: 4
type: execute
wave: 1
depends_on: []
files_modified: [config-template.gradle, agent/build.gradle]
autonomous: true
requirements: []
must_haves:
  truths:
    - "config-template.gradle 存在于根目录"
    - "agent/build.gradle 成功引用外部 gradle 文件"
    - "构建成功，config.json 正确打包"
  artifacts:
    - path: "config-template.gradle"
      provides: "config.json.template 拷贝任务定义"
    - path: "agent/build.gradle"
      provides: "引用 config-template.gradle"
  key_links:
    - from: "agent/build.gradle"
      to: "config-template.gradle"
      via: "apply from: rootProject.file('config-template.gradle')"
---

<objective>
把 config.json.template 拷贝任务从 agent/build.gradle 抽取到根目录独立 gradle 文件中。

Purpose: 简化 agent/build.gradle，抽取通用构建任务到根目录
Output: 根目录新增 config-template.gradle，agent/build.gradle 引用该文件
</objective>

<context>
当前代码位于:
- agent/build.gradle (lines 57-77): afterEvaluate 块中的 Copy 任务

涉及文件:
- agent/build.gradle: 需要移除内嵌的 Copy 任务
- 需要创建: 根目录 config-template.gradle
</context>

<tasks>

<task type="auto">
  <name>创建根目录 config-template.gradle</name>
  <files>config-template.gradle</files>
  <action>
在项目根目录创建 config-template.gradle 文件，包含:
- 定义 ext.configTemplateFile = "${rootProject.projectDir}/config.json.template"
- 提供 copyConfigTemplate task，可被子模块 apply from 调用
- 使用 project.afterEvaluate 确保 android 插件已加载
  </action>
  <verify>
config-template.gradle 存在且语法正确
  </verify>
  <done>config-template.gradle 创建完成，包含 Copy 任务定义</done>
</task>

<task type="auto">
  <name>修改 agent/build.gradle 引用外部 gradle 文件</name>
  <files>agent/build.gradle</files>
  <action>
1. 移除 lines 57-77 的 afterEvaluate Copy 任务块
2. 在文件末尾添加: apply from: rootProject.file('config-template.gradle')
3. 确保引用路径正确
  </action>
  <verify>
gradle :agent:tasks --group=build setup 可看到 copyConfigTemplate 任务
  </verify>
  <done>agent/build.gradle 成功引用 config-template.gradle</done>
</task>

<task type="auto">
  <name>验证构建</name>
  <files>config-template.gradle, agent/build.gradle</files>
  <action>
执行 ./gradlew :agent:assembleDebug 验证:
1. config.json.template 被正确拷贝到 assets 目录
2. 构建无错误
  </action>
  <verify>
./gradlew :agent:assembleDebug 成功
  </verify>
  <done>config.json 正确生成在 assets 目录</done>
</task>

</tasks>

<verification>
- config-template.gradle 存在且可被 agent 模块引用
- agent/build.gradle 移除了内嵌的 Copy 任务
- 构建成功，config.json 正确打包
</verification>

<success_criteria>
1. 根目录有 config-template.gradle 文件
2. agent/build.gradle 引用该文件
3. 构建成功，config.json 正确打包到 APK
</success_criteria>

<output>
After completion, create `.planning/quick/4-config-json-template-agent-build-gradle-/4-SUMMARY.md`
</output>
