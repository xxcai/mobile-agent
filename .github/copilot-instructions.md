# Copilot instructions for `mobile-agent`

## Build, test, and lint commands

- Android work here assumes **Java 21**, **Android SDK 34**, and **NDK 26.3.11579264**. On ARM64 Linux, follow `docs/superpowers/plans/2026-04-02-arm64-android-sdk-directory.md`; the verified local setup uses:
  - `JAVA_HOME=/home/tony/android-studio/jbr`
  - `ANDROID_HOME=/home/tony/Android/android-sdk-aarch64`
  - `ANDROID_SDK_ROOT=/home/tony/Android/android-sdk-aarch64`
- Before any native build that touches `agent-core`, install the Conan toolchain inputs:
  - `cd agent-core && conan install . -pr android.profile -s arch=armv8 --build missing && cd ..`
- Build the SDK modules:
  - `./gradlew :agent-core:assembleDebug :agent-android:assembleDebug :agent-screen-vision:assembleDebug`
- Build the demo app:
  - `./gradlew :app:assembleDebug`
- Run all unit tests:
  - `./gradlew :agent-core:testDebugUnitTest :agent-android:testDebugUnitTest :agent-screen-vision:testDebugUnitTest :app:testDebugUnitTest`
- Run a single unit test:
  - `./gradlew :agent-screen-vision:testDebugUnitTest --tests 'com.screenvision.sdk.internal.compact.PageContextPrunerTest'`
  - `./gradlew :app:testDebugUnitTest --tests 'com.hh.agent.mockbusiness.BusinessHomeFragmentTest'`
- Lint modules explicitly:
  - `./gradlew :agent-core:lint :agent-android:lint :agent-screen-vision:lint :app:lint`
- Export the offline SDK bundle / refresh demo AARs:
  - `./gradlew syncDemoSdkAars`
  - `./gradlew exportLocalMavenRepo --offline`
  - `./gradlew zipLocalMavenRepo --offline`

## High-level architecture

- Treat the repository as a four-layer Android agent stack:
  - `app`: demo host app, probe/debug screens, sample shortcut wiring, manifest-backed route tooling
  - `agent-android`: Java/Kotlin Android integration layer; owns `AgentInitializer`, `AndroidToolManager`, tool channels, view-context capture, gesture execution, WebView action execution, lifecycle/UI integration
  - `agent-core`: native/JNI agent runtime plus Java API surface; owns prompt construction, tool dispatch, workspace assets, and the `icraw` native engine
  - `agent-screen-vision`: optional on-device screenshot/OCR/UI-analysis module that augments `agent-android`
- The main runtime path is **observe -> understand -> act -> re-observe**, not a simple chat SDK:
  1. `AgentInitializer.initialize(...)` bootstraps config/workspace/logging, registers Java-side tools and shortcuts, and initializes the native runtime.
  2. `AndroidToolManager` aggregates top-level tool channels into `tools.json` and routes native tool calls back into Java.
  3. `android_view_context_tool` chooses the right observation source for the current page:
     - `native_xml` for native Android structure
     - `screen_snapshot` for screenshot + OCR/UI understanding
     - `web_dom` for WebView/H5 pages
  4. Native structure and screen-vision output are merged into `hybridObservation`; WebView pages keep `webDom/pageUrl/pageTitle`.
  5. Every observation is registered with a `snapshotId`, and later actions must stay bound to that snapshot.
  6. Native pages execute through `android_gesture_tool`; WebView pages execute through `android_web_action_tool`.
- `PromptBuilder` in `agent-core` assembles the runtime prompt from workspace assets in a defined order: `SOUL.md`, `USER.md`, `AGENTS.md`, `TOOLS.md`, always-on skills, skill summaries, memory, then tool/runtime sections. If a task touches prompt behavior, read `docs/architecture/prompt-construction.md` and the workspace assets under `agent-core/src/main/assets/workspace/`.
- For the current Android product, start with `README.md`, `docs/README.md`, and the `agent-*` / `app` modules. `cxxplatform/` is a separate subtree with its own `AGENTS.md`; do not treat it as part of the default Android integration path unless the task explicitly targets it.

## Key conventions

- Host-app business capabilities are modeled as `ShortcutExecutor`s and exposed through the stable top-level channels `run_shortcut` and `describe_shortcut`. Add a new `AndroidToolChannelExecutor` only when the feature is a genuinely new platform-level tool channel, not just another business action.
- For page understanding, prefer the fused output first:
  - read `hybridObservation.summary`
  - then `hybridObservation.actionableNodes`
  - then `hybridObservation.conflicts`
  - only fall back to raw `nativeViewXml`, `screenVisionCompact`, or `webDom` when you need evidence the fused layer did not preserve
- Gesture execution is intentionally **observation-bound**. Changes to page-action flows should preserve `snapshotId`-based validation and use observation metadata such as `targetNodeIndex` and `referencedBounds` instead of freehand coordinate guessing.
- WebView/H5 actions are a separate path. If the task is inside DOM content, prefer `web_dom` observation plus `android_web_action_tool`; do not force those flows through native gesture logic.
- The demo app is **AAR-based**, not wired directly to the library modules:
  - `app/build.gradle` consumes `agent-core-debug.aar`, `agent-android-debug.aar`, and `agent-screen-vision-debug.aar` from `app/libs`
  - root task `syncDemoSdkAars` refreshes those artifacts from the library modules
  - `app:preBuild` already depends on `syncDemoSdkAars`
- `agent-screen-vision` is designed as an optional module that auto-installs when it is on the host app classpath. For normal integration work, prefer `AgentInitializer.initialize(...)`; only wire `AgentScreenVision.install(...)` manually when the task is explicitly about replacing or bypassing the default auto-install path.
- Root `config-template.gradle` injects a generated `config.json` asset into Android modules when `config.json.template` exists at the repository root. If config-driven behavior seems missing, check whether the template file is present before debugging runtime code.
