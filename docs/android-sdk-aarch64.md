# ARM64 Android SDK Directory

当前项目在 ARM64 Linux 上实际使用的 Android SDK 目录是：

- `/home/tony/Android/android-sdk-aarch64`

长期目标仍然是把这套已验证可用的组合沉淀为仓库内可复用的 `dist/android-sdk-aarch64/`。但截至目前，fresh 验证通过的实际配置仍然基于上述宿主机目录。

## 支持范围

- 平台：ARM64 Linux
- Java：Java 21
- 当前已验证项目：`mobile-agent`

## 目录内容

当前已验证可用的 SDK 目录包含：

- `platforms/android-34`
- `build-tools/34.0.0`
- `build-tools/34.0.0/aapt2`
- `cmake/3.31.6`
- `cmake/3.22.1/bin/ninja`
- `ndk/26.3.11579264`
- `platform-tools`
- `licenses`

其中 ARM64 Linux 下关键可执行文件已确认可运行：

- `cmake/3.31.6/bin/cmake`
- `build-tools/34.0.0/aapt2`
- `cmake/3.22.1/bin/ninja`
- `ndk/26.3.11579264/toolchains/llvm/prebuilt/linux-x86_64/bin/clang`

## 使用方式

在项目根目录的 `local.properties` 中配置：

```properties
sdk.dir=/home/tony/Android/android-sdk-aarch64
cmake.dir=/home/tony/Android/android-sdk-aarch64/cmake/3.31.6
```

如果是其他 Android 工程，按你自己的绝对路径替换即可。

如果本地 ARM64 Linux 需要覆盖仓库默认的 CMake / NDK 版本，而不修改仓库默认配置，可在用户级 `~/.gradle/gradle.properties` 中增加：

```properties
agentCoreCmakeVersion=3.31.6
agentCoreNdkVersion=26.3.11579264
```

仓库默认值仍保持：

- `cmake`: `3.22.1`
- `ndkVersion`: `26.3.11579264`

也就是说，ARM64 Linux 上的版本切换应优先通过本地 Gradle 属性覆盖，而不是直接修改仓库内 `agent-core/build.gradle` 的默认版本。

项目级 `gradle.properties` 当前还需要显式保留：

```properties
android.aapt2FromMavenOverride=/home/tony/Android/android-sdk-aarch64/build-tools/34.0.0/aapt2
```

这样可以强制 AGP 使用 ARM64 的 `aapt2`，避免回退到 Maven 下发的 `x86_64` 二进制。

## 构建前准备

在 `agent-core/` 目录先执行 Conan 安装：

```bash
conan install . -pr android.profile -s arch=armv8 \
  -c tools.android:ndk_path=/home/tony/Android/android-sdk-aarch64/ndk/26.3.11579264 \
  --build missing
```

说明：

- 仓库内的 `agent-core/android.profile` 不再写死 `tools.android:ndk_path`
- 需要在本地通过 `-c tools.android:ndk_path=...` 或本地 profile include 注入

然后在仓库根目录执行：

```bash
JAVA_HOME="/home/tony/android-studio/jbr" PATH="/home/tony/android-studio/jbr/bin:$PATH" ./gradlew :agent-core:assembleDebug
JAVA_HOME="/home/tony/android-studio/jbr" PATH="/home/tony/android-studio/jbr/bin:$PATH" ./gradlew :app:assembleDebug
```

## 已验证结果

已 fresh 验证通过：

- `conan install . -pr android.profile -s arch=armv8 -c tools.android:ndk_path=/home/tony/Android/android-sdk-aarch64/ndk/26.3.11579264 --build missing`
- `./gradlew :agent-core:assembleDebug`
- `./gradlew :app:assembleDebug`

并且还验证过：

- 清理用户级 `~/.gradle/gradle.properties` 中的 `android.aapt2FromMavenOverride` 覆盖项后，项目构建只消费仓库级配置
- 通过用户级 `~/.gradle/gradle.properties` 覆盖 `agentCoreCmakeVersion` / `agentCoreNdkVersion`，可以在不修改仓库默认版本的前提下完成本地 ARM64 构建

## 已知限制

- 当前目录面向 ARM64 Linux，不保证 x86_64 Linux、macOS、Windows 可用
- `android.aapt2FromMavenOverride` 仍是项目级必要配置，因为 AGP 默认下载的 Maven `aapt2` 在当前 ARM64 Linux 上是 `x86_64`
- `:app:assembleDebug` 可能仍出现 native strip 警告，但不阻塞 Debug APK 产出
- 这份目录以“当前项目可构建”为目标，不包含 emulator 等额外 SDK 组件
- 当前文档描述的是“已验证可用的宿主机 SDK 目录”，不是仓库内 `dist/android-sdk-aarch64/` 已经落地的状态
