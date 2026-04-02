# ARM64 Android SDK Directory Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在仓库内生成一份可独立复用的 ARM64 Linux Android SDK 目录，并用该目录 fresh 跑通 `conan install`、`:agent-core:assembleDebug`、`:app:assembleDebug`。

**Architecture:** 先把当前已验证可用的 ARM64 CMake、ARM64 Ninja、ARM64 NDK host toolchain 组合沉淀到 `dist/android-sdk-aarch64/`，再把仓库配置收敛到消费这份目录，而不是依赖当前机器 `/home/tony/...` 下的临时替身。验证阶段通过独立 `sdk.dir` 重新证明构建链闭环。

**Status Note (2026-04-02):** 当前实际 fresh 验证通过的配置仍然使用 `/home/tony/Android/android-sdk-aarch64`，并由项目级 `gradle.properties` 显式指定 `/home/tony/Android/android-sdk-aarch64/build-tools/34.0.0/aapt2`。仓库默认 `cmake` / `ndkVersion` 不应直接切到 ARM64 本地实验值；本地 ARM64 版本覆盖应通过 `~/.gradle/gradle.properties` 和 Conan 命令参数完成。`dist/android-sdk-aarch64/` 仍是目标态，不应误读为已经落地。

**Tech Stack:** Gradle, Android SDK directory layout, Conan 2, CMake, Android NDK, Bash, Markdown docs.

---

## File Structure

### Existing files to modify

- `agent-core/build.gradle`
  - 保持仓库默认版本，并提供本地 Gradle 属性覆盖入口。
- `agent-core/android.profile`
  - 移除单机私有路径依赖，改由本地 Conan 命令或 profile include 注入 NDK 路径。
- `local.properties`
  - 切换为指向独立 SDK 目录，作为 fresh 验证入口。
- `README.md`
  - 补充独立 SDK 目录的使用方式与验证命令。

### New files and directories to create

- `dist/android-sdk-aarch64/`
  - 独立 Android SDK 根目录。
- `dist/android-sdk-aarch64/build-tools/<version>/`
  - 当前项目构建所需 build-tools。
- `dist/android-sdk-aarch64/platform-tools/`
  - 当前项目调试与构建所需 platform-tools。
- `dist/android-sdk-aarch64/platforms/android-34/`
  - 当前项目 compileSdk 所需 platform。
- `dist/android-sdk-aarch64/licenses/`
  - Android SDK 许可文件。
- `dist/android-sdk-aarch64/cmake/3.31.6/`
  - ARM64 可运行的 CMake 目录。
- `dist/android-sdk-aarch64/ndk/26.3.11579264/`
  - ARM64 可运行的 NDK 目录。
- `docs/android-sdk-aarch64.md`
  - 说明目录用途、使用方法和验证结果。

## Chunk 1: Build A Self-Contained SDK Directory

### Task 1: Inventory the exact SDK components needed

**Files:**
- Inspect: `/home/tony/android-studio/Sdk`
- Inspect: `/home/tony/Android/Sdk`
- Inspect: `/home/tony/tmp/termux-ndk-r29-aarch64/full/android-ndk-r29`
- Output target: `dist/android-sdk-aarch64/`

- [ ] **Step 1: Verify the exact component versions currently used**

Run commands to confirm:

- compile platform version
- build-tools version
- platform-tools presence
- CMake version actually used by Gradle
- NDK version expected by project

- [ ] **Step 2: Verify ARM64 host architectures for candidate binaries**

Run `file` on:

- `cmake`
- `ninja`
- `clang`
- `clang++`

Expected: final selected binaries are ARM64.

- [ ] **Step 3: Create the target SDK root directory**

Create:

- `dist/android-sdk-aarch64/`

- [ ] **Step 4: Copy the required standard SDK subdirectories**

Copy into target root:

- `platforms/android-34`
- `platform-tools`
- `build-tools/<verified-version>`
- `licenses`

- [ ] **Step 5: Copy the ARM64 CMake directory into target SDK**

Copy:

- `<verified-arm64-cmake>/` -> `dist/android-sdk-aarch64/cmake/3.31.6/`

- [ ] **Step 6: Copy the ARM64 NDK directory into target SDK**

Copy the actual NDK contents, not a symlink:

- `<verified-arm64-ndk>/` -> `dist/android-sdk-aarch64/ndk/26.3.11579264/`

- [ ] **Step 7: Verify the copied directory is self-contained**

Run `file` and `ls -l` against copied binaries.
Expected:

- key executables are ARM64
- no critical paths are symlinks back to `/home/tony/Android/Sdk` or `/home/tony/tmp`

## Chunk 2: Point The Repository At The New SDK Directory

### Task 2: Update project configuration to consume the new SDK

**Files:**
- Modify: `agent-core/build.gradle`
- Modify: `agent-core/android.profile`
- Modify: `local.properties`

- [ ] **Step 1: Write a small failing verification assumption list**

Document the assumptions being tested:

- project uses `cmake 3.31.6`
- Conan reads NDK from the target SDK root
- no configuration path still points at old `/home/tony/...` SDK paths

- [ ] **Step 2: Update `agent-core/build.gradle` to keep repository defaults but allow local override**

Keep the repository default aligned with `origin/main`, and expose local Gradle properties for ARM64-specific overrides.

- [ ] **Step 3: Update `agent-core/android.profile` to stop depending on old single-machine paths**

Do not hardcode a Linux-host local path in the repository; pass the NDK path from the local Conan command or a machine-local profile include.

- [ ] **Step 4: Update `local.properties` to point `sdk.dir` at `dist/android-sdk-aarch64`**

Remove obsolete temporary path settings that were only needed during host-machine experiments.

- [ ] **Step 5: Grep the repo for stale SDK/NDK absolute paths**

Run search for:

- `/home/tony/Android/Sdk`
- `/home/tony/android-studio/Sdk`
- `/home/tony/tmp/termux-ndk`
- `/Users/caixiao`

Expected: no stale runtime-critical path remains in project config.

## Chunk 3: Fresh Verification With The Independent SDK Directory

### Task 3: Rebuild from the new SDK root

**Files:**
- Read-only verification of generated outputs

- [ ] **Step 1: Run Conan install using the updated configuration**

Run:

```bash
conan install . -pr android.profile -s arch=armv8 \
  -c tools.android:ndk_path=/home/tony/Android/android-sdk-aarch64/ndk/26.3.11579264 \
  --build missing
```

Workdir: `agent-core`

Expected: `Install finished successfully`.

- [ ] **Step 2: Run `:agent-core:assembleDebug`**

Run:

```bash
JAVA_HOME="/home/tony/android-studio/jbr" PATH="/home/tony/android-studio/jbr/bin:$PATH" ./gradlew :agent-core:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run `:app:assembleDebug`**

Run:

```bash
JAVA_HOME="/home/tony/android-studio/jbr" PATH="/home/tony/android-studio/jbr/bin:$PATH" ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Confirm artifact outputs exist**

Verify:

- `agent-core/build/outputs/aar/agent-core-debug.aar`
- `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 5: Record any residual warnings without treating them as blockers**

If strip warnings remain, note them in docs as known non-blocking caveats.

## Chunk 4: Document And Hand Off The SDK Directory

### Task 4: Write usage documentation

**Files:**
- Create: `docs/android-sdk-aarch64.md`
- Modify: `README.md`

- [ ] **Step 1: Write the standalone SDK directory usage doc**

Document:

- directory location
- supported platform: ARM64 Linux
- required `local.properties` example
- required Java path assumption
- verified build commands
- known caveats

- [ ] **Step 2: Add a short entry to `README.md`**

Link to the new doc and mention where the SDK directory lives.

- [ ] **Step 3: Verify docs match the actual built directory**

Check that documented paths and versions match files on disk.

Plan complete and saved to `docs/superpowers/plans/2026-04-02-arm64-android-sdk-directory.md`. Ready to execute?
