# Android Route Tooling Guide

本文面向宿主 App 开发者，说明当前仓库里的 route tooling 如何接入、如何声明 native route manifest，以及如何调用 route shortcuts。

## 能力范围

当前 route tooling 解决两件事：

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

当前 route tooling 通过默认 shortcut runtime 路径接入：

- `resolve_route`
- `open_resolved_route`

默认 shortcut 注册入口在 [RouteShortcutProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteShortcutProvider.java)。

需要注意：

- 默认 app 初始化路径已经通过 `run_shortcut` 暴露 route tooling
- agent 默认应先命中 `route_navigator` skill，再按 skill 规程调用 `run_shortcut`
- 如果缺少 route shortcut 的详细定义或参数结构，应先调用 `describe_shortcut`
- 带参数 route 的真实 LLM 链路已验证通过，推荐固定走：
  - `read_file("skills/route_navigator/SKILL.md")`
  - `resolve_route`
  - 必要时 `describe_shortcut("open_resolved_route")`
  - `open_resolved_route`

推荐协议是：

```json
{
  "shortcut": "resolve_route",
  "args": {}
}
```

和：

```json
{
  "shortcut": "open_resolved_route",
  "args": {}
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

### 基础输入

`open_resolved_route` 接收已经解析好的 target：

- `targetType`
- `uri`
- `title`

### 可选 routeArgs

当目标 route 在 manifest 中声明了参数时，可以额外传入 `routeArgs`。

`routeArgs` 的格式是“按参数名组织的对象”，每个参数项都是：

```json
{
  "value": "...",
  "encoded": true
}
```

示例：

```json
{
  "shortcut": "open_resolved_route",
  "args": {
    "targetType": "native",
    "uri": "ui://myapp.im/createGroup",
    "title": "createGroup",
    "routeArgs": {
      "source": {
        "value": "agent card",
        "encoded": false
      }
    }
  }
}
```

执行时序固定：

1. 校验 target
2. 如果存在 `routeArgs`，按 manifest 参数元数据组装最终 URI
3. 如前台是 `ContainerActivity`，先收起容器页
4. 等待宿主前台稳定
5. 调用宿主 `openUri(...)`

当前结果只区分成功或失败，不做跳转后页面确认。

### LLM 调用建议

如果调用方是 LLM，建议按下面的约束使用：

- 顶层 `args` 只放：
  - `targetType`
  - `uri`
  - `title`
  - 可选 `routeArgs`
- 不要把 `source`、`payload`、`page`、`module` 这类 route 参数直接放到顶层
- 带参数 route 时，优先先读 `route_navigator`，并在不确定时调用 `describe_shortcut("open_resolved_route")`
- 当某个 manifest 参数声明了 `encode`：
  - 传原始值时显式传 `encoded=false`
  - 传已编码值时显式传 `encoded=true`
- 如果收到错误 `routeArgs.<name>.encoded is required`，应补上 `encoded` 后重试，不要继续猜测

### encode + encoded 规则

参数是否编码由 manifest 中的 `params[].encode` 决定。

规则如下：

- 未声明 `encode`：宿主直接使用传入的原值
- 声明了 `encode` 且 `encoded=false`：宿主按 manifest 指定编码执行一次编码
- 声明了 `encode` 且 `encoded=true`：宿主直接使用传入值，不重复编码
- 声明了 `encode` 但缺少 `encoded`：按错误处理，不做内容猜测

当前支持的 `encode` 枚举：

- `url`
- `base64`
- `base64url`

说明：

- 当前只在 `open_resolved_route` 的 URI 组装阶段使用 `encode + encoded`
- 当前 runtime 不会靠内容猜测“这个值像不像已经编码”

## Native Route Manifest

### 目录约定

当前 native route manifest 统一放在：

`src/main/assets/mobile_agent/manifests/<module>.json`

demo 当前提供的样例文件是：

- [app.json](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/mobile_agent/manifests/app.json)

运行时会扫描 `mobile_agent/manifests/` 目录下所有 `.json` 文件，并合并成 `NativeRouteRegistry`。

### 最小 schema

```json
{
  "module": "myapp.app",
  "routes": [
    {
      "path": "ui://myapp.im/createGroup",
      "description": "创建群聊页面",
      "params": [
        {
          "name": "source",
          "required": false,
          "description": "入口来源；如果最终拼到 query 参数里且包含中文、空格或保留字符，需要按 url 编码；调用侧需要显式标记当前值是否已编码。",
          "encode": "url"
        }
      ]
    }
  ]
}
```

字段说明：

- 顶层 `module`：必填，对应 native module 标识
- 顶层 `routes`：必填数组，可为空但不能缺失
- `routes[].path`：必填，基础 URI
- `routes[].description`：选填，页面说明
- `routes[].params`：选填，参数声明数组
- `params[].name`：必填
- `params[].required`：选填，默认 `false`
- `params[].description`：选填
- `params[].encode`：选填，不写表示不要编码

当前不包含：

- `schemaVersion`
- `shortcuts`
- `skills`
- route 级别的 `module`

### 当前代码落点

manifest 相关实现位于：

- [RouteManifestLoader.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/manifest/RouteManifestLoader.java)
- [RouteManifestParser.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/manifest/RouteManifestParser.java)
- [ManifestBackedRouteUriComposer.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/manifest/ManifestBackedRouteUriComposer.java)

native registry model 和 bridge 适配器位于 `agent-android`：

- [NativeRouteRegistry.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/NativeRouteRegistry.java)
- [NativeRouteRegistryEntry.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/NativeRouteRegistryEntry.java)
- [RegistryBackedNativeRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RegistryBackedNativeRouteBridge.java)

当前运行时已经由 manifest 驱动，不再依赖 `DefaultNativeRouteRegistry` 参与装配。

## 验证入口

### 手工验证

当前 app 已提供最小手工验证入口：

- 启动 app 后，长按首页顶部标题“微信”
- 进入 [RouteManualVerificationActivity.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/RouteManualVerificationActivity.java)
- 可直接触发：
  - 无参 route
  - `encode=url` 且 `encoded=false`
  - `encode=url` 且 `encoded=true`
  - `encode=base64` 且 `encoded=false`
  - 缺少 `encoded` 的错误分支

成功用例会打开 [RouteNativeDemoActivity.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/RouteNativeDemoActivity.java) 展示最终 URI。

### LLM 端到端验证

当前已验证：

- `route_navigator` 可按 `routeArgs + encoded` 调用 `open_resolved_route`
- `encode=url` 路径可正确补编码
- `encode=base64` 路径可正确补编码
- 当故意缺少 `encoded` 时，LLM 能根据错误 message 修正并重试

当前仍未增强的部分：

- route 自然语言召回仍主要依赖 `uri/module/title/description` 的字符串命中
- 对多别名页面的召回能力仍需要单独 feature 处理

## App 层接入点

App 层当前只需要提供或组装以下能力：

- native route manifest assets
- miniapp query source
- `HostRouteInvoker`

默认 shortcut 组装入口在 [RouteShortcutProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteShortcutProvider.java)。

说明：

- route tooling 的通用运行时、source model 和 bridge 适配器已经下沉到 `agent-android/src/main/java/com/hh/agent/android/route/`
- App 层主要保留 manifest 资源、demo invoker 和最终装配代码

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

## Demo 与正式能力边界

以下实现当前是 demo / 验证用途：

- [DemoHostRouteInvoker.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DemoHostRouteInvoker.java)
- [RouteNativeDemoActivity.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/RouteNativeDemoActivity.java)

这些实现的作用是：

- 验证 route tooling contract
- 验证 `ContainerActivity` 收起和跳转时序
- 展示最终实际打开的 URI

它们不是正式业务页面或正式宿主路由实现。

## 接真实能力时优先改哪里

接真实 native 名单时，优先改：

- `app` 各模块下的 `src/main/assets/mobile_agent/manifests/<module>.json`
- 如有需要，扩展 [RouteManifestLoader.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/manifest/RouteManifestLoader.java) 的扫描或校验规则

接真实 miniapp 查询时，优先改：

- [DefaultMockMiniAppQuerySource.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultMockMiniAppQuerySource.java)
- 或新增真实 `MiniAppQuerySource` 实现并在 [RouteShortcutProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteShortcutProvider.java) 中切换

通常不需要改：

- [RouteResolver.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteResolver.java)
- [RouteHint.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteHint.java)
- [RouteResolution.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteResolution.java)
- [RouteTarget.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteTarget.java)
- [RouteOpener.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteOpener.java)

## 手工测试清单

这轮没有新增专门的 mock 页面，继续复用 [RouteNativeDemoActivity.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/RouteNativeDemoActivity.java) 观察最终 URI。

建议至少覆盖以下用例：

1. 无参 route
   输入：`open_resolved_route` 打开 `ui://myapp.search/selectActivity`
   预期：页面打开，最终 URI 不变
2. `encode=url` 且 `encoded=false`
   输入：`source.value = "agent card"`
   预期：最终 URI 中 `source=agent+card`
3. `encode=url` 且 `encoded=true`
   输入：`source.value = "agent+card"`
   预期：最终 URI 保持 `agent+card`，不重复编码
4. `encode=base64` 且 `encoded=false`
   输入：`payload.value = "{\"tab\":\"message\"}"`
   预期：最终 URI 中 `payload=eyJ0YWIiOiJtZXNzYWdlIn0=`
5. 声明了 `encode` 但缺少 `encoded`
   输入：省略 `encoded`
   预期：shortcut 返回明确错误，不打开页面

## 当前代码边界

当前推荐分层如下：

- `agent-android`
  - route runtime
  - `RouteResolver` / `RouteOpener`
  - source model
  - bridge 适配器
- `app`
  - native route manifest assets
  - miniapp query source 数据源
  - manifest loader / URI composer
  - `DemoHostRouteInvoker`
  - 调试入口和 demo 页面

如果只是把 mock source 替换成真实 source，优先改 `app`，不要直接改 `agent-android` 的 runtime contract。
