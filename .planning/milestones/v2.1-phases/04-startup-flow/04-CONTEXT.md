# Phase 4: 启动流程梳理 - Context

**Gathered:** 2026-03-09
**Status:** Ready for planning

<domain>
## Phase Boundary

梳理启动流程，检查并修复：
1. 内存泄漏风险
2. 主线程阻塞风险

</domain>

<decisions>
## Implementation Decisions

### 1. Android Context 泄漏检查

#### 1.1 NativeMobileAgentApiAdapter
- **位置**: `NativeMobileAgentApiAdapter.java:44`
- **问题**: 持有 Activity Context (`private Context context`)
- **生命周期**: 作为 MobileAgentApi 被 MainPresenter 持有，Activity 销毁时未清理
- **风险**: ⚠️ **存在泄漏风险** - Context 未在 onDestroy 中置空

#### 1.2 AndroidToolManager
- **位置**: `AndroidToolManager.java:29`
- **问题**: 持有 Activity Context (`private final Context context`)
- **生命周期**: 在 createApi() 中创建，MainPresenter 持有引用
- **风险**: ⚠️ **存在泄漏风险** - Context 未在 onDestroy 中置空

#### 1.3 MessageAdapter
- **位置**: `MessageAdapter.java:36-44`
- **问题**: 持有 Context 创建 Markwon，但 Markwon 是不可变的，可以接受
- **风险**: ✅ 低风险 - Markwon 是线程安全的，不持有 Activity 引用

#### 1.4 Tools (DisplayNotificationTool, ReadClipboardTool 等)
- **位置**: `DisplayNotificationTool.java:23`, `ReadClipboardTool.java:15`
- **问题**: 持有 Context
- **风险**: ⚠️ 需要检查 Tools 的生命周期管理

#### 1.5 WorkspaceManager (✅ 好榜样)
- **位置**: `WorkspaceManager.java:25`
- **做法**: 使用 `context.getApplicationContext()` 避免 Activity 泄漏
- **风险**: ✅ 无泄漏风险

### 2. 修复建议

| 对象 | 修复方案 |
|------|---------|
| NativeMobileAgentApiAdapter | 在 setContext() 后添加 clearContext()，在 onDestroy 中调用 |
| AndroidToolManager | 在 onDestroy 中置空 context |
| Tools | 检查并确保在不需要时释放 |

### 2. 主线程阻塞检查

#### AgentActivity.onCreate
- ⚠️ NativeMobileAgentApiAdapter.loadConfigFromAssets() - 同步读取文件
- ⚠️ presenter.loadMessages() - 虽然内部使用 executor，但初始化 createApi() 可能在主线程

### 3. 异步初始化
- 配置加载放到后台线程
- Agent 初始化放到后台线程

</decisions>

<specifics>
## Specific Ideas

- 现有 MVP 架构保持不变
- 异步初始化不改变 UI 交互逻辑

</specifics>

<deferred>
## Deferred Ideas

None

</deferred>

---

*Phase: 04-startup-flow*
*Context gathered: 2026-03-09*
