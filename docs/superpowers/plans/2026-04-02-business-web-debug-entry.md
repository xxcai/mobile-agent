# Business Web Debug Entry Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为业务首页增加一个仅在 debug 构建中可见的调试入口，使其能稳定打开带调试 extras 的 `BusinessWebActivity` 并支持真机验证 `web_dom` 与 `android_web_action_tool`。

**Architecture:** 保持 `BusinessWebActivity` 现有调试能力不变，只在 `BusinessHomeFragment` 增加一个最小入口和一条专用启动分支。测试重点放在入口可见性与启动 `Intent` 内容，确保普通业务入口行为不回归。

**Tech Stack:** Android app module, Java, AndroidX Fragment, JUnit/Robolectric（如项目现有测试栈支持）

---

## File Map

- Modify: `app/src/main/java/com/hh/agent/mockbusiness/BusinessHomeFragment.java`
  责任：保留普通业务页打开逻辑，新增 debug-only 入口绑定与调试页启动逻辑。
- Modify: `app/src/main/res/layout/fragment_business_home.xml`
  责任：为业务首页增加一个最小可见的调试入口控件。
- Modify: `app/build.gradle`
  责任：补齐实现所需的最小单元测试依赖，仅在缺失时添加。
- Create: `app/src/test/java/com/hh/agent/mockbusiness/BusinessHomeFragmentTest.java`
  责任：覆盖调试入口可见性、调试入口 Intent extras、普通业务入口不携带调试 extras。

## Chunk 1: 调试入口测试与实现

### Task 1: 搭建 `BusinessHomeFragment` 的测试抓手

**Files:**
- Modify: `app/build.gradle`
- Create: `app/src/test/java/com/hh/agent/mockbusiness/BusinessHomeFragmentTest.java`

- [ ] **Step 1: 写出失败测试，验证调试入口在 debug 构建下可见**

```java
@Test
public void debugEntry_isVisibleInDebugBuild() {
    // 启动 fragment，查找调试入口 view，并断言可见
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.hh.agent.mockbusiness.BusinessHomeFragmentTest.debugEntry_isVisibleInDebugBuild`
Expected: FAIL，原因是调试入口控件或测试依赖尚不存在

- [ ] **Step 3: 先确认现有测试栈是否足以承载 fragment 单测；仅在缺失时补最小依赖**

```gradle
testImplementation 'org.robolectric:robolectric:<compatible-version>'
testImplementation 'androidx.test:core:<compatible-version>'
```

说明：优先使用与 `testDebugUnitTest` 兼容的 JVM/Robolectric 路径，不引入只适用于 instrumentation 的测试依赖。

- [ ] **Step 4: 重新运行测试，确认失败原因收敛到“入口未实现”**

Run: `./gradlew :app:testDebugUnitTest --tests com.hh.agent.mockbusiness.BusinessHomeFragmentTest.debugEntry_isVisibleInDebugBuild`
Expected: FAIL，能稳定定位到缺少目标 view 或布局未包含入口

### Task 2: 写出调试入口 Intent 行为测试

**Files:**
- Create: `app/src/test/java/com/hh/agent/mockbusiness/BusinessHomeFragmentTest.java`

- [ ] **Step 1: 写出失败测试，验证点击调试入口后启动 `BusinessWebActivity`**

```java
@Test
public void clickingDebugEntry_opensBusinessWebActivity() {
    // 点击调试入口，捕获 next started activity，断言 component class
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.hh.agent.mockbusiness.BusinessHomeFragmentTest.clickingDebugEntry_opensBusinessWebActivity`
Expected: FAIL，原因是入口点击尚未绑定或没有启动目标 activity

- [ ] **Step 3: 写出失败测试，验证调试入口附带完整 extras**

```java
@Test
public void clickingDebugEntry_includesDebugExtras() {
    // 断言 title、enable debug、auto probe、target hint、template asset
    // 并断言不包含 html_content
}
```

- [ ] **Step 4: 运行测试并确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.hh.agent.mockbusiness.BusinessHomeFragmentTest.clickingDebugEntry_includesDebugExtras`
Expected: FAIL，原因是 extras 尚未按设计传递

- [ ] **Step 5: 写出失败测试，验证普通业务入口仍然不带调试 extras**

```java
@Test
public void clickingRegularBusinessEntry_keepsNormalIntent() {
    // 点击现有业务入口，断言不会带 debug extras，且仍会携带 html_content
}
```

- [ ] **Step 6: 运行测试并确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.hh.agent.mockbusiness.BusinessHomeFragmentTest.clickingRegularBusinessEntry_keepsNormalIntent`
Expected: FAIL，原因是测试尚未绑定到实际普通入口，或当前实现尚不可区分普通/调试路径

### Task 3: 用最小代码实现调试入口

**Files:**
- Modify: `app/src/main/res/layout/fragment_business_home.xml`
- Modify: `app/src/main/java/com/hh/agent/mockbusiness/BusinessHomeFragment.java`

- [ ] **Step 1: 在业务首页布局中加入最小调试入口控件**

```xml
<TextView
    android:id="@+id/businessDebugEntry"
    ...
    android:visibility="gone"
    android:text="打开业务调试页" />
```

- [ ] **Step 2: 在 `BusinessHomeFragment` 中仅为 debug 构建绑定该入口**

```java
View debugEntry = view.findViewById(R.id.businessDebugEntry);
if (BuildConfig.DEBUG) {
    debugEntry.setVisibility(View.VISIBLE);
    debugEntry.setOnClickListener(v -> openDebugWebPage());
} else {
    debugEntry.setVisibility(View.GONE);
}
```

- [ ] **Step 3: 新增最小 `openDebugWebPage()`，只传设计规定的 extras**

```java
private void openDebugWebPage() {
    Intent intent = new Intent(requireContext(), BusinessWebActivity.class);
    intent.putExtra(BusinessWebActivity.EXTRA_TITLE, "业务调试页");
    intent.putExtra(BusinessWebActivity.EXTRA_ENABLE_DEBUG_CONTROLS, true);
    intent.putExtra(BusinessWebActivity.EXTRA_AUTO_RUN_VIEW_CONTEXT_PROBE, true);
    intent.putExtra(BusinessWebActivity.EXTRA_PROBE_TARGET_HINT, "debug submit button");
    intent.putExtra(BusinessWebActivity.EXTRA_PAGE_TEMPLATE_ASSET, "business_page_form.html");
    startActivity(intent);
}
```

- [ ] **Step 4: 保持现有 `openWebPage(String title, String htmlContent)` 行为不变**

Run: 无单独命令；通过下一步测试验证

- [ ] **Step 5: 运行新增测试并确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests com.hh.agent.mockbusiness.BusinessHomeFragmentTest`
Expected: PASS

- [ ] **Step 6: 至少对 release 资源合并或 release 构建做一次可见性回归验证**

Run: `./gradlew :app:assembleRelease`
Expected: PASS，且实现本身保证入口默认 `gone`，不会在 release 中暴露

## Chunk 2: 构建与真机验证

### Task 4: 验证本地构建未被调试入口破坏

**Files:**
- No file changes expected

- [ ] **Step 1: 运行 app 单测全集或最小相关测试集**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS

- [ ] **Step 2: 重新构建 debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: PASS

### Task 5: 真机验证调试入口链路

**Files:**
- No file changes expected

- [ ] **Step 1: 安装最新 APK 到已连接设备**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: Success

- [ ] **Step 2: 手动进入“业务”tab 并点击调试入口**

Expected: 打开 `BusinessWebActivity`，调试面板可见

- [ ] **Step 3: 连续 3 次重复进入并观察自动 probe 结果**

Expected: 每次都能进入页面、产生 probe 结果文本，且输出包含 `source=web_dom` 与 `interactionDomain=web`

- [ ] **Step 4: 在页面中点击 `Run Web Action`**

Expected: 输出包含 `channel=android_web_action_tool`、`domain=web`、`action=click`

- [ ] **Step 5: 如失败则抓取日志并保留证据**

Run: `adb logcat -d -s AgentAndroid icraw AndroidRuntime *:S`
Expected: 记录失败证据，区分入口问题与运行时问题
