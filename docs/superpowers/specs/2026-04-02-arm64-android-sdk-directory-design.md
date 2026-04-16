# ARM64 Android SDK 目录已完成设计说明

## 设计目标

为 `mobile-agent` 在 ARM64 Linux 上整理一套稳定的本地构建约束，使仓库能够在不改动默认版本的前提下，通过宿主机 SDK 目录、本地 Gradle 属性覆盖和 Conan 参数完成 native 构建。

## 当前已落地设计

### 1. 保持仓库默认版本稳定

当前仓库没有把 ARM64 Linux 的实验性版本直接写成默认值，而是保留：

- `agent-core/build.gradle` 默认 `cmake = 3.22.1`
- `agent-core/build.gradle` 默认 `ndkVersion = 26.3.11579264`

同时通过以下本地 Gradle 属性提供覆盖能力：

- `agentCoreCmakeVersion`
- `agentCoreNdkVersion`

### 2. Conan 配置不再依赖仓库内硬编码路径

`agent-core/android.profile` 当前已经移除固定 NDK 绝对路径，改为由本地 Conan 命令注入：

```bash
conan install . -pr android.profile -s arch=armv8 \
  -c tools.android:ndk_path=/home/tony/Android/android-sdk-aarch64/ndk/26.3.11579264 \
  --build missing
```

### 3. ARM64 Linux 的当前工作方案已经明确

当前已验证可用的宿主机目录是：

- `/home/tony/Android/android-sdk-aarch64`

本地 ARM64 Linux 工作方案包括：

- `local.properties` 指向该 SDK 目录
- Java 使用 `/home/tony/android-studio/jbr`
- 必要时通过本机级 `android.aapt2FromMavenOverride` 强制使用 ARM64 的 `aapt2`

### 4. `agent-screen-vision` 的 JNI 预编译依赖沿用老版本布局

对 `/home/tony/Project/mobile-agent-old` 的构建链回查后，可以确认以下事实：

- 根任务 `syncDemoSdkAars` 依赖 `:agent-screen-vision:assembleDebug`
- 根任务 `exportLocalMavenRepo` / `zipLocalMavenRepo` 依赖 `:agent-screen-vision` 的发布产物
- `agent-screen-vision/src/main/cpp/CMakeLists.txt` 直接从 `src/main/jniLibs/${ANDROID_ABI}` 读取：
  - `libMNN.so`
  - `libMNN_Express.so`

旧仓库实际保存了两套 ABI 的预编译库：

- `agent-screen-vision/src/main/jniLibs/arm64-v8a/`
- `agent-screen-vision/src/main/jniLibs/armeabi-v7a/`

当前仓库的 `agent-screen-vision/build.gradle.kts` 仍配置 `arm64-v8a` 和 `armeabi-v7a` 两个 ABI，因此完整 SDK AAR 构建必须把这 4 个 `.so` 文件视为正式构建前置，而不是“可选的本地调试残留物”。

### 5. ARM64 宿主机构建与完整 AAR 打包是两层验证

当前设计上需要区分两类验证：

1. **基础 ARM64 构建链验证**
   - `conan install`
   - `:agent-core:assembleDebug`
   - `:app:assembleDebug`

2. **完整 SDK 打包验证**
   - `:agent-core:assembleDebug`
   - `:agent-android:assembleDebug`
   - `:agent-screen-vision:assembleDebug`

第二层比第一层额外依赖 `agent-screen-vision` 的 `MNN` 预编译库是否齐全。只有这 4 个 `.so` 已就位，根任务 `syncDemoSdkAars` 和离线 Maven 导出链才具备可执行前提。

### 6. 文档收口策略已经落地

当前 ARM64 Linux 的说明已经收口到计划文档本身，仓库不再单独维护第二份 ARM64 说明文档，避免内容漂移。

## 当前已验证结果

当前仓库中已经确认可行的部分包括：

- `agent-core/build.gradle` 提供本地覆盖入口
- `agent-core/android.profile` 不再硬编码单机私有路径
- `README.md` 已指向统一的 ARM64 说明入口
- 宿主机目录 `/home/tony/Android/android-sdk-aarch64` 的使用方式、限制和验证命令已被文档化
- `agent-screen-vision` 的 legacy `jniLibs` 前置已经与老版本构建链对齐并被文档化
- 完整 3 模块 AAR 生成所需的额外前置条件已经明确

## 未纳入本文档的内容

以下目标尚未完成，因此本文档不再保留其目标态描述：

- 仓库内自包含的 `dist/android-sdk-aarch64/`
- 基于该独立目录的 fresh 构建闭环
- 围绕独立目录的最终交付结构说明

## 说明

本文档只保留当前仓库已实际落地的设计决策与配置收敛结果，不再保留未完成的目标态方案。
