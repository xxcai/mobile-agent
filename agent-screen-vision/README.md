## Agent Screen Vision

This module contains the offline OCR and UI-analysis SDK migrated from `mobile-cor-codeX`.

What it adds:
- `ScreenVisionSdk` and the native runtime from the migrated project
- `ScreenVisionSnapshotAnalyzer`, which adapts the SDK to `agent-android`
- `AgentScreenVision.install(context)`, which registers the analyzer through `AgentInitializer`

Runtime notes:
- module `minSdk` is aligned to `24` so the demo app can depend on it directly
- screenshot capture uses `PixelCopy.request(Window, ...)`, so the analyzer is only installed on Android 8.0+ (`API 26`)

Suggested app-side wiring:
```java
AgentScreenVision.install(this);
```

Suggested routing:
- keep `native_xml` as the default source for standard Android pages
- route selected activities to `screen_snapshot` through `ActivityViewContextSourcePolicy`
- consume `hybridObservation.summary`, `hybridObservation.actionableNodes`, and `hybridObservation.conflicts` first, and keep `snapshotId` for observation-bound gesture execution