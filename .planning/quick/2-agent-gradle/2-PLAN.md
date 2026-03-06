# Plan: 清理 agent 中未使用的 gradle 依赖

**must_haves:**
- 移除未使用的依赖后项目仍能正常构建

## 任务 1: 清理 agent/build.gradle 中未使用的依赖

- **files**: `agent/build.gradle`
- **action**: 移除未使用的依赖：
  - `androidx.appcompat:appcompat:1.6.1`
  - `com.google.android.material:material:1.11.0`
  - `testImplementation 'junit:junit:4.13.2'`
  - `androidTestImplementation 'androidx.test.ext:junit:1.1.5'`
  - `androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'`
- **verify**: 执行 `./gradlew :agent:assembleDebug` 确认构建成功
- **done**: 移除 agent/build.gradle 中所有未使用的依赖
