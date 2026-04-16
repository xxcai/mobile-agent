# ARM64 Android SDK 当前已完成项

## 背景

当前仓库还没有交付仓库内自包含的 `dist/android-sdk-aarch64/`，但已经完成一批与 ARM64 Linux 构建直接相关的收敛工作，并明确了当前真正可用的宿主机方案。

本文档只保留这些已完成内容。

## 已完成项

### 1. `agent-core/build.gradle` 已支持本地覆盖

当前仓库已完成：

- 保留默认 `cmake = 3.22.1`
- 保留默认 `ndkVersion = 26.3.11579264`
- 提供 `agentCoreCmakeVersion`
- 提供 `agentCoreNdkVersion`

这意味着本地 ARM64 Linux 可通过用户级 Gradle 属性覆盖版本，而无需直接修改仓库默认值。

### 2. `agent-core/android.profile` 已移除单机硬编码路径

当前 profile 已不再写死 NDK 路径，而是改为由本地 Conan 命令参数注入。

### 3. 当前可用的宿主机 SDK 方案已明确

当前实际验证通过的 SDK 目录是：

- `/home/tony/Android/android-sdk-aarch64`

对应使用方式已经明确：

- `local.properties` 指向该 SDK 目录
- 本地 JDK 使用 `/home/tony/android-studio/jbr`
- Conan 通过 `tools.android:ndk_path` 指向该目录下的 NDK
- 必要时通过本机级 `android.aapt2FromMavenOverride` 指向 ARM64 的 `aapt2`

### 4. 当前已验证命令已固定

已确认可用的命令为：

```bash
cd agent-core
conan install . -pr android.profile -s arch=armv8 \
  -c tools.android:ndk_path=/home/tony/Android/android-sdk-aarch64/ndk/26.3.11579264 \
  --build missing
cd ..

JAVA_HOME="/home/tony/android-studio/jbr" PATH="/home/tony/android-studio/jbr/bin:$PATH" ./gradlew :agent-core:assembleDebug
JAVA_HOME="/home/tony/android-studio/jbr" PATH="/home/tony/android-studio/jbr/bin:$PATH" ./gradlew :app:assembleDebug
```

### 5. 老版本 `agent-screen-vision` 的构建前置已核对

对 `/home/tony/Project/mobile-agent-old` 的回查已经确认：

- 旧仓库根任务 `syncDemoSdkAars`、`exportLocalMavenRepo`、`zipLocalMavenRepo` 都把 `agent-screen-vision` 当作正式产物参与构建或发布
- `agent-screen-vision/src/main/cpp/CMakeLists.txt` 直接从 `src/main/jniLibs/${ANDROID_ABI}` 加载：
  - `libMNN.so`
  - `libMNN_Express.so`
- 旧仓库实际保存了两套 ABI 的预编译库：
  - `agent-screen-vision/src/main/jniLibs/arm64-v8a/`
  - `agent-screen-vision/src/main/jniLibs/armeabi-v7a/`

当前仓库的根任务依赖关系与旧仓库保持一致，且 `agent-screen-vision/build.gradle.kts` 仍配置为同时编译：

- `arm64-v8a`
- `armeabi-v7a`

因此，**完整生成 3 个 SDK AAR 的前置条件** 不是只有 Conan + ARM64 SDK/NDK，还包括：

- `agent-screen-vision/src/main/jniLibs/arm64-v8a/libMNN.so`
- `agent-screen-vision/src/main/jniLibs/arm64-v8a/libMNN_Express.so`
- `agent-screen-vision/src/main/jniLibs/armeabi-v7a/libMNN.so`
- `agent-screen-vision/src/main/jniLibs/armeabi-v7a/libMNN_Express.so`

如果缺少其中任意一套，`:agent-screen-vision:assembleDebug` 会在对应 ABI 的 CMake/Ninja 阶段失败，进而阻塞 `syncDemoSdkAars` 和离线 Maven 导出链路。

### 6. 当前已验证的完整 SDK AAR 构建命令

在上述 4 个 `MNN` 预编译库存在的前提下，当前仓库已经验证通过：

```bash
cd agent-core
conan install . -pr android.profile -s arch=armv8 \
  -c tools.android:ndk_path=/home/tony/Android/android-sdk-aarch64/ndk/26.3.11579264 \
  --build missing
cd ..

JAVA_HOME="/home/tony/android-studio/jbr" PATH="/home/tony/android-studio/jbr/bin:$PATH" \
  ./gradlew :agent-core:assembleDebug :agent-android:assembleDebug :agent-screen-vision:assembleDebug
```

对应产物路径为：

- `agent-core/build/outputs/aar/agent-core-debug.aar`
- `agent-android/build/outputs/aar/agent-android-debug.aar`
- `agent-screen-vision/build/outputs/aar/agent-screen-vision-debug.aar`

### 7. 文档入口已统一

当前 ARM64 Linux 的用法说明已经统一收口在 `docs/superpowers/plans/2026-04-02-arm64-android-sdk-directory.md`，不再额外维护第二份独立说明文档。

## 当前结论

当前仓库中，与 ARM64 Linux 构建直接相关且已经落地的部分，主要是：

- 本地 Gradle 属性覆盖入口
- Conan 配置去硬编码
- 宿主机 SDK 方案固化
- `agent-screen-vision` 的 legacy JNI 预编译库前置已经明确
- 完整 3 模块 SDK AAR 构建命令已经验证
- 文档入口统一

本文档不再保留未完成的独立 SDK 目录目标、目录结构设计或对应的分阶段待办。
