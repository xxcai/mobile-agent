# Phase 2: Java Tools 注册机制 - Context

**Gathered:** 2026-03-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Java 层接收并执行 Android Tools。通过 ToolExecutor 接口和 tools.json 配置，实现可配置的 Android 工具系统。

</domain>

<decisions>
## Implementation Decisions

### ToolExecutor 位置
- **agent library**: 与 NativeAgent 同级
- 位于 `com.hh.agent.library` 包下
- 便于与 native 代码集成

### tools.json 加载方式
- **assets 目录预置**: 初始版本打包到 APK
- **workspace 拷贝**: 启动时拷贝到 workspace 目录
- **版本控制**: 引入 version 字段，支持后续网络下发更新
- 配置格式:
```json
{
  "version": 1,
  "tools": [
    {
      "name": "show_toast",
      "description": "显示 Toast 消息",
      "params": {
        "message": {"type": "string", "required": true}
      }
    }
  ]
}
```

### Tool 路由方式
- **策略模式**: 每个 Tool 一个实现类
- ToolExecutor 接口 + 具体 Tool 实现类
- 易于扩展新的 Tools

### 架构设计
```
AndroidToolManager (管理器)
    ├── ToolExecutor (接口)
    │   └── show_toast (策略实现)
    └── tools.json (配置)
```

</decisions>

<specifics>
## Specific Ideas

- 第一个 Tool: show_toast (用于测试)
- 后续可扩展: get_device_info, send_notification 等

</specifics>

<deferred>
## Deferred Ideas

- 网络下发更新 tools.json - 后续版本
- 动态注册 Tool - 后续版本

</deferred>

---

*Phase: 02-java-tools*
*Context gathered: 2026-03-04*
