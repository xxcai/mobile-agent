---
phase: quick
plan: 5
type: execute
wave: 1
depends_on: []
files_modified: [config-template.gradle, app/build.gradle]
autonomous: true
requirements: []
must_haves:
  truths:
    - "config-template.gradle 支持 app 模块和应用构建变体"
    - "app/build.gradle 成功引用 config-template.gradle"
    - "构建成功，app 模块正确打包 config.json"
  artifacts:
    - path: "config-template.gradle"
      provides: "支持 library 和 application 变体的 config.json.template 拷贝任务"
    - path: "app/build.gradle"
      provides: "引用 config-template.gradle"
  key_links:
    - from: "app/build.gradle"
      to: "config-template.gradle"
      via: "apply from: rootProject.file('config-template.gradle')"
---

<objective>
优化 quick task 4 生成的 config-template.gradle，使其同时支持 library 和 application 变体，并在 app 模块中引用。

Purpose: 让 config-template.gradle 可被 app 模块复用
Output: config-template.gradle 支持 app 模块，app/build.gradle 引用该文件
</objective>

<context>
当前代码位于:
- config-template.gradle (lines 8-24): 仅使用 android.libraryVariants.all
- app/build.gradle: 未引用 config-template.gradle

涉及文件:
- config-template.gradle: 需要修改为同时支持 libraryVariants 和 applicationVariants
- app/build.gradle: 需要添加 apply from 引用
</context>

<tasks>

<task type="auto">
  <name>修改 config-template.gradle 同时支持 app 和 library 变体</name>
  <files>config-template.gradle</files>
  <action>
修改 config-template.gradle:
1. 保留 libraryVariants 处理逻辑
2. 添加 applicationVariants 处理逻辑（用于 app 模块）
3. 使用统一的变量命名避免冲突
4. 目标路径改为 "intermediates/assets/${variantName}/merge${capitalizeVariant}Assets/out" (app 模块使用)

注意: library 模块使用 library_assets 路径，app 模块使用 assets 路径
  </action>
  <verify>
./gradlew tasks --all | grep -i "copyConfigTemplate" 显示两个变体的任务
  </verify>
  <done>config-template.gradle 同时支持 library 和 application 变体</done>
</task>

<task type="auto">
  <name>修改 app/build.gradle 引用 config-template.gradle</name>
  <files>app/build.gradle</files>
  <action>
在 app/build.gradle 文件末尾添加:
apply from: rootProject.file('config-template.gradle')

确保在 dependencies 块之后添加
  </action>
  <verify>
app/build.gradle 包含 apply from 语句
  </verify>
  <done>app/build.gradle 成功引用 config-template.gradle</done>
</task>

<task type="auto">
  <name>验证构建</name>
  <files>config-template.gradle, app/build.gradle</files>
  <action>
执行 ./gradlew :app:assembleDebug 验证:
1. config.json.template 被正确拷贝到 app 模块的 assets 目录
2. 构建无错误
  </action>
  <verify>
./gradlew :app:assembleDebug 成功
  </verify>
  <done>app 模块正确生成 config.json 在 assets 目录</done>
</task>

</tasks>

<verification>
- config-template.gradle 同时支持 library 和 application 变体
- app/build.gradle 成功引用 config-template.gradle
- 构建成功，config.json 正确打包到 app APK
</verification>

<success_criteria>
1. config-template.gradle 修改完成，支持两种变体
2. app/build.gradle 引用该文件
3. 构建成功，config.json 正确打包到 APK
</success_criteria>

<output>
After completion, create `.planning/quick/5-quick-task-4-gradle-app/5-SUMMARY.md`
</output>
