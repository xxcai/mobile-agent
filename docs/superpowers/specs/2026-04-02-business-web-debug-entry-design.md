# Business Web Debug Entry 已完成设计说明

## 设计目标

在不放开 `BusinessWebActivity` 导出能力的前提下，为当前 App 增加一个最小的应用内调试入口，使业务首页可以稳定进入带 debug extras 的业务 WebView 页面，同时不影响普通业务入口。

## 已落地设计

### 1. 入口位置

调试入口放在 `BusinessHomeFragment` 内，与现有业务首页内容共存：

- 首屏可见
- 与正式业务入口区分明确
- 默认隐藏，仅在 debug 场景显示

### 2. 行为设计

点击调试入口后，启动同一个 `BusinessWebActivity`，并携带固定 extras：

- `EXTRA_TITLE = "业务调试页"`
- `EXTRA_ENABLE_DEBUG_CONTROLS = true`
- `EXTRA_AUTO_RUN_VIEW_CONTEXT_PROBE = true`
- `EXTRA_PROBE_TARGET_HINT = "debug submit button"`
- `EXTRA_PAGE_TEMPLATE_ASSET = "business_page_form.html"`

同时保持以下边界：

- 不修改 `BusinessWebActivity` 的导出状态
- 不新增独立 debug Activity
- 不改动 `web_dom` 或 `android_web_action_tool` 的运行时实现
- 普通业务入口仍然使用原有 `title + html_content` 打开逻辑

### 3. 代码结构

当前实现已保持最小改动范围：

- `BusinessHomeFragment` 保留普通业务页打开方法
- 新增独立的 `openDebugWebPage()`
- 调试入口与普通入口各走各的分支

### 4. 测试设计

当前设计已经通过 Robolectric 测试固定下来，重点验证：

- debug 入口是否出现
- 启动目标是否为 `BusinessWebActivity`
- 调试 extras 是否齐全
- 普通业务入口是否不带调试 extras
- 非 debug 场景下入口是否隐藏

## 已实现结果

当前仓库已具备以下结果：

- `BusinessHomeFragment` 中存在明确的调试入口
- 该入口仅在 debug 构建中可见
- 点击后打开 `BusinessWebActivity`
- 调试入口不传 `EXTRA_HTML_CONTENT`
- 普通业务入口行为保持不变
- 调试入口相关单测已落地

## 说明

本文档只保留已经实现并能从当前仓库代码中直接验证的设计内容，不再保留未完成的真机链路目标或候选方案比较。
