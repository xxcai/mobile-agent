# Phase 3: 代码下沉 - Context

**Gathered:** 2026-03-09
**Status:** 需清理 app 模块冗余资源

<domain>
## Phase Boundary

将 AndroidToolManager、WorkspaceManager、Tools 从 app 模块下沉到 agent-android 模块，并清理 app 模块中的冗余资源。

</domain>

<decisions>
## Implementation Decisions

### 代码迁移（已完成）
- AndroidToolManager → agent-android
- 6 个 Android Tools → agent-android/tool/
- WorkspaceManager → agent-android
- Activity/UI → agent-android

### 资源清理

#### 1. app/assets/dist/ (可删除)
- **状态**: ✅ 可删除
- **原因**: 无任何 Java 代码引用此目录

#### 2. app/assets/tools.json (下沉到 agent-android)
- **状态**: 下沉到 agent-android
- **原因**: 代码在 agent-android 中读取，资源配置应与代码在一起
- **操作**: 移动到 `agent-android/src/main/assets/tools.json`

#### 3. app/res/ 资源文件分析

| 文件 | 状态 | 原因 |
|------|------|------|
| **values/strings.xml** | ❌ 必须保留 | AndroidManifest 引用 `@string/app_name` |
| **values/colors.xml** | ❌ 必须保留 | themes.xml 引用 `@color/primary`, `@color/primary_dark` |
| **values/themes.xml** | ❌ 必须保留 | AndroidManifest 引用 `@style/AppTheme` |
| **drawable/ic_launcher.xml** | ❌ 必须保留 | AndroidManifest 引用 `@drawable/ic_launcher` |
| **xml/network_security_config.xml** | ❌ 必须保留 | AndroidManifest 引用 `@xml/network_security_config` |
| layout/activity_main.xml | ✅ 可删除 | LauncherActivity 跳转到 AgentActivity，不再使用 |
| layout/item_message.xml | ✅ 可删除 | 同上 |
| layout/item_message_user.xml | ✅ 可删除 | 同上 |
| layout/item_thinking.xml | ✅ 可删除 | 无任何引用 |
| drawable/bg_edit_text.xml | ✅ 可删除 | 被 activity_main.xml 引用，可随 layout 删除 |
| drawable/bg_send_button.xml | ✅ 可删除 | 无任何引用 |

#### 4. 可删除文件清单

```
app/src/main/res/layout/activity_main.xml
app/src/main/res/layout/item_message.xml
app/src/main/res/layout/item_message_user.xml
app/src/main/res/layout/item_thinking.xml
app/src/main/res/drawable/bg_edit_text.xml
app/src/main/res/drawable/bg_send_button.xml
app/src/main/assets/dist/  (整个目录)
```

#### 5. 必须保留文件清单

```
app/src/main/res/values/strings.xml
app/src/main/res/values/colors.xml
app/src/main/res/values/themes.xml
app/src/main/res/drawable/ic_launcher.xml
app/src/main/res/xml/network_security_config.xml
```

### 最终状态
- app 模块：LauncherActivity + 必要的 AndroidManifest 资源
- agent-android 模块：Android 适配层 + UI
- agent-core 模块：纯 Java 核心

</decisions>

<specifics>
## Specific Ideas

无

</specifics>

<deferred>
## Deferred Ideas

None

</deferred>

---

*Phase: 03-code-sinking*
*Context gathered: 2026-03-09*
