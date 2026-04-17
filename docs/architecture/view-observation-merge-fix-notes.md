# 合并后 View Observation 修改说明

本文记录 `feature/half-rule-half-model` 合并 `view-observation-unification` 相关代码之后，在当前分支上完成的诊断、修复与验证。目标是说明为什么要改、具体改了什么、运行时如何判断问题是否仍然存在，以及后续还需要继续收口的风险。

## 背景

合并后的代码在 `android_view_context_tool` 返回结果中新增了一组统一页面观测字段：

- `uiTree`
- `screenElements`
- `pageSummary`
- `quality`
- `raw`

这组字段的目标是把 native view tree、screen vision、web dom 等不同来源的页面观测统一成一套结构，方便后续 AgentCore 使用同一种数据模型做页面理解和候选选择。

合并前的主链路主要依赖：

- `nativeViewXml`：native 层级结构、resource-id、bounds、文本。
- `screenVisionCompact`：OCR/视觉控件/页面摘要。
- `hybridObservation`：native 与 screen vision 融合后的可执行候选和页面摘要。

合并后多了一层 canonical/unified observation。如果这层结构直接进入 tool result，会带来两个风险：

1. `raw` 可能包含完整 `nativeViewXml`、完整视觉结果、完整 hybrid 结果，导致 LLM 请求体显著变大。
2. `screenElements` 可能包含大量文本型、非真正可点击节点，导致候选污染，影响 LLM 或后续规则选择点击目标。

因此本次修改先做低风险收口：让统一观测能正常生成，但不要让大 raw 默认进入模型，同时增加日志判断新结构是否会引入候选污染。

## 合并后发现的问题

### 1. canonical observation 构建失败

合并后第一次运行时，日志持续出现：

```text
[ViewContextSnapshotProvider][canonical_observation_failed]
message=http://apache.org/xml/features/disallow-doctype-decl
```

原因在 `NativeXmlObservationAdapter.NativeXmlTreeParser.parseXmlOnce(...)`。

原逻辑直接调用：

```java
factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
```

部分 Android XML parser 不支持这些 feature，会直接抛异常。异常被 `ViewContextSnapshotProvider.tryBuildUnifiedObservation(...)` 捕获后，`UnifiedViewObservation` 返回 `null`，导致：

- `uiTree=null`
- `screenElements=null`
- `pageSummary=null`
- `quality=null`
- `raw=null`

功能没有立即失败，是因为旧字段仍然存在：

- `nativeViewXml`
- `screenVisionCompact`
- `hybridObservation`

但合并新增的统一观测实际上没有生效。

### 2. raw 默认返回存在请求体膨胀风险

合并后的 `ViewContextSnapshotProvider` 会把 `snapshot.rawJson` 写入 tool result：

```java
.withJson("raw", snapshot.rawJson)
```

如果 canonical observation 构建成功，`raw` 会包含完整底层观测内容。即使 `rawFallbackIncluded=false`，也可能被返回给模型，造成请求体膨胀。

从修复后的日志可以看到，内部 snapshot 的 raw 体积并不小：

```text
raw_snapshot_length=38605 ~ 55486
```

如果这部分默认进入请求体，会抵消之前对上下文和 observation 的裁剪收益。

### 3. screenElements 存在文本型候选污染风险

修复 canonical 构建后，日志开始出现：

```text
[ViewContextSnapshotProvider][canonical_screen_elements_text_only_dominant]
```

示例：

```text
MeMainActivity: screen_element_count=18 actionable_count=3 text_only_count=15
HWBoxRecentlyUsedActivity: screen_element_count=18 actionable_count=7 text_only_count=11
```

这说明 `screenElements` 中大量元素是文本型、非真正可点击节点。如果后续 AgentCore 或 LLM 优先消费 `screenElements`，可能把标题、状态文本、普通说明文字当成候选点击目标。

本次修改没有直接改变 `screenElements` 生成策略，只增加诊断日志确认风险，并确保 raw 不进入请求体。

## 修改内容

### 1. XML parser 安全 feature 改为 best-effort

修改文件：

```text
agent-android/src/main/java/com/hh/agent/android/viewcontext/NativeXmlObservationAdapter.java
```

修改前：

```java
factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
factory.setExpandEntityReferences(false);
```

修改后：

```java
setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
factory.setExpandEntityReferences(false);
```

新增 helper：

```java
private static void setFeatureIfSupported(DocumentBuilderFactory factory,
                                          String feature,
                                          boolean enabled) {
    try {
        factory.setFeature(feature, enabled);
    } catch (Exception ignored) {
        // Keep parsing available on Android parsers that reject optional features.
    }
}
```

这样做的原因：

- 保留 XML 安全加固意图。
- Android parser 不支持某个 feature 时，不让整个 canonical observation 构建失败。
- 仍保留 `factory.setExpandEntityReferences(false)` 作为通用保护。

修改后的预期日志：

```text
canonical_observation_failed 不再出现
ui_tree_length > 0
screen_elements_length > 0
```

### 2. raw 只在 raw fallback 开启时返回

修改文件：

```text
agent-android/src/main/java/com/hh/agent/android/viewcontext/ViewContextSnapshotProvider.java
```

修改前：

```java
.withJson("raw", snapshot.rawJson)
```

修改后：

```java
.withJson("raw", includeRawFallback ? snapshot.rawJson : null)
```

这样做的原因：

- `rawFallbackIncluded=false` 时，tool result 不应携带完整 raw。
- 避免 `nativeViewXml`、vision raw、hybrid raw 重复进入 LLM 请求体。
- 保留内部 snapshot 的 raw，方便调试或显式 fallback 使用。

修改后的预期日志：

```text
raw_length=0
raw_returned_without_raw_fallback=false
raw_snapshot_length>0
```

字段含义：

- `raw_length`：实际返回给 tool result 的 raw 长度。
- `raw_snapshot_length`：内部 snapshot 中缓存的 raw 长度。
- `raw_returned_without_raw_fallback`：如果为 `true`，说明 raw 在未请求 fallback 时泄漏到返回结果中。

### 3. 增加 canonical observation 诊断日志

修改文件：

```text
agent-android/src/main/java/com/hh/agent/android/viewcontext/ViewContextSnapshotProvider.java
```

新增日志：

```text
[ViewContextSnapshotProvider][canonical_observation_summary]
```

记录字段：

- `source`
- `activity`
- `detail_mode`
- `ui_tree_length`
- `screen_elements_length`
- `page_summary_length`
- `quality_length`
- `raw_length`
- `raw_snapshot_length`
- `raw_returned_without_raw_fallback`
- `screen_element_count`
- `actionable_count`
- `non_actionable_count`
- `text_only_count`
- `bounds_count`
- `fused_count`
- `native_count`
- `native_xml_count`
- `vision_only_count`
- `web_dom_count`
- `unknown_source_count`

新增告警日志：

```text
[ViewContextSnapshotProvider][canonical_raw_payload_returned_without_fallback]
```

触发条件：

```text
includeRawFallback=false 且 raw_length>0
```

含义：raw 在未显式 fallback 时进入了 tool result，需要立刻修复。

新增告警日志：

```text
[ViewContextSnapshotProvider][canonical_screen_elements_text_only_dominant]
```

触发条件：

```text
screen_element_count>0 且 text_only_count>actionable_count
```

含义：当前 `screenElements` 文本型节点明显多于可执行节点，存在候选污染风险。

### 4. 单测同步 raw 默认不返回的策略

修改文件：

```text
agent-android/src/test/java/com/hh/agent/android/viewcontext/ScreenSnapshotObservationProviderTest.java
```

修改前：

```java
assertTrue(result.getJSONObject("raw").has("visualObservationJson"));
```

修改后：

```java
assertTrue(result.isNull("raw"));
```

测试仍保留内部 snapshot raw 校验：

```java
assertTrue(new JSONObject(latest.rawJson).has("visualObservationJson"));
```

这说明：

- 对外 tool result 默认不返回 raw。
- 内部 snapshot 仍保留 raw，供调试和显式 fallback 使用。

## 运行验证结果

### 编译与测试

已执行：

```powershell
.\gradlew.bat :agent-android:testDebugUnitTest --offline
.\gradlew.bat :agent-android:assembleDebug :agent-core:assembleDebug :agent-screen-vision:assembleDebug --offline
.\gradlew.bat syncDemoSdkAars --offline
```

结果：

- `agent-android` 单测通过。
- 三个 Debug AAR 构建通过。
- AAR 已同步到 `app/libs`。

### 云空间任务验证

用户输入：

```text
帮我看一下云空间页面的内容。
```

关键日志：

```text
route_resolved selected_skill=cloud_space_summary navigation_goal=HWBoxRecentlyUsedActivity
fast_execute_hit action=tap target=云空间 source=fused score=1
goal_reached_context_reset current_page=com.huawei.it.hwbox.ui.bizui.recentlyused.HWBoxRecentlyUsedActivity
request_payload_summary request_profile=readout body_length=8010 tool_count=0
loop_complete duration_ms=9856 iteration_count=1
```

结论：

- 云空间链路正常。
- 本地 fast execute 仍能命中云空间入口。
- readout 请求体约 8KB，没有因 raw 引入膨胀。
- `uiTree/screenElements` 已生成，但未破坏当前云空间任务。

### 打卡任务验证

关键日志：

```text
fast_execute_fallback reason=ambiguous_candidate count=0 target=打卡按钮
request_payload_summary request_profile=navigation_escalation body_length=33046
android_gesture_tool args referencedBounds=[494,150][586,212] targetDescriptor=打卡
PunchStateBackground showFree:finish punch!
observation_fingerprint modal打卡成功 ... labels=打卡,打卡成功,首次打卡,末次打卡
```

结论：

- 打卡任务最后观察到了 `打卡成功` 和时间更新。
- 但本地 fast execute 没有稳定命中“打卡按钮”，回退到了 LLM navigation escalation。
- LLM 返回的点击区域是顶部标题文本附近，不是主内容区的打卡按钮。
- 这条路径结果可能成功，但动作选择不可靠。

当前打卡问题和 raw 修复无直接关系，更接近候选排序和 button/action 类任务的通用点击策略问题。

## 如何判断本次修复是否生效

运行任务后查看 logcat。

### 1. 判断 canonical observation 是否构建成功

不应再出现：

```text
canonical_observation_failed
```

应该出现：

```text
canonical_observation_summary ui_tree_length>0 screen_elements_length>0
```

### 2. 判断 raw 是否被挡住

应该出现：

```text
raw_length=0
raw_returned_without_raw_fallback=false
```

允许出现：

```text
raw_snapshot_length>0
```

这表示 raw 只存在内部 snapshot，没有进入 tool result。

### 3. 判断 screenElements 是否存在候选污染

如果出现：

```text
canonical_screen_elements_text_only_dominant
```

说明当前页面 `screenElements` 中文本型节点过多。此时需要谨慎让 AgentCore 或 LLM 直接消费完整 `screenElements`。

### 4. 判断请求体是否被 raw 拉大

看：

```text
request_payload_summary body_length=...
```

正常 readout 应保持较小，例如云空间这次约：

```text
body_length=8010 tool_count=0
```

如果 readout 或 navigation 请求突然大幅升高，同时 `raw_length>0`，说明 raw 泄漏进入请求体。

## 当前仍需关注的风险

### 1. screenElements 不应直接作为最终点击候选

从日志看，`screenElements` 会包含很多文本型元素。建议后续把它作为观测输入，而不是直接作为最终候选集合。

更稳的消费方式：

- clickable/input/containerClickable 节点可作为候选。
- text-only 节点只作为语义锚点。
- 候选点击点优先来自 native/hybrid 的可点击父容器。
- 标题栏、导航栏、状态文本应降权。

### 2. uiTree/screenElements 需要阶段化裁剪

当前虽然 raw 被挡住，但 `uiTree` 和 `screenElements` 已经开始返回，体积并不小：

```text
ui_tree_length=15560
screen_elements_length=13068
```

后续如果模型请求直接携带这两项，也可能增加请求体。建议继续做阶段化策略：

- `DISCOVERY`：保留必要结构和目标相关节点。
- `FOLLOW_UP`：只保留目标相关节点、可点击区域、页面摘要。
- `READOUT`：默认不需要完整 `uiTree/screenElements`，只保留内容摘要。

### 3. 打卡这类 action/button 任务需要通用候选策略

打卡页面的失败模式不是业务特例，而是通用模式：页面标题和主按钮文本相同，模型容易点标题。

建议后续通用优化：

- 对 `button/action/submit/confirm/punch` 类 step，优先主内容区候选。
- 惩罚顶部标题栏、导航栏、返回按钮附近的同名文本。
- 如果文本候选不可点击，向上找可点击父容器。
- 如果页面已经出现成功信号，例如“打卡成功”“时间更新”，直接进入 readout，不再重复点击。

## 本次修改边界

本次修改只做三件事：

1. 修复 Android XML parser feature 不兼容导致 canonical observation 构建失败。
2. 阻止 raw 在默认路径进入 tool result / LLM 请求体。
3. 增加 canonical observation 诊断日志，暴露 screenElements 候选污染风险。

本次没有做：

- 没有改变 AgentCore 的主导航策略。
- 没有让 AgentCore 优先消费 `screenElements`。
- 没有针对“云空间”或“打卡”写死业务逻辑。
- 没有直接解决打卡按钮候选排序问题。

这样分层处理的原因是：先保证合并后的统一观测结构可用且不伤害请求体，再基于日志逐步决定是否让 AgentCore 消费这套新结构。
