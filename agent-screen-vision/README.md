## Agent Screen Vision

This module contains the offline OCR and UI-analysis SDK migrated from `mobile-cor-codeX`.

What it adds:
- `ScreenVisionSdk` and the native runtime from the migrated project
- `ScreenVisionSnapshotAnalyzer`, which adapts the SDK to `agent-android`
- `AgentScreenVision.install(context)`, which registers the analyzer through `AgentInitializer`

Runtime notes:
- module `minSdk` is aligned to `24` so the demo app can depend on it directly
- screenshot capture uses `PixelCopy.request(Window, ...)`, so the analyzer is only installed on Android 8.0+ (`API 26`)
- when the module is present on the host app classpath, the latest `agent-android` will auto-install it during `AgentInitializer.initialize(...)`

Suggested app-side wiring:
```java
AgentInitializer.initialize(...);
```

Local Maven repo export:
- run `./gradlew exportLocalMavenRepo --offline` from Git Bash/macOS/Linux
- or run `./scripts/export-local-maven-repo.ps1` on Windows
- consume the exported repository from another machine with `maven { url uri(".../local-maven-repo") }`

Suggested routing:
- keep `native_xml` as the default source for standard Android pages
- route selected activities to `screen_snapshot` through `ActivityViewContextSourcePolicy`
- consume `hybridObservation.summary`, `hybridObservation.actionableNodes`, and `hybridObservation.conflicts` first, and keep `snapshotId` for observation-bound gesture execution
