# View Observation Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace source-specific LLM-facing observation fields with one canonical `uiTree + screenElements + pageSummary + quality + raw` schema built through a facade over native XML, Web DOM, visual observation, and hybrid observation inputs.

**Architecture:** Add canonical DTOs plus a facade with four source adapters in `agent-android`, then update the snapshot registry and providers so `android_view_context_tool` dual-writes canonical fields and legacy raw fields. Keep gesture and web-action protocols unchanged, and migrate resolver/prompt consumers to read canonical fields first with legacy fallback behind them.

**Tech Stack:** Java, Android tool channels, `org.json`, existing snapshot registry, JUnit/Robolectric-style JVM tests, existing `ToolResult` JSON serialization

---

## File Map

### Create

- `agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedViewObservation.java`
  Canonical observation DTO containing metadata, `uiTree`, `screenElements`, `pageSummary`, `quality`, and `raw`.
- `agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedUiTreeNode.java`
  Canonical tree node DTO.
- `agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedScreenElement.java`
  Canonical flat element DTO.
- `agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedObservationAdapter.java`
  Source adapter interface.
- `agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedViewObservationFacade.java`
  Canonical facade selecting adapters and assembling the final observation.
- `agent-android/src/main/java/com/hh/agent/android/viewcontext/NativeXmlObservationAdapter.java`
  Native XML -> canonical adapter.
- `agent-android/src/main/java/com/hh/agent/android/viewcontext/WebDomObservationAdapter.java`
  Web DOM -> canonical adapter.
- `agent-android/src/main/java/com/hh/agent/android/viewcontext/VisualObservationAdapter.java`
  Screen vision -> canonical adapter.
- `agent-android/src/main/java/com/hh/agent/android/viewcontext/HybridObservationAdapter.java`
  Hybrid observation -> canonical adapter.
- `agent-android/src/test/java/com/hh/agent/android/viewcontext/UnifiedViewObservationFacadeTest.java`
  Canonical facade tests across all four source types.

### Modify

- `agent-android/src/main/java/com/hh/agent/android/viewcontext/ViewObservationSnapshot.java`
  Replace “raw-only” semantics with canonical observation fields plus raw attachments.
- `agent-android/src/main/java/com/hh/agent/android/viewcontext/ViewObservationSnapshotRegistry.java`
  Store canonical observation payloads while keeping existing snapshot identity behavior.
- `agent-android/src/main/java/com/hh/agent/android/viewcontext/ViewContextSnapshotProvider.java`
  Build canonical observations for native/screen paths.
- `agent-android/src/main/java/com/hh/agent/android/viewcontext/RealWebDomSnapshotProvider.java`
  Build canonical observations for web paths.
- `agent-android/src/main/java/com/hh/agent/android/channel/AllViewContextSourceHandler.java`
  Forward canonical fields when wrapping native results.
- `agent-android/src/test/java/com/hh/agent/android/channel/ViewContextToolChannelTest.java`
  Assert canonical fields exist and old raw fields remain.
- `agent-android/src/test/java/com/hh/agent/android/viewcontext/RealWebDomSnapshotProviderTest.java`
  Assert web canonical output shape.
- `agent-android/src/test/java/com/hh/agent/android/viewcontext/ScreenSnapshotObservationProviderTest.java`
  Assert native/screen canonical output shape.
- `app/src/main/java/com/hh/agent/viewcontext/ObservationTargetResolver.java`
  Read canonical `screenElements` before legacy fallback.
- `app/src/test/java/com/hh/agent/viewcontext/ObservationTargetResolverTest.java`
  Assert canonical-first resolution.
- `agent-core/src/main/assets/workspace/TOOLS.md`
  Update model guidance toward canonical fields first.
- `docs/protocols/observation-bound-execution.md`
  Clarify that execution still binds by `snapshotId`, but observation reading is now canonical-first.
- `docs/architecture/hybrid-observation-reference.md`
  Document how hybrid becomes an input source instead of the final LLM-facing schema.

## Task 1: Add canonical observation DTOs and facade contracts

**Files:**
- Create: `agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedViewObservation.java`
- Create: `agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedUiTreeNode.java`
- Create: `agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedScreenElement.java`
- Create: `agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedObservationAdapter.java`
- Create: `agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedViewObservationFacade.java`
- Test: `agent-android/src/test/java/com/hh/agent/android/viewcontext/UnifiedViewObservationFacadeTest.java`

- [ ] **Step 1: Write the failing facade contract tests**

```java
@Test
public void build_returnsCanonicalObservationMetadata() throws Exception {
    UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
            "native_xml",
            "com.hh.agent.ChatActivity",
            "native",
            "发送消息",
            null,
            null,
            "<hierarchy activity=\"com.hh.agent.ChatActivity\"><node index=\"0\" class=\"android.widget.Button\" text=\"发送消息\" bounds=\"[820,1500][1040,1700]\"></node></hierarchy>",
            null,
            null,
            null,
            null
    );

    assertEquals("native_xml", observation.source);
    assertEquals("native", observation.interactionDomain);
    assertEquals("发送消息", observation.targetHint);
    assertNotNull(observation.uiTreeJson);
    assertNotNull(observation.screenElementsJson);
}

@Test
public void build_retainsRawAttachments() throws Exception {
    UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
            "web_dom",
            "com.hh.agent.BusinessWebActivity",
            "web",
            "提交",
            "https://example.test/form",
            "Mock Page",
            null,
            "{\"pageUrl\":\"https://example.test/form\",\"pageTitle\":\"Mock Page\",\"tree\":{\"tag\":\"body\",\"children\":[]}}",
            null,
            null,
            null
    );

    JSONObject raw = new JSONObject(observation.rawJson);
    assertTrue(raw.has("webDom"));
    assertEquals("https://example.test/form", observation.pageUrl);
    assertEquals("Mock Page", observation.pageTitle);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew -x syncDemoSdkAars :agent-android:testDebugUnitTest --tests 'com.hh.agent.android.viewcontext.UnifiedViewObservationFacadeTest'
```

Expected: FAIL with missing canonical DTO and facade symbols.

- [ ] **Step 3: Write the canonical DTOs and facade skeleton**

```java
public final class UnifiedViewObservation {
    public final String source;
    public final String interactionDomain;
    public final String activityClassName;
    public final String targetHint;
    public final String pageUrl;
    public final String pageTitle;
    public final String pageSummary;
    public final String uiTreeJson;
    public final String screenElementsJson;
    public final String qualityJson;
    public final String rawJson;

    public UnifiedViewObservation(String source,
                                  String interactionDomain,
                                  String activityClassName,
                                  String targetHint,
                                  String pageUrl,
                                  String pageTitle,
                                  String pageSummary,
                                  String uiTreeJson,
                                  String screenElementsJson,
                                  String qualityJson,
                                  String rawJson) {
        this.source = source;
        this.interactionDomain = interactionDomain;
        this.activityClassName = activityClassName;
        this.targetHint = targetHint;
        this.pageUrl = pageUrl;
        this.pageTitle = pageTitle;
        this.pageSummary = pageSummary;
        this.uiTreeJson = uiTreeJson;
        this.screenElementsJson = screenElementsJson;
        this.qualityJson = qualityJson;
        this.rawJson = rawJson;
    }
}
```

```java
public interface UnifiedObservationAdapter {
    boolean supports(String source);

    UnifiedViewObservation adapt(String source,
                                 String activityClassName,
                                 String interactionDomain,
                                 String targetHint,
                                 String pageUrl,
                                 String pageTitle,
                                 String nativeViewXml,
                                 String webDom,
                                 String visualObservationJson,
                                 String hybridObservationJson,
                                 String screenSnapshot) throws Exception;
}
```

```java
public final class UnifiedViewObservationFacade {
    private static final List<UnifiedObservationAdapter> ADAPTERS = Arrays.asList(
            new HybridObservationAdapter(),
            new NativeXmlObservationAdapter(),
            new WebDomObservationAdapter(),
            new VisualObservationAdapter()
    );

    private UnifiedViewObservationFacade() {
    }

    public static UnifiedViewObservation build(String source,
                                               String activityClassName,
                                               String interactionDomain,
                                               String targetHint,
                                               String pageUrl,
                                               String pageTitle,
                                               String nativeViewXml,
                                               String webDom,
                                               String visualObservationJson,
                                               String hybridObservationJson,
                                               String screenSnapshot) throws Exception {
        for (UnifiedObservationAdapter adapter : ADAPTERS) {
            if (adapter.supports(source)) {
                return adapter.adapt(source,
                        activityClassName,
                        interactionDomain,
                        targetHint,
                        pageUrl,
                        pageTitle,
                        nativeViewXml,
                        webDom,
                        visualObservationJson,
                        hybridObservationJson,
                        screenSnapshot);
            }
        }
        throw new IllegalArgumentException("Unsupported observation source: " + source);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew -x syncDemoSdkAars :agent-android:testDebugUnitTest --tests 'com.hh.agent.android.viewcontext.UnifiedViewObservationFacadeTest'
```

Expected: PASS with the canonical DTO/facade contract test green once minimal adapters exist.

- [ ] **Step 5: Commit**

```bash
git add agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedViewObservation.java \
        agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedUiTreeNode.java \
        agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedScreenElement.java \
        agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedObservationAdapter.java \
        agent-android/src/main/java/com/hh/agent/android/viewcontext/UnifiedViewObservationFacade.java \
        agent-android/src/test/java/com/hh/agent/android/viewcontext/UnifiedViewObservationFacadeTest.java
git commit -m "feat: add canonical unified view observation facade"
```

## Task 2: Implement the native XML adapter against orb-eye style fields

**Files:**
- Create: `agent-android/src/main/java/com/hh/agent/android/viewcontext/NativeXmlObservationAdapter.java`
- Test: `agent-android/src/test/java/com/hh/agent/android/viewcontext/UnifiedViewObservationFacadeTest.java`

- [ ] **Step 1: Write the failing native adapter test**

```java
@Test
public void build_nativeXml_emitsUiTreeAndScreenElements() throws Exception {
    UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
            "native_xml",
            "com.hh.agent.ChatActivity",
            "native",
            "发送消息",
            null,
            null,
            "<hierarchy activity=\"com.hh.agent.ChatActivity\"><node index=\"0\" class=\"android.widget.FrameLayout\" bounds=\"[0,0][1080,1920]\"><node index=\"2\" class=\"android.widget.Button\" text=\"发送消息\" bounds=\"[820,1500][1040,1700]\" clickable=\"true\"></node></node></hierarchy>",
            null,
            null,
            null,
            null
    );

    JSONObject uiTree = new JSONObject(observation.uiTreeJson);
    JSONArray elements = new JSONArray(observation.screenElementsJson);

    assertEquals("android.widget.FrameLayout", uiTree.getString("className"));
    assertEquals("native_xml", uiTree.getString("source"));
    assertEquals("发送消息", elements.getJSONObject(0).getString("text"));
    assertEquals("[820,1500][1040,1700]", elements.getJSONObject(0).getString("bounds"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew -x syncDemoSdkAars :agent-android:testDebugUnitTest --tests 'com.hh.agent.android.viewcontext.UnifiedViewObservationFacadeTest'
```

Expected: FAIL because native XML is not yet parsed into canonical `uiTree` and `screenElements`.

- [ ] **Step 3: Implement the native adapter**

```java
public final class NativeXmlObservationAdapter implements UnifiedObservationAdapter {
    @Override
    public boolean supports(String source) {
        return "native_xml".equals(source) || "screen_snapshot".equals(source);
    }

    @Override
    public UnifiedViewObservation adapt(String source,
                                        String activityClassName,
                                        String interactionDomain,
                                        String targetHint,
                                        String pageUrl,
                                        String pageTitle,
                                        String nativeViewXml,
                                        String webDom,
                                        String visualObservationJson,
                                        String hybridObservationJson,
                                        String screenSnapshot) throws Exception {
        JSONObject uiTree = NativeXmlTreeParser.parseRoot(nativeViewXml, source);
        JSONArray screenElements = NativeXmlTreeParser.collectElements(nativeViewXml, source);
        JSONObject quality = new JSONObject()
                .put("adapterName", "NativeXmlObservationAdapter")
                .put("mode", "native_only")
                .put("nativeNodeCount", screenElements.length());
        JSONObject raw = new JSONObject()
                .put("nativeViewXml", nativeViewXml)
                .put("webDom", JSONObject.NULL)
                .put("visualObservationJson", JSONObject.NULL)
                .put("hybridObservationJson", JSONObject.NULL)
                .put("screenSnapshot", screenSnapshot);
        return new UnifiedViewObservation(
                source,
                interactionDomain,
                activityClassName,
                targetHint,
                pageUrl,
                pageTitle,
                buildNativeSummary(screenElements, activityClassName),
                uiTree.toString(),
                screenElements.toString(),
                quality.toString(),
                raw.toString()
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew -x syncDemoSdkAars :agent-android:testDebugUnitTest --tests 'com.hh.agent.android.viewcontext.UnifiedViewObservationFacadeTest'
```

Expected: PASS with canonical native tree and element extraction.

- [ ] **Step 5: Commit**

```bash
git add agent-android/src/main/java/com/hh/agent/android/viewcontext/NativeXmlObservationAdapter.java \
        agent-android/src/test/java/com/hh/agent/android/viewcontext/UnifiedViewObservationFacadeTest.java
git commit -m "feat: map native xml into canonical observation schema"
```

## Task 3: Implement the Web DOM adapter

**Files:**
- Create: `agent-android/src/main/java/com/hh/agent/android/viewcontext/WebDomObservationAdapter.java`
- Modify: `agent-android/src/test/java/com/hh/agent/android/viewcontext/RealWebDomSnapshotProviderTest.java`
- Test: `agent-android/src/test/java/com/hh/agent/android/viewcontext/UnifiedViewObservationFacadeTest.java`

- [ ] **Step 1: Write the failing Web DOM adapter test**

```java
@Test
public void build_webDom_emitsCanonicalTagTreeAndElements() throws Exception {
    UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
            "web_dom",
            "com.hh.agent.BusinessWebActivity",
            "web",
            "提交",
            "https://example.test/form",
            "Mock Page",
            null,
            "{\"pageUrl\":\"https://example.test/form\",\"pageTitle\":\"Mock Page\",\"nodeCount\":2,\"maxDepthReached\":1,\"truncated\":false,\"tree\":{\"ref\":\"node-0\",\"tag\":\"body\",\"text\":\"\",\"bounds\":{\"x\":0,\"y\":0,\"width\":1080,\"height\":1920},\"children\":[{\"ref\":\"node-1\",\"tag\":\"button\",\"selector\":\"button#submit\",\"text\":\"提交\",\"ariaLabel\":\"提交按钮\",\"clickable\":true,\"inputable\":false,\"bounds\":{\"x\":12,\"y\":34,\"width\":120,\"height\":44},\"children\":[]}]}}",
            null,
            null,
            null
    );

    JSONObject uiTree = new JSONObject(observation.uiTreeJson);
    JSONArray elements = new JSONArray(observation.screenElementsJson);

    assertEquals("body", uiTree.getString("tagName"));
    assertEquals("web_dom", uiTree.getString("source"));
    assertEquals("node-1", elements.getJSONObject(0).getString("ref"));
    assertEquals("button#submit", elements.getJSONObject(0).getString("selector"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew -x syncDemoSdkAars :agent-android:testDebugUnitTest --tests 'com.hh.agent.android.viewcontext.UnifiedViewObservationFacadeTest' --tests 'com.hh.agent.android.viewcontext.RealWebDomSnapshotProviderTest'
```

Expected: FAIL because Web DOM is not yet normalized into canonical tree and element fields.

- [ ] **Step 3: Implement the Web DOM adapter**

```java
public final class WebDomObservationAdapter implements UnifiedObservationAdapter {
    @Override
    public boolean supports(String source) {
        return "web_dom".equals(source);
    }

    @Override
    public UnifiedViewObservation adapt(String source,
                                        String activityClassName,
                                        String interactionDomain,
                                        String targetHint,
                                        String pageUrl,
                                        String pageTitle,
                                        String nativeViewXml,
                                        String webDom,
                                        String visualObservationJson,
                                        String hybridObservationJson,
                                        String screenSnapshot) throws Exception {
        JSONObject payload = new JSONObject(webDom);
        JSONObject uiTree = WebDomCanonicalMapper.mapTree(payload.optJSONObject("tree"), source);
        JSONArray screenElements = WebDomCanonicalMapper.collectElements(payload.optJSONObject("tree"), source);
        JSONObject quality = new JSONObject()
                .put("adapterName", "WebDomObservationAdapter")
                .put("mode", "web_only")
                .put("webNodeCount", payload.optInt("nodeCount", screenElements.length()))
                .put("webTruncated", payload.optBoolean("truncated", false))
                .put("webMaxDepthReached", payload.optInt("maxDepthReached", 0));
        JSONObject raw = new JSONObject()
                .put("nativeViewXml", JSONObject.NULL)
                .put("webDom", payload)
                .put("visualObservationJson", JSONObject.NULL)
                .put("hybridObservationJson", JSONObject.NULL)
                .put("screenSnapshot", screenSnapshot);
        return new UnifiedViewObservation(
                source,
                interactionDomain,
                activityClassName,
                targetHint,
                firstNonEmpty(pageUrl, payload.optString("pageUrl", null)),
                firstNonEmpty(pageTitle, payload.optString("pageTitle", null)),
                buildWebSummary(firstNonEmpty(pageTitle, payload.optString("pageTitle", null)), screenElements.length()),
                uiTree.toString(),
                screenElements.toString(),
                quality.toString(),
                raw.toString()
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew -x syncDemoSdkAars :agent-android:testDebugUnitTest --tests 'com.hh.agent.android.viewcontext.UnifiedViewObservationFacadeTest' --tests 'com.hh.agent.android.viewcontext.RealWebDomSnapshotProviderTest'
```

Expected: PASS with canonical Web DOM tree and screen element extraction.

- [ ] **Step 5: Commit**

```bash
git add agent-android/src/main/java/com/hh/agent/android/viewcontext/WebDomObservationAdapter.java \
        agent-android/src/test/java/com/hh/agent/android/viewcontext/UnifiedViewObservationFacadeTest.java \
        agent-android/src/test/java/com/hh/agent/android/viewcontext/RealWebDomSnapshotProviderTest.java
git commit -m "feat: map web dom into canonical observation schema"
```

## Task 4: Implement the visual and hybrid adapters

**Files:**
- Create: `agent-android/src/main/java/com/hh/agent/android/viewcontext/VisualObservationAdapter.java`
- Create: `agent-android/src/main/java/com/hh/agent/android/viewcontext/HybridObservationAdapter.java`
- Test: `agent-android/src/test/java/com/hh/agent/android/viewcontext/UnifiedViewObservationFacadeTest.java`

- [ ] **Step 1: Write the failing visual and hybrid tests**

```java
@Test
public void build_visualObservation_emitsSimplifiedVisualTree() throws Exception {
    UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
            "screen_snapshot",
            "com.hh.agent.ChatActivity",
            "native",
            "发送",
            null,
            null,
            null,
            null,
            "{\"summary\":\"聊天输入页\",\"sections\":[{\"id\":\"section-compose\",\"type\":\"compose_bar\",\"summaryText\":\"输入区\",\"bbox\":[0,1400,1080,1800]}],\"items\":[{\"id\":\"item-send\",\"summaryText\":\"发送按钮\",\"bbox\":[800,1480,1050,1710]}],\"texts\":[{\"id\":\"text-send\",\"text\":\"发送\",\"bbox\":[820,1500,1040,1700]}],\"controls\":[{\"id\":\"control-send\",\"type\":\"button\",\"label\":\"发送\",\"bbox\":[820,1500,1040,1700],\"score\":0.97}]}",
            null,
            null
    );

    JSONObject uiTree = new JSONObject(observation.uiTreeJson);
    JSONArray elements = new JSONArray(observation.screenElementsJson);

    assertEquals("visual_root", uiTree.getString("className"));
    assertTrue(elements.length() > 0);
    assertEquals("button", elements.getJSONObject(0).getString("role"));
}

@Test
public void build_hybridObservation_prefersActionableNodesAsCanonicalElements() throws Exception {
    UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
            "native_xml",
            "com.hh.agent.ChatActivity",
            "native",
            "发送",
            null,
            null,
            "<hierarchy activity=\"com.hh.agent.ChatActivity\"><node index=\"2\" class=\"android.widget.Button\" text=\"发送\" bounds=\"[820,1500][1040,1700]\"></node></hierarchy>",
            null,
            "{\"summary\":\"聊天输入页\"}",
            "{\"summary\":\"聊天输入页\",\"availableSignals\":{\"nativeXml\":true,\"screenVisionCompact\":true},\"quality\":{\"fusedMatchCount\":1},\"actionableNodes\":[{\"source\":\"fused\",\"nativeNodeIndex\":2,\"text\":\"发送\",\"bounds\":\"[820,1500][1040,1700]\",\"score\":0.95}],\"conflicts\":[]}",
            null
    );

    JSONArray elements = new JSONArray(observation.screenElementsJson);
    JSONObject quality = new JSONObject(observation.qualityJson);

    assertEquals("发送", elements.getJSONObject(0).getString("text"));
    assertEquals("fused", elements.getJSONObject(0).getString("source"));
    assertEquals(1, quality.getInt("fusedMatchCount"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew -x syncDemoSdkAars :agent-android:testDebugUnitTest --tests 'com.hh.agent.android.viewcontext.UnifiedViewObservationFacadeTest'
```

Expected: FAIL because visual and hybrid adapters are not yet implemented.

- [ ] **Step 3: Implement the visual and hybrid adapters**

```java
public final class VisualObservationAdapter implements UnifiedObservationAdapter {
    @Override
    public boolean supports(String source) {
        return "screen_snapshot_visual_only".equals(source);
    }

    @Override
    public UnifiedViewObservation adapt(String source,
                                        String activityClassName,
                                        String interactionDomain,
                                        String targetHint,
                                        String pageUrl,
                                        String pageTitle,
                                        String nativeViewXml,
                                        String webDom,
                                        String visualObservationJson,
                                        String hybridObservationJson,
                                        String screenSnapshot) throws Exception {
        JSONObject visual = new JSONObject(visualObservationJson);
        JSONObject uiTree = VisualCanonicalMapper.mapTree(visual, source);
        JSONArray screenElements = VisualCanonicalMapper.collectElements(visual, source);
        JSONObject quality = new JSONObject()
                .put("adapterName", "VisualObservationAdapter")
                .put("mode", "screen_only")
                .put("visionTextCount", visual.optJSONArray("texts") != null ? visual.optJSONArray("texts").length() : 0)
                .put("visionControlCount", visual.optJSONArray("controls") != null ? visual.optJSONArray("controls").length() : 0);
        JSONObject raw = new JSONObject()
                .put("nativeViewXml", JSONObject.NULL)
                .put("webDom", JSONObject.NULL)
                .put("visualObservationJson", visual)
                .put("hybridObservationJson", JSONObject.NULL)
                .put("screenSnapshot", screenSnapshot);
        return new UnifiedViewObservation(source, interactionDomain, activityClassName, targetHint, pageUrl, pageTitle, visual.optString("summary", null), uiTree.toString(), screenElements.toString(), quality.toString(), raw.toString());
    }
}
```

```java
public final class HybridObservationAdapter implements UnifiedObservationAdapter {
    @Override
    public boolean supports(String source) {
        return "hybrid".equals(source) || "native_xml".equals(source);
    }

    @Override
    public UnifiedViewObservation adapt(String source,
                                        String activityClassName,
                                        String interactionDomain,
                                        String targetHint,
                                        String pageUrl,
                                        String pageTitle,
                                        String nativeViewXml,
                                        String webDom,
                                        String visualObservationJson,
                                        String hybridObservationJson,
                                        String screenSnapshot) throws Exception {
        if (hybridObservationJson == null || hybridObservationJson.trim().isEmpty()) {
            return null;
        }
        JSONObject hybrid = new JSONObject(hybridObservationJson);
        JSONObject uiTree = NativeXmlTreeParser.parseRoot(nativeViewXml, "hybrid");
        JSONArray screenElements = HybridCanonicalMapper.mapActionableNodes(hybrid.optJSONArray("actionableNodes"));
        JSONObject quality = new JSONObject()
                .put("adapterName", "HybridObservationAdapter")
                .put("mode", hybrid.optString("mode", "hybrid_native_screen"))
                .put("availableSignals", hybrid.optJSONObject("availableSignals"))
                .put("fusedMatchCount", hybrid.optJSONObject("quality") != null ? hybrid.optJSONObject("quality").optInt("fusedMatchCount", 0) : 0);
        JSONObject raw = new JSONObject()
                .put("nativeViewXml", nativeViewXml)
                .put("webDom", JSONObject.NULL)
                .put("visualObservationJson", visualObservationJson != null ? new JSONObject(visualObservationJson) : JSONObject.NULL)
                .put("hybridObservationJson", hybrid)
                .put("screenSnapshot", screenSnapshot);
        return new UnifiedViewObservation(source, interactionDomain, activityClassName, targetHint, pageUrl, pageTitle, hybrid.optString("summary", null), uiTree.toString(), screenElements.toString(), quality.toString(), raw.toString());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew -x syncDemoSdkAars :agent-android:testDebugUnitTest --tests 'com.hh.agent.android.viewcontext.UnifiedViewObservationFacadeTest'
```

Expected: PASS with all four source types producing canonical observations.

- [ ] **Step 5: Commit**

```bash
git add agent-android/src/main/java/com/hh/agent/android/viewcontext/VisualObservationAdapter.java \
        agent-android/src/main/java/com/hh/agent/android/viewcontext/HybridObservationAdapter.java \
        agent-android/src/test/java/com/hh/agent/android/viewcontext/UnifiedViewObservationFacadeTest.java
git commit -m "feat: add visual and hybrid canonical adapters"
```

## Task 5: Integrate canonical observations into snapshot storage and tool output

**Files:**
- Modify: `agent-android/src/main/java/com/hh/agent/android/viewcontext/ViewObservationSnapshot.java`
- Modify: `agent-android/src/main/java/com/hh/agent/android/viewcontext/ViewObservationSnapshotRegistry.java`
- Modify: `agent-android/src/main/java/com/hh/agent/android/viewcontext/ViewContextSnapshotProvider.java`
- Modify: `agent-android/src/main/java/com/hh/agent/android/viewcontext/RealWebDomSnapshotProvider.java`
- Modify: `agent-android/src/main/java/com/hh/agent/android/channel/AllViewContextSourceHandler.java`
- Test: `agent-android/src/test/java/com/hh/agent/android/channel/ViewContextToolChannelTest.java`
- Test: `agent-android/src/test/java/com/hh/agent/android/viewcontext/RealWebDomSnapshotProviderTest.java`
- Test: `agent-android/src/test/java/com/hh/agent/android/viewcontext/ScreenSnapshotObservationProviderTest.java`

- [ ] **Step 1: Write the failing integration tests**

```java
@Test
public void executeUsesRuntimeResolvedSource_andReturnsCanonicalFields() throws Exception {
    ViewContextToolChannel channel = channelWithMockWebDom(fakeResolver("web_dom"));
    JSONObject result = new JSONObject(channel.execute(new JSONObject().put("targetHint", "contact")).toJsonString());

    assertTrue(result.has("uiTree"));
    assertTrue(result.has("screenElements"));
    assertTrue(result.has("pageSummary"));
    assertTrue(result.has("quality"));
    assertTrue(result.has("raw"));
    assertTrue(result.has("webDom"));
}
```

```java
@Test
public void getCurrentWebDomSnapshot_populatesCanonicalSnapshotFields() throws Exception {
    FakeBridge bridge = new FakeBridge();
    bridge.rawEvalResult = "{\"pageUrl\":\"https://example.test/form\",\"pageTitle\":\"Form Local Page\",\"nodeCount\":2,\"maxDepthReached\":1,\"truncated\":false,\"tree\":{\"ref\":\"node-0\",\"tag\":\"body\",\"children\":[]}}";

    RealWebDomSnapshotProvider provider = new RealWebDomSnapshotProvider(bridge);
    JSONObject json = new JSONObject(provider.getCurrentWebDomSnapshot("debug submit button").toJsonString());

    assertTrue(json.has("uiTree"));
    assertTrue(json.has("screenElements"));
    assertTrue(json.has("quality"));
    assertTrue(json.has("raw"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew -x syncDemoSdkAars :agent-android:testDebugUnitTest --tests 'com.hh.agent.android.channel.ViewContextToolChannelTest' --tests 'com.hh.agent.android.viewcontext.RealWebDomSnapshotProviderTest' --tests 'com.hh.agent.android.viewcontext.ScreenSnapshotObservationProviderTest'
```

Expected: FAIL because providers and snapshot registry do not yet emit canonical fields.

- [ ] **Step 3: Implement snapshot and tool integration**

```java
ViewObservationSnapshot snapshot = ViewObservationSnapshotRegistry.createSnapshot(
        activityClassName,
        source,
        interactionDomain,
        targetHint,
        unifiedObservation,
        nativeViewXml,
        webDom,
        pageUrl,
        pageTitle,
        visualObservationJson,
        screenSnapshot,
        hybridObservationJson
);

return ToolResult.success()
        .with("snapshotId", snapshot.snapshotId)
        .with("source", snapshot.source)
        .with("interactionDomain", snapshot.interactionDomain)
        .with("activityClassName", snapshot.activityClassName)
        .with("targetHint", snapshot.targetHint)
        .with("pageUrl", snapshot.pageUrl)
        .with("pageTitle", snapshot.pageTitle)
        .withJson("uiTree", snapshot.uiTreeJson)
        .withJson("screenElements", snapshot.screenElementsJson)
        .with("pageSummary", snapshot.pageSummary)
        .withJson("quality", snapshot.qualityJson)
        .withJson("raw", snapshot.rawJson)
        .with("nativeViewXml", snapshot.nativeViewXml)
        .with("webDom", snapshot.webDom)
        .withJson("screenVisionCompact", compactObservationJson)
        .withJson("hybridObservation", hybridObservationJson);
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew -x syncDemoSdkAars :agent-android:testDebugUnitTest --tests 'com.hh.agent.android.channel.ViewContextToolChannelTest' --tests 'com.hh.agent.android.viewcontext.RealWebDomSnapshotProviderTest' --tests 'com.hh.agent.android.viewcontext.ScreenSnapshotObservationProviderTest'
```

Expected: PASS with canonical and legacy fields both present.

- [ ] **Step 5: Commit**

```bash
git add agent-android/src/main/java/com/hh/agent/android/viewcontext/ViewObservationSnapshot.java \
        agent-android/src/main/java/com/hh/agent/android/viewcontext/ViewObservationSnapshotRegistry.java \
        agent-android/src/main/java/com/hh/agent/android/viewcontext/ViewContextSnapshotProvider.java \
        agent-android/src/main/java/com/hh/agent/android/viewcontext/RealWebDomSnapshotProvider.java \
        agent-android/src/main/java/com/hh/agent/android/channel/AllViewContextSourceHandler.java \
        agent-android/src/test/java/com/hh/agent/android/channel/ViewContextToolChannelTest.java \
        agent-android/src/test/java/com/hh/agent/android/viewcontext/RealWebDomSnapshotProviderTest.java \
        agent-android/src/test/java/com/hh/agent/android/viewcontext/ScreenSnapshotObservationProviderTest.java
git commit -m "feat: expose canonical observation schema from providers"
```

## Task 6: Migrate resolver and prompt consumers to canonical-first reading

**Files:**
- Modify: `app/src/main/java/com/hh/agent/viewcontext/ObservationTargetResolver.java`
- Modify: `app/src/test/java/com/hh/agent/viewcontext/ObservationTargetResolverTest.java`
- Modify: `agent-core/src/main/assets/workspace/TOOLS.md`
- Modify: `docs/protocols/observation-bound-execution.md`
- Modify: `docs/architecture/hybrid-observation-reference.md`

- [ ] **Step 1: Write the failing resolver test**

```java
@Test
public void resolve_prefersCanonicalScreenElementsBeforeLegacyFallback() throws Exception {
    JSONObject viewContext = new JSONObject()
            .put("screenElements", new JSONArray()
                    .put(new JSONObject()
                            .put("text", "发送")
                            .put("source", "fused")
                            .put("bounds", "[820,1500][1040,1700]")
                            .put("score", 0.95)
                            .put("nativeNodeIndex", 2)))
            .put("hybridObservation", new JSONObject()
                    .put("actionableNodes", new JSONArray()
                            .put(new JSONObject()
                                    .put("text", "旧发送")
                                    .put("bounds", "[1,1][2,2]")
                                    .put("score", 0.10)));

    ObservationTargetResolver.TargetReference target =
            ObservationTargetResolver.resolve(viewContext, "发送");

    assertEquals("[820,1500][1040,1700]", target.bounds);
    assertEquals("fused", target.source);
    assertEquals(Integer.valueOf(2), target.nodeIndex);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew -x syncDemoSdkAars :app:testDebugUnitTest --tests 'com.hh.agent.viewcontext.ObservationTargetResolverTest'
```

Expected: FAIL because resolver still reads `hybridObservation` first.

- [ ] **Step 3: Implement canonical-first resolution and update docs**

```java
@Nullable
public static TargetReference resolve(@Nullable JSONObject viewContextJson,
                                      @Nullable String targetHint) {
    if (viewContextJson == null) {
        return null;
    }

    TargetReference canonicalMatch = resolveFromScreenElements(
            viewContextJson.optJSONArray("screenElements"),
            targetHint
    );
    if (canonicalMatch != null) {
        return canonicalMatch;
    }

    TargetReference hybridMatch = resolveFromHybrid(
            viewContextJson.optJSONObject("hybridObservation"),
            targetHint
    );
    if (hybridMatch != null) {
        return hybridMatch;
    }

    return resolveFromNativeXml(viewContextJson.optString("nativeViewXml", ""), targetHint);
}
```

```md
After calling `android_view_context_tool`, interpret the result in this order:

1. Use `pageSummary` for page-level understanding.
2. Use `screenElements` for target selection and `referencedBounds`.
3. Use `uiTree` when screen structure matters.
4. Fall back to `hybridObservation`, `nativeViewXml`, or `webDom` only when canonical fields are insufficient.
```

- [ ] **Step 4: Run tests to verify it passes**

Run:

```bash
./gradlew -x syncDemoSdkAars :app:testDebugUnitTest --tests 'com.hh.agent.viewcontext.ObservationTargetResolverTest'
```

Expected: PASS with canonical-first resolution and legacy fallback still green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hh/agent/viewcontext/ObservationTargetResolver.java \
        app/src/test/java/com/hh/agent/viewcontext/ObservationTargetResolverTest.java \
        agent-core/src/main/assets/workspace/TOOLS.md \
        docs/protocols/observation-bound-execution.md \
        docs/architecture/hybrid-observation-reference.md
git commit -m "refactor: prefer canonical observation fields in consumers"
```

## Task 7: Run focused regression coverage

**Files:**
- No code changes

- [ ] **Step 1: Run canonical observation tests**

Run:

```bash
./gradlew -x syncDemoSdkAars :agent-android:testDebugUnitTest --tests 'com.hh.agent.android.viewcontext.UnifiedViewObservationFacadeTest' --tests 'com.hh.agent.android.viewcontext.RealWebDomSnapshotProviderTest' --tests 'com.hh.agent.android.viewcontext.ScreenSnapshotObservationProviderTest' --tests 'com.hh.agent.android.channel.ViewContextToolChannelTest'
```

Expected: PASS with all canonical facade/provider/channel tests green.

- [ ] **Step 2: Run app-side canonical consumer tests**

Run:

```bash
./gradlew -x syncDemoSdkAars :app:testDebugUnitTest --tests 'com.hh.agent.viewcontext.ObservationTargetResolverTest'
```

Expected: PASS with canonical-first and legacy fallback paths green.

- [ ] **Step 3: Run packaging sanity build**

Run:

```bash
./gradlew -x syncDemoSdkAars :app:assembleDebug
```

Expected: BUILD SUCCESSFUL and no compile errors from canonical observation types.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "test: verify canonical observation unification rollout"
```

## Self-Review Notes

- **Spec coverage:** The plan covers canonical schema creation, four adapters, provider/registry integration, canonical-first consumer migration, docs, and focused regression.
- **Placeholder scan:** No `TODO`, `TBD`, or “implement later” markers remain; every task includes exact files, concrete code, and explicit commands.
- **Type consistency:** The same canonical property names are used throughout: `uiTree`, `screenElements`, `pageSummary`, `quality`, and `raw`.
