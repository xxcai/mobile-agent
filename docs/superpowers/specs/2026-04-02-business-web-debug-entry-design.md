# Business Web Debug Entry Design

## Goal

在不放开 `BusinessWebActivity` 导出能力的前提下，为当前 App 增加一个最小的应用内调试入口，使真机上可以稳定进入带 debug extras 的业务 WebView 页面，并自动触发 `android_view_context_tool(web_dom)` 探测，随后手动或自动验证 `android_web_action_tool`。

## Scope

本轮覆盖：

- 在现有应用内导航链路中增加一个仅用于调试的可见入口
- 该入口启动 `BusinessWebActivity` 时附带完整调试 extras
- 默认进入 `business_page_form.html`
- 默认开启 debug controls
- 默认自动执行一次 view context probe
- 为入口 intent extras 增加可回归的测试覆盖
- 该入口只在 debug 构建中显示

本轮不覆盖：

- 将 `BusinessWebActivity` 改为导出组件
- 新增独立 debug Activity、deeplink 或 shell 可直达入口
- 重做 `BusinessWebActivity` 调试面板交互
- 修改 `web_dom` / `android_web_action_tool` 的运行时实现

## Context

当前真机调试已经确认以下事实：

- `BusinessWebActivity` 已具备调试能力，支持 `EXTRA_ENABLE_DEBUG_CONTROLS`、`EXTRA_AUTO_RUN_VIEW_CONTEXT_PROBE`、`EXTRA_PROBE_TARGET_HINT`、`EXTRA_PAGE_TEMPLATE_ASSET`
- `BusinessWebActivity` 在 `AndroidManifest.xml` 中仍为 `android:exported="false"`，无法通过 `adb shell am start` 直接拉起
- `BusinessHomeFragment` 现有业务入口只会传递 `title` 和 `html_content`，不会带调试 extras
- `BusinessWebActivity` 现有自动 probe 逻辑会在页面加载后通过 `postDelayed(..., 500L)` 触发一次 view context probe
- 因此当前“能进入业务页”与“能进入可调试业务页”之间还存在最后一段应用内入口缺口

这意味着，当前最小问题不是 WebView 调试能力缺失，而是缺少一个稳定、低风险、可重复进入调试态页面的应用内入口。

## Approaches Considered

### Approach A: 在 `BusinessHomeFragment` 增加调试入口

优点：

- 改动最小
- 不需要放开组件导出面
- 复用现有页面与导航结构
- 真机验证路径最接近真实应用内操作

缺点：

- 需要在现有业务首页上增加一个仅调试用途的 UI 元素
- 不能直接通过外部 adb 命令一键启动

### Approach B: 新增 debug-only 转发 Activity

优点：

- 可以为 adb 启动提供更直接的抓手
- 不需要修改 `BusinessHomeFragment` 现有布局入口语义

缺点：

- manifest 和组件数量增加
- 比当前问题需要的改动更大
- 会引入新的调试专用路由维护成本

### Approach C: 临时把 `BusinessWebActivity` 设为 `exported=true`

优点：

- 实现最快

缺点：

- 直接扩大组件暴露面
- 调试便利性建立在不必要的安全放宽上
- 不符合当前“最小且低风险”的目标

### Decision

采用 Approach A。

## Design

### 1. Debug Entry Placement

调试入口放在 `BusinessHomeFragment` 内，并与现有业务首页内容共存。

入口应满足：

- 用户进入“业务”tab 后即可看到
- 与正常业务入口有清晰区分，避免误解为正式功能
- 不替换、不影响现有业务卡片和快捷入口行为
- 只在 debug 构建中可见，release 构建不显示

本轮优先选择“新增一个最小可见按钮或卡片”的实现方式，而不是重构整个业务首页布局。

### 2. Debug Entry Behavior

点击调试入口后，仍然启动同一个 `BusinessWebActivity`，但显式附带以下 extras：

- `EXTRA_TITLE = "业务调试页"`
- `EXTRA_ENABLE_DEBUG_CONTROLS = true`
- `EXTRA_AUTO_RUN_VIEW_CONTEXT_PROBE = true`
- `EXTRA_PROBE_TARGET_HINT = "debug submit button"`
- `EXTRA_PAGE_TEMPLATE_ASSET = "business_page_form.html"`

页面来源规则明确为：

- 调试入口以 `EXTRA_PAGE_TEMPLATE_ASSET = "business_page_form.html"` 作为唯一页面来源
- 本轮调试入口不传 `EXTRA_HTML_CONTENT`，避免与 asset 模板产生优先级歧义

这样进入页面后将具备以下默认行为：

- 调试面板直接可见
- WebView 加载表单模板页面
- 页面加载后自动执行一次 `android_view_context_tool`
- 调试面板中保留 `Run Web Action` 按钮供后续验证 `android_web_action_tool`

### 3. Code Structure

为控制改动范围，`BusinessHomeFragment` 内应只新增与调试入口直接相关的最小逻辑：

- 一个用于打开普通业务页的现有方法继续保留
- 新增一个专门打开调试页的方法，例如语义上等价于 `openDebugWebPage()`
- 现有业务入口仍调用普通方法，调试入口单独调用调试方法

不在本轮引入额外导航抽象、路由层或通用 intent builder，除非测试证明当前结构难以覆盖。

### 4. Testing Strategy

本轮采用最小 TDD 闭环，重点验证“调试入口 intent 是否带齐关键 extras”。

建议测试边界：

- 从 `BusinessHomeFragment` 点击调试入口
- 捕获启动的 `Intent`
- 断言目标组件为 `BusinessWebActivity`
- 断言调试相关 extras 全部存在且值正确
- 断言调试入口只在 debug 构建下出现
- 断言普通业务入口仍然走原有普通打开逻辑，不附带调试 extras

至少覆盖：

- `EXTRA_ENABLE_DEBUG_CONTROLS = true`
- `EXTRA_AUTO_RUN_VIEW_CONTEXT_PROBE = true`
- `EXTRA_PROBE_TARGET_HINT = "debug submit button"`
- `EXTRA_PAGE_TEMPLATE_ASSET = "business_page_form.html"`

如果当前项目已有 fragment / activity 启动测试模式，则优先复用既有测试栈，不额外引入新的测试框架。

### 5. Verification Strategy

代码完成后需要做两层验证：

1. 本地测试验证入口 intent extras 正确
2. 真机验证完整调试链路

其中“稳定进入”在本轮的最小可执行定义为：

- 同一安装包下，连续 3 次从业务首页点击调试入口
- 每次都能进入 `BusinessWebActivity`
- 每次都能看到 debug panel
- 每次自动 probe 都能产出结果文本；若其中失败，需保留日志并确认是运行时链路问题而不是入口未生效

真机验证顺序：

1. 安装最新 debug APK
2. 打开 App，进入“业务”tab
3. 点击新增调试入口
4. 确认 `BusinessWebActivity` 打开且 debug panel 可见
5. 确认自动 probe 输出 `source=web_dom`、`interactionDomain=web`
6. 手动点击 `Run Web Action`
7. 确认输出 `channel=android_web_action_tool`、`domain=web`、`action=click`

## Risks

### Risk 1: 调试入口 UI 放置位置不明显

如果入口埋得太深，真机验证效率仍会很低。

缓解方式：

- 将入口放在业务首页首屏可见区域
- 文案中明确体现“调试”用途

### Risk 2: 页面自动 probe 时机不稳定

若 WebView 模板加载与现有自动 probe 延迟之间存在时序波动，自动探测可能偶发失败。

缓解方式：

- 保留手动 `Run View Context` 按钮作为二次验证手段
- 真机失败时先保留日志证据，再判断是否需要调整等待策略

### Risk 3: 业务首页测试覆盖难以直接抓取 Intent

如果当前测试栈不方便从 fragment 级别校验 `startActivity`，实现可能需要轻微调整代码边界以便测试。

缓解方式：

- 优先复用现有 AndroidX / Robolectric 能力
- 仅在测试确实受阻时再做最小提炼

## Success Criteria

完成后应满足：

- `BusinessHomeFragment` 中存在一个明确的调试入口
- 该入口仅在 debug 构建中可见
- 点击后打开 `BusinessWebActivity`
- 打开的 intent 带齐约定的调试 extras
- 调试入口不传 `EXTRA_HTML_CONTENT`，页面由 `business_page_form.html` 模板唯一驱动
- 普通业务入口行为保持不变，不附带调试 extras
- 真机可稳定进入 `business_page_form.html` 调试页
- 可在该页面验证 `android_view_context_tool(web_dom)` 与 `android_web_action_tool`
