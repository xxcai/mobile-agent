# Android Skill 编写规范

本文档总结当前仓库中围绕 `skills/` 的实际协作经验，目标是给 AI 和协作者提供一份统一的 Skill 编写规格。

本文档不是介绍“如何把文件放进 assets”，而是回答：

- Skill 应该如何组织结构
- 什么时候拆 Skill
- 什么时候引入 `references/`
- 怎样写才不容易诱导模型走错路
- 怎样对齐当前工程的 `shortcuts + skills` 模式

本文档主要基于本仓库当前已经验证过的写法：

- Skill 主文档负责规程、路由和约束
- Reference 文档负责参数、错误、细节说明
- 不依赖运行时硬拦截，优先靠清晰规程减少错误调用

## 1. 总体原则

### 1.1 Skill 不是工具

Skill 是行为规程，不是可执行动作。

- Skill 用 `read_file("skills/<skill_name>/SKILL.md")` 读取
- Shortcut 用 `describe_shortcut("<shortcut_name>")` 查看定义
- Shortcut 用 `run_shortcut(...)` 执行

不要在 Skill 中写出会诱导模型把 Skill 名当 shortcut 的表述。

例如：

- `im_sender` 是 skill 名，不是 shortcut 名
- `contact_resolver` 是 skill 名，不是 shortcut 名

### 1.2 Skill 负责路由，Shortcut 负责执行

Skill 的职责是：

- 判断何时进入当前任务域
- 决定下一步该读哪个 Skill / Reference
- 决定何时调用哪个 shortcut
- 明确何时不要继续
- 明确哪些 fallback 是禁止的

Shortcut 的职责是：

- 执行一个明确、原子、可验证的动作

不要把大量业务判断塞进 shortcut，也不要让 Skill 退化成 shortcut 参数抄写页。

### 1.3 Skill 主文档讲流程，Reference 讲细节

推荐写法：

- `SKILL.md` 负责：
  - 触发条件
  - 工作流程
  - 决策分支
  - 禁止事项
  - 少量关键示例
- `references/*.md` 负责：
  - 参数约束
  - 返回结构
  - 错误码语义
  - 候选选择规则
  - 特定动作的细粒度限制

## 2. Skill 类型划分

当前工程里的 Skill 至少分两类。

### 2.1 shortcut-guided skill

用于指导 Agent 串联业务 shortcut。

适用场景：

- 联系人解析
- 发消息
- route 导航

当前示例：

- [contact_resolver/SKILL.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/contact_resolver/SKILL.md)
- [im_sender/SKILL.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/im_sender/SKILL.md)
- [route_navigator/SKILL.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/route_navigator/SKILL.md)

### 2.2 visual-operation skill

用于指导 Agent 使用 `android_view_context_tool` / `android_gesture_tool` 观察和操作页面。

适用场景：

- 页面总结
- 受控浏览
- 基于页面结构的阅读、点击、滑动

当前示例：

- [moments_summary/SKILL.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/moments_summary/SKILL.md)
- [cloud_space_summary/SKILL.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/cloud_space_summary/SKILL.md)

不要把所有 Skill 都强行改写成 shortcut-first。

## 3. 推荐目录结构

最小结构：

```text
skills/
└── my_skill/
    └── SKILL.md
```

带细节说明的结构：

```text
skills/
└── my_skill/
    ├── SKILL.md
    └── references/
        ├── action-a.md
        └── action-b.md
```

当前推荐做法：

- 一个 Skill 一个目录
- `SKILL.md` 是主入口
- 细节文档放 `references/`

## 4. 主 Skill 文档的推荐结构

`SKILL.md` 建议按下面顺序组织：

1. Frontmatter
2. 标题与一句话目标
3. `CRITICAL` 前置规则
4. 使用原则
5. 触发条件
6. 入口分类
7. 推荐 Shortcut
8. 工作流程
9. 结果处理
10. 错误处理
11. 示例
12. 禁止事项

### 4.1 把最关键约束放在最前面

如果某个 Skill 依赖另一个 Skill，或依赖某份 reference，必须在最开头明确写成强规则。

推荐写法：

```md
**CRITICAL — 当联系人尚未明确时，MUST 先用 `read_file` 读取并遵循 `skills/contact_resolver/SKILL.md`。**
**CRITICAL — 调用 `send_im_message` 前，MUST 先读取 `skills/im_sender/references/send-im-message.md`。**
```

重点：

- 使用明确的 workspace 相对路径
- 不要写模糊的 `references/foo.md`
- 不要让模型猜“相对谁”

### 4.2 工作流程要是一棵单一决策树

不要把规则分散成：

- 前面一组补充说明
- 中间一组流程
- 后面再追加几个“例外”

更推荐：

- 先判断输入类型
- 再判断是否需要调用 shortcut
- 再决定下一步是追问、调用还是停止

## 5. 什么时候拆 Skill

当一个 Skill 同时承担两个相对独立的子问题时，应考虑拆分。

典型信号：

- 同一个 Skill 既要处理“目标解析”，又要处理“动作执行”
- 续轮状态规则越来越多
- 需要单独说明前置解析、候选确认、错误 fallback

本仓库中的实际例子：

- 最初 `im_sender` 同时负责“搜人 + 候选选择 + 发消息”
- 后来拆成：
  - [contact_resolver/SKILL.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/contact_resolver/SKILL.md)
  - [im_sender/SKILL.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/im_sender/SKILL.md)

拆分原则：

- 前置解析 Skill 产出稳定状态
- 后续执行 Skill 消费该状态

不要让“候选序号解释”和“最终发送确认”混在一个超重 Skill 里。

## 6. 什么时候引入 Reference

满足任一条件时，建议引入 `references/`：

- 某个 shortcut 的参数约束较多
- 某个动作的成功/失败条件需要单独说明
- 某类续轮规则容易误导模型
- 多个 Skill 会复用同一套细节规则

当前例子：

- [contact-selection.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/contact_resolver/references/contact-selection.md)
- [send-im-message.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/im_sender/references/send-im-message.md)

这些内容适合放 Reference，而不是反复堆在主 Skill 里：

- 候选序号不等于 `contact_id`
- 何时可跳过重搜
- `send_im_message` 的成功条件
- 常见错误码处理

## 7. 必须避免的几类写法

### 7.1 不要把 Skill 名写成像 shortcut 一样

错误风险：

- 模型会先试 `run_shortcut("im_sender")`
- 或 `describe_shortcut("im_sender")`

正确做法：

- 在 Skill 中反复明确“这是 skill 名，不是 shortcut 名”
- 真正可执行的动作放在“推荐 Shortcut”小节里

### 7.2 不要让规则前后打架

典型错误：

- 前面写“步骤 2 默认先搜索联系人”
- 后面再写“其实有候选时不应重新搜索”

正确做法：

- 把“什么时候要搜、什么时候可跳过”写在同一个步骤里

### 7.3 不要写模糊的 Reference 路径

避免：

- `references/send-im-message.md`

推荐：

- `skills/im_sender/references/send-im-message.md`

### 7.4 不要把错误 fallback 留成默认自由发挥

如果某条错误路径不应该走，必须写成禁止项。

例如：

- 不要把候选序号直接当成 `contact_id`
- 不要在已有候选联系人列表时无意义重搜
- 不要在已有业务规程时退回 UI 手势兜底
- 不要在本回合未成功执行时宣称“已发送”

## 8. 续轮场景的写法要求

对短输入续轮，例如：

- `1`
- `2`
- `第一个`
- `技术部那个`
- `再发一条`

必须在 Skill 中明确这些输入属于哪类延续：

- 候选选择延续
- 新的执行动作延续
- 只是补充消息内容

不要把短输入默认当成新的独立任务。

推荐写法：

- 在“入口分类”里单独列出续轮类型
- 在 Reference 中写清楚解释规则
- 在示例中给出上一轮和这一轮的完整对照

## 9. Shortcut-guided skill 的最低要求

新增一个 shortcut-guided skill 时，至少满足：

- 明确该 Skill 负责什么，不负责什么
- 明确推荐哪些 shortcut
- 明确 Skill 名不是 shortcut 名
- 如果依赖其他 Skill，前置写 `CRITICAL`
- 如果依赖 Reference，使用显式 workspace 相对路径
- 明确哪些场景不要调用 shortcut，而是先追问
- 明确哪些结果下不能宣称任务完成
- 至少提供一个首轮示例和一个续轮示例

## 10. 评审清单

新增或改写 Skill 时，提交前至少检查：

- 是否明确区分了 Skill 与 shortcut
- 是否把最关键的 MUST 规则放在文首
- 是否使用了显式 workspace 相对路径
- 是否把主流程写成一棵单一决策树
- 是否避免了“前文默认 A，后文再例外否定 A”的结构
- 是否把复杂细节下沉到 `references/`
- 是否写明了禁止的 fallback 路径
- 是否覆盖了续轮输入
- 是否给出至少一个失败分支

## 11. 当前推荐参考样例

可直接参考这些文件：

- [contact_resolver/SKILL.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/contact_resolver/SKILL.md)
- [contact-selection.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/contact_resolver/references/contact-selection.md)
- [im_sender/SKILL.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/im_sender/SKILL.md)
- [send-im-message.md](/Users/caixiao/Workspace/projects/mobile-agent/app/src/main/assets/workspace/skills/im_sender/references/send-im-message.md)

这些文件共同体现了当前仓库中较成熟的 Skill 写法：

- 先拆职责
- 再写规程
- 细节下沉到 reference
- 用明确禁止项限制错误调用路径
