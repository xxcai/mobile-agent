# Business Web Debug Entry 已完成项

## 背景

当前仓库已经为业务首页补齐一个仅在 debug 构建下可见的调试入口，用于进入带调试 extras 的 `BusinessWebActivity`，验证 WebView 调试面板、`web_dom` 观测链路和普通业务入口不回归。

## 已完成实现

### 1. 业务首页增加 debug-only 入口

已在 `app/src/main/res/layout/fragment_business_home.xml` 中增加：

- `businessDebugEntry`
- 默认 `visibility="gone"`
- 文案为“打开业务调试页”

### 2. `BusinessHomeFragment` 已接入专用调试分支

已在 `app/src/main/java/com/hh/agent/mockbusiness/BusinessHomeFragment.java` 中完成：

- `bindDebugEntry(view)` 绑定
- 非 debug 场景下隐藏调试入口
- debug 场景下显示入口并调用 `openDebugWebPage()`
- 普通业务入口继续走原有 `openWebPage(String title, String htmlContent)` 逻辑

### 3. 调试入口携带的 extras 已固定

调试入口启动 `BusinessWebActivity` 时，已传递：

- `EXTRA_TITLE = "业务调试页"`
- `EXTRA_ENABLE_DEBUG_CONTROLS = true`
- `EXTRA_AUTO_RUN_VIEW_CONTEXT_PROBE = true`
- `EXTRA_PROBE_TARGET_HINT = "debug submit button"`
- `EXTRA_PAGE_TEMPLATE_ASSET = "business_page_form.html"`

同时已确认：

- 调试入口不再传 `EXTRA_HTML_CONTENT`
- 普通业务入口仍然传 `EXTRA_HTML_CONTENT`

## 已完成测试覆盖

`app/src/test/java/com/hh/agent/mockbusiness/BusinessHomeFragmentTest.java` 已覆盖：

- debug 构建下调试入口可见
- 点击调试入口后启动 `BusinessWebActivity`
- 调试入口 extras 完整且正确
- 普通业务入口不带调试 extras
- 非 debuggable 场景下调试入口隐藏

`app/build.gradle` 中也已具备对应单测依赖和 `includeAndroidResources = true` 的 Robolectric 配置。

## 已完成验证

本次已确认通过：

```bash
./gradlew -x syncDemoSdkAars :app:testDebugUnitTest --tests 'com.hh.agent.mockbusiness.BusinessHomeFragmentTest'
./gradlew -x syncDemoSdkAars :app:assembleDebug
```

之所以跳过 `syncDemoSdkAars`，是因为当前仓库直接走根任务会被 `agent-screen-vision/src/main/jniLibs/arm64-v8a/libMNN_Express.so` 缺失阻塞；这不是本调试入口实现本身的问题。

## 当前结论

当前仓库中，业务首页 debug 入口相关代码、测试和本地构建验证已经完成。本文档只保留已落地内容，不再保留未执行的真机手工步骤或实现前的分阶段待办描述。
