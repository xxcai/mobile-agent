# 当前未提交修改说明

本文档记录当前工作区未提交修改的内容、修改原因、关键代码 diff 片段，以及是否建议入库。
记录时间：2026-04-23。

当前未提交内容分为四类：

- 核心代码改动
- Skill 描述改动
- AAR 构建产物
- 未跟踪文档与临时文件

其中真正会影响运行逻辑的是核心代码改动、Skill 描述改动和重新构建后的 AAR。

## 1. Shortcut 协议硬约束

### `DescribeShortcutChannel.java`

路径：

```text
agent-android/src/main/java/com/hh/agent/android/channel/DescribeShortcutChannel.java
```

修改原因：

最近一次“发消息”任务失败时，模型虽然已经读取了 `im_sender/SKILL.md` 和 `contact_resolver/SKILL.md`，但仍然幻化了未注册 shortcut：

- `resolve_contact`
- `search_contact`

因此需要把 `describe_shortcut.shortcut` 从纯文本提示约束升级为 schema 级 enum 约束，让模型只能选择已注册 shortcut。

关键修改：

```diff
+import java.util.ArrayList;
+import java.util.List;

- "查询某个已注册 shortcut 的详细定义（参数结构、示例和约束）。")
+ "按需查询某个已注册 shortcut 的详细定义。"
+         + "shortcut 字段必须是工具 schema enum 中的精确值；"
+         + "不要发明、翻译、改写单复数、缩写或根据自然语言推断 shortcut 名称。")

- .description("要查询定义的 shortcut 名称。"), true)
+ .description("要查询定义的 shortcut 名称。只能使用 enum 中的精确值。")
+ .enumValues(getShortcutNames()), true)
```

不支持的 shortcut 返回结果也增加了更明确的能力边界和可选列表：

```diff
- .with("shortcut", shortcutName);
+ .with("shortcut", shortcutName)
+ .with("requestedShortcut", shortcutName)
+ .with("failureType", "capability_boundary")
+ .with("suggestedNextAction", "choose_registered_shortcut_from_skill")
+ .withJson("validShortcuts", buildValidShortcutsJson().toString());
```

新增枚举来源：

```java
private String[] getShortcutNames() {
    List<String> names = new ArrayList<>();
    for (ShortcutDefinition definition : shortcutRuntime.listDefinitions()) {
        if (definition != null && definition.getName() != null && !definition.getName().trim().isEmpty()) {
            names.add(definition.getName());
        }
    }
    return names.toArray(new String[0]);
}
```

影响范围：

- 限制 `describe_shortcut` 只能查询已注册 shortcut。
- 避免模型将 skill 名、自然语言动作名、近义词当作 shortcut 查询。
- 不改变宿主 API。

### `ShortcutRuntimeChannel.java`

路径：

```text
agent-android/src/main/java/com/hh/agent/android/channel/ShortcutRuntimeChannel.java
```

修改原因：

`run_shortcut` 是最终执行入口。仅靠提示词无法阻止模型调用未注册 shortcut，因此这里也恢复 schema 级 enum allowlist。
同时，日志中出现过 `search_contacts` 参数被传成 `name` 的情况，而正确字段应为 `query`。因此参数描述也加强为“字段名必须精确匹配”。

关键修改：

```diff
- "运行已注册的 shortcut 原子动作。"
-         + "协议固定为 {\"shortcut\":\"名称\",\"args\":{...}}。")
+ "运行已注册的 shortcut 原子动作。"
+         + "shortcut 字段必须是工具 schema enum 中的精确值；"
+         + "不要发明、翻译、改写单复数、缩写或根据自然语言推断 shortcut 名称。"
+         + "协议固定为 {\"shortcut\":\"名称\",\"args\":{...}}。")

- .description(buildShortcutChoicesDescription()), true)
+ .description(buildShortcutChoicesDescription())
+ .enumValues(getShortcutNames()), true)
```

参数描述修改：

```diff
- return "传给 shortcut 的 JSON 参数对象，字段结构由目标 shortcut 定义决定。";
+ return "传给 shortcut 的 JSON 参数对象。字段名必须严格使用匹配 SKILL.md、"
+         + "明确引用的 reference 文件或 describe_shortcut 返回定义中的字段；"
+         + "不要把 query/name/keyword 等近义字段互相替换。";
```

说明：

这里不是要删除 `buildArgsDescription()`，而是增强它的约束。
当前已改成中文增强版，保留原始语义并补强字段名约束：

```java
private String buildArgsDescription() {
    return "传给 shortcut 的 JSON 参数对象。字段名必须严格使用匹配 SKILL.md、明确引用的 reference 文件或 describe_shortcut 返回定义中的字段；不要把 query/name/keyword 等近义字段互相替换。";
}
```

新增 valid shortcut 列表：

```java
private JSONArray buildValidShortcutsJson() {
    JSONArray shortcuts = new JSONArray();
    for (ShortcutDefinition definition : shortcutRuntime.listDefinitions()) {
        if (definition != null && definition.getName() != null) {
            shortcuts.put(definition.getName());
        }
    }
    return shortcuts;
}
```

影响范围：

- 强约束 `run_shortcut.shortcut` 只能使用已注册值。
- 降低 `resolve_contact`、`search_contact` 这类自然语言幻化 shortcut 的概率。
- 降低参数字段名被模型改写的概率。

## 2. Prompt 与 Skill 注入约束

### `skill_loader.cpp`

路径：

```text
agent-core/src/main/cpp/src/core/skill_loader.cpp
```

修改原因：

全局 Skill 摘要提示原来只说明“Skill 名不是 shortcut 名”，但不足以阻止模型基于自然语言动作发明 shortcut。
因此补充 shortcut 精确标识符、validShortcuts、reference 文件路径等约束。

关键修改：

```diff
 ss << "Skill names are not shortcut names. Never pass them to run_shortcut or describe_shortcut.\n";
-ss << "If a shortcut's parameters are unclear, call describe_shortcut before run_shortcut.\n\n";
+ss << "Do not invent shortcut names from natural-language actions.\n";
+ss << "Shortcut names are exact identifiers; do not translate, singularize, pluralize, or abbreviate them.\n";
+ss << "The shortcut field must be explicitly listed in the matched SKILL.md or validShortcuts.\n";
+ss << "If shortcut_not_supported returns validShortcuts, use one exact value from validShortcuts or stop; do not retry the rejected name.\n";
+ss << "If a listed shortcut's parameters are unclear, call describe_shortcut before run_shortcut.\n";
+ss << "Reference file paths are exact. Read only paths explicitly written in SKILL.md or returned by list_files; do not invent filenames from shortcut names.\n\n";
```

影响范围：

- 约束所有 Skill 选择后的通用执行行为。
- 避免模型继续重试已经被 `shortcut_not_supported` 拒绝的名称。
- 避免模型根据 shortcut 名猜测 reference 文件名。

### `agent_context_manager.hpp`

路径：

```text
agent-core/src/main/cpp/src/core/agent_context_manager.hpp
```

修改原因：

为了控制请求体大小，后续轮次只注入 Skill 摘要。
但发消息失败说明：如果摘要中没有完整业务约束，模型可能直接推断 shortcut 或 reference 文件名。
因此提示中明确：摘要只用于路由和本地导航，真正执行 shortcut 或业务动作前仍需读取完整 Skill。

关键修改：

```diff
- body << "Matched skill summaries. Full SKILL.md content is intentionally omitted; ";
- body << "use the structured execution hints and request escalation only if more detail is required.\n\n";
+ body << "Matched skill summaries are only for route selection and local navigation hints. ";
+ body << "Before invoking any shortcut or business-side action, read the matched SKILL.md ";
+ body << "unless its full content is already present in this turn. ";
+ body << "Do not infer shortcut names from summaries; use only shortcuts explicitly listed ";
+ body << "in the SKILL.md or returned by describe_shortcut. ";
+ body << "Reference paths in SKILL.md are exact; do not replace hyphens/underscores ";
+ body << "or invent reference filenames from shortcut names.\n\n";
```

影响范围：

- 保留“后续轮摘要注入”的 token 优化方向。
- 对需要业务 shortcut 的任务，提醒模型先读取完整 Skill。
- 避免 `send-im-message.md` 被猜成 `send_im_message.md`。

## 3. Core 场景特化清理

这组改动的目标是：业务语义保留在 `SKILL.md` 中，Core 只保留通用 UI 结构、区域、角色和安全策略。

### `agent_candidate_matcher.hpp`

路径：

```text
agent-core/src/main/cpp/src/core/agent_candidate_matcher.hpp
```

#### 3.1 移除头像/个人中心/我的等业务词

修改原因：

之前 core 会通过“头像 / 个人中心 / 我的”等中文词判断左上角入口。
这会让“云空间”测试场景的业务语义泄漏到通用候选匹配逻辑中，可能影响其他任务。

关键修改：

```diff
- || contains_runtime_match(step.anchor_type, "profile_entry")) {
+ || normalize_region_label(step.anchor_type) == "profile_entry"
+ || normalize_region_label(step.container_role) == "header") {
     return true;
 }
-
-static const std::vector<std::string> keywords = {
-    u8"左上角", u8"头像", u8"个人头像", u8"我的头像",
-    u8"个人中心", u8"我的", u8"我", "avatar", "profile", "me", "mine"
-};
```

新的判断只信任结构化字段：

- `anchor_type=profile_entry`
- `container_role=header`
- `region=top_left/header_left`

#### 3.2 抽象日程/业务网格角色

修改原因：

`business_grid`、`schedule_item`、`schedule_list` 属于场景特化命名。
Core 应该识别更通用的 UI role，例如 `content_item`、`detail_item`、`card_grid`。

关键修改：

```diff
- || candidate_anchor_type_is(candidate, "schedule_item")
+ || candidate_anchor_type_is(candidate, "content_item")
+ || candidate_anchor_type_is(candidate, "detail_item")

- || candidate_container_role_is(candidate, "schedule_list");
+ || candidate_container_role_is(candidate, "feed");
```

```diff
- if (candidate_container_role_is(candidate, "business_grid")
-         || candidate_container_role_is(candidate, "grid")
+ if (candidate_container_role_is(candidate, "grid")
+         || candidate_container_role_is(candidate, "card_grid")
+         || candidate_container_role_is(candidate, "launcher_grid")
          || candidate_container_role_is(candidate, "card")) {
```

#### 3.3 左上角坐标兜底改成 Skill 显式开启

修改原因：

左上角坐标兜底属于高风险能力。
以前只要 core 判断是角落入口，就可能启用坐标恢复。现在改为必须由 Skill 明确声明 `fallback_strategy=top_left_header_entry`。

关键修改：

```diff
+bool step_allows_coordinate_fallback(const SkillStepHint& step) {
+    const std::string normalized_strategy = normalize_region_label(step.fallback_strategy);
+    if (normalized_strategy != "top_left_header_entry") {
+        return false;
+    }
+    const std::string normalized_region = normalize_region_label(step.region);
+    return normalized_region == "top_left" || normalized_region == "header_left";
+}

- if (!step_prefers_corner_entry(step) || snapshot.snapshot_id.empty()
+ if (!step_allows_coordinate_fallback(step) || snapshot.snapshot_id.empty()
```

说明：

这使得坐标兜底从“Core 根据业务词隐式判断”变成“Skill 显式授权”。

### `agent_navigation_executor.hpp`

路径：

```text
agent-core/src/main/cpp/src/core/agent_navigation_executor.hpp
```

修改原因：

移除“云空间”作为 readout hint 的 core 特化词。
云空间是否需要 readout 应由 `cloud_space_summary/SKILL.md` 的 route 和 stop condition 决定。

关键修改：

```diff
- u8"云空间", u8"文档", u8"文件", u8"内容",
+ u8"文档", u8"文件", u8"内容",
```

### `agent_observation.hpp`

路径：

```text
agent-core/src/main/cpp/src/core/agent_observation.hpp
```

修改原因：

移除“日程 / 日历”弱完成信号。
查看日程任务是否完成，应由 Skill 中的 `stop_condition` 和 readout contract 判断，不应在 core 中写死业务词。

关键修改：

```diff
- u8"日程",
- u8"日历",
```

### `agent_request_builder.hpp`

路径：

```text
agent-core/src/main/cpp/src/core/agent_request_builder.hpp
```

修改原因：

当模型或本地执行准备点击角落入口时，如果 gesture 不在安全区域，只有 Skill 明确允许坐标兜底时才进行恢复；否则拒绝该 tool call。

关键修改：

```diff
- if (step_prefers_corner_entry(pending_step)) {
+ if (step_prefers_corner_entry(pending_step)
+         || step_allows_coordinate_fallback(pending_step)) {
```

```diff
- auto recovery_call = build_corner_region_recovery_tool_call(pending_step, snapshot);
+ std::optional<ToolCall> recovery_call;
+ if (step_allows_coordinate_fallback(pending_step)) {
+     recovery_call = build_corner_region_recovery_tool_call(pending_step, snapshot);
+ }
```

### `agent_runtime_state.hpp`

路径：

```text
agent-core/src/main/cpp/src/core/agent_runtime_state.hpp
```

修改原因：

新增 `fallback_strategy` 字段，用于承载 Skill 对坐标兜底的显式授权。

关键修改：

```diff
 struct SkillStepHint {
     std::string region;
     std::string anchor_type;
     std::string container_role;
+    std::string fallback_strategy;
```

### `agent_route_planner.hpp`

路径：

```text
agent-core/src/main/cpp/src/core/agent_route_planner.hpp
```

修改原因：

解析、序列化和日志描述 `fallback_strategy`，让该字段从 Skill frontmatter 进入运行时状态。

关键修改：

```diff
+ if (!step.fallback_strategy.empty()) {
+     json["fallback_strategy"] = step.fallback_strategy;
+ }
```

```diff
+ if (!step.fallback_strategy.empty()) {
+     if (stream.tellp() > 0) {
+         stream << " ";
+     }
+     stream << "[fallback_strategy=" << step.fallback_strategy << "]";
+ }
```

```diff
+ step.fallback_strategy = first_string_value(step_json,
+         {"fallback_strategy", "fallbackStrategy", "coordinate_fallback", "coordinateFallback"});
```

## 4. Skill 文件改动

### `attendance_punch/SKILL.md`

路径：

```text
app/src/main/assets/workspace/skills/attendance_punch/SKILL.md
```

修改原因：

将业务特化 role `business_grid` 改成通用 UI role `card_grid`。

关键修改：

```diff
- "container_role":"business_grid"
+ "container_role":"card_grid"
```

### `calendar_schedule/SKILL.md`

路径：

```text
app/src/main/assets/workspace/skills/calendar_schedule/SKILL.md
```

修改原因：

同样将 `business_grid` 改成 `card_grid`，避免 core 依赖业务命名。

关键修改：

```diff
- "container_role":"business_grid"
+ "container_role":"card_grid"
```

### `cloud_space_summary/SKILL.md`

路径：

```text
app/src/main/assets/workspace/skills/cloud_space_summary/SKILL.md
```

修改原因：

云空间入口需要左上角兜底，但该能力必须由 Skill 显式开启，而不是 core 根据“头像 / 我的 / 个人中心”等业务词推断。

关键修改：

```diff
- "region":"top_left","action":"tap"
+ "region":"top_left","anchor_type":"profile_entry","container_role":"header",
+ "fallback_strategy":"top_left_header_entry","action":"tap"
```

说明文字同步更新：

```diff
- 如果页面上没有明确头像，不要猜测位置。
+ 如果页面上没有明确头像，只有 `execution_hints` 显式配置 `fallback_strategy=top_left_header_entry` 时，runtime 才可以使用保守左上角坐标恢复；否则不要猜测位置。
```

### `contact_resolver/SKILL.md`

路径：

```text
app/src/main/assets/workspace/skills/contact_resolver/SKILL.md
```

修改原因：

发消息失败中，模型把联系人解析幻化成 `resolve_contact`，并把 `search_contacts` 的参数 `query` 改成了 `name`。
因此将 shortcut 名称和参数字段约束补到 Skill 中。

关键修改：

```diff
+ - “解析联系人 / 查找联系人 / 搜索联系人”这类语义动作必须使用已注册 shortcut `search_contacts`
+ - 不要根据自然语言动作发明 shortcut 名，例如不要调用未注册的 `resolve_contact`
+ - 调用 `search_contacts` 时参数必须使用 `query`，不要使用 `name`、`keyword` 等未声明字段
```

## 5. AAR 构建产物

以下 AAR 已根据当前代码重新构建并同步到 `app/libs`：

```text
app/libs/agent-android-debug.aar
app/libs/agent-core-debug.aar
```

变化：

```text
agent-android-debug.aar: 380937 -> 391159 bytes
agent-core-debug.aar:    4156310 -> 4157130 bytes
```

说明：

- `agent-android-debug.aar` 包含 shortcut enum schema 和 shortcut channel 错误返回增强。
- `agent-core-debug.aar` 包含 prompt、Skill summary、候选匹配和坐标兜底策略调整。

## 6. 未跟踪文档文件

以下文件是 OCR + Native 融合 PPT / Draw.io 图：

```text
docs/architecture/ocr-native-fusion-ppt.svg
docs/architecture/ocr-native-fusion-ppt.drawio
docs/architecture/ocr-native-fusion-example-flow-ppt.svg
docs/architecture/ocr-native-fusion-example-flow-ppt.drawio
```

用途：

- 用于 PPT 展示 nativeViewXml 与 OCR / screen vision 融合过程。
- `.svg` 可直接用于文档或汇报。
- `.drawio` 可继续编辑。

是否建议入库：

- 如果后续文档或汇报需要复用，建议入库。
- 如果只是临时图稿，可以暂不提交。

## 7. `tmp/` 临时文件

当前未跟踪的 `tmp/` 内容包括：

```text
tmp/aar-inspect-agent-android/classes.jar
tmp/device-task-20260423-105843/events.jsonl
tmp/generate_web_dom_native_xml_ui_locator_ppt.ps1
tmp/latest-events-20260422-113818-82cca109.jsonl
tmp/me_page_current.png
tmp/meta-20260422-113818-82cca109.json
tmp/paused/ShortcutAliasResolver.java
tmp/response-20260422-113818-82cca109.txt
tmp/task-20260422-154350/events.jsonl
tmp/task-20260422-154350/response.txt
tmp/task-20260422-162106/events.jsonl
tmp/task-20260422-162106/response.txt
```

说明：

- `events.jsonl` / `response.txt` 是任务分析日志。
- `me_page_current.png` 是用于绘图参考的页面截图。
- `tmp/paused/ShortcutAliasResolver.java` 是暂停的 shortcut alias 实验文件，已移出源码目录，避免参与编译。
- `classes.jar` 是 AAR 检查产物。

是否建议入库：

- 不建议提交 `tmp/`。
- `ShortcutAliasResolver.java` 如需继续研究，应移回正式源码路径后单独评审；当前不应入库。

## 8. 建议提交拆分

建议按以下粒度拆分提交：

1. Shortcut 协议硬约束
   - `DescribeShortcutChannel.java`
   - `ShortcutRuntimeChannel.java`

2. Prompt 与 Skill 读取约束
   - `skill_loader.cpp`
   - `agent_context_manager.hpp`
   - `contact_resolver/SKILL.md`

3. Core 场景特化清理
   - `agent_candidate_matcher.hpp`
   - `agent_navigation_executor.hpp`
   - `agent_observation.hpp`
   - `agent_request_builder.hpp`
   - `agent_runtime_state.hpp`
   - `agent_route_planner.hpp`
   - `attendance_punch/SKILL.md`
   - `calendar_schedule/SKILL.md`
   - `cloud_space_summary/SKILL.md`

4. AAR 构建产物
   - `app/libs/agent-android-debug.aar`
   - `app/libs/agent-core-debug.aar`

5. 可选文档图
   - `docs/architecture/ocr-native-fusion-*.svg`
   - `docs/architecture/ocr-native-fusion-*.drawio`

不建议提交：

- `tmp/`

## 9. 当前风险点

### 9.1 提示风格一致性

`ShortcutRuntimeChannel.java` 和 `DescribeShortcutChannel.java` 中的 shortcut 协议硬约束已改为中文增强版。
当前仍有部分 Core prompt 使用英文，这是历史风格；如果后续希望全中文化，可单独评审 `skill_loader.cpp` 与 `agent_context_manager.hpp` 中的通用提示。

### 9.2 enum schema 兼容性

`run_shortcut.shortcut` 和 `describe_shortcut.shortcut` 增加 enum 后，模型更不容易幻化 shortcut。
但如果后续 shortcut 是动态注册的，需要确认 `shortcutRuntime.listDefinitions()` 在 tool schema 构建时已经包含完整列表。

### 9.3 坐标兜底依赖 Skill 显式声明

左上角兜底默认收紧后，如果某个 Skill 需要角落入口但未配置 `fallback_strategy`，可能无法使用保守坐标恢复。
这属于预期行为：高风险兜底必须由 Skill 显式授权。

## 10. 验证记录

已执行：

```powershell
.\gradlew.bat :agent-core:assembleDebug :agent-android:assembleDebug syncDemoSdkAars --offline
```

结果：

```text
BUILD SUCCESSFUL
```

注意：

第一次构建曾因为 PowerShell 写文件引入 UTF-8 BOM 导致 Java 编译失败。
随后已将相关文件重写为 UTF-8 without BOM，并重新构建成功。