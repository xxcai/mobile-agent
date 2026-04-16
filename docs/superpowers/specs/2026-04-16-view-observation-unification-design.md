# View Observation 统一设计说明

## 1. 背景

当前仓库存在四类并行 observation 表达：

- `nativeViewXml`
- `webDom`
- `visualObservationJson`
- `hybridObservationJson`

它们分别服务于不同来源与阶段：

- `nativeViewXml` 提供原生结构
- `webDom` 提供 WebView DOM 树
- `visualObservationJson` 提供 screen vision 的视觉语义结果
- `hybridObservationJson` 提供 native + vision 融合后的解释结果

但它们直接暴露给上层后，调用方必须知道每一路数据的历史背景与字段差异，导致：

1. LLM 不能稳定依赖单一 observation 契约。
2. `android_view_context_tool` 的输出职责混杂了“标准结果”和“原始附件”。
3. 未来要把 resolver、gesture、web action 迁到统一结构时，缺少稳定中间层。

## 2. 参考模型

本次统一的目标语义参考 `orb-eye` 中的两类输出：

- `getUiTree()`
- `getScreenElements()`

它们的核心思想不是把所有原始数据直接透传给模型，而是先提炼成两层稳定结构：

1. **UI Tree**
   - 用于表达页面层级结构与节点状态
2. **Screen Elements**
   - 用于表达当前可理解、可选择、可执行的屏幕元素集合

因此，本次统一不再以 `hybridObservation` 为主入口，也不再把 `tree / nodes / nodesFormat` 当作兼容附属字段，而是定义一套新的 canonical schema：

- `uiTree`
- `screenElements`
- `pageSummary`
- `quality`
- `raw`

## 3. 设计目标

本次设计目标如下：

1. 对 LLM 只暴露一套统一的页面理解结构。
2. 四类 observation 都通过门面模式归一到同一 schema。
3. Registry 保存的是统一 observation 快照，而不是单纯的原始材料集合。
4. 旧字段继续保留一段时间，作为兼容和排障附件。

## 4. 非目标

本次设计不做以下事情：

1. 不修改 `android_gesture_tool` 输入协议。
2. 不修改 `android_web_action_tool` 输入协议。
3. 不删除既有 `nativeViewXml`、`webDom`、`visualObservationJson`、`hybridObservationJson` 返回字段。
4. 不要求一次性把所有 consumer 都切到新结构。

## 5. 统一后的 canonical schema

## 5.1 顶层结构

统一后的 observation 以 `UnifiedViewObservation` 表示，顶层字段定义如下：

| 字段 | 含义 | 用途 | 备注 |
| --- | --- | --- | --- |
| `snapshotId` | 当前 observation 快照唯一标识 | 用于 gesture / web action / 日志链路做 observation 绑定 | 继续沿用现有 observation-bound 协议 |
| `activityClassName` | 当前前台页面对应的 Activity 类名 | 帮助模型和执行层理解页面归属 | WebView 页面也保留宿主 Activity 名 |
| `source` | 本次 canonical observation 的主来源 | 说明本次主观察结果来自 native、web、visual 或 hybrid | 不是 raw 附件是否存在的完整枚举 |
| `interactionDomain` | 当前页面主交互域 | 帮助后续判断走 native gesture 还是 web action | 典型值为 `native`、`web` |
| `targetHint` | 本轮 observation 绑定的任务提示词 | 帮助模型理解 observation 的筛选背景 | 可为空 |
| `createdAtEpochMs` | observation 创建时间戳 | 用于调试、排序、时效性判断 | 与 snapshot 生命周期一致 |
| `currentTurnOnly` | observation 是否只在当前回合有效 | 约束执行链不要跨轮滥用旧快照 | 与现有 snapshot 语义保持一致 |
| `pageUrl` | 当前页面 URL | 便于识别 Web 页面和做新旧页面一致性校验 | native 页面通常为空 |
| `pageTitle` | 当前页面标题 | 便于模型快速理解 Web 页或业务页标题 | native 页面可为空 |
| `pageSummary` | 面向 LLM 的统一页面摘要 | 作为模型阅读 observation 的第一入口 | 取代对 `hybridObservation.summary` 的直接依赖 |
| `uiTree` | 统一页面树结构 | 表达页面层级、节点状态与空间位置 | 对应 `orb-eye` 的 `getUiTree()` 语义 |
| `screenElements` | 统一屏幕元素列表 | 作为目标选择、对齐和执行引用的主数据 | 对应 `orb-eye` 的 `getScreenElements()` 语义 |
| `quality` | observation 质量与来源统计 | 帮助模型、调试页、日志判断可靠性 | 统一承载 source 质量指标 |
| `raw` | 原始 observation 附件集合 | 用于兼容旧 consumer、调试和排障 | LLM 主路径不应优先依赖它 |

## 5.2 `uiTree` 结构

`uiTree` 表示统一后的页面树。单节点字段定义如下：

| 字段 | 含义 | 用途 | 备注 |
| --- | --- | --- | --- |
| `className` | 节点的原生类名 | 帮助模型理解 native 控件类型 | Native/Hybrid 主要使用 |
| `text` | 节点可见文本 | 提供页面语义信息 | 允许为空字符串 |
| `contentDescription` | 节点的描述性文本 | 补充无文本控件的语义 | native 对应 accessibility desc，web 可映射 aria label |
| `resourceId` | 节点资源 ID | 辅助精确识别控件 | Web/Visual 路径通常为空 |
| `packageName` | 节点所属包名 | 区分多窗口或宿主归属 | 主要用于 native |
| `tagName` | DOM 标签名或视觉语义标签 | 帮助识别 web/visual 节点类型 | Web 主要使用，如 `button`、`input` |
| `source` | 当前树节点的来源标记 | 表明该节点来自哪一路感知 | 典型值如 `native_xml`、`web_dom`、`visual`、`hybrid` |
| `clickable` | 节点是否可点击 | 帮助判断可交互性 | 所有来源统一暴露 |
| `editable` | 节点是否可编辑 | 帮助识别输入框等输入目标 | 所有来源统一暴露 |
| `focused` | 节点是否处于焦点态 | 帮助理解当前输入焦点或激活态 | native 更常见 |
| `selected` | 节点是否已被选中 | 帮助识别 tab、列表选中项等 | 所有来源统一暴露 |
| `enabled` | 节点是否可用 | 避免模型选择不可操作控件 | 所有来源统一暴露 |
| `scrollable` | 节点是否可滚动 | 帮助判断是否为列表/容器 | 所有来源统一暴露 |
| `checked` | 节点是否处于勾选态 | 帮助理解复选框、开关等状态 | 所有来源统一暴露 |
| `bounds` | 节点边界框 | 提供统一几何位置表达 | 建议统一为 `[l,t][r,b]` 字符串 |
| `centerX` | 节点中心点 X 坐标 | 便于后续执行引用或调试展示 | 由 `bounds` 派生 |
| `centerY` | 节点中心点 Y 坐标 | 便于后续执行引用或调试展示 | 由 `bounds` 派生 |
| `children` | 子节点列表 | 表达页面层级结构 | Visual 路径可输出简化子树 |

补充约束：

- Native 来源主要填充 `className/resourceId/packageName`
- Web 来源主要填充 `tagName`
- Visual 来源不强求完整 DOM/Accessibility 层级，可生成简化树
- 所有来源都统一输出 `bounds/centerX/centerY`

## 5.3 `screenElements` 结构

`screenElements` 表示统一后的可理解、可引用、可执行元素列表。单元素字段定义如下：

| 字段 | 含义 | 用途 | 备注 |
| --- | --- | --- | --- |
| `elementId` | 当前快照内的统一元素标识 | 用于日志、调试、引用元素 | 不要求跨快照稳定 |
| `source` | 元素来源标记 | 帮助判断元素是 native、web、visual 还是 fused | Hybrid 映射时可保留 `fused` |
| `text` | 元素主显示文本 | 作为目标匹配和页面理解的主要语义 | 可为空 |
| `contentDescription` | 元素描述文本 | 为图标、按钮等补充可读语义 | 与 `text` 互补 |
| `resourceId` | 元素资源 ID | 帮助精确识别 native 控件 | Web/Visual 路径通常为空 |
| `className` | 元素原生类名 | 帮助理解控件类型 | Native/Hybrid 常用 |
| `tagName` | 元素 DOM 标签名或视觉语义标签 | 帮助理解 web/visual 控件类型 | Web 常用 |
| `role` | 元素语义角色 | 帮助 LLM 理解它是按钮、输入框、tab、列表项等 | 可由 hybrid/visual 推断 |
| `clickable` | 元素是否可点击 | 选择 tap 目标的重要依据 | 所有来源统一暴露 |
| `editable` | 元素是否可编辑 | 选择输入目标的重要依据 | 所有来源统一暴露 |
| `scrollable` | 元素是否可滚动 | 识别列表、滚动容器 | 所有来源统一暴露 |
| `enabled` | 元素是否可用 | 避免选择禁用目标 | 所有来源统一暴露 |
| `selected` | 元素是否选中 | 理解 tab、当前项状态 | 所有来源统一暴露 |
| `checked` | 元素是否勾选 | 理解 switch、checkbox 等状态 | 所有来源统一暴露 |
| `bounds` | 元素边界框 | 用于目标选择与 gesture 引用 | 建议统一为 `[l,t][r,b]` |
| `centerX` | 元素中心点 X 坐标 | 后续执行层可直接引用 | 由 `bounds` 派生 |
| `centerY` | 元素中心点 Y 坐标 | 后续执行层可直接引用 | 由 `bounds` 派生 |
| `score` | 元素置信度或优先级分数 | 帮助模型在多个候选间排序 | Hybrid/Visual 路径更常用 |
| `ref` | Web 元素引用标识 | 便于 `android_web_action_tool` 做稳定元素定位 | 主要用于 Web 路径 |
| `selector` | Web 元素选择器 | 作为 `ref` 的补充定位信息 | 主要用于 Web 路径 |
| `nativeNodeIndex` | 元素在 native 树中的节点索引 | 便于兼容旧 native 引用链路 | Native/Hybrid 路径保留 |

补充约束：

- `elementId` 是统一元素标识，不要求跨快照稳定
- Native 路径可保留 `nativeNodeIndex`
- Web 路径可保留 `ref/selector`
- Hybrid 路径可把现有 `actionableNodes` 映射进该结构

## 5.4 `quality` 结构

统一后的 `quality` 用于表达 observation 质量、可用信号和 adapter 归因。字段定义如下：

| 字段 | 含义 | 用途 | 备注 |
| --- | --- | --- | --- |
| `mode` | 当前 observation 的质量模式或融合模式 | 帮助理解这是 native、web、screen 还是 hybrid 结果 | 可复用现有 `native_only`、`web_only`、`hybrid_native_screen` 等语义 |
| `availableSignals` | 当前 observation 可用的底层信号集合 | 帮助判断结果依赖了哪些输入 | 可为对象，如 `nativeXml=true`、`screenVisionCompact=true` |
| `nativeNodeCount` | 纳入 canonical/native 处理的 native 节点数 | 帮助判断 native 树信息密度 | native/hybrid 常用 |
| `visionTextCount` | 视觉 observation 中的文本项数量 | 帮助判断视觉文本覆盖度 | visual/hybrid 常用 |
| `visionControlCount` | 视觉 observation 中的控件项数量 | 帮助判断视觉控件覆盖度 | visual/hybrid 常用 |
| `fusedMatchCount` | native 与 visual 成功融合匹配的数量 | 帮助判断 hybrid 结果可信度 | hybrid 常用 |
| `webNodeCount` | Web DOM 节点数量 | 帮助判断 web 树规模 | web 常用 |
| `webTruncated` | Web DOM 是否发生截断 | 帮助判断 web 结果是否不完整 | web 常用 |
| `adapterName` | 生成当前 canonical observation 的 adapter 名称 | 便于调试、日志和问题归因 | 如 `HybridObservationAdapter` |

补充约束：

- `quality` 允许按 source 补充特有统计项
- 不要求所有字段在所有 source 下都非空
- 缺失字段应按“该来源不适用”理解，而不是解析失败

## 5.5 `raw` 结构

`raw` 作为兼容附件保留原始 observation 输入。字段定义如下：

| 字段 | 含义 | 用途 | 备注 |
| --- | --- | --- | --- |
| `nativeViewXml` | 原始 native XML 页面树 | 兼容旧 consumer、调试 native 树解析问题 | 不应作为 LLM 主阅读入口 |
| `webDom` | 原始 Web DOM snapshot | 兼容旧 web consumer、调试 DOM 抓取问题 | 结构保持接近 `WebDomScriptFactory` 当前输出 |
| `visualObservationJson` | 原始视觉 observation 结果 | 调试视觉识别与 compact 裁剪结果 | 主要用于 visual/hybrid 排障 |
| `hybridObservationJson` | 原始 hybrid observation 结果 | 兼容旧 prompt/consumer、调试融合逻辑 | 在迁移期继续保留 |
| `screenSnapshot` | 屏幕截图引用或截图数据句柄 | 辅助调试视觉结果与真实页面差异 | 是否为 URL/句柄取决于现有 provider 输出 |

使用原则：

- LLM 主路径不依赖 `raw`
- 调试、回归、旧 consumer 兼容可继续使用 `raw`
- canonical 字段与 raw 不一致时，以 canonical 为主、raw 用于排障解释

## 6. 门面模式设计

## 6.1 核心对象

新增以下对象：

- `UnifiedViewObservation`
- `UnifiedUiTreeNode`
- `UnifiedScreenElement`
- `UnifiedObservationAdapter`
- `UnifiedViewObservationFacade`

职责划分：

- `UnifiedViewObservation`
  - 单次统一 observation 结果
- `UnifiedUiTreeNode`
  - 统一树节点 DTO
- `UnifiedScreenElement`
  - 统一元素 DTO
- `UnifiedObservationAdapter`
  - 各 source 的适配接口
- `UnifiedViewObservationFacade`
  - 对外唯一入口，负责按 source 调对应 adapter

## 6.2 Adapter 列表

本次至少包含四个 adapter：

- `NativeXmlObservationAdapter`
- `WebDomObservationAdapter`
- `VisualObservationAdapter`
- `HybridObservationAdapter`

## 6.3 门面调用方式

统一入口形态如下：

```java
UnifiedViewObservation observation = UnifiedViewObservationFacade.build(
    source,
    activityClassName,
    interactionDomain,
    targetHint,
    pageUrl,
    pageTitle,
    nativeViewXml,
    webDom,
    visualObservationJson,
    hybridObservationJson,
    screenSnapshot
);
```

门面内部流程：

1. 根据 `source` 选择主 adapter
2. 将四类 raw observation 作为上下文输入
3. 生成 canonical 的 `uiTree`
4. 生成 canonical 的 `screenElements`
5. 生成 `pageSummary`
6. 生成 `quality`
7. 附带 `raw`

## 7. 四类 observation 的映射规则

## 7.1 Native XML -> Canonical

输入：

- `nativeViewXml`

输出规则：

- `uiTree`
  - 由 XML 层级解析得到
- `screenElements`
  - 从包含文本、可点击、可编辑、可滚动语义的节点提取
- `pageSummary`
  - 基于高频文本、当前 Activity、关键控件生成
- `quality`
  - 记录 native 节点数量、文本节点数量、adapter 名

该路径的目标是接近 `orb-eye` 的 `getUiTree` 与 `getScreenElements`。

## 7.2 Web DOM -> Canonical

输入：

- `webDom`

当前 `WebDomScriptFactory` 已经能生成接近统一目标的 DOM tree，包含：

- `ref`
- `tag`
- `id`
- `selector`
- `text`
- `ariaLabel`
- `clickable`
- `inputable`
- `bounds`
- `children`

输出规则：

- `uiTree`
  - 直接由 DOM tree 规范字段名得到
- `screenElements`
  - flatten 所有可交互或有文本价值的 DOM 节点
- `pageSummary`
  - 基于 `pageTitle/pageUrl` 与元素统计生成
- `quality`
  - 记录 `nodeCount/maxDepthReached/truncated`

## 7.3 Visual Observation -> Canonical

输入：

- `visualObservationJson`

该路径不具备真实 DOM/Accessibility 树，因此不强行构造伪完整层级。

输出规则：

- `uiTree`
  - 生成简化视觉根节点，例如 `className = "visual_root"`
  - `children` 按 section / item 组织
- `screenElements`
  - 由 texts / controls / items / sections 提取
- `pageSummary`
  - 优先使用 vision summary
- `quality`
  - 记录 text/control/item/section 数量与裁剪信息

## 7.4 Hybrid Observation -> Canonical

输入：

- `hybridObservationJson`
- `nativeViewXml`
- `visualObservationJson`

输出规则：

- `uiTree`
  - 以 native tree 为主结构
  - 可在节点上补充 hybrid 语义，但不改变主层级来源
- `screenElements`
  - 优先由 `hybridObservation.actionableNodes` 映射生成
- `pageSummary`
  - 直接使用 `hybridObservation.summary`
- `quality`
  - 直接吸收 `availableSignals/quality/mode`

该路径将成为对 LLM 最友好的主 observation 结果，但它仍然只是 canonical schema 的一个输入来源，而不是最终输出结构本身。

## 8. 与现有 provider / registry 的关系

## 8.1 `ViewContextSnapshotProvider`

native / screen 路径继续做已有采集与裁剪：

1. dump native tree
2. 生成 `visualObservationJson`
3. 生成 `hybridObservationJson`

新增步骤：

4. 调 `UnifiedViewObservationFacade`
5. 产出 canonical observation
6. 写入 snapshot
7. 对 `ToolResult` 输出 canonical 字段

## 8.2 `RealWebDomSnapshotProvider`

web 路径继续保留现有 DOM 抓取与 metadata 提取，新增：

1. 调 `UnifiedViewObservationFacade`
2. 生成 canonical `uiTree + screenElements`
3. 写入 snapshot
4. 输出 canonical 字段

## 8.3 `ViewObservationSnapshotRegistry`

registry 不再只存 raw observation，而是存：

- canonical observation 主字段
- raw 附件

这样后续 gesture / web action 即使继续只依赖 `snapshotId/source/pageUrl`，registry 也已经完成了统一语义升级。

## 9. 对 LLM 的主接口

统一后，LLM 主消费顺序改为：

1. `pageSummary`
2. `screenElements`
3. `uiTree`
4. `quality`
5. 必要时再查看 `raw`

不再要求模型优先理解：

- `hybridObservation.summary`
- `hybridObservation.actionableNodes`
- `nativeViewXml`
- `webDom`

这些字段将退为兼容与调试信息。

## 10. Consumer 兼容策略

## 10.1 Gesture / Web Action

本阶段不改输入协议。

原因：

- 当前它们主要依赖 `snapshotId`
- 以及 activity/page/source 的一致性校验

因此只要 snapshot 继续保存这些元信息，就不会被新 schema 打断。

## 10.2 `ObservationTargetResolver`

本阶段建议改为：

1. 先读 `screenElements`
2. 如果没有 canonical elements，再回退 `hybridObservation.actionableNodes`
3. 最后再回退 `nativeViewXml`

这样 consumer 开始向 canonical schema 迁移，但不会丢失现有能力。

## 10.3 agent-core Prompt

prompt 文档应改为：

1. 优先用 `pageSummary` 建立页面理解
2. 优先用 `screenElements` 选目标
3. 需要层级关系时读 `uiTree`
4. 只在必要时回退 `raw`

## 11. 测试策略

测试重点不再是“新加一个兼容投影字段”，而是：

1. 四种 source 都能生成 canonical observation
2. `uiTree` 结构稳定
3. `screenElements` 结构稳定
4. raw 附件仍然保留
5. snapshot 元数据不回归

至少需要以下测试：

1. `UnifiedViewObservationFacadeTest`
   - native XML 映射
   - web DOM 映射
   - visual observation 映射
   - hybrid observation 映射

2. `ViewContextToolChannelTest`
   - 返回 canonical 字段
   - 旧字段继续存在

3. `RealWebDomSnapshotProviderTest`
   - Web DOM 路径的 `uiTree/screenElements` 正确

4. `ScreenSnapshotObservationProviderTest`
   - native/screen/hybrid 路径的 canonical 字段正确

5. `ObservationTargetResolverTest`
   - 先走 `screenElements`
   - 再走旧 fallback

## 12. 风险与缓解

### 风险 1：Visual 路径没有真实树结构

缓解：

- 允许 visual adapter 输出简化树
- 统一目标是“方便模型理解”，不是还原底层真实树

### 风险 2：Hybrid 与 canonical 语义重叠

缓解：

- 明确 hybrid 是输入源
- canonical 是最终输出层

### 风险 3：payload 体积变大

缓解：

- `uiTree` 和 `screenElements` 只保留 LLM 真正需要的字段
- `raw` 继续按 detail mode 控制裁剪

## 13. 最终结论

本次统一的核心是：

- 不再以 `hybridObservation` 为 LLM 主接口
- 不再把 `tree/nodes/nodesFormat` 作为临时兼容结构
- 以 `orb-eye` 风格的 `uiTree + screenElements` 为统一 canonical schema
- 四类 observation 全部通过 facade/adapters 归一
- LLM 主读 canonical 字段，旧字段降为兼容附件

这套设计既能统一当前四类 observation，又能为后续 consumer 迁移提供明确、稳定、可渐进演进的边界。
