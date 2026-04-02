# ARM64 Android SDK Directory Design

## Goal

在 `mobile-agent` 仓库内产出一份可独立复用的 Android SDK 目录，使 Android Studio 或 Gradle 在 ARM64 Linux 上仅通过配置 `sdk.dir=<该目录>` 即可完成当前项目的 NDK 构建与 `:app:assembleDebug`。

## Scope

本轮覆盖：

- 产出一个接近 Android SDK Manager 安装形态的独立目录
- 目录内包含当前项目编译所需的最小 SDK 组件
- 目录内的 `cmake`、`ninja`、`ndk` 在 ARM64 Linux 上可运行
- 用该目录 fresh 验证当前仓库的 `conan install`、`:agent-core:assembleDebug`、`:app:assembleDebug`
- 将仓库配置从“依赖当前机器私有替身路径”收敛到“依赖指定 SDK 目录”

本轮不覆盖：

- 模拟器、profiler、device manager 等 IDE 扩展组件
- 通过 SDK Manager 自动更新该目录
- 发布为压缩包之外的安装器或自更新工具
- 对 x86_64 Linux、macOS、Windows 的通用兼容

## Context

当前仓库在 ARM64 Linux 上已经通过一组本机级实验打通了 native 构建链：

- `agent-core:configureCMakeRelease[arm64-v8a]` 已可通过
- `:agent-core:assembleDebug` 已可通过
- `:app:assembleDebug` 已可通过

但当前可用状态依赖的是宿主机上的临时替身方案，而不是一份可独立复用的 SDK 目录：

- `agent-core/build.gradle` 临时切到了 `cmake 3.31.6`
- `local.properties` 临时指向 `android-studio/Sdk/cmake/3.31.6`
- `/home/tony/Android/Sdk/cmake/3.22.1/bin/ninja` 被本机 ARM64 `ninja` 替换
- `/home/tony/Android/Sdk/ndk/26.3.11579264` 被 `termux-ndk` 软链替换

这些修改已经证明当前真正可行的工具链组合是：

- ARM64 CMake
- ARM64 Ninja
- ARM64 Android NDK host toolchain

补充状态说明：截至 2026-04-02，项目实际 fresh 验证通过的配置仍然是 `/home/tony/Android/android-sdk-aarch64`，并通过项目级 `gradle.properties` 显式固定 `android.aapt2FromMavenOverride=/home/tony/Android/android-sdk-aarch64/build-tools/34.0.0/aapt2`。因此本设计文档中的 `dist/android-sdk-aarch64/` 仍然表示目标态，而不是当前已经交付完成的目录。

接下来的目标不是继续依赖当前机器目录，而是把这组已验证可行的组合固化成一份单独目录，例如 `dist/android-sdk-aarch64/`，让其他本地工程也能通过 `sdk.dir` 直接复用。

## Approaches Considered

### Approach A: 继续保留“宿主机现有 SDK + 本机替身修补”

优点：改动最少，当前机器已经可用。

缺点：不可移植，强依赖 `/home/tony/...`；无法作为独立 SDK 目录复用；容易污染本机原有 SDK。

### Approach B: 产出一份最小可编译 Android SDK 目录

优点：最符合“像 SDK Manager 下载后的 SDK 目录”的目标；可以通过 `sdk.dir` 独立复用；能隔离本机实验性修改。

缺点：需要显式整理目录结构，并补齐当前项目最小所需的组件集合。

### Approach C: 只产出一个“修补脚本”，要求用户先准备原始 SDK

优点：目录体积最小。

缺点：交付物不是独立 SDK 目录，仍依赖外部基础 SDK，不满足当前目标。

### Decision

采用 Approach B。

## Design

### 1. Directory Layout

目标目录命名为：

- `dist/android-sdk-aarch64/`

目录结构尽量贴近标准 Android SDK，至少包含：

- `build-tools/<version>/`
- `cmake/3.31.6/`
- `licenses/`
- `ndk/26.3.11579264/`
- `platform-tools/`
- `platforms/android-34/`

本轮以“当前项目可编译”的最小闭包为准，不额外引入 `emulator/`、`sources/`、`cmdline-tools/`。

### 2. Toolchain Composition Rules

这份 SDK 目录中的关键组件来源如下：

- `platforms`、`platform-tools`、`licenses`、`build-tools`：优先复用当前已验证可用的 Android SDK 目录内容
- `cmake/3.31.6`：使用当前 ARM64 Linux 上可运行的 CMake 目录
- `ndk/26.3.11579264`：直接放入当前已验证可运行的 ARM64 NDK 目录内容，而不是依赖软链
- `ninja`：应位于最终被 Gradle/CMake 实际使用的路径中，并保证是 ARM64 可执行文件

这里的关键原则是“目录内部自洽”，不能再依赖：

- `/home/tony/android-studio/...`
- `/home/tony/Android/Sdk/...`
- `/home/tony/tmp/...`

也就是说，交付出的目录本身必须包含构建所需的最终可执行文件，而不是再指向宿主机其他目录。

### 3. Repository Configuration Expectations

为保证仓库能真正消费这份独立 SDK 目录，仓库内配置需要满足以下约束：

- `agent-core/build.gradle` 固定使用 `cmake 3.31.6`
- `local.properties` 不再依赖当前机器私有目录，只需要由使用者设置 `sdk.dir=<独立 SDK 目录>`
- `agent-core/android.profile` 不能再写死旧机器路径，至少要与 `sdk.dir/ndk/26.3.11579264` 对齐

如果 `android.profile` 仍需显式 NDK 路径，则该路径必须来自当前 SDK 根目录推导或由本地命令覆盖，而不是仓库内硬编码单机私有路径。

### 4. Verification Strategy

验证必须用“独立 SDK 目录”重新证明，而不是沿用当前宿主机的临时替身状态。

最小验证链路：

1. 使用新的 `sdk.dir=dist/android-sdk-aarch64`
2. 在 `agent-core` 目录执行：
   - `conan install . -pr android.profile -s arch=armv8 --build missing`
3. 在仓库根目录执行：
   - `./gradlew :agent-core:assembleDebug`
   - `./gradlew :app:assembleDebug`

只有在上述命令 fresh 通过时，才能认为这份 SDK 目录达到了“可独立使用”的要求。

### 5. Documentation And Handoff

本轮还需要补充一份面向使用者的简短说明，至少覆盖：

- SDK 目录位置
- `local.properties` 示例
- 当前支持的平台范围（仅 ARM64 Linux）
- 已验证通过的构建命令
- 目录中哪些组件是为了 ARM64 Linux native 构建专门替换的

## Risks

### Risk 1: 项目仍隐式依赖宿主机其他路径

如果复制目录时遗漏了软链、脚本中的绝对路径或 Conan profile 中的旧路径，那么 `sdk.dir` 虽然指向新目录，构建实际仍可能回退到宿主机 SDK。

缓解方式：

- 检查新目录内软链目标
- grep 配置文件与生成工具链中的绝对路径
- 用 fresh 构建验证实际调用路径

### Risk 2: NDK 目录版本号与内部实际来源不一致

当前项目固定使用 `26.3.11579264`，但已验证可用的 ARM64 NDK 实际来自 `termux-ndk` 变体。若目录内容与版本号声明的组合被后续工具额外校验，可能引发兼容风险。

缓解方式：

- 保持项目对 `26.3.11579264` 的外部契约不变
- 在说明文档中显式注明该目录是 ARM64 Linux 可用替代实现

### Risk 3: strip / 其他宿主工具仍存在局部兼容问题

当前 `:app:assembleDebug` 已通过，但有 unstripped native libs 提示，说明某些宿主工具未必完全理想。

缓解方式：

- 先以“可编译和可打 debug 包”为交付标准
- 将 strip 警告保留为后续兼容性优化项，而不阻塞本轮独立 SDK 目录交付

## Success Criteria

完成后应满足：

- 仓库内存在一份独立目录 `dist/android-sdk-aarch64/`
- 该目录不依赖当前机器其他 SDK 路径中的关键可执行文件
- 使用 `sdk.dir=<该目录>` 可以完成 `conan install`、`:agent-core:assembleDebug`、`:app:assembleDebug`
- 项目文档中明确记录使用方式与限制范围
