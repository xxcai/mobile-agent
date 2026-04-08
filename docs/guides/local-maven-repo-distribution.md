# 本地 Maven 仓库离线分发方案

这套方案适用于下面这种场景：

- 个人电脑可以正常开发和解析依赖
- 公司电脑不能直接访问 `google()` / `mavenCentral()`
- 宿主工程需要集成 `agent-core`、`agent-android`、`agent-screen-vision`
- 希望把 SDK 本身和三方依赖一起打成一个可拷贝的本地 Maven 仓库

## 方案目标

导出后的仓库同时包含：

- `com.hh.agent:agent-core:1.0.0-debug`
- `com.hh.agent:agent-android:1.0.0-debug`
- `com.hh.agent:agent-screen-vision:1.0.0-debug`
- `gson`
- `ML Kit`
- `play-services` / `firebase` / `datatransport` / `odml` 等传递依赖

这样公司电脑在构建时只需要指向这份本地 Maven 仓库，不再依赖公网仓库，也不需要手工往 `libs/` 里塞一堆 jar/aar。

## 个人电脑导出

### 方式一：直接运行 Gradle 任务

如果你的环境能直接执行 wrapper 或系统 Gradle，可以运行：

```bash
./gradlew exportLocalMavenRepo --offline
./gradlew zipLocalMavenRepo --offline
```

### 方式二：运行封装脚本（Windows 推荐）

```powershell
.\scripts\export-local-maven-repo.ps1
```

只导出目录、不打 zip：

```powershell
.\scripts\export-local-maven-repo.ps1 -SkipZip
```

这个脚本会按顺序尝试：

- `gradlew.bat`
- `bash ./gradlew`
- 本机 `~/.gradle/wrapper/dists/` 下的 `gradle.bat`
- 系统环境变量中的 `gradle`

## 导出产物

导出完成后会得到：

- 本地仓库目录：`build/local-maven-repo/`
- 打包 zip：`build/distributions/mobile-agent-local-maven-repo-1.0.0-debug.zip`

推荐把 zip 或整个 `build/local-maven-repo/` 拷到公司电脑。

## 公司电脑接入

假设把仓库解压到宿主工程根目录下：

```text
third_party/mobile-agent-repo/
```

### 1. 在 `settings.gradle` 或根 `build.gradle` 增加本地仓库

如果项目使用 `dependencyResolutionManagement`，推荐加在 `settings.gradle`：

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url uri("$rootDir/third_party/mobile-agent-repo") }
        google()
        mavenCentral()
    }
}
```

如果公司环境不允许走公网，可以只保留本地 Maven 仓库：

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url uri("$rootDir/third_party/mobile-agent-repo") }
    }
}
```

### 2. 在宿主模块添加依赖

如果需要完整的 hybrid observation 能力，推荐：

```gradle
dependencies {
    implementation "com.hh.agent:agent-android:1.0.0-debug"
    implementation "com.hh.agent:agent-screen-vision:1.0.0-debug"
}
```

说明：

- `agent-core` 已经会通过传递依赖带进来，一般不需要单独写
- 如果宿主只需要基础 Android Agent 能力、不需要截图 OCR，可以只依赖 `agent-android`

### 3. 补充 Android 打包配置

```gradle
android {
    defaultConfig {
        ndk {
            abiFilters 'arm64-v8a'
        }
    }

    packagingOptions {
        jniLibs {
            pickFirsts += ['**/libc++_shared.so']
        }
    }
}
```

说明：

- `agent-core` 当前只提供 `arm64-v8a` native 库
- `agent-core` 和 `agent-screen-vision` 都包含 `libc++_shared.so`，需要 `pickFirst`

### 4. 初始化代码

如果宿主已经在调用：

```java
AgentInitializer.initialize(...);
```

通常不需要再额外调用：

```java
AgentScreenVision.install(...);
```

因为新版 `agent-android` 已经会在初始化时自动尝试反射安装 `AgentScreenVision`。

## 运行时验证日志

接入成功后，冷启动应用时优先关注这些日志：

- `screen_snapshot_auto_install_complete`
- `screen_snapshot_analyzer_set`
- `install_complete`
- `ScreenVisionSnapshotAnalyzer`

如果看到：

- `screen_snapshot_auto_install_unavailable reason=module_not_on_classpath`

说明 `agent-screen-vision` 没有真正进入宿主运行时 classpath。

## 什么时候还需要宿主改代码

通常只有两类情况需要额外适配：

- 宿主自己手写了 `nativeViewXml` 解析逻辑
- 宿主需要通过 `ActivityViewContextSourcePolicy` 明确把某些页面路由到 `screen_snapshot`

如果宿主只是黑盒调用 SDK，并且已经有 `AgentInitializer.initialize(...)`，多数情况下不需要改业务初始化代码。
