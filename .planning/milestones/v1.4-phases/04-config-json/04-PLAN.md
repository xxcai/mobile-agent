# Phase 4: 修复config.json安全问题 - Plan

## Goal
修复 config.json 文件的安全问题：通过模板 + Gradle 构建时复制的方式，避免敏感 API Key 进入代码仓库。

## Tasks

### Task 1: 创建 config.json 模板文件
- [ ] 将 `app/src/main/assets/config.json` 复制到项目根目录 `config.json.template`
- [ ] 确认模板文件包含实际 API Key

### Task 2: 更新 .gitignore
- [ ] 在 `.gitignore` 中添加忽略规则：`config.json.template`
- [ ] 清理之前多余的 config.json 规则（如果存在）

### Task 3: 添加 Gradle 构建任务
- [ ] 在 `agent/build.gradle` 中添加 `copyConfigTemplate` task：
  - 任务名：`copyConfigTemplate`
  - 在 `processDebugResources` 之前执行
  - 将模板复制到 build/intermediates 目录（而非源码目录）
  - 让 Gradle 打包机制自动收集到 AAR 中

### Task 4: 清理冗余代码
- [ ] 删除 `app/build.gradle` 中的 `injectApiKey` task（第 53-69 行）
  - 旧逻辑：从 local.properties 读取 apiKey 注入到 config.json
  - 新方案：模板文件已包含实际 apiKey，无需注入

### Task 5: 删除遗留文件
- [ ] 删除 `app/src/main/assets/config.json.bak`
- [ ] 删除项目根目录下的 `.planning/config.json`（如果存在）
- [ ] 删除 `app/build/` 目录中的旧 config.json（通过 clean）

### Task 6: 验证构建
- [ ] 运行 `./gradlew clean assembleDebug`
- [ ] 验证 `app/build/assets/` 目录包含 config.json
- [ ] 确认 git status 中 config.json 模板文件未被跟踪

## Implementation Notes

### Gradle Task 示例（agent/build.gradle）
```groovy
// 使用 variant-aware 方式，只处理当前构建类型
android.applicationVariants.all { variant ->
    def variantName = variant.name
    def capitalizeVariant = variantName.capitalize()
    def processResourcesTask = project.tasks.findByName("process${capitalizeVariant}Resources")

    if (processResourcesTask) {
        def copyTask = project.tasks.create(name: "copyConfigTemplate${capitalizeVariant}", type: Copy) {
            from "${projectDir}/src/main/assets/config.json.template"
            into "${project.buildDir}/intermediates/assets/${variantName}/merge${capitalizeVariant}Assets"
            rename 'config.json.template', 'config.json'
        }
        copyTask.dependsOn processResourcesTask
    }
}
```

### 注意事项
- 模板文件必须包含实际的 API Key，因为运行时需要
- 构建产物中的 config.json 不会被提交到仓库（build 目录已被忽略）
- 开发者在修改 API Key 时只需修改模板文件

## Verification Criteria
- [ ] `config.json.template` 存在且不被 git 跟踪
- [ ] 构建后 agent 的 assets 包含 config.json
- [ ] 运行 app 时能正确加载 config.json
