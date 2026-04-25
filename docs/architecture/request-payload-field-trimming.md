# 请求体字段裁剪清单

本文档独立记录 `android_view_context_tool` 返回给 LLM 的字段级裁剪策略，说明不同 source、`screenVisionCompact`、`hybridObservation` 中每个字段的含义、是否删除，以及删除后对 LLM 结果的影响。

裁剪目标只针对发送给 LLM 的请求体，不删除本地 `ViewObservationSnapshotRegistry`、诊断日志和调试回放所需的完整观测数据。

## 请求体字段裁剪矩阵

本节描述的是“发送给 LLM 的 `android_view_context_tool` 结果”如何裁剪，不是删除本地观测能力。完整的页面快照仍保存在 `ViewObservationSnapshotRegistry` 和诊断链路中，用于 gesture / web action 校验、问题定位和回放。常规请求体只保留模型完成当前阶段任务所需的信息。

### 1. 顶层字段按 source 裁剪

| 场景 | 默认删除字段 | 暂不删除字段 | 对 LLM 的影响 |
| --- | --- | --- | --- |
| Native / Hybrid 原生页面 | `uiTree`、`screenElements`、`raw`、`screenVisionRaw` | `hybridObservation`、精简 `screenVisionCompact`、最小 `nativeViewXml` 摘要、`snapshotId`、`pageSummary`、`quality` | 低风险。原生导航主要依赖 `hybridObservation.actionableNodes`，完整树和 raw 主要用于调试 |
| Screen-only / 视觉兜底页面 | `raw`、`screenVisionRaw`，通常不返回完整 `uiTree` | `screenVisionCompact`、`hybridObservation` 或视觉候选摘要、`snapshotId` | 中低风险。必须保留可见文本、控件语义和必要 `bbox`，否则视觉兜底会退化 |
| Web / H5 页面 | `raw`、`screenVisionRaw`、空值字段如 `nativeViewXml:null`、`screenSnapshot:null` | `screenElements`、`webDom`、`pageUrl`、`pageTitle`、`snapshotId` | 不能一刀切。`screenElements` 当前包含 `ref/selector`，是 `android_web_action_tool` click/input 的关键依据 |
| `source=all` 调试场景 | 按实际子 source 分别裁剪 | 命中的 native/web 子结果各自保留必要字段 | 需要避免误删 Web 所需的 `screenElements.ref/selector` |
| Debug / `includeRawFallback=true` | 默认不删除 | 允许返回完整字段 | 用于排障，不作为常规 LLM 请求体基线 |

因此，`uiTree` 和 `screenElements` 对 Native 场景是可裁剪的大字段，但对 Web 场景不能同等处理。Web 场景只有在 `ref/selector/text/bounds` 等信息已经稳定映射到新的统一候选结构后，才能继续删除 `screenElements`。

### 2. `screenVisionCompact` 字段裁剪

`screenVisionCompact` 是视觉侧的紧凑结果。它的价值在于补充可见文本、视觉控件和区域信息；不是让 LLM 重新阅读完整 OCR / 视觉调试输出。

| 字段 | 裁剪策略 | 对 LLM 的影响 |
| --- | --- | --- |
| `debug` | 默认删除 | 低影响。主要用于视觉裁剪和排障 |
| `debug.dropSummary` | 仅诊断保留 | 低影响。常规请求不需要知道每类信号被裁掉多少 |
| `counts` | 可删除或保留 | 极低影响。只是 `texts/controls/sections/items` 数量统计，信息量小 |
| `texts[].id` | 默认删除 | 低影响。模型不需要视觉文本内部 id |
| `texts[].confidence` | 默认删除 | 低影响。排序和融合已在本地完成 |
| `texts[].importance` | 默认删除 | 低影响。模型不应重新基于视觉 importance 做排序 |
| `controls[].id` | 默认删除 | 低影响。点击不依赖视觉控件 id |
| `controls[].confidence` | 默认删除 | 低影响 |
| `controls[].importance` | 默认删除 | 低影响 |
| `sections[].matchedNativeNodeCount` | 默认删除 | 低影响，偏调试统计 |
| `sections[].collapsedItemCount` | 默认删除 | 低影响，偏视觉区域统计 |
| `items[].matchedNativeNodeCount` | 默认删除 | 低影响，偏调试统计 |
| `items[].textCount` | 默认删除 | 低影响，偏统计 |
| `items[].controlCount` | 默认删除 | 低影响，偏统计 |
| `sections` | 导航阶段可强压缩或删除 | 低到中等影响。布局理解有帮助，但目标点击主要依赖 `hybridObservation.actionableNodes` |
| `items` | 导航阶段可强压缩；Readout 阶段谨慎保留 | 中等影响。读内容任务可能需要列表项文本 |

`screenVisionCompact` 中不建议删除的字段如下。

| 字段 | 保留原因 |
| --- | --- |
| `summary` | AgentCore 会把它作为可见文本 fallback，用于页面理解 |
| `page` | 提供页面尺寸、标题等上下文，成本较小 |
| `texts[].text` | readout 和视觉兜底需要 |
| `controls[].label`、`controls[].type`、`controls[].role` | 视觉兜底和无 native 匹配时有用 |
| `texts[].bbox`、`controls[].bbox` | 当 `hybridObservation` 缺失或 screen-only 时，需要它定位目标 |
| `items[].summaryText`、`items[].bbox` | readout 阶段可能需要，用于总结页面列表内容 |

阶段化规则如下。

| 阶段 | 保留重点 | 可裁剪重点 |
| --- | --- | --- |
| `DISCOVERY` | `summary`、`page`、top texts、top controls、少量 sections/items | 删除 debug 明细和信号 id/confidence/importance |
| `FOLLOW_UP` | 当前目标相关 top texts / controls，必要 `bbox` | 强压缩 sections/items，删除统计字段 |
| `READOUT` | 可见文本、列表项摘要、状态文本 | 删除导航无关控件统计和调试信息 |

### 3. `hybridObservation` 字段裁剪

`hybridObservation` 是 Native + Screen Vision 融合后的主结构，也是原生页面 LLM 判断和本地 fast execute 的核心输入。它不能像 raw 字段一样整体删除，只能按用途裁剪。

| 字段 | 裁剪策略 | 对 LLM 的影响 |
| --- | --- | --- |
| `debug` | 默认删除 | 低影响。正常决策不需要 matchPairs/nativeOnlyCandidates/visionOnlyCandidates 明细 |
| `primarySource` | 默认删除 | 低影响。顶层已有 `source` |
| `activityClassName` | 默认删除重复值 | 低影响。顶层已有同名字段 |
| `targetHint` | 默认删除重复值 | 低影响。顶层已有当前 targetHint |
| `availableSignals` | 可删除或压成摘要 | 低影响，偏诊断 |
| `quality.visionDroppedTextCount` | 默认删除 | 低影响，偏视觉裁剪诊断 |
| `quality.visionDroppedControlCount` | 默认删除 | 低影响，偏视觉裁剪诊断 |
| `sections` | 导航请求可删除 | 低影响。导航目标选择主要看 `actionableNodes` |
| `listItems` | 导航请求可删除；Readout 阶段谨慎保留摘要 | 中等影响。读内容时可能需要列表项 |
| `executionHint` | 可在 prompt 固化后删除 | 低影响。内容基本固定，重复携带浪费 token |
| `actionableNodes[].nativeNodeIndex` | 默认删除 | 低影响，偏调试和追踪 |
| `actionableNodes[].matchScore` | 默认删除 | 低影响。保留 `source` 和 `score` 通常足够 |
| `actionableNodes[].visionRole` | 可条件删除 | 中低影响。若 `visionLabel/text/anchorType` 足够，可删 |
| `conflicts[].nativeNodeIndex` | 默认删除 | 低影响，偏调试 |
| `conflicts[].message` | 可压缩 | 中低影响。高风险冲突保留简短 message，普通 warning 可只保留 `code/severity` |
| `conflicts[].bounds` | Readout 阶段可删除；导航阶段谨慎保留 | 导航判断冲突位置时有用 |

`hybridObservation.actionableNodes[]` 中，Readout 阶段可以删除或弱化以下执行相关字段。

| 字段 | Readout 阶段策略 | 原因 |
| --- | --- | --- |
| `className` | 可删除或只保留目标相关项 | 内容总结通常不需要控件类名 |
| `contentDescription` | 可删除或只保留目标相关项 | 内容总结更依赖可见文本 |
| `parentSemanticContext` | 可删除 | 父容器语义主要服务导航 |
| `clickable` | 可删除 | readout 不执行点击 |
| `containerClickable` | 可删除 | readout 不执行点击 |
| `badgeLike` | 可删除 | 主要服务候选过滤 |
| `numericLike` | 可删除 | 主要服务候选过滤 |
| `decorativeLike` | 可删除 | 主要服务候选过滤 |
| `repeatGroup` | 可删除 | 主要服务候选过滤 |
| `anchorType` | 可弱化 | readout 通常不需要入口类型 |
| `containerRole` | 可弱化 | readout 通常不需要容器角色 |

导航阶段不建议删除的 `hybridObservation` 字段如下。

| 字段 | 保留原因 |
| --- | --- |
| `summary` | 页面理解主入口 |
| `page.width`、`page.height` | 区域判断、坐标归一和 fallback 有用 |
| `quality.nativeNodeCount`、`quality.fusedMatchCount` | 成本小，可辅助判断观测质量 |
| `actionableNodes[].id` | 候选追踪和日志有用，成本低 |
| `actionableNodes[].source` | 判断 `fused/native/vision_only` 置信度必须要 |
| `actionableNodes[].text` | 目标匹配核心字段 |
| `actionableNodes[].visionLabel` | 视觉语义补强 |
| `actionableNodes[].resourceId` | 稳定 native 识别依据 |
| `actionableNodes[].region` | 角落入口、底部 Tab、顶部栏等通用判断需要 |
| `actionableNodes[].anchorType` | 判断入口类型，如 tab/card/icon/text |
| `actionableNodes[].containerRole` | 判断父容器角色，如 grid item/list item/card |
| `actionableNodes[].bounds` | 点击执行必须要 |
| `actionableNodes[].score` | 候选排序和置信判断需要 |
| `actionableNodes[].actionability` | 给 LLM 快速理解候选可执行性 |
| `actionableNodes[].clickable`、`actionableNodes[].containerClickable` | 判断点自身还是父容器必须要 |
| `actionableNodes[].enabled`、`actionableNodes[].selected` | 状态判断有用，建议保留 |
| `conflicts[].code`、`conflicts[].severity` | 判断是否阻断 fast execute 必须要 |
| `conflicts[].bounds` | 导航阶段判断冲突位置有用 |

### 4. 删除字段对 LLM 的影响分级

| 字段类别 | 代表字段 | 影响等级 | 说明 |
| --- | --- | --- | --- |
| 调试类 | `debug`、`raw`、`screenVisionRaw`、`nativeNodeIndex` | 低 | 主要用于排障和回放，不应进入常规模型阅读路径 |
| 统计类 | `counts`、`matchedNativeNodeCount`、`collapsedItemCount`、`visionDroppedTextCount` | 低 | 对任务判断帮助有限，可通过日志保留 |
| 重复元信息 | `primarySource`、重复 `activityClassName`、重复 `targetHint` | 低 | 顶层已有等价字段 |
| 视觉排序中间量 | `confidence`、`importance`、`matchScore` | 低到中 | 本地已经完成排序融合，LLM 不应重新做底层排序 |
| 页面内容 | `summary`、`texts[].text`、`items[].summaryText` | 高 | 删除会影响 readout 和页面理解 |
| 可执行语义 | `source`、`text`、`visionLabel`、`resourceId`、`region`、`anchorType`、`containerRole` | 高 | 删除会影响目标匹配和候选排序 |
| 点击依据 | `bounds`、`clickable`、`containerClickable`、`enabled` | 高 | 删除会影响 gesture / fast execute |
| Web 执行引用 | `screenElements[].ref`、`screenElements[].selector` | 高 | 删除会导致 `android_web_action_tool` click/input 缺少目标引用 |

### 5. 字段级明细表

本表用于指导后续实现。`是` 表示常规 LLM 请求体默认删除；`否` 表示常规请求体保留；`条件删除` 表示需要按 source、detail mode 或诊断开关决定；`阶段删除` 表示导航阶段和 readout 阶段策略不同。

#### 5.1 顶层 `android_view_context_tool` 字段

| 字段名 | 字段含义 | Native / Hybrid 是否删除 | Web / H5 是否删除 | 说明 |
| --- | --- | --- | --- | --- |
| `success` | tool 调用是否成功 | 否 | 否 | LLM 和 runtime 都需要知道 tool 是否成功 |
| `channel` | tool 通道名 | 否 | 否 | Web provider 当前会返回，保留有助于日志和诊断 |
| `source` | 观测来源，如 `native_xml`、`screen_snapshot`、`web_dom` | 否 | 否 | 决定后续使用 gesture 还是 web action |
| `interactionDomain` | 交互域，如 `native` 或 `web` | 否 | 否 | 用于区分执行工具类型 |
| `mock` | 是否 mock 数据 | 否 | 否 | 防止把 mock observation 当真实页面执行 |
| `targetHint` | 本轮观测目标提示 | 否 | 否 | 候选筛选和日志定位需要 |
| `activityClassName` | 当前 Activity 类名 | 否 | 否 | 页面判断、stop condition 和日志定位需要 |
| `observationMode` | 观测模式，如 native tree 或 web dom | 否 | 否 | 帮助判断结果可信来源 |
| `observationDetailMode` | `DISCOVERY/FOLLOW_UP/READOUT` 细节档位 | 否 | 条件删除 | Native 阶段保留；Web 当前未使用可不返回 |
| `visualObservationMode` | screen vision 观测模式 | 条件删除 | 是 | Native 有视觉结果时保留；Web DOM 不依赖它 |
| `snapshotId` | 本轮 snapshot 标识 | 否 | 否 | gesture 和 web action 都需要绑定当前观测 |
| `snapshotCreatedAtEpochMs` | snapshot 创建时间 | 条件删除 | 条件删除 | 常规 LLM 可删除，诊断日志保留 |
| `snapshotScope` | snapshot 有效范围 | 条件删除 | 条件删除 | 当前固定为 current turn，常规 LLM 可删除 |
| `snapshotCurrentTurnOnly` | snapshot 是否仅当前 turn 有效 | 条件删除 | 条件删除 | 可由工具规则说明承载，常规 LLM 可删除 |
| `rawFallbackIncluded` | 是否包含 raw fallback | 条件删除 | 条件删除 | 诊断模式保留；常规请求可删除 |
| `uiTree` | 统一页面树结构 | 是 | 条件删除 | Native 默认删除；Web 第一阶段不建议删，除非已有等价精简 tree/element 替代 |
| `screenElements` | 统一屏幕元素列表 | 是 | 否 | Native 可由 `hybridObservation.actionableNodes` 替代；Web click/input 需要 `ref/selector` |
| `pageSummary` | 页面级摘要 | 否 | 否 | 成本小，是 LLM 快速理解页面的入口 |
| `quality` | 观测质量摘要 | 条件删除 | 条件删除 | 保留核心统计；删除调试统计 |
| `raw` | 原始统一 raw 包装 | 是 | 是 | 主要用于调试，常规 LLM 请求体删除 |
| `nativeViewXml` | 原生 View XML 摘要 | 条件删除 | 是 | Native 第一阶段保留最小摘要；Web 为空值时删除 |
| `webDom` | Web DOM 原始/紧凑结构 | 不适用 | 条件删除 | Web 第一阶段保留；后续有等价 `screenElements/hybridObservation` 后可压缩 |
| `webDomFormat` | Web DOM 格式 | 不适用 | 否 | 成本低，保留可避免格式歧义 |
| `screenSnapshot` | 截图引用 | 条件删除 | 是 | 常规 LLM 不直接消费时删除；诊断保留 |
| `screenSnapshotWidth` | 截图宽度 | 条件删除 | 是 | 可由 `hybridObservation.page.width` 替代 |
| `screenSnapshotHeight` | 截图高度 | 条件删除 | 是 | 可由 `hybridObservation.page.height` 替代 |
| `screenVisionCompact` | 视觉紧凑结果 | 条件删除 | 是 | Native/screen-only 保留精简版；Web DOM 不依赖 |
| `screenVisionRaw` | 原始视觉结果 | 是 | 是 | 调试字段，常规 LLM 请求体删除 |
| `hybridObservation` | native + vision 融合观测 | 否 | 条件删除 | Native 主输入；Web 当前主要用 `screenElements/webDom`，后续统一后可保留 Web 版 |
| `pageUrl` | Web 页面 URL | 不适用 | 否 | Web 页面判断和任务上下文需要 |
| `pageTitle` | Web 页面标题 | 不适用 | 否 | Web 页面理解需要 |
| `webViewCandidateCount` | WebView 候选数量 | 不适用 | 条件删除 | 诊断保留，常规 LLM 可删除 |
| `webViewSelectionReason` | WebView 选择原因 | 不适用 | 条件删除 | 诊断保留，常规 LLM 可删除 |
| `failureStage` | Web DOM 失败阶段 | 不适用 | 否 | 失败结果中保留，方便 LLM 决定重试或回退 |
| `rawJsResult` | Web JS 原始返回 | 不适用 | 是 | 失败诊断字段，常规 LLM 删除或截断 |
| `decodedJsResult` | Web JS 解码结果 | 不适用 | 是 | 失败诊断字段，常规 LLM删除或截断 |

#### 5.2 `screenVisionCompact` 字段

| 字段名 | 字段含义 | 是否删除 | 说明 |
| --- | --- | --- | --- |
| `summary` | 视觉模块生成的页面摘要 | 否 | 页面理解和 AgentCore 可见文本 fallback 需要 |
| `page` | 页面几何或标题等页面信息 | 否 | 成本小，可辅助坐标和页面判断 |
| `counts` | 各类视觉信号数量统计 | 是 | 对 LLM 判断帮助有限，日志保留即可 |
| `texts` | OCR 文本信号列表 | 阶段删除 | 导航保留 top 文本；readout 保留内容相关文本 |
| `texts[].id` | 文本信号内部 id | 是 | LLM 不需要内部 id |
| `texts[].text` | OCR 文本内容 | 否 | readout 和视觉兜底需要 |
| `texts[].bbox` | 文本区域坐标 | 条件删除 | screen-only 或视觉兜底需要；纯 readout 可按需保留 |
| `texts[].confidence` | OCR 置信度 | 是 | 本地已完成排序和融合 |
| `texts[].importance` | 视觉重要性 | 是 | 本地已完成排序和裁剪 |
| `controls` | 视觉控件信号列表 | 阶段删除 | 导航保留 top 控件；readout 可减少 |
| `controls[].id` | 控件信号内部 id | 是 | LLM 不需要内部 id |
| `controls[].type` | 控件类型 | 否 | 视觉兜底判断按钮、输入框等需要 |
| `controls[].label` | 控件可见标签 | 否 | 目标匹配需要 |
| `controls[].role` | 控件视觉角色 | 否 | 无 native 匹配时有价值 |
| `controls[].bbox` | 控件区域坐标 | 条件删除 | screen-only 或视觉兜底点击需要 |
| `controls[].confidence` | 控件识别置信度 | 是 | 本地已完成排序和融合 |
| `controls[].importance` | 控件视觉重要性 | 是 | 本地已完成排序和裁剪 |
| `sections` | 页面视觉分区 | 阶段删除 | discovery 可保留少量；follow-up 可强压缩；readout 只保留内容相关摘要 |
| `sections[].id` | 分区 id | 条件删除 | 常规 LLM 可删除，诊断保留 |
| `sections[].type` | 分区类型 | 条件删除 | 布局理解需要时保留 |
| `sections[].sectionId` | 所属分区 id | 条件删除 | 多级区域诊断保留即可 |
| `sections[].summaryText` | 分区摘要文本 | 条件删除 | readout 有价值，导航可压缩 |
| `sections[].bbox` | 分区坐标 | 条件删除 | 布局判断需要时保留 |
| `sections[].importance` | 分区重要性 | 是 | 本地排序后可删除 |
| `sections[].matchedNativeNodeCount` | 命中的 native 节点数量 | 是 | 调试统计 |
| `sections[].collapsedItemCount` | 折叠条目数量 | 是 | 调试统计 |
| `items` | 视觉列表项 | 阶段删除 | readout 阶段比导航阶段更重要 |
| `items[].id` | 条目 id | 条件删除 | 常规 LLM 可删除，诊断保留 |
| `items[].type` | 条目类型 | 条件删除 | 读内容场景可保留 |
| `items[].sectionId` | 条目所属区域 | 条件删除 | 诊断保留即可 |
| `items[].summaryText` | 条目摘要文本 | 否 | readout 总结内容需要 |
| `items[].bbox` | 条目坐标 | 条件删除 | readout 可保留，纯文本回答可删除 |
| `items[].importance` | 条目重要性 | 是 | 本地排序后可删除 |
| `items[].matchedNativeNodeCount` | 条目命中 native 数量 | 是 | 调试统计 |
| `items[].textCount` | 条目包含文本数量 | 是 | 调试统计 |
| `items[].controlCount` | 条目包含控件数量 | 是 | 调试统计 |
| `debug` | 视觉调试信息 | 是 | 常规请求删除 |
| `debug.dropSummary` | 被裁剪信号统计 | 是 | 仅诊断模式保留 |

#### 5.3 `hybridObservation` 字段

| 字段名 | 字段含义 | 是否删除 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | hybrid schema 版本 | 条件删除 | 调试有用；常规 LLM 可删除或保留，成本极低 |
| `mode` | 融合模式，如 `hybrid_native_screen` | 否 | 判断 native/vision 可用性需要 |
| `primarySource` | 主观测来源 | 是 | 顶层 `source` 已有等价信息 |
| `activityClassName` | Activity 类名 | 是 | 顶层已有同名字段 |
| `targetHint` | 当前目标提示 | 是 | 顶层已有同名字段 |
| `summary` | 融合后的页面摘要 | 否 | 页面理解主入口 |
| `executionHint` | 固定执行提示 | 条件删除 | 可固化到 prompt，避免每次 observation 重复携带 |
| `page` | 页面几何信息 | 否 | 区域判断和坐标归一需要 |
| `page.width` | 页面宽度 | 否 | bounds 和 region 判断需要 |
| `page.height` | 页面高度 | 否 | bounds 和 region 判断需要 |
| `availableSignals` | native/vision 可用性摘要 | 条件删除 | 诊断有用，常规 LLM 可压缩 |
| `availableSignals.nativeXml` | native XML 是否可用 | 条件删除 | 可由 mode/quality 推断 |
| `availableSignals.screenVisionCompact` | 视觉紧凑结果是否可用 | 条件删除 | 可由 mode/quality 推断 |
| `availableSignals.visualPageGeometry` | 视觉页面几何是否可用 | 条件删除 | 可由 page 推断 |
| `quality` | 融合质量统计 | 条件删除 | 保留核心统计，删除调试统计 |
| `quality.nativeNodeCount` | native 节点数量 | 否 | 成本低，可辅助质量判断 |
| `quality.nativeTextNodeCount` | native 文本节点数量 | 条件删除 | 常规 LLM 可删除 |
| `quality.visionTextCount` | 视觉文本数量 | 条件删除 | 常规 LLM 可删除 |
| `quality.visionControlCount` | 视觉控件数量 | 条件删除 | 常规 LLM 可删除 |
| `quality.fusedMatchCount` | native/vision 融合匹配数量 | 否 | 可辅助判断观测可信度 |
| `quality.visionDroppedTextCount` | 视觉文本裁剪数量 | 是 | 调试统计 |
| `quality.visionDroppedControlCount` | 视觉控件裁剪数量 | 是 | 调试统计 |
| `actionableNodes` | 可执行候选列表 | 否 | 导航和 fast execute 的核心输入 |
| `actionableNodes[].id` | 候选 id | 否 | 候选追踪和日志有用 |
| `actionableNodes[].source` | 候选来源：`fused/native/vision_only` | 否 | 置信判断必须要 |
| `actionableNodes[].text` | native 或视觉文本 | 否 | 目标匹配核心字段 |
| `actionableNodes[].contentDescription` | native content-desc | 阶段删除 | 导航保留；readout 可删除或只保留目标相关项 |
| `actionableNodes[].className` | native 控件类名 | 阶段删除 | 导航可能用于 icon/容器判断；readout 可删除 |
| `actionableNodes[].resourceId` | native resource-id | 否 | 稳定目标识别依据 |
| `actionableNodes[].visionLabel` | 视觉标签 | 否 | 视觉语义补强 |
| `actionableNodes[].visionRole` | 视觉角色 | 条件删除 | 若 `visionLabel/anchorType` 足够，可删 |
| `actionableNodes[].visionType` | 视觉类型 | 条件删除 | 视觉兜底时保留，常规可删 |
| `actionableNodes[].nativeNodeIndex` | native 节点索引 | 是 | 调试追踪字段 |
| `actionableNodes[].region` | 页面区域，如 top-left/bottom | 否 | 角落入口、底部 tab 等判断需要 |
| `actionableNodes[].anchorType` | 候选锚点类型，如 card/icon/text/tab | 阶段删除 | 导航保留；readout 可弱化 |
| `actionableNodes[].containerRole` | 父容器角色，如 card/list item/grid item | 阶段删除 | 导航保留；readout 可弱化 |
| `actionableNodes[].parentSemanticContext` | 父容器语义上下文 | 阶段删除 | 导航保留；readout 可删除 |
| `actionableNodes[].bounds` | 点击区域 | 否 | gesture / fast execute 必须要 |
| `actionableNodes[].score` | 候选得分 | 否 | 排序和置信判断需要 |
| `actionableNodes[].actionability` | 可执行性标签 | 否 | 让 LLM 快速理解候选可执行性 |
| `actionableNodes[].matchScore` | native/vision 匹配分 | 是 | 本地融合中间量，保留 `source/score` 即可 |
| `actionableNodes[].matchedVisionId` | 匹配的视觉信号 id | 是 | 调试字段 |
| `actionableNodes[].matchedVisionKind` | 匹配的视觉信号类型 | 条件删除 | 诊断保留，常规可删 |
| `actionableNodes[].clickable` | 节点自身是否可点击 | 阶段删除 | 导航保留；readout 可删除 |
| `actionableNodes[].containerClickable` | 父容器是否可点击 | 阶段删除 | 导航保留；readout 可删除 |
| `actionableNodes[].enabled` | 是否可用 | 否 | 状态判断有用 |
| `actionableNodes[].selected` | 是否选中 | 否 | tab/状态判断有用 |
| `actionableNodes[].badgeLike` | 是否像角标 | 阶段删除 | 导航候选过滤保留；readout 可删 |
| `actionableNodes[].numericLike` | 是否像纯数字 | 阶段删除 | 导航候选过滤保留；readout 可删 |
| `actionableNodes[].decorativeLike` | 是否像装饰元素 | 阶段删除 | 导航候选过滤保留；readout 可删 |
| `actionableNodes[].repeatGroup` | 是否重复组候选 | 阶段删除 | 导航候选过滤保留；readout 可删 |
| `sections` | 融合后的页面区域 | 阶段删除 | 导航可删；readout 只保留摘要 |
| `sections[].id` | 区域 id | 条件删除 | 诊断字段 |
| `sections[].type` | 区域类型 | 条件删除 | 布局理解需要时保留 |
| `sections[].sectionId` | 所属区域 | 条件删除 | 诊断字段 |
| `sections[].summaryText` | 区域摘要 | 条件删除 | readout 有价值 |
| `sections[].bounds` | 区域坐标 | 条件删除 | 布局判断需要时保留 |
| `sections[].importance` | 区域重要性 | 是 | 本地排序后可删除 |
| `sections[].matchedNativeNodeIds` | 区域内 native 节点 id | 是 | 调试字段 |
| `sections[].matchedNativeNodeCount` | 区域内 native 节点数量 | 是 | 调试统计 |
| `sections[].collapsedItemCount` | 折叠条目数 | 是 | 调试统计 |
| `listItems` | 融合后的列表项 | 阶段删除 | 导航可删；readout 保留摘要文本 |
| `listItems[].id` | 列表项 id | 条件删除 | 诊断字段 |
| `listItems[].type` | 列表项类型 | 条件删除 | readout 可保留 |
| `listItems[].sectionId` | 所属区域 | 条件删除 | 诊断字段 |
| `listItems[].summaryText` | 列表项摘要 | 否 | readout 内容总结需要 |
| `listItems[].bounds` | 列表项坐标 | 条件删除 | readout 可按需保留 |
| `listItems[].importance` | 列表项重要性 | 是 | 本地排序后可删除 |
| `listItems[].matchedNativeNodeIds` | 命中的 native 节点 id | 是 | 调试字段 |
| `listItems[].matchedNativeNodeCount` | 命中的 native 节点数量 | 是 | 调试统计 |
| `listItems[].textCount` | 文本数量 | 是 | 调试统计 |
| `listItems[].controlCount` | 控件数量 | 是 | 调试统计 |
| `conflicts` | 观测冲突列表 | 条件删除 | 导航保留高风险冲突；readout 可压缩 |
| `conflicts[].code` | 冲突类型 | 否 | fast execute 阻断判断需要 |
| `conflicts[].severity` | 冲突等级 | 否 | 判断 warning/error 需要 |
| `conflicts[].message` | 冲突描述 | 条件删除 | 高风险保留简短 message，普通 warning 可删 |
| `conflicts[].bounds` | 冲突区域 | 阶段删除 | 导航保留；readout 可删除 |
| `conflicts[].nativeNodeIndex` | 关联 native 节点索引 | 是 | 调试字段 |
| `debug` | 融合调试信息 | 是 | 常规 LLM 请求体删除 |
| `debug.matchPairs` | native/vision 匹配明细 | 是 | 调试字段 |
| `debug.nativeOnlyCandidates` | native-only 候选明细 | 是 | 调试字段 |
| `debug.visionOnlyCandidates` | vision-only 候选明细 | 是 | 调试字段 |
| `debug.topNativeTexts` | top native 文本 | 是 | 调试字段，必要文本已进入 summary/actionable/listItems |
### 6. 裁剪后的验收标准

字段裁剪必须满足以下条件，才认为对 LLM 结果没有实质影响。

| 验收项 | 标准 |
| --- | --- |
| Native 导航 | 仍能从 `hybridObservation.actionableNodes` 解析出候选、bounds、source、score 和点击状态 |
| Native readout | 仍保留目标页摘要、可见文本、列表项摘要和关键状态文本 |
| Web 操作 | 仍能拿到 `screenElements.ref/selector`，并基于同一 `snapshotId` 调用 `android_web_action_tool` |
| 请求体大小 | Native view context 目标降到 `<15KB~20KB`，readout 目标降到 `<10KB` |
| 回退能力 | 若候选缺失或 readout 内容不足，应提高 `ObservationDetailMode` 或打开诊断字段，而不是继续低置信执行 |