# Android Route Tooling Guide

本文面向宿主 App 开发者，说明如何接入当前仓库里的 route tooling 能力。

## 能力范围

当前 route tooling 只解决两件事：

- 将结构化 `RouteHint` 解析成可跳转的目标
- 调用宿主跳转能力打开目标页

当前不包含：

- 跳转后的页面识别
- 表单填写
- 手势执行闭环

## 支持的目标类型

- 原生页面：URI 使用 `ui://...`
- 小程序页面：URI 使用 `h5://数字编号`

示例：

- native: `ui://myapp.im/createGroup`
- miniapp: `h5://1001001`

## 当前接入边界

当前 route tooling 已经有两套接入方式：

- 默认 shortcut runtime 路径
  - `resolve_route`
  - `open_resolved_route`
- 兼容/调试路径
  - 旧 `ToolExecutor` 版 `resolve_route`
  - 旧 `ToolExecutor` 版 `open_resolved_route`

默认 shortcut 注册入口在 [RouteShortcutProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteShortcutProvider.java)。
兼容 `ToolExecutor` 注册入口在 [RouteToolProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteToolProvider.java)。

需要注意：

- 默认 app 初始化路径已经通过 `run_shortcut` 暴露 route tooling
- `ToolChannelTestActivity` 的 route probes 也已经切到 `run_shortcut`
- 旧 `ToolExecutor` 版 route tooling 仅保留为兼容/调试链路
- agent 默认应先命中 `route_navigator` skill，再按 skill 规程调用 `run_shortcut`
- 如果缺少 route shortcut 的详细定义或参数结构，应先调用 `describe_shortcut`

因此当前 route tooling 的推荐协议是：

```json
{
  "shortcut": "resolve_route",
  "args": { ... }
}
```

和：

```json
{
  "shortcut": "open_resolved_route",
  "args": { ... }
}
```

## resolve_route

### 输入

`resolve_route` 接收 `RouteHint`，当前支持的主要字段有：

- `targetTypeHint`
- `uri`
- `nativeModule`
- `miniAppName`
- `keywords`

其中：

- `uri` 适用于已知精确目标
- `nativeModule` 适用于已知原生模块但不知道具体页面
- `miniAppName` 适用于已知小程序名称
- `keywords` 是兜底线索

### 输出

`resolve_route` 返回 `RouteResolution`，状态包括：

- `resolved`
- `candidates`
- `not_found`
- `insufficient_hint`

成功命中时会返回统一 `RouteTarget`：

- `targetType`
- `uri`
- `title`

说明：

- native source 内部保留模块和页面描述
- miniapp source 内部保留 `appName`
- 对外统一映射成 `RouteTarget.title`

## open_resolved_route

`open_resolved_route` 只接收已经解析好的 target：

- `targetType`
- `uri`
- `title`

执行时序固定：

1. 校验 target
2. 如前台是 `ContainerActivity`，先收起容器页
3. 等待宿主前台稳定
4. 调用宿主 `openUri(...)`

当前结果只区分成功或失败，不做跳转后页面确认。

## App 层接入点

App 层当前只需要提供或组装以下能力：

- native route source
- miniapp query source
- `HostRouteInvoker`

默认 shortcut 组装入口在 [RouteShortcutProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteShortcutProvider.java)。
旧 `ToolExecutor` 兼容组装入口在 [RouteToolProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteToolProvider.java)。

说明：

- route tooling 的通用运行时、source model 和 bridge 适配器已经下沉到 `agent-android/src/main/java/com/hh/agent/android/route/`
- App 层主要保留宿主数据源、demo invoker 和最终装配代码

### Native

当前 native 侧使用 App registry 驱动，但 registry model 和 bridge 适配器位于 `agent-android`：

- [NativeRouteRegistry.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/NativeRouteRegistry.java)
- [NativeRouteRegistryEntry.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/NativeRouteRegistryEntry.java)
- [RegistryBackedNativeRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RegistryBackedNativeRouteBridge.java)
- [DefaultNativeRouteRegistry.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultNativeRouteRegistry.java)

当前 registry 只要求最小字段：

- `uri`
- `module`
- `description` 可选

后续如果宿主已有真实声明收集结果，优先替换 [DefaultNativeRouteRegistry.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultNativeRouteRegistry.java) 的数据来源。

### MiniApp

当前 miniapp 侧使用 App mock query source 驱动，但 query source contract 和 bridge 适配器位于 `agent-android`：

- [MiniAppQuerySource.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/MiniAppQuerySource.java)
- [MiniAppQueryResult.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/MiniAppQueryResult.java)
- [QuerySourceBackedMiniAppRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/QuerySourceBackedMiniAppRouteBridge.java)
- [DefaultMockMiniAppQuerySource.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultMockMiniAppQuerySource.java)

当前 query result 最小字段：

- `uri`
- `appName`
- `description` 可选

注意：

- miniapp 内部 source 允许保留 `appName`
- 映射到 `RouteTarget` 时统一使用 `title`
- miniapp URI 必须使用 `h5://数字编号`

后续接真实查询时，优先替换 [DefaultMockMiniAppQuerySource.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultMockMiniAppQuerySource.java) 或直接新增真实 `MiniAppQuerySource` 实现。

查询失败约定：

- bridge 返回空列表
- bridge 内记日志
- 不向 resolver 抛异常

如果真实查询仍然满足：

- 单 query 输入
- `h5://数字编号` URI
- `appName` + `description` 输出

通常不需要修改 [QuerySourceBackedMiniAppRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/QuerySourceBackedMiniAppRouteBridge.java) 本身。

## Demo 与正式能力边界

以下实现当前是 demo / 验证用途：

- [DemoHostRouteInvoker.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DemoHostRouteInvoker.java)
- [RouteNativeDemoActivity.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/RouteNativeDemoActivity.java)
- [ToolChannelTestActivity.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/ToolChannelTestActivity.java)

这些实现的作用是：

- 验证 route tooling contract
- 验证 `ContainerActivity` 收起和跳转时序
- 提供最小手工测试入口

它们不是正式业务页面或正式宿主路由实现。

## 接真实能力时优先改哪里

接真实 native 名单时，优先改：

- [DefaultNativeRouteRegistry.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultNativeRouteRegistry.java)
- [RegistryBackedNativeRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RegistryBackedNativeRouteBridge.java)

接真实 miniapp 查询时，优先改：

- [DefaultMockMiniAppQuerySource.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultMockMiniAppQuerySource.java)
- 或新增真实 `MiniAppQuerySource` 实现并在 [RouteShortcutProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteShortcutProvider.java) 中切换

通常不需要改：

- [RouteResolver.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteResolver.java)
- [RouteHint.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteHint.java)
- [RouteResolution.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteResolution.java)
- [RouteTarget.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteTarget.java)
- [RegistryBackedNativeRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RegistryBackedNativeRouteBridge.java)
- [QuerySourceBackedMiniAppRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/QuerySourceBackedMiniAppRouteBridge.java)

## 当前代码边界

当前推荐分层如下：

- `agent-android`
  - route runtime
  - `RouteResolver` / `RouteOpener`
  - source model
  - bridge 适配器
- `app`
  - native registry 数据源
  - miniapp query source 数据源
  - `DemoHostRouteInvoker`
  - 调试入口和 demo 页面

如果只是把 mock source 替换成真实 source，优先改 `app`，不要直接改 `agent-android` 的 runtime contract。
