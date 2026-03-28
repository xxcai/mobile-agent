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

## 对外 Tool

当前通过 `call_android_tool` 暴露两个 business tool：

- `resolve_route`
- `open_resolved_route`

工具注册入口在 [RouteToolProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteToolProvider.java)。

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

实际组装入口在 [RouteToolProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteToolProvider.java)。

### Native

当前 native 侧使用 App registry 驱动：

- [NativeRouteRegistry.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/NativeRouteRegistry.java)
- [NativeRouteRegistryEntry.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/NativeRouteRegistryEntry.java)
- [DefaultNativeRouteRegistry.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultNativeRouteRegistry.java)
- [RegistryBackedNativeRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RegistryBackedNativeRouteBridge.java)

当前 registry 只要求最小字段：

- `uri`
- `module`
- `description` 可选

后续如果宿主已有真实声明收集结果，优先替换 [DefaultNativeRouteRegistry.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultNativeRouteRegistry.java) 的数据来源。

### MiniApp

当前 miniapp 侧使用 App mock query source 驱动：

- [MiniAppQuerySource.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/MiniAppQuerySource.java)
- [MiniAppQueryResult.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/MiniAppQueryResult.java)
- [DefaultMockMiniAppQuerySource.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultMockMiniAppQuerySource.java)
- [QuerySourceBackedMiniAppRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/QuerySourceBackedMiniAppRouteBridge.java)

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
- [RegistryBackedNativeRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RegistryBackedNativeRouteBridge.java)

接真实 miniapp 查询时，优先改：

- [DefaultMockMiniAppQuerySource.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultMockMiniAppQuerySource.java)
- 或新增真实 `MiniAppQuerySource` 实现并在 [RouteToolProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteToolProvider.java) 中切换

通常不需要改：

- [RouteResolver.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteResolver.java)
- [RouteHint.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteHint.java)
- [RouteResolution.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteResolution.java)
- [RouteTarget.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteTarget.java)
