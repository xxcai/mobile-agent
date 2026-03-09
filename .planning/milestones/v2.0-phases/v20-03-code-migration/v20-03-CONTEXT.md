# Phase v20-03: 代码迁移 - Context

**Gathered:** 2026-03-09
**Status:** Ready for planning

<domain>
## Phase Boundary

将平台相关逻辑从 agent 模块上移到 app 模块。agent 模块重构为职责单一的通用 Agent（AAR），不含任何 Android 依赖，后续新增 Android 功能只需修改 app 模块。

</domain>

<decisions>
## Implementation Decisions

### 架构原则
- **agent**: 纯 Agent 逻辑，不含任何 Android 依赖，可打包为纯 Java AAR
- **app**: 负责 Android 平台集成、Tool 注册、生命周期管理

### 迁移范围

#### 保留在 agent（通用能力）- 无 Android 依赖
| 类 | 理由 |
|---|------|
| `MobileAgentApi` | 纯 Java 接口 |
| `NativeMobileAgentApi` | JNI 绑定层 |
| `NativeAgent` | C++ 引擎的 Java 包装 |
| `ToolExecutor` | 工具执行抽象接口 |
| `Message` / `Session` | 通用数据模型 |
| `AndroidToolCallback` | 回调接口（仅接口） |

#### 移至 app 模块（平台相关）- 含 Android 依赖
| 类 | 理由 |
|---|------|
| `AndroidToolManager` | 依赖 `Context`，需要 app 注入 |
| `tools/ShowToastTool` | 直接调用 Android Toast API |
| `tools/DisplayNotificationTool` | 直接调用 Android Notification API |
| `tools/ReadClipboardTool` | 直接调用 Android Clipboard API |
| `tools/TakeScreenshotTool` | 直接调用 Android MediaStore API |
| `tools/SearchContactsTool` | 直接调用 Android Contacts API |
| `tools/SendImMessageTool` | 直接调用 Android IMS API |
| `WorkspaceManager` | 依赖 Android 文件系统 API |

### 包结构
- app 模块中 tools 包: `com.hh.agent.tools`

### agent/library 目录处理
- 迁移后删除 tools/ 子目录
- 保留 api/ 和 model/ 子目录
- 精简为: agent/src/main/java/com/hh/agent/library/{api,model}/

### 验证方式
- 编译通过 + 完整功能测试（聊天、Tool 调用正常工作）

### 新的模块结构
```
agent (AAR - 通用 Agent，无 Android 依赖)
├── api/MobileAgentApi.java
├── api/NativeMobileAgentApi.java
├── NativeAgent.java
├── ToolExecutor.java
├── model/Message.java, Session.java
└── AndroidToolCallback.java

app (平台集成)
├── presenter/MainPresenter.java
├── presenter/NativeMobileAgentApiAdapter.java
├── tools/  ★ 从 agent 移入
│   ├── ShowToastTool.java
│   ├── DisplayNotificationTool.java
│   ├── ReadClipboardTool.java
│   ├── TakeScreenshotTool.java
│   ├── SearchContactsTool.java
│   └── SendImMessageTool.java
└── AndroidToolManager.java  ★ 从 agent 移入
```

### 执行顺序（按依赖顺序）
1. 创建 app/src/main/java/com/hh/agent/tools/ 目录
2. 移动 tools/*.java 到 app 模块（先移被依赖的）
3. 移动 AndroidToolManager.java 到 app 模块
4. 移动 WorkspaceManager.java 到 app 模块
5. 更新 agent 模块的 build.gradle（移除 Android 依赖）
6. 更新 app 模块的 build.gradle（添加 tools 目录）
7. 精简 agent/library 目录（删除 tools/，保留 api/ 和 model/）
8. 验证编译 + 完整功能测试

### 后续扩展优势
- 新增 Android Tool: 只需在 app/tools/ 添加实现类
- 新增平台能力: 在 app 模块添加 WorkspaceManager 等
- agent 模块无需任何改动
- 完全解耦 Agent 逻辑与平台能力

</decisions>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- MobileAgentApi 接口 — app 通过此接口与 agent 交互
- NativeMobileAgentApiAdapter — 当前在 app 中作为适配器
- ToolExecutor — 工具执行抽象，可复用于 app

### Integration Points
- MainPresenter 调用 mobileAgentApi 方法
- MainActivity 管理 Activity Context
- AndroidToolManager 依赖 Context，需从 app 注入

### 当前 Android 依赖分布
agent 模块中有 Android 依赖的文件:
- AndroidToolManager.java (android.content.Context)
- tools/*.java (android.* 各 API)
- WorkspaceManager.java (android files)
- NativeMobileAgentApi.java (android.util.Log)

</codebase_context>

<specifics>
## Specific Ideas

无特定需求，标准代码迁移操作。

</specifics>

<deferred>
## Deferred Ideas

- AAR 打包配置 — v2.0 里程碑的 out of scope 项
- 验证测试 — v20-04 阶段

</deferred>

---

*Phase: v20-03-code-migration*
*Context gathered: 2026-03-09*
