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
- we码（WeCode / 微码）页面：URI 使用 `h5://数字编号`

示例：

- native: `ui://myapp.im/createGroup`
- wecode: `h5://1001001`

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
- `weCodeName`
- `keywords`

其中：

- `uri` 适用于已知精确目标
- `nativeModule` 适用于已知原生模块但不知道具体页面
- `weCodeName` 适用于已知 we码名称
- `keywords` 是兜底线索

当前 native route 的召回顺序是：

1. 如果已知精确 `uri`，直接走 `uri_direct`
2. 如果只给了 `keywords`，runtime 会先尝试根据 manifest `routes[].keywords` 推断单一 `nativeModule`
3. 若成功收敛到单一 module，优先走 module 内召回
4. 若不能唯一收敛，则继续走全局 native route 召回
5. 如果多个目标同时命中，返回 `candidates`

当前 LLM 编排边界是：

- `resolved`：直接打开
- `candidates`：先尝试受控代选；若不能唯一判定，则定向追问
- `not_found`：不自动重试，只向用户说明未命中并提示补充线索
- `insufficient_hint`：直接追问更具体的页面、模块或小程序信息

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
- wecode source 内部保留 `appName`
- 对外统一映射成 `RouteTarget.title`

### `candidates` 的 LLM 处理边界

当前允许 LLM 直接代选的前提是：

- 用户原话里存在显式区分词
- 且该区分词能唯一映射到单个候选

当前允许作为显式区分词的主要类型：

- `module` 词，例如：`登录`、`账号安全`、`审批`
- 业务域词，例如：`IM`、`报销`、`通讯录`
- 入口来源词，例如：`首页入口`、`消息入口`
- 页面限定短语，例如：`登录里的`、`设置里的`

以下情况不允许直接代选，必须追问：

- 只有泛词，例如：`详情`、`设置`、`记录`、`密码`
- 区分词同时映射到多个候选
- 候选跨模块且用户原话没有提供足够差异线索

推荐追问方式：

- 围绕候选差异直接追问，例如：
  - `您要打开登录里的修改密码，还是账号安全里的修改密码？`
- 不要机械抛出完整候选列表后只问“您要哪一个”

### `not_found` 的 LLM 处理边界

当 `resolve_route` 返回 `not_found` 时：

- 本回合只允许两种动作：
  - 向用户说明未找到
  - 给出补充线索建议
- 本回合禁止：
  - 再次调用 `resolve_route`
  - 改写 `keywords_csv` 后重试
  - 把原词拆成更短关键词再试
  - 猜测 `open_resolved_route`

建议补充的信息类型：

- `module` / 业务域词
- 页面功能词
- 常见别名或口语化叫法

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
- 如果 `resolve_route` 返回 `candidates`：
  - 只有用户原话中存在显式区分词且能唯一映射时，才允许直接代选
  - 否则必须围绕候选差异追问，不要机械复述完整候选列表
- 用户一旦明确回复“第一个 / 第二个 / 前者 / 后者 / login 那个 / settings 那个”这类确认语句，应直接把上一轮候选映射为一个明确 target，并调用 `open_resolved_route`
- 如果上一轮已经围绕候选差异追问，而用户只回复“登录 / 账号安全 / 设置 / IM / 报销”这类差异词，也应直接映射回上一轮候选并调用 `open_resolved_route`
- 如果 `resolve_route` 返回 `not_found`：
  - 不自动 rewrite
  - 不自动再次调用 `resolve_route`
  - 只回抛给用户，并提示补模块 / 功能 / 别名线索

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
- [login.json](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/mobile_agent/manifests/login.json)
- [settings.json](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/mobile_agent/manifests/settings.json)

运行时会扫描 `mobile_agent/manifests/` 目录下所有 `.json` 文件，并合并成 `NativeRouteRegistry`。

### 最小 schema

```json
{
  "module": "myapp.app",
  "routes": [
    {
      "path": "ui://myapp.im/createGroup",
      "description": "创建群聊页面",
      "keywords": ["IM", "群聊", "建群", "创建群聊"],
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
- `routes[].keywords`：选填，召回词面数组；首阶段类型固定为 `string[]`
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
- `moduleKeywords` / `routeKeywords` 一类类型化字段

### keywords 约束

`routes[].keywords` 的设计边界如下：

- 仅用于召回增强
- 第一阶段固定为纯字符串数组
- 不区分“module 词”和“route 词”
- 不承担唯一定位责任
- 命中多个 route 或多个 module 时，合法结果是 `candidates`

建议：

- 每个 route 控制在 3-8 个关键词
- 同时放入：
  - 页面常见别名
  - 业务域 / module 线索

例如：

```json
{
  "path": "ui://myapp.login/resetPassword",
  "description": "修改密码页面",
  "keywords": ["登录", "密码", "修改密码", "忘记密码", "找回密码"]
}
```

### 当前代码落点

manifest 相关实现位于：

- [RouteManifestLoader.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/manifest/RouteManifestLoader.java)
- [RouteManifestParser.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/manifest/RouteManifestParser.java)
- [ManifestBackedRouteModuleResolver.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/manifest/ManifestBackedRouteModuleResolver.java)
- [ManifestBackedRouteUriComposer.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/manifest/ManifestBackedRouteUriComposer.java)

native registry model 和 bridge 适配器位于 `agent-android`：

- [NativeRouteRegistry.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/NativeRouteRegistry.java)
- [NativeRouteRegistryEntry.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/NativeRouteRegistryEntry.java)
- [RegistryBackedNativeRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RegistryBackedNativeRouteBridge.java)

当前运行时已经由 manifest 驱动，不再依赖 `DefaultNativeRouteRegistry` 参与装配。

### 当前召回能力边界

当前 native route 召回已经包含：

- `uri`
- `module`
- path 末段页面名
- `description`
- route `keywords`

其中：

- module-first 收缩由 [ResolveRouteShortcut.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/shortcut/ResolveRouteShortcut.java) 在进入 resolver 前完成
- route `keywords` 命中由 [RegistryBackedNativeRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RegistryBackedNativeRouteBridge.java) 负责
- 当前仍使用字符串包含匹配，不包含 embedding、query rewrite 或 LLM 候选判定

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

### 召回手工测试样本

当前 demo 已补最小冲突样本，可直接用 LLM 对话验证召回行为。

建议至少覆盖：

1. 单 module 单 route 命中
   输入：`打开忘记密码页面`
   预期：命中 `ui://myapp.login/resetPassword`

2. 同 module 多 route 候选
   输入：`打开登录密码页面`
   预期：优先收缩到 `myapp.login`，再因同 module 下多个 route 同时命中而返回 `candidates`

3. 多 module 冲突候选
   输入：`打开修改密码页面`
   预期：`myapp.login/changePassword` 与 `myapp.settings/changePassword` 同时命中，返回 `candidates`
4. 候选确认后直接打开
   输入：
   - `打开修改密码页面`
   - `第一个`
   预期：
   - 第一轮返回 `candidates`
   - 第二轮直接打开 `ui://myapp.login/changePassword`
   - 不应再出现 `open_schema`、`open_uri` 这类不存在或不应使用的 shortcut

### LLM 端到端验证

当前已验证：

- `route_navigator` 可按 `routeArgs + encoded` 调用 `open_resolved_route`
- `encode=url` 路径可正确补编码
- `encode=base64` 路径可正确补编码
- 当故意缺少 `encoded` 时，LLM 能根据错误 message 修正并重试
- 当 `resolve_route` 返回多个 `candidates` 时，LLM 已支持：
  - 用户原话含显式区分词时直接代选
  - 无法唯一判定时围绕候选差异定向追问
- 用户确认候选后，LLM 会继续命中 `route_navigator` 并直接调用 `open_resolved_route`
- 当 `resolve_route` 返回 `not_found` 时，LLM 会：
  - 只调用一次 `resolve_route`
  - 不自动重试
  - 只提示补模块 / 功能 / 别名信息

当前仍未增强的部分：

- route 召回虽然已支持 `keywords`，但仍主要依赖字符串包含匹配
- 尚未引入 `not_found` 后的 query rewrite
- 尚未引入代码层的候选选择强约束 shortcut

## App 层接入点

App 层当前只需要提供或组装以下能力：

- wecode query source
- native route manifest assets
- `HostRouteInvoker`

默认 shortcut 组装入口在 [RouteShortcutProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteShortcutProvider.java)。

说明：

- route tooling 的通用运行时、source model 和 bridge 适配器已经下沉到 `agent-android/src/main/java/com/hh/agent/android/route/`
- App 层主要保留 manifest 资源、demo invoker 和最终装配代码

### WeCode

当前 wecode 侧使用 App mock query source 驱动，但 query source contract 和 bridge 适配器位于 `agent-android`：

- [WeCodeQuerySource.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/WeCodeQuerySource.java)
- [WeCodeQueryResult.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/WeCodeQueryResult.java)
- [QuerySourceBackedWeCodeRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/QuerySourceBackedWeCodeRouteBridge.java)
- [DefaultMockWeCodeQuerySource.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultMockWeCodeQuerySource.java)

当前 query result 最小字段：

- `uri`
- `appName`
- `description` 可选

注意：

- wecode 内部 source 允许保留 `appName`
- 映射到 `RouteTarget` 时统一使用 `title`
- wecode URI 必须使用 `h5://数字编号`

后续接真实查询时，优先替换 [DefaultMockWeCodeQuerySource.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultMockWeCodeQuerySource.java) 或直接新增真实 `WeCodeQuerySource` 实现。

查询失败约定：

- bridge 返回空列表
- bridge 内记日志
- 不向 resolver 抛异常

如果真实查询仍然满足：

- 单 query 输入
- `h5://数字编号` URI
- `appName` + `description` 输出

通常不需要修改 [QuerySourceBackedWeCodeRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/QuerySourceBackedWeCodeRouteBridge.java) 本身。
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
- 如有需要，扩展 [RouteManifestLoader.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/manifest/RouteManifestLoader.java) 的扫描或校验规则

接真实 wecode 查询时，优先改：

- [DefaultMockWeCodeQuerySource.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/DefaultMockWeCodeQuerySource.java)
- 或新增真实 `WeCodeQuerySource` 实现并在 [RouteShortcutProvider.java](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/java/com/hh/agent/app/RouteShortcutProvider.java) 中切换

通常不需要改：

- [RouteResolver.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteResolver.java)
- [RouteHint.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteHint.java)
- [RouteResolution.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteResolution.java)
- [RouteTarget.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RouteTarget.java)
- [RegistryBackedNativeRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/RegistryBackedNativeRouteBridge.java)
- [QuerySourceBackedWeCodeRouteBridge.java](/Users/caixiao/Workspace/projects/mobile-agent/agent-android/src/main/java/com/hh/agent/android/route/QuerySourceBackedWeCodeRouteBridge.java)
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
  - wecode query source 数据源
  - manifest loader / URI composer
  - `DemoHostRouteInvoker`
  - 调试入口和 demo 页面

如果只是把 mock source 替换成真实 source，优先改 `app`，不要直接改 `agent-android` 的 runtime contract。
